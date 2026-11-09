# CSV monomorphic writers and plain framing (WS10)

Plan: `docs/superpowers/plans/2026-11-08-beat-stock-svm.md` P0.5, P0.6 and WS10; rulings in
`2026-11-08-beat-stock-svm-ledger.md` under "WS10 (CSV worker)". SVM / Native Image is the
admission target; JVM timing is not recorded here and gates nothing.

## Source and binaries

- Base: `perf/beat-stock-svm` at `e650d87` (`csv/` unchanged from `main` `2e0df58`).
- Harness (benchmark-only) `7cd2584`: a `typed-build` workload (the `typed` cell's
  `CsvPlan.parse` without the readback, so construction and readback are reported separately),
  a `--profile` mode for callgrind (fixed untimed iterations inside
  `CsvComparison.profileRegion`), symbol-bearing `csv_profile` / `csv_profile_debug` images
  (`-H:-DeleteLocalSymbols -H:+PreserveFramePointer`, the latter `-g`) and the manual
  `csv_aot_symbols` gate. `csv_pair` runs `typed-build` for numeric/mixed.
- Before-image: production `e650d87` + harness `7cd2584`, RBE invocation `b4759f66`, preserved
  in ignored `build/csv-writers-before/` with a SHA-256 manifest. `csv-comparison`
  `9e8c94472643f83151b5c2f97fd37479712f88a3dee9563ce06129abe63af7c9`; `csv-comparison-profile`
  `02fbd31df6374f87fd89ddd39f9f541f5bac27ea4bf9f34123727fb4c9527d70`;
  `csv-comparison-profile-debug` `df13652ca9924ad76a2febea6c3c6d022d0a774b9c0c9913afb0d2df0794bdc0`.
- All images: pinned `25.5.5-elide.27` SDK (Java `25.0.4.1.1+1`, GraalVM CE `25.5.5-dev+1.1`),
  Linux x86-64, `-O2`, `-march=compatibility`, serial GC, no PGO.
- Pairs: `csv_pair` interleaves three fresh forks of each binary on one pinned CPU per shard of
  one AMD EPYC RBE worker; medians of fork medians, ranges are fork-median minima/maxima. The
  native kernel selects AVX-512 there. Only x86-64 SVM claims are made.

## Final qualification: `afa4aa5` against the before-image

Admitted stack, rebased onto `641aa45`: plain framing with staged records for typed (`299fa5b`)
and all-STRING (`6098cf1`) plans, per-column passes with inlined parsers (`43c4073`), lent
staging chunks (`66add6f`), the popcount-free decimal dot (`c352096`), `getString` as one
host-inlining root (`59e87de`) and removal of the unused loops/writers (`afa4aa5`). The rebase
changes no source in the `csv_svm` image closure (bazel query of its source files against the
58 paths `e650d87..641aa45` touches: none), so the preserved before-image is the baseline.

Full 8-shard paired run, default engines plus `typed-build` (invocation `ab5c9c3b`, 704 s,
candidate `f316042690bf3f74a2ceada2c2fa598548dff4a26d0454bed6a5338a590b8de6`, baseline
`9e8c9447…`). All checksums, input hashes and row counts agree between binaries and engines
(the runner rejects any mismatch). No forks were dropped. Raw records: `pairs/final/`.

### Library cells (1 MiB and 128 MiB; ns/byte median of fork medians [fork range], GB/s, B/row)

