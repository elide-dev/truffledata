# Same-binary native-view experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | eager | 1632.895 (1625.624–1641.411) | 1743.750 |
| proto-mixed | buffa | ingest | view | 886.191 (874.008–905.334) | 501.500 |
| proto-mixed | buffa | ingest-generated | eager | 1519.250 (1515.279–1540.309) | 1743.750 |
| proto-mixed | buffa | ingest-generated | view | 765.037 (761.176–772.309) | 501.500 |
| proto-mixed-full | buffa | ingest | eager | 2321.211 (2306.749–2334.205) | 1870.250 |
| proto-mixed-full | buffa | ingest | view | 1980.120 (1969.224–2055.504) | 1572.750 |
| proto-scalar | buffa | ingest-generated | eager | 52.150 (51.948–52.324) | 88.000 |
| proto-scalar | buffa | ingest-generated | view | 52.654 (52.163–53.164) | 88.000 |
| proto-mixed | generated-owned | ingest | eager | 650.319 (643.818–660.933) | 1800.500 |
| proto-mixed | generated-owned | ingest | view | 647.986 (644.420–651.718) | 1800.500 |
| proto-mixed | stock | ingest | eager | 637.849 (632.176–646.393) | 1776.750 |
| proto-mixed | stock | ingest | view | 654.957 (646.235–659.996) | 1776.750 |
| proto-mixed-full | stock | ingest | eager | 679.231 (674.974–709.421) | 1776.750 |
| proto-mixed-full | stock | ingest | view | 678.994 (675.622–696.473) | 1776.750 |
| proto-mixed | buffa | interpret-generated | eager | 15.463 (15.314–15.547) | 2.000 |
| proto-mixed | buffa | interpret-generated | view | 16.851 (16.773–16.859) | 2.000 |
| proto-mixed | buffa | peak-generated | eager | 6.800 (6.689–6.990) | 1.000 |
| proto-mixed | buffa | peak-generated | view | 9.413 (9.353–9.471) | 1.000 |
| proto-mixed | stock | interpret-boundary | eager | 5.505 (5.411–5.719) | 2.000 |
| proto-mixed | stock | interpret-boundary | view | 5.514 (5.450–5.527) | 2.000 |
| proto-mixed | stock | peak-boundary | eager | 5.337 (5.220–5.382) | 1.000 |
| proto-mixed | stock | peak-boundary | view | 5.301 (5.256–5.386) | 1.000 |
| proto-mixed | generated-owned | peak | eager | 2.703 (2.700–2.769) | 1.000 |
| proto-mixed | generated-owned | peak | view | 2.765 (2.710–2.773) | 1.000 |
| proto-mixed-full | buffa | ingest-generated | eager | 1804.986 (1796.249–1824.226) | 1870.250 |
| proto-mixed-full | buffa | ingest-generated | view | 1395.468 (1386.644–1421.371) | 1572.750 |
| proto-mixed-full | owned | ingest-generated | eager | 3221.578 (3217.858–3274.185) | 2976.000 |
| proto-mixed-full | owned | ingest-generated | view | 3246.495 (3239.384–3315.513) | 2976.000 |
| proto-mixed-full | generated-owned | ingest | eager | 697.269 (691.588–712.073) | 1800.500 |
| proto-mixed-full | generated-owned | ingest | view | 690.879 (688.494–701.215) | 1800.500 |
