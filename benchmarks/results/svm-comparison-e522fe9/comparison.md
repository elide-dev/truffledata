# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 106.48 (85.03–118.38) | 136.00 |
| proto-scalar | stock | read-aot | 1.86 (1.49–2.99) | 0.00 |
| proto-scalar | stock | interpret | 2.60 (2.31–2.99) | 2.00 |
| proto-scalar | stock | peak | 1.22 (1.18–1.34) | 1.00 |
| proto-scalar | owned | ingest | 93.00 (89.46–145.03) | 88.00 |
| proto-scalar | owned | ingest-typed | 134.75 (86.05–134.81) | 56.00 |
| proto-scalar | owned | interpret | 28.93 (24.40–41.77) | 2.00 |
| proto-scalar | owned | peak | 2.48 (2.39–4.14) | 1.00 |
| proto-scalar | buffa | ingest | 557.69 (413.96–629.55) | 320.00 |
| proto-scalar | buffa | ingest-typed | 489.83 (336.07–775.22) | 320.00 |
| proto-scalar | buffa | interpret | 26.03 (19.48–30.71) | 2.00 |
| proto-scalar | buffa | peak | 4.66 (4.07–5.78) | 1.00 |
| proto-bytes | stock | ingest | 23666.90 (21743.13–37160.33) | 65699.00 |
| proto-bytes | stock | read-aot | 3.35 (2.92–3.46) | 0.00 |
| proto-bytes | stock | interpret | 5.79 (4.73–5.90) | 2.00 |
| proto-bytes | stock | peak | 4.33 (3.21–4.36) | 1.00 |
| proto-bytes | stock-aliased | ingest | 70.32 (66.50–72.65) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 3.16 (2.92–3.86) | 0.00 |
| proto-bytes | stock-aliased | interpret | 6.53 (4.64–6.99) | 2.00 |
| proto-bytes | stock-aliased | peak | 3.45 (3.31–4.39) | 1.00 |
| proto-bytes | owned | ingest | 64686.92 (24534.94–130956.34) | 66035.00 |
| proto-bytes | owned | interpret | 23.18 (18.68–30.52) | 2.00 |
| proto-bytes | owned | peak | 4.23 (4.20–5.33) | 1.00 |
| proto-bytes | buffa | ingest | 309.22 (288.82–458.65) | 312.00 |
| proto-bytes | buffa | interpret | 24.41 (21.76–35.51) | 2.00 |
| proto-bytes | buffa | peak | 5.61 (5.01–8.44) | 1.00 |
| timestamp-json | stock | ingest | 3646.49 (3462.02–3769.65) | 6439.75 |
| timestamp-json | owned | ingest | 1707.28 (1678.49–1877.67) | 3703.75 |
| timestamp-json | owned | ingest-typed | 1628.40 (1484.82–1829.63) | 3671.75 |
| timestamp-json | buffa | ingest | 1106.68 (688.83–1380.66) | 728.00 |
| timestamp-json | buffa | ingest-typed | 707.94 (662.59–727.35) | 728.00 |
| mysql-int4 | stock | ingest | 36.52 (33.48–51.58) | 16.00 |
| mysql-int4 | stock | read-aot | 1.49 (1.39–1.58) | 0.00 |
| mysql-int4 | stock | interpret | 3.08 (1.96–3.40) | 2.00 |
| mysql-int4 | stock | peak | 1.27 (1.13–1.35) | 1.00 |
| mysql-int4 | static | ingest | 141.74 (107.57–146.54) | 160.00 |
| mysql-int4 | static | ingest-typed | 136.82 (122.19–224.90) | 160.00 |
| mysql-int4 | static | interpret | 18.81 (14.44–18.82) | 2.00 |
| mysql-int4 | static | peak | 3.01 (2.25–3.36) | 1.00 |
| postgres-int4 | stock | ingest | 50.53 (38.39–51.93) | 16.00 |
| postgres-int4 | stock | read-aot | 1.62 (1.35–2.03) | 0.00 |
| postgres-int4 | stock | interpret | 2.46 (2.46–3.18) | 2.00 |
| postgres-int4 | stock | peak | 1.59 (1.05–1.62) | 1.00 |
| postgres-int4 | static | ingest | 116.78 (114.86–128.43) | 160.00 |
| postgres-int4 | static | ingest-typed | 106.13 (103.31–161.32) | 160.00 |
| postgres-int4 | static | interpret | 17.40 (15.16–22.38) | 2.00 |
| postgres-int4 | static | peak | 2.59 (2.38–3.66) | 1.00 |
| proto-mixed | stock | ingest | 1577.60 (967.37–1670.93) | 1776.75 |
| proto-mixed | stock | read-aot | 9.70 (9.68–11.13) | 0.00 |
| proto-mixed | stock | interpret-boundary | 11.93 (9.19–16.19) | 2.00 |
| proto-mixed | stock | peak-boundary | 10.83 (7.22–11.64) | 1.00 |
| proto-mixed | owned | ingest | 11455.23 (9706.34–26293.87) | 3339.50 |
| proto-mixed | owned | ingest-typed | 9984.85 (9029.70–9999.43) | 3339.50 |
| proto-mixed | owned | interpret | 68.76 (67.79–106.64) | 2.00 |
| proto-mixed | owned | peak | 13.06 (9.81–14.56) | 1.00 |
| proto-mixed | buffa | ingest | 5592.22 (5534.60–6792.45) | 1879.75 |
| proto-mixed | buffa | ingest-typed | 4361.54 (3600.88–4719.97) | 1879.75 |
| proto-mixed | buffa | interpret | 56.37 (53.73–58.48) | 2.00 |
| proto-mixed | buffa | peak | 14.42 (10.90–15.99) | 1.00 |
| proto-mixed-full | stock | ingest | 1081.01 (1047.34–1230.27) | 1776.75 |
| proto-mixed-full | owned | ingest | 16797.68 (14830.44–19094.41) | 3339.50 |
| proto-mixed-full | buffa | ingest | 4923.74 (4532.92–8577.18) | 2006.25 |

Stock read-aot is the native-image host baseline. Schema 4 adds stock guest tiers; mixed protobuf uses explicitly labeled boundary projections because its collection getters fail the SVM JIT blocklist.
Ingest includes parsing/materialization and an uncached projected read. Setup is excluded.
ingest-typed uses primitive readLong projections; ingest retains generic read projections.
Buffa binary input is already in unmanaged memory; owned engines start with Java byte arrays.
Buffa results are scoped borrowed views; stock-aliased borrows immutable ByteString input; stock/owned copy payloads.
The direct stock AOT baseline excludes guest interop and contract enforcement.
MySQL/Postgres measure driver INT4 decoder functions and retained scalar rows, not JDBC queries, transport, or servers.
Java heap counters exclude native allocations and compiler/background threads. Native allocation is not reported as zero.
Shared execution hardware and short samples make these diagnostic comparisons, not performance guarantees.

Binary SHA-256: `259f0baece38dc60f59cc2b87e3ff4662777863a3bef5da7412b645ca74fc092`
Revision: `e522fe93098a7d80f52d8da6eca785a959611ca7`
