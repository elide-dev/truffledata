# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 174.06 (101.08–186.79) | 136.00 |
| proto-scalar | stock | read-aot | 1.81 (1.77–2.44) | 0.00 |
| proto-scalar | owned | ingest | 192.48 (115.96–222.72) | 88.00 |
| proto-scalar | owned | ingest-typed | 237.63 (133.07–250.83) | 56.00 |
| proto-scalar | owned | interpret | 50.60 (36.33–60.90) | 2.00 |
| proto-scalar | owned | peak | 4.27 (3.38–6.60) | 1.00 |
| proto-scalar | buffa | ingest | 561.26 (509.17–562.26) | 312.00 |
| proto-scalar | buffa | ingest-typed | 514.16 (505.66–534.70) | 312.00 |
| proto-scalar | buffa | interpret | 40.11 (27.76–51.31) | 2.00 |
| proto-scalar | buffa | peak | 6.54 (4.87–6.59) | 1.00 |
| proto-bytes | stock | ingest | 60953.75 (54253.22–80493.51) | 65699.00 |
| proto-bytes | stock | read-aot | 4.28 (3.96–6.33) | 0.00 |
| proto-bytes | stock-aliased | ingest | 168.26 (136.97–207.21) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 4.89 (3.29–5.43) | 0.00 |
| proto-bytes | owned | ingest | 15285.99 (14587.89–32329.49) | 66051.00 |
| proto-bytes | owned | interpret | 34.94 (28.02–43.00) | 2.00 |
| proto-bytes | owned | peak | 6.93 (6.84–6.99) | 1.00 |
| proto-bytes | buffa | ingest | 418.55 (395.02–731.67) | 304.00 |
| proto-bytes | buffa | interpret | 26.76 (24.64–28.77) | 2.00 |
| proto-bytes | buffa | peak | 6.97 (6.27–11.38) | 1.00 |
| timestamp-json | stock | ingest | 5856.45 (5534.96–9423.19) | 6439.75 |
| timestamp-json | owned | ingest | 3504.59 (2445.27–4024.80) | 3703.75 |
| timestamp-json | owned | ingest-typed | 2525.02 (2397.27–3798.55) | 3671.75 |
| timestamp-json | buffa | ingest | 1073.99 (885.00–1609.30) | 720.00 |
| timestamp-json | buffa | ingest-typed | 1387.27 (890.65–1637.89) | 720.00 |
| mysql-int4 | stock | ingest | 64.15 (63.61–67.59) | 16.00 |
| mysql-int4 | stock | read-aot | 1.95 (1.84–2.84) | 0.00 |
| mysql-int4 | static | ingest | 193.32 (182.43–337.22) | 160.00 |
| mysql-int4 | static | ingest-typed | 349.67 (180.27–399.27) | 160.00 |
| mysql-int4 | static | interpret | 31.15 (30.46–34.01) | 2.00 |
| mysql-int4 | static | peak | 4.70 (3.55–6.35) | 1.00 |
| postgres-int4 | stock | ingest | 72.40 (41.65–81.32) | 16.00 |
| postgres-int4 | stock | read-aot | 1.70 (1.68–1.85) | 0.00 |
| postgres-int4 | static | ingest | 246.57 (194.79–250.35) | 160.00 |
| postgres-int4 | static | ingest-typed | 257.43 (161.59–331.73) | 160.00 |
| postgres-int4 | static | interpret | 22.23 (17.67–22.54) | 2.00 |
| postgres-int4 | static | peak | 3.60 (3.51–3.63) | 1.00 |
| proto-mixed | stock | ingest | 1748.68 (1418.27–1879.60) | 1776.75 |
| proto-mixed | stock | read-aot | 12.19 (8.31–13.05) | 0.00 |
| proto-mixed | owned | ingest | 16816.75 (16729.33–31501.69) | 3847.50 |
| proto-mixed | owned | ingest-typed | 28421.49 (24454.85–31801.71) | 3847.50 |
| proto-mixed | owned | interpret | 95.98 (94.83–180.60) | 2.00 |
| proto-mixed | owned | peak | 18.28 (14.32–22.23) | 1.00 |
| proto-mixed | buffa | ingest | 8546.71 (8123.76–13232.19) | 4204.50 |
| proto-mixed | buffa | ingest-typed | 11931.05 (10535.74–13165.89) | 4204.50 |
| proto-mixed | buffa | interpret | 92.53 (91.33–106.19) | 2.00 |
| proto-mixed | buffa | peak | 11.68 (11.53–23.27) | 1.00 |

Stock read-aot is the native-image host baseline; it has no Truffle interpreter/JIT tier.
Ingest includes parsing/materialization and an uncached projected read. Setup is excluded.
ingest-typed uses primitive readLong projections; ingest retains generic read projections.
Buffa binary input is already in unmanaged memory; owned engines start with Java byte arrays.
Buffa results are scoped borrowed views; stock-aliased borrows immutable ByteString input; stock/owned copy payloads.
The direct stock AOT baseline excludes guest interop and contract enforcement.
MySQL/Postgres measure driver INT4 decoder functions and retained scalar rows, not JDBC queries, transport, or servers.
Java heap counters exclude native allocations and compiler/background threads. Native allocation is not reported as zero.
Shared execution hardware and short samples make these diagnostic comparisons, not performance guarantees.

Binary SHA-256: `14497ec39612f142e756d70ffbaa70eaba31e2caf6d904f0aaebab2d31c6bad8`
Revision: `a817f6c531f7e6444fdda1f1d2ee14cda0c88056`
