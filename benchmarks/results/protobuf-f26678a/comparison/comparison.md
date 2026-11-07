# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 63.40 (56.20–66.71) | 136.00 |
| proto-scalar | stock | read-aot | 1.15 (1.07–1.29) | 0.00 |
| proto-scalar | stock | interpret | 1.53 (1.51–1.54) | 2.00 |
| proto-scalar | stock | peak | 0.92 (0.91–1.68) | 1.00 |
| proto-scalar | stock | serialize | 15.68 (15.53–29.77) | 56.00 |
| proto-scalar | stock | project-aot | 39.46 (36.60–46.50) | 136.00 |
| proto-scalar | stock | project-interpret | 39.14 (38.64–41.53) | 138.00 |
| proto-scalar | stock | project-peak | 39.17 (38.84–40.72) | 137.00 |
| proto-scalar | owned | ingest | 66.02 (63.53–67.34) | 64.00 |
| proto-scalar | owned | ingest-typed | 66.23 (65.73–92.19) | 48.00 |
| proto-scalar | owned | interpret | 18.72 (17.42–20.40) | 2.00 |
| proto-scalar | owned | peak | 1.91 (1.81–2.07) | 1.00 |
| proto-scalar | owned | ingest-generated | 24.84 (24.55–25.06) | 48.00 |
| proto-scalar | owned | read-generated-aot | 6.81 (6.56–6.95) | 0.00 |
| proto-scalar | owned | interpret-generated | 7.66 (7.57–8.46) | 2.00 |
| proto-scalar | owned | peak-generated | 1.35 (1.28–1.37) | 1.00 |
| proto-scalar | owned | serialize | 14.11 (13.33–15.49) | 24.00 |
| proto-scalar | buffa | ingest | 109.74 (109.71–123.42) | 112.00 |
| proto-scalar | buffa | ingest-typed | 99.75 (99.29–112.54) | 80.00 |
| proto-scalar | buffa | interpret | 13.64 (13.11–14.87) | 2.00 |
| proto-scalar | buffa | peak | 3.16 (1.66–6.43) | 1.00 |
| proto-scalar | buffa | ingest-generated | 60.06 (57.16–60.93) | 80.00 |
| proto-scalar | buffa | read-generated-aot | 8.06 (7.86–11.51) | 0.00 |
| proto-scalar | buffa | interpret-generated | 8.99 (8.79–9.74) | 2.00 |
| proto-scalar | buffa | peak-generated | 1.90 (1.83–3.95) | 1.00 |
| proto-scalar | buffa | serialize | 14.20 (13.20–18.51) | 24.00 |
| proto-scalar | generated-owned | ingest | 21.81 (21.60–25.27) | 88.00 |
| proto-scalar | generated-owned | read-aot | 0.91 (0.89–1.06) | 0.00 |
| proto-scalar | generated-owned | interpret | 1.28 (1.22–1.31) | 2.00 |
| proto-scalar | generated-owned | peak | 0.99 (0.95–1.03) | 1.00 |
| proto-scalar | generated-owned | serialize | 18.30 (17.18–19.79) | 24.00 |
| proto-scalar | generated-owned | project-aot | 7.55 (7.38–7.76) | 0.00 |
| proto-scalar | generated-owned | project-interpret | 8.52 (8.45–8.72) | 2.00 |
| proto-scalar | generated-owned | project-peak | 9.44 (8.84–9.71) | 1.00 |
| proto-bytes | stock | ingest | 16619.36 (15322.37–17482.85) | 65699.00 |
| proto-bytes | stock | read-aot | 2.53 (2.48–2.55) | 0.00 |
| proto-bytes | stock | interpret | 3.59 (3.52–3.65) | 2.00 |
| proto-bytes | stock | peak | 2.87 (2.67–4.22) | 1.00 |
| proto-bytes | stock | serialize | 7978.59 (4790.78–11264.40) | 65599.00 |
| proto-bytes | stock-aliased | ingest | 43.25 (42.82–47.45) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 2.40 (2.36–2.54) | 0.00 |
| proto-bytes | stock-aliased | interpret | 3.37 (3.25–3.55) | 2.00 |
| proto-bytes | stock-aliased | peak | 2.43 (2.38–2.56) | 1.00 |
| proto-bytes | stock-aliased | serialize | 6700.62 (3644.29–39147.41) | 65599.00 |
| proto-bytes | owned | ingest | 4022.92 (3843.43–4102.61) | 65995.00 |
| proto-bytes | owned | interpret | 15.55 (13.69–16.37) | 2.00 |
| proto-bytes | owned | peak | 3.40 (3.35–4.09) | 1.00 |
| proto-bytes | owned | ingest-generated | 7042.63 (7036.48–11604.97) | 65995.00 |
| proto-bytes | owned | read-generated-aot | 11.07 (10.43–12.83) | 0.00 |
| proto-bytes | owned | interpret-generated | 11.57 (11.03–11.59) | 2.00 |
| proto-bytes | owned | peak-generated | 3.44 (3.03–6.26) | 1.00 |
| proto-bytes | owned | serialize | 11880.31 (4080.93–24729.53) | 65615.00 |
| proto-bytes | buffa | ingest | 176.09 (163.66–487.97) | 288.00 |
| proto-bytes | buffa | interpret | 17.19 (16.38–26.13) | 2.00 |
| proto-bytes | buffa | peak | 3.26 (3.12–3.57) | 1.00 |
| proto-bytes | buffa | ingest-generated | 112.72 (109.35–127.23) | 288.00 |
| proto-bytes | buffa | read-generated-aot | 7.43 (7.06–7.61) | 0.00 |
| proto-bytes | buffa | interpret-generated | 7.95 (7.84–9.09) | 2.00 |
| proto-bytes | buffa | peak-generated | 3.86 (3.55–4.01) | 1.00 |
| proto-bytes | buffa | serialize | 14737.61 (12939.67–17359.92) | 65567.00 |
| proto-bytes | generated-owned | ingest | 12628.23 (6919.22–21926.73) | 65651.00 |
| proto-bytes | generated-owned | read-aot | 1.12 (1.10–1.15) | 0.00 |
| proto-bytes | generated-owned | interpret | 1.35 (1.35–1.41) | 2.00 |
| proto-bytes | generated-owned | peak | 1.36 (1.34–1.42) | 1.00 |
| proto-bytes | generated-owned | serialize | 21630.99 (10616.30–36041.16) | 65583.00 |
| timestamp-json | stock | ingest | 2741.74 (2725.93–3141.69) | 6439.75 |
| timestamp-json | owned | ingest | 1139.13 (1105.97–1371.77) | 3655.75 |
| timestamp-json | owned | ingest-typed | 1323.16 (1122.37–2966.51) | 3639.75 |
| timestamp-json | owned | ingest-generated | 1132.72 (1088.68–1203.58) | 3639.75 |
| timestamp-json | buffa | ingest | 513.19 (497.24–552.12) | 704.00 |
| timestamp-json | buffa | ingest-typed | 528.49 (481.07–545.65) | 704.00 |
| timestamp-json | buffa | ingest-generated | 440.01 (419.49–464.27) | 704.00 |
| mysql-int4 | stock | ingest | 28.54 (27.96–29.08) | 16.00 |
| mysql-int4 | stock | read-aot | 0.97 (0.90–1.46) | 0.00 |
| mysql-int4 | stock | interpret | 1.59 (1.58–1.70) | 2.00 |
| mysql-int4 | stock | peak | 0.84 (0.84–0.87) | 1.00 |
| mysql-int4 | static | ingest | 85.92 (73.59–767.15) | 80.00 |
| mysql-int4 | static | ingest-typed | 75.51 (71.70–78.46) | 80.00 |
| mysql-int4 | static | interpret | 11.72 (11.16–336.61) | 2.00 |
| mysql-int4 | static | peak | 1.80 (1.79–1.96) | 1.00 |
| postgres-int4 | stock | ingest | 20.68 (20.22–21.68) | 16.00 |
| postgres-int4 | stock | read-aot | 1.06 (0.98–1.93) | 0.00 |
| postgres-int4 | stock | interpret | 1.75 (1.63–1.76) | 2.00 |
| postgres-int4 | stock | peak | 0.87 (0.82–0.88) | 1.00 |
| postgres-int4 | static | ingest | 68.40 (64.89–69.67) | 80.00 |
| postgres-int4 | static | ingest-typed | 76.16 (70.20–76.25) | 80.00 |
| postgres-int4 | static | interpret | 11.69 (11.11–12.52) | 2.00 |
| postgres-int4 | static | peak | 1.90 (1.86–2.87) | 1.00 |
| proto-mixed | stock | ingest | 747.50 (718.48–749.45) | 1776.75 |
| proto-mixed | stock | read-aot | 5.64 (5.63–5.82) | 0.00 |
| proto-mixed | stock | interpret-boundary | 6.66 (6.35–6.69) | 2.00 |
| proto-mixed | stock | peak-boundary | 5.99 (5.90–6.23) | 1.00 |
| proto-mixed | stock | serialize | 480.42 (469.20–574.34) | 436.50 |
| proto-mixed | owned | ingest | 3731.10 (3517.03–4991.92) | 2976.00 |
| proto-mixed | owned | ingest-typed | 3678.59 (3563.70–3884.69) | 2976.00 |
| proto-mixed | owned | interpret | 54.08 (51.36–60.53) | 2.00 |
| proto-mixed | owned | peak | 8.50 (7.47–9.40) | 1.00 |
| proto-mixed | owned | ingest-generated | 3294.09 (3290.61–3444.10) | 2976.00 |
| proto-mixed | owned | read-generated-aot | 34.93 (34.50–37.76) | 0.00 |
| proto-mixed | owned | interpret-generated | 36.98 (35.74–40.77) | 2.00 |
| proto-mixed | owned | peak-generated | 7.18 (7.06–7.48) | 1.00 |
| proto-mixed | owned | serialize | 2793.75 (2699.96–3148.99) | 556.00 |
| proto-mixed | buffa | ingest | 1751.05 (1750.07–1764.67) | 1743.75 |
| proto-mixed | buffa | ingest-typed | 1844.20 (1788.11–1874.79) | 1743.75 |
| proto-mixed | buffa | interpret | 49.19 (42.89–63.93) | 2.00 |
| proto-mixed | buffa | peak | 8.70 (8.48–9.14) | 1.00 |
| proto-mixed | buffa | ingest-generated | 1638.85 (1597.00–2129.88) | 1743.75 |
| proto-mixed | buffa | read-generated-aot | 17.29 (17.15–281.32) | 0.00 |
| proto-mixed | buffa | interpret-generated | 18.00 (17.59–18.01) | 2.00 |
| proto-mixed | buffa | peak-generated | 8.08 (8.04–8.86) | 1.00 |
| proto-mixed | buffa | serialize | 27.47 (21.59–39.55) | 176.50 |
| proto-mixed | generated-owned | ingest | 806.08 (774.86–834.56) | 1800.50 |
| proto-mixed | generated-owned | read-aot | 4.68 (4.57–5.09) | 0.00 |
| proto-mixed | generated-owned | interpret | 3.36 (3.14–3.64) | 2.00 |
| proto-mixed | generated-owned | peak | 3.46 (3.35–3.50) | 1.00 |
| proto-mixed | generated-owned | serialize | 520.94 (507.88–579.33) | 192.50 |
| proto-mixed-full | stock | ingest | 829.34 (765.03–882.11) | 1776.75 |
| proto-mixed-full | owned | ingest | 4851.20 (4823.21–4897.73) | 2976.00 |
| proto-mixed-full | owned | ingest-generated | 3954.28 (3921.38–4136.83) | 2976.00 |
| proto-mixed-full | buffa | ingest | 2728.80 (2475.97–2739.01) | 1870.25 |
| proto-mixed-full | buffa | ingest-generated | 2116.99 (1914.92–2400.32) | 1870.25 |
| proto-mixed-full | generated-owned | ingest | 891.99 (816.02–1073.19) | 1800.50 |

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
