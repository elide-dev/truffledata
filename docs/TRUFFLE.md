# Truffle contracts and budgets

Schema identity is stable adapter-owned state, not a global interning cache. Fields separate logical kind, nullability, and optional presence. JDBC contract refinements are supplied by the caller or driver; no SQL syntax is inspected. Inference from observed data is never treated as proof of non-nullness.

`ReadLongNode`, `ReadDoubleNode`, `ReadBooleanNode`, `ReadLengthNode`, and row member access have three-entry polymorphic caches and a generic fallback. The standalone accessor deliberately owns its cache as an adopted child; node object inlining is explicitly disabled for that API. Immutable schema predicates are marked idempotent. Dynamic-object getter caches are explicitly shared between generic and primitive reads. Static-object property handles are cached by layout and ordinal. The primitive batch specialization bypasses state-array reads only for a validated required, non-null INT64 field.

Native protobuf primitive exports also bind immutable field descriptors by plan identity and ordinal,
with a three-entry limit and generic fallback. Mutable row directories and materialization caches
remain mutable to the compiler. The shared-consumer experiment records a rejected count-capability
prototype: its read/allocation improvements came with cross-workload regressions, so it does not
change the common access API.

## Layout-bound accessors

`Accessor` is the one read contract every representation can bind: an immutable object for one
field of one row *layout*, with `accepts`, `read`, `present`, `readLong`, `readDouble`,
`readBoolean` and `length` (UTF-16 units for strings, elements for arrays, folded to exact length
contracts after the read). Results are exactly `DataLibrary`'s. `AbstractRow.accessor(ordinal)`
returns the row's accessor; it is identity-stable for every row of the same layout and rejects
rows of any other layout with `ContractViolation` instead of reading them. The key is the layout,
not the schema: two static layouts, two protobuf plans or a `ProtoRow` and its storage can share
one `Schema`, so schema identity alone never binds a read.

`ReadLongNode`, `ReadDoubleNode`, `ReadBooleanNode` and `ReadLengthNode` try an `accessed`
specialization first: guard `index == cachedIndex` and `accessor.accepts(row)` on a cached accessor
(three entries, asserted a partial-evaluation constant), body one accessor call. Rows that only offer
the default fall through to the unchanged `DataLibrary` specializations; `generic` replaces both, so
schema churn stays bounded (three accessor entries, three library entries, then the generic path).

| Representation | Accessor bound to | Compiled read |
|---|---|---|
| `StaticLayout.Record` | the layout instance | constant static property and state |
| `Batch.Row` | (schema, `Batch.Row`) | column array load (long array for required INT64) |
| generated protobuf messages | (plan, generated class) | exact-class cast, ordinal switch folds to a field |
| `ProtoRow` | (plan, `ProtoRow`) | inline scalars, or the storage layout's accessor |
| Buffa `ScalarRow` / eager `ArrayRow` | (plan, row class) | owner check, inline field or element load |
| `DynamicRecord`, `NativeRow`, CSV, JSON/tape rows | schema default (`LibraryAccessor`) | unchanged library specializations |

`Schema.accessor(ordinal)` is the default: a `LibraryAccessor` over the uncached `DataLibrary` for
host callers outside guest compilation; nodes never cache it. `Schema.accessor(representation,
ordinal, factory)` holds representation tables keyed by a row class or layout object. Accessor
lookup, the default's library calls, the strict-mode result audit and the length of foreign interop
arrays are behind boundaries; compiled accessor reads cross none. Accessors use `Field.Kind` only:
typed tape slots (`TypedLayout`, `TypedRecord`) can adopt the contract later without a new kind
enum.

The [shared consumer experiment](../benchmarks/comparison/CONSUMER.md) exercises nested protobuf
work through common nodes on JVM and SVM with the same graph budget. Its native smoke test runs in
the reusable native-backend CI job and contributes JUnit to the existing Codecov upload. Typed JSON
scalar records now export `DataLibrary` and ordinary row interop over their native slots. They
remain scoped and SVM-only; shared compiled reads, schema-cache saturation, null/presence,
close/thread checks and allocation budgets are exercised by `//json/tests:native_test`.

Ingestion, JDBC execution, Protobuf reflection, exact numeric conversion, Unicode code-point traversal, recursive array-element validation, and name lookup live behind Truffle boundaries. Primitive batch/dynamic reads do not cross a boundary. Static-record access uses cached property handles; megamorphic fallback has an explicit boundary. Immutable descriptor/property arrays use `@CompilationFinal`; mutable payload arrays are never declared compilation-final by contents. There is no speculative database-schema assumption requiring an external invalidation notification.

