# TruffleData 0.4.0

Generated protobuf contract accessors now expose primitive getters, presence checks,
and array/bytes lengths for owned and native borrowed rows. Schema-bound access keeps
plans and field ordinals available to Truffle partial evaluation while preserving
contract, ownership and lifetime checks.

Small native int32/int64 messages avoid general staging, temporary arrays and boxed
integers. JVM FFM and static SVM backends reuse result slots and release callbacks.
Separate scalar rows and static library exports preserve ordinary read specialization;
primitive-only static rows avoid payload-tracker allocation.

The SVM comparison suite adds 26 generated-getter cases, for 88 cases across ingestion,
AOT, interpreter, peak JIT and allocations. In the measured implementation:

- Buffa nested interpreted reads: 51.79 to 22.58 ns/row with generated getters.
- Owned generated scalar ingestion: 29.82 ns / 56 Java heap bytes versus stock 67.01 ns / 136 bytes.
- Buffa scalar ingestion: 232 Java heap bytes through the generic API, 200 through generated getters, versus 320 for the 0.3.0 generic baseline.
- Primitive-only SQL fixture allocation: 128 bytes instead of 160.

Stock remains faster in several binary paths. Java heap counters exclude native memory;
SQL comparisons cover driver decoders rather than complete queries. The charts retain
fork ranges and raw data, including earlier noisy runs. Measurements identify implementation
commit `c8ef96a2b4bef30226999056341eb30735e6a6bc`, included in this release.

Tests cover compiled generated access, dynamic layouts, closed views, defaults, presence,
duplicates, wrong contracts, cumulative budgets and native recovery after failures.
Allocation and coverage gates enforce these paths without lowering existing thresholds.

The runtime remains Java 25 and Elide GraalVM `25.5.5-elide.24`. The distribution includes
eight Java libraries, the BOM, sources, checksums and Linux x86-64 Buffa native attachments.
See [publishing and consumption](PUBLISHING.md), [generated getters](CONTRACTS.md#generated-protobuf-getters)
and [comparison measurements](COMPARISON_BENCHMARKS.md).
