# Next SVM performance tranche

Decision owner: Sam, 2026-11-08. SVM / Native Image is the production performance
target. JVM is a correctness and test target. JVM timing regressions do not veto
measured SVM wins. This supersedes the earlier B4 rejection and JVM performance
release gates; it does not waive semantic, lifetime, SVM graph/allocation, no-PGO,
or prebuilt-native distribution requirements. See the root `AGENTS.md`.

Status: Sam authorized the first parallel tranche after compaction ("ok do it").
B4 Phase 1 is restored in `29aa408`; seven validation targets (including both
native smokes) and all nine coverage suites/method contracts pass. The experiments
below are in progress, not admitted optimizations or promised speedups. The
production baseline is `9a1dcec`. Follow-ons remain conditional on these results.

Execution branches start at that baseline: `tranche/json` in `td-climb-json`,
`tranche/csv` in `td-climb-slab`, and `tranche/protobuf` in `td-climb-b4`.
The parent integrates into `feat/followup-v5` in `truffledata-followup`.
Builds and tests remain serialized through the shared wrapper. Initial portable
SVM images and source/binary provenance are retained in the ignored local
`build/svm-tranche-baseline/9a1dcec/` (build `bad1392b`). Benchmark-only additions
for nested typed JSON and owned CSV are applied to the old production baseline
before recapturing images; candidates must use the same workload definitions.
No push, CI dispatch, publication, or main-branch merge is authorized.

### Execution checkpoint (qualification in progress)

- CSV candidate `234304c` isolates all-STRING framing; regular and forced-index
  JVM suites pass 121/121 each, native comparison smoke passes 162 cells, and
  typed compiled reads retain zero invokes/allocation sites (`b7b8fae5`). Eight
  paired SVM shards are running in `07bb26fb`; completed small-input cells show
  modest/neutral results, not a step-change conclusion. Test-only `6992a00`
  extends native acceptance to a large projected/owned string fixture.
- Protobuf candidate `f045f5c`, evidence `f3f9a3f`, removes monitors from warm
  handle-cache reads with acquire/release publication and synchronized misses.
  All seven correctness/allocation/native smoke targets pass (`4097845d`),
  including unchanged 168/136 B scalar floors and 48 B owner. Paired timing is
  pending. Test-only `30e4b7f` plus `fadf9e4` adds shared JVM/SVM publication races.
- JSON candidate checkpoint `a966cf2` is unqualified. The initial exact native
  differential passed, but its array-heavy producer probes lost 1–4%; that
  negative evidence is retained. The revised frame writer passes 155,647 exact
  Arm64 ASan/UBSan cases and Cargo link/fingerprint checks. Revised pinned native,
  JVM and six-shard SVM pairs (including flat/generic competitor controls) remain.
- Parent test infrastructure `c5d237f` selects the pinned Linux Arm64 SDK for
  existing format native acceptance targets; these new configurations are not
  yet executed. Final integration requires nine-suite method coverage plus
  native acceptance on both Linux ISAs; only x86 SVM timing claims are planned.

Current queue: finish CSV pair, revised JSON native/JVM/build, protobuf full
paired matrix, JSON pairs, then admission/integration and final common checks.
No candidate production change is integrated at this checkpoint. Worker source,
negative experiments, baselines and validation artifacts remain local.

## Restore and audit

- Restore the exact B4 guard/bound-hook implementation and its API-specific tests.
  The paired SVM full generated interpreter improved 259.36 to 213.56 ns (17.7%);
  sparse improved 36.36 to 31.83 ns (12.4%). Full/sparse generated and slab peak
  fork ranges overlap. Full interpreter/stock is still 3.501x generated and
  6.724x slab, above the 2.5x target. JVM regressions remain disclosed, non-gating.
  [Paired evidence](../../../benchmarks/results/protobuf-b4-213a289b/README.md).
- No additional proven JSON/CSV SVM win was found rejected solely for JVM timing.
  JSON text fusion lost on the pinned native toolchain (0.969/0.964/0.993x);
  AVX2 gained only 0.1–2.2% and AVX-512 regressed. CSV's larger row-capacity guess
  has only JVM measurements; it is unmeasured on SVM, not a known win to restore.
  The selected CSV unescape path already beats the rejected SWAR alternative on SVM.

