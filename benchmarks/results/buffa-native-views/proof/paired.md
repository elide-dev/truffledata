# Same-binary native-view experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | eager | 1631.609 (1621.400–1658.765) | 1743.750 |
| proto-mixed | buffa | ingest | view | 945.023 (939.596–957.432) | 493.500 |
| proto-mixed | buffa | ingest-generated | eager | 1507.029 (1501.377–1530.993) | 1743.750 |
| proto-mixed | buffa | ingest-generated | view | 831.129 (825.481–881.724) | 493.500 |
| proto-mixed-full | buffa | ingest | eager | 2331.441 (2320.237–2377.831) | 1870.250 |
| proto-mixed-full | buffa | ingest | view | 2217.297 (2212.355–2240.644) | 1809.000 |
| proto-scalar | buffa | ingest-generated | eager | 55.204 (54.841–55.402) | 88.000 |
| proto-scalar | buffa | ingest-generated | view | 55.302 (54.924–55.586) | 88.000 |
| proto-mixed | generated-owned | ingest | eager | 667.055 (666.403–682.980) | 1800.500 |
| proto-mixed | generated-owned | ingest | view | 672.458 (661.287–677.658) | 1800.500 |
| proto-mixed | stock | ingest | eager | 649.440 (633.097–659.214) | 1776.750 |
| proto-mixed | stock | ingest | view | 648.210 (641.363–656.763) | 1776.750 |
| proto-mixed-full | stock | ingest | eager | 677.523 (676.013–681.362) | 1776.750 |
| proto-mixed-full | stock | ingest | view | 670.336 (661.214–690.931) | 1776.750 |
| proto-mixed | buffa | interpret-generated | eager | 17.384 (17.337–17.509) | 2.000 |
| proto-mixed | buffa | interpret-generated | view | 19.882 (19.813–19.958) | 2.000 |
| proto-mixed | buffa | peak-generated | eager | 6.853 (6.662–6.923) | 1.000 |
| proto-mixed | buffa | peak-generated | view | 10.744 (10.682–10.980) | 1.000 |
| proto-mixed | stock | interpret-boundary | eager | 5.518 (5.451–5.627) | 2.000 |
| proto-mixed | stock | interpret-boundary | view | 5.508 (5.472–5.531) | 2.000 |
| proto-mixed | stock | peak-boundary | eager | 5.445 (5.314–5.846) | 1.000 |
| proto-mixed | stock | peak-boundary | view | 5.338 (5.202–5.404) | 1.000 |
| proto-mixed | generated-owned | peak | eager | 2.755 (2.751–2.757) | 1.000 |
| proto-mixed | generated-owned | peak | view | 2.756 (2.751–2.762) | 1.000 |
| proto-mixed-full | buffa | ingest-generated | eager | 1883.372 (1882.494–1898.046) | 1870.250 |
| proto-mixed-full | buffa | ingest-generated | view | 1723.533 (1709.615–1732.149) | 1809.000 |
| proto-mixed-full | owned | ingest-generated | eager | 3334.516 (3312.842–3435.539) | 2976.000 |
| proto-mixed-full | owned | ingest-generated | view | 3311.676 (3305.766–3362.346) | 2976.000 |
| proto-mixed-full | generated-owned | ingest | eager | 715.722 (712.053–721.763) | 1800.500 |
| proto-mixed-full | generated-owned | ingest | view | 715.348 (707.059–722.682) | 1800.500 |
