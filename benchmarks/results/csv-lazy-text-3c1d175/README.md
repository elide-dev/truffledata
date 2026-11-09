# CSV lazy `CsvText` cells (wave 4, main work)

Plan: `docs/superpowers/plans/2026-11-08-beat-stock-svm.md` §4 WS10 "Open ruling" (a lazy
`CsvText`/`TruffleString` cell as an API decision) and the Rust `StringRecord`-class reuse
argument. Ruling (Sam, 2026-11-09): approved as an API addition; `String` cells remain. Ledger:
"CSV wave 4 (CsvText, CsvBatch.row)". API and semantics: `docs/CSV.md` "Text cells". SVM /
Native Image is the admission target; JVM timing is not recorded.

## Summary

- **What landed (`6d5f938`, `050bccd`):** `CsvText` is a retained lazy STRING cell bound to its
  batch. `CsvRow.getText(c)` allocates one text; `getText(c, into)` rebinds the caller's text
  (zero allocation, zero copy for unescaped heap cells). Escaped and mapped cells fetch one
  decoded copy on the first byte-level read; `toString()` materializes as `getString` does and
  is cached per binding; `charLength()` counts UTF-16 units from the bytes; `toTruffleString()`
  is one UTF-8 copy. Every read checks the batch's lifetime (a closed mapping fails every later
  read), equality and hashing are by content, and the cell is an interop string. Opt-in
  `CsvPlan.withTextCells(true)` returns `CsvText` from DataLibrary/interop reads.
- **Cells.** `strings/truffledata-text(-native)`: `Csv.parse` plus one reusable text walking
  every cell with `charLength()` (the workload checksum is the UTF-16 length, so this is the
  minimum read that agrees with every other engine; no `String`). `truffledata-text-string(-native)`:
  the same walk calling `toString()` through the text (must match `getString` within noise).
  New cells have no baseline binary, so they are compared with the copied-strings cell
  (`truffledata-batch(-native)`) of the same shard, run and binary.
- **Result (final image `3c1d175`, invocation `07be4722`):** the lazy cell beats copied strings
  on every fixture — native 0.75× / 0.76× / 0.67× / 0.65× (numeric 1 MiB, mixed 1 MiB,
  numeric 128 MiB, mixed 128 MiB), disjoint fork ranges on three of four (the fourth has one
  7.4 ns/B fork); Java-SWAR 0.96× / 0.77× / 0.65× / 0.75×, disjoint on the two 128 MiB
  fixtures — at **148 vs 404 B/row (numeric) and 464 vs 1,363 B/row (mixed)**: the 256 and
  899 B/row of per-cell `String`s are gone, what remains is the batch (input copy + spans) and
  the 24 B row view. The materializing path is within noise of `getString`: native 0.93× /
  0.94× / 1.07× / 0.93×, Java 0.86× / 1.08× / 1.00× / 1.01×, every range overlapping.
- **The first image regressed the materializing path** (1.11–1.35× vs `getString`): `textAt`
  → `bind` and `toString` → `live` were out-of-line calls per cell that `stringAt` (a
  host-inlining root) does not pay. `050bccd` makes `textAt`, `toString` and `charLength`
  `@BytecodeInterpreterSwitch` roots; the regression disappears in run 2.

## Source and binaries

Base `5b2e8b7`; before-image `cb60b545…` (invocation `00a23358`, `build/csv-before-5b2e8b7/`).
Run 1 candidate `6d5f938` (`eea2c6fd…`, invocation `46614515`) still carried the row
force-inline probe (`csv-row-inline-899ee9c/`), which is why its B/row figures are 24 lower
than run 2's. Run 2 candidate `3c1d175` (CsvText + roots, probe reverted; `c58215b3…`,
profile `ebb1560a…`, invocation `07be4722`). Hashes in `images.sha256`. Pinned
`25.5.5-elide.27` CE SDK, Linux x86-64, `-O2`, `-march=compatibility`, serial GC, no PGO.

## AOT shape (`aot/`, `csv_profile`, x86-64, llvm-objdump)

