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
| full | decode | 798.0 | — | 841.2 (1.05×) | 1534.1 (1.92×) | 1548.5 (1.94×) | — | 1193.3 (1.50×) | 839.2 (1.05×) | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | 11.9 | 140.9 (11.82×) | 9.7 (0.82×) | 29.7 (2.49×) | 29.6 (2.48×) | 33.8 (2.83×) | 33.9 (2.85×) | — | — | — | — |
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
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | 774.8 | — | 814.3 (1.05×) | — | — | — | — | — | — | 691.7 (0.89×) | 1460.6 (1.89×) |
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
| candidate | full | decode | stock | 798.026 (795.006–803.436) | 1.00 | 187.8 | 1629.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | generated-owned | 841.196 (833.420–846.686) | 1.05 | 178.2 | 1607.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | slab | 1534.142 (1527.546–1535.156) | 1.92 | 97.7 | 991.75 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | decode | slab-native | 1548.491 (1535.665–2059.231) | 1.94 | 96.8 | 991.75 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | decode | slab-lazy | 1193.287 (1180.902–1245.106) | 1.50 | 125.6 | 1004.25 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | full | decode | slab-session | 839.227 (793.561–5794.772) | 1.05 | 178.6 | 40.00 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | holdout-sparse | peak | stock | 11.926 (11.725–12.097) | 1.00 | 42766.3 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout-sparse | peak | stock-interop | 140.928 (138.271–141.326) | 11.82 | 3619.1 | 192.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout-sparse | peak | generated-owned | 9.742 (9.731–9.758) | 0.82 | 52353.9 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout-sparse | peak | slab | 29.684 (29.668–30.007) | 2.49 | 17182.0 | 1.00 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout-sparse | peak | slab-native | 29.579 (29.569–86.708) | 2.48 | 17243.1 | 1.00 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout-sparse | peak | slab-projected | 33.788 (33.767–34.067) | 2.83 | 15094.9 | 1.00 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | peak | slab-lazy | 33.930 (33.917–33.974) | 2.85 | 15031.7 | 1.00 | 0.0 | 528.5 / 528.5 | 0.0 | 510.0 |
| baseline | full | decode | stock | 774.765 (772.306–782.978) | 1.00 | 193.5 | 1629.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | decode | generated-owned | 814.310 (804.462–821.999) | 1.05 | 184.1 | 1607.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | decode | native-view | 691.721 (686.474–697.749) | 0.89 | 216.7 | 144.00 | 1165.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | decode | native-eager | 1460.590 (1447.305–1484.849) | 1.89 | 102.6 | 1214.25 | 1141.5 | 0.0 / 0.0 | 0.0 | 0.0 |
