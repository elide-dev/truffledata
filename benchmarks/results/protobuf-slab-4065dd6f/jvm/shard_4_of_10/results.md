# Consumer comparison (jvm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | 708.5 | — | 924.1 (1.30×) | 361.1 (0.51×) | 838.9 (1.18×) | 326.6 (0.46×) | 720.7 (1.02×) | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | 24.6 | 35.7 (1.45×) | 17.4 (0.71×) | 131.6 (5.34×) | 130.9 (5.32×) | — | 141.8 (5.76×) | — | — | — | — |
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
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | 680.1 | — | 360.0 (0.53×) | — | — | — | — | — | — | 2360.4 (3.47×) | 2826.9 (4.16×) |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | 27.1 | — | 18.8 (0.69×) | — | — | — | — | — | — | 90.3 (3.33×) | 40.4 (1.49×) |
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
| candidate | sparse | detach | stock | 708.468 (603.898–727.851) | 1.00 | 211.6 | 1393.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | detach | generated-owned | 924.074 (544.495–924.977) | 1.30 | 162.2 | 1838.75 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | detach | slab | 361.102 (360.215–365.787) | 0.51 | 415.1 | 991.00 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | sparse | detach | slab-native | 838.929 (801.811–877.687) | 1.18 | 178.7 | 1185.50 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | sparse | detach | slab-projected | 326.626 (317.327–330.316) | 0.46 | 459.0 | 1185.50 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | detach | slab-lazy | 720.747 (711.993–728.479) | 1.02 | 208.0 | 944.25 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | full | peak | stock | 24.620 (24.389–27.559) | 1.00 | 6088.7 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | peak | stock-interop | 35.683 (35.566–35.950) | 1.45 | 4201.1 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | peak | generated-owned | 17.417 (17.190–19.214) | 0.71 | 8607.0 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | peak | slab | 131.591 (131.487–131.653) | 5.34 | 1139.2 | 33.50 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | peak | slab-native | 130.884 (130.776–132.012) | 5.32 | 1145.3 | 33.50 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | peak | slab-lazy | 141.784 (141.084–141.936) | 5.76 | 1057.3 | 1.50 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| baseline | sparse | detach | stock | 680.132 (650.689–705.735) | 1.00 | 220.4 | 1393.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | detach | generated-owned | 360.014 (294.655–959.473) | 0.53 | 416.4 | 1838.75 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | detach | native-view | 2360.359 (2321.595–2386.031) | 3.47 | 63.5 | 4494.25 | 1165.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | detach | native-eager | 2826.889 (2822.430–2866.394) | 4.16 | 53.0 | 5940.50 | 1141.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | peak | stock | 27.145 (19.745–31.281) | 1.00 | 5522.4 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | peak | generated-owned | 18.849 (18.806–21.191) | 0.69 | 7953.0 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | peak | native-view | 90.340 (79.104–154.820) | 3.33 | 1659.3 | 1.50 | 1165.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | peak | native-eager | 40.377 (40.025–40.492) | 1.49 | 3712.7 | 1.50 | 1141.5 | 0.0 / 0.0 | 0.0 | 0.0 |
