# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | 2768.6 | — | 1925.3 (0.70×) | 1066.9 (0.39×) | 1450.7 (0.52×) | 2178.1 (0.79×) | 3028.5 (1.09×) | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | 111.6 | 3218.1 (28.84×) | 52.4 (0.47×) | 116.6 (1.04×) | 111.3 (1.00×) | — | 222.4 (1.99×) | — | — | — | — |
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
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | 2831.6 | — | 2253.5 (0.80×) | 3120.2 (1.10×) | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | 183.6 | — | 60.4 (0.33×) | 163.8 (0.89×) | — | — | — | — | — | — | — |
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
| candidate | sparse | detach | stock | 2768.614 (2551.944–2867.426) | 1.00 | 54.1 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | detach | generated-owned | 1925.322 (1872.465–2825.490) | 0.70 | 77.9 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | detach | slab | 1066.917 (1052.540–1614.158) | 0.39 | 140.5 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | detach | slab-native | 1450.703 (1164.336–1750.882) | 0.52 | 103.3 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | detach | slab-projected | 2178.074 (1902.923–7648.998) | 0.79 | 68.8 | 689.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | detach | slab-lazy | 3028.490 (2417.098–3731.129) | 1.09 | 49.5 | 1084.50 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | full | peak | stock | 111.604 (105.900–178.003) | 1.00 | 1343.2 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | peak | stock-interop | 3218.131 (3150.829–3350.813) | 28.84 | 46.6 | 1874.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | peak | generated-owned | 52.359 (49.642–84.445) | 0.47 | 2863.1 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | peak | slab | 116.551 (104.830–160.522) | 1.04 | 1286.2 | 1.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | peak | slab-native | 111.268 (105.185–153.894) | 1.00 | 1347.2 | 1.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | peak | slab-lazy | 222.368 (147.178–273.629) | 1.99 | 674.1 | 1.00 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| baseline | sparse | detach | stock | 2831.579 (2555.503–2844.095) | 1.00 | 52.9 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | detach | generated-owned | 2253.525 (1832.244–3474.699) | 0.80 | 66.5 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | detach | slab | 3120.241 (2150.815–3654.310) | 1.10 | 48.0 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | peak | stock | 183.582 (107.466–193.289) | 1.00 | 816.6 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | peak | generated-owned | 60.425 (51.098–84.524) | 0.33 | 2480.9 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | peak | slab | 163.842 (107.858–173.820) | 0.89 | 914.9 | 1.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
