# Optimize native dependencies

This run enables Bazel `--compilation_mode=opt` on the lazy-index-cache-path implementation, including the JVM-only exact buffer-class specialization. It precedes the ThinLTO configuration change. Buffa, simdutf's Rust wrapper, and the adapter all have `--codegen=opt-level=3`; the inspected actions still had `-Cembed-bitcode=no` and no cross-crate LTO flag.

Five randomized fresh forks per cell and metric on unclemax, pinned to CPU 2, using the existing `../full/run.py` protocol. The executable hash and raw measurements are in `paired.json`. Both variants use the same executable; only the native-view request differs. No other timing or profiling run was active.

Generated view ingestion measured 724.721 ns/row projected and 1362.077 ns/row full, versus stock 642.711 and 678.172 ns/row in the view-labelled control. Previous lazy-index-cache-path medians were 781.604 and 1446.416 ns/row. These are successive runs, not an interleaved binary comparison. Retained generated view reads measured 16.862 ns interpreter and 9.389 ns peak; stock boundary reads measured 5.474 and 5.316 ns. The gap remains open.

Java allocations remain 421.5 bytes/row projected and 1382.25 full. These figures exclude unmanaged input, native index capacity, and detached result metadata; they do not establish total allocation parity.

Validation: Rust native tests, SVM native views, and SVM fallback passed; the comparison executable built successfully. [Build invocation](https://app.buildbuddy.io/invocation/5adc9d12-df68-4d28-aa14-eb1ce12a572d).
