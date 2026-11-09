# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | 1094.2 | — | 909.5 (0.83×) | 498.3 (0.46×) | 524.3 (0.48×) | 1085.7 (0.99×) | 1376.4 (1.26×) | 875.7 (0.80×) | 656.5 (0.60×) | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | 968.7 | — | 965.0 (1.00×) | 920.9 (0.95×) | 764.6 (0.79×) | — | 3173.5 (3.28×) | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | 19740.9 | 482592.1 (24.45×) | 8952.6 (0.45×) | 27166.3 (1.38×) | 18656.4 (0.95×) | — | 32124.2 (1.63×) | — | — | — | — |

## baseline: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | 923.5 | — | 1676.1 (1.81×) | 1152.5 (1.25×) | — | — | — | 962.3 (1.04×) | — | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | 921.5 | — | 1224.1 (1.33×) | 1525.5 (1.66×) | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | 24360.2 | — | 7506.3 (0.31×) | 16568.4 (0.68×) | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | sparse | decode | stock | 1094.174 (872.067–1251.254) | 1.00 | 137.0 | 1629.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | decode | generated-owned | 909.495 (905.333–2911.407) | 0.83 | 164.8 | 1607.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | decode | slab | 498.334 (479.120–768.509) | 0.46 | 300.8 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | decode | slab-native | 524.263 (496.701–531.089) | 0.48 | 285.9 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | decode | slab-projected | 1085.658 (1061.341–1180.043) | 0.99 | 138.1 | 594.25 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | decode | slab-lazy | 1376.417 (1316.552–1624.245) | 1.26 | 108.9 | 972.25 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | sparse | decode | slab-session | 875.737 (872.074–1331.132) | 0.80 | 171.2 | 40.00 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | sparse | decode | slab-session-projected | 656.549 (638.920–669.397) | 0.60 | 228.3 | 40.00 | 0.0 | 289.2 / 0.0 | 855.6 | 149.9 |
| candidate | full | detach | stock | 968.665 (882.619–988.329) | 1.00 | 154.8 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | detach | generated-owned | 964.964 (909.108–1074.526) | 1.00 | 155.3 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | detach | slab | 920.908 (796.090–8354.301) | 0.95 | 162.8 | 1440.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | detach | slab-native | 764.574 (719.941–4464.648) | 0.79 | 196.1 | 1440.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | detach | slab-lazy | 3173.478 (3067.118–3548.534) | 3.28 | 47.2 | 3047.25 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| candidate | large | peak | stock | 19740.902 (17190.965–21102.952) | 1.00 | 2625.1 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | peak | stock-interop | 482592.109 (478178.516–669086.979) | 24.45 | 107.4 | 666098.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | peak | generated-owned | 8952.577 (7328.030–11740.536) | 0.45 | 5788.5 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | peak | slab | 27166.336 (21721.905–36405.087) | 1.38 | 1907.6 | 1.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| candidate | large | peak | slab-native | 18656.406 (16548.743–20423.385) | 0.95 | 2777.7 | 1.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| candidate | large | peak | slab-lazy | 32124.171 (29909.269–32882.396) | 1.63 | 1613.2 | 1.00 | 0.0 | 45619.8 / 281827.8 | 0.0 | 51822.3 |
| baseline | sparse | decode | stock | 923.536 (811.556–1210.909) | 1.00 | 162.3 | 1629.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | decode | generated-owned | 1676.075 (905.621–7802.403) | 1.81 | 89.4 | 1607.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | decode | slab | 1152.469 (942.235–2021.191) | 1.25 | 130.1 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | decode | slab-session | 962.297 (851.705–1610.477) | 1.04 | 155.8 | 40.00 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| baseline | full | detach | stock | 921.475 (873.295–966.748) | 1.00 | 162.7 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | detach | generated-owned | 1224.077 (986.281–1465.759) | 1.33 | 122.5 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | detach | slab | 1525.522 (1356.322–2038.791) | 1.66 | 98.3 | 1440.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | large | peak | stock | 24360.163 (21323.805–34503.390) | 1.00 | 2127.3 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | peak | generated-owned | 7506.273 (6388.346–13066.281) | 0.31 | 6903.9 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | peak | slab | 16568.431 (16488.164–17668.617) | 0.68 | 3127.8 | 1.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