| Fixture | Workload | Engine | Stage | Baseline median [range] | Candidate median [range] | Ratio | GB/s base -> cand | B/row base -> cand |
|---|---|---|---|---:|---:|---:|---:|---:|
| numeric 1 MiB | strings | truffledata-batch | ingest | 4.7522 [4.6805-4.7714] | 4.0146 [4.0137-4.0836] | 0.8448 | 0.2104 -> 0.2491 | 404.30 -> 404.30 |
| numeric 1 MiB | strings | truffledata-batch-native | ingest | 4.3187 [4.3068-4.3248] | 3.5982 [3.5917-3.6159] | 0.8332 | 0.2316 -> 0.2779 | 404.36 -> 404.36 |
| numeric 1 MiB | strings | truffledata-batch-owned | ingest | 4.2667 [4.2591-6.7024] | 3.5924 [3.5557-4.1929] | 0.8420 | 0.2344 -> 0.2784 | 352.49 -> 352.49 |
| numeric 1 MiB | strings | truffledata-batch-owned-native | ingest | 3.8375 [3.8321-3.8815] | 3.1485 [3.1309-3.1541] | 0.8205 | 0.2606 -> 0.3176 | 352.55 -> 352.55 |
| numeric 1 MiB | typed | truffledata-plan | ingest | 3.5229 [3.5155-3.5665] | 3.0659 [3.0651-3.0806] | 0.8703 | 0.2839 -> 0.3262 | 88.52 -> 88.51 |
| numeric 1 MiB | typed | truffledata-plan-native | ingest | 3.0935 [3.0864-3.1044] | 2.6291 [2.6252-2.6677] | 0.8499 | 0.3233 -> 0.3804 | 88.57 -> 88.57 |
| numeric 1 MiB | typed-build | truffledata-plan | ingest | 3.0457 [3.0447-3.0556] | 2.6166 [2.6039-2.6200] | 0.8591 | 0.3283 -> 0.3822 | 64.52 -> 64.51 |
| numeric 1 MiB | typed-build | truffledata-plan-native | ingest | 2.6472 [2.6407-2.7850] | 2.1773 [2.1725-2.1856] | 0.8225 | 0.3778 -> 0.4593 | 64.57 -> 64.57 |
| numeric 1 MiB | typed-reads | truffledata-plan | interpret | 72.3399 [72.1747-72.3998] | 71.7728 [71.7042-72.9894] | 0.9922 |  | 2.00 -> 2.00 |
| numeric 1 MiB | typed-reads | truffledata-plan | peak | 6.5600 [6.5537-6.6228] | 6.5743 [6.5511-6.5869] | 1.0022 |  | 1.00 -> 1.00 |
| numeric 1 MiB | typed-reads | truffledata-plan | read-aot | 71.1843 [70.6662-71.2613] | 70.8958 [70.8411-72.1563] | 0.9959 |  | 0.00 -> 0.00 |
| mixed 1 MiB | strings | truffledata-batch | ingest | 4.1933 [4.1639-4.4398] | 3.6736 [3.6627-3.6867] | 0.8761 | 0.2385 -> 0.2722 | 1362.63 -> 1362.60 |
| mixed 1 MiB | strings | truffledata-batch-native | ingest | 3.6539 [3.5733-3.6596] | 3.0808 [3.0601-3.1124] | 0.8431 | 0.2737 -> 0.3246 | 1362.90 -> 1362.87 |
| mixed 1 MiB | strings | truffledata-batch-owned | ingest | 3.7677 [3.6639-3.8281] | 3.2192 [3.1588-3.2224] | 0.8544 | 0.2654 -> 0.3106 | 1132.94 -> 1132.91 |
| mixed 1 MiB | strings | truffledata-batch-owned-native | ingest | 3.2111 [3.1572-3.2286] | 2.6546 [2.6409-2.6834] | 0.8267 | 0.3114 -> 0.3767 | 1133.21 -> 1133.18 |
| mixed 1 MiB | typed | truffledata-plan | ingest | 2.7306 [2.6805-2.9458] | 2.4302 [2.4096-2.4476] | 0.8900 | 0.3662 -> 0.4115 | 448.16 -> 448.14 |
| mixed 1 MiB | typed | truffledata-plan-native | ingest | 2.0658 [2.0602-2.0834] | 1.8288 [1.8243-1.8539] | 0.8853 | 0.4841 -> 0.5468 | 448.43 -> 448.40 |
| mixed 1 MiB | typed-build | truffledata-plan | ingest | 2.4834 [2.4684-2.5041] | 2.2205 [2.1920-2.2352] | 0.8941 | 0.4027 -> 0.4504 | 424.16 -> 424.14 |
| mixed 1 MiB | typed-build | truffledata-plan-native | ingest | 1.8970 [1.8825-1.9308] | 1.6491 [1.6463-1.6648] | 0.8693 | 0.5271 -> 0.6064 | 424.43 -> 424.40 |
| quoted 1 MiB | strings | truffledata-batch | ingest | 1.5955 [1.5822-1.5957] | 1.5544 [1.5258-1.5643] | 0.9743 | 0.6268 -> 0.6433 | 1496.28 -> 1496.26 |
| quoted 1 MiB | strings | truffledata-batch-native | ingest | 1.1781 [1.1731-1.1851] | 1.1363 [1.1331-1.1620] | 0.9646 | 0.8488 -> 0.8800 | 1497.05 -> 1497.03 |
| quoted 1 MiB | strings | truffledata-batch-owned | ingest | 1.1346 [1.1219-1.1364] | 1.1005 [1.0897-1.1096] | 0.9699 | 0.8814 -> 0.9087 | 830.86 -> 830.84 |
| quoted 1 MiB | strings | truffledata-batch-owned-native | ingest | 0.7353 [0.7188-0.7400] | 0.6953 [0.6880-0.6968] | 0.9456 | 1.3600 -> 1.4382 | 831.62 -> 831.60 |
| escaped 1 MiB | strings | truffledata-batch | ingest | 2.7351 [2.6682-2.7561] | 2.6595 [2.6401-2.6955] | 0.9724 | 0.3656 -> 0.3760 | 594.20 -> 594.19 |
| escaped 1 MiB | strings | truffledata-batch-native | ingest | 2.3680 [2.3604-2.4786] | 2.3665 [2.3452-2.3706] | 0.9994 | 0.4223 -> 0.4226 | 594.46 -> 594.46 |
| escaped 1 MiB | strings | truffledata-batch-owned | ingest | 2.3223 [2.3180-2.3720] | 2.2956 [2.2782-9.5770] | 0.9885 | 0.4306 -> 0.4356 | 362.65 -> 362.65 |
| escaped 1 MiB | strings | truffledata-batch-owned-native | ingest | 2.0764 [2.0741-2.0783] | 1.9813 [1.9711-2.0095] | 0.9542 | 0.4816 -> 0.5047 | 362.92 -> 362.91 |
| numeric 128 MiB | strings | truffledata-batch | ingest | 5.1009 [5.0708-5.1740] | 4.4898 [4.4878-4.5187] | 0.8802 | 0.1960 -> 0.2227 | 473.02 -> 473.02 |
| numeric 128 MiB | strings | truffledata-batch-native | ingest | 4.8645 [4.8212-14.5485] | 4.1097 [4.0337-10.4316] | 0.8448 | 0.2056 -> 0.2433 | 473.08 -> 473.08 |
| numeric 128 MiB | strings | truffledata-batch-owned | ingest | 4.7067 [4.6785-4.7664] | 3.9812 [3.9146-4.0342] | 0.8459 | 0.2125 -> 0.2512 | 419.11 -> 419.11 |
| numeric 128 MiB | strings | truffledata-batch-owned-native | ingest | 4.3262 [4.3115-4.3335] | 3.5621 [3.5393-3.5624] | 0.8234 | 0.2311 -> 0.2807 | 419.17 -> 419.17 |
| numeric 128 MiB | typed | truffledata-plan | ingest | 3.6791 [3.6764-7.3262] | 3.2478 [3.2372-5.1393] | 0.8828 | 0.2718 -> 0.3079 | 147.89 -> 147.89 |
| numeric 128 MiB | typed | truffledata-plan-native | ingest | 3.3174 [3.3004-5.6064] | 2.8925 [2.8669-2.9180] | 0.8719 | 0.3014 -> 0.3457 | 147.95 -> 147.95 |
| numeric 128 MiB | typed-build | truffledata-plan | ingest | 3.2727 [3.1779-3.2785] | 2.7679 [2.7377-2.8056] | 0.8458 | 0.3056 -> 0.3613 | 123.89 -> 123.89 |
| numeric 128 MiB | typed-build | truffledata-plan-native | ingest | 2.8701 [2.8220-2.9493] | 2.3727 [2.3557-2.3821] | 0.8267 | 0.3484 -> 0.4215 | 123.95 -> 123.95 |
| mixed 128 MiB | strings | truffledata-batch | ingest | 4.7129 [4.5798-4.7359] | 4.1856 [4.1548-4.2035] | 0.8881 | 0.2122 -> 0.2389 | 1362.14 -> 1362.14 |
| mixed 128 MiB | strings | truffledata-batch-native | ingest | 4.2592 [4.2180-4.3296] | 3.7837 [3.5777-3.8339] | 0.8884 | 0.2348 -> 0.2643 | 1362.40 -> 1362.40 |
| mixed 128 MiB | strings | truffledata-batch-owned | ingest | 4.4783 [4.3397-4.5665] | 3.7554 [3.7405-3.8069] | 0.8386 | 0.2233 -> 0.2663 | 1130.68 -> 1130.68 |
| mixed 128 MiB | strings | truffledata-batch-owned-native | ingest | 3.7498 [3.6267-3.7594] | 3.1057 [3.0226-3.8639] | 0.8282 | 0.2667 -> 0.3220 | 1130.94 -> 1130.94 |
| mixed 128 MiB | typed | truffledata-plan | ingest | 2.8148 [2.8097-2.8219] | 2.5100 [2.5065-2.5525] | 0.8917 | 0.3553 -> 0.3984 | 448.85 -> 448.85 |
| mixed 128 MiB | typed | truffledata-plan-native | ingest | 2.3041 [2.3028-2.3081] | 1.9999 [1.9980-2.0087] | 0.8680 | 0.4340 -> 0.5000 | 449.11 -> 449.11 |
| mixed 128 MiB | typed-build | truffledata-plan | ingest | 2.6399 [2.6364-2.6892] | 2.3390 [2.3337-2.3557] | 0.8860 | 0.3788 -> 0.4275 | 424.85 -> 424.85 |
| mixed 128 MiB | typed-build | truffledata-plan-native | ingest | 2.1268 [2.1167-2.1475] | 1.8133 [1.8086-1.8245] | 0.8526 | 0.4702 -> 0.5515 | 425.11 -> 425.11 |
| quoted 128 MiB | strings | truffledata-batch | ingest | 1.5632 [1.5562-1.5822] | 1.5347 [1.5130-1.8159] | 0.9818 | 0.6397 -> 0.6516 | 1494.50 -> 1494.50 |
| quoted 128 MiB | strings | truffledata-batch-native | ingest | 1.3078 [1.3039-1.3429] | 1.1853 [1.1787-1.6095] | 0.9063 | 0.7647 -> 0.8437 | 1495.24 -> 1495.24 |
| quoted 128 MiB | strings | truffledata-batch-owned | ingest | 1.1290 [1.1186-1.1474] | 1.0904 [1.0831-1.0918] | 0.9658 | 0.8857 -> 0.9171 | 827.55 -> 827.55 |
| quoted 128 MiB | strings | truffledata-batch-owned-native | ingest | 0.7636 [0.7594-0.7806] | 0.7328 [0.7291-0.7368] | 0.9596 | 1.3096 -> 1.3647 | 828.29 -> 828.29 |
| escaped 128 MiB | strings | truffledata-batch | ingest | 3.3209 [3.3069-11.1816] | 3.2987 [3.2868-3.5000] | 0.9933 | 0.3011 -> 0.3032 | 593.19 -> 593.19 |
| escaped 128 MiB | strings | truffledata-batch-native | ingest | 2.9739 [2.9524-2.9739] | 2.9205 [2.9062-2.9291] | 0.9821 | 0.3363 -> 0.3424 | 593.44 -> 593.44 |
| escaped 128 MiB | strings | truffledata-batch-owned | ingest | 2.8736 [2.8699-2.8930] | 2.8238 [2.8222-2.8452] | 0.9827 | 0.3480 -> 0.3541 | 360.80 -> 360.80 |
| escaped 128 MiB | strings | truffledata-batch-owned-native | ingest | 2.5563 [2.5303-2.5648] | 2.4853 [2.4703-2.4875] | 0.9722 | 0.3912 -> 0.4024 | 361.05 -> 361.05 |

### Aggregates (unweighted geometric mean of candidate/baseline time over fixture/size cells)

