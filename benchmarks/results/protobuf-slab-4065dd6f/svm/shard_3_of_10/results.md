# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | 808.7 | — | 851.2 (1.05×) | 1712.0 (2.12×) | 1662.1 (2.06×) | 1174.7 (1.45×) | 1378.9 (1.71×) | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | 61.6 | 1747.9 (28.36×) | 268.6 (4.36×) | 414.6 (6.73×) | 410.8 (6.67×) | — | 436.9 (7.09×) | — | — | — | — |
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
| sparse | heap | 816.2 | — | 823.4 (1.01×) | — | — | — | — | — | — | 2523.6 (3.09×) | 1730.3 (2.12×) |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | 61.4 | — | 288.0 (4.69×) | — | — | — | — | — | — | 398.0 (6.48×) | 339.3 (5.52×) |
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
| candidate | sparse | heap | stock | 808.660 (803.494–812.240) | 1.00 | 185.4 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | heap | generated-owned | 851.201 (844.109–876.122) | 1.05 | 176.1 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | heap | slab | 1712.025 (1684.619–2648.440) | 2.12 | 87.6 | 1361.50 | 0.0 | 547.2 / 867.4 | 0.0 | 0.0 |
| candidate | sparse | heap | slab-native | 1662.071 (1661.392–1694.008) | 2.06 | 90.2 | 1361.50 | 0.0 | 547.2 / 867.4 | 0.0 | 0.0 |
| candidate | sparse | heap | slab-projected | 1174.657 (1172.733–1214.795) | 1.45 | 127.6 | 929.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | heap | slab-lazy | 1378.897 (1378.711–1383.658) | 1.71 | 108.7 | 1324.50 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | full | interpret | stock | 61.622 (61.398–61.870) | 1.00 | 2432.7 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | interpret | stock-interop | 1747.907 (1745.107–1768.133) | 28.36 | 85.8 | 1875.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | interpret | generated-owned | 268.555 (266.529–268.940) | 4.36 | 558.2 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | interpret | slab | 414.628 (411.200–415.291) | 6.73 | 361.5 | 18.00 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | interpret | slab-native | 410.848 (410.607–422.772) | 6.67 | 364.9 | 18.00 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | interpret | slab-lazy | 436.939 (433.450–444.204) | 7.09 | 343.1 | 18.00 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| baseline | sparse | heap | stock | 816.169 (803.922–822.942) | 1.00 | 183.7 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | heap | generated-owned | 823.418 (810.634–898.493) | 1.01 | 182.1 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | heap | native-view | 2523.643 (878.188–8189.093) | 3.09 | 59.4 | 664.00 | 1165.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | heap | native-eager | 1730.322 (1663.934–1792.619) | 2.12 | 86.6 | 1545.75 | 1141.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | interpret | stock | 61.434 (61.381–61.467) | 1.00 | 2440.1 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | interpret | generated-owned | 287.954 (286.391–291.157) | 4.69 | 520.6 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | interpret | native-view | 398.025 (397.309–398.592) | 6.48 | 376.6 | 2.00 | 1165.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | interpret | native-eager | 339.309 (336.157–340.140) | 5.52 | 441.8 | 2.00 | 1141.5 | 0.0 / 0.0 | 0.0 | 0.0 |
