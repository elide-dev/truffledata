# TruffleData

An embeddable, query-informed data runtime for Elide. Databases execute SQL; this project does not parse SQL, plan queries, or translate guest callbacks into queries.

The adapter supplies a result schema, optionally refined by an application contract. Ingestion validates that contract before exposing immutable batch rows. Truffle specializes access to the resulting representation. A separate live adapter supports Truffle dynamic objects, and a language-owned static layout supports immutable records.

## Build

Use Bazelisk; `.bazelversion` pins Bazel. **Bazel is the only build system.** Maven coordinates are resolved by `rules_jvm_external`, not a Maven build.

```sh
bazel test //...
bazel build //core:core //jdbc:jdbc //protobuf:protobuf
bazel test //tests:compiled --nocache_test_results
```

The default configuration uses BuildBuddy remote execution and remote caching on Linux amd64. JDKs, Java compilation toolchains, native helper toolchains, Go, and the remote executor image are pinned. It follows Komodo's setup, including a case-sensitive output cache on macOS. It does not use `JAVA_HOME` to select the compilation or test JDK.

BuildBuddy credentials belong in your user Bazel configuration, never in source control. The profiles are `remote` (default), `buildbuddy` (cache/BES settings), `local` (explicit host execution without remote services), `ci`, `workflows`, and `untrusted` (separate PR cache namespace). See [build details](docs/BAZEL.md).

## Maven artifacts

