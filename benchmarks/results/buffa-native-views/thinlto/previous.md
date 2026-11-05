# Interleaved ThinLTO comparison

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | previous | 811.461 (798.209–814.946) | 421.500 |
| proto-mixed | buffa | ingest | candidate | 802.056 (801.008–807.510) | 421.500 |
| proto-mixed | buffa | ingest-generated | previous | 717.786 (702.940–728.044) | 421.500 |
| proto-mixed | buffa | ingest-generated | candidate | 695.114 (690.802–706.616) | 421.500 |
| proto-mixed-full | buffa | ingest | previous | 1898.411 (1886.241–1956.587) | 1382.250 |
| proto-mixed-full | buffa | ingest | candidate | 1905.044 (1890.654–1918.789) | 1382.250 |
| proto-scalar | buffa | ingest-generated | previous | 50.039 (49.883–50.427) | 80.000 |
| proto-scalar | buffa | ingest-generated | candidate | 49.992 (49.786–50.366) | 80.000 |
| proto-mixed | generated-owned | ingest | previous | 672.516 (667.196–681.599) | 1800.500 |
| proto-mixed | generated-owned | ingest | candidate | 667.394 (664.636–692.844) | 1800.500 |
| proto-mixed | stock | ingest | previous | 642.897 (632.734–656.356) | 1776.750 |
| proto-mixed | stock | ingest | candidate | 640.552 (637.410–666.883) | 1776.750 |
| proto-mixed-full | stock | ingest | previous | 684.972 (676.097–698.595) | 1776.750 |
| proto-mixed-full | stock | ingest | candidate | 680.610 (671.133–686.794) | 1776.750 |
| proto-mixed | buffa | interpret-generated | previous | 16.690 (16.639–16.811) | 2.000 |
| proto-mixed | buffa | interpret-generated | candidate | 16.737 (16.645–16.838) | 2.000 |
| proto-mixed | buffa | peak-generated | previous | 9.363 (9.346–9.451) | 1.000 |
| proto-mixed | buffa | peak-generated | candidate | 9.340 (9.246–9.378) | 1.000 |
| proto-mixed | stock | interpret-boundary | previous | 5.470 (5.419–5.518) | 2.000 |
| proto-mixed | stock | interpret-boundary | candidate | 5.410 (5.379–5.538) | 2.000 |
| proto-mixed | stock | peak-boundary | previous | 5.252 (5.238–5.325) | 1.000 |
| proto-mixed | stock | peak-boundary | candidate | 5.276 (5.204–5.376) | 1.000 |
| proto-mixed | generated-owned | peak | previous | 2.703 (2.703–2.721) | 1.000 |
| proto-mixed | generated-owned | peak | candidate | 2.728 (2.700–2.733) | 1.000 |
| proto-mixed-full | buffa | ingest-generated | previous | 1341.730 (1331.767–1349.306) | 1382.250 |
| proto-mixed-full | buffa | ingest-generated | candidate | 1325.566 (1322.588–1330.962) | 1382.250 |
| proto-mixed-full | owned | ingest-generated | previous | 3311.494 (3253.540–3349.294) | 2976.000 |
| proto-mixed-full | owned | ingest-generated | candidate | 3291.151 (3240.819–3321.753) | 2976.000 |
| proto-mixed-full | generated-owned | ingest | previous | 703.260 (697.324–714.547) | 1800.500 |
| proto-mixed-full | generated-owned | ingest | candidate | 701.125 (696.508–708.374) | 1800.500 |
