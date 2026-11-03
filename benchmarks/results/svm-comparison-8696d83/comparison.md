# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 97.71 (90.28–165.99) | 136.00 |
| proto-scalar | stock | read-aot | 1.67 (1.24–1.96) | 0.00 |
| proto-scalar | owned | ingest | 85.41 (80.61–155.04) | 88.00 |
| proto-scalar | owned | ingest-typed | 101.52 (86.20–113.45) | 56.00 |
| proto-scalar | owned | interpret | 29.89 (23.91–34.77) | 2.00 |
| proto-scalar | owned | peak | 2.06 (1.95–2.36) | 1.00 |
| proto-scalar | buffa | ingest | 375.64 (356.57–637.23) | 328.00 |
| proto-scalar | buffa | ingest-typed | 383.80 (380.75–529.71) | 328.00 |
| proto-scalar | buffa | interpret | 22.37 (18.02–23.00) | 2.00 |
| proto-scalar | buffa | peak | 4.60 (3.62–5.61) | 1.00 |
| proto-bytes | stock | ingest | 24006.97 (22269.09–32065.11) | 65699.00 |
| proto-bytes | stock | read-aot | 4.06 (3.50–5.09) | 0.00 |
| proto-bytes | stock-aliased | ingest | 69.98 (68.33–86.81) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 4.69 (2.85–8.74) | 0.00 |
| proto-bytes | owned | ingest | 50353.23 (44776.82–110182.97) | 66051.00 |
| proto-bytes | owned | interpret | 24.01 (23.29–29.87) | 2.00 |
| proto-bytes | owned | peak | 5.49 (4.18–7.93) | 1.00 |
| proto-bytes | buffa | ingest | 334.19 (304.48–452.42) | 320.00 |
| proto-bytes | buffa | interpret | 23.13 (19.97–23.43) | 2.00 |
| proto-bytes | buffa | peak | 5.48 (4.22–5.71) | 1.00 |
| timestamp-json | stock | ingest | 4706.53 (4164.99–5077.69) | 6439.75 |
| timestamp-json | owned | ingest | 1793.04 (1491.42–1801.97) | 3703.75 |
| timestamp-json | owned | ingest-typed | 1492.90 (1418.21–2114.07) | 3671.75 |
| timestamp-json | buffa | ingest | 630.16 (577.55–637.25) | 720.00 |
| timestamp-json | buffa | ingest-typed | 632.80 (612.15–869.63) | 720.00 |
| mysql-int4 | stock | ingest | 36.21 (34.06–48.20) | 16.00 |
| mysql-int4 | stock | read-aot | 1.48 (1.15–1.71) | 0.00 |
| mysql-int4 | static | ingest | 117.47 (101.59–148.70) | 160.00 |
| mysql-int4 | static | ingest-typed | 128.67 (117.07–129.53) | 160.00 |
| mysql-int4 | static | interpret | 14.84 (12.84–16.61) | 2.00 |
| mysql-int4 | static | peak | 3.82 (3.64–4.62) | 1.00 |
| postgres-int4 | stock | ingest | 32.95 (26.37–39.92) | 16.00 |
| postgres-int4 | stock | read-aot | 1.18 (1.14–1.46) | 0.00 |
| postgres-int4 | static | ingest | 149.24 (100.01–182.53) | 160.00 |
| postgres-int4 | static | ingest-typed | 146.97 (111.08–2024.03) | 160.00 |
| postgres-int4 | static | interpret | 19.80 (14.52–22.02) | 2.00 |
| postgres-int4 | static | peak | 4.05 (2.31–7.33) | 1.00 |

Stock read-aot is the native-image host baseline; it has no Truffle interpreter/JIT tier.
Ingest includes parsing/materialization and an uncached projected read. Setup is excluded.
ingest-typed uses primitive readLong projections; ingest retains generic read projections.
Buffa binary input is already in unmanaged memory; owned engines start with Java byte arrays.
Buffa results are scoped borrowed views; stock-aliased borrows immutable ByteString input; stock/owned copy payloads.
The direct stock AOT baseline excludes guest interop and contract enforcement.
MySQL/Postgres measure driver INT4 decoder functions and retained scalar rows, not JDBC queries, transport, or servers.
Java heap counters exclude native allocations and compiler/background threads. Native allocation is not reported as zero.
Shared execution hardware and short samples make these diagnostic comparisons, not performance guarantees.

Binary SHA-256: `49eb51412f739cb2334db21c974d433cb247fd859242399286e05b81666d4699`
Revision: `8696d83fa546b8b83e179e2fbe8f41a8dc69c218`
