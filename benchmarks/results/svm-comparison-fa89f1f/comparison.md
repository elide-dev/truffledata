# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 119.28 (106.48–159.06) | 136.00 |
| proto-scalar | stock | read-aot | 2.05 (2.03–3.46) | 0.00 |
| proto-scalar | stock | interpret | 3.72 (2.94–3.73) | 2.00 |
| proto-scalar | stock | peak | 1.63 (1.60–1.69) | 1.00 |
| proto-scalar | stock | serialize | 32.82 (29.11–33.06) | 56.00 |
| proto-scalar | stock | project-aot | 71.26 (70.63–71.49) | 136.00 |
| proto-scalar | stock | project-interpret | 104.16 (99.33–108.77) | 138.00 |
| proto-scalar | stock | project-peak | 81.45 (79.48–106.64) | 137.00 |
| proto-scalar | owned | ingest | 170.46 (141.15–173.16) | 64.00 |
| proto-scalar | owned | ingest-typed | 196.13 (111.73–214.60) | 48.00 |
| proto-scalar | owned | interpret | 48.23 (31.89–48.84) | 2.00 |
| proto-scalar | owned | peak | 3.42 (3.26–7.70) | 1.00 |
| proto-scalar | owned | ingest-generated | 79.04 (48.67–82.80) | 48.00 |
| proto-scalar | owned | read-generated-aot | 11.89 (11.83–14.21) | 0.00 |
| proto-scalar | owned | interpret-generated | 22.93 (14.19–23.74) | 2.00 |
| proto-scalar | owned | peak-generated | 4.79 (2.80–5.29) | 1.00 |
| proto-scalar | owned | serialize | 156.17 (155.74–165.10) | 104.00 |
| proto-scalar | buffa | ingest | 209.09 (208.85–320.36) | 112.00 |
| proto-scalar | buffa | ingest-typed | 214.41 (200.69–256.01) | 80.00 |
| proto-scalar | buffa | interpret | 27.41 (26.81–35.10) | 2.00 |
| proto-scalar | buffa | peak | 6.39 (4.33–6.57) | 1.00 |
| proto-scalar | buffa | ingest-generated | 116.11 (115.52–198.35) | 80.00 |
| proto-scalar | buffa | read-generated-aot | 23.27 (16.01–24.38) | 0.00 |
| proto-scalar | buffa | interpret-generated | 18.63 (17.29–27.27) | 2.00 |
| proto-scalar | buffa | peak-generated | 4.07 (4.06–6.75) | 1.00 |
| proto-scalar | buffa | serialize | 24.73 (24.48–37.94) | 24.00 |
| proto-scalar | generated-owned | ingest | 40.71 (38.50–43.75) | 88.00 |
| proto-scalar | generated-owned | read-aot | 1.86 (1.62–2.49) | 0.00 |
| proto-scalar | generated-owned | interpret | 2.64 (2.25–3.35) | 2.00 |
| proto-scalar | generated-owned | peak | 2.66 (1.87–4.23) | 1.00 |
| proto-scalar | generated-owned | serialize | 52.00 (32.14–52.45) | 24.00 |
| proto-scalar | generated-owned | project-aot | 13.64 (13.59–13.70) | 0.00 |
| proto-scalar | generated-owned | project-interpret | 18.72 (18.30–22.08) | 2.00 |
| proto-scalar | generated-owned | project-peak | 20.52 (17.01–24.34) | 1.00 |
| proto-bytes | stock | ingest | 31384.67 (30766.62–35232.09) | 65699.00 |
| proto-bytes | stock | read-aot | 6.29 (4.56–6.31) | 0.00 |
| proto-bytes | stock | interpret | 9.04 (5.58–13.05) | 2.00 |
| proto-bytes | stock | peak | 4.82 (4.25–5.66) | 1.00 |
| proto-bytes | stock | serialize | 10518.71 (10187.63–11265.61) | 65599.00 |
| proto-bytes | stock-aliased | ingest | 111.82 (93.47–134.78) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 3.26 (3.24–4.78) | 0.00 |
| proto-bytes | stock-aliased | interpret | 5.63 (5.57–6.51) | 2.00 |
| proto-bytes | stock-aliased | peak | 4.20 (4.18–4.55) | 1.00 |
| proto-bytes | stock-aliased | serialize | 10888.14 (10483.57–11007.31) | 65599.00 |
| proto-bytes | owned | ingest | 11489.82 (11075.31–12694.87) | 65995.00 |
| proto-bytes | owned | interpret | 27.67 (27.03–32.50) | 2.00 |
| proto-bytes | owned | peak | 10.47 (6.79–10.66) | 1.00 |
| proto-bytes | owned | ingest-generated | 12098.51 (11561.00–15860.15) | 65995.00 |
| proto-bytes | owned | read-generated-aot | 30.29 (24.48–34.06) | 0.00 |
| proto-bytes | owned | interpret-generated | 24.39 (24.22–34.02) | 2.00 |
| proto-bytes | owned | peak-generated | 7.41 (6.58–7.58) | 1.00 |
| proto-bytes | owned | serialize | 12307.58 (12230.17–12670.73) | 65615.00 |
| proto-bytes | buffa | ingest | 361.49 (360.30–370.17) | 272.00 |
| proto-bytes | buffa | interpret | 33.87 (25.85–40.56) | 2.00 |
| proto-bytes | buffa | peak | 7.56 (5.90–10.12) | 1.00 |
| proto-bytes | buffa | ingest-generated | 355.21 (280.23–508.89) | 272.00 |
| proto-bytes | buffa | read-generated-aot | 14.86 (11.39–18.07) | 0.00 |
| proto-bytes | buffa | interpret-generated | 21.61 (13.81–23.38) | 2.00 |
| proto-bytes | buffa | peak-generated | 7.03 (6.28–9.43) | 1.00 |
| proto-bytes | buffa | serialize | 13047.65 (11096.87–21686.50) | 65567.00 |
| proto-bytes | generated-owned | ingest | 12519.92 (11002.97–22604.37) | 65651.00 |
| proto-bytes | generated-owned | read-aot | 3.39 (2.48–5.03) | 0.00 |
| proto-bytes | generated-owned | interpret | 4.96 (2.97–5.82) | 2.00 |
| proto-bytes | generated-owned | peak | 2.94 (2.78–3.84) | 1.00 |
| proto-bytes | generated-owned | serialize | 11027.63 (10908.78–11648.72) | 65583.00 |
| timestamp-json | stock | ingest | 5339.33 (5337.68–7960.79) | 6439.75 |
| timestamp-json | owned | ingest | 2360.10 (2323.29–3232.51) | 3655.75 |
| timestamp-json | owned | ingest-typed | 3182.01 (2335.91–3359.25) | 3639.75 |
| timestamp-json | owned | ingest-generated | 2214.69 (2209.42–2810.09) | 3639.75 |
| timestamp-json | buffa | ingest | 837.08 (809.43–976.99) | 688.00 |
| timestamp-json | buffa | ingest-typed | 856.16 (808.79–982.92) | 688.00 |
| timestamp-json | buffa | ingest-generated | 1049.90 (703.87–1303.07) | 688.00 |
| mysql-int4 | stock | ingest | 64.33 (60.12–86.09) | 16.00 |
| mysql-int4 | stock | read-aot | 2.27 (1.83–2.34) | 0.00 |
| mysql-int4 | stock | interpret | 3.02 (3.02–4.90) | 2.00 |
| mysql-int4 | stock | peak | 1.53 (1.48–2.64) | 1.00 |
| mysql-int4 | static | ingest | 213.47 (162.36–251.97) | 128.00 |
| mysql-int4 | static | ingest-typed | 167.40 (165.35–300.56) | 128.00 |
| mysql-int4 | static | interpret | 17.60 (17.51–17.81) | 2.00 |
| mysql-int4 | static | peak | 3.52 (3.36–3.57) | 1.00 |
| postgres-int4 | stock | ingest | 41.75 (38.77–51.81) | 16.00 |
| postgres-int4 | stock | read-aot | 2.34 (2.16–3.54) | 0.00 |
| postgres-int4 | stock | interpret | 4.55 (3.05–5.09) | 2.00 |
| postgres-int4 | stock | peak | 2.41 (1.62–2.45) | 1.00 |
| postgres-int4 | static | ingest | 253.07 (151.89–281.15) | 128.00 |
| postgres-int4 | static | ingest-typed | 160.70 (150.36–262.27) | 128.00 |
| postgres-int4 | static | interpret | 18.85 (18.55–20.45) | 2.00 |
| postgres-int4 | static | peak | 5.99 (3.35–6.43) | 1.00 |
| proto-mixed | stock | ingest | 1307.37 (1296.64–1364.10) | 1776.75 |
| proto-mixed | stock | read-aot | 7.73 (7.62–12.18) | 0.00 |
| proto-mixed | stock | interpret-boundary | 9.98 (9.96–15.04) | 2.00 |
| proto-mixed | stock | peak-boundary | 11.57 (9.38–15.06) | 1.00 |
| proto-mixed | stock | serialize | 968.58 (877.05–1356.22) | 436.50 |
| proto-mixed | owned | ingest | 7138.10 (7109.96–9066.96) | 2976.00 |
| proto-mixed | owned | ingest-typed | 7313.85 (7152.01–12303.71) | 2976.00 |
| proto-mixed | owned | interpret | 105.85 (88.86–128.58) | 2.00 |
| proto-mixed | owned | peak | 13.75 (13.45–23.70) | 1.00 |
| proto-mixed | owned | ingest-generated | 10563.47 (7013.58–11283.25) | 2976.00 |
| proto-mixed | owned | read-generated-aot | 62.35 (62.07–96.40) | 0.00 |
| proto-mixed | owned | interpret-generated | 102.20 (97.05–102.83) | 2.00 |
| proto-mixed | owned | peak-generated | 18.64 (13.03–19.14) | 1.00 |
| proto-mixed | owned | serialize | 6212.30 (5387.12–7889.13) | 556.00 |
| proto-mixed | buffa | ingest | 7196.96 (6059.05–9112.92) | 1727.75 |
| proto-mixed | buffa | ingest-typed | 6880.25 (6284.09–9218.94) | 1727.75 |
| proto-mixed | buffa | interpret | 95.47 (82.12–126.81) | 2.00 |
| proto-mixed | buffa | peak | 26.42 (16.37–29.05) | 1.00 |
| proto-mixed | buffa | ingest-generated | 9684.81 (5627.98–9954.38) | 1727.75 |
| proto-mixed | buffa | read-generated-aot | 37.40 (30.66–40.98) | 0.00 |
| proto-mixed | buffa | interpret-generated | 35.77 (32.98–52.27) | 2.00 |
| proto-mixed | buffa | peak-generated | 15.57 (14.89–16.14) | 1.00 |
| proto-mixed | buffa | serialize | 49.79 (47.69–79.91) | 176.50 |
| proto-mixed | generated-owned | ingest | 1638.80 (1621.43–2218.38) | 1800.50 |
| proto-mixed | generated-owned | read-aot | 11.82 (11.24–12.47) | 0.00 |
| proto-mixed | generated-owned | interpret | 5.07 (5.05–5.54) | 2.00 |
| proto-mixed | generated-owned | peak | 5.90 (5.32–9.29) | 1.00 |
| proto-mixed | generated-owned | serialize | 1365.33 (1033.76–1711.21) | 192.50 |
| proto-mixed-full | stock | ingest | 2120.11 (1499.14–2377.26) | 1776.75 |
| proto-mixed-full | owned | ingest | 11298.71 (9782.00–14236.74) | 2976.00 |
| proto-mixed-full | buffa | ingest | 9205.58 (8427.16–11762.75) | 1854.25 |
| proto-mixed-full | generated-owned | ingest | 1591.31 (1557.14–2320.04) | 1800.50 |

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

Binary SHA-256: `964327921ccd754f085330d381d801a2ee21cb512132d514f6b45b00fbae4441`
Revision: `fa89f1f36136910d6ff8877064feed8145cc9e43`
