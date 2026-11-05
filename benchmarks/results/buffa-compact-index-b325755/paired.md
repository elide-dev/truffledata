# Paired compact native index experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | baseline | 1756.794 (1734.680–1846.084) | 1727.750 |
| proto-mixed | buffa | ingest | candidate | 1787.030 (1769.697–1889.836) | 1727.750 |
| proto-mixed | buffa | ingest-generated | baseline | 1653.472 (1627.554–1718.992) | 1727.750 |
| proto-mixed | buffa | ingest-generated | candidate | 1752.948 (1650.926–1759.341) | 1727.750 |
| proto-mixed-full | buffa | ingest | baseline | 2474.636 (2448.009–2602.947) | 1854.250 |
| proto-mixed-full | buffa | ingest | candidate | 2521.467 (2489.466–2722.144) | 1854.250 |
| proto-scalar | buffa | ingest-generated | baseline | 54.302 (52.416–55.654) | 80.000 |
| proto-scalar | buffa | ingest-generated | candidate | 53.436 (52.880–56.213) | 80.000 |
| proto-mixed | generated-owned | ingest | baseline | 674.131 (673.263–677.175) | 1800.500 |
| proto-mixed | generated-owned | ingest | candidate | 644.078 (639.809–685.695) | 1800.500 |
| proto-mixed | stock | ingest | baseline | 635.158 (626.623–664.692) | 1776.750 |
| proto-mixed | stock | ingest | candidate | 637.350 (631.451–684.802) | 1776.750 |
