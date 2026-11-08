# Consumer comparison (jvm)

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
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | 266.7 | — | 365.7 (1.37×) | 624.8 (2.34×) | 1050.4 (3.94×) | — | 933.1 (3.50×) | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | 71307.6 | — | 175650.7 (2.46×) | 175314.8 (2.46×) | 251614.1 (3.53×) | — | 338573.5 (4.75×) | 212670.4 (2.98×) | — | — | — |
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
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | 800.6 | — | 366.6 (0.46×) | — | — | — | — | — | — | 1187.0 (1.48×) | 1445.3 (1.81×) |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | 183501.2 | — | 144887.8 (0.79×) | — | — | — | — | — | — | 312745.1 (1.70×) | 362485.3 (1.98×) |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | full | heap | stock | 266.690 (259.351–267.994) | 1.00 | 562.1 | 1393.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | heap | generated-owned | 365.716 (362.945–369.273) | 1.37 | 409.9 | 1838.75 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | heap | slab | 624.841 (621.481–638.274) | 2.34 | 239.9 | 1649.00 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | heap | slab-native | 1050.427 (1018.777–1060.449) | 3.94 | 142.7 | 2166.75 | 0.0 | 547.2 / 867.4 | 0.0 | 0.0 |
| candidate | full | heap | slab-lazy | 933.137 (930.077–2653.969) | 3.50 | 160.6 | 3145.00 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| candidate | large | resident | stock | 71307.604 (69034.211–284840.835) | 1.00 | 726.7 | 306084.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | resident | generated-owned | 175650.698 (142076.060–245326.404) | 2.46 | 295.0 | 533144.75 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | resident | slab | 175314.814 (175051.554–176100.148) | 2.46 | 295.6 | 460800.75 | 0.0 | 172771.8 / 172771.8 | 0.0 | 0.0 |
| candidate | large | resident | slab-native | 251614.111 (251055.767–252877.062) | 3.53 | 206.0 | 319156.00 | 0.0 | 172771.8 / 172771.8 | 0.0 | 0.0 |
| candidate | large | resident | slab-lazy | 338573.531 (330723.059–345057.162) | 4.75 | 153.1 | 971560.50 | 0.0 | 45619.8 / 281827.8 | 0.0 | 51822.3 |
| candidate | large | resident | slab-session | 212670.398 (211656.062–215391.967) | 2.98 | 243.7 | 109526.75 | 0.0 | 172771.8 / 0.0 | 207545.1 | 0.0 |
| baseline | full | heap | stock | 800.589 (311.651–824.237) | 1.00 | 187.2 | 1393.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | heap | generated-owned | 366.640 (365.788–376.466) | 0.46 | 408.9 | 1838.75 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | heap | native-view | 1187.030 (1168.294–1200.962) | 1.48 | 126.3 | 2252.75 | 1165.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | heap | native-eager | 1445.319 (1402.402–1538.147) | 1.81 | 103.7 | 2505.00 | 1141.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | resident | stock | 183501.243 (68985.975–195121.535) | 1.00 | 282.4 | 306068.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | resident | generated-owned | 144887.815 (142088.254–146133.162) | 0.79 | 357.7 | 533144.75 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | resident | native-view | 312745.125 (305397.156–328388.963) | 1.70 | 165.7 | 437040.25 | 409153.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | resident | native-eager | 362485.323 (358027.035–367605.257) | 1.98 | 143.0 | 579437.50 | 409129.5 | 0.0 / 0.0 | 0.0 | 0.0 |
