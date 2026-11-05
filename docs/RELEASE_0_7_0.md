# TruffleData 0.7.0

Opt-in native-index typed views retain the native protobuf index and materialize
Java values on demand. Ownership can outlive the decoder; thread, lifetime,
schema, size and strict-mode checks remain enforced. Binary decoding certifies
supported view semantics, and native array sizes reuse field-index counts.
Sparse finalization skips already-validated absent defaults.

The native seam advances to **ABI 8**. Upgrade the Buffa JAR, generated bindings,
and native attachments together. The runtime remains Java 25 with Elide GraalVM
25.5.5-elide.24 and Myna 0.8.0. Native builds align with Elide toolchain 2026.11.1
and its recommended Rust nightly-2026-10-09, optimize native dependencies, and
enable Rust/C++ ThinLTO where supported by the configured toolchains.

Shared primitive exports cache immutable field metadata, allowing the nested
consumer to compile within the existing 10,000-node budget. Native-view coverage
now enforces every executable access method, with one exact SVM pointer helper
checked by native tests instead of JVM coverage. Lifetime and schema-cache
saturation tests cover both JVM and SVM. Experimental, source-only JSON scalar
views gain the same shared access protocol and interop; their scoped API remains
SVM-only and is not a published JSON artifact.

The new shared-consumer suite compares stock protobuf, generated owned records
and native views through sparse/full reads, first access, interpreter, peak JIT,
heap ingestion and detachment. It validates checksums and compilation, records
Java allocations and observable native storage, and runs native smoke checks in
CI and release verification. The accepted Linux/SVM measurements show:

| Full consumption | Stock | Generated owned | Native view |
|---|---:|---:|---:|
| Resident decode + guest, ns/row | 612.654 | 627.719 | 1,140.120 |
| Java allocation, B/row | 1,630.5 | 1,608.5 | 1,307.25 |

Native views still cost **1.86× stock latency** here. Their native index also
retains an average 1,488 bytes plus a 24-byte result header; this is not total
native memory accounting. Stock guest computation crosses an explicit Truffle
boundary. A count-only API experiment was rejected after mixed regressions.
Direct schema-specific native storage and actual Elide/TruffleCEL integration
remain future work. See the [complete evidence and limitations](../benchmarks/results/shared-consumer/README.md).

Nine libraries and the BOM are published at https://maven.elide.dev, with source
JARs, POMs, checksums and Linux x86-64 static/shared Buffa attachments. See
[consumption and publication verification](PUBLISHING.md).
