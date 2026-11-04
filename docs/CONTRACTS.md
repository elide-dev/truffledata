# Known schemas and static representations

A schema is immutable evidence supplied by an adapter or application. Every value is checked before it enters a snapshot. A live `DynamicRecord` checks on every read, including typed integer reads, because its backing object can change. SQL remains opaque: no query parsing or planning is involved.

`Field` describes presence, nullability and kind. Its optional `Constraints` describe integer width/sign and inclusive value ranges, float32/64 representability, inclusive length bounds, array element contracts, named record types, or exact decimal precision/scale. Missing facts mean unknown. String lengths explicitly count UTF-16 code units or Unicode code points; array lengths count elements. Zero length is a valid exact bound. Null and missing values remain distinct and do not satisfy a non-null length operation.

```java
Field age = Field.requiredLong("age").withConstraints(Constraints.NONE
    .withIntegerRange(new Constraints.IntegerRange(0, 120)));
Field id = Field.integer("id", 32, false, false, false); // UINT32, present and non-null
Field name = new Field("name", Field.Kind.STRING, false, false,
    Constraints.NONE.withLength(new Constraints.Length(1, 40, Constraints.LengthUnit.CODE_POINTS)));
Field samples = new Field("samples", Field.Kind.ARRAY, false, false,
    Constraints.NONE
        .withLength(new Constraints.Length(8, 8, Constraints.LengthUnit.ELEMENTS))
        .withElement(Field.integer("sample", 16, true, false, false)));
Schema contract = Schema.of(id, name, samples);
```

A `StaticLayout` belongs to an initialized Truffle language. Build it once for a known contract and reuse it. It uses final byte/short/int/long/float/double/boolean properties where the contract permits, with storage narrowed further by explicit integer bounds when possible, and separate presence bytes for nullable/optional fields. Unsigned values use narrow storage and unsigned extension on reads; UINT64 uses lossless `ExactNumber`. Object properties hold strings, arrays, exact decimals and nested rows. These representations stay language-neutral through Truffle interop.

`ReadLongNode`, `ReadDoubleNode`, `ReadBooleanNode` and `ReadLengthNode` have bounded three-entry specialization caches. Exact validated lengths can fold to constants. Property handles, schema identity, width and presence are stable compiler inputs; data contents are never marked compilation-final merely because a schema is known. All externally supplied values still pass validation. Length/width declarations are contracts, not unchecked compiler assumptions.

## Protobuf

Generated Java messages supply descriptors directly:

```java
var adapter = new ProtobufAdapter(language, MyMessage.getDescriptor(), Limits.DEFAULT, 16);
AbstractRow row = adapter.snapshot(message); // StaticLayout.Record
```

The adapter compiles a bounded descriptor graph once per instance, including recursive and nested message types. With a language owner, every message descriptor has a static layout, and nested messages use those layouts too. Without a language owner, the existing constructor retains immutable columnar snapshots. `snapshot` now returns `AbstractRow` to expose either representation; callers explicitly typed as `Batch.Row` must update.

Descriptor-derived facts include signed/unsigned numeric widths, float32/64, scalar defaults, null exclusion, independent optional presence, repeated element types, byte-array UINT8 elements and named nested records. Optional fields and oneofs preserve absence; absent required proto2 fields fail validation. Protobuf itself does not impose fixed string/repeated lengths. Applications can supply such refinements through the constructor's `Map<String, Constraints.Length>`, keyed by fully qualified field name. Unknown fields and incompatible kinds/units are rejected.

For `.proto` sources, use the normal Protobuf compiler and include imports:

```sh
protoc --include_imports --descriptor_set_out=model.pb model.proto
```

Pass the parsed `FileDescriptorSet` to `ProtobufAdapter.fromDescriptorSet(language, set, "package.Message", limits, maxDepth)`. Use the returned adapter's `descriptor()` when decoding dynamic messages; descriptor identity is deliberately enforced. This is the compatibility tabular adapter. The lossless path below also accepts wire bytes directly, without constructing protobuf-java messages.