| Symbol | Run 1 (`6d5f938`) | Run 2 (`3c1d175`) |
|---|---|---|
| `CsvBatch_textAt` | 74 / 7 calls (calls `bind`) | 242 / 10 (`bind` inlined) |
| `CsvText_bind` | 124 / 0 | inlined (no symbol) |
| `CsvText_toString` | 135 / 4 (calls `live`, `Utf8.string`) | 1,666 / 74 (root; cf. `stringAt` 1,586 / 74) |
| `CsvText_charLength` | 179 / 9 (calls `live`, `Utf8.ascii`) | 270 / 15 |
| `CsvText_live` | 116 / 5 | 82 / 4 (still a symbol for the other readers) |
| lazy loop `lambda$base$5` | 221 / 11 | 166 / 8 |
| materializing loop `lambda$base$6` | 235 / 12 | 171 / 9 |
| copied-strings loop `lambda$base$4` | 233 / 14 (probe) | 173 / 11 |

## Paired SVM (`pairs/`)

`csv_pair`, three fresh forks per binary, interleaved on one pinned CPU per shard (AMD EPYC RBE
workers), shards 0, 1, 4, 5. ns/byte, median of fork medians [fork range]; B/row is Java
allocation per row. Both runs were on loaded workers (run 2 numeric 1 MiB: FastCSV 9.1 ns/B
against its usual 3.4–4.5), so only same-shard ratios are read; absolute GB/s is not claimed.

### Run 2, final (`run2-final/`, invocation `07be4722`)

| Fixture | Engine | Candidate | Copied strings (same shard) | Ratio | Ranges | B/row text / copied |
|---|---|---:|---:|---:|---|---:|
| numeric 1 MiB | text-native (lazy) | 5.5655 [4.6533-6.9018] | 7.4141 [7.0784-8.1754] | 0.751 | separate | 148.36 / 404.36 |
| numeric 1 MiB | text-string-native | 6.9051 [6.1491-9.2348] | 7.4141 [7.0784-8.1754] | 0.931 | overlap | 404.36 / 404.36 |
| numeric 1 MiB | text (lazy, Java) | 7.2277 [7.0524-7.5241] | 7.5650 [6.0736-7.8054] | 0.955 | overlap | 148.30 / 404.30 |
| numeric 1 MiB | text-string (Java) | 6.5409 [6.5393-8.4573] | 7.5650 [6.0736-7.8054] | 0.865 | overlap | 404.30 / 404.30 |
| mixed 1 MiB | text-native (lazy) | 3.2779 [2.6959-3.3101] | 4.2917 [3.6308-4.3753] | 0.764 | separate | 464.33 / 1362.87 |
| mixed 1 MiB | text-string-native | 4.0422 [3.7851-4.2174] | 4.2917 [3.6308-4.3753] | 0.942 | overlap | 1362.88 / 1362.87 |
| mixed 1 MiB | text (lazy, Java) | 3.9298 [3.3745-5.3205] | 5.1143 [3.9515-5.8492] | 0.768 | overlap | 464.06 / 1362.60 |
| mixed 1 MiB | text-string (Java) | 5.5430 [5.1122-16.0210] | 5.1143 [3.9515-5.8492] | 1.084 | overlap | 1362.61 / 1362.60 |
| numeric 128 MiB | text-native (lazy) | 3.8466 [3.2102-7.4160] | 5.7454 [4.5759-6.4899] | 0.670 | overlap (one fork) | 217.08 / 473.08 |
| numeric 128 MiB | text-string-native | 6.1242 [5.9116-7.0628] | 5.7454 [4.5759-6.4899] | 1.066 | overlap | 473.08 / 473.08 |
| numeric 128 MiB | text (lazy, Java) | 3.8144 [3.4178-4.2319] | 5.8588 [5.4507-7.1214] | 0.651 | separate | 217.02 / 473.02 |
| numeric 128 MiB | text-string (Java) | 5.8823 [5.6667-6.9201] | 5.8588 [5.4507-7.1214] | 1.004 | overlap | 473.02 / 473.02 |
| mixed 128 MiB | text-native (lazy) | 3.2923 [2.9811-4.0675] | 5.0739 [4.9714-5.7987] | 0.649 | separate | 464.32 / 1362.40 |
| mixed 128 MiB | text-string-native | 4.7322 [4.3368-5.1953] | 5.0739 [4.9714-5.7987] | 0.933 | overlap | 1362.40 / 1362.40 |
| mixed 128 MiB | text (lazy, Java) | 4.3255 [3.4852-4.8213] | 5.7915 [5.5132-6.4961] | 0.747 | separate | 464.07 / 1362.14 |
| mixed 128 MiB | text-string (Java) | 5.8448 [5.2564-5.9311] | 5.7915 [5.5132-6.4961] | 1.009 | overlap | 1362.14 / 1362.14 |

