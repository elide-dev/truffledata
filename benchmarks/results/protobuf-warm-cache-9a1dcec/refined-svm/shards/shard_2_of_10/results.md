# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | 811.8 | — | 844.6 (1.04×) | 1089.9 (1.34×) | 1050.0 (1.29×) | 1077.4 (1.33×) | 1301.0 (1.60×) | 922.9 (1.14×) | 692.6 (0.85×) | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | 76.2 | — | 53.7 (0.70×) | 291.6 (3.83×) | 286.0 (3.75×) | — | 1543.2 (20.25×) | — | — | — | — |
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
| sparse | resident | 801.2 | — | 891.7 (1.11×) | 1027.0 (1.28×) | 1035.8 (1.29×) | 1095.2 (1.37×) | 1342.6 (1.68×) | 873.1 (1.09×) | 689.7 (0.86×) | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | 70.4 | — | 43.4 (0.62×) | 336.1 (4.77×) | 312.1 (4.43×) | — | 1528.6 (21.70×) | — | — | — | — |
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
| candidate | sparse | resident | stock | 811.795 (811.459–860.500) | 1.00 | 184.7 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | resident | generated-owned | 844.579 (843.728–845.843) | 1.04 | 177.5 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | resident | slab | 1089.919 (1047.263–1220.413) | 1.34 | 137.5 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | resident | slab-native | 1050.012 (1041.200–1051.566) | 1.29 | 142.8 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | resident | slab-projected | 1077.370 (1064.086–1085.221) | 1.33 | 139.1 | 689.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | resident | slab-lazy | 1300.961 (1294.876–1390.295) | 1.60 | 115.2 | 1084.50 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | sparse | resident | slab-session | 922.904 (877.787–977.192) | 1.14 | 162.4 | 151.25 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | sparse | resident | slab-session-projected | 692.610 (680.137–700.238) | 0.85 | 216.4 | 151.25 | 0.0 | 289.2 / 0.0 | 855.6 | 149.9 |
| candidate | full | first | stock | 76.202 (68.521–79.122) | 1.00 | 1967.2 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | first | generated-owned | 53.721 (36.370–82.091) | 0.70 | 2790.4 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | first | slab | 291.600 (285.656–306.283) | 3.83 | 514.1 | 672.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | first | slab-native | 286.036 (282.347–288.443) | 3.75 | 524.1 | 672.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | first | slab-lazy | 1543.204 (1523.765–1594.279) | 20.25 | 97.1 | 2115.00 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| baseline | sparse | resident | stock | 801.181 (795.954–808.693) | 1.00 | 187.1 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | resident | generated-owned | 891.737 (888.933–902.537) | 1.11 | 168.1 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | resident | slab | 1027.045 (1025.629–1066.974) | 1.28 | 146.0 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | resident | slab-native | 1035.836 (1029.377–1049.015) | 1.29 | 144.7 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | resident | slab-projected | 1095.213 (1085.599–1132.525) | 1.37 | 136.9 | 689.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| baseline | sparse | resident | slab-lazy | 1342.635 (1314.478–1365.296) | 1.68 | 111.7 | 1084.50 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| baseline | sparse | resident | slab-session | 873.066 (867.500–892.612) | 1.09 | 171.7 | 151.25 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| baseline | sparse | resident | slab-session-projected | 689.736 (674.017–710.912) | 0.86 | 217.3 | 151.25 | 0.0 | 289.2 / 0.0 | 855.6 | 149.9 |
| baseline | full | first | stock | 70.435 (68.000–75.597) | 1.00 | 2128.3 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | first | generated-owned | 43.384 (38.522–46.552) | 0.62 | 3455.3 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | first | slab | 336.135 (307.845–343.318) | 4.77 | 446.0 | 672.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | first | slab-native | 312.143 (308.943–335.738) | 4.43 | 480.2 | 672.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | first | slab-lazy | 1528.616 (1504.001–1567.940) | 21.70 | 98.1 | 2115.00 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