## JDBC and SQLite

Known query contracts can use static rows directly:

```java
var layout = new StaticLayout(language, contract, Limits.DEFAULT);
try (var cursor = JdbcSource.executeStatic(connection, sql, parameters, layout, Limits.DEFAULT)) {
    AbstractRow row;
    while ((row = cursor.nextRecord()) != null) {
        // Retained static records survive cursor closure and work in any interop guest.
    }
}
```

Guest iteration also yields static rows when the cursor was opened with `executeStatic`. The batch API remains columnar; do not mix batch and record/iterator consumption. JDBC static iteration decodes one row directly into its static layout, with one-record lookahead for idempotent guest has-next calls; it creates no intermediate columnar batch. Batch consumption remains a separate mode. The connection is borrowed and stays open; result sets and statements close on exhaustion, failure, or explicit cursor closure. Custom decoders and statement configuration remain available.

`JdbcContracts.describe(metadata, MYSQL|POSTGRESQL, limits)` is opt-in metadata cooperation. It interprets numeric widths/sign, nullability, string precision as a maximum code-point length, and decimal precision/scale. Unknown widths or kinds remain unconstrained. Supply an explicit schema for query refinements (such as non-null projections), arrays, unreliable driver metadata, or application-specific conversions. Defaults do not inspect SQL.

SQLite's affinity does not prove a storage type, width, or length. `JdbcContracts.describe(..., SQLITE, ...)` is conservative. `SqliteSource.execute` requires a known `StaticLayout`, adapts SQLite integer booleans strictly from 0/1 and BLOBs into unsigned-byte arrays, and validates each value. The application supplies a JDBC connection; this library does not manage database credentials or pool connections. The example uses Xerial SQLite JDBC and its actual native SQLite engine, not an emulation. The separate `truffledata-jdbc-sqlite` integration declares Xerial SQLite JDBC as a dependency; generic JDBC consumers do not inherit it.

## Executable integration samples and budgets

`tests/.../IntegrationTest.java` runs generated `google.protobuf.Type` messages and a serialized descriptor-set round trip through static nested records into JavaScript and Python with host access disabled. The same test runs MySQL/PostgreSQL-shaped metadata/result fixtures through parameter binding, decoding, schema validation, static storage, closure and guest reads. Those fixtures do not claim real-server or real-driver compatibility. The SQLite test opens an actual native in-memory SQLite database and proves rejection of a boolean value SQLite itself accepts.

`ContractsTest` covers width/sign limits, float narrowing, decimal bounds, typed/exact-length arrays, nullable/optional primitive properties, and dynamic mutation after specialization. The compiled allocation gate exercises mixed static integer/float/boolean/exact-length reads under the existing 10,000-node Graal graph limit, with at most 256 AST nodes, at most 0.05 bytes per retained read, and at most 256 bytes per four-field static-record ingestion. Structural budgets and descriptor/value recursion limits remain enforced.

```sh
bazel test //tests:test //tests:compiled //tests:allocation
```

## Strict diagnostic mode

Run the embedding JVM with **both** `-ea -Dtruffledata.strict=true` to audit
`DataLibrary` exports, including accesses made by JavaScript/Python through row
interop. Configure these before initializing Truffle; this is a process-wide test
setting, not a guest-controlled or dynamically switchable option. The correctness
suite enables both flags. Compiled/allocation gates leave the property disabled to
measure the production path while retaining other JVM/Truffle assertions.

