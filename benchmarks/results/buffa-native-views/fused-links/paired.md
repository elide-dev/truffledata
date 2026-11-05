# Same-binary native-view experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | eager | 1619.851 (1611.652–1623.669) | 1743.750 |
| proto-mixed | buffa | ingest | view | 866.197 (862.104–890.793) | 501.500 |
| proto-mixed | buffa | ingest-generated | eager | 1517.505 (1514.984–1531.421) | 1743.750 |
| proto-mixed | buffa | ingest-generated | view | 765.479 (760.656–770.089) | 501.500 |
| proto-mixed-full | buffa | ingest | eager | 2340.777 (2324.571–2351.623) | 1870.250 |
| proto-mixed-full | buffa | ingest | view | 1968.394 (1954.007–2023.848) | 1572.750 |
| proto-scalar | buffa | ingest-generated | eager | 53.493 (53.358–53.954) | 88.000 |
| proto-scalar | buffa | ingest-generated | view | 53.418 (53.360–53.691) | 88.000 |
| proto-mixed | generated-owned | ingest | eager | 660.706 (652.318–673.802) | 1800.500 |
| proto-mixed | generated-owned | ingest | view | 659.432 (653.722–670.018) | 1800.500 |
| proto-mixed | stock | ingest | eager | 628.653 (621.563–631.354) | 1776.750 |
| proto-mixed | stock | ingest | view | 627.165 (623.241–639.972) | 1776.750 |
| proto-mixed-full | stock | ingest | eager | 674.997 (666.880–681.678) | 1776.750 |
| proto-mixed-full | stock | ingest | view | 673.197 (666.058–702.283) | 1776.750 |
| proto-mixed | buffa | interpret-generated | eager | 17.349 (17.306–17.523) | 2.000 |
| proto-mixed | buffa | interpret-generated | view | 18.967 (18.954–19.257) | 2.000 |
| proto-mixed | buffa | peak-generated | eager | 6.784 (6.705–6.812) | 1.000 |
| proto-mixed | buffa | peak-generated | view | 9.252 (9.194–9.360) | 1.000 |
| proto-mixed | stock | interpret-boundary | eager | 5.399 (5.372–5.445) | 2.000 |
| proto-mixed | stock | interpret-boundary | view | 5.481 (5.402–5.543) | 2.000 |
| proto-mixed | stock | peak-boundary | eager | 5.239 (5.225–5.434) | 1.000 |
| proto-mixed | stock | peak-boundary | view | 5.322 (5.228–5.423) | 1.000 |
| proto-mixed | generated-owned | peak | eager | 2.744 (2.705–2.756) | 1.000 |
| proto-mixed | generated-owned | peak | view | 2.723 (2.705–2.768) | 1.000 |
| proto-mixed-full | buffa | ingest-generated | eager | 1887.054 (1879.913–1903.092) | 1870.250 |
| proto-mixed-full | buffa | ingest-generated | view | 1477.575 (1474.670–1493.112) | 1572.750 |
| proto-mixed-full | owned | ingest-generated | eager | 3345.580 (3313.776–3379.950) | 2976.000 |
| proto-mixed-full | owned | ingest-generated | view | 3347.095 (3319.677–3368.411) | 2976.000 |
| proto-mixed-full | generated-owned | ingest | eager | 695.020 (692.355–702.428) | 1800.500 |
| proto-mixed-full | generated-owned | ingest | view | 700.492 (693.080–704.197) | 1800.500 |
