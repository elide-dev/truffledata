# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | 1570.9 | — | 1751.2 (1.11×) | 2413.7 (1.54×) | 2281.2 (1.45×) | 1794.4 (1.14×) | 2253.4 (1.43×) | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | 105.4 | 3084.4 (29.27×) | 48.5 (0.46×) | 210.8 (2.00×) | 211.2 (2.00×) | — | 281.9 (2.68×) | — | — | — | — |
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
| sparse | detach | 1500.1 | — | 1736.1 (1.16×) | 2273.3 (1.52×) | 2286.4 (1.52×) | 1820.5 (1.21×) | 2250.5 (1.50×) | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | 106.1 | 3099.2 (29.22×) | 48.6 (0.46×) | 310.5 (2.93×) | 310.4 (2.93×) | — | 385.4 (3.63×) | — | — | — | — |
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
| candidate | sparse | detach | stock | 1570.851 (1511.787–1755.870) | 1.00 | 95.4 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | detach | generated-owned | 1751.172 (1742.292–2481.967) | 1.11 | 85.6 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | detach | slab | 2413.665 (2293.887–2430.175) | 1.54 | 62.1 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | detach | slab-native | 2281.246 (2260.150–2306.639) | 1.45 | 65.7 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | detach | slab-projected | 1794.409 (1792.414–7565.914) | 1.14 | 83.5 | 689.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | detach | slab-lazy | 2253.436 (2209.963–3386.723) | 1.43 | 66.5 | 1084.50 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | full | peak | stock | 105.368 (105.122–106.432) | 1.00 | 1422.7 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | peak | stock-interop | 3084.381 (3034.798–3112.311) | 29.27 | 48.6 | 1874.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | peak | generated-owned | 48.481 (48.156–74.212) | 0.46 | 3092.1 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | peak | slab | 210.799 (209.175–222.441) | 2.00 | 711.1 | 17.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | peak | slab-native | 211.214 (209.022–211.804) | 2.00 | 709.7 | 17.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | peak | slab-lazy | 281.907 (281.776–282.690) | 2.68 | 531.8 | 17.00 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| baseline | sparse | detach | stock | 1500.112 (1485.335–1518.572) | 1.00 | 99.9 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | detach | generated-owned | 1736.128 (1722.169–1742.065) | 1.16 | 86.3 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | detach | slab | 2273.292 (2262.482–2318.911) | 1.52 | 65.9 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | detach | slab-native | 2286.414 (2236.769–2378.177) | 1.52 | 65.6 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | detach | slab-projected | 1820.507 (1812.073–1872.818) | 1.21 | 82.3 | 689.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| baseline | sparse | detach | slab-lazy | 2250.474 (2245.711–2458.063) | 1.50 | 66.6 | 1084.50 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| baseline | full | peak | stock | 106.057 (105.655–107.310) | 1.00 | 1413.5 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | peak | stock-interop | 3099.179 (3087.148–3774.969) | 29.22 | 48.4 | 1874.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | peak | generated-owned | 48.605 (48.600–48.743) | 0.46 | 3084.1 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | peak | slab | 310.534 (308.669–311.382) | 2.93 | 482.7 | 17.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | peak | slab-native | 310.430 (309.582–312.992) | 2.93 | 482.9 | 17.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | peak | slab-lazy | 385.438 (384.352–387.255) | 3.63 | 388.9 | 17.00 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
