# CSV comparison

The comparison cell for the CSV backend (plan Task 7, expanded). It measures the structural-index
kernels (`csv/native`, `csv/kernel`) and the current pure-Java scanner against the Java CSV field
and native references, on identical bytes and on the same worker.

Raw samples, hosts, binary hashes and caveats are in [`csv-b5bd173/`](../results/csv-b5bd173/README.md) (shipped paths) and
[`benchmarks/results/csv-f5daec1/`](../results/csv-f5daec1/README.md). **Shared-host timings are
advisory. Compare only within one row**: each row is one dataset at one size, measured on one
worker.

## Method

- **Fixtures** (`CsvFixture`) are deterministic, with a header and LF line endings. They are
  generated at 1 MiB and 128 MiB, with no downloads and no checked-in data:
  - `numeric`: 8 short numeric and boolean columns, about 5.6 B per field.
  - `mixed`: 24 columns of integers, decimals, booleans, dates, short and sometimes quoted
    text, an empty column, and non-ASCII text.
  - `quoted`: long quoted text (200–1,200 B) with delimiters, newlines and rare doubled quotes.
  - `escaped`: escape-heavy dialogue and embedded JSON.
- **Workloads** (stage `ingest`; the whole fixture is held in a `byte[]`):
  - `index`: structural index only.
  - `scan`: record and field boundaries, with no decoding.
  - `strings`: one `java.lang.String` per data cell.
  - `typed`: INT64, FLOAT64 and BOOLEAN columns decoded and summed.
  - `typed-reads`: 32 retained rows read through `ReadLong`, `ReadDouble` and
    `ReadBooleanNode` (`read-aot`, `interpret` and `peak`, as in the JSON comparison).
- **Engines**:
  - truffledata:
    - `truffledata-scanner`: the current `CsvScanner`.
    - `truffledata-batch`: `Csv.parse` plus `getString`.
    - `truffledata-plan`: `CsvPlan` plus typed getters.
    - `kernel-<k>`: `IndexedScanner` over kernel `k`, falling back to the strict scanner for
      flagged records. Its `strings` and `typed` paths validate UTF-8 strictly first.
  - Java competitors, benchmark-only Maven dependencies:
    - FastCSV 4.4.0, through its callback handler for `scan`.
    - Jackson dataformat-csv 2.22.4, as a token stream.
    - univocity-parsers 2.9.1, with trimming off and empty values kept as `""`.
    - Commons CSV 1.14.1.
  - Native references, from the same worker and on the same SHA-256 bytes:
    - `native-indexed-scan`: the csv/native kernel with a C++ consumer, no Java involved.
    - `csv-core-scan`: Rust `csv-core` 0.1.13 `read_record`.
    - `csv-byte-record`, `csv-string-record` and `csv-typed`: Rust `csv` 1.4.0.
- **Runtimes**:
  - SVM `-march=compatibility`, the repository's image setting.
  - SVM `-march=x86-64-v3`.
  - GraalVM 25 JVM.
- **Rigor**: every engine in a workload must produce the same checksum, including the Rust
  `csv-typed` row. Each cell is measured in two fresh-process forks, each with five time samples
  and three allocation samples.
- **Not included**:
  - opencsv: popular, but not a throughput leader, and it would add four Commons dependencies.
  - SimpleFlatMapper: unmaintained.
  - zsv: would need a third-party C build. The csv/native kernel is itself the simdcsv design
    (simdjson stage 1), so `native-indexed-scan` stands in for that class.

## Fused batches and published native kernel (csv-720f0c7)

The latest run measures the shipped paths after fusing batch building into the index scan, the
`String` fast paths, the mapped-cell buffer and the published native kernel (installed through
`CsvIndexing.loadNative`, as applications do). Raw samples, the full tables and caveats are in
[`csv-720f0c7/`](../results/csv-720f0c7/README.md); [`delta.md`](../results/csv-720f0c7/delta.md)
gives every row against `csv-b5bd173` as same-shard ratios (`csv_delta.py`), since the two runs
used different hosts.

| Shipped native path | × kernel consumer, before → after | Rows ahead of best Java, before → after | × Rust/C++ reference (after) |
|---|---|---|---|
| Typed (`CsvPlan`) | 0.38–0.64 → **0.43–0.78** | 12/12 → **12/12** (1.2–3.2×) | `csv-typed` 0.58–1.80 (ahead in 7 of 12) |
| Strings (`Csv.parse` + `getString`) | 0.26–0.87 → 0.11–0.93 | 11/24 → **13/24** | `csv-string-record` 0.22–1.50 |
| Scan (`CsvScanner`) | 0.83–2.17 → 0.62–1.32 | 24/24 → **24/24** (1.7–19×) | `csv-core-scan` 0.85–9.9 (ahead in 21 of 24) |

- Typed improved most on JVM `numeric` 1 MiB (0.38× → 0.78× of the kernel path, 0.64× → 1.15×
  Rust `csv`) and SVM `numeric` 128 MiB (0.38× → 0.59×).
- The low end of strings is JVM `quoted`: the old per-byte unescape loop (fixed after this run in
  `bf044ec`; probe 0.87× the kernel at 128 MiB) and a pre-existing bimodal slowdown at 1 MiB.
