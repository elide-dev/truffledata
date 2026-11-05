# Same-binary native-view experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | eager | 1649.445 (1636.675–1669.746) | 1743.750 |
| proto-mixed | buffa | ingest | view | 937.198 (933.265–950.181) | 501.500 |
| proto-mixed | buffa | ingest-generated | eager | 1528.020 (1519.839–1537.436) | 1743.750 |
| proto-mixed | buffa | ingest-generated | view | 843.824 (838.618–844.923) | 501.500 |
| proto-mixed-full | buffa | ingest | eager | 2344.928 (2323.799–2392.956) | 1870.250 |
| proto-mixed-full | buffa | ingest | view | 2159.417 (2131.116–2209.648) | 1572.750 |
| proto-scalar | buffa | ingest-generated | eager | 54.064 (53.454–54.608) | 88.000 |
| proto-scalar | buffa | ingest-generated | view | 54.175 (53.819–54.646) | 88.000 |
| proto-mixed | generated-owned | ingest | eager | 651.372 (645.655–661.925) | 1800.500 |
| proto-mixed | generated-owned | ingest | view | 653.556 (651.648–670.453) | 1800.500 |
| proto-mixed | stock | ingest | eager | 639.479 (634.592–657.887) | 1776.750 |
| proto-mixed | stock | ingest | view | 640.773 (637.482–649.211) | 1776.750 |
| proto-mixed-full | stock | ingest | eager | 686.575 (684.014–704.264) | 1776.750 |
| proto-mixed-full | stock | ingest | view | 690.983 (675.563–703.418) | 1776.750 |
| proto-mixed | buffa | interpret-generated | eager | 17.497 (17.321–17.543) | 2.000 |
| proto-mixed | buffa | interpret-generated | view | 19.155 (18.869–19.204) | 2.000 |
| proto-mixed | buffa | peak-generated | eager | 6.722 (6.687–6.830) | 1.000 |
| proto-mixed | buffa | peak-generated | view | 9.235 (9.190–9.508) | 1.000 |
| proto-mixed | stock | interpret-boundary | eager | 5.385 (5.351–5.548) | 2.000 |
| proto-mixed | stock | interpret-boundary | view | 5.420 (5.317–5.535) | 2.000 |
| proto-mixed | stock | peak-boundary | eager | 5.302 (5.240–5.361) | 1.000 |
| proto-mixed | stock | peak-boundary | view | 5.363 (5.296–5.386) | 1.000 |
| proto-mixed | generated-owned | peak | eager | 2.755 (2.706–2.760) | 1.000 |
| proto-mixed | generated-owned | peak | view | 2.754 (2.702–2.838) | 1.000 |
| proto-mixed-full | buffa | ingest-generated | eager | 1898.323 (1879.276–1933.184) | 1870.250 |
| proto-mixed-full | buffa | ingest-generated | view | 1672.537 (1644.776–1677.563) | 1572.750 |
| proto-mixed-full | owned | ingest-generated | eager | 3343.097 (3307.391–3400.428) | 2976.000 |
| proto-mixed-full | owned | ingest-generated | view | 3351.316 (3321.389–3372.246) | 2976.000 |
| proto-mixed-full | generated-owned | ingest | eager | 701.630 (695.431–708.174) | 1800.500 |
| proto-mixed-full | generated-owned | ingest | view | 698.614 (696.155–712.773) | 1800.500 |
