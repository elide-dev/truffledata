# Diagnostic short-ASCII probe

This isolated microbenchmark compares `str::from_utf8` plus `encode_utf16().count()` with a short-input `is_ascii()` fast path. Five interleaved fresh processes per cell, CPU 2 on unclemax, 30 ms warmup and 200 ms measurement. No concurrent timing/profiling run was active. Source, raw measurements, and compiler identity are retained here. Built with `rustc -Copt-level=3 probe.rs -o probe`.

The host compiler is Rust 1.98.0, not the adapter's pinned Rust 1.99.1. Functions are deliberately non-inlined to keep the standalone comparison stable. These results are diagnostic and do not establish an adapter-level speedup. Only short strings are tested; the adapter's long-string simdutf path is outside this probe.

Median nanoseconds per call, baseline → ASCII check: ASCII 8 bytes 5.2279 → 1.0978; 32 bytes 9.0679 → 1.4977; 63 bytes 17.0703 → 2.5826. Mixed Unicode 7.3535 → 7.6904, supplementary Unicode 7.3027 → 7.5099, malformed input 2.6471 → 3.0217. The fast path checks every byte, so successful ASCII detection also proves UTF-8 validity and gives exact UTF-16 length. Non-ASCII input retains full validation.

This supports a separate adapter experiment after the compact-count comparison, with the existing malformed-input and Unicode-budget tests preserved.
