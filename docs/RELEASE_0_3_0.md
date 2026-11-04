# TruffleData 0.3.0

This release updates the pinned GraalVM toolchain and Maven runtime dependencies to
Elide GraalVM `25.5.5-elide.24`. CE and EE distributions remain pinned by SHA-256
for Linux x86-64, Linux ARM64, and macOS ARM64. Consumers should align their
GraalVM runtime and guest language artifacts with this version.

The release retains the Buffa materialization optimizations, Java fallback, and
JVM/static SVM native integration introduced in 0.2.0. All eight library artifacts,
the BOM, and Linux x86-64 Buffa native attachments are published at maven.elide.dev.

See [publishing and consumption](PUBLISHING.md) and
[comparison benchmarks](COMPARISON_BENCHMARKS.md) for setup and measured results.
