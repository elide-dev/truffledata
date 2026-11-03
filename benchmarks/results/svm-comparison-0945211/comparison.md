# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 66.98 (62.56–99.82) | 136.00 |
| proto-scalar | stock | read-aot | 1.39 (1.39–2.24) | 0.00 |
| proto-scalar | owned | ingest | 80.59 (76.94–136.12) | 88.00 |
| proto-scalar | owned | interpret | 23.18 (22.86–35.39) | 2.00 |
| proto-scalar | owned | peak | 2.20 (2.14–2.51) | 1.00 |
| proto-scalar | buffa | ingest | 352.93 (336.50–363.56) | 400.00 |
| proto-scalar | buffa | interpret | 17.32 (16.52–17.49) | 2.00 |
| proto-scalar | buffa | peak | 4.51 (4.35–4.97) | 1.00 |
| proto-bytes | stock | ingest | 20783.92 (20706.44–25902.17) | 65699.00 |
| proto-bytes | stock | read-aot | 2.97 (2.95–3.25) | 0.00 |
| proto-bytes | stock-aliased | ingest | 54.23 (51.70–73.87) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 3.23 (3.18–4.53) | 0.00 |
| proto-bytes | owned | ingest | 41881.35 (33369.69–96982.96) | 66051.00 |
| proto-bytes | owned | interpret | 21.06 (18.84–22.13) | 2.00 |
| proto-bytes | owned | peak | 6.06 (4.36–6.85) | 1.00 |
| proto-bytes | buffa | ingest | 486.43 (283.53–540.38) | 392.00 |
| proto-bytes | buffa | interpret | 19.26 (17.71–21.44) | 2.00 |
| proto-bytes | buffa | peak | 4.98 (4.93–5.88) | 1.00 |
| timestamp-json | stock | ingest | 3659.08 (3325.33–116943.07) | 6439.75 |
| timestamp-json | owned | ingest | 1605.68 (1487.43–1637.83) | 3703.75 |
| timestamp-json | buffa | ingest | 690.61 (641.04–757.98) | 792.00 |
| mysql-int4 | stock | ingest | 33.95 (33.15–44.78) | 16.00 |
| mysql-int4 | stock | read-aot | 1.18 (1.12–1.19) | 0.00 |
| mysql-int4 | static | ingest | 110.32 (106.53–123.17) | 160.00 |
| mysql-int4 | static | interpret | 15.73 (13.26–17.51) | 2.00 |
| mysql-int4 | static | peak | 2.37 (2.34–11.91) | 1.00 |
| postgres-int4 | stock | ingest | 23.88 (23.81–29.05) | 16.00 |
| postgres-int4 | stock | read-aot | 1.56 (1.16–1.96) | 0.00 |
| postgres-int4 | static | ingest | 102.16 (96.51–106.31) | 160.00 |
| postgres-int4 | static | interpret | 14.58 (14.57–20.44) | 2.00 |
| postgres-int4 | static | peak | 2.47 (2.36–2.77) | 1.00 |

Stock read-aot is the native-image host baseline; it has no Truffle interpreter/JIT tier.
Ingest includes parsing/materialization and an uncached projected read. Setup is excluded.
Buffa binary input is already in unmanaged memory; owned engines start with Java byte arrays.
Buffa results are scoped borrowed views; stock-aliased borrows immutable ByteString input; stock/owned copy payloads.
The direct stock AOT baseline excludes guest interop and contract enforcement.
MySQL/Postgres measure driver INT4 decoder functions and retained scalar rows, not JDBC queries, transport, or servers.
Java heap counters exclude native allocations and compiler/background threads. Native allocation is not reported as zero.
Shared execution hardware and short samples make these diagnostic comparisons, not performance guarantees.

Binary SHA-256: `8e6735475c1d8e71a5dfa421fe19648b11564ce70e532b9670ea743728c2b038`
Revision: `0945211a4a9dd55f9997d17721438d8de8135c30`
