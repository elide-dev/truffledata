# CSV typed readback getters (follow-up, Lever A)

Plan: `docs/superpowers/plans/2026-11-08-beat-stock-svm.md` §4 WS10 mechanism (3), the part
left unfinished (`CsvRow` readback getters). Ledger: "Follow-ups (CSV readback, JSON
directoryKey)". SVM / Native Image is the admission target; JVM timing is not recorded here and
gates nothing.

## Summary

- **Why the getters were out of line.** With the CE SDK, Native Image inlines an AOT callee into
  ordinary Java code only when the callee is "trivial":
  `InliningUtilities.isTrivialMethod`, at most 20 graph nodes with one call
  (`MaxNodesInTrivialMethod`), or 40 with none. Native Image's points-to analysis treats every
  instance field as possibly null, so each getter carries explicit checks: `batch`, `columns`,
  the column element and `present*` for null, plus the column and row bounds. The getters
  therefore exceed the budget. `getString` is a single load and call, so it is trivial and was
  already inlined. Truffle host inlining (`@BytecodeInterpreterSwitch`, as on `stringAt`) only
  inlines callees *into* a rooted method. Rooting the getters would therefore grow the getters
  (by inlining `longSlow`) and leave the call in the caller's loop. That variant was not built.
- **Candidate (`ef16275`):** `@jdk.internal.vm.annotation.ForceInline` on `getLong`, `getDouble`
  and `getBoolean`. Native Image honors it (`HostedMethod.shouldBeInlined`), and HotSpot ignores
  it outside the JDK. The bodies and errors are unchanged. `//csv` compiles with
  `--add-exports=java.base/jdk.internal.vm.annotation=ALL-UNNAMED`, and `data_java_library`
  gains a `javacopts` pass-through.
- **It works mechanically.** The three getter symbols disappear. The harness loop has no getter
  call left (`CsvComparison_lambda$base$9` 341 → 678 instructions, its calls now
  `*Slow` paths and exception stubs). Callgrind typed numeric 1 MiB falls 3.3% (−12 Ir per cell),
  and construction (`typed-build`) is identical to the instruction.
- **It does not show in time.** Paired SVM typed native numeric 1 MiB 2.4306 → 2.4142 ns/B
  (0.993×, **overlapping** forks; 0.411 → 0.414 GB/s). Readback (typed − typed-build) stays
  0.461 → 0.470 ns/B, 19% of the cell. **Killed** under the brief's rule (<3% on typed numeric
  1 MiB with overlapping forks) and reverted.

## Source and binaries

- Base `cd3c193`. Before-images were built in RBE invocation `3aa6d586` and preserved with a
  SHA-256 manifest under ignored `build/fu-before-cd3c193/csv/`. `csv-comparison` `cb60b545…`
  is byte-identical to the WS10b FLOAT64 candidate image (`../csv-numbers-5f0406d/`), so that
  image is this pair's baseline; the profile image is `114c3a2a…`.
- Candidate tree `ef16275` = Lever A plus the tape-only Lever B commit `ada7835`. `//tape` is
  not in the CSV image closure. The images come from RBE invocation `5311f2f0`, preserved under
  `build/fu-cand-ef16275/csv/` (`csv-comparison` `1ef350e0…`, profile `b23f180a…`). All hashes
  are in `images.sha256`.
- Images: pinned `25.5.5-elide.27` CE SDK, Linux x86-64, `-O2`, `-march=compatibility`,
  serial GC, no PGO.

## AOT shape (`aot/`, `csv_profile`, x86-64, llvm-objdump)

| Symbol | Before (`cd3c193`) | After (`ef16275`) |
|---|---|---|
| `CsvRow_getLong` / `getDouble` / `getBoolean` | 81 / 7 calls, 80 / 7, 81 / 7 (24 B frame) | inlined (no symbol) |
| `CsvComparison_lambda$base$9` (typed cell loop) | 341 / 16 / 88 B / 85 stack loads | 678 / 51 / 104 B / 128 |
| `CsvBatch_row` | 67 / 4 | 67 / 4 |
| `CsvBatch_longSlow` | 82 / 8 | 82 / 8 |
| `CsvBatch_stringAt` | 1,586 / 74 | 1,586 / 74 |

