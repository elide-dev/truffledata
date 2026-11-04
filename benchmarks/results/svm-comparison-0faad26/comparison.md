# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 157.34 (112.53–178.36) | 136.00 |
| proto-scalar | stock | read-aot | 2.43 (2.09–3.66) | 0.00 |
| proto-scalar | stock | interpret | 3.17 (2.98–5.62) | 2.00 |
| proto-scalar | stock | peak | 3.05 (2.59–3.22) | 1.00 |
| proto-scalar | stock | serialize | 32.82 (30.57–53.14) | 56.00 |
| proto-scalar | stock | project-aot | 101.09 (72.10–106.88) | 136.00 |
| proto-scalar | stock | project-interpret | 107.12 (74.55–115.00) | 138.00 |
| proto-scalar | stock | project-peak | 81.80 (81.48–118.26) | 137.00 |
| proto-scalar | owned | ingest | 261.78 (118.24–279.84) | 88.00 |
| proto-scalar | owned | ingest-typed | 185.85 (125.08–302.81) | 56.00 |
| proto-scalar | owned | interpret | 59.34 (57.36–60.02) | 2.00 |
| proto-scalar | owned | peak | 5.47 (3.34–6.50) | 1.00 |
| proto-scalar | owned | ingest-generated | 78.98 (51.30–97.28) | 56.00 |
| proto-scalar | owned | read-generated-aot | 21.13 (13.11–23.60) | 0.00 |
| proto-scalar | owned | interpret-generated | 19.71 (15.03–26.82) | 2.00 |
| proto-scalar | owned | peak-generated | 5.35 (2.72–5.72) | 1.00 |
| proto-scalar | owned | serialize | 211.91 (192.98–264.62) | 136.00 |
| proto-scalar | buffa | ingest | 356.62 (323.38–413.85) | 112.00 |
| proto-scalar | buffa | ingest-typed | 222.83 (216.11–332.35) | 80.00 |
| proto-scalar | buffa | interpret | 29.84 (27.23–32.01) | 2.00 |
| proto-scalar | buffa | peak | 6.06 (3.82–7.13) | 1.00 |
| proto-scalar | buffa | ingest-generated | 218.16 (138.04–273.03) | 80.00 |
| proto-scalar | buffa | read-generated-aot | 17.35 (17.30–23.34) | 0.00 |
| proto-scalar | buffa | interpret-generated | 19.01 (17.99–30.48) | 2.00 |
| proto-scalar | buffa | peak-generated | 5.13 (4.18–7.15) | 1.00 |
| proto-scalar | buffa | serialize | 26.68 (24.39–46.60) | 24.00 |
| proto-scalar | generated-owned | ingest | 69.45 (57.47–80.09) | 88.00 |
| proto-scalar | generated-owned | read-aot | 2.05 (1.74–3.21) | 0.00 |
| proto-scalar | generated-owned | interpret | 3.20 (2.79–3.40) | 2.00 |
| proto-scalar | generated-owned | peak | 2.53 (1.93–4.06) | 1.00 |
| proto-scalar | generated-owned | serialize | 43.62 (38.12–43.94) | 24.00 |
| proto-scalar | generated-owned | project-aot | 15.90 (13.53–25.65) | 0.00 |
| proto-scalar | generated-owned | project-interpret | 17.70 (16.93–21.43) | 2.00 |
| proto-scalar | generated-owned | project-peak | 15.55 (15.50–15.84) | 1.00 |
| proto-bytes | stock | ingest | 52562.68 (31066.45–52901.87) | 65699.00 |
| proto-bytes | stock | read-aot | 5.85 (5.23–6.49) | 0.00 |
| proto-bytes | stock | interpret | 6.62 (5.53–6.76) | 2.00 |
| proto-bytes | stock | peak | 8.18 (4.27–8.66) | 1.00 |
| proto-bytes | stock | serialize | 12535.19 (10546.83–12947.10) | 65599.00 |
| proto-bytes | stock-aliased | ingest | 112.33 (83.10–124.88) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 3.38 (3.29–6.70) | 0.00 |
| proto-bytes | stock-aliased | interpret | 6.29 (5.39–10.83) | 2.00 |
| proto-bytes | stock-aliased | peak | 5.96 (5.24–7.91) | 1.00 |
| proto-bytes | stock-aliased | serialize | 12448.37 (12408.34–13066.30) | 65599.00 |
| proto-bytes | owned | ingest | 14122.29 (14088.60–15273.86) | 66003.00 |
| proto-bytes | owned | interpret | 47.32 (30.84–57.80) | 2.00 |
| proto-bytes | owned | peak | 10.78 (6.55–11.44) | 1.00 |
| proto-bytes | owned | ingest-generated | 14003.38 (13811.01–14074.32) | 66003.00 |
| proto-bytes | owned | read-generated-aot | 25.80 (25.69–25.93) | 0.00 |
| proto-bytes | owned | interpret-generated | 41.15 (33.77–42.78) | 2.00 |
| proto-bytes | owned | peak-generated | 8.07 (6.88–9.56) | 1.00 |
| proto-bytes | owned | serialize | 12891.71 (11558.42–13966.47) | 65615.00 |
| proto-bytes | buffa | ingest | 624.54 (358.39–673.17) | 272.00 |
| proto-bytes | buffa | interpret | 26.38 (26.28–44.63) | 2.00 |
| proto-bytes | buffa | peak | 11.27 (6.33–12.37) | 1.00 |
| proto-bytes | buffa | ingest-generated | 475.32 (349.12–498.77) | 272.00 |
| proto-bytes | buffa | read-generated-aot | 15.35 (12.41–21.71) | 0.00 |
| proto-bytes | buffa | interpret-generated | 20.98 (14.36–23.93) | 2.00 |
| proto-bytes | buffa | peak-generated | 10.64 (6.29–12.15) | 1.00 |
| proto-bytes | buffa | serialize | 13185.34 (12698.42–19977.17) | 65567.00 |
| proto-bytes | generated-owned | ingest | 13679.42 (13666.54–28572.85) | 65651.00 |
| proto-bytes | generated-owned | read-aot | 3.37 (2.66–4.51) | 0.00 |
| proto-bytes | generated-owned | interpret | 4.06 (3.12–4.30) | 2.00 |
| proto-bytes | generated-owned | peak | 3.02 (2.80–4.65) | 1.00 |
| proto-bytes | generated-owned | serialize | 13449.98 (12629.58–15639.68) | 65583.00 |
| timestamp-json | stock | ingest | 9035.55 (9024.21–9335.67) | 6439.75 |
| timestamp-json | owned | ingest | 4038.63 (3373.12–4057.58) | 3703.75 |
| timestamp-json | owned | ingest-typed | 3667.26 (2404.37–4305.54) | 3671.75 |
| timestamp-json | owned | ingest-generated | 3469.46 (2325.22–3476.33) | 3671.75 |
| timestamp-json | buffa | ingest | 999.01 (918.76–1376.60) | 688.00 |
| timestamp-json | buffa | ingest-typed | 1436.37 (852.34–1651.98) | 688.00 |
| timestamp-json | buffa | ingest-generated | 1333.11 (782.45–1340.44) | 688.00 |
| mysql-int4 | stock | ingest | 117.94 (81.31–123.15) | 16.00 |
| mysql-int4 | stock | read-aot | 2.75 (1.85–3.45) | 0.00 |
| mysql-int4 | stock | interpret | 3.64 (3.13–4.66) | 2.00 |
| mysql-int4 | stock | peak | 1.50 (1.50–1.78) | 1.00 |
| mysql-int4 | static | ingest | 284.71 (188.58–320.65) | 128.00 |
| mysql-int4 | static | ingest-typed | 193.05 (162.51–362.40) | 128.00 |
| mysql-int4 | static | interpret | 30.33 (18.74–34.99) | 2.00 |
| mysql-int4 | static | peak | 5.21 (3.50–6.69) | 1.00 |
| postgres-int4 | stock | ingest | 63.44 (62.35–67.17) | 16.00 |
| postgres-int4 | stock | read-aot | 2.35 (2.13–2.78) | 0.00 |
| postgres-int4 | stock | interpret | 3.41 (3.08–6.03) | 2.00 |
| postgres-int4 | stock | peak | 1.64 (1.58–4.41) | 1.00 |
| postgres-int4 | static | ingest | 198.19 (172.33–345.77) | 128.00 |
| postgres-int4 | static | ingest-typed | 150.66 (149.85–217.36) | 128.00 |
| postgres-int4 | static | interpret | 17.77 (17.64–18.21) | 2.00 |
| postgres-int4 | static | peak | 6.30 (3.90–7.30) | 1.00 |
| proto-mixed | stock | ingest | 1474.16 (1367.68–2104.78) | 1776.75 |
| proto-mixed | stock | read-aot | 11.00 (8.83–14.93) | 0.00 |
| proto-mixed | stock | interpret-boundary | 10.29 (10.29–17.13) | 2.00 |
| proto-mixed | stock | peak-boundary | 11.74 (10.98–11.80) | 1.00 |
| proto-mixed | stock | serialize | 1022.12 (942.25–1420.81) | 436.50 |
| proto-mixed | owned | ingest | 10074.52 (8285.55–13683.33) | 3086.50 |
| proto-mixed | owned | ingest-typed | 7476.14 (7347.02–17003.78) | 3086.50 |
| proto-mixed | owned | interpret | 176.66 (147.14–177.24) | 2.00 |
| proto-mixed | owned | peak | 19.22 (12.76–23.56) | 1.00 |
| proto-mixed | owned | ingest-generated | 10876.01 (6738.87–12078.83) | 3086.50 |
| proto-mixed | owned | read-generated-aot | 67.71 (64.77–69.00) | 0.00 |
| proto-mixed | owned | interpret-generated | 109.48 (83.90–122.27) | 2.00 |
| proto-mixed | owned | peak-generated | 16.05 (12.02–16.33) | 1.00 |
| proto-mixed | owned | serialize | 8951.04 (7389.88–9020.08) | 556.00 |
| proto-mixed | buffa | ingest | 6666.14 (6512.40–9277.12) | 1727.75 |
| proto-mixed | buffa | ingest-typed | 10092.19 (9117.25–11091.48) | 1727.75 |
| proto-mixed | buffa | interpret | 142.40 (129.56–155.19) | 2.00 |
| proto-mixed | buffa | peak | 16.06 (15.37–31.13) | 1.00 |
| proto-mixed | buffa | ingest-generated | 9381.78 (6129.36–10755.12) | 1727.75 |
| proto-mixed | buffa | read-generated-aot | 43.15 (36.18–55.95) | 0.00 |
| proto-mixed | buffa | interpret-generated | 49.28 (38.50–65.99) | 2.00 |
| proto-mixed | buffa | peak-generated | 24.05 (17.91–28.68) | 1.00 |
| proto-mixed | buffa | serialize | 76.09 (71.61–76.46) | 176.50 |
| proto-mixed | generated-owned | ingest | 2308.47 (1680.55–3038.70) | 1800.50 |
| proto-mixed | generated-owned | read-aot | 10.08 (8.60–15.02) | 0.00 |
| proto-mixed | generated-owned | interpret | 10.02 (9.72–11.72) | 2.00 |
| proto-mixed | generated-owned | peak | 8.58 (5.45–10.89) | 1.00 |
| proto-mixed | generated-owned | serialize | 1768.83 (1118.49–2010.54) | 192.50 |
| proto-mixed-full | stock | ingest | 1429.60 (1416.34–2644.91) | 1776.75 |
| proto-mixed-full | owned | ingest | 9800.44 (9545.79–19080.47) | 3086.50 |
| proto-mixed-full | buffa | ingest | 11390.02 (10389.77–14344.39) | 1854.25 |
| proto-mixed-full | generated-owned | ingest | 1985.01 (1671.96–2432.24) | 1800.50 |

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

Binary SHA-256: `9db9135ac111e23889dc6fbdb999fb6fe890f62b659d8fb64ac15d57ed00db8e`
Revision: `0faad260161b70692537744bf68778555866bbe5`
