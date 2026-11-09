# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | 833.4 | — | 837.3 (1.00×) | 1074.9 (1.29×) | 1070.8 (1.28×) | 1232.4 (1.48×) | 1439.6 (1.73×) | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | 62.1 | 1783.8 (28.71×) | 215.7 (3.47×) | 351.2 (5.65×) | 347.6 (5.59×) | — | 380.6 (6.12×) | — | — | — | — |
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
| sparse | heap | 812.8 | — | 863.8 (1.06×) | 1061.3 (1.31×) | 1040.4 (1.28×) | 1214.1 (1.49×) | 1425.4 (1.75×) | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | 61.8 | 1720.1 (27.84×) | 215.4 (3.49×) | 418.9 (6.78×) | 416.0 (6.73×) | — | 440.4 (7.13×) | — | — | — | — |
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
| candidate | sparse | heap | stock | 833.415 (808.908–862.161) | 1.00 | 179.9 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | heap | generated-owned | 837.302 (835.124–845.504) | 1.00 | 179.0 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | heap | slab | 1074.855 (1066.036–6609.621) | 1.29 | 139.5 | 958.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | heap | slab-native | 1070.807 (1064.259–1074.639) | 1.28 | 140.0 | 958.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | heap | slab-projected | 1232.351 (1230.880–11559.703) | 1.48 | 121.6 | 897.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | heap | slab-lazy | 1439.579 (1425.102–1470.707) | 1.73 | 104.1 | 1292.50 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | full | interpret | stock | 62.137 (61.737–62.808) | 1.00 | 2412.5 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | interpret | stock-interop | 1783.757 (1775.646–1786.969) | 28.71 | 84.0 | 1875.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | interpret | generated-owned | 215.656 (215.527–215.794) | 3.47 | 695.1 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | interpret | slab | 351.174 (350.597–351.603) | 5.65 | 426.9 | 18.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | interpret | slab-native | 347.565 (346.199–351.910) | 5.59 | 431.3 | 18.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | interpret | slab-lazy | 380.556 (377.543–383.547) | 6.12 | 393.9 | 18.00 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| baseline | sparse | heap | stock | 812.755 (790.575–837.352) | 1.00 | 184.4 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | heap | generated-owned | 863.779 (858.582–874.551) | 1.06 | 173.5 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | heap | slab | 1061.269 (1046.483–1153.500) | 1.31 | 141.3 | 958.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | heap | slab-native | 1040.352 (1029.871–1062.162) | 1.28 | 144.1 | 958.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | heap | slab-projected | 1214.083 (1197.950–1292.213) | 1.49 | 123.5 | 897.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| baseline | sparse | heap | slab-lazy | 1425.440 (1423.317–1481.549) | 1.75 | 105.2 | 1292.50 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| baseline | full | interpret | stock | 61.783 (61.674–62.265) | 1.00 | 2426.3 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | interpret | stock-interop | 1720.060 (1703.630–1897.131) | 27.84 | 87.2 | 1875.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | interpret | generated-owned | 215.428 (214.691–225.347) | 3.49 | 695.9 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | interpret | slab | 418.926 (414.228–419.057) | 6.78 | 357.8 | 18.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | interpret | slab-native | 415.962 (411.752–432.429) | 6.73 | 360.4 | 18.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | interpret | slab-lazy | 440.384 (439.939–441.829) | 7.13 | 340.4 | 18.00 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
