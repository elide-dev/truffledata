# Continuous simulation benchmarks

Every main push and pull request runs the reusable
[CodSpeed workflow](../.github/workflows/benchmarks.yml). The initial suite has
20 stable identities in [cases.json](../benchmarks/cases.json); executable
workloads live in [SimulationBenchmark.java](../benchmarks/src/main/java/dev/elide/truffledata/bench/SimulationBenchmark.java).
Bazel builds a Linux amd64 Native Image with the library's pinned GraalVM and LLVM
toolchains through BuildBuddy RBE/RBC. The CI client executes it under CodSpeed
simulation, using native region hooks and action pins shared with Komodo.
Authentication uses GitHub OIDC. The benchmark image declares the generated
Protobuf accessors and JDBC proxy interfaces it uses; these fixture-specific
reachability settings are not general metadata for arbitrary consumer schemas.

Simulation measures AOT/interpreter instruction cost, **not warmed guest JIT
performance**, database latency, throughput, or elapsed time. Guest compilation
is disabled explicitly. The separate JVM compiled-code and allocation gates
remain authoritative for specialization, constant folding and allocation budgets.
JMH remains available for JVM diagnostics.

## Workloads and boundaries

| Cases | Work included |
| --- | --- |
| Required, nullable and wide-projection batch reads | Read 256 retained rows; 50% nulls; project one of 16 columns in the wide case |
| Static required/mixed/exact-length reads | Cached adopted Truffle nodes over primitive properties, mixed fields and known four-code-unit strings |
| Dynamic required reads | Live object access with contract validation |
| Interop member/ordinal reads | Adopted cached interop dispatch for guest-style access |
| Narrow/wide batch ingestion | Normalize, validate and materialize 256 × 1 or 256 × 16 cells |
| Static mixed ingestion | Materialize 256 four-field records, including nullable integers |
| Retained row views | Allocate 256 escaping row views |
| Generated Protobuf batch/static | Snapshot 256 nested generated messages with two repeated field records each; consume names |
| Protobuf wire/static | Decode the same bytes as DynamicMessage, snapshot and consume names |
| MySQL/Postgres fixture batch/static | Bind, execute fixture, normalize Java Integer/null, validate signed/unsigned int32 contracts, ingest four fields, sum IDs and close cursor resources |

Each region performs **eight iterations of 256 rows/messages**. Schemas, layouts,
input data, descriptors, call targets and four warmup iterations are prepared
outside measurement. Each case runs in its own process. The region includes the
Java callback, loop, checksum validation and volatile sinks; allocation cases
use escaping sinks deliberately. GC during the region is included. Reports are
full-region costs; results.json also records instructions per input row/message
(not per cell).

Checksums are independently declared in the manifest and enforced by both Java
and the measurement driver. The driver requires exactly one positive instruction
count per named region, ignoring startup/warmup/shutdown. It fails on missing or
duplicate regions, wrong runner mode, changed prepared binaries, and mismatched
work sizes/checksums. Native hooks independently reject uninstrumented execution
when measurement is requested.

The simulation executable retains symbols but strips DWARF unsupported by the
simulator; its Bazel action checks that the machine-code section is byte-identical
to the original image. CI retains separate debug symbols, logs, the prepared
binary's SHA-256/commit identity, summary and raw profiles for 14 days.

## Elapsed driver baselines

The original four CodSpeed JDBC cases remain driver-boundary mocks. A separate
[JMH suite](../benchmarks/jdbc/src/main/java/dev/elide/truffledata/bench/jdbc/JdbcPipelineBenchmark.java)
now compares real SQLite, MySQL and PostgreSQL drivers. Each driver has six cases:

| Cases | Measured boundary |
| --- | --- |
| `plainQueryRetain`, `batchQueryRetain`, `staticQueryRetain` | Prepare/bind/execute, decode/validate, retain 256 immutable rows, close cursor resources, then checksum all four fields |
| `plainRetainedRead`, `batchRetainedRead`, `staticRetainedRead` | Checksum the same retained rows, with database work and materialization excluded |

The dataset identity is `jdbc-retained-four-field-v1`: 256 rows, required signed INT32
IDs 1000–1255, 50% nullable INT32 values (twice the even row ordinal), an exact
four-code-unit string, and booleans true every third row. The checksum is 322262.
Every invocation checks it. The plain baseline stores immutable Java records and
validates the same nullity, width, string length, boolean domain and row count. It
uses JDBC directly with the same forward-only/streaming/fetch/timeout policies,
including MySQL's raw integer boolean check. All variants retain their output beyond
result-set close; escaping output storage is included in query-case allocation.
The plain retained-read baseline is direct Java access, not guest host-object interop.

