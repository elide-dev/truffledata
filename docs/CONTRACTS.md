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

Pass the parsed `FileDescriptorSet` to `ProtobufAdapter.fromDescriptorSet(language, set, "package.Message", limits, maxDepth)`. Use the returned adapter's `descriptor()` when decoding dynamic messages; descriptor identity is deliberately enforced. The data runtime consumes compiler output rather than implementing another Protobuf parser.

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

SQLite's affinity does not prove a storage type, width, or length. `JdbcContracts.describe(..., SQLITE, ...)` is conservative. `SqliteSource.execute` requires a known `StaticLayout`, adapts SQLite integer booleans strictly from 0/1 and BLOBs into unsigned-byte arrays, and validates each value. The application supplies a JDBC connection; this library does not manage database credentials or pool connections. The example uses Xerial SQLite JDBC and its actual native SQLite engine, not an emulation. SQLite JDBC is a test runtime dependency, not a mandatory dependency for other JDBC consumers.

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
JDBC iteration avoids an intermediate columnar batch; remaining allocations and
copies must be evaluated against that ownership guarantee.
