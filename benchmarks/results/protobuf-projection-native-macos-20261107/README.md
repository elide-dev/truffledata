# Native projection qualification

Local macOS arm64, Rust nightly 2026-10-09. This is correctness/sanitizer evidence, not a timing comparison.

The isolated Cargo build copies the repository native sources and its generated seam bindings. Build flags: `--target aarch64-apple-darwin`, `RUSTFLAGS="-Zsanitizer=address -Cforce-frame-pointers=yes"`. Run the test executable from the repository root with `ASAN_OPTIONS=detect_leaks=1` so the pinned golden fixture paths resolve.

`asan.txt` records 49 passing native tests, including 8,000 seeded inputs tested with full, sparse and lazy projections. Java integration and Linux release qualification are separate gates.
