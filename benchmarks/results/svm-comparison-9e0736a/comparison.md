# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 95.90 (91.84–116.91) | 136.00 |
| proto-scalar | stock | read-aot | 1.97 (1.75–2.07) | 0.00 |
| proto-scalar | stock | interpret | 2.71 (1.82–3.37) | 2.00 |
| proto-scalar | stock | peak | 1.83 (1.10–1.86) | 1.00 |
| proto-scalar | stock | serialize | 33.44 (32.64–39.85) | 56.00 |
| proto-scalar | stock | project-aot | 75.91 (64.74–78.19) | 136.00 |
| proto-scalar | stock | project-interpret | 65.04 (58.65–97.95) | 138.00 |
| proto-scalar | stock | project-peak | 67.97 (51.88–89.76) | 137.00 |
| proto-scalar | owned | ingest | 110.79 (98.17–124.24) | 88.00 |
| proto-scalar | owned | ingest-typed | 130.80 (116.18–133.12) | 56.00 |
| proto-scalar | owned | interpret | 31.77 (27.65–46.09) | 2.00 |
| proto-scalar | owned | peak | 2.46 (1.70–3.23) | 1.00 |
| proto-scalar | owned | ingest-generated | 43.11 (36.07–59.67) | 56.00 |
| proto-scalar | owned | read-generated-aot | 11.69 (10.99–12.89) | 0.00 |
| proto-scalar | owned | interpret-generated | 17.79 (11.43–18.22) | 2.00 |
| proto-scalar | owned | peak-generated | 3.16 (2.74–5.12) | 1.00 |
| proto-scalar | owned | serialize | 111.18 (104.55–142.18) | 136.00 |
| proto-scalar | buffa | ingest | 220.30 (153.44–332.03) | 112.00 |
| proto-scalar | buffa | ingest-typed | 188.08 (151.52–216.28) | 80.00 |
| proto-scalar | buffa | interpret | 18.33 (15.64–19.83) | 2.00 |
| proto-scalar | buffa | peak | 2.22 (1.96–2.86) | 1.00 |
| proto-scalar | buffa | ingest-generated | 119.17 (102.19–170.73) | 80.00 |
| proto-scalar | buffa | read-generated-aot | 14.83 (13.77–15.35) | 0.00 |
| proto-scalar | buffa | interpret-generated | 19.45 (12.71–19.46) | 2.00 |
| proto-scalar | buffa | peak-generated | 3.76 (2.46–3.95) | 1.00 |
| proto-scalar | buffa | serialize | 27.47 (26.43–33.66) | 24.00 |
| proto-scalar | generated-owned | ingest | 39.40 (31.56–42.89) | 88.00 |
| proto-scalar | generated-owned | read-aot | 1.73 (1.57–1.87) | 0.00 |
| proto-scalar | generated-owned | interpret | 2.03 (2.01–2.13) | 2.00 |
| proto-scalar | generated-owned | peak | 1.80 (1.17–14.50) | 1.00 |
| proto-scalar | generated-owned | serialize | 21.93 (20.87–29.62) | 24.00 |
| proto-scalar | generated-owned | project-aot | 13.48 (8.77–15.17) | 0.00 |
| proto-scalar | generated-owned | project-interpret | 11.67 (11.13–14.39) | 2.00 |
| proto-scalar | generated-owned | project-peak | 13.81 (12.16–18.90) | 1.00 |
| proto-bytes | stock | ingest | 23165.99 (20969.30–28165.20) | 65699.00 |
| proto-bytes | stock | read-aot | 4.25 (3.32–5.26) | 0.00 |
| proto-bytes | stock | interpret | 5.95 (5.43–7.02) | 2.00 |
| proto-bytes | stock | peak | 4.57 (3.74–5.49) | 1.00 |
| proto-bytes | stock | serialize | 5958.14 (5163.91–6573.40) | 65599.00 |
| proto-bytes | stock-aliased | ingest | 86.08 (72.11–91.93) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 3.50 (3.01–3.63) | 0.00 |
| proto-bytes | stock-aliased | interpret | 6.10 (5.39–9.17) | 2.00 |
| proto-bytes | stock-aliased | peak | 3.36 (2.92–3.99) | 1.00 |
| proto-bytes | stock-aliased | serialize | 6639.59 (6290.34–17913.14) | 65599.00 |
| proto-bytes | owned | ingest | 9520.95 (8879.64–14714.75) | 66035.00 |
| proto-bytes | owned | interpret | 20.47 (20.36–28.54) | 2.00 |
| proto-bytes | owned | peak | 3.86 (3.77–5.36) | 1.00 |
| proto-bytes | owned | ingest-generated | 10320.40 (6580.54–11697.43) | 66035.00 |
| proto-bytes | owned | read-generated-aot | 19.60 (17.87–20.31) | 0.00 |
| proto-bytes | owned | interpret-generated | 21.89 (20.47–26.53) | 2.00 |
| proto-bytes | owned | peak-generated | 5.64 (5.34–6.07) | 1.00 |
| proto-bytes | owned | serialize | 6717.82 (5895.45–47828.16) | 65615.00 |
| proto-bytes | buffa | ingest | 282.68 (256.32–377.22) | 272.00 |
| proto-bytes | buffa | interpret | 26.53 (26.24–30.69) | 2.00 |
| proto-bytes | buffa | peak | 3.94 (3.84–6.21) | 1.00 |
| proto-bytes | buffa | ingest-generated | 222.86 (168.14–301.84) | 272.00 |
| proto-bytes | buffa | read-generated-aot | 9.78 (8.93–12.68) | 0.00 |
| proto-bytes | buffa | interpret-generated | 11.96 (9.63–22.17) | 2.00 |
| proto-bytes | buffa | peak-generated | 5.68 (4.84–7.25) | 1.00 |
| proto-bytes | buffa | serialize | 32714.79 (6246.35–36835.85) | 65567.00 |
| proto-bytes | generated-owned | ingest | 6323.24 (5471.29–127089.09) | 65651.00 |
| proto-bytes | generated-owned | read-aot | 2.18 (1.33–2.19) | 0.00 |
| proto-bytes | generated-owned | interpret | 2.85 (2.12–3.10) | 2.00 |
| proto-bytes | generated-owned | peak | 2.71 (2.58–4.14) | 1.00 |
| proto-bytes | generated-owned | serialize | 5647.03 (5606.32–15436.66) | 65583.00 |
| timestamp-json | stock | ingest | 3863.57 (3815.77–6272.67) | 6439.75 |
| timestamp-json | owned | ingest | 2080.45 (2036.82–2116.31) | 3703.75 |
| timestamp-json | owned | ingest-typed | 1871.66 (1745.63–2210.63) | 3671.75 |
| timestamp-json | owned | ingest-generated | 1910.68 (1737.15–2192.39) | 3671.75 |
| timestamp-json | buffa | ingest | 709.06 (625.72–900.24) | 688.00 |
| timestamp-json | buffa | ingest-typed | 865.12 (543.19–880.45) | 688.00 |
| timestamp-json | buffa | ingest-generated | 810.95 (765.01–883.08) | 688.00 |
| mysql-int4 | stock | ingest | 47.00 (44.46–72.08) | 16.00 |
| mysql-int4 | stock | read-aot | 1.61 (1.57–1.87) | 0.00 |
| mysql-int4 | stock | interpret | 3.73 (2.15–3.80) | 2.00 |
| mysql-int4 | stock | peak | 1.98 (1.15–2.29) | 1.00 |
| mysql-int4 | static | ingest | 100.61 (93.36–183.38) | 128.00 |
| mysql-int4 | static | ingest-typed | 128.51 (93.34–170.10) | 128.00 |
| mysql-int4 | static | interpret | 15.29 (14.44–17.14) | 2.00 |
| mysql-int4 | static | peak | 3.66 (2.28–4.55) | 1.00 |
| postgres-int4 | stock | ingest | 26.38 (21.88–29.59) | 16.00 |
| postgres-int4 | stock | read-aot | 1.25 (1.14–1.50) | 0.00 |
| postgres-int4 | stock | interpret | 2.02 (2.01–3.38) | 2.00 |
| postgres-int4 | stock | peak | 1.52 (1.24–1.76) | 1.00 |
| postgres-int4 | static | ingest | 128.18 (82.80–144.62) | 128.00 |
| postgres-int4 | static | ingest-typed | 135.70 (129.38–157.79) | 128.00 |
| postgres-int4 | static | interpret | 18.04 (14.69–22.96) | 2.00 |
| postgres-int4 | static | peak | 3.69 (2.31–3.96) | 1.00 |
| proto-mixed | stock | ingest | 1285.64 (893.11–1601.46) | 1776.75 |
| proto-mixed | stock | read-aot | 7.86 (7.81–8.99) | 0.00 |
| proto-mixed | stock | interpret-boundary | 8.76 (7.59–10.91) | 2.00 |
| proto-mixed | stock | peak-boundary | 11.69 (8.08–12.88) | 1.00 |
| proto-mixed | stock | serialize | 957.65 (732.31–1021.77) | 436.50 |
| proto-mixed | owned | ingest | 9330.41 (8789.22–13234.63) | 3339.50 |
| proto-mixed | owned | ingest-typed | 11316.85 (11063.21–15626.30) | 3339.50 |
| proto-mixed | owned | interpret | 72.58 (71.85–90.30) | 2.00 |
| proto-mixed | owned | peak | 13.14 (13.12–13.26) | 1.00 |
| proto-mixed | owned | ingest-generated | 11732.14 (11426.28–12318.06) | 3339.50 |
| proto-mixed | owned | read-generated-aot | 54.07 (47.28–69.87) | 0.00 |
| proto-mixed | owned | interpret-generated | 66.83 (58.92–72.84) | 2.00 |
| proto-mixed | owned | peak-generated | 11.20 (10.49–13.71) | 1.00 |
| proto-mixed | owned | serialize | 3690.95 (3361.55–5265.09) | 1130.50 |
| proto-mixed | buffa | ingest | 5717.28 (4091.92–6124.64) | 1727.75 |
| proto-mixed | buffa | ingest-typed | 4965.33 (4340.35–6132.38) | 1727.75 |
| proto-mixed | buffa | interpret | 61.55 (52.27–87.38) | 2.00 |
| proto-mixed | buffa | peak | 11.27 (11.13–12.25) | 1.00 |
| proto-mixed | buffa | ingest-generated | 4533.30 (3029.17–7132.17) | 1727.75 |
| proto-mixed | buffa | read-generated-aot | 29.22 (26.96–30.32) | 0.00 |
| proto-mixed | buffa | interpret-generated | 33.18 (24.73–40.18) | 2.00 |
| proto-mixed | buffa | peak-generated | 12.19 (8.82–12.75) | 1.00 |
| proto-mixed | buffa | serialize | 32.60 (27.77–40.64) | 176.50 |
| proto-mixed | generated-owned | ingest | 922.22 (865.39–1926.86) | 1895.00 |
| proto-mixed | generated-owned | read-aot | 8.04 (7.54–10.20) | 0.00 |
| proto-mixed | generated-owned | interpret | 6.98 (6.66–7.51) | 2.00 |
| proto-mixed | generated-owned | peak | 6.24 (3.96–6.69) | 1.00 |
| proto-mixed | generated-owned | serialize | 4072.66 (3548.18–4719.11) | 1130.50 |
| proto-mixed-full | stock | ingest | 1301.31 (989.17–1611.10) | 1776.75 |
| proto-mixed-full | owned | ingest | 17667.53 (12209.41–17889.90) | 3339.50 |
| proto-mixed-full | buffa | ingest | 7238.67 (6978.65–9763.12) | 1854.25 |
| proto-mixed-full | generated-owned | ingest | 1082.69 (1062.84–1872.97) | 1895.00 |

