# Consumer comparison (jvm)

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
| full | resident | 1139.3 | — | 604.7 (0.53×) | 3289.5 (2.89×) | 5670.9 (4.98×) | — | 5182.0 (4.55×) | 1437.3 (1.26×) | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | 105680.2 | — | 236198.5 (2.24×) | 227121.4 (2.15×) | 353517.2 (3.35×) | — | 76818.4 (0.73×) | 248895.7 (2.36×) | — | — | — |
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
| full | resident | 426.2 | — | 599.8 (1.41×) | — | — | — | — | — | — | 1668.8 (3.92×) | 2054.0 (4.82×) |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | 106023.4 | — | 240981.0 (2.27×) | — | — | — | — | — | — | 301718.2 (2.85×) | 492423.6 (4.64×) |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | full | resident | stock | 1139.312 (484.053–1530.721) | 1.00 | 131.6 | 1393.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | resident | generated-owned | 604.717 (602.936–625.346) | 0.53 | 247.9 | 1838.75 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | resident | slab | 3289.493 (3224.525–3355.598) | 2.89 | 45.6 | 1649.00 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | resident | slab-native | 5670.870 (5368.589–5966.568) | 4.98 | 26.4 | 1843.50 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | resident | slab-lazy | 5182.038 (5162.908–5904.236) | 4.55 | 28.9 | 3145.00 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| candidate | full | resident | slab-session | 1437.304 (1432.785–1454.531) | 1.26 | 104.3 | 728.75 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | large | decode | stock | 105680.177 (103419.388–106894.294) | 1.00 | 490.4 | 306043.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | decode | generated-owned | 236198.462 (229308.692–266810.961) | 2.24 | 219.4 | 533143.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | decode | slab | 227121.413 (217205.398–256287.697) | 2.15 | 228.2 | 351223.00 | 0.0 | 172771.8 / 172771.8 | 0.0 | 0.0 |
| candidate | large | decode | slab-native | 353517.243 (353084.951–359760.875) | 3.35 | 146.6 | 209578.25 | 0.0 | 172771.8 / 172771.8 | 0.0 | 0.0 |
| candidate | large | decode | slab-lazy | 76818.401 (76544.870–77453.041) | 0.73 | 674.6 | 198198.75 | 0.0 | 45619.8 / 45619.8 | 0.0 | 51822.3 |
| candidate | large | decode | slab-session | 248895.714 (248337.668–249637.846) | 2.36 | 208.2 | 48.00 | 0.0 | 172771.8 / 0.0 | 207545.1 | 0.0 |
| baseline | full | resident | stock | 426.226 (420.317–435.377) | 1.00 | 351.7 | 1393.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | resident | generated-owned | 599.843 (596.759–605.807) | 1.41 | 249.9 | 1838.75 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | resident | native-view | 1668.779 (1658.144–1672.027) | 3.92 | 89.8 | 1924.75 | 1165.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | resident | native-eager | 2053.955 (2025.748–7066.842) | 4.82 | 73.0 | 2177.00 | 1141.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | decode | stock | 106023.431 (99781.335–106904.372) | 1.00 | 488.8 | 306067.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | decode | generated-owned | 240980.954 (233220.371–285365.339) | 2.27 | 215.0 | 533143.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | decode | native-view | 301718.202 (292215.983–526651.708) | 2.85 | 171.8 | 336.00 | 409153.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | decode | native-eager | 492423.634 (490828.661–493798.643) | 4.64 | 105.2 | 452185.75 | 409129.5 | 0.0 / 0.0 | 0.0 | 0.0 |
