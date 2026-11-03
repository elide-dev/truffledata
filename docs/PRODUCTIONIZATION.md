# Production acceptance ledger

This ledger tracks the full productionization goal. An unchecked item is not a shipped guarantee.
The production goal is the shared TruffleData layer: static/dynamic/nested object models,
nullity and presence contracts, bounded specialization across shapes and contexts, allocation
and retention, Protobuf and consumer Native Image support, stable APIs/diagnostics, and
actual Elide embedding. JDBC is one integration use case, already covered extensively.
Additional database pooling, deadlines, server benchmarking and session automation are
deferred unless they expose a shared-layer defect. SQL remains opaque to this library.

Shared-layer acceptance still required:

- Compiled nullable, polymorphic/megamorphic, nested, mutation and multi-context gates.
- Cross-representation ownership/retention and allocation evidence, beyond JDBC ingestion.
- Consumer Native Image support including generated and dynamic Protobuf schemas.
- API/binary compatibility, bounded diagnostics, support matrix and release provenance.
- Actual Elide embedding with host access disabled and context isolation.

The table below retains prior implementation history; database-specific open items are
not the governing definition of shared-layer production readiness.

| Requirement | Required acceptance evidence | State |
| --- | --- | --- |
| Adapter SPI and three driver artifacts | Versioned SPI, explicit overrides, concrete driver dependencies, Gazelle targets and publication consumer tests | Initial alpha.3 SPI/artifacts published; compatibility gate pending |
| BOM | Published dependency management with tested driver/library combinations | Published in alpha.3; staged consumer and public bytes verified |
| Real-driver matrix | Actual MySQL/PostgreSQL services and SQLite; values, streaming, prepared statements, failures and connection reuse through JS/Python | Initial real-service cases verified; broader edge/failure matrix pending |
| Lifecycle | Deadlines, cancellation races, early close, context shutdown, borrowed/owned connections and pool reuse tests | Opt-in active-execution cancellation and core cleanup races tested; fetch interruption, deadlines, context shutdown and pool ownership pending |
| Memory | Bounded stream decoding, aggregate consumption/retention policies, large payload and adversarial tests including driver buffering | Aggregate logical ingestion limits implemented; retention leases, physical memory and driver buffering pending |
| Contract provenance | Explicit vs metadata origins, revision/lifetime policy, projection/session/schema compatibility tests | Opt-in JDBC scope/revision and exact projection guards verified; automatic host/session integration pending |
| Consumer Native Image | Packaged library/driver metadata, schema-specific Protobuf generation, real-driver native executables on supported platforms | Pending; existing native benchmark is fixture-specific |
| Truffle gates | Nullable, polymorphic/megamorphic, nested, exception and context-ownership cases; graph/allocation budgets | Pending extensions; existing primitive gates pass |
| Performance comparisons | Equivalent plain-JDBC baselines; warmed JIT elapsed-time suite separate from CodSpeed simulation | Six real-driver JMH boundaries per driver implemented; full SQLite baseline reviewed, server experiments and allocation optimization pending |
| Elide integration | Real embedding tests for guest capabilities, cleanup and host access disabled | Pending |
| Operational diagnostics | Bounded rows/bytes/error/cancellation/contract telemetry without query/parameter disclosure by default | Pending |
| Compatibility and release | API/binary compatibility gate, support matrix, dependency reporting/provenance and verified publication | Pending extensions |

Existing evidence: alpha.2 CI https://github.com/elide-dev/truffledata/actions/runs/41575194959
passed JVM tests, allocation/JIT gates, Codecov reporting and 20 CodSpeed simulation cases.
It does not prove the unchecked production requirements above.

Performance is the primary acceptance objective. Minimize copies, boxing and repeated validation;
validate once at ownership/ingress boundaries and reuse immutable storage/facts. Add a strict
correctness mode enabled in tests and guest acceptance to audit established facts and cache
assumptions. Mutable live inputs retain guards/invalidation. Measure validation, decoding,
materialization and retention separately. No claimed improvement is accepted solely from an
instruction-count change without equivalent semantics and ownership.

