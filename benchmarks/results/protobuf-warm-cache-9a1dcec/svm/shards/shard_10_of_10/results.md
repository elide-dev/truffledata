# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | 855.7 | — | 865.4 (1.01×) | 1619.3 (1.89×) | 1314.1 (1.54×) | — | 2964.1 (3.46×) | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | 247345.2 | — | 273582.3 (1.11×) | 340534.9 (1.38×) | 338777.7 (1.37×) | — | 830636.9 (3.36×) | 269559.3 (1.09×) | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## baseline: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | 868.0 | — | 887.8 (1.02×) | 1285.5 (1.48×) | 1296.1 (1.49×) | — | 2920.5 (3.36×) | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | 247707.1 | — | 272990.6 (1.10×) | 355537.2 (1.44×) | 366369.8 (1.48×) | — | 837889.4 (3.38×) | 264563.0 (1.07×) | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | full | heap | stock | 855.750 (844.192–858.972) | 1.00 | 175.2 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | heap | generated-owned | 865.439 (862.362–865.659) | 1.01 | 173.2 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | heap | slab | 1619.276 (1314.736–18720.398) | 1.89 | 92.6 | 1504.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | heap | slab-native | 1314.108 (1302.777–1458.591) | 1.54 | 114.1 | 1504.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | heap | slab-lazy | 2964.116 (2915.807–2968.285) | 3.46 | 50.6 | 3295.25 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| candidate | large | resident | stock | 247345.223 (230102.857–260414.531) | 1.00 | 209.5 | 433009.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | resident | generated-owned | 273582.292 (265441.250–276131.979) | 1.11 | 189.4 | 460180.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | resident | slab | 340534.875 (334791.194–355643.250) | 1.38 | 152.2 | 317632.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| candidate | large | resident | slab-native | 338777.688 (338086.250–339244.875) | 1.37 | 153.0 | 317632.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| candidate | large | resident | slab-lazy | 830636.875 (798134.531–903081.719) | 3.36 | 62.4 | 834853.25 | 0.0 | 45619.8 / 281827.8 | 0.0 | 51822.3 |
| candidate | large | resident | slab-session | 269559.271 (261491.094–271263.750) | 1.09 | 192.2 | 91383.25 | 0.0 | 172771.8 / 0.0 | 207545.1 | 0.0 |
| baseline | full | heap | stock | 868.026 (857.947–896.203) | 1.00 | 172.7 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | heap | generated-owned | 887.819 (884.588–901.148) | 1.02 | 168.8 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | heap | slab | 1285.515 (1271.991–3233.518) | 1.48 | 116.6 | 1504.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | heap | slab-native | 1296.134 (1292.249–6056.391) | 1.49 | 115.7 | 1504.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | heap | slab-lazy | 2920.489 (2861.767–2926.896) | 3.36 | 51.3 | 3295.25 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| baseline | large | resident | stock | 247707.143 (238769.237–275892.448) | 1.00 | 209.2 | 433009.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | resident | generated-owned | 272990.573 (267390.990–273845.156) | 1.10 | 189.8 | 460180.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | resident | slab | 355537.250 (347750.938–361324.688) | 1.44 | 145.8 | 317632.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| baseline | large | resident | slab-native | 366369.750 (358841.625–372858.750) | 1.48 | 141.4 | 317632.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| baseline | large | resident | slab-lazy | 837889.375 (761902.500–875224.688) | 3.38 | 61.8 | 834853.25 | 0.0 | 45619.8 / 281827.8 | 0.0 | 51822.3 |
| baseline | large | resident | slab-session | 264562.964 (263004.786–264768.125) | 1.07 | 195.9 | 91383.25 | 0.0 | 172771.8 / 0.0 | 207545.1 | 0.0 |
