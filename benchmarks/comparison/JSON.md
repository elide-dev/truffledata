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
| `simdjson-generic-java` | `JsonSvm.decode`, `export(JavaSink)`, read the Java tree, close | Live messages; the guest exports each into a fresh `JavaSink` through an adopted `JsonExportNode` and reads the tree behind a boundary |
| `simdjson-generic-interop` | Decode, read through cached `InteropLibrary`, close | Live messages, read by the guest through cached interop, one library per member site |
| `simdjson-snapshot` | Decode, `snapshot()`, close, read the snapshot through interop | Snapshots, read like `simdjson-generic-interop` |

Stages match the scalar workload. Ingestion includes parsing, consumption, escape
of the retained result, and native release. Retained reads rotate over 32 live
values. Peak uses the same tier and in-helper compiled marker checks.

Caveats:

- Gson's tree parser is lenient (it accepts comments, unquoted names and similar);
  it still rejects trailing input. This is a valid-input performance fixture, not a
  conformance comparison. Gson stores numbers as `LazilyParsedNumber`, so its reads
  parse number text on every access.
- Array elements are counted but not read. `JsonValue.readArrayElement` calls the
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
| `simdjson-export-walk`, peak (smoke-only, constant counting sink) | ≤ 1,000 | 2 | ≤ 2 | ≤ 1.0 |
| `simdjson-generic-java`, peak and read-aot | ≤ 1,000 (peak) | 2 (peak) | not gated | ≤ 2,400 |
| `gson-tree`, peak | ≤ 1,000 | 1 | 0 | — |

The interop loop measured 1,754–1,810 nodes: ten inlined member reads and their
owner checks. The export loops have two loops, the document loop and the tape walk.
Neither has a per-member loop or recursion. The walk's two graph allocation sites
are `JsonOwner.exportStack`'s container stacks for nested or unconfined exports. The
counter's 1.0 B/document shows they never run for the outermost export on a
confined message. 1.0 B/document is the harness's own per-call boxing; all three
scalar peak cells measure exactly that.

**`JavaSink` budget: 2,400 Java bytes per document.** The measured value is 2,357 B
(2,356 B when the export runs directly from AOT code). It covers only what the sink
needs: the `JavaSink` and its stack, two `LinkedHashMap`s with their tables and
entries, two `ArrayList`s, the key and value `String`s, boxed numbers, and the 16–19
key and string views per document. Those views escape because `JavaSink`'s event
methods are boundaries. The budget leaves 43 B of headroom. That is less than two
additional 24-byte objects per document. Any per-node allocation in the walk (at
least 16 B × ~40 nodes) or an escaping view per scalar fails it.

### Results

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
