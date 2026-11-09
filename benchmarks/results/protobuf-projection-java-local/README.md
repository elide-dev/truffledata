# Java projection and scoped-input checkpoint

Direct macOS arm64 qualification on the pinned GraalVM .27 APIs: all four
ProjectionTest methods pass; complete Buffa acceptance passes on pure Java and
the native FFM projection library. The compiled-read suite passes fatal Truffle
warnings and includes lazy materialized-child expiry after session reset.

The tests cover omitted-field masks/counts, full lazy child realization, input
window ownership, external bytes, corrupt input offsets/depth headers, strict
and deferred UTF-8, retained slab budgets, immutable leases after source-handle
close, stale lazy descendants/arrays, pins, and durable explicit snapshots.

The integrated Linux JVM/SVM gate remains pending. Its first compilation caught
three verifier null-flow facts and one SVM attachment null check; these were
fixed. These direct results do not establish the E1 throughput gate.
