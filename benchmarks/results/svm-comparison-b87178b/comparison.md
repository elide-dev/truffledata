# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 110.43 (105.50–131.44) | 136.00 |
| proto-scalar | stock | read-aot | 3.20 (1.94–3.24) | 0.00 |
| proto-scalar | owned | ingest | 183.08 (125.80–217.34) | 88.00 |
| proto-scalar | owned | ingest-typed | 124.20 (114.95–207.83) | 56.00 |
| proto-scalar | owned | interpret | 47.15 (42.02–52.09) | 2.00 |
| proto-scalar | owned | peak | 3.96 (3.78–5.46) | 1.00 |
| proto-scalar | buffa | ingest | 930.30 (647.01–1046.47) | 312.00 |
| proto-scalar | buffa | ingest-typed | 881.53 (663.29–1159.00) | 312.00 |
| proto-scalar | buffa | interpret | 38.85 (34.30–44.56) | 2.00 |
| proto-scalar | buffa | peak | 7.05 (5.87–8.11) | 1.00 |
| proto-bytes | stock | ingest | 74371.62 (60326.64–88286.77) | 65699.00 |
| proto-bytes | stock | read-aot | 6.52 (5.40–6.63) | 0.00 |
| proto-bytes | stock-aliased | ingest | 140.35 (103.93–149.80) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 5.47 (5.09–5.94) | 0.00 |
| proto-bytes | owned | ingest | 27976.22 (17471.74–30489.92) | 66051.00 |
| proto-bytes | owned | interpret | 45.00 (38.30–45.43) | 2.00 |
| proto-bytes | owned | peak | 9.00 (7.08–10.81) | 1.00 |
| proto-bytes | buffa | ingest | 403.94 (400.91–602.47) | 304.00 |
| proto-bytes | buffa | interpret | 31.19 (27.71–42.12) | 2.00 |
| proto-bytes | buffa | peak | 6.36 (5.97–6.52) | 1.00 |
| timestamp-json | stock | ingest | 8208.44 (7125.98–8571.18) | 6439.75 |
| timestamp-json | owned | ingest | 3703.14 (3670.78–3707.04) | 3703.75 |
| timestamp-json | owned | ingest-typed | 3503.64 (3293.66–3672.51) | 3671.75 |
| timestamp-json | buffa | ingest | 1599.83 (1259.17–1635.86) | 720.00 |
| timestamp-json | buffa | ingest-typed | 1627.85 (912.96–2207.67) | 720.00 |
| mysql-int4 | stock | ingest | 97.15 (65.14–110.07) | 16.00 |
| mysql-int4 | stock | read-aot | 2.49 (2.37–2.86) | 0.00 |
| mysql-int4 | static | ingest | 318.07 (185.25–326.79) | 160.00 |
| mysql-int4 | static | ingest-typed | 235.11 (178.63–290.31) | 160.00 |
| mysql-int4 | static | interpret | 26.35 (25.37–26.50) | 2.00 |
| mysql-int4 | static | peak | 6.15 (3.83–6.54) | 1.00 |
| postgres-int4 | stock | ingest | 76.64 (74.47–105.58) | 16.00 |
| postgres-int4 | stock | read-aot | 3.08 (2.93–4.07) | 0.00 |
| postgres-int4 | static | ingest | 214.06 (212.21–272.90) | 160.00 |
| postgres-int4 | static | ingest-typed | 173.22 (145.36–243.24) | 160.00 |
| postgres-int4 | static | interpret | 19.23 (17.82–19.88) | 2.00 |
| postgres-int4 | static | peak | 3.86 (3.74–5.38) | 1.00 |
| proto-mixed | stock | ingest | 1927.76 (1912.99–2534.66) | 1776.75 |
| proto-mixed | stock | read-aot | 13.57 (12.44–13.59) | 0.00 |
| proto-mixed | owned | ingest | 14874.63 (14596.15–21534.75) | 3562.50 |
| proto-mixed | owned | ingest-typed | 14081.31 (13760.48–15193.85) | 3562.50 |
| proto-mixed | owned | interpret | 130.35 (113.81–143.79) | 2.00 |
| proto-mixed | owned | peak | 22.85 (21.34–26.86) | 1.00 |
| proto-mixed | buffa | ingest | 11332.87 (10283.52–11416.48) | 2195.25 |
| proto-mixed | buffa | ingest-typed | 9860.33 (7908.66–13976.76) | 2195.25 |
| proto-mixed | buffa | interpret | 115.44 (82.80–128.61) | 2.00 |
| proto-mixed | buffa | peak | 19.48 (14.87–20.16) | 1.00 |

Stock read-aot is the native-image host baseline; it has no Truffle interpreter/JIT tier.
Ingest includes parsing/materialization and an uncached projected read. Setup is excluded.
ingest-typed uses primitive readLong projections; ingest retains generic read projections.
Buffa binary input is already in unmanaged memory; owned engines start with Java byte arrays.
Buffa results are scoped borrowed views; stock-aliased borrows immutable ByteString input; stock/owned copy payloads.
The direct stock AOT baseline excludes guest interop and contract enforcement.
MySQL/Postgres measure driver INT4 decoder functions and retained scalar rows, not JDBC queries, transport, or servers.
Java heap counters exclude native allocations and compiler/background threads. Native allocation is not reported as zero.
Shared execution hardware and short samples make these diagnostic comparisons, not performance guarantees.

Binary SHA-256: `bda3787348d8bd937d90a2a37b8a141dae86b244033f101db64722fa2fe3b797`
Revision: `b87178b60fd1773c6ebc89ff91a448a6600f4313`
