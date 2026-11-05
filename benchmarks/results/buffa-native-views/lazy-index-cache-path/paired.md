# Same-binary native-view experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | eager | 1618.574 (1606.065–1630.741) | 1743.750 |
| proto-mixed | buffa | ingest | view | 891.845 (877.323–899.123) | 421.500 |
| proto-mixed | buffa | ingest-generated | eager | 1506.073 (1485.167–1520.883) | 1743.750 |
| proto-mixed | buffa | ingest-generated | view | 781.604 (767.931–791.884) | 421.500 |
| proto-mixed-full | buffa | ingest | eager | 2331.545 (2316.964–2368.639) | 1870.250 |
| proto-mixed-full | buffa | ingest | view | 1987.144 (1985.352–1999.039) | 1382.250 |
| proto-scalar | buffa | ingest-generated | eager | 54.422 (53.984–55.191) | 80.000 |
| proto-scalar | buffa | ingest-generated | view | 55.000 (54.584–55.208) | 80.000 |
| proto-mixed | generated-owned | ingest | eager | 671.009 (659.092–680.894) | 1800.500 |
| proto-mixed | generated-owned | ingest | view | 676.188 (659.516–681.303) | 1800.500 |
| proto-mixed | stock | ingest | eager | 653.362 (643.994–671.426) | 1776.750 |
| proto-mixed | stock | ingest | view | 645.704 (637.747–650.982) | 1776.750 |
| proto-mixed-full | stock | ingest | eager | 677.053 (670.441–682.144) | 1776.750 |
| proto-mixed-full | stock | ingest | view | 700.454 (678.219–718.681) | 1776.750 |
| proto-mixed | buffa | interpret-generated | eager | 15.406 (15.364–15.459) | 2.000 |
| proto-mixed | buffa | interpret-generated | view | 16.760 (16.656–17.070) | 2.000 |
| proto-mixed | buffa | peak-generated | eager | 6.688 (6.647–6.763) | 1.000 |
| proto-mixed | buffa | peak-generated | view | 9.264 (9.245–9.336) | 1.000 |
| proto-mixed | stock | interpret-boundary | eager | 5.445 (5.399–5.536) | 2.000 |
| proto-mixed | stock | interpret-boundary | view | 5.402 (5.385–5.492) | 2.000 |
| proto-mixed | stock | peak-boundary | eager | 5.344 (5.235–5.390) | 1.000 |
| proto-mixed | stock | peak-boundary | view | 5.302 (5.230–5.311) | 1.000 |
| proto-mixed | generated-owned | peak | eager | 2.712 (2.706–2.727) | 1.000 |
| proto-mixed | generated-owned | peak | view | 2.708 (2.707–2.715) | 1.000 |
| proto-mixed-full | buffa | ingest-generated | eager | 1780.594 (1765.370–1801.044) | 1870.250 |
| proto-mixed-full | buffa | ingest-generated | view | 1446.416 (1428.044–1457.212) | 1382.250 |
| proto-mixed-full | owned | ingest-generated | eager | 3269.197 (3243.157–3319.457) | 2976.000 |
| proto-mixed-full | owned | ingest-generated | view | 3251.167 (3234.312–3304.515) | 2976.000 |
| proto-mixed-full | generated-owned | ingest | eager | 707.505 (696.710–710.324) | 1800.500 |
| proto-mixed-full | generated-owned | ingest | view | 708.662 (694.029–720.967) | 1800.500 |
