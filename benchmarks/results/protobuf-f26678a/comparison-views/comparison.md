# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 110.30 (110.02–111.25) | 136.00 |
| proto-scalar | stock | read-aot | 2.34 (2.32–2.34) | 0.00 |
| proto-scalar | stock | interpret | 3.04 (3.04–3.09) | 2.00 |
| proto-scalar | stock | peak | 1.85 (1.84–1.89) | 1.00 |
| proto-scalar | stock | serialize | 33.19 (32.78–33.61) | 56.00 |
| proto-scalar | stock | project-aot | 77.01 (76.90–77.47) | 136.00 |
| proto-scalar | stock | project-interpret | 81.25 (80.09–81.60) | 138.00 |
| proto-scalar | stock | project-peak | 82.23 (81.22–95.91) | 137.00 |
| proto-scalar | owned | ingest | 138.27 (132.64–162.10) | 64.00 |
| proto-scalar | owned | ingest-typed | 125.99 (125.73–126.62) | 48.00 |
| proto-scalar | owned | interpret | 36.59 (36.54–36.59) | 2.00 |
| proto-scalar | owned | peak | 3.84 (3.84–3.84) | 1.00 |
| proto-scalar | owned | ingest-generated | 46.80 (46.58–47.01) | 48.00 |
| proto-scalar | owned | read-generated-aot | 13.03 (12.96–13.05) | 0.00 |
| proto-scalar | owned | interpret-generated | 14.37 (14.36–15.04) | 2.00 |
| proto-scalar | owned | peak-generated | 2.49 (2.47–2.51) | 1.00 |
| proto-scalar | owned | serialize | 24.74 (24.60–25.47) | 24.00 |
| proto-scalar | buffa | ingest | 221.34 (219.59–257.08) | 112.00 |
| proto-scalar | buffa | ingest-typed | 207.84 (206.87–211.25) | 80.00 |
| proto-scalar | buffa | interpret | 27.59 (27.56–28.09) | 2.00 |
| proto-scalar | buffa | peak | 4.09 (4.07–4.14) | 1.00 |
| proto-scalar | buffa | ingest-generated | 119.35 (118.98–120.57) | 80.00 |
| proto-scalar | buffa | read-generated-aot | 16.08 (16.08–16.21) | 0.00 |
| proto-scalar | buffa | interpret-generated | 18.35 (18.31–18.47) | 2.00 |
| proto-scalar | buffa | peak-generated | 4.13 (4.13–4.25) | 1.00 |
| proto-scalar | buffa | serialize | 27.44 (27.29–27.58) | 24.00 |
| proto-scalar | generated-owned | ingest | 39.45 (39.32–40.72) | 88.00 |
| proto-scalar | generated-owned | read-aot | 1.83 (1.82–1.83) | 0.00 |
| proto-scalar | generated-owned | interpret | 2.32 (2.32–2.33) | 2.00 |
| proto-scalar | generated-owned | peak | 1.92 (1.92–2.07) | 1.00 |
| proto-scalar | generated-owned | serialize | 33.34 (33.26–34.11) | 24.00 |
| proto-scalar | generated-owned | project-aot | 13.56 (13.56–16.49) | 0.00 |
| proto-scalar | generated-owned | project-interpret | 15.02 (14.95–15.97) | 2.00 |
| proto-scalar | generated-owned | project-peak | 15.65 (15.58–15.68) | 1.00 |
| proto-bytes | stock | ingest | 26909.29 (26591.60–27394.68) | 65699.00 |
| proto-bytes | stock | read-aot | 4.54 (4.53–4.55) | 0.00 |
| proto-bytes | stock | interpret | 6.09 (6.08–6.10) | 2.00 |
| proto-bytes | stock | peak | 4.99 (4.98–5.24) | 1.00 |
| proto-bytes | stock | serialize | 8366.04 (7081.31–15576.24) | 65599.00 |
| proto-bytes | stock-aliased | ingest | 86.59 (86.48–212.69) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 4.17 (4.15–4.21) | 0.00 |
| proto-bytes | stock-aliased | interpret | 5.74 (5.71–5.79) | 2.00 |
| proto-bytes | stock-aliased | peak | 4.67 (4.65–4.70) | 1.00 |
| proto-bytes | stock-aliased | serialize | 11280.54 (7510.42–24087.66) | 65599.00 |
| proto-bytes | owned | ingest | 22077.18 (8347.69–32893.69) | 65995.00 |
| proto-bytes | owned | interpret | 28.56 (28.52–28.79) | 2.00 |
| proto-bytes | owned | peak | 7.03 (6.96–7.06) | 1.00 |
| proto-bytes | owned | ingest-generated | 8772.75 (7833.16–28771.12) | 65995.00 |
| proto-bytes | owned | read-generated-aot | 20.61 (19.82–21.14) | 0.00 |
| proto-bytes | owned | interpret-generated | 22.02 (21.98–22.58) | 2.00 |
| proto-bytes | owned | peak-generated | 6.74 (6.67–6.75) | 1.00 |
| proto-bytes | owned | serialize | 8267.70 (6281.12–18980.86) | 65615.00 |
| proto-bytes | buffa | ingest | 305.91 (302.21–320.36) | 176.00 |
| proto-bytes | buffa | interpret | 31.64 (31.54–32.55) | 2.00 |
| proto-bytes | buffa | peak | 7.88 (7.87–8.03) | 1.00 |
| proto-bytes | buffa | ingest-generated | 239.36 (238.68–244.90) | 176.00 |
| proto-bytes | buffa | read-generated-aot | 14.67 (14.64–14.92) | 0.00 |
| proto-bytes | buffa | interpret-generated | 16.91 (16.80–17.02) | 2.00 |
| proto-bytes | buffa | peak-generated | 9.09 (9.09–9.14) | 1.00 |
| proto-bytes | buffa | serialize | 7705.86 (6977.64–7764.61) | 65567.00 |
| proto-bytes | generated-owned | ingest | 7598.70 (7597.83–7609.17) | 65651.00 |
| proto-bytes | generated-owned | read-aot | 2.17 (2.15–2.17) | 0.00 |
| proto-bytes | generated-owned | interpret | 2.91 (2.77–3.04) | 2.00 |
| proto-bytes | generated-owned | peak | 2.87 (2.87–11.88) | 1.00 |
| proto-bytes | generated-owned | serialize | 33967.84 (7125.39–46781.27) | 65583.00 |
| timestamp-json | stock | ingest | 4790.58 (4782.02–4933.53) | 6439.75 |
| timestamp-json | owned | ingest | 2004.42 (1962.90–280298.77) | 3655.75 |
| timestamp-json | owned | ingest-typed | 1993.99 (1963.88–2002.06) | 3639.75 |
| timestamp-json | owned | ingest-generated | 2552.89 (1827.63–282382.42) | 3639.75 |
| timestamp-json | buffa | ingest | 903.29 (900.91–906.49) | 704.00 |
| timestamp-json | buffa | ingest-typed | 912.53 (910.23–917.20) | 704.00 |
| timestamp-json | buffa | ingest-generated | 777.67 (772.91–781.83) | 704.00 |
| mysql-int4 | stock | ingest | 59.74 (59.64–60.11) | 16.00 |
| mysql-int4 | stock | read-aot | 1.83 (1.82–1.83) | 0.00 |
| mysql-int4 | stock | interpret | 3.36 (3.30–28.13) | 2.00 |
| mysql-int4 | stock | peak | 1.69 (1.65–1.73) | 1.00 |
| mysql-int4 | static | ingest | 142.88 (141.66–1197.28) | 80.00 |
| mysql-int4 | static | ingest-typed | 141.75 (141.54–141.80) | 80.00 |
| mysql-int4 | static | interpret | 21.64 (21.39–22.33) | 2.00 |
| mysql-int4 | static | peak | 4.07 (4.04–4.08) | 1.00 |
| postgres-int4 | stock | ingest | 39.11 (38.99–39.25) | 16.00 |
| postgres-int4 | stock | read-aot | 2.07 (2.05–2.07) | 0.00 |
| postgres-int4 | stock | interpret | 3.28 (3.28–3.31) | 2.00 |
| postgres-int4 | stock | peak | 1.69 (1.68–1.71) | 1.00 |
| postgres-int4 | static | ingest | 131.15 (130.61–135.43) | 80.00 |
| postgres-int4 | static | ingest-typed | 129.64 (129.64–129.94) | 80.00 |
| postgres-int4 | static | interpret | 21.38 (21.37–21.39) | 2.00 |
| postgres-int4 | static | peak | 4.06 (4.05–4.09) | 1.00 |
| proto-mixed | stock | ingest | 1284.45 (1279.64–1294.49) | 1776.75 |
| proto-mixed | stock | read-aot | 11.53 (11.51–11.58) | 0.00 |
| proto-mixed | stock | interpret-boundary | 12.64 (12.47–14.03) | 2.00 |
| proto-mixed | stock | peak-boundary | 13.14 (13.10–13.15) | 1.00 |
| proto-mixed | stock | serialize | 904.26 (902.87–916.91) | 436.50 |
| proto-mixed | owned | ingest | 7162.66 (7086.77–9053.78) | 2976.00 |
| proto-mixed | owned | ingest-typed | 7017.17 (6961.36–7052.54) | 2976.00 |
| proto-mixed | owned | interpret | 106.21 (106.16–106.80) | 2.00 |
| proto-mixed | owned | peak | 15.08 (15.07–15.09) | 1.00 |
| proto-mixed | owned | ingest-generated | 6649.08 (6608.10–6661.61) | 2976.00 |
| proto-mixed | owned | read-generated-aot | 69.27 (68.97–69.27) | 0.00 |
| proto-mixed | owned | interpret-generated | 74.07 (73.38–74.31) | 2.00 |
| proto-mixed | owned | peak-generated | 14.53 (14.50–14.54) | 1.00 |
| proto-mixed | owned | serialize | 5740.59 (5732.83–5749.44) | 556.00 |
| proto-mixed | buffa | ingest | 1733.98 (1727.01–1739.72) | 445.50 |
| proto-mixed | buffa | ingest-typed | 1760.84 (1757.52–1887.38) | 445.50 |
| proto-mixed | buffa | interpret | 101.92 (99.92–102.48) | 2.00 |
| proto-mixed | buffa | peak | 21.24 (21.10–21.52) | 1.00 |
| proto-mixed | buffa | ingest-generated | 1452.81 (1442.91–1529.61) | 341.50 |
| proto-mixed | buffa | read-generated-aot | 36.79 (36.55–36.87) | 0.00 |
| proto-mixed | buffa | interpret-generated | 39.07 (38.87–39.22) | 2.00 |
| proto-mixed | buffa | peak-generated | 20.42 (20.34–20.66) | 1.00 |
| proto-mixed | buffa | serialize | 41.50 (41.07–41.69) | 176.50 |
| proto-mixed | generated-owned | ingest | 1423.55 (1399.21–1484.68) | 1800.50 |
| proto-mixed | generated-owned | read-aot | 8.97 (8.87–9.52) | 0.00 |
| proto-mixed | generated-owned | interpret | 6.94 (6.91–6.96) | 2.00 |
| proto-mixed | generated-owned | peak | 6.71 (6.70–6.74) | 1.00 |
| proto-mixed | generated-owned | serialize | 1200.44 (1196.56–1201.82) | 192.50 |
| proto-mixed-full | stock | ingest | 1348.62 (1342.83–1352.69) | 1776.75 |
| proto-mixed-full | owned | ingest | 10499.92 (10021.05–11591.18) | 2976.00 |
| proto-mixed-full | owned | ingest-generated | 7260.71 (7243.66–7294.35) | 2976.00 |
| proto-mixed-full | buffa | ingest | 4170.32 (4131.13–4199.72) | 1406.25 |
| proto-mixed-full | buffa | ingest-generated | 2807.81 (2798.53–2833.89) | 1406.25 |
| proto-mixed-full | generated-owned | ingest | 1461.96 (1457.14–1510.63) | 1800.50 |