Schema/layout creation, database seeding, and initial retained outputs happen outside
measurement. Both Truffle read call targets must demonstrate compiled execution in
trial setup; interpreter-only runs fail. Compilation failures/performance warnings are
errors, and the compiler graph budget is 10,000 nodes. Normal JMH warmup still runs afterward.
Default timing uses three JVM forks, five one-second warmups and eight one-second
measurements. Scores and GC allocation are normalized **per row**, not per query or
field. JMH/GC harness cost, volatile output publication, checksum checks and Truffle
call-target entry remain included. Strict diagnostic mode is rejected for timing.

Run a local elapsed-time experiment with the Bazel-provided host JVM:

```sh
tools/bench/jdbc.sh sqlite
# Uses TRUFFLEDATA_MYSQL_URL or TRUFFLEDATA_POSTGRESQL_URL:
tools/bench/jdbc.sh mysql
tools/bench/jdbc.sh postgresql
```

Optional trailing arguments override JMH timing/fork settings. Results must still
contain all six cases for the selected driver with elapsed and allocation metrics.
The runner writes JSON results, raw JMH logs, commit/dirty state, source SHA-256 and
machine metadata to `build/reports/jmh/<driver>/run.XXXXXX/` (a new directory for each run). Logs include driver/server versions,
dataset identity and checksum. It does not print connection URLs or credentials.
Avoid unrelated CPU/database work while measuring. These are local elapsed timings;
network/server effects belong in the query cases and do not imply guest-language
throughput improvements across other workloads.

`//benchmarks/jdbc:smoke` checks SQLite in ordinary CI. The reusable service job and
`tools/integration/run.sh` also run `//benchmarks/jdbc:drivers_smoke` for MySQL and
PostgreSQL. All smoke cases require compiled Truffle reads and correct outputs, but
their one short, non-forked measurement is **not performance evidence** and does not
gate noisy wall-clock scores. CodSpeed's 20 stable AOT/interpreter identities remain
unchanged and separate from these elapsed-time experiments.

Changes to work size, dataset, representation or measured boundary need new
benchmark identities. CodSpeed tracks instruction regressions; no absolute
instruction budget is invented before collecting history. Existing deterministic
JIT/allocation budgets still fail CI independently.

## Running

- JVM correctness for every workload: `bazel test //benchmarks:simulation_smoke`
  (also included in `bazel test //...`).
- Prepare native image, including from macOS via RBE:
  `python3 tools/bench/bench.py prepare`.
- Native smoke on Linux amd64: `python3 tools/bench/bench.py smoke`.
- Within CodSpeed simulation: `python3 tools/bench/bench.py run`.

The runner never falls back to elapsed timing or an uninstrumented run.

## Initial SQLite elapsed baseline

The clean `dc12d6565090e423248caa109dc01dbdaa3ad23f` experiment on macOS arm64
used the default three forks, five warmups and eight measurement iterations per fork,
with SQLite JDBC 3.53.4.0 / SQLite 3.53.4 and the Bazel host JVM 25.0.4.1.1.
[Raw JMH samples](../benchmarks/results/sqlite-dc12d65/results.json) and
[environment/source identity](../benchmarks/results/sqlite-dc12d65/environment.json)
are retained. All six cases passed checksums and compiled-read admission.

| Boundary | Plain JDBC records | Batch rows | Static rows |
| --- | ---: | ---: | ---: |
| Query + retained output, ns/row | 401.83 ± 26.28 | 379.46 ± 2.20 | 388.61 ± 3.35 |
| Query + retained output, allocated bytes/row | 194.35 | 231.47 | 268.43 |
| Retained read, ns/row | 1.00 ± 0.06 | 2.77 ± 0.08 | 1.26 ± 0.06 |
| Retained read, allocated bytes/row | approximately 0 | 0.188 | 0.188 |

Errors are JMH's reported 99.9% confidence intervals, not bounds on other machines
or workloads. Query intervals overlap; this run does not establish a query speedup.
Batch/static query allocation exceeds the plain baseline by approximately 19%/38%.
Direct Java retained reads are fastest here. The Truffle retained-read allocation
includes call-target entry and result boxing amortized over 256 rows; it must not be
advertised as zero allocation. These results identify optimization work, not a
production performance certification. Server-driver elapsed experiments and broader
payload/projection distributions remain outstanding.

### Direct static ingestion follow-up

A clean `9debf6b` run repeated `staticQueryRetain` alone with the same benchmark source,
dataset, machine, JVM, three forks and 24 measured samples. [Raw samples](../benchmarks/results/sqlite-static-9debf6b/results.json)
and [environment](../benchmarks/results/sqlite-static-9debf6b/environment.json) are retained.
Direct static ingestion measured 236.53 bytes/row versus the earlier 268.43, approximately
32 bytes/row (12%) less. It removes the temporary four-element row array and combines
payload accounting into one traversal. Query timing was 381.69 ± 3.28 ns/row; these
separate sequential runs do not establish a general throughput improvement. Allocation
still exceeds the plain-record baseline, and the other five cases were not remeasured.
