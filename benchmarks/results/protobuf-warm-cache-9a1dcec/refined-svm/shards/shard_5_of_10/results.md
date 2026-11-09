# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | 15.2 | — | 13.1 (0.87×) | 48.3 (3.18×) | 49.4 (3.26×) | 56.1 (3.70×) | 57.9 (3.82×) | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | 2146.9 | — | 2215.5 (1.03×) | 3210.0 (1.50×) | 3315.1 (1.54×) | — | 5659.0 (2.64×) | 2242.1 (1.04×) | — | — | — |
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
| sparse | first | 14.9 | — | 12.9 (0.86×) | 53.1 (3.55×) | 47.3 (3.16×) | 54.8 (3.66×) | 56.1 (3.75×) | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | 2108.9 | — | 2235.2 (1.06×) | 3256.5 (1.54×) | 3245.9 (1.54×) | — | 5663.2 (2.69×) | 2309.9 (1.10×) | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | sparse | first | stock | 15.153 (15.119–17.960) | 1.00 | 9892.9 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | first | generated-owned | 13.120 (13.040–13.478) | 0.87 | 11425.7 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | first | slab | 48.254 (47.934–1293.173) | 3.18 | 3106.6 | 126.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | first | slab-native | 49.376 (48.906–57.352) | 3.26 | 3036.0 | 126.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | first | slab-projected | 56.071 (54.724–59.215) | 3.70 | 2673.5 | 95.50 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | first | slab-lazy | 57.938 (57.900–63.918) | 3.82 | 2587.3 | 112.25 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | holdout | resident | stock | 2146.892 (2132.416–2157.658) | 1.00 | 237.6 | 5310.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | resident | generated-owned | 2215.516 (2189.965–2238.649) | 1.03 | 230.2 | 5427.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | resident | slab | 3209.991 (3196.177–3596.030) | 1.50 | 158.9 | 3445.25 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout | resident | slab-native | 3315.058 (3248.865–3374.740) | 1.54 | 153.9 | 3445.25 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout | resident | slab-lazy | 5658.988 (5630.912–5761.887) | 2.64 | 90.1 | 7743.75 | 0.0 | 528.5 / 2471.0 | 0.0 | 510.0 |
| candidate | holdout | resident | slab-session | 2242.071 (2115.492–2363.715) | 1.04 | 227.5 | 1197.00 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
| baseline | sparse | first | stock | 14.949 (14.784–15.408) | 1.00 | 10028.0 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | first | generated-owned | 12.904 (12.890–13.647) | 0.86 | 11617.3 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | first | slab | 53.082 (46.805–53.530) | 3.55 | 2824.1 | 126.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | first | slab-native | 47.255 (47.249–47.502) | 3.16 | 3172.3 | 126.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | first | slab-projected | 54.763 (52.800–59.776) | 3.66 | 2737.3 | 95.50 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| baseline | sparse | first | slab-lazy | 56.114 (54.361–56.252) | 3.75 | 2671.4 | 112.25 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| baseline | holdout | resident | stock | 2108.867 (2106.000–2133.940) | 1.00 | 241.9 | 5310.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | resident | generated-owned | 2235.156 (2194.232–2236.018) | 1.06 | 228.2 | 5427.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | resident | slab | 3256.512 (3225.354–3583.084) | 1.54 | 156.6 | 3445.25 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| baseline | holdout | resident | slab-native | 3245.858 (3243.821–3263.656) | 1.54 | 157.1 | 3445.25 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| baseline | holdout | resident | slab-lazy | 5663.168 (5626.895–6313.245) | 2.69 | 90.1 | 7743.75 | 0.0 | 528.5 / 2471.0 | 0.0 | 510.0 |
| baseline | holdout | resident | slab-session | 2309.860 (2137.206–2494.682) | 1.10 | 220.8 | 1197.00 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
