# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | 798.0 | — | 823.1 (1.03×) | 1064.9 (1.33×) | 1071.7 (1.34×) | 1205.4 (1.51×) | 1414.6 (1.77×) | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | 61.6 | 1793.6 (29.13×) | 216.1 (3.51×) | 350.8 (5.70×) | 349.5 (5.68×) | — | 382.7 (6.22×) | — | — | — | — |
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
| sparse | heap | 783.7 | — | 865.1 (1.10×) | 1060.2 (1.35×) | 1043.5 (1.33×) | 1231.7 (1.57×) | 1414.5 (1.80×) | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | 61.5 | 1700.6 (27.63×) | 217.6 (3.54×) | 412.3 (6.70×) | 413.4 (6.72×) | — | 438.1 (7.12×) | — | — | — | — |
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
| candidate | sparse | heap | stock | 797.955 (797.552–798.641) | 1.00 | 187.9 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | heap | generated-owned | 823.143 (821.184–832.540) | 1.03 | 182.1 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | heap | slab | 1064.875 (1064.342–12293.284) | 1.33 | 140.8 | 958.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | heap | slab-native | 1071.681 (1061.687–1100.463) | 1.34 | 139.9 | 958.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | heap | slab-projected | 1205.390 (1193.165–15308.557) | 1.51 | 124.4 | 897.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | heap | slab-lazy | 1414.569 (1402.356–1430.006) | 1.77 | 106.0 | 1292.50 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | full | interpret | stock | 61.566 (61.307–61.935) | 1.00 | 2434.9 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | interpret | stock-interop | 1793.553 (1778.973–1805.666) | 29.13 | 83.6 | 1875.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | interpret | generated-owned | 216.110 (215.336–225.188) | 3.51 | 693.7 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | interpret | slab | 350.790 (346.985–353.842) | 5.70 | 427.3 | 18.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | interpret | slab-native | 349.533 (349.502–350.932) | 5.68 | 428.9 | 18.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | interpret | slab-lazy | 382.696 (378.347–385.742) | 6.22 | 391.7 | 18.00 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| baseline | sparse | heap | stock | 783.730 (781.000–788.467) | 1.00 | 191.3 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | heap | generated-owned | 865.077 (857.286–911.466) | 1.10 | 173.3 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | heap | slab | 1060.224 (1047.920–1087.063) | 1.35 | 141.4 | 958.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | heap | slab-native | 1043.504 (1036.411–1044.250) | 1.33 | 143.7 | 958.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | heap | slab-projected | 1231.704 (1191.607–1353.838) | 1.57 | 121.7 | 897.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| baseline | sparse | heap | slab-lazy | 1414.512 (1405.653–1438.496) | 1.80 | 106.0 | 1292.50 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| baseline | full | interpret | stock | 61.543 (61.409–61.638) | 1.00 | 2435.8 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | interpret | stock-interop | 1700.562 (1684.301–1704.161) | 27.63 | 88.2 | 1875.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | interpret | generated-owned | 217.602 (215.522–230.081) | 3.54 | 688.9 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | interpret | slab | 412.307 (412.131–412.522) | 6.70 | 363.6 | 18.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | interpret | slab-native | 413.398 (413.179–417.247) | 6.72 | 362.6 | 18.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | interpret | slab-lazy | 438.084 (436.325–443.198) | 7.12 | 342.2 | 18.00 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
