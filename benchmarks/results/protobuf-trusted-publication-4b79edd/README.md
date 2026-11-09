# Protobuf trusted in-process slab publication — WS2, `4b79edd` (2026-11-09)

Plan: `docs/superpowers/plans/2026-11-08-beat-stock-svm.md` §4 WS2; Sam's ruling and the design entry
are in `2026-11-08-beat-stock-svm-ledger.md` ("P0.1/WS2" and "Rulings (Sam, 2026-11-09)"); this
worker's entries are under "WS2 (protobuf trusted publication worker)". Evidence for the mechanism:
`../protobuf-decode-ablation-f19af5b/` (the Java verifier was 600.8 of 972.1 ns/row of the full
decode cell; `probe-trusted` bounded the gain at −62.8%).

**Change.** Seam ABI 11 → 12: the Rust slab decoder accumulates the logical totals that
`SlabMessage.validate` computes (cells and UTF-16 string bytes, orphaned payloads subtracted, proto2
absent defaults from a new TDB3 field-entry word) and reports them in `SlabResult` (`reserved u64`
→ `cells u32 · string_bytes u32`, `0xFFFFFFFF` when unknown). The SVM/FFM backends publish slabs
their own decoder wrote into arrays they allocated and never exposed through the new
`SlabMessage.produced`: a `CERTIFIED` owning slab whose totals lie within the plan's limits keeps
only the O(1) `SlabFormat.check` header check; anything else takes the previous `INTERNAL` path
(header check + exact `validate`) with unchanged messages. External, copied (`copyOf`,
`copyRecordOf`, session `retain`), projected and `takeOwnership` slabs keep the full
`SlabVerifier.verifyOwning` walk. Slab bytes and the golden files are unchanged (`golden_slabs_match`
and `LayoutTest` pass unmodified). Production source `1b01c5b`; `4b79edd` adds only the
`@TruffleBoundary` budget for the new entry (`PolicyTest`).

