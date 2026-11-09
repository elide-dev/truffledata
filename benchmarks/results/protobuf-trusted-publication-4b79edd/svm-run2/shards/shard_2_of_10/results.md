# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | 878.3 | — | 932.0 (1.06×) | 512.1 (0.58×) | 497.5 (0.57×) | 1221.6 (1.39×) | 1644.2 (1.87×) | 1273.7 (1.45×) | 775.5 (0.88×) | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | 79.6 | — | 49.7 (0.62×) | 307.4 (3.86×) | 309.8 (3.89×) | — | 1644.8 (20.67×) | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
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
| sparse | resident | 887.8 | — | 1251.7 (1.41×) | 1059.0 (1.19×) | — | — | — | 1121.0 (1.26×) | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | 76.7 | — | 38.0 (0.50×) | 316.3 (4.13×) | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
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
| candidate | sparse | resident | stock | 878.281 (845.163–1165.222) | 1.00 | 170.7 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | resident | generated-owned | 931.951 (876.744–939.946) | 1.06 | 160.9 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | resident | slab | 512.051 (481.904–514.691) | 0.58 | 292.8 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | resident | slab-native | 497.517 (493.573–11513.860) | 0.57 | 301.3 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | resident | slab-projected | 1221.647 (1167.892–1301.187) | 1.39 | 122.7 | 689.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | resident | slab-lazy | 1644.200 (1534.751–1888.091) | 1.87 | 91.2 | 1084.50 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | sparse | resident | slab-session | 1273.670 (925.824–1541.922) | 1.45 | 117.7 | 151.25 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | sparse | resident | slab-session-projected | 775.467 (762.621–1102.182) | 0.88 | 193.3 | 151.25 | 0.0 | 289.2 / 0.0 | 855.6 | 149.9 |
| candidate | full | first | stock | 79.578 (79.090–130.858) | 1.00 | 1883.8 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | first | generated-owned | 49.656 (41.396–51.594) | 0.62 | 3018.9 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | first | slab | 307.433 (302.108–605.318) | 3.86 | 487.6 | 632.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | first | slab-native | 309.843 (298.527–325.936) | 3.89 | 483.8 | 632.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | first | slab-lazy | 1644.797 (1619.360–1981.298) | 20.67 | 91.1 | 2075.00 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| baseline | sparse | resident | stock | 887.813 (887.768–923.790) | 1.00 | 168.8 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | resident | generated-owned | 1251.710 (870.066–1321.654) | 1.41 | 119.8 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | resident | slab | 1059.043 (1041.225–1072.482) | 1.19 | 141.5 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | resident | slab-session | 1121.016 (978.166–1154.073) | 1.26 | 133.7 | 151.25 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| baseline | full | first | stock | 76.664 (70.705–80.882) | 1.00 | 1955.4 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | first | generated-owned | 38.037 (36.855–39.735) | 0.50 | 3941.1 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | first | slab | 316.265 (308.584–387.224) | 4.13 | 474.0 | 632.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
