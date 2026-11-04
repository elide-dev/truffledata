# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 71.86 (71.53–73.23) | 136.00 |
| proto-scalar | stock | read-aot | 1.33 (1.31–1.33) | 0.00 |
| proto-scalar | stock | interpret | 2.01 (1.98–2.09) | 2.00 |
| proto-scalar | stock | peak | 1.15 (1.12–1.16) | 1.00 |
| proto-scalar | owned | ingest | 87.79 (81.28–88.12) | 88.00 |
| proto-scalar | owned | ingest-typed | 83.47 (80.73–120.50) | 56.00 |
| proto-scalar | owned | interpret | 21.94 (20.91–22.26) | 2.00 |
| proto-scalar | owned | peak | 1.97 (1.84–1.99) | 1.00 |
| proto-scalar | buffa | ingest | 368.36 (367.65–462.57) | 320.00 |
| proto-scalar | buffa | ingest-typed | 353.70 (346.15–494.10) | 320.00 |
| proto-scalar | buffa | interpret | 19.18 (18.19–19.38) | 2.00 |
| proto-scalar | buffa | peak | 4.10 (3.93–4.38) | 1.00 |
| proto-bytes | stock | ingest | 20288.48 (20283.08–52956.16) | 65699.00 |
| proto-bytes | stock | read-aot | 2.97 (2.95–3.06) | 0.00 |
| proto-bytes | stock | interpret | 4.68 (4.49–5.12) | 2.00 |
| proto-bytes | stock | peak | 3.21 (3.17–3.45) | 1.00 |
| proto-bytes | stock-aliased | ingest | 54.54 (50.74–95.49) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 2.71 (2.70–2.78) | 0.00 |
| proto-bytes | stock-aliased | interpret | 4.06 (4.00–4.26) | 2.00 |
| proto-bytes | stock-aliased | peak | 2.85 (2.82–2.94) | 1.00 |
| proto-bytes | owned | ingest | 33552.88 (19325.81–66797.99) | 66035.00 |
| proto-bytes | owned | interpret | 17.87 (17.43–18.02) | 2.00 |
| proto-bytes | owned | peak | 4.35 (4.06–4.49) | 1.00 |
| proto-bytes | buffa | ingest | 295.05 (287.61–296.07) | 312.00 |
| proto-bytes | buffa | interpret | 18.91 (18.76–18.93) | 2.00 |
| proto-bytes | buffa | peak | 4.00 (3.96–4.01) | 1.00 |
| timestamp-json | stock | ingest | 3401.07 (3317.91–3426.55) | 6439.75 |
| timestamp-json | owned | ingest | 1847.30 (1599.66–2222.00) | 3703.75 |
| timestamp-json | owned | ingest-typed | 1417.72 (1395.74–1577.29) | 3671.75 |
| timestamp-json | buffa | ingest | 650.73 (608.96–755.50) | 728.00 |
| timestamp-json | buffa | ingest-typed | 670.78 (611.19–850.19) | 728.00 |
| mysql-int4 | stock | ingest | 36.87 (34.76–61.65) | 16.00 |
| mysql-int4 | stock | read-aot | 1.14 (1.11–1.15) | 0.00 |
| mysql-int4 | stock | interpret | 2.02 (2.02–2.11) | 2.00 |
| mysql-int4 | stock | peak | 1.05 (1.01–1.05) | 1.00 |
| mysql-int4 | static | ingest | 111.60 (111.60–123.43) | 160.00 |
| mysql-int4 | static | ingest-typed | 109.09 (108.09–112.72) | 160.00 |
| mysql-int4 | static | interpret | 14.24 (13.42–14.58) | 2.00 |
| mysql-int4 | static | peak | 2.42 (2.27–3.75) | 1.00 |
| postgres-int4 | stock | ingest | 24.98 (24.20–25.21) | 16.00 |
| postgres-int4 | stock | read-aot | 1.26 (1.18–1.39) | 0.00 |
| postgres-int4 | stock | interpret | 2.00 (1.95–2.08) | 2.00 |
| postgres-int4 | stock | peak | 1.03 (1.00–1.09) | 1.00 |
| postgres-int4 | static | ingest | 99.89 (97.86–101.87) | 160.00 |
| postgres-int4 | static | ingest-typed | 100.33 (97.49–123.86) | 160.00 |
| postgres-int4 | static | interpret | 14.17 (13.59–16.61) | 2.00 |
| postgres-int4 | static | peak | 2.51 (2.30–2.61) | 1.00 |
| proto-mixed | stock | ingest | 944.87 (871.17–949.07) | 1776.75 |
| proto-mixed | stock | read-aot | 7.74 (7.53–7.93) | 0.00 |
| proto-mixed | stock | interpret-boundary | 9.97 (8.35–12.43) | 2.00 |
| proto-mixed | stock | peak-boundary | 7.57 (7.40–8.76) | 1.00 |
| proto-mixed | owned | ingest | 12122.44 (8402.78–52072.51) | 3339.50 |
| proto-mixed | owned | ingest-typed | 8378.54 (8145.63–9539.10) | 3339.50 |
| proto-mixed | owned | interpret | 66.80 (65.19–72.65) | 2.00 |
| proto-mixed | owned | peak | 9.74 (9.08–12.02) | 1.00 |
| proto-mixed | buffa | ingest | 3878.05 (3832.19–4222.00) | 1767.75 |
| proto-mixed | buffa | ingest-typed | 3898.84 (3504.15–4850.82) | 1767.75 |
| proto-mixed | buffa | interpret | 52.49 (51.86–60.89) | 2.00 |
| proto-mixed | buffa | peak | 10.21 (9.68–10.46) | 1.00 |
| proto-mixed-full | stock | ingest | 1015.95 (962.78–1163.75) | 1776.75 |
| proto-mixed-full | owned | ingest | 10859.72 (10093.53–16282.44) | 3339.50 |
| proto-mixed-full | buffa | ingest | 4647.38 (4620.02–4983.65) | 1894.25 |

Stock read-aot is the native-image host baseline. Schema 4 adds stock guest tiers; mixed protobuf uses explicitly labeled boundary projections because its collection getters fail the SVM JIT blocklist.
Ingest includes parsing/materialization and an uncached projected read. Setup is excluded.
ingest-typed uses primitive readLong projections; ingest retains generic read projections.
Buffa binary input is already in unmanaged memory; owned engines start with Java byte arrays.
Buffa results are scoped borrowed views; stock-aliased borrows immutable ByteString input; stock/owned copy payloads.
The direct stock AOT baseline excludes guest interop and contract enforcement.
MySQL/Postgres measure driver INT4 decoder functions and retained scalar rows, not JDBC queries, transport, or servers.
Java heap counters exclude native allocations and compiler/background threads. Native allocation is not reported as zero.
Shared execution hardware and short samples make these diagnostic comparisons, not performance guarantees.

Binary SHA-256: `f60c89eca8600d1914a0b71c562606e8cbdbc79de4109c15e42b70809d5ffb03`
Revision: `98a8c6304a408766e9a25806a164ee1b3e1ab565`
