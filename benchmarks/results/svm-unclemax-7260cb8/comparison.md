# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 47.61 (47.46–48.33) | 136.00 |
| proto-scalar | stock | read-aot | 0.95 (0.95–0.95) | 0.00 |
| proto-scalar | stock | interpret | 1.34 (1.34–1.35) | 2.00 |
| proto-scalar | stock | peak | 0.80 (0.80–0.80) | 1.00 |
| proto-scalar | stock | serialize | 13.68 (13.62–14.01) | 56.00 |
| proto-scalar | stock | project-aot | 32.96 (32.32–33.36) | 136.00 |
| proto-scalar | stock | project-interpret | 33.15 (32.98–34.73) | 138.00 |
| proto-scalar | stock | project-peak | 33.79 (33.34–35.06) | 137.00 |
| proto-scalar | owned | ingest | 58.01 (57.24–58.19) | 64.00 |
| proto-scalar | owned | ingest-typed | 57.26 (55.99–60.04) | 48.00 |
| proto-scalar | owned | interpret | 15.24 (15.23–15.29) | 2.00 |
| proto-scalar | owned | peak | 1.51 (1.51–1.61) | 1.00 |
| proto-scalar | owned | ingest-generated | 21.13 (20.98–21.39) | 48.00 |
| proto-scalar | owned | read-generated-aot | 6.68 (6.67–6.72) | 0.00 |
| proto-scalar | owned | interpret-generated | 7.62 (7.60–7.65) | 2.00 |
| proto-scalar | owned | peak-generated | 1.08 (1.08–1.08) | 1.00 |
| proto-scalar | owned | serialize | 11.53 (11.51–12.27) | 24.00 |
| proto-scalar | buffa | ingest | 89.30 (89.23–89.88) | 112.00 |
| proto-scalar | buffa | ingest-typed | 91.67 (87.04–92.30) | 80.00 |
| proto-scalar | buffa | interpret | 11.69 (11.63–12.24) | 2.00 |
| proto-scalar | buffa | peak | 1.45 (1.45–1.53) | 1.00 |
| proto-scalar | buffa | ingest-generated | 53.35 (53.03–56.89) | 80.00 |
| proto-scalar | buffa | read-generated-aot | 7.84 (7.84–7.85) | 0.00 |
| proto-scalar | buffa | interpret-generated | 8.70 (8.69–9.08) | 2.00 |
| proto-scalar | buffa | peak-generated | 1.49 (1.49–1.58) | 1.00 |
| proto-scalar | buffa | serialize | 11.71 (11.68–11.74) | 24.00 |
| proto-scalar | generated-owned | ingest | 17.68 (17.68–17.97) | 88.00 |
| proto-scalar | generated-owned | read-aot | 0.76 (0.76–0.76) | 0.00 |
| proto-scalar | generated-owned | interpret | 0.97 (0.97–1.03) | 2.00 |
| proto-scalar | generated-owned | peak | 0.82 (0.82–0.83) | 1.00 |
| proto-scalar | generated-owned | serialize | 15.21 (14.36–15.26) | 24.00 |
| proto-scalar | generated-owned | project-aot | 6.74 (6.73–6.78) | 0.00 |
| proto-scalar | generated-owned | project-interpret | 7.88 (7.21–8.11) | 2.00 |
| proto-scalar | generated-owned | project-peak | 7.77 (7.66–8.07) | 1.00 |
| proto-bytes | stock | ingest | 14484.73 (14458.33–14541.58) | 65699.00 |
| proto-bytes | stock | read-aot | 2.10 (2.08–2.11) | 0.00 |
| proto-bytes | stock | interpret | 3.13 (3.11–3.15) | 2.00 |
| proto-bytes | stock | peak | 2.28 (2.25–2.29) | 1.00 |
| proto-bytes | stock | serialize | 3155.66 (3120.84–3178.02) | 65599.00 |
| proto-bytes | stock-aliased | ingest | 36.90 (36.73–37.29) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 1.97 (1.97–1.98) | 0.00 |
| proto-bytes | stock-aliased | interpret | 3.04 (2.94–3.18) | 2.00 |
| proto-bytes | stock-aliased | peak | 2.07 (2.06–2.07) | 1.00 |
| proto-bytes | stock-aliased | serialize | 3168.63 (3161.51–3175.07) | 65599.00 |
| proto-bytes | owned | ingest | 3529.49 (3433.71–3544.13) | 65995.00 |
| proto-bytes | owned | interpret | 12.26 (12.11–12.27) | 2.00 |
| proto-bytes | owned | peak | 2.84 (2.84–2.88) | 1.00 |
| proto-bytes | owned | ingest-generated | 3531.02 (3529.38–3540.51) | 65995.00 |
| proto-bytes | owned | read-generated-aot | 10.14 (10.05–10.27) | 0.00 |
| proto-bytes | owned | interpret-generated | 11.15 (11.06–11.29) | 2.00 |
| proto-bytes | owned | peak-generated | 2.72 (2.70–2.74) | 1.00 |
| proto-bytes | owned | serialize | 3203.89 (3138.56–3417.04) | 65615.00 |
| proto-bytes | buffa | ingest | 170.55 (170.46–181.34) | 272.00 |
| proto-bytes | buffa | interpret | 13.55 (13.44–14.29) | 2.00 |
| proto-bytes | buffa | peak | 2.66 (2.63–2.68) | 1.00 |
| proto-bytes | buffa | ingest-generated | 136.35 (128.35–139.44) | 272.00 |
| proto-bytes | buffa | read-generated-aot | 6.46 (6.46–6.92) | 0.00 |
| proto-bytes | buffa | interpret-generated | 7.39 (7.09–7.47) | 2.00 |
| proto-bytes | buffa | peak-generated | 2.88 (2.87–2.89) | 1.00 |
| proto-bytes | buffa | serialize | 3134.78 (3112.94–3166.49) | 65567.00 |
| proto-bytes | generated-owned | ingest | 3217.13 (3186.45–3514.29) | 65651.00 |
| proto-bytes | generated-owned | read-aot | 0.90 (0.90–0.90) | 0.00 |
| proto-bytes | generated-owned | interpret | 1.13 (1.13–1.15) | 2.00 |
| proto-bytes | generated-owned | peak | 1.21 (1.18–1.24) | 1.00 |
| proto-bytes | generated-owned | serialize | 3165.77 (3142.45–3198.72) | 65583.00 |
| timestamp-json | stock | ingest | 2283.29 (2281.79–2283.35) | 6439.75 |
| timestamp-json | owned | ingest | 982.15 (977.62–989.41) | 3655.75 |
| timestamp-json | owned | ingest-typed | 968.52 (963.45–1019.62) | 3639.75 |
| timestamp-json | owned | ingest-generated | 934.58 (932.47–935.93) | 3639.75 |
| timestamp-json | buffa | ingest | 473.31 (471.22–473.37) | 688.00 |
| timestamp-json | buffa | ingest-typed | 473.55 (473.16–525.86) | 688.00 |
| timestamp-json | buffa | ingest-generated | 427.20 (421.69–440.97) | 688.00 |
| mysql-int4 | stock | ingest | 23.37 (23.30–23.63) | 16.00 |
| mysql-int4 | stock | read-aot | 0.79 (0.79–0.79) | 0.00 |
| mysql-int4 | stock | interpret | 1.42 (1.39–1.45) | 2.00 |
| mysql-int4 | stock | peak | 0.73 (0.72–0.73) | 1.00 |
| mysql-int4 | static | ingest | 65.37 (64.84–65.61) | 80.00 |
| mysql-int4 | static | ingest-typed | 65.20 (65.15–69.06) | 80.00 |
| mysql-int4 | static | interpret | 9.84 (9.84–10.59) | 2.00 |
| mysql-int4 | static | peak | 1.59 (1.59–1.60) | 1.00 |
| postgres-int4 | stock | ingest | 17.07 (17.05–17.08) | 16.00 |
| postgres-int4 | stock | read-aot | 0.87 (0.86–0.87) | 0.00 |
| postgres-int4 | stock | interpret | 1.39 (1.39–1.42) | 2.00 |
| postgres-int4 | stock | peak | 0.73 (0.72–0.77) | 1.00 |
| postgres-int4 | static | ingest | 56.23 (56.02–56.49) | 80.00 |
| postgres-int4 | static | ingest-typed | 56.78 (56.21–59.95) | 80.00 |
| postgres-int4 | static | interpret | 9.78 (9.73–10.20) | 2.00 |
| postgres-int4 | static | peak | 1.60 (1.59–1.60) | 1.00 |
| proto-mixed | stock | ingest | 633.47 (633.12–637.38) | 1776.75 |
| proto-mixed | stock | read-aot | 4.92 (4.91–4.99) | 0.00 |
| proto-mixed | stock | interpret-boundary | 5.28 (5.26–5.42) | 2.00 |
| proto-mixed | stock | peak-boundary | 5.09 (5.07–5.67) | 1.00 |
| proto-mixed | stock | serialize | 428.96 (428.57–432.73) | 436.50 |
| proto-mixed | owned | ingest | 3126.00 (3101.93–3149.02) | 2976.00 |
| proto-mixed | owned | ingest-typed | 3109.52 (3103.18–3113.86) | 2976.00 |
| proto-mixed | owned | interpret | 44.86 (44.83–44.88) | 2.00 |
| proto-mixed | owned | peak | 6.81 (6.53–6.98) | 1.00 |
| proto-mixed | owned | ingest-generated | 2935.35 (2926.52–2962.84) | 2976.00 |
| proto-mixed | owned | read-generated-aot | 33.28 (33.27–37.88) | 0.00 |
| proto-mixed | owned | interpret-generated | 34.06 (34.06–34.37) | 2.00 |
| proto-mixed | owned | peak-generated | 6.18 (6.13–6.40) | 1.00 |
| proto-mixed | owned | serialize | 2491.25 (2473.04–2648.89) | 556.00 |
| proto-mixed | buffa | ingest | 2464.89 (2461.89–2473.30) | 1727.75 |
| proto-mixed | buffa | ingest-typed | 2535.89 (2511.60–2610.64) | 1727.75 |
| proto-mixed | buffa | interpret | 37.98 (37.96–38.38) | 2.00 |
| proto-mixed | buffa | peak | 7.01 (6.97–7.12) | 1.00 |
| proto-mixed | buffa | ingest-generated | 2360.35 (2353.65–2383.57) | 1727.75 |
| proto-mixed | buffa | read-generated-aot | 16.17 (16.13–17.25) | 0.00 |
| proto-mixed | buffa | interpret-generated | 17.09 (17.04–17.09) | 2.00 |
| proto-mixed | buffa | peak-generated | 6.53 (6.48–6.99) | 1.00 |
| proto-mixed | buffa | serialize | 18.76 (18.59–19.82) | 176.50 |
| proto-mixed | generated-owned | ingest | 688.59 (684.55–696.92) | 1800.50 |
| proto-mixed | generated-owned | read-aot | 3.96 (3.95–3.97) | 0.00 |
| proto-mixed | generated-owned | interpret | 2.73 (2.72–2.74) | 2.00 |
| proto-mixed | generated-owned | peak | 2.70 (2.70–2.75) | 1.00 |
| proto-mixed | generated-owned | serialize | 469.84 (467.31–489.40) | 192.50 |
| proto-mixed-full | stock | ingest | 693.03 (669.83–693.56) | 1776.75 |
| proto-mixed-full | owned | ingest | 4226.84 (4219.31–4230.45) | 2976.00 |
| proto-mixed-full | buffa | ingest | 3162.39 (3066.31–3290.03) | 1854.25 |
| proto-mixed-full | generated-owned | ingest | 729.18 (725.44–735.34) | 1800.50 |

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

Binary SHA-256: `eaefeea3e6f75489db22f19619b4ed1be933abcd93fe6909168fe6df0e47aa9a`
Revision: `7260cb87be5e92240034580e5d1c8d76b786297e`
