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
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | 861.7 | — | 857.3 (0.99×) | 1311.1 (1.52×) | 1329.4 (1.54×) | — | 2775.8 (3.22×) | 1094.5 (1.27×) | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | 223390.1 | — | 261937.2 (1.17×) | 289612.9 (1.30×) | 287794.3 (1.29×) | — | 155548.7 (0.70×) | 211383.0 (0.95×) | — | — | — |
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
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | 855.5 | — | 880.7 (1.03×) | 1276.7 (1.49×) | 1277.1 (1.49×) | — | 2784.8 (3.26×) | 1111.9 (1.30×) | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | 226283.8 | — | 262181.8 (1.16×) | 292891.9 (1.29×) | 311007.4 (1.37×) | — | 162139.1 (0.72×) | 210354.0 (0.93×) | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | full | resident | stock | 861.746 (850.298–937.416) | 1.00 | 174.0 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | resident | generated-owned | 857.269 (851.437–873.988) | 0.99 | 174.9 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | resident | slab | 1311.056 (1275.425–17395.684) | 1.52 | 114.3 | 1480.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | resident | slab-native | 1329.413 (1270.731–1338.948) | 1.54 | 112.8 | 1480.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | resident | slab-lazy | 2775.775 (2762.553–2779.368) | 3.22 | 54.0 | 3087.25 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| candidate | full | resident | slab-session | 1094.485 (1079.605–1181.877) | 1.27 | 137.0 | 718.25 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | large | decode | stock | 223390.058 (216247.738–236730.973) | 1.00 | 232.0 | 433008.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | decode | generated-owned | 261937.201 (250869.763–265265.844) | 1.17 | 197.8 | 460179.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | decode | slab | 289612.901 (283212.630–293937.271) | 1.30 | 178.9 | 226294.50 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| candidate | large | decode | slab-native | 287794.292 (284368.958–298972.219) | 1.29 | 180.1 | 226294.50 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| candidate | large | decode | slab-lazy | 155548.733 (154504.466–160580.684) | 0.70 | 333.2 | 116107.75 | 0.0 | 45619.8 / 45619.8 | 0.0 | 51822.3 |
| candidate | large | decode | slab-session | 211383.016 (210122.332–214162.520) | 0.95 | 245.2 | 40.00 | 0.0 | 172771.8 / 0.0 | 207545.1 | 0.0 |
| baseline | full | resident | stock | 855.509 (851.228–858.574) | 1.00 | 175.2 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | resident | generated-owned | 880.738 (878.725–902.524) | 1.03 | 170.2 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | resident | slab | 1276.693 (1273.895–1293.400) | 1.49 | 117.4 | 1480.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | resident | slab-native | 1277.126 (1270.834–1296.949) | 1.49 | 117.4 | 1480.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | resident | slab-lazy | 2784.776 (2733.907–3050.654) | 3.26 | 53.8 | 3087.25 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| baseline | full | resident | slab-session | 1111.928 (1095.676–1227.854) | 1.30 | 134.8 | 718.25 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| baseline | large | decode | stock | 226283.790 (210710.160–228033.821) | 1.00 | 229.0 | 433008.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | decode | generated-owned | 262181.844 (246234.036–268319.422) | 1.16 | 197.7 | 460179.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | decode | slab | 292891.927 (286039.604–306261.964) | 1.29 | 176.9 | 226294.50 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| baseline | large | decode | slab-native | 311007.391 (284477.016–324468.487) | 1.37 | 166.6 | 226294.50 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| baseline | large | decode | slab-lazy | 162139.141 (156451.766–169209.434) | 0.72 | 319.6 | 116107.75 | 0.0 | 45619.8 / 45619.8 | 0.0 | 51822.3 |
| baseline | large | decode | slab-session | 210354.031 (209543.574–213955.840) | 0.93 | 246.4 | 40.00 | 0.0 | 172771.8 / 0.0 | 207545.1 | 0.0 |
