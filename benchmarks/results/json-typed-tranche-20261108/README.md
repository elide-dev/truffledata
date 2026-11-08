# Nested/repeated structural typed JSON candidate

Status: **unqualified candidate**. The first implementation passed correctness but lost on
three native producer fixtures. The revised frame implementation passes local sanitizer and
Cargo checks; pinned revised producer, JVM, SVM allocation/graph and paired image measurements
remain pending. No SVM speedup is claimed here.

The baseline image has production `9a1dcec` and the identical Java harness from `c43af1c`
(integration `d3f40cf`). `baseline.json` records its image/source hashes. No PGO, ISA change,
consumer-side native generation, ABI or retained result layout change is introduced.

## Implementation and contracts

The structural writer extends the existing scalar producer with at most 64 record/array frames
for the already bounded 32-record schemas. Immutable schema/key/selected-field pointers are
cached in frames; slots remain integer indexes because the arenas may grow. An array's stage1
indexes are counted before exactly reserving contiguous scalar slots or record entries, including
record arrays whose elements allocate descendants. Required/null/type/range/UTF-8/lexical and
input/index limit checks remain in force. All structural input must pass before publication.

On a failed nested decode, the partial arenas are released and the old On-Demand walk is replayed
to preserve established public error classification, including malformed arrays with tight
budgets. A structural failure never publishes a result even if the old walk were to accept it.
The independent test-only On-Demand producer remains the acceptance/output oracle. Flat scalar
plans retain a separate compile-time specialization. The checked-in Cargo headers match the
production sources.

## Current evidence

- `native-initial.log`: pinned Linux x86-64 invocation
  [194aaa6f](https://app.buildbuddy.io/invocation/194aaa6f-c162-4137-902c-48645700e1f2),
  all four native typed/API/probe targets pass. Exact differential: 155,635 cases.
  The initial producer ratios (old/new, five rounds) were nested **1.075–1.140x**,
  repeated **0.977–0.985x**, mixed **0.958–0.987x**, churn **0.966–0.980x**.
  `initial-writer.patch` and `initial-sources.json` preserve this rejected version.
- `native-arm64-frame-revision.log`: Apple Clang 21, Arm64 diagnostic after caching schema,
  selected field/key pointers and slot indexes, and initializing only active frame members.
  Five-round native producer ratios were nested **1.347–1.450x**, repeated **1.029–1.059x**,
  mixed **1.044–1.057x**, churn **1.051–1.103x**. These are a different host/toolchain and
  do not establish pinned Linux or SVM gains. The subsequent expected-key wrap comparison
  removes a dynamic modulo without changing its result; it still awaits pinned timing.
- `sanitizers-arm64.log`, `sanitizer-metadata.json`, `sanitize.py`: all dependencies and five
  tests compiled with ASan+UBSan. Typed exact differential, typed/API contracts, generic
  200-document/60-malformed corpus, and one million decoded-key shape sequences pass.
- `typed-wrap-check.log`, `typed-wrap-sources.json`: the final expected-key wrap and additional
  empty-child/late-invalid-element cases pass ASan+UBSan: **155,647 cases**. For nested/repeated
  plans the differential compares exact status, slots/bits, decoded text, lexical bytes, record
  tables and index memory extents. The pre-existing flat corpus compares acceptance/output.
- `cargo-arm64.log`: the synchronized source package builds and passes its link/fingerprint test.

The revised probe's exact fixture index storage matches the oracle: nested **736 B retained /
1,056 B peak**, repeated **6,032 / 8,592 B**, mixed **2,944 / 4,224 B** for the unescaped first
input and **3,040 / 4,320 B** for the escaped/reordered second input. Parser input capacity is
not actual scratch memory. The new per-record key metadata belongs to the reusable plan,
not each message; escaped output text is separately owned and plain strings reference the
result's owned input.

## Paired SVM qualification

`//benchmarks/comparison:json_typed_pair` keeps six groups on independent pinned CPUs; each
complete before/after cell stays on one host/CPU. Four groups are nested, repeated16, mixed4
escaped/reordered, and churn0..8. Two control groups retain the flat scalar and generic competitor
workloads. Every image first runs `--typed-smoke`; three fresh forks per image/cell retain raw
samples, checksums, source/image hashes, tier markers and medians/ranges. Four typed groups
measure ingest/read-aot/interpret/peak; controls measure ingest/peak. Memory and time are separate.
The nested/repeated fixtures are never compared numerically with generic competitor fixtures.

The local baseline label is `//build/json-typed-tranche-baseline:json-comparison`, supplied through
`--//benchmarks/comparison:json_typed_svm_baseline=...`. It is a hard link to the preserved image;
no machine-specific baseline label or binary is committed.
