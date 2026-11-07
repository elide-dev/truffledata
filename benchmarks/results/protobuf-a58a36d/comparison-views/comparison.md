# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 111.59 (109.52–111.68) | 136.00 |
| proto-scalar | stock | read-aot | 2.30 (2.30–2.30) | 0.00 |
| proto-scalar | stock | interpret | 3.04 (3.02–3.04) | 2.00 |
| proto-scalar | stock | peak | 1.86 (1.85–2.15) | 1.00 |
| proto-scalar | stock | serialize | 32.76 (32.52–32.86) | 56.00 |
| proto-scalar | stock | project-aot | 79.55 (76.53–81.09) | 136.00 |
| proto-scalar | stock | project-interpret | 80.90 (79.81–82.21) | 138.00 |
| proto-scalar | stock | project-peak | 87.26 (81.69–87.27) | 137.00 |
| proto-scalar | owned | ingest | 131.20 (130.88–143.32) | 64.00 |
| proto-scalar | owned | ingest-typed | 126.54 (125.75–127.09) | 48.00 |
| proto-scalar | owned | interpret | 36.32 (35.98–36.56) | 2.00 |
| proto-scalar | owned | peak | 3.93 (3.89–3.99) | 1.00 |
| proto-scalar | owned | ingest-generated | 46.27 (46.17–46.44) | 48.00 |
| proto-scalar | owned | read-generated-aot | 13.31 (13.04–13.35) | 0.00 |
| proto-scalar | owned | interpret-generated | 14.23 (14.22–14.69) | 2.00 |
| proto-scalar | owned | peak-generated | 2.49 (2.48–2.51) | 1.00 |
| proto-scalar | owned | serialize | 24.82 (24.76–25.19) | 24.00 |
| proto-scalar | buffa | ingest | 221.42 (214.03–263.33) | 112.00 |
| proto-scalar | buffa | ingest-typed | 203.36 (203.15–203.39) | 80.00 |
| proto-scalar | buffa | interpret | 29.14 (28.45–29.61) | 2.00 |
| proto-scalar | buffa | peak | 4.14 (4.12–4.14) | 1.00 |
| proto-scalar | buffa | ingest-generated | 120.47 (119.05–124.10) | 80.00 |
| proto-scalar | buffa | read-generated-aot | 16.25 (15.96–16.67) | 0.00 |
| proto-scalar | buffa | interpret-generated | 18.17 (18.16–18.29) | 2.00 |
| proto-scalar | buffa | peak-generated | 4.31 (4.16–4.53) | 1.00 |
| proto-scalar | buffa | serialize | 28.96 (27.09–1681.40) | 24.00 |
| proto-scalar | generated-owned | ingest | 39.46 (39.37–41.67) | 88.00 |
| proto-scalar | generated-owned | read-aot | 1.83 (1.82–1.85) | 0.00 |
| proto-scalar | generated-owned | interpret | 2.32 (2.32–2.33) | 2.00 |
| proto-scalar | generated-owned | peak | 1.96 (1.92–1.98) | 1.00 |
| proto-scalar | generated-owned | serialize | 33.71 (33.68–33.99) | 24.00 |
| proto-scalar | generated-owned | project-aot | 14.54 (13.59–150.32) | 0.00 |
| proto-scalar | generated-owned | project-interpret | 15.92 (14.91–3101.60) | 2.00 |
| proto-scalar | generated-owned | project-peak | 15.62 (15.59–15.76) | 1.00 |
| proto-bytes | stock | ingest | 29436.76 (27480.53–72264.70) | 65699.00 |
| proto-bytes | stock | read-aot | 4.54 (4.49–104.59) | 0.00 |
| proto-bytes | stock | interpret | 6.13 (6.02–6.15) | 2.00 |
| proto-bytes | stock | peak | 4.97 (4.97–5.00) | 1.00 |
| proto-bytes | stock | serialize | 7425.26 (6958.62–7782.37) | 65599.00 |
| proto-bytes | stock-aliased | ingest | 86.21 (86.08–86.56) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 4.11 (4.11–4.13) | 0.00 |
| proto-bytes | stock-aliased | interpret | 5.65 (5.64–5.72) | 2.00 |
| proto-bytes | stock-aliased | peak | 4.66 (4.64–4.99) | 1.00 |
| proto-bytes | stock-aliased | serialize | 8357.89 (7029.09–50591.33) | 65599.00 |
| proto-bytes | owned | ingest | 15196.31 (7976.33–31331.98) | 65995.00 |
| proto-bytes | owned | interpret | 28.75 (28.54–29.06) | 2.00 |
| proto-bytes | owned | peak | 7.02 (7.01–7.77) | 1.00 |
| proto-bytes | owned | ingest-generated | 8344.42 (7959.72–42971.21) | 65995.00 |
| proto-bytes | owned | read-generated-aot | 19.56 (19.52–19.56) | 0.00 |
| proto-bytes | owned | interpret-generated | 22.62 (21.76–22.68) | 2.00 |
| proto-bytes | owned | peak-generated | 6.71 (6.70–6.80) | 1.00 |
| proto-bytes | owned | serialize | 7028.87 (6667.29–34678.11) | 65615.00 |
| proto-bytes | buffa | ingest | 270.94 (269.43–286.44) | 160.00 |
| proto-bytes | buffa | interpret | 31.72 (31.64–31.99) | 2.00 |
| proto-bytes | buffa | peak | 8.07 (8.03–8.82) | 1.00 |
| proto-bytes | buffa | ingest-generated | 202.80 (201.55–215.60) | 160.00 |
| proto-bytes | buffa | read-generated-aot | 14.66 (14.64–14.69) | 0.00 |
| proto-bytes | buffa | interpret-generated | 16.27 (16.22–16.41) | 2.00 |
| proto-bytes | buffa | peak-generated | 9.30 (9.26–9.62) | 1.00 |
| proto-bytes | buffa | serialize | 7219.36 (7164.51–7891.48) | 65567.00 |
| proto-bytes | generated-owned | ingest | 7277.82 (7239.29–42399.58) | 65651.00 |
| proto-bytes | generated-owned | read-aot | 2.23 (2.14–2.24) | 0.00 |
| proto-bytes | generated-owned | interpret | 2.88 (2.87–2.94) | 2.00 |
| proto-bytes | generated-owned | peak | 2.77 (2.75–2.81) | 1.00 |
| proto-bytes | generated-owned | serialize | 8932.89 (7815.51–43790.76) | 65583.00 |
| timestamp-json | stock | ingest | 4819.61 (4787.56–4891.74) | 6439.75 |
| timestamp-json | owned | ingest | 1960.00 (1922.55–1972.22) | 3655.75 |
| timestamp-json | owned | ingest-typed | 1929.75 (1912.30–1930.08) | 3639.75 |
| timestamp-json | owned | ingest-generated | 1783.90 (1777.75–1803.05) | 3639.75 |
| timestamp-json | buffa | ingest | 917.88 (917.74–921.33) | 720.00 |
| timestamp-json | buffa | ingest-typed | 932.07 (930.85–953.11) | 720.00 |
| timestamp-json | buffa | ingest-generated | 770.56 (759.40–793.62) | 720.00 |
| mysql-int4 | stock | ingest | 59.49 (59.40–59.83) | 16.00 |
| mysql-int4 | stock | read-aot | 1.83 (1.82–1.83) | 0.00 |
| mysql-int4 | stock | interpret | 3.34 (3.33–3.35) | 2.00 |
| mysql-int4 | stock | peak | 1.69 (1.69–1.70) | 1.00 |
| mysql-int4 | static | ingest | 147.95 (144.39–1130.04) | 80.00 |
| mysql-int4 | static | ingest-typed | 141.71 (141.27–143.20) | 80.00 |
| mysql-int4 | static | interpret | 21.59 (21.40–22.33) | 2.00 |
| mysql-int4 | static | peak | 4.06 (4.05–4.07) | 1.00 |
| postgres-int4 | stock | ingest | 39.06 (38.89–39.24) | 16.00 |
| postgres-int4 | stock | read-aot | 2.07 (2.07–2.09) | 0.00 |
| postgres-int4 | stock | interpret | 3.29 (3.28–3.34) | 2.00 |
| postgres-int4 | stock | peak | 1.69 (1.68–1.71) | 1.00 |
| postgres-int4 | static | ingest | 130.60 (128.93–132.34) | 80.00 |
| postgres-int4 | static | ingest-typed | 129.63 (129.00–130.11) | 80.00 |
| postgres-int4 | static | interpret | 21.36 (21.35–21.46) | 2.00 |
| postgres-int4 | static | peak | 4.05 (4.05–4.05) | 1.00 |
| proto-mixed | stock | ingest | 1308.50 (1290.76–1350.51) | 1776.75 |
| proto-mixed | stock | read-aot | 11.72 (11.57–12.03) | 0.00 |
| proto-mixed | stock | interpret-boundary | 12.65 (12.64–12.85) | 2.00 |
| proto-mixed | stock | peak-boundary | 12.97 (12.90–13.99) | 1.00 |
| proto-mixed | stock | serialize | 909.20 (909.00–951.05) | 436.50 |
| proto-mixed | owned | ingest | 7176.00 (7088.47–7185.02) | 2976.00 |
| proto-mixed | owned | ingest-typed | 7022.43 (7017.22–7045.90) | 2976.00 |
| proto-mixed | owned | interpret | 106.96 (106.47–111.04) | 2.00 |
| proto-mixed | owned | peak | 15.58 (15.23–15.80) | 1.00 |
| proto-mixed | owned | ingest-generated | 6765.50 (6742.21–6770.69) | 2976.00 |
| proto-mixed | owned | read-generated-aot | 69.23 (69.07–69.30) | 0.00 |
| proto-mixed | owned | interpret-generated | 73.69 (72.66–796.64) | 2.00 |
| proto-mixed | owned | peak-generated | 14.47 (14.47–14.58) | 1.00 |
| proto-mixed | owned | serialize | 5889.59 (5877.04–5947.27) | 556.00 |
| proto-mixed | buffa | ingest | 1728.58 (1700.17–1729.76) | 365.50 |
| proto-mixed | buffa | ingest-typed | 1704.87 (1693.54–1710.21) | 365.50 |
| proto-mixed | buffa | interpret | 100.18 (99.62–100.62) | 2.00 |
| proto-mixed | buffa | peak | 22.84 (22.83–23.31) | 1.00 |
| proto-mixed | buffa | ingest-generated | 1422.32 (1416.17–1538.01) | 261.50 |
| proto-mixed | buffa | read-generated-aot | 40.85 (40.59–41.21) | 0.00 |
| proto-mixed | buffa | interpret-generated | 42.07 (41.89–42.39) | 2.00 |
| proto-mixed | buffa | peak-generated | 24.20 (24.11–24.32) | 1.00 |
| proto-mixed | buffa | serialize | 42.30 (42.29–46.47) | 176.50 |
| proto-mixed | generated-owned | ingest | 1403.62 (1399.26–1407.37) | 1800.50 |
| proto-mixed | generated-owned | read-aot | 8.90 (8.89–9.88) | 0.00 |
| proto-mixed | generated-owned | interpret | 7.04 (6.95–7.21) | 2.00 |
| proto-mixed | generated-owned | peak | 6.77 (6.72–6.85) | 1.00 |
| proto-mixed | generated-owned | serialize | 1323.59 (1289.08–2660.52) | 192.50 |
| proto-mixed-full | stock | ingest | 1358.34 (1338.43–1365.86) | 1776.75 |
| proto-mixed-full | owned | ingest | 10327.76 (10236.09–10341.69) | 2976.00 |
| proto-mixed-full | owned | ingest-generated | 7317.76 (7304.65–7391.68) | 2976.00 |
| proto-mixed-full | buffa | ingest | 4053.64 (4023.33–4063.68) | 1221.75 |
| proto-mixed-full | buffa | ingest-generated | 2601.89 (2556.52–2746.26) | 1221.75 |
| proto-mixed-full | generated-owned | ingest | 1475.91 (1462.99–1487.38) | 1800.50 |

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