| Workload | Engine | Cells | Geometric mean | Per-cell range |
|---|---|---:|---:|---:|
| strings | fastcsv | 8 | 1.0011 | 0.9826-1.0146 |
| strings | jackson | 8 | 0.9969 | 0.9785-1.0159 |
| strings | kernel-native | 8 | 0.9956 | 0.9764-1.0305 |
| strings | truffledata-batch | 8 | 0.9247 | 0.8448-0.9933 |
| strings | truffledata-batch-native | 8 | 0.9056 | 0.8332-0.9994 |
| strings | truffledata-batch-owned | 8 | 0.9086 | 0.8386-0.9885 |
| strings | truffledata-batch-owned-native | 8 | 0.8888 | 0.8205-0.9722 |
| typed | fastcsv | 4 | 0.9770 | 0.9663-0.9965 |
| typed | kernel-native | 4 | 0.9674 | 0.9568-0.9724 |
| typed | truffledata-plan | 4 | 0.8836 | 0.8703-0.8917 |
| typed | truffledata-plan-native | 4 | 0.8687 | 0.8499-0.8853 |
| typed-build | truffledata-plan | 4 | 0.8710 | 0.8458-0.8941 |
| typed-build | truffledata-plan-native | 4 | 0.8425 | 0.8225-0.8693 |

The unchanged Java competitors stay flat (FastCSV strings 1.001, Jackson 0.997, FastCSV typed
0.977). `kernel-native` typed improves 3.3% because it shares `Numbers` with the library (the
dot fix); its strings path, which does not, is 0.996. Read controls are unchanged: read-aot
0.996, interpreter 0.992, peak 1.002 (ranges overlap), 0/2/1 B per row. Bytes/row are equal within
0.03 B/row in every cell (staging chunks are lent per thread, not allocated per batch).

### Standing on this run (1 MiB, native index, same shard)

| Cell | Before GB/s | After GB/s | vs FastCSV after | vs kernel-native after (own bound) |
|---|---:|---:|---:|---:|
| typed mixed | 0.4841 | 0.5468 | 3.15× | 0.655× (was 0.606×) |
| typed numeric | 0.3233 | 0.3804 | 3.54× | 0.816× (was 0.714×) |
| copied strings mixed | 0.2737 | 0.3246 | 1.03× (was 0.86×) | 0.738× |
| copied strings numeric | 0.2316 | 0.2779 | 0.93× (was 0.77×) | 0.629× |
| owned strings mixed | 0.3114 | 0.3767 | 1.19× | 0.856× |
| owned strings numeric | 0.2606 | 0.3176 | 1.06× | 0.719× |

Plan hypotheses (HYPOTHESIS → MEASURED): typed mixed 0.477 → ≥0.7 GB/s is **not reached**
(0.547 GB/s on this host, whose before-image measures 0.484); copied strings ≥0.35 GB/s is
**not reached** (0.325; owned input 0.377); numeric typed vs Rust `csv` ≥1.0× is **not
measured** here (`csv_pair` has no Rust probe; the last Rust ratio was 0.61–0.66×, and the
kernel-native ratio moved 0.714× → 0.816×). The WS10 kill rule (<10% typed on paired SVM) is
**not triggered**: typed native improves 15.0% (numeric) and 11.5% (mixed) at 1 MiB, 12.8% and
13.2% at 128 MiB, every range separated.

## P0.5 instruction attribution (before image)

Callgrind 3.24 (Debian trixie, `--platform linux/amd64` container on the Apple M5 Max host)
over the symbol- and line-bearing `csv_profile_debug` image built from production `e650d87`
with harness `7cd2584` (invocation `b4759f66`). `--profile` runs three untimed warm-ups, then
collection toggles on `CsvComparison.profileRegion` for two iterations over the 1 MiB
fixture; Ir below is per iteration. **Instruction counts, not time.** Under Valgrind the native
index kernel dispatches to AVX2 (`native-avx2-svm-leaf`), not the AVX-512 kernel the RBE
timing hosts select; the kernel row is therefore an upper bound for those hosts. Every
checksum equals the kernel-native reference's. `-g` does not change code shape: the
`frameFused`/`frameSpans`/`framePlain`/`record`/`resize` symbols have identical sizes in the
debug and non-debug profile images.

Inlined code inside `IndexScan.frameFused` cannot be split by debug line tables (Native
Image's -O2 line info is too coarse: lines such as the empty-line branch collect 13% of the
loop). It is split exactly by **execution count** instead: with per-instruction counts, an
instruction executed 4R times (R = records) in the numeric fixture belongs to the four INT64
fields, 3R to the three FLOAT64 fields, 8R to every field, 7R to delimiters, R to the record
end. `callgrind/execution-classes.md` holds the joins (per-instruction Ir from `tools/bench/callgrind_split.py` against `llvm-objdump` of the debug image).

### Typed (`truffledata-plan-native`, `plan.parse` plus a readback of every typed cell)

| Component | numeric Ir | share | mixed Ir | share |
|---|---:|---:|---:|---:|
| Framing + fused `put` dispatch + record commit (frameFused, per-field/delimiter/record classes) | 30.32M | 39.8% | 17.52M | 38.2% |
| FLOAT64 conversion (inlined `Numbers.parseDouble`, 304 / 285 Ir per value) | 18.46M | 24.2% | 7.81M | 17.0% |
| INT64 conversion (inlined `Numbers.parseLong`, 114 / 115 Ir per value) | 9.23M | 12.1% | 2.63M | 5.7% |
| Typed-field common (offset/length/decoder test; mixed only separable) | — | — | 2.08M | 4.5% |
| BOOLEAN conversion | in record class | | 0.24M | 0.5% |
| Column grow (inlined array zeroing on growth) | 1.28M | 1.7% | 0.87M | 1.9% |
| Input copy (`parseBytes`, STRING plans only) | 0 | 0% | 5.24M | 11.4% |
| Native index kernel (AVX2 under Valgrind) | 2.93M | 3.8% | 2.77M | 6.0% |
| UTF-8 prevalidation (simdutf) | 0.18M | 0.2% | 0.34M | 0.7% |
| Allocation slow paths | 0.02M | 0.0% | 0.88M | 1.9% |
| Readback (`CsvRow.get*` calls, `row()`, harness loop) | 12.53M | 16.4% | 5.11M | 11.1% |
| **Total per iteration** | **76.20M (72.7 Ir/B)** | | **45.91M (43.8 Ir/B)** | |
| `typed-build` (construction only) | 63.65M | 83.5% | 40.80M | 88.9% |

Reference, same image: `kernel-native` typed numeric is 58.96M Ir (framing 14.65M, handler
11.40M, conversion 29.95M: `digits` 12.14M, `parseDouble` 6.50M, `dot` 5.95M, `parseLong`
4.23M, `bool` 1.15M; kernel 2.75M); mixed is 31.64M.

### Framing loop shape per field (execution-count classes)

| Loop (numeric / mixed) | Per field | Per delimiter | Per record | Notes |
|---|---:|---:|---:|---|
| `framePlain` (scan, `CsvScanner`) | 75 / 59 | 26 / 23 | 61 | ~30 spill moves per field |
| `frameSpans` (all-STRING, admitted) | 113 / 103 | 31 / 31 | 87 | +38–44 per field over plain |
| `frameFused` (typed) | 139 / 97 | 31 / 31 | 113 | + per-value conversion above |
| `IndexedScanner` (kernel-native reference) | 90 / 89 (incl. delimiter) | | | |

### Strings (`truffledata-batch-native`, copied input; owned omits the copy)

| Component | numeric Ir | share | mixed Ir | share |
|---|---:|---:|---:|---:|
| `frameSpans` (framing + span stores, includes growth zeroing) | 26.56M | 31.9% | 17.47M | 27.7% |
| Input copy (`parseBytes`) | 5.24M | 6.3% | 5.24M | 8.3% |
| `getString`: `stringAt` + `Utf8.string` + `String` constructors + bounds checks | 42.71M | 51.3% | 32.85M | 52.0% |
| Harness loop, `row()`, `checkLive` | 5.14M | 6.2% | 3.12M | 4.9% |
| Native index kernel + UTF-8 validation | 3.11M | 3.7% | 3.11M | 4.9% |
| **Total per iteration (copied / owned)** | **83.21M / 77.91M** | | **63.17M / 57.92M** | |

The kernel-native strings reference builds a `String` per cell through the charset decoder
(numeric 63.31M Ir per iteration: `String(Charset,…)` 21.60M, framing 14.65M, handler 11.11M):
our Latin-1 path builds each String in fewer instructions, but the retained batch adds the
span-only loop's extra per-field cost, the input copy and the `stringAt` indirection.

### Findings

- Conversion is not made expensive by fusion: inlined conversion costs match the out-of-line
  reference per value (INT64 114 vs 112, FLOAT64 304 vs 325 Ir). It is 36% of typed numeric
  and 27% of typed mixed. In `-march=compatibility` images `Numbers.dot` computes two software
  popcounts (about 60 of the 304 Ir per FLOAT64 value) because `Long.bitCount` has no POPCNT.
