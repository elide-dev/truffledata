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
| sparse | peak | 12.0 | 143.7 (11.96×) | 10.4 (0.87×) | 14.0 (1.16×) | 14.1 (1.18×) | 29.6 (2.46×) | 27.2 (2.26×) | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | 2585.7 | — | 2918.5 (1.13×) | 1095.0 (0.42×) | 1205.6 (0.47×) | 3200.5 (1.24×) | 2619.0 (1.01×) | 2238.7 (0.87×) | 1529.9 (0.59×) | — | — |
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
| sparse | peak | 11.9 | 177.1 (14.92×) | 10.4 (0.88×) | 14.3 (1.21×) | 14.6 (1.23×) | 28.9 (2.44×) | 29.6 (2.50×) | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | 2529.5 | — | 2745.0 (1.09×) | 1147.8 (0.45×) | 1108.8 (0.44×) | 1783.4 (0.71×) | 2390.7 (0.95×) | 2246.5 (0.89×) | 1506.9 (0.60×) | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | sparse | peak | stock | 12.019 (11.939–12.190) | 1.00 | 12472.1 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | peak | stock-interop | 143.730 (142.193–274.726) | 11.96 | 1043.0 | 192.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | peak | generated-owned | 10.397 (10.000–15.007) | 0.87 | 14417.8 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | peak | slab | 13.986 (13.852–17.930) | 1.16 | 10718.2 | 1.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | peak | slab-native | 14.147 (14.077–14.168) | 1.18 | 10596.3 | 1.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | peak | slab-projected | 29.590 (28.665–55.569) | 2.46 | 5066.2 | 1.00 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | peak | slab-lazy | 27.188 (26.681–41.153) | 2.26 | 5513.6 | 1.00 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | holdout-sparse | resident | stock | 2585.742 (2537.425–2588.460) | 1.00 | 197.2 | 5310.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | generated-owned | 2918.451 (2732.441–3094.933) | 1.13 | 174.8 | 5427.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab | 1095.008 (1080.501–1116.617) | 0.42 | 465.8 | 2466.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab-native | 1205.618 (1093.038–1240.589) | 0.47 | 423.0 | 2466.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab-projected | 3200.518 (1964.488–10555.466) | 1.24 | 159.4 | 689.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab-lazy | 2619.047 (2449.856–25731.804) | 1.01 | 194.7 | 1807.75 | 0.0 | 528.5 / 528.5 | 0.0 | 510.0 |
| candidate | holdout-sparse | resident | slab-session | 2238.696 (2186.428–2838.508) | 0.87 | 227.8 | 151.25 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
| candidate | holdout-sparse | resident | slab-session-projected | 1529.865 (1525.806–1681.267) | 0.59 | 333.4 | 151.25 | 0.0 | 528.5 / 0.0 | 2296.1 | 510.0 |
| baseline | sparse | peak | stock | 11.863 (11.834–14.006) | 1.00 | 12636.3 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | peak | stock-interop | 177.051 (139.473–179.884) | 14.92 | 846.7 | 192.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | peak | generated-owned | 10.431 (10.298–15.166) | 0.88 | 14370.5 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | peak | slab | 14.304 (14.208–16.202) | 1.21 | 10480.0 | 1.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | peak | slab-native | 14.624 (14.105–20.859) | 1.23 | 10251.0 | 1.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | peak | slab-projected | 28.896 (27.717–34.429) | 2.44 | 5187.7 | 1.00 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| baseline | sparse | peak | slab-lazy | 29.620 (27.388–42.306) | 2.50 | 5060.9 | 1.00 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| baseline | holdout-sparse | resident | stock | 2529.537 (2450.461–2677.058) | 1.00 | 201.6 | 5310.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout-sparse | resident | generated-owned | 2744.952 (2688.030–2963.738) | 1.09 | 185.8 | 5427.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout-sparse | resident | slab | 1147.781 (1129.646–1358.872) | 0.45 | 444.4 | 2466.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| baseline | holdout-sparse | resident | slab-native | 1108.757 (1094.487–1125.637) | 0.44 | 460.0 | 2466.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| baseline | holdout-sparse | resident | slab-projected | 1783.367 (1752.188–1832.635) | 0.71 | 286.0 | 689.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| baseline | holdout-sparse | resident | slab-lazy | 2390.659 (2378.964–2617.121) | 0.95 | 213.3 | 1807.75 | 0.0 | 528.5 / 528.5 | 0.0 | 510.0 |
| baseline | holdout-sparse | resident | slab-session | 2246.471 (2238.132–3773.146) | 0.89 | 227.0 | 151.25 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
| baseline | holdout-sparse | resident | slab-session-projected | 1506.934 (1491.744–1618.591) | 0.60 | 338.5 | 151.25 | 0.0 | 528.5 / 0.0 | 2296.1 | 510.0 |
