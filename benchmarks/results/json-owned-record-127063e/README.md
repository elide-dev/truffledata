# JSON typed reads: owned records for scalar-only plans — WS6 step 2 (2026-11-09)

Plan: `docs/superpowers/plans/2026-11-08-beat-stock-svm.md` WS6; Sam's ruling and every entry in
`2026-11-08-beat-stock-svm-ledger.md` under "WS6 step 2 (JSON owned record worker)". SVM / Native
Image is the admission target; no JVM timing was taken. **Verdict: killed and reverted
(`6e63e04`).** Production source at the end of this worker equals the base `5bc030b`.

## Change measured (`a397046`, `127063e`)

`JsonPlan.decode` keeps its static type (`TypedRecord`) and every exported `DataLibrary`/interop
message. A plan whose root has only INT64/FLOAT64/BOOLEAN/EXACT fields (`TypedLayout.scalars()`)
copies the root entry's presence/null masks and slot payloads into a `long[]` at decode and
releases the native result before returning (SVM: word accessors straight from the
`JsonTypedResult`, no `SvmTypedOwner`; JVM: `Scalars.detach` over the FFM owner, then `close`).
The record carries its own lifetime state (confining thread, closed flag, format) and creates an
owner facade (`RecordOwner`) only when `owner()` is asked for; closing either closes both, and a
closed or cross-thread read fails exactly as a native record's does (same exception class and
message, thread checked before closed). Plans with STRING, RECORD or ARRAY fields keep the native
representation. `127063e` moves the owned lifetime failure and the "no native owner" guard out of
line (`transferToInterpreter` + boundary factory) and tests the representation on `values` only.

New tests (both reverted with the change): `JvmScopedTest.scalarPlansDecodeOwnedRecordsWithTheNativeLifetime`
(values, missing/null, EXACT, interop, scoped `ContractViolation`, snapshot survival, facade
identity/`root()`/assumption, five cross-thread rejections, closed reads through every entry
point, facade created after close, message parity with a closed native record) and
`SvmAcceptance.ownedScalars`. Existing `CompiledReads` (compiled closed reads, 0.05 B/read,
`TreatPerformanceWarningsAsErrors`) ran over owned records unchanged.

## Images (`manifests/`)

All x86-64, pinned `25.5.5-elide.27`, `-O2`, `-march=compatibility`, serial GC, no PGO, built on
RBE and preserved under the worktree's ignored `build/`:

- before `5bc030b` (`json-comparison` `21630a42…929f`, invocation `b7b15e0e`), with its profile
  and simulation images;
- candidate `127063e` (`json-comparison` `31251aee…5f0a`, invocation `1aef800c`);
- ceiling probe `f55ed1d` (`de3972b6…0883`, invocation `1ea3b095`): `127063e` with every owned
  read check removed (thread, closed, schema, ordinal, kind, presence) — a measurement bound
  only, never a candidate.

Pairs: `//benchmarks/comparison:json_typed_pair` with both `label_flag`s pointed at the preserved
images, three fresh forks per binary per cell, interleaved, one pinned CPU per shard; medians of
fork medians, ranges are fork-median minima/maxima. All shards ran on AMD EPYC workers.

## Paired results

### Candidate `127063e` vs `5bc030b` (`pairs/candidate/`, invocation `75416146`)

| Cell (shard) | Before ns | After ns | Ratio | Ranges | Java B before → after |
|---|---:|---:|---:|---|---|
| typed (`simdjson-typed`) peak (4) | 4.472 (4.378–16.211) | 4.310 (4.240–6.376) | 1.038× | overlap | 1.0 → 1.0 |
| specialized peak (4) | 2.871 (2.758–3.038) | 2.956 (2.833–3.141) | 0.971× | overlap | 1.0 → 1.0 |
| typed ingest (4) | 244.075 (240.887–246.192) | 228.745 (218.970–282.803) | 1.067× | overlap | 112 → 120 |
| specialized ingest (4) | 241.616 (240.668–262.107) | 250.788 (234.580–259.812) | 0.963× | overlap | 112 → 120 |
| typed read-aot / interpret (7) | 4.717 / 5.207 | 4.842 / 5.310 | 0.974× / 0.981× | overlap | 0 / 2 |
| specialized read-aot / interpret (7) | 7.100 / 7.837 | 6.816 / 7.963 | 1.042× / 0.984× | overlap | 0 / 2 |
| Gson owned record peak / read-aot (4, 7) | 1.451 / 1.376 | 1.494 / 1.418 | — | overlap | — |
| nested ingest / read-aot / interpret / peak (0) | 405.5 / 23.8 / 28.4 / 24.5 | 517.0 / 29.2 / 29.6 / 23.8 | 0.784× / 0.817× / 0.962× / 1.029× | yes / no / yes / no | 160→248, 48→112, 50→114, 49→49 |
| repeated ingest / read-aot / interpret / peak (1) | 3,696.6 / 385.9 / 387.7 / 347.8 | 3,688.8 / 437.3 / 458.0 / 353.4 | 1.002× / 0.882× / 0.847× / 0.984× | no / yes / no / no | 1,568→2,040, 1,456→1,904 |
| mixed ingest / read-aot / interpret / peak (2) | 1,543.4 / 161.9 / 161.2 / 150.2 | 1,633.4 / 180.4 / 182.5 / 150.2 | 0.945× / 0.898× / 0.883× / 1.000× | yes / yes / yes / no | 592→776, 480→640 |
| churn ingest / read-aot / interpret / peak (3) | 2,278.5 / 235.0 / 236.7 / 236.6 | 2,609.0 / 262.0 / 273.9 / 216.8 | 0.873× / 0.897× / 0.864× / 1.091× | yes / no / yes / yes | 558.5→735, 446.5→599 |

