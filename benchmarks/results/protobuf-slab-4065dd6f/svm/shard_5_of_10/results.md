# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | 14.4 | — | 13.4 (0.93×) | 61.2 (4.24×) | 68.4 (4.74×) | 54.7 (3.79×) | 60.0 (4.15×) | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | 2228.2 | — | 2211.4 (0.99×) | 3754.4 (1.68×) | 3732.2 (1.67×) | — | 5571.0 (2.50×) | 2121.8 (0.95×) | — | — | — |
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
| sparse | first | 14.2 | — | 12.8 (0.90×) | — | — | — | — | — | — | 118.5 (8.32×) | 21.2 (1.49×) |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | 2113.7 | — | 2124.6 (1.01×) | — | — | — | — | — | — | 2920.4 (1.38×) | 4034.6 (1.91×) |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | sparse | first | stock | 14.437 (14.253–14.935) | 1.00 | 10383.8 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | first | generated-owned | 13.432 (13.223–13.496) | 0.93 | 11160.8 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | first | slab | 61.247 (57.476–840.676) | 4.24 | 2447.5 | 126.50 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | sparse | first | slab-native | 68.363 (65.814–1202.851) | 4.74 | 2192.8 | 126.50 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | sparse | first | slab-projected | 54.652 (54.383–55.149) | 3.79 | 2742.9 | 95.50 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | first | slab-lazy | 59.981 (54.894–61.190) | 4.15 | 2499.2 | 112.25 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | holdout | resident | stock | 2228.182 (2091.768–2502.863) | 1.00 | 228.9 | 5310.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | resident | generated-owned | 2211.383 (2198.000–2226.995) | 0.99 | 230.6 | 5427.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | resident | slab | 3754.355 (3725.212–3761.036) | 1.68 | 135.9 | 3425.75 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout | resident | slab-native | 3732.181 (3715.428–3738.579) | 1.67 | 136.7 | 3425.75 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout | resident | slab-lazy | 5570.990 (5535.732–6013.691) | 2.50 | 91.6 | 7914.50 | 0.0 | 528.5 / 2471.0 | 0.0 | 510.0 |
| candidate | holdout | resident | slab-session | 2121.846 (2111.253–2129.293) | 0.95 | 240.4 | 1197.00 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
| baseline | sparse | first | stock | 14.246 (13.865–14.680) | 1.00 | 10522.4 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | first | generated-owned | 12.810 (11.680–13.195) | 0.90 | 11701.9 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | first | native-view | 118.479 (116.937–121.219) | 8.32 | 1265.3 | 312.00 | 1165.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | first | native-eager | 21.223 (20.689–24.066) | 1.49 | 7063.4 | 1.00 | 1141.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | resident | stock | 2113.708 (2112.662–2114.089) | 1.00 | 241.3 | 5310.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | resident | generated-owned | 2124.575 (2115.797–2171.704) | 1.01 | 240.1 | 5427.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | resident | native-view | 2920.376 (2873.397–2928.762) | 1.38 | 174.6 | 3913.25 | 3315.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | resident | native-eager | 4034.623 (4017.159–4051.528) | 1.91 | 126.4 | 4684.25 | 3291.0 | 0.0 / 0.0 | 0.0 | 0.0 |