Java null is represented with JSpecify `@Nullable` only where it can occur (driver return values, end-of-stream, optional storage arrays). SQL/data null and missing are explicit non-null sentinel objects. Generated DSL sources are excluded from NullAway; handwritten production packages are NullMarked, and there are no NullAway suppressions. A source-policy test guards annotation coverage, cache limits, and reviewed boundary counts. Compiler warnings are errors.

| Check | Budget |
|---|---:|
| Schema/member/layout PIC | 3 entries before generic fallback |
| Accessor tree after schema churn | 128 nodes |
| Mixed static typed-accessor tree | 256 nodes |
| Compiled test graph | 10,000 Graal nodes |
| Retained INT64 read | 0.05 allocated bytes/read, averaged over 1,000,000 reads |
| Accessor INT64 + string length read, every adopting representation | 0.05 allocated bytes/read |
| Static four-field ingestion | 256 allocated bytes/row |
| Batch ingestion, one required INT64 column | 64 allocated bytes/cell |
| Direct column filling, 1,024 required INT64 cells | 9,216 allocated bytes/batch |
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

## Expanded shared-model gates

The allocation and compiled targets exercise nullable static reads with null/missing
failures, and one adopted accessor transitioning through 1, 2, 4 and 8 distinct schema
identities across static, batch and dynamic representations. Rows originate in two
contexts on a shared engine and are read after those producing contexts close. This
checks retained representation independence; it does not authorize sharing live dynamic
objects across threads. The 1/2/4-identity paths must stay within 0.05 bytes/read; the
8-identity generic path has a 16-byte/read budget and currently measures 12 bytes/read.
AST size remains bounded (12 nodes observed at saturation, 256-node ceiling).

A nested static outer record containing a live DynamicRecord exercises post-compilation
shape changes, width/type/null/missing rejection and successful recovery. Compiled
execution is required again after recovery, with a 0.05-byte/read allocation ceiling.
The outer record's slots are immutable; retaining a live nested object deliberately
preserves that object's live semantics, rather than silently copying it. Do not infer
transitive immutability from a static outer representation.

## Protobuf Native Image metadata

`dev.elide.truffledata.protobuf.NativeMetadata` generates reflection configuration from
full-runtime Protobuf message classes on a build JVM. For a consumer classpath:

```sh
java -cp "$CONSUMER_CLASSPATH" dev.elide.truffledata.protobuf.NativeMetadata   example.Messages\$Order > reflect-config.json
```

Package the JSON under `META-INF/native-image/<group>/<artifact>/reflect-config.json`,
or pass its path to Native Image's reflection configuration option. A Bazel consumer
can define a `java_binary` using this main class and its own generated message runtime
dependencies, then invoke it from a genrule. `//protobuf:native_metadata` is the ready
binary for classes in this repository's Protobuf runtime; the native benchmark now
uses such a genrule rather than a hand-maintained list.

The generator includes messages, builders, enums, imported message return types,
and descriptor/edition classes used by dynamic messages. Names are deterministic and
class discovery is bounded to 16,384 classes. Full Message API is required; lite-only
classes and custom reflection conventions are outside this generator's contract.
This is reflection metadata, not general class-initialization policy or driver support.
Generated metadata passed the native simulation executable, including generated and
wire-decoded Protobuf paths. Consumer-specific native executables and additional schema
features still require acceptance; image construction alone is insufficient evidence.

## Host inlining budgets for downstream images

TruffleData ships no `native-image.properties` and sets no Truffle host-inlining
option, so a consumer image uses the Native Image defaults
(`-H:TruffleHostInliningMaxSubtreeInvokes=32`, `-H:TruffleHostInliningBaseBudget=5000`).
On the protobuf consumer benchmark (x86-64 SVM, paired images, three forks; see
`benchmarks/results/protobuf-dispatch-5815c56/`), building the *image* with
`-H:TruffleHostInliningMaxSubtreeInvokes=64 -H:TruffleHostInliningBaseBudget=20000`
let the benchmark's own guest root inline one more node and moved the full-fixture
interpreter cell -3.7% (disjoint), but also moved the stock-interop control -6 to -16%,
regressed a sparse interpreter cell +10% and grew the image 2.2%. The effect comes from
the consumer's own interpreter nodes, not from TruffleData's. Image builders (Elide,
TruffleCEL) who run large guest interpreters may evaluate these two options on their
own paired measurements; TruffleData does not ship them and makes no claim for other
guests. This note records the 2026-11-09 ruling that the B4 interpreter gate is judged
against the stock-interop cell (the like-for-like interpreter path), not against direct
AOT getters behind a single boundary, because harness and dispatch alone sit at about
2.0x of the direct-getter cell before any library body runs.

