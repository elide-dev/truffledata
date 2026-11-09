# JSON reads and snapshot on SVM — P0.4, P0.3, WS6, WS7, WS8 probe (2026-11-08)

Plan: `docs/superpowers/plans/2026-11-08-beat-stock-svm.md` P0.3, P0.4, WS6, WS7, WS8; rulings
and measurements in `2026-11-08-beat-stock-svm-ledger.md` under "JSON (P0.3/P0.4/WS6/WS7
worker)". SVM / Native Image is the admission target; no JVM timing was taken.

## Source and binaries

- Base: `perf/beat-stock-svm` at `1a23860` (WS1+WS4 and WS10 merged; `json/`, `tape/` and the
  JSON harness unchanged from `main` `2e0df58` apart from the fuzz tooling of PR #39).
- Before-image: the preserved production `9a1dcec` + harness `d3f40cf` `expanded/json-comparison`
  (`build/svm-tranche-baseline/expanded/` in the followup checkout, invocation `8bad3434`),
  staged under this worktree's ignored `build/json-reads-before/` with its manifest;
  sha256 `2a0a294efc4f501f3f5214150e4e0a8a41cbe2c9160354f43af3d367fd1021ea` (59,379,712 B).
  Both never-measured fixes the plan names (ABI 10 stream intent `5002235`, bulk snapshot copy
  `7a0c27f`) are ancestors of `9a1dcec`, so the before-image already contains them.
- Current-source images (`1a23860`, invocation `e10b70ef`), preserved in ignored
  `build/json-after-1a23860/` with `manifest.json`: `json-comparison`
  `b45c164d…bdae` (59,445,248 B), `json-comparison-profile` `5c22321e…d254`,
  `truffledata-simulation` `f76da3c5…21d2`.
- All images: pinned `25.5.5-elide.27` SDK, Linux x86-64, `-O2`, `-march=compatibility`, serial
  GC, no PGO. Pairs: `json_typed_pair` interleaves three fresh forks of each binary per cell on
  one pinned CPU per shard of an RBE worker; medians of fork medians, ranges are fork-median
  minima/maxima. Only x86-64 SVM claims are made; shards may land on different workers (the CPU
  model is recorded per shard), so cells are compared only within a shard.

## P0.4 — current-source recapture (`p04/`)

`//benchmarks/comparison:json_typed_pair` shards 4–7 (invocation `88419cfb`), candidate
`1a23860` against the preserved `9a1dcec` image; `p04/paired.md` has every cell. Shards 4, 5
and 7 ran on AMD EPYC workers, shard 6 (the retained forms) on an Intel Xeon 2.70 GHz worker.

| Cell (shard) | Before ns/doc | Current ns/doc | Ratio | Ranges |
|---|---:|---:|---:|---|
| snapshot ingest (6, Xeon) | 3,049.752 (3,048.831–3,054.005) | 3,019.853 (3,012.064–3,021.873) | 1.010× | disjoint |
| snapshot peak read (6, Xeon) | 80.249 (80.172–80.600) | 80.426 (80.338–81.216) | 0.998× | overlap |
| snapshot read-aot / interpret (6) | 346.581 / 352.441 | 349.414 / 347.206 | 0.992× / 1.015× | overlap |
| stream-interop ingest (6, Xeon) | 1,284.568 (1,281.632–2,172.367) | 1,278.224 (1,272.272–2,189.294) | 1.005× | overlap |
| generic-interop ingest (5, EPYC) | 1,042.455 (1,034.499–1,048.610) | 1,058.558 (1,047.285–1,064.269) | 0.985× | overlap |
| generic-interop peak (5, EPYC) | 34.711 (34.625–34.807) | 35.759 (34.907–36.805) | 0.971× | disjoint |
| typed (`simdjson-typed`) peak (4, EPYC) | 4.376 (4.374–4.381) | 4.383 (4.374–4.404) | 0.998× | overlap |
| specialized peak (4, EPYC) | 2.777 (2.761–2.811) | 2.766 (2.758–2.783) | 1.004× | overlap |
| typed / specialized read-aot (7, EPYC) | 4.655 / 6.651 | 4.647 / 6.794 | 1.002× / 0.979× | overlap |
| typed / specialized interpret (7, EPYC) | 5.231 / 7.954 | 5.225 / 7.573 | 1.001× / 1.050× | overlap / disjoint |
| Gson owned record peak / read-aot / interpret (4, 7) | 1.345 / 1.359 / 1.739 | 1.344 / 1.360 / 1.770 | — | overlap |

Allocation cells are identical between the images (snapshot ingest 1,183.5 B, stream interop
304.75 B, generic interop 384 B, typed ingest 112 B, peak 1.0 B). Reading: the plan's 2,060 ns
snapshot and 1,289 ns stream-interop figures were unclemax absolutes of the pre-fix `ce7399f`
candidate image, not stale code; on these RBE workers the current source is within 1% of the
`9a1dcec` image on every retained cell, and the two fixes are in both. Absolute values differ
by worker (snapshot ingest 3,020 ns on the Xeon shard; the same cell is not on the EPYC shards
here, which is why the WS7 pair below puts generic-interop on the snapshot shard).

## P0.3 — generic ingest split (`p03/`)

`//benchmarks:simulation` at `1a23860` (same build) under `valgrind --tool=callgrind
--toggle-collect=__codspeed_root_frame__databench` (Valgrind 3.24, Debian trixie amd64
container), cases `json-generic-interop-read` (JsonSvm.decode + ten AOT interop reads + close,
256 documents × 8 iterations) and `json-generic-snapshot`; `tools/bench/callgrind_split.py`
then `p03/classify.py` class the self Ir. Instruction counts, not time. The simulation decodes
through `JsonSvm.decode` (a fresh native handle and arenas per call) while the 885 ns cell
decodes through a `JsonSession` (reused parser/arena capacity, `session_retain` publication), so
the native allocation rows here overstate the session cell; the Java-side rows are the same code.

Interop read: 31,201,890 Ir = 15,235 Ir/document (CodSpeed final `31,656,185`: −1.4%).

| Class | Ir/document | Share |
|---|---:|---:|
| native decode (`td_json_decode` inclusive: stage2 walk 5,580 self, stage1 839, `close_directory` 707, unescape 356, arenas/malloc/mutex ≈1,100) | 9,128 | 59.9% |
| Java publication (`JsonSvm.decode` self 254, pinned object push/close 96, `calloc` 71, status/descriptor/message) | 372 | 2.4% |
| Java release (`JsonMessage.close` self 54, native `free` of the result) | 83 | 0.5% |
| directory-rank lookups (`SvmJsonMessage.directoryKey`, 10 per document) | 710 | 4.7% |
| interop export bodies (`TapeValueGen` `readMember` self 1,780, `accepts` 418, `as*`/`is*`/`getArraySize` ≈ 920, `readWord` 156, `aliases` 110, `TapeOwner.check` 27; includes the 10 `TapeValue` view allocations = the 240 B) | 3,647 | 23.9% |
| harness dispatch (`InteropReader.read` self 348, `InteropLibraryGen$CachedDispatch` 828) | 1,176 | 7.7% |
| benchmark loop | 98 | 0.6% |

Java per-document work outside the ten reads (publication + release) is 455 Ir/document, 3.0% of
the case; even with the native `free`/`calloc` it is under 7%. The ten reads are 36.3% (5,533),
of which 4.7% are the directory-rank lookups and 7.7% the harness's interop dispatch. WS9's
trigger (≥200 ns of Java per-document cost excluding the reads, 23% of the 885 ns cell) is not
met on instruction evidence; see the decode-only timing cell below for the time split.

Snapshot: 53,042,752 Ir = 25,900 Ir/document (CodSpeed final `53,497,031`: −0.8%).
`HeapTape.copy` and the hooks it calls are 9,066 Ir/document (35.0%; `HeapTape.copy` self
6,783 = 26.2%, `rawSpan` 860, checked `word` 681, `copyText` 629, `directoryEntry` 210); the
snapshot's own reads add `TapeReader.directoryKey` (the heap default, 1,400 vs 710 native),
`HeapTape.readWord` 483 and `textWord` 450.

### Time split on one host (`ws6/paired.md`, `../json-snapshot-a1d8782/pairs/paired.md`)

The new ingest-only `simdjson-generic-decode` cell (session decode + publication + close, no
read; candidate images only, reported unpaired) beside the ten-read `simdjson-generic-interop`
ingest on the same shard:

| Shard | decode-only ns/doc (B/doc) | ten-read ingest ns/doc (B/doc) | decode share |
|---|---:|---:|---:|
| Intel Xeon 2.70 GHz (`a1a7685`, invocation `96d2bb35`) | 1,013.831 (1,002.226–1,015.945) (120) | 1,672.651 (1,669.879–2,060.456) (384) | 61% |
| AMD EPYC (`a1d8782`, invocation `ef6dde1a`) | 588.795 (580.250–20,863.977; one fork hit a 20 µs outlier) (120) | 862.821 (860.580–887.282) (384) | 68% |

Decode + publication + release is 61–68% of the generic ingest cell in time, matching the
instruction split (native 59.9% + Java publication/release 2.9%). The plan's diagnosis that
native decode is "~100–130 ns of the 885 ns cell" (a stage2 throughput extrapolation) does not
hold for this fixture: the per-document native cost is the majority of the cell, and the Java
publication/release is ~3% of it. WS9's trigger is not met by either measure.

## WS6 — typed reads: hoisting the lifetime checks (`ws6/`, `aot/`)

Probe a (`a1a7685`): `TypedOwner` folded the final `thread` compare and the `open` load into
one mutable `gate` reference (`gate != Thread.currentThread()`), rejection outlined behind a
boundary. Paired against the `9a1dcec` image (invocation `96d2bb35`; shards 4 and 7 on AMD EPYC):

| Cell | Before | Probe a | Ratio | Ranges |
|---|---:|---:|---:|---|
| specialized peak | 2.774 (2.760–2.775) | 3.501 (3.429–3.557) | 0.792× | disjoint |
| typed peak | 4.380 (4.377–4.384) | 4.938 (4.928–4.947) | 0.887× | disjoint |
| specialized read-aot / interpret | 6.685 / 7.783 | 6.371 / 7.261 | 1.049× / 1.072× | disjoint |
| typed read-aot / interpret | 4.751 / 5.189 | 4.537 / 4.932 | 1.047× / 1.052× | overlap / disjoint |
| ingest, Gson controls, allocation cells | — | — | 1.00–1.01×, 0.995–1.006×, identical | overlap |

The `json_native_smoke` compiler trace explains the peak loss: the typed read root was 307
nodes / 8 Ifs after partial evaluation and 250 / 4 after the Graal tier (specialized 240 / 8 →
183 / 4) on the original source, i.e. the three reads' owner checks already fold to one compare
per record in compiled code; with the mutable gate they are 325 / 11 → 268 / 7 (258 / 11 →
201 / 7). Probe b (`4bb83e5`: original compare, rejection outlined) makes the graphs larger
still (352 / 14 → 295 / 10; 285 / 14 → 228 / 10): a boundary call on the rejection branch is a
real branch where the inline throw was a foldable guard. Both probes are reverted (`a1d8782`);
the typed cells of the final pair are at parity (specialized peak 2.752 → 2.751, typed 4.364 →
4.379, read-aot/interpret 1.003×/0.998× and 1.002×/0.978×, the last disjoint by 0.16 ns).

AOT shapes (`aot/`): the probes shrank the AOT bodies (`TypedAccess_longValue` 302 → 226
instructions, `TypedRecord_bits` 255 → 158, `TypedOwner_check` 139 → 43) by outlining the
exception construction, which is where the 5–7% interpreter/read-aot gain came from; the final
image restores the original shapes. The hypothesis "typed reads pay owner/confinement checks per
access" holds for the AOT and interpreter tiers only; at peak the remaining gap to the Gson owned
record (2.75 vs 1.35 ns per record, three reads) is the record → owner → slot-arena load chain
and the per-record schema check, which a check hoist cannot remove. The plan's second step (an
owned heap record for scalar plans) would change what `JsonPlan.decode` returns or make
`TypedRecord` carry inline values: an API/ownership decision, left open for Sam.

