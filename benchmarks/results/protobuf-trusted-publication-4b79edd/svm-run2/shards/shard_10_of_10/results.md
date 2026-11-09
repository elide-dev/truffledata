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
| full | heap | 937.1 | — | 1141.9 (1.22×) | 1060.2 (1.13×) | 1241.0 (1.32×) | — | 3590.0 (3.83×) | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | 455941.5 | — | 330069.2 (0.72×) | 173798.9 (0.38×) | 174175.2 (0.38×) | — | 1152771.9 (2.53×) | 442947.0 (0.97×) | — | — | — |
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
| full | heap | 1212.7 | — | 1101.7 (0.91×) | 1515.3 (1.25×) | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | 463737.8 | — | 362864.5 (0.78×) | 377406.4 (0.81×) | — | — | — | 316022.4 (0.68×) | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | full | heap | stock | 937.141 (866.437–978.013) | 1.00 | 160.0 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | heap | generated-owned | 1141.854 (1058.365–1374.537) | 1.22 | 131.3 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | heap | slab | 1060.204 (783.195–3221.029) | 1.13 | 141.4 | 1464.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | heap | slab-native | 1240.980 (935.194–1281.501) | 1.32 | 120.8 | 1464.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | heap | slab-lazy | 3590.042 (3329.766–4947.359) | 3.83 | 41.8 | 3255.25 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| candidate | large | resident | stock | 455941.484 (412658.562–528491.979) | 1.00 | 113.7 | 433009.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | resident | generated-owned | 330069.250 (323076.000–550728.828) | 0.72 | 157.0 | 460180.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | resident | slab | 173798.889 (164058.781–178182.431) | 0.38 | 298.2 | 281177.50 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| candidate | large | resident | slab-native | 174175.156 (168753.625–230196.741) | 0.38 | 297.5 | 281177.50 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| candidate | large | resident | slab-lazy | 1152771.875 (1129350.312–1589826.547) | 2.53 | 45.0 | 834813.25 | 0.0 | 45619.8 / 281827.8 | 0.0 | 51822.3 |
| candidate | large | resident | slab-session | 442946.953 (279742.604–458618.594) | 0.97 | 117.0 | 91383.25 | 0.0 | 172771.8 / 0.0 | 207545.1 | 0.0 |
| baseline | full | heap | stock | 1212.658 (1098.832–1621.312) | 1.00 | 123.6 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | heap | generated-owned | 1101.664 (1001.681–1361.182) | 0.91 | 136.1 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | heap | slab | 1515.274 (1401.806–1782.965) | 1.25 | 98.9 | 1464.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | large | resident | stock | 463737.812 (253153.527–521703.750) | 1.00 | 111.7 | 433009.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | resident | generated-owned | 362864.500 (343344.812–438614.688) | 0.78 | 142.8 | 460180.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | resident | slab | 377406.375 (365385.438–504866.328) | 0.81 | 137.3 | 317592.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| baseline | large | resident | slab-session | 316022.438 (304623.490–373054.312) | 0.68 | 164.0 | 91383.25 | 0.0 | 172771.8 / 0.0 | 207545.1 | 0.0 |
