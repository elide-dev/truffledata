# Paired sparse-finalization experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | baseline | 1723.385 (1713.113–1729.719) | 1727.750 |
| proto-mixed | buffa | ingest | candidate | 1677.435 (1666.372–1697.094) | 1727.750 |
| proto-mixed | buffa | ingest-generated | baseline | 1599.083 (1597.531–1618.356) | 1727.750 |
| proto-mixed | buffa | ingest-generated | candidate | 1568.146 (1542.696–1601.427) | 1727.750 |
| proto-mixed-full | buffa | ingest | baseline | 2439.856 (2400.673–2477.185) | 1854.250 |
| proto-mixed-full | buffa | ingest | candidate | 2387.425 (2371.413–2397.748) | 1854.250 |
| proto-scalar | buffa | ingest-generated | baseline | 56.652 (56.587–56.832) | 80.000 |
| proto-scalar | buffa | ingest-generated | candidate | 52.526 (51.834–52.752) | 80.000 |
| proto-mixed | generated-owned | ingest | baseline | 656.632 (641.235–660.525) | 1800.500 |
| proto-mixed | generated-owned | ingest | candidate | 640.003 (639.519–643.702) | 1800.500 |
| proto-mixed | stock | ingest | baseline | 649.131 (644.707–661.441) | 1776.750 |
| proto-mixed | stock | ingest | candidate | 655.090 (645.399–671.197) | 1776.750 |
