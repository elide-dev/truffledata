# Same-binary native-view experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | eager | 1619.620 (1614.534–1624.547) | 1735.750 |
| proto-mixed | buffa | ingest | view | 1139.675 (1139.142–1161.091) | 477.500 |
| proto-mixed | buffa | ingest-generated | eager | 1502.455 (1499.309–1516.219) | 1735.750 |
| proto-mixed | buffa | ingest-generated | view | 1029.849 (1024.917–1035.158) | 477.500 |
| proto-mixed-full | buffa | ingest | eager | 2338.505 (2322.505–2364.806) | 1862.250 |
| proto-mixed-full | buffa | ingest | view | 2417.841 (2374.856–2443.337) | 2037.250 |
| proto-scalar | buffa | ingest-generated | eager | 53.648 (53.242–53.787) | 88.000 |
| proto-scalar | buffa | ingest-generated | view | 53.828 (53.252–54.314) | 88.000 |
| proto-mixed | generated-owned | ingest | eager | 645.013 (641.426–675.916) | 1800.500 |
| proto-mixed | generated-owned | ingest | view | 648.227 (638.282–650.046) | 1800.500 |
| proto-mixed | stock | ingest | eager | 633.120 (625.044–663.165) | 1776.750 |
| proto-mixed | stock | ingest | view | 631.299 (627.730–672.467) | 1776.750 |
| proto-mixed-full | stock | ingest | eager | 678.188 (672.800–712.243) | 1776.750 |
| proto-mixed-full | stock | ingest | view | 676.553 (675.224–679.760) | 1776.750 |
| proto-mixed | buffa | interpret-generated | eager | 17.167 (17.143–17.559) | 2.000 |
| proto-mixed | buffa | interpret-generated | view | 19.622 (19.387–19.711) | 2.000 |
| proto-mixed | buffa | peak-generated | eager | 6.654 (6.629–6.743) | 1.000 |
| proto-mixed | buffa | peak-generated | view | 11.596 (11.594–11.891) | 1.000 |
| proto-mixed | stock | interpret-boundary | eager | 5.492 (5.392–5.522) | 2.000 |
| proto-mixed | stock | interpret-boundary | view | 5.440 (5.359–5.519) | 2.000 |
| proto-mixed | stock | peak-boundary | eager | 5.305 (5.159–5.385) | 1.000 |
| proto-mixed | stock | peak-boundary | view | 5.256 (5.189–5.277) | 1.000 |
| proto-mixed | generated-owned | peak | eager | 2.702 (2.694–2.706) | 1.000 |
| proto-mixed | generated-owned | peak | view | 2.703 (2.696–2.712) | 1.000 |
| proto-mixed-full | buffa | ingest-generated | eager | 1879.199 (1868.332–1882.654) | 1862.250 |
| proto-mixed-full | buffa | ingest-generated | view | 1889.935 (1869.942–1903.493) | 2037.250 |
| proto-mixed-full | owned | ingest-generated | eager | 3334.616 (3328.085–3360.390) | 2976.000 |
| proto-mixed-full | owned | ingest-generated | view | 3340.502 (3328.294–3377.380) | 2976.000 |
| proto-mixed-full | generated-owned | ingest | eager | 701.192 (697.756–714.765) | 1800.500 |
| proto-mixed-full | generated-owned | ingest | view | 696.965 (694.953–698.988) | 1800.500 |