## First parallel tranche

### JSON: structural typed decoding for nested and repeated schemas

`json/native/typed.h` routes scalar plans through `stage2_walk<flat_writer>`;
nested/repeated plans still traverse On-Demand through `members`/`elements` and
perform per-key plan lookup. Extend the structural typed writer with bounded
record/array frames, plan-specific key positions and direct typed-slot emission.
Keep the existing producer as the differential oracle during the experiment.

The flat structural producer already measured 1.681–1.882x native gains in the
[pinned qualification](../../../benchmarks/results/json-sota-final-qualification/README.md).
That motivates this experiment but does not predict nested performance. A useful
working hypothesis is 1.2–1.5x nested ingest, to be replaced by measurements on
nested records, arrays of records, mixed scalars, escaped/reordered keys and churn.

Preserve required/null/type/range contracts, duplicate and unknown-field behavior,
depth/byte/cell budgets, exact numeric and lexical semantics, and failure before
publication. Do not silently switch to validate-only-used-fields semantics:
[simdjson's On-Demand design](https://simdjson.org/api/3.0.0/md_doc_ondemand_design.html)
explicitly distinguishes that policy from complete validation. Require exact
acceptance/status/output differential coverage and the native sanitizer corpus.
Measure end-to-end SVM ingest as well as native producer time and allocations.

### CSV: specialize retained-batch construction for SVM

Keep the existing SIMD indexer and the plain/fused framing separation. Specialize
the consumer selected at scan entry: first an all-STRING span-only builder, then
kind-specific typed writers. Hoist stable plan/mapping decisions out of per-cell
`BatchBuilder.put`; keep number parsers out of the text-only framing loop.

This extends existing fusion rather than proposing it again. The historical AOT
[disassembly](../../../benchmarks/results/csv-3f23d0c/probes/frame-disassembly.md)
counts 7,832 instructions/295 calls for fused framing versus 1,108/20 for plain
framing. The [CSV ledger](2026-11-06-csv-ledger.md) records SVM typed numeric at
0.57–0.73x and short-field strings at 0.43–0.66x the kernel consumer. These are
opportunity bounds from different workloads, not expected speedups of this patch.

First recapture the current selected unescape and `parseOwned` paths, which are
newer than the full CSV capture. Compare complete retained outputs, not a scan
count against a materialized batch. Preserve dialect fallback, quote/escape/CRLF
framing, nulls, exact numeric rounding/overflow, UTF-8, limits, row error order and
partial-row behavior. Check that faster construction does not regress SVM compiled
column reads, and report bytes/row alongside GB/s.

A native index-to-column producer is a possible later experiment, not a measured
win: the existing `kernel-native` typed reference is Java `KernelEngine.record`
over native indexes; the C++ probe is scan-only. Neither establishes a native
typed-materializer advantage. Avoid introducing a new ABI before the cheaper
plan-specialization experiment supplies evidence.

### Protobuf: remove warm-read cache and traversal overhead after B4

Reprofile the restored image first: the old 18.7% `Accessor.bound` sample share
predates Phase 1 and is no longer a valid attribution of the remaining cost.
The historical profile also identifies repeated-array interop dispatch, child
row lookup and map access. Current `SlabMessage.row/text/blob/array/unsigned`
still synchronize cache hits, and array/unsigned views share a boxed-key map.

Prototype warm-hit reads with explicit safe publication and synchronization only
on misses, preserving stable flyweight identity under races. Replace repeated-list
map lookup with a compact verified-offset/view index where measurement supports
it. Lists do not currently carry the same handle ids as records and strings:
do not index them by a nonexistent handle or introduce an ABI change by accident.
An array reference marked volatile does not publish later element writes: use
acquire/release slots (or equivalent) and a synchronized recheck on a miss.
Never read the current mutable `HashMap` concurrently with writes without its
lock. Any replacement must distinguish a list's type, ordinal and expanded
projection interpretation, including externally supplied slabs with aliased list
offsets; the verifier's identity table does not establish list identity.
Keep the 48-byte owner footprint and measured scalar allocation floor unless a
separate measured representation decision justifies changing them.

Then specialize repeated-child/array access and generic library dispatch where
the new profile still shows it hot. Avoid moving ordinary benchmark work into a
special harness shortcut. Public layout checks, borrowed epoch checks and
projection checks remain mandatory; cache publication must preserve concurrent
durable reads. Re-run full/sparse interpreter and peak pairs and native
allocation/graph gates. The existing goal remains <=2.5x stock; cache changes
alone are not assumed sufficient to get slab from 6.724x to 2.5x.

## Follow-ons, ordered by measured leverage

1. **JSON native snapshot specialization.** The final snapshot callgrind trace
   attributes 25.97% of instructions to `HeapTape.copy` itself, another 3.29% to
   `rawSpan` and 2.61% to `word`. Specialize its two existing sizing/relocation
   passes for concrete native storage and bulk directory loads. Single-header
   scans and bulk text copying already ship. Preserve exact retained extents,
   subtree rebasing, directories, lexical/alias semantics and independence after
   owner close. A 10–20% end-to-end instruction reduction is an experiment
   hypothesis, not a timing claim. [Final profiles](../../../benchmarks/results/json-codspeed-20261108/final).
2. **CSV direct input-to-sink decoding.** `CsvBatch.export` already reuses
   `CsvText`, but only after building a retained batch. Add an explicit streaming
   consumption path that avoids retained column storage and a later export walk.
   Define callback lifetime, reentrancy, malformed-input behavior and ownership;
   benchmark this separately from durable batch construction and from eager
   Java String output. [Arrow's table/streaming distinction](https://arrow.apache.org/docs/cpp/csv.html#tradeoffs)
   is useful prior art for keeping those workloads distinct, not evidence of our
   implementation's speed. If large-input profiles instead identify column
   grow/trim copies and mapping faults as dominant, test bounded segmented growth
   next, including its cost on compiled getters and immutable retained storage.
3. **Protobuf bulk scoped decode.** Existing sessions already reuse arenas and
   can decode with zero steady Java allocation. New work would batch several
   messages per native call/pin and consume them within an explicit scope. The
   measured durable scalar path still has 24 B/message of native residue,
   consistent with its pin. First measure transition/pin/owner costs separately;
   batching does not eliminate actual parsing or required validation. Preserve
   per-message errors, capacity/depth limits, reset invalidation and opt-in retain.
   Do not change individual durable-message ownership implicitly.

The existing shared-subgraph verification time risk also remains actionable:
memoizing structure requires preserving depth, identity, per-visit logical
budgets and projected state. Keep the bounded reproducer and the common
allocation-free path. This is correctness/complexity work, not permission to
skip validation on externally supplied slabs.

## Admission and measurement

- One isolated candidate per mechanism; preserve its before image and source/SDK
  hashes. Build workers may research in parallel; local Bazel stays serialized.
- Paired SVM measurements on the same host/CPU/SDK, at least three fresh forks;
  report raw medians and ranges, checksum equality, peak and interpreter tiers,
  allocations and retained/native memory separately. Confirm supported SVM ISA
  paths; don't generalize an x86-only win to Arm.
- Instruction profiles guide experiments; they are not elapsed-time speedups.
  The percentages above are hypotheses or measured attribution, never guarantees.
- JVM differential, API, lifetime and method-coverage tests remain required. JVM
  throughput/latency comparisons may be retained diagnostically, but are not
  admission or release gates. In particular, the old E1 JVM selected-small and C1
  JVM native/Java timing misses no longer block the production-performance tranche.
- Preserve the SVM allocation, graph, semantic and performance contracts. Keep
  measured SVM wins even at a JVM timing cost, and retain measured negatives.
  No new PGO or consumer-side native-code-generation requirement is authorized.

Recommended dispatch: JSON nested structural writer, CSV plan-specialized batch
construction, and protobuf warm-read caches/dispatch in parallel, with serialized
qualification. The follow-ons remain conditional on the first tranche's results.
