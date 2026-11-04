# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 97.06 (96.74–149.56) | 136.00 |
| proto-scalar | stock | read-aot | 1.94 (1.81–2.13) | 0.00 |
| proto-scalar | stock | interpret | 4.51 (2.73–5.15) | 2.00 |
| proto-scalar | stock | peak | 1.76 (1.54–3.11) | 1.00 |
| proto-scalar | owned | ingest | 204.58 (115.54–206.18) | 88.00 |
| proto-scalar | owned | ingest-typed | 163.76 (128.54–223.10) | 56.00 |
| proto-scalar | owned | interpret | 31.99 (31.60–62.64) | 2.00 |
| proto-scalar | owned | peak | 5.18 (4.48–5.32) | 1.00 |
| proto-scalar | buffa | ingest | 510.69 (498.58–525.69) | 312.00 |
| proto-scalar | buffa | ingest-typed | 991.05 (900.83–993.10) | 312.00 |
| proto-scalar | buffa | interpret | 39.92 (30.05–49.70) | 2.00 |
| proto-scalar | buffa | peak | 5.43 (5.29–8.54) | 1.00 |
| proto-bytes | stock | ingest | 70091.82 (67120.06–79451.61) | 65699.00 |
| proto-bytes | stock | read-aot | 4.94 (4.57–5.27) | 0.00 |
| proto-bytes | stock | interpret | 5.36 (3.92–6.88) | 2.00 |
| proto-bytes | stock | peak | 6.56 (4.40–7.59) | 1.00 |
| proto-bytes | stock-aliased | ingest | 129.20 (112.88–150.38) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 3.51 (3.29–5.41) | 0.00 |
| proto-bytes | stock-aliased | interpret | 4.04 (3.82–5.15) | 2.00 |
| proto-bytes | stock-aliased | peak | 4.83 (4.40–5.97) | 1.00 |
| proto-bytes | owned | ingest | 26366.85 (12851.11–26945.12) | 66035.00 |
| proto-bytes | owned | interpret | 43.91 (42.12–45.37) | 2.00 |
| proto-bytes | owned | peak | 10.02 (6.29–10.38) | 1.00 |
| proto-bytes | buffa | ingest | 387.56 (381.62–509.04) | 304.00 |
| proto-bytes | buffa | interpret | 30.97 (25.87–41.66) | 2.00 |
| proto-bytes | buffa | peak | 6.80 (5.45–7.19) | 1.00 |
| timestamp-json | stock | ingest | 5471.23 (5447.21–7744.10) | 6439.75 |
| timestamp-json | owned | ingest | 3517.22 (2393.50–4079.82) | 3703.75 |
| timestamp-json | owned | ingest-typed | 3455.02 (2656.82–3512.80) | 3671.75 |
| timestamp-json | buffa | ingest | 1034.73 (811.90–1628.68) | 720.00 |
| timestamp-json | buffa | ingest-typed | 1411.84 (1183.86–1425.55) | 720.00 |
| mysql-int4 | stock | ingest | 95.46 (60.38–104.18) | 16.00 |
| mysql-int4 | stock | read-aot | 2.43 (2.04–3.08) | 0.00 |
| mysql-int4 | stock | interpret | 2.88 (2.86–4.48) | 2.00 |
| mysql-int4 | stock | peak | 2.38 (1.69–2.53) | 1.00 |
| mysql-int4 | static | ingest | 194.76 (172.49–372.64) | 160.00 |
| mysql-int4 | static | ingest-typed | 248.81 (192.10–311.25) | 160.00 |
| mysql-int4 | static | interpret | 19.51 (17.87–20.51) | 2.00 |
| mysql-int4 | static | peak | 3.60 (3.59–4.62) | 1.00 |
| postgres-int4 | stock | ingest | 49.71 (38.57–80.18) | 16.00 |
| postgres-int4 | stock | read-aot | 2.82 (2.57–3.65) | 0.00 |
| postgres-int4 | stock | interpret | 4.05 (2.87–4.71) | 2.00 |
| postgres-int4 | stock | peak | 2.47 (1.35–2.53) | 1.00 |
| postgres-int4 | static | ingest | 218.31 (144.93–326.86) | 160.00 |
| postgres-int4 | static | ingest-typed | 264.47 (139.61–313.39) | 160.00 |
| postgres-int4 | static | interpret | 17.94 (17.70–29.81) | 2.00 |
| postgres-int4 | static | peak | 5.39 (3.42–6.12) | 1.00 |
| proto-mixed | stock | ingest | 2021.71 (1289.52–2136.77) | 1776.75 |
| proto-mixed | stock | read-aot | 8.95 (7.29–10.36) | 0.00 |
| proto-mixed | stock | interpret-boundary | 8.94 (8.92–9.16) | 2.00 |
| proto-mixed | stock | peak-boundary | 9.94 (8.64–15.93) | 1.00 |
| proto-mixed | owned | ingest | 20170.43 (14068.02–24258.32) | 3339.50 |
| proto-mixed | owned | ingest-typed | 23394.24 (18944.00–24309.04) | 3339.50 |
| proto-mixed | owned | interpret | 119.83 (91.05–153.65) | 2.00 |
| proto-mixed | owned | peak | 18.28 (12.72–22.44) | 1.00 |
| proto-mixed | buffa | ingest | 6246.94 (5837.41–10309.79) | 2037.50 |
| proto-mixed | buffa | ingest-typed | 6783.65 (5776.89–10270.67) | 2037.50 |
| proto-mixed | buffa | interpret | 130.11 (94.56–131.41) | 2.00 |
| proto-mixed | buffa | peak | 16.97 (15.72–26.10) | 1.00 |

Stock read-aot is the native-image host baseline. Schema 4 adds stock guest tiers; mixed protobuf uses explicitly labeled boundary projections because its collection getters fail the SVM JIT blocklist.
Ingest includes parsing/materialization and an uncached projected read. Setup is excluded.
ingest-typed uses primitive readLong projections; ingest retains generic read projections.
Buffa binary input is already in unmanaged memory; owned engines start with Java byte arrays.
Buffa results are scoped borrowed views; stock-aliased borrows immutable ByteString input; stock/owned copy payloads.
The direct stock AOT baseline excludes guest interop and contract enforcement.
MySQL/Postgres measure driver INT4 decoder functions and retained scalar rows, not JDBC queries, transport, or servers.
Java heap counters exclude native allocations and compiler/background threads. Native allocation is not reported as zero.
Shared execution hardware and short samples make these diagnostic comparisons, not performance guarantees.

Binary SHA-256: `1d0146107a6011f45bec7fb37d603fb3af1a44388f10665a799d84c20cc240a8`
Revision: `e6718448abc2b0a994d9578c2eabaa626c1dadbd`
