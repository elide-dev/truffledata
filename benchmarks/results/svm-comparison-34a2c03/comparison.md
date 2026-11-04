# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 96.90 (96.80–103.50) | 136.00 |
| proto-scalar | stock | read-aot | 1.74 (1.67–2.90) | 0.00 |
| proto-scalar | owned | ingest | 129.97 (112.13–198.72) | 88.00 |
| proto-scalar | owned | ingest-typed | 207.40 (173.46–220.49) | 56.00 |
| proto-scalar | owned | interpret | 58.21 (35.21–62.92) | 2.00 |
| proto-scalar | owned | peak | 3.84 (3.38–4.89) | 1.00 |
| proto-scalar | buffa | ingest | 662.81 (618.77–834.41) | 312.00 |
| proto-scalar | buffa | ingest-typed | 812.02 (504.84–1041.26) | 312.00 |
| proto-scalar | buffa | interpret | 31.55 (26.49–45.65) | 2.00 |
| proto-scalar | buffa | peak | 4.79 (4.77–8.33) | 1.00 |
| proto-bytes | stock | ingest | 65132.81 (61984.92–80411.21) | 65699.00 |
| proto-bytes | stock | read-aot | 5.18 (4.60–6.96) | 0.00 |
| proto-bytes | stock-aliased | ingest | 167.57 (80.65–183.78) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 5.60 (3.97–5.70) | 0.00 |
| proto-bytes | owned | ingest | 22910.16 (13034.15–35519.66) | 66035.00 |
| proto-bytes | owned | interpret | 28.88 (27.95–46.94) | 2.00 |
| proto-bytes | owned | peak | 8.62 (6.59–11.58) | 1.00 |
| proto-bytes | buffa | ingest | 468.52 (396.56–484.87) | 304.00 |
| proto-bytes | buffa | interpret | 36.45 (25.00–47.10) | 2.00 |
| proto-bytes | buffa | peak | 8.29 (6.19–10.30) | 1.00 |
| timestamp-json | stock | ingest | 8066.57 (5972.69–8100.42) | 6439.75 |
| timestamp-json | owned | ingest | 2837.46 (2344.83–3869.65) | 3703.75 |
| timestamp-json | owned | ingest-typed | 2294.54 (2280.03–3738.75) | 3671.75 |
| timestamp-json | buffa | ingest | 1209.56 (834.12–1340.34) | 720.00 |
| timestamp-json | buffa | ingest-typed | 851.33 (828.32–982.68) | 720.00 |
| mysql-int4 | stock | ingest | 82.59 (70.19–90.23) | 16.00 |
| mysql-int4 | stock | read-aot | 2.38 (1.74–2.53) | 0.00 |
| mysql-int4 | static | ingest | 227.56 (191.06–261.13) | 160.00 |
| mysql-int4 | static | ingest-typed | 195.37 (194.43–202.70) | 160.00 |
| mysql-int4 | static | interpret | 17.72 (17.66–17.72) | 2.00 |
| mysql-int4 | static | peak | 4.37 (3.56–5.10) | 1.00 |
| postgres-int4 | stock | ingest | 76.50 (65.19–78.40) | 16.00 |
| postgres-int4 | stock | read-aot | 1.96 (1.95–2.16) | 0.00 |
| postgres-int4 | static | ingest | 173.69 (145.02–250.61) | 160.00 |
| postgres-int4 | static | ingest-typed | 194.17 (145.25–234.18) | 160.00 |
| postgres-int4 | static | interpret | 17.48 (17.44–25.54) | 2.00 |
| postgres-int4 | static | peak | 3.68 (3.63–4.76) | 1.00 |
| proto-mixed | stock | ingest | 1637.10 (1333.81–2483.93) | 1776.75 |
| proto-mixed | stock | read-aot | 13.31 (10.04–13.74) | 0.00 |
| proto-mixed | owned | ingest | 19565.14 (16247.37–20479.40) | 3388.75 |
| proto-mixed | owned | ingest-typed | 17488.33 (13386.03–17868.86) | 3388.75 |
| proto-mixed | owned | interpret | 114.02 (91.97–164.42) | 2.00 |
| proto-mixed | owned | peak | 13.28 (13.23–18.99) | 1.00 |
| proto-mixed | buffa | ingest | 6275.32 (6250.27–8939.66) | 2195.25 |
| proto-mixed | buffa | ingest-typed | 7303.36 (7082.53–8042.01) | 2195.25 |
| proto-mixed | buffa | interpret | 99.67 (83.12–140.69) | 2.00 |
| proto-mixed | buffa | peak | 11.61 (11.29–12.22) | 1.00 |

Stock read-aot is the native-image host baseline; it has no Truffle interpreter/JIT tier.
Ingest includes parsing/materialization and an uncached projected read. Setup is excluded.
ingest-typed uses primitive readLong projections; ingest retains generic read projections.
Buffa binary input is already in unmanaged memory; owned engines start with Java byte arrays.
Buffa results are scoped borrowed views; stock-aliased borrows immutable ByteString input; stock/owned copy payloads.
The direct stock AOT baseline excludes guest interop and contract enforcement.
MySQL/Postgres measure driver INT4 decoder functions and retained scalar rows, not JDBC queries, transport, or servers.
Java heap counters exclude native allocations and compiler/background threads. Native allocation is not reported as zero.
Shared execution hardware and short samples make these diagnostic comparisons, not performance guarantees.

Binary SHA-256: `9f1c5fa9c3c59946f37f75e6c700299106af0612c6ef2e482791fb648e2ead24`
Revision: `34a2c0363cc3e47d460d5234883a10a4e06de952`
