# Paired contract-validation experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | baseline | 2496.946 (2361.946–2576.815) | 1727.750 |
| proto-mixed | buffa | ingest | candidate | 2115.371 (2085.427–2252.783) | 1727.750 |
| proto-mixed | buffa | ingest-generated | baseline | 2303.790 (2254.774–2416.011) | 1727.750 |
| proto-mixed | buffa | ingest-generated | candidate | 1979.798 (1972.153–2079.971) | 1727.750 |
| proto-mixed-full | buffa | ingest | baseline | 3098.027 (3049.816–3283.756) | 1854.250 |
| proto-mixed-full | buffa | ingest | candidate | 2941.413 (2815.979–2978.458) | 1854.250 |
| proto-scalar | buffa | ingest-generated | baseline | 56.901 (54.380–58.196) | 80.000 |
| proto-scalar | buffa | ingest-generated | candidate | 59.449 (57.406–60.660) | 80.000 |
| proto-mixed | generated-owned | ingest | baseline | 689.027 (658.683–710.558) | 1800.500 |
| proto-mixed | generated-owned | ingest | candidate | 640.676 (636.666–691.063) | 1800.500 |
| proto-mixed | stock | ingest | baseline | 667.155 (665.165–699.040) | 1776.750 |
| proto-mixed | stock | ingest | candidate | 651.225 (637.766–678.969) | 1776.750 |
