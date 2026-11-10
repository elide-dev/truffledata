# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | 1567.5 | — | 1783.1 (1.14×) | 1005.5 (0.64×) | 1010.5 (0.64×) | 3481.5 (2.22×) | 2423.1 (1.55×) | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | 106.6 | 3052.3 (28.62×) | 48.9 (0.46×) | 102.6 (0.96×) | 103.2 (0.97×) | — | 141.1 (1.32×) | — | — | — | — |
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
| sparse | detach | 1721.0 | — | 1777.4 (1.03×) | 1021.5 (0.59×) | 1009.1 (0.59×) | 1972.8 (1.15×) | 2368.0 (1.38×) | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | 104.3 | 3040.7 (29.15×) | 49.5 (0.47×) | 101.2 (0.97×) | 102.5 (0.98×) | — | 146.7 (1.41×) | — | — | — | — |
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
| candidate | sparse | detach | stock | 1567.542 (1553.951–1688.270) | 1.00 | 95.6 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | detach | generated-owned | 1783.063 (1775.086–1869.777) | 1.14 | 84.1 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | detach | slab | 1005.483 (989.040–1005.911) | 0.64 | 149.1 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | detach | slab-native | 1010.452 (1003.734–1014.454) | 0.64 | 148.4 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | detach | slab-projected | 3481.489 (2002.510–5631.533) | 2.22 | 43.1 | 689.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | detach | slab-lazy | 2423.083 (2405.621–2539.441) | 1.55 | 61.9 | 1084.50 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | full | peak | stock | 106.642 (105.934–108.021) | 1.00 | 1405.7 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | peak | stock-interop | 3052.263 (3049.479–3159.930) | 28.62 | 49.1 | 1874.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | peak | generated-owned | 48.878 (48.857–53.292) | 0.46 | 3067.0 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | peak | slab | 102.554 (102.391–106.053) | 0.96 | 1461.7 | 1.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | peak | slab-native | 103.231 (102.331–103.627) | 0.97 | 1452.1 | 1.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | peak | slab-lazy | 141.067 (140.703–141.633) | 1.32 | 1062.7 | 1.00 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| baseline | sparse | detach | stock | 1720.974 (1510.506–1779.317) | 1.00 | 87.1 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | detach | generated-owned | 1777.429 (1759.244–1807.464) | 1.03 | 84.3 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | detach | slab | 1021.494 (986.620–1092.253) | 0.59 | 146.8 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | detach | slab-native | 1009.113 (1002.715–1048.700) | 0.59 | 148.6 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | detach | slab-projected | 1972.820 (1872.114–2107.790) | 1.15 | 76.0 | 689.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| baseline | sparse | detach | slab-lazy | 2367.968 (2281.665–2401.078) | 1.38 | 63.3 | 1084.50 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| baseline | full | peak | stock | 104.320 (103.663–127.460) | 1.00 | 1437.0 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | peak | stock-interop | 3040.666 (3017.110–3350.934) | 29.15 | 49.3 | 1874.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | peak | generated-owned | 49.515 (49.002–50.539) | 0.47 | 3027.5 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | peak | slab | 101.188 (100.943–102.187) | 0.97 | 1481.5 | 1.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | peak | slab-native | 102.497 (101.596–108.185) | 0.98 | 1462.5 | 1.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | peak | slab-lazy | 146.706 (141.467–233.946) | 1.41 | 1021.8 | 1.00 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
