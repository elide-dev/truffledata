# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 126.19 (101.93–132.51) | 136.00 |
| proto-scalar | stock | read-aot | 1.97 (1.82–2.96) | 0.00 |
| proto-scalar | stock | interpret | 2.76 (2.76–3.58) | 2.00 |
| proto-scalar | stock | peak | 2.04 (1.71–2.17) | 1.00 |
| proto-scalar | owned | ingest | 110.20 (110.18–123.78) | 88.00 |
| proto-scalar | owned | ingest-typed | 147.43 (111.07–168.05) | 56.00 |
| proto-scalar | owned | interpret | 39.16 (34.86–51.18) | 2.00 |
| proto-scalar | owned | peak | 5.44 (3.59–5.94) | 1.00 |
| proto-scalar | buffa | ingest | 510.65 (507.17–887.10) | 312.00 |
| proto-scalar | buffa | ingest-typed | 921.83 (657.62–1008.48) | 312.00 |
| proto-scalar | buffa | interpret | 25.82 (25.73–46.09) | 2.00 |
| proto-scalar | buffa | peak | 8.24 (4.80–8.45) | 1.00 |
| proto-bytes | stock | ingest | 58173.98 (53215.32–82883.37) | 65699.00 |
| proto-bytes | stock | read-aot | 4.25 (4.22–6.97) | 0.00 |
| proto-bytes | stock | interpret | 6.24 (5.31–6.70) | 2.00 |
| proto-bytes | stock | peak | 4.57 (4.17–5.44) | 1.00 |
| proto-bytes | stock-aliased | ingest | 108.29 (86.89–121.53) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 5.88 (5.22–6.06) | 0.00 |
| proto-bytes | stock-aliased | interpret | 5.31 (3.74–6.22) | 2.00 |
| proto-bytes | stock-aliased | peak | 4.21 (4.16–7.13) | 1.00 |
| proto-bytes | owned | ingest | 27926.71 (12325.37–28308.92) | 66035.00 |
| proto-bytes | owned | interpret | 28.79 (27.48–45.35) | 2.00 |
| proto-bytes | owned | peak | 6.30 (6.29–8.13) | 1.00 |
| proto-bytes | buffa | ingest | 386.25 (386.09–501.69) | 304.00 |
| proto-bytes | buffa | interpret | 32.30 (24.71–40.69) | 2.00 |
| proto-bytes | buffa | peak | 9.37 (5.76–10.40) | 1.00 |
| timestamp-json | stock | ingest | 7752.89 (5331.19–8546.17) | 6439.75 |
| timestamp-json | owned | ingest | 2331.31 (2326.26–2392.02) | 3703.75 |
| timestamp-json | owned | ingest-typed | 3443.29 (2658.17–3457.57) | 3671.75 |
| timestamp-json | buffa | ingest | 1142.64 (892.50–1773.76) | 720.00 |
| timestamp-json | buffa | ingest-typed | 839.63 (832.41–1273.78) | 720.00 |
| mysql-int4 | stock | ingest | 86.47 (72.11–100.10) | 16.00 |
| mysql-int4 | stock | read-aot | 1.77 (1.75–2.18) | 0.00 |
| mysql-int4 | stock | interpret | 2.94 (2.83–4.39) | 2.00 |
| mysql-int4 | stock | peak | 1.98 (1.77–2.44) | 1.00 |
| mysql-int4 | static | ingest | 252.03 (186.27–318.78) | 160.00 |
| mysql-int4 | static | ingest-typed | 164.63 (161.48–297.43) | 160.00 |
| mysql-int4 | static | interpret | 17.58 (17.19–18.19) | 2.00 |
| mysql-int4 | static | peak | 5.94 (3.44–6.11) | 1.00 |
| postgres-int4 | stock | ingest | 67.10 (54.98–77.58) | 16.00 |
| postgres-int4 | stock | read-aot | 1.73 (1.70–2.69) | 0.00 |
| postgres-int4 | stock | interpret | 4.55 (3.53–4.95) | 2.00 |
| postgres-int4 | stock | peak | 1.36 (1.36–1.39) | 1.00 |
| postgres-int4 | static | ingest | 145.35 (141.87–176.06) | 160.00 |
| postgres-int4 | static | ingest-typed | 175.54 (152.24–216.91) | 160.00 |
| postgres-int4 | static | interpret | 19.36 (16.98–30.13) | 2.00 |
| postgres-int4 | static | peak | 4.74 (3.78–6.12) | 1.00 |
| proto-mixed | stock | ingest | 1262.97 (1262.15–1567.33) | 1776.75 |
| proto-mixed | stock | read-aot | 9.12 (7.99–10.60) | 0.00 |
| proto-mixed | stock | interpret-boundary | 10.52 (10.36–12.69) | 2.00 |
| proto-mixed | stock | peak-boundary | 11.66 (10.04–14.72) | 1.00 |
| proto-mixed | owned | ingest | 19478.04 (13514.93–22373.21) | 3339.50 |
| proto-mixed | owned | ingest-typed | 20605.19 (19582.01–21881.73) | 3339.50 |
| proto-mixed | owned | interpret | 119.96 (105.33–150.03) | 2.00 |
| proto-mixed | owned | peak | 13.04 (12.91–17.91) | 1.00 |
| proto-mixed | buffa | ingest | 8076.62 (6080.29–8911.77) | 2195.25 |
| proto-mixed | buffa | ingest-typed | 6429.55 (5979.78–11739.85) | 2195.25 |
| proto-mixed | buffa | interpret | 101.13 (97.13–106.61) | 2.00 |
| proto-mixed | buffa | peak | 12.29 (11.37–20.35) | 1.00 |

Stock read-aot is the native-image host baseline. Schema 4 adds stock guest tiers; mixed protobuf uses explicitly labeled boundary projections because its collection getters fail the SVM JIT blocklist.
Ingest includes parsing/materialization and an uncached projected read. Setup is excluded.
ingest-typed uses primitive readLong projections; ingest retains generic read projections.
Buffa binary input is already in unmanaged memory; owned engines start with Java byte arrays.
Buffa results are scoped borrowed views; stock-aliased borrows immutable ByteString input; stock/owned copy payloads.
The direct stock AOT baseline excludes guest interop and contract enforcement.
MySQL/Postgres measure driver INT4 decoder functions and retained scalar rows, not JDBC queries, transport, or servers.
Java heap counters exclude native allocations and compiler/background threads. Native allocation is not reported as zero.
Shared execution hardware and short samples make these diagnostic comparisons, not performance guarantees.

Binary SHA-256: `0c9fa38222a2bf76fce2b50f16133f3552d956da67b8759c5a442fa02c9ba975`
Revision: `3070d3c9a62b93ff5cb14521ed120052ad9147ef`
