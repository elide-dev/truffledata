# Paired native index load experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | baseline | 1671.281 (1665.333–1682.963) | 1727.750 |
| proto-mixed | buffa | ingest | candidate | 1597.370 (1585.657–1598.359) | 1727.750 |
| proto-mixed | buffa | ingest-generated | baseline | 1545.210 (1542.780–1552.995) | 1727.750 |
| proto-mixed | buffa | ingest-generated | candidate | 1467.028 (1465.638–1467.978) | 1727.750 |
| proto-mixed-full | buffa | ingest | baseline | 2379.251 (2364.563–2390.347) | 1854.250 |
| proto-mixed-full | buffa | ingest | candidate | 2300.691 (2297.885–2321.647) | 1854.250 |
| proto-scalar | buffa | ingest-generated | baseline | 53.248 (53.116–53.848) | 80.000 |
| proto-scalar | buffa | ingest-generated | candidate | 52.700 (52.346–53.249) | 80.000 |
| proto-mixed | generated-owned | ingest | baseline | 692.300 (682.454–721.599) | 1800.500 |
| proto-mixed | generated-owned | ingest | candidate | 633.070 (631.824–634.134) | 1800.500 |
| proto-mixed | stock | ingest | baseline | 634.704 (632.930–651.501) | 1776.750 |
| proto-mixed | stock | ingest | candidate | 621.086 (619.799–631.091) | 1776.750 |
