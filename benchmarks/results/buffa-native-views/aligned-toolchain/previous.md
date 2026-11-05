# Interleaved aligned-toolchain comparison

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | previous | 737.326 (732.726–741.056) | 445.500 |
| proto-mixed | buffa | ingest | candidate | 750.236 (742.739–760.077) | 445.500 |
| proto-mixed | buffa | ingest-generated | previous | 621.784 (618.696–623.946) | 341.500 |
| proto-mixed | buffa | ingest-generated | candidate | 659.404 (653.166–670.089) | 341.500 |
| proto-mixed-full | buffa | ingest | previous | 1890.073 (1866.669–1928.359) | 1406.250 |
| proto-mixed-full | buffa | ingest | candidate | 1876.696 (1861.957–1901.222) | 1406.250 |
| proto-scalar | buffa | ingest-generated | previous | 52.046 (52.009–52.271) | 80.000 |
| proto-scalar | buffa | ingest-generated | candidate | 50.113 (49.735–50.686) | 80.000 |
| proto-mixed | generated-owned | ingest | previous | 659.428 (651.588–674.373) | 1800.500 |
| proto-mixed | generated-owned | ingest | candidate | 661.914 (648.271–667.493) | 1800.500 |
| proto-mixed | stock | ingest | previous | 651.663 (644.389–673.455) | 1776.750 |
| proto-mixed | stock | ingest | candidate | 652.602 (645.268–658.369) | 1776.750 |
| proto-mixed-full | stock | ingest | previous | 688.620 (682.137–699.370) | 1776.750 |
| proto-mixed-full | stock | ingest | candidate | 683.151 (670.084–694.527) | 1776.750 |
| proto-mixed | buffa | interpret-generated | previous | 16.478 (16.143–16.528) | 2.000 |
| proto-mixed | buffa | interpret-generated | candidate | 16.367 (16.290–16.505) | 2.000 |
| proto-mixed | buffa | peak-generated | previous | 8.445 (8.337–8.527) | 1.000 |
| proto-mixed | buffa | peak-generated | candidate | 8.438 (8.376–8.555) | 1.000 |
| proto-mixed | stock | interpret-boundary | previous | 5.454 (5.398–5.480) | 2.000 |
| proto-mixed | stock | interpret-boundary | candidate | 5.444 (5.408–5.546) | 2.000 |
| proto-mixed | stock | peak-boundary | previous | 5.365 (5.188–5.431) | 1.000 |
| proto-mixed | stock | peak-boundary | candidate | 5.352 (5.316–5.718) | 1.000 |
| proto-mixed | generated-owned | peak | previous | 2.749 (2.734–2.786) | 1.000 |
| proto-mixed | generated-owned | peak | candidate | 2.744 (2.739–2.784) | 1.000 |
| proto-mixed-full | buffa | ingest-generated | previous | 1284.427 (1280.972–1304.253) | 1406.250 |
| proto-mixed-full | buffa | ingest-generated | candidate | 1299.548 (1284.528–1309.250) | 1406.250 |
| proto-mixed-full | owned | ingest-generated | previous | 3262.589 (3231.800–3263.874) | 2976.000 |
| proto-mixed-full | owned | ingest-generated | candidate | 3284.785 (3273.028–3376.097) | 2976.000 |
| proto-mixed-full | generated-owned | ingest | previous | 682.921 (678.321–692.432) | 1800.500 |
| proto-mixed-full | generated-owned | ingest | candidate | 693.772 (678.515–698.340) | 1800.500 |
