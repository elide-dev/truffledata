# Consumer comparison (jvm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | 292.7 | — | 355.0 (1.21×) | 447.6 (1.53×) | 944.3 (3.23×) | 398.3 (1.36×) | 248.2 (0.85×) | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | 32.0 | 714.6 (22.34×) | 61.7 (1.93×) | 208.5 (6.52×) | 204.2 (6.38×) | — | 227.7 (7.12×) | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
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
| sparse | heap | 243.7 | — | 353.2 (1.45×) | — | — | — | — | — | — | 920.3 (3.78×) | 1367.4 (5.61×) |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | 30.9 | — | 43.7 (1.41×) | — | — | — | — | — | — | 179.8 (5.81×) | 110.8 (3.58×) |
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
| candidate | sparse | heap | stock | 292.674 (255.744–298.091) | 1.00 | 512.2 | 1393.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | heap | generated-owned | 354.993 (353.827–12192.441) | 1.21 | 422.3 | 1838.75 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | heap | slab | 447.636 (445.527–490.076) | 1.53 | 334.9 | 991.00 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | sparse | heap | slab-native | 944.330 (897.804–1337.823) | 3.23 | 158.7 | 1508.75 | 0.0 | 547.2 / 867.4 | 0.0 | 0.0 |
| candidate | sparse | heap | slab-projected | 398.268 (394.321–939.103) | 1.36 | 376.4 | 1185.50 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | heap | slab-lazy | 248.201 (247.337–251.935) | 0.85 | 604.0 | 944.25 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | full | interpret | stock | 31.994 (24.080–32.219) | 1.00 | 4685.5 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | interpret | stock-interop | 714.606 (703.301–717.660) | 22.34 | 209.8 | 2698.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | interpret | generated-owned | 61.721 (57.211–63.122) | 1.93 | 2428.8 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | interpret | slab | 208.514 (207.055–223.601) | 6.52 | 718.9 | 33.50 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | interpret | slab-native | 204.169 (196.817–209.876) | 6.38 | 734.2 | 33.50 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | interpret | slab-lazy | 227.747 (226.495–1095.559) | 7.12 | 658.2 | 33.50 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| baseline | sparse | heap | stock | 243.688 (243.047–314.272) | 1.00 | 615.2 | 1393.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | heap | generated-owned | 353.194 (351.835–405.585) | 1.45 | 424.4 | 1838.75 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | heap | native-view | 920.283 (915.816–925.443) | 3.78 | 162.9 | 1345.25 | 1165.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | heap | native-eager | 1367.370 (1348.683–1377.187) | 5.61 | 109.6 | 2290.00 | 1141.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | interpret | stock | 30.940 (25.436–31.698) | 1.00 | 4845.0 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | interpret | generated-owned | 43.727 (42.265–44.202) | 1.41 | 3428.2 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | interpret | native-view | 179.816 (179.462–197.907) | 5.81 | 833.7 | 1.50 | 1165.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | interpret | native-eager | 110.771 (110.402–112.891) | 3.58 | 1353.3 | 1.50 | 1141.5 | 0.0 / 0.0 | 0.0 | 0.0 |
