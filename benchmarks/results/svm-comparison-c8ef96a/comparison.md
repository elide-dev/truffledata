# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 67.01 (66.44–67.94) | 136.00 |
| proto-scalar | stock | read-aot | 1.42 (1.31–1.63) | 0.00 |
| proto-scalar | stock | interpret | 1.82 (1.81–1.83) | 2.00 |
| proto-scalar | stock | peak | 1.09 (1.09–1.14) | 1.00 |
| proto-scalar | owned | ingest | 78.93 (78.41–1502.90) | 88.00 |
| proto-scalar | owned | ingest-typed | 77.71 (77.45–78.56) | 56.00 |
| proto-scalar | owned | interpret | 21.81 (21.27–22.45) | 2.00 |
| proto-scalar | owned | peak | 1.82 (1.79–1.86) | 1.00 |
| proto-scalar | owned | ingest-generated | 29.82 (29.40–29.93) | 56.00 |
| proto-scalar | owned | read-generated-aot | 9.41 (9.40–9.66) | 0.00 |
| proto-scalar | owned | interpret-generated | 10.92 (10.71–10.95) | 2.00 |
| proto-scalar | owned | peak-generated | 1.82 (1.81–3.57) | 1.00 |
| proto-scalar | buffa | ingest | 231.69 (231.29–233.98) | 232.00 |
| proto-scalar | buffa | ingest-typed | 276.37 (239.30–304.72) | 200.00 |
| proto-scalar | buffa | interpret | 15.95 (15.84–16.12) | 2.00 |
| proto-scalar | buffa | peak | 2.08 (2.02–2.39) | 1.00 |
| proto-scalar | buffa | ingest-generated | 161.51 (160.71–164.28) | 200.00 |
| proto-scalar | buffa | read-generated-aot | 10.02 (9.84–12.23) | 0.00 |
| proto-scalar | buffa | interpret-generated | 11.38 (11.28–12.10) | 2.00 |
| proto-scalar | buffa | peak-generated | 2.08 (2.05–2.31) | 1.00 |
| proto-bytes | stock | ingest | 19339.80 (19289.80–19902.76) | 65699.00 |
| proto-bytes | stock | read-aot | 2.81 (2.75–2.82) | 0.00 |
| proto-bytes | stock | interpret | 4.25 (4.22–4.60) | 2.00 |
| proto-bytes | stock | peak | 3.20 (3.18–3.24) | 1.00 |
| proto-bytes | stock-aliased | ingest | 51.13 (50.57–60.87) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 2.85 (2.74–3.53) | 0.00 |
| proto-bytes | stock-aliased | interpret | 4.07 (3.97–4.20) | 2.00 |
| proto-bytes | stock-aliased | peak | 2.93 (2.85–3.13) | 1.00 |
| proto-bytes | owned | ingest | 10550.00 (9345.45–38759.12) | 66035.00 |
| proto-bytes | owned | interpret | 17.54 (17.31–20.49) | 2.00 |
| proto-bytes | owned | peak | 4.80 (3.91–6.32) | 1.00 |
| proto-bytes | owned | ingest-generated | 157193.44 (42683.32–229000.81) | 66035.00 |
| proto-bytes | owned | read-generated-aot | 14.14 (13.40–14.32) | 0.00 |
| proto-bytes | owned | interpret-generated | 15.20 (15.06–16.20) | 2.00 |
| proto-bytes | owned | peak-generated | 3.81 (3.81–3.85) | 1.00 |
| proto-bytes | buffa | ingest | 252.36 (245.63–361.18) | 296.00 |
| proto-bytes | buffa | interpret | 18.51 (18.35–20.09) | 2.00 |
| proto-bytes | buffa | peak | 3.87 (3.80–4.21) | 1.00 |
| proto-bytes | buffa | ingest-generated | 170.82 (170.44–173.86) | 296.00 |
| proto-bytes | buffa | read-generated-aot | 7.04 (7.01–7.14) | 0.00 |
| proto-bytes | buffa | interpret-generated | 9.10 (8.68–9.70) | 2.00 |
| proto-bytes | buffa | peak-generated | 4.19 (4.16–4.31) | 1.00 |
| timestamp-json | stock | ingest | 3389.41 (3226.50–3479.04) | 6439.75 |
| timestamp-json | owned | ingest | 1293.85 (1292.04–1298.42) | 3703.75 |
| timestamp-json | owned | ingest-typed | 1326.31 (1317.56–1352.49) | 3671.75 |
| timestamp-json | owned | ingest-generated | 1222.76 (1191.14–1305.36) | 3671.75 |
| timestamp-json | buffa | ingest | 578.03 (572.15–814.80) | 712.00 |
| timestamp-json | buffa | ingest-typed | 558.41 (549.52–586.32) | 712.00 |
| timestamp-json | buffa | ingest-generated | 471.51 (467.90–475.03) | 712.00 |
| mysql-int4 | stock | ingest | 33.91 (32.71–34.21) | 16.00 |
| mysql-int4 | stock | read-aot | 1.12 (1.08–1.13) | 0.00 |
| mysql-int4 | stock | interpret | 2.57 (1.90–2.80) | 2.00 |
| mysql-int4 | stock | peak | 0.99 (0.98–0.99) | 1.00 |
| mysql-int4 | static | ingest | 101.29 (100.94–2185.12) | 128.00 |
| mysql-int4 | static | ingest-typed | 103.12 (102.37–105.83) | 128.00 |
| mysql-int4 | static | interpret | 13.50 (13.48–14.21) | 2.00 |
| mysql-int4 | static | peak | 3.62 (2.18–10.80) | 1.00 |
| postgres-int4 | stock | ingest | 23.88 (23.80–25.48) | 16.00 |
| postgres-int4 | stock | read-aot | 1.27 (1.18–1.51) | 0.00 |
| postgres-int4 | stock | interpret | 1.96 (1.91–2.02) | 2.00 |
| postgres-int4 | stock | peak | 0.99 (0.98–1.05) | 1.00 |
| postgres-int4 | static | ingest | 95.29 (93.76–96.13) | 128.00 |
| postgres-int4 | static | ingest-typed | 94.90 (93.01–94.93) | 128.00 |
| postgres-int4 | static | interpret | 13.64 (13.54–14.80) | 2.00 |
| postgres-int4 | static | peak | 2.21 (2.21–2.22) | 1.00 |
| proto-mixed | stock | ingest | 897.82 (879.19–906.76) | 1776.75 |
| proto-mixed | stock | read-aot | 6.89 (6.75–6.99) | 0.00 |
| proto-mixed | stock | interpret-boundary | 7.66 (7.61–8.74) | 2.00 |
| proto-mixed | stock | peak-boundary | 7.32 (6.99–7.86) | 1.00 |
| proto-mixed | owned | ingest | 8145.12 (8091.04–8664.76) | 3339.50 |
| proto-mixed | owned | ingest-typed | 8740.24 (7975.99–11804.48) | 3339.50 |
| proto-mixed | owned | interpret | 63.90 (62.37–63.92) | 2.00 |
| proto-mixed | owned | peak | 8.92 (8.86–9.50) | 1.00 |
| proto-mixed | owned | ingest-generated | 8962.61 (8189.03–13924.43) | 3339.50 |
| proto-mixed | owned | read-generated-aot | 47.94 (47.91–49.29) | 0.00 |
| proto-mixed | owned | interpret-generated | 50.47 (49.88–65.36) | 2.00 |
| proto-mixed | owned | peak-generated | 9.14 (8.63–11.16) | 1.00 |
| proto-mixed | buffa | ingest | 3451.98 (3370.80–4092.06) | 1751.75 |
| proto-mixed | buffa | ingest-typed | 3423.76 (3346.80–3570.39) | 1751.75 |
| proto-mixed | buffa | interpret | 51.79 (51.78–53.53) | 2.00 |
| proto-mixed | buffa | peak | 10.01 (9.72–10.01) | 1.00 |
| proto-mixed | buffa | ingest-generated | 3198.47 (3196.30–5654.29) | 1751.75 |
| proto-mixed | buffa | read-generated-aot | 22.86 (19.85–31.11) | 0.00 |
| proto-mixed | buffa | interpret-generated | 22.58 (22.48–28.96) | 2.00 |
| proto-mixed | buffa | peak-generated | 9.65 (9.34–14.41) | 1.00 |
| proto-mixed-full | stock | ingest | 919.75 (917.96–951.85) | 1776.75 |
| proto-mixed-full | owned | ingest | 11036.35 (9398.78–16396.05) | 3339.50 |
| proto-mixed-full | buffa | ingest | 4336.52 (4322.88–4741.98) | 1878.25 |

Stock read-aot is the native-image host baseline. Schema 4 adds stock guest tiers; mixed protobuf uses explicitly labeled boundary projections because its collection getters fail the SVM JIT blocklist.
Ingest includes parsing/materialization and an uncached projected read. Setup is excluded.
ingest-typed uses primitive readLong projections; ingest retains generic read projections.
Generated stages use schema-bound generated getters; read-generated-aot excludes guest calls. Original library-dispatch stages remain.
Buffa binary input is already in unmanaged memory; owned engines start with Java byte arrays.
Buffa results are scoped borrowed views; stock-aliased borrows immutable ByteString input; stock/owned copy payloads.
The direct stock AOT baseline excludes guest interop and contract enforcement.
MySQL/Postgres measure driver INT4 decoder functions and retained scalar rows, not JDBC queries, transport, or servers.
Java heap counters exclude native allocations and compiler/background threads. Native allocation is not reported as zero.
Shared execution hardware and short samples make these diagnostic comparisons, not performance guarantees.

Binary SHA-256: `7072749dbf609bb5341c5688e7f0096f0d35adeb051c994df917e1b109418d78`
Revision: `c8ef96a2b4bef30226999056341eb30735e6a6bc`
