# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | 864.9 | — | 921.0 (1.06×) | 484.8 (0.56×) | 473.3 (0.55×) | 1163.6 (1.35×) | 1404.6 (1.62×) | 1426.9 (1.65×) | 795.4 (0.92×) | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | 76.6 | — | 50.0 (0.65×) | 309.8 (4.05×) | 306.8 (4.01×) | — | 1972.9 (25.77×) | — | — | — | — |
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
| sparse | resident | 853.3 | — | 891.6 (1.04×) | 1016.9 (1.19×) | — | — | — | 934.4 (1.10×) | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | 74.8 | — | 41.9 (0.56×) | 321.5 (4.30×) | — | — | — | — | — | — | — |
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
| candidate | sparse | resident | stock | 864.895 (860.255–893.714) | 1.00 | 173.3 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | resident | generated-owned | 921.041 (859.284–931.048) | 1.06 | 162.8 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | resident | slab | 484.813 (473.859–812.122) | 0.56 | 309.2 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | resident | slab-native | 473.285 (471.107–11177.900) | 0.55 | 316.7 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | resident | slab-projected | 1163.567 (1149.974–1193.922) | 1.35 | 128.8 | 689.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | resident | slab-lazy | 1404.571 (1382.590–1442.246) | 1.62 | 106.7 | 1084.50 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | sparse | resident | slab-session | 1426.919 (1252.183–1887.962) | 1.65 | 105.1 | 151.25 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | sparse | resident | slab-session-projected | 795.445 (745.226–1042.109) | 0.92 | 188.5 | 151.25 | 0.0 | 289.2 / 0.0 | 855.6 | 149.9 |
| candidate | full | first | stock | 76.564 (71.750–80.232) | 1.00 | 1957.9 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | first | generated-owned | 50.048 (45.529–71.831) | 0.65 | 2995.2 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | first | slab | 309.780 (295.563–518.578) | 4.05 | 483.9 | 632.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | first | slab-native | 306.768 (294.618–378.253) | 4.01 | 488.7 | 632.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | first | slab-lazy | 1972.945 (1658.383–5216.335) | 25.77 | 76.0 | 2075.00 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| baseline | sparse | resident | stock | 853.327 (829.685–897.315) | 1.00 | 175.7 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | resident | generated-owned | 891.621 (876.045–949.062) | 1.04 | 168.1 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | resident | slab | 1016.861 (999.136–1627.450) | 1.19 | 147.4 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | resident | slab-session | 934.426 (929.969–947.533) | 1.10 | 160.4 | 151.25 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| baseline | full | first | stock | 74.755 (66.792–81.508) | 1.00 | 2005.3 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | first | generated-owned | 41.875 (36.912–48.862) | 0.56 | 3579.8 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | first | slab | 321.522 (320.296–333.698) | 4.30 | 466.2 | 632.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
