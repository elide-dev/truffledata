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
| full | heap | 1089.5 | — | 1591.0 (1.46×) | 903.8 (0.83×) | 836.9 (0.77×) | — | 3402.3 (3.12×) | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | 295845.1 | — | 317860.8 (1.07×) | 157367.9 (0.53×) | 167249.0 (0.57×) | — | 1038056.2 (3.51×) | 275903.2 (0.93×) | — | — | — |
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
| full | heap | 987.9 | — | 1017.1 (1.03×) | 1355.4 (1.37×) | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | 302350.3 | — | 304620.4 (1.01×) | 353844.6 (1.17×) | — | — | — | 283737.6 (0.94×) | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | full | heap | stock | 1089.519 (922.967–1467.026) | 1.00 | 137.6 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | heap | generated-owned | 1591.000 (907.635–2309.965) | 1.46 | 94.2 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | heap | slab | 903.788 (834.861–992.067) | 0.83 | 165.9 | 1464.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | heap | slab-native | 836.878 (734.091–1193.087) | 0.77 | 179.1 | 1464.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | heap | slab-lazy | 3402.267 (3034.463–3898.213) | 3.12 | 44.1 | 3255.25 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| candidate | large | resident | stock | 295845.104 (285610.469–300690.938) | 1.00 | 175.2 | 433009.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | resident | generated-owned | 317860.750 (282335.260–392762.422) | 1.07 | 163.0 | 460180.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | resident | slab | 157367.875 (156840.622–184771.389) | 0.53 | 329.3 | 281177.50 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| candidate | large | resident | slab-native | 167249.034 (167229.906–168984.438) | 0.57 | 309.9 | 281177.50 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| candidate | large | resident | slab-lazy | 1038056.250 (988772.516–1174990.938) | 3.51 | 49.9 | 834813.25 | 0.0 | 45619.8 / 281827.8 | 0.0 | 51822.3 |
| candidate | large | resident | slab-session | 275903.229 (268796.198–282067.083) | 0.93 | 187.8 | 91383.25 | 0.0 | 172771.8 / 0.0 | 207545.1 | 0.0 |
| baseline | full | heap | stock | 987.926 (961.847–1498.580) | 1.00 | 151.7 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | heap | generated-owned | 1017.073 (889.303–1019.850) | 1.03 | 147.4 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | heap | slab | 1355.387 (1333.939–1783.161) | 1.37 | 110.6 | 1464.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | large | resident | stock | 302350.312 (268009.583–313030.750) | 1.00 | 171.4 | 433009.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | resident | generated-owned | 304620.365 (290033.438–386380.569) | 1.01 | 170.1 | 460180.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | resident | slab | 353844.625 (347250.875–388822.188) | 1.17 | 146.5 | 317592.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| baseline | large | resident | slab-session | 283737.557 (269215.156–391865.859) | 0.94 | 182.6 | 91383.25 | 0.0 | 172771.8 / 0.0 | 207545.1 | 0.0 |
