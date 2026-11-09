# JSON heap-tape directory lookups (follow-up, Lever B)

Ledger: `docs/superpowers/plans/2026-11-08-beat-stock-svm-ledger.md`, "Follow-ups (CSV readback,
JSON directoryKey)". SVM / Native Image is the admission target; JVM timing is not recorded here
and gates nothing.

## Summary

- **Candidate (`ada7835`):** `HeapTape.directoryKey`, an override of `TapeReader.directoryKey`'s
  element-wise default over the heap tape's own arrays: the same checks, the same `(int)` casts,
  the same word loads and masks, the same miss result (`-1`) and the same exceptions on invalid
  indices. Pinned by `CompactTapeTest.heapDirectoryKeyMatchesTheElementWiseDefault`.
- **Paired SVM (snapshot cells): parity.** read-aot 172.132 → 172.290 ns/doc (0.999×, overlap),
  interpret 1.002×, peak 1.001×, ingest 0.969× (overlap, one slow candidate fork). **Killed**
  under the brief's rule (<3% on snapshot read-aot with overlapping forks) and reverted.
- **Why parity:** in the `json_svm` measurement image the default was already monomorphic.
  `HeapTape` is the only reader that reaches it (`SvmJsonMessage` and `SvmTape` override it), and
  it is reached from runtime-compiled read code, so Truffle host inlining had already
  devirtualized and inlined `readWord`, `directoryEntry` and `textWord` into it. The before
  symbol `TapeReader_directoryKey` (301 instructions / 10 calls, all exception and allocation
  stubs) becomes `HeapTape_directoryKey` (302 / 10): the same code.
- **Where it does move:** the CodSpeed simulation image (`//benchmarks:simulation`) also links
  YAML, and there another reader without an override (`SvmYamlMessage` or `SegmentTape`; not
  identified) shares the default, which made four indirect hook calls per lookup. Callgrind `json-generic-snapshot`: 49,547,534 → 47,192,404 Ir (−4.75%,
  24,193 → 23,043 Ir/document). Inclusive `directoryKey` fell 4,648,064 → 2,293,760 Ir (−50.6%),
  and the interop-read control is unchanged (31,207,831 → 31,207,752). These are instruction
  counts in an image the timing pair does not measure, so they are not a time claim.

The ledger's "1,400 vs 710 Ir/doc" came from the simulation image. It does not describe the
measurement image, where the default had already been specialized.

## Source and binaries

- Base `cd3c193` (`perf/beat-stock-svm`). Before-images built in RBE invocation `3aa6d586`,
  preserved with SHA-256 manifest under ignored `build/fu-before-cd3c193/json/`:
  `json-comparison` `21630a42…929f`, `json-comparison-profile` `5f18717a…dae`,
  `truffledata-simulation` `e07cc516…aed`.
- Candidate tree `ef16275` (Lever B `ada7835` plus the CSV-only Lever A commit; `//csv` is not
  in the JSON image closure, and the shared `data_java_library` macro change produces identical
  javacopts for every other library). RBE invocation `5311f2f0`, preserved under
  `build/fu-cand-ef16275/json/`: `json-comparison` `2c3c8531…a4`, profile `514c0657…ac`,
  simulation `ea785bfe…2c`. All hashes: `images.sha256`.
- Images: pinned `25.5.5-elide.27` CE SDK, Linux x86-64, `-O2`, `-march=compatibility`,
  serial GC, no PGO.

## AOT shape (`aot/`, `json_svm_profile`, x86-64, llvm-objdump)

| Symbol | Before (`cd3c193`) | After (`ada7835`) |
|---|---|---|
| `TapeReader_directoryKey` (default) | 301 / 10 calls / 72 B frame | — (no longer reached) |
| `HeapTape_directoryKey` | — | 302 / 10 / 72 B |
| `SvmJsonMessage_directoryKey` | 141 / 0 | 141 / 0 |
| `TapeOwner_hinted` | 47 / 2 | 47 / 2 |
| `TapeValue_memberNode` (5 variants) | 195/11, 110/6, 89/5, 94/7, 99/6 | unchanged |

Simulation image (`aot/sim-*.md`): before, `TapeReader::directoryKey` is 251 instructions / 6
calls, 4 of them indirect (`readWord`, `directoryEntry`, `textWord` ×2). After,
`HeapTape::directoryKey` is 265 / 5 with no indirect call, and the default (163 / 0) is left
monomorphic for the remaining reader.

`json_aot_budgets.json` is unchanged: it pins neither symbol, and the candidate is reverted.

## Paired SVM (`pairs/`)

Invocation `7ed224e5`. `json_typed_pair`, `JSON_PAIR_GROUPS=6` (snapshot at every stage, with
the stream-interop, generic-interop and decode-only cells as same-host controls), three fresh
forks per image interleaved on one pinned CPU (AMD EPYC shard). Median of fork medians, ns per
document, fork-median ranges.

