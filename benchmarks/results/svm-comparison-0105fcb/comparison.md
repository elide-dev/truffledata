# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 114.98 (114.71–209.31) | 136.00 |
| proto-scalar | stock | read-aot | 2.07 (2.03–3.09) | 0.00 |
| proto-scalar | stock | interpret | 4.56 (3.20–5.14) | 2.00 |
| proto-scalar | stock | peak | 2.91 (1.55–3.50) | 1.00 |
| proto-scalar | stock | serialize | 29.75 (29.48–39.93) | 56.00 |
| proto-scalar | stock | project-aot | 102.10 (72.11–111.03) | 136.00 |
| proto-scalar | stock | project-interpret | 108.41 (93.15–112.21) | 138.00 |
| proto-scalar | stock | project-peak | 99.60 (99.45–103.36) | 137.00 |
| proto-scalar | owned | ingest | 224.65 (127.25–253.73) | 88.00 |
| proto-scalar | owned | ingest-typed | 249.87 (121.19–259.18) | 56.00 |
| proto-scalar | owned | interpret | 49.86 (35.09–56.12) | 2.00 |
| proto-scalar | owned | peak | 6.33 (5.24–6.39) | 1.00 |
| proto-scalar | owned | ingest-generated | 67.75 (61.21–84.67) | 56.00 |
| proto-scalar | owned | read-generated-aot | 16.26 (16.18–16.27) | 0.00 |
| proto-scalar | owned | interpret-generated | 26.80 (20.36–28.43) | 2.00 |
| proto-scalar | owned | peak-generated | 4.04 (3.12–5.23) | 1.00 |
| proto-scalar | owned | serialize | 269.70 (255.49–271.68) | 136.00 |
| proto-scalar | buffa | ingest | 208.58 (200.54–270.28) | 112.00 |
| proto-scalar | buffa | ingest-typed | 208.56 (191.47–405.28) | 80.00 |
| proto-scalar | buffa | interpret | 37.75 (28.28–41.96) | 2.00 |
| proto-scalar | buffa | peak | 5.20 (5.07–6.00) | 1.00 |
| proto-scalar | buffa | ingest-generated | 108.28 (107.41–215.02) | 80.00 |
| proto-scalar | buffa | read-generated-aot | 26.04 (15.87–27.83) | 0.00 |
| proto-scalar | buffa | interpret-generated | 25.78 (21.64–30.91) | 2.00 |
| proto-scalar | buffa | peak-generated | 4.77 (4.06–7.72) | 1.00 |
| proto-scalar | buffa | serialize | 25.66 (25.02–42.25) | 24.00 |
| proto-scalar | generated-owned | ingest | 39.43 (38.49–69.06) | 88.00 |
| proto-scalar | generated-owned | read-aot | 2.11 (1.61–2.86) | 0.00 |
| proto-scalar | generated-owned | interpret | 2.77 (1.92–3.24) | 2.00 |
| proto-scalar | generated-owned | peak | 3.35 (1.94–3.69) | 1.00 |
| proto-scalar | generated-owned | serialize | 55.09 (51.81–55.92) | 24.00 |
| proto-scalar | generated-owned | project-aot | 13.80 (13.69–24.93) | 0.00 |
| proto-scalar | generated-owned | project-interpret | 15.47 (14.89–29.82) | 2.00 |
| proto-scalar | generated-owned | project-peak | 22.20 (20.69–28.90) | 1.00 |
| proto-bytes | stock | ingest | 51095.76 (34941.17–52362.67) | 65699.00 |
| proto-bytes | stock | read-aot | 5.26 (4.03–6.64) | 0.00 |
| proto-bytes | stock | interpret | 8.79 (5.49–11.43) | 2.00 |
| proto-bytes | stock | peak | 8.34 (6.46–9.05) | 1.00 |
| proto-bytes | stock | serialize | 12623.75 (12302.97–14611.77) | 65599.00 |
| proto-bytes | stock-aliased | ingest | 140.44 (80.75–164.93) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 3.28 (3.27–5.03) | 0.00 |
| proto-bytes | stock-aliased | interpret | 8.26 (6.51–8.35) | 2.00 |
| proto-bytes | stock-aliased | peak | 4.18 (4.17–7.11) | 1.00 |
| proto-bytes | stock-aliased | serialize | 11134.23 (10807.31–12000.27) | 65599.00 |
| proto-bytes | owned | ingest | 13326.45 (12348.10–14540.93) | 66019.00 |
| proto-bytes | owned | interpret | 27.55 (27.27–37.73) | 2.00 |
| proto-bytes | owned | peak | 9.34 (6.74–9.94) | 1.00 |
| proto-bytes | owned | ingest-generated | 11950.69 (10592.17–14247.97) | 66019.00 |
| proto-bytes | owned | read-generated-aot | 22.20 (21.57–32.33) | 0.00 |
| proto-bytes | owned | interpret-generated | 26.01 (23.99–29.75) | 2.00 |
| proto-bytes | owned | peak-generated | 10.82 (9.64–11.42) | 1.00 |
| proto-bytes | owned | serialize | 12245.68 (10547.61–21275.63) | 65615.00 |
| proto-bytes | buffa | ingest | 707.96 (358.96–757.77) | 272.00 |
| proto-bytes | buffa | interpret | 41.84 (27.92–50.25) | 2.00 |
| proto-bytes | buffa | peak | 10.29 (5.82–10.45) | 1.00 |
| proto-bytes | buffa | ingest-generated | 291.72 (270.33–536.09) | 272.00 |
| proto-bytes | buffa | read-generated-aot | 15.82 (12.68–18.29) | 0.00 |
| proto-bytes | buffa | interpret-generated | 22.49 (14.10–26.19) | 2.00 |
| proto-bytes | buffa | peak-generated | 8.56 (6.33–9.88) | 1.00 |
| proto-bytes | buffa | serialize | 14405.78 (13813.85–17783.97) | 65567.00 |
| proto-bytes | generated-owned | ingest | 12526.07 (11287.27–22640.27) | 65651.00 |
| proto-bytes | generated-owned | read-aot | 3.32 (2.41–3.92) | 0.00 |
| proto-bytes | generated-owned | interpret | 2.97 (2.92–4.91) | 2.00 |
| proto-bytes | generated-owned | peak | 3.23 (2.99–5.38) | 1.00 |
| proto-bytes | generated-owned | serialize | 12384.79 (11005.92–12674.47) | 65583.00 |
| timestamp-json | stock | ingest | 5464.37 (5431.87–8780.78) | 6439.75 |
| timestamp-json | owned | ingest | 4254.18 (2659.63–4268.18) | 3703.75 |
| timestamp-json | owned | ingest-typed | 4021.82 (3390.12–4725.76) | 3671.75 |
| timestamp-json | owned | ingest-generated | 2226.57 (2209.49–2229.51) | 3671.75 |
| timestamp-json | buffa | ingest | 1591.76 (1382.00–1603.32) | 688.00 |
| timestamp-json | buffa | ingest-typed | 1580.79 (807.55–1622.52) | 688.00 |
| timestamp-json | buffa | ingest-generated | 697.83 (695.10–1138.75) | 688.00 |
| mysql-int4 | stock | ingest | 101.34 (60.62–102.40) | 16.00 |
| mysql-int4 | stock | read-aot | 1.84 (1.84–2.78) | 0.00 |
| mysql-int4 | stock | interpret | 3.17 (3.12–4.07) | 2.00 |
| mysql-int4 | stock | peak | 2.36 (1.47–2.53) | 1.00 |
| mysql-int4 | static | ingest | 352.78 (187.33–389.25) | 128.00 |
| mysql-int4 | static | ingest-typed | 349.07 (166.46–380.36) | 128.00 |
| mysql-int4 | static | interpret | 31.03 (26.09–34.49) | 2.00 |
| mysql-int4 | static | peak | 6.74 (6.72–6.76) | 1.00 |
| postgres-int4 | stock | ingest | 66.69 (39.00–81.56) | 16.00 |
| postgres-int4 | stock | read-aot | 1.97 (1.96–3.88) | 0.00 |
| postgres-int4 | stock | interpret | 5.29 (4.53–5.91) | 2.00 |
| postgres-int4 | stock | peak | 1.56 (1.51–2.56) | 1.00 |
| postgres-int4 | static | ingest | 229.13 (149.81–322.40) | 128.00 |
| postgres-int4 | static | ingest-typed | 210.33 (149.05–344.71) | 128.00 |
| postgres-int4 | static | interpret | 26.56 (23.99–32.12) | 2.00 |
| postgres-int4 | static | peak | 6.54 (6.49–6.83) | 1.00 |
| proto-mixed | stock | ingest | 1768.49 (1306.72–2180.51) | 1776.75 |
| proto-mixed | stock | read-aot | 11.98 (7.71–13.68) | 0.00 |
| proto-mixed | stock | interpret-boundary | 14.13 (9.28–14.78) | 2.00 |
| proto-mixed | stock | peak-boundary | 8.85 (8.67–8.91) | 1.00 |
| proto-mixed | stock | serialize | 1065.48 (965.63–1234.31) | 436.50 |
| proto-mixed | owned | ingest | 20513.79 (17962.12–21076.41) | 3213.00 |
| proto-mixed | owned | ingest-typed | 12400.80 (11543.96–12416.13) | 3213.00 |
| proto-mixed | owned | interpret | 169.40 (95.39–172.25) | 2.00 |
| proto-mixed | owned | peak | 23.39 (14.48–24.39) | 1.00 |
| proto-mixed | owned | ingest-generated | 11638.57 (10977.98–22354.60) | 3213.00 |
| proto-mixed | owned | read-generated-aot | 114.29 (111.94–119.26) | 0.00 |
| proto-mixed | owned | interpret-generated | 116.28 (114.33–119.50) | 2.00 |
| proto-mixed | owned | peak-generated | 22.95 (20.44–26.43) | 1.00 |
| proto-mixed | owned | serialize | 8355.72 (4925.12–9212.29) | 556.00 |
| proto-mixed | buffa | ingest | 6945.71 (6156.37–9932.78) | 1727.75 |
| proto-mixed | buffa | ingest-typed | 8930.87 (8635.58–11263.15) | 1727.75 |
| proto-mixed | buffa | interpret | 97.23 (83.08–138.64) | 2.00 |
| proto-mixed | buffa | peak | 30.86 (19.05–31.20) | 1.00 |
| proto-mixed | buffa | ingest-generated | 8825.27 (6411.15–10662.59) | 1727.75 |
| proto-mixed | buffa | read-generated-aot | 53.82 (31.98–59.50) | 0.00 |
| proto-mixed | buffa | interpret-generated | 38.77 (35.50–66.97) | 2.00 |
| proto-mixed | buffa | peak-generated | 28.22 (26.78–29.09) | 1.00 |
| proto-mixed | buffa | serialize | 67.82 (53.81–75.03) | 176.50 |
| proto-mixed | generated-owned | ingest | 2195.67 (1492.53–2456.98) | 1800.50 |
| proto-mixed | generated-owned | read-aot | 14.24 (13.53–14.38) | 0.00 |
| proto-mixed | generated-owned | interpret | 6.00 (5.10–6.93) | 2.00 |
| proto-mixed | generated-owned | peak | 6.66 (5.33–7.75) | 1.00 |
| proto-mixed | generated-owned | serialize | 1112.24 (1071.48–1894.95) | 192.50 |
| proto-mixed-full | stock | ingest | 1912.42 (1485.18–1945.44) | 1776.75 |
| proto-mixed-full | owned | ingest | 24029.27 (13953.82–29988.36) | 3213.00 |
| proto-mixed-full | buffa | ingest | 11228.58 (7759.81–14164.44) | 1854.25 |
| proto-mixed-full | generated-owned | ingest | 1601.61 (1557.35–2326.96) | 1800.50 |

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

Binary SHA-256: `2788dc1bc481766c729d6d8dd75ad9d6077baae93088881c6cc9b32aeebfdbac`
Revision: `0105fcbbe0dd214e356831f5bf4ee3e8b9f2f2fe`
