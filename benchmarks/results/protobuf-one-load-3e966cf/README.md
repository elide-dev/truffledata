# Protobuf one-load slab reads — WS1 + WS4 (2026-11-08)

Plan: `docs/superpowers/plans/2026-11-08-beat-stock-svm.md` WS1 (generated per-contract
slab row classes) with WS4's no-ABI list table; rulings and measurements in
`2026-11-08-beat-stock-svm-ledger.md` under "WS1 (protobuf reads worker)".

Before: `e650d87` (content of `641aa45`, the rebased `perf/beat-stock-svm` tip). After:
`3e966cf` = `30913eb` (WS4: lock-free list/unsigned view table, precomputed `SlabArray`)
plus `3e966cf` (WS1: generated `SlabRow_<Type>`/`SlabMessage_<Type>` with exact-class
accessors). Both images: Linux x86-64, pinned SDK `25.5.5-elide.27`, `-O2`,
`-march=compatibility`, serial GC, no PGO. Image hashes and build invocations are in
`aot-before/before-manifest.json` and `aot-after/after-manifest.json`; the images stay in the
worktree's ignored `build/before-e650d87/` and `build/after-3e966cf/`. SVM is the
performance target; JVM suites are correctness gates and no JVM timing was taken.

## Paired SVM measurement

`//benchmarks/comparison:consumer_measure` (invocation `cf99db74-dc58-4e70-99c9-dfe1273a39d6`,
ten remote shards, all nine engines and five modes for both binaries, three fresh forks per
cell, checksums verified). Full report: [`svm/merged/results.md`](svm/merged/results.md),
raw: `svm/merged/results.json`, shards: `svm/shards/`. Times are ns/row, medians of fork
medians with observed ranges (not confidence intervals); shared-worker timings are advisory.

| Cell | Before | After | Change | After / stock |
|---|---:|---:|---:|---:|
| Full slab peak | 206.913 (206.687–223.419) | 106.302 (105.933–112.789) | −48.6%, disjoint | 1.00× (stock 105.993) |
| Full slab interpreter | 351.754 (346.924–354.123) | 217.499 (216.252–221.297) | −38.2%, disjoint | 3.54× (generated-owned 3.53×) |
| Full slab first access | 287.142 (286.580–288.287) | 291.611 (289.171–297.215) | +1.6%, narrowly disjoint | 4.43× |
| Holdout slab peak | 226.055 (213.157–227.740) | 126.099 (125.264–126.161) | −44.2%, disjoint | 0.93× (stock 135.772) |
| Holdout-sparse slab peak | 20.955 (20.866–30.001) | 13.769 (13.713–13.879) | −34.3%, disjoint | 1.19× |
| Sparse slab peak | 20.841 (20.810–20.967) | 13.713 (13.646–15.699) | −34.2%, disjoint | 1.17× |
| Sparse slab interpreter | 44.461 (44.032–44.574) | 32.144 (32.087–32.212) | −27.7%, disjoint | 3.36× (generated-owned 3.31×) |
| Sparse slab first access | 50.208 (49.638–53.770) | 55.890 (52.976–1168.447) | +11.3% median, one 1.2 µs fork | 3.89× |
| Large slab peak | 24,033.551 (23,746.491–24,577.153) | 16,624.910 (16,311.904–16,824.008) | −30.8%, disjoint | 0.99× |
| Full slab-lazy peak | 279.634 (277.844–353.220) | 142.395 (141.893–166.821) | −49.1%, disjoint | 1.34× |
| Full slab-lazy interpreter | 396.035 (395.753–415.041) | 250.519 (250.504–252.549) | −36.7%, disjoint | 4.07× |

Controls on the same shards: full peak stock 108.633 (104.220–122.455) → 105.993
(105.313–106.979), generated-owned 51.225 (49.034–57.486) → 49.467 (49.276–86.393); full
interpreter stock 61.326 → 61.495, generated-owned 215.708 → 216.813; sparse peak stock
11.760 → 11.708, generated-owned 9.812 → 9.821. All control ranges overlap. `slab-native`
tracks `slab` within its ranges in every cell (same SVM producer). Sparse projected
peak/interpreter (generic rows) is unchanged within ranges (26.216 → 26.206; 49.222 → 51.639,
+4.9% with ranges separated by 1.4 ns, the generic `get` now being a virtual call).

The full slab peak cell reaches stock parity (1.00×, ranges 105.9–112.8 against
105.3–107.0); it does not reach the generated-owned 0.47×. The B4 interpreter gate (≤2.5×
stock) remains unmet: slab and generated-owned interpretation are now the same 3.5×, which is
the harness-dispatch floor the plan describes (P0.2: 42–44% of interpreter leaf samples are
`SharedRoot.score`, DSL dispatch, `FieldAudit` and interop array messages for both).

