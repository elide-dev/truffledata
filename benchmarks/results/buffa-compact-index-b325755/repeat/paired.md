# Paired compact native index experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | baseline | 1722.593 (1718.088–1824.418) | 1727.750 |
| proto-mixed | buffa | ingest | candidate | 1715.060 (1707.268–1725.816) | 1727.750 |
| proto-mixed | buffa | ingest-generated | baseline | 1693.710 (1604.403–1705.143) | 1727.750 |
| proto-mixed | buffa | ingest-generated | candidate | 1679.919 (1595.798–1683.459) | 1727.750 |
| proto-mixed-full | buffa | ingest | baseline | 2559.799 (2448.212–2562.346) | 1854.250 |
| proto-mixed-full | buffa | ingest | candidate | 2550.168 (2525.733–2564.654) | 1854.250 |
| proto-scalar | buffa | ingest-generated | baseline | 53.153 (52.685–55.643) | 80.000 |
| proto-scalar | buffa | ingest-generated | candidate | 54.179 (52.406–55.007) | 80.000 |
| proto-mixed | generated-owned | ingest | baseline | 636.392 (635.979–668.693) | 1800.500 |
| proto-mixed | generated-owned | ingest | candidate | 672.940 (642.143–680.117) | 1800.500 |
| proto-mixed | stock | ingest | baseline | 645.263 (631.561–673.330) | 1776.750 |
| proto-mixed | stock | ingest | candidate | 641.080 (631.295–680.802) | 1776.750 |