## AOT symbol gate (`aot/`, `//benchmarks/comparison:json_aot_symbols`)

Before (`1a23860`) → after (`a1d8782`), x86-64 `-O2`, llvm-objdump local, shape only:
`HeapTape_copy` 815 instructions / 32 calls / 152 B frame → 1,003 / 34 / 168 (both passes now
inline over the heap array; the per-word `word`/`rawSpan` calls are gone) plus the new
`SvmTape_copyWords` 74 / 3; unchanged `TypedRecord_bits` 255 / 12, `TypedRecord_leaf` 103 / 4,
`TypedAccess_longValue` 302 / 15, `TypedOwner_check` 139 / 5, `SvmTypedOwner_bits` 25 / 0,
`SvmJsonMessage_directoryKey` 141 / 0, `TapeOwner_find` 431 / 34,
`JsonFixture$InteropReader_read` 587 / 40, `JsonComparison$Reads_nativeValue` 1,500 / 73.
Budgets are pinned from the after image with ~10% headroom in
`benchmarks/comparison/json_aot_budgets.json`; the gate passes on RBE (invocation `4f393aa3`)
and the before image fails it on the missing `SvmTape_copyWords` symbol, as intended.

## WS8 — number kernel probe (not run)

The probe (test-only ISA variants of the exact production walk whose double parsing calls
`simdjson::westmere`/`haswell::numberparsing`, a canada-style numeric fixture, boundary-number
parity) does not build: the amalgamated `simdjson.h` exposes `numberparsing` only for the
builtin implementation (`fallback` under `-march=compatibility`); the other implementations'
kernels are compiled inside `simdjson.cpp` (`no member named 'haswell' in namespace
'simdjson'`, invocation `4f393aa3`). A probe needs a second amalgamation translation unit built
with another builtin (an ODR hazard inside one test binary) or a vendored instantiation of
`generic/numberparsing.h` under the haswell namespace, which exceeds the half day the plan
allots. The commit is reverted (`a1c47a8`); nothing lands and no numeric claim is made. Note
from the stage2 source: integers of up to 18 digits already take a scalar fast path
(`integer18`); only non-integer tokens reach the pinned number parser.

## Gates and invocations

- JVM: `//tape/tests:jvm`, `//json/tests:jvm`, `//yaml/tests:jvm`, `//tools/bench:test`
  (invocations `f5b37f36`, `968c4d15`, `c65f1a73`).
- SVM: `//json/tests:native_test`, `//yaml/tests:native_test`, `//benchmarks/comparison:json_native_smoke`
  (24 compiled graphs and nine allocation cells unchanged; typed roots 307/250 and 240/183 nodes
  on the final image), `//json/native:session_replay`, `//benchmarks/comparison:json_aot_symbols`
  (`4f393aa3`).
- Pairs: `88419cfb` (P0.4), `96d2bb35` (WS6 probe a), `ef6dde1a` (WS7 / final).
