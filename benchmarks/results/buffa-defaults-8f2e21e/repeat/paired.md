# Paired contract-validation experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | baseline | 2105.184 (2088.148–2252.648) | 1727.750 |
| proto-mixed | buffa | ingest | candidate | 2008.564 (1990.532–2149.041) | 1727.750 |
| proto-mixed | buffa | ingest-generated | baseline | 2037.678 (1998.052–2148.378) | 1727.750 |
| proto-mixed | buffa | ingest-generated | candidate | 1888.428 (1869.900–1995.381) | 1727.750 |
| proto-mixed-full | buffa | ingest | baseline | 2868.668 (2783.038–2944.931) | 1854.250 |
| proto-mixed-full | buffa | ingest | candidate | 2718.392 (2689.814–2873.454) | 1854.250 |
| proto-scalar | buffa | ingest-generated | baseline | 52.745 (51.969–54.926) | 80.000 |
| proto-scalar | buffa | ingest-generated | candidate | 52.959 (52.479–55.337) | 80.000 |
| proto-mixed | generated-owned | ingest | baseline | 644.000 (629.921–667.811) | 1800.500 |
| proto-mixed | generated-owned | ingest | candidate | 637.399 (635.528–688.849) | 1800.500 |
| proto-mixed | stock | ingest | baseline | 640.806 (626.924–646.053) | 1776.750 |
| proto-mixed | stock | ingest | candidate | 640.545 (634.795–682.743) | 1776.750 |