- The SVM scan ratios fell from about 0.9× to about 0.7× of the kernel consumer since the fused
  path; it is open (see the results README).

## Shipped paths over the structural index (csv-b5bd173)

Every parse path now frames records over the structural index. These tables measure the
library's own entry points, all within one shard per row
([`benchmarks/results/csv-b5bd173/`](../results/csv-b5bd173/README.md),
`csv_summary.py`):

- **Before (per-byte)**: the same entry point with the index path off, which is the library
  before this change.
- **Shipped (java-swar)**: the default kernel, which is what the published pure-Java jar runs.
- **Shipped (native)**: the native AVX-512 kernel installed through `CsvIndexing`.
- **Best kernel path**: the reference consumers (no batch retained), as before.

Shared-host timings are advisory; a few cells are fork outliers (see the results README).

### scan (GB/s): `CsvScanner`

| Runtime | Dataset | Size | Before (per-byte) | Shipped (java-swar) | Shipped (native) | Best kernel path | Best Java competitor | csv-core-scan | native-indexed-scan |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| SVM (compat) | escaped | 1 MiB | 0.47 | 1.35 | **3.65** | 3.79 (kernel-native) | 0.20 (jackson) | 0.48 | 4.22 |
| SVM (compat) | escaped | 128 MiB | 1.05 | 2.29 | **5.41** | 5.35 (kernel-native-small) | 0.39 (fastcsv) | 1.04 | 6.40 |
| SVM (compat) | mixed | 1 MiB | 0.82 | 1.12 | **1.95** | 1.70 (kernel-native-small) | 0.48 (fastcsv) | 1.03 | 2.75 |
| SVM (compat) | mixed | 128 MiB | 0.83 | 1.06 | **1.74** | 1.81 (kernel-native) | 0.46 (fastcsv) | 1.01 | 2.19 |
| SVM (compat) | numeric | 1 MiB | 0.77 | 0.97 | **1.47** | 1.68 (kernel-native) | 0.49 (fastcsv) | 1.08 | 2.46 |
| SVM (compat) | numeric | 128 MiB | 0.40 | 0.55 | **0.67** | 0.78 (kernel-native-small) | 0.28 (fastcsv) | 0.48 | 0.56 |
| SVM (compat) | quoted | 1 MiB | 1.58 | 1.68 | **8.13** | 9.00 (kernel-native-small) | 0.54 (fastcsv) | 0.85 | 9.91 |
| SVM (compat) | quoted | 128 MiB | 1.54 | 1.59 | **4.52** | 5.15 (kernel-native) | 0.47 (fastcsv) | 0.85 | 5.32 |
| SVM (x86-64-v3) | escaped | 1 MiB | 0.88 | 1.88 | **6.14** | 6.81 (kernel-native-small) | 0.36 (fastcsv) | 0.80 | 7.64 |
| SVM (x86-64-v3) | escaped | 128 MiB | 0.65 | 1.24 | **3.15** | 3.33 (kernel-native) | 0.25 (fastcsv) | 0.57 | 3.82 |
| SVM (x86-64-v3) | mixed | 1 MiB | 0.83 | 1.15 | **1.96** | 2.12 (kernel-native-small) | 0.41 (fastcsv) | 0.96 | 2.68 |
| SVM (x86-64-v3) | mixed | 128 MiB | 0.82 | 1.07 | **1.75** | 1.75 (kernel-native) | 0.40 (fastcsv) | 1.03 | 2.26 |
| SVM (x86-64-v3) | numeric | 1 MiB | 0.75 | 0.95 | **1.49** | 1.65 (kernel-native-small) | 0.58 (fastcsv) | 0.95 | 2.38 |
| SVM (x86-64-v3) | numeric | 128 MiB | 0.76 | 1.02 | **1.43** | 1.72 (kernel-native) | 0.58 (fastcsv) | 1.12 | 2.07 |
| SVM (x86-64-v3) | quoted | 1 MiB | 1.57 | 1.75 | **8.23** | 9.01 (kernel-native) | 0.93 (fastcsv) | 0.84 | 9.94 |
| SVM (x86-64-v3) | quoted | 128 MiB | 2.65 | 2.20 | **8.67** | 9.04 (kernel-native) | 1.19 (fastcsv) | 1.40 | 10.32 |
| JVM | escaped | 1 MiB | 1.09 | 2.53 | **7.65** | 7.17 (kernel-vector) | 0.42 (fastcsv) | 0.83 | 8.19 |
| JVM | escaped | 128 MiB | 0.66 | 1.58 | **3.15** | 3.29 (kernel-vector) | 0.32 (jackson) | 0.48 | 3.10 |
| JVM | mixed | 1 MiB | 0.59 | 1.02 | **1.66** | 1.69 (kernel-native-small) | 0.39 (fastcsv) | 0.57 | 1.48 |
| JVM | mixed | 128 MiB | 0.59 | 0.99 | **1.49** | 1.65 (kernel-native-small) | 0.34 (fastcsv) | 0.61 | 1.41 |
| JVM | numeric | 1 MiB | 1.02 | 1.52 | **2.88** | 3.00 (kernel-native-small) | 1.10 (fastcsv) | 1.12 | 2.50 |
| JVM | numeric | 128 MiB | 1.01 | 1.53 | **2.49** | 3.32 (kernel-native-small) | 0.87 (fastcsv) | 1.15 | 2.48 |
| JVM | quoted | 1 MiB | 4.38 | 2.74 | **13.85** | 13.19 (kernel-native-small) | 1.83 (fastcsv) | 1.49 | 14.46 |
| JVM | quoted | 128 MiB | 2.59 | 1.62 | **6.06** | 7.06 (kernel-native-small) | 1.27 (fastcsv) | 0.93 | 7.30 |