Before, each getter's 7 calls were `longSlow` plus 4 NullPointerException and 2 index stubs; the
hot path was about 30 instructions plus the call. In the harness loop each call site saved and
reloaded the loop state (5–6 stack loads around every call). After inlining, the lambda's
51 calls are 2 `row`, 2 per `*Slow`, 26 NPE and 12 bounds stubs and safepoints; no getter is
called. `csv_aot_budgets.json` is unchanged (it pins no getter, and the candidate is reverted).

## Callgrind (`callgrind/`, profile image, Valgrind 3.24 amd64 container)

`--profile typed|typed-build truffledata-plan-native numeric 1048576 2` (two iterations, 40,480
rows, 8 typed cells per row = 323,840 cell reads), collection toggled on `profileRegion`.

| | Before | After |
|---|---:|---:|
| typed total | 118,768,915 | 114,801,811 (−3.34%) |
| typed-build total | 93,653,936 | 93,653,936 (identical) |
| readback (typed − typed-build) | 25,114,979 | 21,147,875 (−15.8%) |
| getters + harness loop self | 22,871,410 (70.6 Ir/cell) | 18,904,306 (58.4 Ir/cell) |
| `CsvBatch_row` + `checkLive` (per row, unchanged) | 2,186,038 | 2,186,038 |

## Paired SVM (`pairs/`, invocation `7ed224e5`)

`csv_pair`, shards 0, 1, 4, 5 (numeric/mixed at 1 and 128 MiB, every default engine plus
`typed-build`, and the shard-0 compiled read controls), three fresh forks per binary,
interleaved on one pinned CPU per shard (AMD EPYC workers). ns/byte, median of fork medians
[fork range]. Full table: `pairs/paired.md`.

| Fixture | Workload | Engine | Before | After | Ratio | Ranges | GB/s | B/row |
|---|---|---|---:|---:|---:|---|---:|---:|
| numeric 1 MiB | typed | plan-native | 2.4306 [2.4214-2.5274] | 2.4142 [2.4084-2.4637] | 0.9933 | overlap | 0.4114 → 0.4142 | 88.57 → 88.57 |
| numeric 1 MiB | typed-build | plan-native | 1.9697 [1.9642-1.9811] | 1.9443 [1.9400-1.9557] | 0.9871 | separate | 0.5077 → 0.5143 | 64.57 → 64.57 |
| numeric 1 MiB | typed | plan (Java SWAR) | 2.8526 [2.8429-2.9589] | 2.8370 [2.8202-2.8572] | 0.9945 | overlap | 0.3506 → 0.3525 | 88.51 → 88.51 |
| mixed 1 MiB | typed | plan-native | 1.7612 [1.7572-1.8107] | 1.7636 [1.7385-1.7740] | 1.0014 | overlap | 0.5678 → 0.5670 | 448.40 → 448.40 |
| mixed 1 MiB | typed-build | plan-native | 1.5690 [1.5604-1.6033] | 1.5872 [1.5612-1.5876] | 1.0116 | overlap | 0.6374 → 0.6300 | 424.40 → 424.40 |
| numeric 128 MiB | typed | plan-native | 2.7731 [2.7219-4.7047] | 2.8699 [2.6957-2.9260] | 1.0349 | overlap | 0.3606 → 0.3484 | 147.95 → 147.95 |
| mixed 128 MiB | typed | plan-native | 1.9374 [1.9307-1.9882] | 1.9984 [1.9115-2.3642] | 1.0315 | overlap | 0.5162 → 0.5004 | 449.11 → 449.11 |

Readback (typed − typed-build medians, ns/B; share of the typed cell):

