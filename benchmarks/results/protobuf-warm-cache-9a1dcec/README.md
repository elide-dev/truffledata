# Protobuf slab warm-cache experiment

Baseline: `9a1dcec`, with B4 Phase 1 restored and the slab allocation work integrated.
SVM is the performance target; JVM runs validate semantics and coverage. No PGO.

## Restored baseline profile

The [profile summary](baseline-profile/summary.json) comes from a fresh
`consumer_interpreter_profile` run (`8bad3434`). The four original JFRs, JSONL
records, binary checksum and compressed symbols are retained alongside it.
Each engine has 3,000 application execution samples. These samples identify
where to experiment; they are not elapsed-time speedups.

Generated interpretation no longer attributes a leading leaf cost to
`Accessor.bound`. Slab still attributes 647/3,000 leaf samples (21.6%) there.
Slab cache paths account for 281 inclusive samples in `row` (9.4%), 102 in
`text` (3.4%) and 128 in `array` (4.3%). These inclusive counts may overlap.

The first isolated candidate removes synchronization and Truffle boundaries from
warm record/text/blob handle hits. A volatile table reference publishes the table;
acquire/release element operations publish each entry. Synchronized miss paths
recheck the slot before construction, and lazy children publish only after budget
and retained-byte accounting commit. The owner gains no instance fields.
The boxed list/unsigned map remains synchronized, and its representation is
unchanged. Public layout, projection and borrowed lifetime checks are unchanged.

New concurrency tests race cold and warm row/text/blob/list reads, check stable
identity and initialized contents, and ensure a lazy child charges its budget
once. The existing boundary count remains 16: three boundaries move to misses.

## Reproduction

The exact preserved baseline image is described by
[baseline-provenance.json](baseline-provenance.json). The image and its Bazel
alias live in ignored `build/protobuf-warm-before`; invocation-only baseline
configuration is not committed.

```sh
bzl test //benchmarks/comparison:consumer_measure \
  --//benchmarks/comparison:consumer_svm_baseline=//build/protobuf-warm-before:consumer_svm \
  --test_env=TRUFFLEDATA_CONSUMER_BASELINE_ENGINES=stock,stock-interop,generated-owned,slab,slab-native,slab-projected,slab-lazy,slab-session,slab-session-projected \
  --test_env=TRUFFLEDATA_CONSUMER_BASELINE_MODES=sparse,full,holdout,holdout-sparse,large \
  '--test_env=TRUFFLEDATA_BENCH_REVISION=baseline 9a1dcec; candidate f045f5c; SDK 25.5.5-elide.27'
python3 tools/bench/consumer.py merge --output /new/merged shard_*/results.json
```

## Correctness checkpoint

Candidate `f045f5c` passes all seven focused targets in
`4097845d-7c64-4600-b5d9-1214fbd26045`: core, protobuf runtime, Buffa JVM strict and
non-strict, compiled allocation, native allocation and consumer native smoke.
[Logs and XML](correctness/4097845d/) are preserved. All 36 runtime tests pass,
including the concurrent cold/warm and lazy-publication tests. The consumer smoke
passes all 132 cells and unchanged graph/AST gates. Native scalar allocation
remains 168 B/message generic and 136 B/message typed/generated, with a 56 B slab
and 112 B publication (64 B array + 48 B owner). Compiled accessor allocation
remains 0.000044 B/read generated and 0.000556 B/read for other representations,
below 0.05. Parent will run combined nine-target method coverage after admission.

## Initial candidate: complete paired SVM matrix

The common preserved-image invocation `950032e5-dc4f-40f9-a67f-0a73f3b5dce3`
passes all ten consumer shards. Both hashes in every report match the
[pair manifest](svm/pair-manifest.json). The [complete report](svm/merged/results.md)
and [raw JSON](svm/merged/results.json) contain **264 summary cells / 1,584 raw
records**, all nine engines and five supported modes, three fresh forks per cell.
[Assessment](assessment.json) preserves every before/after and stock ratio.
No Java-allocation or retained-memory metric changes in any of the 132 pairs.

All times below are ns/row, lower is better. Ranges are observed ranges of three
fork medians, not confidence intervals; shared-worker timings remain advisory.

| SVM workload | Baseline | Initial candidate | Change |
|---|---:|---:|---:|
| Slab full interpreter | 412.31 [412.13, 412.52] | 350.79 [346.99, 353.84] | −14.9% |
| Slab full peak | 311.37 [311.19, 527.98] | 206.95 [204.74, 271.55] | −33.5% |
| Slab sparse interpreter | 52.30 [52.23, 52.81] | 44.78 [43.94, 46.47] | −14.4% |
| Slab sparse peak | 29.50 [29.37, 744.22] | 21.02 [20.95, 21.11] | −28.7% |
| Native slab large peak | 42,159.29 | 23,407.64 | −44.5% |
| Projected slab sparse first read | 52.47 [52.10, 53.73] | 57.02 [54.82, 58.43] | +8.7% |

The native/lazy/projected warm-read variants also improve. Full generated
interpretation is stable at 217.60 → 216.11 ns; generated sparse peak changes
9.723 → 9.774 ns (+0.52%, narrowly disjoint ranges), while its stock control rises
0.92%. The full interpreter/stock ratios remain **5.698× slab and 3.510× generated**,
so the absolute B4 ceiling of 2.5× remains open.

The direct cold-read tradeoff is coherent across sparse variants: first read is
+3.9% slab (wide overlapping ranges), +4.3% native, +8.7% projected and +4.5% lazy.
The initial implementation decodes the handle before the acquire, then decodes
it again on the synchronized miss. A separate handle-forwarding refinement is
being qualified; the initial production commit/image and complete measurements
remain preserved. **Initial candidate `f045f5c` is not yet admitted.**

Other observed changes require a separate attribution: native sparse decode is
+5.25%, unprojected session full resident +5.64%, and stock-interop full interpreter
+5.47%, all with disjoint observed ranges. Native decode never calls the changed
row/text/blob helpers; unprojected session reads use the unchanged SlabSession
caches. SVM slab and slab-native decode use the same producer path, yet sparse
slab decode is −0.37%. Same-shard controls are mixed (sparse decode stock +0.86%,
generated −5.65%; full resident stock −1.18%, generated −4.98%). These observations
remain disclosed without attributing them directly to cache-hit execution.
Full slab resident is +18.2% with wide overlapping ranges, including a 19.8 µs
candidate fork; native slab resident is +0.23%. No noisy fork is discarded.

The initial image is 43,978,864 bytes versus 43,126,896 baseline (+1.98%). Both
preserved images remain ignored. These are x86 SVM timings; final shared JVM and
x86/Arm native acceptance, including cold/warm publication races, is separate.

