# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 118.88 (99.57–123.96) | 136.00 |
| proto-scalar | stock | read-aot | 1.83 (1.50–2.04) | 0.00 |
| proto-scalar | stock | interpret | 2.80 (2.72–3.06) | 2.00 |
| proto-scalar | stock | peak | 1.58 (1.46–1.89) | 1.00 |
| proto-scalar | stock | serialize | 32.83 (26.93–793.52) | 56.00 |
| proto-scalar | stock | project-aot | 69.35 (52.32–76.37) | 136.00 |
| proto-scalar | stock | project-interpret | 66.93 (62.13–69.00) | 138.00 |
| proto-scalar | stock | project-peak | 69.80 (61.59–80.38) | 137.00 |
| proto-scalar | owned | ingest | 112.01 (74.63–138.68) | 88.00 |
| proto-scalar | owned | ingest-typed | 105.50 (74.28–108.60) | 56.00 |
| proto-scalar | owned | interpret | 36.29 (24.17–36.48) | 2.00 |
| proto-scalar | owned | peak | 3.22 (1.93–4.26) | 1.00 |
| proto-scalar | owned | ingest-generated | 47.87 (42.64–51.83) | 56.00 |
| proto-scalar | owned | read-generated-aot | 13.63 (10.26–13.95) | 0.00 |
| proto-scalar | owned | interpret-generated | 16.15 (14.80–16.42) | 2.00 |
| proto-scalar | owned | peak-generated | 2.87 (2.10–3.24) | 1.00 |
| proto-scalar | owned | serialize | 137.16 (131.07–161.67) | 136.00 |
| proto-scalar | buffa | ingest | 244.59 (169.05–256.40) | 112.00 |
| proto-scalar | buffa | ingest-typed | 229.78 (177.08–264.76) | 80.00 |
| proto-scalar | buffa | interpret | 23.35 (17.86–25.92) | 2.00 |
| proto-scalar | buffa | peak | 3.45 (2.40–3.58) | 1.00 |
| proto-scalar | buffa | ingest-generated | 104.85 (78.39–121.19) | 80.00 |
| proto-scalar | buffa | read-generated-aot | 15.88 (11.07–16.12) | 0.00 |
| proto-scalar | buffa | interpret-generated | 16.57 (12.40–17.22) | 2.00 |
| proto-scalar | buffa | peak-generated | 2.66 (2.04–3.27) | 1.00 |
| proto-scalar | buffa | serialize | 21.32 (17.38–28.59) | 24.00 |
| proto-scalar | generated-owned | ingest | 31.73 (27.50–43.29) | 88.00 |
| proto-scalar | generated-owned | read-aot | 1.84 (1.10–1.86) | 0.00 |
| proto-scalar | generated-owned | interpret | 2.20 (2.15–2.43) | 2.00 |
| proto-scalar | generated-owned | peak | 1.73 (1.49–1.77) | 1.00 |
| proto-scalar | generated-owned | serialize | 27.91 (26.85–31.99) | 24.00 |
| proto-scalar | generated-owned | project-aot | 11.48 (10.77–13.05) | 0.00 |
| proto-scalar | generated-owned | project-interpret | 10.64 (10.32–13.42) | 2.00 |
| proto-scalar | generated-owned | project-peak | 12.97 (11.50–16.15) | 1.00 |
| proto-bytes | stock | ingest | 27512.00 (25230.85–30669.03) | 65699.00 |
| proto-bytes | stock | read-aot | 3.95 (3.78–4.51) | 0.00 |
| proto-bytes | stock | interpret | 5.61 (4.81–5.68) | 2.00 |
| proto-bytes | stock | peak | 3.67 (2.97–4.58) | 1.00 |
| proto-bytes | stock | serialize | 7529.19 (5744.77–7788.34) | 65599.00 |
| proto-bytes | stock-aliased | ingest | 83.03 (75.48–136.98) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 3.82 (3.66–4.48) | 0.00 |
| proto-bytes | stock-aliased | interpret | 5.28 (5.23–6.26) | 2.00 |
| proto-bytes | stock-aliased | peak | 3.82 (3.56–5.53) | 1.00 |
| proto-bytes | stock-aliased | serialize | 6569.24 (5624.59–29947.00) | 65599.00 |
| proto-bytes | owned | ingest | 9835.95 (7488.41–10062.12) | 66035.00 |
| proto-bytes | owned | interpret | 24.40 (18.86–26.74) | 2.00 |
| proto-bytes | owned | peak | 6.20 (4.43–6.54) | 1.00 |
| proto-bytes | owned | ingest-generated | 12771.84 (5640.55–23518.16) | 66035.00 |
| proto-bytes | owned | read-generated-aot | 22.34 (15.53–23.42) | 0.00 |
| proto-bytes | owned | interpret-generated | 20.48 (19.31–25.80) | 2.00 |
| proto-bytes | owned | peak-generated | 5.86 (5.73–6.57) | 1.00 |
| proto-bytes | owned | serialize | 5744.99 (5178.66–55882.01) | 65615.00 |
| proto-bytes | buffa | ingest | 316.28 (244.98–411.11) | 272.00 |
| proto-bytes | buffa | interpret | 21.49 (19.66–24.28) | 2.00 |
| proto-bytes | buffa | peak | 4.88 (4.84–6.15) | 1.00 |
| proto-bytes | buffa | ingest-generated | 274.33 (237.18–289.45) | 272.00 |
| proto-bytes | buffa | read-generated-aot | 12.92 (11.12–14.24) | 0.00 |
| proto-bytes | buffa | interpret-generated | 14.18 (13.51–16.89) | 2.00 |
| proto-bytes | buffa | peak-generated | 6.11 (5.13–8.04) | 1.00 |
| proto-bytes | buffa | serialize | 39790.76 (6976.48–47546.43) | 65567.00 |
| proto-bytes | generated-owned | ingest | 6345.32 (6232.25–41314.88) | 65651.00 |
| proto-bytes | generated-owned | read-aot | 1.43 (1.28–2.18) | 0.00 |
| proto-bytes | generated-owned | interpret | 1.89 (1.67–2.12) | 2.00 |
| proto-bytes | generated-owned | peak | 2.80 (2.36–3.28) | 1.00 |
| proto-bytes | generated-owned | serialize | 6434.31 (6262.60–6446.19) | 65583.00 |
| timestamp-json | stock | ingest | 4517.54 (4194.48–4565.89) | 6439.75 |
| timestamp-json | owned | ingest | 2089.20 (1940.36–2108.34) | 3703.75 |
| timestamp-json | owned | ingest-typed | 2162.26 (1482.94–2172.46) | 3671.75 |
| timestamp-json | owned | ingest-generated | 1689.13 (1493.32–2065.92) | 3671.75 |
| timestamp-json | buffa | ingest | 779.49 (588.52–1025.11) | 688.00 |
| timestamp-json | buffa | ingest-typed | 651.22 (622.43–888.57) | 688.00 |
| timestamp-json | buffa | ingest-generated | 619.31 (493.10–847.43) | 688.00 |
| mysql-int4 | stock | ingest | 67.14 (47.83–70.50) | 16.00 |
| mysql-int4 | stock | read-aot | 2.07 (1.93–2.40) | 0.00 |
| mysql-int4 | stock | interpret | 3.23 (3.17–7.62) | 2.00 |
| mysql-int4 | stock | peak | 1.37 (1.20–1.48) | 1.00 |
| mysql-int4 | static | ingest | 179.12 (143.82–196.25) | 128.00 |
| mysql-int4 | static | ingest-typed | 193.96 (102.46–207.50) | 128.00 |
| mysql-int4 | static | interpret | 16.44 (16.34–20.59) | 2.00 |
| mysql-int4 | static | peak | 3.23 (2.81–4.79) | 1.00 |
| postgres-int4 | stock | ingest | 34.61 (25.11–40.78) | 16.00 |
| postgres-int4 | stock | read-aot | 2.02 (1.94–2.19) | 0.00 |
| postgres-int4 | stock | interpret | 2.48 (2.27–2.81) | 2.00 |
| postgres-int4 | stock | peak | 1.28 (1.15–1.71) | 1.00 |
| postgres-int4 | static | ingest | 163.97 (162.96–165.12) | 128.00 |
| postgres-int4 | static | ingest-typed | 127.24 (94.48–172.31) | 128.00 |
| postgres-int4 | static | interpret | 20.18 (19.94–24.78) | 2.00 |
| postgres-int4 | static | peak | 2.99 (2.90–3.38) | 1.00 |
| proto-mixed | stock | ingest | 1166.93 (1142.38–1423.86) | 1776.75 |
| proto-mixed | stock | read-aot | 9.94 (9.32–10.08) | 0.00 |
| proto-mixed | stock | interpret-boundary | 10.99 (7.77–11.28) | 2.00 |
| proto-mixed | stock | peak-boundary | 10.04 (9.85–13.83) | 1.00 |
| proto-mixed | stock | serialize | 665.04 (620.78–1022.78) | 436.50 |
| proto-mixed | owned | ingest | 12673.24 (9396.65–14066.83) | 3339.50 |
| proto-mixed | owned | ingest-typed | 14863.79 (10961.73–15584.64) | 3339.50 |
| proto-mixed | owned | interpret | 63.79 (57.41–76.49) | 2.00 |
| proto-mixed | owned | peak | 16.07 (13.97–19.54) | 1.00 |
| proto-mixed | owned | ingest-generated | 15641.88 (10421.48–17601.43) | 3339.50 |
| proto-mixed | owned | read-generated-aot | 67.94 (56.93–72.52) | 0.00 |
| proto-mixed | owned | interpret-generated | 58.92 (51.97–81.28) | 2.00 |
| proto-mixed | owned | peak-generated | 10.96 (10.00–13.49) | 1.00 |
| proto-mixed | owned | serialize | 4778.20 (3862.19–5319.11) | 556.00 |
| proto-mixed | buffa | ingest | 5922.07 (5638.31–8590.12) | 1727.75 |
| proto-mixed | buffa | ingest-typed | 5637.30 (4529.97–7242.99) | 1727.75 |
| proto-mixed | buffa | interpret | 77.47 (75.82–79.19) | 2.00 |
| proto-mixed | buffa | peak | 16.08 (8.96–16.73) | 1.00 |
| proto-mixed | buffa | ingest-generated | 5860.00 (4874.72–9449.84) | 1727.75 |
| proto-mixed | buffa | read-generated-aot | 29.42 (26.46–33.01) | 0.00 |
| proto-mixed | buffa | interpret-generated | 37.03 (32.86–38.60) | 2.00 |
| proto-mixed | buffa | peak-generated | 12.20 (10.47–13.83) | 1.00 |
| proto-mixed | buffa | serialize | 38.08 (36.93–42.65) | 176.50 |
| proto-mixed | generated-owned | ingest | 1246.03 (1083.93–1474.29) | 1800.50 |
| proto-mixed | generated-owned | read-aot | 7.30 (6.08–8.06) | 0.00 |
| proto-mixed | generated-owned | interpret | 4.97 (4.51–5.70) | 2.00 |
| proto-mixed | generated-owned | peak | 4.86 (4.77–5.29) | 1.00 |
| proto-mixed | generated-owned | serialize | 1448.27 (1324.34–1697.48) | 192.50 |
| proto-mixed-full | stock | ingest | 1151.65 (1125.83–1416.41) | 1776.75 |
| proto-mixed-full | owned | ingest | 16075.29 (13560.41–18354.80) | 3339.50 |
| proto-mixed-full | buffa | ingest | 6986.85 (5019.58–7519.30) | 1854.25 |
| proto-mixed-full | generated-owned | ingest | 971.29 (887.81–1239.52) | 1800.50 |

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

Binary SHA-256: `a8b5d141bb652724061c926b58616124785c4a98671a61752076eff1df519c50`
Revision: `2fae8de8cbd2f17456a11dacbbec099624b48e68`
