# Same-binary native-view experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | eager | 1602.433 (1594.730–1617.360) | 1735.750 |
| proto-mixed | buffa | ingest | view | 1584.627 (1569.124–1598.577) | 493.500 |
| proto-mixed | buffa | ingest-generated | eager | 1500.522 (1494.688–1523.131) | 1735.750 |
| proto-mixed | buffa | ingest-generated | view | 1500.157 (1494.761–1520.630) | 493.500 |
| proto-mixed-full | buffa | ingest | eager | 2307.334 (2299.960–2325.401) | 1862.250 |
| proto-mixed-full | buffa | ingest | view | 2791.394 (2749.962–2826.044) | 2053.250 |
| proto-scalar | buffa | ingest-generated | eager | 55.179 (54.634–55.672) | 88.000 |
| proto-scalar | buffa | ingest-generated | view | 55.249 (54.843–56.037) | 88.000 |
| proto-mixed | generated-owned | ingest | eager | 652.951 (643.797–658.388) | 1800.500 |
| proto-mixed | generated-owned | ingest | view | 642.629 (640.982–651.538) | 1800.500 |
| proto-mixed | stock | ingest | eager | 646.865 (641.532–663.496) | 1776.750 |
| proto-mixed | stock | ingest | view | 647.202 (638.569–675.435) | 1776.750 |
| proto-mixed-full | stock | ingest | eager | 674.943 (665.963–683.185) | 1776.750 |
| proto-mixed-full | stock | ingest | view | 670.517 (662.767–678.898) | 1776.750 |
| proto-mixed | buffa | interpret-generated | eager | 18.962 (18.690–19.045) | 2.000 |
| proto-mixed | buffa | interpret-generated | view | 23.860 (23.790–24.007) | 2.000 |
| proto-mixed | buffa | peak-generated | eager | 9.245 (9.184–9.338) | 1.000 |
| proto-mixed | buffa | peak-generated | view | 14.728 (14.685–15.128) | 1.000 |
| proto-mixed | stock | interpret-boundary | eager | 5.398 (5.384–5.424) | 2.000 |
| proto-mixed | stock | interpret-boundary | view | 5.526 (5.379–5.615) | 2.000 |
| proto-mixed | stock | peak-boundary | eager | 5.216 (5.196–5.416) | 1.000 |
| proto-mixed | stock | peak-boundary | view | 5.279 (5.166–5.352) | 1.000 |
| proto-mixed | generated-owned | peak | eager | 2.705 (2.699–2.712) | 1.000 |
| proto-mixed | generated-owned | peak | view | 2.706 (2.701–2.708) | 1.000 |
