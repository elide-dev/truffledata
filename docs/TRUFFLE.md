# Truffle contracts and budgets

Schema identity is stable adapter-owned state, not a global interning cache. Fields separate logical kind, nullability, and optional presence. JDBC contract refinements are supplied by the caller or driver; no SQL syntax is inspected. Inference from observed data is never treated as proof of non-nullness.

`ReadLongNode`, `ReadDoubleNode`, `ReadBooleanNode`, `ReadLengthNode`, and row member access have three-entry polymorphic caches and a generic fallback. The standalone accessor deliberately owns its cache as an adopted child; node object inlining is explicitly disabled for that API. Immutable schema predicates are marked idempotent. Dynamic-object getter caches are explicitly shared between generic and primitive reads. Static-object property handles are cached by layout and ordinal. The primitive batch specialization bypasses state-array reads only for a validated required, non-null INT64 field.

Ingestion, JDBC execution, Protobuf reflection, exact numeric conversion, Unicode code-point traversal, and name lookup live behind Truffle boundaries. Primitive batch/dynamic reads do not cross a boundary. Static-record access uses cached property handles; megamorphic fallback has an explicit boundary. Immutable descriptor/property arrays use `@CompilationFinal`; mutable payload arrays are never declared compilation-final by contents. There is no speculative database-schema assumption requiring an external invalidation notification.

Java null is represented with JSpecify `@Nullable` only where it can occur (driver return values, end-of-stream, optional storage arrays). SQL/data null and missing are explicit non-null sentinel objects. Generated DSL sources are excluded from NullAway; handwritten production packages are NullMarked, and there are no NullAway suppressions. A source-policy test guards annotation coverage, cache limits, and reviewed boundary counts. Compiler warnings are errors.

| Check | Budget |
|---|---:|
| Schema/member/layout PIC | 3 entries before generic fallback |
| Accessor tree after schema churn | 128 nodes |
| Mixed static typed-accessor tree | 256 nodes |
| Compiled test graph | 10,000 Graal nodes |
| Retained INT64 read | 0.05 allocated bytes/read, averaged over 1,000,000 reads |
| Static four-field ingestion | 256 allocated bytes/row |
| Batch ingestion, one required INT64 column | 64 allocated bytes/cell |
| Retained row view creation | 48 allocated bytes/row |
| Default batch rows | 1,024 |
| Default columns | 256 |
| Default cells | 262,144 |
| Default string payload, including nested arrays/rows | 8 MiB (two bytes per UTF-16 code unit) |
| Nested core payload / array-contract depth | 128 |
| Protobuf nesting | Explicit caller limit, restricted to 1–128 |

Allocation tests use thread allocation counters and force retained outputs where allocation is expected. The compiled gate requires `inCompiledCode()` to have executed, disables background compilation, and treats compilation failures and performance warnings as errors. Loop counts are reported so the test does not accidentally benchmark only an interpreter. Fixed invocation allocations are included in the per-read average. These budgets are workload-specific, not proof that every generic interop operation or nullable path is allocation-free.

Protobuf schemas are compiled once per adapter into an immutable descriptor graph, including recursive types. The graph is bounded by `maxColumns` descriptors and `maxCells` total declared fields; repeated nested messages reuse the same schema identities and, with a language owner, static layouts. Explicit integer ranges and length/element/decimal constraints are covered in [known contracts](CONTRACTS.md).

Structural limits are checked before allocating batch storage. Core ingestion accounts nested arrays/rows and strings, and the Protobuf adapter also checks nested/repeated payloads before constructing them. These limits do not bound JDBC driver buffering, pre-existing caller objects, future mutation of retained live views, or total retained application memory. The owner must bound retention and configure source-level limits. Native Image, cancellation, and large real-driver payload behavior need dedicated acceptance gates before shipping.

The continuous Native Image benchmark build also checks Truffle runtime-compilation reachability. Exact-number conversion bodies (not just their fits checks) are bounded away from guest compilation; Unicode code-point counting uses a separate boundary while UTF-16 length and exact-length specialization stay inline. See [simulation scope](BENCHMARKS.md).
