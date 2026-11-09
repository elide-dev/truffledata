# CSV `CsvBatch.row` force-inline (wave 4, probe 1)

Plan: `docs/superpowers/plans/2026-11-08-beat-stock-svm.md` §4 WS10 readback residue; ledger
"Follow-ups (CSV readback …)" Open item and "CSV wave 4 (CsvText, CsvBatch.row)". Ruling (Sam,
2026-11-09): the JDK-internal `ForceInline` annotation with its compile-time `--add-exports` is
acceptable in the published library *if* this probe measures a paired SVM win; otherwise it does
not land. SVM / Native Image is the admission target; no JVM timing is recorded.

## Summary

- **Candidate (`899ee9c`, two commits):** `@jdk.internal.vm.annotation.ForceInline` on
  `CsvBatch.row`, `CsvBatch.checkLive` and the three typed getters `CsvRow.getLong/getDouble/
  getBoolean`. Same bodies, same errors; `//csv` compiles with
  `--add-exports=java.base/jdk.internal.vm.annotation=ALL-UNNAMED` (confined to the csv target's
  `javacopts`). The getters are "what `row` needs": with them out of line the row escapes as their
  receiver and cannot be scalar-replaced (the `ef16275` getters-only probe left the 24 B row and
  was killed at 0.993×). `checkLive` was added in a second step because, once `row` was inlined,
  it fell out of line as a 36-instruction call per row (it had been inlined into `row`).
- **Mechanically complete.** `CsvBatch_row`, `CsvRow_get*` have no symbol in the after image;
  the typed harness loop (`CsvComparison_lambda$base$9`) has no row, getter or `checkLive` call
  left (341/16 → 486/27 instructions/calls; the remaining calls are the three `*Slow` paths,
  NPE/bounds stubs, safepoints and two cold `IllegalStateException` allocations for the closed
  mapping). **The row is scalar-replaced:** every typed cell drops exactly 24.00 B/row (numeric
  88.57 → 64.57 = the `typed-build` figure, mixed 448.40 → 424.40, 128 MiB 147.95 → 123.95),
  and every strings cell that iterates rows drops the same 24 B (`getString` was already
  inlined). Allocation is deterministic across forks.
- **Timing:** see the verdict below; both runs landed on loaded workers (fork spreads of
  10–20% on unchanged controls, baseline typed numeric native 3.41 and 2.90 ns/B against the
  2.43 recorded on a quiet worker in `csv-readback-ef16275`).

## Source and binaries

