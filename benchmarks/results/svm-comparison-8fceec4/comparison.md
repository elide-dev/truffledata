# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 69.15 (66.60–91.46) | 136.00 |
| proto-scalar | stock | read-aot | 1.42 (1.32–1.87) | 0.00 |
| proto-scalar | stock | interpret | 1.89 (1.85–2.53) | 2.00 |
| proto-scalar | stock | peak | 1.10 (1.09–1.10) | 1.00 |
| proto-scalar | owned | ingest | 81.67 (79.48–84.93) | 88.00 |
| proto-scalar | owned | ingest-typed | 78.10 (77.94–78.93) | 56.00 |
| proto-scalar | owned | interpret | 21.66 (20.86–22.44) | 2.00 |
| proto-scalar | owned | peak | 2.37 (1.90–2.62) | 1.00 |
| proto-scalar | owned | ingest-generated | 29.08 (29.03–30.75) | 56.00 |
| proto-scalar | owned | read-generated-aot | 9.46 (9.42–12.15) | 0.00 |
| proto-scalar | owned | interpret-generated | 10.52 (10.41–11.02) | 2.00 |
| proto-scalar | owned | peak-generated | 1.83 (1.82–2.75) | 1.00 |
| proto-scalar | buffa | ingest | 210.30 (209.54–295.84) | 256.00 |
| proto-scalar | buffa | ingest-typed | 211.67 (210.82–293.70) | 224.00 |
| proto-scalar | buffa | interpret | 16.28 (16.17–16.33) | 2.00 |
| proto-scalar | buffa | peak | 4.33 (4.29–5.38) | 1.00 |
| proto-scalar | buffa | ingest-generated | 153.25 (149.53–154.54) | 224.00 |
| proto-scalar | buffa | read-generated-aot | 9.78 (9.75–9.89) | 0.00 |
| proto-scalar | buffa | interpret-generated | 11.10 (10.84–12.03) | 2.00 |
| proto-scalar | buffa | peak-generated | 4.44 (4.28–4.53) | 1.00 |
| proto-bytes | stock | ingest | 25159.89 (19664.59–52670.82) | 65699.00 |
| proto-bytes | stock | read-aot | 2.83 (2.79–2.83) | 0.00 |
| proto-bytes | stock | interpret | 4.40 (4.20–4.51) | 2.00 |
| proto-bytes | stock | peak | 3.39 (3.18–4.57) | 1.00 |
| proto-bytes | stock-aliased | ingest | 50.10 (49.93–51.59) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 2.85 (2.79–2.92) | 0.00 |
| proto-bytes | stock-aliased | interpret | 3.97 (3.92–4.12) | 2.00 |
| proto-bytes | stock-aliased | peak | 2.87 (2.85–3.07) | 1.00 |
| proto-bytes | owned | ingest | 19587.75 (9334.60–48454.95) | 66035.00 |
| proto-bytes | owned | interpret | 17.11 (16.79–17.15) | 2.00 |
| proto-bytes | owned | peak | 4.88 (4.03–5.13) | 1.00 |
| proto-bytes | owned | ingest-generated | 24893.75 (7967.69–34692.96) | 66035.00 |
| proto-bytes | owned | read-generated-aot | 13.78 (13.69–15.03) | 0.00 |
| proto-bytes | owned | interpret-generated | 15.35 (15.28–15.70) | 2.00 |
| proto-bytes | owned | peak-generated | 3.83 (3.82–4.00) | 1.00 |
| proto-bytes | buffa | ingest | 229.02 (226.27–240.33) | 312.00 |
| proto-bytes | buffa | interpret | 19.22 (18.78–20.20) | 2.00 |
| proto-bytes | buffa | peak | 5.31 (5.24–6.10) | 1.00 |
| proto-bytes | buffa | ingest-generated | 166.99 (165.01–170.53) | 312.00 |
| proto-bytes | buffa | read-generated-aot | 7.45 (7.08–7.46) | 0.00 |
| proto-bytes | buffa | interpret-generated | 8.69 (8.56–10.12) | 2.00 |
| proto-bytes | buffa | peak-generated | 5.34 (4.99–5.57) | 1.00 |
| timestamp-json | stock | ingest | 3260.99 (3241.40–3352.84) | 6439.75 |
| timestamp-json | owned | ingest | 1339.74 (1330.53–1399.71) | 3703.75 |
| timestamp-json | owned | ingest-typed | 1453.56 (1391.38–2041.77) | 3671.75 |
| timestamp-json | owned | ingest-generated | 1253.64 (1247.21–1453.98) | 3671.75 |
| timestamp-json | buffa | ingest | 564.65 (533.16–613.88) | 728.00 |
| timestamp-json | buffa | ingest-typed | 589.16 (558.68–589.43) | 728.00 |
| timestamp-json | buffa | ingest-generated | 475.33 (467.06–483.60) | 728.00 |
| mysql-int4 | stock | ingest | 34.72 (32.97–35.62) | 16.00 |
| mysql-int4 | stock | read-aot | 1.09 (1.09–1.12) | 0.00 |
| mysql-int4 | stock | interpret | 1.89 (1.88–1.99) | 2.00 |
| mysql-int4 | stock | peak | 1.00 (1.00–1.01) | 1.00 |
| mysql-int4 | static | ingest | 109.21 (108.21–1573.07) | 128.00 |
| mysql-int4 | static | ingest-typed | 116.26 (116.17–141.03) | 128.00 |
| mysql-int4 | static | interpret | 13.64 (13.63–13.94) | 2.00 |
| mysql-int4 | static | peak | 2.29 (2.21–3.31) | 1.00 |
| postgres-int4 | stock | ingest | 24.93 (24.73–25.08) | 16.00 |
| postgres-int4 | stock | read-aot | 1.27 (1.19–1.31) | 0.00 |
| postgres-int4 | stock | interpret | 1.90 (1.90–2.23) | 2.00 |
| postgres-int4 | stock | peak | 1.06 (0.99–1.25) | 1.00 |
| postgres-int4 | static | ingest | 94.57 (92.77–97.62) | 128.00 |
| postgres-int4 | static | ingest-typed | 96.55 (95.16–106.90) | 128.00 |
| postgres-int4 | static | interpret | 13.51 (13.48–13.99) | 2.00 |
| postgres-int4 | static | peak | 2.22 (2.20–2.41) | 1.00 |
| proto-mixed | stock | ingest | 862.85 (856.08–1093.78) | 1776.75 |
| proto-mixed | stock | read-aot | 6.91 (6.81–7.01) | 0.00 |
| proto-mixed | stock | interpret-boundary | 7.74 (7.66–7.82) | 2.00 |
| proto-mixed | stock | peak-boundary | 7.72 (7.70–7.79) | 1.00 |
| proto-mixed | owned | ingest | 8257.52 (8229.17–8290.59) | 3339.50 |
| proto-mixed | owned | ingest-typed | 8441.39 (8319.06–17517.82) | 3339.50 |
| proto-mixed | owned | interpret | 62.03 (61.49–63.56) | 2.00 |
| proto-mixed | owned | peak | 9.67 (9.65–12.02) | 1.00 |
| proto-mixed | owned | ingest-generated | 8286.66 (8258.02–8372.06) | 3339.50 |
| proto-mixed | owned | read-generated-aot | 49.81 (49.48–51.68) | 0.00 |
| proto-mixed | owned | interpret-generated | 54.39 (50.16–55.42) | 2.00 |
| proto-mixed | owned | peak-generated | 9.24 (8.66–10.00) | 1.00 |
| proto-mixed | buffa | ingest | 3413.82 (3382.36–3551.17) | 1767.75 |
| proto-mixed | buffa | ingest-typed | 3416.20 (3351.39–3455.19) | 1767.75 |
| proto-mixed | buffa | interpret | 51.54 (49.59–57.71) | 2.00 |
| proto-mixed | buffa | peak | 12.98 (12.80–13.14) | 1.00 |
| proto-mixed | buffa | ingest-generated | 3314.17 (3208.75–3600.50) | 1767.75 |
| proto-mixed | buffa | read-generated-aot | 22.11 (20.64–25.68) | 0.00 |
| proto-mixed | buffa | interpret-generated | 22.54 (22.40–23.60) | 2.00 |
| proto-mixed | buffa | peak-generated | 10.52 (10.42–10.57) | 1.00 |
| proto-mixed-full | stock | ingest | 939.16 (917.69–1293.47) | 1776.75 |
| proto-mixed-full | owned | ingest | 10197.01 (9865.46–11872.39) | 3339.50 |
| proto-mixed-full | buffa | ingest | 4595.45 (4324.51–5291.35) | 1894.25 |

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

Binary SHA-256: `bf5837551e1b5e4019eddee79680469ce27b17ae8b935e257d7d399311bd7d9b`
Revision: `8fceec43b47feb5c249bcb5dec5225e76e5e227d`
