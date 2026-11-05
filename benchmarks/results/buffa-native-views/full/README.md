# Full generated-getter consumption

Schema 12 adds `proto-mixed-full/owned|buffa/ingest-generated`, consuming every populated field with generated schema getters and direct array access. The original uncached-interop case and stock baseline remain. The fixture independently checks the full checksum during setup and measurement. The comparison also fixes `TypedRow.getSize` to dispatch through concrete borrowed array types: JVM smoke testing found a non-inlined interface call.

Build and JVM smoke/view checks: https://app.buildbuddy.io/invocation/3ec90268-a8e5-4b3d-9261-82a30285cd6b. All passed; the SVM image built with JIT blocklist checks. Result-validator unit tests passed. Source: local full-getter and concrete-size-dispatch changes on `44809c1`.

Same-binary eager/view modes, five randomized forks per cell/metric, CPU 2 on unclemax. Remote: `/tmp/truffledata-native-views.G2ATYf/full`. Raw samples include executable hash, tier/backend and checksums. `run.py` requires `tools/bench/compare.py` beside it and an executable named `candidate`.

Full native-view ingestion is 1889.9 ns through generated getters versus 2417.8 ns via uncached interop; eager generated ingestion is 1879.2 ns; stock is 676.6 ns; concrete generated-owned is 697.0 ns. Java allocation is 2037.25 native-view, 1862.25 eager, 1776.75 stock and 1800.50 generated-owned bytes/row. Native storage is excluded.

Projected native-view ingestion remains 1029.8 ns; retained generated peak reads improve to 11.60 ns with concrete size dispatch (eager 6.65 ns). Cross-build effects remain possible. No claim of parity follows from these results.