- Base `5b2e8b7` (`perf/beat-stock-svm` with Sam's rulings and WS2/WS6.2 merged). Before-images
  built on RBE in invocation `00a23358` (96 remote cache hits) and preserved under ignored
  `build/csv-before-5b2e8b7/` with `manifest.json`; they are byte-identical to the follow-ups'
  `fu-before-cd3c193/csv` images (`csv-comparison` `cb60b545…`, profile `114c3a2a…`), so the
  WS10b/readback profiles describe this baseline exactly.
- Candidates: step 1 `9f79044` (row + getters; invocation `4b4dc345`, `build/csv-probe-9f79044/`),
  step 2 `899ee9c` (+ `checkLive`; invocation `6707e311`, `build/csv-probe-899ee9c/`). Hashes in
  `images.sha256`. Pinned `25.5.5-elide.27` CE SDK, Linux x86-64, `-O2`, `-march=compatibility`,
  serial GC, no PGO.

## AOT shape (`aot/`, `csv_profile`, x86-64, llvm-objdump)

| Symbol | Before (`5b2e8b7`) | Step 1 (`9f79044`) | Step 2 (`899ee9c`) |
|---|---|---|---|
| `CsvBatch_row` | 67 / 4 calls | inlined (no symbol) | inlined |
| `CsvRow_getLong` / `getDouble` / `getBoolean` | 81/7, 80/7, 81/7 | inlined | inlined |
| `CsvBatch_checkLive` | inlined into `row` (no symbol) | 36 / 2 (now called from the loop, 2×) | 36 / 2 (kept for other callers; not called from the loop) |
| `CsvComparison_lambda$base$9` (typed loop) | 341 / 16 / 88 B / 85 stack loads | 408 / 25 / 104 B / 97 | 486 / 27 / 104 B / 87 |
| `CsvComparison_lambda$base$4` (copied strings loop) | 173 / 11 | 188 / 12 | 233 / 14 |
| `CsvBatch_stringAt` | 1,586 / 74 | 1,586 / 74 | 1,618 / 75 (`checkLive` inlined into `stringSlow`) |

Typed-loop calls after step 2: 11 NPE stubs, 3 bounds stubs, 3 safepoints, `longSlow`/
`doubleSlow`/`booleanSlow` once each, `parseBytes`, 2 `slowNewInstance` + 2 `fillInStackTrace`
(the inlined `CsvMapping.closed()` at the two former `row` sites), one unwind. No `CsvRow`
allocation and no getter call remain.

## Paired SVM (`pairs/`)

`csv_pair`, three fresh forks per binary, interleaved on one pinned CPU per shard (AMD EPYC RBE
workers). ns/byte, median of fork medians [fork range]; B/row is Java allocation per row.

### Run 1 (`run1-loaded/`, invocation `21035efc`; shards 0, 1, 4, 5, every default engine)

| Fixture | Workload | Engine | Before | After | Ratio | Ranges | B/row |
|---|---|---|---:|---:|---:|---|---:|
| numeric 1 MiB | typed | plan-native | 3.4137 [2.7192-3.4957] | 2.9062 [2.8873-3.8967] | 0.851 | overlap | 88.57 → 64.57 |
| numeric 1 MiB | typed | plan (Java SWAR) | 4.3023 [3.0989-4.8139] | 3.2699 [3.0792-3.4082] | 0.760 | overlap | 88.51 → 64.51 |
| numeric 1 MiB | typed-build | plan-native | 2.2458 [2.2056-2.5499] | 2.4481 [2.3033-2.4487] | 1.090 | overlap | 64.57 → 64.57 |
| mixed 1 MiB | typed | plan-native | 1.8370 [1.7804-1.9993] | 1.8464 [1.8211-1.8671] | 1.005 | overlap | 448.40 → 424.40 |
| mixed 1 MiB | typed-build | plan-native | 1.7907 [1.7033-1.8959] | 2.1780 [1.6767-2.3313] | 1.216 | overlap | 424.40 → 424.40 |
| numeric 128 MiB | typed | plan-native | 3.5167 [3.1983-7.7948] | 3.1249 [2.9503-3.3993] | 0.889 | overlap | 147.95 → 123.95 |
| numeric 128 MiB | typed-build | plan-native | 2.6206 [2.3903-2.7115] | 2.7479 [2.3764-2.8819] | 1.049 | overlap | 123.95 → 123.95 |
| mixed 128 MiB | typed | plan-native | 2.4073 [2.3374-3.2715] | 2.8644 [2.2400-10.6778] | 1.190 | overlap | 449.11 → 425.11 |
| mixed 128 MiB | typed-build | plan-native | 1.8853 [1.7740-1.9019] | 2.0702 [1.8140-2.0741] | 1.098 | overlap | 425.11 → 425.11 |

Controls in the same shards (code this change does not reach): `kernel-native` typed 0.92 /
1.14 / 1.11 / 1.05, FastCSV typed 0.95 / 1.07 / 0.96 / 0.98, FastCSV strings 0.96 / **1.34** /
1.04 / 1.06; the unchanged construction cell (`typed-build`) moved 1.05–1.22×. The workers
were loaded; no timing conclusion is drawn from run 1.

### Run 2 (`run2/`, invocation `46614515`; shards 0, 1, typed engines only)

| Fixture | Workload | Engine | Before | After | Ratio | Ranges | B/row |
|---|---|---|---:|---:|---:|---|---:|
| numeric 1 MiB | typed | plan-native | 2.8959 [2.6365-3.2058] | 2.5892 [2.4005-2.7889] | 0.894 | overlap | 88.57 → 64.57 |
| numeric 1 MiB | typed | plan (Java SWAR) | 3.2419 [3.1947-3.5328] | 3.1325 [3.0294-3.5336] | 0.966 | overlap | 88.51 → 64.51 |
| numeric 1 MiB | typed-build | plan-native | 2.2718 [2.1731-2.3859] | 2.2497 [2.1743-2.2660] | 0.990 | overlap | 64.57 → 64.57 |
| mixed 1 MiB | typed | plan-native | 2.6954 [2.2901-5.5239] | 2.0229 [2.0106-2.0731] | 0.751 | separate | 448.40 → 424.40 |
| mixed 1 MiB | typed-build | plan-native | 1.9560 [1.7915-2.0924] | 2.0914 [1.7801-2.2793] | 1.069 | overlap | 424.40 → 424.40 |

Controls: `kernel-native` typed 0.99 / 0.94, FastCSV typed 0.88 / 1.14 (separate, unchanged
code), `typed-build` Java 1.08 / 0.95 (separate). The mixed native "separate" row rests on a
baseline fork at 5.52 ns/B; its candidate forks (2.01–2.07) sit inside the baseline's other two
(2.29–2.70) only on the low side. Loaded again.

### Run 3 (`../csv-lazy-text-6d5f938/pairs/run1-with-probe/`, invocation `d3a6708f`; shards 0, 1, 4, 5)

The first `CsvText` candidate image (`6d5f938`) carries this probe unchanged, so its typed cells
are a third observation of the same change against the same baseline.

| Fixture | Engine | Before | After | Ratio | Ranges |
|---|---|---:|---:|---:|---|
| numeric 1 MiB | typed plan-native | 3.2562 [2.9674-3.6607] | 3.3498 [3.0166-3.9295] | 1.029 | overlap |
| numeric 1 MiB | typed plan (Java) | 4.5127 [3.6546-5.0447] | 3.7671 [3.1248-4.0909] | 0.835 | overlap |
| mixed 1 MiB | typed plan-native | 2.5416 [1.9889-2.8232] | 2.2151 [2.0372-2.3420] | 0.872 | overlap |
| numeric 128 MiB | typed plan-native | 3.5492 [2.9721-3.5580] | 3.3818 [2.7991-3.8230] | 0.953 | overlap |
| mixed 128 MiB | typed plan-native | 2.6687 [2.5653-2.9613] | 2.3035 [2.2591-2.7442] | 0.863 | overlap |

Controls: `kernel-native` typed 1.12 / 0.79 / 1.01 / 1.01, FastCSV typed 1.14 / 1.14 / 0.99 /
1.06, `typed-build` native 0.98 / 0.95 / 0.89 / 1.12 (all overlapping).

### Run 4 (`run4/`, invocation see `provenance.json`; shards 0, 1, `truffledata-plan` typed cells only)

| Fixture | Engine | Before | After | Ratio | Ranges |
|---|---|---:|---:|---:|---|
| numeric 1 MiB | typed plan-native | 3.1104 [2.8025-3.2307] | 2.6807 [2.5953-3.6529] | 0.862 | overlap |
| numeric 1 MiB | typed plan (Java) | 3.2225 [3.1847-3.7218] | 3.3250 [2.9962-3.3837] | 1.032 | overlap |
| mixed 1 MiB | typed plan-native | 2.2542 [2.0969-2.2819] | 2.0248 [1.9540-2.4334] | 0.898 | overlap |
| mixed 1 MiB | typed plan (Java) | 2.7150 [2.4600-3.0066] | 2.5567 [2.5463-3.2163] | 0.942 | overlap |

## Verdict

**Killed** under the brief's rule for probe 1 (<3% or overlapping forks). Over four pairs on
RBE workers the typed native cells moved 0.851 / 0.894 / 1.029 / 0.862 (numeric 1 MiB) and
1.005 / 0.751 / 0.872 / 0.898 (mixed 1 MiB) with overlapping fork ranges in every run but one
(run 2 mixed, carried by a 5.5 ns/B baseline fork), while unchanged controls in the same shards
moved as much (0.79–1.34). The direction is consistent (a −10 to −15% median) and the
mechanism is real — the `CsvRow` is scalar-replaced and every typed and strings cell allocates
exactly 24.00 B/row less — but no uncontended worker was obtained and the brief admits only a
disjoint win. By Sam's ruling the JDK-internal `ForceInline` annotation and its
`--add-exports` therefore do not land: both commits are reverted (`af16de0`, `76904de`), so
production `CsvBatch`/`CsvRow` are `5b2e8b7`'s. What stays measured for a later tranche: the
remaining typed readback is the per-row `row` call and its allocation, and force-inlining
`row`+getters+`checkLive` removes both mechanically (AOT shape above); a quiet-worker pair is
the missing evidence, not a new mechanism.

## Reproduction

```sh
bzl build //benchmarks/comparison:csv_svm //benchmarks/comparison:csv_profile  # at 5b2e8b7, then at 899ee9c
# preserve bazel-bin/benchmarks/comparison/csv-comparison{,-profile} under build/<dir>/ with an exports_files BUILD
bzl test //benchmarks/comparison:csv_pair \
  --//benchmarks/comparison:csv_svm_candidate=//build/csv-probe-899ee9c:csv-comparison \
  --//benchmarks/comparison:csv_svm_baseline=//build/csv-before-5b2e8b7:csv-comparison \
  --test_env=CSV_PAIR_SHARDS=0,1 --test_env=CSV_PAIR_ENGINES=typed --nocache_test_results
python3 tools/bench/aot_symbols.py IMAGE --pattern '^CsvBatch_(row|checkLive)$' \
  --pattern '^CsvRow_get(Long|Double|Boolean)$' --pattern '^CsvComparison_lambda\$base\$9$'
```
