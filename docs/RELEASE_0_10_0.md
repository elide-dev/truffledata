# TruffleData 0.10.0

This follow-up to the intermediate 0.9.0 release adds native YAML support and
completes the public protobuf slab and compact JSON reader migration. Java 25,
Myna 0.13.0 and Elide GraalVM 25.5.5-elide.27 remain the runtime baseline.
Upgrade native attachments and generated bindings together.

The 0.10.0 release scope supports the TruffleCEL and Elide 1.6.3 integrations.
SVM / GraalVM Native Image is the production performance target; JVM correctness,
API, ownership/lifetime and coverage tests remain required. JVM timing regressions
are disclosed but do not veto measured SVM gains.

## Data APIs

- Protobuf durable messages own their slab bytes. Contract types use generated
  Java producers on the JVM; Native Image uses the native decoder with Java
  fallback. Reusable sessions and explicit projections support borrowed data and
  lazy children with checked lifetimes. Raw unknown-field presence and byte counts
  are available without copying on immutable, generated, owning and borrowed rows.
- Resolved protobuf Editions, extensions and MessageSet plans can bind supported
  host sources explicitly. `Source.ingestion()` identifies direct, normalized and
  unsupported paths before input is processed.
- JSON uses compact readers and a structural-index producer for ordinary and
  streamed documents. Sink documents omit member directories; retaining them
  constructs the directories needed for key lookup. Duplicate and Unicode checks
  remain mandatory.
- YAML exposes documents, metadata, directives, aliases, typed records, sessions,
  streams and text output, with JVM FFM and static Native Image backends.
- CSV accepts bounded byte-array windows, copied or ownership-transferred input,
  and caller-owned file-channel mappings. Schema name lookup uses an immutable
  ordinal index. Ownership-transferred input must remain unchanged while the
  batch or its rows are reachable.

## Distribution

Twenty-one libraries and the BOM include source/Javadoc jars, Gradle module
metadata, POMs, signatures and checksums. The project is dual licensed under MIT
or Apache 2.0, at the consumer's option. The publisher validates a signed Central
bundle before publication; the Elide repository also supplies the GraalVM fork.

Buffa, JSON, YAML and CSV natives support Linux x86-64, Linux AArch64 and macOS
AArch64. CSV additionally supplies static musl archives for both Linux
architectures and SDK tarballs containing C headers, manifests, license notices,
link instructions and hashes. CSV implementation symbols are isolated from the
five-function public C ABI.

Standalone Buffa Cargo consumption is outside the 0.10.0 Maven/native release
scope: its generated-seam packaging and composed Rust link qualification remain
unfinished. Shipping Buffa Java bindings and native attachments does not claim
that a standalone Cargo dependency is ready.

## Compatibility and measurement

`Accessor` adds an optional protected constructor for layouts identified by exact row class and
schema identity, plus protected `readLongBound`, `readDoubleBound`, `readBooleanBound`,
`lengthBound` and `assertAccepted` hooks. Existing subclasses retain checked defaults. Bound
reads skip only a repeated layout check; lifetime, projection, presence and type checks remain.
Public accessor methods stay checked. This is an additive API change.

Native protobuf ABI11 retires the old binary view and scalar entry points.
Public migration aliases delegate to owning slabs; protobuf text/JSON parsing
retains its separate typed support. Compiled runtime-schema decoders were
measured and were not adopted: their historical qualification missed the SVM
speed gate as well as the joint gate. Runtime schemas keep the table-driven
Java fallback.

## Measured SVM changes

The final performance tranche compares preserved production baseline `9a1dcec`
with each admitted candidate using at least three fresh forks per cell, paired
on the same pinned Linux x86-64 worker CPU per workload group. Different groups
may use different hosts. The images use the pinned SDK, `-O2`, serial GC and no PGO. These are
isolated candidate measurements, not a single combined-application speedup or
an estimate of TruffleCEL/Elide performance. The baseline already includes the
earlier slab allocation reduction and restored B4 accessor optimization.