| Fixture | Engine | Before | After |
|---|---|---:|---:|
| numeric 1 MiB | plan-native | 0.4609 (19.0%) | 0.4699 (19.5%) |
| numeric 1 MiB | plan | 0.4411 (15.5%) | 0.4387 (15.5%) |
| mixed 1 MiB | plan-native | 0.1922 (10.9%) | 0.1764 (10.0%) |
| numeric 128 MiB | plan-native | 0.5805 (20.9%) | 0.6486 (22.6%) |
| mixed 128 MiB | plan-native | 0.2214 (11.4%) | 0.2868 (14.4%) |

Controls on the same shards: compiled reads read-aot 1.019, interpreter 1.020, peak 1.000
(all overlapping; 0/2/1 B/row); FastCSV, Jackson and kernel-native 0.97–1.04, all
overlapping. Every cell's bytes/row is identical. The one separate cell, numeric 1 MiB
`typed-build` native (0.987×), runs construction code this change does not touch, and its
callgrind count is identical to the instruction. It is shard drift or image layout and is not
claimed.

**Verdict: killed.** The out-of-line call was ~12 Ir of ~70 per cell in the harness loop; with
it gone, the readback share is unchanged within noise. The remaining readback is the per-row
`CsvBatch.row` call (`checkLive`, `checkIndex`, and the 24 B `CsvRow` allocation: typed
88.57 vs typed-build 64.57 B/row) and the loop's own per-cell kind switch and checks. Those are
not getter-call costs.

## Not built

- `@BytecodeInterpreterSwitch` on the getters. A host-inlining root inlines into itself, not
  into callers, so the per-cell call would remain and the getter would grow by `longSlow`. This
  is a reasoned negative from the `stringAt` precedent (WS10(d): 102 → 1,586 instructions, still
  a call from `getString`'s callers), not a measurement.
- Making the getters trivial. The check count is fixed by analysis nullness (every instance
  field load is nullable) and the two bounds checks. Even a leaf body cannot fit 40 nodes with
  six explicit exception edges.

## Correctness

Same bodies, same slow paths, same exceptions. Gates on the candidate tree `ef16275` (invocation
`7f4f634b`, `--nocache_test_results`): `//csv/tests:jvm`, `:jvm_indexed`, `:native_test` (typed
and row iteration 1.6e-5 B/read, export 0.0064 B/row), `//csv/native:index_replay`,
`//benchmarks/comparison:csv_native_smoke` (166 cells agree; `CsvReads[truffledata-plan]`
439/383 nodes, 1 loop, 0 invokes, 0 allocations), `:csv_aot_symbols` and `//tools/bench:test`
all pass. The change is reverted (`ac285f6`), so the production source is `cd3c193`'s.

## Reproduction

```sh
bzl build //benchmarks/comparison:csv_svm //benchmarks/comparison:csv_profile
# preserve bazel-bin/benchmarks/comparison/csv-comparison{,-profile} under build/<dir>/csv/
bzl test //benchmarks/comparison:csv_pair \
  --//benchmarks/comparison:csv_svm_baseline=//build/fu-before-cd3c193/csv:csv-comparison \
  --test_env=CSV_PAIR_SHARDS=0,1,4,5 --nocache_test_results
python3 tools/bench/aot_symbols.py IMAGE --pattern '^CsvRow_get(Long|Double|Boolean|String)$' \
  --pattern '^CsvComparison_lambda\$base\$9$'
docker run --rm --platform linux/amd64 -v $PWD/build/fu-before-cd3c193/csv:/b:ro -v $PWD/out:/out \
  td-valgrind-trixie valgrind --tool=callgrind --toggle-collect='*profileRegion*' \
  --compress-strings=no --compress-pos=no --dump-instr=yes --callgrind-out-file=/out/cg.typed \
  /b/csv-comparison-profile --profile typed truffledata-plan-native numeric 1048576 2
python3 tools/bench/callgrind_split.py out/cg.typed --top 20
```
