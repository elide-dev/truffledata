# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 65.23 (65.09–66.36) | 136.00 |
| proto-scalar | stock | read-aot | 1.32 (1.30–1.44) | 0.00 |
| proto-scalar | stock | interpret | 2.12 (1.90–24.94) | 2.00 |
| proto-scalar | stock | peak | 1.09 (1.09–1.09) | 1.00 |
| proto-scalar | stock | serialize | 20.20 (19.30–22.86) | 56.00 |
| proto-scalar | stock | project-aot | 44.50 (43.64–1020.19) | 136.00 |
| proto-scalar | stock | project-interpret | 44.40 (44.27–45.67) | 138.00 |
| proto-scalar | stock | project-peak | 46.56 (46.52–47.98) | 137.00 |
| proto-scalar | owned | ingest | 80.33 (79.66–81.03) | 88.00 |
| proto-scalar | owned | ingest-typed | 77.69 (76.72–78.32) | 56.00 |
| proto-scalar | owned | interpret | 21.23 (21.03–21.41) | 2.00 |
| proto-scalar | owned | peak | 1.92 (1.79–2.00) | 1.00 |
| proto-scalar | owned | ingest-generated | 29.21 (29.11–29.54) | 56.00 |
| proto-scalar | owned | read-generated-aot | 9.54 (9.49–10.09) | 0.00 |
| proto-scalar | owned | interpret-generated | 10.62 (10.49–10.73) | 2.00 |
| proto-scalar | owned | peak-generated | 1.86 (1.83–1.87) | 1.00 |
| proto-scalar | owned | serialize | 97.00 (95.57–136.04) | 136.00 |
| proto-scalar | buffa | ingest | 125.59 (125.42–134.65) | 176.00 |
| proto-scalar | buffa | ingest-typed | 123.04 (120.90–127.75) | 144.00 |
| proto-scalar | buffa | interpret | 15.92 (15.79–15.96) | 2.00 |
| proto-scalar | buffa | peak | 2.03 (2.02–10.81) | 1.00 |
| proto-scalar | buffa | ingest-generated | 81.40 (80.04–82.45) | 144.00 |
| proto-scalar | buffa | read-generated-aot | 10.82 (10.63–10.97) | 0.00 |
| proto-scalar | buffa | interpret-generated | 12.67 (11.80–17.75) | 2.00 |
| proto-scalar | buffa | peak-generated | 2.08 (2.07–2.11) | 1.00 |
| proto-scalar | buffa | serialize | 16.73 (16.57–16.81) | 24.00 |
| proto-scalar | generated-owned | ingest | 24.11 (24.06–24.27) | 80.00 |
| proto-scalar | generated-owned | read-aot | 2.93 (2.93–3.01) | 0.00 |
| proto-scalar | generated-owned | interpret | 4.48 (4.46–4.51) | 2.00 |
| proto-scalar | generated-owned | peak | 1.25 (1.24–1.27) | 1.00 |
| proto-scalar | generated-owned | serialize | 25.11 (25.07–25.19) | 40.00 |
| proto-scalar | generated-owned | project-aot | 9.44 (9.08–9.57) | 0.00 |
| proto-scalar | generated-owned | project-interpret | 10.12 (10.11–11.39) | 2.00 |
| proto-scalar | generated-owned | project-peak | 10.80 (10.50–11.40) | 1.00 |
| proto-bytes | stock | ingest | 20279.49 (19193.53–20729.34) | 65699.00 |
| proto-bytes | stock | read-aot | 2.96 (2.88–10.67) | 0.00 |
| proto-bytes | stock | interpret | 4.25 (4.14–4.29) | 2.00 |
| proto-bytes | stock | peak | 3.17 (3.16–3.22) | 1.00 |
| proto-bytes | stock | serialize | 21407.22 (3984.23–31974.10) | 65599.00 |
| proto-bytes | stock-aliased | ingest | 50.10 (49.83–50.82) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 2.90 (2.72–15.25) | 0.00 |
| proto-bytes | stock-aliased | interpret | 3.98 (3.93–4.60) | 2.00 |
| proto-bytes | stock-aliased | peak | 6.20 (3.23–15.94) | 1.00 |
| proto-bytes | stock-aliased | serialize | 23769.79 (4315.24–40204.79) | 65599.00 |
| proto-bytes | owned | ingest | 20631.85 (4818.19–40160.70) | 66035.00 |
| proto-bytes | owned | interpret | 17.11 (16.89–17.54) | 2.00 |
| proto-bytes | owned | peak | 3.95 (3.94–4.10) | 1.00 |
| proto-bytes | owned | ingest-generated | 5351.82 (4748.70–25484.97) | 66035.00 |
| proto-bytes | owned | read-generated-aot | 14.24 (13.85–15.32) | 0.00 |
| proto-bytes | owned | interpret-generated | 17.01 (15.30–70.92) | 2.00 |
| proto-bytes | owned | peak-generated | 3.91 (3.71–3.96) | 1.00 |
| proto-bytes | owned | serialize | 4596.94 (4583.02–7258.04) | 65615.00 |
| proto-bytes | buffa | ingest | 254.46 (252.42–258.38) | 304.00 |
| proto-bytes | buffa | interpret | 18.33 (18.23–18.51) | 2.00 |
| proto-bytes | buffa | peak | 3.82 (3.78–3.90) | 1.00 |
| proto-bytes | buffa | ingest-generated | 178.45 (174.36–178.98) | 304.00 |
| proto-bytes | buffa | read-generated-aot | 8.97 (8.78–9.54) | 0.00 |
| proto-bytes | buffa | interpret-generated | 10.34 (9.95–10.44) | 2.00 |
| proto-bytes | buffa | peak-generated | 4.19 (4.19–4.23) | 1.00 |
| proto-bytes | buffa | serialize | 27449.77 (25139.29–31923.41) | 65567.00 |
| proto-bytes | generated-owned | ingest | 17369.25 (4082.64–79677.32) | 65651.00 |
| proto-bytes | generated-owned | read-aot | 3.56 (3.54–4.57) | 0.00 |
| proto-bytes | generated-owned | interpret | 4.93 (4.89–5.14) | 2.00 |
| proto-bytes | generated-owned | peak | 1.65 (1.52–2.05) | 1.00 |
| proto-bytes | generated-owned | serialize | 4497.63 (4314.49–4511.48) | 65583.00 |
| timestamp-json | stock | ingest | 3289.91 (3274.22–3621.51) | 6439.75 |
| timestamp-json | owned | ingest | 1850.93 (1409.04–3431.05) | 3703.75 |
| timestamp-json | owned | ingest-typed | 1545.51 (1276.72–2136.39) | 3671.75 |
| timestamp-json | owned | ingest-generated | 1276.43 (1262.95–1277.09) | 3671.75 |
| timestamp-json | buffa | ingest | 578.39 (568.66–605.53) | 720.00 |
| timestamp-json | buffa | ingest-typed | 580.53 (568.75–594.83) | 720.00 |
| timestamp-json | buffa | ingest-generated | 518.10 (516.79–9908.81) | 720.00 |
| mysql-int4 | stock | ingest | 35.47 (33.10–36.30) | 16.00 |
| mysql-int4 | stock | read-aot | 1.12 (1.10–1.97) | 0.00 |
| mysql-int4 | stock | interpret | 1.92 (1.91–1.96) | 2.00 |
| mysql-int4 | stock | peak | 1.00 (1.00–1.00) | 1.00 |
| mysql-int4 | static | ingest | 114.35 (100.63–118.11) | 128.00 |
| mysql-int4 | static | ingest-typed | 102.49 (98.82–107.21) | 128.00 |
| mysql-int4 | static | interpret | 14.25 (13.38–23.52) | 2.00 |
| mysql-int4 | static | peak | 2.23 (2.19–2.31) | 1.00 |
| postgres-int4 | stock | ingest | 23.44 (23.42–25.29) | 16.00 |
| postgres-int4 | stock | read-aot | 1.22 (1.21–1.27) | 0.00 |
| postgres-int4 | stock | interpret | 1.95 (1.89–2.04) | 2.00 |
| postgres-int4 | stock | peak | 1.02 (1.00–1.27) | 1.00 |
| postgres-int4 | static | ingest | 93.38 (92.84–96.28) | 128.00 |
| postgres-int4 | static | ingest-typed | 94.24 (94.19–106.26) | 128.00 |
| postgres-int4 | static | interpret | 13.46 (13.46–14.58) | 2.00 |
| postgres-int4 | static | peak | 2.22 (2.19–18.29) | 1.00 |
| proto-mixed | stock | ingest | 869.35 (862.52–876.99) | 1776.75 |
| proto-mixed | stock | read-aot | 6.89 (6.74–7.88) | 0.00 |
| proto-mixed | stock | interpret-boundary | 7.80 (7.68–7.87) | 2.00 |
| proto-mixed | stock | peak-boundary | 7.07 (7.05–9.84) | 1.00 |
| proto-mixed | owned | ingest | 8051.23 (8032.73–10821.72) | 3339.50 |
| proto-mixed | owned | ingest-typed | 8008.15 (7970.06–8034.21) | 3339.50 |
| proto-mixed | owned | interpret | 61.48 (61.45–62.48) | 2.00 |
| proto-mixed | owned | peak | 9.06 (9.03–9.09) | 1.00 |
| proto-mixed | owned | ingest-generated | 8051.76 (7758.74–24638.92) | 3339.50 |
| proto-mixed | owned | read-generated-aot | 46.66 (46.51–46.69) | 0.00 |
| proto-mixed | owned | interpret-generated | 48.05 (47.54–172.12) | 2.00 |
| proto-mixed | owned | peak-generated | 8.73 (8.65–8.82) | 1.00 |
| proto-mixed | buffa | ingest | 3489.29 (3378.35–3608.05) | 1759.75 |
| proto-mixed | buffa | ingest-typed | 3464.92 (3430.34–3676.17) | 1759.75 |
| proto-mixed | buffa | interpret | 52.17 (51.52–53.20) | 2.00 |
| proto-mixed | buffa | peak | 9.80 (9.58–16.52) | 1.00 |
| proto-mixed | buffa | ingest-generated | 3449.60 (3242.08–8627.37) | 1759.75 |
| proto-mixed | buffa | read-generated-aot | 22.68 (22.63–22.75) | 0.00 |
| proto-mixed | buffa | interpret-generated | 23.99 (23.87–24.32) | 2.00 |
| proto-mixed | buffa | peak-generated | 9.17 (9.05–9.18) | 1.00 |
| proto-mixed-full | stock | ingest | 936.99 (935.82–971.39) | 1776.75 |
| proto-mixed-full | owned | ingest | 9699.78 (9596.76–10301.14) | 3339.50 |
| proto-mixed-full | buffa | ingest | 4518.23 (4400.05–4519.90) | 1886.25 |

