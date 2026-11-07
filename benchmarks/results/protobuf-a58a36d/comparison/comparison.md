# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 57.30 (55.20–65.67) | 136.00 |
| proto-scalar | stock | read-aot | 1.08 (1.07–1.09) | 0.00 |
| proto-scalar | stock | interpret | 1.53 (1.50–1.59) | 2.00 |
| proto-scalar | stock | peak | 0.91 (0.91–0.92) | 1.00 |
| proto-scalar | stock | serialize | 17.00 (15.35–23.01) | 56.00 |
| proto-scalar | stock | project-aot | 35.22 (35.14–36.52) | 136.00 |
| proto-scalar | stock | project-interpret | 37.32 (36.19–37.89) | 138.00 |
| proto-scalar | stock | project-peak | 36.33 (35.99–36.89) | 137.00 |
| proto-scalar | owned | ingest | 66.27 (62.91–66.33) | 64.00 |
| proto-scalar | owned | ingest-typed | 65.12 (61.48–67.91) | 48.00 |
| proto-scalar | owned | interpret | 16.89 (16.83–17.35) | 2.00 |
| proto-scalar | owned | peak | 1.87 (1.68–1.92) | 1.00 |
| proto-scalar | owned | ingest-generated | 23.22 (22.76–23.91) | 48.00 |
| proto-scalar | owned | read-generated-aot | 6.54 (6.39–6.71) | 0.00 |
| proto-scalar | owned | interpret-generated | 7.30 (7.04–8.60) | 2.00 |
| proto-scalar | owned | peak-generated | 1.24 (1.23–1.27) | 1.00 |
| proto-scalar | owned | serialize | 13.09 (12.93–13.56) | 24.00 |
| proto-scalar | buffa | ingest | 101.47 (96.77–143.56) | 112.00 |
| proto-scalar | buffa | ingest-typed | 95.54 (95.49–97.16) | 80.00 |
| proto-scalar | buffa | interpret | 13.31 (13.20–20.19) | 2.00 |
| proto-scalar | buffa | peak | 1.69 (1.67–1.75) | 1.00 |
| proto-scalar | buffa | ingest-generated | 57.53 (57.37–57.82) | 80.00 |
| proto-scalar | buffa | read-generated-aot | 8.01 (7.87–9.11) | 0.00 |
| proto-scalar | buffa | interpret-generated | 9.09 (8.76–9.34) | 2.00 |
| proto-scalar | buffa | peak-generated | 1.69 (1.65–1.73) | 1.00 |
| proto-scalar | buffa | serialize | 13.11 (12.92–13.28) | 24.00 |
| proto-scalar | generated-owned | ingest | 22.82 (21.17–25.66) | 88.00 |
| proto-scalar | generated-owned | read-aot | 0.92 (0.87–0.92) | 0.00 |
| proto-scalar | generated-owned | interpret | 1.12 (1.12–1.19) | 2.00 |
| proto-scalar | generated-owned | peak | 0.94 (0.93–0.95) | 1.00 |
| proto-scalar | generated-owned | serialize | 17.29 (16.07–20.05) | 24.00 |
| proto-scalar | generated-owned | project-aot | 7.57 (7.52–7.60) | 0.00 |
| proto-scalar | generated-owned | project-interpret | 8.56 (8.02–9.98) | 2.00 |
| proto-scalar | generated-owned | project-peak | 8.74 (8.57–8.94) | 1.00 |
| proto-bytes | stock | ingest | 16041.57 (15915.49–16831.83) | 65699.00 |
| proto-bytes | stock | read-aot | 2.37 (2.30–2.39) | 0.00 |
| proto-bytes | stock | interpret | 3.48 (3.48–3.62) | 2.00 |
| proto-bytes | stock | peak | 2.62 (2.58–53.70) | 1.00 |
| proto-bytes | stock | serialize | 3560.35 (3519.28–3664.51) | 65599.00 |
| proto-bytes | stock-aliased | ingest | 42.03 (41.30–42.12) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 2.22 (2.22–2.26) | 0.00 |
| proto-bytes | stock-aliased | interpret | 3.38 (3.25–3.56) | 2.00 |
| proto-bytes | stock-aliased | peak | 2.48 (2.43–2.49) | 1.00 |
| proto-bytes | stock-aliased | serialize | 3815.08 (3414.48–31144.96) | 65599.00 |
| proto-bytes | owned | ingest | 3864.47 (3781.06–7031.32) | 65995.00 |
| proto-bytes | owned | interpret | 13.98 (13.57–14.83) | 2.00 |
| proto-bytes | owned | peak | 3.22 (3.13–3.36) | 1.00 |
| proto-bytes | owned | ingest-generated | 3767.11 (3579.81–15211.33) | 65995.00 |
| proto-bytes | owned | read-generated-aot | 10.53 (9.92–10.89) | 0.00 |
| proto-bytes | owned | interpret-generated | 10.77 (10.73–11.09) | 2.00 |
| proto-bytes | owned | peak-generated | 3.06 (3.00–8.46) | 1.00 |
| proto-bytes | owned | serialize | 3767.81 (3701.01–24113.58) | 65615.00 |
| proto-bytes | buffa | ingest | 145.13 (144.94–145.85) | 264.00 |
| proto-bytes | buffa | interpret | 15.33 (15.31–16.12) | 2.00 |
| proto-bytes | buffa | peak | 3.11 (2.97–3.14) | 1.00 |
| proto-bytes | buffa | ingest-generated | 111.57 (107.42–137.39) | 264.00 |
| proto-bytes | buffa | read-generated-aot | 7.19 (6.82–7.47) | 0.00 |
| proto-bytes | buffa | interpret-generated | 7.92 (7.63–10.40) | 2.00 |
| proto-bytes | buffa | peak-generated | 3.50 (3.45–3.66) | 1.00 |
| proto-bytes | buffa | serialize | 3577.69 (3526.60–3721.22) | 65567.00 |
| proto-bytes | generated-owned | ingest | 3800.17 (3590.95–5103.50) | 65651.00 |
| proto-bytes | generated-owned | read-aot | 1.14 (1.07–1.14) | 0.00 |
| proto-bytes | generated-owned | interpret | 1.29 (1.26–1.36) | 2.00 |
| proto-bytes | generated-owned | peak | 1.29 (1.28–1.35) | 1.00 |
| proto-bytes | generated-owned | serialize | 23775.32 (3464.90–27453.47) | 65583.00 |
| timestamp-json | stock | ingest | 2631.44 (2617.78–3072.41) | 6439.75 |
| timestamp-json | owned | ingest | 1087.85 (1082.94–1383.14) | 3655.75 |
| timestamp-json | owned | ingest-typed | 1096.39 (1093.02–1263.16) | 3639.75 |
| timestamp-json | owned | ingest-generated | 1095.60 (1029.56–1191.59) | 3639.75 |
| timestamp-json | buffa | ingest | 496.15 (493.07–522.35) | 720.00 |
| timestamp-json | buffa | ingest-typed | 508.39 (504.46–510.50) | 720.00 |
| timestamp-json | buffa | ingest-generated | 419.86 (413.48–444.00) | 720.00 |
| mysql-int4 | stock | ingest | 26.87 (26.11–28.12) | 16.00 |
| mysql-int4 | stock | read-aot | 0.94 (0.90–1.07) | 0.00 |
| mysql-int4 | stock | interpret | 1.57 (1.55–1.92) | 2.00 |
| mysql-int4 | stock | peak | 0.83 (0.83–0.84) | 1.00 |
| mysql-int4 | static | ingest | 74.29 (68.33–979.55) | 80.00 |
| mysql-int4 | static | ingest-typed | 71.54 (69.99–85.50) | 80.00 |
| mysql-int4 | static | interpret | 11.18 (11.07–11.83) | 2.00 |
| mysql-int4 | static | peak | 1.78 (1.78–1.92) | 1.00 |
| postgres-int4 | stock | ingest | 19.39 (19.29–20.16) | 16.00 |
| postgres-int4 | stock | read-aot | 0.97 (0.97–1.02) | 0.00 |
| postgres-int4 | stock | interpret | 1.57 (1.55–1.89) | 2.00 |
| postgres-int4 | stock | peak | 0.83 (0.82–0.84) | 1.00 |
| postgres-int4 | static | ingest | 63.61 (63.60–67.41) | 80.00 |
| postgres-int4 | static | ingest-typed | 69.20 (64.36–81.02) | 80.00 |
| postgres-int4 | static | interpret | 11.24 (10.94–11.78) | 2.00 |
| postgres-int4 | static | peak | 1.86 (1.78–1.91) | 1.00 |
| proto-mixed | stock | ingest | 712.12 (708.81–731.90) | 1776.75 |
| proto-mixed | stock | read-aot | 5.64 (5.57–7.32) | 0.00 |
| proto-mixed | stock | interpret-boundary | 6.27 (6.00–6.64) | 2.00 |
| proto-mixed | stock | peak-boundary | 5.97 (5.72–6.13) | 1.00 |
| proto-mixed | stock | serialize | 457.78 (455.63–463.05) | 436.50 |
| proto-mixed | owned | ingest | 3446.96 (3389.70–3493.07) | 2976.00 |
| proto-mixed | owned | ingest-typed | 3452.07 (3431.59–3545.22) | 2976.00 |
| proto-mixed | owned | interpret | 49.97 (49.49–63.17) | 2.00 |
| proto-mixed | owned | peak | 7.50 (7.40–7.51) | 1.00 |
| proto-mixed | owned | ingest-generated | 3267.48 (3255.78–3336.88) | 2976.00 |
| proto-mixed | owned | read-generated-aot | 34.89 (33.96–36.35) | 0.00 |
| proto-mixed | owned | interpret-generated | 35.31 (34.91–37.32) | 2.00 |
| proto-mixed | owned | peak-generated | 7.21 (6.91–8.99) | 1.00 |
| proto-mixed | owned | serialize | 2810.66 (2752.49–3174.82) | 556.00 |
| proto-mixed | buffa | ingest | 1549.23 (1531.89–1577.36) | 1411.50 |
| proto-mixed | buffa | ingest-typed | 1516.11 (1468.75–1568.60) | 1411.50 |
| proto-mixed | buffa | interpret | 43.76 (43.38–47.92) | 2.00 |
| proto-mixed | buffa | peak | 8.22 (7.70–8.41) | 1.00 |
| proto-mixed | buffa | ingest-generated | 1366.34 (1357.63–1401.02) | 1411.50 |
| proto-mixed | buffa | read-generated-aot | 17.33 (16.41–19.50) | 0.00 |
| proto-mixed | buffa | interpret-generated | 17.16 (16.84–17.31) | 2.00 |
| proto-mixed | buffa | peak-generated | 7.48 (7.41–9.68) | 1.00 |
| proto-mixed | buffa | serialize | 23.89 (20.89–40.02) | 176.50 |
| proto-mixed | generated-owned | ingest | 730.90 (717.63–764.78) | 1800.50 |
| proto-mixed | generated-owned | read-aot | 4.65 (4.41–4.82) | 0.00 |
| proto-mixed | generated-owned | interpret | 3.17 (3.07–3.19) | 2.00 |
| proto-mixed | generated-owned | peak | 3.18 (3.14–3.47) | 1.00 |
| proto-mixed | generated-owned | serialize | 538.64 (529.14–549.67) | 192.50 |
| proto-mixed-full | stock | ingest | 830.14 (773.59–839.39) | 1776.75 |
| proto-mixed-full | owned | ingest | 4686.70 (4678.23–4844.42) | 2976.00 |
| proto-mixed-full | owned | ingest-generated | 3740.03 (3640.72–3846.76) | 2976.00 |
| proto-mixed-full | buffa | ingest | 2274.53 (2225.06–2287.47) | 1538.00 |
| proto-mixed-full | buffa | ingest-generated | 1631.75 (1610.74–2084.62) | 1538.00 |
| proto-mixed-full | generated-owned | ingest | 787.29 (779.92–816.77) | 1800.50 |

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

Binary SHA-256: `b86057dc10d6267c0a57864ba5d5c9e4dba0d867b97e6392ac23bcf607cf6dd0`
Revision: `a58a36d`