### strings (GB/s): `Csv.parse` plus `getString` per cell

| Runtime | Dataset | Size | Before (per-byte) | Shipped (java-swar) | Shipped (native) | Mapped (native) | Best kernel path | Best Java competitor | csv-string-record |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| SVM (compat) | escaped | 1 MiB | 0.18 | 0.22 | **0.24** | 0.17 | 0.34 (kernel-native) | 0.17 (jackson) | 0.43 |
| SVM (compat) | escaped | 128 MiB | 0.22 | 0.40 | **0.44** | 0.40 | 0.63 (kernel-native) | 0.38 (jackson) | 0.80 |
| SVM (compat) | mixed | 1 MiB | 0.20 | 0.22 | **0.24** | 0.18 | 0.41 (kernel-native) | 0.27 (fastcsv) | 0.44 |
| SVM (compat) | mixed | 128 MiB | 0.19 | 0.19 | **0.21** | 0.18 | 0.34 (kernel-swar) | 0.30 (fastcsv) | 0.46 |
| SVM (compat) | numeric | 1 MiB | 0.18 | 0.18 | **0.20** | 0.15 | 0.43 (kernel-native) | 0.26 (fastcsv) | 0.75 |
| SVM (compat) | numeric | 128 MiB | 0.09 | 0.10 | **0.09** | 0.07 | 0.21 (kernel-native) | 0.13 (fastcsv) | 0.25 |
| SVM (compat) | quoted | 1 MiB | 0.50 | 0.49 | **0.68** | 0.42 | 0.91 (kernel-native) | 0.38 (fastcsv) | 0.78 |
| SVM (compat) | quoted | 128 MiB | 0.43 | 0.43 | **0.56** | 0.62 | 0.82 (kernel-native) | 0.36 (fastcsv) | 0.74 |
| SVM (x86-64-v3) | escaped | 1 MiB | 0.32 | 0.41 | **0.48** | 0.37 | 0.67 (kernel-native) | 0.34 (jackson) | 0.56 |
| SVM (x86-64-v3) | escaped | 128 MiB | 0.15 | 0.21 | **0.33** | 0.29 | 0.44 (kernel-native) | 0.22 (jackson) | 0.48 |
| SVM (x86-64-v3) | mixed | 1 MiB | 0.22 | 0.20 | **0.26** | 0.18 | 0.45 (kernel-native) | 0.31 (fastcsv) | 0.46 |
| SVM (x86-64-v3) | mixed | 128 MiB | 0.19 | 0.19 | **0.21** | 0.18 | 0.43 (kernel-native) | 0.30 (fastcsv) | 0.47 |
| SVM (x86-64-v3) | numeric | 1 MiB | 0.17 | 0.19 | **0.20** | 0.14 | 0.42 (kernel-native) | 0.32 (fastcsv) | 0.72 |
| SVM (x86-64-v3) | numeric | 128 MiB | 0.16 | 0.18 | **0.17** | 0.14 | 0.41 (kernel-native) | 0.31 (fastcsv) | 0.68 |
| SVM (x86-64-v3) | quoted | 1 MiB | 0.61 | 0.61 | **0.93** | 0.53 | 1.85 (kernel-native) | 0.73 (fastcsv) | 0.79 |
| SVM (x86-64-v3) | quoted | 128 MiB | 0.77 | 0.85 | **0.72** | 0.91 | 2.73 (kernel-native) | 0.97 (fastcsv) | 1.01 |
| JVM | escaped | 1 MiB | 0.29 | 0.34 | **0.28** | 0.34 | 0.44 (kernel-native) | 0.41 (jackson) | 0.61 |
| JVM | escaped | 128 MiB | 0.20 | 0.25 | **0.27** | 0.27 | 0.31 (kernel-native) | 0.33 (jackson) | 0.42 |
| JVM | mixed | 1 MiB | 0.24 | 0.28 | **0.36** | 0.20 | 0.49 (kernel-native) | 0.22 (fastcsv) | 0.33 |
| JVM | mixed | 128 MiB | 0.22 | 0.23 | **0.25** | 0.20 | 0.39 (kernel-swar) | 0.24 (fastcsv) | 0.32 |
| JVM | numeric | 1 MiB | 0.33 | 0.49 | **0.62** | 0.37 | 0.89 (kernel-native) | 0.20 (fastcsv) | 0.77 |
| JVM | numeric | 128 MiB | 0.43 | 0.12 | **0.57** | 0.37 | 0.88 (kernel-native) | 0.46 (fastcsv) | 0.81 |
| JVM | quoted | 1 MiB | 0.88 | 0.84 | **0.71** | 0.04 | 1.56 (kernel-swar) | 1.26 (fastcsv) | 1.04 |
| JVM | quoted | 128 MiB | 0.67 | 0.53 | **0.86** | 0.76 | 1.56 (kernel-native) | 0.90 (fastcsv) | 0.77 |

