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
| full | resident | 879.8 | — | 894.3 (1.02×) | 909.7 (1.03×) | 825.5 (0.94×) | — | 2975.0 (3.38×) | 1524.4 (1.73×) | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | 267610.9 | — | 294012.1 (1.10×) | 107399.4 (0.40×) | 98859.4 (0.37×) | — | 166623.3 (0.62×) | 251697.6 (0.94×) | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
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
| full | resident | 943.4 | — | 1127.4 (1.20×) | 1341.9 (1.42×) | — | — | — | 1181.1 (1.25×) | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | 234283.0 | — | 299750.0 (1.28×) | 392221.3 (1.67×) | — | — | — | 235761.5 (1.01×) | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | full | resident | stock | 879.768 (874.157–891.278) | 1.00 | 170.4 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | resident | generated-owned | 894.341 (883.335–895.670) | 1.02 | 167.6 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | resident | slab | 909.696 (777.206–2019.110) | 1.03 | 164.8 | 1440.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | resident | slab-native | 825.514 (807.061–18151.622) | 0.94 | 181.6 | 1440.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | resident | slab-lazy | 2975.037 (2902.728–3061.325) | 3.38 | 50.4 | 3047.25 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| candidate | full | resident | slab-session | 1524.356 (1354.774–1606.491) | 1.73 | 98.3 | 718.25 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | large | decode | stock | 267610.854 (232090.612–274863.318) | 1.00 | 193.6 | 433008.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | decode | generated-owned | 294012.141 (271604.922–304488.786) | 1.10 | 176.3 | 460179.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | decode | slab | 107399.391 (105158.787–112555.252) | 0.40 | 482.5 | 189880.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| candidate | large | decode | slab-native | 98859.367 (98316.066–106298.788) | 0.37 | 524.2 | 189880.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| candidate | large | decode | slab-lazy | 166623.312 (164969.241–183437.625) | 0.62 | 311.0 | 116107.75 | 0.0 | 45619.8 / 45619.8 | 0.0 | 51822.3 |
| candidate | large | decode | slab-session | 251697.612 (224281.576–287980.635) | 0.94 | 205.9 | 40.00 | 0.0 | 172771.8 / 0.0 | 207545.1 | 0.0 |
| baseline | full | resident | stock | 943.426 (927.425–1021.896) | 1.00 | 158.9 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | resident | generated-owned | 1127.435 (1075.994–1134.517) | 1.20 | 133.0 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | resident | slab | 1341.860 (1232.329–1357.872) | 1.42 | 111.7 | 1440.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | resident | slab-session | 1181.083 (1139.840–1382.136) | 1.25 | 126.9 | 718.25 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| baseline | large | decode | stock | 234282.969 (222392.629–260105.254) | 1.00 | 221.2 | 433008.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | decode | generated-owned | 299750.036 (290460.172–343564.069) | 1.28 | 172.9 | 460179.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | decode | slab | 392221.258 (282913.906–602976.729) | 1.67 | 132.1 | 226294.50 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| baseline | large | decode | slab-session | 235761.460 (217682.590–235975.393) | 1.01 | 219.8 | 40.00 | 0.0 | 172771.8 / 0.0 | 207545.1 | 0.0 |
