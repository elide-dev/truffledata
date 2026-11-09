# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | 839.6 | — | 854.6 (1.02×) | 1542.7 (1.84×) | 1292.9 (1.54×) | — | 2802.5 (3.34×) | 1147.3 (1.37×) | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | 217520.3 | — | 250375.2 (1.15×) | 293893.8 (1.35×) | 293722.0 (1.35×) | — | 158310.1 (0.73×) | 213637.2 (0.98×) | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## baseline: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | 849.6 | — | 899.5 (1.06×) | 1304.8 (1.54×) | 1290.0 (1.52×) | — | 2789.4 (3.28×) | 1086.0 (1.28×) | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | 228737.6 | — | 261732.8 (1.14×) | 298428.1 (1.30×) | 286891.3 (1.25×) | — | 158378.2 (0.69×) | 211174.8 (0.92×) | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | full | resident | stock | 839.597 (836.007–862.331) | 1.00 | 178.5 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | resident | generated-owned | 854.648 (851.516–909.024) | 1.02 | 175.4 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | resident | slab | 1542.722 (1288.360–19819.786) | 1.84 | 97.2 | 1480.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | resident | slab-native | 1292.950 (1288.957–1295.132) | 1.54 | 115.9 | 1480.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | resident | slab-lazy | 2802.539 (2763.594–2847.552) | 3.34 | 53.5 | 3087.25 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| candidate | full | resident | slab-session | 1147.293 (1119.486–1225.757) | 1.37 | 130.7 | 718.25 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | large | decode | stock | 217520.309 (217137.637–1563128.594) | 1.00 | 238.2 | 433008.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | decode | generated-owned | 250375.210 (239518.174–266130.224) | 1.15 | 207.0 | 460179.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | decode | slab | 293893.818 (293181.318–308893.271) | 1.35 | 176.3 | 226294.50 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| candidate | large | decode | slab-native | 293722.026 (289967.859–297411.484) | 1.35 | 176.4 | 226294.50 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| candidate | large | decode | slab-lazy | 158310.056 (156244.019–163747.303) | 0.73 | 327.3 | 116107.75 | 0.0 | 45619.8 / 45619.8 | 0.0 | 51822.3 |
| candidate | large | decode | slab-session | 213637.211 (211175.664–215277.426) | 0.98 | 242.6 | 40.00 | 0.0 | 172771.8 / 0.0 | 207545.1 | 0.0 |
| baseline | full | resident | stock | 849.611 (846.130–1006.921) | 1.00 | 176.4 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | resident | generated-owned | 899.472 (891.021–1007.802) | 1.06 | 166.7 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | resident | slab | 1304.822 (1262.049–1327.408) | 1.54 | 114.9 | 1480.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | resident | slab-native | 1289.960 (1267.738–1338.344) | 1.52 | 116.2 | 1480.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | resident | slab-lazy | 2789.399 (2769.519–3591.425) | 3.28 | 53.7 | 3087.25 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| baseline | full | resident | slab-session | 1086.018 (1081.974–1110.731) | 1.28 | 138.0 | 718.25 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| baseline | large | decode | stock | 228737.571 (222084.273–241621.835) | 1.00 | 226.6 | 433008.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | decode | generated-owned | 261732.781 (248278.429–281821.292) | 1.14 | 198.0 | 460179.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | decode | slab | 298428.109 (286873.995–306067.693) | 1.30 | 173.7 | 226294.50 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| baseline | large | decode | slab-native | 286891.292 (286228.188–295624.745) | 1.25 | 180.6 | 226294.50 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| baseline | large | decode | slab-lazy | 158378.222 (157354.750–159575.163) | 0.69 | 327.2 | 116107.75 | 0.0 | 45619.8 / 45619.8 | 0.0 | 51822.3 |
| baseline | large | decode | slab-session | 211174.844 (209879.125–212048.676) | 0.92 | 245.4 | 40.00 | 0.0 | 172771.8 / 0.0 | 207545.1 | 0.0 |
