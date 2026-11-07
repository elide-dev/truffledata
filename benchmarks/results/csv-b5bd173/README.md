# CSV comparison: shipped paths over the structural index (2026-11-06)

The library's own entry points (`CsvScanner`, `Csv.parse`, `CsvPlan.parse`, `Csv.parse(Path)`)
after the structural-index kernels were wired into every parse path, measured against the same
entry points with the index path off, the reference kernel consumers, the Java competitors and
the native references. One `csv_measure` run per binary on shared BuildBuddy RBE workers.

**Shared-host timings are advisory.** Each figure is one run on shared machines, the median of two
fresh-process forks. They are not confidence intervals. Each Bazel shard ran on its own worker,
so compare only **within one shard** (one dataset at one size, or the sweep), never across shards
or across this run and `csv-f5daec1`/`csv-1d2e9ab`. Every worker reported AVX-512, so `native`
picked AVX-512BW. A few cells are fork outliers well below their neighbours (for example JVM
`quoted` 1 MiB `truffledata-mapped-native` at 0.04 GB/s, and JVM `numeric` 128 MiB
`truffledata-batch` at 0.12); the ranges in `csv-comparison.md` show them.

## Files

- `raw/csv-<binary>-<group>.json`: every cell's raw samples (five time samples of at least 0.3 s,
  three allocation samples, two forks per cell), fixture SHA-256, checksums, native probe rows
  from the same worker, `/proc/cpuinfo`, SVM binary hash.
- [`csv-comparison.md`](csv-comparison.md): every cell, from
  `python3 benchmarks/comparison/csv_report.py raw/*.json`.
- [`summary.md`](summary.md): the same-shard before/after tables, from
  `python3 benchmarks/comparison/csv_summary.py raw/*fixture*.json`.

## Engines

- `truffledata-<scanner|batch|plan>`: the shipped entry point with the default kernel
  (`java-swar`).
- `…-native`: the same with `CsvIndexing.install(provider)` of the native kernel
  (`SvmIndexer.provider()` on SVM, `FfmIndexer.Library.load(…).provider()` on the JVM).
- `…-bytes`: the same with the index path off (`CsvIndexing.install(null)`): the library before
  the kernels were wired in. This is the in-shard "before".
- `truffledata-mapped[-native]`: `Csv.parse(Path)` plus `getString`.
- `sweep-*`: the shipped scanner or batch with the index path forced at every size.
- `kernel-*`: the reference consumers (`IndexedScanner` plus decoding inside the harness, no
  batch retained), as in `csv-f5daec1`.

## Reproduction

- Source: `b5bd173` (`perf(csv): reuse one index buffer per thread and index from 16 KiB`).
- Measurement (all three tests, 27 shards, plus both smokes):
  <https://app.buildbuddy.io/invocation/7d9782c3-76b5-4def-800d-251b785aa08a>.
- `csv-1d2e9ab/` is the first run of the same harness. It found the native provider allocating a
  fresh 4 MiB index per 1 MiB parse (207 B/row on `numeric`), fixed in `b5bd173`; it is kept as
  the evidence for that fix and for the 16 KiB threshold
  (<https://app.buildbuddy.io/invocation/d64c9b11-a3d1-4e56-85e2-dc28df03f5f2>).

The write-up is in [`benchmarks/comparison/CSV.md`](../../comparison/CSV.md).
