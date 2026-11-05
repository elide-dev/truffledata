# Paired compiled profile-links experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | baseline | 1891.641 (1796.035–1905.650) | 1727.750 |
| proto-mixed | buffa | ingest | candidate | 1822.108 (1812.380–1840.998) | 1727.750 |
| proto-mixed | buffa | ingest-generated | baseline | 1781.070 (1700.589–1796.983) | 1727.750 |
| proto-mixed | buffa | ingest-generated | candidate | 1706.148 (1620.012–1717.426) | 1727.750 |
| proto-mixed-full | buffa | ingest | baseline | 2506.324 (2493.220–2640.566) | 1854.250 |
| proto-mixed-full | buffa | ingest | candidate | 2462.348 (2413.950–2596.747) | 1854.250 |
| proto-scalar | buffa | ingest-generated | baseline | 54.898 (52.184–55.680) | 80.000 |
| proto-scalar | buffa | ingest-generated | candidate | 54.832 (53.887–57.432) | 80.000 |
| proto-mixed | generated-owned | ingest | baseline | 686.891 (641.285–688.141) | 1800.500 |
| proto-mixed | generated-owned | ingest | candidate | 665.640 (634.705–669.287) | 1800.500 |
| proto-mixed | stock | ingest | baseline | 646.368 (643.241–685.760) | 1776.750 |
| proto-mixed | stock | ingest | candidate | 664.667 (627.511–670.427) | 1776.750 |