### typed (GB/s): `CsvPlan.parse` plus typed getters

| Runtime | Dataset | Size | Before (per-byte) | Shipped (java-swar) | Shipped (native) | Best kernel path | Best Java competitor | csv-typed |
|---|---|---|---:|---:|---:|---:|---:|---:|
| SVM (compat) | mixed | 1 MiB | 0.33 | 0.35 | **0.44** | 0.81 (kernel-native) | 0.17 (fastcsv) | 0.37 |
| SVM (compat) | mixed | 128 MiB | 0.29 | 0.34 | **0.41** | 0.74 (kernel-native) | 0.17 (fastcsv) | 0.37 |
| SVM (compat) | numeric | 1 MiB | 0.21 | 0.23 | **0.24** | 0.44 (kernel-native) | 0.11 (fastcsv) | 0.46 |
| SVM (compat) | numeric | 128 MiB | 0.07 | 0.09 | **0.09** | 0.24 (kernel-native) | 0.05 (univocity) | 0.15 |
| SVM (x86-64-v3) | mixed | 1 MiB | 0.34 | 0.37 | **0.46** | 0.85 (kernel-native) | 0.15 (univocity) | 0.37 |
| SVM (x86-64-v3) | mixed | 128 MiB | 0.32 | 0.35 | **0.42** | 0.78 (kernel-native) | 0.16 (fastcsv) | 0.40 |
| SVM (x86-64-v3) | numeric | 1 MiB | 0.22 | 0.23 | **0.26** | 0.44 (kernel-native) | 0.11 (fastcsv) | 0.47 |
| SVM (x86-64-v3) | numeric | 128 MiB | 0.15 | 0.20 | **0.22** | 0.48 (kernel-native) | 0.11 (fastcsv) | 0.44 |
| JVM | mixed | 1 MiB | 0.27 | 0.34 | **0.41** | 0.65 (kernel-native) | 0.17 (fastcsv) | 0.26 |
| JVM | mixed | 128 MiB | 0.24 | 0.32 | **0.32** | 0.59 (kernel-native) | 0.18 (fastcsv) | 0.25 |
| JVM | numeric | 1 MiB | 0.22 | 0.21 | **0.26** | 0.69 (kernel-native) | 0.21 (univocity) | 0.40 |
| JVM | numeric | 128 MiB | 0.11 | 0.28 | **0.30** | 0.70 (kernel-native) | 0.24 (fastcsv) | 0.48 |

### Where the shipped library stands

- **Scan: first.** With the native kernel the shipped scanner runs at 1.7–7.8× its per-byte
  self, at 0.75–1.15× of the reference kernel path, 2.4–18× the best Java competitor, and ahead
  of Rust `csv-core` in all 24 rows (1.3–9.8×). Against the C++ consumer of the same index
  (`native-indexed-scan`) it reaches 60–120%. With the default `java-swar` kernel it is
  0.63–2.9× its per-byte self: faster in 21 of 24 rows, slower on JVM `quoted` and SVM v3
  `quoted` 128 MiB, where the per-byte loop already runs at 2.6–4.4 GB/s.
- **Strings: mixed.** Native: 0.8–2.2× the per-byte path (faster in 20 of 24 rows), ahead of the
  best Java competitor in only 11 of 24 rows (behind FastCSV on every SVM `numeric` and `mixed`
  row, behind Jackson/FastCSV on every JVM `escaped` and `quoted` row, and on SVM v3 `quoted`
  128 MiB), and 0.25–1.2× of Rust's
  reused `StringRecord` (not like for like: one Java `String` per cell). It stays at 0.26–0.87×
  of the reference kernel path: the batch retains spans and every read goes through `CsvRow` and
  `getString`, where the kernel path makes each `String` straight from the index.
- **Typed: first among Java parsers.** Native: 1.1–2.7× the per-byte path, 1.2–3.1× the best
  Java competitor in every row, ahead of Rust `csv` on `mixed` (1.05–1.6×) and behind it on
  `numeric` (0.50–0.65×). It stays at 0.38–0.63× of the reference kernel path: the shipped path
  builds and retains a columnar batch (input copy, column arrays) and reads it back through
  `CsvRow`.
- **Mapped input** (`Csv.parse(Path)`, strings): 0.6–1.3× the heap path outside one outlier
  fork (JVM `quoted` 1 MiB, 0.04 GB/s). Each `getString` copies
  the cell out of the mapping before decoding, and each parse maps the file afresh; the window
  copy is not the main cost (locally, mapped parse without reads is 15–37% below heap parse at
  16 MiB, mostly page faults of the fresh mapping).

### Kernel selection (shipped)

| Runtime | Kernel | Below `MIN_INDEX_BYTES` (16 KiB) | Source |
| --- | --- | --- | --- |
| SVM (`-march=compatibility`), native library linked | `SvmIndexer.provider()`: AVX-512BW/AVX2/SWAR by CPU, leaf calls up to 1 MiB, 64 Ki entries per call, simdutf | per-byte scanner | fixture rows above; sweep in `csv-1d2e9ab` |
| SVM (`-march=x86-64-v3`), native library linked | as above | per-byte scanner | as above |
| JVM, `csv_shared.so` available | `FfmIndexer.Library.load(path).provider()`: critical heap calls, global-arena library, 64 Ki entries per call, simdutf | per-byte scanner | as above |
| JVM without the library | `java-swar` (default); `VectorIndexer.provider()` is the measured JVM alternative (`kernel-vector` led two JVM `escaped` rows) | per-byte scanner | as above |
| SVM without the library | `java-swar` (default; the Vector API is not intrinsified in portable images) | per-byte scanner | `csv-f5daec1` |
| Formats with an escape other than the quote, a comment marker, a multi-byte delimiter, `ignoreSurroundingSpaces`, Commons `trim` or `trailingDelimiter` | per-byte (Commons lexer) | — | not representable in the index |

