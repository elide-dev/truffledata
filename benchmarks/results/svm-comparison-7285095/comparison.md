# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 148.39 (141.41–187.14) | 136.00 |
| proto-scalar | stock | read-aot | 3.00 (1.77–3.48) | 0.00 |
| proto-scalar | owned | ingest | 175.17 (170.05–233.44) | 88.00 |
| proto-scalar | owned | ingest-typed | 172.40 (109.60–243.04) | 56.00 |
| proto-scalar | owned | interpret | 46.77 (45.64–64.02) | 2.00 |
| proto-scalar | owned | peak | 6.39 (5.69–6.40) | 1.00 |
| proto-scalar | buffa | ingest | 1144.19 (1081.30–1180.04) | 312.00 |
| proto-scalar | buffa | ingest-typed | 729.13 (703.88–1131.08) | 312.00 |
| proto-scalar | buffa | interpret | 44.66 (39.96–49.46) | 2.00 |
| proto-scalar | buffa | peak | 8.01 (7.95–8.92) | 1.00 |
| proto-bytes | stock | ingest | 74429.55 (71201.15–81656.22) | 65699.00 |
| proto-bytes | stock | read-aot | 5.25 (4.88–10.01) | 0.00 |
| proto-bytes | stock-aliased | ingest | 168.11 (155.19–1629.43) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 5.85 (5.78–5.86) | 0.00 |
| proto-bytes | owned | ingest | 24690.19 (15026.46–28370.30) | 66051.00 |
| proto-bytes | owned | interpret | 42.65 (28.12–47.73) | 2.00 |
| proto-bytes | owned | peak | 10.72 (7.73–10.82) | 1.00 |
| proto-bytes | buffa | ingest | 730.26 (590.49–788.26) | 304.00 |
| proto-bytes | buffa | interpret | 41.04 (36.80–47.20) | 2.00 |
| proto-bytes | buffa | peak | 9.89 (8.95–11.54) | 1.00 |
| timestamp-json | stock | ingest | 9026.77 (7893.75–9082.59) | 6439.75 |
| timestamp-json | owned | ingest | 3968.52 (3436.33–4046.52) | 3703.75 |
| timestamp-json | owned | ingest-typed | 3128.30 (3006.06–3947.85) | 3671.75 |
| timestamp-json | buffa | ingest | 1484.52 (850.42–1631.82) | 720.00 |
| timestamp-json | buffa | ingest-typed | 1640.24 (1469.88–1687.19) | 720.00 |
| mysql-int4 | stock | ingest | 114.35 (99.36–116.62) | 16.00 |
| mysql-int4 | stock | read-aot | 3.31 (3.11–3.32) | 0.00 |
| mysql-int4 | static | ingest | 355.55 (223.99–424.00) | 160.00 |
| mysql-int4 | static | ingest-typed | 386.00 (380.22–404.71) | 160.00 |
| mysql-int4 | static | interpret | 34.64 (32.60–34.66) | 2.00 |
| mysql-int4 | static | peak | 5.09 (4.94–7.43) | 1.00 |
| postgres-int4 | stock | ingest | 67.40 (65.28–82.09) | 16.00 |
| postgres-int4 | stock | read-aot | 2.94 (2.25–3.05) | 0.00 |
| postgres-int4 | static | ingest | 308.66 (198.10–386.24) | 160.00 |
| postgres-int4 | static | ingest-typed | 244.20 (212.58–312.08) | 160.00 |
| postgres-int4 | static | interpret | 32.56 (30.16–35.28) | 2.00 |
| postgres-int4 | static | peak | 6.89 (5.35–7.33) | 1.00 |
| proto-mixed | stock | ingest | 2182.79 (1591.47–2293.40) | 1776.75 |
| proto-mixed | stock | read-aot | 12.22 (10.67–14.62) | 0.00 |
| proto-mixed | owned | ingest | 30806.15 (28352.37–31017.91) | 3562.50 |
| proto-mixed | owned | ingest-typed | 30867.29 (18266.07–31427.97) | 3562.50 |
| proto-mixed | owned | interpret | 135.71 (102.07–165.69) | 2.00 |
| proto-mixed | owned | peak | 20.75 (19.82–25.27) | 1.00 |
| proto-mixed | buffa | ingest | 8384.41 (8329.95–9555.08) | 2195.25 |
| proto-mixed | buffa | ingest-typed | 10485.25 (7685.01–11350.73) | 2195.25 |
| proto-mixed | buffa | interpret | 137.94 (82.28–150.61) | 2.00 |
| proto-mixed | buffa | peak | 21.36 (18.78–23.39) | 1.00 |

Stock read-aot is the native-image host baseline; it has no Truffle interpreter/JIT tier.
Ingest includes parsing/materialization and an uncached projected read. Setup is excluded.
ingest-typed uses primitive readLong projections; ingest retains generic read projections.
Buffa binary input is already in unmanaged memory; owned engines start with Java byte arrays.
Buffa results are scoped borrowed views; stock-aliased borrows immutable ByteString input; stock/owned copy payloads.
The direct stock AOT baseline excludes guest interop and contract enforcement.
MySQL/Postgres measure driver INT4 decoder functions and retained scalar rows, not JDBC queries, transport, or servers.
Java heap counters exclude native allocations and compiler/background threads. Native allocation is not reported as zero.
Shared execution hardware and short samples make these diagnostic comparisons, not performance guarantees.

Binary SHA-256: `bac4d9be58cb870922d07c6ba9bebe5af6adb18142df261c8a20a764939d7ae9`
Revision: `7285095ff1d27fa258a020273320eef82f8e41cd`
