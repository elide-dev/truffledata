# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | 17.3 | — | 17.4 (1.00×) | 53.7 (3.10×) | 54.1 (3.13×) | 65.7 (3.79×) | 62.9 (3.63×) | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | 2247.0 | — | 2862.2 (1.27×) | 1903.1 (0.85×) | 1606.5 (0.71×) | — | 6913.8 (3.08×) | 3049.1 (1.36×) | — | — | — |
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
| sparse | first | 27.2 | — | 15.7 (0.58×) | 54.2 (1.99×) | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | 2590.8 | — | 2454.3 (0.95×) | 5240.0 (2.02×) | — | — | — | 2406.6 (0.93×) | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | sparse | first | stock | 17.312 (17.176–23.641) | 1.00 | 8659.2 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | first | generated-owned | 17.389 (17.291–17.896) | 1.00 | 8621.0 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | first | slab | 53.712 (51.293–64.231) | 3.10 | 2790.9 | 126.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | first | slab-native | 54.139 (47.098–83.131) | 3.13 | 2768.9 | 126.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | first | slab-projected | 65.667 (61.147–70.794) | 3.79 | 2282.8 | 95.50 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | first | slab-lazy | 62.890 (58.699–66.463) | 3.63 | 2383.6 | 112.25 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | holdout | resident | stock | 2247.007 (2189.800–2408.944) | 1.00 | 227.0 | 5310.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | resident | generated-owned | 2862.204 (2465.593–3456.051) | 1.27 | 178.2 | 5427.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | resident | slab | 1903.122 (1481.701–2112.880) | 0.85 | 268.0 | 3391.25 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout | resident | slab-native | 1606.520 (1490.350–1920.249) | 0.71 | 317.5 | 3391.25 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout | resident | slab-lazy | 6913.834 (6434.379–9692.834) | 3.08 | 73.8 | 7703.75 | 0.0 | 528.5 / 2471.0 | 0.0 | 510.0 |
| candidate | holdout | resident | slab-session | 3049.132 (2544.383–6517.742) | 1.36 | 167.3 | 1197.00 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
| baseline | sparse | first | stock | 27.209 (22.284–124.335) | 1.00 | 5509.5 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | first | generated-owned | 15.670 (14.035–19.333) | 0.58 | 9566.7 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | first | slab | 54.206 (50.577–78.745) | 1.99 | 2765.5 | 126.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | holdout | resident | stock | 2590.795 (2397.651–3014.952) | 1.00 | 196.9 | 5310.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | resident | generated-owned | 2454.289 (2409.179–2723.030) | 0.95 | 207.8 | 5427.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | resident | slab | 5240.013 (3652.760–5922.593) | 2.02 | 97.3 | 3405.25 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| baseline | holdout | resident | slab-session | 2406.620 (2219.253–2624.647) | 0.93 | 211.9 | 1197.00 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
