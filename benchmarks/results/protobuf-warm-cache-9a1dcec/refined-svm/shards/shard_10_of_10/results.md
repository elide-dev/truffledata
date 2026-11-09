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
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | 856.0 | — | 850.0 (0.99×) | 1332.5 (1.56×) | 1279.6 (1.49×) | — | 2865.2 (3.35×) | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | 238343.0 | — | 272189.6 (1.14×) | 333319.3 (1.40×) | 340526.1 (1.43×) | — | 765788.2 (3.21×) | 262354.0 (1.10×) | — | — | — |
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
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | 838.2 | — | 884.0 (1.05×) | 1295.2 (1.55×) | 1285.1 (1.53×) | — | 2845.8 (3.40×) | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | 241516.2 | — | 268854.2 (1.11×) | 366994.8 (1.52×) | 370253.1 (1.53×) | — | 784750.5 (3.25×) | 264244.2 (1.09×) | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | full | heap | stock | 855.991 (844.107–860.708) | 1.00 | 175.1 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | heap | generated-owned | 849.954 (848.618–855.360) | 0.99 | 176.4 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | heap | slab | 1332.541 (1276.164–17754.830) | 1.56 | 112.5 | 1504.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | heap | slab-native | 1279.603 (1272.479–1292.527) | 1.49 | 117.2 | 1504.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | heap | slab-lazy | 2865.233 (2860.688–2880.862) | 3.35 | 52.3 | 3295.25 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| candidate | large | resident | stock | 238343.036 (238296.920–246607.460) | 1.00 | 217.4 | 433009.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | resident | generated-owned | 272189.578 (265008.802–280693.328) | 1.14 | 190.4 | 460180.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | resident | slab | 333319.312 (331701.062–341323.875) | 1.40 | 155.5 | 317632.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| candidate | large | resident | slab-native | 340526.125 (335437.625–342209.938) | 1.43 | 152.2 | 317632.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| candidate | large | resident | slab-lazy | 765788.229 (743952.510–829839.219) | 3.21 | 67.7 | 834853.25 | 0.0 | 45619.8 / 281827.8 | 0.0 | 51822.3 |
| candidate | large | resident | slab-session | 262354.010 (261129.219–262370.885) | 1.10 | 197.5 | 91383.25 | 0.0 | 172771.8 / 0.0 | 207545.1 | 0.0 |
| baseline | full | heap | stock | 838.158 (832.022–872.740) | 1.00 | 178.9 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | heap | generated-owned | 883.951 (877.797–924.620) | 1.05 | 169.6 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | heap | slab | 1295.231 (1286.010–17974.364) | 1.55 | 115.7 | 1504.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | heap | slab-native | 1285.110 (1276.997–1287.330) | 1.53 | 116.6 | 1504.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | heap | slab-lazy | 2845.767 (2839.031–2885.331) | 3.40 | 52.7 | 3295.25 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| baseline | large | resident | stock | 241516.205 (238276.161–252019.196) | 1.00 | 214.6 | 433009.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | resident | generated-owned | 268854.219 (267226.464–269130.990) | 1.11 | 192.8 | 460180.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | resident | slab | 366994.812 (338719.062–368053.125) | 1.52 | 141.2 | 317632.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| baseline | large | resident | slab-native | 370253.131 (355215.688–392799.750) | 1.53 | 140.0 | 317632.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| baseline | large | resident | slab-lazy | 784750.469 (782413.281–933957.656) | 3.25 | 66.0 | 834853.25 | 0.0 | 45619.8 / 281827.8 | 0.0 | 51822.3 |
| baseline | large | resident | slab-session | 264244.219 (261809.844–266754.792) | 1.09 | 196.1 | 91383.25 | 0.0 | 172771.8 / 0.0 | 207545.1 | 0.0 |
