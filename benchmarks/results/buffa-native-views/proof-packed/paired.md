# Same-binary native-view experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | eager | 1625.907 (1613.508–1638.969) | 1735.750 |
| proto-mixed | buffa | ingest | view | 942.853 (937.736–965.952) | 485.500 |
| proto-mixed | buffa | ingest-generated | eager | 1510.293 (1497.866–1520.564) | 1735.750 |
| proto-mixed | buffa | ingest-generated | view | 836.246 (833.316–842.273) | 485.500 |
| proto-mixed-full | buffa | ingest | eager | 2327.557 (2312.987–2439.496) | 1862.250 |
| proto-mixed-full | buffa | ingest | view | 2188.368 (2171.944–2237.505) | 1801.000 |
| proto-scalar | buffa | ingest-generated | eager | 53.813 (53.211–54.601) | 88.000 |
| proto-scalar | buffa | ingest-generated | view | 53.713 (53.386–54.440) | 88.000 |
| proto-mixed | generated-owned | ingest | eager | 659.202 (654.703–666.485) | 1800.500 |
| proto-mixed | generated-owned | ingest | view | 669.357 (663.166–677.176) | 1800.500 |
| proto-mixed | stock | ingest | eager | 641.741 (635.300–651.679) | 1776.750 |
| proto-mixed | stock | ingest | view | 633.935 (627.536–657.304) | 1776.750 |
| proto-mixed-full | stock | ingest | eager | 682.521 (670.105–684.842) | 1776.750 |
| proto-mixed-full | stock | ingest | view | 687.884 (684.822–730.198) | 1776.750 |
| proto-mixed | buffa | interpret-generated | eager | 17.360 (17.236–17.476) | 2.000 |
| proto-mixed | buffa | interpret-generated | view | 19.970 (19.840–20.089) | 2.000 |
| proto-mixed | buffa | peak-generated | eager | 6.667 (6.600–6.784) | 1.000 |
| proto-mixed | buffa | peak-generated | view | 10.853 (10.674–10.936) | 1.000 |
| proto-mixed | stock | interpret-boundary | eager | 5.461 (5.433–5.547) | 2.000 |
| proto-mixed | stock | interpret-boundary | view | 5.470 (5.407–5.551) | 2.000 |
| proto-mixed | stock | peak-boundary | eager | 5.263 (5.204–5.331) | 1.000 |
| proto-mixed | stock | peak-boundary | view | 5.262 (5.198–5.409) | 1.000 |
| proto-mixed | generated-owned | peak | eager | 2.736 (2.720–2.773) | 1.000 |
| proto-mixed | generated-owned | peak | view | 2.727 (2.715–2.740) | 1.000 |
| proto-mixed-full | buffa | ingest-generated | eager | 1879.229 (1873.665–1895.296) | 1862.250 |
| proto-mixed-full | buffa | ingest-generated | view | 1707.358 (1701.399–1711.041) | 1801.000 |
| proto-mixed-full | owned | ingest-generated | eager | 3339.611 (3315.376–3442.994) | 2976.000 |
| proto-mixed-full | owned | ingest-generated | view | 3317.376 (3309.706–3366.801) | 2976.000 |
| proto-mixed-full | generated-owned | ingest | eager | 704.741 (694.399–718.261) | 1800.500 |
| proto-mixed-full | generated-owned | ingest | view | 703.189 (698.384–709.355) | 1800.500 |
