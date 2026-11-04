# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 92.47 (77.08–103.68) | 136.00 |
| proto-scalar | stock | read-aot | 1.38 (1.31–1.53) | 0.00 |
| proto-scalar | stock | interpret | 2.83 (2.47–24.35) | 2.00 |
| proto-scalar | stock | peak | 1.18 (1.09–1.57) | 1.00 |
| proto-scalar | stock | serialize | 22.28 (21.47–34.49) | 56.00 |
| proto-scalar | stock | project-aot | 68.72 (61.12–68.73) | 136.00 |
| proto-scalar | stock | project-interpret | 64.60 (52.53–78.42) | 138.00 |
| proto-scalar | stock | project-peak | 80.96 (53.74–84.28) | 137.00 |
| proto-scalar | owned | ingest | 136.79 (97.77–141.61) | 88.00 |
| proto-scalar | owned | ingest-typed | 136.61 (90.49–145.08) | 56.00 |
| proto-scalar | owned | interpret | 22.29 (21.78–34.41) | 2.00 |
| proto-scalar | owned | peak | 2.82 (2.44–2.94) | 1.00 |
| proto-scalar | owned | ingest-generated | 39.50 (39.49–49.94) | 56.00 |
| proto-scalar | owned | read-generated-aot | 11.25 (10.34–13.17) | 0.00 |
| proto-scalar | owned | interpret-generated | 14.41 (13.62–17.27) | 2.00 |
| proto-scalar | owned | peak-generated | 2.80 (2.12–3.03) | 1.00 |
| proto-scalar | owned | serialize | 165.69 (122.24–190.75) | 136.00 |
| proto-scalar | buffa | ingest | 187.61 (140.48–232.37) | 112.00 |
| proto-scalar | buffa | ingest-typed | 218.73 (128.60–240.33) | 80.00 |
| proto-scalar | buffa | interpret | 24.70 (19.99–26.16) | 2.00 |
| proto-scalar | buffa | peak | 3.27 (2.36–3.49) | 1.00 |
| proto-scalar | buffa | ingest-generated | 95.43 (83.85–99.63) | 80.00 |
| proto-scalar | buffa | read-generated-aot | 14.99 (12.35–16.61) | 0.00 |
| proto-scalar | buffa | interpret-generated | 16.64 (12.11–19.47) | 2.00 |
| proto-scalar | buffa | peak-generated | 3.19 (2.61–4.27) | 1.00 |
| proto-scalar | buffa | serialize | 24.23 (16.12–24.32) | 24.00 |
| proto-scalar | generated-owned | ingest | 33.29 (28.99–38.94) | 80.00 |
| proto-scalar | generated-owned | read-aot | 1.18 (1.09–1.35) | 0.00 |
| proto-scalar | generated-owned | interpret | 1.64 (1.61–2.51) | 2.00 |
| proto-scalar | generated-owned | peak | 1.98 (1.58–2.53) | 1.00 |
| proto-scalar | generated-owned | serialize | 39.81 (36.51–40.57) | 40.00 |
| proto-scalar | generated-owned | project-aot | 13.52 (10.89–14.09) | 0.00 |
| proto-scalar | generated-owned | project-interpret | 15.58 (13.98–19.21) | 2.00 |
| proto-scalar | generated-owned | project-peak | 14.28 (9.89–17.16) | 1.00 |
| proto-bytes | stock | ingest | 26893.81 (21050.64–35960.35) | 65699.00 |
| proto-bytes | stock | read-aot | 3.49 (2.68–4.21) | 0.00 |
| proto-bytes | stock | interpret | 6.00 (5.66–6.30) | 2.00 |
| proto-bytes | stock | peak | 4.64 (4.17–5.04) | 1.00 |
| proto-bytes | stock | serialize | 5278.65 (4950.54–6011.53) | 65599.00 |
| proto-bytes | stock-aliased | ingest | 65.88 (58.87–69.81) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 3.02 (2.98–3.08) | 0.00 |
| proto-bytes | stock-aliased | interpret | 5.57 (4.72–6.24) | 2.00 |
| proto-bytes | stock-aliased | peak | 4.28 (3.83–7.20) | 1.00 |
| proto-bytes | stock-aliased | serialize | 6631.69 (5138.81–41686.40) | 65599.00 |
| proto-bytes | owned | ingest | 5641.29 (5452.13–23557.42) | 66035.00 |
| proto-bytes | owned | interpret | 25.80 (19.71–27.35) | 2.00 |
| proto-bytes | owned | peak | 5.38 (4.65–7.30) | 1.00 |
| proto-bytes | owned | ingest-generated | 6513.47 (5066.32–34398.40) | 66035.00 |
| proto-bytes | owned | read-generated-aot | 17.11 (14.15–19.67) | 0.00 |
| proto-bytes | owned | interpret-generated | 19.18 (17.74–21.46) | 2.00 |
| proto-bytes | owned | peak-generated | 6.08 (4.61–6.55) | 1.00 |
| proto-bytes | owned | serialize | 5260.71 (4517.61–43521.91) | 65615.00 |
| proto-bytes | buffa | ingest | 342.92 (243.37–421.53) | 272.00 |
| proto-bytes | buffa | interpret | 27.47 (19.91–30.47) | 2.00 |
| proto-bytes | buffa | peak | 4.37 (3.86–5.93) | 1.00 |
| proto-bytes | buffa | ingest-generated | 239.11 (158.04–299.28) | 272.00 |
| proto-bytes | buffa | read-generated-aot | 13.63 (11.34–14.66) | 0.00 |
| proto-bytes | buffa | interpret-generated | 15.41 (13.36–15.67) | 2.00 |
| proto-bytes | buffa | peak-generated | 5.79 (5.38–6.46) | 1.00 |
| proto-bytes | buffa | serialize | 31028.40 (6421.83–38393.20) | 65567.00 |
| proto-bytes | generated-owned | ingest | 5372.38 (4398.49–6164.22) | 65651.00 |
| proto-bytes | generated-owned | read-aot | 1.65 (1.41–3.02) | 0.00 |
| proto-bytes | generated-owned | interpret | 3.35 (1.97–3.39) | 2.00 |
| proto-bytes | generated-owned | peak | 2.29 (1.99–3.32) | 1.00 |
| proto-bytes | generated-owned | serialize | 5286.07 (4826.49–6326.02) | 65583.00 |
| timestamp-json | stock | ingest | 3801.86 (3187.27–5395.70) | 6439.75 |
| timestamp-json | owned | ingest | 2127.02 (1996.26–2155.13) | 3703.75 |
| timestamp-json | owned | ingest-typed | 2173.56 (1851.05–2281.37) | 3671.75 |
| timestamp-json | owned | ingest-generated | 1939.70 (1431.80–2123.04) | 3671.75 |
| timestamp-json | buffa | ingest | 640.90 (611.72–662.10) | 688.00 |
| timestamp-json | buffa | ingest-typed | 708.73 (541.62–879.81) | 688.00 |
| timestamp-json | buffa | ingest-generated | 533.88 (450.13–630.21) | 688.00 |
| mysql-int4 | stock | ingest | 48.54 (47.86–66.33) | 16.00 |
| mysql-int4 | stock | read-aot | 1.25 (1.14–1.89) | 0.00 |
| mysql-int4 | stock | interpret | 2.19 (2.05–3.15) | 2.00 |
| mysql-int4 | stock | peak | 1.12 (1.00–1.24) | 1.00 |
| mysql-int4 | static | ingest | 164.89 (124.03–227.40) | 128.00 |
| mysql-int4 | static | ingest-typed | 171.33 (164.68–176.35) | 128.00 |
| mysql-int4 | static | interpret | 16.49 (15.46–22.35) | 2.00 |
| mysql-int4 | static | peak | 3.82 (3.06–4.88) | 1.00 |
| postgres-int4 | stock | ingest | 34.69 (25.74–39.66) | 16.00 |
| postgres-int4 | stock | read-aot | 1.96 (1.56–2.08) | 0.00 |
| postgres-int4 | stock | interpret | 2.63 (1.88–2.92) | 2.00 |
| postgres-int4 | stock | peak | 1.38 (1.17–1.97) | 1.00 |
| postgres-int4 | static | ingest | 140.94 (121.29–150.07) | 128.00 |
| postgres-int4 | static | ingest-typed | 137.46 (91.64–168.82) | 128.00 |
| postgres-int4 | static | interpret | 19.15 (12.62–20.31) | 2.00 |
| postgres-int4 | static | peak | 2.76 (2.70–5.77) | 1.00 |
| proto-mixed | stock | ingest | 1140.93 (880.95–1283.88) | 1776.75 |
| proto-mixed | stock | read-aot | 7.46 (7.41–8.83) | 0.00 |
| proto-mixed | stock | interpret-boundary | 10.69 (9.68–12.60) | 2.00 |
| proto-mixed | stock | peak-boundary | 9.26 (7.68–10.00) | 1.00 |
| proto-mixed | owned | ingest | 12355.84 (8446.12–13406.48) | 3339.50 |
| proto-mixed | owned | ingest-typed | 10270.51 (9130.12–11496.60) | 3339.50 |
| proto-mixed | owned | interpret | 74.94 (67.60–106.61) | 2.00 |
| proto-mixed | owned | peak | 11.82 (11.51–13.45) | 1.00 |
| proto-mixed | owned | ingest-generated | 13522.70 (8360.73–13582.83) | 3339.50 |
| proto-mixed | owned | read-generated-aot | 57.65 (48.35–62.99) | 0.00 |
| proto-mixed | owned | interpret-generated | 60.81 (60.12–74.45) | 2.00 |
| proto-mixed | owned | peak-generated | 13.77 (13.00–13.86) | 1.00 |
| proto-mixed | buffa | ingest | 5354.17 (3551.77–6728.09) | 1727.75 |
| proto-mixed | buffa | ingest-typed | 3881.24 (3184.11–3920.25) | 1727.75 |
| proto-mixed | buffa | interpret | 80.49 (65.51–81.38) | 2.00 |
| proto-mixed | buffa | peak | 10.44 (10.35–16.34) | 1.00 |
| proto-mixed | buffa | ingest-generated | 5295.62 (3181.87–5539.82) | 1727.75 |
| proto-mixed | buffa | read-generated-aot | 28.87 (27.54–41.94) | 0.00 |
| proto-mixed | buffa | interpret-generated | 35.35 (31.96–40.16) | 2.00 |
| proto-mixed | buffa | peak-generated | 11.74 (10.32–12.97) | 1.00 |
| proto-mixed-full | stock | ingest | 1450.32 (962.39–1535.11) | 1776.75 |
| proto-mixed-full | owned | ingest | 10877.53 (10725.78–12621.41) | 3339.50 |
| proto-mixed-full | buffa | ingest | 8152.02 (7894.30–8525.28) | 1854.25 |