Verified first increment (2026-11-02): direct static JDBC iteration avoids intermediate
columnar batches and reads one row at a time; decoder compilation occurs once per
execution through the new adapter SPI. Strict DataLibrary result assertions are enabled
in the correctness suite, including JS/Python interop and intentionally broken exports.
All 10 ordinary Bazel test targets passed, including compiled/allocation gates,
benchmark smoke and Maven consumer tests:
https://app.buildbuddy.io/invocation/7d91e496-2f6f-4adf-bb6b-d02bcb83827b.
These checks establish correctness and existing budgets, not an end-to-end throughput
improvement or completion of the real-driver/Native Image acceptance matrix.

SQLite integration increment: `truffledata-jdbc-sqlite` now has a Gazelle-generated
Bazel target, a thin Maven publication with explicit Xerial dependency, and a consumer
that runs a real SQLite query against the publication jars. Acceptance exercises
borrowed transaction rollback, wrong-driver rejection, conservative untyped metadata,
invalid storage classes, and static JS/Python access. MySQL/PostgreSQL artifacts and
the BOM remain pending. This SQLite artifact has not yet been published publicly.

Compact binary snapshots replace boxed arrays in SQLite and Protobuf. The allocation
gate includes unsigned-byte contract validation and measures 65,568 bytes for a
65,536-byte payload (one owned byte array plus 32 bytes overhead). Narrower byte
contracts still inspect values. This is allocation evidence, not a throughput claim.
The coverage pipeline now includes the fourth production module; validation found
32 production source records with coverage in all four modules in
https://app.buildbuddy.io/invocation/7aa7a213-098f-4145-878b-8c2a863ea4f8.
Driver cell buffering, native consumers, cancellation/deadlines and total query
retention bounds are still pending acceptance items.
Final verification of this increment, including generated Protobuf binary messages
and equivalent nesting-limit rejection for compact/boxed arrays: all 10 Bazel test
targets passed at https://app.buildbuddy.io/invocation/862c7502-0b59-430f-9406-29db51ada748.


Server integrations and BOM increment (2026-11-02): MySQL and PostgreSQL now have
separate Gazelle targets and thin Maven publications, completing the three integration
artifact implementations. The POM-only BOM manages all six libraries, three upstream
drivers, Protobuf and the tested Truffle runtime/language versions. A repeatable Maven
consumer verifies downloaded staged artifact bytes, compiles/runs using the Bazel SDK,
and proves an explicit pgJDBC version override. These artifacts are not yet public.

`tools/integration/run.sh` passed against MySQL 8.4.13 and PostgreSQL 18.6, including
static JS/Python access, prepared values, PostgreSQL arrays/JSON and a real server
cursor, early close, rollback ownership and connection reuse. It also verifies that
MySQL's TINYINT boolean coercion cannot hide values outside 0..1. The reusable CI job
uses pinned server images, harden-runner, inherited BuildBuddy credentials and JUnit
uploading, with the service test local to its databases and compilation through RBE.
See [driver scope and remaining gaps](DRIVERS.md).

All ten regular Bazel targets passed at
https://app.buildbuddy.io/invocation/032b7b57-4cb9-4d55-bb55-0c073968e539.
The scalar decoder allocation comparison measured 0 additional bytes/cell in the
allocation target (the compiled target also passed its bound). The Protobuf 4.36.1
upgrade passed the Native Image simulation smoke at
https://app.buildbuddy.io/invocation/d5794217-7b8c-45ef-9d89-0612832d2f54;
this remains a benchmark image, not real-driver native acceptance. Six-module coverage
passed at https://app.buildbuddy.io/invocation/9da27b6f-27e5-41fc-866d-5d86becb9e34.
The prior pushed increment's CI, including CodSpeed, completed successfully at
https://github.com/elide-dev/truffledata/actions/runs/41578766603.

