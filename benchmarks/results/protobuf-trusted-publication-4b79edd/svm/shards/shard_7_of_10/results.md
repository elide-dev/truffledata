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
| sparse | peak | 12.5 | 145.9 (11.71×) | 9.9 (0.80×) | 14.4 (1.16×) | 14.5 (1.16×) | 27.7 (2.22×) | 29.7 (2.38×) | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | 2578.9 | — | 2835.4 (1.10×) | 1162.8 (0.45×) | 1124.8 (0.44×) | 2087.1 (0.81×) | 4397.3 (1.71×) | 2265.8 (0.88×) | 1547.0 (0.60×) | — | — |
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
| sparse | peak | 11.9 | — | 10.0 (0.84×) | 14.2 (1.20×) | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
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
| candidate | sparse | peak | stock | 12.465 (11.942–16.847) | 1.00 | 12026.5 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | peak | stock-interop | 145.925 (142.992–146.484) | 11.71 | 1027.3 | 192.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | peak | generated-owned | 9.934 (9.866–11.100) | 0.80 | 15090.7 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | peak | slab | 14.443 (13.864–14.633) | 1.16 | 10379.2 | 1.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | peak | slab-native | 14.517 (14.170–16.755) | 1.16 | 10326.3 | 1.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | peak | slab-projected | 27.664 (27.022–29.265) | 2.22 | 5418.8 | 1.00 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | peak | slab-lazy | 29.667 (28.712–40.151) | 2.38 | 5053.0 | 1.00 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | holdout-sparse | resident | stock | 2578.904 (2559.736–2682.671) | 1.00 | 197.8 | 5310.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | generated-owned | 2835.383 (2720.126–3823.992) | 1.10 | 179.9 | 5427.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab | 1162.762 (1143.917–1205.657) | 0.45 | 438.6 | 2466.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab-native | 1124.767 (1092.097–1147.533) | 0.44 | 453.5 | 2466.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab-projected | 2087.088 (1923.753–13469.910) | 0.81 | 244.4 | 689.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab-lazy | 4397.250 (2577.591–27322.052) | 1.71 | 116.0 | 1807.75 | 0.0 | 528.5 / 528.5 | 0.0 | 510.0 |
| candidate | holdout-sparse | resident | slab-session | 2265.781 (2265.157–2302.588) | 0.88 | 225.1 | 151.25 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
| candidate | holdout-sparse | resident | slab-session-projected | 1547.022 (1526.639–1640.575) | 0.60 | 329.7 | 151.25 | 0.0 | 528.5 / 0.0 | 2296.1 | 510.0 |
| baseline | sparse | peak | stock | 11.900 (11.740–13.282) | 1.00 | 12596.7 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | peak | generated-owned | 10.048 (10.035–10.098) | 0.84 | 14918.3 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | peak | slab | 14.230 (14.180–15.667) | 1.20 | 10534.2 | 1.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
