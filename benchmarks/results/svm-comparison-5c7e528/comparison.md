# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 98.99 (96.48–164.28) | 136.00 |
| proto-scalar | stock | read-aot | 1.77 (1.77–2.95) | 0.00 |
| proto-scalar | owned | ingest | 490.42 (484.60–495.08) | 496.00 |
| proto-scalar | owned | interpret | 41.84 (40.30–54.26) | 2.00 |
| proto-scalar | owned | peak | 5.65 (5.63–5.73) | 1.00 |
| proto-scalar | buffa | ingest | 610.10 (603.44–614.43) | 592.00 |
| proto-scalar | buffa | interpret | 22.87 (22.55–36.56) | 2.00 |
| proto-scalar | buffa | peak | 6.93 (6.63–10.68) | 1.00 |
| proto-bytes | stock | ingest | 30475.07 (30115.90–32539.26) | 65699.00 |
| proto-bytes | stock | read-aot | 4.10 (4.07–6.85) | 0.00 |
| proto-bytes | stock-aliased | ingest | 131.32 (77.52–140.27) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 3.25 (3.24–3.35) | 0.00 |
| proto-bytes | owned | ingest | 11866.05 (11184.58–23584.49) | 66099.00 |
| proto-bytes | owned | interpret | 35.22 (27.10–42.88) | 2.00 |
| proto-bytes | owned | peak | 6.61 (6.59–6.72) | 1.00 |
| proto-bytes | buffa | ingest | 484.02 (476.58–500.88) | 568.00 |
| proto-bytes | buffa | interpret | 23.65 (23.39–23.89) | 2.00 |
| proto-bytes | buffa | peak | 6.22 (6.21–6.35) | 1.00 |
| timestamp-json | stock | ingest | 5500.63 (5326.81–50531.19) | 6439.75 |
| timestamp-json | owned | ingest | 2598.46 (2549.44–4034.04) | 3815.75 |
| timestamp-json | buffa | ingest | 919.27 (908.52–924.29) | 984.00 |
| mysql-int4 | stock | ingest | 61.17 (60.03–112.74) | 16.00 |
| mysql-int4 | stock | read-aot | 1.67 (1.66–2.12) | 0.00 |
| mysql-int4 | static | ingest | 153.39 (152.79–153.43) | 160.00 |
| mysql-int4 | static | interpret | 17.03 (17.01–17.09) | 2.00 |
| mysql-int4 | static | peak | 6.10 (3.64–7.33) | 1.00 |
| postgres-int4 | stock | ingest | 38.47 (37.49–76.03) | 16.00 |
| postgres-int4 | stock | read-aot | 1.68 (1.67–3.26) | 0.00 |
| postgres-int4 | static | ingest | 135.18 (134.14–220.15) | 160.00 |
| postgres-int4 | static | interpret | 17.10 (17.09–17.11) | 2.00 |
| postgres-int4 | static | peak | 3.49 (3.47–3.51) | 1.00 |

Stock read-aot is the native-image host baseline; it has no Truffle interpreter/JIT tier.
Ingest includes parsing/materialization and an uncached projected read. Setup is excluded.
Buffa binary input is already in unmanaged memory; owned engines start with Java byte arrays.
Buffa results are scoped borrowed views; stock-aliased borrows immutable ByteString input; stock/owned copy payloads.
The direct stock AOT baseline excludes guest interop and contract enforcement.
MySQL/Postgres measure driver INT4 decoder functions and retained scalar rows, not JDBC queries, transport, or servers.
Java heap counters exclude native allocations and compiler/background threads. Native allocation is not reported as zero.
Shared execution hardware and short samples make these diagnostic comparisons, not performance guarantees.

Binary SHA-256: `7ec2df5f450bc8a000351ac15895f62f50e1b8e93dde1a5c532b78021133302f`
Revision: `5c7e528d0833703cfdddf7d62151f043d3a3d76f`