Stock read-aot is the native-image host baseline. Schema 4 adds stock guest tiers; mixed protobuf uses explicitly labeled boundary projections because its collection getters fail the SVM JIT blocklist.
Ingest includes parsing/materialization and an uncached projected read. Setup is excluded.
ingest-typed uses primitive readLong projections; ingest retains generic read projections.
Generated stages use schema-bound generated getters; read-generated-aot excludes guest calls. Original library-dispatch stages remain.
Schema 7 adds generated-owned concrete scalar/bytes records and serialization. Serialization emits a fresh byte array; Buffa binary views forward the validated original wire, while owned and stock messages encode fields. These operations have equivalent wire semantics, but differ in work performed.
Schema 9 measures parse-and-project with both parsers eligible for guest compilation (no explicit parser boundary); only invalid-input exception construction crosses a boundary. Both engines use a 10,000 graph-size budget. AOT and interpreter measurements use the same operation.
Schema 10 uses direct concrete generated getter loops matching the stock retained-read harness, without the generic projection adapter.
Schema 11 adds concrete generated nested/repeated decoding, full consumption, and matched mixed-message serialization.
Schema 12 adds full nested/repeated consumption through generated schema getters, retaining the uncached-interop full-consumption case.
Buffa binary input is already in unmanaged memory; owned engines start with Java byte arrays.
Buffa results are scoped borrowed views; stock-aliased borrows immutable ByteString input; stock/owned copy payloads.
The direct stock AOT baseline excludes guest interop and contract enforcement.
MySQL/Postgres measure driver INT4 decoder functions and retained scalar rows, not JDBC queries, transport, or servers.
Java heap counters exclude native allocations and compiler/background threads. Native allocation is not reported as zero.
Shared execution hardware and short samples make these diagnostic comparisons, not performance guarantees.

Binary SHA-256: `e0d7c145610b3c211ee5fecd663bc6cc1dc6bc3381500b75f8cddf299ae81b54`
Revision: `f26678a`
