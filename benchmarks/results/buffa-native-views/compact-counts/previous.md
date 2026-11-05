# Interleaved compact-count comparison

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | previous | 739.822 (735.604–745.902) | 445.500 |
| proto-mixed | buffa | ingest | candidate | 775.699 (774.841–822.953) | 421.500 |
| proto-mixed | buffa | ingest-generated | previous | 621.271 (617.259–637.529) | 341.500 |
| proto-mixed | buffa | ingest-generated | candidate | 645.080 (641.268–652.510) | 317.500 |
| proto-mixed-full | buffa | ingest | previous | 1866.033 (1862.371–1899.790) | 1406.250 |
| proto-mixed-full | buffa | ingest | candidate | 1900.329 (1898.191–1916.011) | 1382.250 |
| proto-scalar | buffa | ingest-generated | previous | 50.205 (50.056–50.485) | 80.000 |
| proto-scalar | buffa | ingest-generated | candidate | 49.794 (49.592–50.072) | 80.000 |
| proto-mixed | generated-owned | ingest | previous | 660.244 (646.876–669.780) | 1800.500 |
| proto-mixed | generated-owned | ingest | candidate | 647.583 (643.258–654.029) | 1800.500 |
| proto-mixed | stock | ingest | previous | 641.728 (631.903–643.524) | 1776.750 |
| proto-mixed | stock | ingest | candidate | 629.640 (624.620–638.634) | 1776.750 |
| proto-mixed-full | stock | ingest | previous | 682.716 (680.773–695.401) | 1776.750 |
| proto-mixed-full | stock | ingest | candidate | 667.244 (665.269–671.416) | 1776.750 |
| proto-mixed | buffa | interpret-generated | previous | 16.236 (16.207–16.563) | 2.000 |
| proto-mixed | buffa | interpret-generated | candidate | 16.676 (16.615–16.858) | 2.000 |
| proto-mixed | buffa | peak-generated | previous | 8.430 (8.413–8.451) | 1.000 |
| proto-mixed | buffa | peak-generated | candidate | 8.952 (8.852–9.069) | 1.000 |
| proto-mixed | stock | interpret-boundary | previous | 5.444 (5.387–5.583) | 2.000 |
| proto-mixed | stock | interpret-boundary | candidate | 5.488 (5.440–5.542) | 2.000 |
| proto-mixed | stock | peak-boundary | previous | 5.336 (5.280–5.449) | 1.000 |
| proto-mixed | stock | peak-boundary | candidate | 5.370 (5.190–5.460) | 1.000 |
| proto-mixed | generated-owned | peak | previous | 2.704 (2.701–2.714) | 1.000 |
| proto-mixed | generated-owned | peak | candidate | 2.709 (2.702–2.724) | 1.000 |
| proto-mixed-full | buffa | ingest-generated | previous | 1276.348 (1272.261–1293.473) | 1406.250 |
| proto-mixed-full | buffa | ingest-generated | candidate | 1306.973 (1304.652–1317.947) | 1382.250 |
| proto-mixed-full | owned | ingest-generated | previous | 3266.822 (3251.215–3295.346) | 2976.000 |
| proto-mixed-full | owned | ingest-generated | candidate | 3249.627 (3237.577–3261.271) | 2976.000 |
| proto-mixed-full | generated-owned | ingest | previous | 703.158 (682.476–711.782) | 1800.500 |
| proto-mixed-full | generated-owned | ingest | candidate | 690.161 (683.531–697.777) | 1800.500 |
