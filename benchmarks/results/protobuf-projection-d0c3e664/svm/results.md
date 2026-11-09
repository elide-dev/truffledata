# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | 795.8 | — | — | 1964.8 (2.47×) | — | 1309.8 (1.65×) | 1608.3 (2.02×) | 895.9 (1.13×) | 699.9 (0.88×) | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | 2604.8 | — | — | 5555.5 (2.13×) | — | — | 7429.6 (2.85×) | 2601.3 (1.00×) | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | 2414.0 | — | — | 5021.4 (2.08×) | — | 1903.9 (0.79×) | 2809.4 (1.16×) | 2187.0 (0.91×) | 1448.5 (0.60×) | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | sparse | resident | stock | 795.834 (790.983–799.700) | 1.00 | 188.4 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | resident | slab | 1964.777 (1955.921–1966.680) | 2.47 | 76.3 | 2184.00 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | sparse | resident | slab-projected | 1309.811 (1306.940–1312.615) | 1.65 | 114.4 | 1118.00 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | resident | slab-lazy | 1608.346 (1597.170–1612.137) | 2.02 | 93.2 | 1727.25 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | sparse | resident | slab-session | 895.878 (890.886–965.531) | 1.13 | 167.3 | 151.25 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | sparse | resident | slab-session-projected | 699.869 (695.817–712.301) | 0.88 | 214.2 | 151.25 | 0.0 | 289.2 / 0.0 | 855.6 | 149.9 |
| candidate | holdout | resident | stock | 2604.804 (2584.158–2615.033) | 1.00 | 195.8 | 5310.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | resident | slab | 5555.474 (5481.833–5685.485) | 2.13 | 91.8 | 6399.50 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout | resident | slab-lazy | 7429.557 (7355.175–7470.665) | 2.85 | 68.6 | 8937.25 | 0.0 | 528.5 / 2471.0 | 0.0 | 510.0 |
| candidate | holdout | resident | slab-session | 2601.253 (2598.705–2815.039) | 1.00 | 196.1 | 1197.00 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
| candidate | holdout-sparse | resident | stock | 2414.021 (2401.508–2441.964) | 1.00 | 211.3 | 5310.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab | 5021.357 (4997.248–5022.272) | 2.08 | 101.6 | 5434.25 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab-projected | 1903.945 (1891.853–1968.294) | 0.79 | 267.9 | 1118.00 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab-lazy | 2809.384 (2803.996–2820.902) | 1.16 | 181.5 | 3001.25 | 0.0 | 528.5 / 528.5 | 0.0 | 510.0 |
| candidate | holdout-sparse | resident | slab-session | 2187.016 (2169.781–2187.844) | 0.91 | 233.2 | 151.25 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
| candidate | holdout-sparse | resident | slab-session-projected | 1448.545 (1437.567–1483.698) | 0.60 | 352.1 | 151.25 | 0.0 | 528.5 / 0.0 | 2296.1 | 510.0 |
