# TruffleData 0.11.0

This release lands the "beat stock on SVM" performance tranche and a set of
correctness fixes, and moves the baseline to Java 25, Myna 0.14.0, Elide GraalVM
25.5.5-elide.28 and Elide toolchain 2026.11.6. Upgrade native attachments and generated
bindings together: Myna 0.14.0 regenerates every ABI fingerprint, and Linux AArch64 now has
its own ABI class (unsigned C `char`). The bindings jars carry both classes, and each consumer
verifies a native library against the class of the platform it runs on.

SVM / GraalVM Native Image is the production performance target; JVM correctness,
API, ownership/lifetime and coverage tests remain required. All figures below are
paired SVM measurements (Linux x86-64, `-O2`, serial GC, no PGO) against the
tranche baseline. Each one was measured in its own preserved image. They are not
a timing capture of the combined release. The evidence, including every rejected
candidate, is in `docs/superpowers/plans/2026-11-08-beat-stock-svm-ledger.md` and
`benchmarks/results/`.

## Performance (SVM)

- **Protobuf slab decode:** full slab decode 1,006 → ≈423–449 ns/row, about 0.52×
  stock protobuf-java's time. Peak slab reads 206.9 → 106.3 ns/row (stock parity).
  The interpreter goes from 351.8 to 217.5 ns/row. Holdout, large and sparse decode
  and resident size drop 49–73%.
- **CSV:** typed numeric readback 0.323 → 0.418 GB/s, mixed 0.484 → 0.571 GB/s,
  copied strings (mixed) 0.274 → 0.325 GB/s.
- **CSV lazy text cells:** reading cells through a reusable `CsvText` takes
  0.65–0.76× the time of copied `String` cells. It allocates 256–899 B/row less.
- **JSON snapshot ingest:** 1,491.9 → 1,297.7 ns/doc.

## API

- CSV: `CsvText` is now a lazy, batch-bound STRING cell. `CsvRow.getText(c)` and
  the reusable `getText(c, into)` read without materializing a `String`. Every
  read checks the batch's lifetime, so a closed mapping fails later reads. Opt in
  with `CsvPlan.withTextCells(true)` to have DataLibrary/interop reads return
  `CsvText`. `String` remains the default, and `CsvPlan.generic(format)` is now
  public. See `docs/CSV.md` "Text cells".
- Protobuf: `SlabRow` and `SlabMessage` are no longer final; generated classes get
  a protected surface. `SlabMessage.produced` is public.
- Buffa: the new opt-in artifact `dev.elide:truffledata-protobuf-buffa-cells`
  routes slab decoding through Myna Truffle cells when
  `-Dtruffledata.buffa.cells=true` is set. With the flag off, the native image's
  symbol table is unchanged. The cells route makes no performance claim. See
  `docs/BUFFA.md`.

## Truffle cells (opt-in)

Only the Buffa slab producer has a Myna 0.14.0 Truffle cells route in this release:
`truffledata.buffa.cells`, a build-time (Native Image) / `static final` (JVM) flag, default off.
Its call sites hold the concrete generated cell and call `executeInt`, so the status result is
not boxed. The JVM route passes its flag-on and flag-off parity tests; the Native Image route
with this change compiles but its `native_test` has not been run, and there is no paired SVM
measurement, so the route makes no performance claim.

JSON and YAML ship no cells route and no flag: their cell bindings are generated and checked
against the production seams in the build, and nothing calls them. CSV has no cells bindings.
Full adoption (routes, resource and borrow declarations, `NativeBytes`, paired SVM measurement
and the per-format default) is follow-up work.

## Fixes

- CSV: `maxRows = Long.MAX_VALUE` no longer overflows and stops reading early (#60).
- CSV: skipped blank and comment lines no longer count toward `maxRecordBytes`
  on the Commons stream path (#53).
- Tape: `HeapTape.toMyna()` preserves per-node metadata (#54).
- Buffa: `BorrowedSlabRow` accessor caches are keyed on layout identity, so
  layouts that share a nested schema no longer share accessors (#55).
- Buffa: FFM slab messages trim spare learned capacity before retaining the
  array, as SVM already did (#56).
- Buffa: nested `Any` contracts are compared structurally across models (#61).
- Protobuf: a closed-enum map entry with repeated value occurrences is rejected if any
  occurrence is out of set, so `encode()`/`decode()` round trips are stable (#57). Known
  deviation: for an out-of-set value followed by a valid one, protobuf-java generated code
  keeps the entry with the last value; TruffleData now moves the whole entry to unknown fields.
- JSON: after `finish()`, a final root scalar that touches the end of the
  buffered input is released (#58).
- YAML: the stream document limit is enforced on the classified extent rather
  than on all pending bytes (#59).

## Distribution

Published on Elide Maven and GitHub. Maven Central stays deferred under
[#28](https://github.com/elide-dev/truffledata/issues/28) until the organization's
release-size quota resets. Twenty-five libraries and the BOM ship with
source/Javadoc jars, Gradle module metadata, POMs, signatures and checksums,
dual licensed MIT or Apache 2.0.

## Deferred

Small strings and TruffleString cells (WS13), quiet-worker timing pairs for the
Buffa cells route and for CSV `CsvBatch.row` inlining, JSON typed per-record check
folding, and the CSV register-pressure inner loop are future work. They are
hypotheses, not committed outcomes. Issues #37 and #38 remain open.
