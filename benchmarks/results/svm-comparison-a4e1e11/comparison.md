# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 110.89 (106.45–110.89) | 136.00 |
| proto-scalar | stock | read-aot | 2.05 (1.99–3.50) | 0.00 |
| proto-scalar | stock | interpret | 2.95 (2.85–2.98) | 2.00 |
| proto-scalar | stock | peak | 1.55 (1.55–2.00) | 1.00 |
| proto-scalar | owned | ingest | 142.28 (126.24–217.24) | 88.00 |
| proto-scalar | owned | ingest-typed | 128.25 (126.30–175.33) | 56.00 |
| proto-scalar | owned | interpret | 32.59 (32.18–42.41) | 2.00 |
| proto-scalar | owned | peak | 3.08 (3.06–3.10) | 1.00 |
| proto-scalar | buffa | ingest | 655.86 (502.33–1025.48) | 320.00 |
| proto-scalar | buffa | ingest-typed | 528.51 (510.47–541.27) | 320.00 |
| proto-scalar | buffa | interpret | 28.04 (27.87–28.79) | 2.00 |
| proto-scalar | buffa | peak | 5.40 (5.35–5.48) | 1.00 |
| proto-bytes | stock | ingest | 53875.43 (53253.91–54545.89) | 65699.00 |
| proto-bytes | stock | read-aot | 4.09 (4.07–4.62) | 0.00 |
| proto-bytes | stock | interpret | 5.39 (5.34–5.89) | 2.00 |
| proto-bytes | stock | peak | 4.57 (4.52–7.71) | 1.00 |
| proto-bytes | stock-aliased | ingest | 79.31 (78.33–139.94) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 3.28 (3.27–3.32) | 0.00 |
| proto-bytes | stock-aliased | interpret | 7.47 (5.42–12.89) | 2.00 |
| proto-bytes | stock-aliased | peak | 5.32 (3.95–6.94) | 1.00 |
| proto-bytes | owned | ingest | 25456.66 (16393.56–40030.03) | 66035.00 |
| proto-bytes | owned | interpret | 29.17 (27.07–30.41) | 2.00 |
| proto-bytes | owned | peak | 6.44 (6.28–6.59) | 1.00 |
| proto-bytes | buffa | ingest | 398.79 (394.44–400.18) | 312.00 |
| proto-bytes | buffa | interpret | 25.29 (25.27–25.65) | 2.00 |
| proto-bytes | buffa | peak | 5.75 (5.70–6.24) | 1.00 |
| timestamp-json | stock | ingest | 7250.24 (5419.08–7556.27) | 6439.75 |
| timestamp-json | owned | ingest | 2333.88 (2310.75–3420.27) | 3703.75 |
| timestamp-json | owned | ingest-typed | 3906.53 (3638.62–4034.78) | 3671.75 |
| timestamp-json | buffa | ingest | 837.39 (834.41–1107.48) | 728.00 |
| timestamp-json | buffa | ingest-typed | 1046.60 (835.34–1211.12) | 728.00 |
| mysql-int4 | stock | ingest | 77.25 (60.15–102.99) | 16.00 |
| mysql-int4 | stock | read-aot | 2.03 (1.79–2.05) | 0.00 |
| mysql-int4 | stock | interpret | 3.20 (2.98–4.94) | 2.00 |
| mysql-int4 | stock | peak | 1.68 (1.37–1.89) | 1.00 |
| mysql-int4 | static | ingest | 167.19 (166.30–169.30) | 160.00 |
| mysql-int4 | static | ingest-typed | 166.94 (166.09–243.92) | 160.00 |
| mysql-int4 | static | interpret | 17.79 (17.68–17.80) | 2.00 |
| mysql-int4 | static | peak | 3.42 (3.18–3.46) | 1.00 |
| postgres-int4 | stock | ingest | 55.03 (38.86–75.15) | 16.00 |
| postgres-int4 | stock | read-aot | 2.15 (1.96–2.66) | 0.00 |
| postgres-int4 | stock | interpret | 3.01 (2.98–3.05) | 2.00 |
| postgres-int4 | stock | peak | 1.39 (1.37–2.05) | 1.00 |
| postgres-int4 | static | ingest | 150.31 (150.18–222.04) | 160.00 |
| postgres-int4 | static | ingest-typed | 148.78 (147.62–149.98) | 160.00 |
| postgres-int4 | static | interpret | 23.85 (17.87–24.83) | 2.00 |
| postgres-int4 | static | peak | 3.23 (3.22–3.37) | 1.00 |
| proto-mixed | stock | ingest | 1316.53 (1316.33–1617.04) | 1776.75 |
| proto-mixed | stock | read-aot | 7.57 (7.49–9.37) | 0.00 |
| proto-mixed | stock | interpret-boundary | 9.45 (9.33–15.45) | 2.00 |
| proto-mixed | stock | peak-boundary | 8.65 (8.62–8.98) | 1.00 |
| proto-mixed | owned | ingest | 13485.96 (13213.59–28955.98) | 3339.50 |
| proto-mixed | owned | ingest-typed | 13308.41 (13271.42–25162.57) | 3339.50 |
| proto-mixed | owned | interpret | 93.21 (91.43–93.35) | 2.00 |
| proto-mixed | owned | peak | 12.86 (12.76–17.75) | 1.00 |
| proto-mixed | buffa | ingest | 6585.02 (6549.31–9394.49) | 1879.75 |
| proto-mixed | buffa | ingest-typed | 6585.82 (6485.83–9954.72) | 1879.75 |
| proto-mixed | buffa | interpret | 110.41 (85.93–123.57) | 2.00 |
| proto-mixed | buffa | peak | 15.64 (15.63–16.13) | 1.00 |
| proto-mixed-full | stock | ingest | 1413.86 (1365.05–1532.86) | 1776.75 |
| proto-mixed-full | owned | ingest | 15492.16 (15155.08–20658.49) | 3339.50 |
| proto-mixed-full | buffa | ingest | 8293.10 (8284.88–9259.67) | 2006.25 |

Stock read-aot is the native-image host baseline. Schema 4 adds stock guest tiers; mixed protobuf uses explicitly labeled boundary projections because its collection getters fail the SVM JIT blocklist.
Ingest includes parsing/materialization and an uncached projected read. Setup is excluded.
ingest-typed uses primitive readLong projections; ingest retains generic read projections.
Buffa binary input is already in unmanaged memory; owned engines start with Java byte arrays.
Buffa results are scoped borrowed views; stock-aliased borrows immutable ByteString input; stock/owned copy payloads.
The direct stock AOT baseline excludes guest interop and contract enforcement.
MySQL/Postgres measure driver INT4 decoder functions and retained scalar rows, not JDBC queries, transport, or servers.
Java heap counters exclude native allocations and compiler/background threads. Native allocation is not reported as zero.
Shared execution hardware and short samples make these diagnostic comparisons, not performance guarantees.

Binary SHA-256: `ab8c1fe65ae5224aff5f55ec6d9cd1aa54bbb6fdd8514cf03dd5c60e6f160f33`
Revision: `a4e1e11a92c3a57c22de86d02a61e765a6642660`
