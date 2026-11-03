# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 138.47 (95.26–175.74) | 136.00 |
| proto-scalar | stock | read-aot | 3.40 (1.77–3.41) | 0.00 |
| proto-scalar | owned | ingest | 272.98 (177.83–276.84) | 88.00 |
| proto-scalar | owned | interpret | 57.30 (30.54–58.23) | 2.00 |
| proto-scalar | owned | peak | 5.24 (4.40–6.09) | 1.00 |
| proto-scalar | buffa | ingest | 1123.96 (549.81–1180.27) | 400.00 |
| proto-scalar | buffa | interpret | 38.74 (25.71–40.96) | 2.00 |
| proto-scalar | buffa | peak | 9.79 (9.30–12.84) | 1.00 |
| proto-bytes | stock | ingest | 50318.17 (34661.04–53963.65) | 65699.00 |
| proto-bytes | stock | read-aot | 4.38 (4.14–4.42) | 0.00 |
| proto-bytes | stock-aliased | ingest | 141.78 (124.66–147.00) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 4.50 (3.27–6.01) | 0.00 |
| proto-bytes | owned | ingest | 24590.58 (14579.04–39600.34) | 66051.00 |
| proto-bytes | owned | interpret | 32.31 (30.04–43.47) | 2.00 |
| proto-bytes | owned | peak | 11.50 (7.28–11.67) | 1.00 |
| proto-bytes | buffa | ingest | 432.83 (427.18–900.49) | 392.00 |
| proto-bytes | buffa | interpret | 33.22 (23.42–40.21) | 2.00 |
| proto-bytes | buffa | peak | 7.31 (6.24–13.03) | 1.00 |
| timestamp-json | stock | ingest | 9136.12 (5414.01–47516.59) | 6439.75 |
| timestamp-json | owned | ingest | 2517.62 (2396.82–3852.13) | 3703.75 |
| timestamp-json | buffa | ingest | 894.52 (864.30–1474.01) | 792.00 |
| mysql-int4 | stock | ingest | 120.24 (111.42–120.33) | 16.00 |
| mysql-int4 | stock | read-aot | 2.27 (1.76–3.24) | 0.00 |
| mysql-int4 | static | ingest | 327.85 (187.28–330.82) | 160.00 |
| mysql-int4 | static | interpret | 25.81 (21.57–33.06) | 2.00 |
| mysql-int4 | static | peak | 3.70 (3.64–7.14) | 1.00 |
| postgres-int4 | stock | ingest | 70.78 (39.80–75.91) | 16.00 |
| postgres-int4 | stock | read-aot | 2.72 (1.73–3.57) | 0.00 |
| postgres-int4 | static | ingest | 198.15 (150.18–299.15) | 160.00 |
| postgres-int4 | static | interpret | 17.62 (17.31–35.78) | 2.00 |
| postgres-int4 | static | peak | 3.88 (3.76–6.66) | 1.00 |

Stock read-aot is the native-image host baseline; it has no Truffle interpreter/JIT tier.
Ingest includes parsing/materialization and an uncached projected read. Setup is excluded.
Buffa binary input is already in unmanaged memory; owned engines start with Java byte arrays.
Buffa results are scoped borrowed views; stock-aliased borrows immutable ByteString input; stock/owned copy payloads.
The direct stock AOT baseline excludes guest interop and contract enforcement.
MySQL/Postgres measure driver INT4 decoder functions and retained scalar rows, not JDBC queries, transport, or servers.
Java heap counters exclude native allocations and compiler/background threads. Native allocation is not reported as zero.
Shared execution hardware and short samples make these diagnostic comparisons, not performance guarantees.

Binary SHA-256: `7cd2283eb8366affbe92fb446221916a5be16d035cc26b78588c31e18b73d61f`
Revision: `71b4c16ae5da1b1ade656457d66bb967843e650a`
