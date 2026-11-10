# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | 22.2 | — | 32.5 (1.46×) | 147.7 (6.65×) | 61.6 (2.77×) | 84.0 (3.78×) | 252.3 (11.36×) | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | 2811.0 | — | 3069.6 (1.09×) | 1870.5 (0.67×) | 1821.6 (0.65×) | — | 7475.9 (2.66×) | 2858.9 (1.02×) | — | — | — |
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
| sparse | first | 31.7 | — | 32.2 (1.02×) | 62.6 (1.97×) | 65.9 (2.08×) | 89.4 (2.81×) | 76.2 (2.40×) | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | 2697.1 | — | 2943.3 (1.09×) | 1669.1 (0.62×) | 1680.1 (0.62×) | — | 7526.0 (2.79×) | 2874.8 (1.07×) | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | sparse | first | stock | 22.221 (19.728–23.933) | 1.00 | 6746.0 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | first | generated-owned | 32.519 (20.609–34.733) | 1.46 | 4609.8 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | first | slab | 147.686 (69.655–1591.537) | 6.65 | 1015.0 | 126.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | first | slab-native | 61.613 (58.347–93.802) | 2.77 | 2433.0 | 126.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | first | slab-projected | 83.972 (75.078–100.212) | 3.78 | 1785.2 | 95.50 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | first | slab-lazy | 252.328 (74.767–2255.919) | 11.36 | 594.1 | 112.25 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | holdout | resident | stock | 2811.001 (2780.171–3065.543) | 1.00 | 181.4 | 5310.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | resident | generated-owned | 3069.600 (2954.737–3177.315) | 1.09 | 166.2 | 5427.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | resident | slab | 1870.452 (1612.172–2581.670) | 0.67 | 272.7 | 3391.25 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout | resident | slab-native | 1821.563 (1658.915–3316.593) | 0.65 | 280.0 | 3391.25 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout | resident | slab-lazy | 7475.855 (7240.800–7493.767) | 2.66 | 68.2 | 7703.75 | 0.0 | 528.5 / 2471.0 | 0.0 | 510.0 |
| candidate | holdout | resident | slab-session | 2858.909 (2841.749–5092.114) | 1.02 | 178.4 | 1197.00 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
| baseline | sparse | first | stock | 31.746 (26.445–45.638) | 1.00 | 4722.0 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | first | generated-owned | 32.237 (19.200–33.396) | 1.02 | 4650.1 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | first | slab | 62.629 (56.890–69.997) | 1.97 | 2393.5 | 126.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | first | slab-native | 65.906 (57.324–113.226) | 2.08 | 2274.5 | 126.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | first | slab-projected | 89.356 (75.359–132.726) | 2.81 | 1677.6 | 95.50 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| baseline | sparse | first | slab-lazy | 76.207 (74.914–78.788) | 2.40 | 1967.1 | 112.25 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| baseline | holdout | resident | stock | 2697.084 (2611.448–2714.308) | 1.00 | 189.1 | 5310.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | resident | generated-owned | 2943.306 (2861.768–3892.611) | 1.09 | 173.3 | 5427.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | resident | slab | 1669.074 (1628.199–1963.160) | 0.62 | 305.6 | 3391.25 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| baseline | holdout | resident | slab-native | 1680.081 (1656.395–1757.908) | 0.62 | 303.6 | 3391.25 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| baseline | holdout | resident | slab-lazy | 7526.010 (7404.950–9050.013) | 2.79 | 67.8 | 7703.75 | 0.0 | 528.5 / 2471.0 | 0.0 | 510.0 |
| baseline | holdout | resident | slab-session | 2874.767 (2718.758–2947.235) | 1.07 | 177.4 | 1197.00 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
