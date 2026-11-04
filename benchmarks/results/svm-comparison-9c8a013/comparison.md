# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 89.74 (76.48–1050.43) | 136.00 |
| proto-scalar | stock | read-aot | 2.60 (1.44–2.76) | 0.00 |
| proto-scalar | stock | interpret | 2.44 (2.02–2.93) | 2.00 |
| proto-scalar | stock | peak | 1.49 (1.34–1.66) | 1.00 |
| proto-scalar | owned | ingest | 123.06 (84.52–131.99) | 88.00 |
| proto-scalar | owned | ingest-typed | 110.06 (83.37–139.23) | 56.00 |
| proto-scalar | owned | interpret | 26.06 (25.39–36.20) | 2.00 |
| proto-scalar | owned | peak | 2.22 (1.98–3.35) | 1.00 |
| proto-scalar | owned | ingest-generated | 48.50 (48.39–60.24) | 56.00 |
| proto-scalar | owned | read-generated-aot | 14.34 (11.96–14.83) | 0.00 |
| proto-scalar | owned | interpret-generated | 12.82 (12.59–19.20) | 2.00 |
| proto-scalar | owned | peak-generated | 2.56 (2.43–6.74) | 1.00 |
| proto-scalar | buffa | ingest | 312.79 (227.60–313.64) | 232.00 |
| proto-scalar | buffa | ingest-typed | 230.62 (229.73–286.11) | 200.00 |
| proto-scalar | buffa | interpret | 18.12 (18.11–25.68) | 2.00 |
| proto-scalar | buffa | peak | 2.47 (2.42–3.20) | 1.00 |
| proto-scalar | buffa | ingest-generated | 193.60 (193.03–245.93) | 200.00 |
| proto-scalar | buffa | read-generated-aot | 14.62 (12.29–14.78) | 0.00 |
| proto-scalar | buffa | interpret-generated | 15.15 (14.72–15.66) | 2.00 |
| proto-scalar | buffa | peak-generated | 2.44 (2.41–2.81) | 1.00 |
| proto-bytes | stock | ingest | 26371.30 (24945.20–120000.88) | 65699.00 |
| proto-bytes | stock | read-aot | 3.60 (3.11–4.65) | 0.00 |
| proto-bytes | stock | interpret | 4.98 (4.63–5.57) | 2.00 |
| proto-bytes | stock | peak | 5.43 (4.18–10.42) | 1.00 |
| proto-bytes | stock-aliased | ingest | 73.77 (61.14–101.55) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 4.45 (3.23–4.70) | 0.00 |
| proto-bytes | stock-aliased | interpret | 5.58 (5.22–6.75) | 2.00 |
| proto-bytes | stock-aliased | peak | 3.80 (3.63–3.87) | 1.00 |
| proto-bytes | owned | ingest | 21645.59 (13243.12–24996.79) | 66035.00 |
| proto-bytes | owned | interpret | 27.64 (22.18–33.37) | 2.00 |
| proto-bytes | owned | peak | 6.55 (4.81–7.32) | 1.00 |
| proto-bytes | owned | ingest-generated | 47211.06 (26725.13–52837.52) | 66035.00 |
| proto-bytes | owned | read-generated-aot | 14.74 (14.37–76.20) | 0.00 |
| proto-bytes | owned | interpret-generated | 22.02 (18.73–22.57) | 2.00 |
| proto-bytes | owned | peak-generated | 5.47 (5.21–5.57) | 1.00 |
| proto-bytes | buffa | ingest | 348.15 (256.08–372.23) | 296.00 |
| proto-bytes | buffa | interpret | 22.51 (20.10–27.45) | 2.00 |
| proto-bytes | buffa | peak | 4.48 (4.46–7.33) | 1.00 |
| proto-bytes | buffa | ingest-generated | 213.47 (209.66–274.43) | 296.00 |
| proto-bytes | buffa | read-generated-aot | 11.05 (7.99–11.97) | 0.00 |
| proto-bytes | buffa | interpret-generated | 14.20 (12.92–14.59) | 2.00 |
| proto-bytes | buffa | peak-generated | 5.91 (4.34–7.63) | 1.00 |
| timestamp-json | stock | ingest | 4142.08 (3955.26–4973.73) | 6439.75 |
| timestamp-json | owned | ingest | 1850.48 (1526.17–1929.60) | 3703.75 |
| timestamp-json | owned | ingest-typed | 2030.37 (1660.03–2498.87) | 3671.75 |
| timestamp-json | owned | ingest-generated | 2194.56 (1616.42–2214.58) | 3671.75 |
| timestamp-json | buffa | ingest | 996.55 (714.64–1428.98) | 712.00 |
| timestamp-json | buffa | ingest-typed | 1040.62 (601.84–1252.43) | 712.00 |
| timestamp-json | buffa | ingest-generated | 541.83 (471.96–764.05) | 712.00 |
| mysql-int4 | stock | ingest | 42.87 (42.25–63.00) | 16.00 |
| mysql-int4 | stock | read-aot | 1.46 (1.20–2.47) | 0.00 |
| mysql-int4 | stock | interpret | 2.19 (2.14–2.42) | 2.00 |
| mysql-int4 | stock | peak | 1.25 (1.19–1.56) | 1.00 |
| mysql-int4 | static | ingest | 184.11 (124.68–2010.13) | 128.00 |
| mysql-int4 | static | ingest-typed | 163.41 (130.89–180.59) | 128.00 |
| mysql-int4 | static | interpret | 16.98 (14.88–18.02) | 2.00 |
| mysql-int4 | static | peak | 3.93 (2.91–4.37) | 1.00 |
| postgres-int4 | stock | ingest | 27.62 (24.99–49.12) | 16.00 |
| postgres-int4 | stock | read-aot | 1.50 (1.36–1.98) | 0.00 |
| postgres-int4 | stock | interpret | 3.10 (2.81–3.56) | 2.00 |
| postgres-int4 | stock | peak | 1.38 (1.14–1.69) | 1.00 |
| postgres-int4 | static | ingest | 110.83 (102.63–115.47) | 128.00 |
| postgres-int4 | static | ingest-typed | 138.10 (137.88–162.01) | 128.00 |
| postgres-int4 | static | interpret | 15.99 (15.41–26.86) | 2.00 |
| postgres-int4 | static | peak | 2.76 (2.74–2.97) | 1.00 |
| proto-mixed | stock | ingest | 1387.54 (968.02–2442.90) | 1776.75 |
| proto-mixed | stock | read-aot | 8.52 (7.77–22.91) | 0.00 |
| proto-mixed | stock | interpret-boundary | 10.19 (8.70–12.62) | 2.00 |
| proto-mixed | stock | peak-boundary | 9.38 (8.79–11.80) | 1.00 |
| proto-mixed | owned | ingest | 11645.05 (9129.84–14729.85) | 3339.50 |
| proto-mixed | owned | ingest-typed | 10770.00 (8869.11–16722.35) | 3339.50 |
| proto-mixed | owned | interpret | 69.39 (67.46–90.33) | 2.00 |
| proto-mixed | owned | peak | 13.33 (10.95–18.56) | 1.00 |
| proto-mixed | owned | ingest-generated | 12441.34 (8527.28–16752.99) | 3339.50 |
| proto-mixed | owned | read-generated-aot | 51.91 (49.64–67.84) | 0.00 |
| proto-mixed | owned | interpret-generated | 55.80 (53.41–70.63) | 2.00 |
| proto-mixed | owned | peak-generated | 10.55 (10.29–20.35) | 1.00 |
| proto-mixed | buffa | ingest | 4443.68 (3834.26–5865.91) | 1751.75 |
| proto-mixed | buffa | ingest-typed | 6654.31 (4294.72–6955.36) | 1751.75 |
| proto-mixed | buffa | interpret | 100.31 (91.11–117.97) | 2.00 |
| proto-mixed | buffa | peak | 11.12 (10.94–19.58) | 1.00 |
| proto-mixed | buffa | ingest-generated | 3452.56 (3414.45–3626.18) | 1751.75 |
| proto-mixed | buffa | read-generated-aot | 30.75 (22.91–30.77) | 0.00 |
| proto-mixed | buffa | interpret-generated | 45.08 (33.28–51.80) | 2.00 |
| proto-mixed | buffa | peak-generated | 14.86 (13.47–15.20) | 1.00 |
| proto-mixed-full | stock | ingest | 1145.02 (975.29–1314.16) | 1776.75 |
| proto-mixed-full | owned | ingest | 14592.70 (13994.28–18401.28) | 3339.50 |
| proto-mixed-full | buffa | ingest | 5268.65 (5108.90–9041.20) | 1878.25 |

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