Paired (before → after) controls in the same run, all overlapping: copied strings native 0.96 /
1.06 / 0.94 / 1.04, owned native 1.09 / 0.96 / 1.10 / 1.08, FastCSV 1.11 / 1.27 / 0.92 / 1.03,
`kernel-native` 1.14 / 0.94 / 0.78 / 0.97; every paired cell's B/row identical (the candidate
changes no existing path's allocation). Typed cells were not re-paired on this image (the
strings-only run); the final tree's typed code is `5b2e8b7`'s.

### Run 1, first image (`run1-with-probe/`, invocation `d3a6708f`)

Lazy native 0.89× / 0.63× / 0.68× / 0.86× (separate on all four), lazy Java 0.67× / 0.98× /
0.74× / 0.84×; materializing native **1.35× / 1.11× / 1.20× / 1.27×** (one separate), Java
1.09× / 1.18× / 1.25× / 1.19× — the regression the roots fix removes. B/row: lazy 124.4 /
440.3 / 193.1 / 440.3 (24 lower than run 2 because the image carried the row probe).

## Standing

Against FastCSV strings in the same shards (run 2, native): lazy text 0.61× / 0.62× / 0.90× /
0.78× of FastCSV's time (FastCSV 9.11, 5.31, 4.28, 4.24 ns/B were themselves inflated by the
load); copied strings 0.81× / 0.81× / 1.34× / 1.20×. The plan's "copied strings ≥ 0.35 GB/s"
hypothesis is not re-measured here (loaded workers); the lazy cell's gain is a same-shard
ratio of 0.65–0.76× (native) over copied strings and 256–899 B/row less allocation.

## Not built

- A `TruffleString` cell (`toTruffleString()` per cell): one copy per cell, measured only for
  correctness.
- An SVM allocation gate for the reusable `getText` walk in `SvmAcceptance` (the JVM test
  `reusedTextReadsDoNotAllocatePerRow` bounds it at < 32 B/row, i.e. the row views).
- Widening core's STRING contract so `CsvText` passes strict `Values.validate` (documented as
  Deferred; the option is interop-only).

## Correctness

`CsvTextTest` (content equal to `getString` on heap, owned, Commons-escape, mapped and
snapshot batches; reuse; `ContractViolation` leaves the text unchanged; unbound and
closed-mapping reads throw; equality/hash by content; `charLength` over 1–4-byte sequences;
the `withTextCells` option through DataLibrary and polyglot; a 10,000-row reusable walk
allocates < 32 B/row). Gates: see the ledger entry for the final invocation ids.

## Reproduction

```sh
bzl build //benchmarks/comparison:csv_svm //benchmarks/comparison:csv_profile   # at 3c1d175
bzl test //benchmarks/comparison:csv_pair \
  --//benchmarks/comparison:csv_svm_baseline=//build/csv-before-5b2e8b7:csv-comparison \
  --test_env=CSV_PAIR_SHARDS=0,1,4,5 --test_env=CSV_PAIR_ENGINES=strings --nocache_test_results
python3 tools/bench/aot_symbols.py IMAGE --pattern '^CsvBatch_textAt$' --pattern '^CsvText_' \
  --pattern '^CsvComparison_lambda\$base\$(4|5|6)$'
```
