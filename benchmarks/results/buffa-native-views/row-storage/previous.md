# Interleaved row-storage comparison

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | previous | 883.109 (876.822–904.493) | 501.500 |
| proto-mixed | buffa | ingest | candidate | 902.148 (889.711–915.178) | 485.500 |
| proto-mixed | buffa | ingest-generated | previous | 765.968 (762.009–772.252) | 501.500 |
| proto-mixed | buffa | ingest-generated | candidate | 782.686 (779.512–792.218) | 485.500 |
| proto-mixed-full | buffa | ingest | previous | 2001.221 (1984.124–2133.636) | 1572.750 |
| proto-mixed-full | buffa | ingest | candidate | 2037.995 (2005.356–2069.522) | 1446.250 |
| proto-scalar | buffa | ingest-generated | previous | 52.579 (52.211–52.710) | 88.000 |
| proto-scalar | buffa | ingest-generated | candidate | 54.382 (54.146–56.027) | 80.000 |
| proto-mixed | generated-owned | ingest | previous | 647.663 (644.021–660.624) | 1800.500 |
| proto-mixed | generated-owned | ingest | candidate | 652.104 (645.107–667.575) | 1800.500 |
| proto-mixed | stock | ingest | previous | 644.521 (641.523–678.853) | 1776.750 |
| proto-mixed | stock | ingest | candidate | 659.380 (654.043–664.527) | 1776.750 |
| proto-mixed-full | stock | ingest | previous | 685.963 (671.782–695.979) | 1776.750 |
| proto-mixed-full | stock | ingest | candidate | 703.830 (697.671–731.795) | 1776.750 |
| proto-mixed | buffa | interpret-generated | previous | 16.898 (16.836–16.949) | 2.000 |
| proto-mixed | buffa | interpret-generated | candidate | 16.978 (16.785–17.093) | 2.000 |
| proto-mixed | buffa | peak-generated | previous | 9.280 (9.241–9.343) | 1.000 |
| proto-mixed | buffa | peak-generated | candidate | 9.241 (9.207–9.430) | 1.000 |
| proto-mixed | stock | interpret-boundary | previous | 5.482 (5.451–5.549) | 2.000 |
| proto-mixed | stock | interpret-boundary | candidate | 5.411 (5.337–5.523) | 2.000 |
| proto-mixed | stock | peak-boundary | previous | 5.352 (5.180–5.405) | 1.000 |
| proto-mixed | stock | peak-boundary | candidate | 5.285 (5.276–5.344) | 1.000 |
| proto-mixed | generated-owned | peak | previous | 2.716 (2.704–2.733) | 1.000 |
| proto-mixed | generated-owned | peak | candidate | 2.721 (2.707–2.742) | 1.000 |
| proto-mixed-full | buffa | ingest-generated | previous | 1401.331 (1384.800–1410.803) | 1572.750 |
| proto-mixed-full | buffa | ingest-generated | candidate | 1433.959 (1423.387–1444.717) | 1446.250 |
| proto-mixed-full | owned | ingest-generated | previous | 3296.668 (3250.460–3311.720) | 2976.000 |
| proto-mixed-full | owned | ingest-generated | candidate | 3269.963 (3243.719–3312.076) | 2976.000 |
| proto-mixed-full | generated-owned | ingest | previous | 694.135 (686.452–711.364) | 1800.500 |
| proto-mixed-full | generated-owned | ingest | candidate | 689.869 (680.843–693.955) | 1800.500 |
