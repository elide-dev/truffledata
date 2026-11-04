# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 73.93 (70.33–98.15) | 136.00 |
| proto-scalar | stock | read-aot | 1.38 (1.33–2.06) | 0.00 |
| proto-scalar | stock | interpret | 2.48 (1.97–3.31) | 2.00 |
| proto-scalar | stock | peak | 1.16 (1.15–1.39) | 1.00 |
| proto-scalar | owned | ingest | 118.24 (84.19–136.99) | 88.00 |
| proto-scalar | owned | ingest-typed | 85.21 (78.85–89.72) | 56.00 |
| proto-scalar | owned | interpret | 23.88 (23.18–36.94) | 2.00 |
| proto-scalar | owned | peak | 2.15 (2.02–4.02) | 1.00 |
| proto-scalar | buffa | ingest | 522.40 (433.49–586.80) | 320.00 |
| proto-scalar | buffa | ingest-typed | 568.59 (375.90–584.01) | 320.00 |
| proto-scalar | buffa | interpret | 25.23 (18.94–29.10) | 2.00 |
| proto-scalar | buffa | peak | 4.87 (4.36–5.60) | 1.00 |
| proto-bytes | stock | ingest | 23225.69 (21035.39–28771.85) | 65699.00 |
| proto-bytes | stock | read-aot | 3.09 (3.05–3.34) | 0.00 |
| proto-bytes | stock | interpret | 4.98 (4.46–5.63) | 2.00 |
| proto-bytes | stock | peak | 3.82 (3.80–3.92) | 1.00 |
| proto-bytes | stock-aliased | ingest | 74.55 (61.35–78.80) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 3.11 (2.76–3.51) | 0.00 |
| proto-bytes | stock-aliased | interpret | 5.17 (4.98–8.36) | 2.00 |
| proto-bytes | stock-aliased | peak | 3.55 (3.53–4.28) | 1.00 |
| proto-bytes | owned | ingest | 41751.47 (12441.80–84379.33) | 66035.00 |
| proto-bytes | owned | interpret | 21.69 (17.63–31.24) | 2.00 |
| proto-bytes | owned | peak | 4.14 (3.80–5.01) | 1.00 |
| proto-bytes | buffa | ingest | 380.40 (280.38–508.41) | 312.00 |
| proto-bytes | buffa | interpret | 30.19 (19.48–32.97) | 2.00 |
| proto-bytes | buffa | peak | 4.10 (4.03–8.57) | 1.00 |
| timestamp-json | stock | ingest | 3590.83 (3393.24–6251.54) | 6439.75 |
| timestamp-json | owned | ingest | 1610.56 (1546.99–1810.18) | 3703.75 |
| timestamp-json | owned | ingest-typed | 1482.47 (1372.26–1895.93) | 3671.75 |
| timestamp-json | buffa | ingest | 580.16 (574.11–1069.47) | 728.00 |
| timestamp-json | buffa | ingest-typed | 779.68 (689.05–10690.72) | 728.00 |
| mysql-int4 | stock | ingest | 43.05 (35.92–45.88) | 16.00 |
| mysql-int4 | stock | read-aot | 1.49 (1.37–1.75) | 0.00 |
| mysql-int4 | stock | interpret | 1.96 (1.96–2.31) | 2.00 |
| mysql-int4 | stock | peak | 1.09 (1.04–1.48) | 1.00 |
| mysql-int4 | static | ingest | 113.86 (110.22–128.93) | 160.00 |
| mysql-int4 | static | ingest-typed | 116.59 (113.17–143.63) | 160.00 |
| mysql-int4 | static | interpret | 14.46 (13.39–14.71) | 2.00 |
| mysql-int4 | static | peak | 2.55 (2.43–3.25) | 1.00 |
| postgres-int4 | stock | ingest | 27.16 (26.26–62.28) | 16.00 |
| postgres-int4 | stock | read-aot | 1.44 (1.23–1.91) | 0.00 |
| postgres-int4 | stock | interpret | 2.14 (2.07–2.58) | 2.00 |
| postgres-int4 | stock | peak | 1.47 (1.42–1.53) | 1.00 |
| postgres-int4 | static | ingest | 121.88 (113.83–135.75) | 160.00 |
| postgres-int4 | static | ingest-typed | 107.56 (106.07–112.11) | 160.00 |
| postgres-int4 | static | interpret | 17.10 (15.12–24.08) | 2.00 |
| postgres-int4 | static | peak | 2.65 (2.23–2.80) | 1.00 |
| proto-mixed | stock | ingest | 979.26 (908.89–1003.58) | 1776.75 |
| proto-mixed | stock | read-aot | 9.98 (7.91–10.71) | 0.00 |
| proto-mixed | stock | interpret-boundary | 9.69 (8.05–10.53) | 2.00 |
| proto-mixed | stock | peak-boundary | 9.53 (7.61–9.64) | 1.00 |
| proto-mixed | owned | ingest | 10921.43 (9095.05–15918.84) | 3339.50 |
| proto-mixed | owned | ingest-typed | 9904.27 (9268.88–10074.31) | 3339.50 |
| proto-mixed | owned | interpret | 67.84 (65.80–72.81) | 2.00 |
| proto-mixed | owned | peak | 13.10 (9.90–13.89) | 1.00 |
| proto-mixed | buffa | ingest | 6609.91 (4204.74–9413.80) | 1767.75 |
| proto-mixed | buffa | ingest-typed | 3995.72 (3500.07–4764.23) | 1767.75 |
| proto-mixed | buffa | interpret | 53.91 (52.35–88.93) | 2.00 |
| proto-mixed | buffa | peak | 12.22 (11.74–12.41) | 1.00 |
| proto-mixed-full | stock | ingest | 1095.13 (1010.07–1300.81) | 1776.75 |
| proto-mixed-full | owned | ingest | 14563.34 (12687.66–18214.01) | 3339.50 |
| proto-mixed-full | buffa | ingest | 6677.98 (4628.37–9160.16) | 1894.25 |

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
Revision: `700ef7402e2fe88dff2b7141d5a5b78325d3d355`
