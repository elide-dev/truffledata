# Paired separate binary-loop experiment (rejected)

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | baseline | 1715.280 (1705.296–1812.947) | 1727.750 |
| proto-mixed | buffa | ingest | candidate | 1678.170 (1671.893–1785.621) | 1727.750 |
| proto-mixed | buffa | ingest-generated | baseline | 1688.884 (1604.867–1704.216) | 1727.750 |
| proto-mixed | buffa | ingest-generated | candidate | 1560.324 (1556.857–1649.941) | 1727.750 |
| proto-mixed-full | buffa | ingest | baseline | 2421.322 (2405.496–2587.920) | 1854.250 |
| proto-mixed-full | buffa | ingest | candidate | 2483.109 (2465.618–2541.195) | 1854.250 |
| proto-scalar | buffa | ingest-generated | baseline | 54.719 (52.248–55.875) | 80.000 |
| proto-scalar | buffa | ingest-generated | candidate | 55.048 (52.121–55.630) | 80.000 |
| proto-mixed | generated-owned | ingest | baseline | 640.079 (634.675–686.240) | 1800.500 |
| proto-mixed | generated-owned | ingest | candidate | 644.928 (634.752–674.418) | 1800.500 |
| proto-mixed | stock | ingest | baseline | 677.355 (630.960–679.782) | 1776.750 |
| proto-mixed | stock | ingest | candidate | 618.930 (605.508–654.778) | 1776.750 |
