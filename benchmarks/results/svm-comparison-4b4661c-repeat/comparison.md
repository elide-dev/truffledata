# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 105.07 (93.12–111.98) | 136.00 |
| proto-scalar | stock | read-aot | 1.53 (1.46–1.97) | 0.00 |
| proto-scalar | stock | interpret | 4.38 (2.77–36.14) | 2.00 |
| proto-scalar | stock | peak | 1.52 (1.47–1.55) | 1.00 |
| proto-scalar | stock | serialize | 27.14 (22.02–28.37) | 56.00 |
| proto-scalar | stock | project-aot | 61.44 (52.55–72.19) | 136.00 |
| proto-scalar | stock | project-interpret | 76.49 (62.17–81.38) | 138.00 |
| proto-scalar | stock | project-peak | 64.62 (53.94–89.93) | 137.00 |
| proto-scalar | owned | ingest | 84.35 (82.19–106.35) | 88.00 |
| proto-scalar | owned | ingest-typed | 110.22 (105.31–122.15) | 56.00 |
| proto-scalar | owned | interpret | 27.07 (22.35–32.15) | 2.00 |
| proto-scalar | owned | peak | 2.19 (2.05–3.30) | 1.00 |
| proto-scalar | owned | ingest-generated | 38.82 (34.02–52.06) | 56.00 |
| proto-scalar | owned | read-generated-aot | 12.16 (10.84–13.72) | 0.00 |
| proto-scalar | owned | interpret-generated | 14.25 (13.75–16.69) | 2.00 |
| proto-scalar | owned | peak-generated | 4.30 (2.27–4.61) | 1.00 |
| proto-scalar | owned | serialize | 160.93 (156.27–3016.11) | 136.00 |
| proto-scalar | buffa | ingest | 143.01 (131.63–181.76) | 112.00 |
| proto-scalar | buffa | ingest-typed | 158.94 (130.38–194.57) | 80.00 |
| proto-scalar | buffa | interpret | 18.66 (17.11–24.70) | 2.00 |
| proto-scalar | buffa | peak | 3.10 (2.80–5.26) | 1.00 |
| proto-scalar | buffa | ingest-generated | 114.44 (98.17–117.83) | 80.00 |
| proto-scalar | buffa | read-generated-aot | 12.45 (12.25–23.94) | 0.00 |
| proto-scalar | buffa | interpret-generated | 16.92 (16.64–20.63) | 2.00 |
| proto-scalar | buffa | peak-generated | 2.94 (2.18–4.33) | 1.00 |
| proto-scalar | buffa | serialize | 24.37 (16.75–27.37) | 24.00 |
| proto-scalar | generated-owned | ingest | 33.88 (33.00–40.89) | 80.00 |
| proto-scalar | generated-owned | read-aot | 1.22 (1.00–1.83) | 0.00 |
| proto-scalar | generated-owned | interpret | 2.13 (1.82–2.23) | 2.00 |
| proto-scalar | generated-owned | peak | 1.55 (1.30–1.76) | 1.00 |
| proto-scalar | generated-owned | serialize | 38.98 (37.29–43.24) | 40.00 |
| proto-scalar | generated-owned | project-aot | 9.12 (8.96–9.45) | 0.00 |
| proto-scalar | generated-owned | project-interpret | 13.67 (12.87–16.67) | 2.00 |
| proto-scalar | generated-owned | project-peak | 13.45 (12.34–17.65) | 1.00 |
| proto-bytes | stock | ingest | 31817.13 (30447.72–33699.56) | 65699.00 |
| proto-bytes | stock | read-aot | 3.52 (3.31–3.96) | 0.00 |
| proto-bytes | stock | interpret | 5.59 (5.17–5.84) | 2.00 |
| proto-bytes | stock | peak | 4.04 (3.81–4.12) | 1.00 |
| proto-bytes | stock | serialize | 8794.15 (5852.39–9794.62) | 65599.00 |
| proto-bytes | stock-aliased | ingest | 69.91 (58.87–77.61) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 3.25 (2.59–3.37) | 0.00 |
| proto-bytes | stock-aliased | interpret | 5.33 (5.20–6.80) | 2.00 |
| proto-bytes | stock-aliased | peak | 5.81 (3.53–6.43) | 1.00 |
| proto-bytes | stock-aliased | serialize | 7238.05 (5939.59–43710.00) | 65599.00 |
| proto-bytes | owned | ingest | 6326.27 (6224.84–21493.60) | 66035.00 |
| proto-bytes | owned | interpret | 23.76 (20.24–26.70) | 2.00 |
| proto-bytes | owned | peak | 6.38 (5.49–7.58) | 1.00 |
| proto-bytes | owned | ingest-generated | 5747.00 (5017.57–28682.27) | 66035.00 |
| proto-bytes | owned | read-generated-aot | 30.93 (13.85–31.12) | 0.00 |
| proto-bytes | owned | interpret-generated | 22.14 (18.01–23.72) | 2.00 |
| proto-bytes | owned | peak-generated | 4.50 (3.65–5.68) | 1.00 |
| proto-bytes | owned | serialize | 6657.96 (5580.57–12668.06) | 65615.00 |
| proto-bytes | buffa | ingest | 383.75 (263.70–403.68) | 272.00 |
| proto-bytes | buffa | interpret | 23.23 (19.29–25.94) | 2.00 |
| proto-bytes | buffa | peak | 5.35 (4.53–5.89) | 1.00 |
| proto-bytes | buffa | ingest-generated | 251.62 (236.33–315.00) | 272.00 |
| proto-bytes | buffa | read-generated-aot | 11.61 (9.08–13.05) | 0.00 |
| proto-bytes | buffa | interpret-generated | 11.49 (11.37–11.69) | 2.00 |
| proto-bytes | buffa | peak-generated | 5.83 (5.04–6.27) | 1.00 |
| proto-bytes | buffa | serialize | 29126.94 (5472.26–32469.83) | 65567.00 |
| proto-bytes | generated-owned | ingest | 5513.44 (5313.87–5565.32) | 65651.00 |
| proto-bytes | generated-owned | read-aot | 1.68 (1.56–2.23) | 0.00 |
| proto-bytes | generated-owned | interpret | 2.48 (2.20–6.25) | 2.00 |
| proto-bytes | generated-owned | peak | 2.41 (1.66–2.77) | 1.00 |
| proto-bytes | generated-owned | serialize | 5700.93 (5335.06–37133.29) | 65583.00 |
| timestamp-json | stock | ingest | 4528.36 (3208.63–5429.84) | 6439.75 |
| timestamp-json | owned | ingest | 8889.23 (1224.77–28159.83) | 3703.75 |
| timestamp-json | owned | ingest-typed | 1784.35 (1506.15–2182.60) | 3671.75 |
| timestamp-json | owned | ingest-generated | 2026.13 (1947.96–2139.05) | 3671.75 |
| timestamp-json | buffa | ingest | 592.32 (511.52–595.47) | 688.00 |
| timestamp-json | buffa | ingest-typed | 653.67 (533.95–895.05) | 688.00 |
| timestamp-json | buffa | ingest-generated | 569.45 (470.09–644.82) | 688.00 |
| mysql-int4 | stock | ingest | 50.25 (44.92–51.21) | 16.00 |
| mysql-int4 | stock | read-aot | 1.48 (1.30–1.50) | 0.00 |
| mysql-int4 | stock | interpret | 2.75 (1.96–3.25) | 2.00 |
| mysql-int4 | stock | peak | 1.23 (1.15–1.44) | 1.00 |
| mysql-int4 | static | ingest | 119.55 (116.63–153.90) | 128.00 |
| mysql-int4 | static | ingest-typed | 161.87 (106.97–182.15) | 128.00 |
| mysql-int4 | static | interpret | 16.74 (14.91–20.73) | 2.00 |
| mysql-int4 | static | peak | 3.40 (2.08–4.69) | 1.00 |
| postgres-int4 | stock | ingest | 31.03 (27.78–48.15) | 16.00 |
| postgres-int4 | stock | read-aot | 1.67 (1.63–2.00) | 0.00 |
| postgres-int4 | stock | interpret | 3.51 (2.70–5.02) | 2.00 |
| postgres-int4 | stock | peak | 1.17 (1.12–3.05) | 1.00 |
| postgres-int4 | static | ingest | 111.25 (106.42–151.73) | 128.00 |
| postgres-int4 | static | ingest-typed | 129.41 (112.92–211.44) | 128.00 |
| postgres-int4 | static | interpret | 20.26 (14.25–26.66) | 2.00 |
| postgres-int4 | static | peak | 3.20 (2.64–3.89) | 1.00 |
| proto-mixed | stock | ingest | 1195.42 (1118.11–1219.66) | 1776.75 |
| proto-mixed | stock | read-aot | 8.99 (7.74–9.43) | 0.00 |
| proto-mixed | stock | interpret-boundary | 11.41 (8.86–11.55) | 2.00 |
| proto-mixed | stock | peak-boundary | 7.50 (6.76–7.78) | 1.00 |
| proto-mixed | owned | ingest | 11384.49 (9332.73–14512.47) | 3339.50 |
| proto-mixed | owned | ingest-typed | 18498.44 (11544.48–25012.68) | 3339.50 |
| proto-mixed | owned | interpret | 87.24 (76.39–96.82) | 2.00 |
| proto-mixed | owned | peak | 14.75 (12.22–16.05) | 1.00 |
| proto-mixed | owned | ingest-generated | 13585.58 (11536.33–16440.75) | 3339.50 |
| proto-mixed | owned | read-generated-aot | 66.54 (62.49–68.90) | 0.00 |
| proto-mixed | owned | interpret-generated | 61.41 (53.96–76.21) | 2.00 |
| proto-mixed | owned | peak-generated | 11.53 (8.60–14.70) | 1.00 |
| proto-mixed | buffa | ingest | 4474.45 (3853.71–6507.19) | 1727.75 |
| proto-mixed | buffa | ingest-typed | 5321.78 (5230.15–7358.49) | 1727.75 |
| proto-mixed | buffa | interpret | 69.10 (53.68–80.18) | 2.00 |
| proto-mixed | buffa | peak | 14.69 (14.56–16.33) | 1.00 |
| proto-mixed | buffa | ingest-generated | 6647.56 (5750.47–8161.45) | 1727.75 |
| proto-mixed | buffa | read-generated-aot | 35.73 (29.31–36.05) | 0.00 |
| proto-mixed | buffa | interpret-generated | 38.49 (34.96–51.16) | 2.00 |
| proto-mixed | buffa | peak-generated | 11.54 (11.12–18.29) | 1.00 |
| proto-mixed-full | stock | ingest | 1172.11 (1006.90–1794.59) | 1776.75 |
| proto-mixed-full | owned | ingest | 17948.38 (13538.97–23983.25) | 3339.50 |
| proto-mixed-full | buffa | ingest | 5285.26 (4453.56–8429.40) | 1854.25 |

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
