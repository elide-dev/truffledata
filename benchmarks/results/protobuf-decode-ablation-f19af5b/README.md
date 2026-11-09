# Protobuf SVM decode pipeline ablation — P0.1 (2026-11-08)

Plan: `docs/superpowers/plans/2026-11-08-beat-stock-svm.md` §3 P0.1 (decides WS2/WS3); rulings in
`2026-11-08-beat-stock-svm-ledger.md` under "P0.1/WS2 (protobuf decode worker)".

**Source.** Production code is `bf06974` (the merged WS1+WS4 tip of `perf/beat-stock-svm`);
`f19af5b` adds only benchmark code: the `probe-*` engines (`benchmarks/comparison/probes/`,
split-package helpers reachable from the consumer harness only), the standalone native probe
(`consumer_native_probe.c`) and the unsharded run `//benchmarks/comparison:consumer_decode_ablation`.
Probe image sha256 `69b0c19f…f092` (`binary.sha256`; the production-only before-image of this
worktree is `81f5b58b…e11c`, preserved under ignored `build/before-bf06974/`). Linux x86-64,
SDK `25.5.5-elide.27`, `-O2`, `-march=compatibility`, serial GC, no PGO.

**Run.** One RBE worker (AMD EPYC, pinned to CPU 6, load 0.0 before), invocation
[`9e9929b7`](https://app.buildbuddy.io/invocation/9e9929b7-3fd6-41e0-8579-82c033cf64b4):
`tools/bench/consumer.py` with three fresh forks per cell (time and memory), interleaved, every
probe plus `stock`, `slab`, `slab-session` on the full fixture and the probe references on the
holdout fixture (`probes/results.json`, `probes/results.md`), then the standalone decoder three
times per fixture on the same CPU (`native.jsonl`), then diagnostic JFR samples (`jfr/`).
`perf` is not installed on the worker (`perf.txt`, `perf_event_paranoid` 2), so no `perf record`
exists; JFR stands in and cannot see native frames (threads in native code are not sampled).

Times are ns/row (= per message, 32 rows per operation including the harness's own loop and
release), medians of fork medians with the observed fork range, not confidence intervals.
Memory is current-thread Java allocation per row. Full fixture: 149.9 B wire/message, slab
extents 368–728 B, learned capacity 728 B; holdout: 2,200 B capacity. Every probe decode was
checked byte-for-byte against the production slab before timing (`DecodeProbe.check`).

## Owning slab pipeline, one stage at a time (`SvmBackend.slab(WireInput)`, the decode cell)

| Stage (cumulative) | Probe | Full ns/row | Δ | Java B/row | Holdout ns/row | Δ | Java B/row |
|---|---|---:|---:|---:|---:|---:|---:|
| Harness `prepare()`/`release()` loop | `probe-empty` | 6.9 (6.9–7.6) | — | 0 | 7.0 (6.8–7.1) | — | 0 |
| Native decode into reusable scratch (ordinary transition) | `probe-native` | 282.8 (281.7–289.3) | +275.9 | 0 | 744.6 (739.4–747.2) | +737.6 | 0 |
| + fresh zeroed `byte[capacity]` | `probe-alloc` | 305.2 (305.0–310.4) | +22.4 | 736 | 943.1 (940.1–953.4) | +198.5 | 2,208 |
| + output `PinnedObject`, decode into it | `probe-pin` | 348.9 (339.8–368.4) | +43.7 | 760 | 961.7 (961.3–962.6) | +18.6 | 2,232 |
| + `copyOf` trim rule | `probe-trim` | 335.0 (332.4–340.2) | −13.9 | 760 | 979.9 (950.6–1,177.8) | +18.2 | 2,232 |
| + `SlabVerifier.verifyOwning` | `probe-verify` | 935.8 (930.8–2,951.7) | **+600.8** | 760 | 2,779.9 (2,666.2–3,562.2) | **+1,800.0** | 2,232 |
| + `takeOwnership` owner publication | `probe-publish` | 938.7 (933.3–977.0) | +2.9 | 808 | 3,444.0 (3,421.1–3,448.5) | **+664.1** | 2,294 |
| + `BuffaPlan.decodeSlab` plumbing (= production) | `probe-plan` | 972.1 (963.5–976.8) | +33.4 | 808 | 3,479.0 (3,470.1–3,485.6) | +35.0 | 2,294 |
| Production engine through the harness's `slab(i)` | `slab` | 1,006.0 (991.8–18,431.8) | | 808 | — | | — |
| Stock protobuf-java parse (reference) | `stock` | 791.9 (790.0–804.0) | | 1,629.5 | — | | — |
| Standalone native decoder, same CPU, no JVM | `consumer_native_probe` | 267.3 (260.8–271.3) | | — | 732.5 (730.7–734.7) | | — |

- The trim copy never runs on either fixture (`probe_trim_rows` 0 of 32: the learned capacity is
  never more than `max(256, extent)` above an extent), so the trim row is noise.
- The Java verifier is the decode cell's largest stage: 600.8 ns of 972.1 (61.8%) on the full
  fixture, more than the native decode itself (267–283 ns). On the holdout fixture the static
  budget bound (`Layout.withinBudget`) fails for slabs with that many handles, so
  `verifyOwning` returns `EXACT` and `takeOwnership` runs the constructor's exact accounting walk
  as well: +1,800 + 664 = 2,464 ns of 3,479 (70.8%).
- `probe-publish-generic` (publication through the generic `SlabMessage` constructor instead of
  the generated root factory) is 940.5 (927.8–952.2) against `probe-publish` 938.7
  (933.3–977.0): the factory call WS1 added is not measurable (−1.8 ns, overlapping). The +4.9%
  WS1 decode movement's unexplained ~1.5% is therefore not the factory; in this one run two
  engines executing the same production call (`slab` 1,006.0 and `probe-plan` 972.1) differ by
  3.5%, which is the process-level noise floor of this cell.
- `probe-trusted` (pin + decode + `owning(..., 0)`: no Java walk at all, the WS2 shape) is
  348.9 (348.0–350.9) full / 971.8 (968.9–992.9) holdout. `probe-internal` (header check plus the
  exact logical validation only, without the structural walk) is 787.9 (782.1–1,379.2) /
  2,175.7: keeping the Java logical accounting would recover only 151 ns of the 590.

## Session path residual (`slab-session`, scoped arena, no owner)

| Probe | Full ns/row | Holdout ns/row | Java B/row |
|---|---:|---:|---:|
| `probe-leaf-call` (no-op leaf `@CFunction`) | 7.9 (7.8–8.2) | 8.0 (7.8–8.2) | 0 |
| `probe-transition` (no-op ordinary `@CFunction`) | 13.1 (13.0–13.2) | 13.2 (13.0–13.3) | 0 |
| `probe-pin-only` (`PinnedObject` create/close) | 29.0 (28.1–45.8) | 28.3 (28.2–31.8) | 24 |
| `probe-lease` (`WireInput` retain/size/attachment/release) | 18.2 (18.2–18.4) | 18.3 (18.1–18.7) | 0 |
| `probe-native-leaf` (decode through the leaf `decodeSlabSmall`) | 282.3 (278.6–288.5) | 749.3 (737.2–900.3) | 0 |
| `probe-session-raw` (`SvmBackend.writeSlab` into the arena) | 289.0 (284.1–290.9) | 757.8 (749.4–761.5) | 0 |
| `probe-session` / `slab-session` (production) | 805.0 (800.8–809.0) / 797.8 | 2,065.9 (2,065.4–2,082.7) | 40 |

Net of the 6.9 ns harness: one ordinary transition costs 6.2 ns and a leaf call 1.0 ns, so the
transition is ~5 ns (decoding through the leaf entry measures the same as the transitioning one,
282.3 vs 282.8); a pin is 22 ns and 24 B; lease handling 11 ns. The session's native write is
289.0 − 267.3 = **22 ns above the standalone decoder** (holdout 25 ns): transition, result slot,
busy flag, attachment checks and harness together. The rest of the session cell, 516 ns full /
1,308 ns holdout, is `SlabSession.decode`'s own Java work after the native call, and the JFR
samples put it in the borrow validation walk: `SlabSession.word` 1,164, `validateRecord` 836,
`validateValue` 291 of 2,959 Java samples (77%); `BorrowedSlabRow.<init>`/lease/reset are the
remainder. The plan's "session path (no owner, no verifier, no output array)" is therefore only
partly right: the session has no structural verifier but does run a Java logical validation
(required fields and budgets) over every borrow.

The plan's "~600 ns unattributed residual above the standalone probe (182 ns)" compared
different shapes and workers. On the consumer fixture on the same worker the standalone decoder
is 267 ns (full, 150 B wire) and 733 ns (holdout); the residual is the Java validation walk, not
per-call fixed cost. **WS3's condition (≥250 ns/row of per-call fixed cost on the session path)
is not met: the measured per-call cost is 22–25 ns.**

## Copy-instead-of-pin and exact-extent variants (contract-preserving candidates)

| Probe | Full ns/row | Java B/row | Holdout ns/row | Java B/row | Compare with |
|---|---:|---:|---:|---:|---|
| `probe-copy-out` (decode into reusable native scratch, copy exact extent to a fresh `byte[extent]`) | 323.7 (321.8–326.9) | 555.2 | 931.3 (929.0–956.1) | 1,646.5 | `probe-trim` 335.0 / 979.9 |
| `probe-copy-out-publish` (+ `takeOwnership`) | 951.2 (944.1–977.0) | 603.2 | 3,454.8 (3,296.0–3,556.3) | 1,708.5 | `probe-publish` 938.7 / 3,444.0 |
| `probe-copy-out-leaf-publish` (leaf decode) | 949.9 (947.2–16,038.9) | 603.2 | 3,336.3 (3,318.4–3,931.3) | 1,708.5 | `probe-publish` |
| `probe-trusted-copy-out-leaf` (no Java walk) | 325.9 (324.9–424.5) | 603.2 | 930.5 (928.6–951.3) | 1,694.5 | `probe-trusted` 348.9 / 971.8 |
| `probe-heap-pin2` (heap input: both arrays pinned, as production) | 372.4 (363.2–383.3) | 784 | 997.3 (992.7–1,010.2) | 2,256 | |
| `probe-heap-copy-in` (heap input copied to reusable native scratch, output pinned) | 349.3 (345.1–355.1) | 760 | 972.9 (952.4–977.3) | 2,232 | `probe-heap-pin2` |
| `probe-plan-heap` (production heap-input decode) | 1,016.5 (1,007.0–1,034.9) | 832 | 3,564.1 (3,502.9–3,613.4) | 2,318 | `probe-plan` 972.1 / 3,479.0 |

Exact-extent copy-out saves 11 ns (−3%) and 205 B/row before verification (holdout 49 ns,
586 B/row) and makes the retained array exact, but end to end with publication it measures
+12.5 ns full (+1.3%, overlapping) and +0.3% holdout; the leaf entry adds nothing measurable.
Copying the heap input (≤256 B here: 150 B average) instead of pinning saves 23 ns, 2.3% of the
heap-input cell. Every contract-preserving pin/copy/trim/leaf variant is ≤5% of the decode
cell, below the brief's 10% admission bar; none was implemented in production.

## Attribution inside the verifier (shape and samples, not timing)

JFR on `slab full decode` (2,953 Java samples; native time is invisible): leaf
`SlabFormat.getInt` 1,276 (43%), `SlabVerifier.ownedValue` 520, `SlabVerifier.owned` 430,
`PinnedObject.create` 215, `SlabFormat.getLong` 71, `SlabCodec.utf16` 68. The AOT bodies
(profile image `6b18aadf…`, same production source) show why: `SlabFormat.getInt` inlines, but
its `ByteArraySupport` call devirtualizes to `CheckedByteArraySupport.getInt`, a 56-instruction
out-of-line body that allocates its exception inline and is never inlined, so every slab word
the verifier reads is a call (6 call sites in `SlabVerifier_owned`, 8 in `_ownedValue`, plus
the non-inlined `SlabFormat_utf16Length`/`_payload`/`_unknown`/`_oneofCase`, `SlabVerifier_span`
and `_claim`). The session walk has the same shape (`SlabSession_span` 5 call sites in
`validateRecord`). UTF-8 re-validation (`SlabCodec.utf16`) is 2% of the samples.

## WS2 upper bound (measured)

Skipping every Java walk for slabs our own decoder wrote: full 938.7 → 348.9 ns/row
(**−589.8 ns, −62.8%** of the publication path; production decode 972.1 would become ≈382 ns,
0.48× stock 791.9, before any native accounting cost) and holdout 3,444.0 → 971.8 (−2,472 ns,
−71.8%). The session's borrow validation (516 / 1,308 ns) is the same question for scoped
decodes. Implementation needs Sam's ruling (ledger, Open).

## Files

`probes/` (consumer.py report: `results.json` raw samples, `results.md`), `native.jsonl` (three
standalone runs × two fixtures × two entry points; `ns_per_row` = per message), `fixture/`
(the dumped schema program, wires, limits, capacity and per-message extents the standalone run
used), `jfr/` (recordings, cell records and `summary.json` from `tools/bench/json_profile_summary.py`),
`perf.txt`, `binary.sha256`, `invocation.txt`.
