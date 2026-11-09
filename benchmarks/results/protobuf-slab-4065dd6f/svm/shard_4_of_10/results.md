# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | 1512.1 | — | 1752.3 (1.16×) | 2964.1 (1.96×) | 2971.6 (1.97×) | 1858.6 (1.23×) | 2324.0 (1.54×) | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | 106.1 | 3297.5 (31.07×) | 49.0 (0.46×) | 390.1 (3.68×) | 310.0 (2.92×) | — | 388.7 (3.66×) | — | — | — | — |
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
| sparse | detach | 1513.4 | — | 1868.5 (1.23×) | — | — | — | — | — | — | 11105.5 (7.34×) | 13301.3 (8.79×) |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | 105.7 | — | 44.9 (0.43×) | — | — | — | — | — | — | 171.5 (1.62×) | 122.6 (1.16×) |
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
| candidate | sparse | detach | stock | 1512.098 (1490.394–1513.958) | 1.00 | 99.1 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | detach | generated-owned | 1752.318 (1748.627–1757.170) | 1.16 | 85.5 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | detach | slab | 2964.109 (2958.074–4960.719) | 1.96 | 50.6 | 1118.25 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | sparse | detach | slab-native | 2971.568 (2955.362–3026.181) | 1.97 | 50.4 | 1118.25 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | sparse | detach | slab-projected | 1858.592 (1854.060–1865.613) | 1.23 | 80.7 | 721.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | detach | slab-lazy | 2323.994 (2215.345–2503.486) | 1.54 | 64.5 | 1116.50 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | full | peak | stock | 106.143 (105.801–110.490) | 1.00 | 1412.3 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | peak | stock-interop | 3297.484 (3062.979–4311.999) | 31.07 | 45.5 | 1874.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | peak | generated-owned | 48.961 (48.860–49.076) | 0.46 | 3061.8 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | peak | slab | 390.104 (310.919–454.242) | 3.68 | 384.3 | 17.00 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | peak | slab-native | 309.963 (308.685–310.727) | 2.92 | 483.6 | 17.00 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | peak | slab-lazy | 388.655 (386.255–404.588) | 3.66 | 385.7 | 17.00 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| baseline | sparse | detach | stock | 1513.364 (1489.101–1710.130) | 1.00 | 99.1 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | detach | generated-owned | 1868.493 (1864.792–1966.114) | 1.23 | 80.2 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | detach | native-view | 11105.462 (10832.621–12503.153) | 7.34 | 13.5 | 4019.75 | 1165.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | detach | native-eager | 13301.323 (13159.169–14534.043) | 8.79 | 11.3 | 5090.00 | 1141.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | peak | stock | 105.654 (105.276–105.756) | 1.00 | 1418.8 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | peak | generated-owned | 44.907 (44.886–50.548) | 0.43 | 3338.1 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | peak | native-view | 171.494 (171.100–174.095) | 1.62 | 874.1 | 1.00 | 1165.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | peak | native-eager | 122.610 (122.578–122.887) | 1.16 | 1222.6 | 1.00 | 1141.5 | 0.0 / 0.0 | 0.0 | 0.0 |
