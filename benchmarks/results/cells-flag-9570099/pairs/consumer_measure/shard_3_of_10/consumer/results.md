# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | 678.8 | — | 709.1 (1.04×) | 438.7 (0.65×) | 427.0 (0.63×) | 1642.6 (2.42×) | 1209.0 (1.78×) | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | 50.4 | 1534.2 (30.44×) | 189.1 (3.75×) | 190.8 (3.79×) | 179.9 (3.57×) | — | 209.5 (4.16×) | — | — | — | — |
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
| sparse | heap | 680.2 | — | 710.0 (1.04×) | 418.4 (0.62×) | 423.9 (0.62×) | 1021.1 (1.50×) | 1224.4 (1.80×) | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | 54.5 | 1531.7 (28.09×) | 186.6 (3.42×) | 191.6 (3.51×) | 185.9 (3.41×) | — | 206.1 (3.78×) | — | — | — | — |
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
| candidate | sparse | heap | stock | 678.825 (675.692–690.280) | 1.00 | 220.8 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | heap | generated-owned | 709.106 (694.931–716.703) | 1.04 | 211.4 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | heap | slab | 438.745 (407.453–469.961) | 0.65 | 341.7 | 958.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | heap | slab-native | 427.014 (415.055–477.268) | 0.63 | 351.1 | 958.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | heap | slab-projected | 1642.601 (1076.430–11182.326) | 2.42 | 91.3 | 897.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | heap | slab-lazy | 1209.047 (1206.353–1336.495) | 1.78 | 124.0 | 1292.50 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | full | interpret | stock | 50.393 (50.073–50.444) | 1.00 | 2974.8 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | interpret | stock-interop | 1534.202 (1466.510–1548.981) | 30.44 | 97.7 | 1875.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | interpret | generated-owned | 189.069 (182.848–237.156) | 3.75 | 792.9 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | interpret | slab | 190.780 (184.306–191.812) | 3.79 | 785.8 | 2.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | interpret | slab-native | 179.909 (179.279–191.371) | 3.57 | 833.2 | 2.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | interpret | slab-lazy | 209.481 (205.914–216.139) | 4.16 | 715.6 | 2.00 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| baseline | sparse | heap | stock | 680.244 (642.871–705.989) | 1.00 | 220.4 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | heap | generated-owned | 710.042 (692.898–737.637) | 1.04 | 211.1 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | heap | slab | 418.420 (410.348–418.988) | 0.62 | 358.3 | 958.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | heap | slab-native | 423.902 (408.810–517.241) | 0.62 | 353.6 | 958.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | heap | slab-projected | 1021.094 (1002.153–1046.352) | 1.50 | 146.8 | 897.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| baseline | sparse | heap | slab-lazy | 1224.436 (1183.446–1335.161) | 1.80 | 122.4 | 1292.50 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| baseline | full | interpret | stock | 54.526 (51.102–61.861) | 1.00 | 2749.3 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | interpret | stock-interop | 1531.744 (1451.198–1623.603) | 28.09 | 97.9 | 1875.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | interpret | generated-owned | 186.621 (175.807–190.459) | 3.42 | 803.3 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | interpret | slab | 191.584 (183.892–202.053) | 3.51 | 782.5 | 2.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | interpret | slab-native | 185.903 (177.171–186.310) | 3.41 | 806.4 | 2.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | interpret | slab-lazy | 206.136 (204.933–217.469) | 3.78 | 727.2 | 2.00 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
