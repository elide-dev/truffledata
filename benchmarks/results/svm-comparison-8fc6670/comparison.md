# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 58.82 (56.60–59.37) | 136.00 |
| proto-scalar | stock | read-aot | 1.16 (1.14–1.73) | 0.00 |
| proto-scalar | stock | interpret | 2.04 (1.83–3.11) | 2.00 |
| proto-scalar | stock | peak | 1.05 (1.01–2.93) | 1.00 |
| proto-scalar | stock | serialize | 17.80 (17.15–883.24) | 56.00 |
| proto-scalar | stock | project-aot | 48.10 (43.81–60.61) | 136.00 |
| proto-scalar | stock | project-interpret | 42.63 (42.34–71.72) | 138.00 |
| proto-scalar | stock | project-peak | 54.07 (42.39–65.90) | 137.00 |
| proto-scalar | owned | ingest | 80.07 (69.90–101.43) | 88.00 |
| proto-scalar | owned | ingest-typed | 80.25 (76.48–112.15) | 56.00 |
| proto-scalar | owned | interpret | 18.91 (17.77–22.51) | 2.00 |
| proto-scalar | owned | peak | 1.76 (1.63–2.31) | 1.00 |
| proto-scalar | owned | ingest-generated | 30.52 (28.56–32.47) | 56.00 |
| proto-scalar | owned | read-generated-aot | 8.81 (8.29–12.06) | 0.00 |
| proto-scalar | owned | interpret-generated | 15.97 (10.62–16.06) | 2.00 |
| proto-scalar | owned | peak-generated | 1.89 (1.83–2.07) | 1.00 |
| proto-scalar | owned | serialize | 121.03 (102.89–138.53) | 136.00 |
| proto-scalar | buffa | ingest | 125.13 (115.55–129.83) | 112.00 |
| proto-scalar | buffa | ingest-typed | 115.07 (107.12–120.54) | 80.00 |
| proto-scalar | buffa | interpret | 15.60 (15.18–26.12) | 2.00 |
| proto-scalar | buffa | peak | 2.16 (1.67–2.18) | 1.00 |
| proto-scalar | buffa | ingest-generated | 68.97 (68.88–75.48) | 80.00 |
| proto-scalar | buffa | read-generated-aot | 12.21 (11.36–22.99) | 0.00 |
| proto-scalar | buffa | interpret-generated | 14.04 (10.00–17.65) | 2.00 |
| proto-scalar | buffa | peak-generated | 2.05 (1.83–2.18) | 1.00 |
| proto-scalar | buffa | serialize | 16.32 (14.47–19.23) | 24.00 |
| proto-scalar | generated-owned | ingest | 22.09 (21.84–25.24) | 88.00 |
| proto-scalar | generated-owned | read-aot | 1.05 (0.94–1.79) | 0.00 |
| proto-scalar | generated-owned | interpret | 1.22 (1.20–1.81) | 2.00 |
| proto-scalar | generated-owned | peak | 1.31 (1.11–16.18) | 1.00 |
| proto-scalar | generated-owned | serialize | 17.87 (17.21–19.03) | 24.00 |
| proto-scalar | generated-owned | project-aot | 8.95 (8.29–13.08) | 0.00 |
| proto-scalar | generated-owned | project-interpret | 9.62 (8.94–14.32) | 2.00 |
| proto-scalar | generated-owned | project-peak | 10.61 (9.38–11.02) | 1.00 |
| proto-bytes | stock | ingest | 19674.29 (19168.51–22126.23) | 65699.00 |
| proto-bytes | stock | read-aot | 2.63 (2.54–3.34) | 0.00 |
| proto-bytes | stock | interpret | 4.00 (3.81–4.34) | 2.00 |
| proto-bytes | stock | peak | 2.81 (2.69–3.48) | 1.00 |
| proto-bytes | stock | serialize | 5324.62 (4755.51–5903.50) | 65599.00 |
| proto-bytes | stock-aliased | ingest | 46.21 (44.97–146.53) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 2.58 (2.55–2.58) | 0.00 |
| proto-bytes | stock-aliased | interpret | 5.84 (3.90–6.62) | 2.00 |
| proto-bytes | stock-aliased | peak | 3.29 (2.61–3.67) | 1.00 |
| proto-bytes | stock-aliased | serialize | 7353.81 (5387.47–8071.06) | 65599.00 |
| proto-bytes | owned | ingest | 5792.10 (5438.75–10561.00) | 66035.00 |
| proto-bytes | owned | interpret | 20.51 (15.27–21.76) | 2.00 |
| proto-bytes | owned | peak | 3.79 (3.79–6.70) | 1.00 |
| proto-bytes | owned | ingest-generated | 7089.12 (5838.79–24705.37) | 66035.00 |
| proto-bytes | owned | read-generated-aot | 12.70 (12.10–13.15) | 0.00 |
| proto-bytes | owned | interpret-generated | 18.67 (14.29–19.14) | 2.00 |
| proto-bytes | owned | peak-generated | 4.17 (3.23–6.41) | 1.00 |
| proto-bytes | owned | serialize | 6639.84 (6213.88–37299.71) | 65615.00 |
| proto-bytes | buffa | ingest | 268.35 (230.79–354.17) | 272.00 |
| proto-bytes | buffa | interpret | 18.41 (16.85–26.72) | 2.00 |
| proto-bytes | buffa | peak | 3.71 (3.52–4.14) | 1.00 |
| proto-bytes | buffa | ingest-generated | 190.69 (185.84–224.57) | 272.00 |
| proto-bytes | buffa | read-generated-aot | 9.07 (7.55–9.92) | 0.00 |
| proto-bytes | buffa | interpret-generated | 9.74 (8.94–16.43) | 2.00 |
| proto-bytes | buffa | peak-generated | 3.79 (3.73–4.41) | 1.00 |
| proto-bytes | buffa | serialize | 6459.54 (5916.49–29142.04) | 65567.00 |
| proto-bytes | generated-owned | ingest | 6350.39 (5871.04–28153.57) | 65651.00 |
| proto-bytes | generated-owned | read-aot | 1.12 (1.09–1.13) | 0.00 |
| proto-bytes | generated-owned | interpret | 1.47 (1.41–2.93) | 2.00 |
| proto-bytes | generated-owned | peak | 1.80 (1.35–2.73) | 1.00 |
| proto-bytes | generated-owned | serialize | 9903.08 (8731.81–16382.95) | 65583.00 |
| timestamp-json | stock | ingest | 4000.35 (3102.74–4101.27) | 6439.75 |
| timestamp-json | owned | ingest | 1323.09 (1273.78–1979.71) | 3703.75 |
| timestamp-json | owned | ingest-typed | 1667.84 (1317.34–1673.25) | 3671.75 |
| timestamp-json | owned | ingest-generated | 1273.94 (1233.53–1407.02) | 3671.75 |
| timestamp-json | buffa | ingest | 510.31 (497.47–541.44) | 688.00 |
| timestamp-json | buffa | ingest-typed | 519.73 (493.38–616.16) | 688.00 |
| timestamp-json | buffa | ingest-generated | 445.67 (429.75–632.76) | 688.00 |
| mysql-int4 | stock | ingest | 36.76 (33.50–78.50) | 16.00 |
| mysql-int4 | stock | read-aot | 0.97 (0.96–1.10) | 0.00 |
| mysql-int4 | stock | interpret | 1.85 (1.70–2.48) | 2.00 |
| mysql-int4 | stock | peak | 0.90 (0.87–1.11) | 1.00 |
| mysql-int4 | static | ingest | 94.91 (88.21–105.32) | 128.00 |
| mysql-int4 | static | ingest-typed | 97.58 (87.43–110.50) | 128.00 |
| mysql-int4 | static | interpret | 11.81 (11.32–13.99) | 2.00 |
| mysql-int4 | static | peak | 1.99 (1.88–2.00) | 1.00 |
| postgres-int4 | stock | ingest | 21.34 (20.27–35.24) | 16.00 |
| postgres-int4 | stock | read-aot | 1.21 (1.09–1.29) | 0.00 |
| postgres-int4 | stock | interpret | 2.33 (1.77–2.96) | 2.00 |
| postgres-int4 | stock | peak | 0.91 (0.91–1.22) | 1.00 |
| postgres-int4 | static | ingest | 123.11 (84.89–141.38) | 128.00 |
| postgres-int4 | static | ingest-typed | 82.16 (79.73–90.77) | 128.00 |
| postgres-int4 | static | interpret | 13.83 (12.53–14.52) | 2.00 |
| postgres-int4 | static | peak | 2.23 (2.08–3.37) | 1.00 |
| proto-mixed | stock | ingest | 758.37 (751.74–832.79) | 1776.75 |
| proto-mixed | stock | read-aot | 6.56 (5.84–7.06) | 0.00 |
| proto-mixed | stock | interpret-boundary | 8.28 (6.88–9.18) | 2.00 |
| proto-mixed | stock | peak-boundary | 6.31 (6.23–15.59) | 1.00 |
| proto-mixed | stock | serialize | 537.77 (506.59–719.78) | 436.50 |
| proto-mixed | owned | ingest | 11531.65 (7708.02–13160.10) | 3339.50 |
| proto-mixed | owned | ingest-typed | 13909.59 (7152.75–14281.04) | 3339.50 |
| proto-mixed | owned | interpret | 62.72 (59.19–86.67) | 2.00 |
| proto-mixed | owned | peak | 8.19 (8.14–9.63) | 1.00 |
| proto-mixed | owned | ingest-generated | 7652.84 (6975.67–7711.13) | 3339.50 |
| proto-mixed | owned | read-generated-aot | 55.17 (46.72–61.80) | 0.00 |
| proto-mixed | owned | interpret-generated | 47.35 (44.44–67.78) | 2.00 |
| proto-mixed | owned | peak-generated | 8.39 (8.32–10.70) | 1.00 |
| proto-mixed | owned | serialize | 3273.71 (3049.74–5310.76) | 556.00 |
| proto-mixed | buffa | ingest | 3085.17 (2921.89–3277.09) | 1727.75 |
| proto-mixed | buffa | ingest-typed | 2990.64 (2961.46–3930.02) | 1727.75 |
| proto-mixed | buffa | interpret | 48.13 (45.31–66.98) | 2.00 |
| proto-mixed | buffa | peak | 9.08 (9.00–9.11) | 1.00 |
| proto-mixed | buffa | ingest-generated | 2915.60 (2845.82–9780.92) | 1727.75 |
| proto-mixed | buffa | read-generated-aot | 21.13 (20.11–23.49) | 0.00 |
| proto-mixed | buffa | interpret-generated | 35.03 (22.43–40.29) | 2.00 |
| proto-mixed | buffa | peak-generated | 8.30 (7.77–9.09) | 1.00 |
| proto-mixed | buffa | serialize | 33.50 (25.11–44.48) | 176.50 |
| proto-mixed | generated-owned | ingest | 801.80 (792.19–1319.95) | 1800.50 |
| proto-mixed | generated-owned | read-aot | 5.56 (5.55–14.31) | 0.00 |
| proto-mixed | generated-owned | interpret | 3.92 (3.27–4.04) | 2.00 |
| proto-mixed | generated-owned | peak | 3.65 (3.32–4.22) | 1.00 |
| proto-mixed | generated-owned | serialize | 1139.49 (652.84–1306.07) | 192.50 |
| proto-mixed-full | stock | ingest | 844.77 (828.00–908.01) | 1776.75 |
| proto-mixed-full | owned | ingest | 9690.63 (8257.90–17216.02) | 3339.50 |
| proto-mixed-full | buffa | ingest | 3964.93 (3617.16–6418.27) | 1854.25 |
| proto-mixed-full | generated-owned | ingest | 909.58 (811.36–928.43) | 1800.50 |

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

Binary SHA-256: `f011a7cd9542d99050b4cc2702d106803a3629f78020d4e99bf7eb0455e7748b`
Revision: `8fc66702d7f42014aa615845450d18bad41ad3b0`
