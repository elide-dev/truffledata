# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | 14.2 | — | 12.8 (0.90×) | 50.0 (3.54×) | 49.9 (3.53×) | 57.0 (4.03×) | 57.7 (4.08×) | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | 2090.6 | — | 2155.7 (1.03×) | 3283.9 (1.57×) | 3242.1 (1.55×) | — | 5795.7 (2.77×) | 2105.6 (1.01×) | — | — | — |
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
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | 14.2 | — | 12.6 (0.89×) | 48.1 (3.39×) | 47.9 (3.37×) | 52.5 (3.70×) | 55.3 (3.89×) | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | 2132.7 | — | 2208.2 (1.04×) | 3233.3 (1.52×) | 3279.4 (1.54×) | — | 5591.1 (2.62×) | 2126.8 (1.00×) | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | sparse | first | stock | 14.152 (13.535–14.424) | 1.00 | 10592.7 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | first | generated-owned | 12.751 (12.705–12.785) | 0.90 | 11756.2 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | first | slab | 50.031 (48.839–1181.134) | 3.54 | 2996.3 | 126.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | first | slab-native | 49.942 (49.161–50.556) | 3.53 | 3001.6 | 126.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | first | slab-projected | 57.022 (54.817–58.427) | 4.03 | 2628.9 | 95.50 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | first | slab-lazy | 57.743 (57.616–61.187) | 4.08 | 2596.1 | 112.25 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | holdout | resident | stock | 2090.604 (2072.535–2123.416) | 1.00 | 244.0 | 5310.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | resident | generated-owned | 2155.745 (2152.199–2159.978) | 1.03 | 236.6 | 5427.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | resident | slab | 3283.862 (3249.982–3539.859) | 1.57 | 155.3 | 3445.25 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout | resident | slab-native | 3242.144 (3237.587–3255.915) | 1.55 | 157.3 | 3445.25 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout | resident | slab-lazy | 5795.711 (5789.993–5806.653) | 2.77 | 88.0 | 7743.75 | 0.0 | 528.5 / 2471.0 | 0.0 | 510.0 |
| candidate | holdout | resident | slab-session | 2105.593 (2102.607–2149.708) | 1.01 | 242.2 | 1197.00 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
| baseline | sparse | first | stock | 14.196 (14.109–14.205) | 1.00 | 10559.6 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | first | generated-owned | 12.622 (12.239–13.641) | 0.89 | 11876.7 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | first | slab | 48.144 (46.866–52.571) | 3.39 | 3113.7 | 126.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | first | slab-native | 47.865 (46.730–48.343) | 3.37 | 3131.8 | 126.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | first | slab-projected | 52.467 (52.103–53.733) | 3.70 | 2857.2 | 95.50 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| baseline | sparse | first | slab-lazy | 55.277 (53.913–55.404) | 3.89 | 2711.9 | 112.25 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| baseline | holdout | resident | stock | 2132.719 (2106.384–2157.895) | 1.00 | 239.1 | 5310.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | resident | generated-owned | 2208.225 (2195.535–2278.711) | 1.04 | 231.0 | 5427.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | resident | slab | 3233.347 (3221.317–3290.062) | 1.52 | 157.7 | 3445.25 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| baseline | holdout | resident | slab-native | 3279.385 (3254.742–3282.691) | 1.54 | 155.5 | 3445.25 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| baseline | holdout | resident | slab-lazy | 5591.132 (5525.198–5649.085) | 2.62 | 91.2 | 7743.75 | 0.0 | 528.5 / 2471.0 | 0.0 | 510.0 |
| baseline | holdout | resident | slab-session | 2126.769 (2099.175–2127.639) | 1.00 | 239.8 | 1197.00 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
