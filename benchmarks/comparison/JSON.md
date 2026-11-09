# JSON comparison

Run `bazel test //benchmarks/comparison:json_native_smoke` for correctness and tier
checks; run `bazel test //benchmarks/comparison:json_measure` for three fresh forks
per timing/allocation cell. Reports are in that test's undeclared outputs. The
reusable native CI job runs the smoke target. No Jackson or protobuf dependency
is needed by this separate JSON executable. The executable has two workloads:
scalar records (typed plans) and generic documents (the index, export, interop and
snapshots). They are described below.

## Scalar records

The fixture contains 32 varying UTF-8 objects with shuffled keys and three required
non-null fields: long, double, boolean. Native simdjson decodes through a reusable
`JsonPlan`. Stock uses a hand-written Gson streaming decoder from UTF-8 bytes
through `InputStreamReader`, producing an owned Java record. It checks duplicate,
unknown, missing, and trailing fields. This is a valid-input performance fixture,
not proof of identical malformed-number semantics or general JSON compliance.
Gson's reader buffers are included; this is not a comparison with Jackson's byte
parser, a reused reader, or a hand-written simdjson C++ baseline.

Ingestion includes parsing, three-field consumption, escape of the returned record,
and native result release. Plan compilation is excluded. Retained-read cases reuse
32 live records and rotate the starting index. Both use the same guest loop for
interpreter and peak tests; direct AOT reads bypass the guest call. Peak requires a
stable last-tier target before and after sampling, plus an `inCompiledCode()`
marker returned from the read helper itself. The marker is masked from the checksum;
a compiled wrapper calling an AOT helper fails the probe. Allocation counters are calibrated
with an escaped megabyte array. Java allocation excludes all native memory; native
input padding, slot buffers, parser scratch, and allocator overhead are not included.

The initial `d2191a5` results expose a retained-read gap: native reads took roughly
22–35 ns per record versus about 2 ns for owned stock values. Native ingestion used
56 Java bytes, versus 11,288 for the Gson reader-based path. Timing on shared RBE
workers was highly variable, especially ingestion and interpretation; do not use
these results as a universal speedup claim. Raw samples, fork ranges, binary hashes,
and source provenance are retained under `benchmarks/results/json-svm-*`.

An experiment caching the native slot address and presence/null masks in the Java
record raised ingestion allocation to 80 bytes. Its timing ranges overlapped the
baseline and peak reads remained well behind stock. That change was reverted;
schema-specialized access remains the next optimization to investigate.

## Profile before further tuning

`bazel test //benchmarks/comparison:json_profile` builds a separate SVM executable
with JFR, local symbols, and frame pointers. Recording begins only after benchmark
warmup and last-tier stabilization. Each cell records three seconds of work.
`python3 tools/bench/json_profile_summary.py <recording-directory>` summarizes
application stacks, total versus selected samples, and sampled allocation counts.
Sampled counts are not allocation bytes, and Java-visible stacks do not account
for native parser allocations.

The diagnostic executable also accepts `engine stage time recording-path` directly,
so it can run under Linux `perf record`. Use a dedicated Linux host, record the
binary hash, CPU model, affinity, and source commit, and serialize profiling and
uninstrumented timing runs. Never use recorded timings as comparison scores.

The first symbolized hardware profiles on unclemax expose a weakness in the peak
check: a valid last-tier target still calls the AOT `Reads.read` helper, for stock
as well as native records. Native generic profiles concentrate in `check`, `slot`,
and `checkThread`; specialized profiles concentrate in `bits`, `nativeValue`, and
`checkThread`. These are observations from the diagnostic image, not proof of a
parser bottleneck. Existing `peak` results mean a compiled wrapper, and must not
be presented as fully compiled guest scalar reads. The next compiler investigation
must verify the hot loop and accesses themselves are compiled before using that
stage to claim peak JIT performance.

The cause was the benchmark's explicit runtime initialization of `JsonComparison`.
Its read helper calls static methods on that class; SVM rejects runtime compilation
graphs with class-initialization checks. Both benchmark images now use build-time
initialization for this class (its initial state contains no native resources).
The smoke test now passes with the helper-level compiled marker. Historical results
remain unchanged for provenance; use new measurements for peak comparisons.

