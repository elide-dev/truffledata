# JSON performance probes at `861dff1` (2026-11-06)

Raw output of two temporary probes used to write
`docs/superpowers/plans/2026-11-06-json-performance.md`. Both ran once on shared
BuildBuddy RBE workers (Intel Xeon @ 2.70 GHz, 5 vCPU, simdjson active implementation
`icelake`, AVX-512). **All timings are advisory:** one worker, one run, best-of-7 rounds
of a tight loop, no confidence intervals, no cross-host comparability. The worker that ran
`native.txt` was about 1.7× slower than the worker behind the earlier probe quoted in the
plan's brief (`D0 current document` 2,235 ns here against 1,308 ns there), so compare rows
within one file, never absolute values across files or hosts. These numbers support no
speedup claim; the plan uses them only as ratios and as hypotheses for the comparison
harness to test.

## Files

- `perf_probe_test.cc`: the C++ probe (a temporary `cc_test` next to `json/native/BUILD.bazel`,
  removed before commit). Variants: stage 1 only, bare On-Demand walks with and without a
  `std::pmr::unordered_set` per object, a lean walk (`escaped_key`, raw tokens with a
  control/backslash scan, `get_number` before any grammar check, linear/open-addressing
  duplicate table), the current `truffledata::json::document` over the default resource and
  over a monotonic arena, the lean walk into Myna's `value_builder` in an arena with and
  without lexical spans, DOM parse alone and DOM plus builder walk, the C ABI, NDJSON through
  per-document `iterate` versus `iterate_many`, `at_pointer` on the 228 KB array, a frame
  index built by `raw_json()` skipping, and materializing one element from a frame.
  The `A1` line timed with clock reads inside the loop and is not comparable; ignore it.
- `native.txt`: the measurement lines; `native-raw.log`: the full Bazel/test log.
  Invocation: <https://app.buildbuddy.io/invocation/91e9e648-418f-48be-bb63-b72ce5cdbd82>.
- `PerfProbe.java`: the SVM probe (a temporary `native_image` + `sh_test` in `json/tests`,
  removed before commit): `JsonSvm.decode`+close for the 210 B fixture document, `{}` and the
  228 KB array; the raw generated ABI with the result struct and pin hoisted, to separate
  transitions and native work from Java owner construction; leaf call, `calloc`/`free` and
  `PinnedObject` costs; AOT member reads through `member(Utf8Key)`, uncached interop,
  `ValueTape.find` directly and a position-hint prototype; `stringValue`,
  `asTruffleString`, counting/`String`/`JavaSink` exports and `snapshot()`; allocation per
  operation from the thread allocation counter. Not Truffle-compiled: these are AOT
  numbers, so compiled-read gates in `CompiledReads` and the comparison harness remain the
  only evidence for compiled behaviour.
- `svm.txt`: its measurement lines; `svm-raw.log`: the full log.
  Invocation: <https://app.buildbuddy.io/invocation/881c529f-503c-46ba-9cd8-d0faeddb64f8>.
  Headline: `JsonSvm.decode`+close of the 210 B document costs 2,341 ns / 296 B against
  2,159 ns / 0 B for the raw ABI on the same worker, so the Java owner layer is ≈ 180 ns;
  the brief's "unattributed ~1.6 µs" was a cross-host subtraction artifact.

## Fixture

The 210 B document is `JsonFixture.document(1)` (nine members: I64, short string, F64,
boolean, string array, nested object, integer array, a 60-byte escaped string, null). The
medium input is a 20-element array of such documents (4,330 B); the big input is a
1,000-element array (228,485 B); the NDJSON input is the same 1,000 documents separated by
newlines. Checksums were not asserted; the probes consumed every value and folded results
into a volatile sink to prevent elision.

## Caveats specific to these probes

- The C++ probe's "lean" variants are standalone copies, not the production decoder; they
  show what public simdjson APIs allow, not what `decoder.h` does today.
- The monotonic arena in the probe wastes the old block on every vector growth; a presized
  writer (plan Task A2) would allocate less and is expected to be faster than `D2`.
- `SIMDJSON_THREADS_ENABLED` is not defined in this build, so `iterate_many` ran
  single-threaded, which is also the production configuration.
- Java allocation figures exclude native memory entirely.
