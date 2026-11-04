# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 126.79 (124.21–128.47) | 136.00 |
| proto-scalar | stock | read-aot | 2.66 (2.55–3.27) | 0.00 |
| proto-scalar | stock | interpret | 3.94 (3.09–4.84) | 2.00 |
| proto-scalar | stock | peak | 2.09 (2.03–2.10) | 1.00 |
| proto-scalar | stock | serialize | 35.21 (34.79–1487.33) | 56.00 |
| proto-scalar | stock | project-aot | 84.38 (80.36–89.44) | 136.00 |
| proto-scalar | stock | project-interpret | 86.95 (86.33–89.99) | 138.00 |
| proto-scalar | stock | project-peak | 89.84 (87.43–91.88) | 137.00 |
| proto-scalar | owned | ingest | 247.27 (150.38–277.60) | 64.00 |
| proto-scalar | owned | ingest-typed | 143.70 (128.62–143.85) | 48.00 |
| proto-scalar | owned | interpret | 39.82 (37.55–40.33) | 2.00 |
| proto-scalar | owned | peak | 4.35 (4.24–4.61) | 1.00 |
| proto-scalar | owned | ingest-generated | 51.17 (49.28–51.85) | 48.00 |
| proto-scalar | owned | read-generated-aot | 16.75 (15.94–17.18) | 0.00 |
| proto-scalar | owned | interpret-generated | 17.91 (17.68–19.09) | 2.00 |
| proto-scalar | owned | peak-generated | 3.77 (3.77–5.32) | 1.00 |
| proto-scalar | owned | serialize | 29.95 (28.08–31.69) | 24.00 |
| proto-scalar | buffa | ingest | 261.18 (249.37–264.89) | 112.00 |
| proto-scalar | buffa | ingest-typed | 275.87 (265.53–418.25) | 80.00 |
| proto-scalar | buffa | interpret | 34.97 (29.43–49.12) | 2.00 |
| proto-scalar | buffa | peak | 4.30 (4.13–4.69) | 1.00 |
| proto-scalar | buffa | ingest-generated | 133.95 (129.10–136.65) | 80.00 |
| proto-scalar | buffa | read-generated-aot | 18.48 (18.34–20.58) | 0.00 |
| proto-scalar | buffa | interpret-generated | 22.78 (21.57–25.62) | 2.00 |
| proto-scalar | buffa | peak-generated | 5.14 (4.25–5.29) | 1.00 |
| proto-scalar | buffa | serialize | 27.82 (26.81–28.93) | 24.00 |
| proto-scalar | generated-owned | ingest | 41.81 (40.28–43.05) | 88.00 |
| proto-scalar | generated-owned | read-aot | 2.01 (1.84–2.24) | 0.00 |
| proto-scalar | generated-owned | interpret | 3.08 (2.39–3.30) | 2.00 |
| proto-scalar | generated-owned | peak | 2.16 (1.98–6.58) | 1.00 |
| proto-scalar | generated-owned | serialize | 34.27 (33.96–36.00) | 24.00 |
| proto-scalar | generated-owned | project-aot | 14.85 (13.81–19.20) | 0.00 |
| proto-scalar | generated-owned | project-interpret | 18.03 (16.74–18.76) | 2.00 |
| proto-scalar | generated-owned | project-peak | 16.35 (15.82–16.52) | 1.00 |
| proto-bytes | stock | ingest | 30317.25 (29022.81–46547.83) | 65699.00 |
| proto-bytes | stock | read-aot | 4.56 (4.50–4.93) | 0.00 |
| proto-bytes | stock | interpret | 6.68 (6.20–7.53) | 2.00 |
| proto-bytes | stock | peak | 5.45 (5.04–6.18) | 1.00 |
| proto-bytes | stock | serialize | 8511.51 (8220.24–8723.39) | 65599.00 |
| proto-bytes | stock-aliased | ingest | 105.70 (96.50–107.56) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 5.24 (4.63–6.53) | 0.00 |
| proto-bytes | stock-aliased | interpret | 7.80 (6.66–12.73) | 2.00 |
| proto-bytes | stock-aliased | peak | 5.26 (5.13–5.26) | 1.00 |
| proto-bytes | stock-aliased | serialize | 8976.52 (7879.82–9510.91) | 65599.00 |
| proto-bytes | owned | ingest | 10022.62 (9344.57–14583.64) | 65995.00 |
| proto-bytes | owned | interpret | 37.61 (30.71–38.65) | 2.00 |
| proto-bytes | owned | peak | 7.21 (7.19–7.61) | 1.00 |
| proto-bytes | owned | ingest-generated | 10002.05 (9383.65–10044.49) | 65995.00 |
| proto-bytes | owned | read-generated-aot | 24.31 (23.17–26.83) | 0.00 |
| proto-bytes | owned | interpret-generated | 28.18 (26.48–32.32) | 2.00 |
| proto-bytes | owned | peak-generated | 7.04 (6.99–8.65) | 1.00 |
| proto-bytes | owned | serialize | 9318.24 (9147.27–52934.72) | 65615.00 |
| proto-bytes | buffa | ingest | 404.46 (394.11–407.37) | 272.00 |
| proto-bytes | buffa | interpret | 31.90 (30.77–36.16) | 2.00 |
| proto-bytes | buffa | peak | 7.44 (7.07–7.50) | 1.00 |
| proto-bytes | buffa | ingest-generated | 342.76 (309.26–416.16) | 272.00 |
| proto-bytes | buffa | read-generated-aot | 17.76 (15.61–17.91) | 0.00 |
| proto-bytes | buffa | interpret-generated | 17.44 (17.39–22.75) | 2.00 |
| proto-bytes | buffa | peak-generated | 8.25 (8.23–9.75) | 1.00 |
| proto-bytes | buffa | serialize | 9078.85 (9003.34–43344.99) | 65567.00 |
| proto-bytes | generated-owned | ingest | 9606.46 (9002.96–142751.42) | 65651.00 |
| proto-bytes | generated-owned | read-aot | 2.31 (2.27–2.45) | 0.00 |
| proto-bytes | generated-owned | interpret | 3.23 (2.89–3.24) | 2.00 |
| proto-bytes | generated-owned | peak | 3.78 (2.75–3.88) | 1.00 |
| proto-bytes | generated-owned | serialize | 8728.41 (8432.70–9630.17) | 65583.00 |
| timestamp-json | stock | ingest | 4987.17 (4887.66–6803.62) | 6439.75 |
| timestamp-json | owned | ingest | 2116.90 (2021.38–2124.53) | 3655.75 |
| timestamp-json | owned | ingest-typed | 2208.43 (2005.25–2234.50) | 3639.75 |
| timestamp-json | owned | ingest-generated | 2808.50 (2285.50–3164.71) | 3639.75 |
| timestamp-json | buffa | ingest | 860.36 (859.13–905.64) | 688.00 |
| timestamp-json | buffa | ingest-typed | 912.48 (853.91–926.94) | 688.00 |
| timestamp-json | buffa | ingest-generated | 809.69 (754.73–855.03) | 688.00 |
| mysql-int4 | stock | ingest | 63.09 (61.96–66.50) | 16.00 |
| mysql-int4 | stock | read-aot | 1.91 (1.87–1.93) | 0.00 |
| mysql-int4 | stock | interpret | 3.57 (3.48–37.77) | 2.00 |
| mysql-int4 | stock | peak | 1.87 (1.74–2.77) | 1.00 |
| mysql-int4 | static | ingest | 171.16 (167.59–280.82) | 128.00 |
| mysql-int4 | static | ingest-typed | 172.34 (167.98–212.14) | 128.00 |
| mysql-int4 | static | interpret | 24.04 (22.12–31.71) | 2.00 |
| mysql-int4 | static | peak | 4.21 (4.16–4.50) | 1.00 |
| postgres-int4 | stock | ingest | 42.90 (41.76–43.02) | 16.00 |
| postgres-int4 | stock | read-aot | 2.12 (2.04–2.26) | 0.00 |
| postgres-int4 | stock | interpret | 3.51 (3.43–5.23) | 2.00 |
| postgres-int4 | stock | peak | 1.98 (1.73–2.08) | 1.00 |
| postgres-int4 | static | ingest | 163.73 (161.79–165.42) | 128.00 |
| postgres-int4 | static | ingest-typed | 160.71 (156.07–187.52) | 128.00 |
| postgres-int4 | static | interpret | 22.75 (22.60–23.77) | 2.00 |
| postgres-int4 | static | peak | 5.04 (4.60–5.99) | 1.00 |
| proto-mixed | stock | ingest | 1580.50 (1526.71–2146.68) | 1776.75 |
| proto-mixed | stock | read-aot | 13.79 (12.09–15.42) | 0.00 |
| proto-mixed | stock | interpret-boundary | 13.80 (13.42–15.22) | 2.00 |
| proto-mixed | stock | peak-boundary | 18.95 (14.37–18.97) | 1.00 |
| proto-mixed | stock | serialize | 1081.64 (965.93–1246.35) | 436.50 |
| proto-mixed | owned | ingest | 8184.53 (7652.45–14485.25) | 2976.00 |
| proto-mixed | owned | ingest-typed | 7764.32 (7383.24–9467.05) | 2976.00 |
| proto-mixed | owned | interpret | 114.76 (111.23–117.55) | 2.00 |
| proto-mixed | owned | peak | 15.69 (15.01–16.56) | 1.00 |
| proto-mixed | owned | ingest-generated | 8193.07 (7977.43–16303.32) | 2976.00 |
| proto-mixed | owned | read-generated-aot | 82.91 (81.14–114.73) | 0.00 |
| proto-mixed | owned | interpret-generated | 84.79 (82.09–85.03) | 2.00 |
| proto-mixed | owned | peak-generated | 16.53 (14.72–26.87) | 1.00 |
| proto-mixed | owned | serialize | 6263.69 (6123.70–6822.51) | 556.00 |
| proto-mixed | buffa | ingest | 6300.77 (6163.91–8135.42) | 1727.75 |
| proto-mixed | buffa | ingest-typed | 6783.56 (6275.66–7019.44) | 1727.75 |
| proto-mixed | buffa | interpret | 94.72 (91.99–108.73) | 2.00 |
| proto-mixed | buffa | peak | 19.76 (18.14–20.05) | 1.00 |
| proto-mixed | buffa | ingest-generated | 5705.88 (5645.77–5809.89) | 1727.75 |
| proto-mixed | buffa | read-generated-aot | 43.47 (43.06–53.05) | 0.00 |
| proto-mixed | buffa | interpret-generated | 45.73 (45.59–54.97) | 2.00 |
| proto-mixed | buffa | peak-generated | 17.75 (16.90–19.60) | 1.00 |
| proto-mixed | buffa | serialize | 46.17 (43.55–50.51) | 176.50 |
| proto-mixed | generated-owned | ingest | 1483.66 (1368.29–1558.52) | 1800.50 |
| proto-mixed | generated-owned | read-aot | 10.03 (9.95–13.10) | 0.00 |
| proto-mixed | generated-owned | interpret | 7.68 (7.20–10.78) | 2.00 |
| proto-mixed | generated-owned | peak | 7.21 (6.90–7.77) | 1.00 |
| proto-mixed | generated-owned | serialize | 1248.04 (1240.84–1273.33) | 192.50 |
| proto-mixed-full | stock | ingest | 1437.68 (1398.15–1624.32) | 1776.75 |
| proto-mixed-full | owned | ingest | 16738.64 (12345.04–17272.34) | 2976.00 |
| proto-mixed-full | buffa | ingest | 8371.03 (7826.27–9694.06) | 1854.25 |
| proto-mixed-full | generated-owned | ingest | 1579.42 (1485.55–1803.23) | 1800.50 |

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

Binary SHA-256: `6d9bc593134202efdc8a8dee8fe02a778d4197001458f5293eef515483849190`
Revision: `f564926a6f5f6ac806415e41f4c645641fcbbe16`
