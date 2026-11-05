# Same-binary native-view experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | eager | 1618.530 (1617.053–1624.573) | 1743.750 |
| proto-mixed | buffa | ingest | view | 936.398 (924.741–943.013) | 501.500 |
| proto-mixed | buffa | ingest-generated | eager | 1518.730 (1513.841–1520.584) | 1743.750 |
| proto-mixed | buffa | ingest-generated | view | 833.241 (826.994–838.185) | 501.500 |
| proto-mixed-full | buffa | ingest | eager | 2322.363 (2313.136–2335.746) | 1870.250 |
| proto-mixed-full | buffa | ingest | view | 2135.834 (2128.113–2185.685) | 1572.750 |
| proto-scalar | buffa | ingest-generated | eager | 53.215 (53.057–53.305) | 88.000 |
| proto-scalar | buffa | ingest-generated | view | 53.337 (53.101–53.725) | 88.000 |
| proto-mixed | generated-owned | ingest | eager | 650.704 (646.125–657.468) | 1800.500 |
| proto-mixed | generated-owned | ingest | view | 651.726 (647.197–652.922) | 1800.500 |
| proto-mixed | stock | ingest | eager | 634.198 (626.424–639.209) | 1776.750 |
| proto-mixed | stock | ingest | view | 642.761 (624.271–656.760) | 1776.750 |
| proto-mixed-full | stock | ingest | eager | 681.474 (676.693–688.846) | 1776.750 |
| proto-mixed-full | stock | ingest | view | 676.370 (674.529–682.823) | 1776.750 |
| proto-mixed | buffa | interpret-generated | eager | 17.500 (17.342–17.768) | 2.000 |
| proto-mixed | buffa | interpret-generated | view | 18.914 (18.856–19.141) | 2.000 |
| proto-mixed | buffa | peak-generated | eager | 6.724 (6.610–6.753) | 1.000 |
| proto-mixed | buffa | peak-generated | view | 9.317 (9.182–9.464) | 1.000 |
| proto-mixed | stock | interpret-boundary | eager | 5.465 (5.392–5.532) | 2.000 |
| proto-mixed | stock | interpret-boundary | view | 5.433 (5.378–5.546) | 2.000 |
| proto-mixed | stock | peak-boundary | eager | 5.375 (5.254–5.421) | 1.000 |
| proto-mixed | stock | peak-boundary | view | 5.271 (5.251–5.383) | 1.000 |
| proto-mixed | generated-owned | peak | eager | 2.706 (2.703–2.747) | 1.000 |
| proto-mixed | generated-owned | peak | view | 2.708 (2.704–2.719) | 1.000 |
| proto-mixed-full | buffa | ingest-generated | eager | 1902.360 (1895.852–1939.711) | 1870.250 |
| proto-mixed-full | buffa | ingest-generated | view | 1644.249 (1627.160–1713.756) | 1572.750 |
| proto-mixed-full | owned | ingest-generated | eager | 3341.974 (3334.974–3382.412) | 2976.000 |
| proto-mixed-full | owned | ingest-generated | view | 3336.278 (3322.328–3377.838) | 2976.000 |
| proto-mixed-full | generated-owned | ingest | eager | 701.384 (696.341–702.054) | 1800.500 |
| proto-mixed-full | generated-owned | ingest | view | 699.889 (698.389–707.465) | 1800.500 |
