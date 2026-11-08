# B4 exact-layout guards and bound hooks — rejected (2026-11-08)

The Phase 1 candidate `38f7399` improves generated SVM interpreter reads, but regresses
JVM generated interpreter reads and JVM slab resident consumption. **Reject the production
change.** Keep representation/parity/lifetime regression tests and the baseline-harness fix.
The protected Accessor constructor and bound hooks do not ship. B4 remains open; neither
candidate SVM full interpreter ratio meets the required 2.5× stock ceiling.

## Complete paired evidence

Baseline is exact `1cfcb37`, candidate is `38f7399`, measurement harness is `6edd5e3`.
Both use GraalVM 25.5.5-elide.27; no PGO. These images predate the separate slab allocation
work integrated after this experiment. The baseline and rejected candidate must not be
presented as a fresh measurement of the final combined branch.

- [SVM complete report](svm/merged/results.md), [raw/summary JSON](svm/merged/results.json):
  invocation [213a289b](https://app.buildbuddy.io/invocation/213a289b-9dd7-4e56-800b-2f4b895915f3).
- [JVM complete report](jvm/merged/results.md), [raw/summary JSON](jvm/merged/results.json):
  invocation [d12b5d0b](https://app.buildbuddy.io/invocation/d12b5d0b-3b84-4745-8afc-699c25eeb16f).
- Each runtime: all ten shards, **195 summary cells / 1,170 raw records**, merged with
  `complete: true`. Every shard retains results, test log and XML.
- [Assessment](assessment.json) includes full/sparse interpreter, peak, decode and resident
  timings, within-binary stock ratios, before/after ratios and observed fork ranges.
- [Rejected patch](rejected-phase1.patch) includes the original implementation and the
  API-specific tests. Commits `d182a5d` and `38f7399` remain in branch history.

All figures below are ns/row, lower is better. Brackets are observed ranges of three fresh
fork medians, not confidence intervals. Baseline/candidate and every engine in a `(mode, stage)`
group execute on the same worker, interleaved and pinned to one CPU. Shared-host timings remain
advisory. Stock controls can vary between forks; raw ns and denominator ranges remain available.

| Full workload | Baseline | Rejected candidate | Candidate / baseline |
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
overlap on SVM and generated peak improves on JVM. However, the design's narrower SVM
interpreter condition is not permission to accept the measured JVM regressions: generated
full interpreter is 20.2% slower and slab full resident 40.7% slower, both with nonoverlapping
observed ranges. Production is restored; no threshold is relaxed and no Phase 2 work is started.

## Correctness and retained work

Candidate JVM/native consumer smoke tests both pass all 132 cells, including AST and Graal graph
limits, in [7f8ce7c9](https://app.buildbuddy.io/invocation/7f8ce7c9-b33f-4861-88e2-fa8ab0ced019).
Their logs are under `correctness/`. The inherited candidate allocation-suite evidence
[9a78db31](https://app.buildbuddy.io/invocation/9a78db31-5057-48ea-83cc-0394218d39d4) is also
retained: generated accessors measure 0.000044 B/read, other accessor representations
0.000556 B/read, below the unchanged 0.05 B/read gate.

Retained tests exercise cached guard/accepts agreement across representations and schemas,
checked versus cached/inline value and failure parity, foreign-layout respecialization, and
interpreter-tier typed/borrowed reads after close/reset. Tests that require the rejected API
are archived only. After restoring production and adapting the retained tests, `//tests:test`,
`//protobuf-buffa/tests:jvm` and `:jvm_non_strict` all pass in
[0ef3c883](https://app.buildbuddy.io/invocation/0ef3c883-b1fb-45ca-b60a-59cf75acb82a);
logs are under `correctness/restored/`. Coverage on the final combined integration tip remains the integration
owner's check; this experiment does not certify the release.

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