Lifecycle failure increment: owned result sets and statements are both closed even
when cleanup throws an unchecked exception or assertion error. Ingestion/initialization
failures remain primary, with independent cleanup failures suppressed directly and
self-suppression guarded. Five adversarial H2 tests cover both execution APIs, static
and batch decoding, idempotence, and borrowed connection ownership. This is not yet
cancellation, deadline, pool or context-shutdown acceptance. All ten targets passed:
https://app.buildbuddy.io/invocation/1f8fed17-4d33-47e7-b783-db2038a3083f.

The scalar allocation fixture now uses a warmed real H2 result through a shared call
site, avoiding dynamic-proxy/tiered-compilation noise. Allocation and compiled gates
each passed three repetitions with the existing budgets unchanged. The updated local
service runner also passed MySQL/PostgreSQL acceptance. The previous CI service job
failed before running tests because its no-remote-exec tag prevented remote compilation;
the corrected runner selects standalone test execution and a local TestRunner strategy
without restricting compilation. Linux CI verification completed successfully, including
Codecov, CodSpeed, real services and the Maven BOM consumer:
https://github.com/elide-dev/truffledata/actions/runs/41583533382.

Direct column ingestion increment: JDBC batch consumption now uses a checked,
synchronous `Batch.read` source to fill owned column storage directly. It removes
intermediate row arrays and the second validation pass; retained rows remain valid.
Capacity is bounded before allocation and deliberately not trimmed, trading unused
slots in the final batch for avoiding another allocation/copy. Both allocation and
compiled gates measured 8,312 bytes for 1,024 required longs against a 9,216-byte
budget. No throughput improvement is inferred from this allocation result.
All ten regular targets passed at
https://app.buildbuddy.io/invocation/e2b09873-2a1c-4cf8-b7b2-8c1a74ea994d,
and the real MySQL/PostgreSQL service runner passed again with direct batch filling.

Real-driver failure/value increment: six live acceptance methods now cover both database
integrations, including exact DECIMAL(30,10), Unicode, 64 KiB binary snapshots, nullable
values, leap-day/local microsecond timestamps, retained batch/static rows, budget
failure cleanup and driver query timeouts. The timeout test exposed Connector/J's
orphaned streaming-result path. An additive default `JdbcAdapter.execute` hook permits
the MySQL adapter to close only a result owned by its failed statement, under the driver
lock, preserving the original error and borrowed connection. A rejected second query
also verifies that another statement's active stream is untouched. The implementation
is tied to the pinned Connector/J protocol API and needs upgrade acceptance.
All ten ordinary targets passed at
https://app.buildbuddy.io/invocation/da2c0cc3-1790-4e09-8d9e-6f4aa361c197.
Xerial's query timeout only bounds lock waiting; cross-driver execution/fetch/cleanup
deadlines and cancellation are still outstanding, not implied by these timeout tests.

Aggregate consumption increment: every JDBC cursor now retains query-wide row, nested
cell and string-byte budgets across batch/static advancement, with finite configurable
defaults. Parent accounting shares the existing payload walk; a test verifies one read
per nested value rather than another traversal. SQLite exercises row/cell/string limits
in all three consumption modes and exact-limit EOF, plus overflow rejection in the
core accountant. All ten targets passed at
https://app.buildbuddy.io/invocation/9ef7c9d1-3a80-42b7-aa12-708ae4e80ac7.
Allocation and compiled budgets remain unchanged. These limits bound accepted logical
payload; they are not a bound on driver buffers or actual retained JVM memory.
Live MySQL/PostgreSQL acceptance also passed with cumulative row exhaustion in batch
and static modes, followed by successful borrowed-connection reuse.
The cumulative-accounting allocation gate passed in both ordinary and compiled modes:
8,280–8,320 bytes per 1,024 required longs against the unchanged 9,216-byte bound,
https://app.buildbuddy.io/invocation/6694f9e9-b178-4233-b3d7-96ef1182f9d4.
The direct-column increment's full CI also passed:
https://github.com/elide-dev/truffledata/actions/runs/41583973197.