The 16 KiB threshold comes from the `csv-1d2e9ab` sweep: on SVM the per-byte scanner wins at
256 B–4 KiB in most rows and the native index wins every row from 16 KiB.

## Kernel-only results (csv-f5daec1; GB/s of input; median of fork medians)

"Best kernel path" is the fastest `kernel-*` engine. "Current library" was the shipped path
before this change: `CsvScanner`, `Csv.parse` or `CsvPlan` with the per-byte scanners.

### scan (GB/s)

| Runtime | Dataset | Size | Best kernel path | Current library | Best Java competitor | csv-core-scan | native-indexed-scan |
|---|---|---|---:|---:|---:|---:|---:|
| SVM (compat) | escaped | 1 MiB | **3.31** (kernel-native-small) | 0.43 | 0.16 (jackson) | 0.38 | 3.82 |
| SVM (compat) | escaped | 128 MiB | **4.76** (kernel-native-small) | 0.78 | 0.31 (jackson) | 0.98 | 6.09 |
| SVM (compat) | mixed | 1 MiB | **1.21** (kernel-native-small) | 0.45 | 0.24 (fastcsv) | 0.60 | 1.53 |
| SVM (compat) | mixed | 128 MiB | **1.66** (kernel-native-small) | 0.66 | 0.32 (fastcsv) | 1.05 | 2.20 |
| SVM (compat) | numeric | 1 MiB | **1.34** (kernel-native) | 0.70 | 0.38 (fastcsv) | 1.02 | 2.02 |
| SVM (compat) | numeric | 128 MiB | **0.68** (kernel-native-small) | 0.37 | 0.26 (fastcsv) | 0.53 | 0.79 |
| SVM (compat) | quoted | 1 MiB | **7.19** (kernel-native-small) | 0.64 | 0.42 (fastcsv) | 0.82 | 9.10 |
| SVM (compat) | quoted | 128 MiB | **4.97** (kernel-native-small) | 0.74 | 0.51 (fastcsv) | 0.80 | 5.28 |
| SVM (x86-64-v3) | escaped | 1 MiB | **6.33** (kernel-native) | 0.63 | 0.34 (fastcsv) | 0.79 | 8.20 |
| SVM (x86-64-v3) | escaped | 128 MiB | **3.39** (kernel-native-small) | 0.64 | 0.25 (fastcsv) | 0.55 | 3.60 |
| SVM (x86-64-v3) | mixed | 1 MiB | **1.96** (kernel-native-small) | 0.88 | 0.32 (fastcsv) | 1.00 | 2.73 |
| SVM (x86-64-v3) | mixed | 128 MiB | **1.21** (kernel-native-small) | 0.53 | 0.19 (fastcsv) | 0.61 | 1.42 |
| SVM (x86-64-v3) | numeric | 1 MiB | **1.39** (kernel-native) | 0.83 | 0.40 (fastcsv) | 1.11 | 2.22 |
| SVM (x86-64-v3) | numeric | 128 MiB | **0.86** (kernel-vector) | 0.44 | 0.36 (fastcsv) | 0.69 | 1.43 |
| SVM (x86-64-v3) | quoted | 1 MiB | **8.99** (kernel-native-small) | 0.94 | 0.91 (fastcsv) | 0.85 | 9.76 |
| SVM (x86-64-v3) | quoted | 128 MiB | **9.13** (kernel-native-small) | 1.66 | 1.15 (fastcsv) | 1.39 | 9.19 |
| JVM | escaped | 1 MiB | **6.97** (kernel-native-small) | 0.99 | 0.37 (jackson) | 0.75 | 6.42 |
| JVM | escaped | 128 MiB | **2.84** (kernel-native-small) | 0.66 | 0.32 (jackson) | 0.42 | 2.80 |
| JVM | mixed | 1 MiB | **1.46** (kernel-native-small) | 0.47 | 0.36 (fastcsv) | 0.61 | 1.47 |
| JVM | mixed | 128 MiB | **1.61** (kernel-native-small) | 0.52 | 0.37 (fastcsv) | 0.61 | 1.01 |
| JVM | numeric | 1 MiB | **2.64** (kernel-native-small) | 0.87 | 0.50 (fastcsv) | 0.87 | 2.08 |
| JVM | numeric | 128 MiB | **2.60** (kernel-vector) | 0.85 | 0.75 (fastcsv) | 1.10 | 2.53 |
| JVM | quoted | 1 MiB | **13.63** (kernel-native-small) | 2.78 | 1.10 (fastcsv) | 1.24 | 9.62 |
| JVM | quoted | 128 MiB | **4.80** (kernel-native-small) | 1.39 | 1.13 (fastcsv) | 0.83 | 5.17 |

