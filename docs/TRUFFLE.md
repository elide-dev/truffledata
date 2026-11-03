# Truffle contracts and budgets

Schema identity is stable adapter-owned state, not a global interning cache. Fields separate logical kind, nullability, and optional presence. JDBC contract refinements are supplied by the caller or driver; no SQL syntax is inspected. Inference from observed data is never treated as proof of non-nullness.

`ReadLongNode` and row member access have three-entry polymorphic caches and a generic fallback. The standalone accessor deliberately owns its cache as an adopted child; node object inlining is explicitly disabled for that API. Immutable schema predicates are marked idempotent. Dynamic-object getter caches are explicitly shared between generic and primitive reads. Static-object property handles are cached by layout and ordinal. The primitive batch specialization bypasses state-array reads only for a validated required, non-null INT64 field.

Ingestion, JDBC execution, Protobuf reflection, exact numeric conversion, and name lookup live behind Truffle boundaries. Primitive batch/dynamic reads do not cross a boundary. Static-record access uses cached property handles; megamorphic fallback has an explicit boundary. Immutable descriptor/property arrays use `@CompilationFinal`; mutable payload arrays are never declared compilation-final by contents. There is no speculative database-schema assumption requiring an external invalidation notification.

Java null is represented with JSpecify `@Nullable` only where it can occur (driver return values, end-of-stream, optional storage arrays). SQL/data null and missing are explicit non-null sentinel objects. Generated DSL sources are excluded from NullAway; handwritten production packages are NullMarked, and there are no NullAway suppressions. A source-policy test guards annotation coverage, cache limits, and reviewed boundary counts. Compiler warnings are errors.

| Check | Budget |
|---|---:|
| Schema/member/layout PIC | 3 entries before generic fallback |
| Accessor tree after schema churn | 128 nodes |
| Compiled test graph | 10,000 Graal nodes |
| Retained INT64 read | 0.05 allocated bytes/read, averaged over 1,000,000 reads |
| Batch ingestion, one required INT64 column | 64 allocated bytes/cell |
| Retained row view creation | 48 allocated bytes/row |
| Default batch rows | 1,024 |
| Default columns | 256 |
| Default cells | 262,144 |
| Default direct string payload | 8 MiB of UTF-16 code units |
| Protobuf nesting | Explicit caller limit, restricted to 1–128 |

Allocation tests use thread allocation counters and force retained outputs where allocation is expected. The compiled gate requires `inCompiledCode()` to have executed, disables background compilation, and treats compilation failures and performance warnings as errors. Loop counts are reported so the test does not accidentally benchmark only an interpreter. Fixed invocation allocations are included in the per-read average. These budgets are workload-specific, not proof that every generic interop operation or nullable path is allocation-free.

Protobuf schemas are compiled once per adapter into an immutable descriptor graph, including recursive types. The graph is bounded by `maxColumns` descriptors and `maxCells` total declared fields; repeated nested messages reuse the same schema identities.

Structural limits are checked before allocating batch storage. Direct strings are accounted during ingestion, and the Protobuf adapter additionally accounts nested/repeated cells and strings. These limits do not bound JDBC driver buffering, pre-existing caller objects, arbitrary nested live views, or total retained application memory. The owner must bound retention and configure source-level limits. Native Image, cancellation, and large real-driver payload behavior need dedicated acceptance gates before shipping.
