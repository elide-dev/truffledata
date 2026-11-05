# Same-binary native-view experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | eager | 1604.086 (1597.093–1616.003) | 1735.750 |
| proto-mixed | buffa | ingest | view | 1119.052 (1107.124–1122.565) | 477.500 |
| proto-mixed | buffa | ingest-generated | eager | 1511.873 (1504.632–1525.311) | 1735.750 |
| proto-mixed | buffa | ingest-generated | view | 1014.547 (1008.467–1034.375) | 477.500 |
| proto-mixed-full | buffa | ingest | eager | 2304.785 (2291.896–2306.691) | 1862.250 |
| proto-mixed-full | buffa | ingest | view | 2343.670 (2321.136–2501.423) | 2037.250 |
| proto-scalar | buffa | ingest-generated | eager | 53.214 (53.051–53.899) | 88.000 |
| proto-scalar | buffa | ingest-generated | view | 53.313 (52.902–62.843) | 88.000 |
| proto-mixed | generated-owned | ingest | eager | 633.634 (628.611–639.482) | 1800.500 |
| proto-mixed | generated-owned | ingest | view | 634.706 (632.042–691.635) | 1800.500 |
| proto-mixed | stock | ingest | eager | 637.239 (633.457–668.970) | 1776.750 |
| proto-mixed | stock | ingest | view | 641.707 (640.059–650.839) | 1776.750 |
| proto-mixed-full | stock | ingest | eager | 668.057 (662.539–679.113) | 1776.750 |
| proto-mixed-full | stock | ingest | view | 668.199 (661.944–691.397) | 1776.750 |
| proto-mixed | buffa | interpret-generated | eager | 18.764 (18.653–18.861) | 2.000 |
| proto-mixed | buffa | interpret-generated | view | 24.045 (23.896–24.216) | 2.000 |
| proto-mixed | buffa | peak-generated | eager | 9.318 (9.178–9.421) | 1.000 |
| proto-mixed | buffa | peak-generated | view | 15.172 (15.060–15.550) | 1.000 |
| proto-mixed | stock | interpret-boundary | eager | 5.452 (5.377–5.519) | 2.000 |
| proto-mixed | stock | interpret-boundary | view | 5.414 (5.383–5.526) | 2.000 |
| proto-mixed | stock | peak-boundary | eager | 5.245 (5.190–5.376) | 1.000 |
| proto-mixed | stock | peak-boundary | view | 5.263 (5.225–5.299) | 1.000 |
| proto-mixed | generated-owned | peak | eager | 2.699 (2.696–2.701) | 1.000 |
| proto-mixed | generated-owned | peak | view | 2.698 (2.695–2.708) | 1.000 |
