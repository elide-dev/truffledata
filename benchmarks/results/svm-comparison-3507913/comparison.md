# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 64.17 (61.49–80.76) | 136.00 |
| proto-scalar | stock | read-aot | 1.38 (1.30–1.79) | 0.00 |
| proto-scalar | owned | ingest | 75.93 (74.02–89.52) | 88.00 |
| proto-scalar | owned | ingest-typed | 73.48 (73.34–81.74) | 56.00 |
| proto-scalar | owned | interpret | 24.17 (22.83–28.01) | 2.00 |
| proto-scalar | owned | peak | 2.00 (1.94–3.64) | 1.00 |
| proto-scalar | buffa | ingest | 494.21 (335.75–549.20) | 400.00 |
| proto-scalar | buffa | ingest-typed | 357.60 (347.23–397.32) | 400.00 |
| proto-scalar | buffa | interpret | 16.50 (15.95–25.41) | 2.00 |
| proto-scalar | buffa | peak | 6.06 (4.26–6.68) | 1.00 |
| proto-bytes | stock | ingest | 21338.98 (19882.05–22924.40) | 65699.00 |
| proto-bytes | stock | read-aot | 2.94 (2.88–2.96) | 0.00 |
| proto-bytes | stock-aliased | ingest | 55.29 (53.78–85.75) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 3.30 (2.95–3.34) | 0.00 |
| proto-bytes | owned | ingest | 41294.30 (33918.20–92751.68) | 66051.00 |
| proto-bytes | owned | interpret | 19.97 (17.84–23.25) | 2.00 |
| proto-bytes | owned | peak | 3.98 (3.88–4.10) | 1.00 |
| proto-bytes | buffa | ingest | 284.40 (280.53–286.47) | 392.00 |
| proto-bytes | buffa | interpret | 18.21 (17.28–21.55) | 2.00 |
| proto-bytes | buffa | peak | 4.40 (4.39–4.84) | 1.00 |
| timestamp-json | stock | ingest | 3608.43 (3300.61–3943.63) | 6439.75 |
| timestamp-json | owned | ingest | 1392.32 (1359.96–1413.05) | 3703.75 |
| timestamp-json | owned | ingest-typed | 1432.09 (1396.64–1494.84) | 3671.75 |
| timestamp-json | buffa | ingest | 641.06 (585.92–698.32) | 792.00 |
| timestamp-json | buffa | ingest-typed | 602.18 (590.09–611.92) | 792.00 |
| mysql-int4 | stock | ingest | 34.06 (33.37–62.48) | 16.00 |
| mysql-int4 | stock | read-aot | 1.30 (1.07–1.37) | 0.00 |
| mysql-int4 | static | ingest | 112.62 (100.75–139.08) | 160.00 |
| mysql-int4 | static | ingest-typed | 117.36 (102.32–119.33) | 160.00 |
| mysql-int4 | static | interpret | 14.31 (13.38–16.55) | 2.00 |
| mysql-int4 | static | peak | 2.34 (2.27–3.04) | 1.00 |
| postgres-int4 | stock | ingest | 24.75 (23.84–25.03) | 16.00 |
| postgres-int4 | stock | read-aot | 1.32 (1.04–1.87) | 0.00 |
| postgres-int4 | static | ingest | 92.32 (92.24–92.83) | 160.00 |
| postgres-int4 | static | ingest-typed | 95.42 (94.98–2206.08) | 160.00 |
| postgres-int4 | static | interpret | 14.84 (13.64–17.27) | 2.00 |
| postgres-int4 | static | peak | 2.84 (2.32–2.94) | 1.00 |

Stock read-aot is the native-image host baseline; it has no Truffle interpreter/JIT tier.
Ingest includes parsing/materialization and an uncached projected read. Setup is excluded.
ingest-typed uses primitive readLong projections; ingest retains generic read projections.
Buffa binary input is already in unmanaged memory; owned engines start with Java byte arrays.
Buffa results are scoped borrowed views; stock-aliased borrows immutable ByteString input; stock/owned copy payloads.
The direct stock AOT baseline excludes guest interop and contract enforcement.
MySQL/Postgres measure driver INT4 decoder functions and retained scalar rows, not JDBC queries, transport, or servers.
Java heap counters exclude native allocations and compiler/background threads. Native allocation is not reported as zero.
Shared execution hardware and short samples make these diagnostic comparisons, not performance guarantees.

Binary SHA-256: `ea5254738c95f9f88dd1b0e3ac15420f9a5f8c2e7841ba8d340274b69fb25fd7`
Revision: `350791357787549efc2bd8a170fc3b51d5e6e27a`
