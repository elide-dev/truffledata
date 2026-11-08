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
| sparse | peak | 8.7 | 14.1 (1.61×) | 9.2 (1.05×) | 27.7 (3.17×) | 36.6 (4.19×) | 36.4 (4.16×) | 35.4 (4.06×) | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | 953.3 | — | 1393.1 (1.46×) | 1278.5 (1.34×) | 2436.8 (2.56×) | 1034.7 (1.09×) | 599.5 (0.63×) | 1573.4 (1.65×) | 1316.5 (1.38×) | — | — |
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
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | 8.4 | — | 8.6 (1.02×) | — | — | — | — | — | — | 22.0 (2.60×) | 16.0 (1.90×) |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
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
| candidate | sparse | peak | stock | 8.738 (8.246–9.049) | 1.00 | 17156.2 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | peak | stock-interop | 14.058 (13.863–14.899) | 1.61 | 10663.1 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | peak | generated-owned | 9.215 (8.345–10.635) | 1.05 | 16267.4 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | peak | slab | 27.715 (25.468–31.603) | 3.17 | 5408.9 | 1.50 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | sparse | peak | slab-native | 36.608 (28.683–36.693) | 4.19 | 4094.9 | 1.50 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | sparse | peak | slab-projected | 36.368 (27.502–38.644) | 4.16 | 4121.9 | 1.50 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | peak | slab-lazy | 35.438 (29.888–39.429) | 4.06 | 4230.0 | 1.50 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | holdout-sparse | resident | stock | 953.306 (953.187–956.527) | 1.00 | 535.0 | 4706.75 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | generated-owned | 1393.146 (1364.870–1396.465) | 1.46 | 366.1 | 6343.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab | 1278.460 (1272.510–1290.017) | 1.34 | 398.9 | 2729.00 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab-native | 2436.759 (2421.278–2444.930) | 2.56 | 209.3 | 2527.75 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab-projected | 1034.655 (779.116–2688.364) | 1.09 | 492.9 | 1185.50 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab-lazy | 599.491 (578.546–599.752) | 0.63 | 850.8 | 1980.25 | 0.0 | 528.5 / 528.5 | 0.0 | 510.0 |
| candidate | holdout-sparse | resident | slab-session | 1573.432 (1571.297–1626.114) | 1.65 | 324.2 | 138.75 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
| candidate | holdout-sparse | resident | slab-session-projected | 1316.457 (1297.162–1328.049) | 1.38 | 387.4 | 138.75 | 0.0 | 528.5 / 0.0 | 2296.1 | 510.0 |
| baseline | sparse | peak | stock | 8.436 (8.209–9.248) | 1.00 | 17770.0 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | peak | generated-owned | 8.587 (8.218–9.720) | 1.02 | 17457.7 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | peak | native-view | 21.969 (20.399–29.921) | 2.60 | 6823.4 | 1.50 | 1165.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | peak | native-eager | 16.005 (15.380–16.902) | 1.90 | 9366.2 | 1.50 | 1141.5 | 0.0 / 0.0 | 0.0 | 0.0 |
