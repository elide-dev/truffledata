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
| full | decode | 842.6 | — | 846.3 (1.00×) | 437.4 (0.52×) | 417.0 (0.49×) | — | 1319.3 (1.57×) | 832.9 (0.99×) | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | 12.5 | 152.7 (12.21×) | 10.3 (0.82×) | 14.9 (1.19×) | 15.4 (1.24×) | 29.6 (2.37×) | 26.6 (2.12×) | — | — | — | — |
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
| full | decode | 796.6 | — | 946.5 (1.19×) | 448.9 (0.56×) | 445.5 (0.56×) | — | 1373.0 (1.72×) | 836.6 (1.05×) | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | 12.6 | 140.9 (11.19×) | 10.0 (0.80×) | 14.6 (1.16×) | 14.1 (1.12×) | 26.8 (2.13×) | 27.1 (2.15×) | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | full | decode | stock | 842.607 (836.702–844.578) | 1.00 | 177.9 | 1629.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | generated-owned | 846.295 (846.085–920.414) | 1.00 | 177.1 | 1607.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | slab | 437.445 (433.317–534.045) | 0.52 | 342.7 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | decode | slab-native | 417.025 (404.556–431.284) | 0.49 | 359.5 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | decode | slab-lazy | 1319.341 (1285.846–1746.806) | 1.57 | 113.6 | 972.25 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | full | decode | slab-session | 832.914 (810.721–869.822) | 0.99 | 180.0 | 40.00 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | holdout-sparse | peak | stock | 12.505 (12.182–12.693) | 1.00 | 40785.8 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout-sparse | peak | stock-interop | 152.749 (150.225–161.460) | 12.21 | 3339.0 | 192.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout-sparse | peak | generated-owned | 10.294 (9.972–11.501) | 0.82 | 49548.7 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout-sparse | peak | slab | 14.895 (14.802–15.767) | 1.19 | 34242.5 | 1.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | peak | slab-native | 15.445 (13.907–15.886) | 1.24 | 33023.5 | 1.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | peak | slab-projected | 29.586 (27.671–30.889) | 2.37 | 17239.0 | 1.00 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | peak | slab-lazy | 26.567 (26.479–26.941) | 2.12 | 19197.9 | 1.00 | 0.0 | 528.5 / 528.5 | 0.0 | 510.0 |
| baseline | full | decode | stock | 796.640 (789.104–808.012) | 1.00 | 188.2 | 1629.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | decode | generated-owned | 946.489 (856.683–1026.501) | 1.19 | 158.4 | 1607.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | decode | slab | 448.935 (413.061–462.096) | 0.56 | 333.9 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | decode | slab-native | 445.486 (428.283–522.240) | 0.56 | 336.5 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | decode | slab-lazy | 1372.998 (1296.272–2623.216) | 1.72 | 109.2 | 972.25 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| baseline | full | decode | slab-session | 836.634 (829.939–851.601) | 1.05 | 179.2 | 40.00 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| baseline | holdout-sparse | peak | stock | 12.594 (11.759–13.205) | 1.00 | 40497.0 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout-sparse | peak | stock-interop | 140.908 (139.550–140.952) | 11.19 | 3619.6 | 192.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout-sparse | peak | generated-owned | 10.019 (9.912–10.115) | 0.80 | 50904.9 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout-sparse | peak | slab | 14.580 (14.498–18.104) | 1.16 | 34980.8 | 1.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| baseline | holdout-sparse | peak | slab-native | 14.143 (13.868–15.163) | 1.12 | 36062.3 | 1.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| baseline | holdout-sparse | peak | slab-projected | 26.838 (26.816–27.142) | 2.13 | 19004.3 | 1.00 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| baseline | holdout-sparse | peak | slab-lazy | 27.124 (26.730–43.162) | 2.15 | 18803.5 | 1.00 | 0.0 | 528.5 / 528.5 | 0.0 | 510.0 |
