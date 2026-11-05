# Scalar JSON comparison

Run `bazel test //benchmarks/comparison:json_native_smoke` for correctness and tier
checks; run `bazel test //benchmarks/comparison:json_measure` for three fresh forks
per timing/allocation cell. Reports are in that test's undeclared outputs. The
reusable native CI job runs the smoke target. No Jackson or protobuf dependency
is needed by this separate JSON executable.

The fixture contains 32 varying UTF-8 objects with shuffled keys and three required
non-null fields: long, double, boolean. Native simdjson decodes through a reusable
`JsonScalarPlan`. Stock uses a hand-written Gson streaming decoder from UTF-8 bytes
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
