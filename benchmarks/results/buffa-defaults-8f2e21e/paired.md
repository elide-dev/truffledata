# Paired contract-validation experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | baseline | 2194.741 (2080.242–2252.182) | 1727.750 |
| proto-mixed | buffa | ingest | candidate | 2100.537 (1995.367–2110.389) | 1727.750 |
| proto-mixed | buffa | ingest-generated | baseline | 2089.918 (2002.974–2135.941) | 1727.750 |
| proto-mixed | buffa | ingest-generated | candidate | 1969.478 (1861.939–1988.296) | 1727.750 |
| proto-mixed-full | buffa | ingest | baseline | 2786.645 (2784.643–2950.949) | 1854.250 |
| proto-mixed-full | buffa | ingest | candidate | 2859.040 (2716.842–2893.012) | 1854.250 |
| proto-scalar | buffa | ingest-generated | baseline | 53.999 (52.875–55.262) | 80.000 |
| proto-scalar | buffa | ingest-generated | candidate | 55.244 (52.635–55.918) | 80.000 |
| proto-mixed | generated-owned | ingest | baseline | 636.299 (635.021–646.577) | 1800.500 |
| proto-mixed | generated-owned | ingest | candidate | 685.626 (639.415–690.882) | 1800.500 |
| proto-mixed | stock | ingest | baseline | 647.382 (635.690–690.507) | 1776.750 |
| proto-mixed | stock | ingest | candidate | 673.952 (649.267–678.897) | 1776.750 |
