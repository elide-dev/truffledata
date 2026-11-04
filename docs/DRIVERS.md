# Driver integrations and acceptance

The three integration artifacts cooperate with upstream drivers through `JdbcAdapter`.
They accept borrowed connections (including pool wrappers that implement JDBC unwrap),
retain statement/result ownership, and leave commit/rollback/connection close to the
caller. They do not fork drivers, manage credentials, or plan SQL. Applications may
provide a different adapter through the same opt-in API.

| Artifact | Pinned driver | Observed database version |
| --- | --- | --- |
| `truffledata-jdbc-mysql` | Connector/J 26.10.0 | MySQL 8.4.13 |
| `truffledata-jdbc-postgresql` | pgJDBC 42.7.14 | PostgreSQL 18.6 |
| `truffledata-jdbc-sqlite` | Xerial 3.53.4.0 | SQLite 3.53.4 |

This is the tested development combination, not a claim of compatibility with every
server/driver version. The BOM also manages Protobuf 4.36.1 and the Elide GraalVM
25.5.5-elide.24 API/runtime/language versions. Driver versions remain overridable;
consumer override resolution does not establish functional compatibility for that override.

## Streaming prerequisites

PostgreSQL streaming requires an existing non-autocommit transaction, extended query
mode, and a single statement. The adapter asks pgJDBC's own JDBC query parser to
classify the statement; it does not implement SQL parsing or planning. Statements
are forward-only/read-only, close cursors at commit, and use a positive fetch size.
Preflight rejects an unsupported mode instead of silently accepting eager buffering.
The real-service test observes a live server cursor via `pg_cursors`.

MySQL streaming selects Connector/J's `Integer.MIN_VALUE` fetch mode and requires
`allowMultiQueries=false`. In this mode the positive fetch-size option is not a block
size; Connector/J streams rows. Closing an unread result may drain its remaining
protocol data. An explicit BOOLEAN contract over integer/BIT metadata checks the raw
integral domain 0..1, because `getObject` can otherwise hide a value such as 2 by
converting it to true. Non-streaming operation is an explicit query option.

The adapter's execution hook also handles a Connector/J timeout edge: the driver may
register a streaming result before throwing, without returning a JDBC ResultSet or
assigning it to the statement. Under the driver's connection lock, the integration
closes that result only when its owning query is the failed statement. It preserves
the execution exception and suppresses cleanup failures. This uses Connector/J's
internal protocol API and is covered against the pinned version; driver upgrades
must rerun timeout and unrelated-stream ownership acceptance.

