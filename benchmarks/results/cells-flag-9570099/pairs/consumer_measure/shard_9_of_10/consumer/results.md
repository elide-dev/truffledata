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
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | 910.0 | — | 957.2 (1.05×) | 755.0 (0.83×) | 745.3 (0.82×) | — | 2870.7 (3.15×) | 1162.9 (1.28×) | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | 232879.7 | — | 269006.9 (1.16×) | 103394.0 (0.44×) | 98662.7 (0.42×) | — | 165272.9 (0.71×) | 222322.6 (0.95×) | — | — | — |
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
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | 905.6 | — | 905.1 (1.00×) | 798.5 (0.88×) | 741.7 (0.82×) | — | 2887.8 (3.19×) | 1130.8 (1.25×) | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | 231855.3 | — | 291916.8 (1.26×) | 101435.0 (0.44×) | 102533.9 (0.44×) | — | 169390.6 (0.73×) | 219661.6 (0.95×) | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | full | resident | stock | 909.978 (908.149–976.992) | 1.00 | 164.7 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | resident | generated-owned | 957.196 (899.024–957.553) | 1.05 | 156.6 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | resident | slab | 755.010 (705.583–33300.592) | 0.83 | 198.5 | 1440.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | resident | slab-native | 745.318 (711.062–886.590) | 0.82 | 201.1 | 1440.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | resident | slab-lazy | 2870.670 (2855.044–3133.042) | 3.15 | 52.2 | 3047.25 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| candidate | full | resident | slab-session | 1162.945 (1127.652–1193.775) | 1.28 | 128.9 | 718.25 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | large | decode | stock | 232879.670 (226971.492–232960.107) | 1.00 | 222.5 | 433008.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | decode | generated-owned | 269006.943 (268700.042–289451.531) | 1.16 | 192.6 | 460179.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | decode | slab | 103394.045 (98546.008–104394.731) | 0.44 | 501.2 | 189880.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| candidate | large | decode | slab-native | 98662.723 (97948.953–108359.727) | 0.42 | 525.2 | 189880.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| candidate | large | decode | slab-lazy | 165272.869 (164358.222–170842.391) | 0.71 | 313.6 | 116107.75 | 0.0 | 45619.8 / 45619.8 | 0.0 | 51822.3 |
| candidate | large | decode | slab-session | 222322.613 (215764.605–293105.240) | 0.95 | 233.1 | 40.00 | 0.0 | 172771.8 / 0.0 | 207545.1 | 0.0 |
| baseline | full | resident | stock | 905.563 (844.859–908.107) | 1.00 | 165.5 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | resident | generated-owned | 905.146 (889.848–955.935) | 1.00 | 165.6 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | resident | slab | 798.518 (707.830–807.977) | 0.88 | 187.7 | 1440.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | resident | slab-native | 741.746 (709.975–744.374) | 0.82 | 202.1 | 1440.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | resident | slab-lazy | 2887.846 (2826.017–3033.252) | 3.19 | 51.9 | 3047.25 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| baseline | full | resident | slab-session | 1130.797 (1121.529–1181.745) | 1.25 | 132.6 | 718.25 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| baseline | large | decode | stock | 231855.273 (226167.789–235511.960) | 1.00 | 223.5 | 433008.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | decode | generated-owned | 291916.786 (276562.281–296480.885) | 1.26 | 177.5 | 460179.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | decode | slab | 101435.010 (97164.263–123103.904) | 0.44 | 510.9 | 189880.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| baseline | large | decode | slab-native | 102533.947 (98510.926–107256.198) | 0.44 | 505.4 | 189880.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| baseline | large | decode | slab-lazy | 169390.616 (164811.897–212150.242) | 0.73 | 305.9 | 116107.75 | 0.0 | 45619.8 / 45619.8 | 0.0 | 51822.3 |
| baseline | large | decode | slab-session | 219661.613 (211750.156–222032.605) | 0.95 | 235.9 | 40.00 | 0.0 | 172771.8 / 0.0 | 207545.1 | 0.0 |
