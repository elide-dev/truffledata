# CSV comparison, first shipped-path run (superseded by csv-b5bd173)

Raw samples of the first run of the shipped-path harness at `1d2e9ab`
(<https://app.buildbuddy.io/invocation/d64c9b11-a3d1-4e56-85e2-dc28df03f5f2>), kept because two
decisions rest on it:

- The native provider then indexed 1 Mi entries per call and allocated its index per scanner, so
  a 1 MiB parse allocated a 4 MiB array: `truffledata-scanner-native` 207 B/row against 13 B/row
  for `java-swar` on SVM `numeric` 1 MiB, and native `strings`/`typed` below the SWAR default
  (`csv-comparison.md`). `b5bd173` shares one 64 Ki-entry buffer per thread.
- The size sweep (256 B–16 MiB) put the per-byte/index crossover between 4 and 16 KiB on SVM,
  which set `CsvIndexing.MIN_INDEX_BYTES` to 16 KiB.

Same caveats as `csv-b5bd173/README.md`: shared hosts, advisory, compare only within a shard.
`summary.md` and `csv-comparison.md` are generated from `raw/` by `csv_summary.py` and
`csv_report.py`.
