# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | 18.8 | — | 22.0 (1.17×) | 57.7 (3.07×) | 65.1 (3.47×) | 70.7 (3.76×) | 86.1 (4.58×) | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | 2674.1 | — | 2806.5 (1.05×) | 1626.7 (0.61×) | 1605.3 (0.60×) | — | 7205.6 (2.69×) | 2622.1 (0.98×) | — | — | — |
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
| sparse | first | 20.5 | — | 17.5 (0.85×) | 59.6 (2.90×) | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | 2828.7 | — | 2817.7 (1.00×) | 4160.4 (1.47×) | — | — | — | 2979.6 (1.05×) | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | sparse | first | stock | 18.787 (18.341–19.379) | 1.00 | 7979.1 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | first | generated-owned | 21.996 (17.566–30.721) | 1.17 | 6815.2 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | first | slab | 57.745 (54.906–75.362) | 3.07 | 2596.0 | 126.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | first | slab-native | 65.117 (55.330–71.239) | 3.47 | 2302.1 | 126.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | first | slab-projected | 70.679 (66.589–102.136) | 3.76 | 2120.9 | 95.50 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | first | slab-lazy | 86.113 (71.107–88.430) | 4.58 | 1740.8 | 112.25 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | holdout | resident | stock | 2674.100 (2659.696–2708.394) | 1.00 | 190.7 | 5310.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | resident | generated-owned | 2806.527 (2767.545–2833.744) | 1.05 | 181.7 | 5427.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | resident | slab | 1626.722 (1577.044–1632.316) | 0.61 | 313.5 | 3391.25 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout | resident | slab-native | 1605.344 (1594.148–2096.565) | 0.60 | 317.7 | 3391.25 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout | resident | slab-lazy | 7205.624 (7202.753–9641.186) | 2.69 | 70.8 | 7703.75 | 0.0 | 528.5 / 2471.0 | 0.0 | 510.0 |
| candidate | holdout | resident | slab-session | 2622.120 (2617.803–2728.036) | 0.98 | 194.5 | 1197.00 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
| baseline | sparse | first | stock | 20.543 (19.734–22.207) | 1.00 | 7297.0 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | first | generated-owned | 17.534 (16.688–20.107) | 0.85 | 8549.6 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | first | slab | 59.574 (58.263–103.467) | 2.90 | 2516.3 | 126.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | holdout | resident | stock | 2828.703 (2663.816–2839.516) | 1.00 | 180.3 | 5310.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | resident | generated-owned | 2817.701 (2794.167–2862.576) | 1.00 | 181.0 | 5427.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | resident | slab | 4160.378 (3957.977–4501.756) | 1.47 | 122.6 | 3405.25 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| baseline | holdout | resident | slab-session | 2979.623 (2679.450–3028.331) | 1.05 | 171.2 | 1197.00 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
