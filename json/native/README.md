# Native JSON decoder foundation

The initial C++ decoder traverses simdjson On-Demand directly into Myna ABI v3,
without an intermediate DOM. It accepts all root kinds, rejects decoded duplicate
keys, validates number grammar without restricting numeric range, and retains raw
string/key spellings alongside the index. Every field is visited before publication.

Each document currently copies input into a padded allocation and retains its own
parser. Long decoded strings borrow that parser's scratch; numeric tokens borrow
the retained input. The document cannot be copied or reparsed. This establishes
correct retained-region lifetimes before adding parser pooling or adopted inputs.
Returned descriptors and lexical views are valid only while the document lives.

Run `bazel test //json/native:test //json/native:api_test //json:bindings`. Tests check malformed values, trailing input,
escaped duplicate keys, exact huge exponents, decoded Unicode, raw lexical text,
independent documents, container offsets, and node/text/depth limits. Allocation
checks inject failure at every index allocation and verify cleanup, including
constructor failure and invalid input.

`storage_limits` caps logical input bytes before copying and index allocations
before requesting memory. Myna nodes, regions, lexical spans, traversal storage,
and duplicate-key sets share the index budget. Reallocation overlap contributes
to the peak. Statistics distinguish copied/padded input, retained/peak requested
index bytes, index allocation count, and the parser's public input capacity.
These counts exclude allocator metadata. The parser owns its scratch allocations;
its input capacity is explicitly not presented as scratch bytes.

Run the native memory checks under both sanitizers with:

```sh
bazel test //json/native:test //json/native:api_test --copt=-fsanitize=address,undefined \
  --copt=-fno-sanitize-recover=all --linkopt=-fsanitize=address,undefined
```

Both the adapter and simdjson are instrumented by this invocation.

This is an internal foundation, not yet a published Java API. Remaining work includes
parser scratch accounting,
native input leases, JVM scoped views, snapshots, typed fixed slots, guest interop,
and compiled access/allocation gates.
Myna's current node/text/depth limits do not constitute a total native-memory budget.


## Native interface

`json.seam` defines the versioned C layout and generates JVM FFM and direct SVM
bindings. `api.h` documents status codes and caller obligations. Decode publishes
an owning `JsonResult` only after validation, including a pointer to the immutable
Myna descriptor. Reads need no further native calls. The caller must validate the
binding fingerprint and Myna view version before reading their layouts.

Initialize results to zero, never copy their ownership, and release each result on
the same storage. Releasing that storage again is harmless. A live result cannot
be overwritten by another decode. Input can be discarded after decode returns;
the result retains its own padded copy, index, and parser scratch. Returned raw
views are invalid after release. Thread confinement and invalidation checks belong
to the forthcoming Java owner, not the raw C pointer interface.

The ABI currently accepts a maximum requested nesting depth of 128. Failures map
to argument, limit, allocation, syntax, or internal-error statuses; exceptions do
not cross C. Statistics additionally report the fixed control allocation separately
from input and index storage. Parser scratch remains excluded from byte totals.
Native ownership/error tests pass under ASan/UBSan. Real consumer tests run with
`bazel test //json/tests:native_test //json/tests:jvm`: the first statically links
the parser into SVM and reads through Myna's `NativeValueTape`; the second calls the
shared library through generated JVM FFM handles. Both check the binding fingerprint,
retained input lifetime, exact numeric text, and release behavior. These are ABI
integration tests, not yet scoped guest/JIT acceptance or performance comparisons.
The existing native CI job explicitly includes the SVM target; JVM acceptance runs
with the ordinary `//...` test suite.


## Initial scoped SVM API

`JsonSvm.decode(byte[])` copies UTF-8 input into a retained native document and
returns a thread-confined `JsonMessage`. Its root and descendant `JsonValue` views
check the message before each native read. Close is idempotent on the owning thread;
cross-thread access and closure fail. Exact number text is preserved, including
exponents beyond bounded decimal representations. The SVM integration test covers
retained children, input mutation, thread rejection, and invalidation after close.
The API compiles with the repository's NullAway/JSpecify and Truffle processors.

This initial API uses ordinary lifetime checks; compiled guest specialization and
assumption-based guards still need their own tests. Array lookup currently walks
child subtrees and should not be treated as constant-time random access. Snapshots,
interop exports, configurable Java limits, and the JVM scoped backend remain pending.

## Typed scalar foundation

`typed.h` adds reusable closed-record scalar plans and directly populated slots.
It does not construct a generic Myna tape. Plans own their field names and support
up to 64 fields with separate presence/null masks, required/nullable contracts,
signed integer ranges, unsigned maxima, decoded UTF-8 byte limits, UTF-16/code-point
length bounds, and exact binary32 representability. Integer
kinds require integer JSON syntax; float64 explicitly uses checked finite binary64
conversion. The generic path remains the lossless arbitrary-number route.

Every accepted field is consumed and validated, escaped duplicates and unknown
fields are rejected, and trailing input is rejected. Typed strings retain their
raw lexical spelling. Results retain their own input/parser independently of the
plan. Index accounting covers the single slot-buffer allocation; parser scratch
and plan construction allocations are separate. The caller must keep a plan alive
only during decoding, not during later result access.

`//json/native:typed_test` exercises these contracts, integer boundaries, all 64
presence bits, resource limits, and retained lifetimes; it also passes ASan/UBSan.
JSON application ABI 3 exposes reusable plan creation/release and typed decode/release,
with Myna-generated 80-byte field descriptors and 40-byte scalar slots. The result
publishes masks, slot count, and allocation metrics without copying a second slot
array. Plans copy field names under a name-byte limit. Completed results can outlive
their plans. These operations explicitly retain normal VM transitions.

Native ABI tests and ASan/UBSan cover malformed schemas, bounds, ownership, result
reuse, and release order. Actual SVM acceptance calls generated typed bindings,
closes the plan, and reads a slot through generated direct accessors. Core Java schemas now compile through `JsonScalarPlan.compile(schema)` into a
reusable thread-confined SVM plan. `plan.decode(utf8)` returns a scoped
`JsonScalarRecord`: primitive getters read native slots directly, while strings
and unsigned `BigInteger` values materialize on request. Results outlive their
plans and reject cross-thread and post-close reads. Unsupported nested, repeated,
and general exact/decimal contracts are rejected explicitly. Generated
schema-specific getters, JVM typed views, compiled guest acceptance, and
performance comparisons remain outstanding.

The shared `//bazel:unicode` simdutf library serves Rust Buffa conversion and C++
JSON contracts. Counting decoded UTF-8 requires no intermediate UTF-16 buffer or
Java string. `//json/native:unicode_test` links both consumers and exercises raw
and escaped supplementary characters, long strings, length boundaries, and
float narrowing under ASan/UBSan. Typed slot allocation remains one buffer; this
is not a claim about parser scratch or total process allocation.
