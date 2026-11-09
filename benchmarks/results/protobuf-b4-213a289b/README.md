# B4 exact-layout guards and bound hooks — admitted for SVM (2026-11-08)

The user explicitly clarified that **SVM is the primary performance target and JVM is a
correctness/testing target**. This supersedes the earlier decision to reject Phase 1 for JVM
timing regressions. The exact production delta measured as `38f7399` is reinstated on the combined
branch, together with its API-specific tests. SVM generated full interpretation improves 17.7%;
SVM full/sparse peak ranges overlap. The protected Accessor constructor and bound hooks now ship.
B4 remains open: neither candidate SVM full interpreter ratio meets the required 2.5× stock ceiling.

The JVM regressions remain recorded below and are not silently erased or relabeled as wins.
JVM correctness, lifetime checks and coverage remain required. No new Phase 2 optimization or
JVM timing run was performed for this change in priority.

## Complete paired evidence

Baseline is exact `1cfcb37`, candidate is `38f7399`, measurement harness is `6edd5e3`.
Both use GraalVM 25.5.5-elide.27; no PGO. These images predate the separate slab allocation
work integrated after this experiment. The baseline and Phase 1 candidate must not be
presented as a fresh measurement of the final combined branch.

- [SVM complete report](svm/merged/results.md), [raw/summary JSON](svm/merged/results.json):
  invocation [213a289b](https://app.buildbuddy.io/invocation/213a289b-9dd7-4e56-800b-2f4b895915f3).
- [JVM complete report](jvm/merged/results.md), [raw/summary JSON](jvm/merged/results.json):
  invocation [d12b5d0b](https://app.buildbuddy.io/invocation/d12b5d0b-3b84-4745-8afc-699c25eeb16f).
- Each runtime: all ten shards, **195 summary cells / 1,170 raw records**, merged with
  `complete: true`. Every shard retains results, test log and XML.
- [Assessment](assessment.json) includes full/sparse interpreter, peak, decode and resident
  timings, within-binary stock ratios, before/after ratios and observed fork ranges.
- [Original patch](rejected-phase1.patch.gz) includes the implementation and API-specific tests.
  Its historical filename records the initial rejection; the patch is now reinstated. Commits
  `d182a5d` and `38f7399` remain in branch history.

All figures below are ns/row, lower is better. Brackets are observed ranges of three fresh
fork medians, not confidence intervals. Baseline/candidate and every engine in a `(mode, stage)`
group execute on the same worker, interleaved and pinned to one CPU. Shared-host timings remain
advisory. Stock controls can vary between forks; raw ns and denominator ranges remain available.

| Full workload | Baseline | Phase 1 candidate | Candidate / baseline |
|---|---:|---:|---:|
| SVM generated interpreter | 259.36 [259.17, 259.82] | 213.56 [212.55, 214.60] | 0.823× |
| SVM slab interpreter | 415.16 [413.38, 415.26] | 410.13 [408.92, 412.43] | 0.988× |
| JVM generated interpreter | 50.07 [49.95, 50.70] | 60.17 [59.31, 62.76] | **1.202×** |
| JVM slab interpreter | 216.59 [204.73, 250.09] | 215.40 [212.32, 246.63] | 0.995× |
| SVM generated peak | 49.22 [48.99, 49.68] | 50.17 [49.37, 50.49] | 1.019× |
| SVM slab peak | 312.70 [311.75, 312.78] | 311.65 [310.58, 314.14] | 0.997× |
| JVM generated peak | 20.01 [19.98, 21.32] | 17.33 [17.26, 18.18] | 0.866× |
| JVM slab peak | 130.07 [130.06, 131.52] | 131.40 [130.57, 131.96] | 1.010× |
| JVM slab resident | 2116.13 [1050.14, 2274.72] | 2977.25 [2798.34, 3397.35] | **1.407×** |

SVM full interpreter/stock: generated 4.229× → 3.501×; slab 6.769× → 6.724×.
Sparse generated interpreter improves 36.36 → 31.83 ns (0.876×); slab 52.64 → 52.29 ns
(0.993×). The SVM gain exceeds the design's 5% minimum for generated rows, while peak ranges
overlap on SVM and generated peak improves on JVM. The initial decision rejected the candidate
because JVM generated full interpreter is 20.2% slower and slab full resident 40.7% slower, both
with nonoverlapping observed ranges. The user's subsequent SVM priority supersedes that rejection;
these JVM timings are now non-gating observations. No numeric threshold is relaxed.

SVM peak inspection: full generated 49.22 [48.99, 49.68] → 50.17 [49.37, 50.49] ns (+1.92%);
sparse generated 9.802 [9.681, 9.815] → 9.840 [9.800, 9.911] ns (+0.39%); full slab
312.70 [311.75, 312.78] → 311.65 [310.58, 314.14] ns (−0.33%); sparse slab
29.553 [29.543, 30.006] → 29.616 [29.422, 29.623] ns (+0.21%). All observed ranges overlap.
There is no demonstrated SVM peak regression under the design's fork-range criterion; these
measurements do not prove exact equality. Phase 1 is admitted as an incremental SVM win, while
B4's absolute 2.5× target and subsequent Phase 2 work remain open.

## Correctness and restoration

Candidate JVM/native consumer smoke tests both pass all 132 cells, including AST and Graal graph
limits, in [7f8ce7c9](https://app.buildbuddy.io/invocation/7f8ce7c9-b33f-4861-88e2-fa8ab0ced019).
Their logs are under `correctness/`. The inherited candidate allocation-suite evidence
[9a78db31](https://app.buildbuddy.io/invocation/9a78db31-5057-48ea-83cc-0394218d39d4) is also
retained: generated accessors measure 0.000044 B/read, other accessor representations
0.000556 B/read, below the unchanged 0.05 B/read gate.

Tests exercise exact guard/accepts agreement across representations and schemas, checked versus
bound/cached/inline value and failure parity, foreign-layout respecialization, strict-mode hook
misuse, and interpreter-tier typed/borrowed reads after close/reset. All API-specific tests are
reinstated with the implementation. Historical rejection validation remains under
`correctness/restored/`: before the priority clarification, the checked implementation with retained
tests passed `//tests:test`, `//protobuf-buffa/tests:jvm` and `:jvm_non_strict` in
[0ef3c883](https://app.buildbuddy.io/invocation/0ef3c883-b1fb-45ca-b60a-59cf75acb82a).
The subsequent restoration of Phase 1 requires the fresh integration validation recorded below.
This experiment does not certify the release.

## Fresh integration validation after SVM admission

Base `7baf72d` plus the exact production delta `1cfcb37..38f7399` passes all seven targets in
[d19a0431](https://app.buildbuddy.io/invocation/d19a0431-21cb-4bbc-a6c9-00db0381db90):
`consumer_native_smoke`, `native_smoke`, core correctness/allocation, protobuf runtime and
Buffa JVM strict/non-strict. The consumer native smoke covers all 132 cells and unchanged
AST/graph limits. Compact-scalar native ingest remains 168 B/message generic, 136 B/message
typed/generated; the slab extent remains 56 B. Generated compiled accessors remain
0.000044 B/read and other accessor representations 0.000556 B/read, below 0.05.

The full nine-target JVM coverage lane passes in
[f2c1f687](https://app.buildbuddy.io/invocation/f2c1f687-1b50-419c-bd4b-1df551941155), followed by
successful `reports.py coverage` and `coverage_contract.py`: 201 production files across
12 modules, no new exemptions. New Accessor, GeneratedMessage, ProtoRow, SlabRow,
BorrowedSlabRow and TypedRow methods are all covered. [Validation manifest and source hashes](correctness/admitted-d19a0431/validation.json),
test logs/XML and coverage summaries are preserved under `correctness/admitted-d19a0431/`.
Integrated slab and JSON changes remain intact. No further JVM performance run was needed.

## Baseline provenance and reproduction

The preserved baseline SVM image and JVM deploy JAR were built in
[1bdf1570](https://app.buildbuddy.io/invocation/1bdf1570-1318-4011-bfac-0b40a4600a4d).
[Provenance](baseline-provenance/provenance.json) retains source and payload hashes; the
[ignored baseline package definition](baseline-provenance/BUILD.bazel.txt) records the JVM
launcher over its preserved dependency closure. The unchanged Buffa shared library and same
SDK toolchain serve both JVM binaries. Payloads remain ignored under `build/b4-before`.
The baseline JAR is named `baseline_payload.jar` to avoid colliding with java_binary's generated
`consumer_jvm_deploy.jar` output. The first paired invocation completed SVM but detected this
wrapper-only cycle on JVM; JVM then ran separately with the corrected wrapper. Neither
measurement reused an incomplete report.

```sh
bzl test //benchmarks/comparison:consumer_measure \
  --//benchmarks/comparison:consumer_svm_baseline=//build/b4-before:consumer_svm \
  --test_env=TRUFFLEDATA_CONSUMER_BASELINE_ENGINES=stock,generated-owned,slab \
  --test_env=TRUFFLEDATA_CONSUMER_BASELINE_MODES=sparse,full,holdout,holdout-sparse,large \
  '--test_env=TRUFFLEDATA_BENCH_REVISION=baseline 1cfcb37; candidate 38f7399; harness 6edd5e3; SDK 25.5.5-elide.27'
# JVM: consumer_measure_jvm and consumer_jvm_baseline=//build/b4-before:consumer_jvm.
python3 tools/bench/consumer.py merge --output /new/merged shard_*/results.json
```

The optional engine/mode environment overrides preserve the default historical-baseline
matrix when absent. They are necessary for modern baselines without retired native-view
engines. Invocation-only baseline labels and binary payloads are not committed.