Cancellation increment: opt-in single-execution controls register a statement before
execution, permit cross-thread driver cancellation, reject subsequent buffered/static
reads, and serialize core resource cleanup after cancellation hooks. Tests cover
pre-cancelled admission, idle buffered data, reuse rejection, late cancellation, and
in-flight cleanup ordering. Eight live MySQL/PostgreSQL acceptance methods passed,
including active-query cancellation observed through server state and caller recovery.
SQLite native execution cancellation also passed, with a VM progress handler used only
to coordinate the test. All ten ordinary targets passed at
https://app.buildbuddy.io/invocation/3a3f5708-7573-441c-9c34-8d2889f9d495.
No hard deadline or automatic context shutdown guarantee is implied: ignored driver
cancellation, driver-start races, idle owner cleanup, fetch and cleanup phases remain
explicit acceptance work. The previous MySQL recovery increment's full CI passed:
https://github.com/elide-dev/truffledata/actions/runs/41584620950.
An additional cancellation-hook transport-failure test verifies that the requester
receives the driver error while the consuming thread still rejects and cleans up the
query; the correctness suite passed at
https://app.buildbuddy.io/invocation/f8b1d114-82f9-428d-9cd2-ed5b2832f641.

JMH comparison increment: all eleven ordinary targets passed at
https://app.buildbuddy.io/invocation/ef5e3cfb-dce6-4b08-8cf9-87310e018ad3.
SQLite's final compiler graph/performance-warning checks passed at
https://app.buildbuddy.io/invocation/6a697a07-30f1-49c0-a8a1-3207ac855aec.
The local service runner passed eight driver acceptance methods and twelve compiled
MySQL/PostgreSQL benchmark smoke cases. A full clean-commit SQLite run completed
three forks and 24 measured samples for each of six cases; raw results and limitations
are recorded in [BENCHMARKS.md](BENCHMARKS.md#initial-sqlite-elapsed-baseline).
Query timing intervals overlap, and batch/static allocation exceeds plain records;
there is no accepted general speedup claim. The previous cancellation increment's
full CI passed: https://github.com/elide-dev/truffledata/actions/runs/41586275999.

Release increment: `0.1.0-alpha.3` published all six library artifacts and the BOM
from `5c0be85`, with immutable jar/source/POM byte checks against public downloads
and all seven public Maven metadata documents verified. The versioned release build
passed all eleven targets at
https://app.buildbuddy.io/invocation/4770d5f0-ce63-4295-8e5b-afe43087c84c.
The implementation's complete CI, including final graph-budget driver smoke checks,
passed at https://github.com/elide-dev/truffledata/actions/runs/41588045830.
Release: https://github.com/elide-dev/truffledata/releases/tag/v0.1.0-alpha.3.
This makes the integrations consumable; it does not close the remaining production
requirements in the table above. Earlier publication-pending notes describe historical
increments and are superseded by this release evidence.

Direct static ingestion increment: `StaticLayout.read` fills owned shape properties from
a synchronous field source and charges local/parent budgets in the same payload walk.
JDBC reuses one source per cursor instead of allocating a temporary row array and
walking its payload twice. All eleven targets passed at
https://app.buildbuddy.io/invocation/0311e0b2-a3f6-4191-bdc5-cff7fa0b1e84.
The new mixed-record allocation gate measured 88 bytes/row against 192 in ordinary
and compiled modes at
https://app.buildbuddy.io/invocation/f4f13c8b-6bb2-4a0f-9b0b-34b6e9d71df7.
Live MySQL/PostgreSQL acceptance and twelve compiled benchmark smoke cases passed.
Three forked SQLite static-query trials measured approximately 32 fewer allocated
bytes/row; [samples and limitations](BENCHMARKS.md#direct-static-ingestion-follow-up)
are retained. This improves ingestion without completing physical-memory or retention
acceptance, and is newer than the published alpha.3 artifacts.

Scoped contract increment: `JdbcContract` records explicit/driver-metadata origin and
a caller-defined revision, pins reuse to one connection acquisition, and compares
ordered metadata signatures before decoder creation. Scope closure revokes new
admissions without taking connection ownership or invalidating retained snapshots.
All eleven ordinary targets passed at
https://app.buildbuddy.io/invocation/f3672159-d785-4e4f-8c82-2e8051560a6e.
Ten live-driver acceptance methods and twelve compiled benchmark smoke cases passed,
including new MySQL/PostgreSQL projection/type/length drift and connection reuse cases.
This is opt-in caller cooperation; automatic Elide/pool/session invalidation remains
outstanding. [Contract usage and limitations](DRIVERS.md#scoped-reusable-contracts).

Host cleanup prerequisite: cursor operations and `closeFromHost` now serialize through
the cursor monitor. A host/context cleanup thread can close an abandoned cursor while
ordinary guest operations remain owner-thread confined. Tests prove cleanup waits for
an active decoder, aggregates independent cleanup failures, is idempotent, preserves
retained rows, and does not close the borrowed connection. All eleven targets passed:
https://app.buildbuddy.io/invocation/8c0b86b7-0319-47ce-ba04-a34d68e1f878.
This is an embedding prerequisite, not completed Elide context registration or a hard
deadline guarantee. No Elide repository changes have been made yet.

The host-cleanup increment also passed live MySQL/PostgreSQL tests closing a cursor
from a host executor and then reusing the borrowed connection. All ten driver methods
and twelve compiled benchmark smoke cases passed. A three-fork static-query elapsed
check retained the 236.53 bytes/row allocation result; timing intervals overlap the
prior direct-ingestion run. [Samples](BENCHMARKS.md#host-cleanup-serialization-follow-up)
are committed without claiming a general zero-cost synchronization guarantee.

Shared-layer readiness increment: nullable/static, mixed 1/2/4/8-identity dispatch,
retained values across producing-context closure, and nested dynamic mutation/recovery
now run under the existing 10,000-node compiler graph and allocation gates. Native
Protobuf reflection metadata is generated from message classes, replacing the benchmark's
hand-maintained list. All eleven ordinary targets and the actual native smoke executable
passed at https://app.buildbuddy.io/invocation/3d21c451-ec1a-40d2-99e2-4d25b2a7ad3f.
Native compilation used generated metadata at
https://app.buildbuddy.io/invocation/db6a50a2-11d8-470f-a742-1a5ab0454f19.
This improves shared-model evidence; general consumer Native Image, API compatibility,
transitive ownership contracts and actual Elide embedding remain open. Protovalidate
annotation translation is now in scope: supported rules must respect presence/ignore
semantics, while unrepresentable rules cannot become compiler assumptions.

Protovalidate increment: opt-in annotation compilation now derives presence-bearing
required facts, integer intervals and string/bytes/repeated lengths from real upstream
options. Tests use a SHA-pinned v1.1.0 schema compiled with Bazel protoc and exercise
both registered extensions and unknown descriptor-set options. Integer bounds select
narrow static storage; ingestion still enforces them. All eleven targets passed at
https://app.buildbuddy.io/invocation/cbeb753f-713f-4192-a89e-85dab6a88ebd.
The registered-extension and source-descriptor acceptance additions passed at
https://app.buildbuddy.io/invocation/83907085-0e65-43c0-9cc8-e8c51f628bc1.
[Supported rules and explicit omissions](CONTRACTS.md#protovalidate-structural-annotations)
prevent equating these facts with full Protovalidate validation. Native execution of
the annotation-enabled path and broader schema features remain acceptance work.