| Cell | Before | After | Before/after | Ranges | B/doc |
|---|---:|---:|---:|---|---:|
| snapshot ingest | 1,435.169 (1,433.127–1,464.728) | 1,481.830 (1,454.988–1,609.064) | 0.969× | overlap | 1,183.5 → 1,183.5 |
| snapshot read-aot | 172.132 (170.081–172.866) | 172.290 (168.242–220.331) | 0.999× | overlap | 240 → 240 |
| snapshot interpret | 174.101 (173.663–178.349) | 173.770 (172.049–175.104) | 1.002× | overlap | 242 → 242 |
| snapshot peak | 41.762 (41.523–41.774) | 41.724 (41.369–41.820) | 1.001× | overlap | 1 → 1 |
| stream-interop ingest (control) | 635.130 (621.299–647.528) | 615.077 (608.212–658.658) | 1.033× | overlap | 304.75 → 304.75 |
| generic-interop ingest (control) | 897.146 (886.563–1,058.992) | 895.696 (892.990–1,044.855) | 1.002× | overlap | 384 → 384 |
| generic-interop peak (control) | 29.261 (29.104–30.860) | 29.225 (29.074–29.730) | 1.001× | overlap | 1 → 1 |
| generic-decode ingest (control) | 594.389 (593.081–597.486) | 588.924 (585.834–590.274) | 1.009× | disjoint | 120 → 120 |

Allocation is identical in every cell. The decode-only control does not call `directoryKey`,
and its 0.9% disjoint shift is host drift. **Verdict: killed** (read-aot 0.999× with overlapping
forks, under 3%).

## Callgrind (`callgrind/`, simulation image, Valgrind 3.24 amd64 container)

`valgrind --tool=callgrind --toggle-collect=__codspeed_root_frame__databench`, 256 documents ×
8 iterations, checksums equal (649,814). The main-thread part (`.2`) was split by
`tools/bench/callgrind_split.py`.

| Case / function | Before | After |
|---|---:|---:|
| `json-generic-snapshot` total | 49,547,534 | 47,192,404 (−4.75%) |
| `directoryKey` inclusive (default → override) | 4,648,064 | 2,293,760 |
| `HeapTape::readWord` | 989,184 | 559,104 |
| `HeapTape::textWord` | 921,600 | 0 (not called from lookups) |
| `HeapTape::directoryEntry` | 430,080 | 0 |
| `json-generic-interop-read` total (control) | 31,207,831 | 31,207,752 |

## Correctness

`CompactTapeTest.heapDirectoryKeyMatchesTheElementWiseDefault` (`//tape/tests:jvm`) compares
`HeapTape.directoryKey` with `TapeReader`'s default through an element-wise view of the same tape.
Each outcome is the result or the exception class. The test covers every word index from −1 to
`nodeCount` inclusive, every rank from −1 to members + 2, and 103 probe keys: each tape key, its
one-byte-shorter prefix, a last-byte and a first-byte variant, plus absent, pooled and case
variants. The tapes are two generated directory documents (keys of 0–20 bytes, Latin-1 and
Cyrillic UTF-8 keys, a nested object, and one escaped pool key in the second), their root and
nested-object snapshots, and the existing `document()` fixture with its snapshot. Two mutants
fail it: a wrong high-word mask (`key.length - 7`) and a dropped `rank < 0` test.

Gates on the candidate tree `ef16275` (invocation `7f4f634b`, `--nocache_test_results`):
`//tape/tests:jvm`, `//json/tests:jvm`, `:native_test`, `//benchmarks/comparison:json_native_smoke`
(24 compilations, nine allocation cells), `:json_aot_symbols`, `//json/native:session_replay` and
`//tools/bench:test` all pass. The override and its test are reverted (`dbab33a`), so the
production source is `cd3c193`'s. The test is kept in history in `ada7835` if the override is
revisited for images where the default is polymorphic.

## Reproduction

```sh
bzl build //benchmarks/comparison:json_svm //benchmarks/comparison:json_svm_profile //benchmarks:simulation
# preserve bazel-bin/benchmarks/comparison/json-comparison* and bazel-bin/benchmarks/truffledata-simulation
bzl test //benchmarks/comparison:json_typed_pair \
  --//benchmarks/comparison:json_typed_svm_baseline=//build/fu-before-cd3c193/json:json-comparison \
  --test_env=JSON_PAIR_GROUPS=6 --nocache_test_results
python3 tools/bench/json_pair_summary.py pairs/shard_6/json-typed-pair.json --markdown pairs/paired.md
python3 tools/bench/aot_symbols.py IMAGE --pattern '^(TapeReader|HeapTape|SvmJsonMessage)_directoryKey$'
```
