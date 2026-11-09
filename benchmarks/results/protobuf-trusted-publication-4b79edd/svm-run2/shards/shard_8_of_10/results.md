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
| full | decode | 864.1 | — | 991.2 (1.15×) | 448.7 (0.52×) | 472.0 (0.55×) | — | 1436.0 (1.66×) | 947.0 (1.10×) | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | 12.7 | 160.0 (12.62×) | 11.1 (0.87×) | 15.6 (1.23×) | 15.9 (1.25×) | 29.7 (2.34×) | 35.5 (2.80×) | — | — | — | — |
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
| full | decode | 1356.7 | — | 1064.4 (0.78×) | 1056.6 (0.78×) | — | — | — | 965.1 (0.71×) | — | — | — |
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
| candidate | full | decode | stock | 864.072 (857.830–878.969) | 1.00 | 173.5 | 1629.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | generated-owned | 991.241 (970.116–1126.675) | 1.15 | 151.2 | 1607.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | slab | 448.720 (430.381–451.018) | 0.52 | 334.1 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | decode | slab-native | 471.957 (433.413–515.214) | 0.55 | 317.6 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | decode | slab-lazy | 1436.030 (1384.595–1541.296) | 1.66 | 104.4 | 972.25 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | full | decode | slab-session | 947.017 (907.569–1363.103) | 1.10 | 158.3 | 40.00 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | holdout-sparse | peak | stock | 12.681 (12.448–16.257) | 1.00 | 40219.0 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout-sparse | peak | stock-interop | 160.006 (159.930–172.325) | 12.62 | 3187.6 | 192.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout-sparse | peak | generated-owned | 11.065 (10.305–11.520) | 0.87 | 46092.9 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout-sparse | peak | slab | 15.610 (15.059–19.769) | 1.23 | 32673.5 | 1.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | peak | slab-native | 15.898 (15.513–17.167) | 1.25 | 32081.8 | 1.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | peak | slab-projected | 29.732 (28.505–51.778) | 2.34 | 17154.4 | 1.00 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | peak | slab-lazy | 35.511 (29.338–38.646) | 2.80 | 14362.7 | 1.00 | 0.0 | 528.5 / 528.5 | 0.0 | 510.0 |
| baseline | full | decode | stock | 1356.698 (1262.411–1458.706) | 1.00 | 110.5 | 1629.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | decode | generated-owned | 1064.360 (962.908–1295.166) | 0.78 | 140.8 | 1607.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | decode | slab | 1056.559 (998.184–1514.777) | 0.78 | 141.9 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | decode | slab-session | 965.112 (948.065–1506.173) | 0.71 | 155.3 | 40.00 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