The implementation uses Truffle's `GenerateLibrary.assertions` wrapper. Strict mode
checks returned values against field nullity, presence, type, width, range, length
and nested element constraints; primitive messages also audit their fallback
results. Row schema identity and required presence are checked. Each requested
value accessor executes once, so diagnostics do not reread mutable sources. A
contradiction raises `AssertionError`; data values are not included in diagnostics.
Checks run behind a diagnostic Truffle boundary and can allocate/box, so strict
runs are not representative throughput measurements. Without JVM assertions,
Truffle does not install the wrapper; without the property, its diagnostic work
is disabled. Native Image embeddings must configure assertion support and the
property at the appropriate initialization stage; runtime toggling of a setting
captured during image construction is not supported.

Strict mode supplements the mandatory ingestion validation and mutable-source
checks; disabling it does not authorize invalid inputs. It currently audits field
results and row schema identity, not every ownership/lifetime invariant or an
arbitrary third-party export's purity. Snapshot construction still copies caller
owned mutable storage where required to preserve retained-row semantics. Static
JDBC iteration avoids an intermediate columnar batch. Batch JDBC ingestion fills
columns through `Batch.read`, validating each cell once without intermediate row
arrays. Its synchronous source is not retained, and column storage is never exposed
for mutation. Capacity is checked against structural limits before allocation and
retained without trimming/copying, so a short final batch retains unused capacity.
Nested payload limits still apply while reading. These per-batch limits do not bound
driver buffering or total application retention. Remaining allocations and copies
must be evaluated against that ownership guarantee.


## SQLite driver integration

`dev.elide.truffledata.jdbc.sqlite.SqliteAdapter.INSTANCE` implements the versioned
adapter SPI in the separate `truffledata-jdbc-sqlite` artifact. Pass it and
`JdbcQueryOptions` to `JdbcSource.execute`; an explicit schema and optional reusable
static layout preserve known query facts. Without a contract, SQLite fields stay
nullable `ANY` because affinity cannot establish runtime storage types. Driver
wrappers must support JDBC `isWrapperFor(SQLiteConnection.class)`.

The adapter configures a forward-only/read-only statement, fetch hint and driver
query timeout without changing the borrowed connection's transaction settings.
`requireStreaming` means stepping the native result cursor here; it does not bound
SQLite engine execution memory, temporary sorts, or each driver's native-to-Java
cell conversion. Xerial may materialize a complete BLOB/string before the library
can inspect it. End-to-end payload limits and deadline/cancellation guarantees
remain production acceptance work; `setQueryTimeout` alone is not their proof.

Binary snapshots now use compact `byte[]` storage with unsigned INT64 element
access. Mutable arrays/buffers are copied once into private storage; subsequent
rows can share the immutable `DataArray`. SQLite and Protobuf snapshots use this
representation instead of expanding bytes into boxed object arrays. This keeps
retained data independent of source mutation while reducing ingestion allocation.

## Protovalidate structural annotations

Opt in when constructing a Protobuf adapter, using the final boolean argument:

```java
var adapter = new ProtobufAdapter(language, descriptor, limits, 16, Map.of(), true);
// Or consume protoc --descriptor_set_out --include_imports output directly:
var adapterFromSources = ProtobufAdapter.fromDescriptorSet(
    language, descriptorSet, "example.Order", limits, 16, true);
```

The compiler reads `buf.validate.field` once while creating schemas. Registered custom
options and descriptor-set options retained as unknown fields are both supported. The
latter require the imported validation descriptor; options are decoded as DynamicMessage,
so consumers do not need the Protovalidate Java/CEL runtime just to derive these facts.
Existing constructors keep annotation translation disabled. All imported facts are
still enforced on ingestion in both static and batch representations.

| Annotation | Derived contract |
| --- | --- |
| `required` on a presence-bearing field | Field cannot be missing |
| Signed integer and uint32 `const`, `gt/gte`, `lt/lte` | Inclusive INT64 range intersected with the Protobuf width |
| String `len`, `min_len`, `max_len` | Unicode code-point length, not UTF-16 units or UTF-8 bytes |
| Bytes `len`, `min_len`, `max_len` | Unsigned-byte element count |
| Repeated `min_items`, `max_items` | Sequence element count intersected with the existing structural limit |

