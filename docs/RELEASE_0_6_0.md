# TruffleData 0.6.0

Buffa native index entries shrink from 32 to 24 bytes, reducing storage per entry
and capacity slot by 25%. This is an index-memory improvement, not a claim of
25% lower total message memory or faster decoding. Paired Linux/SVM measurements
do not establish a throughput improvement; Java allocation is unchanged.

The native seam moves to **ABI 7**. Upgrade `truffledata-protobuf-buffa`, its bindings,
and native attachments together. Lengths and subtree ends use bounded 32-bit
representations; source offsets and scalar values retain their 64-bit representation.
Budget, overflow, ownership and lifetime checks remain enforced.

The release also refreshes the Bazel module lock and records profiling experiments
and a tighter single-CPU benchmark protocol. Rejected traversal and lookup
experiments are evidence only. The unfinished sparse-finalization experiment is
excluded.

Nine libraries and the BOM are published at https://maven.elide.dev, with source
JARs, POMs, checksums, and Linux x86-64 static/shared Buffa attachments. Java 25,
Elide GraalVM 25.5.5-elide.24, and Myna 0.8.0 remain unchanged. Experimental JSON
modules remain source-only.

Release preparation reuses the existing Rust, JVM, strict/non-strict, SVM, fallback,
and static-consumer acceptance results. Publication outputs and uploaded bytes are
checked; no new full verification suite or benchmark cycle is run.

See [consumption instructions](PUBLISHING.md) and
[measurement evidence](COMPARISON_BENCHMARKS.md).
