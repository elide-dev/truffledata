# CSV comparison: fused batches, lean String cells, published native kernel (2026-11-06)

The library's own entry points (`CsvScanner`, `Csv.parse`, `CsvPlan.parse`, `Csv.parse(Path)`)
after the fused index-to-column consumer, the String fast paths, the mapped-cell buffer and the
published native kernel (installed through `CsvIndexing.loadNative`, as applications do),
measured against the same entry points with the index path off, the reference kernel consumers,
the Java competitors and the native references. One `csv_measure` run per binary on shared
BuildBuddy RBE workers.

**Shared-host timings are advisory.** Each figure is one run on shared machines, the median of two
fresh-process forks. They are not confidence intervals. Each Bazel shard ran on its own worker,
so compare only **within one shard** (one dataset at one size, or the sweep), never across shards.
Against the previous run (`csv-b5bd173`), compare the same-shard **ratios** (shipped path against
the kernel path, the best Java competitor and the native probes of its own shard), never the
absolute GB/s: the two runs used different hosts. All 27 shards ran on AMD EPYC (family 26, AVX-512) workers.

## Files

- `raw/csv-<binary>-<group>.json`: every cell's raw samples (five time samples of at least 0.3 s,
  three allocation samples, two forks per cell), fixture SHA-256, checksums, native probe rows
  from the same worker, `/proc/cpuinfo`, SVM binary hash.
- [`csv-comparison.md`](csv-comparison.md): every cell, from
  `python3 benchmarks/comparison/csv_report.py raw/*.json`.
- [`summary.md`](summary.md): the same-shard before/after tables (per-byte path against shipped),
  from `python3 benchmarks/comparison/csv_summary.py raw/*fixture*.json`.
- [`delta.md`](delta.md): the shipped native paths against `csv-b5bd173`, as same-shard ratios, from
  `python3 benchmarks/comparison/csv_delta.py ../csv-b5bd173/raw raw`.

## Engines

As in `csv-b5bd173`: `truffledata-<scanner|batch|plan>` (default `java-swar`), `…-native` (now
`CsvIndexing.install(CsvIndexing.loadNative(…))`: the static backend in the images, the FFM
backend on the JVM), `…-bytes` (index path off), `truffledata-mapped[-native]`, `sweep-*` and
the `kernel-*` reference consumers (`IndexedScanner` plus decoding in the harness, no batch
retained).

## Reproduction

- Source: `720f0c7` (the unescape fix `bf044ec` came after this run; see the caveats)..
- Measurement (all three tests, 27 shards): https://app.buildbuddy.io/invocation/17c0503f-ac37-4e16-ad90-37837389221d.

## Caveats

- **JVM `quoted` strings after `bf044ec`.** This run predates the run-copy unescape. Its JVM
  `quoted` strings cells (1 MiB 0.51, 128 MiB 0.17 GB/s) reflect the old per-byte unescape loop.
  A same-worker probe after the fix (shard `quoted` 128 MiB, two forks,
  https://app.buildbuddy.io/invocation/aae3dc2d-7d59-4ddb-902d-de658b49d3de)
  measured 2.2 GB/s, 0.87x the kernel path.
- **Bimodal JVM `quoted` 1 MiB.** On the JVM, index-path engines (`truffledata-batch`,
  `truffledata-batch-native`, `truffledata-mapped-native`) on `quoted` 1 MiB switch within a fork
  between about 2-4 GB/s and about 0.1 GB/s, in both directions. The same pattern is in the
  `csv-b5bd173` raw samples, so it predates this change. Probes ruled out JIT recompilation
  (no deoptimization or recompilation of CSV methods after warm-up), G1 region size, and the
  native kernel (the java-swar engine shows it too). The cause is open; these cells are not
  representative in either mode.
- **SVM scan.** The shipped scanner's ratio to the kernel path fell on SVM (for example compat
  `numeric` 1 MiB 0.88x to 0.69x), consistently across forks, because `IndexScan.frame` now also
  carries the fused batch path (likely, not yet shown). A probe without the host-inlining annotation gave the same ratios,
  so the annotation is not the cause (https://app.buildbuddy.io/invocation/459fda6b-031e-4efa-b7c8-b62e6023dbbe).
