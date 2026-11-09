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
| sparse | peak | 11.8 | 140.9 (11.97×) | 10.7 (0.91×) | 21.1 (1.80×) | 21.2 (1.80×) | 26.4 (2.24×) | 26.3 (2.24×) | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | 2478.6 | — | 2692.5 (1.09×) | 3651.0 (1.47×) | 3631.0 (1.46×) | 1696.2 (0.68×) | 2366.1 (0.95×) | 2164.1 (0.87×) | 1598.2 (0.64×) | — | — |
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
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | 11.7 | 138.6 (11.80×) | 10.9 (0.93×) | 30.1 (2.56×) | 30.5 (2.60×) | 33.9 (2.89×) | 34.2 (2.91×) | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | 2365.2 | — | 2673.4 (1.13×) | 3507.2 (1.48×) | 3725.3 (1.58×) | 1768.7 (0.75×) | 2356.3 (1.00×) | 2163.7 (0.91×) | 1434.3 (0.61×) | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | sparse | peak | stock | 11.773 (11.660–12.722) | 1.00 | 12733.0 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | peak | stock-interop | 140.892 (140.161–145.266) | 11.97 | 1064.0 | 192.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | peak | generated-owned | 10.749 (9.918–11.797) | 0.91 | 13945.4 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | peak | slab | 21.134 (20.966–22.911) | 1.80 | 7093.1 | 1.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | peak | slab-native | 21.168 (21.122–22.181) | 1.80 | 7081.6 | 1.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | peak | slab-projected | 26.401 (26.079–27.246) | 2.24 | 5678.0 | 1.00 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | peak | slab-lazy | 26.348 (26.193–26.560) | 2.24 | 5689.5 | 1.00 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | holdout-sparse | resident | stock | 2478.560 (2458.655–2497.213) | 1.00 | 205.8 | 5310.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | generated-owned | 2692.512 (2689.900–2855.592) | 1.09 | 189.4 | 5427.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab | 3650.992 (3499.947–31149.388) | 1.47 | 139.7 | 2480.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab-native | 3630.959 (3497.040–3751.800) | 1.46 | 140.5 | 2480.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab-projected | 1696.218 (1691.417–1782.799) | 0.68 | 300.7 | 689.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab-lazy | 2366.150 (2317.876–2378.156) | 0.95 | 215.6 | 1807.75 | 0.0 | 528.5 / 528.5 | 0.0 | 510.0 |
| candidate | holdout-sparse | resident | slab-session | 2164.116 (2150.787–2182.241) | 0.87 | 235.7 | 151.25 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
| candidate | holdout-sparse | resident | slab-session-projected | 1598.203 (1435.219–1723.551) | 0.64 | 319.1 | 151.25 | 0.0 | 528.5 / 0.0 | 2296.1 | 510.0 |
| baseline | sparse | peak | stock | 11.745 (11.728–11.848) | 1.00 | 12763.0 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | peak | stock-interop | 138.618 (137.320–141.847) | 11.80 | 1081.4 | 192.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | peak | generated-owned | 10.916 (9.837–11.144) | 0.93 | 13732.8 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | peak | slab | 30.053 (29.689–30.147) | 2.56 | 4988.0 | 1.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | peak | slab-native | 30.523 (29.959–30.708) | 2.60 | 4911.3 | 1.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | peak | slab-projected | 33.899 (33.656–35.645) | 2.89 | 4422.2 | 1.00 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| baseline | sparse | peak | slab-lazy | 34.204 (33.992–36.546) | 2.91 | 4382.7 | 1.00 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| baseline | holdout-sparse | resident | stock | 2365.164 (2342.613–2382.064) | 1.00 | 215.6 | 5310.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout-sparse | resident | generated-owned | 2673.379 (2643.890–2768.650) | 1.13 | 190.8 | 5427.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout-sparse | resident | slab | 3507.243 (3505.810–3513.431) | 1.48 | 145.4 | 2480.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| baseline | holdout-sparse | resident | slab-native | 3725.336 (3463.488–3726.496) | 1.58 | 136.9 | 2480.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| baseline | holdout-sparse | resident | slab-projected | 1768.717 (1683.310–1928.680) | 0.75 | 288.4 | 689.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| baseline | holdout-sparse | resident | slab-lazy | 2356.321 (2353.829–2380.656) | 1.00 | 216.5 | 1807.75 | 0.0 | 528.5 / 528.5 | 0.0 | 510.0 |
| baseline | holdout-sparse | resident | slab-session | 2163.693 (2156.823–2167.460) | 0.91 | 235.7 | 151.25 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
| baseline | holdout-sparse | resident | slab-session-projected | 1434.251 (1425.402–1566.386) | 0.61 | 355.6 | 151.25 | 0.0 | 528.5 / 0.0 | 2296.1 | 510.0 |
