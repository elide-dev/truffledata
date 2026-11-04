# SVM comparison benchmarks

## Generated-access measurements

The [88-cell SVM run](https://github.com/elide-dev/truffledata/actions/runs/41776420028)
on implementation commit `c8ef96a2b4be` compares our generated getters with stock
protobuf Java. It uses three fresh timing forks and three separate allocation forks
per cell. [Raw JSON](../benchmarks/results/svm-comparison-c8ef96a/comparison.json) and
[full results](../benchmarks/results/svm-comparison-c8ef96a/comparison.md) retain the
original generic and typed API cases as well as the new generated cases.

| Ingestion projection | Stock ns/message | TruffleData ns/message | Stock / TruffleData Java heap bytes |
|---|---:|---:|---:|
| Scalar, owned generated getters | 67.01 | 29.82 | 136 / 56 |
| Scalar, Buffa generated getters | 67.01 | 161.51 | 136 / 200 |
| Timestamp JSON, Buffa generated getters | 3389.41 | 471.51 | 6439.75 / 712 |
| Nested protobuf, Buffa generated getters | 897.82 | 3198.47 | 1776.75 / 1751.75 |

Generated getters reduce Buffa nested interpreted reads from 51.79 to 22.58 ns/row
in the same run. Peak reads measure 10.01 versus 9.65 ns/row, with overlapping fork
ranges; this does not establish a peak-read improvement. Stock scalar peak reads
remain 1.09 ns versus owned 1.82 and Buffa 2.08; stock nested guest reads cross the
marked boundary. The aliased stock bytes baseline remains faster than Buffa
(51.13 versus 170.82 ns ingestion). General nested messages still materialize a
native index into rows and arrays; these results do not establish that the remaining
cost is in Buffa's parser itself.

The scalar native path skips general materialization state, temporary arrays and
boxed integers; both native backends reuse their result slot and release callback.
Existing generic Buffa scalar ingestion now uses 232 Java heap bytes versus 320 in
the 0.3.0 report; generated ingestion uses 200. Primitive-only static rows avoid a
payload tracker, reducing the SQL fixtures from 160 to 128 bytes. Native memory is
excluded from these Java heap counts. Timing ranges remain visible in the full
report, including noisy large-payload copies; the owned bytes generated-ingestion
result is especially unstable and should not be used to rank implementations.

An intermediate scalar storage union slowed ordinary bytes/nested reads. Separate
scalar rows, static library exports and final public accessors remove that dispatch
cost: ordinary Buffa peak reads measure 3.87 ns for bytes and 10.01 ns for nested
messages, versus 4.00 and 10.21 ns in the 0.3.0 run. These cross-run timings indicate
recovery, not a statistically established speedup. The earlier
[8fceec4 run](../benchmarks/results/svm-comparison-8fceec4/comparison.md),
[9c8a013 run](../benchmarks/results/svm-comparison-9c8a013/comparison.md) and
[repeat](../benchmarks/results/svm-comparison-9c8a013-repeat/comparison.md) remain
available, including their noisy results.

![Generated interpreter reads](images/generated-access/comparison-generated-interpreter.svg)

![Generated peak reads](images/generated-access/comparison-generated-peak.svg)

![Generated ingestion allocations](images/generated-access/comparison-generated-allocations.svg)

The same directory also contains generic-API charts and a provenance manifest.
The subsequent smoke-only allocation gates allow at most 240 Java heap bytes for
Buffa typed/generated scalar ingestion, 272 for generic scalar ingestion, and
4 bytes per retained read including guest arguments and the boxed batch checksum.
They do not change the timed measurement loop.

## Release 0.3.0 measurements

The [0.3.0 run](https://github.com/elide-dev/truffledata/actions/runs/41766750084)
passed all 62 cells on release commit `98a8c6304a40`, using Elide GraalVM
`25.5.5-elide.24`. Each cell has three fresh timing forks and three separate
allocation forks. [Raw JSON](../benchmarks/results/svm-comparison-0.3.0/comparison.json)
and the [full table](../benchmarks/results/svm-comparison-0.3.0/comparison.md) include
interpreter, peak JIT, ingestion, and direct AOT baselines. The
[0.2.0 baseline](../benchmarks/results/svm-comparison-0.2.0/comparison.md) remains available.

![Interpreter reads](images/comparison-interpreter.svg)

![Peak JIT reads](images/comparison-peak.svg)

![Ingestion Java heap allocations](images/comparison-allocations.svg)

Buffa's nested-message projected ingestion uses 1,767.75 Java heap bytes/message,
versus stock's 1,776.75; full consumption uses 1,894.25 versus 1,776.75. These allocation
counts are unchanged from 0.2.0. Projected Buffa ingestion measured 3.88 µs/message
and full consumption 4.65 µs/message in this run. This does not establish a universal
performance win: stock remains faster in several paths, timings come from shared
runners, and Java heap counters exclude native memory. BytesValue ownership differs
between engines; the aliased stock baseline is shown explicitly. SQL cases measure
INT4 decoder functions, not servers, transport, or complete JDBC queries. Stock mixed
message guest reads use the explicitly marked boundary variant.

To regenerate the SVG/PNG graphics and provenance manifest:

```sh
python3 -m venv build/plot-venv
build/plot-venv/bin/pip install -r tools/bench/plot-requirements.txt
build/plot-venv/bin/python tools/bench/plot_comparison.py \
  benchmarks/results/svm-comparison-0.3.0/comparison.json docs/images --label 0.3.0
```

The [manifest](images/comparison-manifest.json) records the source data hash,
measured binary hash, revision, and plotting-library version. PNG copies are next
to the SVG files, and release assets include both formats.


This small suite compares TruffleData with protobuf Java's generated-message APIs and
stock MySQL/Postgres integer decoding. One SVM image includes all engines; each measured
cell runs in a fresh process. The default CI smoke checks correctness, execution tiers,
and allocation accounting. Full measurement is opt-in and needs no database servers,
Docker, JMH, or CodSpeed instrumentation.

```sh
# JVM/native correctness (Bazel builds and runs remotely on Linux amd64).
bazel test //benchmarks/comparison:smoke //benchmarks/comparison:native_smoke

# Three timing and three allocation processes per cell; force fresh measurements.
TRUFFLEDATA_BENCH_REVISION=$(git rev-parse HEAD) bazel test \
  //benchmarks/comparison:measure --nocache_test_results \
  --test_env=TRUFFLEDATA_BENCH_REVISION --test_output=errors
```

The measurement test writes `comparison.json` and `comparison.md` to its Bazel undeclared
outputs (`bazel-testlogs/benchmarks/comparison/measure/test.outputs/`; Bazel may zip them).
The **SVM comparison measurements** workflow runs the same target on demand and uploads
both files. On Linux amd64, a built image can also run directly:

```sh
python3 tools/bench/compare.py run --binary /path/to/truffledata-comparison \
  --revision "$(git rev-parse HEAD)" --output build/comparison
```

## Workloads and boundaries

| Workload | Stock baseline | TruffleData engines | Projection |
|---|---|---|---|
| `proto-scalar` | `Timestamp.parseFrom(byte[])` | owned static contract rows, Buffa scoped typed views | seconds + nanos |
| `proto-bytes` | `BytesValue.parseFrom(byte[])` with default copying, plus `stock-aliased` using immutable ByteString + `enableAliasing(true)` | owned static contract rows, Buffa scoped typed views | length of a 64 KiB payload |
| `proto-mixed` | `Type.parseFrom(byte[])` | owned contract rows, Buffa scoped typed views | UTF-16 name length + nested field count + oneof count + syntax enum |
| `timestamp-json` | reused `JsonFormat.Parser` into a generated Timestamp builder | descriptor-free Java parser, native WKT parser | seconds + nanos |
| `mysql-int4` | Connector/J `MysqlTextValueDecoder.getInt` | the same decoder followed by a static INT32 contract | integer value |
| `postgres-int4` | pgjdbc `ByteConverter.int4` | the same decoder followed by a static INT32 contract | integer value |

There are 32 varying rows/messages per batch. Checksums are independently computed from
fixture values and checked inside each measured batch. Rotation changes traversal order.
Stock SQL retained values are boxed scalar values; TruffleData uses static records.
The stock read loops use concrete generated protobuf getters or unbox retained scalars.

`ingest` includes parse/decode, row materialization, retention, a projected read and scoped
cleanup. Guest compilation is disabled; TruffleData projection uses the uncached data API.
`ingest-typed` adds six measurements using the primitive `DataLibrary.readLong` projection
for owned/Buffa scalar messages and Timestamp JSON, and static MySQL/Postgres integer rows.
The original generic `ingest` measurements remain. Byte-length projections already use the
typed interop length API and receive no duplicate measurement. Stock continues to use its
generated primitive getters. This separates library improvements from the effect of callers
choosing typed reads. Report schema 2 includes these cells; schema 1 remains readable.
Report schema 6 additionally compares our generated contract getters with stock generated
getters. `ingest-generated`, `read-generated-aot`, `interpret-generated`, and
`peak-generated` use the same projections and fixture rotation. The generated accessor
binds to a contract once, uses literal ordinals and primitive return types, and preserves
borrowed-message lifetime checks. These 26 additional cells bring the matrix to 88;
the original library-dispatch measurements remain available. JSON adds ingestion only.

Schema construction, parser construction, source creation and context startup are excluded.
For binary Buffa, source bytes already reside in unmanaged memory and the result borrows
that memory. Stock/owned parsers consume existing Java arrays and own their decoded payloads.
`stock-aliased` instead starts with an immutable ByteString and enables CodedInputStream
aliasing, retaining slices of that input; ByteString creation is setup, like native input
creation. This supplies a closer ownership baseline for Buffa’s borrowed byte views.
This is deliberately a comparison of those public APIs and ownership policies, not an
assertion of equal ownership or a measurement of copying data into native memory.

Retained-read stages exclude ingestion. `interpret` disables Truffle compilation;
`peak` warms and explicitly compiles the call target, requiring valid final-tier code before
and after every sample. Warmup exercises the measurement loop until the compiled target
remains valid, including after initial profile-driven deoptimizations. Performance warnings are errors and the Graal graph is bounded.
`read-aot` remains the direct stock SVM host baseline, excluding guest calls and contract
checks. Report schema 4 additionally runs stock getters in Truffle `interpret` and `peak`
roots with the same batch size, rotation, call arguments, boxed checksum return, warmup,
and tier assertions as TruffleData. These matched guest cases distinguish call/tier costs
from data-access costs; they do not add TruffleData contracts or interop to stock objects.
The direct AOT baseline remains in every new report. Older report schemas remain valid.
For mixed protobuf only, stock stages are labeled `interpret-boundary` and `peak-boundary`:
its collection getters make forbidden JDK methods reachable when exposed directly to guest
compilation ([failed build](https://app.buildbuddy.io/invocation/1914c4d8-1d9d-4b28-a1dc-a01690f88f2a)).
That projection therefore uses one boundary per row; its getter code remains AOT while the
guest loop compiles. Scalar, byte-length, and retained SQL stock getters remain directly
compilable. No blocklist entry or performance-warning check is disabled.
Timestamp JSON is ingestion-only because retained Timestamp reads are already covered
by `proto-scalar`.

MySQL/Postgres cases isolate actual driver decoding functions and retained data access.
They do **not** exercise ResultSet getters, JdbcSource adapters, connections, SQL, network,
or server execution, and cannot support a claim about query throughput. Existing
`//benchmarks/jdbc:drivers_smoke` and the JDBC JMH suite cover real drivers/servers separately.
No dependency upgrades or query-planning functionality are introduced by this suite.

## Measurement and limits

Each of 62 cells has three independent timing processes and three independent allocation
processes (372 short-lived processes). Each process records three samples. Timing uses
100 ms warmup and at least 50 ms per sample. Allocation uses fixed work: eight batches for
ingestion or 128 batches for retained reads, with one warmup batch of the same size.
Results report medians of process medians and the min/max process medians, not confidence
intervals. Job order is deterministically shuffled. Builds are cached; measurements are not.

Allocation uses `ThreadMXBean.getThreadAllocatedBytes`, including on SVM, and fails if an
escaped 1 MiB allocation does not advance the counter. It measures current-thread Java heap
bytes, including call/boxing overhead. It excludes native heap, compiler and background
threads; those allocations are **not zero** and should not be inferred from the chart/table.
This suite reports no RSS metric and establishes no total-memory or zero-allocation claim.

The image uses the pinned GraalVM toolchain with Serial GC, optimization level 2, four build
threads, static Buffa linkage, and the repository's Truffle blocklist checks. Raw results
record tool versions, binary hash, revision, host CPU/kernel, row checksums, backend and
execution tier. Missing samples, wrong backends/tiers and failed calibration invalidate the
run. Short samples on shared RBE hardware are diagnostics for these fixtures, not performance
guarantees; use a quiet pinned host and longer repeated experiments before tuning around
small differences.

## Initial measured baseline

[Raw samples](../benchmarks/results/svm-comparison-fa855f7/comparison.json) and the
[full comparison table](../benchmarks/results/svm-comparison-fa855f7/comparison.md) record
revision `fa855f7`. The 31-cell run took 34.5 seconds inside the remote test; native-image
build time is separate. These are short shared-hardware diagnostic measurements.

| Ingestion/projection | Stock default | Stock aliased | Buffa native |
|---|---:|---:|---:|
| Timestamp JSON | 8.24 µs / 6,440 B | — | 1.59 µs / 984 B |
| 64 KiB bytes | 51.04 µs / 65,699 B | 0.156 µs / 144 B | 0.791 µs / 568 B |
| Scalar protobuf | 0.149 µs / 136 B | — | 1.068 µs / 592 B |

Bytes here are current-thread Java heap allocation, excluding native heap. The aliased
protobuf-Java baseline is faster and allocates less Java heap than Buffa on this byte
fixture; the large advantage over default parsing mostly reflects avoiding payload copies.
Native Timestamp JSON has a clear advantage in this sample. Small scalar ingestion favors
stock generated protobuf. There is no across-the-board performance win.

Peak-JIT TruffleData retained reads were approximately 3.3–5.8× faster than its interpreter
reads across these fixtures. Direct stock AOT reads were still faster. For MySQL/Postgres
INT4 retained data, static TruffleData reads measured about 5.7/6.1 ns per row at peak,
versus 29.2/31.2 ns interpreted and 2.6/3.0 ns for direct stock scalar reads. This isolates
contract/guest-access costs and does not establish database-query performance.

## Decoder optimization checkpoint

Revision `5c7e528` ([samples](../benchmarks/results/svm-comparison-5c7e528/comparison.json),
[table](../benchmarks/results/svm-comparison-5c7e528/comparison.md)) removes eager unknown-field
buffers, shares empty unknown-field storage, and uses a bounded dense prefix for field-number
lookup. Sparse field numbers still use the map; the dense prefix is bounded by schema size
and capped at 4096 entries.

Owned scalar ingestion dropped from 616 to 496 Java heap bytes/message. In this run it took
490 ns/message versus stock's 99 ns/message, with stock allocating 136 bytes. Stock timings
also changed substantially from the initial run, so the absolute timing change between runs
must not be attributed entirely to these edits. This checkpoint does not meet stock parity.

A separate experiment made the generated static storage subclass the row itself. It passed
correctness and comparison smoke tests but failed the existing mixed-representation allocation
gate: four receivers caused 12 bytes/read instead of the allowed 0.05 bytes/read. Distinct
generated receiver classes saturated library dispatch earlier. The experiment was reverted;
the allocation gate and dispatch limits were not relaxed. Any future combined row/storage
representation must preserve polymorphic dispatch behavior as well as improve ingestion.

Revision `e194e54` ([samples](../benchmarks/results/svm-comparison-e194e54/comparison.json),
[table](../benchmarks/results/svm-comparison-e194e54/comparison.md)) additionally reuses
compiled field contracts and immutable empty native-view fields, and avoids slicing a freshly
wrapped byte array. Java heap allocation changes from the initial baseline are:

| Ingestion | Initial B/message | Updated B/message |
|---|---:|---:|
| Owned scalar | 616 | 448 |
| Buffa scalar | 592 | 400 |
| Buffa borrowed bytes | 568 | 392 |
| Buffa Timestamp JSON | 984 | 792 |

All original comparison cells and stock baselines remain. Core/protobuf regressions, Buffa
JVM acceptance, SVM smoke, and the full 31-cell measurement passed. Timing remains noisy:
owned scalar took 679 ns versus stock's 167 ns in this run; Buffa took 1012 ns. Allocation
improvements do not establish timing parity or superiority, and exclude native heap usage.

## Compact scalar records

Revision `71b4c16` ([samples](../benchmarks/results/svm-comparison-71b4c16/comparison.json))
adds compact storage and bounded direct decoding for one or two implicit-presence INT32/INT64
fields with zero defaults and single-byte tags. This applies by contract shape, not message
name. Unknown/mismatched fields and noncanonical tags fall back to the lossless decoder;
malformed values retain the existing rejection behavior. The generic ByteBuffer route is
covered as a differential oracle alongside generated protobuf Java messages.

Revision `0945211` ([samples](../benchmarks/results/svm-comparison-0945211/comparison.json),
[table](../benchmarks/results/svm-comparison-0945211/comparison.md)) moves compact reads into
Truffle specializations before delegated-library initialization. The unchanged generic
projection benchmark measured scalar ingestion at 80.59 ns and 88 Java heap bytes/message,
versus stock's 66.98 ns and 136 bytes. Retained peak reads measured 2.20 ns/row versus the direct
stock AOT lower bound of 1.39 ns/row. These results establish an allocation win for this case,
not a latency win. Other cells remain mixed and shared-host timings vary substantially.

The 78-test regression suite, compiled allocation tests, JIT matrix, Buffa JVM acceptance,
SVM smoke, and the full comparison run passed during these two revisions. No allocation or
JIT budgets were relaxed. The row class remains uniform across compact and general messages
so the previous generated-class dispatch regression is not reintroduced.

Revision `3507913` ([samples](../benchmarks/results/svm-comparison-3507913/comparison.json),
[table](../benchmarks/results/svm-comparison-3507913/comparison.md)) adds the six typed-ingestion
cells while retaining all 31 previous cells. Owned scalar ingestion measured 73.48 ns and
56 Java heap bytes with primitive projection, 75.93 ns and 88 bytes with generic projection,
and stock measured 64.17 ns and 136 bytes. Both TruffleData routes win allocation in this
fixture; neither establishes a latency win. The 37-cell SVM run and JVM/native smoke passed.

### Scoped lifetime and binary bookkeeping allocations

The `8696d83` and `8c3fbec` reports retain the complete 37-cell matrix. Cached
retained owners acquire a lifetime assumption before compilation; uncached and
megamorphic reads use ordinary lifetime checks. Close invalidates cached owners,
and the acceptance corpus checks constant receivers and cache saturation on JVM,
static SVM, and the SVM Java fallback. Binary decoding also avoids text-only
field tracking, and ordinary repeated fields no longer allocate a map.

Buffa scalar ingestion fell from 400 to 312 Java heap bytes per message, binary
bytes ingestion from 392 to 304, and Timestamp JSON from 792 to 720. These are
Java heap savings, not total native-plus-Java allocation figures. Timing varies
substantially between the short shared-hardware runs; stock aliased bytes and
stock driver decoding still lead. The native smoke enforces compact owned scalar
allocation budgets of 96 B for generic and 64 B for primitive projections.

The mixed binary fixture uses the ordinary-schema `google.protobuf.Type` message
with a Unicode name, 4–8 repeated nested field records, two oneof names, an enum,
and a nested source context. It uses the existing generated protobuf-java classes
without another Java code-generation toolchain. Report schema 3 adds ten cells;
the validator continues to accept complete schema 1 and 2 reports.

### Mixed-message materialization

Reports `fc91750` and `7285095` isolate empty repeated-field storage and UTF-8
materialization changes. Shared immutable defaults and lazy repeated-field lists
reduce owned mixed-message allocation from 3,847.50 to 3,562.50 B/message and
Buffa from 4,204.50 to 3,983.50 B/message. Avoiding sliced buffers, a charset
decoder, and an intermediate character buffer then reduces Buffa to 2,195.25 B.
The native parser still validates UTF-8 and budgets; Java materialization retains
bounds and lease checks. Empty borrowed arrays retain owner lifetime enforcement.
Stock remains lower at 1,776.75 B/message and faster at ingestion. Timings vary
across these short shared-machine runs, so allocation savings are the stronger
before/after evidence.

### Optional SVM profiling

Use `bazel test //benchmarks/comparison:profile --nocache_test_results` to capture
three fresh JFR processes per engine for mixed-message ingestion. Each process
runs eight diagnostic rounds with Serial GC. Use the separate
`//benchmarks/comparison:profile_epsilon` target for a no-GC probe with one round
per process and a 1 GiB heap cap. Both targets also collect matched one-round
fresh-process timings with recording off (`*.unprofiled.jsonl`); `gc.txt` labels
the collector. These ingestion-only diagnostics supplement the full comparison.

This toolchain rejects signal-handler sampling when Truffle runtime code installation
is enabled. The recordings therefore use the safepoint sampler: distributions are
biased toward safepoint locations and are clues for experiments, not unbiased CPU
percentages. Runtime code installation and normal peak-JIT checks remain enabled. Select another
workload with `--test_arg=proto-scalar` (or another comparison workload). Raw
recordings and diagnostic output are under the test's `test.outputs` directory.
Pass `--test_env=TRUFFLEDATA_BENCH_REVISION` with the current commit in the environment
to preserve revision provenance; the binary SHA-256 is recorded automatically.

The separate manual `svm_profile` image enables
[GraalVM JFR](https://www.graalvm.org/latest/reference-manual/native-image/debugging-and-diagnostics/JFR/).
Normal comparison images and scores remain uninstrumented. The custom configuration
collects CPU and sampled Java allocations; profile timings are diagnostic, and
allocation samples are not exact byte counters or native allocation measurements.
Use `jfr print --json --stack-depth 128 --events jdk.ExecutionSample,jdk.ObjectAllocationSample FILE.jfr`
to inspect a recording, filtering stacks to `Comparison$Fixture.ingest` when
attributing ingestion costs rather than setup. Native Rust internals may not be
resolved by the Java stack sampler.

The first paired GC probe is recorded in `benchmarks/results/svm-gc-probe-ed52cde`.
Both targets pass. Recording-off median ingestion times (Serial / Epsilon) were
1.64 / 24.08 µs for stock, 17.51 / 16.93 µs for owned, and 5.25 / 5.81 µs for Buffa.
Stock's Serial forks ranged from 0.99 to 24.48 µs, so this is diagnostic evidence,
not an isolated measurement of collector pause cost or a production win under Epsilon.
The profiler cannot provide unbiased CPU percentages with this Truffle runtime.

Run `python3 tools/bench/profile_summary.py RECORDING_DIRECTORY` to reproduce
summaries with source recording hashes, sample counts, ingestion filtering, and a
safepoint-dominance flag. The raw JFR files remain test artifacts in the linked
BuildBuddy invocation; the repository stores summaries and full recording-off timing
samples. No recording's absence of allocation samples is interpreted as zero allocation.

Report schema 5 adds `proto-mixed-full` ingestion for stock, owned, and Buffa. It uses the
same mixed messages but consumes every populated field, including all nested field names,
numbers, kinds, cardinalities, packed flags, oneof strings, and the source filename. This
checks the materialization cost when consumers read the whole message. Compiled-plan setup
and warmup remain excluded; adaptive native string hints have seen the projection before
measured samples. It is not a first-message or cold-plan measurement. Existing schemas and
all prior comparison cells remain supported.

## Development: generated records and parse/project fusion

Schema 7 adds generated owned scalar records and matching binary serialization cases.
Schema 8 adds scalar parse-and-project without retaining the message, but used an explicit
boundary for stock parsing. Schema 9 removes that stock parser boundary: both engines can
inline parsing and projection into the guest compilation. Only invalid-input exception
construction crosses a cold boundary. The isolated `//tests/jit-negative:stock_parse_test`
probe verifies that stock parsing actually executes at the last compiled tier in SVM with
fatal performance warnings and a 10,000 graph-size budget. The full SVM comparison uses
that same budget and requires every peak cell to compile. On HotSpot, the full stock
parse/project root exceeded even an experimental 200,000 budget. The JVM smoke test reports
that specific graph-size bailout as an unsupported capability; it does not substitute a
boundary score. Other failures still fail the test, and SVM does not permit this exception.
Do not interpret schema 8 results as stock's best fused JIT result.

Generated records and generic owned rows are separate engines. Buffa's binary scalar path
uses a fixed native output slot and has a dedicated zero-native-allocation test. Benchmark
Java allocation counters still exclude native allocations in other workloads; they do not
imply that all Buffa ingestion is allocation-free.

Schema 10 gives concrete generated records the same direct getter loops as stock records
for retained AOT/interpreter/JIT reads, without the generic projection adapter used in
schemas 7–9. Schema-bound generic and borrowed getter measurements remain separate cases.
Removing an explicit parser boundary allows inlining; a compiled root alone does not prove
that every stock parser helper was inlined. The measurements report the resulting operation,
including any calls the SVM compiler elects to retain.

## Measured independent-runtime development snapshot

Revision `4b4661c` is unreleased. The [first run](../benchmarks/results/svm-comparison-4b4661c/comparison.json)
and [one repeat](../benchmarks/results/svm-comparison-4b4661c-repeat/comparison.json) each cover
111 cells with three timing processes and three separate allocation processes per cell.
The repeat followed concurrent CI work; timing remained variable, so both runs are retained.
Charts below use the first run consistently, rather than selecting the better result per case.

| Scalar operation | Stock ns, first / repeat | Generated record ns, first / repeat | Stock / generated Java bytes |
| --- | ---: | ---: | ---: |
| Retained ingestion | 92.47 / 105.07 | 33.29 / 33.88 | 136 / 80 |
| Interpreter reads | 2.83 / 4.38 | 1.64 / 2.13 | 2 / 2 |
| Peak reads | 1.18 / 1.52 | 1.98 / 1.55 | 1 / 1 |
| Binary serialization | 22.28 / 27.14 | 39.81 / 38.98 | 56 / 40 |
| Fused parse/project, peak | 80.96 / 64.62 | 14.28 / 13.45 | 137 / 1 |

The generated records improve ingestion and whole-operation projection here; they do not
win every operation. Stock still leads scalar serialization and general nested ingestion.
The small retained-read differences should be read with the process ranges, not as a
universal ranking. Parse/project heap counts include guest-call overhead; the separate
compiled allocation test records 32 bytes across 100,000 generated projections.

Buffa scalar ingestion now uses **112 Java bytes**, or **80** with generated access, compared
with **240 / 208** at `7b4eb4a`. Both current runs report those same allocation counts.
The fixed scalar native decoder also passes an allocator-instrumented zero-allocation test.
Other Buffa shapes may still allocate a native index; Java heap counters do not include it.
Native input/schema and reusable decoder scratch are not per-message index allocations.

Generic 64 KiB byte serialization now uses a bulk copy: approximately 5.3 / 6.7 µs in the two
current runs, versus approximately 417 µs in the earlier `7b4eb4a` run. Large byte timings
have wide ranges on shared runners. Binary Buffa serialization forwards input, whereas
owned engines serialize message storage; these remain explicitly different operations.

![Development interpreter reads](images/independent/comparison-interpreter.svg)
![Development peak reads](images/independent/comparison-peak.svg)
![Development ingestion allocations](images/independent/comparison-allocations.svg)
![Development parse/project peak](images/independent/comparison-projection-peak.svg)
![Development binary serialization](images/independent/comparison-serialization.svg)

The [chart manifest](images/independent/comparison-manifest.json) records the source-data
hash, executable hash and implementation revision. The [validation and measurement run](https://app.buildbuddy.io/invocation/6156aff1-0dc2-4795-a235-80cbc6764654)
passed native/fallback tests, both published native consumers, comparison smoke and the full
matrix; the [repeat](https://app.buildbuddy.io/invocation/591882d2-8724-4140-ae77-4373b7b52dfe)
uses the same executable revision. Release 0.4.0 charts above remain unchanged.

## Generated graph decoding and scalar serialization

The unreleased [`9e0736a` run](../benchmarks/results/svm-comparison-9e0736a/comparison.json)
adds schema 11: 120 cells, including concrete generated nested/repeated decoding, full
consumption of the mixed message, and matched mixed-message serialization for every engine.
Each cell has three timing forks and three separate allocation forks. This is one complete
run on shared hardware; the process ranges overlap in some cases, so the small differences
need repeat measurements. Earlier raw results and charts remain available above.

| Operation | Stock | Generated owned | Stock / generated Java bytes |
| --- | ---: | ---: | ---: |
| Scalar ingestion | 95.90 ns | 39.40 ns | 136 / 88 |
| Scalar interpreter reads | 2.71 ns | 2.03 ns | 2 / 2 |
| Scalar peak reads | 1.83 ns | 1.80 ns | 1 / 1 |
| Scalar serialization | 33.44 ns | 21.93 ns | 56 / 24 |
| Scalar parse/project, peak | 67.97 ns | 13.81 ns | 137 / 1 |
| Nested ingestion, full consumption | 1.30 µs | 1.08 µs | 1776.75 / 1895 |
| Nested serialization | 0.96 µs | 4.07 µs | 436.50 / 1130.50 |

Concrete generated decoders remove the generic row construction path for nested/repeated
messages. In this run, generic owned full-consumption ingestion takes 17.67 µs and Buffa
takes 7.24 µs; the generated path takes 1.08 µs. It is close to stock in time, but allocates
6.7% more Java heap. It does not establish a win for generic decoding or borrowed views.
Nested generated records still use the generic serializer, which remains a clear gap.

The specialized scalar serializer now caches encoded size and writes directly into an
exact-size output array. Serialization allocation falls from 40 to 24 bytes per message.
The size cache adds eight bytes to the retained scalar record on this SVM layout, so
scalar ingestion rises from 80 to 88 bytes. Peak retained reads are effectively tied.

[Validation and measurement](https://app.buildbuddy.io/invocation/0b656af7-5f01-4e01-9c2e-371b437dec75)
passes JVM tests, comparison smoke, the 120-cell SVM measurement, and the full native
access matrix (511 cases). Generated guest reads pass fatal performance warnings,
blocklist checks, the 10,000 graph-size budget, and the retained-read allocation gate.
Shape-specific comparison roots and class-profiled interface receivers are required for
SVM compilation; the failed preceding run is not presented as a completed matrix.
The separate protobuf-free native consumer also passed before this measurement.

SQL decoder wrappers and stock's aliased-byte decoder still expose overhead to address;
the objective of matching stock across the supported contexts is not complete. Native
allocation remains outside the Java heap counters. See the raw report for every case,
process range, and borrowing/copying distinction.

The next [`2fae8de` run](../benchmarks/results/svm-comparison-2fae8de/comparison.json)
keeps the same 120 cells and adds direct generated graph serializers and narrow decode-state
fields. Generated nested serialization moves from 4.07 µs / 1130.50 bytes to 1.45 µs /
192.50 bytes. Stock in that run measures 0.67 µs / 436.50 bytes: the generated writer now
allocates less, but is still slower. Nested decode allocation drops from 1895 to 1800.50
bytes, versus stock's 1776.75. Full-consumption generated decoding measures 0.97 µs versus
stock's 1.15 µs, with overlapping process ranges. Generic serialization also avoids temporary
UTF-8 arrays; its nested serialization allocation falls from 1130.50 to 556 bytes.
[The complete native run](https://app.buildbuddy.io/invocation/d7d680e7-ccd7-4b6c-b5e0-a06e0a6fa1a4)
passes the comparison, both native access gates, and the protobuf-free native consumer.
