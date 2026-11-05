# Paired native index reuse experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | baseline | 2013.798 (2004.198–2173.687) | 1727.750 |
| proto-mixed | buffa | ingest | candidate | 1980.758 (1865.593–2013.375) | 1727.750 |
| proto-mixed | buffa | ingest-generated | baseline | 1971.895 (1873.751–1982.168) | 1727.750 |
| proto-mixed | buffa | ingest-generated | candidate | 1865.858 (1773.635–1938.378) | 1727.750 |
| proto-mixed-full | buffa | ingest | baseline | 2877.501 (2703.368–2884.704) | 1854.250 |
| proto-mixed-full | buffa | ingest | candidate | 2723.849 (2562.918–2739.526) | 1854.250 |
| proto-scalar | buffa | ingest-generated | baseline | 53.019 (51.981–55.144) | 80.000 |
| proto-scalar | buffa | ingest-generated | candidate | 52.838 (52.171–55.532) | 80.000 |
| proto-mixed | generated-owned | ingest | baseline | 650.897 (638.349–671.387) | 1800.500 |
| proto-mixed | generated-owned | ingest | candidate | 658.173 (635.701–668.846) | 1800.500 |
| proto-mixed | stock | ingest | baseline | 678.795 (651.364–684.315) | 1776.750 |
| proto-mixed | stock | ingest | candidate | 664.923 (631.443–671.958) | 1776.750 |
