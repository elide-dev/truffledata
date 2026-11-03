# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 167.32 (101.29–255.85) | 136.00 |
| proto-scalar | stock | read-aot | 2.82 (2.73–3.04) | 0.00 |
| proto-scalar | owned | ingest | 678.97 (587.15–846.16) | 448.00 |
| proto-scalar | owned | interpret | 70.80 (66.62–72.43) | 2.00 |
| proto-scalar | owned | peak | 9.34 (6.30–10.26) | 1.00 |
| proto-scalar | buffa | ingest | 1011.72 (997.65–1095.47) | 400.00 |
| proto-scalar | buffa | interpret | 40.27 (35.56–41.07) | 2.00 |
| proto-scalar | buffa | peak | 13.23 (12.99–13.79) | 1.00 |
| proto-bytes | stock | ingest | 50733.79 (45940.43–51021.29) | 65699.00 |
| proto-bytes | stock | read-aot | 6.43 (6.12–7.20) | 0.00 |
| proto-bytes | stock-aliased | ingest | 161.99 (142.07–164.25) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 4.47 (3.45–5.65) | 0.00 |
| proto-bytes | owned | ingest | 28785.15 (16530.98–50749.52) | 66051.00 |
| proto-bytes | owned | interpret | 46.08 (34.53–48.60) | 2.00 |
| proto-bytes | owned | peak | 14.35 (7.38–17.98) | 1.00 |
| proto-bytes | buffa | ingest | 874.57 (851.46–1215.80) | 392.00 |
| proto-bytes | buffa | interpret | 26.92 (26.16–48.72) | 2.00 |
| proto-bytes | buffa | peak | 10.09 (6.38–12.00) | 1.00 |
| timestamp-json | stock | ingest | 6643.27 (5577.93–8344.91) | 6439.75 |
| timestamp-json | owned | ingest | 3933.75 (3861.21–4418.27) | 3815.75 |
| timestamp-json | buffa | ingest | 1631.63 (1344.39–2100.80) | 792.00 |
| mysql-int4 | stock | ingest | 113.26 (106.11–116.93) | 16.00 |
| mysql-int4 | stock | read-aot | 2.84 (1.85–3.18) | 0.00 |
| mysql-int4 | static | ingest | 309.11 (297.74–337.44) | 160.00 |
| mysql-int4 | static | interpret | 29.56 (18.03–32.92) | 2.00 |
| mysql-int4 | static | peak | 6.34 (3.71–7.76) | 1.00 |
| postgres-int4 | stock | ingest | 70.15 (57.22–86.30) | 16.00 |
| postgres-int4 | stock | read-aot | 2.45 (1.68–2.97) | 0.00 |
| postgres-int4 | static | ingest | 299.12 (185.19–532.81) | 160.00 |
| postgres-int4 | static | interpret | 28.76 (19.72–32.89) | 2.00 |
| postgres-int4 | static | peak | 5.79 (5.30–7.29) | 1.00 |

Stock read-aot is the native-image host baseline; it has no Truffle interpreter/JIT tier.
Ingest includes parsing/materialization and an uncached projected read. Setup is excluded.
Buffa binary input is already in unmanaged memory; owned engines start with Java byte arrays.
Buffa results are scoped borrowed views; stock-aliased borrows immutable ByteString input; stock/owned copy payloads.
The direct stock AOT baseline excludes guest interop and contract enforcement.
MySQL/Postgres measure driver INT4 decoder functions and retained scalar rows, not JDBC queries, transport, or servers.
Java heap counters exclude native allocations and compiler/background threads. Native allocation is not reported as zero.
Shared execution hardware and short samples make these diagnostic comparisons, not performance guarantees.

Binary SHA-256: `f38a7d5b006aa20c9c6244bed846ff04ced4307056930b9332bc909dfa2e54fd`
Revision: `e194e541384bfaf5a98bae1643d95a9b3204f7cd`
