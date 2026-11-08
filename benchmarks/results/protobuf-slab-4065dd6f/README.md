# Paired slab consumer qualification — 4065dd6f

Invocation `4065dd6f-2b23-40f9-bb50-bfec5e1a448f`, isolated candidate snapshot `5002235`, x86-64 RBE, ordinary images without PGO. Both ten-shard measurement targets completed. Each runtime contains **208 summary cells and 1,248 raw measurement records**. The merges used the current consumer harness's source, SHA, engine/mode and coverage checks; both report `complete: true`.

- [SVM merged report](svm/merged/results.md), [complete data](svm/merged/results.json)
- [JVM merged report](jvm/merged/results.md), [complete data](jvm/merged/results.json)
- [Task comparisons with medians and ranges](assessment.json)
- [Full qualification outcome](qualification-summary.txt)

Every shard retains raw measurements, summary, worker/load metadata and test log/XML. Unsupported historical engines/modes are excluded explicitly by the harness; new `slab-native` and projection/session modes have no fictitious historical counterpart. Both 132-cell consumer smoke targets passed the existing allocation and graph checks; their logs are retained under `correctness/`. Measurement-target success establishes a complete, correct run; it does **not** mean the performance criteria below passed.

## Provenance and limits

The retained SVM baseline is `f5e2d97`, implementation-identical to `fecb39f` for core/protobuf/runtime/Buffa and this consumer. Its exact SHA is `f2abf6a4231a9732e54614815a3d9d0fcadef092a41f59798e71b5ceea4a990e`. The JVM baseline was rebuilt from exact `fecb39f` in invocation `b38efb86-2fd1-4297-853e-f035e6ae4b9d`. [Baseline provenance](baseline-provenance/provenance.json) retains deploy-JAR, native-library, SDK release/build and launcher hashes; the other files in that directory preserve its original wrapper/build definitions with `.txt` suffixes so they do not create a Bazel package.

The baseline uses GraalVM **25.5.5-elide.24** and the candidate **25.5.5-elide.27**. This is an implementation **and runtime** comparison. JVM `binary_sha256` identifies the launcher script, not a complete deployment; use the separate baseline manifest for its payload hashes. SVM hashes identify the executable. Peak comparisons below therefore cannot isolate library effects. Stock controls and per-fork ranges remain visible in the raw data.

Mode/stage groups keep both binaries and all engines on one worker. E1's cross-mode ratios were measured separately without sharding. Shared-worker timings remain advisory; some cells have wide ranges. Java allocation excludes native allocations, allocator overhead, plan storage and native peak memory. Published stock-interop SVM timings include the documented host-call boundary. This fixture suite does not establish cross-schema performance.

## Findings to retain at the requested pause

| Comparison | SVM | JVM | Status |
|---|---:|---:|---|
| B4 generated-owned / stock, full interpreter | 4.358× | 1.929× | SVM ≤2.5 gate misses |
| B4 slab / stock, full interpreter | 6.729× | 6.517× | SVM ≤2.5 gate misses; JVM ≤2 hypothesis misses |
| Generated-owned full peak / historical same engine | 1.090× | 0.924× | SVM no-regression gate not demonstrated |
| Slab full peak / historical native-view | 2.275× | 1.457× | Replacement reads remain slower |
| C1 native / Java, sparse resident | 0.992× | 2.193× | JVM ≤1.3 hypothesis misses |
| C1 native / Java, full resident | 1.009× | 1.724× | JVM ≤1.3 hypothesis misses |
| C1 native / Java, large resident | 1.026× | 1.435× | JVM ≤1.3 hypothesis misses |
| B3 heap / resident, sparse | 1.070× | 0.990× | SVM +5% criterion misses |
| B3 heap / resident, full | 1.031× | 0.190× | SVM +5% criterion passes; JVM cross-cell profile differs |

SVM generated-owned full peak is 48.96 ns [48.86, 49.08], historical 44.91 ns [44.89, 50.55]. SVM slab full peak is 390.10 ns [310.92, 454.24], historical native-view 171.49 ns [171.10, 174.10]. Thus a simple claim that B4 has closed or all peak regressions are fixed is unsupported. The inline DSL prototype alone did not close it; [the retained profile](../protobuf-interpreter-4065dd6f/README.md) identifies the remaining dispatch cost. No subsequent read-hook prototype was started before the pause.

SVM `slab` and `slab-native` both select the native producer; their ratios are controls, not Java/native speedups. JVM sparse native decode is particularly noisy at 7.407× Java (native 4,178–5,268 ns, Java 661–662 ns); do not generalize that isolated startup/profile result. Full source/backend metadata is retained for every cell.

The broad SOTA decoder/ingestion/allocation targets remain measured hypotheses, many missed here; `assessment.json` uses the revised SOTA thresholds instead of the superseded original table. A2b and A4 retain their recorded stop decisions. A3's x86 investigation is separately recorded and its hypotheses remain unmet. E2 text/ProtoJSON slab migration remains optional/unactivated, and E3/PGO remain withdrawn.

[E1 results](../protobuf-projection-4065dd6f/README.md) passed all required SVM ratios and two of three JVM ratios. Root's later generated eager-projection extension (`844aafe`) and the layout-accessor correctness fix (`63fd6d4`) are **not in this snapshot** and require fresh qualification after resuming.
