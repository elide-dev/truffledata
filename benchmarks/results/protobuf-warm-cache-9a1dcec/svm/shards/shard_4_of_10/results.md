# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | 1530.5 | — | 1737.2 (1.14×) | 2404.0 (1.57×) | 2275.9 (1.49×) | 1847.7 (1.21×) | 2953.1 (1.93×) | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | 106.4 | 3115.8 (29.27×) | 49.5 (0.46×) | 206.9 (1.94×) | 205.3 (1.93×) | — | 280.6 (2.64×) | — | — | — | — |
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
| sparse | detach | 1535.2 | — | 1754.6 (1.14×) | 2276.0 (1.48×) | 2371.0 (1.54×) | 1862.4 (1.21×) | 2305.0 (1.50×) | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | 106.2 | 3116.5 (29.34×) | 49.0 (0.46×) | 311.4 (2.93×) | 316.2 (2.98×) | — | 387.5 (3.65×) | — | — | — | — |
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
| candidate | sparse | detach | stock | 1530.534 (1509.569–1542.226) | 1.00 | 97.9 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | detach | generated-owned | 1737.249 (1733.599–1739.020) | 1.14 | 86.3 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | detach | slab | 2404.017 (2290.920–2434.845) | 1.57 | 62.4 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | detach | slab-native | 2275.878 (2263.740–3729.340) | 1.49 | 65.9 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | detach | slab-projected | 1847.741 (1826.663–5529.228) | 1.21 | 81.1 | 689.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | detach | slab-lazy | 2953.101 (2243.986–3747.048) | 1.93 | 50.8 | 1084.50 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | full | peak | stock | 106.440 (106.199–106.744) | 1.00 | 1408.4 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | peak | stock-interop | 3115.777 (3080.534–5798.644) | 29.27 | 48.1 | 1874.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | peak | generated-owned | 49.469 (48.637–53.100) | 0.46 | 3030.3 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | peak | slab | 206.949 (204.740–271.546) | 1.94 | 724.4 | 17.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | peak | slab-native | 205.274 (205.272–207.573) | 1.93 | 730.3 | 17.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | peak | slab-lazy | 280.569 (280.299–281.888) | 2.64 | 534.3 | 17.00 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| baseline | sparse | detach | stock | 1535.218 (1510.320–1562.545) | 1.00 | 97.6 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | detach | generated-owned | 1754.581 (1735.385–1999.808) | 1.14 | 85.4 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | detach | slab | 2275.976 (2274.352–2477.195) | 1.48 | 65.9 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | detach | slab-native | 2370.996 (2276.680–2417.560) | 1.54 | 63.2 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | detach | slab-projected | 1862.438 (1841.051–3837.850) | 1.21 | 80.5 | 689.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| baseline | sparse | detach | slab-lazy | 2305.008 (2251.464–2390.242) | 1.50 | 65.0 | 1084.50 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| baseline | full | peak | stock | 106.228 (105.920–122.074) | 1.00 | 1411.2 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | peak | stock-interop | 3116.490 (3089.744–5928.854) | 29.34 | 48.1 | 1874.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | peak | generated-owned | 48.959 (48.878–49.046) | 0.46 | 3061.9 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | peak | slab | 311.371 (311.186–527.979) | 2.93 | 481.4 | 17.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | peak | slab-native | 316.231 (312.055–504.523) | 2.98 | 474.0 | 17.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | peak | slab-lazy | 387.504 (386.933–519.738) | 3.65 | 386.8 | 17.00 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
