# Same-binary native-view experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | eager | 1634.461 (1615.232–1669.869) | 1735.750 |
| proto-mixed | buffa | ingest | view | 1141.409 (1133.735–1303.031) | 485.500 |
| proto-mixed | buffa | ingest-generated | eager | 1490.562 (1489.296–1507.670) | 1735.750 |
| proto-mixed | buffa | ingest-generated | view | 1018.074 (1013.068–1021.002) | 485.500 |
| proto-mixed-full | buffa | ingest | eager | 2329.462 (2309.928–2349.082) | 1862.250 |
| proto-mixed-full | buffa | ingest | view | 2371.629 (2365.587–2419.505) | 1801.000 |
| proto-scalar | buffa | ingest-generated | eager | 59.379 (58.932–60.019) | 88.000 |
| proto-scalar | buffa | ingest-generated | view | 59.338 (58.948–59.547) | 88.000 |
| proto-mixed | generated-owned | ingest | eager | 639.809 (635.830–642.659) | 1800.500 |
| proto-mixed | generated-owned | ingest | view | 641.095 (635.621–649.680) | 1800.500 |
| proto-mixed | stock | ingest | eager | 644.845 (637.982–676.381) | 1776.750 |
| proto-mixed | stock | ingest | view | 639.243 (635.971–650.481) | 1776.750 |
| proto-mixed-full | stock | ingest | eager | 686.021 (676.517–692.890) | 1776.750 |
| proto-mixed-full | stock | ingest | view | 680.231 (676.357–690.567) | 1776.750 |
| proto-mixed | buffa | interpret-generated | eager | 17.336 (17.275–17.525) | 2.000 |
| proto-mixed | buffa | interpret-generated | view | 19.862 (19.732–19.940) | 2.000 |
| proto-mixed | buffa | peak-generated | eager | 6.671 (6.628–6.705) | 1.000 |
| proto-mixed | buffa | peak-generated | view | 10.654 (10.585–10.926) | 1.000 |
| proto-mixed | stock | interpret-boundary | eager | 5.554 (5.380–5.566) | 2.000 |
| proto-mixed | stock | interpret-boundary | view | 5.526 (5.404–5.586) | 2.000 |
| proto-mixed | stock | peak-boundary | eager | 5.266 (5.185–5.425) | 1.000 |
| proto-mixed | stock | peak-boundary | view | 5.360 (5.289–5.376) | 1.000 |
| proto-mixed | generated-owned | peak | eager | 2.702 (2.698–2.773) | 1.000 |
| proto-mixed | generated-owned | peak | view | 2.715 (2.703–2.731) | 1.000 |
| proto-mixed-full | buffa | ingest-generated | eager | 1877.015 (1872.536–1891.194) | 1862.250 |
| proto-mixed-full | buffa | ingest-generated | view | 1865.012 (1862.494–1891.901) | 1801.000 |
| proto-mixed-full | owned | ingest-generated | eager | 3367.805 (3340.426–3390.561) | 2976.000 |
| proto-mixed-full | owned | ingest-generated | view | 3343.749 (3339.371–3403.502) | 2976.000 |
| proto-mixed-full | generated-owned | ingest | eager | 684.696 (680.340–689.136) | 1800.500 |
| proto-mixed-full | generated-owned | ingest | view | 690.376 (686.572–698.867) | 1800.500 |
