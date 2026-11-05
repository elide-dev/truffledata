# Paired native index load experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | baseline | 1671.449 (1670.808–1682.599) | 1727.750 |
| proto-mixed | buffa | ingest | candidate | 1585.201 (1582.279–1595.236) | 1727.750 |
| proto-mixed | buffa | ingest-generated | baseline | 1554.625 (1551.469–1568.856) | 1727.750 |
| proto-mixed | buffa | ingest-generated | candidate | 1474.894 (1463.542–1520.507) | 1727.750 |
| proto-mixed-full | buffa | ingest | baseline | 2400.019 (2375.320–2406.523) | 1854.250 |
| proto-mixed-full | buffa | ingest | candidate | 2307.450 (2292.976–2339.468) | 1854.250 |
| proto-scalar | buffa | ingest-generated | baseline | 52.187 (52.010–52.758) | 80.000 |
| proto-scalar | buffa | ingest-generated | candidate | 52.852 (52.468–53.458) | 80.000 |
| proto-mixed | generated-owned | ingest | baseline | 650.450 (646.258–673.159) | 1800.500 |
| proto-mixed | generated-owned | ingest | candidate | 633.523 (630.331–637.908) | 1800.500 |
| proto-mixed | stock | ingest | baseline | 652.150 (635.954–661.593) | 1776.750 |
| proto-mixed | stock | ingest | candidate | 619.322 (614.230–634.448) | 1776.750 |
