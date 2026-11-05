# Paired separate binary-loop experiment (rejected)

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | baseline | 1718.671 (1704.054–1807.569) | 1727.750 |
| proto-mixed | buffa | ingest | candidate | 1696.952 (1665.542–1767.574) | 1727.750 |
| proto-mixed | buffa | ingest-generated | baseline | 1616.289 (1597.023–1698.229) | 1727.750 |
| proto-mixed | buffa | ingest-generated | candidate | 1633.431 (1546.945–1655.961) | 1727.750 |
| proto-mixed-full | buffa | ingest | baseline | 2428.860 (2411.614–2559.435) | 1854.250 |
| proto-mixed-full | buffa | ingest | candidate | 2352.005 (2340.669–2511.157) | 1854.250 |
| proto-scalar | buffa | ingest-generated | baseline | 54.917 (52.024–55.724) | 80.000 |
| proto-scalar | buffa | ingest-generated | candidate | 58.662 (56.304–59.426) | 80.000 |
| proto-mixed | generated-owned | ingest | baseline | 675.483 (643.797–706.554) | 1800.500 |
| proto-mixed | generated-owned | ingest | candidate | 663.737 (628.962–686.365) | 1800.500 |
| proto-mixed | stock | ingest | baseline | 659.895 (632.465–662.848) | 1776.750 |
| proto-mixed | stock | ingest | candidate | 658.317 (618.234–670.916) | 1776.750 |
