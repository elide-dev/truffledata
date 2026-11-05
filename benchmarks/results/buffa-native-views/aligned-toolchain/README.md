# Elide/Rust toolchain alignment

Elide toolchain v2026.11.1 and its recommended Rust nightly-2026-10-09 replace v2026.10.0 / Rust 1.99.1. Target Rust, host Cargo tooling, rustfmt, rust-analyzer, and local rustup configuration use the dated pin. The downloaded Linux bundle's `rustversion` matches; the same dated compiler on unclemax reports LLVM 23.1.3 (rustc.txt). All 22 Rust archive checksums match the dated official manifest.

The implementation remains the count-aware long[] native directory from af47d51. The compact representation was rejected; its patch and measurements are saved under ../compact-counts. Large nested-message count boundary tests are retained. No ASCII fast path was added.

## Verification

[All five selected tests and the SVM benchmark build passed](https://app.buildbuddy.io/invocation/a660146a-ad8c-41ad-bd46-9510c4411310): Rust adapter tests, JVM native views, SVM native views, native JSON decoder tests, and native JSON API tests. See verification.txt for the command and results. This is targeted upgrade verification, not a claim that every repository test or platform was rerun.

Compiler action summaries in actions.json show the nightly Rust toolchain, opt-level=3 for Buffa and the adapter, Rust ThinLTO and dependency linker-plugin bitcode, Elide linker/archive tools, and C++ ThinLTO indexing/backend actions. These do not establish full cross-language or sysroot archive LTO. Linux fat-sysroot integration remains tracked in [toolchain#13](https://github.com/elide-dev/toolchain/issues/13).

## Interleaved comparison

Five fresh forks per binary, cell, and metric; 300 sequential processes on unclemax CPU 2. Both binaries request native views. No concurrent timing/profiling process ran. Previous is the retained index-counts executable; candidate is the aligned-toolchain executable. Raw measurements, binary hashes, validation, and ranges are in previous.json, previous.py, and previous.md.

| Operation | Previous ns/row | Aligned ns/row | Aligned stock ns/row |
|---|---:|---:|---:|
| Generated projected ingestion | 621.784 | 659.404 | 652.602 |
| Generated full ingestion | 1284.427 | 1299.548 | 683.151 |
| Interpreter reads | 16.478 | 16.367 | 5.444 |
| Peak JIT reads | 8.445 | 8.438 | 5.352 |

The upgrade regresses projected ingestion by 6.1%; its fork ranges do not overlap the previous binary, while stock controls are nearly unchanged. Full ingestion is 1.2% slower with overlapping ranges. Retained reads and Java allocation are essentially unchanged. Scalar ingestion improves from 52.046 to 50.113 ns/row. This is a combined toolchain/relinked-image comparison; it does not isolate a specific compiler, linker, or code-layout cause.

Projected Java allocation remains 341.5 bytes/row versus stock 1776.75; full allocation remains 1406.25 versus 1776.75. Java figures exclude unmanaged input, native index capacity, and native metadata, so this is not total-allocation parity.

The alignment is retained to follow the requested toolchain recommendation, with the measured regression recorded explicitly. The previous projected-ingestion win does not hold in this upgraded binary. Overall parity is still unmet: full ingestion is about 1.90x stock, interpreter reads 3.01x, and peak reads 1.58x on these fixtures.

## Stop point

This finishes the bounded toolchain upgrade and current measurement cycle. Work is paused at the user's request. No pushes, releases, or further optimization cycle were performed. Investigating the projected-ingestion regression and remaining materialization/access gaps requires resuming work.
