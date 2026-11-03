# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 149.08 (144.76–149.20) | 136.00 |
| proto-scalar | stock | read-aot | 3.08 (2.58–3.42) | 0.00 |
| proto-scalar | owned | ingest | 1129.57 (919.97–1230.32) | 616.00 |
| proto-scalar | owned | interpret | 58.27 (56.83–65.43) | 2.00 |
| proto-scalar | owned | peak | 10.08 (9.65–10.70) | 1.00 |
| proto-scalar | buffa | ingest | 1068.22 (919.41–1179.56) | 592.00 |
| proto-scalar | buffa | interpret | 36.71 (33.67–37.08) | 2.00 |
| proto-scalar | buffa | peak | 9.97 (9.09–11.13) | 1.00 |
| proto-bytes | stock | ingest | 51038.73 (43531.72–51749.85) | 65699.00 |
| proto-bytes | stock | read-aot | 6.18 (5.97–6.47) | 0.00 |
| proto-bytes | stock-aliased | ingest | 156.06 (116.03–156.27) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 5.54 (5.52–6.02) | 0.00 |
| proto-bytes | owned | ingest | 14823.38 (13714.51–35833.00) | 66219.00 |
| proto-bytes | owned | interpret | 40.44 (34.65–50.66) | 2.00 |
| proto-bytes | owned | peak | 10.68 (10.66–10.80) | 1.00 |
| proto-bytes | buffa | ingest | 790.68 (483.61–1541.90) | 568.00 |
| proto-bytes | buffa | interpret | 39.14 (32.77–46.50) | 2.00 |
| proto-bytes | buffa | peak | 11.92 (8.70–12.17) | 1.00 |
| timestamp-json | stock | ingest | 8241.04 (7277.62–9381.56) | 6439.75 |
| timestamp-json | owned | ingest | 3993.83 (2946.61–4394.48) | 3831.75 |
| timestamp-json | buffa | ingest | 1591.64 (1495.17–1978.98) | 984.00 |
| mysql-int4 | stock | ingest | 109.33 (106.44–114.28) | 16.00 |
| mysql-int4 | stock | read-aot | 2.61 (2.11–2.62) | 0.00 |
| mysql-int4 | static | ingest | 357.70 (254.35–364.27) | 160.00 |
| mysql-int4 | static | interpret | 29.19 (28.76–32.95) | 2.00 |
| mysql-int4 | static | peak | 5.74 (5.12–6.58) | 1.00 |
| postgres-int4 | stock | ingest | 71.72 (51.47–79.48) | 16.00 |
| postgres-int4 | stock | read-aot | 2.99 (2.65–3.23) | 0.00 |
| postgres-int4 | static | ingest | 224.12 (188.45–373.25) | 160.00 |
| postgres-int4 | static | interpret | 31.15 (21.25–33.09) | 2.00 |
| postgres-int4 | static | peak | 6.06 (4.67–6.86) | 1.00 |

Stock read-aot is the native-image host baseline; it has no Truffle interpreter/JIT tier.
Ingest includes parsing/materialization and an uncached projected read. Setup is excluded.
Buffa binary input is already in unmanaged memory; owned engines start with Java byte arrays.
Buffa results are scoped borrowed views; stock-aliased borrows immutable ByteString input; stock/owned copy payloads.
The direct stock AOT baseline excludes guest interop and contract enforcement.
MySQL/Postgres measure driver INT4 decoder functions and retained scalar rows, not JDBC queries, transport, or servers.
Java heap counters exclude native allocations and compiler/background threads. Native allocation is not reported as zero.
Shared execution hardware and short samples make these diagnostic comparisons, not performance guarantees.

Binary SHA-256: `fafada989265f453bce66bf13e2a239c7bff1981188e7eb0b0bd1edde5d0f2a4`
Revision: `fa855f75e8603d49e0df3a26ef26e273edeac56c`