The `23f969a` unclemax cycle measures specialized peak reads at 1.497 ns/record,
generic native reads at 2.568 ns, and stock record reads at 0.948 ns. Native
ingestion is 175–178 ns versus 894 ns for this Gson fixture. These are medians of
three fresh-process fork medians on one host, not general parser rankings.
Compiler expansion traces now show one loop, zero calls, and zero graph allocations
for all three engines; specialized last-tier IR has 147 nodes versus 367 generic
and 64 stock. Hardware samples move from AOT accessors into JIT code.
The smoke test now enforces those loop/call/allocation properties and a 1,000-node
budget, retaining its compiler trace as a test artifact. Java call/return boxing
still accounts for 1 byte/record in the peak harness allocation counter.
Raw results and traces: [`json-unclemax-23f969a`](../results/json-unclemax-23f969a/).

A follow-up [slot-pointer-only cache experiment](../results/json-unclemax-slot-cache/)
ran against the corrected compiled harness. Specialized peak reads measured
1.429 ns (fork medians 1.429–1.520), overlapping the 1.497 ns baseline
(1.487–1.581); generic peak and AOT reads did not improve. It added 8 Java bytes
per ingested record (64 versus 56). The experiment passed ownership, JIT graph,
and allocation gates but was reverted: the measured benefit does not justify
the permanent memory cost. Its exact source patch and samples are retained.

## Generic documents

The `json-generic` workload ([`JsonFixture`](JsonFixture.java)) has 32 mixed UTF-8
documents. Each has nine members: I64 `id`, a short string, an F64 `price`, a
boolean, a string array of one to four tags, a nested object (`w`, `h`, a short
string), an integer array of three or four elements, a long string and null. Every
`note` exceeds Myna's 16-byte inline text. Odd notes carry quote, backslash,
control, two-byte and surrogate-pair escapes, and odd documents reverse member
order. Every engine reads the same ten members per document: eight top-level
members, plus `w` and `h` inside the nested object. It sums `id`, `(long) price`,
`active`, `w`, `(long) h`, the two array sizes, and one each for the note being a
string and `parent` being null. The checksum is 38,219.

