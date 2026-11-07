# CSV comparison: plain framing loop, unescape run copies, pre-touched JVM heap (2026-11-06)

The library's own entry points (`CsvScanner`, `Csv.parse`, `CsvPlan.parse`, `Csv.parse(Path)`) at
`3f23d0c`: the run-copy unescape (`a19119f`), the scan-only framing loop split from the fused batch
loop (`24ac7fe`), and the harness with a fixed, pre-touched JVM heap and three forks per cell
(`3f23d0c`). Measured against the same entry points with the index path off, the reference
kernel consumers, the Java competitors (FastCSV 4.4.0, Jackson dataformat-csv 2.22.4,
univocity-parsers 2.9.1, Commons CSV 1.14.1) and the native references (Rust `csv` 1.4.0 and
`csv-core` 0.1.13, a C++ consumer of the native index). One `csv_measure`, `csv_measure_v3` and
`csv_measure_jvm` run (27 shards) on shared BuildBuddy RBE workers.

**Shared-host timings are advisory.** Each figure is one run on shared machines, the median of
three fresh-process forks (five time samples of at least 0.3 s each, median per fork). They are not
confidence intervals. Each Bazel shard ran on its own worker, so compare only **within one shard**
(one dataset at one size, or the sweep), never across shards. Against earlier runs, compare
same-shard **ratios** (`delta.md`), never absolute GB/s.

## Files

- `raw/csv-<binary>-<group>.json`: every cell's raw samples (five time samples, three allocation
  samples, three forks per cell), fixture SHA-256, checksums, native probe rows from the same
  worker, `/proc/cpuinfo`, SVM binary hash.
- [`csv-comparison.md`](csv-comparison.md): every cell (`csv_report.py raw/*.json`).
- [`summary.md`](summary.md): per-byte path against shipped paths, per shard
  (`csv_summary.py raw/*fixture*.json`).
- [`delta.md`](delta.md): the shipped native paths against `csv-720f0c7` as same-shard ratios
  (`csv_delta.py ../csv-720f0c7/raw raw`).
- [`standing.md`](standing.md): the shipped paths against every competitor, the kernel consumer
  and every native reference, per workload and runtime (`csv_standing.py raw`).
- `probes/`: the probe runs behind the two fixes (not recorded results; see below).

## Reproduction

- Source: `3f23d0c`.
- Measurement (all three tests, 27 shards):
  https://app.buildbuddy.io/invocation/573b8258-5e72-4ac4-95b6-cb42fa31e201

## What changed since `csv-720f0c7`

- **SVM scan.** `truffledata-scanner-native` is back to 0.87–0.98× the reference kernel consumer
  on SVM (was 0.62–0.94×). Cause: `IndexScan.frame` carried the fused batch path, which Native
  Image (no profiles) compiled into the shared loop with the conversions inlined.
  `probes/frame-disassembly.md` has the code shape (local arm64 image) and
  `probes/scan-{head,split}-*.log` the same-worker RBE A/B (SVM compat, numeric 1 MiB 0.70× →
  0.90×, mixed 1 MiB 0.31× (bimodal) → 0.90×; head: https://app.buildbuddy.io/invocation/99b43bbb-3297-4b48-b49c-f782fb13a3f3,
  split: https://app.buildbuddy.io/invocation/f208b2ba-2598-4a86-85f1-8dea29b348fd).
- **JVM bimodality was the harness on these hosts.** On the RBE guests a first-touch minor page
  fault intermittently costs about 40 µs instead of about 1 µs. G1 heap growth (and humongous
  allocation into never-touched regions) put those faults inside timed samples: slow samples
  took about 7,000 faults and about 95% system time per 300 ms, fast ones under 1,000 faults in
  user time (`probes/jvm-quoted-1MiB-trace-*.log`, from `probes/trace-probe.patch`; THP was
  `madvise`, NUMA balancing and KSM off). Every allocating engine was hit, FastCSV and univocity
  included. `csv_jvm` now runs with `-Xms8g -Xmx8g -XX:+AlwaysPreTouch`
  (pre-touch probe: https://app.buildbuddy.io/invocation/9e529756-bd3e-4d48-b69d-fe929613976b).
  JVM `quoted` 1 MiB strings is now 5.4 GB/s with the native kernel (0.51 in `csv-720f0c7`),
  every fork within 10%.

## Caveats

- **SVM 128 MiB allocating cells remain fault-bound.** The native image maps each large array
  (the 128 MiB input copy of `Csv.parse`, the span columns) fresh, about 40,000 faults per parse
  (`probes/svm-quoted-128MiB-trace.log`), so on a slow-fault host those cells drop several-fold
  mid-run, as SVM compat `quoted` 128 MiB `truffledata-batch-native` (0.21 GB/s, forks 0.06–0.98)
  shows. This is a real per-parse cost whose size depends on the host; the image lists no
  pre-touch option. Read those cells with their fork ranges in `csv-comparison.md`.
- **Noisy neighbours.** Some forks of engines that allocate nothing (for example `kernel-swar`,
  `index-vector`) still slow down mid-run: host contention, not faults. Forks whose fastest and
  slowest samples differ by more than 2.5×: JVM 34 of 804 (`csv-720f0c7`: 54 of 536), SVM compat
  17 of 804 (8 of 536), x86-64-v3 33 of 804 (22 of 536). Three forks let the median outvote one
  such fork.
- **Native probe outliers.** JVM `mixed` 128 MiB `csv-core-scan` (0.19 GB/s) is far below its
  neighbours, so its 12.9× ratio is not meaningful.
- **SVM `escaped` strings.** Relative to Jackson and Rust, SVM `escaped` strings fell against
  `csv-720f0c7` (for example compat 1 MiB 1.40× → 1.05× Jackson) while the ratio to the kernel
  consumer rose; both use `CsvScanner.unescape`, whose run copies (`a19119f`) cost Native Image a
  call per run. `7ba51d1` (after this run) copies short runs inline on Native Image only; its
  same-worker probes are in `probes/unescape/` (escaped 1 MiB vs Jackson, SVM compat / JVM: run
  copy 1.03 / 1.57, selected 1.25 / 1.61). It is not in this run's tables.
