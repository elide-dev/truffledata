# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 84.96 (67.76–107.28) | 136.00 |
| proto-scalar | stock | read-aot | 1.26 (1.19–2.26) | 0.00 |
| proto-scalar | owned | ingest | 101.63 (84.94–106.39) | 88.00 |
| proto-scalar | owned | ingest-typed | 125.30 (101.97–139.40) | 56.00 |
| proto-scalar | owned | interpret | 24.04 (23.03–38.54) | 2.00 |
| proto-scalar | owned | peak | 2.37 (2.26–2.55) | 1.00 |
| proto-scalar | buffa | ingest | 527.89 (463.70–567.03) | 312.00 |
| proto-scalar | buffa | ingest-typed | 369.22 (357.26–376.19) | 312.00 |
| proto-scalar | buffa | interpret | 24.43 (22.36–32.39) | 2.00 |
| proto-scalar | buffa | peak | 4.11 (3.86–5.84) | 1.00 |
| proto-bytes | stock | ingest | 36253.27 (34564.27–135550.63) | 65699.00 |
| proto-bytes | stock | read-aot | 3.12 (3.12–3.31) | 0.00 |
| proto-bytes | stock-aliased | ingest | 76.91 (64.07–89.85) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 2.88 (2.78–3.01) | 0.00 |
| proto-bytes | owned | ingest | 79413.10 (25134.89–80394.43) | 66051.00 |
| proto-bytes | owned | interpret | 21.82 (21.24–22.67) | 2.00 |
| proto-bytes | owned | peak | 4.51 (4.44–6.44) | 1.00 |
| proto-bytes | buffa | ingest | 266.04 (264.09–968.76) | 304.00 |
| proto-bytes | buffa | interpret | 23.35 (17.75–24.90) | 2.00 |
| proto-bytes | buffa | peak | 4.49 (4.10–5.53) | 1.00 |
| timestamp-json | stock | ingest | 5049.45 (3454.44–5229.62) | 6439.75 |
| timestamp-json | owned | ingest | 1658.56 (1515.83–1997.56) | 3703.75 |
| timestamp-json | owned | ingest-typed | 1921.51 (1531.92–2148.88) | 3671.75 |
| timestamp-json | buffa | ingest | 853.77 (568.29–877.58) | 720.00 |
| timestamp-json | buffa | ingest-typed | 674.13 (618.68–827.52) | 720.00 |
| mysql-int4 | stock | ingest | 37.00 (33.48–41.47) | 16.00 |
| mysql-int4 | stock | read-aot | 1.34 (1.12–1.49) | 0.00 |
| mysql-int4 | static | ingest | 124.86 (116.24–138.42) | 160.00 |
| mysql-int4 | static | ingest-typed | 129.96 (113.53–164.65) | 160.00 |
| mysql-int4 | static | interpret | 13.64 (13.34–16.16) | 2.00 |
| mysql-int4 | static | peak | 3.55 (2.66–3.68) | 1.00 |
| postgres-int4 | stock | ingest | 34.50 (26.86–50.44) | 16.00 |
| postgres-int4 | stock | read-aot | 1.32 (1.28–1.74) | 0.00 |
| postgres-int4 | static | ingest | 134.69 (96.43–144.41) | 160.00 |
| postgres-int4 | static | ingest-typed | 123.27 (99.22–2325.25) | 160.00 |
| postgres-int4 | static | interpret | 14.84 (14.47–18.95) | 2.00 |
| postgres-int4 | static | peak | 3.22 (2.35–4.51) | 1.00 |

Stock read-aot is the native-image host baseline; it has no Truffle interpreter/JIT tier.
Ingest includes parsing/materialization and an uncached projected read. Setup is excluded.
ingest-typed uses primitive readLong projections; ingest retains generic read projections.
Buffa binary input is already in unmanaged memory; owned engines start with Java byte arrays.
Buffa results are scoped borrowed views; stock-aliased borrows immutable ByteString input; stock/owned copy payloads.
The direct stock AOT baseline excludes guest interop and contract enforcement.
MySQL/Postgres measure driver INT4 decoder functions and retained scalar rows, not JDBC queries, transport, or servers.
Java heap counters exclude native allocations and compiler/background threads. Native allocation is not reported as zero.
Shared execution hardware and short samples make these diagnostic comparisons, not performance guarantees.

Binary SHA-256: `7be975df462c95c81c1353644dd1f8a6e700a8bf29c0cb66d800c2ac69ca3b46`
Revision: `8c3fbec4e093a1f23c0fddf66fc73b635985a2f2`
