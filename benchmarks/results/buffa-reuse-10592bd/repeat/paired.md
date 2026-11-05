# Paired native index reuse experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | baseline | 2001.100 (2000.012–2116.962) | 1727.750 |
| proto-mixed | buffa | ingest | candidate | 1983.040 (1895.052–2032.473) | 1727.750 |
| proto-mixed | buffa | ingest-generated | baseline | 1982.524 (1883.133–2042.580) | 1727.750 |
| proto-mixed | buffa | ingest-generated | candidate | 1795.418 (1788.316–1894.808) | 1727.750 |
| proto-mixed-full | buffa | ingest | baseline | 2729.041 (2716.514–2854.294) | 1854.250 |
| proto-mixed-full | buffa | ingest | candidate | 2743.995 (2600.106–2756.671) | 1854.250 |
| proto-scalar | buffa | ingest-generated | baseline | 55.147 (52.750–58.209) | 80.000 |
| proto-scalar | buffa | ingest-generated | candidate | 54.898 (52.312–55.218) | 80.000 |
| proto-mixed | generated-owned | ingest | baseline | 648.401 (638.032–674.287) | 1800.500 |
| proto-mixed | generated-owned | ingest | candidate | 650.534 (632.583–667.136) | 1800.500 |
| proto-mixed | stock | ingest | baseline | 664.751 (635.435–690.700) | 1776.750 |
| proto-mixed | stock | ingest | candidate | 667.850 (629.215–681.758) | 1776.750 |