## JIT assurance gate

The supported guarantee is scoped to the pinned Elide GraalVM runtime and the checked-in
operation matrix. Native Image must reject any blocklisted call or
`CompilerAsserts.neverPartOfCompilation` reachable for runtime compilation from those roots.
This includes branches the runtime fixtures do not execute: reachability analysis is stronger
than line coverage for this check. It is not proof about arbitrary third-party implementations,
new guest compositions, driver internals behind boundaries, or different GraalVM versions.

`JitMatrix` currently contains 617 cases (550 successful operations and 67 expected rejections) shared by three gates:

- `//tests:test` runs instrumented correctness coverage with strict contract assertions.
- `//tests:jit_matrix` runs without coverage instrumentation, with strict assertions and
  synchronous compilation. Every successful case must execute compiled code under the
  10,000-node graph budget and performance-warnings-as-errors policy.
- `//benchmarks:native_smoke` builds the native executable with the explicit checks from
  `bazel/jit.bzl`, then runs the same matrix with runtime compilation enabled.

The matrix covers batch/static/dynamic representations, all field kinds, integer and floating
storage widths, typed and generic reads, null/missing/rejection behavior, UTF-16 and Unicode
lengths, nested arrays and records, member enumeration and ordinal access, every ExactNumber
conversion, and PIC transitions through 1/2/3/4/8 schema identities. It also covers generated
and wire-decoded Protobuf snapshots and the JDBC cursor interop protocol. Existing allocation
tests separately cover mutation, context closure, retained values, and compiled recovery.
Expected rejection cases verify their exception category without requiring repeated failures
to remain compiled: those paths deliberately transfer to the interpreter. They remain in the
native reachability graph.

Two isolated negative controls must fail native construction with the specific diagnostic and
fixture call trace: an unbounded BigDecimal operation and a reachable `neverPartOfCompilation`.
Network errors, compilation errors, unrelated diagnostics, or an unexpectedly successful build
do not count as a successful control. Positive and negative images share the enforcement flags.
Run the controls with `python3 .github/scripts/jit_gate.py --probes-only`; the full command also
runs the prepared native executable and writes per-case JUnit reports. CI uploads those reports
to Codecov. The Maven publisher requires the native smoke test and both negative controls
before uploading artifacts.

Coverage is collected separately so probes do not change allocation or compilation budgets.
`.github/scripts/coverage_contract.py` requires 100% executable-method coverage for the listed
core access classes, excluding constructors and abstract declarations. It also emits
`build/reports/coverage/gaps.json` with uncovered lines, methods, and branches for every
reported production file. This is a regression gate and a guide for new tests, not a claim
of 100% branch coverage. Codecov receives LCOV and JUnit using the configured token or
GitHub OIDC when that token is unavailable; target-qualified JVM test identities
keep correctness and compiled runs distinct. The source-policy test requires review when a
new exported interop surface or message is introduced. The native gate also compares every
case and its expected outcome against `benchmarks/jit-cases.json`.

The access inventory includes `NativeRow` and its nested arrays. The exact SVM-only
`NativeRow.entry(Tape,int)` pointer helper remains visible as uncovered in JVM LCOV and is the
sole named platform exception for that source; native-view acceptance and the shared consumer
exercise its pointer loads under SVM's JIT checks. JSON's scoped Java API currently requires SVM,
so its compiled acceptance must not be represented as JVM line-coverage evidence.

The initial expanded run covers 603/638 core lines (94.5%). All four typed accessors,
DynamicRecord, and ExactNumber have 100% line coverage. DataLibrary covers 55/59 lines
(93.2%) and every executable method. The 387-case matrix consists of 340 successful
compiled operations and 47 expected rejections, verified on both JVM and Native Image.
The test interpreter metadata uses build-time initialization; applying the old blanket
runtime initialization policy to the benchmark package prevented the new root from being
usable by the native JIT. The benchmark now keeps only its workload holder initialized at
runtime, and prints the runtime-compilation method inventory for audit.