### strings (GB/s)

| Runtime | Dataset | Size | Best kernel path | Current library | Best Java competitor | csv-string-record |
|---|---|---|---:|---:|---:|---:|
| SVM (compat) | escaped | 1 MiB | **0.26** (kernel-native) | 0.16 | 0.18 (jackson) | 0.36 |
| SVM (compat) | escaped | 128 MiB | **0.45** (kernel-native) | 0.25 | 0.25 (fastcsv) | 0.73 |
| SVM (compat) | mixed | 1 MiB | **0.26** (kernel-native) | 0.12 | 0.12 (jackson) | 0.27 |
| SVM (compat) | mixed | 128 MiB | **0.37** (kernel-native) | 0.11 | 0.22 (fastcsv) | 0.47 |
| SVM (compat) | numeric | 1 MiB | **0.29** (kernel-swar) | 0.14 | 0.21 (fastcsv) | 0.68 |
| SVM (compat) | numeric | 128 MiB | **0.20** (kernel-swar) | 0.09 | 0.14 (fastcsv) | 0.39 |
| SVM (compat) | quoted | 1 MiB | **0.84** (kernel-native) | 0.30 | 0.35 (fastcsv) | 0.57 |
| SVM (compat) | quoted | 128 MiB | **0.92** (kernel-native) | 0.34 | 0.35 (fastcsv) | 0.73 |
| SVM (x86-64-v3) | escaped | 1 MiB | **0.59** (kernel-native) | 0.30 | 0.31 (fastcsv) | 0.60 |
| SVM (x86-64-v3) | escaped | 128 MiB | **0.39** (kernel-native) | 0.18 | 0.21 (jackson) | 0.46 |
| SVM (x86-64-v3) | mixed | 1 MiB | **0.44** (kernel-native) | 0.20 | 0.25 (fastcsv) | 0.41 |
| SVM (x86-64-v3) | mixed | 128 MiB | **0.30** (kernel-native) | 0.10 | 0.18 (fastcsv) | 0.32 |
| SVM (x86-64-v3) | numeric | 1 MiB | **0.35** (kernel-native) | 0.17 | 0.21 (fastcsv) | 0.75 |
| SVM (x86-64-v3) | numeric | 128 MiB | **0.24** (kernel-native) | 0.09 | 0.19 (fastcsv) | 0.53 |
| SVM (x86-64-v3) | quoted | 1 MiB | **1.46** (kernel-native) | 0.32 | 0.72 (fastcsv) | 0.79 |
| SVM (x86-64-v3) | quoted | 128 MiB | **2.73** (kernel-native) | 0.49 | 0.96 (fastcsv) | 0.86 |
| JVM | escaped | 1 MiB | **0.39** (kernel-native) | 0.26 | 0.33 (jackson) | 0.48 |
| JVM | escaped | 128 MiB | **0.31** (kernel-native) | 0.15 | 0.32 (jackson) | 0.41 |
| JVM | mixed | 1 MiB | **0.48** (kernel-native) | 0.18 | 0.15 (jackson) | 0.30 |
| JVM | mixed | 128 MiB | **0.43** (kernel-native) | 0.18 | 0.20 (jackson) | 0.32 |
| JVM | numeric | 1 MiB | **0.60** (kernel-native) | 0.31 | 0.22 (jackson) | 0.37 |
| JVM | numeric | 128 MiB | **0.66** (kernel-swar) | 0.33 | 0.41 (fastcsv) | 0.72 |
| JVM | quoted | 1 MiB | **2.27** (kernel-native) | 0.57 | 0.99 (fastcsv) | 0.87 |
| JVM | quoted | 128 MiB | **1.09** (kernel-native) | 0.54 | 0.76 (fastcsv) | 0.72 |

### typed (GB/s)

| Runtime | Dataset | Size | Best kernel path | Current library | Best Java competitor | csv-typed |
|---|---|---|---:|---:|---:|---:|
| SVM (compat) | mixed | 1 MiB | **0.45** (kernel-native) | 0.15 | 0.08 (fastcsv) | 0.21 |
| SVM (compat) | mixed | 128 MiB | **0.68** (kernel-native) | 0.23 | 0.16 (univocity) | 0.38 |
| SVM (compat) | numeric | 1 MiB | **0.35** (kernel-native) | 0.14 | 0.07 (jackson) | 0.42 |
| SVM (compat) | numeric | 128 MiB | **0.23** (kernel-native) | 0.08 | 0.05 (fastcsv) | 0.23 |
| SVM (x86-64-v3) | mixed | 1 MiB | **0.77** (kernel-native) | 0.31 | 0.17 (fastcsv) | 0.36 |
| SVM (x86-64-v3) | mixed | 128 MiB | **0.38** (kernel-native) | 0.16 | 0.10 (fastcsv) | 0.26 |
| SVM (x86-64-v3) | numeric | 1 MiB | **0.42** (kernel-native) | 0.18 | 0.08 (jackson) | 0.44 |
| SVM (x86-64-v3) | numeric | 128 MiB | **0.29** (kernel-native) | 0.07 | 0.07 (fastcsv) | 0.32 |
| JVM | mixed | 1 MiB | **0.66** (kernel-native) | 0.18 | 0.14 (jackson) | 0.26 |
| JVM | mixed | 128 MiB | **0.49** (kernel-swar) | 0.17 | 0.15 (univocity) | 0.25 |
| JVM | numeric | 1 MiB | **0.52** (kernel-native) | 0.16 | 0.17 (fastcsv) | 0.36 |
| JVM | numeric | 128 MiB | **0.55** (kernel-native) | 0.13 | 0.21 (fastcsv) | 0.45 |

