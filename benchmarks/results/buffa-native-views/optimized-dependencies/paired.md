# Same-binary native-view experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | eager | 1540.306 (1538.035–1556.363) | 1743.750 |
| proto-mixed | buffa | ingest | view | 812.894 (809.034–818.359) | 421.500 |
| proto-mixed | buffa | ingest-generated | eager | 1432.912 (1419.674–1450.399) | 1743.750 |
| proto-mixed | buffa | ingest-generated | view | 724.721 (718.272–741.179) | 421.500 |
| proto-mixed-full | buffa | ingest | eager | 2273.860 (2261.705–2291.876) | 1870.250 |
| proto-mixed-full | buffa | ingest | view | 1921.073 (1888.354–2025.704) | 1382.250 |
| proto-scalar | buffa | ingest-generated | eager | 50.318 (50.071–50.685) | 80.000 |
| proto-scalar | buffa | ingest-generated | view | 50.197 (50.175–50.561) | 80.000 |
| proto-mixed | generated-owned | ingest | eager | 666.708 (663.432–670.617) | 1800.500 |
| proto-mixed | generated-owned | ingest | view | 667.036 (656.884–670.417) | 1800.500 |
| proto-mixed | stock | ingest | eager | 648.368 (642.513–653.836) | 1776.750 |
| proto-mixed | stock | ingest | view | 642.711 (639.298–654.840) | 1776.750 |
| proto-mixed-full | stock | ingest | eager | 681.093 (674.810–708.521) | 1776.750 |
| proto-mixed-full | stock | ingest | view | 678.172 (674.502–691.538) | 1776.750 |
| proto-mixed | buffa | interpret-generated | eager | 15.422 (15.405–15.574) | 2.000 |
| proto-mixed | buffa | interpret-generated | view | 16.862 (16.841–17.009) | 2.000 |
| proto-mixed | buffa | peak-generated | eager | 6.819 (6.719–6.886) | 1.000 |
| proto-mixed | buffa | peak-generated | view | 9.389 (9.343–9.475) | 1.000 |
| proto-mixed | stock | interpret-boundary | eager | 5.425 (5.395–5.513) | 2.000 |
| proto-mixed | stock | interpret-boundary | view | 5.474 (5.413–5.549) | 2.000 |
| proto-mixed | stock | peak-boundary | eager | 5.318 (5.291–5.426) | 1.000 |
| proto-mixed | stock | peak-boundary | view | 5.316 (5.274–5.337) | 1.000 |
| proto-mixed | generated-owned | peak | eager | 2.758 (2.749–2.777) | 1.000 |
| proto-mixed | generated-owned | peak | view | 2.755 (2.742–2.798) | 1.000 |
| proto-mixed-full | buffa | ingest-generated | eager | 1708.138 (1701.105–1746.876) | 1870.250 |
| proto-mixed-full | buffa | ingest-generated | view | 1362.077 (1346.793–1374.345) | 1382.250 |
| proto-mixed-full | owned | ingest-generated | eager | 3322.087 (3284.444–3341.692) | 2976.000 |
| proto-mixed-full | owned | ingest-generated | view | 3317.893 (3268.896–3324.363) | 2976.000 |
| proto-mixed-full | generated-owned | ingest | eager | 701.113 (697.810–706.077) | 1800.500 |
| proto-mixed-full | generated-owned | ingest | view | 703.375 (700.943–713.144) | 1800.500 |
