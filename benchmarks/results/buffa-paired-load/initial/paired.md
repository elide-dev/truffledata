# Paired native index load experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | baseline | 1674.784 (1674.590–1678.944) | 1727.750 |
| proto-mixed | buffa | ingest | candidate | 1585.276 (1579.080–1594.207) | 1727.750 |
| proto-mixed | buffa | ingest-generated | baseline | 1559.541 (1552.947–1570.123) | 1727.750 |
| proto-mixed | buffa | ingest-generated | candidate | 1471.888 (1468.334–1482.749) | 1727.750 |
| proto-mixed-full | buffa | ingest | baseline | 2397.338 (2389.202–2423.953) | 1854.250 |
| proto-mixed-full | buffa | ingest | candidate | 2304.358 (2296.718–2395.044) | 1854.250 |
| proto-scalar | buffa | ingest-generated | baseline | 52.452 (52.233–53.371) | 80.000 |
| proto-scalar | buffa | ingest-generated | candidate | 52.839 (52.622–53.074) | 80.000 |
| proto-mixed | generated-owned | ingest | baseline | 641.252 (637.122–654.185) | 1800.500 |
| proto-mixed | generated-owned | ingest | candidate | 633.599 (631.190–639.175) | 1800.500 |
| proto-mixed | stock | ingest | baseline | 646.388 (639.073–653.035) | 1776.750 |
| proto-mixed | stock | ingest | candidate | 626.112 (616.138–626.587) | 1776.750 |