### Index kernels (structural index only, GB/s; SVM compat, same shard per column)

| Kernel | numeric 1 MiB | mixed 128 MiB | quoted 128 MiB | escaped 128 MiB |
|---|---:|---:|---:|---:|
| `native-avx512` from Java (SVM leaf call) | 7.13 | 4.65 | 6.04 | 6.85 |
| `native-avx512`, C++ probe | 7.80 | 4.53 | 6.12 | 7.16 |
| `native-avx2`, C++ probe | 5.82 | 4.56 | 5.94 | 7.16 |
| `java-swar` | 1.58 | 1.55 | 1.78 | 1.95 |
| `java-table` (branch-light scalar) | 0.51 | 0.71 | 0.53 | 0.68 |
| `java-scalar` (oracle) | 0.56 | 0.45 | 0.44 | 0.55 |
| `java-vector` (Vector API) | 0.05 | 0.06 | 0.04 | 0.06 |

The native kernel called from Java runs within about 10% of the same kernel in C++: the
Java-to-native call costs almost nothing at chunk sizes. Java SWAR reaches a fifth to a third of
AVX-512.

### Retained typed reads (32 numeric rows, 8 typed columns; ns/row)

| Runtime | Stage | `truffledata-plan` (CsvRow) | `batch-fastcsv` (core Batch rows) |
|---|---|---:|---:|
| SVM compat | peak | 19.0 (13.6–24.4) | 17.4 (15.4–19.5) |
| SVM v3 | peak | 14.3 (8.1–20.5) | 15.3 (12.8–17.7) |
| JVM | peak | 29.0 (7.1–51.0) | 14.0 (12.4–15.7) |
| SVM compat | read-aot | 96.2 | 80.0 |

The CsvRow read loop compiles to 141–197 nodes with no graph allocations and allocates 1.0 B per
row, which is the harness's per-call boxing. The core `Batch` loop keeps four allocation sites,
because FLOAT64 and BOOLEAN cells are boxed.

## Where we stand

**Against the Java competitors: first in every cell.**

- Scan: the best kernel path is 2.4–21× the best competitor (FastCSV or Jackson) on every
  dataset, size and runtime.
- Typed decode: 2.6–5.6× the best competitor, with no Java allocation per row. The competitors
  allocate 380–2,400 B per row.
- Strings: first in 23 of 24 rows. The exception is JVM escaped 128 MiB: 0.31 GB/s against
  Jackson's 0.32, a tie within noise.

The current library paths are weaker. `CsvScanner` alone beats every competitor's scan
(1.03–2.7×). `Csv.parse` with `getString` reaches 0.5–1.4× of the best competitor. `CsvPlan`
typed beats the best competitor on SVM (1.4–2.6×), but not on the JVM's `numeric` fixture
(0.13–0.16 against 0.17–0.21 GB/s).

**Against the native references:**

- **Scan.** The kernel path beats Rust `csv-core` (and `csv` `ByteRecord`) in every row:
  1.25–2.6× on short fields, 3.5–11× on quoted and escaped text. Against the C++ consumer of the
  same index it reaches 60–100%. On the JVM it sometimes exceeds the C++ probe, because the critical FFM call
  and Graal's code for the consumer loop beat the probe's generic consumer.
- **Typed.** On `numeric` on SVM, 83–100% of Rust `csv` (for example 0.35 against 0.42 GB/s at
  1 MiB, 0.23 against 0.23 at 128 MiB). Ahead on `mixed` (1.5–2.5×), and ahead on every JVM row
  (1.2–2.5×).
- **Strings.** Behind Rust's `StringRecord` on `numeric` (for example 0.35 against 0.75 GB/s on
  SVM v3, and 0.66 against 0.72 on the JVM at 128 MiB), and on `escaped` and `mixed` 128 MiB on
  SVM. That row is not like for like: Java must allocate a UTF-16 `String` per cell (256 B
  per row on `numeric`), while Rust validates into a reused buffer. Ahead on `quoted` (1.26–3.2×),
  on JVM `mixed`, and on JVM `numeric` at 1 MiB.

**What would close the remaining gaps:**

1. **Wire the kernel into the scanner seam.** `Csv.parse` and `CsvPlan` are 2–6× below the
   kernel paths: `BatchBuilder`, per-row `CsvRow`s and array growth cost 300–2,000 B per row. The
   index consumer is ready (`IndexedScanner`).
2. **Per-field consumer cost on dense data.** On `numeric` the index runs at 7–8 GB/s, while scan
   runs at 0.7–2.6 GB/s. The record callback and span packing dominate. Batch the index into
   column spans directly, with no per-record callback. Read offsets as a vector of ints, not
   entry by entry.
3. **`-march=compatibility` on SVM.** It costs the Java kernels about 2× (SWAR) and makes the
   Vector API unusable (0.05 GB/s with 2–29 KB per row allocated: not intrinsified). The v3 image
   restores the Vector API to 3–6.6 GB/s. The native kernel is unaffected because it dispatches
   at run time, which is one more reason to make it the SVM default.
