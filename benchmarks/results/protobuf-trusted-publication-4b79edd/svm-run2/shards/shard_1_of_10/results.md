# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | 989.5 | — | 998.0 (1.01×) | 517.7 (0.52×) | 472.4 (0.48×) | 1348.9 (1.36×) | 1435.2 (1.45×) | 870.3 (0.88×) | 724.3 (0.73×) | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | 966.0 | — | 935.9 (0.97×) | 862.6 (0.89×) | 886.6 (0.92×) | — | 3192.3 (3.30×) | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | 28342.9 | 472315.2 (16.66×) | 11830.3 (0.42×) | 17731.2 (0.63×) | 19225.8 (0.68×) | — | 45196.8 (1.59×) | — | — | — | — |

## baseline: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | 955.1 | — | 1483.8 (1.55×) | 1022.1 (1.07×) | — | — | — | 949.1 (0.99×) | — | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | 1048.3 | — | 1170.4 (1.12×) | 1817.2 (1.73×) | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | 20329.3 | — | 7518.1 (0.37×) | 17644.8 (0.87×) | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | sparse | decode | stock | 989.496 (935.606–995.131) | 1.00 | 151.5 | 1629.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | decode | generated-owned | 998.043 (877.888–1650.381) | 1.01 | 150.2 | 1607.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | decode | slab | 517.662 (472.499–633.003) | 0.52 | 289.6 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | decode | slab-native | 472.449 (441.891–504.768) | 0.48 | 317.3 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | decode | slab-projected | 1348.865 (1151.025–1352.287) | 1.36 | 111.1 | 594.25 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | decode | slab-lazy | 1435.224 (1417.546–1494.780) | 1.45 | 104.4 | 972.25 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | sparse | decode | slab-session | 870.327 (851.987–879.408) | 0.88 | 172.2 | 40.00 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | sparse | decode | slab-session-projected | 724.317 (661.520–775.103) | 0.73 | 207.0 | 40.00 | 0.0 | 289.2 / 0.0 | 855.6 | 149.9 |
| candidate | full | detach | stock | 966.012 (938.907–1056.577) | 1.00 | 155.2 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | detach | generated-owned | 935.887 (933.506–945.556) | 0.97 | 160.2 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | detach | slab | 862.600 (730.268–978.669) | 0.89 | 173.8 | 1440.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | detach | slab-native | 886.596 (799.216–1108.135) | 0.92 | 169.1 | 1440.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | detach | slab-lazy | 3192.253 (3033.496–5780.863) | 3.30 | 47.0 | 3047.25 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| candidate | large | peak | stock | 28342.902 (18621.492–28404.705) | 1.00 | 1828.4 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | peak | stock-interop | 472315.156 (470151.250–713562.188) | 16.66 | 109.7 | 666098.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | peak | generated-owned | 11830.284 (8114.849–15575.464) | 0.42 | 4380.5 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | peak | slab | 17731.225 (17169.684–20281.262) | 0.63 | 2922.7 | 1.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| candidate | large | peak | slab-native | 19225.781 (17525.829–21511.524) | 0.68 | 2695.5 | 1.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| candidate | large | peak | slab-lazy | 45196.804 (38579.771–46878.410) | 1.59 | 1146.6 | 1.00 | 0.0 | 45619.8 / 281827.8 | 0.0 | 51822.3 |
| baseline | sparse | decode | stock | 955.051 (868.439–1008.598) | 1.00 | 157.0 | 1629.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | decode | generated-owned | 1483.770 (909.591–1505.337) | 1.55 | 101.0 | 1607.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | decode | slab | 1022.114 (1001.789–1305.817) | 1.07 | 146.7 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | decode | slab-session | 949.076 (872.217–951.845) | 0.99 | 157.9 | 40.00 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| baseline | full | detach | stock | 1048.343 (1009.688–1088.925) | 1.00 | 143.0 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | detach | generated-owned | 1170.377 (942.966–1307.677) | 1.12 | 128.1 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | detach | slab | 1817.229 (1345.590–2667.363) | 1.73 | 82.5 | 1440.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | large | peak | stock | 20329.257 (18395.390–35059.743) | 1.00 | 2549.1 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | peak | generated-owned | 7518.137 (7384.547–9218.285) | 0.37 | 6893.0 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | peak | slab | 17644.761 (17069.769–25591.885) | 0.87 | 2937.0 | 1.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
