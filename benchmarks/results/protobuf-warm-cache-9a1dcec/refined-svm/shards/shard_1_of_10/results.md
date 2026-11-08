# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | 794.2 | — | 831.4 (1.05×) | 991.3 (1.25×) | 989.6 (1.25×) | 1043.4 (1.31×) | 1209.9 (1.52×) | 791.4 (1.00×) | 576.0 (0.73×) | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | 861.4 | — | 884.7 (1.03×) | 1275.5 (1.48×) | 1302.0 (1.51×) | — | 2873.2 (3.34×) | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | 16480.3 | 439865.5 (26.69×) | 6329.4 (0.38×) | 24673.4 (1.50×) | 23323.2 (1.42×) | — | 34937.2 (2.12×) | — | — | — | — |

## baseline: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | 785.9 | — | 861.4 (1.10×) | 975.0 (1.24×) | 989.6 (1.26×) | 1025.8 (1.31×) | 1228.9 (1.56×) | 805.6 (1.03×) | 590.9 (0.75×) | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | 864.0 | — | 888.3 (1.03×) | 1255.9 (1.45×) | 1272.4 (1.47×) | — | 2791.2 (3.23×) | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | 16176.8 | 430859.6 (26.63×) | 6737.3 (0.42×) | 41818.8 (2.59×) | 42109.4 (2.60×) | — | 52334.9 (3.24×) | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | sparse | decode | stock | 794.191 (778.378–920.151) | 1.00 | 188.8 | 1629.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | decode | generated-owned | 831.414 (830.868–1010.868) | 1.05 | 180.3 | 1607.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | decode | slab | 991.282 (973.102–1186.326) | 1.25 | 151.2 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | decode | slab-native | 989.571 (970.881–1527.037) | 1.25 | 151.5 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | decode | slab-projected | 1043.390 (989.855–1059.354) | 1.31 | 143.7 | 594.25 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | decode | slab-lazy | 1209.885 (1201.906–1246.275) | 1.52 | 123.9 | 972.25 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | sparse | decode | slab-session | 791.391 (780.582–828.471) | 1.00 | 189.4 | 40.00 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | sparse | decode | slab-session-projected | 576.022 (574.467–584.769) | 0.73 | 260.2 | 40.00 | 0.0 | 289.2 / 0.0 | 855.6 | 149.9 |
| candidate | full | detach | stock | 861.383 (855.875–876.009) | 1.00 | 174.0 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | detach | generated-owned | 884.654 (876.821–895.780) | 1.03 | 169.5 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | detach | slab | 1275.488 (1269.068–1286.549) | 1.48 | 117.5 | 1480.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | detach | slab-native | 1301.973 (1280.008–1326.063) | 1.51 | 115.1 | 1480.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | detach | slab-lazy | 2873.160 (2758.037–3028.399) | 3.34 | 52.2 | 3087.25 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| candidate | large | peak | stock | 16480.332 (16245.738–16948.290) | 1.00 | 3144.5 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | peak | stock-interop | 439865.547 (434536.164–445683.914) | 26.69 | 117.8 | 666098.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | peak | generated-owned | 6329.380 (6274.342–7322.864) | 0.38 | 8187.6 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | peak | slab | 24673.423 (23380.765–87594.479) | 1.50 | 2100.3 | 17.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| candidate | large | peak | slab-native | 23323.204 (23298.617–23516.348) | 1.42 | 2221.9 | 17.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| candidate | large | peak | slab-lazy | 34937.188 (34900.611–35365.715) | 2.12 | 1483.3 | 17.00 | 0.0 | 45619.8 / 281827.8 | 0.0 | 51822.3 |
| baseline | sparse | decode | stock | 785.853 (777.534–814.062) | 1.00 | 190.8 | 1629.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | decode | generated-owned | 861.432 (849.366–864.999) | 1.10 | 174.0 | 1607.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | decode | slab | 974.974 (968.490–974.985) | 1.24 | 153.8 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | decode | slab-native | 989.568 (982.297–1022.444) | 1.26 | 151.5 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | decode | slab-projected | 1025.809 (1012.262–1051.347) | 1.31 | 146.1 | 594.25 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| baseline | sparse | decode | slab-lazy | 1228.899 (1215.075–1255.762) | 1.56 | 122.0 | 972.25 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| baseline | sparse | decode | slab-session | 805.568 (793.467–865.084) | 1.03 | 186.1 | 40.00 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| baseline | sparse | decode | slab-session-projected | 590.932 (570.207–641.268) | 0.75 | 253.7 | 40.00 | 0.0 | 289.2 / 0.0 | 855.6 | 149.9 |
| baseline | full | detach | stock | 863.984 (851.864–881.405) | 1.00 | 173.5 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | detach | generated-owned | 888.326 (888.100–894.285) | 1.03 | 168.8 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | detach | slab | 1255.908 (1247.262–1280.449) | 1.45 | 119.4 | 1480.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | detach | slab-native | 1272.434 (1268.681–1277.002) | 1.47 | 117.8 | 1480.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | detach | slab-lazy | 2791.186 (2785.225–2955.276) | 3.23 | 53.7 | 3087.25 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| baseline | large | peak | stock | 16176.804 (15936.365–16410.452) | 1.00 | 3203.5 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | peak | stock-interop | 430859.609 (426630.547–435569.617) | 26.63 | 120.3 | 666098.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | peak | generated-owned | 6737.299 (6355.083–7259.253) | 0.42 | 7691.8 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | peak | slab | 41818.799 (41706.439–42372.610) | 2.59 | 1239.2 | 17.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| baseline | large | peak | slab-native | 42109.449 (41865.839–42918.454) | 2.60 | 1230.7 | 17.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| baseline | large | peak | slab-lazy | 52334.927 (51718.387–53287.581) | 3.24 | 990.2 | 17.00 | 0.0 | 45619.8 / 281827.8 | 0.0 | 51822.3 |