4. **Number parsing.** Typed decode stays below 1 GB/s on every path, the Rust one included. The
   next step is SIMD-assisted integer and float parsing (an 8-digit SWAR integer and the
   Eisel-Lemire float path).
5. **Strings.** A lazy `CsvText` or `TruffleString` (no UTF-16 copy) removes the materialization
   cost. `CsvText` already exists for export.

## Hypotheses (plan Task 7)

The current-library engines map onto the plan as follows:

- `truffledata-scan` is `truffledata-scanner` (scan only, no export).
- `csv-strings` is `truffledata-batch`.
- `csv-typed` is `truffledata-plan`.

All figures are SVM compat.

| | Claim | Result |
| --- | --- | --- |
| H1 | Scan at least 2× FastCSV on `mixed`, and at most 2.0 B per input byte | **Partly confirmed.** The current scanner reaches 1.9× (1 MiB) and 2.1× (128 MiB), with 0 B per row. The kernel path reaches 5.0–5.2×. |
| H2 | `csv-strings` within 0.8–1.25× of FastCSV | **Refuted at 128 MiB.** 1.0× at 1 MiB, 0.5× at 128 MiB (0.11 against 0.22 GB/s). The kernel path reaches 1.7–2.2×. |
| H3 | Scan at least 1.0× FastCSV on long text, with a SWAR follow-up below 1.5× | **Confirmed but below 1.5×** (1.5×, then 1.45×), so the follow-up opened. Done here: the kernel path reaches 9.7–17×. |
| H4 | Typed at least 2× FastCSV, with at most 0.25× of its allocation | **Speed confirmed at 1 MiB, refuted at 128 MiB** (2.8×, then 1.6×). **Allocation refuted:** 317 against 443 B per row (0.72×). The kernel path reaches 4.6–7× with 0 B per row. |
| H5 | Peak typed reads no slower than 1.5× stock `Batch.Row` | **Confirmed on SVM** (1.09× compat, 0.94× v3). On the JVM the 2.07× median comes from a 7–51 ns fork range: noisy, not decided. |

## Kernel choice per runtime and size

From the size sweep (4 KiB–16 MiB, `numeric` and `mixed`, in
[`csv-comparison.md`](../results/csv-f5daec1/csv-comparison.md)) and the fixture cells:

| Runtime | Default | Fallback without the native library | Crossover |
| --- | --- | --- | --- |
| SVM (`-march=compatibility`) | Native kernel. Leaf call (no transition) for chunks of at most 1 MiB, normal transition above that. | `java-swar` | Native beats SWAR by 1.3–3× from 4 KiB up. On `mixed` the leaf call ties or wins below 256 KiB (index 3.0–4.3 against 2.5–3.6 GB/s); on `numeric` the result is mixed. From 1 MiB the two are equal within noise. Below 4 KiB: not measured. |
| SVM (`-march=x86-64-v3`) | Native kernel, as above | `java-vector` (3.1–6.6 GB/s index), then `java-swar` | Native beats the Vector API by 1.3–2× at every size. |
| JVM | Native kernel through a critical FFM call on the heap array (no copy) | `java-vector` | The critical call beats the copying FFM path at most sweep points, and matches or beats `java-vector` except in two fixture cells: `numeric` 128 MiB scan (2.60 against 2.39 GB/s) and `escaped` 128 MiB (a tie at 2.84). Above about 4 MiB the index buffer stops fitting in cache and all paths converge. |
| Any, below 4 KiB | Unmeasured | — | The strict scanner may still win on tiny inputs, because the native path pins two arrays per call. A sweep below 4 KiB is open. |

Branch-light scalar (`java-table`) never beat the current scanner or SWAR, and is dropped as a
default. The Vector API is dropped for the portable SVM image. It stays an option for x86-64-v3
images and the JVM. Local Apple M5 Max (advisory, JVM): `java-vector128` indexes at 3.3–11 GB/s
and NEON native at 6.6–15 GB/s.

## Smoke budgets

`//benchmarks/comparison:csv_native_smoke` (SVM) and `:csv_jvm_smoke` run every engine on every
fixture at 64 KiB. Each requires equal checksums per workload, and zero FLOAT64 slow-path
conversions. The SVM smoke also checks graph budgets:

- `CsvReads[truffledata-plan]`: at most 1,000 nodes, 1 loop, 0 invokes and 0 graph allocations,
  and at most 1.0 Java B per row at peak. Measured: 141–197 nodes.
- `CsvReads[batch-fastcsv]`: the same node, loop and invoke limits. Its allocations are reported,
  not gated. Measured: 4 graph allocation sites.

## Reproduce

```sh
bazel test //benchmarks/comparison:csv_jvm_smoke //benchmarks/comparison:csv_native_smoke
bazel test //benchmarks/comparison:csv_measure //benchmarks/comparison:csv_measure_v3 \
  //benchmarks/comparison:csv_measure_jvm --test_timeout=3600
python3 benchmarks/comparison/csv_report.py <test.outputs>/csv-*.json > csv-comparison.md
python3 benchmarks/comparison/csv_summary.py <test.outputs>/csv-*fixture*.json > summary.md
```
