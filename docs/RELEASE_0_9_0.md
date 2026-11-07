# TruffleData 0.9.0

This intermediate release gives consumers prebuilt Buffa, JSON and CSV natives
for Linux x86-64, Linux AArch64 and macOS AArch64, with matching Java bindings.
Each platform ships a static archive and a shared library. Linux archives carry
machine code and ThinLTO bitcode together; macOS ships a machine-code archive
and a separate `-macos-aarch64-bitcode.a` for LLVM 23/lld consumers. Rust
compiler-builtins and compiler-rt remain machine-code exceptions.

Myna 0.13.0 makes fingerprints describe the resolved ABI, so one bindings JAR
accepts all three platforms. Every seam validates the release target set before
binding generation. GraalVM distributions and Maven dependencies are pinned to
25.5.5-elide.27; Elide LLVM remains 2026.11.5 and Rust nightly-2026-10-09.
Upgrade Java artifacts, bindings and native attachments together.

`ProtoRow.unknownFieldBytes()` exposes a defensive copy of preserved unknown
wire fields, allowing TruffleCEL and other consumers to apply their own equality
policy without re-encoding declared fields. Child records expose their own bytes.

Since 0.8.0, JSON adopts Myna value ABI v4 directories and reusable readers;
protobuf default native ingestion uses compiled field directories and shared
layout-bound accessors; CSV improves short unescape runs. Native compact JSON
tapes and protobuf slab producers are present as groundwork. Their full Java
consumer cutovers, YAML's Java API and further performance work continue toward
the next release. The existing native-free protobuf fallback remains available.

The publication gate requires every native attachment, checks archive members
and shared-library dependencies, verifies fingerprints against the published
bindings, and runs static links with and without ThinLTO plus shared-library
consumers on each platform. See [publishing and linking](PUBLISHING.md).

Eighteen libraries and the BOM are published at https://maven.elide.dev with
source JARs, POMs and checksums. Additional requested distribution work, including
musl and Maven Central/signing, is tracked for the follow-up release.
