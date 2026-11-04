# TruffleData 0.2.0

This release adds optional Myna/Buffa native protobuf access for JVM and static SVM,
including schema-aware binary, ProtoJSON, protobuf text, and well-known-type handling.
Java fallback remains available. Schemas may come from generated contracts or runtime plans;
borrowed input and views have explicit, checked lifetimes.

Native materialization now defers eligible strings, shares empty views per owner, packs
small-message presence bits, reuses validated defaults, and transfers private repeated-field
arrays into final views. Owned protobuf decoding also avoids redundant metadata and array
copies. Nullable static integer reads retain primitive access and distinguish null from missing.

The comparison suite covers stock protobuf Java and MySQL/PostgreSQL INT4 decoder functions,
with interpreter, final-tier JIT, ingestion, and Java-heap allocation measurements. Full-message
protobuf consumption complements small projections. Stock mixed-message guest measurements
are explicitly marked as boundary projections because direct stock collection getters fail
the SVM JIT blocklist. SQL cases do not include servers, network transport, or full JDBC queries.
These results do not establish a universal performance win; native allocations are excluded
from Java-heap counters, and shared-runner timing ranges can be wide.

Published coordinates, native classifier instructions, and consumer checks are described in
[PUBLISHING.md](PUBLISHING.md). Final release benchmark data and graphics are linked from
[COMPARISON_BENCHMARKS.md](COMPARISON_BENCHMARKS.md).
