# Consumer comparison (jvm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | 763.6 | — | 348.0 (0.46×) | 452.0 (0.59×) | 991.0 (1.30×) | 397.7 (0.52×) | 243.1 (0.32×) | 637.8 (0.84×) | 578.1 (0.76×) | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | 28.9 | — | 29.5 (1.02×) | 229.8 (7.96×) | 241.3 (8.36×) | — | 711.9 (24.67×) | — | — | — | — |
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
| sparse | resident | 842.5 | — | 350.3 (0.42×) | — | — | — | — | — | — | 833.2 (0.99×) | 1233.0 (1.46×) |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | 28.6 | — | 31.6 (1.10×) | — | — | — | — | — | — | 415.3 (14.50×) | 64.3 (2.24×) |
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
| candidate | sparse | resident | stock | 763.573 (293.941–860.106) | 1.00 | 196.3 | 1393.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | resident | generated-owned | 347.971 (347.351–364.903) | 0.46 | 430.8 | 1838.75 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | resident | slab | 451.977 (442.137–457.428) | 0.59 | 331.7 | 991.00 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | sparse | resident | slab-native | 990.972 (971.935–993.111) | 1.30 | 151.3 | 1185.50 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | sparse | resident | slab-projected | 397.696 (391.166–398.537) | 0.52 | 376.9 | 1185.50 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | resident | slab-lazy | 243.058 (240.266–243.960) | 0.32 | 616.7 | 944.25 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | sparse | resident | slab-session | 637.810 (637.077–647.733) | 0.84 | 235.0 | 138.75 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | sparse | resident | slab-session-projected | 578.133 (562.568–598.491) | 0.76 | 259.3 | 138.75 | 0.0 | 289.2 / 0.0 | 855.6 | 149.9 |
| candidate | full | first | stock | 28.862 (28.568–28.952) | 1.00 | 5194.0 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | first | generated-owned | 29.530 (29.027–29.975) | 1.02 | 5076.4 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | first | slab | 229.786 (227.200–242.559) | 7.96 | 652.4 | 779.75 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | first | slab-native | 241.309 (240.072–241.858) | 8.36 | 621.2 | 779.75 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | first | slab-lazy | 711.908 (708.755–1094.971) | 24.67 | 210.6 | 2308.25 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| baseline | sparse | resident | stock | 842.536 (807.870–874.102) | 1.00 | 177.9 | 1393.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | resident | generated-owned | 350.271 (349.791–352.574) | 0.42 | 428.0 | 1838.75 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | resident | native-view | 833.186 (827.881–841.410) | 0.99 | 179.9 | 1017.25 | 1165.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | resident | native-eager | 1232.971 (1208.393–1317.303) | 1.46 | 121.6 | 1962.00 | 1141.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | first | stock | 28.643 (28.543–29.288) | 1.00 | 5233.7 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | first | generated-owned | 31.608 (31.519–31.933) | 1.10 | 4742.7 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | first | native-view | 415.339 (415.066–446.696) | 14.50 | 360.9 | 1588.75 | 1165.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | first | native-eager | 64.272 (62.241–73.083) | 2.24 | 2332.4 | 1.50 | 1141.5 | 0.0 / 0.0 | 0.0 | 0.0 |
