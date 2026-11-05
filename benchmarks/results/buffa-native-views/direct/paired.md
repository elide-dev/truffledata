# Same-binary native-view experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | eager | 1642.669 (1635.363–1650.235) | 1743.750 |
| proto-mixed | buffa | ingest | view | 907.372 (905.192–912.948) | 493.500 |
| proto-mixed | buffa | ingest-generated | eager | 1522.296 (1513.369–1523.159) | 1743.750 |
| proto-mixed | buffa | ingest-generated | view | 808.928 (806.252–826.190) | 493.500 |
| proto-mixed-full | buffa | ingest | eager | 2347.784 (2337.785–2369.474) | 1870.250 |
| proto-mixed-full | buffa | ingest | view | 2144.777 (2130.692–2160.456) | 1809.000 |
| proto-scalar | buffa | ingest-generated | eager | 55.757 (55.127–56.884) | 88.000 |
| proto-scalar | buffa | ingest-generated | view | 55.526 (55.396–55.745) | 88.000 |
| proto-mixed | generated-owned | ingest | eager | 642.912 (634.388–645.643) | 1800.500 |
| proto-mixed | generated-owned | ingest | view | 644.503 (642.255–652.956) | 1800.500 |
| proto-mixed | stock | ingest | eager | 634.595 (627.731–643.506) | 1776.750 |
| proto-mixed | stock | ingest | view | 630.261 (624.288–650.448) | 1776.750 |
| proto-mixed-full | stock | ingest | eager | 669.176 (664.083–678.747) | 1776.750 |
| proto-mixed-full | stock | ingest | view | 666.932 (661.485–676.843) | 1776.750 |
| proto-mixed | buffa | interpret-generated | eager | 17.440 (17.408–18.027) | 2.000 |
| proto-mixed | buffa | interpret-generated | view | 19.190 (19.137–19.441) | 2.000 |
| proto-mixed | buffa | peak-generated | eager | 6.796 (6.719–6.859) | 1.000 |
| proto-mixed | buffa | peak-generated | view | 9.664 (9.609–9.777) | 1.000 |
| proto-mixed | stock | interpret-boundary | eager | 5.513 (5.432–5.618) | 2.000 |
| proto-mixed | stock | interpret-boundary | view | 5.483 (5.425–5.495) | 2.000 |
| proto-mixed | stock | peak-boundary | eager | 5.305 (5.239–5.317) | 1.000 |
| proto-mixed | stock | peak-boundary | view | 5.240 (5.236–5.297) | 1.000 |
| proto-mixed | generated-owned | peak | eager | 2.761 (2.750–2.791) | 1.000 |
| proto-mixed | generated-owned | peak | view | 2.746 (2.741–2.783) | 1.000 |
| proto-mixed-full | buffa | ingest-generated | eager | 1913.778 (1879.930–1933.255) | 1870.250 |
| proto-mixed-full | buffa | ingest-generated | view | 1672.096 (1666.602–1700.367) | 1809.000 |
| proto-mixed-full | owned | ingest-generated | eager | 3369.612 (3331.301–3392.129) | 2976.000 |
| proto-mixed-full | owned | ingest-generated | view | 3339.771 (3320.237–3352.001) | 2976.000 |
| proto-mixed-full | generated-owned | ingest | eager | 687.635 (683.723–692.324) | 1800.500 |
| proto-mixed-full | generated-owned | ingest | view | 690.420 (681.659–695.658) | 1800.500 |
