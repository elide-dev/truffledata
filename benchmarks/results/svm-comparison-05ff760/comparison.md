# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 145.35 (101.93–173.16) | 136.00 |
| proto-scalar | stock | read-aot | 3.36 (3.06–4.33) | 0.00 |
| proto-scalar | stock | interpret | 4.67 (2.74–5.07) | 2.00 |
| proto-scalar | stock | peak | 2.69 (2.33–3.30) | 1.00 |
| proto-scalar | owned | ingest | 163.59 (129.31–204.95) | 88.00 |
| proto-scalar | owned | ingest-typed | 150.91 (129.84–211.68) | 56.00 |
| proto-scalar | owned | interpret | 59.19 (57.01–59.95) | 2.00 |
| proto-scalar | owned | peak | 5.01 (4.44–5.95) | 1.00 |
| proto-scalar | buffa | ingest | 1260.59 (773.33–1318.31) | 320.00 |
| proto-scalar | buffa | ingest-typed | 753.89 (563.03–909.02) | 320.00 |
| proto-scalar | buffa | interpret | 32.36 (28.82–34.95) | 2.00 |
| proto-scalar | buffa | peak | 9.94 (5.42–10.79) | 1.00 |
| proto-bytes | stock | ingest | 77883.37 (75603.98–79612.30) | 65699.00 |
| proto-bytes | stock | read-aot | 5.91 (4.58–6.43) | 0.00 |
| proto-bytes | stock | interpret | 8.03 (6.52–8.99) | 2.00 |
| proto-bytes | stock | peak | 5.59 (5.17–7.32) | 1.00 |
| proto-bytes | stock-aliased | ingest | 150.25 (115.87–160.30) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 4.73 (4.15–6.22) | 0.00 |
| proto-bytes | stock-aliased | interpret | 5.98 (4.84–7.18) | 2.00 |
| proto-bytes | stock-aliased | peak | 6.04 (5.01–7.18) | 1.00 |
| proto-bytes | owned | ingest | 24809.33 (13450.68–33838.11) | 66035.00 |
| proto-bytes | owned | interpret | 33.47 (30.17–44.40) | 2.00 |
| proto-bytes | owned | peak | 7.85 (6.22–8.95) | 1.00 |
| proto-bytes | buffa | ingest | 823.00 (712.99–829.10) | 312.00 |
| proto-bytes | buffa | interpret | 47.76 (34.80–49.65) | 2.00 |
| proto-bytes | buffa | peak | 10.24 (9.68–10.69) | 1.00 |
| timestamp-json | stock | ingest | 8996.89 (8518.90–9613.23) | 6439.75 |
| timestamp-json | owned | ingest | 3554.53 (3080.95–4500.41) | 3703.75 |
| timestamp-json | owned | ingest-typed | 3734.69 (3672.75–4037.79) | 3671.75 |
| timestamp-json | buffa | ingest | 1513.56 (905.68–1650.69) | 728.00 |
| timestamp-json | buffa | ingest-typed | 913.41 (910.99–1128.65) | 728.00 |
| mysql-int4 | stock | ingest | 101.00 (63.51–111.56) | 16.00 |
| mysql-int4 | stock | read-aot | 2.78 (2.12–3.46) | 0.00 |
| mysql-int4 | stock | interpret | 5.98 (5.61–6.54) | 2.00 |
| mysql-int4 | stock | peak | 1.39 (1.37–2.50) | 1.00 |
| mysql-int4 | static | ingest | 266.64 (225.95–544.86) | 160.00 |
| mysql-int4 | static | ingest-typed | 353.71 (316.00–372.04) | 160.00 |
| mysql-int4 | static | interpret | 30.14 (18.60–34.17) | 2.00 |
| mysql-int4 | static | peak | 5.67 (4.47–6.65) | 1.00 |
| postgres-int4 | stock | ingest | 79.58 (53.78–81.55) | 16.00 |
| postgres-int4 | stock | read-aot | 2.40 (1.95–2.98) | 0.00 |
| postgres-int4 | stock | interpret | 4.11 (2.84–4.80) | 2.00 |
| postgres-int4 | stock | peak | 2.09 (1.67–2.13) | 1.00 |
| postgres-int4 | static | ingest | 286.44 (281.96–294.32) | 160.00 |
| postgres-int4 | static | ingest-typed | 263.74 (211.01–275.49) | 160.00 |
| postgres-int4 | static | interpret | 19.44 (18.52–34.30) | 2.00 |
| postgres-int4 | static | peak | 5.61 (5.19–6.39) | 1.00 |
| proto-mixed | stock | ingest | 1782.04 (1463.44–2302.01) | 1776.75 |
| proto-mixed | stock | read-aot | 13.18 (10.98–14.77) | 0.00 |
| proto-mixed | stock | interpret-boundary | 13.92 (10.08–15.70) | 2.00 |
| proto-mixed | stock | peak-boundary | 16.34 (12.65–16.65) | 1.00 |
| proto-mixed | owned | ingest | 24331.09 (22727.23–25707.59) | 3339.50 |
| proto-mixed | owned | ingest-typed | 20614.38 (17867.71–21082.02) | 3339.50 |
| proto-mixed | owned | interpret | 147.78 (98.15–165.42) | 2.00 |
| proto-mixed | owned | peak | 20.18 (17.36–20.71) | 1.00 |
| proto-mixed | buffa | ingest | 11515.96 (10421.92–11864.04) | 2021.50 |
| proto-mixed | buffa | ingest-typed | 9802.58 (8338.77–15038.50) | 2021.50 |
| proto-mixed | buffa | interpret | 142.89 (141.48–143.38) | 2.00 |
| proto-mixed | buffa | peak | 23.32 (17.62–30.25) | 1.00 |

Stock read-aot is the native-image host baseline. Schema 4 adds stock guest tiers; mixed protobuf uses explicitly labeled boundary projections because its collection getters fail the SVM JIT blocklist.
Ingest includes parsing/materialization and an uncached projected read. Setup is excluded.
ingest-typed uses primitive readLong projections; ingest retains generic read projections.
Buffa binary input is already in unmanaged memory; owned engines start with Java byte arrays.
Buffa results are scoped borrowed views; stock-aliased borrows immutable ByteString input; stock/owned copy payloads.
The direct stock AOT baseline excludes guest interop and contract enforcement.
MySQL/Postgres measure driver INT4 decoder functions and retained scalar rows, not JDBC queries, transport, or servers.
Java heap counters exclude native allocations and compiler/background threads. Native allocation is not reported as zero.
Shared execution hardware and short samples make these diagnostic comparisons, not performance guarantees.

Binary SHA-256: `0caf6c45e9e4c86eecdc0596eaab867c4cf918dd329403de1c33cdd3019b2d9b`
Revision: `05ff76002e2b178145ec06cae7288e8d9840dc48`
