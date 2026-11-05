# Same-binary native-view experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | eager | 1655.251 (1639.994–1676.692) | 1735.750 |
| proto-mixed | buffa | ingest | view | 1116.880 (1108.344–1121.947) | 477.500 |
| proto-mixed | buffa | ingest-generated | eager | 1543.870 (1510.187–1558.086) | 1735.750 |
| proto-mixed | buffa | ingest-generated | view | 1026.845 (1011.200–1046.333) | 477.500 |
| proto-mixed-full | buffa | ingest | eager | 2356.922 (2330.825–2398.487) | 1862.250 |
| proto-mixed-full | buffa | ingest | view | 2332.452 (2320.790–2365.067) | 2037.250 |
| proto-scalar | buffa | ingest-generated | eager | 55.403 (53.826–57.149) | 88.000 |
| proto-scalar | buffa | ingest-generated | view | 54.687 (53.925–58.018) | 88.000 |
| proto-mixed | generated-owned | ingest | eager | 635.171 (629.667–636.078) | 1800.500 |
| proto-mixed | generated-owned | ingest | view | 638.352 (629.544–645.112) | 1800.500 |
| proto-mixed | stock | ingest | eager | 633.640 (621.622–638.480) | 1776.750 |
| proto-mixed | stock | ingest | view | 628.195 (622.333–632.512) | 1776.750 |
| proto-mixed-full | stock | ingest | eager | 675.229 (667.973–676.833) | 1776.750 |
| proto-mixed-full | stock | ingest | view | 671.382 (663.557–677.270) | 1776.750 |
| proto-mixed | buffa | interpret-generated | eager | 18.654 (18.582–18.734) | 2.000 |
| proto-mixed | buffa | interpret-generated | view | 21.048 (20.774–21.056) | 2.000 |
| proto-mixed | buffa | peak-generated | eager | 9.271 (9.154–9.438) | 1.000 |
| proto-mixed | buffa | peak-generated | view | 12.930 (12.881–12.969) | 1.000 |
| proto-mixed | stock | interpret-boundary | eager | 5.526 (5.374–5.589) | 2.000 |
| proto-mixed | stock | interpret-boundary | view | 5.403 (5.383–5.633) | 2.000 |
| proto-mixed | stock | peak-boundary | eager | 5.229 (5.180–5.384) | 1.000 |
| proto-mixed | stock | peak-boundary | view | 5.277 (5.260–5.329) | 1.000 |
| proto-mixed | generated-owned | peak | eager | 2.699 (2.697–2.750) | 1.000 |
| proto-mixed | generated-owned | peak | view | 2.701 (2.698–2.725) | 1.000 |
