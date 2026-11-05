# Paired typed array-copy experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | baseline | 1979.066 (1879.249–2033.655) | 1727.750 |
| proto-mixed | buffa | ingest | candidate | 1900.864 (1808.182–1911.581) | 1727.750 |
| proto-mixed | buffa | ingest-generated | baseline | 1879.156 (1784.873–1901.695) | 1727.750 |
| proto-mixed | buffa | ingest-generated | candidate | 1726.288 (1699.007–1809.573) | 1727.750 |
| proto-mixed-full | buffa | ingest | baseline | 2586.593 (2583.265–2743.308) | 1854.250 |
| proto-mixed-full | buffa | ingest | candidate | 2668.290 (2519.626–2696.342) | 1854.250 |
| proto-scalar | buffa | ingest-generated | baseline | 60.829 (58.163–61.242) | 80.000 |
| proto-scalar | buffa | ingest-generated | candidate | 56.396 (53.058–56.522) | 80.000 |
| proto-mixed | generated-owned | ingest | baseline | 694.748 (656.208–712.919) | 1800.500 |
| proto-mixed | generated-owned | ingest | candidate | 648.678 (640.276–680.391) | 1800.500 |
| proto-mixed | stock | ingest | baseline | 674.157 (640.876–687.022) | 1776.750 |
| proto-mixed | stock | ingest | candidate | 628.583 (624.170–676.229) | 1776.750 |
