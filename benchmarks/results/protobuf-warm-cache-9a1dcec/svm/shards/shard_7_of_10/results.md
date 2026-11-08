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
| sparse | peak | 11.7 | 140.5 (11.98×) | 9.8 (0.83×) | 21.0 (1.79×) | 21.0 (1.79×) | 26.0 (2.22×) | 26.7 (2.28×) | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | 2405.2 | — | 2590.0 (1.08×) | 3475.4 (1.44×) | 3503.8 (1.46×) | 1679.1 (0.70×) | 2269.1 (0.94×) | 2157.1 (0.90×) | 1471.7 (0.61×) | — | — |
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
| sparse | peak | 11.6 | 136.7 (11.77×) | 9.7 (0.84×) | 29.5 (2.54×) | 29.5 (2.54×) | 33.5 (2.88×) | 34.0 (2.93×) | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | 2335.9 | — | 2636.4 (1.13×) | 3457.0 (1.48×) | 3430.8 (1.47×) | 1676.7 (0.72×) | 2302.7 (0.99×) | 2149.2 (0.92×) | 1429.0 (0.61×) | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | sparse | peak | stock | 11.723 (11.632–11.753) | 1.00 | 12787.0 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | peak | stock-interop | 140.503 (137.484–178.032) | 11.98 | 1066.9 | 192.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | peak | generated-owned | 9.774 (9.771–10.062) | 0.83 | 15337.2 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | peak | slab | 21.017 (20.949–21.110) | 1.79 | 7132.8 | 1.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | peak | slab-native | 20.984 (20.957–21.002) | 1.79 | 7143.9 | 1.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | peak | slab-projected | 26.008 (25.914–26.009) | 2.22 | 5763.9 | 1.00 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | peak | slab-lazy | 26.689 (26.239–26.778) | 2.28 | 5616.7 | 1.00 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | holdout-sparse | resident | stock | 2405.178 (2404.050–2415.191) | 1.00 | 212.1 | 5310.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | generated-owned | 2590.019 (2568.096–2622.223) | 1.08 | 196.9 | 5427.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab | 3475.374 (3469.034–29325.393) | 1.44 | 146.8 | 2480.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab-native | 3503.794 (3426.546–3674.774) | 1.46 | 145.6 | 2480.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab-projected | 1679.099 (1662.967–1699.100) | 0.70 | 303.8 | 689.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab-lazy | 2269.101 (2260.353–2657.123) | 0.94 | 224.8 | 1807.75 | 0.0 | 528.5 / 528.5 | 0.0 | 510.0 |
| candidate | holdout-sparse | resident | slab-session | 2157.093 (2151.702–2163.538) | 0.90 | 236.4 | 151.25 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
| candidate | holdout-sparse | resident | slab-session-projected | 1471.658 (1452.811–1507.760) | 0.61 | 346.6 | 151.25 | 0.0 | 528.5 / 0.0 | 2296.1 | 510.0 |
| baseline | sparse | peak | stock | 11.616 (11.570–11.636) | 1.00 | 12904.6 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | peak | stock-interop | 136.699 (136.521–137.423) | 11.77 | 1096.6 | 192.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | peak | generated-owned | 9.723 (9.721–9.764) | 0.84 | 15417.3 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | peak | slab | 29.496 (29.375–744.223) | 2.54 | 5082.3 | 1.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | peak | slab-native | 29.525 (29.482–29.575) | 2.54 | 5077.3 | 1.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | peak | slab-projected | 33.511 (33.322–33.559) | 2.88 | 4473.4 | 1.00 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| baseline | sparse | peak | slab-lazy | 34.019 (33.775–34.034) | 2.93 | 4406.6 | 1.00 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| baseline | holdout-sparse | resident | stock | 2335.913 (2332.614–2409.424) | 1.00 | 218.3 | 5310.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout-sparse | resident | generated-owned | 2636.410 (2634.182–2657.988) | 1.13 | 193.5 | 5427.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout-sparse | resident | slab | 3457.004 (3456.452–3552.658) | 1.48 | 147.5 | 2480.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| baseline | holdout-sparse | resident | slab-native | 3430.831 (3414.639–3535.494) | 1.47 | 148.7 | 2480.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| baseline | holdout-sparse | resident | slab-projected | 1676.683 (1672.088–1687.380) | 0.72 | 304.2 | 689.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| baseline | holdout-sparse | resident | slab-lazy | 2302.686 (2298.817–2313.729) | 0.99 | 221.5 | 1807.75 | 0.0 | 528.5 / 528.5 | 0.0 | 510.0 |
| baseline | holdout-sparse | resident | slab-session | 2149.221 (2147.973–2229.593) | 0.92 | 237.3 | 151.25 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
| baseline | holdout-sparse | resident | slab-session-projected | 1429.036 (1412.057–1437.206) | 0.61 | 356.9 | 151.25 | 0.0 | 528.5 / 0.0 | 2296.1 | 510.0 |
