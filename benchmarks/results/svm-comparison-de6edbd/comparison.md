# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 161.97 (102.51–205.90) | 136.00 |
| proto-scalar | stock | read-aot | 2.84 (2.27–3.09) | 0.00 |
| proto-scalar | owned | ingest | 148.13 (115.28–202.72) | 88.00 |
| proto-scalar | owned | ingest-typed | 209.28 (149.91–228.93) | 56.00 |
| proto-scalar | owned | interpret | 44.79 (42.92–45.44) | 2.00 |
| proto-scalar | owned | peak | 4.59 (4.29–6.47) | 1.00 |
| proto-scalar | buffa | ingest | 719.65 (495.31–812.44) | 312.00 |
| proto-scalar | buffa | ingest-typed | 549.74 (525.25–823.01) | 312.00 |
| proto-scalar | buffa | interpret | 37.27 (33.84–46.26) | 2.00 |
| proto-scalar | buffa | peak | 7.80 (5.43–7.88) | 1.00 |
| proto-bytes | stock | ingest | 64666.93 (63177.40–77960.90) | 65699.00 |
| proto-bytes | stock | read-aot | 4.99 (4.26–5.26) | 0.00 |
| proto-bytes | stock-aliased | ingest | 107.43 (84.77–155.56) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 3.91 (3.86–4.00) | 0.00 |
| proto-bytes | owned | ingest | 33369.32 (14623.59–39712.07) | 66035.00 |
| proto-bytes | owned | interpret | 33.00 (27.67–35.65) | 2.00 |
| proto-bytes | owned | peak | 7.43 (7.02–9.64) | 1.00 |
| proto-bytes | buffa | ingest | 540.58 (398.01–850.51) | 304.00 |
| proto-bytes | buffa | interpret | 41.94 (26.50–47.21) | 2.00 |
| proto-bytes | buffa | peak | 6.75 (5.94–7.71) | 1.00 |
| timestamp-json | stock | ingest | 7207.53 (5959.52–7358.89) | 6439.75 |
| timestamp-json | owned | ingest | 3084.22 (2612.82–3516.77) | 3703.75 |
| timestamp-json | owned | ingest-typed | 2649.00 (2406.95–3443.05) | 3671.75 |
| timestamp-json | buffa | ingest | 1162.91 (978.28–1363.38) | 720.00 |
| timestamp-json | buffa | ingest-typed | 1154.74 (832.48–1259.15) | 720.00 |
| mysql-int4 | stock | ingest | 69.05 (66.58–109.41) | 16.00 |
| mysql-int4 | stock | read-aot | 1.76 (1.69–2.05) | 0.00 |
| mysql-int4 | static | ingest | 235.17 (214.03–285.31) | 160.00 |
| mysql-int4 | static | ingest-typed | 192.27 (183.40–307.15) | 160.00 |
| mysql-int4 | static | interpret | 24.00 (19.79–25.47) | 2.00 |
| mysql-int4 | static | peak | 7.18 (4.62–7.39) | 1.00 |
| postgres-int4 | stock | ingest | 61.15 (54.67–79.06) | 16.00 |
| postgres-int4 | stock | read-aot | 1.72 (1.72–3.09) | 0.00 |
| postgres-int4 | static | ingest | 273.22 (135.93–276.21) | 160.00 |
| postgres-int4 | static | ingest-typed | 267.20 (207.58–280.31) | 160.00 |
| postgres-int4 | static | interpret | 22.89 (21.61–33.90) | 2.00 |
| postgres-int4 | static | peak | 5.06 (4.77–6.51) | 1.00 |
| proto-mixed | stock | ingest | 1332.65 (1288.48–2437.49) | 1776.75 |
| proto-mixed | stock | read-aot | 8.31 (8.05–12.42) | 0.00 |
| proto-mixed | owned | ingest | 22390.19 (21195.87–24956.51) | 3339.50 |
| proto-mixed | owned | ingest-typed | 13863.91 (13363.03–20229.68) | 3339.50 |
| proto-mixed | owned | interpret | 104.44 (100.39–105.29) | 2.00 |
| proto-mixed | owned | peak | 16.34 (13.08–23.86) | 1.00 |
| proto-mixed | buffa | ingest | 8524.64 (7345.48–11586.32) | 2195.25 |
| proto-mixed | buffa | ingest-typed | 9357.76 (8229.46–9781.60) | 2195.25 |
| proto-mixed | buffa | interpret | 108.26 (96.30–112.90) | 2.00 |
| proto-mixed | buffa | peak | 12.17 (11.23–20.05) | 1.00 |

Stock read-aot is the native-image host baseline; it has no Truffle interpreter/JIT tier.
Ingest includes parsing/materialization and an uncached projected read. Setup is excluded.
ingest-typed uses primitive readLong projections; ingest retains generic read projections.
Buffa binary input is already in unmanaged memory; owned engines start with Java byte arrays.
Buffa results are scoped borrowed views; stock-aliased borrows immutable ByteString input; stock/owned copy payloads.
The direct stock AOT baseline excludes guest interop and contract enforcement.
MySQL/Postgres measure driver INT4 decoder functions and retained scalar rows, not JDBC queries, transport, or servers.
Java heap counters exclude native allocations and compiler/background threads. Native allocation is not reported as zero.
Shared execution hardware and short samples make these diagnostic comparisons, not performance guarantees.

Binary SHA-256: `98faa8de33f8b291a51ab63f3996e838f393285ec9e1d33319f1dc17ee6aa85d`
Revision: `de6edbd75b9da1239ba8b2ee808d36af04317fd2`
