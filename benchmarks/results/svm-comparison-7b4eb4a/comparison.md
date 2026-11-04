# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 66.92 (66.80–67.72) | 136.00 |
| proto-scalar | stock | read-aot | 1.35 (1.32–1.37) | 0.00 |
| proto-scalar | stock | interpret | 1.89 (1.88–21.05) | 2.00 |
| proto-scalar | stock | peak | 1.11 (1.10–1.11) | 1.00 |
| proto-scalar | stock | serialize | 21.78 (19.33–23.78) | 56.00 |
| proto-scalar | stock | project-aot | 49.48 (46.12–72.09) | 136.00 |
| proto-scalar | stock | project-interpret | 48.32 (46.97–57.41) | 138.00 |
| proto-scalar | stock | project-peak | 48.74 (48.62–68.75) | 137.00 |
| proto-scalar | owned | ingest | 84.99 (79.32–87.98) | 88.00 |
| proto-scalar | owned | ingest-typed | 78.15 (76.88–111.64) | 56.00 |
| proto-scalar | owned | interpret | 26.27 (21.13–28.38) | 2.00 |
| proto-scalar | owned | peak | 2.12 (1.88–2.27) | 1.00 |
| proto-scalar | owned | ingest-generated | 30.41 (29.22–34.26) | 56.00 |
| proto-scalar | owned | read-generated-aot | 9.65 (9.52–10.97) | 0.00 |
| proto-scalar | owned | interpret-generated | 10.71 (10.62–10.80) | 2.00 |
| proto-scalar | owned | peak-generated | 1.91 (1.89–1.97) | 1.00 |
| proto-scalar | owned | serialize | 87.08 (86.31–92.29) | 136.00 |
| proto-scalar | buffa | ingest | 210.27 (206.16–213.60) | 240.00 |
| proto-scalar | buffa | ingest-typed | 207.82 (205.81–219.64) | 208.00 |
| proto-scalar | buffa | interpret | 16.33 (16.25–16.89) | 2.00 |
| proto-scalar | buffa | peak | 2.07 (2.01–2.10) | 1.00 |
| proto-scalar | buffa | ingest-generated | 153.17 (150.74–154.58) | 208.00 |
| proto-scalar | buffa | read-generated-aot | 10.94 (10.79–14.83) | 0.00 |
| proto-scalar | buffa | interpret-generated | 12.09 (11.86–15.46) | 2.00 |
| proto-scalar | buffa | peak-generated | 2.08 (2.06–2.50) | 1.00 |
| proto-scalar | buffa | serialize | 17.39 (17.29–18.31) | 24.00 |
| proto-scalar | generated-owned | ingest | 24.65 (24.02–24.72) | 80.00 |
| proto-scalar | generated-owned | read-aot | 3.11 (2.92–3.52) | 0.00 |
| proto-scalar | generated-owned | interpret | 4.58 (4.56–4.64) | 2.00 |
| proto-scalar | generated-owned | peak | 1.25 (1.24–1.28) | 1.00 |
| proto-scalar | generated-owned | serialize | 81.32 (80.00–86.20) | 72.00 |
| proto-scalar | generated-owned | project-aot | 9.42 (9.29–10.73) | 0.00 |
| proto-scalar | generated-owned | project-interpret | 10.61 (10.32–10.63) | 2.00 |
| proto-scalar | generated-owned | project-peak | 10.94 (10.86–11.18) | 1.00 |
| proto-bytes | stock | ingest | 21479.62 (19521.19–22351.30) | 65699.00 |
| proto-bytes | stock | read-aot | 3.00 (2.98–3.13) | 0.00 |
| proto-bytes | stock | interpret | 4.38 (4.38–5.98) | 2.00 |
| proto-bytes | stock | peak | 3.66 (3.30–3.77) | 1.00 |
| proto-bytes | stock | serialize | 4622.62 (4549.34–5070.97) | 65599.00 |
| proto-bytes | stock-aliased | ingest | 51.75 (50.30–56.29) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 3.04 (2.95–3.48) | 0.00 |
| proto-bytes | stock-aliased | interpret | 4.29 (3.95–5.85) | 2.00 |
| proto-bytes | stock-aliased | peak | 3.31 (2.95–3.37) | 1.00 |
| proto-bytes | stock-aliased | serialize | 5579.70 (4648.56–38199.89) | 65599.00 |
| proto-bytes | owned | ingest | 4894.51 (4811.83–39261.31) | 66035.00 |
| proto-bytes | owned | interpret | 17.52 (17.49–18.14) | 2.00 |
| proto-bytes | owned | peak | 4.04 (3.96–4.77) | 1.00 |
| proto-bytes | owned | ingest-generated | 5075.45 (4844.89–13902.20) | 66035.00 |
| proto-bytes | owned | read-generated-aot | 14.46 (13.89–16.69) | 0.00 |
| proto-bytes | owned | interpret-generated | 15.45 (15.39–15.62) | 2.00 |
| proto-bytes | owned | peak-generated | 4.27 (3.95–4.95) | 1.00 |
| proto-bytes | owned | serialize | 417000.34 (415849.03–433475.46) | 65615.00 |
| proto-bytes | buffa | ingest | 259.04 (221.39–320.97) | 304.00 |
| proto-bytes | buffa | interpret | 23.45 (19.86–23.85) | 2.00 |
| proto-bytes | buffa | peak | 3.94 (3.81–3.98) | 1.00 |
| proto-bytes | buffa | ingest-generated | 171.44 (169.65–177.92) | 304.00 |
| proto-bytes | buffa | read-generated-aot | 9.14 (8.85–9.65) | 0.00 |
| proto-bytes | buffa | interpret-generated | 10.54 (10.08–10.72) | 2.00 |
| proto-bytes | buffa | peak-generated | 4.27 (4.23–4.36) | 1.00 |
| proto-bytes | buffa | serialize | 24475.12 (4754.53–30797.89) | 65567.00 |
| proto-bytes | generated-owned | ingest | 20722.64 (19862.96–21734.07) | 65651.00 |
| proto-bytes | generated-owned | read-aot | 3.62 (3.57–5.38) | 0.00 |
| proto-bytes | generated-owned | interpret | 5.18 (5.07–6.36) | 2.00 |
| proto-bytes | generated-owned | peak | 1.65 (1.63–2.63) | 1.00 |
| proto-bytes | generated-owned | serialize | 4606.33 (4303.33–6849.17) | 65615.00 |
| timestamp-json | stock | ingest | 3557.23 (3333.12–3838.24) | 6439.75 |
| timestamp-json | owned | ingest | 1595.21 (1390.04–3845.51) | 3703.75 |
| timestamp-json | owned | ingest-typed | 1421.43 (1358.34–1422.82) | 3671.75 |
| timestamp-json | owned | ingest-generated | 1359.75 (1261.28–1501.51) | 3671.75 |
| timestamp-json | buffa | ingest | 567.57 (551.96–570.30) | 720.00 |
| timestamp-json | buffa | ingest-typed | 622.64 (573.22–771.37) | 720.00 |
| timestamp-json | buffa | ingest-generated | 487.43 (478.33–507.48) | 720.00 |
| mysql-int4 | stock | ingest | 34.37 (33.49–38.07) | 16.00 |
| mysql-int4 | stock | read-aot | 1.11 (1.10–1.19) | 0.00 |
| mysql-int4 | stock | interpret | 2.06 (1.97–2.99) | 2.00 |
| mysql-int4 | stock | peak | 1.01 (1.01–1.07) | 1.00 |
| mysql-int4 | static | ingest | 105.21 (102.98–142.97) | 128.00 |
| mysql-int4 | static | ingest-typed | 100.97 (100.54–104.64) | 128.00 |
| mysql-int4 | static | interpret | 13.82 (13.80–15.60) | 2.00 |
| mysql-int4 | static | peak | 2.23 (2.22–2.85) | 1.00 |
| postgres-int4 | stock | ingest | 24.00 (23.53–24.39) | 16.00 |
| postgres-int4 | stock | read-aot | 1.20 (1.19–1.54) | 0.00 |
| postgres-int4 | stock | interpret | 1.92 (1.92–1.95) | 2.00 |
| postgres-int4 | stock | peak | 1.00 (0.98–1.01) | 1.00 |
| postgres-int4 | static | ingest | 94.06 (92.83–94.06) | 128.00 |
| postgres-int4 | static | ingest-typed | 94.41 (91.76–96.43) | 128.00 |
| postgres-int4 | static | interpret | 14.98 (14.73–21.98) | 2.00 |
| postgres-int4 | static | peak | 2.21 (2.20–3.10) | 1.00 |
| proto-mixed | stock | ingest | 892.47 (889.59–910.63) | 1776.75 |
| proto-mixed | stock | read-aot | 6.95 (6.89–7.38) | 0.00 |
| proto-mixed | stock | interpret-boundary | 7.77 (7.68–8.81) | 2.00 |
| proto-mixed | stock | peak-boundary | 7.16 (7.16–7.62) | 1.00 |
| proto-mixed | owned | ingest | 8136.01 (7997.99–12754.82) | 3339.50 |
| proto-mixed | owned | ingest-typed | 8255.00 (8173.18–8459.53) | 3339.50 |
| proto-mixed | owned | interpret | 62.84 (61.75–69.43) | 2.00 |
| proto-mixed | owned | peak | 9.20 (9.19–9.43) | 1.00 |
| proto-mixed | owned | ingest-generated | 8163.54 (8049.33–8380.38) | 3339.50 |
| proto-mixed | owned | read-generated-aot | 50.45 (47.98–51.75) | 0.00 |
| proto-mixed | owned | interpret-generated | 59.04 (50.00–61.68) | 2.00 |
| proto-mixed | owned | peak-generated | 8.61 (8.61–8.62) | 1.00 |
| proto-mixed | buffa | ingest | 3450.98 (3384.54–3461.76) | 1759.75 |
| proto-mixed | buffa | ingest-typed | 3844.80 (3459.00–4156.52) | 1759.75 |
| proto-mixed | buffa | interpret | 52.48 (51.63–56.69) | 2.00 |
| proto-mixed | buffa | peak | 9.91 (9.87–10.70) | 1.00 |
| proto-mixed | buffa | ingest-generated | 3341.51 (3308.34–4055.30) | 1759.75 |
| proto-mixed | buffa | read-generated-aot | 23.35 (23.10–23.43) | 0.00 |
| proto-mixed | buffa | interpret-generated | 29.57 (24.71–42.42) | 2.00 |
| proto-mixed | buffa | peak-generated | 9.26 (9.19–13.36) | 1.00 |
| proto-mixed-full | stock | ingest | 1020.12 (932.64–1051.60) | 1776.75 |
| proto-mixed-full | owned | ingest | 10784.25 (10054.50–15049.21) | 3339.50 |
| proto-mixed-full | buffa | ingest | 4392.14 (4375.44–4526.82) | 1886.25 |

