# Consumer comparison (jvm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | 11.0 | — | 10.9 (0.99×) | 42.5 (3.87×) | 46.0 (4.19×) | 42.9 (3.90×) | 39.2 (3.57×) | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | 1002.7 | — | 1427.1 (1.42×) | 1699.1 (1.69×) | 2771.1 (2.76×) | — | 2660.2 (2.65×) | 2192.2 (2.19×) | — | — | — |
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
| sparse | first | 10.0 | — | 10.6 (1.06×) | — | — | — | — | — | — | 143.0 (14.34×) | 29.6 (2.97×) |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | 997.3 | — | 1455.3 (1.46×) | — | — | — | — | — | — | 3226.3 (3.23×) | 3834.7 (3.85×) |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | sparse | first | stock | 10.982 (9.751–11.855) | 1.00 | 13650.2 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | first | generated-owned | 10.910 (10.771–11.933) | 0.99 | 13740.0 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | first | slab | 42.504 (39.476–44.424) | 3.87 | 3526.9 | 121.75 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | sparse | first | slab-native | 46.020 (43.985–47.247) | 4.19 | 3257.4 | 121.75 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | sparse | first | slab-projected | 42.870 (41.988–44.000) | 3.90 | 3496.7 | 90.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | first | slab-lazy | 39.151 (39.014–39.852) | 3.57 | 3828.9 | 107.50 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | holdout | resident | stock | 1002.664 (997.664–1014.281) | 1.00 | 508.7 | 4706.75 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | resident | generated-owned | 1427.079 (1409.486–1438.157) | 1.42 | 357.4 | 6343.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | resident | slab | 1699.125 (1676.133–1743.988) | 1.69 | 300.2 | 3902.00 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout | resident | slab-native | 2771.070 (2747.798–2859.892) | 2.76 | 184.1 | 3700.75 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout | resident | slab-lazy | 2660.231 (2628.318–2722.026) | 2.65 | 191.7 | 8401.50 | 0.0 | 528.5 / 2471.0 | 0.0 | 510.0 |
| candidate | holdout | resident | slab-session | 2192.156 (2190.031–2255.377) | 2.19 | 232.7 | 1303.25 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
| baseline | sparse | first | stock | 9.974 (9.294–10.396) | 1.00 | 15030.3 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | first | generated-owned | 10.564 (9.920–10.637) | 1.06 | 14190.1 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | first | native-view | 143.020 (142.620–143.022) | 14.34 | 1048.1 | 681.25 | 1165.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | first | native-eager | 29.610 (28.426–30.884) | 2.97 | 5062.7 | 1.50 | 1141.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | resident | stock | 997.330 (980.579–1002.419) | 1.00 | 511.4 | 4706.75 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | resident | generated-owned | 1455.290 (1416.140–1476.635) | 1.46 | 350.5 | 6343.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | resident | native-view | 3226.341 (3221.799–3233.563) | 3.23 | 158.1 | 5893.00 | 3315.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | resident | native-eager | 3834.737 (3826.659–3847.519) | 3.85 | 133.0 | 6624.00 | 3291.0 | 0.0 / 0.0 | 0.0 | 0.0 |
