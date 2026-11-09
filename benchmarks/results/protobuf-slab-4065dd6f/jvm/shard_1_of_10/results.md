# Consumer comparison (jvm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | 394.1 | — | 565.2 (1.43×) | 661.0 (1.68×) | 4896.5 (12.42×) | 1440.5 (3.66×) | 1213.2 (3.08×) | 941.3 (2.39×) | 794.9 (2.02×) | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | 436.4 | — | 596.5 (1.37×) | 1510.5 (3.46×) | 6194.5 (14.20×) | — | 5271.7 (12.08×) | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | 11175.2 | 19803.9 (1.77×) | 9471.6 (0.85×) | 38575.2 (3.45×) | 38421.1 (3.44×) | — | 55626.8 (4.98×) | — | — | — | — |

## baseline: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | 392.0 | — | 554.7 (1.41×) | — | — | — | — | — | — | 1034.2 (2.64×) | 1709.8 (4.36×) |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | 430.0 | — | 600.0 (1.40×) | — | — | — | — | — | — | 4377.6 (10.18×) | 6884.3 (16.01×) |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | 11087.5 | — | 10288.0 (0.93×) | — | — | — | — | — | — | 54394.8 (4.91×) | 23307.8 (2.10×) |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | sparse | decode | stock | 394.099 (390.347–395.171) | 1.00 | 380.4 | 1391.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | decode | generated-owned | 565.235 (550.152–590.515) | 1.43 | 265.2 | 1837.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | decode | slab | 661.030 (660.542–661.682) | 1.68 | 226.8 | 869.25 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | sparse | decode | slab-native | 4896.508 (4178.156–5267.791) | 12.42 | 30.6 | 1087.75 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | sparse | decode | slab-projected | 1440.533 (935.960–2018.537) | 3.66 | 104.1 | 1094.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | decode | slab-lazy | 1213.160 (1103.532–1214.451) | 3.08 | 123.6 | 836.75 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | sparse | decode | slab-session | 941.302 (941.028–1004.672) | 2.39 | 159.3 | 48.00 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | sparse | decode | slab-session-projected | 794.924 (785.703–809.162) | 2.02 | 188.6 | 48.00 | 0.0 | 289.2 / 0.0 | 855.6 | 149.9 |
| candidate | full | detach | stock | 436.378 (421.735–441.328) | 1.00 | 343.5 | 1393.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | detach | generated-owned | 596.487 (591.473–651.920) | 1.37 | 251.3 | 1838.75 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | detach | slab | 1510.510 (1085.634–2898.554) | 3.46 | 99.2 | 1649.00 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | detach | slab-native | 6194.524 (5359.979–6507.491) | 14.20 | 24.2 | 1843.50 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | detach | slab-lazy | 5271.652 (5038.703–5415.363) | 12.08 | 28.4 | 3145.00 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| candidate | large | peak | stock | 11175.233 (11039.375–11319.796) | 1.00 | 4637.2 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | peak | stock-interop | 19803.872 (19270.326–58264.133) | 1.77 | 2616.8 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | peak | generated-owned | 9471.587 (9194.864–10907.991) | 0.85 | 5471.3 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | peak | slab | 38575.177 (38540.563–39020.757) | 3.45 | 1343.4 | 33.50 | 0.0 | 172771.8 / 172771.8 | 0.0 | 0.0 |
| candidate | large | peak | slab-native | 38421.131 (38375.500–38485.256) | 3.44 | 1348.8 | 33.50 | 0.0 | 172771.8 / 172771.8 | 0.0 | 0.0 |
| candidate | large | peak | slab-lazy | 55626.820 (55605.409–55870.323) | 4.98 | 931.6 | 33.50 | 0.0 | 45619.8 / 281827.8 | 0.0 | 51822.3 |
| baseline | sparse | decode | stock | 392.014 (389.082–396.646) | 1.00 | 382.4 | 1391.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | decode | generated-owned | 554.663 (550.946–557.215) | 1.41 | 270.3 | 1837.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | decode | native-view | 1034.164 (1027.733–1038.737) | 2.64 | 145.0 | 336.00 | 1165.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | decode | native-eager | 1709.811 (1693.239–1728.770) | 4.36 | 87.7 | 1782.25 | 1141.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | detach | stock | 429.964 (426.784–431.364) | 1.00 | 348.6 | 1393.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | detach | generated-owned | 599.972 (590.707–604.492) | 1.40 | 249.9 | 1838.75 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | detach | native-view | 4377.633 (4345.336–4967.219) | 10.18 | 34.2 | 4494.25 | 1165.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | detach | native-eager | 6884.292 (5294.172–8635.849) | 16.01 | 21.8 | 5940.50 | 1141.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | peak | stock | 11087.533 (10965.993–11135.498) | 1.00 | 4673.9 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | peak | generated-owned | 10288.025 (9658.713–11582.293) | 0.93 | 5037.1 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | peak | native-view | 54394.833 (54292.430–58281.387) | 4.91 | 952.7 | 1.50 | 409153.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | peak | native-eager | 23307.788 (23206.916–23474.051) | 2.10 | 2223.4 | 1.50 | 409129.5 | 0.0 / 0.0 | 0.0 | 0.0 |
