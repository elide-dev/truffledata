# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 69.68 (66.08–69.74) | 136.00 |
| proto-scalar | stock | read-aot | 1.30 (1.30–1.37) | 0.00 |
| proto-scalar | stock | interpret | 1.77 (1.77–1.80) | 2.00 |
| proto-scalar | stock | peak | 1.07 (1.06–1.15) | 1.00 |
| proto-scalar | owned | ingest | 79.67 (79.32–85.86) | 88.00 |
| proto-scalar | owned | ingest-typed | 76.13 (75.95–77.32) | 56.00 |
| proto-scalar | owned | interpret | 20.91 (20.79–23.32) | 2.00 |
| proto-scalar | owned | peak | 1.77 (1.76–1.79) | 1.00 |
| proto-scalar | buffa | ingest | 327.77 (312.90–343.67) | 320.00 |
| proto-scalar | buffa | ingest-typed | 315.65 (310.83–317.18) | 320.00 |
| proto-scalar | buffa | interpret | 17.47 (17.39–17.88) | 2.00 |
| proto-scalar | buffa | peak | 3.86 (3.83–3.87) | 1.00 |
| proto-bytes | stock | ingest | 19466.73 (19301.87–43847.30) | 65699.00 |
| proto-bytes | stock | read-aot | 2.85 (2.81–3.06) | 0.00 |
| proto-bytes | stock | interpret | 4.24 (4.17–4.31) | 2.00 |
| proto-bytes | stock | peak | 2.96 (2.94–2.96) | 1.00 |
| proto-bytes | stock-aliased | ingest | 50.24 (49.98–51.43) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 2.77 (2.65–3.50) | 0.00 |
| proto-bytes | stock-aliased | interpret | 3.88 (3.87–3.91) | 2.00 |
| proto-bytes | stock-aliased | peak | 2.79 (2.77–2.81) | 1.00 |
| proto-bytes | owned | ingest | 28868.23 (28022.28–36286.55) | 66035.00 |
| proto-bytes | owned | interpret | 16.52 (16.43–16.78) | 2.00 |
| proto-bytes | owned | peak | 3.72 (3.70–3.84) | 1.00 |
| proto-bytes | buffa | ingest | 242.19 (230.38–248.70) | 312.00 |
| proto-bytes | buffa | interpret | 17.55 (17.37–17.95) | 2.00 |
| proto-bytes | buffa | peak | 3.93 (3.78–4.32) | 1.00 |
| timestamp-json | stock | ingest | 3191.76 (3180.73–3199.32) | 6439.75 |
| timestamp-json | owned | ingest | 1295.61 (1284.13–1486.43) | 3703.75 |
| timestamp-json | owned | ingest-typed | 1274.30 (1270.55–1478.34) | 3671.75 |
| timestamp-json | buffa | ingest | 606.65 (575.29–610.80) | 728.00 |
| timestamp-json | buffa | ingest-typed | 570.86 (570.39–570.90) | 728.00 |
| mysql-int4 | stock | ingest | 31.89 (31.75–32.25) | 16.00 |
| mysql-int4 | stock | read-aot | 1.09 (1.08–1.09) | 0.00 |
| mysql-int4 | stock | interpret | 1.85 (1.84–1.86) | 2.00 |
| mysql-int4 | stock | peak | 0.96 (0.95–0.96) | 1.00 |
| mysql-int4 | static | ingest | 104.60 (100.99–110.70) | 160.00 |
| mysql-int4 | static | ingest-typed | 102.44 (101.21–104.48) | 160.00 |
| mysql-int4 | static | interpret | 12.71 (12.64–12.79) | 2.00 |
| mysql-int4 | static | peak | 2.18 (2.17–2.21) | 1.00 |
| postgres-int4 | stock | ingest | 24.38 (23.52–24.53) | 16.00 |
| postgres-int4 | stock | read-aot | 1.18 (1.16–1.26) | 0.00 |
| postgres-int4 | stock | interpret | 1.87 (1.85–1.90) | 2.00 |
| postgres-int4 | stock | peak | 0.95 (0.95–0.96) | 1.00 |
| postgres-int4 | static | ingest | 96.20 (94.57–101.83) | 160.00 |
| postgres-int4 | static | ingest-typed | 95.58 (94.97–95.70) | 160.00 |
| postgres-int4 | static | interpret | 12.73 (12.62–13.25) | 2.00 |
| postgres-int4 | static | peak | 2.15 (2.14–2.18) | 1.00 |
| proto-mixed | stock | ingest | 857.51 (850.63–975.40) | 1776.75 |
| proto-mixed | stock | read-aot | 6.70 (6.68–7.08) | 0.00 |
| proto-mixed | stock | interpret-boundary | 7.63 (7.50–10.46) | 2.00 |
| proto-mixed | stock | peak-boundary | 7.12 (6.99–7.29) | 1.00 |
| proto-mixed | owned | ingest | 8113.42 (7894.69–48321.86) | 3339.50 |
| proto-mixed | owned | ingest-typed | 7996.68 (7946.42–8138.26) | 3339.50 |
| proto-mixed | owned | interpret | 75.76 (61.50–122.45) | 2.00 |
| proto-mixed | owned | peak | 9.49 (9.47–10.23) | 1.00 |
| proto-mixed | buffa | ingest | 3417.76 (3385.66–3636.36) | 1767.75 |
| proto-mixed | buffa | ingest-typed | 3395.92 (3378.24–3446.83) | 1767.75 |
| proto-mixed | buffa | interpret | 49.20 (48.86–49.21) | 2.00 |
| proto-mixed | buffa | peak | 9.95 (9.85–12.37) | 1.00 |
| proto-mixed-full | stock | ingest | 905.58 (904.36–926.90) | 1776.75 |
| proto-mixed-full | owned | ingest | 10082.94 (9567.04–10357.86) | 3339.50 |
| proto-mixed-full | buffa | ingest | 4337.77 (4335.11–4505.92) | 1894.25 |

Stock read-aot is the native-image host baseline. Schema 4 adds stock guest tiers; mixed protobuf uses explicitly labeled boundary projections because its collection getters fail the SVM JIT blocklist.
Ingest includes parsing/materialization and an uncached projected read. Setup is excluded.
ingest-typed uses primitive readLong projections; ingest retains generic read projections.
Buffa binary input is already in unmanaged memory; owned engines start with Java byte arrays.
Buffa results are scoped borrowed views; stock-aliased borrows immutable ByteString input; stock/owned copy payloads.
The direct stock AOT baseline excludes guest interop and contract enforcement.
MySQL/Postgres measure driver INT4 decoder functions and retained scalar rows, not JDBC queries, transport, or servers.
Java heap counters exclude native allocations and compiler/background threads. Native allocation is not reported as zero.
Shared execution hardware and short samples make these diagnostic comparisons, not performance guarantees.

Binary SHA-256: `c2b90fe39453c0e95e07a39a7c50def500cb3330a4f844ef55ae376c472a3504`
Revision: `f3175df290dcffa15f72861e7ba853a31c8abaf4`
