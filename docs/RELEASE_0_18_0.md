# TruffleData 0.18.0

A bindings and toolchain release. Upgrade the TruffleData BOM, generated bindings and native attachments together: Myna 0.23.0 generates new ABI fingerprints for every seam on both supported LP64 classes, so a 0.18.0 binding rejects a 0.17.0 native library and the reverse. The GraalVM repository URL is unchanged: `https://maven.elide.dev/gvm-elide/25.5.5-elide.33`.

- The Buffa, JSON, YAML, CSV and experimental Bebop bindings are regenerated with Myna 0.23.0. The JSON and CSV Cargo crates carry the regenerated headers on both ABI classes. Myna's value headers are unchanged.
- The native libraries build with Elide toolchain 2026.11.8. Its compiler and linker configuration is unchanged from 2026.11.7, and the Rust pin stays `nightly-2026-10-09`. The bundle adds shared C++ runtimes, which TruffleData does not link.
- Myna 0.23.0 adds Truffle upcall cells for signed 64-bit native callbacks and receiver-bound direct FFM upcalls. No TruffleData seam declares a callback, so this release uses neither. A native-to-guest delivery route is follow-up work and is not part of this release.
- No library API, flag or default changes, and all four cells routes remain opt-in.

The README benchmark cards show the 0.12.0 capture. No new performance measurements were taken for this release.
