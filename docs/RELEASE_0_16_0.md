# TruffleData 0.16.0

A runtime-baseline release. Upgrade the TruffleData BOM and the GraalVM repository URL together: the Gradle and Bazel repository URL for the GraalVM dependencies is now `https://maven.elide.dev/gvm-elide/25.5.5-elide.33`.

- The JVM and Native Image builds, and the GraalVM SDK, Truffle and polyglot versions managed by the BOM, move from Elide GraalVM 25.5.5-elide.31 to 25.5.5-elide.33 (CE and the EE test profile).
- The native libraries and Linux ASan checks stay on Elide toolchain 2026.11.7, and the bindings on Myna 0.20.0. The native ABI and its fingerprints are unchanged.
- No library API, flag or default changes. All four cells routes remain opt-in.

The README benchmark cards show the 0.12.0 capture. No new performance measurements were taken for this release.