[Version 0.1.0](https://github.com/elide-dev/truffledata/releases/tag/v0.1.0) is published at `https://maven.elide.dev`: `dev.elide:truffledata-{core,jdbc,jdbc-sqlite,jdbc-mysql,jdbc-postgresql,protobuf}`, plus `truffledata-bom`. Libraries include source jars and consumer POMs. See [publishing and consumption](docs/PUBLISHING.md) for the staging command, explicit upload command and repository setup.

## Known schemas and static data

See [contracts and executable integration samples](docs/CONTRACTS.md) for Protobuf descriptor graphs, static query contracts, width/length facts, and native SQLite support.

## Embedding JDBC

```java
Schema result = Schema.of(Field.requiredLong("a"));
try (JdbcCursor cursor = JdbcSource.execute(
    connection, "SELECT a FROM t WHERE a > ?", List.of(100L), result, Limits.DEFAULT)) {
  Batch batch;
  while ((batch = cursor.nextBatch()) != null) {
    Batch.Row row = batch.row(0);
    // Retained rows stay valid after advancing or closing the cursor.
  }
}
```

The supplied connection remains owned by the caller. The cursor owns its prepared statement and result set. SQL parameters use JDBC binding. A null contract requests conservative JDBC metadata inference. Explicit contracts bind by ordinal, so aliases and duplicate database labels are supported without reparsing SQL. Duplicate labels require ordinal access.

The extended overload accepts a `Decoder` and `StatementConfigurer`. These are the driver-cooperation points for Elide: configure fetch behavior, implement exact conversions, or supply a result-specific schema. PostgreSQL cursor fetching and MySQL streaming have driver/transaction requirements; the adapter never changes transaction policy on a borrowed connection. Local batching does not bound a driver's internal buffering.

Every contract is validated in this prototype. There is no unchecked “trusted” escape hatch. A null, missing field, unsupported value, or incompatible numeric conversion fails rather than manufacturing a default. Cursors are single-pass and thread-confined; batch and iterator consumption cannot be mixed. Closing a cursor releases pending rows, but already returned rows remain usable.

## Guest access

Elide can put a row or cursor into a guest binding without enabling Java host access. Rows implement member and ordinal access; JDBC cursors implement iterable/iterator messages and an explicit `close()` operation.

```js
for (const row of rows) {
  consume(row.a);
}
// Close explicitly when stopping early.
rows.close();
```

```python
for row in rows:
    consume(row.a)
```

The internal `DataLibrary` is for cooperating implementations. `ReadLongNode` provides a primitive read with a bounded schema/ordinal cache. It belongs beneath an adopted Truffle root or parent node; it is not a host-side replacement for the public interop API.

## Representations

- **Batches:** validated snapshots with primitive `long[]` columns for INT64, optional state arrays for null/missing values, and object storage for other kinds. Creating a row view allocates; reading a retained INT64 row does not allocate per element in the compiled acceptance workload.
- **DynamicRecord:** a live view over a `DynamicObject`. Cached `GetNode`/`ContainsKeyNode` operations track shape changes. Reads validate mutable values; a schema assertion is never permission to assume an arbitrary mutable object remains non-null. The owner must confine access to its language/context thread. Values should use core canonical representations.
- **StaticLayout:** built with the embedding language's `TruffleLanguage` instance. Validated widths/ranges select final narrow integer properties; float32/64 and boolean fields also use primitive storage, with independent presence bytes for optional/null values. Other fields use final object properties. Storage is private and initialized once. Property handles are cached by layout and ordinal.
- **ProtoPlan / ProtoRow:** lossless protobuf contracts and native messages, with separate protobuf presence, numeric enums, compact bytes, direct binary decoding/encoding, runtime descriptor compilation, and a Bazel/protoc contract generator. Bound sources accept `MessageOrBuilder`, ProtoJSON, and protobuf text format; schemaless wire inspection is also available. See [lossless protobuf contracts](docs/CONTRACTS.md#lossless-native-protobuf-messages).
- **ProtobufAdapter:** snapshots immutable `Message` values through descriptors. Explicit absence stays distinct from null; implicit scalar fields expose their default values. Repeated fields become sequences, maps become sequences of entry records, and `oneof` presence is retained. Enum values expose their descriptor names. UINT32/FIXED32 widen to long; UINT64/FIXED64 use an exact numeric wrapper. With a language owner, the descriptor graph selects static layouts for every message type. Nested values have depth, cell, and string budgets.
- **DataArray:** a sequence with no column schema, usable for positional or scalar data.

Java null is confined to annotated ingestion boundaries. `DataNull` is the interop null object; `Missing` is an internal presence marker. Reading a missing field through interop raises the appropriate unknown-member/index exception. A present null remains readable.

`ExactNumber` preserves BigInteger/BigDecimal values and only advertises conversions that are exact. This avoids unsigned overflow and decimal rounding; guest arithmetic support is language-dependent. Date/time, binary JDBC objects, LOBs, vendor types, and RPC execution need explicit adapter policies. Unsupported JDBC objects fail rather than being leaked as arbitrary host objects.

## Acceptance checks

- JSpecify package defaults and NullAway in JSpecify mode on owned Java libraries, with compiler warnings treated as errors.
- Positive and negative compiler probes for nullness enforcement.
- Contract, presence, numeric precision, cursor lifetime, budget, shape-mutation, and cache-fallback tests.
- GraalJS and GraalPy interoperability without host access.
- Compiled Truffle acceptance with synchronous compilation, compilation failures and performance warnings treated as errors, and a 10,000-node graph budget.
- Allocation limits for retained reads, ingestion, and retained row creation. See [budgets and scope](docs/TRUFFLE.md).
- [Continuous CodSpeed simulation](docs/BENCHMARKS.md): 20 fixed-work Native Image workloads, independently checked alongside JVM JIT/allocation gates.
- Gazelle generation for source-set targets. Source membership is generated; dependency boundaries are explicit in `bazel/gazelle/sources.json`.

This is a foundation, not an Elide production integration. The database tests use H2, native SQLite, and MySQL/PostgreSQL-shaped JDBC fixtures; real MySQL/PostgreSQL driver matrices, broader Native Image deployment integration, cancellation, asynchronous/RPC execution, and JSON/CSV adapters remain production follow-ups. There is no SQL planner dependency.

## Optional native wire backend (0.2.0 development)

[The Myna/Buffa integration](docs/BUFFA.md) adds borrowed wire and typed decoding, native schema-aware text parsing, explicit off-heap ownership, JVM FFM and static SVM linkage, with Java fallback. Build it with `//protobuf-buffa`; it is not included in the 0.1.0 Maven artifacts.
