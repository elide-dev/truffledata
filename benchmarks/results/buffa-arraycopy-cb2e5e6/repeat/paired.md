# Paired typed array-copy experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | baseline | 1975.275 (1868.655–1982.966) | 1727.750 |
| proto-mixed | buffa | ingest | candidate | 1820.735 (1804.403–1923.650) | 1727.750 |
| proto-mixed | buffa | ingest-generated | baseline | 1862.472 (1766.028–1887.298) | 1727.750 |
| proto-mixed | buffa | ingest-generated | candidate | 1788.473 (1712.647–1808.259) | 1727.750 |
| proto-mixed-full | buffa | ingest | baseline | 2697.551 (2552.604–2742.272) | 1854.250 |
| proto-mixed-full | buffa | ingest | candidate | 2633.269 (2507.460–2645.685) | 1854.250 |
| proto-scalar | buffa | ingest-generated | baseline | 53.197 (52.323–53.379) | 80.000 |
| proto-scalar | buffa | ingest-generated | candidate | 74.397 (74.343–80.067) | 80.000 |
| proto-mixed | generated-owned | ingest | baseline | 649.992 (644.138–680.024) | 1800.500 |
| proto-mixed | generated-owned | ingest | candidate | 685.771 (645.464–699.938) | 1800.500 |
| proto-mixed | stock | ingest | baseline | 675.637 (642.520–677.674) | 1776.750 |
| proto-mixed | stock | ingest | candidate | 627.352 (619.175–668.149) | 1776.750 |
