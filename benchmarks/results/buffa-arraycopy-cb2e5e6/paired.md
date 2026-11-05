# Paired typed array-copy experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | baseline | 1893.405 (1874.582–1987.770) | 1727.750 |
| proto-mixed | buffa | ingest | candidate | 1891.708 (1798.187–1912.131) | 1727.750 |
| proto-mixed | buffa | ingest-generated | baseline | 1797.527 (1786.978–1907.524) | 1727.750 |
| proto-mixed | buffa | ingest-generated | candidate | 1708.196 (1701.067–1789.488) | 1727.750 |
| proto-mixed-full | buffa | ingest | baseline | 2722.981 (2572.920–2770.263) | 1854.250 |
| proto-mixed-full | buffa | ingest | candidate | 2529.462 (2493.873–2659.079) | 1854.250 |
| proto-scalar | buffa | ingest-generated | baseline | 57.247 (54.746–58.475) | 80.000 |
| proto-scalar | buffa | ingest-generated | candidate | 57.501 (56.394–60.742) | 80.000 |
| proto-mixed | generated-owned | ingest | baseline | 635.837 (630.304–671.864) | 1800.500 |
| proto-mixed | generated-owned | ingest | candidate | 684.697 (646.114–700.612) | 1800.500 |
| proto-mixed | stock | ingest | baseline | 636.479 (634.161–666.379) | 1776.750 |
| proto-mixed | stock | ingest | candidate | 630.480 (623.552–672.199) | 1776.750 |