Stock read-aot is the native-image host baseline. Schema 4 adds stock guest tiers; mixed protobuf uses explicitly labeled boundary projections because its collection getters fail the SVM JIT blocklist.
Ingest includes parsing/materialization and an uncached projected read. Setup is excluded.
ingest-typed uses primitive readLong projections; ingest retains generic read projections.
Generated stages use schema-bound generated getters; read-generated-aot excludes guest calls. Original library-dispatch stages remain.
Schema 7 adds generated-owned concrete scalar/bytes records and serialization. Serialization emits a fresh byte array; Buffa binary views forward the validated original wire, while owned and stock messages encode fields. These operations have equivalent wire semantics, but differ in work performed.
Schema 9 measures parse-and-project with both parsers eligible for guest compilation (no explicit parser boundary); only invalid-input exception construction crosses a boundary. Both engines use a 10,000 graph-size budget. AOT and interpreter measurements use the same operation.
Schema 10 uses direct concrete generated getter loops matching the stock retained-read harness, without the generic projection adapter.
Schema 11 adds concrete generated nested/repeated decoding, full consumption, and matched mixed-message serialization.
Buffa binary input is already in unmanaged memory; owned engines start with Java byte arrays.
Buffa results are scoped borrowed views; stock-aliased borrows immutable ByteString input; stock/owned copy payloads.
The direct stock AOT baseline excludes guest interop and contract enforcement.
MySQL/Postgres measure driver INT4 decoder functions and retained scalar rows, not JDBC queries, transport, or servers.
Java heap counters exclude native allocations and compiler/background threads. Native allocation is not reported as zero.
Shared execution hardware and short samples make these diagnostic comparisons, not performance guarantees.

Binary SHA-256: `938ecbf2b585087fa546f6bc65d2750ba43772034a62b18d15fb85113bfd2394`
Revision: `9e0736a354e8991604d9069a3161abde534aba27`
