# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 75.28 (72.43–77.06) | 136.00 |
| proto-scalar | stock | read-aot | 1.84 (1.48–2.26) | 0.00 |
| proto-scalar | stock | interpret | 2.09 (2.08–2.18) | 2.00 |
| proto-scalar | stock | peak | 1.74 (1.35–1.90) | 1.00 |
| proto-scalar | owned | ingest | 102.42 (101.07–146.89) | 88.00 |
| proto-scalar | owned | ingest-typed | 104.55 (99.56–115.25) | 56.00 |
| proto-scalar | owned | interpret | 24.75 (24.66–25.01) | 2.00 |
| proto-scalar | owned | peak | 2.23 (2.17–2.87) | 1.00 |
| proto-scalar | owned | ingest-generated | 37.89 (35.91–39.81) | 56.00 |
| proto-scalar | owned | read-generated-aot | 12.09 (11.36–14.18) | 0.00 |
| proto-scalar | owned | interpret-generated | 14.20 (13.07–15.30) | 2.00 |
| proto-scalar | owned | peak-generated | 2.91 (2.83–4.08) | 1.00 |
| proto-scalar | buffa | ingest | 252.50 (238.97–318.43) | 232.00 |
| proto-scalar | buffa | ingest-typed | 321.68 (253.49–426.28) | 200.00 |
| proto-scalar | buffa | interpret | 19.29 (17.59–20.69) | 2.00 |
| proto-scalar | buffa | peak | 3.52 (2.12–4.45) | 1.00 |
| proto-scalar | buffa | ingest-generated | 188.06 (174.01–192.70) | 200.00 |
| proto-scalar | buffa | read-generated-aot | 14.42 (12.29–15.84) | 0.00 |
| proto-scalar | buffa | interpret-generated | 15.62 (12.95–17.10) | 2.00 |
| proto-scalar | buffa | peak-generated | 2.72 (2.43–3.56) | 1.00 |
| proto-bytes | stock | ingest | 23765.84 (22733.13–29482.40) | 65699.00 |
| proto-bytes | stock | read-aot | 3.48 (3.31–3.78) | 0.00 |
| proto-bytes | stock | interpret | 5.24 (5.14–6.95) | 2.00 |
| proto-bytes | stock | peak | 3.69 (3.32–3.91) | 1.00 |
| proto-bytes | stock-aliased | ingest | 75.68 (70.47–83.84) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 3.64 (3.19–3.98) | 0.00 |
| proto-bytes | stock-aliased | interpret | 4.84 (4.48–6.01) | 2.00 |
| proto-bytes | stock-aliased | peak | 3.81 (3.46–5.06) | 1.00 |
| proto-bytes | owned | ingest | 54041.09 (17595.36–56105.36) | 66035.00 |
| proto-bytes | owned | interpret | 21.66 (18.64–25.79) | 2.00 |
| proto-bytes | owned | peak | 5.34 (4.73–6.62) | 1.00 |
| proto-bytes | owned | ingest-generated | 43697.85 (33657.94–85670.51) | 66035.00 |
| proto-bytes | owned | read-generated-aot | 16.32 (14.16–18.27) | 0.00 |
| proto-bytes | owned | interpret-generated | 18.25 (18.13–20.68) | 2.00 |
| proto-bytes | owned | peak-generated | 4.81 (4.30–4.97) | 1.00 |
| proto-bytes | buffa | ingest | 269.66 (249.83–311.67) | 296.00 |
| proto-bytes | buffa | interpret | 24.60 (23.47–25.41) | 2.00 |
| proto-bytes | buffa | peak | 5.34 (4.28–5.81) | 1.00 |
| proto-bytes | buffa | ingest-generated | 180.85 (180.11–297.00) | 296.00 |
| proto-bytes | buffa | read-generated-aot | 9.07 (8.61–9.15) | 0.00 |
| proto-bytes | buffa | interpret-generated | 12.30 (10.21–14.95) | 2.00 |
| proto-bytes | buffa | peak-generated | 6.07 (5.59–6.48) | 1.00 |
| timestamp-json | stock | ingest | 3738.31 (3610.02–3893.11) | 6439.75 |
| timestamp-json | owned | ingest | 1666.36 (1551.15–1985.04) | 3703.75 |
| timestamp-json | owned | ingest-typed | 1899.77 (1530.93–2104.96) | 3671.75 |
| timestamp-json | owned | ingest-generated | 1512.63 (1437.21–1744.33) | 3671.75 |
| timestamp-json | buffa | ingest | 657.86 (644.83–825.23) | 712.00 |
| timestamp-json | buffa | ingest-typed | 809.08 (647.82–915.45) | 712.00 |
| timestamp-json | buffa | ingest-generated | 668.04 (531.08–776.94) | 712.00 |
| mysql-int4 | stock | ingest | 56.34 (40.54–57.37) | 16.00 |
| mysql-int4 | stock | read-aot | 1.25 (1.16–1.51) | 0.00 |
| mysql-int4 | stock | interpret | 2.30 (2.21–2.78) | 2.00 |
| mysql-int4 | stock | peak | 1.15 (1.14–1.74) | 1.00 |
| mysql-int4 | static | ingest | 146.80 (121.15–2893.09) | 128.00 |
| mysql-int4 | static | ingest-typed | 222.17 (138.01–224.07) | 128.00 |
| mysql-int4 | static | interpret | 16.19 (15.27–17.09) | 2.00 |
| mysql-int4 | static | peak | 3.29 (2.63–5.07) | 1.00 |
| postgres-int4 | stock | ingest | 33.62 (26.46–38.97) | 16.00 |
| postgres-int4 | stock | read-aot | 1.36 (1.30–2.33) | 0.00 |
| postgres-int4 | stock | interpret | 2.41 (2.13–3.00) | 2.00 |
| postgres-int4 | stock | peak | 1.33 (1.11–1.45) | 1.00 |
| postgres-int4 | static | ingest | 134.41 (103.66–134.93) | 128.00 |
| postgres-int4 | static | ingest-typed | 102.61 (98.62–150.65) | 128.00 |
| postgres-int4 | static | interpret | 15.36 (14.73–15.59) | 2.00 |
| postgres-int4 | static | peak | 2.70 (2.46–3.97) | 1.00 |
| proto-mixed | stock | ingest | 1092.61 (1011.16–1146.68) | 1776.75 |
| proto-mixed | stock | read-aot | 9.23 (7.52–9.71) | 0.00 |
| proto-mixed | stock | interpret-boundary | 10.71 (9.52–11.44) | 2.00 |
| proto-mixed | stock | peak-boundary | 8.82 (8.44–8.88) | 1.00 |
| proto-mixed | owned | ingest | 11023.35 (9062.57–14985.63) | 3339.50 |
| proto-mixed | owned | ingest-typed | 10136.69 (9572.82–12867.46) | 3339.50 |
| proto-mixed | owned | interpret | 75.91 (71.94–96.12) | 2.00 |
| proto-mixed | owned | peak | 11.13 (10.39–11.19) | 1.00 |
| proto-mixed | owned | ingest-generated | 10382.77 (9896.42–10731.64) | 3339.50 |
| proto-mixed | owned | read-generated-aot | 52.91 (50.05–60.57) | 0.00 |
| proto-mixed | owned | interpret-generated | 66.93 (61.55–77.10) | 2.00 |
| proto-mixed | owned | peak-generated | 13.72 (11.80–17.37) | 1.00 |
| proto-mixed | buffa | ingest | 4492.89 (4419.87–6232.03) | 1751.75 |
| proto-mixed | buffa | ingest-typed | 4179.57 (3940.07–4445.05) | 1751.75 |
| proto-mixed | buffa | interpret | 82.63 (73.17–97.86) | 2.00 |
| proto-mixed | buffa | peak | 14.94 (13.85–15.09) | 1.00 |
| proto-mixed | buffa | ingest-generated | 4107.40 (3819.57–4236.58) | 1751.75 |
| proto-mixed | buffa | read-generated-aot | 32.14 (32.10–33.63) | 0.00 |
| proto-mixed | buffa | interpret-generated | 24.52 (23.56–27.00) | 2.00 |
| proto-mixed | buffa | peak-generated | 11.27 (10.89–13.51) | 1.00 |
| proto-mixed-full | stock | ingest | 1285.51 (1227.16–1628.10) | 1776.75 |
| proto-mixed-full | owned | ingest | 13628.06 (12649.52–15028.59) | 3339.50 |
| proto-mixed-full | buffa | ingest | 8604.05 (7320.62–9242.99) | 1878.25 |

Stock read-aot is the native-image host baseline. Schema 4 adds stock guest tiers; mixed protobuf uses explicitly labeled boundary projections because its collection getters fail the SVM JIT blocklist.
Ingest includes parsing/materialization and an uncached projected read. Setup is excluded.
ingest-typed uses primitive readLong projections; ingest retains generic read projections.
Generated stages use schema-bound generated getters; read-generated-aot excludes guest calls. Original library-dispatch stages remain.
Buffa binary input is already in unmanaged memory; owned engines start with Java byte arrays.
Buffa results are scoped borrowed views; stock-aliased borrows immutable ByteString input; stock/owned copy payloads.
The direct stock AOT baseline excludes guest interop and contract enforcement.
MySQL/Postgres measure driver INT4 decoder functions and retained scalar rows, not JDBC queries, transport, or servers.
Java heap counters exclude native allocations and compiler/background threads. Native allocation is not reported as zero.
Shared execution hardware and short samples make these diagnostic comparisons, not performance guarantees.

Binary SHA-256: `3ec1e65198115e52d7da9d3bae8344ca77ba9aa28805696aad0fff3871d641ee`
Revision: `9c8a013a43cfff6c68a1b0293e8fbf6791004316`