Stock read-aot is the native-image host baseline. Schema 4 adds stock guest tiers; mixed protobuf uses explicitly labeled boundary projections because its collection getters fail the SVM JIT blocklist.
Ingest includes parsing/materialization and an uncached projected read. Setup is excluded.
ingest-typed uses primitive readLong projections; ingest retains generic read projections.
Generated stages use schema-bound generated getters; read-generated-aot excludes guest calls. Original library-dispatch stages remain.
Schema 7 adds generated-owned concrete scalar/bytes records and serialization. Serialization emits a fresh byte array; Buffa binary views forward the validated original wire, while owned and stock messages encode fields. These operations have equivalent wire semantics, but differ in work performed.
Schema 8 adds parse-and-project without retaining messages: generated code can fuse in guest compilation; stock parsing stays behind an explicit boundary. AOT and interpreter measurements use the same operation.
Buffa binary input is already in unmanaged memory; owned engines start with Java byte arrays.
Buffa results are scoped borrowed views; stock-aliased borrows immutable ByteString input; stock/owned copy payloads.
The direct stock AOT baseline excludes guest interop and contract enforcement.
MySQL/Postgres measure driver INT4 decoder functions and retained scalar rows, not JDBC queries, transport, or servers.
Java heap counters exclude native allocations and compiler/background threads. Native allocation is not reported as zero.
Shared execution hardware and short samples make these diagnostic comparisons, not performance guarantees.

Binary SHA-256: `686385c1f9ac0724ec7227a0766d0712e55bddb0b31201c4c85eb0625795605b`
Revision: `7b4eb4a455ecceb45e4d3afe81dd7072b4c98291`
