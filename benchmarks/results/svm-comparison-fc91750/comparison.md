# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 156.35 (108.78–179.09) | 136.00 |
| proto-scalar | stock | read-aot | 2.99 (2.65–3.33) | 0.00 |
| proto-scalar | owned | ingest | 192.94 (112.10–207.62) | 88.00 |
| proto-scalar | owned | ingest-typed | 137.79 (115.60–226.13) | 56.00 |
| proto-scalar | owned | interpret | 52.07 (44.29–60.49) | 2.00 |
| proto-scalar | owned | peak | 6.30 (6.07–6.48) | 1.00 |
| proto-scalar | buffa | ingest | 1140.30 (1134.98–1208.50) | 312.00 |
| proto-scalar | buffa | ingest-typed | 1124.84 (991.62–1232.02) | 312.00 |
| proto-scalar | buffa | interpret | 33.57 (32.82–38.87) | 2.00 |
| proto-scalar | buffa | peak | 8.55 (8.05–9.08) | 1.00 |
| proto-bytes | stock | ingest | 69789.89 (56284.11–76928.13) | 65699.00 |
| proto-bytes | stock | read-aot | 6.13 (6.04–6.70) | 0.00 |
| proto-bytes | stock-aliased | ingest | 148.55 (96.39–209.56) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 6.08 (4.25–6.18) | 0.00 |
| proto-bytes | owned | ingest | 26626.80 (15600.25–31263.65) | 66051.00 |
| proto-bytes | owned | interpret | 41.57 (37.12–46.33) | 2.00 |
| proto-bytes | owned | peak | 10.86 (10.41–12.48) | 1.00 |
| proto-bytes | buffa | ingest | 647.47 (405.30–811.59) | 304.00 |
| proto-bytes | buffa | interpret | 46.09 (25.34–46.35) | 2.00 |
| proto-bytes | buffa | peak | 8.86 (5.94–10.82) | 1.00 |
| timestamp-json | stock | ingest | 8151.05 (7858.78–9080.02) | 6439.75 |
| timestamp-json | owned | ingest | 3166.55 (2532.11–3330.24) | 3703.75 |
| timestamp-json | owned | ingest-typed | 3829.48 (2872.48–4348.27) | 3671.75 |
| timestamp-json | buffa | ingest | 1230.85 (939.31–1586.55) | 720.00 |
| timestamp-json | buffa | ingest-typed | 1371.84 (821.88–1499.22) | 720.00 |
| mysql-int4 | stock | ingest | 102.16 (93.50–102.60) | 16.00 |
| mysql-int4 | stock | read-aot | 2.92 (2.68–2.99) | 0.00 |
| mysql-int4 | static | ingest | 190.07 (175.84–258.72) | 160.00 |
| mysql-int4 | static | ingest-typed | 358.69 (326.41–399.86) | 160.00 |
| mysql-int4 | static | interpret | 24.36 (23.35–35.17) | 2.00 |
| mysql-int4 | static | peak | 6.43 (6.19–6.69) | 1.00 |
| postgres-int4 | stock | ingest | 73.38 (39.88–74.26) | 16.00 |
| postgres-int4 | stock | read-aot | 2.84 (2.80–5.97) | 0.00 |
| postgres-int4 | static | ingest | 258.82 (253.39–304.76) | 160.00 |
| postgres-int4 | static | ingest-typed | 257.15 (250.01–267.73) | 160.00 |
| postgres-int4 | static | interpret | 26.20 (19.50–33.82) | 2.00 |
| postgres-int4 | static | peak | 6.36 (6.17–6.90) | 1.00 |
| proto-mixed | stock | ingest | 2035.74 (1956.96–2408.01) | 1776.75 |
| proto-mixed | stock | read-aot | 10.96 (8.09–12.05) | 0.00 |
| proto-mixed | owned | ingest | 28195.80 (21058.00–29002.42) | 3562.50 |
| proto-mixed | owned | ingest-typed | 28269.04 (24461.11–31699.51) | 3562.50 |
| proto-mixed | owned | interpret | 144.73 (131.61–159.78) | 2.00 |
| proto-mixed | owned | peak | 23.36 (17.60–48.47) | 1.00 |
| proto-mixed | buffa | ingest | 13422.01 (12661.66–13751.27) | 3983.50 |
| proto-mixed | buffa | ingest-typed | 9330.53 (8908.83–14479.66) | 3983.50 |
| proto-mixed | buffa | interpret | 128.49 (87.30–132.32) | 2.00 |
| proto-mixed | buffa | peak | 21.26 (11.77–22.93) | 1.00 |

Stock read-aot is the native-image host baseline; it has no Truffle interpreter/JIT tier.
Ingest includes parsing/materialization and an uncached projected read. Setup is excluded.
ingest-typed uses primitive readLong projections; ingest retains generic read projections.
Buffa binary input is already in unmanaged memory; owned engines start with Java byte arrays.
Buffa results are scoped borrowed views; stock-aliased borrows immutable ByteString input; stock/owned copy payloads.
The direct stock AOT baseline excludes guest interop and contract enforcement.
MySQL/Postgres measure driver INT4 decoder functions and retained scalar rows, not JDBC queries, transport, or servers.
Java heap counters exclude native allocations and compiler/background threads. Native allocation is not reported as zero.
Shared execution hardware and short samples make these diagnostic comparisons, not performance guarantees.

Binary SHA-256: `91be02a7ee93344baa660eecb073576f4d0776d23944551c7665a53763d86be0`
Revision: `fc917502e7dbf931a301b56131b0e9981ac0313d`
