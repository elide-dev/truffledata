# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 198.38 (187.17–1217.15) | 136.00 |
| proto-scalar | stock | read-aot | 2.53 (2.48–2.67) | 0.00 |
| proto-scalar | stock | interpret | 3.97 (3.63–3.99) | 2.00 |
| proto-scalar | stock | peak | 2.24 (1.99–3.09) | 1.00 |
| proto-scalar | stock | serialize | 42.53 (38.68–1145.46) | 56.00 |
| proto-scalar | stock | project-aot | 118.05 (104.43–130.96) | 136.00 |
| proto-scalar | stock | project-interpret | 101.84 (91.26–124.52) | 138.00 |
| proto-scalar | stock | project-peak | 117.58 (87.78–132.30) | 137.00 |
| proto-scalar | owned | ingest | 206.30 (143.97–460.12) | 64.00 |
| proto-scalar | owned | ingest-typed | 265.10 (256.44–407.66) | 48.00 |
| proto-scalar | owned | interpret | 44.38 (42.47–46.16) | 2.00 |
| proto-scalar | owned | peak | 4.70 (4.39–5.75) | 1.00 |
| proto-scalar | owned | ingest-generated | 69.71 (56.54–101.84) | 48.00 |
| proto-scalar | owned | read-generated-aot | 16.87 (16.64–17.07) | 0.00 |
| proto-scalar | owned | interpret-generated | 20.46 (19.13–27.27) | 2.00 |
| proto-scalar | owned | peak-generated | 4.88 (4.11–5.00) | 1.00 |
| proto-scalar | owned | serialize | 35.88 (29.50–36.21) | 24.00 |
| proto-scalar | buffa | ingest | 329.10 (274.43–334.44) | 112.00 |
| proto-scalar | buffa | ingest-typed | 265.40 (234.20–286.00) | 80.00 |
| proto-scalar | buffa | interpret | 46.45 (41.67–64.12) | 2.00 |
| proto-scalar | buffa | peak | 4.42 (4.42–4.67) | 1.00 |
| proto-scalar | buffa | ingest-generated | 195.71 (165.30–356.93) | 80.00 |
| proto-scalar | buffa | read-generated-aot | 24.05 (19.70–35.72) | 0.00 |
| proto-scalar | buffa | interpret-generated | 26.04 (25.81–29.72) | 2.00 |
| proto-scalar | buffa | peak-generated | 5.14 (4.76–6.70) | 1.00 |
| proto-scalar | buffa | serialize | 36.49 (34.79–39.42) | 24.00 |
| proto-scalar | generated-owned | ingest | 49.45 (47.26–64.84) | 88.00 |
| proto-scalar | generated-owned | read-aot | 2.97 (2.11–3.05) | 0.00 |
| proto-scalar | generated-owned | interpret | 2.79 (2.51–3.05) | 2.00 |
| proto-scalar | generated-owned | peak | 8.86 (3.06–8.95) | 1.00 |
| proto-scalar | generated-owned | serialize | 44.07 (36.67–163.68) | 24.00 |
| proto-scalar | generated-owned | project-aot | 19.92 (16.63–22.42) | 0.00 |
| proto-scalar | generated-owned | project-interpret | 21.37 (15.98–30.40) | 2.00 |
| proto-scalar | generated-owned | project-peak | 19.16 (17.57–20.05) | 1.00 |
| proto-bytes | stock | ingest | 49931.65 (29797.93–51469.03) | 65699.00 |
| proto-bytes | stock | read-aot | 5.22 (4.98–8.30) | 0.00 |
| proto-bytes | stock | interpret | 9.41 (9.25–19.80) | 2.00 |
| proto-bytes | stock | peak | 5.88 (5.65–6.85) | 1.00 |
| proto-bytes | stock | serialize | 20414.57 (9965.91–42155.08) | 65599.00 |
| proto-bytes | stock-aliased | ingest | 139.17 (127.09–160.23) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 5.33 (4.87–6.50) | 0.00 |
| proto-bytes | stock-aliased | interpret | 7.02 (6.47–9.18) | 2.00 |
| proto-bytes | stock-aliased | peak | 5.61 (5.19–10.74) | 1.00 |
| proto-bytes | stock-aliased | serialize | 11134.58 (10178.43–12209.13) | 65599.00 |
| proto-bytes | owned | ingest | 11692.51 (10910.96–27712.01) | 65995.00 |
| proto-bytes | owned | interpret | 38.16 (31.78–38.76) | 2.00 |
| proto-bytes | owned | peak | 8.28 (7.81–9.93) | 1.00 |
| proto-bytes | owned | ingest-generated | 12268.50 (11186.71–99238.94) | 65995.00 |
| proto-bytes | owned | read-generated-aot | 26.72 (25.39–33.74) | 0.00 |
| proto-bytes | owned | interpret-generated | 34.54 (28.14–37.69) | 2.00 |
| proto-bytes | owned | peak-generated | 9.56 (7.13–11.75) | 1.00 |
| proto-bytes | owned | serialize | 13062.39 (10451.33–40570.71) | 65615.00 |
| proto-bytes | buffa | ingest | 467.60 (395.01–482.75) | 272.00 |
| proto-bytes | buffa | interpret | 42.84 (33.70–55.56) | 2.00 |
| proto-bytes | buffa | peak | 8.33 (8.24–9.96) | 1.00 |
| proto-bytes | buffa | ingest-generated | 374.56 (333.14–420.13) | 272.00 |
| proto-bytes | buffa | read-generated-aot | 24.62 (19.28–28.57) | 0.00 |
| proto-bytes | buffa | interpret-generated | 27.02 (25.18–29.73) | 2.00 |
| proto-bytes | buffa | peak-generated | 12.05 (11.25–33.18) | 1.00 |
| proto-bytes | buffa | serialize | 41438.44 (11862.19–48504.52) | 65567.00 |
| proto-bytes | generated-owned | ingest | 51036.06 (18638.33–120327.15) | 65651.00 |
| proto-bytes | generated-owned | read-aot | 2.86 (2.76–3.61) | 0.00 |
| proto-bytes | generated-owned | interpret | 3.54 (3.44–4.27) | 2.00 |
| proto-bytes | generated-owned | peak | 3.60 (3.21–5.17) | 1.00 |
| proto-bytes | generated-owned | serialize | 9693.90 (8931.84–11444.64) | 65583.00 |
| timestamp-json | stock | ingest | 6247.03 (5508.11–6799.45) | 6439.75 |
| timestamp-json | owned | ingest | 3969.76 (2094.20–4905.40) | 3655.75 |
| timestamp-json | owned | ingest-typed | 2176.65 (2089.38–2277.93) | 3639.75 |
| timestamp-json | owned | ingest-generated | 3352.02 (2617.93–3574.33) | 3639.75 |
| timestamp-json | buffa | ingest | 1263.76 (1156.39–2053.07) | 688.00 |
| timestamp-json | buffa | ingest-typed | 1386.22 (1098.35–1617.65) | 688.00 |
| timestamp-json | buffa | ingest-generated | 999.99 (986.68–1200.65) | 688.00 |
| mysql-int4 | stock | ingest | 77.58 (75.56–120.28) | 16.00 |
| mysql-int4 | stock | read-aot | 2.22 (2.17–2.28) | 0.00 |
| mysql-int4 | stock | interpret | 6.91 (5.87–19.71) | 2.00 |
| mysql-int4 | stock | peak | 2.12 (2.08–2.56) | 1.00 |
| mysql-int4 | static | ingest | 175.35 (147.30–612.62) | 80.00 |
| mysql-int4 | static | ingest-typed | 322.98 (254.11–417.10) | 80.00 |
| mysql-int4 | static | interpret | 26.13 (25.94–34.45) | 2.00 |
| mysql-int4 | static | peak | 4.74 (4.17–4.84) | 1.00 |
| postgres-int4 | stock | ingest | 59.41 (47.46–64.21) | 16.00 |
| postgres-int4 | stock | read-aot | 2.58 (2.30–2.84) | 0.00 |
| postgres-int4 | stock | interpret | 3.74 (3.62–4.99) | 2.00 |
| postgres-int4 | stock | peak | 2.95 (2.50–3.03) | 1.00 |
| postgres-int4 | static | ingest | 155.05 (153.25–210.62) | 80.00 |
| postgres-int4 | static | ingest-typed | 203.41 (186.41–226.94) | 80.00 |
| postgres-int4 | static | interpret | 25.69 (24.02–27.97) | 2.00 |
| postgres-int4 | static | peak | 6.91 (6.26–7.16) | 1.00 |
| proto-mixed | stock | ingest | 1814.83 (1724.94–2016.26) | 1776.75 |
| proto-mixed | stock | read-aot | 13.49 (13.18–17.99) | 0.00 |
| proto-mixed | stock | interpret-boundary | 15.72 (14.85–19.57) | 2.00 |
| proto-mixed | stock | peak-boundary | 14.74 (13.78–15.04) | 1.00 |
| proto-mixed | stock | serialize | 1087.58 (1006.31–1839.08) | 436.50 |
| proto-mixed | owned | ingest | 13958.13 (8319.12–14610.60) | 2976.00 |
| proto-mixed | owned | ingest-typed | 11183.95 (9902.82–11606.64) | 2976.00 |
| proto-mixed | owned | interpret | 120.46 (118.70–157.13) | 2.00 |
| proto-mixed | owned | peak | 34.62 (26.12–38.89) | 1.00 |
| proto-mixed | owned | ingest-generated | 9476.30 (7722.36–10428.32) | 2976.00 |
| proto-mixed | owned | read-generated-aot | 83.09 (81.68–85.24) | 0.00 |
| proto-mixed | owned | interpret-generated | 93.39 (88.67–102.70) | 2.00 |
| proto-mixed | owned | peak-generated | 23.94 (18.34–25.65) | 1.00 |
| proto-mixed | owned | serialize | 9571.41 (6428.11–9842.09) | 556.00 |
| proto-mixed | buffa | ingest | 8395.91 (7158.49–8563.56) | 1727.75 |
| proto-mixed | buffa | ingest-typed | 8374.30 (7215.63–10007.12) | 1727.75 |
| proto-mixed | buffa | interpret | 109.25 (99.05–162.92) | 2.00 |
| proto-mixed | buffa | peak | 23.68 (23.49–24.76) | 1.00 |
| proto-mixed | buffa | ingest-generated | 7537.58 (6783.97–10199.19) | 1727.75 |
| proto-mixed | buffa | read-generated-aot | 53.87 (43.14–61.15) | 0.00 |
| proto-mixed | buffa | interpret-generated | 52.98 (49.85–60.81) | 2.00 |
| proto-mixed | buffa | peak-generated | 20.14 (17.78–31.39) | 1.00 |
| proto-mixed | buffa | serialize | 68.24 (49.11–83.99) | 176.50 |
| proto-mixed | generated-owned | ingest | 1786.95 (1508.53–2106.54) | 1800.50 |
| proto-mixed | generated-owned | read-aot | 14.81 (9.68–22.57) | 0.00 |
| proto-mixed | generated-owned | interpret | 11.27 (8.70–11.89) | 2.00 |
| proto-mixed | generated-owned | peak | 9.87 (7.57–11.28) | 1.00 |
| proto-mixed | generated-owned | serialize | 1635.86 (1427.44–2038.88) | 192.50 |
| proto-mixed-full | stock | ingest | 1812.03 (1530.00–2027.88) | 1776.75 |
| proto-mixed-full | owned | ingest | 12989.43 (12444.94–18114.29) | 2976.00 |
| proto-mixed-full | buffa | ingest | 10780.53 (8589.96–10933.86) | 1854.25 |
| proto-mixed-full | generated-owned | ingest | 1817.87 (1808.93–2446.63) | 1800.50 |

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
