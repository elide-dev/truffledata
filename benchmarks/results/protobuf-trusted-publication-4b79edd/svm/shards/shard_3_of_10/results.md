# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | 963.0 | — | 898.4 (0.93×) | 546.8 (0.57×) | 568.8 (0.59×) | 1297.4 (1.35×) | 1827.4 (1.90×) | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | 63.0 | 1959.4 (31.12×) | 233.1 (3.70×) | 224.8 (3.57×) | 232.5 (3.69×) | — | 321.4 (5.11×) | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## baseline: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | 1131.3 | — | 893.3 (0.79×) | 1066.5 (0.94×) | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | 68.6 | — | 222.3 (3.24×) | 236.8 (3.45×) | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | sparse | heap | stock | 963.035 (958.865–984.038) | 1.00 | 155.7 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | heap | generated-owned | 898.412 (884.814–907.414) | 0.93 | 166.9 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | heap | slab | 546.812 (527.053–625.769) | 0.57 | 274.1 | 958.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | heap | slab-native | 568.761 (523.239–668.165) | 0.59 | 263.6 | 958.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | heap | slab-projected | 1297.359 (1282.291–14605.675) | 1.35 | 115.5 | 897.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | heap | slab-lazy | 1827.417 (1508.800–18423.808) | 1.90 | 82.0 | 1292.50 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | full | interpret | stock | 62.957 (62.917–67.162) | 1.00 | 2381.1 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | interpret | stock-interop | 1959.415 (1894.062–1990.037) | 31.12 | 76.5 | 1875.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | interpret | generated-owned | 233.087 (222.457–246.331) | 3.70 | 643.1 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | interpret | slab | 224.791 (224.539–228.945) | 3.57 | 666.9 | 2.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | interpret | slab-native | 232.535 (222.634–251.310) | 3.69 | 644.7 | 2.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | interpret | slab-lazy | 321.439 (267.268–347.353) | 5.11 | 466.4 | 2.00 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| baseline | sparse | heap | stock | 1131.282 (832.581–1422.698) | 1.00 | 132.5 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | heap | generated-owned | 893.262 (866.389–967.297) | 0.79 | 167.8 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | heap | slab | 1066.537 (1064.848–1071.153) | 0.94 | 140.6 | 958.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | interpret | stock | 68.612 (62.900–85.506) | 1.00 | 2184.8 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | interpret | generated-owned | 222.338 (217.791–306.304) | 3.24 | 674.2 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | interpret | slab | 236.779 (226.721–270.314) | 3.45 | 633.1 | 2.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