SQLite steps a native cursor and uses conservative nullable ANY metadata. See
[the SQLite contract notes](CONTRACTS.md#sqlite-driver-integration).

These settings do not prove bounded driver packets, cell allocations, database engine
memory, total retained rows, or end-to-end deadlines. Statement query timeouts are
configured. Live tests exercise MySQL/PostgreSQL timeout exceptions and connection
reuse after caller rollback. These are statement-timeout checks, not deadlines across
execution, fetching and cleanup. Xerial 3.53.4.0 implements its statement timeout using
a temporary connection busy timeout (lock waiting), not a CPU/query execution deadline.
See [Xerial's implementation](https://github.com/xerial/sqlite-jdbc/blob/master/src/main/java/org/sqlite/jdbc3/JDBC3Statement.java).
Cross-driver deadlines and cancellation races still require implementation and acceptance. Connections must remain exclusively controlled by their caller
while a query is consumed; do not change transaction settings under an active cursor.

## Value conversion

The server integrations normalize numbers and SQL NULL to core values, snapshot binary
values into compact unsigned-byte arrays, and materialize JDBC arrays with nested
budgets. Array result sets are closed and `Array.free()` runs on success or failure.
Vendor scalar conversion applies to nested array elements too. PostgreSQL PGobjects
become their textual value; UUIDs and JDBC date/time values become strings (JDBC
local date/time values use their local ISO representation). These text conversions do
not expose a richer temporal or vendor-type contract. Unsupported host representations
fail instead of leaking through guest interop. Explicit field contracts still validate
converted values before publication.

Owned payload limits are checked during conversion, but `getObject` may already have
allocated driver values. Scalar conversion does not allocate a per-cell budget object;
compound arrays get scoped accounting. The current allocation gate compares scalar
conversion with the same ResultSet baseline and allows at most 8 additional bytes/cell.

## Cancellation

The adapter-aware execute overload accepts an optional `JdbcCancellation` after the
consumption limits. Create one control per execution. Another thread may call
`cancel()`; the consuming thread remains responsible for closing the cursor with
try-with-resources. The connection remains borrowed, and rollback after cancellation
is the caller's decision. Controls cannot be reused for another execution.

Cancellation before preparation rejects the query without creating a statement.
During execution/ingestion the control invokes `JdbcAdapter.cancel`, which defaults
to JDBC `Statement.cancel`. Between operations it only records the request. Further
reads reject buffered data as well as new fetches, and core cleanup waits for an
in-flight cancellation hook before closing its result/statement. A late request after
cleanup does not invoke the driver. Driver cancellation failures propagate to the
requesting thread; the cancellation flag remains set for the consuming thread.

The pinned MySQL/PostgreSQL integrations pass active sleeping-query cancellation and
connection reuse after caller rollback. SQLite passes native VM execution cancellation
with a test progress callback proving execution had started. These tests do not prove
interruption of every fetch/driver phase. There is a race between entering an operation
and the driver starting it: a driver may ignore cancellation in that window. The
control still rejects results when the operation returns. A cancellation request may
block in the driver, and does not impose a hard execution or cleanup deadline. Idle
cursors must still be closed by their owner; automatic context teardown is not yet
provided. Never concurrently use a borrowed connection for unrelated work during a
controlled query or cancellation.

## Cumulative query limits

Every JDBC cursor uses `JdbcConsumptionLimits.DEFAULT`: 1,000,000 rows, 64,000,000
cells including nested elements, and 256 MiB of UTF-16 string payload. These limits
are independent of `Limits`, which still bounds each batch/static-row snapshot.
The adapter-aware `JdbcSource.execute` overload accepts an explicit
`JdbcConsumptionLimits` after `JdbcQueryOptions` to raise or lower these totals.

Counts persist across advancement and are not refunded when rows are released. A
breach fails ingestion and closes the owned statement/result, leaving the borrowed
connection open. Retained prior rows remain valid. Accounting shares the per-snapshot
payload walk and does not traverse nested values a second time. Rows consumed into
batch lookahead count even before guest delivery; a failed batch is not partially
published. At the exact row limit, the next request probes for EOF; an additional
source row fails before cell decoding. Closing instead of probing is always allowed.

These defaults are a new finite limit on previously unlimited total consumption.
They bound accepted logical payload, not driver buffering, object overhead, arbitrary
precision scalar storage, or later mutation of live custom-decoder views. MySQL close
may drain unread wire data. Query retention leases, physical memory envelopes and
execution/cleanup deadlines remain separate requirements.

## Reproduce the real-service tests

With a local Docker daemon:

```sh
tools/integration/run.sh
```

The script uses pinned MySQL/PostgreSQL image digests, temporary containers, localhost
ports, and test-only credentials, then removes its containers. On macOS it selects the
local Java toolchain; on Linux compilation retains RBE and the service test executes
beside the local databases. The test is `//tests:drivers`, tagged manual, with local
TestRunner execution selected by the runner,
so ordinary remote unit tests do not need database networking.

Acceptance currently covers prepared parameters, metadata widths, static layouts,
JS/Python reads with host access disabled, unsigned MySQL integers, binary values,
PostgreSQL arrays with NULL and JSON, MySQL boolean-domain rejection, early close,
connection reuse, caller rollback, and PostgreSQL streaming preflight. The edge matrix
adds DECIMAL(30,10) precision/scale, supplementary Unicode, 64 KiB compact binary values,
SQL NULL, leap-day dates and microsecond local timestamps in both batch/static storage.
It verifies retention after closing the source/context, parameter mutation isolation,
string/cell budget failures, invalid SQL, and MySQL/PostgreSQL statement timeouts.
Timezone/offset behavior, multidimensional arrays, cancellation races and adversarial
payloads larger than driver buffers remain in the production ledger.

`.github/workflows/check.drivers.yml` runs the same target against pinned services and
uploads JUnit reporting. It then runs `tools/release/consumer.py` to exercise the staged
BOM with Maven and the Bazel-provided Java SDK. The ordinary coverage job covers all
six Java modules. Production Native Image consumers and direct Elide embedding tests
remain separate acceptance requirements; the native benchmark smoke is not their proof.

## Scoped reusable contracts

`JdbcContract` is an opt-in envelope for a schema and its provenance. Capture it from
an actual result's metadata using the same adapter and connection acquisition that
will consume it. Pass an explicit schema to record `Origin.EXPLICIT`, or null to use
adapter inference and record `Origin.DRIVER_METADATA`. Origins describe the schema
as a whole; they do not imply a database proof of query semantics.

```java
try (var scope = new JdbcContract.Scope(connection, "orders-schema-v3/session-v1")) {
  JdbcContract contract;
  try (var statement = connection.prepareStatement(sql);
       var result = statement.executeQuery()) {
    contract = JdbcContract.capture(adapter, scope, result.getMetaData(), schema, limits);
  }
  var guarded = contract.guardedAdapter();
  try (var cursor = JdbcSource.execute(connection, sql, parameters,
      contract.schema(), layout, limits, guarded, options)) {
    // Consume through the ordinary batch/static/guest interfaces.
  }
}
```

The capture query above is an ordinary database execution, not a dry run or SQL
analysis; bind its parameters when required. Applications can instead capture from
metadata of a result they already own. No connection, statement or result ownership
is transferred. `layout`, if supplied, must use `contract.schema()` by identity.

The guard checks connection identity before preparation and the ordered column labels,
JDBC types/type names, nullability, signedness, precision and scale before decoder
creation. Duplicate labels are preserved by ordinal. Explicit schema names can differ
from captured labels; changing those captured labels still rejects reuse. Unknown or
unstable driver metadata can conservatively reject an otherwise valid query. Metadata
errors propagate with ordinary statement/result cleanup. SQL, parameters and metadata
values are not included in contract rejection messages.

Create a scope per borrowed connection acquisition and caller-defined revision. Close
it **before** returning a pooled connection or changing session/schema assumptions;
construct a fresh scope/contract after the change. Closing is idempotent, releases the
scope's connection reference, and does not close the borrowed connection. Already
admitted cursors retain their immutable schema and continue ordinary value validation;
use cancellation separately to stop execution. Scope closure racing admission may
reject decoder creation. This is explicit lifetime cooperation, not automatic DDL,
transaction, pool or session-change detection. Equal metadata does not establish equal
query semantics. Every value is still checked against the selected schema.

The wrapper preserves the underlying adapter's prepare, execute, decode and cancellation
hooks. Signature checks allocate only at execution admission; they add no work to
retained guest reads. SQLite tests cover identity, revisions, lifetime and invalid values;
real MySQL/PostgreSQL tests cover inferred contracts, alias/type/length drift rejection
and successful borrowed-connection reuse after rejection.

## Host lifecycle cleanup

Register `JdbcCursor.closeFromHost()` with an embedding's resource lifecycle when a
cursor can be abandoned by its guest. The host method may run on a different thread
and serializes cleanup after an active cursor operation. Ordinary `close()`, batch
advancement and guest iteration still require the creating thread. Retained immutable
rows do not acquire this lock and remain readable after cursor cleanup.

Request `JdbcCancellation.cancel()` separately before host cleanup if execution/fetch
may be blocked. Host cleanup waits for the operation and cancellation hook to finish;
it is not a hard timeout and can itself block in driver cleanup. Resource registration
must handle the race between query creation and context shutdown, and the embedding
must close any contract scope before releasing its connection acquisition. The host
method alone does not install automatic context cleanup or pool ownership.