### Cold access and decode

First access carries a small cost: full slab +1.6% with narrowly separated ranges, sparse
slab +11.3% in the median with one 1,168 ns fork (slab-native sparse first improves 50.708 →
47.389, −6.5%, ranges overlap), lazy sparse first 57.739 → 63.700 (+10.3%, ranges 56.7–60.4
vs 58.3–66.1). Every first access now creates a generated row and binds generated accessors
(one `Schema.accessor` table per generated class) where it used to bind the layout's shared
`Slot`; that is a per-site cost outside the warm window. Decode cells: full slab decode
966.031 (965.785–972.409) → 1,013.824 (991.151–1,024.703), +4.9% with separated ranges,
while the stock control on the same shard moved 774.214 → 800.200 (+3.3%, separated) and
generated-owned 812.085 → 835.118 (+2.8%); sparse slab decode +1.1%, large slab decode
−1.0%. About 1.5% of the full decode movement is not explained by the control; the decode
path gained one factory call per message (`SlabMessage.create` → generated root
constructor) and nothing else. No decode-stage claim is made.

### Allocation and retention

Java allocation per row (current-thread, harness included, native excluded): full slab peak
17.00 → 1.00 B/row and interpreter 18.00 → 2.00 (the `Integer` boxing of the list-map probe
is gone; the remaining 1–2 B is the harness, matching generated-owned); holdout peak 17.00 →
1.00; large peak 17.00 → 1.00. Resident/heap/detach slab cells drop 40 B/row (full resident
1,480.50 → 1,440.50, heap 1,504.50 → 1,464.50, detach 1,480.50 → 1,440.50; large resident
317,632 → 317,592): the view table replaces the `HashMap` plus its boxed keys. Decode cells
(808.00 full/sparse, 226,294.50 large), session cells (40.00) and every stock/generated cell
are unchanged. Slab extent, retained capacity, arena and retained-input observations are
identical in all pairs. The 48 B owner is unchanged (`views` is one field as before).

## Gates

- JVM: `//tests:test` (`7889a646`; PolicyTest boundary budgets, AccessorTest exact-layout and
  bound-hook tests, the new `SlabAccessTest.generatedSlabRowsAreExactClassesPerTypeAndRejectOtherLayouts`),
  `//tests:allocation`, `//tests:compiled` (≤0.05 B/read, 10,000-node graphs),
  `//protobuf-runtime:test`, `//protobuf-buffa/tests:jvm`, `:jvm_non_strict`,
  `//benchmarks/comparison:consumer_smoke` (132 cells, 256-node AST) in `53a628f5`;
  `//protobuf-runtime:slab_replay` and `:test` on the rebased tree (`9aa42f21` build,
  replay invocation in the ledger).
- SVM: `//benchmarks/comparison:consumer_native_smoke` 132 cells (`c4d495ab`);
  `consumer_measure` ten shards (`cf99db74`).
- P0.6: `aot-before/` and `aot-after/` hold the symbol tables; budgets re-pinned in
  `benchmarks/comparison/consumer_aot_budgets.json` from the after image (pass) and shown
  failing on the before image only at `SlabMessage_array` (357 > 167 instructions) and the
  generated symbols that did not exist.

| Symbol (x86-64 AOT, shape only) | Before instr / calls | After instr / calls |
|---|---:|---:|
| `SlabMessage_array` | 357 / 13 | 152 / 9 |
| `SlabArray_get` | 1,055 / 72 | 859 / 43 |
| `SlabRow$Slot_lengthIn` (generic) | 1,888 / 146 | 848 / 67 |
| `SlabRow$Slot_readLongBound` (generic) | 486 / 35 | 493 / 35 |
| generated `Field$A{0,1,2}_readLongBound` | — | 133 / 7 |
| generated `Field$A3_lengthBound` | — | 368 / 20 |
| generated `Field$A3_accepts` (never called by read nodes) | — | 38 / 1 |
| `Accessor_bound` | 85 / 3 | 85 / 3 |

## Attribution (diagnosis, not timing)

- `precheck/`: JVM proxy dumps of the consumer `SharedRoot` before the change (slab 7,106
  final HIR nodes, 237 LIR call sites, 45,220 B; generated-owned 756 / 21 / 4,912 B) with the
  surviving call targets.
- `p02-before/`: SVM JFR samples of the before image (`0f91d837`): slab peak 66% of stacks in
  `SlabMessage.array` (`HashMap` probe leaves 66%); slab interpreter `Accessor.bound` 25.9%,
  harness 42.6%. `p02-after/`: the same cells on the after image.