- The builder-coupled framing loops cost 38–64 Ir more per field than `framePlain`, and
  `framePlain` itself spends about 30 spill moves per field (Graal register pressure with
  ~20 live loop variables).
- Column grow/trim is 2–4%; `CsvRow` typed getters are out-of-line 42-Ir calls in the
  harness loop (about half call overhead), 16% of typed numeric.
- Strings: materialization is about half of the copied-strings cell; framing about 30%.

## P0.6 AOT symbol table (csv_profile, x86-64, -O2, `-march=compatibility`)

`tools/bench/aot_symbols.py` over the symbol-bearing `csv_profile` images (the `csv_svm` flags
plus `-H:-DeleteLocalSymbols -H:+PreserveFramePointer`). Shape only, never timing. Columns are
instructions / call sites / frame bytes / stack loads. Before: `02fbd31d…`; final: `2e1cdfc8…`.

| Symbol | Before | Final | Notes |
|---|---|---|---|
| `IndexScan_frameFused` | 7,750 / 296 / 344 / 1,090 | removed | typed loop, parsers inlined (arm64 history 7,832/295) |
| `IndexScan_frameSpans` | 3,252 / 118 / 328 / 628 | removed | all-STRING loop |
| `IndexScan_framePlain` | 1,133 / 25 / 200 / 300 | 1,133 / 25 / 200 / 300 | now the only framing loop (arm64 history 1,108/20) |
| `IndexScan_scan` | 226 / 12 / 104 / 47 | 232 / 14 / 104 / 43 | lends staging chunks |
| `BatchBuilder_put` / `putSpan` / `commit` | inlined into the loops | removed | |
| `BatchBuilder_record` | 561 / 51 / 104 / 131 | 180 / 15 / 56 / 18 | staged copy; slow path split out |
| `BatchBuilder_recordSlow` | — | 148 / 10 / 56 / 22 | the per-record path |
| `BatchBuilder_convert` | — | 284 / 25 / 88 / 46 | per-chunk field dispatch |
| `BatchBuilder_convertLongs` | — | 691 / 31 / 104 / 36 | host-inlining root, parsers inlined |
| `BatchBuilder_convertDoubles` | — | 1,414 / 38 / 120 / 98 | idem |
| `BatchBuilder_convertBooleans` | — | 257 / 17 / 40 / 8 | idem |
| `BatchBuilder_shiftSpans` | — | 117 / 7 / 24 / 3 | STRING chunk pass |
| `Numbers_dot` | 195 / 4 / 40 / 0 | 153 / 4 / 40 / 0 | no software popcount |
| `Numbers_parseDouble` / `parseLong` | 215 / 10, 349 / 20 | unchanged | |
| `CsvBatch_stringAt` | 102 / 8 / 24 / 0 | 1,586 / 74 / 104 / 152 | host-inlining root; String construction inlined |
| `Utf8_string` | 163 / 8 / 56 / 8 | unchanged (still used by escaped and mapped cells) | |
| `CsvRow_getLong` / `getDouble` / `getBoolean` | 81 / 7, 80 / 7, 81 / 7 | unchanged | out of line in the harness loop (42 Ir per call) |

`benchmarks/comparison/csv_aot_budgets.json` pins the final shapes with ~10% headroom; the
manual remote gate is `//benchmarks/comparison:csv_aot_symbols`. The before-image fails 9 of
its 11 budgets (missing symbols and the old `record`), as intended for a re-pinned gate.

## Probe (a): conversion out of the framing loop (mechanism 3 alone)

`c3d105b`: typed fusable bindings stage spans per record column in 64-row chunks while
framing (`stage`/`commitStaged`, the same loop shape as the admitted all-STRING loop) and
convert each field over the chunk in one monomorphic pass (`convert`). A value the fused
path would decline (grammar, constraint, binary32, complex Commons span) replays the chunk
row by row through the per-record conversion (`fill`, shared with `record`), and staged
rows convert when a chunk fills, before `record` writes a row, at `window`/`trim`, and when
the index scan returns, so no later framing error can overtake a staged conversion error.
Tests: chunk boundaries (rows 0, 63, 64, 65, 135), every conversion fault, two faults in one
record, later malformed quote / width errors, row limits and `maxRows`, constraints and
binary32 beyond a chunk, Commons complex spans in typed fields, stream and mapped readers.

AOT shape (`csv_profile`): `frameFused` 7,750 → 3,178 instructions, 296 → 116 calls; the
passes `convertLongs`/`convertDoubles` are 271/251 instructions and **call** `Numbers`
(not inlined). Callgrind: typed numeric −0.3% Ir, mixed −0.1%: the per-field framing path
fell from 139 to 102 Ir, and the conversion calls added it back.

Paired SVM (shards 0–1, invocation `ff24d051`, candidate `dbd1fc79…`, baseline `9e8c9447…`):

| Fixture | Workload | Engine | Stage | Baseline median [range] | Candidate median [range] | Ratio | GB/s base -> cand | B/row base -> cand |
|---|---|---|---|---:|---:|---:|---:|---:|
| numeric 1 MiB | strings | truffledata-batch-native | ingest | 4.3013 [4.2834-4.3355] | 4.3350 [4.3339-4.3949] | 1.0078 | 0.2325 -> 0.2307 | 404.36 -> 404.36 |
| numeric 1 MiB | typed | kernel-native | ingest | 2.2194 [2.2095-2.2248] | 2.2514 [2.2509-10.2416] | 1.0144 | 0.4506 -> 0.4442 | 0.01 -> 0.01 |
| numeric 1 MiB | typed | truffledata-plan | ingest | 3.5584 [3.5405-3.5802] | 3.6692 [3.6608-3.6811] | 1.0312 | 0.2810 -> 0.2725 | 88.52 -> 88.75 |
| numeric 1 MiB | typed | truffledata-plan-native | ingest | 3.1141 [3.1075-3.1307] | 3.2409 [3.2195-3.2694] | 1.0407 | 0.3211 -> 0.3086 | 88.57 -> 88.81 |
| numeric 1 MiB | typed-build | truffledata-plan | ingest | 3.0698 [3.0682-3.1087] | 3.1918 [3.1864-3.2372] | 1.0397 | 0.3258 -> 0.3133 | 64.52 -> 64.75 |
| numeric 1 MiB | typed-build | truffledata-plan-native | ingest | 2.6444 [2.6309-2.6469] | 2.7735 [2.7698-2.7738] | 1.0488 | 0.3782 -> 0.3606 | 64.57 -> 64.81 |
| numeric 1 MiB | typed-reads | truffledata-plan | interpret | 75.6505 [72.3591-100.0906] | 72.5922 [72.3712-72.6695] | 0.9596 |  | 2.00 -> 2.00 |
| numeric 1 MiB | typed-reads | truffledata-plan | peak | 6.6141 [6.5743-6.6223] | 6.6324 [6.5735-6.6336] | 1.0028 |  | 1.00 -> 1.00 |
| numeric 1 MiB | typed-reads | truffledata-plan | read-aot | 71.5098 [70.7850-72.2779] | 70.9968 [70.4278-71.1451] | 0.9928 |  | 0.00 -> 0.00 |
| mixed 1 MiB | strings | truffledata-batch-native | ingest | 3.8432 [3.6584-4.5435] | 3.8907 [3.5844-4.7769] | 1.0124 | 0.2602 -> 0.2570 | 1362.90 -> 1362.90 |
| mixed 1 MiB | typed | kernel-native | ingest | 1.3569 [1.3337-1.7700] | 1.3922 [1.3421-1.7752] | 1.0261 | 0.7370 -> 0.7183 | 0.03 -> 0.03 |
| mixed 1 MiB | typed | truffledata-plan | ingest | 2.8969 [2.8633-2.9073] | 3.0397 [2.9056-3.7797] | 1.0493 | 0.3452 -> 0.3290 | 448.16 -> 451.04 |
| mixed 1 MiB | typed | truffledata-plan-native | ingest | 2.2854 [2.1779-2.5396] | 2.3125 [2.2509-2.4090] | 1.0119 | 0.4376 -> 0.4324 | 448.43 -> 451.30 |
| mixed 1 MiB | typed-build | truffledata-plan | ingest | 2.6128 [2.5129-2.6504] | 2.7184 [2.5735-2.8745] | 1.0404 | 0.3827 -> 0.3679 | 424.16 -> 427.04 |
| mixed 1 MiB | typed-build | truffledata-plan-native | ingest | 2.0289 [2.0109-2.1501] | 2.1371 [2.1253-2.1729] | 1.0534 | 0.4929 -> 0.4679 | 424.43 -> 427.30 |

