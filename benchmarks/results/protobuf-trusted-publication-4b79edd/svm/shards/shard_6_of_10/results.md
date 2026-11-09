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
| sparse | interpret | 11.5 | 264.5 (23.00×) | 36.9 (3.20×) | 38.0 (3.31×) | 41.7 (3.63×) | 86.4 (7.51×) | 81.2 (7.07×) | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | 184.4 | 3884.7 (21.07×) | 71.2 (0.39×) | 218.3 (1.18×) | 165.2 (0.90×) | — | 223.5 (1.21×) | — | — | — | — |
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
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | 11.1 | — | 39.6 (3.56×) | 41.3 (3.72×) | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | 198.9 | — | 65.7 (0.33×) | 142.6 (0.72×) | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | sparse | interpret | stock | 11.499 (11.328–13.052) | 1.00 | 13036.6 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | interpret | stock-interop | 264.484 (227.752–279.853) | 23.00 | 566.8 | 193.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | interpret | generated-owned | 36.851 (34.710–54.001) | 3.20 | 4067.9 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | interpret | slab | 38.032 (35.382–58.636) | 3.31 | 3941.5 | 2.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | interpret | slab-native | 41.708 (41.373–47.101) | 3.63 | 3594.2 | 2.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | interpret | slab-projected | 86.384 (58.920–95.258) | 7.51 | 1735.3 | 2.00 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | interpret | slab-lazy | 81.241 (58.603–85.661) | 7.07 | 1845.2 | 2.00 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | holdout | peak | stock | 184.412 (174.450–188.701) | 1.00 | 2765.7 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | peak | stock-interop | 3884.732 (3289.430–4134.677) | 21.07 | 131.3 | 5319.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | peak | generated-owned | 71.185 (66.010–74.665) | 0.39 | 7164.9 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | peak | slab | 218.264 (149.013–220.856) | 1.18 | 2336.8 | 1.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout | peak | slab-native | 165.181 (153.317–179.554) | 0.90 | 3087.7 | 1.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout | peak | slab-lazy | 223.514 (183.823–228.500) | 1.21 | 2281.9 | 1.00 | 0.0 | 528.5 / 2471.0 | 0.0 | 510.0 |
| baseline | sparse | interpret | stock | 11.120 (11.100–16.035) | 1.00 | 13480.2 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | interpret | generated-owned | 39.610 (36.841–43.585) | 3.56 | 3784.6 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | interpret | slab | 41.313 (41.197–58.071) | 3.72 | 3628.5 | 2.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | holdout | peak | stock | 198.898 (188.766–209.311) | 1.00 | 2564.3 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | peak | generated-owned | 65.744 (62.425–72.642) | 0.33 | 7757.9 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | peak | slab | 142.628 (142.483–223.595) | 0.72 | 3576.0 | 1.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
