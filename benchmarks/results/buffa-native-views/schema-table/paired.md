# Same-binary native-view experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | eager | 1604.443 (1598.813–1616.955) | 1743.750 |
| proto-mixed | buffa | ingest | view | 925.018 (916.218–951.251) | 501.500 |
| proto-mixed | buffa | ingest-generated | eager | 1512.802 (1509.567–1520.402) | 1743.750 |
| proto-mixed | buffa | ingest-generated | view | 824.240 (821.607–867.791) | 501.500 |
| proto-mixed-full | buffa | ingest | eager | 2357.838 (2314.780–2473.744) | 1870.250 |
| proto-mixed-full | buffa | ingest | view | 2170.967 (2141.237–2196.098) | 1572.750 |
| proto-scalar | buffa | ingest-generated | eager | 56.381 (56.066–57.033) | 88.000 |
| proto-scalar | buffa | ingest-generated | view | 56.685 (55.999–56.764) | 88.000 |
| proto-mixed | generated-owned | ingest | eager | 651.509 (649.116–653.980) | 1800.500 |
| proto-mixed | generated-owned | ingest | view | 652.342 (648.314–668.009) | 1800.500 |
| proto-mixed | stock | ingest | eager | 645.534 (634.625–650.365) | 1776.750 |
| proto-mixed | stock | ingest | view | 641.833 (638.362–645.251) | 1776.750 |
| proto-mixed-full | stock | ingest | eager | 683.745 (679.953–691.490) | 1776.750 |
| proto-mixed-full | stock | ingest | view | 685.240 (674.192–698.541) | 1776.750 |
| proto-mixed | buffa | interpret-generated | eager | 17.479 (17.357–17.951) | 2.000 |
| proto-mixed | buffa | interpret-generated | view | 19.039 (19.013–19.241) | 2.000 |
| proto-mixed | buffa | peak-generated | eager | 6.641 (6.634–6.855) | 1.000 |
| proto-mixed | buffa | peak-generated | view | 9.237 (9.174–9.523) | 1.000 |
| proto-mixed | stock | interpret-boundary | eager | 5.475 (5.435–5.613) | 2.000 |
| proto-mixed | stock | interpret-boundary | view | 5.486 (5.327–5.610) | 2.000 |
| proto-mixed | stock | peak-boundary | eager | 5.198 (5.136–5.342) | 1.000 |
| proto-mixed | stock | peak-boundary | view | 5.330 (5.176–5.441) | 1.000 |
| proto-mixed | generated-owned | peak | eager | 2.712 (2.700–2.740) | 1.000 |
| proto-mixed | generated-owned | peak | view | 2.729 (2.701–2.765) | 1.000 |
| proto-mixed-full | buffa | ingest-generated | eager | 1875.296 (1869.167–1884.198) | 1870.250 |
| proto-mixed-full | buffa | ingest-generated | view | 1628.177 (1618.012–1709.589) | 1572.750 |
| proto-mixed-full | owned | ingest-generated | eager | 3319.165 (3315.870–3328.852) | 2976.000 |
| proto-mixed-full | owned | ingest-generated | view | 3341.099 (3319.062–3363.357) | 2976.000 |
| proto-mixed-full | generated-owned | ingest | eager | 695.106 (694.640–701.990) | 1800.500 |
| proto-mixed-full | generated-owned | ingest | view | 699.918 (693.895–714.224) | 1800.500 |
