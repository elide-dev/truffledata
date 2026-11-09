# CSV number conversion (WS10b)

Plan: `docs/superpowers/plans/2026-11-08-beat-stock-svm.md` §4 WS10 mechanism (3), the conversion
itself ("SWAR digits, Eisel-Lemire"); rulings in `2026-11-08-beat-stock-svm-ledger.md` under
"WS10b (CSV conversion worker)". SVM / Native Image is the admission target; JVM timing is not
recorded here and gates nothing.

## Summary

- **The plan's named mechanism already shipped.** `Numbers` at the base already parsed eight
  digits per SWAR step and used Eisel-Lemire (fast_float `compute_float`) for long significands
  and exponents. On both benchmark fixtures every FLOAT64 value is a short decimal on the Clinger
  fast path (significand ≤ 2^53, divide by an exact power of ten); Eisel-Lemire never runs. The
  cost was in how the fast path found and read the digits.
- **FLOAT64 (admitted, `5f0406d`):** one word load per ≤8-byte field (two for 9–16), dot removed
  by a byte shift, one subtract-based digit test, one SWAR conversion, one division. Per value
  264 → 149 instructions (numeric), 268 → 175 (mixed). Paired SVM typed numeric 1 MiB native
  2.6337 → 2.3935 ns/B (0.909×, ranges separate), 0.380 → 0.418 GB/s.
- **INT64 (killed):** sign read from the field's own word and the same digit test, 116.7 → 100.4
  instructions per value, −2.0% typed numeric Ir; paired SVM 0.934× with overlapping forks and
  a 1.007× confirmation run. Not admitted; kept only on the local branch
  `perf/csv-numbers-int64` (`a13d55a`).
- **Readback getters (mechanism 3 of this brief): not started**, because INT64 did not land.
- Differential test: 2,650,390 placed generated tokens plus 291,434 benchmark-fixture fields,
  zero divergences between the new parsers and the `f775a9a` parsers kept verbatim as
  `NumbersReference`.

## Source and binaries

- Base: `perf/beat-stock-svm` at `f775a9a`. Its CSV image closure equals `afa4aa5` (WS10 final):
  the before-image built from `f775a9a` (RBE invocation `ac698f35`) is **byte-identical** to the
  preserved `csv-writers-final` images (`csv-comparison` `f3160426…`, profile `2e1cdfc8…`,
  profile-debug `aae7ddd7…`), so `../csv-writers-afa4aa5/callgrind/final/` are exact profiles of
  this before-image. Preserved with SHA-256 manifest in ignored `build/csv-numbers-before/`.
- FLOAT64 candidate `5f0406d` (images built in invocation `9b241804` from the identical tree
  `87d53db`): `csv-comparison` `cb60b545…`. INT64 probe `a13d55a` on `f775a9a` (no FLOAT64
  change; invocation `9e030539`): `csv-comparison` `2e6b6e18…`. All hashes: `images.sha256`.
- All images: pinned `25.5.5-elide.27` SDK, Linux x86-64, `-O2`, `-march=compatibility`, serial
  GC, no PGO. Pairs: `csv_pair`, three fresh forks per binary interleaved on one pinned CPU per
  shard of one RBE worker (AVX-512 kernel); shards 0, 1, 4, 5 (numeric and mixed at 1 MiB and
  128 MiB, every default engine plus `typed-build`, and the shard-0 compiled read controls).
  Medians of fork medians; ranges are fork-median minima/maxima. x86-64 SVM claims only.

## P0 attribution: where the FLOAT64 and INT64 instructions went (before image)

Callgrind 3.24 (amd64 container), `csv_profile_debug`, `--profile typed-build
truffledata-plan-native numeric 1048576 2`. Each value of a fixture column takes one path, so
per-instruction execution counts give exact per-value costs: in `convertDoubles`, 264
instructions execute exactly 121,440 times (60,720 values per iteration = 3 × 20,240 rows).
Region split by address range (`callgrind/convertDoubles-before.annotated.txt`):

