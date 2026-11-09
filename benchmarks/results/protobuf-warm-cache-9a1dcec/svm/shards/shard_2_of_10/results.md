# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | 793.6 | — | 824.7 (1.04×) | 1047.7 (1.32×) | 1053.5 (1.33×) | 1052.4 (1.33×) | 1264.2 (1.59×) | 888.5 (1.12×) | 686.2 (0.86×) | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | 65.0 | — | 37.2 (0.57×) | 300.8 (4.63×) | 298.8 (4.60×) | — | 1554.4 (23.92×) | — | — | — | — |
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
| sparse | resident | 787.0 | — | 864.5 (1.10×) | 1027.5 (1.31×) | 1031.8 (1.31×) | 1075.3 (1.37×) | 1297.8 (1.65×) | 871.0 (1.11×) | 672.7 (0.85×) | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | 66.4 | — | 35.9 (0.54×) | 302.4 (4.56×) | 308.1 (4.64×) | — | 1526.4 (23.00×) | — | — | — | — |
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
| candidate | sparse | resident | stock | 793.570 (792.857–794.649) | 1.00 | 188.9 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | resident | generated-owned | 824.675 (817.543–846.279) | 1.04 | 181.8 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | resident | slab | 1047.661 (1045.017–1049.227) | 1.32 | 143.1 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | resident | slab-native | 1053.484 (1052.202–1057.794) | 1.33 | 142.3 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | resident | slab-projected | 1052.444 (1048.396–1078.875) | 1.33 | 142.4 | 689.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | resident | slab-lazy | 1264.211 (1246.952–1266.868) | 1.59 | 118.6 | 1084.50 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | sparse | resident | slab-session | 888.495 (873.443–2700.022) | 1.12 | 168.7 | 151.25 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | sparse | resident | slab-session-projected | 686.172 (684.831–691.223) | 0.86 | 218.5 | 151.25 | 0.0 | 289.2 / 0.0 | 855.6 | 149.9 |
| candidate | full | first | stock | 64.993 (64.791–67.034) | 1.00 | 2306.5 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | first | generated-owned | 37.192 (36.135–73.997) | 0.57 | 4030.6 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | first | slab | 300.844 (297.691–304.142) | 4.63 | 498.3 | 672.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | first | slab-native | 298.849 (296.164–639.271) | 4.60 | 501.6 | 672.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | first | slab-lazy | 1554.438 (1553.006–1558.768) | 23.92 | 96.4 | 2115.00 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| baseline | sparse | resident | stock | 786.951 (786.413–793.643) | 1.00 | 190.5 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | resident | generated-owned | 864.500 (854.489–902.939) | 1.10 | 173.4 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | resident | slab | 1027.470 (1017.887–1046.118) | 1.31 | 145.9 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | resident | slab-native | 1031.817 (1022.873–1062.661) | 1.31 | 145.3 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | resident | slab-projected | 1075.281 (1072.183–1077.763) | 1.37 | 139.4 | 689.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| baseline | sparse | resident | slab-lazy | 1297.819 (1296.449–1300.536) | 1.65 | 115.5 | 1084.50 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| baseline | sparse | resident | slab-session | 871.040 (869.408–881.241) | 1.11 | 172.1 | 151.25 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| baseline | sparse | resident | slab-session-projected | 672.673 (672.089–677.540) | 0.85 | 222.9 | 151.25 | 0.0 | 289.2 / 0.0 | 855.6 | 149.9 |
| baseline | full | first | stock | 66.371 (65.326–67.846) | 1.00 | 2258.6 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | first | generated-owned | 35.930 (35.541–37.898) | 0.54 | 4172.2 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | first | slab | 302.357 (300.268–308.101) | 4.56 | 495.8 | 672.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | first | slab-native | 308.118 (299.974–313.159) | 4.64 | 486.5 | 672.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | first | slab-lazy | 1526.361 (1519.506–1532.449) | 23.00 | 98.2 | 2115.00 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
