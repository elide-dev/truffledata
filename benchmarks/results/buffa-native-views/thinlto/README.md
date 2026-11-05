# ThinLTO with the Elide toolchain

The candidate adds `--@rules_rust//rust/settings:lto=thin` and `--features=thin_lto` to the optimized-dependencies baseline. No application-source change separates these binaries. `actions.json` records actual Rust opt-level/LTO flags and C++ compile/index/backend/link flags; Rust static/shared library links use the pinned Elide toolchain.

`previous.py` interleaves five fresh forks of each binary, cell, and metric, using native views on both, pinned to CPU 2 on unclemax. `previous.json` retains executable hashes and raw validated measurements. Timing and memory run separately; no concurrent profiling or benchmark was active.

Generated projected ingestion improves 717.786 to 695.114 ns/row (3.2%); generated full ingestion improves 1341.730 to 1325.566 ns/row (1.2%). Candidate stock controls measure 640.552 and 680.610 ns/row respectively. Thus projected native views remain about 8.5% slower and full reads about 95% slower than stock. Generic full ingestion has no demonstrated improvement. Retained generated reads are effectively unchanged at 16.737 ns interpreter / 9.340 ns peak, versus stock boundary reads at 5.410 / 5.276 ns.

Java allocations are unchanged: 421.5 bytes/row projected and 1382.25 full. These exclude native index capacity, detached result metadata, and unmanaged input. This is not a total-allocation parity claim.

All five requested tests passed: Rust native tests, JVM native views, SVM native views, JSON decoder tests, and JSON API tests. The benchmark executable also built successfully. [Build invocation](https://app.buildbuddy.io/invocation/7920e63c-3eae-434f-b19e-a49679999a08). Verification covers Linux amd64 with the pinned Elide toolchain, not arbitrary downstream linkers or other platforms.

The performance objective remains open. A new profiling build is needed before attributing the remaining costs using the older unoptimized-dependency profile.
