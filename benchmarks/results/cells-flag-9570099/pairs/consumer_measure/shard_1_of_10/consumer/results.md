# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | 831.1 | — | 904.0 (1.09×) | 430.5 (0.52×) | 439.8 (0.53×) | 1125.9 (1.35×) | 1410.3 (1.70×) | 884.3 (1.06×) | 644.3 (0.78×) | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | 946.4 | — | 907.5 (0.96×) | 731.8 (0.77×) | 739.8 (0.78×) | — | 2933.1 (3.10×) | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | 19106.3 | 465054.7 (24.34×) | 6307.3 (0.33×) | 18370.7 (0.96×) | 16525.9 (0.86×) | — | 29146.8 (1.53×) | — | — | — | — |

## baseline: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | 874.5 | — | 978.0 (1.12×) | 432.4 (0.49×) | 441.6 (0.50×) | 1087.2 (1.24×) | 1542.1 (1.76×) | 850.0 (0.97×) | 705.5 (0.81×) | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | 1105.2 | — | 998.2 (0.90×) | 794.3 (0.72×) | 766.9 (0.69×) | — | 2839.4 (2.57×) | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | 18688.4 | 452853.2 (24.23×) | 7271.4 (0.39×) | 16860.2 (0.90×) | 16437.5 (0.88×) | — | 30854.3 (1.65×) | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | sparse | decode | stock | 831.077 (818.192–881.400) | 1.00 | 180.4 | 1629.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | decode | generated-owned | 904.006 (863.316–1018.471) | 1.09 | 165.8 | 1607.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | decode | slab | 430.512 (404.626–577.978) | 0.52 | 348.2 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | decode | slab-native | 439.829 (439.773–671.712) | 0.53 | 340.8 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | decode | slab-projected | 1125.865 (1106.424–1143.282) | 1.35 | 133.1 | 594.25 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | decode | slab-lazy | 1410.282 (1279.362–2185.090) | 1.70 | 106.3 | 972.25 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | sparse | decode | slab-session | 884.297 (815.055–979.591) | 1.06 | 169.5 | 40.00 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | sparse | decode | slab-session-projected | 644.268 (628.319–657.115) | 0.78 | 232.7 | 40.00 | 0.0 | 289.2 / 0.0 | 855.6 | 149.9 |
| candidate | full | detach | stock | 946.432 (920.523–997.504) | 1.00 | 158.4 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | detach | generated-owned | 907.473 (872.065–912.428) | 0.96 | 165.2 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | detach | slab | 731.819 (702.314–1349.925) | 0.77 | 204.8 | 1440.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | detach | slab-native | 739.825 (710.711–759.041) | 0.78 | 202.6 | 1440.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | detach | slab-lazy | 2933.113 (2902.446–2941.944) | 3.10 | 51.1 | 3047.25 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| candidate | large | peak | stock | 19106.326 (16510.940–19454.286) | 1.00 | 2712.3 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | peak | stock-interop | 465054.688 (451274.062–558375.104) | 24.34 | 111.4 | 666098.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | peak | generated-owned | 6307.349 (6252.222–7450.083) | 0.33 | 8216.2 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | peak | slab | 18370.662 (18015.345–18439.426) | 0.96 | 2820.9 | 1.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| candidate | large | peak | slab-native | 16525.934 (16305.628–24250.413) | 0.86 | 3135.8 | 1.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| candidate | large | peak | slab-lazy | 29146.805 (27321.013–45350.571) | 1.53 | 1778.0 | 1.00 | 0.0 | 45619.8 / 281827.8 | 0.0 | 51822.3 |
| baseline | sparse | decode | stock | 874.478 (777.282–1332.466) | 1.00 | 171.4 | 1629.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | decode | generated-owned | 978.037 (861.496–1053.357) | 1.12 | 153.3 | 1607.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | decode | slab | 432.367 (410.306–445.428) | 0.49 | 346.7 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | decode | slab-native | 441.573 (419.400–503.307) | 0.50 | 339.5 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | decode | slab-projected | 1087.166 (1048.256–1122.230) | 1.24 | 137.9 | 594.25 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| baseline | sparse | decode | slab-lazy | 1542.092 (1323.054–1606.522) | 1.76 | 97.2 | 972.25 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| baseline | sparse | decode | slab-session | 849.989 (840.218–876.377) | 0.97 | 176.4 | 40.00 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| baseline | sparse | decode | slab-session-projected | 705.471 (651.050–1117.124) | 0.81 | 212.5 | 40.00 | 0.0 | 289.2 / 0.0 | 855.6 | 149.9 |
| baseline | full | detach | stock | 1105.178 (914.628–1134.710) | 1.00 | 135.6 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | detach | generated-owned | 998.157 (933.548–1759.767) | 0.90 | 150.2 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | detach | slab | 794.260 (730.414–814.693) | 0.72 | 188.7 | 1440.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | detach | slab-native | 766.851 (700.376–783.351) | 0.69 | 195.5 | 1440.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | detach | slab-lazy | 2839.416 (2831.493–2860.946) | 2.57 | 52.8 | 3047.25 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| baseline | large | peak | stock | 18688.438 (16514.391–20056.699) | 1.00 | 2773.0 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | peak | stock-interop | 452853.203 (439739.922–490487.109) | 24.23 | 114.4 | 666098.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | peak | generated-owned | 7271.436 (6721.370–9752.854) | 0.39 | 7126.8 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | peak | slab | 16860.232 (16067.302–17096.967) | 0.90 | 3073.6 | 1.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| baseline | large | peak | slab-native | 16437.457 (16319.027–17473.777) | 0.88 | 3152.7 | 1.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| baseline | large | peak | slab-lazy | 30854.289 (29392.488–34119.864) | 1.65 | 1679.6 | 1.00 | 0.0 | 45619.8 / 281827.8 | 0.0 | 51822.3 |
