# Same-binary native-view experiment

Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.

| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |
|---|---|---|---|---:|---:|
| proto-mixed | buffa | ingest | eager | 1731.543 (1729.225–1747.712) | 1735.750 |
| proto-mixed | buffa | ingest | view | 1159.766 (1147.236–1307.192) | 485.500 |
| proto-mixed | buffa | ingest-generated | eager | 1612.345 (1610.449–1637.322) | 1735.750 |
| proto-mixed | buffa | ingest-generated | view | 1032.556 (1028.816–1050.405) | 485.500 |
| proto-mixed-full | buffa | ingest | eager | 2468.952 (2457.380–2479.990) | 1862.250 |
| proto-mixed-full | buffa | ingest | view | 2667.163 (2613.551–2735.586) | 1801.000 |
| proto-scalar | buffa | ingest-generated | eager | 53.632 (53.390–53.880) | 88.000 |
| proto-scalar | buffa | ingest-generated | view | 53.719 (53.112–53.816) | 88.000 |
| proto-mixed | generated-owned | ingest | eager | 651.115 (642.429–659.987) | 1800.500 |
| proto-mixed | generated-owned | ingest | view | 648.511 (643.732–660.118) | 1800.500 |
| proto-mixed | stock | ingest | eager | 628.137 (620.003–632.705) | 1776.750 |
| proto-mixed | stock | ingest | view | 630.869 (618.139–636.694) | 1776.750 |
| proto-mixed-full | stock | ingest | eager | 672.987 (670.819–678.189) | 1776.750 |
| proto-mixed-full | stock | ingest | view | 679.136 (672.643–679.469) | 1776.750 |
| proto-mixed | buffa | interpret-generated | eager | 17.377 (17.344–17.620) | 2.000 |
| proto-mixed | buffa | interpret-generated | view | 19.852 (19.800–19.996) | 2.000 |
| proto-mixed | buffa | peak-generated | eager | 6.644 (6.613–6.721) | 1.000 |
| proto-mixed | buffa | peak-generated | view | 10.715 (10.573–10.782) | 1.000 |
| proto-mixed | stock | interpret-boundary | eager | 5.524 (5.403–5.551) | 2.000 |
| proto-mixed | stock | interpret-boundary | view | 5.400 (5.312–5.532) | 2.000 |
| proto-mixed | stock | peak-boundary | eager | 5.256 (5.238–5.334) | 1.000 |
| proto-mixed | stock | peak-boundary | view | 5.303 (5.229–5.368) | 1.000 |
| proto-mixed | generated-owned | peak | eager | 2.702 (2.699–2.709) | 1.000 |
| proto-mixed | generated-owned | peak | view | 2.700 (2.695–2.718) | 1.000 |
| proto-mixed-full | buffa | ingest-generated | eager | 2014.558 (1868.634–2019.219) | 1862.250 |
| proto-mixed-full | buffa | ingest-generated | view | 2017.561 (2008.010–2037.135) | 1801.000 |
| proto-mixed-full | owned | ingest-generated | eager | 3337.377 (3315.862–3356.035) | 2976.000 |
| proto-mixed-full | owned | ingest-generated | view | 3339.913 (3322.194–3378.265) | 2976.000 |
| proto-mixed-full | generated-owned | ingest | eager | 694.632 (693.388–700.504) | 1800.500 |
| proto-mixed-full | generated-owned | ingest | view | 697.341 (696.762–705.395) | 1800.500 |
