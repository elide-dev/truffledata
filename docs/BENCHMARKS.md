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

## Comparing driver baselines later

The four JDBC cases use deterministic driver-boundary mocks, **not MySQL or
Postgres drivers or servers**. They isolate this layer's overhead. SQLite already
has real-driver integration coverage; this initial suite claims no SQLite timing.

For plain-JDBC comparisons, reuse the exact 256 rows, four projected columns,
null distribution, binding, signed/unsigned contract, checksum and consumption
boundary. Name a new baseline explicitly; never replace a fixture with a real
driver under an existing identity. Match output ownership too: retained immutable
records and a streaming ResultSet have different lifetimes and allocation costs.
Report decoding/materialization separately from network/server execution; the
database owns planning. Compare equal validation/retention guarantees or label
their differences.

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