Stock read-aot is the native-image host baseline. Schema 4 adds stock guest tiers; mixed protobuf uses explicitly labeled boundary projections because its collection getters fail the SVM JIT blocklist.
Ingest includes parsing/materialization and an uncached projected read. Setup is excluded.
ingest-typed uses primitive readLong projections; ingest retains generic read projections.
Generated stages use schema-bound generated getters; read-generated-aot excludes guest calls. Original library-dispatch stages remain.
Schema 7 adds generated-owned concrete scalar/bytes records and serialization. Serialization emits a fresh byte array; Buffa binary views forward the validated original wire, while owned and stock messages encode fields. These operations have equivalent wire semantics, but differ in work performed.
Schema 9 measures parse-and-project with both parsers eligible for guest compilation (no explicit parser boundary); only invalid-input exception construction crosses a boundary. Both engines use a 10,000 graph-size budget. AOT and interpreter measurements use the same operation.
Schema 10 uses direct concrete generated getter loops matching the stock retained-read harness, without the generic projection adapter.
Buffa binary input is already in unmanaged memory; owned engines start with Java byte arrays.
Buffa results are scoped borrowed views; stock-aliased borrows immutable ByteString input; stock/owned copy payloads.
The direct stock AOT baseline excludes guest interop and contract enforcement.
MySQL/Postgres measure driver INT4 decoder functions and retained scalar rows, not JDBC queries, transport, or servers.
Java heap counters exclude native allocations and compiler/background threads. Native allocation is not reported as zero.
Shared execution hardware and short samples make these diagnostic comparisons, not performance guarantees.

Binary SHA-256: `0ac564d1bca0ad58ed97256cc2833ed1ba7861ef72ebd5801197cbf59e105a57`
Revision: `4b4661c2689ff5994be7a8cda6cafb619be1d6e9`