Probe (a) is a measured loss: typed numeric native 1.041× time with separate fork ranges,
typed-build 1.049× (numeric) and 1.053× (mixed). The controls (`kernel-native` 1.014×/1.026×,
strings 1.008×/1.012×) overlap their baselines.

### Probe (a'): parsers inlined into the passes

`9c68b4e` makes `convertLongs`/`convertDoubles`/`convertBooleans` host-inlining roots
(`@BytecodeInterpreterSwitch`, as the framing loops are). AOT: `convertDoubles` 1,450
instructions with the parsers inlined. Callgrind (per iteration): typed numeric 76.20M → 70.30M
Ir (−7.7%), typed-build 63.65M → 57.74M (−9.3%), typed mixed 45.91M → 43.31M (−5.7%).
Inlined in a pass, a FLOAT64 value still costs 306 Ir (304 fused): the conversion is the same
code wherever it runs.

Paired SVM (invocation `52d47f5f`, candidate `0c400e22…`):

| Fixture | Workload | Engine | Stage | Baseline median [range] | Candidate median [range] | Ratio | GB/s base -> cand | B/row base -> cand |
|---|---|---|---|---:|---:|---:|---:|---:|
| numeric 1 MiB | strings | truffledata-batch-native | ingest | 4.3497 [4.2873-4.4192] | 4.2389 [4.2377-4.2739] | 0.9745 | 0.2299 -> 0.2359 | 404.36 -> 404.36 |
| numeric 1 MiB | typed | kernel-native | ingest | 2.2120 [2.1988-2.2341] | 2.2129 [2.2053-2.2575] | 1.0004 | 0.4521 -> 0.4519 | 0.01 -> 0.01 |
| numeric 1 MiB | typed | truffledata-plan | ingest | 3.5054 [3.4992-3.5125] | 3.4935 [3.4928-4.4162] | 0.9966 | 0.2853 -> 0.2862 | 88.52 -> 88.75 |
| numeric 1 MiB | typed | truffledata-plan-native | ingest | 3.0933 [3.0854-3.1309] | 3.0729 [3.0512-3.4318] | 0.9934 | 0.3233 -> 0.3254 | 88.57 -> 88.81 |
| numeric 1 MiB | typed-build | truffledata-plan | ingest | 3.0634 [3.0394-3.1671] | 3.0106 [3.0060-3.0277] | 0.9828 | 0.3264 -> 0.3322 | 64.52 -> 64.75 |
| numeric 1 MiB | typed-build | truffledata-plan-native | ingest | 2.6474 [2.6284-2.6478] | 2.5686 [2.5677-2.5973] | 0.9702 | 0.3777 -> 0.3893 | 64.57 -> 64.81 |
| numeric 1 MiB | typed-reads | truffledata-plan | interpret | 72.2224 [72.2089-73.2159] | 72.2292 [71.7502-72.3379] | 1.0001 |  | 2.00 -> 2.00 |
| numeric 1 MiB | typed-reads | truffledata-plan | peak | 6.5922 [6.5303-171.8770] | 6.6022 [6.5322-171.9355] | 1.0015 |  | 1.00 -> 1.00 |
| numeric 1 MiB | typed-reads | truffledata-plan | read-aot | 70.3609 [70.3035-70.4287] | 70.4235 [70.1832-70.4923] | 1.0009 |  | 0.00 -> 0.00 |
| mixed 1 MiB | strings | truffledata-batch-native | ingest | 3.6078 [3.5507-3.6420] | 3.5887 [3.5661-3.5944] | 0.9947 | 0.2772 -> 0.2787 | 1362.90 -> 1362.90 |
| mixed 1 MiB | typed | kernel-native | ingest | 1.2307 [1.2220-1.3160] | 1.2287 [1.2238-1.2610] | 0.9984 | 0.8126 -> 0.8139 | 0.03 -> 0.03 |
| mixed 1 MiB | typed | truffledata-plan | ingest | 2.6796 [2.6780-2.6949] | 2.6352 [2.6263-2.6789] | 0.9835 | 0.3732 -> 0.3795 | 448.16 -> 451.04 |
| mixed 1 MiB | typed | truffledata-plan-native | ingest | 2.0978 [2.0442-2.1154] | 2.0812 [2.0616-2.0941] | 0.9921 | 0.4767 -> 0.4805 | 448.43 -> 451.30 |
| mixed 1 MiB | typed-build | truffledata-plan | ingest | 2.4855 [2.4631-2.4936] | 2.4609 [2.4364-2.4734] | 0.9901 | 0.4023 -> 0.4064 | 424.16 -> 427.04 |
| mixed 1 MiB | typed-build | truffledata-plan-native | ingest | 1.9011 [1.8839-1.9421] | 1.9301 [1.8811-2.4317] | 1.0153 | 0.5260 -> 0.5181 | 424.43 -> 427.30 |

The −7.7% instruction reduction buys 0.7% typed time on numeric (overlapping ranges:
3.0854–3.1309 vs 3.0512–3.4318) and 3.0% on numeric construction (separate ranges); mixed is
0.8% (typed, overlapping) and +1.5% (typed-build, overlapping). **Mechanism (3) alone fails the
≥10% numeric 1 MiB kill gate and is not admitted.** The staging and passes remain only as the
writer of probe (b).

## Probe (b): plain framing for typed plans (mechanism 2, Java-only)

