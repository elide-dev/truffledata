# SVM data access comparison

Median of three process medians; ranges are min–max process medians, not confidence intervals.
Time and allocations use separate fresh processes. Lower is better.

| Workload | Engine | Stage | ns/row (range) | Java heap B/row |
|---|---|---|---:|---:|
| proto-scalar | stock | ingest | 112.93 (111.88–189.88) | 136.00 |
| proto-scalar | stock | read-aot | 3.43 (1.95–3.59) | 0.00 |
| proto-scalar | stock | interpret | 3.52 (2.83–4.24) | 2.00 |
| proto-scalar | stock | peak | 1.79 (1.55–4.59) | 1.00 |
| proto-scalar | owned | ingest | 175.13 (123.58–224.08) | 88.00 |
| proto-scalar | owned | ingest-typed | 149.15 (141.27–246.83) | 56.00 |
| proto-scalar | owned | interpret | 50.93 (39.66–57.94) | 2.00 |
| proto-scalar | owned | peak | 4.48 (3.26–5.55) | 1.00 |
| proto-scalar | buffa | ingest | 516.22 (503.52–868.87) | 320.00 |
| proto-scalar | buffa | ingest-typed | 508.85 (503.78–679.42) | 320.00 |
| proto-scalar | buffa | interpret | 29.47 (26.25–31.06) | 2.00 |
| proto-scalar | buffa | peak | 5.52 (5.35–6.22) | 1.00 |
| proto-bytes | stock | ingest | 53249.08 (53069.78–66422.33) | 65699.00 |
| proto-bytes | stock | read-aot | 5.83 (5.35–6.90) | 0.00 |
| proto-bytes | stock | interpret | 7.94 (5.38–9.83) | 2.00 |
| proto-bytes | stock | peak | 6.67 (4.59–7.79) | 1.00 |
| proto-bytes | stock-aliased | ingest | 150.64 (84.89–153.02) | 144.00 |
| proto-bytes | stock-aliased | read-aot | 4.57 (4.53–5.67) | 0.00 |
| proto-bytes | stock-aliased | interpret | 8.39 (7.55–8.98) | 2.00 |
| proto-bytes | stock-aliased | peak | 5.80 (5.11–6.72) | 1.00 |
| proto-bytes | owned | ingest | 23475.34 (12853.42–33004.16) | 66035.00 |
| proto-bytes | owned | interpret | 34.08 (28.07–37.46) | 2.00 |
| proto-bytes | owned | peak | 9.30 (6.30–10.66) | 1.00 |
| proto-bytes | buffa | ingest | 749.67 (703.65–784.25) | 312.00 |
| proto-bytes | buffa | interpret | 26.29 (24.78–37.19) | 2.00 |
| proto-bytes | buffa | peak | 8.68 (6.26–9.81) | 1.00 |
| timestamp-json | stock | ingest | 7980.30 (5401.11–8840.25) | 6439.75 |
| timestamp-json | owned | ingest | 3885.38 (2812.56–4128.08) | 3703.75 |
| timestamp-json | owned | ingest-typed | 3134.24 (2313.91–3726.59) | 3671.75 |
| timestamp-json | buffa | ingest | 1417.30 (1395.45–1820.36) | 728.00 |
| timestamp-json | buffa | ingest-typed | 1278.94 (830.61–1565.46) | 728.00 |
| mysql-int4 | stock | ingest | 72.10 (62.03–93.15) | 16.00 |
| mysql-int4 | stock | read-aot | 2.05 (1.79–2.56) | 0.00 |
| mysql-int4 | stock | interpret | 5.00 (3.51–5.68) | 2.00 |
| mysql-int4 | stock | peak | 2.22 (2.06–2.73) | 1.00 |
| mysql-int4 | static | ingest | 261.64 (165.83–373.61) | 160.00 |
| mysql-int4 | static | ingest-typed | 303.96 (168.82–313.69) | 160.00 |
| mysql-int4 | static | interpret | 24.94 (17.59–26.15) | 2.00 |
| mysql-int4 | static | peak | 3.40 (3.40–6.15) | 1.00 |
| postgres-int4 | stock | ingest | 69.38 (39.20–71.39) | 16.00 |
| postgres-int4 | stock | read-aot | 2.72 (1.92–3.74) | 0.00 |
| postgres-int4 | stock | interpret | 3.30 (3.03–3.55) | 2.00 |
| postgres-int4 | stock | peak | 1.49 (1.36–1.58) | 1.00 |
| postgres-int4 | static | ingest | 284.81 (159.15–292.54) | 160.00 |
| postgres-int4 | static | ingest-typed | 221.22 (207.41–301.84) | 160.00 |
| postgres-int4 | static | interpret | 22.63 (17.91–23.51) | 2.00 |
| postgres-int4 | static | peak | 4.70 (3.88–5.50) | 1.00 |
| proto-mixed | stock | ingest | 2015.81 (1262.88–2035.57) | 1776.75 |
| proto-mixed | stock | read-aot | 12.44 (11.97–14.07) | 0.00 |
| proto-mixed | stock | interpret-boundary | 16.73 (10.36–17.51) | 2.00 |
| proto-mixed | stock | peak-boundary | 12.55 (11.74–12.86) | 1.00 |
| proto-mixed | owned | ingest | 23555.67 (13128.71–24716.61) | 3339.50 |
| proto-mixed | owned | ingest-typed | 16330.40 (13197.59–24513.91) | 3339.50 |
| proto-mixed | owned | interpret | 133.91 (97.13–168.84) | 2.00 |
| proto-mixed | owned | peak | 19.64 (15.76–23.39) | 1.00 |
| proto-mixed | buffa | ingest | 6630.14 (6057.62–11049.52) | 2021.50 |
| proto-mixed | buffa | ingest-typed | 7696.87 (5983.16–11630.44) | 2021.50 |
| proto-mixed | buffa | interpret | 111.78 (84.73–118.68) | 2.00 |
| proto-mixed | buffa | peak | 18.31 (16.10–23.07) | 1.00 |
| proto-mixed-full | stock | ingest | 1378.26 (1346.48–1548.70) | 1776.75 |
| proto-mixed-full | owned | ingest | 24645.54 (15451.23–27345.24) | 3339.50 |
| proto-mixed-full | buffa | ingest | 10856.21 (8162.60–13637.62) | 2148.00 |

Stock read-aot is the native-image host baseline. Schema 4 adds stock guest tiers; mixed protobuf uses explicitly labeled boundary projections because its collection getters fail the SVM JIT blocklist.
Ingest includes parsing/materialization and an uncached projected read. Setup is excluded.
ingest-typed uses primitive readLong projections; ingest retains generic read projections.
Buffa binary input is already in unmanaged memory; owned engines start with Java byte arrays.
Buffa results are scoped borrowed views; stock-aliased borrows immutable ByteString input; stock/owned copy payloads.
The direct stock AOT baseline excludes guest interop and contract enforcement.
MySQL/Postgres measure driver INT4 decoder functions and retained scalar rows, not JDBC queries, transport, or servers.
Java heap counters exclude native allocations and compiler/background threads. Native allocation is not reported as zero.
Shared execution hardware and short samples make these diagnostic comparisons, not performance guarantees.

Binary SHA-256: `c05bd485e22401ae703d4fbe89f60ab59029b2adf23111f06bcc86ded53bdcc7`
Revision: `f535fd3169650b64474bd01a1f33aa9d014a5450`
