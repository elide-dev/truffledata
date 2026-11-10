# TruffleData 0.11.0

This release lands the "beat stock on SVM" performance tranche and a set of
correctness fixes, and moves the baseline to Java 25, Myna 0.16.0, Elide GraalVM
25.5.5-elide.28 and Elide toolchain 2026.11.6. Upgrade native attachments and generated
bindings together: Myna 0.16.0 regenerates every ABI fingerprint, and since Myna 0.14.0 Linux
AArch64 has its own ABI class (unsigned C `char`). The bindings jars carry both classes, and each consumer
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
- Myna Truffle cells: Buffa, JSON, YAML and CSV each have an opt-in route that makes
  their native calls through Myna-generated cells
  (`-Dtruffledata.<format>.cells=true`, default off), shipped in separate
  artifacts. With the flag off the default route's code is unchanged. See
  "Truffle cells (opt-in)" below.

## Truffle cells (opt-in)

All four native seams have a Myna Truffle cells route behind a flag that is off by default:
`truffledata.buffa.cells`, `truffledata.json.cells`, `truffledata.yaml.cells` and
`truffledata.csv.cells`. On the JVM the flag is a `static final` read once. In a Native Image the
route is chosen when the image is built, and an image built without the flag contains no cells
code. Setting the flag without the route's Feature or artifact fails the image build (JSON, YAML
and CSV have negative-control builds for this; Buffa's Feature is required by every Buffa image).

| Format | Native calls that go through cells |
|---|---|
| Buffa | the slab producer (`decode_slab`, `decode_slab_small`) |
| JSON | document decode (`decode`, `decode_adopted`, `release`); sessions, streams and typed plans are not routed |
| YAML | the static backend's session decode, shared text, retain, reset, streams and typed decode |
| CSV | the index kernel (`index`, `index_leaf`), `validate_utf8` and `kernel` |

In a Native Image the routes are host code compiled ahead of time, so they call each generated
cell's static `invoke` entry (new in Myna 0.16.0): native carriers and raw addresses, with no
argument array, box, pointer carrier or borrow object. A source scan over all four routes requires
`invoke` and rejects any `execute` call in a Native Image route; for Buffa and JSON a check of the
built flag-on image confirms that no cell `execute` symbol is called. On HotSpot the routes use
`invoke` wherever every buffer is a native address, and the typed `executeInt` where a heap array
is lent (Buffa's small heap entry). Every route has parity tests against the default route, on the
JVM and in a Native Image, comparing results and errors.

@@MEASURED@@

What the routes do not do:

- **No `resource` or `borrow` declarations.** Myna's validator (0.16.0) cannot express them for
  these C signatures: creates return a status and write the handle into a caller-allocated result
  struct, and every buffer import also takes a result or handle pointer. With `invoke` the routes
  need neither: they keep each address valid for the call exactly as the default routes do.
- **On HotSpot, cells cannot hold a caller's memory segment open for the call.** Buffa and JSON
  make the confined-thread and liveness checks themselves before the call; YAML keeps
  caller-segment calls on the default binding. A shared arena closed by another thread during
  a cells call is not caught. HotSpot performance of the cells routes is not a goal of this
  release.

Each route ships in its own artifact: `truffledata-protobuf-buffa-cells`,
`truffledata-json-cells`, `truffledata-yaml-cells` and `truffledata-csv-cells`. They depend on the
new `truffledata-cells`, which carries Myna's cells runtime once; no default artifact depends on
any of them.

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
release-size quota resets. Twenty-nine libraries and the BOM ship with
source/Javadoc jars, Gradle module metadata, POMs, signatures and checksums,
dual licensed MIT or Apache 2.0.

## Deferred

Small strings and TruffleString cells (WS13), quiet-worker timing pairs for CSV
`CsvBatch.row` inlining, JSON typed per-record check
folding, and the CSV register-pressure inner loop are future work. They are
hypotheses, not committed outcomes. Issues #37 and #38 remain open.