These bounds participate in ordinary storage selection: int32 `[0,127]` uses a byte
static property. The same rules apply to nested descriptors. Existing explicit length
contracts are intersected when their units agree; mixing units fails construction
rather than dropping a constraint. Impossible represented bounds fail construction.

`IGNORE_ALWAYS` disables annotation-derived facts, including `required`.
`IGNORE_IF_ZERO_VALUE` on an implicit-presence field makes its rules conditional, so
no unconditional annotation facts are derived. On a presence-bearing field it preserves
the default absent-field behavior. Required implicit-presence fields mean non-default,
not merely non-null; that rule remains external. Reversed numeric bounds express an
excluded interval and are not translated into a contiguous range.

This is **not complete Protovalidate validation**. General CEL, patterns/formats,
float/uint64 bounds, enum membership, collection item/key/value rules, oneof rules,
and implicit-presence non-default requirements still need the caller's validator.
Map cardinality is also external: TruffleData represents map entry sequences, which can
retain duplicate keys in dynamic messages, so entry count is not always logical map size.
Strict mode audits the facts actually represented in TruffleData; it does not add the
omitted validator semantics. A snapshot succeeding does not prove full Protovalidate validity.

Acceptance compiles the upstream v1.1.0 `validate.proto` at commit
`4abe5a0b321404d9c825a6c6ca5e384fd3946018`, SHA-256 verified, through Bazel's pinned
protoc. Tests cover registered and unknown options, Unicode, presence/ignore cases,
excluded ranges, static narrowing, rejected values and wire-decoded batch snapshots.
See [upstream rule definitions](https://github.com/bufbuild/protovalidate/blob/4abe5a0b321404d9c825a6c6ca5e384fd3946018/proto/protovalidate/buf/validate/validate.proto).


## Lossless native protobuf messages

`ProtoPlan` and `ProtoRow` are additive to `ProtobufAdapter`; the original tabular enum-name and optional-field behavior remains available. Plans contain immutable field numbers, ordinals, scalar kinds, explicit presence, declared defaults, oneof groups, map/repeated shape, enum names/numbers and openness, integer widths, and nested type names. Neither a plan nor a native row retains a protobuf-java descriptor or message.

```java
var plan = ProtoCompiler.compile(language, MyMessage.getDescriptor(), limits, 32, maxWireBytes);
var source = ProtoCompiler.bind(plan, MyMessage.getDescriptor()); // bind once
ProtoRow fromMessage = source.snapshot(message);               // MessageOrBuilder, including builders
ProtoRow fromWire = plan.decode(bytes);                         // direct bounded wire parser
ProtoRow fromJson = source.parseJson(json);                     // ProtoJSON
ProtoRow fromText = source.parseText(text);                     // protobuf text format
ProtoRow constructed = plan.newBuilder().set(countOrdinal, 42L).build();
byte[] wire = plan.encode(constructed);
```

For runtime schemas, the `ProtoCompiler.compile` overload accepts a `FileDescriptorSet` and fully qualified message name, with the same language owner and budgets. `ProtoCompiler.bind` verifies the entire reachable graph once and enforces descriptor identity on subsequent snapshots. `ProtoCompiler.snapshot` is a convenient one-shot form; reuse a bound source for repeated ingestion. Schemas containing proto2 maps with closed-enum values use a preflighted serialization/decode compatibility path for source objects: protobuf-java `DynamicMessage` otherwise gives those unknown entries different semantics from wire parsing. This uncommon case incurs a temporary wire copy. Host construction accepts normalized TruffleData values. `uint64` and `fixed64` use `ExactNumber`, including small values; `Values.normalize(BigInteger)` constructs one. Builders are mutable host objects and must not be shared concurrently. Built rows are immutable snapshots, including binary payloads.

Read values using cached `DataLibrary` dispatch, as with other rows. `row.has(ordinal)` reports protobuf presence separately: explicit fields retain their set bit, implicit fields use non-default values, and repeated/map fields use non-emptiness. Absent scalar fields remain readable with declared defaults (including proto2 defaults); an absent message reads `Missing.INSTANCE`. Thus `DataLibrary.isPresent` describes readability, while `ProtoRow.has` describes protobuf presence. Closed-enum unknown numbers remain unknown wire fields; open-enum numbers are returned as signed INT32 values without losing unknown numbers. `FieldPlan.kind() == BYTES` and `DataArray.isBytes()` distinguish binary payloads from numeric repeated fields. Maps remain sequences of typed entry rows with last-key-wins decoding; map construction rejects duplicate keys. Nested message plans are available through `plan.nested(ordinal)`.

The direct binary codec handles proto2/proto3 scalar encodings, packed/unpacked repeated fields, groups, oneofs, recursive messages, singular-message merging, unknown fields, required-field checks, and malformed wire input. Serialization preserves semantics and unknown fields, not the original byte ordering or noncanonical varint spelling. Input is bounded by `maxWireBytes`; depth, cells, and payload budgets are checked before expanding input. UTF-8 strings are validated strictly. Editions, extension ranges, and MessageSet currently fail explicitly when compiling lossless plans. ProtoJSON/text compatibility parsing uses protobuf-java and temporary dynamic builders on the host; input length and lexical nesting are checked first, then the resulting graph receives the same native-row validation. This path does not claim the binary decoder's allocation bound or descriptor-free parsing.

### Compile contracts with protoc and Bazel

```starlark
load("@truffledata//bazel:protobuf.bzl", "proto_contract_library")

proto_contract_library(
    name = "contracts",
    srcs = ["example.proto"],
    proto_path = "path/to/protos",
    java_package = "example.data",
    class_name = "Contracts",
)
```

The protoc plugin `//protobuf:protoc-gen-truffledata` emits ordinary Java literals, including imported/reachable types and proto3 optional fields. It does not embed descriptor blobs or invoke `ProtoCompiler` at runtime. Include imported `.proto` inputs in `srcs` under the same declared source root. Each target emits one registry class; use distinct class/package names for multiple registries.

```java
var model = Contracts.create(language, limits, 32, maxWireBytes);
var plan = model.type("example.Message");
var row = plan.decode(bytes);
```

The same generated class supports static storage with a language owner and batch storage without one. Runtime plan identity remains local to each model/context, with no global descriptor or layout cache. `tests/native_proto2.proto`, `tests/native_proto3.proto`, and `ProtoNativeTest` exercise actual protoc generation, Java compilation, direct decoding, source ingestion, both text formats, and differential round trips against protobuf-java. The shared JVM/Native Image JIT matrix exercises the native row access surface; allocation tests bound retained reads and compact binary ingestion separately.

### Schemaless wire input and native backends

`ProtoWire.decode(bytes, limits, maxDepth, maxWireBytes)` returns an ordered `DataArray` of rows with `number`, `wireType`, and `value`. Duplicate fields stay separate. Varints and fixed-width values expose raw unsigned numeric bits, length-delimited fields remain compact bytes, and groups contain nested wire rows. No schema means no reliable field names, declared signedness, defaults, presence discipline, or distinction between strings, bytes, packed arrays, and embedded messages. Text formats with named fields require a schema.

The direct codec is Java code suitable for JVM and Native Image execution. Buffa/Myna is not a bundled backend. A future optional native decoder should consume the same generated contracts, cross the ABI once per message/batch, and materialize the same native-row semantics. Its ownership contract must keep borrowed Buffa views alive until consumption completes; guest reads must not make a native call per field. Myna can generate direct Substrate VM C bindings and JVM FFM bindings, but those crossings, lifetimes, and platform artifacts need their own tests and measurements before claiming a performance improvement. The current Java path provides the differential oracle and allocation baselines for that experiment.


SVM is the primary target for an optional Myna backend: native parser code must be statically linked, with direct generated C bindings and no runtime shared-library discovery. JVM support may use Myna's JVM binding route where the runtime/platform supports it. Backend selection must retain the Java codec when native support is absent or disabled on either runtime. A malformed input or contract failure must propagate, never trigger a retry through a more permissive parser. Native enablement requires differential tests and allocation/ownership checks on JVM and SVM, including an explicit Java-only fallback run. These are requirements for a future backend, not capabilities of a bundled Buffa implementation today.


### Descriptor-free text input

Generated and runtime-compiled plans expose `parseText(String)` and `parseJson(String)`.
They construct the same owned `ProtoRow` representation as binary decoding, including
static layouts when the plan has a language owner. Parsing stays behind Truffle boundaries;
guest reads continue through the existing cached `DataLibrary` exports.

Text format supports scalar types, numeric/named enums, quoted and concatenated strings,
byte escapes, comments, repeated lists, message braces/angle brackets, proto2 groups,
map entries, required fields, explicit presence, and oneofs. Unknown fields, extensions,
duplicate singular fields, and multiple oneof members are rejected. Duplicate map keys
use the last entry. Extensions and expanded `Any` syntax are unsupported by this parser; the existing model
also rejects schemas declaring extension ranges.

ProtoJSON streams messages directly into contract rows. It accepts proto names
and JSON names (including custom `json_name`, retained by the contract generator),
base64/base64url bytes, quoted integers, integral exponent notation, named/open numeric
enums, repeated arrays, and map objects. Null leaves ordinary fields unset; Value null
is a present sentinel. Null array elements and map values are otherwise rejected, except
for the explicit `google.protobuf.NullValue` enum. Duplicate fields, aliases of the same
field, duplicate map keys, and competing oneof members are rejected.

Well-known types have schema-validated special JSON mappings: wrappers, Timestamp,
Duration, FieldMask, Struct, Value, ListValue and Any. A bare wrapper root rejects null;
use a containing message for nullable wrapper presence. Any requires an explicit payload
allowlist, supplied as `plan.parseJson(json, List.of(payloadPlan))`; unknown type URLs fail.
Registries resolve the type name after the final URL slash without network access. Any
payloads are packed with the bounded wire encoder and share aggregate parsing budgets.
The descriptor-bound compatibility API remains available and does not expose this registry.

Generated contracts can include standard protobuf imports using the Bazel macro's
`proto_deps` attribute with public `proto_library` targets; for example, the WKT test
contracts consume `@protobuf//:timestamp_proto` through `//tests:wkt_proto`. Generation
uses the transitive ProtoInfo import graph, so these plans need no runtime descriptors.

Both parsers reject input exceeding `maxWireBytes / 2` UTF-16 characters before tokenization.
Model cell, string-payload, and message-depth budgets apply to the aggregate result and
are charged during parsing. Tokenization/unescape scratch is bounded by the input limit;
these limits are not exact total-allocation counters. Integer tokens are capped at 128
characters, and large exponents are checked before decimal-to-integer expansion. These
resource limits can deliberately reject excessively padded spellings accepted by protobuf-java.
Malformed input raises `ContractViolation`; errors do not include input values.

`ProtoPlan.decode(ByteBuffer)` parses a buffer's remaining region without changing its
position/limit or making an intermediate whole-input copy. Keep its memory immutable and
alive during the call. Returned rows own their payloads. Native `WireInput` users can call
`BuffaWire.decodeTyped(input, plan)` to enforce the input lease during this operation.
For scoped native typed rows and native schema-aware text ingestion, use the optional
`BuffaPlan` API described in [the Buffa integration](BUFFA.md#typed-and-text-input).

## Generated protobuf getters

The contract plugin also emits an `Access_` class for each message, with package/nesting
separators replaced by `$`. Bind it once to the corresponding `ProtoPlan`, then use
`get_<proto_field_name>`, `has_<proto_field_name>`, and array/bytes
`size_<proto_field_name>`. Both `ProtoRow` and Buffa `TypedRow` implement `ProtoAccess`.
Integer, floating point, and boolean getters return normalized Java primitives;
unsigned 64-bit values retain their exact representation. Construction checks the
structural definition, and reads reject rows belonging to another plan. Borrowed
rows retain their lifetime and thread checks.

Keep the bound accessor in a final guest-node field so partial evaluation can fold
its plan and ordinal. Static-object access with a nonconstant layout uses a boundary
fallback. Generated getters do not perform native calls or descriptor lookup.

## Independent protobuf runtime (unreleased)

The development `truffledata-protobuf-runtime` artifact has no `protobuf-java` dependency.
The existing `truffledata-protobuf` artifact remains the optional compatibility bridge and
build-time generator. Buffa and generated contracts depend on the independent runtime.

Runtime schemas can come from `protoc --descriptor_set_out --include_imports` bytes:

```java
var plan = ProtoDescriptors.compile(language, descriptorBytes, "example.Order",
    Limits.DEFAULT, 32, 1_000_000);
var row = plan.parseText("id: 123");
byte[] wire = plan.encode(row);
```

This reader supports proto2/proto3 types, imports, maps, oneofs, explicit presence and defaults.
It budgets descriptor bytes, cells and decoded strings before allocating their storage, and
caps descriptor nesting at 128. The supplied depth limit controls data nesting. Editions,
extension-bearing message types and MessageSet need another backend. Custom options do not
become assertions through this API; the explicit compatibility adapter remains available for
Protovalidate refinements. Imported option declarations do not themselves require an extension
runtime. This is a contract compiler for descriptor sets, not a `.proto` source compiler.

For flat scalar schemas with up to 64 fields, the generator now emits immutable `Message_*`
records, direct getters, concrete builders, wire-tag parsers and exact-size serializers. Presence
and oneof state are independent of values; unknown wire fields survive encoding. Strings and
bytes are owned, and unsigned 64-bit values retain the schema's `ExactNumber` representation.
Build and decode enforce budgets; reads do not repeat ingestion validation.

```java
var access = new Contracts.Access_example$Point(plan);
var point = access.newBuilder().set_x(123).set_y(456).build();
long x = point.get_x();
byte[] encoded = point.encode();
var decoded = access.decode(encoded);
```

Small primitive-only schemas (up to eight fields) also expose `decodeForProjection`. Call it
inside a Truffle operation and consume the result immediately to let partial evaluation and
escape analysis remove temporary storage. Unknown fields use the ordinary bounded decoder.
The optimization is measured with a compiled allocation gate; it is not a blanket promise that
every caller eliminates allocations.

Generated binary decoders also emit concrete immutable records for nested/repeated schemas
when every reachable message has at most 64 fields. They support recursive message merges,
packed/unpacked repeated values, oneofs, groups, required fields, closed-enum unknowns and
last-key-wins maps. Each graph shares one bounded input cursor. Completed subtrees propagate
retained cell/payload totals, including declared defaults, without revisiting ordinary child
graphs; maps account for their deduplicated retained entries. Parsing limits also count
discarded input. Final scalar fields use their declared widths. Read these records through
direct getters, generated accessors, DataLibrary or interop. Complex records currently use
the generic encoder; concrete builders and fused projection decoding remain flat-only.
Wider schemas retain generic getters and the generic plan codec. Generated nested children
can be installed in a generic builder when they belong to the exact same model.

Borrowed `TypedMessage.encode()` forwards validated binary wire bytes into an owned output
array, preserving unknown fields. Text/JSON views encode their semantic snapshot. Both paths
check the view's lifetime, including after owner closure.
