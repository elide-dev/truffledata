# Production acceptance ledger

This ledger tracks the full productionization goal. An unchecked item is not a shipped guarantee.
The intended first production scope is bounded synchronous query consumption with MySQL,
PostgreSQL and SQLite integrations plus Protobuf, embedded in Elide. SQL stays opaque;
the database owns planning. CSV, RPC execution and asynchronous delivery are subsequent scopes.

| Requirement | Required acceptance evidence | State |
| --- | --- | --- |
| Adapter SPI and three driver artifacts | Versioned SPI, explicit overrides, concrete driver dependencies, Gazelle targets and publication consumer tests | In progress |
| BOM | Published dependency management with tested driver/library combinations | Pending |
| Real-driver matrix | Actual MySQL/PostgreSQL services and SQLite; values, streaming, prepared statements, failures and connection reuse through JS/Python | Pending |
| Lifecycle | Deadlines, cancellation races, early close, context shutdown, borrowed/owned connections and pool reuse tests | Pending |
| Memory | Bounded stream decoding, aggregate consumption/retention policies, large payload and adversarial tests including driver buffering | Pending |
| Contract provenance | Explicit vs metadata origins, revision/lifetime policy, projection/session/schema compatibility tests | Pending |
| Consumer Native Image | Packaged library/driver metadata, schema-specific Protobuf generation, real-driver native executables on supported platforms | Pending; existing native benchmark is fixture-specific |
| Truffle gates | Nullable, polymorphic/megamorphic, nested, exception and context-ownership cases; graph/allocation budgets | Pending extensions; existing primitive gates pass |
| Performance comparisons | Equivalent plain-JDBC baselines; warmed JIT elapsed-time suite separate from CodSpeed simulation | Pending |
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
