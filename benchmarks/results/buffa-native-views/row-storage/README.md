# Separate eager storage from borrowed row shapes

Source: row-storage change on `5194f14`. TypedRow retains common owner/plan state; ArrayRow carries eager values and presence. NativeRow no longer carries empty eager arrays or unused presence bits, and ScalarRow retains only its own inline scalar storage/presence. Public typed access and scoped lifetime semantics remain unchanged.

Eager, native-view and strict-view JVM tests passed: https://app.buildbuddy.io/invocation/30acb319-14a6-4e88-96c9-1ea31f6ba3ed. Strict eager, view, and Java-only fallback SVM tests passed: https://app.buildbuddy.io/invocation/beec8f8d-556b-4512-8550-ed183d000b14.

`paired.json` uses the five-fork same-binary view/eager protocol from `../full/run.py`. `previous.json` interleaves the previous single-check binary and this candidate, both in view mode, with five fresh forks per metric/cell on CPU 2. Script: `previous.py`; remote `/tmp/truffledata-native-views.G2ATYf/row-storage`.

Full Java allocation falls 1572.75 to 1446.25 bytes/row (8.0%), projected 501.5 to 485.5, scalar 88 to 80. Native memory is excluded. Interleaved full latency is 1401.3 versus 1434.0 ns, projected 766.0 versus 782.7, scalar 52.6 versus 54.4. Stock ingestion also slows approximately 2–3% in this binary; cross-build code layout can contribute, but this is still a measured latency tradeoff, not a speedup. Retained interpreter and peak reads are approximately unchanged. Keep provisionally for the smaller row shapes while further optimizing materialization; do not claim stock parity.