`807e366` frames typed fusable bindings with the scan loop `framePlain` (1,133 instructions,
the plain shape) instead of `frameFused`. While the index scan frames, `BatchBuilder.record`
copies a complete, valid record's spans into the staged chunk (width, row capacity and the
`commit` UTF-8 rule checked once per record) and the (a') column passes convert it. Records it
does not take (wrong width, invalid UTF-8, row limit) and every per-byte scanner record go
through the per-record path, which converts staged rows first; staging is enabled only while
the index scan frames, because the per-byte scanners throw syntax errors before calling
`record`. This is the plan's mechanism (2) goal — the plain loop writes spans unconditionally
and the width is validated once per record — without a kernel record-end bit: the Java plain
loop already exists, and the eight kernels are untouched. A precomputed separator-kind array
would only replace the loop's one-byte load and compare (~3 instructions per entry); it was not
built or measured.

Callgrind (per iteration, before → b): typed numeric 76.20M → 69.13M Ir (−9.3%), typed mixed
45.91M → 41.79M (−9.0%), typed-build −11.1% / −10.1%.

Paired SVM (invocation `e9ea2fe9`, candidate `e48e0ed9…`, baseline `9e8c9447…`):

| Fixture | Workload | Engine | Stage | Baseline median [range] | Candidate median [range] | Ratio | GB/s base -> cand | B/row base -> cand |
|---|---|---|---|---:|---:|---:|---:|---:|
| numeric 1 MiB | strings | truffledata-batch-native | ingest | 4.3149 [4.2705-4.3765] | 4.2905 [4.2719-4.3973] | 0.9944 | 0.2318 -> 0.2331 | 404.36 -> 404.36 |
| numeric 1 MiB | typed | kernel-native | ingest | 2.2386 [2.2109-2.2423] | 2.2221 [2.2123-2.2236] | 0.9926 | 0.4467 -> 0.4500 | 0.01 -> 0.01 |
| numeric 1 MiB | typed | truffledata-plan | ingest | 3.5374 [3.5253-4.1092] | 3.2969 [3.2719-3.3027] | 0.9320 | 0.2827 -> 0.3033 | 88.52 -> 88.75 |
| numeric 1 MiB | typed | truffledata-plan-native | ingest | 3.1137 [3.0801-3.1209] | 2.8758 [2.8678-2.8872] | 0.9236 | 0.3212 -> 0.3477 | 88.57 -> 88.81 |
| numeric 1 MiB | typed-build | truffledata-plan | ingest | 3.0462 [3.0408-3.0667] | 2.7948 [2.7914-2.8223] | 0.9175 | 0.3283 -> 0.3578 | 64.52 -> 64.75 |
| numeric 1 MiB | typed-build | truffledata-plan-native | ingest | 2.6239 [2.6222-2.6350] | 2.4052 [2.3994-4.4046] | 0.9167 | 0.3811 -> 0.4158 | 64.57 -> 64.81 |
| numeric 1 MiB | typed-reads | truffledata-plan | interpret | 72.5808 [72.4465-72.6050] | 72.3333 [72.0032-72.4030] | 0.9966 |  | 2.00 -> 2.00 |
| numeric 1 MiB | typed-reads | truffledata-plan | peak | 6.6174 [6.6062-6.7159] | 6.5968 [6.5866-6.9231] | 0.9969 |  | 1.00 -> 1.00 |
| numeric 1 MiB | typed-reads | truffledata-plan | read-aot | 70.7248 [70.3916-71.2840] | 70.4248 [70.1913-71.2832] | 0.9958 |  | 0.00 -> 0.00 |
| mixed 1 MiB | strings | truffledata-batch-native | ingest | 3.6159 [3.6012-3.6772] | 3.5964 [3.5786-8.2135] | 0.9946 | 0.2766 -> 0.2781 | 1362.90 -> 1362.90 |
| mixed 1 MiB | typed | kernel-native | ingest | 1.2795 [1.2702-4.4755] | 1.2298 [1.2171-1.2469] | 0.9611 | 0.7815 -> 0.8132 | 0.03 -> 0.03 |
| mixed 1 MiB | typed | truffledata-plan | ingest | 2.6984 [2.6887-2.8801] | 2.5195 [2.4967-2.6090] | 0.9337 | 0.3706 -> 0.3969 | 448.16 -> 451.04 |
| mixed 1 MiB | typed | truffledata-plan-native | ingest | 2.0782 [2.0696-2.0848] | 1.9056 [1.9001-1.9523] | 0.9169 | 0.4812 -> 0.5248 | 448.43 -> 451.30 |
| mixed 1 MiB | typed-build | truffledata-plan | ingest | 2.4845 [2.4472-2.4968] | 2.3052 [2.2819-2.3176] | 0.9278 | 0.4025 -> 0.4338 | 424.16 -> 427.04 |
| mixed 1 MiB | typed-build | truffledata-plan-native | ingest | 1.9054 [1.8623-1.9421] | 1.7595 [1.7248-1.8719] | 0.9234 | 0.5248 -> 0.5683 | 424.43 -> 427.30 |

Typed native improves 7.6% (numeric) and 8.3% (mixed), construction 8.3% / 7.7%, all with
separated fork ranges; Java-SWAR typed 6.8% / 6.6%. The compiled read controls are unchanged
(read-aot 0.996, interpreter 0.997, peak 0.997; 0/2/1 B per row). Probe (b) adds 0.24 B/row
(numeric) and 2.88 B/row (mixed) for per-batch staging chunks, removed by `6a75bd8` below.

## Allocation-neutral staging and the decimal-dot test

`6a75bd8` (rebased `66add6f`): the staged chunk is always converted when an index scan returns,
so it is lent per thread for one scan (as `IndexScan`'s index buffer is) instead of allocated
per batch. A failing scan returns it unconverted; a scan nested in a callback finds none and
allocates its own. This restores the before-image bytes/row exactly.

`9cb9256` (rebased `c352096`), from the P0.5 finding: `Numbers.dot` tests "no dot, or exactly
one dot in exactly one word" with `x & (x - 1)` instead of two `Long.bitCount`s, which have no
POPCNT in `-march=compatibility` images. `NumbersTest` covers every dot placement against the
JDK. The `kernel-native` reference uses the same `Numbers` code and gains too.

Paired SVM against the (b) image (invocation `a1e4a98c`, candidate `48ff0a1a…`, baseline
`e48e0ed9…`), i.e. the increment of these two commits:

| Fixture | Workload | Engine | Stage | Baseline median [range] | Candidate median [range] | Ratio | GB/s base -> cand | B/row base -> cand |
|---|---|---|---|---:|---:|---:|---:|---:|
| numeric 1 MiB | strings | truffledata-batch-native | ingest | 4.3524 [4.3474-5.0248] | 4.3396 [4.2952-4.9650] | 0.9970 | 0.2298 -> 0.2304 | 404.36 -> 404.36 |
| numeric 1 MiB | typed | kernel-native | ingest | 2.2219 [2.2178-2.2610] | 2.1444 [2.1429-2.1556] | 0.9651 | 0.4501 -> 0.4663 | 0.01 -> 0.01 |
| numeric 1 MiB | typed | truffledata-plan | ingest | 3.3057 [3.2878-3.3215] | 3.0743 [3.0374-3.0846] | 0.9300 | 0.3025 -> 0.3253 | 88.75 -> 88.52 |
| numeric 1 MiB | typed | truffledata-plan-native | ingest | 2.8783 [2.8718-2.9057] | 2.6388 [2.6350-2.6827] | 0.9168 | 0.3474 -> 0.3790 | 88.81 -> 88.57 |
| numeric 1 MiB | typed-build | truffledata-plan | ingest | 2.8238 [2.8220-2.8729] | 2.5921 [2.5921-2.5961] | 0.9179 | 0.3541 -> 0.3858 | 64.75 -> 64.52 |
| numeric 1 MiB | typed-build | truffledata-plan-native | ingest | 2.4310 [2.4280-2.9195] | 2.1624 [2.1434-2.2866] | 0.8895 | 0.4113 -> 0.4625 | 64.81 -> 64.57 |
| numeric 1 MiB | typed-reads | truffledata-plan | interpret | 72.6319 [71.7445-72.9019] | 73.6632 [72.5567-74.7700] | 1.0142 |  | 2.00 -> 2.00 |
| numeric 1 MiB | typed-reads | truffledata-plan | peak | 6.6284 [6.6112-7.6354] | 6.6199 [6.6110-7.7024] | 0.9987 |  | 1.00 -> 1.00 |
| numeric 1 MiB | typed-reads | truffledata-plan | read-aot | 70.4442 [70.1696-71.3307] | 71.2800 [71.2410-72.1431] | 1.0119 |  | 0.00 -> 0.00 |
| mixed 1 MiB | strings | truffledata-batch-native | ingest | 3.5724 [3.5705-3.5819] | 3.6023 [3.5794-3.6533] | 1.0083 | 0.2799 -> 0.2776 | 1362.90 -> 1362.90 |
| mixed 1 MiB | typed | kernel-native | ingest | 1.2348 [1.2226-1.2552] | 1.2164 [1.1878-1.2245] | 0.9851 | 0.8098 -> 0.8221 | 0.03 -> 0.03 |
| mixed 1 MiB | typed | truffledata-plan | ingest | 2.5167 [2.4988-2.5228] | 2.4207 [2.4076-2.4488] | 0.9619 | 0.3973 -> 0.4131 | 451.04 -> 448.17 |
| mixed 1 MiB | typed | truffledata-plan-native | ingest | 1.9556 [1.9472-1.9893] | 1.8569 [1.8452-1.8627] | 0.9495 | 0.5113 -> 0.5385 | 451.30 -> 448.43 |
| mixed 1 MiB | typed-build | truffledata-plan | ingest | 2.3556 [2.3303-2.3947] | 2.2716 [2.2617-2.2916] | 0.9643 | 0.4245 -> 0.4402 | 427.04 -> 424.17 |
| mixed 1 MiB | typed-build | truffledata-plan-native | ingest | 1.7805 [1.7610-1.8728] | 1.6768 [1.6458-1.7235] | 0.9418 | 0.5616 -> 0.5964 | 427.30 -> 424.43 |

## Probe (c): the all-STRING path on the plain loop (mechanism 1 generalized)

`6098cf1` stages every fusable binding, including the admitted all-STRING one; a STRING field's
chunk pass is the span shift (`words[f][row] = span + shift`), so the kind decision is made
once per field per chunk instead of per cell, and `frameSpans`/`putSpan` become unused. The
callgrind change is small (copied strings numeric −0.7%, mixed −1.7% Ir); the timing change is
larger. Paired SVM against the b+f+e image on the four 1 MiB fixtures (invocation `87c57c94`,
candidate `839846e9…`, baseline `48ff0a1a…`):

| Fixture | Workload | Engine | Stage | Baseline median [range] | Candidate median [range] | Ratio | GB/s base -> cand | B/row base -> cand |
|---|---|---|---|---:|---:|---:|---:|---:|
| numeric 1 MiB | strings | fastcsv | ingest | 3.4749 [3.3992-3.7499] | 3.4099 [3.3886-3.4830] | 0.9813 | 0.2878 -> 0.2933 | 322.66 -> 322.66 |
| numeric 1 MiB | strings | kernel-native | ingest | 2.3350 [2.2789-2.4463] | 2.2850 [2.2679-2.2986] | 0.9786 | 0.4283 -> 0.4376 | 256.01 -> 256.01 |
| numeric 1 MiB | strings | truffledata-batch | ingest | 4.7546 [4.7359-4.7971] | 4.5084 [4.4600-4.5518] | 0.9482 | 0.2103 -> 0.2218 | 404.30 -> 404.30 |
| numeric 1 MiB | strings | truffledata-batch-native | ingest | 4.3612 [4.3282-4.3672] | 4.0299 [4.0215-4.0525] | 0.9240 | 0.2293 -> 0.2481 | 404.36 -> 404.36 |
| numeric 1 MiB | strings | truffledata-batch-owned-native | ingest | 3.8432 [3.8367-3.8490] | 3.5550 [3.5348-3.5658] | 0.9250 | 0.2602 -> 0.2813 | 352.55 -> 352.55 |
| numeric 1 MiB | typed | truffledata-plan-native | ingest | 2.6739 [2.6519-2.6773] | 2.6622 [2.6426-164.6650] | 0.9956 | 0.3740 -> 0.3756 | 88.57 -> 88.57 |
| mixed 1 MiB | strings | fastcsv | ingest | 3.1837 [3.1775-3.1967] | 3.3767 [3.3579-3.4380] | 1.0606 | 0.3141 -> 0.2961 | 995.90 -> 995.90 |
| mixed 1 MiB | strings | kernel-native | ingest | 2.2667 [2.2603-2.3158] | 2.2894 [2.2851-2.3050] | 1.0100 | 0.4412 -> 0.4368 | 898.57 -> 898.57 |
| mixed 1 MiB | strings | truffledata-batch | ingest | 4.1465 [4.1365-4.2090] | 3.9185 [3.9054-3.9549] | 0.9450 | 0.2412 -> 0.2552 | 1362.64 -> 1362.64 |
| mixed 1 MiB | strings | truffledata-batch-native | ingest | 3.5743 [3.5279-3.5772] | 3.4271 [3.3976-3.4539] | 0.9588 | 0.2798 -> 0.2918 | 1362.90 -> 1362.90 |
| mixed 1 MiB | strings | truffledata-batch-owned-native | ingest | 3.1619 [3.1338-3.1638] | 2.9890 [2.9265-3.4488] | 0.9453 | 0.3163 -> 0.3346 | 1133.21 -> 1133.21 |
| mixed 1 MiB | typed | truffledata-plan-native | ingest | 1.8335 [1.8301-1.8642] | 1.8377 [1.8291-1.8609] | 1.0023 | 0.5454 -> 0.5442 | 448.43 -> 448.43 |
| quoted 1 MiB | strings | fastcsv | ingest | 1.4561 [1.4558-1.4703] | 1.4922 [1.4571-1.5130] | 1.0248 | 0.6868 -> 0.6702 | 850.86 -> 850.86 |
| quoted 1 MiB | strings | kernel-native | ingest | 0.6505 [0.6440-0.6618] | 0.6502 [0.6405-0.6528] | 0.9995 | 1.5373 -> 1.5381 | 768.52 -> 768.52 |
| quoted 1 MiB | strings | truffledata-batch | ingest | 1.5754 [1.5648-1.5964] | 1.5761 [1.5689-1.5824] | 1.0005 | 0.6348 -> 0.6345 | 1496.29 -> 1496.29 |
| quoted 1 MiB | strings | truffledata-batch-native | ingest | 1.1878 [1.1862-1.1886] | 1.1702 [1.1611-1.1718] | 0.9852 | 0.8419 -> 0.8546 | 1497.06 -> 1497.06 |
| quoted 1 MiB | strings | truffledata-batch-owned-native | ingest | 0.7152 [0.7072-0.7213] | 0.7138 [0.7114-0.7184] | 0.9981 | 1.3982 -> 1.4009 | 831.63 -> 831.63 |
| escaped 1 MiB | strings | fastcsv | ingest | 2.8364 [2.8312-2.9938] | 2.8612 [2.8189-2.9885] | 1.0088 | 0.3526 -> 0.3495 | 344.10 -> 344.10 |
| escaped 1 MiB | strings | kernel-native | ingest | 1.8797 [1.8717-1.9150] | 1.9296 [1.8702-1.9644] | 1.0265 | 0.5320 -> 0.5182 | 284.15 -> 284.15 |
| escaped 1 MiB | strings | truffledata-batch | ingest | 2.7055 [2.6876-2.7302] | 2.6899 [2.6711-2.7031] | 0.9942 | 0.3696 -> 0.3718 | 594.20 -> 594.20 |
| escaped 1 MiB | strings | truffledata-batch-native | ingest | 2.4190 [2.4055-2.5163] | 2.4115 [2.4010-2.4305] | 0.9969 | 0.4134 -> 0.4147 | 594.47 -> 594.47 |
| escaped 1 MiB | strings | truffledata-batch-owned-native | ingest | 2.0206 [2.0116-2.0343] | 2.0378 [2.0086-2.0618] | 1.0085 | 0.4949 -> 0.4907 | 362.92 -> 362.92 |

Short-field strings improve with separated ranges (numeric copied/owned native 0.924/0.925,
mixed copied native 0.959; mixed owned 0.945 with one slow candidate fork); quoted and escaped
(long cells, materialization-bound) are within noise. The FastCSV/kernel-native controls move
−2% to +6% in the same shards, so single-cell differences below that are not claimed.

## Probe (d): String construction from spans in one method (mechanism 4)

P0.5 put `getString` materialization at 51–52% of the copied-strings cell, spread over a call
chain: `CsvBatch.stringAt` (50 Ir) → `Utf8.string` (74) → `String.<init>` /
`Arrays.copyOfRange` (118) plus bounds checks (22). `59e87de` makes `stringAt` a host-inlining
root, so the Latin-1 test and the String construction inline into one method (AOT
`CsvBatch_stringAt` 102 → 1,586 instructions, 8 → 74 call sites, almost all slow paths). The
String is still built from the retained span at read time with the same Latin-1/UTF-8 rules;
cells, laziness and the API are unchanged. Callgrind: copied strings numeric 82.66M → 70.02M
Ir (−15.3%), mixed 62.11M → 53.26M (−14.2%). Paired SVM against the (c) image (invocation
`c2b663de`, candidate `9fb69d27…`, baseline `839846e9…`):

| Fixture | Workload | Engine | Stage | Baseline median [range] | Candidate median [range] | Ratio | GB/s base -> cand | B/row base -> cand |
|---|---|---|---|---:|---:|---:|---:|---:|
| numeric 1 MiB | strings | fastcsv | ingest | 3.4858 [3.3612-3.4965] | 3.3602 [3.3508-3.4202] | 0.9640 | 0.2869 -> 0.2976 | 322.66 -> 322.66 |
| numeric 1 MiB | strings | kernel-native | ingest | 2.3518 [2.3409-2.5439] | 2.2726 [2.2678-2.2854] | 0.9663 | 0.4252 -> 0.4400 | 256.01 -> 256.01 |
| numeric 1 MiB | strings | truffledata-batch | ingest | 4.5369 [4.5291-4.5429] | 4.0525 [4.0275-4.0920] | 0.8932 | 0.2204 -> 0.2468 | 404.30 -> 404.30 |
| numeric 1 MiB | strings | truffledata-batch-native | ingest | 4.0462 [3.9901-4.0482] | 3.5886 [3.5877-3.6228] | 0.8869 | 0.2471 -> 0.2787 | 404.36 -> 404.36 |
| numeric 1 MiB | strings | truffledata-batch-owned-native | ingest | 3.6175 [3.6168-3.6254] | 3.3323 [3.1464-3.4043] | 0.9212 | 0.2764 -> 0.3001 | 352.55 -> 352.55 |
| numeric 1 MiB | typed | truffledata-plan-native | ingest | 2.6904 [2.6029-2.7580] | 2.6440 [2.6387-2.6562] | 0.9828 | 0.3717 -> 0.3782 | 88.57 -> 88.57 |
| mixed 1 MiB | strings | fastcsv | ingest | 3.3781 [3.3588-3.5706] | 3.2256 [3.1852-65.9546] | 0.9549 | 0.2960 -> 0.3100 | 995.90 -> 995.90 |
| mixed 1 MiB | strings | kernel-native | ingest | 2.2466 [2.2208-2.2756] | 2.3242 [2.3135-2.3467] | 1.0345 | 0.4451 -> 0.4303 | 898.57 -> 898.57 |
| mixed 1 MiB | strings | truffledata-batch | ingest | 4.0118 [3.9193-4.0996] | 3.7914 [3.6832-3.8442] | 0.9451 | 0.2493 -> 0.2638 | 1362.64 -> 1362.64 |
| mixed 1 MiB | strings | truffledata-batch-native | ingest | 3.4082 [3.4005-3.5351] | 3.0732 [3.0729-3.1446] | 0.9017 | 0.2934 -> 0.3254 | 1362.90 -> 1362.90 |
| mixed 1 MiB | strings | truffledata-batch-owned-native | ingest | 2.9405 [2.9132-3.0681] | 2.5992 [2.5801-2.6926] | 0.8839 | 0.3401 -> 0.3847 | 1133.21 -> 1133.21 |
| mixed 1 MiB | typed | truffledata-plan-native | ingest | 1.8873 [1.8208-2.2094] | 1.8696 [1.8069-1.9441] | 0.9906 | 0.5298 -> 0.5349 | 448.43 -> 448.43 |
| quoted 1 MiB | strings | fastcsv | ingest | 1.4805 [1.4801-1.4823] | 1.4667 [1.4652-1.4784] | 0.9907 | 0.6754 -> 0.6818 | 850.86 -> 850.86 |
| quoted 1 MiB | strings | kernel-native | ingest | 0.6516 [0.6383-0.6728] | 0.6439 [0.6436-0.6727] | 0.9882 | 1.5346 -> 1.5530 | 768.52 -> 768.52 |
| quoted 1 MiB | strings | truffledata-batch | ingest | 1.5649 [1.5565-1.5857] | 1.5256 [1.5256-1.5653] | 0.9749 | 0.6390 -> 0.6555 | 1496.29 -> 1496.29 |
| quoted 1 MiB | strings | truffledata-batch-native | ingest | 1.1720 [1.1647-1.1929] | 1.1619 [1.1406-1.1763] | 0.9914 | 0.8532 -> 0.8606 | 1497.06 -> 1497.06 |
| quoted 1 MiB | strings | truffledata-batch-owned-native | ingest | 0.7067 [0.7023-0.7102] | 0.6905 [0.6827-0.6952] | 0.9771 | 1.4151 -> 1.4483 | 831.63 -> 831.63 |
| escaped 1 MiB | strings | fastcsv | ingest | 2.8622 [2.8482-2.8683] | 2.8285 [2.8107-2.8383] | 0.9882 | 0.3494 -> 0.3535 | 344.10 -> 344.10 |
| escaped 1 MiB | strings | kernel-native | ingest | 1.8768 [1.8713-1.8947] | 1.8371 [1.8313-1.9030] | 0.9788 | 0.5328 -> 0.5443 | 284.15 -> 284.15 |
| escaped 1 MiB | strings | truffledata-batch | ingest | 2.6689 [2.6596-2.7358] | 2.6457 [2.6373-2.7140] | 0.9913 | 0.3747 -> 0.3780 | 594.20 -> 594.20 |
| escaped 1 MiB | strings | truffledata-batch-native | ingest | 2.4008 [2.3496-2.4477] | 2.3658 [2.3131-2.4290] | 0.9854 | 0.4165 -> 0.4227 | 594.47 -> 594.47 |
| escaped 1 MiB | strings | truffledata-batch-owned-native | ingest | 2.0521 [2.0077-2.0554] | 1.9617 [1.9560-2.3267] | 0.9560 | 0.4873 -> 0.5098 | 362.92 -> 362.92 |

Short-field strings improve 8–12% with separated ranges (copied native numeric 0.887, mixed
0.902; owned 0.921 / 0.884); long quoted/escaped cells 1–4%. In the same shards FastCSV and
kernel-native move −4.5% to +3.5%, so the long-cell gains are not claimed individually.

## Validation (final tree `afa4aa5`, rebased on `641aa45`)

[Invocation 09873009](https://app.buildbuddy.io/invocation/09873009-7b09-4358-8c8b-fd0f9f08196f),
`--nocache_test_results`, all pass:

- `//csv/tests:jvm` and `//csv/tests:jvm_indexed` (forced index path, small SWAR blocks): 124
  tests each, zero failures (121 before plus three added: chunk boundaries and error/field order
  with later framing errors, constraints/binary32/Commons complex spans in staged chunks, stream
  and mapped readers with two readers of different widths alternating on one thread). The
  existing per-byte vs index differential suites compare whole exported batches on every entry
  point (parse, parseOwned, chunked streams, mapped parse/stream, path parse) over eight dialects.
- `//csv/native:index_replay` (PR #39 corpus regression for the index kernels; the kernels are
  unchanged, `IndexScan` consumes them).
- `//csv/tests:native_test`: SVM CSV acceptance passed; row iteration 1.6e-5 B/read, export
  0.0064 B/row (unchanged gates).
- `//benchmarks/comparison:csv_native_smoke`: 166 throughput cells agree per workload (162
  before plus the four `typed-build` cells); `CsvReads[truffledata-plan]` 439/383 nodes, one
  loop, zero invokes, zero allocations (unchanged); peak 1.0 B/row.

[Invocation 8942ff67](https://app.buildbuddy.io/invocation/8942ff67-3456-45d8-81cb-37f3dfdd49ab):
`//benchmarks/comparison:csv_aot_symbols` (budgets re-pinned from the final image) and
`//tools/bench:test` pass; `test_csv_pair` (4 tests) passes locally. Earlier probe commits each
passed `//csv/tests:jvm` and `:jvm_indexed` (invocations `676f690b`, `395297f7`, `69e2f22b`,
`6783f9ad`, `44fb600b`). JVM timing is not recorded and gates nothing.

Semantics preserved by construction and covered above: dialect fallback (per-byte scanner for a
declined record, with staging disabled outside the index scan), quote/escape/CRLF framing (the
unchanged `framePlain`), configured nulls (stateful plans never stage), exact numeric rounding
and overflow (same `Numbers` code; `dot` returns identical offsets), UTF-8 (the `commit` rule per
staged record, prevalidation unchanged), limits (row capacity checked per staged record; width,
`maxColumns`, `maxRecordBytes` in the unchanged loop), row error order (staged rows convert
before any later record or per-byte scan can report, and a failing chunk replays row by row in
record and field order) and partial-row behaviour (staged rows are written only on conversion).

## Reproduction

Build and preserve the before-image from the base production plus harness (one RBE invocation),
then export it as an ignored local label:

```sh
bzl build //benchmarks/comparison:csv_svm //benchmarks/comparison:csv_profile \
  //benchmarks/comparison:csv_profile_debug --output_groups=+debug_files
mkdir -p build/csv-writers-before && cp bazel-bin/benchmarks/comparison/csv-comparison* build/csv-writers-before/
printf 'exports_files(["csv-comparison"], visibility = ["//visibility:public"])\n' > build/csv-writers-before/BUILD.bazel
```

Paired run of the current tree against it (subset shards/engines as recorded in each
`provenance.json`; the admission run uses all eight shards and the default engines):

```sh
bzl test //benchmarks/comparison:csv_pair \
  --//benchmarks/comparison:csv_svm_baseline=//build/csv-writers-before:csv-comparison \
  --test_env=CSV_PAIR_BASELINE_REVISION=e650d87+harness-7cd2584 \
  --test_env=CSV_PAIR_CANDIDATE_REVISION=<sha> --nocache_test_results
```

Callgrind (amd64 container with Valgrind 3.24; Valgrind 3.19 rejects the image's DWARF):

```sh
docker build --platform linux/amd64 -t td-valgrind-trixie - <<'EOF'
FROM debian:trixie-slim
RUN apt-get update && apt-get install -y --no-install-recommends valgrind python3 binutils
EOF
docker run --rm --platform linux/amd64 -v $PWD/build/csv-writers-before:/b:ro -v $PWD/out:/out \
  td-valgrind-trixie valgrind --tool=callgrind --toggle-collect='*profileRegion*' \
  --compress-strings=no --compress-pos=no --dump-instr=yes --callgrind-out-file=/out/cg.CELL \
  /b/csv-comparison-profile-debug --profile typed truffledata-plan-native numeric 1048576 2
python3 tools/bench/callgrind_split.py out/cg.CELL --top 30
```

AOT symbol table: `python3 tools/bench/aot_symbols.py build/csv-writers-before/csv-comparison-profile
--pattern '^IndexScan_frame' --pattern '^BatchBuilder_'`, or the manual remote gate
`bzl test //benchmarks/comparison:csv_aot_symbols` with `csv_aot_budgets.json`.
