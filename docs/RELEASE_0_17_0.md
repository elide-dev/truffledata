# TruffleData 0.17.0

A bindings release. Upgrade the TruffleData BOM, generated bindings and native attachments together: Myna 0.22.0 generates new ABI fingerprints for every seam on both supported LP64 classes, so a 0.17.0 binding rejects a 0.16.0 native library and the reverse. The GraalVM repository URL is unchanged from 0.16.0: `https://maven.elide.dev/gvm-elide/25.5.5-elide.33`.

- The Buffa, JSON, YAML, CSV and experimental Bebop bindings are regenerated with Myna 0.22.0, which takes in 0.21.0 too. Myna 0.21.0 targets the same Elide GraalVM 25.5.5-elide.33 as TruffleData 0.16.0.
- Generated C headers add a static-assert fallback for MSVC C compilers without C11. The JSON and CSV Cargo crates carry the new headers on both ABI classes.
- Native Image field-address accessors for pointer slots now return `WordPointer` instead of `PointerBase`. TruffleData's own code needed no change; recompile any Java code that calls the generated bindings directly.
- The native toolchain stays on Elide toolchain 2026.11.7. No library API, flag or default changes, and all four cells routes remain opt-in.

The README benchmark cards show the 0.12.0 capture. No new performance measurements were taken for this release.
