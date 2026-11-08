# SVM tranche — 2026-11-08

Production baseline: `9a1dcec` (restored B4 plus integrated JSON and slab work).
SVM is the production performance target; JVM correctness and coverage remain
required, while JVM timings do not veto an SVM gain. The measurements below were
completed locally on `feat/followup-v5` before release authorization.

All three mechanisms are admitted locally. Combined coverage and native
acceptance on Linux x86-64 and Arm64 pass. The tranche is complete. Sam subsequently
authorized 0.10.0 for the TruffleCEL and Elide 1.6.3 integrations, deferring the
remaining performance goals; release qualification remains separate.
The performance evidence below is from isolated, preserved images, not a
fresh timing capture of one combined application. The combined branch receives
its own semantic, coverage and native acceptance checks.

## Performance against the baseline and other implementations

[Rendered charts and regeneration](../../../docs/COMPARISON_BENCHMARKS.md#release-0100-paired-svm-charts)
cover the JSON typed/control and generic comparisons, protobuf warm/cold and
decode/ownership cases, and CSV retained-string fixtures. The
[chart manifest](../../../docs/images/svm-tranche-20261108/comparison-manifest.json)
links the exact input reports, image hashes and reproducible SVG/PNG artifacts.

These are three-fork paired Linux x86-64 SVM measurements using the pinned SDK,
O2, serial GC and no PGO. Lower ns is better; higher GB/s is better. Compare
implementations within a row's workload, not between different format fixtures.
Full fork ranges, raw samples and source/image hashes remain in the format reports.

| Workload | Our baseline → candidate | Current comparison | Interpretation |
|---|---:|---|---|
| JSON nested typed ingest | 829.10 → 700.30 ns/document | No matched external typed-schema cell | 15.5% lower time |
| JSON repeated typed ingest | 4,209.47 → 3,639.22 ns/document | No matched external typed-schema cell | 13.5% lower time |
| JSON mixed/churn typed ingest | 1,677.52 → 1,517.45 / 2,639.50 → 2,286.20 ns/document | No matched external typed-schema cell | 9.5% / 13.4% lower time |
| JSON generic ingest | 866.28 → 885.30 ns/document | fastjson2 650.56; DSL-JSON 801.91; jsoniter 997.10; Jackson 1,305.22 | 2.2% regression; behind fastjson2 and DSL-JSON, ahead of jsoniter and Jackson |
| JSON generic compiled reads | 29.01 → 28.79 ns/document | Jackson 66.99; fastjson2 85.71; DSL-JSON 89.90; jsoniter 115.95 | About 2.3× Jackson and 3.0× fastjson2 throughput |
| Protobuf full slab interpreter reads | 418.93 → 351.17 ns/row | Stock 62.14; stock guest interop 1,783.76; our generated-owned 215.66 | 16.2% lower time; still 5.652× direct stock |
| Protobuf full slab peak reads | 310.53 → 210.80 ns/row | Stock 105.37; stock guest interop 3,084.38; our generated-owned 48.48 | 32.1% lower time; still 2.001× direct stock |
| Protobuf sparse slab interpreter / peak | 52.87 → 44.07 / 30.05 → 21.13 ns/row | Stock 10.74 / 11.77 | 16.6% / 29.7% lower time |
| Protobuf full generated-owned peak reads | 48.61 → 48.48 ns/row | Stock 105.37 | Stable; about 2.17× stock throughput |
| Protobuf full slab decode/release | 978.41 → 972.15 ns/row | Stock 790.84; our generated-owned 825.24; scoped slab session 783.23 | Overlapping ranges; owning slab remains 1.23× stock time |
| Protobuf full slab resident decode/read/release | 1,276.69 → 1,311.06 ns/row | Stock 861.75; our generated-owned 857.27 | +2.7% with overlapping, noisy ranges; still 1.52× stock time |
| CSV mixed 1 MiB, typed/native retained batch | 0.4645 → 0.4774 GB/s | FastCSV 0.1696; kernel-native 0.8217 | 2.8× FastCSV; below the kernel consumer |
| CSV mixed 1 MiB, copied strings/native | 0.2683 → 0.2740 GB/s | FastCSV 0.3013; Jackson 0.2207; kernel-native 0.4336 | Below FastCSV, above Jackson |
| CSV mixed 1 MiB, owned strings/native | 0.3088 → 0.3206 GB/s | Same string consumers as above; different input ownership | Owned API predates this tranche |

JSON's four new typed ingest workloads have separated baseline/candidate fork
ranges. All 34 Java allocation cells match; typed read changes stay within 1.6%.
Generic ingest's +2.2% regression has separated ranges and remains disclosed.
Flat typed ingest is +1.5% with overlapping ranges. Gson's noisy ingest controls
are retained in the [JSON admission report](../json-typed-tranche-20261108/admission.md),
not used to normalize other results. Generic JSON ingest allocates 384 Java B/doc
(144 library plus 240 read-harness), versus fastjson2 1,372 and Jackson 2,245;
this excludes native input/index storage and is not a total retained-memory comparison.

All four primary protobuf warm slab comparisons have separated fork ranges.
Large native slab peak time improves 44.6%. All 132 allocation/retention pairs
match. Separate native allocation gates retain the 168/136 B scalar floors and
48 B owner.
There are cold costs: native and lazy sparse first reads regress about 4.5% and
3.3% with separated ranges. Projected sparse first read is +2.4% with overlap,
improved from the first candidate's +8.7% separated-range penalty. Resident/decode
controls and all noisy forks remain in the
[complete protobuf report](../protobuf-warm-cache-9a1dcec/README.md). Sparse stock
interpreter has a large outlier, so its ratio needs the recorded ranges.
Stock is AOT Java generated protobuf getters; the interpreter rows include guest
node dispatch. Stock guest interop is a separate adapter over those protobuf
objects and is slower than both library representations in these read cells.
The B4 full interpreter target of ≤2.5× direct stock remains unmet and deferred
from 0.10.0 for both slab (5.652×) and generated-owned (3.471×). Restored B4 Phase 1 remains in
the baseline; this tranche does not attribute its earlier gain to cache changes.
Warm-read gains do not imply equivalent decode or whole-pipeline gains. Full slab
decode allocates 808 Java B/row versus stock 1,629.5; scoped sessions allocate
40 Java B/row in this harness and separately retain an 855.6 B native arena.
These allocation/ownership differences predate this cache change. Native transient
allocation peaks are not measured. Full slab resident candidate ranges include
a retained 17.4 µs outlier; the three-fork median alone does not establish a regression.

Across all eight CSV string fixture/size cells, copied/owned and Java-SWAR/native
aggregates improve by about 1.5–2.0% in elapsed time. This is incremental, not a
step-change. Every allocation cell matches; retained-read ranges overlap and
graph/allocation gates pass unchanged. Large allocating outliers remain included.
CSV keeps a retained batch, while reference consumers have different retention
contracts. `kernel-native` is a Java consumer over native indexes, not a native
typed materializer. Its throughput is an opportunity bound, not an equivalent
retained-batch result. The runtime selected AVX-512 indexing on the paired hosts;
these numbers establish neither Arm nor AVX2-only performance.
[Complete CSV report](../csv-span-only-20261108/README.md).

## Scope and remaining work

The JSON change extends structural typed decoding to nested/repeated plans with
bounded frames and failure-only diagnostic replay. CSV selects an all-STRING
span writer once per scan. Protobuf publishes row/text/blob handle-cache entries
with acquire/release access and keeps synchronized miss rechecks; list/unsigned
maps stay synchronized. Layout/lifetime checks and the ABI are unchanged.
More typed CSV specialization, JSON snapshot work,
protobuf list indexing and bulk scoped decode remain separate future experiments.
The pre-existing slab verification-complexity risk and full distribution/release
qualification are not closed by this tranche. JSON throughput/memory goals and
YAML performance floors are explicitly deferred from 0.10.0 below, with their
misses preserved. No allocation/graph budgets or coverage exemptions are relaxed.

## 0.10.0 release disposition

Sam authorized the 0.10.0 release on 2026-11-08 to unblock TruffleCEL and Elide
1.6.3. Remaining performance goals and standalone Buffa Cargo completion are
deferred from this Maven/native release, not declared achieved. Future experiments
carry no delivery commitment or promised gain. This tranche is not full release
qualification; the current
[release ledger](../../../docs/superpowers/plans/2026-11-08-release-resume.md)
tracks safety remediation, normal CI and staged/public artifact checks. Recent
native and coverage evidence is reused rather than repeating the benchmark matrix.

- **Safety:** remediation of shared-subgraph verification CPU amplification is
  tracked separately in release work; performance deferral does not waive it. This
  report's benchmark candidates do not establish that a later fix is qualified.
- **Cargo:** Buffa's standalone generated-seam packaging is unfinished and
  excluded from 0.10.0's Maven/native scope. JSON/CSV crates already exist, but
  composed-link automation and the full Cargo matrix remain separate work. No
  standalone Buffa Cargo readiness is claimed.
- **Protobuf performance:** the unmet SVM B4 interpreter ceiling and remaining
  sparse decode/cold costs are deferred. List indexing, dispatch specialization
  and opt-in bulk scoped decode are possible experiments. JVM timing-only
  objections are superseded by the user's production priority.
- **JSON:** the last qualifying pinned native producer capture misses retained
  big/huge and validate small/huge floors; current-source recapture remains.
  The 1.5× logical index/input memory criterion remains structurally unmet.
  Duplicate-check price needs pinned qualification; broader end-to-end targets
  also need current matched-host measurements. These performance goals are deferred
  from 0.10.0; snapshot specialization and layout changes remain hypotheses.
  Existing correctness, graph and allocation failures recorded earlier are closed.
- **YAML:** four native decoder floors remain missed (config, block, flow,
  text). These are C++ session-decode/reset microbenchmarks against a rapidyaml
  scan handler, not JVM or end-to-end SVM figures. The stream floor and SVM
  graph/allocation gates pass. The four misses and full comparison are deferred;
  inspecting literal-filter code generation is a hypothesis, not a promised fix.
  Native/sanitizer evidence and packaged-artifact checks remain distinct from timing.
- **CSV:** no unmet mandatory throughput floor was identified. Original H1–H5
  hypotheses explicitly allow either measurement outcome without blocking
  release; the required semantic, coverage, graph and allocation gates pass.
  Short-string/numeric gaps and host-fault sensitivity remain measured follow-ons;
  typed specialization, direct sinks and column growth are unmeasured options.
- **Distribution:** the final platform staging, packaged consumers, signatures/
  metadata and normal green CI/main checks remain release work. The older
  0.9.0 cross-platform fingerprint blocker was already resolved; it is not
  counted again. Consult the release ledger for actual completion/publication status.

## Integrated qualification

Production/test integration is `7fca64b`: CSV `fef9ae5` plus large acceptance
`2267344`; JSON `152dac5` plus Cargo synchronization `de07d75`; protobuf
`de2e498`, `598ce6f`, `4fd49c2`, `7fca64b`. The three worker branches and both
protobuf candidate images remain available. The original checkout stays on
`main` at `223edf7`.

- Combined coverage `b436036f-46bf-4662-b8ff-c9d65a474d7e`: all nine targets pass,
  455 tests with no failures/errors/skips (eight targets executed, tape cached).
  LCOV validates 201 production files across 12 modules; the access-method
  contract passes without new exclusions.
  [Evidence and source/SDK hashes](final-coverage/README.md).
- Reporting-tool tests: 14 pass; paired CSV harness integrity tests: four pass.
  [Logs](report-tests/).
- Combined Linux x86-64 native acceptance passes all five targets in
  `6227eb3c-34a5-4bd0-9c95-37f33ca1b1b7`: CSV, JSON, protobuf strict/non-strict,
  and protobuf Java fallback. Shared publication races pass in all three
  protobuf images. [Manifest and original logs](final-native-x86/manifest.json).
- Arm64 passes the same five targets in `0de92e3b-a02f-4532-ac98-eed245afec71`,
  including all three protobuf publication races. The fresh toolchain/dependency
  and image build took 2,268.607 seconds; the final tests took 9.5–18.2 seconds.
  [Manifest and original logs](final-native-arm64/manifest.json). Both ISA archives
  have verified hashes and identical production source hashes. These tests
  exercise semantic, ownership/lifetime and compiled-graph contracts; they do
  not measure Arm throughput.

All workers are finished. The Arm Bazel server was shut down after archiving;
its separate case-sensitive cache and all preserved benchmark images remain
available. At the measurement checkpoint the integration branch was committed
locally; nothing had been pushed, dispatched to CI, merged remotely or published.
The subsequent release authorization supersedes that pause for release work,
without authorizing another performance cycle or changing these recorded results.

## Preserved baselines

`baseline/` retains binary SHA-256 manifests and compressed build logs for:

- `bad1392b-f949-4900-8507-9832f2112339`: original consumer, profiling consumer,
  JSON and CSV portable SVM images from `9a1dcec` production/harness.
- `8bad3434-d179-4ab9-9d0f-56a1d1d1822c`: expanded JSON/CSV workloads at harness
  `d3f40cf`, still `9a1dcec` production. CSV adds owned-input string batches;
  JSON adds nested/repeated/mixed/churn typed documents. This invocation also
  passed the restored protobuf interpreter profile test.

Both builds use the repository's pinned `25.5.5-elide.27` SDK, Linux x86-64,
`-march=compatibility`, `-O2`, and no PGO. The vendor banner is
`GraalVM CE 25.5.5-dev+1.1`, Java `25.0.4.1.1+1`.
The local executables remain under ignored `build/svm-tranche-baseline/` in the
integration checkout. Candidate pairs must copy these artifacts, not rebuild an
approximation of the old production source with different workloads.

New CSV and JSON paired runners interleave three fresh forks of each image on a
single pinned logical CPU per shard, verify result checksums, and retain raw
samples and timing/allocation ranges. Separate shards are separate same-host
comparisons; their hosts may differ. Measurements remain subject to shared-host
noise. Instruction/sample profiles are diagnosis, not elapsed-time speedups.
