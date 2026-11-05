# Same-binary native-view experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | eager | 1644.597 (1639.341–1659.478) | 1743.750 |
| proto-mixed | buffa | ingest | view | 899.551 (894.842–921.446) | 485.500 |
| proto-mixed | buffa | ingest-generated | eager | 1531.662 (1523.828–1535.391) | 1743.750 |
| proto-mixed | buffa | ingest-generated | view | 777.428 (774.165–793.141) | 485.500 |
| proto-mixed-full | buffa | ingest | eager | 2353.584 (2338.249–2383.858) | 1870.250 |
| proto-mixed-full | buffa | ingest | view | 2021.676 (2012.395–2030.235) | 1446.250 |
| proto-scalar | buffa | ingest-generated | eager | 54.771 (54.212–55.366) | 80.000 |
| proto-scalar | buffa | ingest-generated | view | 54.207 (54.054–54.781) | 80.000 |
| proto-mixed | generated-owned | ingest | eager | 655.655 (645.888–672.403) | 1800.500 |
| proto-mixed | generated-owned | ingest | view | 652.071 (647.226–672.667) | 1800.500 |
| proto-mixed | stock | ingest | eager | 657.642 (651.361–698.308) | 1776.750 |
| proto-mixed | stock | ingest | view | 662.005 (656.539–666.821) | 1776.750 |
| proto-mixed-full | stock | ingest | eager | 703.439 (698.680–709.044) | 1776.750 |
| proto-mixed-full | stock | ingest | view | 700.485 (698.194–706.371) | 1776.750 |
| proto-mixed | buffa | interpret-generated | eager | 15.398 (15.239–15.520) | 2.000 |
| proto-mixed | buffa | interpret-generated | view | 16.920 (16.838–16.968) | 2.000 |
| proto-mixed | buffa | peak-generated | eager | 6.726 (6.705–6.765) | 1.000 |
| proto-mixed | buffa | peak-generated | view | 9.390 (9.259–9.447) | 1.000 |
| proto-mixed | stock | interpret-boundary | eager | 5.404 (5.384–5.530) | 2.000 |
| proto-mixed | stock | interpret-boundary | view | 5.441 (5.355–5.588) | 2.000 |
| proto-mixed | stock | peak-boundary | eager | 5.263 (5.198–5.407) | 1.000 |
| proto-mixed | stock | peak-boundary | view | 5.244 (5.223–5.279) | 1.000 |
| proto-mixed | generated-owned | peak | eager | 2.721 (2.707–2.728) | 1.000 |
| proto-mixed | generated-owned | peak | view | 2.718 (2.709–2.741) | 1.000 |
| proto-mixed-full | buffa | ingest-generated | eager | 1834.056 (1818.004–1851.752) | 1870.250 |
| proto-mixed-full | buffa | ingest-generated | view | 1440.293 (1426.692–1447.479) | 1446.250 |
| proto-mixed-full | owned | ingest-generated | eager | 3259.980 (3239.494–3277.074) | 2976.000 |
| proto-mixed-full | owned | ingest-generated | view | 3259.061 (3252.281–3277.024) | 2976.000 |
| proto-mixed-full | generated-owned | ingest | eager | 690.291 (688.405–693.568) | 1800.500 |
| proto-mixed-full | generated-owned | ingest | view | 692.994 (685.130–699.466) | 1800.500 |
