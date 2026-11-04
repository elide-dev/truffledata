# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 152.26 (151.15–276.70) | 136.00 |
| proto-scalar | stock | read-aot | 2.47 (2.45–4.54) | 0.00 |
| proto-scalar | stock | interpret | 4.78 (3.45–6.87) | 2.00 |
| proto-scalar | stock | peak | 1.85 (1.85–2.83) | 1.00 |
| proto-scalar | stock | serialize | 38.49 (37.95–39.91) | 56.00 |
| proto-scalar | stock | project-aot | 168.88 (93.55–196.52) | 136.00 |
| proto-scalar | stock | project-interpret | 95.13 (94.55–100.89) | 138.00 |
| proto-scalar | stock | project-peak | 115.11 (100.58–195.27) | 137.00 |
| proto-scalar | owned | ingest | 179.14 (171.54–214.66) | 64.00 |
| proto-scalar | owned | ingest-typed | 331.63 (239.72–333.39) | 48.00 |
| proto-scalar | owned | interpret | 52.89 (39.46–75.61) | 2.00 |
| proto-scalar | owned | peak | 6.10 (3.83–6.34) | 1.00 |
| proto-scalar | owned | ingest-generated | 62.52 (61.77–133.34) | 48.00 |
| proto-scalar | owned | read-generated-aot | 14.22 (14.04–26.53) | 0.00 |
| proto-scalar | owned | interpret-generated | 23.26 (20.74–30.20) | 2.00 |
| proto-scalar | owned | peak-generated | 4.37 (2.98–5.26) | 1.00 |
| proto-scalar | owned | serialize | 37.60 (32.11–50.31) | 24.00 |
| proto-scalar | buffa | ingest | 276.11 (275.27–534.98) | 112.00 |
| proto-scalar | buffa | ingest-typed | 492.86 (297.92–497.10) | 80.00 |
| proto-scalar | buffa | interpret | 48.05 (33.67–57.64) | 2.00 |
| proto-scalar | buffa | peak | 5.03 (4.37–7.58) | 1.00 |
| proto-scalar | buffa | ingest-generated | 176.16 (162.16–191.16) | 80.00 |
| proto-scalar | buffa | read-generated-aot | 21.80 (18.03–63.48) | 0.00 |
| proto-scalar | buffa | interpret-generated | 21.30 (21.11–27.16) | 2.00 |
| proto-scalar | buffa | peak-generated | 6.89 (5.44–7.44) | 1.00 |
| proto-scalar | buffa | serialize | 46.16 (34.96–49.79) | 24.00 |
| proto-scalar | generated-owned | ingest | 82.50 (50.95–87.84) | 88.00 |
| proto-scalar | generated-owned | read-aot | 1.95 (1.92–2.52) | 0.00 |
| proto-scalar | generated-owned | interpret | 2.36 (2.30–4.05) | 2.00 |
| proto-scalar | generated-owned | peak | 3.48 (1.97–3.64) | 1.00 |
| proto-scalar | generated-owned | serialize | 46.68 (41.25–77.37) | 24.00 |
| proto-scalar | generated-owned | project-aot | 15.73 (15.70–16.85) | 0.00 |
| proto-scalar | generated-owned | project-interpret | 19.84 (19.11–31.83) | 2.00 |
| proto-scalar | generated-owned | project-peak | 24.86 (18.11–29.46) | 1.00 |
| proto-bytes | stock | ingest | 36634.90 (34990.37–36965.34) | 65699.00 |
| proto-bytes | stock | read-aot | 5.56 (5.27–5.58) | 0.00 |
| proto-bytes | stock | interpret | 7.63 (7.54–8.05) | 2.00 |
| proto-bytes | stock | peak | 10.50 (5.87–10.85) | 1.00 |
| proto-bytes | stock | serialize | 19017.16 (17183.08–19649.98) | 65599.00 |
| proto-bytes | stock-aliased | ingest | 125.02 (122.77–236.21) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 7.30 (4.40–7.76) | 0.00 |
| proto-bytes | stock-aliased | interpret | 7.15 (6.67–9.35) | 2.00 |
| proto-bytes | stock-aliased | peak | 8.07 (5.58–8.71) | 1.00 |
| proto-bytes | stock-aliased | serialize | 16559.77 (16486.41–18225.06) | 65599.00 |
| proto-bytes | owned | ingest | 18714.76 (17823.23–20938.89) | 65995.00 |
| proto-bytes | owned | interpret | 33.84 (31.24–47.95) | 2.00 |
| proto-bytes | owned | peak | 7.96 (7.89–7.99) | 1.00 |
| proto-bytes | owned | ingest-generated | 20893.65 (18444.48–22407.60) | 65995.00 |
| proto-bytes | owned | read-generated-aot | 30.40 (23.77–35.77) | 0.00 |
| proto-bytes | owned | interpret-generated | 28.78 (27.23–49.48) | 2.00 |
| proto-bytes | owned | peak-generated | 7.65 (7.57–11.89) | 1.00 |
| proto-bytes | owned | serialize | 16922.57 (16755.35–20317.45) | 65615.00 |
| proto-bytes | buffa | ingest | 563.67 (524.84–1032.13) | 272.00 |
| proto-bytes | buffa | interpret | 67.29 (43.92–78.06) | 2.00 |
| proto-bytes | buffa | peak | 7.48 (6.97–8.24) | 1.00 |
| proto-bytes | buffa | ingest-generated | 705.37 (416.33–735.06) | 272.00 |
| proto-bytes | buffa | read-generated-aot | 15.19 (14.24–17.69) | 0.00 |
| proto-bytes | buffa | interpret-generated | 30.40 (17.28–34.18) | 2.00 |
| proto-bytes | buffa | peak-generated | 7.72 (7.61–8.50) | 1.00 |
| proto-bytes | buffa | serialize | 18591.60 (16790.97–20738.85) | 65567.00 |
| proto-bytes | generated-owned | ingest | 21073.87 (19984.23–26257.98) | 65651.00 |
| proto-bytes | generated-owned | read-aot | 3.06 (3.03–4.66) | 0.00 |
| proto-bytes | generated-owned | interpret | 6.96 (3.53–7.23) | 2.00 |
| proto-bytes | generated-owned | peak | 3.61 (3.48–6.16) | 1.00 |
| proto-bytes | generated-owned | serialize | 18063.29 (16748.69–18408.99) | 65583.00 |
| timestamp-json | stock | ingest | 10264.59 (6987.21–11647.19) | 6439.75 |
| timestamp-json | owned | ingest | 4666.40 (3298.43–5264.09) | 3655.75 |
| timestamp-json | owned | ingest-typed | 4029.54 (3874.10–4751.63) | 3639.75 |
| timestamp-json | owned | ingest-generated | 2931.93 (2814.18–4467.74) | 3639.75 |
| timestamp-json | buffa | ingest | 1182.60 (1133.59–1194.51) | 688.00 |
| timestamp-json | buffa | ingest-typed | 1646.14 (1121.47–1944.13) | 688.00 |
| timestamp-json | buffa | ingest-generated | 1095.87 (964.59–2092.78) | 688.00 |
| mysql-int4 | stock | ingest | 129.13 (73.38–132.94) | 16.00 |
| mysql-int4 | stock | read-aot | 3.05 (2.46–3.28) | 0.00 |
| mysql-int4 | stock | interpret | 3.86 (3.78–7.59) | 2.00 |
| mysql-int4 | stock | peak | 1.71 (1.67–2.17) | 1.00 |
| mysql-int4 | static | ingest | 284.40 (204.90–297.94) | 80.00 |
| mysql-int4 | static | ingest-typed | 301.23 (214.13–361.04) | 80.00 |
| mysql-int4 | static | interpret | 30.63 (25.23–39.43) | 2.00 |
| mysql-int4 | static | peak | 3.93 (3.91–6.60) | 1.00 |
| postgres-int4 | stock | ingest | 74.41 (63.08–84.75) | 16.00 |
| postgres-int4 | stock | read-aot | 2.40 (2.26–3.96) | 0.00 |
| postgres-int4 | stock | interpret | 8.68 (4.04–8.79) | 2.00 |
| postgres-int4 | stock | peak | 1.68 (1.68–1.69) | 1.00 |
| postgres-int4 | static | ingest | 242.05 (178.09–370.90) | 80.00 |
| postgres-int4 | static | ingest-typed | 196.41 (183.30–302.72) | 80.00 |
| postgres-int4 | static | interpret | 35.18 (23.75–42.86) | 2.00 |
| postgres-int4 | static | peak | 4.24 (3.93–5.36) | 1.00 |
| proto-mixed | stock | ingest | 2008.56 (1975.72–3652.14) | 1776.75 |
| proto-mixed | stock | read-aot | 18.43 (11.66–19.42) | 0.00 |
| proto-mixed | stock | interpret-boundary | 22.11 (12.42–22.55) | 2.00 |
| proto-mixed | stock | peak-boundary | 11.25 (11.18–20.89) | 1.00 |
| proto-mixed | stock | serialize | 1189.31 (1176.87–1564.21) | 436.50 |
| proto-mixed | owned | ingest | 12931.27 (10916.36–17099.96) | 2976.00 |
| proto-mixed | owned | ingest-typed | 11136.27 (10386.35–15202.65) | 2976.00 |
| proto-mixed | owned | interpret | 126.52 (116.98–127.37) | 2.00 |
| proto-mixed | owned | peak | 20.28 (15.31–22.75) | 1.00 |
| proto-mixed | owned | ingest-generated | 10696.96 (9627.44–17995.98) | 2976.00 |
| proto-mixed | owned | read-generated-aot | 89.89 (75.90–171.48) | 0.00 |
| proto-mixed | owned | interpret-generated | 83.26 (80.36–84.30) | 2.00 |
| proto-mixed | owned | peak-generated | 22.77 (13.63–23.77) | 1.00 |
| proto-mixed | owned | serialize | 11580.28 (6700.50–13372.10) | 556.00 |
| proto-mixed | buffa | ingest | 9065.87 (9014.07–15904.00) | 1727.75 |
| proto-mixed | buffa | ingest-typed | 9626.08 (9374.44–12657.81) | 1727.75 |
| proto-mixed | buffa | interpret | 176.43 (137.54–186.09) | 2.00 |
| proto-mixed | buffa | peak | 20.47 (20.38–30.71) | 1.00 |
| proto-mixed | buffa | ingest-generated | 10988.31 (8379.59–14327.20) | 1727.75 |
| proto-mixed | buffa | read-generated-aot | 56.28 (40.44–83.83) | 0.00 |
| proto-mixed | buffa | interpret-generated | 44.73 (44.20–52.01) | 2.00 |
| proto-mixed | buffa | peak-generated | 17.87 (17.60–30.07) | 1.00 |
| proto-mixed | buffa | serialize | 68.31 (62.76–80.03) | 176.50 |
| proto-mixed | generated-owned | ingest | 2749.02 (1990.96–3505.41) | 1800.50 |
| proto-mixed | generated-owned | read-aot | 10.26 (10.15–10.35) | 0.00 |
| proto-mixed | generated-owned | interpret | 6.96 (6.77–13.74) | 2.00 |
| proto-mixed | generated-owned | peak | 6.66 (6.51–10.89) | 1.00 |
| proto-mixed | generated-owned | serialize | 1545.24 (1535.64–1592.97) | 192.50 |
| proto-mixed-full | stock | ingest | 2121.08 (2082.10–3552.45) | 1776.75 |
| proto-mixed-full | owned | ingest | 14540.72 (14413.93–14561.55) | 2976.00 |
| proto-mixed-full | buffa | ingest | 12544.27 (11531.11–21032.24) | 1854.25 |
| proto-mixed-full | generated-owned | ingest | 3399.59 (1959.31–3436.61) | 1800.50 |

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

Binary SHA-256: `60940f0c35ff0409fe06124239e0f9ac78580df4d902a6181ccda79f879910de`
Revision: `7fe13fc422b4efeabb1dd55dc7147e9ebe216bb9`