**Images.** Before: `5bc030b` (the worktree base, built on RBE, invocation `0b5ebf6c`, remote-cache
hit, preserved under ignored `build/before-5bc030b/`; consumer sha256 `9410bf02…a7de`, byte-identical
to the `954656d` after-image of `../protobuf-verifier-loads-954656d/`, so that pair's "after" is
this pair's "before"). After: `4b79edd`, consumer `37259773…a900`, profile `fe65096e…15f5`
(`binary.sha256`). Linux x86-64, SDK `25.5.5-elide.27`, `-O2`, `-march=compatibility`, serial GC, no
PGO. Only x86-64 claims are made; the host (Apple M5 Max) produced no timings.

## Paired SVM measurement (`//benchmarks/comparison:consumer_measure`, ten shards, three fresh forks)

Baseline engines `stock,generated-owned,slab,slab-session`; every shard measures whole (mode, stage)
groups for both binaries on one worker. ns/row, median of fork medians (fork range); Java B/row is
the current-thread allocation per row. Run 1: invocation
[`7d979173`](https://app.buildbuddy.io/invocation/7d979173-7d3a-4663-ab20-8b2446b27307) (`svm/`);
run 2, a replication with identical flags: [`997b4647`](https://app.buildbuddy.io/invocation/997b4647-86b2-449f-851a-2483316796c6)
(`svm-run2/`). Full per-cell tables for the four engines: `svm/paired.md`, `svm-run2/paired.md`;
the complete matrices (all nine engines, every cell): `svm*/merged/results.md`.

### Target cells (`slab` engine)

| Cell | Run 1 before | Run 1 after | Run 1 change | Run 2 before | Run 2 after | Run 2 change |
|---|---:|---:|---:|---:|---:|---:|
| Full decode | 965.2 (932.0–2,030.2) | **423.0 (420.8–430.2)** | **−56.2%, disjoint** | 1,056.6 (998.2–1,514.8) | **448.7 (430.4–451.0)** | **−57.5%, disjoint** |
| Full resident | 1,341.9 (1,232.3–1,357.9) | 909.7 (777.2–2,019.1) | −32.2%, overlapping (one 2,019 fork, max sample 16,896) | 1,297.3 (1,253.3–1,415.1) | 773.8 (717.7–21,683.2) | −40.3%, overlapping (one 21,683 fork) |
| Full heap | 1,355.4 (1,333.9–1,783.2) | 903.8 (834.9–992.1) | −33.3%, disjoint | 1,515.3 (1,401.8–1,783.0) | 1,060.2 (783.2–3,221.0) | −30.0%, overlapping |
| Full detach | 1,525.5 (1,356.3–2,038.8) | 920.9 (796.1–8,354.3) | −39.6%, overlapping | 1,817.2 (1,345.6–2,667.4) | 862.6 (730.3–978.7) | −52.5%, disjoint |
| Sparse decode | 1,152.5 (942.2–2,021.2) | 498.3 (479.1–768.5) | −56.8%, disjoint | 1,022.1 (1,001.8–1,305.8) | 517.7 (472.5–633.0) | −49.4%, disjoint |
| Sparse resident | 1,016.9 (999.1–1,627.4) | 484.8 (473.9–812.1) | −52.3%, disjoint | 1,059.0 (1,041.2–1,072.5) | 512.1 (481.9–514.7) | −51.6%, disjoint |
| Sparse heap | 1,066.5 (1,064.8–1,071.2) | 546.8 (527.1–625.8) | −48.7%, disjoint | 1,319.8 (1,215.0–1,721.6) | 559.0 (556.8–572.5) | −57.6%, disjoint |
| Holdout resident | 5,240.0 (3,652.8–5,922.6) | 1,903.1 (1,481.7–2,112.9) | −63.7%, disjoint | 4,160.4 (3,958.0–4,501.8) | 1,626.7 (1,577.0–1,632.3) | −60.9%, disjoint |
| Large decode | 392,221 (282,914–602,977) | 107,399 (105,159–112,555) | −72.6%, disjoint | 290,919 (280,231–312,764) | 101,531 (99,550–111,740) | −65.1%, disjoint |
| Large resident | 353,845 (347,251–388,822) | 157,368 (156,841–184,771) | −55.5%, disjoint | 377,406 (365,385–504,866) | 173,799 (164,059–178,182) | −53.9%, disjoint |

Full slab decode against stock in the same shard: 423.0 / 809.8 = **0.52× stock** (run 1), 448.7 /
864.1 = 0.52× (run 2); it was 1.16–1.22× before (965.2 / 834.9; 1,056.6 / 1,356.7 on run 2's loaded
worker). The P0.1 bound (`probe-trusted` 348.9 with the probe's reusable scratch, production ≈382)
predicted ≈−60%; the production cell lands at −56–58% because `BuffaPlan` plumbing, the fresh
`byte[]` and the pin remain.

Read cells (first, interpret, peak) move within their ranges in both runs, as they must (no read
path changed); the only disjoint read movement is run 1's large peak `slab` +64% (16,568 → 27,166),
whose after forks (21,722 / 27,166 / 36,405, one 312,584 sample) are worker noise: run 2 measures the
same cell at 17,645 → 17,731 (+0.5%).

### Controls and the session cell

| Cell | Run 1 before → after | Run 2 before → after |
|---|---:|---:|
| Full decode `stock` | 834.9 (814.5–870.2) → 809.8 (799.6–811.4), −3.0% disjoint | 1,356.7 (1,262.4–1,458.7) → 864.1 (857.8–879.0), −36.3% disjoint (loaded baseline forks) |
| Full decode `generated-owned` | 909.7 (895.1–950.7) → 926.4 (859.3–1,122.1), +1.8% overlapping | 1,064.4 (962.9–1,295.2) → 991.2 (970.1–1,126.7), −6.9% overlapping |
| Full decode `slab-session` (unchanged path) | 848.0 (823.6–1,224.9) → 982.8 (854.3–989.1), +15.9% overlapping | 965.1 (948.1–1,506.2) → 947.0 (907.6–1,363.1), −1.9% overlapping |
| Sparse resident `slab-session` | 934.4 (930.0–947.5) → 1,426.9 (1,252.2–1,888.0), +52.7% disjoint | 1,121.0 (978.2–1,154.1) → 1,273.7 (925.8–1,541.9), +13.6% overlapping |
| Holdout resident `slab-session` | 2,406.6 → 3,049.1, +26.7% overlapping | 2,979.6 → 2,622.1, −12.0% overlapping |
| Large decode `slab-session` | 235,762 → 251,698, +6.8% overlapping | 225,047 → 231,551, +2.9% overlapping |

The session path is untouched by this change (it never ran the owning verifier and does not call
`produced`), and the standalone decoder below shows no accounting cost of that size; the
`slab-session` movements are of both signs across the two runs, with their largest (+52.7%,
run 1 sparse resident) not reproduced by run 2 (+13.6%, overlapping; the same shard's `stock` control
moved +1.4%). Both runs show more worker load than `../protobuf-verifier-loads-954656d/svm/` did
(controls moving ±10–36% with overlapping forks, `load_average_after` up to 1.0 on two shards); the
target cells separate by 2× regardless.

### Java allocation

Identical in every cell except the two whose verifier allocated: `slab` large decode 226,294.5 →
189,880.0 B/row and large resident 317,592.0 → 281,177.5 (the `verifyOwning` identity array,
`new long[handles + 2]` for slabs with more than 62 handles, no longer runs), and holdout resident
3,405.2 → 3,391.2. Full decode stays at 808 B/row (the `SlabMessage` owner stays 48 B; the retained
array is the learned 728 B capacity), `slab-session` at 40 B, the compact-scalar floors at 168.0 /
136.0 B and the owned record at 48.0 B (`gates/native_smoke.log`).

## Standalone decoder (accounting cost; `consumer_decode_ablation`, one worker, three processes)

The Rust decoder's new work per message is the accumulation (one add per record, string, list and
unknown span, a slot read-back per singular string/bytes field) plus the two result words.
`ablation-before/` is the base commit `5bc030b` (invocation `4032addb`), `ablation-after/` is
`4b79edd` (invocation `0ed6bf11`), `ablation-after-run2/` a second, uncached after run (invocation
`72b1b8cc`); `native.jsonl` holds `td_buffa_decode_slab` / `_small` on the dumped consumer fixtures
(full: 149.9 B wire, 728 B capacity; holdout: 510 B, 2,200 B), fifteen 50 ms rounds per process, ns
per message, median (minimum) of each of the three processes:

| Entry · fixture | Before `5bc030b` | After run 1 | After run 2 |
|---|---|---|---|
| `decode_slab` · full | 266.8 (264.2) · 282.9 (273.1) · 280.0 (266.7) | 349.1 (335.1) · 386.4 (284.3) · 290.7 (283.4) | 351.3 (245.9) · 304.9 (255.8) · 398.6 (281.3) |
| `decode_slab_small` · full | 279.4 (267.2) · 289.8 (282.5) · 274.1 (265.9) | 373.8 (332.6) · 331.5 (294.1) · 296.6 (286.8) | 310.2 (252.6) · 393.7 (382.8) · 373.0 (279.1) |
| `decode_slab` · holdout | 754.7 (738.1) · 794.9 (775.1) · 755.1 (741.3) | 891.9 (814.4) · 1,306.7 (926.4) · 800.6 (774.2) | 891.9 (756.7) · 1,137.3 (1,062.7) · 1,011.5 (772.0) |
| `decode_slab_small` · holdout | 761.1 (739.1) · 803.8 (761.6) · 743.1 (736.0) | 1,133.1 (839.2) · 784.9 (774.4) · 841.0 (785.4) | 786.6 (686.6) · 1,129.9 (1,109.7) · 934.5 (683.3) |

The before run reproduces P0.1's quiet-worker figure (267.3 full / 732.5 holdout); both after runs
landed on loaded workers: their medians spread 291–399 full with per-process minima 246–335, and
the same workers measured the unchanged `stock` engine at 849 (812–924) and 1,039 (991–1,220)
against 792–835 in the pairs, `probe-empty` (the harness loop alone) at 7.2–9.7 against 6.9. Those
runs therefore bound the accounting cost rather than measure it: the after minima (245.9, 255.8,
281.3, 283.4, 284.3) bracket the before minima (264.2–273.1), so the cost is inside the worker
spread and below any resolvable threshold on these workers; the paired matrix's `slab-session`
cell, whose native call carries the same accounting and nothing else of this change, moves −1.9%
(run 2) / +15.9% (run 1) with overlapping forks. The A2b stop rule holds: the hot loop gained one
add per record/string/list/unknown span, a slot read-back before each singular string or bytes slot
write, and the per-type defaults loop at close for proto2 types only; the Rust test
`logical_totals_follow_the_java_accounting` and the parity differential pin the semantics.

The probe engines (`*/probes/results.md`) on the same loaded workers: `probe-native` (native decode
into reusable scratch, ordinary transition) 314.7 (285.5–387.8) before vs 350.4 (300.0–528.9) /
355.3 (272.0–945.9) after; `probe-trusted` (pin + decode + `owning(..., 0)`, the WS2 shape) 501.2
(416.4–607.6) vs 414.2 (382.4–469.2) / 598.8 (376.6–602.2); `probe-plan` (the production call)
1,271.0 (938.3–1,680.5) before vs 452.5 (442.5–471.7) / 631.3 (467.6–753.7) after; `probe-verify`
(the owning verifier stage) 882.5 / 932.0 / 1,526.1 — the Java walk still exists for external slabs
and the probe still runs it; the production `slab` engine 1,050.2 (949.1–1,166.4) before vs 466.1
(413.2–596.8) / 552.8 (421.4–723.7) after. The paired ten-shard runs above, not these, carry the
decode claim.

## Gates (all in the final pair's invocation `997b4647`, and in `7d979173`; all pass)

`//protobuf-runtime:test` (goldens, `SlabVerifierTest` external-slab tests unchanged),
`//protobuf-runtime:slab_replay` (fuzz corpus), `//protobuf-buffa/native:test` (`golden_slabs_match`,
`small_entry_allocates_nothing`, `failure_injection_leaks_nothing`, the new
`logical_totals_follow_the_java_accounting`), `//protobuf-buffa/tests:slab_producers` (the parity
differential plus the new `producedTotalsMatchJavaValidation`: native totals equal the Java `validate`
totals on every golden wire, 6,000 random wires per fixture plan including the fuzz harness's plans and
a proto2-defaults plan, with cell/byte budgets at and one below the totals rejecting through
`produced` with `takeOwnership`'s messages; and `corruptedCertifiedSlabFailsTheHeaderCheckOrTheExternalPath`),
`//protobuf-buffa/tests:jvm`, `:jvm_non_strict`, `:jvm_no_native_access` (the FFM backend now
publishes through `produced`), `//tests:test` (policy: boundary budget 16 → 17 for `SlabMessage`),
`//tests:allocation`, `//benchmarks/comparison:consumer_smoke` and `:consumer_native_smoke` (132
cells each, `gates/*.log`), `:native_smoke` (168.0 / 136.0 B floors, 48.0 B owner),
`:consumer_aot_symbols` (`gates/aot-symbols.md`; the before and after profile images disassembled
with the same local llvm tooling are identical for every budgeted symbol, so no re-pin: the change
touches no read-path body).

## Files

`svm/`, `svm-run2/`: `shards/shard_N_of_10/results.json` raw samples, `merged/` (`consumer.py merge`),
`paired.md` (the four-engine before/after table with fork ranges and Java B/row), `invocation.txt`.
`ablation-before/`, `ablation-after/`, `ablation-after-run2/`: `probes/`, `native.jsonl`,
`binary.sha256`, `perf.txt`, `invocation.txt`. `gates/`: AOT symbol report and smoke logs.
`binary.sha256`: the after images.
