# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | 1525.4 | — | 1767.6 (1.16×) | 1033.0 (0.68×) | 1044.3 (0.68×) | 2911.5 (1.91×) | 2358.2 (1.55×) | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | 106.1 | 3096.9 (29.19×) | 50.5 (0.48×) | 104.0 (0.98×) | 102.8 (0.97×) | — | 143.0 (1.35×) | — | — | — | — |
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
| sparse | detach | 1532.8 | — | 1779.8 (1.16×) | 2101.3 (1.37×) | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | 106.5 | — | 50.4 (0.47×) | 102.7 (0.96×) | — | — | — | — | — | — | — |
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
| candidate | sparse | detach | stock | 1525.367 (1521.987–1538.971) | 1.00 | 98.3 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | detach | generated-owned | 1767.582 (1761.320–1770.058) | 1.16 | 84.8 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | detach | slab | 1033.025 (1029.752–1384.899) | 0.68 | 145.1 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | detach | slab-native | 1044.310 (1033.054–1067.414) | 0.68 | 143.5 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | detach | slab-projected | 2911.463 (1866.900–5638.969) | 1.91 | 51.5 | 689.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | detach | slab-lazy | 2358.201 (2343.529–2579.686) | 1.55 | 63.6 | 1084.50 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | full | peak | stock | 106.097 (106.015–106.795) | 1.00 | 1412.9 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | peak | stock-interop | 3096.942 (3065.877–3499.479) | 29.19 | 48.4 | 1874.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | peak | generated-owned | 50.508 (48.881–57.023) | 0.48 | 2968.0 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | peak | slab | 103.957 (102.719–113.252) | 0.98 | 1442.0 | 1.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | peak | slab-native | 102.769 (102.418–103.657) | 0.97 | 1458.7 | 1.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | peak | slab-lazy | 142.960 (141.427–147.841) | 1.35 | 1048.6 | 1.00 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| baseline | sparse | detach | stock | 1532.782 (1526.524–1553.993) | 1.00 | 97.8 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | detach | generated-owned | 1779.807 (1765.982–2702.882) | 1.16 | 84.2 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | detach | slab | 2101.293 (2069.678–2929.717) | 1.37 | 71.3 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | peak | stock | 106.501 (105.910–106.876) | 1.00 | 1407.6 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | peak | generated-owned | 50.396 (49.244–69.865) | 0.47 | 2974.5 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | peak | slab | 102.739 (102.624–105.736) | 0.96 | 1459.1 | 1.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