| FLOAT64 region (before, per value) | Instructions |
|---|---:|
| Pass loop: bound, span load/decode, Commons test, length window | 28 |
| Pass loop: binary32 test, output store with null/bounds check, safepoint, spill reloads, back edge | 26 |
| Sign byte: null/bounds check, load, `+`/`-` compares | 16 |
| `Numbers.dot`: two word loads with bounds checks, two zero-byte tests, window masks, one-dot test, offset | 81 |
| Whole-part digits word: lengths, bounds check, load, 9 constants, OR-mask, digit test, 3 multiplies | 57 |
| Fraction digits word (reuses the `dot` load): mask, digit test, 3 multiplies | 34 |
| Combine: both parts' sign test, `TENS[fraction]` multiply, 2^53 test | 13 |
| Clinger: `POWERS[fraction]`, `cvtsi2sd`, `divsd`, negate test | 9 |
| **Total** | **264** |

(The ledger's 304–306 per value comes from probe (a'), before the popcount-free `Numbers.dot`
(`c352096`, `dot` 195 → 153 instructions); the WS10 closing entry repeated it. On the WS10 final
image it is 306 − 42 = 264.) Eisel-Lemire, the grammar loop and
the JDK fallback execute zero times on both fixtures.

| INT64 region (before, per value; `delta` is negative 1 in 8 values) | Instructions |
|---|---:|
| Pass loop: bound, span load/decode, Commons test | 15.0 |
| Sign: `i < end`, null/bounds check, byte load, compare, digit count | 16.0 |
| Length window tests | 4.0 |
| Word: null/bounds check, load, `count == 0` test | 10.0 |
| Mask to field (OR with `'0'`) and digit test | 26.0 |
| SWAR conversion (constants, 3 multiplies) | 13.0 |
| Negate / result | 6.5 |
| Pass loop: output store, safepoint, reloads, back edge | 25.0 |
| **Total** | **116.7** |

So INT64 was already the single-load SWAR kernel; ~40 of its 117 instructions are the pass loop
around it, and the parse proper is ~75.

## Mechanism 1: FLOAT64 from the words that end the field (`5f0406d`, admitted)

