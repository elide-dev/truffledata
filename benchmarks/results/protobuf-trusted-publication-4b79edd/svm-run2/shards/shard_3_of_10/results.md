# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | 944.8 | — | 956.6 (1.01×) | 559.0 (0.59×) | 557.0 (0.59×) | 1394.2 (1.48×) | 2442.1 (2.58×) | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | 65.4 | 1970.1 (30.13×) | 237.8 (3.64×) | 233.8 (3.58×) | 236.5 (3.62×) | — | 273.6 (4.18×) | — | — | — | — |
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
| sparse | heap | 1043.1 | — | 1037.5 (0.99×) | 1319.8 (1.27×) | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | 68.5 | — | 234.6 (3.42×) | 242.2 (3.53×) | — | — | — | — | — | — | — |
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
| candidate | sparse | heap | stock | 944.787 (856.753–1001.385) | 1.00 | 158.7 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | heap | generated-owned | 956.622 (936.994–1149.376) | 1.01 | 156.7 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | heap | slab | 558.979 (556.835–572.470) | 0.59 | 268.2 | 958.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | heap | slab-native | 557.044 (551.133–594.229) | 0.59 | 269.1 | 958.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | heap | slab-projected | 1394.219 (1317.250–13997.402) | 1.48 | 107.5 | 897.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | heap | slab-lazy | 2442.122 (1615.495–18916.183) | 2.58 | 61.4 | 1292.50 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | full | interpret | stock | 65.392 (64.176–70.781) | 1.00 | 2292.4 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | interpret | stock-interop | 1970.075 (1859.691–1996.440) | 30.13 | 76.1 | 1875.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | interpret | generated-owned | 237.805 (223.343–252.844) | 3.64 | 630.4 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | interpret | slab | 233.801 (227.442–255.285) | 3.58 | 641.2 | 2.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | interpret | slab-native | 236.473 (230.217–237.444) | 3.62 | 633.9 | 2.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | interpret | slab-lazy | 273.633 (272.944–278.731) | 4.18 | 547.8 | 2.00 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| baseline | sparse | heap | stock | 1043.052 (923.180–1497.704) | 1.00 | 143.7 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | heap | generated-owned | 1037.497 (886.803–1189.847) | 0.99 | 144.5 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | heap | slab | 1319.806 (1214.986–1721.584) | 1.27 | 113.6 | 958.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | interpret | stock | 68.531 (65.205–80.336) | 1.00 | 2187.4 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | interpret | generated-owned | 234.646 (233.683–234.984) | 3.42 | 638.9 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | interpret | slab | 242.223 (232.766–281.959) | 3.53 | 618.9 | 2.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
