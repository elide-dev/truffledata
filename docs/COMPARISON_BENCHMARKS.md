# SVM comparison benchmarks

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
`read-aot` is the stock SVM host baseline: stock Java APIs do not acquire a Truffle execution
tier. These direct host getters also exclude guest interop and contract enforcement.
Timestamp JSON is ingestion-only because retained Timestamp reads are already covered
by `proto-scalar`.

MySQL/Postgres cases isolate actual driver decoding functions and retained data access.
They do **not** exercise ResultSet getters, JdbcSource adapters, connections, SQL, network,
or server execution, and cannot support a claim about query throughput. Existing
`//benchmarks/jdbc:drivers_smoke` and the JDBC JMH suite cover real drivers/servers separately.
No dependency upgrades or query-planning functionality are introduced by this suite.

## Measurement and limits

Each of 47 cells has three independent timing processes and three independent allocation
processes (222 short-lived processes). Each process records three samples. Timing uses
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