`Numbers.parseDouble`, after an optional sign, for `n` ≤ 8 bytes (input offset ≥ 8): load the
word that ends the field, mark dots with the exact zero-byte test masked to the field, reject two
dots or a lone dot, shift the bytes below the dot up one byte (`undot`), subtract `'0'` from the
field bytes only, test all bytes are 0..9 with `((t + 0x76…) | t) & 0x80…` (the lowest offending
byte cannot receive a borrow, so it is ≥ 0x80 or 0x0A..0x7F), convert with the SWAR multiply chain
and divide by `POWERS[fraction]`. For 9–16 bytes the same over two words (the dot's word shifts,
the low word's top byte carries into the high word). With a dot at most 15 digits remain, so
significand and divisor are exact doubles and one IEEE division is correctly rounded (Clinger);
without one, a ≤16-digit long converts to double with round-to-nearest-even (JLS 5.1.2). Every
other token (exponents, >16 bytes, fields ending before input byte 8/16) takes the unchanged
grammar with Eisel-Lemire and the JDK fallback. `Numbers.dot` and `TENS` are deleted.

Per value: numeric 264 → **149** instructions (all values one path); mixed 268.4 → **175.2**
(27,396 values per iteration; lat/lon/balance take the two-word path). Remaining: the pass loop
(~54, unchanged) and the sign byte load (16).

### Callgrind (Ir per iteration; checksums equal to the before-image in every cell)

| Cell | Before | FLOAT64 | Ratio |
|---|---:|---:|---:|
| typed numeric | 66.37M | 59.38M | 0.8948 |
| typed mixed | 40.58M | 38.03M | 0.9370 |
| typed-build numeric | 53.81M | 46.83M | 0.8702 |
| typed-build mixed | 35.47M | 32.91M | 0.9280 |

Only `BatchBuilder::convertDoubles` moves (numeric 16.18M → 9.20M, mixed 7.35M → 4.80M); every
other method's self Ir is identical.

### Paired SVM (invocation `e5236b2b`, candidate `cb60b545…`, baseline `f3160426…`)

Typed and construction cells (full table with every engine: `pairs/float64.md`; raw:
`pairs/float64/shard_*/`). ns/byte, median of fork medians [fork range].

| Fixture | Workload | Engine | Baseline | Candidate | Ratio | Ranges | GB/s | B/row |
|---|---|---|---:|---:|---:|---|---:|---:|
| numeric 1 MiB | typed | truffledata-plan-native | 2.6337 [2.6248-2.6577] | 2.3935 [2.3809-2.4328] | 0.9088 | separate | 0.3797 → 0.4178 | 88.57 → 88.57 |
| numeric 1 MiB | typed | truffledata-plan | 3.0711 [3.0332-3.0764] | 2.8879 [2.8490-3.0434] | 0.9404 | overlap | 0.3256 → 0.3463 | 88.51 → 88.51 |
| numeric 1 MiB | typed-build | truffledata-plan-native | 2.1586 [2.1400-2.1845] | 1.9499 [1.9312-1.9651] | 0.9033 | separate | 0.4633 → 0.5128 | 64.57 → 64.57 |
| numeric 1 MiB | typed-build | truffledata-plan | 2.6319 [2.5993-2.6330] | 2.3860 [2.3779-2.3935] | 0.9066 | separate | 0.3799 → 0.4191 | 64.51 → 64.51 |
| mixed 1 MiB | typed | truffledata-plan-native | 1.8404 [1.8075-1.8658] | 1.7520 [1.7370-1.7694] | 0.9520 | separate | 0.5434 → 0.5708 | 448.40 → 448.40 |
| mixed 1 MiB | typed | truffledata-plan | 2.4284 [2.3738-2.4403] | 2.3242 [2.3121-2.3313] | 0.9571 | separate | 0.4118 → 0.4303 | 448.14 → 448.14 |
| mixed 1 MiB | typed-build | truffledata-plan-native | 1.6515 [1.6222-1.6731] | 1.5865 [1.5629-1.5918] | 0.9607 | separate | 0.6055 → 0.6303 | 424.40 → 424.40 |
| numeric 128 MiB | typed | truffledata-plan-native | 2.9759 [2.9431-5.3247] | 2.7608 [2.7495-2.8122] | 0.9277 | separate | 0.3360 → 0.3622 | 147.95 → 147.95 |
| numeric 128 MiB | typed-build | truffledata-plan-native | 2.4361 [2.4141-2.4443] | 2.2361 [2.2358-2.2483] | 0.9179 | separate | 0.4105 → 0.4472 | 123.95 → 123.95 |
| mixed 128 MiB | typed | truffledata-plan-native | 2.0247 [2.0123-6.1038] | 1.9396 [1.9292-2.0109] | 0.9580 | separate | 0.4939 → 0.5156 | 449.11 → 449.11 |
| mixed 128 MiB | typed-build | truffledata-plan-native | 1.8245 [1.8155-1.8263] | 1.7405 [1.7382-1.7937] | 0.9540 | separate | 0.5481 → 0.5746 | 425.11 → 425.11 |

Geometric means over the four fixture/size cells: typed native 0.936, typed Java-SWAR 0.951,
typed-build native 0.934. **Kill gate (<5% typed numeric 1 MiB with overlapping forks) not
triggered:** 9.1% with separated ranges. Mixed gains less (4.8%) because FLOAT64 is a smaller
share of its work (six of 24 columns; STRING plans also pay the input copy).

Readback (typed minus typed-build medians, native, 1 MiB): numeric 0.475 → 0.444 ns/B, mixed
0.189 → 0.166 ns/B; the conversion change is construction-side, as expected.

Controls (same shards): compiled reads unchanged (read-aot 1.003, interpreter 0.998, peak 1.007,
all overlapping; 0/2/1 B/row); FastCSV typed 0.985–0.996, FastCSV/Jackson strings 0.991–1.022.
Library strings cells overlap except mixed 128 MiB copied native 1.043 (separate), alongside
Jackson 1.018 (separate) in the same shard; the strings path calls no `Numbers` code, so this is
shard drift and is not claimed either way. `kernel-native` typed improves 0.845×/0.906× (1 MiB)
and 0.839×/0.910× (128 MiB) because it calls the same `Numbers.parseDouble`, and its share of
conversion is larger.

### Standing (1 MiB, native index, same shard)

| Cell | Before GB/s | After GB/s | vs FastCSV after | vs kernel-native after |
|---|---:|---:|---:|---:|
| typed numeric | 0.3797 | 0.4178 | 3.84× | 0.760× (was 0.817×; the bound moved more) |
| typed mixed | 0.5434 | 0.5708 | 3.23× | 0.613× (was 0.644×) |

The plan's typed ≥0.7 GB/s hypothesis is still not reached; Rust `csv` is not in `csv_pair`.

## Mechanism 2: INT64 sign from the field's word (`a13d55a`, killed)

Isolated on `f775a9a`: fields of up to 16 bytes load the word(s) ending them once; a leading
`-` becomes `0` by adding 3 to its byte (so the sign costs no separate byte load), the
subtract-based digit test replaces the OR-with-`'0'` mask; >16 bytes or near the input start
take the unchanged overflow-checked loop (≤16 digits cannot overflow, so overflow detection is
the old code exactly). Per value 116.7 → 100.4 instructions; callgrind typed numeric 66.37M →
65.04M (0.980), typed-build 0.975, mixed 0.990. AOT: `convertLongs` 691 → 741 instructions and
`parseLong` 349 → 517 (more code, fewer executed), which would also have needed an upward
budget ruling.

Paired SVM (invocation `d3f4decc`, `pairs/int64-probe.md`): typed numeric 1 MiB native 2.8117
[2.6543-2.8126] → 2.6249 [2.6187-2.8220] (0.934, **ranges overlap**; two baseline forks were
slow), typed-build native 0.991, 128 MiB typed native 0.991 (overlap), mixed 0.951–1.000
(overlap). Confirmation run of the numeric 1 MiB typed cells (invocation `8db3e5aa`,
`pairs/int64-probe-confirm.md`): typed native **1.007** [2.6008-2.6535 vs 2.6124-2.9439],
typed-build native 1.025, both overlapping. **Killed** under the <5%/overlapping rule: a −2% Ir
change does not show in time. The branch is not merged.

## Mechanism 3: `CsvRow` readback getters — not started

The brief allows it only if (1) and (2) both land. Recorded shape for a later tranche:
`CsvRow_getLong`/`getDouble`/`getBoolean` are 81/80/81-instruction out-of-line calls (7 call
sites each) and cost 3.40M + 2.49M Ir per typed numeric iteration in the harness loop.

## Observation (not changed): `framePlain`

`framePlain` is unchanged (1,133 instructions, 25 calls, 200 B frame, 300 stack loads, 127 stack
stores) and is the largest single method in typed numeric (17.07M of 59.38M Ir after this
change, 29%). The pass loops around the parsers show the same pattern on a smaller scale: about
ten spill reloads and an explicit null/bounds check per array access per value.

## AOT symbol table (csv_profile, x86-64, -O2, `-march=compatibility`)

Instructions / calls / frame bytes / stack loads (`aot/*.md`, `aot/*.json`).

| Symbol | Before (`f775a9a`) | FLOAT64 (`5f0406d`) | INT64 probe (`a13d55a`) |
|---|---|---|---|
| `BatchBuilder_convertDoubles` | 1,414 / 38 / 120 / 98 | **1,321 / 38 / 120 / 75** | 1,414 / 38 / 120 / 98 |
| `BatchBuilder_convertLongs` | 691 / 31 / 104 / 36 | 691 / 31 / 104 / 36 | 741 / 34 / 104 / 33 |
| `Numbers_parseDouble` | 215 / 10 / 56 / 33 | 174 / 11 / 56 / 12 | 215 / 10 / 56 / 33 |
| `Numbers_shortDecimal` | — | 155 / 0 / 8 / 0 | — |
| `Numbers_longDecimal` | — | 204 / 0 / 8 / 0 | — |
| `Numbers_dot` | 153 / 4 / 40 / 0 | removed | 153 / 4 / 40 / 0 |
| `Numbers_parseLong` | 349 / 20 / 56 / 12 | 349 / 20 / 56 / 12 | 517 / 24 / 56 / 7 |
| `Numbers_digits` | 85 / 3 / 40 / 0 | 85 / 3 / 40 / 0 | 85 / 3 / 40 / 0 |
| `Numbers_grammar` / `eiselLemire` | 503 / 25, 162 / 0 | unchanged | unchanged |
| `IndexScan_framePlain` | 1,133 / 25 / 200 / 300 | unchanged | unchanged |
| `CsvRow_getLong` / `getDouble` / `getBoolean` | 81 / 7, 80 / 7, 81 / 7 | unchanged | unchanged |

`csv_aot_budgets.json` (`85801f7`): `convertDoubles` re-pinned downward 1,555 → 1,453; the
`Numbers_dot` key removed with the method; `Numbers_parseDouble` (191), `shortDecimal` (170) and
`longDecimal` (224) pinned with ~10% headroom. No budget moved upward.

## Correctness: the differential test

`csv/tests/NumbersDifferentialTest.java` (JVM suites `//csv/tests:jvm` and `:jvm_indexed`)
compares `Numbers.parseDouble` and `parseLong` with `NumbersReference` (the `f775a9a` parsers,
verbatim): identical double bits (including `-0.0`) or long, or both reject. Every token is
placed at ten offsets after filler bytes (`,` `9` `0` `.` `-` `+` `e` `0xFF` `\n` `"` `0x00`, and a
uniformly random byte one time in four) so the word loads see every neighbour. Corpus:
every 1–18-byte shape with a dot at every position or none; every byte value 0..255 at every
position of the 1–17-byte shapes; 40k grammar-soup tokens; 2^53 ± 40 with every dot placement;
every exponent −400..400 with six mantissas; subnormal/normal/overflow edges; 40k 17–20-digit
significands with exponents; 20k shortest and plain representations of random doubles; 4k exact
midpoints and their neighbours; 20k short halfway-looking decimals; 40k integers to 21 digits and
the int64 edges — **2,650,390 placed tokens**; plus the four benchmark fixtures at 1 MiB parsed
field by field in place (`CsvFixture`, via the new test-only `//benchmarks/comparison:csv_fixtures`)
— **291,434 fields**. **Divergences found: none.** `NumbersTest` (JDK oracle) still passes.

The test was checked against mutants of the new code: accepting a lone dot, a weakened digit
constant, a wrong carry byte, a wrong low-word fraction, a wrong post-shift mask, and for the
INT64 probe a lone `-`, a wrong sign substitution and a 17-byte window: all fail it. Two mutants
are equivalent rather than missed: the 2^53 bound on the dot-free path (long-to-double is itself
correctly rounded, so the bound was removed) and the two-dot check in the low word (a second dot
survives the shift as 0x2E and fails the digit test).

## Validation (final tree `85801f7`)

[Invocation a9010ef9](https://app.buildbuddy.io/invocation/a9010ef9-e993-4f0a-8c19-8d9a726eddce),
`--nocache_test_results`, all pass: `//csv/tests:jvm` and `//csv/tests:jvm_indexed` (126 tests
each), `//csv/tests:native_test` (typed and row iteration 1.6e-5 B/read, export 0.0064 B/row),
`//csv/native:index_replay`, `//benchmarks/comparison:csv_native_smoke` (166 cells agree;
`CsvReads[truffledata-plan]` 439/383 nodes, 1 loop, 0 invokes, 0 allocations),
`//benchmarks/comparison:csv_aot_symbols` (re-pinned budgets), `//tools/bench:test`. Every pair
compared full-output checksums, input hashes and row counts across binaries and engines.

## Reproduction

```sh
# before-image (from f775a9a), then export as an ignored label
bzl build //benchmarks/comparison:csv_svm //benchmarks/comparison:csv_profile \
  //benchmarks/comparison:csv_profile_debug --output_groups=+debug_files
mkdir -p build/csv-numbers-before && cp bazel-bin/benchmarks/comparison/csv-comparison* build/csv-numbers-before/
printf 'exports_files(["csv-comparison"], visibility = ["//visibility:public"])\n' > build/csv-numbers-before/BUILD.bazel
# paired run of the current tree
bzl test //benchmarks/comparison:csv_pair \
  --//benchmarks/comparison:csv_svm_baseline=//build/csv-numbers-before:csv-comparison \
  --test_env=CSV_PAIR_SHARDS=0,1,4,5 --test_env=CSV_PAIR_BASELINE_REVISION=f775a9a \
  --test_env=CSV_PAIR_CANDIDATE_REVISION=<sha> --nocache_test_results
```

Callgrind and AOT tables as in `../csv-writers-afa4aa5/README.md` (cells `typed` and
`typed-build`, `truffledata-plan-native`, numeric/mixed, 1 MiB, two iterations).
