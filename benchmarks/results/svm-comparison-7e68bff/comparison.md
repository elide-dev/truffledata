# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 174.06 (150.73–183.58) | 136.00 |
| proto-scalar | stock | read-aot | 3.54 (2.79–3.73) | 0.00 |
| proto-scalar | stock | interpret | 5.17 (4.86–6.12) | 2.00 |
| proto-scalar | stock | peak | 2.47 (2.32–2.72) | 1.00 |
| proto-scalar | owned | ingest | 198.65 (176.75–251.49) | 88.00 |
| proto-scalar | owned | ingest-typed | 220.03 (170.19–252.40) | 56.00 |
| proto-scalar | owned | interpret | 60.85 (47.36–63.37) | 2.00 |
| proto-scalar | owned | peak | 5.79 (3.38–7.33) | 1.00 |
| proto-scalar | buffa | ingest | 695.86 (549.34–1154.36) | 320.00 |
| proto-scalar | buffa | ingest-typed | 905.53 (763.41–1315.49) | 320.00 |
| proto-scalar | buffa | interpret | 38.76 (38.42–53.62) | 2.00 |
| proto-scalar | buffa | peak | 9.99 (9.36–10.02) | 1.00 |
| proto-bytes | stock | ingest | 68872.40 (65671.57–69594.44) | 65699.00 |
| proto-bytes | stock | read-aot | 6.59 (6.19–7.10) | 0.00 |
| proto-bytes | stock | interpret | 10.75 (8.95–11.89) | 2.00 |
| proto-bytes | stock | peak | 7.77 (6.40–8.44) | 1.00 |
| proto-bytes | stock-aliased | ingest | 103.76 (88.26–166.48) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 5.89 (5.85–6.21) | 0.00 |
| proto-bytes | stock-aliased | interpret | 9.79 (8.70–11.03) | 2.00 |
| proto-bytes | stock-aliased | peak | 6.27 (5.35–6.55) | 1.00 |
| proto-bytes | owned | ingest | 17330.83 (16617.42–28599.15) | 66035.00 |
| proto-bytes | owned | interpret | 49.05 (31.73–50.46) | 2.00 |
| proto-bytes | owned | peak | 10.40 (10.28–10.69) | 1.00 |
| proto-bytes | buffa | ingest | 604.99 (566.77–797.91) | 312.00 |
| proto-bytes | buffa | interpret | 45.03 (37.00–46.36) | 2.00 |
| proto-bytes | buffa | peak | 7.19 (6.56–10.93) | 1.00 |
| timestamp-json | stock | ingest | 7901.60 (7729.74–10440.77) | 6439.75 |
| timestamp-json | owned | ingest | 4049.93 (3890.37–4271.35) | 3703.75 |
| timestamp-json | owned | ingest-typed | 3693.06 (3554.65–4211.34) | 3671.75 |
| timestamp-json | buffa | ingest | 1313.59 (975.28–1461.28) | 728.00 |
| timestamp-json | buffa | ingest-typed | 1556.04 (1039.09–1700.30) | 728.00 |
| mysql-int4 | stock | ingest | 91.23 (80.49–93.44) | 16.00 |
| mysql-int4 | stock | read-aot | 3.02 (2.99–3.26) | 0.00 |
| mysql-int4 | stock | interpret | 4.87 (3.80–6.22) | 2.00 |
| mysql-int4 | stock | peak | 2.04 (1.94–2.39) | 1.00 |
| mysql-int4 | static | ingest | 355.35 (229.05–398.06) | 160.00 |
| mysql-int4 | static | ingest-typed | 321.20 (291.31–359.71) | 160.00 |
| mysql-int4 | static | interpret | 35.49 (30.46–35.65) | 2.00 |
| mysql-int4 | static | peak | 6.36 (5.24–7.32) | 1.00 |
| postgres-int4 | stock | ingest | 68.21 (47.08–84.49) | 16.00 |
| postgres-int4 | stock | read-aot | 3.25 (3.06–3.62) | 0.00 |
| postgres-int4 | stock | interpret | 4.51 (3.28–6.06) | 2.00 |
| postgres-int4 | stock | peak | 2.61 (2.21–2.64) | 1.00 |
| postgres-int4 | static | ingest | 240.07 (172.55–380.08) | 160.00 |
| postgres-int4 | static | ingest-typed | 330.68 (255.69–349.14) | 160.00 |
| postgres-int4 | static | interpret | 33.73 (32.24–34.03) | 2.00 |
| postgres-int4 | static | peak | 6.10 (4.93–6.32) | 1.00 |
| proto-mixed | stock | ingest | 2426.59 (1698.93–2456.12) | 1776.75 |
| proto-mixed | stock | read-aot | 13.88 (12.57–13.98) | 0.00 |
| proto-mixed | stock | interpret-boundary | 15.67 (13.01–17.29) | 2.00 |
| proto-mixed | stock | peak-boundary | 15.26 (12.85–15.63) | 1.00 |
| proto-mixed | owned | ingest | 24435.29 (23959.61–36381.16) | 3339.50 |
| proto-mixed | owned | ingest-typed | 24463.79 (16956.18–24746.90) | 3339.50 |
| proto-mixed | owned | interpret | 141.67 (130.00–157.58) | 2.00 |
| proto-mixed | owned | peak | 23.78 (23.23–23.85) | 1.00 |
| proto-mixed | buffa | ingest | 11111.02 (10687.54–11279.29) | 1927.00 |
| proto-mixed | buffa | ingest-typed | 10137.44 (8783.66–10963.17) | 1927.00 |
| proto-mixed | buffa | interpret | 129.98 (93.67–144.88) | 2.00 |
| proto-mixed | buffa | peak | 31.40 (23.80–31.75) | 1.00 |
| proto-mixed-full | stock | ingest | 2560.25 (2289.36–2687.50) | 1776.75 |
| proto-mixed-full | owned | ingest | 26983.37 (18644.31–28244.82) | 3339.50 |
| proto-mixed-full | buffa | ingest | 12201.08 (10180.72–15129.02) | 2053.50 |

Stock read-aot is the native-image host baseline. Schema 4 adds stock guest tiers; mixed protobuf uses explicitly labeled boundary projections because its collection getters fail the SVM JIT blocklist.
Ingest includes parsing/materialization and an uncached projected read. Setup is excluded.
ingest-typed uses primitive readLong projections; ingest retains generic read projections.
Buffa binary input is already in unmanaged memory; owned engines start with Java byte arrays.
Buffa results are scoped borrowed views; stock-aliased borrows immutable ByteString input; stock/owned copy payloads.
The direct stock AOT baseline excludes guest interop and contract enforcement.
MySQL/Postgres measure driver INT4 decoder functions and retained scalar rows, not JDBC queries, transport, or servers.
Java heap counters exclude native allocations and compiler/background threads. Native allocation is not reported as zero.
Shared execution hardware and short samples make these diagnostic comparisons, not performance guarantees.

Binary SHA-256: `ef38e7fc239af5b06209999506d89294289720e98d338e5d9e0c59d47edd5f7e`
Revision: `7e68bffcad7e0569a6cc627abbe0848753902778`