| Workload | Baseline → candidate | Measured change |
|---|---:|---:|
| JSON nested typed ingest | 829.10 → 700.30 ns/document | 15.5% lower time |
| JSON repeated typed ingest | 4,209.47 → 3,639.22 ns/document | 13.5% lower time |
| JSON mixed / churn typed ingest | 1,677.52 → 1,517.45 / 2,639.50 → 2,286.20 ns/document | 9.5% / 13.4% lower time |
| Protobuf full slab interpreter reads | 418.93 → 351.17 ns/row | 16.2% lower time |
| Protobuf full slab peak reads | 310.53 → 210.80 ns/row | 32.1% lower time |
| Protobuf sparse slab interpreter / peak reads | 52.87 → 44.07 / 30.05 → 21.13 ns/row | 16.6% / 29.7% lower time |
| CSV retained strings, eight fixture/size cells | Candidate/baseline time geomeans 0.9802–0.9853 across four copied/owned and Java/native-indexed paths | 1.5–2.0% lower aggregate time |

JSON's nested/repeated structural writer, protobuf's safely published warm handle
caches and CSV's all-STRING span writer preserve the measured allocation cells.
CSV's owned-input API predates this change; its ownership savings are not counted
as a new optimization. Native indexing and retained-batch construction are
different stages, and streaming reference consumers do not retain equivalent
batches.

The tradeoffs remain visible: generic JSON ingest takes 2.2% longer; flat typed
JSON is 1.5% slower with overlapping fork ranges. Protobuf native/lazy sparse
first reads take about 4.5%/3.3% longer, despite faster warm reads. CSV's gains
are modest, individual cells are not uniformly faster, and noisy large-input
forks remain in the results. Full protobuf slab interpretation still takes
5.652× direct stock getter time; generated-owned interpretation takes 3.471×.
Warm-read improvements do not imply equal gains in decode or complete pipelines.

[JSON typed charts](images/svm-tranche-20261108/comparison-json-typed.svg),
[JSON generic comparisons](images/svm-tranche-20261108/comparison-json-generic.svg),
[protobuf comparisons](images/svm-tranche-20261108/comparison-protobuf.svg) and
[CSV comparisons](images/svm-tranche-20261108/comparison-csv.svg) link the measured
workloads to the [full evidence and ranges](../benchmarks/results/svm-tranche-20261108/README.md).
Linux x86-64 and Arm64 acceptance tests pass; those Arm tests establish semantic
and compiled-graph behavior, not Arm speedups.

## Deferred performance work

The following performance goals remain future work beyond 0.10.0.
The recorded misses remain misses; no timing or memory goal is
marked achieved by this release decision. These are possible investigations,
with no delivery date, implementation commitment or promised speedup:

- **Protobuf:** the ≤2.5× direct-stock interpreter target and remaining sparse
  decode/cold-read costs. Profiles could test repeated-list indexing, dispatch
  specialization or opt-in bulk scoped decode while preserving ownership,
  lifetime and projection checks.
- **JSON:** retained big/huge and validation small/huge native producer floors,
  the structurally unmet 1.5× logical index/input memory criterion, duplicate-check
  price qualification and broader end-to-end goals. Native snapshot specialization
  and representation changes are hypotheses requiring separate measurements.
- **YAML:** config, block, flow and text native decoder ratios remain
  0.561/0.693/0.692/0.771 against floors of 0.597/0.715/0.724/0.895. These C++
  decoder/reset probes compare with rapidyaml's scan handler; they are not JVM
  or end-to-end SVM results. Stream and SVM graph/allocation gates pass. Literal
  filtering, handler code generation and a direct typed handler are possible
  investigations, not committed changes. The full three-fork comparison remains
  unrun.
- **CSV:** short-field retained strings and numeric typed paths still trail
  some consumers; large allocating inputs remain sensitive to host page faults.
  Further plan specialization, direct input-to-sink decoding and bounded column
  growth are unmeasured options. Original H1–H5 throughput hypotheses were
  explicitly non-blocking and remain recorded with their actual outcomes.

This disposition does not waive semantic, ownership, allocation/graph or safety
contracts. Recent combined coverage (455 tests; 201 production files across 12
modules) and five-target native acceptance on each Linux ISA are retained as
qualification evidence. Release execution uses the normal CI run and staged and
public artifact checks; it does not repeat the full performance matrix or imply
that publication checks have already completed. The
[release ledger](superpowers/plans/2026-11-07-distribution-ledger.md) records their
actual status.
