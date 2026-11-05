# Interleaved count-index comparison

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | previous | 813.564 (810.712–815.483) | 421.500 |
| proto-mixed | buffa | ingest | candidate | 740.269 (738.166–746.820) | 445.500 |
| proto-mixed | buffa | ingest-generated | previous | 702.951 (699.570–723.623) | 421.500 |
| proto-mixed | buffa | ingest-generated | candidate | 616.153 (614.610–623.330) | 341.500 |
| proto-mixed-full | buffa | ingest | previous | 1911.874 (1906.799–1956.188) | 1382.250 |
| proto-mixed-full | buffa | ingest | candidate | 1868.427 (1857.943–1922.817) | 1406.250 |
| proto-scalar | buffa | ingest-generated | previous | 53.917 (53.515–54.193) | 80.000 |
| proto-scalar | buffa | ingest-generated | candidate | 50.340 (50.187–51.194) | 80.000 |
| proto-mixed | generated-owned | ingest | previous | 665.630 (661.170–669.919) | 1800.500 |
| proto-mixed | generated-owned | ingest | candidate | 652.343 (650.026–658.400) | 1800.500 |
| proto-mixed | stock | ingest | previous | 644.840 (640.507–646.602) | 1776.750 |
| proto-mixed | stock | ingest | candidate | 641.543 (639.497–643.052) | 1776.750 |
| proto-mixed-full | stock | ingest | previous | 679.161 (673.053–691.693) | 1776.750 |
| proto-mixed-full | stock | ingest | candidate | 681.066 (675.549–687.404) | 1776.750 |
| proto-mixed | buffa | interpret-generated | previous | 16.810 (16.719–16.923) | 2.000 |
| proto-mixed | buffa | interpret-generated | candidate | 16.280 (16.185–16.319) | 2.000 |
| proto-mixed | buffa | peak-generated | previous | 9.341 (9.281–9.419) | 1.000 |
| proto-mixed | buffa | peak-generated | candidate | 8.441 (8.351–8.498) | 1.000 |
| proto-mixed | stock | interpret-boundary | previous | 5.435 (5.404–5.630) | 2.000 |
| proto-mixed | stock | interpret-boundary | candidate | 5.428 (5.396–5.638) | 2.000 |
| proto-mixed | stock | peak-boundary | previous | 5.296 (5.268–5.435) | 1.000 |
| proto-mixed | stock | peak-boundary | candidate | 5.273 (5.237–5.329) | 1.000 |
| proto-mixed | generated-owned | peak | previous | 2.708 (2.701–2.723) | 1.000 |
| proto-mixed | generated-owned | peak | candidate | 2.717 (2.702–2.737) | 1.000 |
| proto-mixed-full | buffa | ingest-generated | previous | 1337.572 (1333.487–1344.271) | 1382.250 |
| proto-mixed-full | buffa | ingest-generated | candidate | 1284.491 (1275.561–1286.614) | 1406.250 |
| proto-mixed-full | owned | ingest-generated | previous | 3326.236 (3249.254–3340.560) | 2976.000 |
| proto-mixed-full | owned | ingest-generated | candidate | 3268.867 (3244.263–3293.594) | 2976.000 |
| proto-mixed-full | generated-owned | ingest | previous | 700.103 (699.201–711.099) | 1800.500 |
| proto-mixed-full | generated-owned | ingest | candidate | 683.604 (679.339–691.092) | 1800.500 |
