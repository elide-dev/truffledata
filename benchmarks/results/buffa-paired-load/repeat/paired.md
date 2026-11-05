# Paired native index load experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | baseline | 1669.735 (1659.193–1700.255) | 1727.750 |
| proto-mixed | buffa | ingest | candidate | 1580.758 (1578.652–1606.996) | 1727.750 |
| proto-mixed | buffa | ingest-generated | baseline | 1551.057 (1544.565–1554.986) | 1727.750 |
| proto-mixed | buffa | ingest-generated | candidate | 1471.010 (1461.625–1481.632) | 1727.750 |
| proto-mixed-full | buffa | ingest | baseline | 2375.126 (2371.836–2382.384) | 1854.250 |
| proto-mixed-full | buffa | ingest | candidate | 2305.050 (2285.318–2357.957) | 1854.250 |
| proto-scalar | buffa | ingest-generated | baseline | 56.947 (56.634–57.715) | 80.000 |
| proto-scalar | buffa | ingest-generated | candidate | 52.468 (52.212–53.829) | 80.000 |
| proto-mixed | generated-owned | ingest | baseline | 689.482 (684.254–690.503) | 1800.500 |
| proto-mixed | generated-owned | ingest | candidate | 631.808 (631.199–636.265) | 1800.500 |
| proto-mixed | stock | ingest | baseline | 641.748 (631.897–643.949) | 1776.750 |
| proto-mixed | stock | ingest | candidate | 622.983 (616.205–631.754) | 1776.750 |