`pairs/candidate-repeat/` (invocation `362d6a70`) re-ran shards 4 and 7 three times; the three
runs were scheduled concurrently and runs 1–2 are visibly contended (Gson peak 1.45–2.33 ns).
The cleanest run (3, Gson peak 1.459/1.460) gives typed peak 4.533 (4.434–4.563) → 4.388
(4.376–4.403), 1.033× disjoint, specialized peak 3.055 → 2.968, 1.029× overlap, read tiers
0.80–1.12× overlapping.

Reading: the owned representation is at parity with the native one at peak (≤3.3%, disjoint
once out of four flat peak observations) and in the read tiers. Ingest is not measurably slower
(1.067×/0.963× overlapping here, 1.033×/1.064× in the probe pair below; +8 B/doc): the one copy
costs no more than retaining the result and releasing it at close. Carrying both representations
in the one final `TypedRecord` class costs every native record: +16 B per record (48 → 64 B;
structural read-aot allocation +64 to +448 B/row) and an extra representation test, so the
structural typed cells regress 10–18% in read-aot/interpret and up to 22% in ingest, several
with disjoint ranges.

### Ceiling probe `f55ed1d` vs `5bc030b` (`pairs/ceiling-probe-{1,2}/`)

Run 2 (invocation `338e10d6`, uncontended, Gson peak 1.384/1.362):

| Cell | Before ns | Check-free owned ns | Ratio |
|---|---:|---:|---:|
| typed peak | 4.463 (4.454–4.587) | 1.929 (1.920–2.028) | 2.313× (disjoint) |
| specialized peak | 2.868 (2.848–2.877) | 1.906 (1.881–1.965) | 1.505× (disjoint) |
| typed read-aot / interpret | 5.072 / 5.260 | 2.163 / 2.556 | 2.345× / 2.058× (disjoint) |
| specialized read-aot / interpret | 6.825 / 7.929 | 3.647 / 4.014 | 1.871× / 1.975× (disjoint) |
| typed / specialized ingest | 241.622 / 247.497 | 233.987 / 232.662 | 1.033× / 1.064× |

Run 1 (invocation `0b4ffa7b`) was on a contended worker (before typed peak 5.16, Gson 1.62–1.93)
and shows the same direction. Reading: with the same owned storage, removing the per-record
checks moves specialized peak from 2.96 to 1.91 ns and typed from 4.31 to 1.93 ns; the candidate
(checks kept) sits on the native numbers. So the remaining peak gap to the Gson owned record is
the per-record lifetime/schema/kind/presence checks (≈1.0 ns specialized, ≈2.4 ns typed per
three-read record), not the record → owner → slot-arena load chain the step-1 ledger entry named;
and even a check-free owned record stays ≈1.4× Gson (1.91 vs 1.38 ns: representation test,
array indirection and bounds). The hypothesis (typed peak ≤1.4 ns with lifetime semantics kept)
is not reachable by a storage change.

## Compiled graphs and AOT shapes (candidate `127063e`)

`json_native_smoke` (invocation `ace0c862`, `smoke/json-compilation-127063e.log`) passes: 24
graphs, nine allocation cells. Both representations are compiled into every typed read root, so
the roots grow: typed 307/250 nodes (8/4 Ifs) → 427/370 (17/13); specialized 240/183 (8/4) →
363/306 (17/13). `json_aot_symbols` fails (`aot/aot-symbols-candidate-127063e.md`):
`TypedRecord_bits` 288 instructions / 15 calls (budget 280/14), `TypedRecord_slotBits` 334/19
(332/18), `TypedAccess_longValue` 348/18 (332/17); the first version (`a397046`, inline owned
throws) was 610/601/645. `aot/unmeasured-aot-trim.patch` (misuse failures out of line) was
written to fit the budgets but neither built nor measured once the pair killed the change.

## Gates and invocations

- Candidate `127063e`: `//tape/tests:jvm`, `//json/tests:jvm`, `//tests:test`,
  `//benchmarks/comparison:json_native_smoke`, `//json/tests:native_test` pass (`ace0c862`);
  `//yaml/tests:jvm` passes (`a6ddb6dc`, at `a397046`); `//benchmarks/comparison:json_aot_symbols`
  fails as above. `//json/native:session_replay` not run (no native source changed; Cargo vendor
  copy untouched).
- Final source (`6e63e04`) is byte-identical to `5bc030b` in `tape/`, `json/` and `tests/`, so
  every gate stands as at the base; no budget was re-pinned.
- Callgrind Ir: not measured. `//benchmarks:simulation` has no typed JSON case (only the four
  generic cases), so the P0.3 method would need a new simulation case in both images.
- Pairs: `75416146` (candidate), `362d6a70` (candidate repeat), `0b4ffa7b` and `338e10d6`
  (ceiling probe).
