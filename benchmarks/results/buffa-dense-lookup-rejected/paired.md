# Paired dense field lookup experiment (rejected)

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | baseline | 1729.699 (1705.234–1815.150) | 1727.750 |
| proto-mixed | buffa | ingest | candidate | 1726.970 (1718.847–1738.610) | 1727.750 |
| proto-mixed | buffa | ingest-generated | baseline | 1662.209 (1581.584–1677.325) | 1727.750 |
| proto-mixed | buffa | ingest-generated | candidate | 1596.887 (1586.462–1676.561) | 1727.750 |
| proto-mixed-full | buffa | ingest | baseline | 2423.761 (2401.474–2593.498) | 1854.250 |
| proto-mixed-full | buffa | ingest | candidate | 2416.584 (2398.352–2445.111) | 1854.250 |
| proto-scalar | buffa | ingest-generated | baseline | 53.023 (52.465–55.434) | 80.000 |
| proto-scalar | buffa | ingest-generated | candidate | 55.475 (52.534–55.916) | 80.000 |
| proto-mixed | generated-owned | ingest | baseline | 639.974 (636.826–685.241) | 1800.500 |
| proto-mixed | generated-owned | ingest | candidate | 652.044 (645.710–688.624) | 1800.500 |
| proto-mixed | stock | ingest | baseline | 671.031 (649.764–678.135) | 1776.750 |
| proto-mixed | stock | ingest | candidate | 664.276 (629.551–677.667) | 1776.750 |