Stock read-aot is the native-image host baseline. Schema 4 adds stock guest tiers; mixed protobuf uses explicitly labeled boundary projections because its collection getters fail the SVM JIT blocklist.
Ingest includes parsing/materialization and an uncached projected read. Setup is excluded.
ingest-typed uses primitive readLong projections; ingest retains generic read projections.
Generated stages use schema-bound generated getters; read-generated-aot excludes guest calls. Original library-dispatch stages remain.
Schema 7 adds generated-owned concrete scalar/bytes records and serialization. Serialization emits a fresh byte array; Buffa binary views forward the validated original wire, while owned and stock messages encode fields. These operations have equivalent wire semantics, but differ in work performed.
Schema 9 measures parse-and-project with both stock and generated parsing inline in guest compilation; only invalid-input exception construction crosses a boundary. Both engines use a 10,000 graph-size budget. AOT and interpreter measurements use the same operation.
Buffa binary input is already in unmanaged memory; owned engines start with Java byte arrays.
Buffa results are scoped borrowed views; stock-aliased borrows immutable ByteString input; stock/owned copy payloads.
The direct stock AOT baseline excludes guest interop and contract enforcement.
MySQL/Postgres measure driver INT4 decoder functions and retained scalar rows, not JDBC queries, transport, or servers.
Java heap counters exclude native allocations and compiler/background threads. Native allocation is not reported as zero.
Shared execution hardware and short samples make these diagnostic comparisons, not performance guarantees.

Binary SHA-256: `9be58a30cad56d60964b614fcd62cc489535b89cb2d923bf842fbb5f4b6ea5f1`
Revision: `646754d1a03307269a9744182eff78f92648eb59`
