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
| sparse | interpret | 10.9 | 218.5 (20.12×) | 45.2 (4.17×) | 40.9 (3.77×) | 36.6 (3.37×) | 58.2 (5.36×) | 56.6 (5.21×) | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | 151.8 | 3537.3 (23.30×) | 76.4 (0.50×) | 138.3 (0.91×) | 151.5 (1.00×) | — | 257.9 (1.70×) | — | — | — | — |
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
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | 13.0 | — | 35.9 (2.76×) | 40.7 (3.12×) | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | 151.7 | — | 64.9 (0.43×) | 149.0 (0.98×) | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | sparse | interpret | stock | 10.862 (10.329–11.488) | 1.00 | 13801.2 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | interpret | stock-interop | 218.527 (181.370–337.970) | 20.12 | 686.0 | 193.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | interpret | generated-owned | 45.243 (37.717–53.266) | 4.17 | 3313.4 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | interpret | slab | 40.933 (37.211–42.250) | 3.77 | 3662.2 | 2.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | interpret | slab-native | 36.571 (34.285–43.898) | 3.37 | 4099.1 | 2.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | interpret | slab-projected | 58.239 (55.962–61.216) | 5.36 | 2574.0 | 2.00 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | interpret | slab-lazy | 56.599 (56.209–139.724) | 5.21 | 2648.6 | 2.00 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | holdout | peak | stock | 151.831 (143.939–159.826) | 1.00 | 3359.2 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | peak | stock-interop | 3537.281 (3127.512–3694.925) | 23.30 | 144.2 | 5319.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | peak | generated-owned | 76.353 (64.926–85.432) | 0.50 | 6679.9 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | peak | slab | 138.300 (138.232–155.662) | 0.91 | 3687.9 | 1.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout | peak | slab-native | 151.543 (135.889–187.397) | 1.00 | 3365.6 | 1.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout | peak | slab-lazy | 257.855 (183.828–342.158) | 1.70 | 1978.0 | 1.00 | 0.0 | 528.5 / 2471.0 | 0.0 | 510.0 |
| baseline | sparse | interpret | stock | 13.020 (11.344–13.775) | 1.00 | 11513.2 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | interpret | generated-owned | 35.921 (34.135–39.175) | 2.76 | 4173.2 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | interpret | slab | 40.671 (35.221–58.504) | 3.12 | 3685.8 | 2.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | holdout | peak | stock | 151.682 (149.003–220.089) | 1.00 | 3362.5 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | peak | generated-owned | 64.926 (62.216–69.708) | 0.43 | 7855.6 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | peak | slab | 148.968 (139.858–162.894) | 0.98 | 3423.8 | 1.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