| Engine | Ingest (per document) | Retained form and read |
| --- | --- | --- |
| `gson-tree` | `JsonParser.parseReader` over `InputStreamReader` from UTF-8 bytes, then read | `JsonElement` trees, read in one `@TruffleBoundary` call per document |
| `simdjson-generic-java` | `JsonSession.decode`, `export(JavaSink)`, read the Java tree, close | Live messages; the guest exports each into a fresh `JavaSink` through an adopted `TapeExportNode` and reads the tree behind a boundary |
| `simdjson-generic-interop` | Decode, read through cached `InteropLibrary`, close | Live messages, read by the guest through cached interop, one library per member site |
| `simdjson-snapshot` | Decode, `snapshot()`, close, read the snapshot through interop | Snapshots, read like `simdjson-generic-interop` |
| `simdjson-session-java` (ingest only) | Borrowed `JsonSession.parse(input, JavaSink)`, read the Java tree | Nothing native is retained, so there is no read stage |
| `simdjson-session-truffle` (ingest only) | Borrowed `JsonSession.parse(input, TruffleSink)` over the shared text arena, read the `TruffleString`-keyed tree | As above (Task D2) |
| `gson-stream-lines` (ingest only) | The 32 documents as one NDJSON buffer: split at newlines, parse each line into a Gson tree, read it | No read stage (Gson's strict reader takes one top-level value, so lines are split by the caller) |
| `simdjson-stream-java` (ingest only) | The same buffer fed to one `JsonStream` per run (never finished); `next(JavaSink)` per document, read the Java tree | No read stage (Task F2) |
| `simdjson-stream-interop` (ingest only) | As above; `next()` lends each root, ten AOT interop reads | No read stage (Task F2) |
| `jackson-tree` | Jackson databind `ObjectMapper.readTree(byte[])`, then read | `JsonNode` trees, read in one boundary call per document |
| `jackson-stream` (ingest only) | Jackson's streaming `JsonParser` over the bytes, reading the ten members as their tokens pass | No retained form |
| `fastjson2-tree` | fastjson2 `JSON.parseObject(byte[])`, then read | `JSONObject` trees, read like Gson's |
| `dsljson-map` | DSL-JSON `DslJson.deserialize(Map.class, byte[], length)`, then read | `Map`/`List`/`Number` trees |
| `jsoniter-any` | jsoniter `JsonIterator.deserialize(byte[])` (lazy `Any`), then read | `Any` trees over the input bytes |
| `simdjson-java` (ingest only) | simdjson-java `SimdJsonParser.parse(byte[], length)` (Vector API), then read | No retained form: values live in the parser's reused tape |

The six competitor engines (the JSON scoreboard, JSON performance ledger V3) are
benchmark-only Maven dependencies (`MODULE.bazel`): Jackson 2.22.4 (streaming and
databind), fastjson2 2.0.66, DSL-JSON 2.0.2, jsoniter 0.9.23 and simdjson-java
0.4.0. They run in the same SVM image as every other cell, from `JsonCompetitors`
(initialized at image run time; `--add-modules=jdk.incubator.vector` for
simdjson-java), parse the same UTF-8 bytes and read the same ten members to the
same checksum. Each library runs with its defaults: no schema binding, code
generation or reflection-based object mapping, so these are tree (or streaming)
parsers against our index, not data-binding results. Their compiled read roots are
graph-gated like `gson-tree` (one boundary call per document).

Since performance plan Task D1 the `JavaSink` cells (`simdjson-generic-java` and
`simdjson-session-java`) share one `JavaSink.KeyCache` per run, as an embedder
decoding one schema would; their earlier results materialized every key.

Since performance plan Task B2 every simdjson ingest decodes through one explicit
`JsonSession` per run (`decode` moves the session's storage into the message);
the retained rows are still built with `JsonSvm.decode`. Earlier results used
`JsonSvm.decode` for ingest too.

Stages match the scalar workload. Ingestion includes parsing, consumption, escape
of the retained result, and native release. Retained reads rotate over 32 live
values. Peak uses the same tier and in-helper compiled marker checks.

Caveats:

- Gson's tree parser is lenient (it accepts comments, unquoted names and similar);
  it still rejects trailing input. This is a valid-input performance fixture, not a
  conformance comparison. Gson stores numbers as `LazilyParsedNumber`, so its reads
  parse number text on every access.
- Array elements are counted but not read. `TapeValue.readArrayElement` calls the
  instance boundary `element(index)`. In compiled code, that would materialize the
  freshly read array view on every access. Reading elements needs a static
  `(owner, node)` element path like `memberNode`. Until then, element reads are not
  in this fixture.
- Strings are type-checked, not materialized, in the interop reads. Materializing
  needs a boundary that receives the view (`asString` or `asTruffleString`).
- Native memory is not counted. A live message retains its padded input, index,
  decoded text and lexical spans. A snapshot copies all of them onto the Java heap,
  so its retained form and its ingestion allocation include that copy.

### Smoke gates

`json_native_smoke` attributes each Truffle-tier expansion trace to its root and
applies per-root budgets. Every rule requires zero invokes in the graph. That trace
column counts unresolved Java calls only, not `@TruffleBoundary` calls. The
`gson-tree` root makes one boundary call per document and still reports zero.

| Root | Graph nodes | Loops | Graph allocations | Java B/document (counter) |
| --- | ---: | ---: | ---: | ---: |
| Scalar engines (unchanged) | ≤ 1,000 | 1 | 0 | — |
| `simdjson-generic-interop`, peak | ≤ 2,000 | 1 | 0 | ≤ 1.0 |
| `simdjson-snapshot`, peak | ≤ 2,000 | 1 | 0 | not gated (see results) |
| `simdjson-export-walk`, peak (smoke-only, constant counting sink) | ≤ 1,000 | 2 | 0 | ≤ 1.0 |
| `simdjson-generic-java`, peak and read-aot | ≤ 1,000 (peak) | 2 (peak) | not gated | ≤ 1,200 |
| `gson-tree`, peak | ≤ 1,000 | 1 | 0 | — |
| `simdjson-generic-interop`, ingest | — | — | — | ≤ 480 |
| `simdjson-session-java`, ingest | — | — | — | ≤ 1,280 (1,400 before ledger V1) |
| `simdjson-session-truffle`, ingest | — | — | — | ≤ 1,680 (1,800 before V1) |
| `simdjson-stream-java`, ingest | — | — | — | ≤ 1,280 (1,400 before V1) |
| `simdjson-stream-interop`, ingest | — | — | — | ≤ 320 (440 before V1) |
| Competitor tree engines, peak (`jackson-tree`, `fastjson2-tree`, `dsljson-map`, `jsoniter-any`) | ≤ 1,000 | 1 | 0 | — |

The interop loop measured 1,754–1,810 nodes: ten inlined member reads and their
owner checks. With position-hinted member lookup (performance plan Task C1) it
measures 1,916–1,972: each read inlines its hint check (one tape call comparing the
key's side-table entry and node words) and keeps one static boundary for misses.
Earlier drafts that inlined the miss path and three separate tape reads per member
measured 2,866–3,037 nodes and were rejected by this gate. Since Myna value ABI v4
(ledger V1) each read checks a directory-rank hint through one tape call and the
owner reads node kinds with Myna's checked `kindAt`: 1,621–1,678 nodes. An exact-type
dispatch over the two image tapes measured 1,841–1,898 and was dropped. The export loops have two loops, the document loop and the tape walk.
Neither has a per-member loop or recursion. Since Task D1 the walk has no graph
allocation: the owner's reusable `TapeText` cursor carries the container stack, and a
first, nested or unconfined export allocates its cursor behind a boundary
(`TapeOwner.newCursor`). Before D1 the walk had two allocation sites (the container
stacks for nested or unconfined exports). The counter's 1.0 B/document shows the
outermost export on a confined message allocates nothing. 1.0 B/document is the harness's own per-call boxing; all three
scalar peak cells measure exactly that.

**`JavaSink` budget: 1,200 Java bytes per document** (2,400 before Task D1). The
measured value is 1,169 B at peak and 1,168 B from AOT code, against 2,357 B before
D1. It covers only what the sink builds: the `JavaSink` and its stack, two
`LinkedHashMap`s with their tables and entries, two `ArrayList`s, the value
`String`s and boxed numbers. Since D1 the sink receives one reusable `TapeText`
cursor instead of 16–19 views per document, decodes ASCII strings with one copy, and
every `JavaSink` cell shares one `JavaSink.KeyCache`, so the twelve member names
are not reallocated per document. The budget leaves 31 B of headroom: any per-node
allocation in the walk, an escaping view per scalar, or a key `String` per member
fails it.

**Session ingest budgets (Task B2).** Interop ingest measured 472 B/document: the
session decode's Java objects (tape reader, side-table accessor, message, pinned
input) plus the same 240 B of ten AOT interop views that `read-aot` shows, against
544 B through `JsonSvm.decode` before sessions. The borrowed `JavaSink` parse
measured 3,596 B/document in B2 and 1,360 B/document after Task D1 (the host tree
and value `String`s; the borrow's Java objects; the session lends its one text
cursor to every borrow), under a 1,400 B gate. The plan's 1,900 B target is met;
its 160 B decode-overhead target is not (the 240 B of AOT interop views are read
cost; since ledger V1 borrows rebind one reader per session, elide-dev/myna#47).

**Truffle session budget (Task D2).** The borrowed parse into a `TruffleSink` measured
1,772.5 B/document under a 1,800 B gate: the host tree, one `TruffleString` per string
value sharing the document's text arena, and the arena itself (a `byte[]` of
`2 × length + 16` bytes per document, since the strings keep it). Keys come from a
`TruffleSink.KeyCache`. The plan's graph gate `(1000, 2, None)` applies to a compiled
read root; this cell is ingest-only like `simdjson-session-java`, so it has none.

**Stream budgets (Task F2).** One NDJSON buffer of the 32 documents per operation
through a `JsonStream` that is never finished. `next(JavaSink)` measured 1,338.5
B/document under the session `JavaSink` gate (1,400 B; the plan held streams to the
Task D1 budget). `next()` plus ten AOT interop reads measured 432.75 B/document under a
440 B gate: a borrow owner and tape reader per lent document (as for session parses)
and the 240 B of interop views. The plan's 1.0 B/document and `(2000, 1, 0)` graph
gate assumed a compiled read root over a reusable borrow; streamed documents are lent
one at a time, so these cells are ingest-only (like the session cells) and the reusable
borrow reader came with Myna 0.11.0 (elide-dev/myna#47, ledger V1).

**Myna value ABI v4 (ledger V1).** Sessions rebind one tape reader and lexical
accessor for every borrow instead of allocating them, so every borrowed cell lost
120 B/document (session `JavaSink` 1,360 → 1,240, `TruffleSink` 1,772.5 → 1,652.5,
stream `JavaSink` 1,338.5 → 1,218.5, stream interop 432.75 → 312.75) and their gates
tightened to match. Retained messages keep a reader each; interop ingest measures 480
B/document (472 before: the reader's per-reader SIMD threshold) under its unchanged
gate. Snapshots copy their subtree's directory (snapshot ingest 1,997.5 → 2,101.5 B,
not gated).

### Results

The v4 results, the gaps measured before and after on one host, native decode
throughput against simdjson's ceiling and the competitor scoreboard are in
[`json-v4-86a691a`](../results/json-v4-86a691a/). On that host compiled member reads
of ten members take 90.9 ns per document against 223.8 ns for Gson trees (819.5 ns
before v4 directories), and native decode with ten interop reads ingests a document in
769.4 ns against Gson's 1,970.8 ns.

The first run is [`json-generic-c6e7abc`](../results/json-generic-c6e7abc/), one
`json_measure` invocation on a shared RBE worker. **Its timings are advisory.** At
peak, interop reads of ten members took about 2.7 µs per document against about
0.53 µs for Gson trees. Ingestion was 5.6 µs (decode and interop read) and 5.9 µs
(decode and `JavaSink`) against Gson's 4.7 µs. Java allocation was 528 and 3,660
B/document against Gson's 13,404, excluding native memory. Snapshot reads allocated
22,749 B and took about 28 µs per document. That is a defect behind `HeapValueTape`'s
boundaries; the graph itself has none. These figures do not support a speedup claim
for the generic path. The read cost and the snapshot allocation are the next things
to investigate.

## Numeric documents

The `json-numeric` workload (`JsonFixture.numericDocument`, plan 2026-11-08-beat-stock-svm
WS8) has 32 generated canada-style GeoJSON features. Each has a closed polygon ring of 160–188
`[lon,lat]` pairs, spelled with 17 significant digits like canada.json (`-54.738137000000002`).
The 32 documents total about 231 KB. The data is generated, not copied. Every engine is
ingest-only and reads the same six members: `properties.id`, the ring size, and the first and
last points rounded to microdegrees. The checksum is re-derived independently in
`json_typed_pair.py`/`json_measure.py`.

| Engine | Ingest |
| --- | --- |
| `simdjson-numeric-interop` | `JsonSession.decode`, six cached-interop reads, close |
| `simdjson-numeric-decode` | `JsonSession.decode` and close, no read |
| `gson-numeric` | Gson tree (`LazilyParsedNumber`: unread coordinates are never converted) |
| `jackson-numeric` | Jackson `readTree` (`DoubleNode` per coordinate) |
| `fastjson2-numeric` | fastjson2 `parseObject` (default `BigDecimal` decimals) |

`json_native_smoke` runs every cell. `json_typed_pair` shard group 8 pairs the family with
the mixed generic ingest/decode cells as same-host controls. This is the numeric-heavy
admission identity Sam ruled for number-parsing changes: ≥3% here with no loss on big/huge
mixed. The first candidate, a runtime-dispatched SSE4.2/AVX2 instantiation of simdjson's
number parser, was 4–11% slower and was reverted; see
[json-number-kernel-d34e525](../results/json-number-kernel-d34e525/).
