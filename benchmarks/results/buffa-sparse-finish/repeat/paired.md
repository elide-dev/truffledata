# Paired sparse-finalization experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | baseline | 1713.961 (1712.932–1716.812) | 1727.750 |
| proto-mixed | buffa | ingest | candidate | 1666.234 (1659.115–1685.712) | 1727.750 |
| proto-mixed | buffa | ingest-generated | baseline | 1607.285 (1596.540–1615.946) | 1727.750 |
| proto-mixed | buffa | ingest-generated | candidate | 1556.765 (1544.277–1586.034) | 1727.750 |
| proto-mixed-full | buffa | ingest | baseline | 2421.256 (2412.192–2460.038) | 1854.250 |
| proto-mixed-full | buffa | ingest | candidate | 2372.882 (2367.449–2383.725) | 1854.250 |
| proto-scalar | buffa | ingest-generated | baseline | 57.524 (56.928–57.609) | 80.000 |
| proto-scalar | buffa | ingest-generated | candidate | 52.481 (52.188–52.895) | 80.000 |
| proto-mixed | generated-owned | ingest | baseline | 643.334 (638.116–648.612) | 1800.500 |
| proto-mixed | generated-owned | ingest | candidate | 639.641 (639.178–647.541) | 1800.500 |
| proto-mixed | stock | ingest | baseline | 642.445 (631.666–644.438) | 1776.750 |
| proto-mixed | stock | ingest | candidate | 655.839 (643.763–666.717) | 1776.750 |
