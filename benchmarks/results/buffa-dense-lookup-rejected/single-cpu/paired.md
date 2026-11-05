# Paired dense field lookup experiment (rejected)

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | baseline | 1708.856 (1703.181–1720.299) | 1727.750 |
| proto-mixed | buffa | ingest | candidate | 1707.598 (1700.923–1726.014) | 1727.750 |
| proto-mixed | buffa | ingest-generated | baseline | 1591.095 (1588.309–1599.290) | 1727.750 |
| proto-mixed | buffa | ingest-generated | candidate | 1592.352 (1585.368–1597.035) | 1727.750 |
| proto-mixed-full | buffa | ingest | baseline | 2408.240 (2400.564–2426.564) | 1854.250 |
| proto-mixed-full | buffa | ingest | candidate | 2375.571 (2364.758–2387.395) | 1854.250 |
| proto-scalar | buffa | ingest-generated | baseline | 52.590 (52.427–52.987) | 80.000 |
| proto-scalar | buffa | ingest-generated | candidate | 53.173 (52.530–53.309) | 80.000 |
| proto-mixed | generated-owned | ingest | baseline | 648.667 (643.442–654.052) | 1800.500 |
| proto-mixed | generated-owned | ingest | candidate | 646.045 (642.643–651.565) | 1800.500 |
| proto-mixed | stock | ingest | baseline | 633.253 (631.071–673.239) | 1776.750 |
| proto-mixed | stock | ingest | candidate | 642.946 (638.382–651.466) | 1776.750 |
