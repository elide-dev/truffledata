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
| sparse | interpret | 9.8 | 193.7 (19.75×) | 71.0 (7.24×) | 36.9 (3.76×) | 41.8 (4.26×) | 53.0 (5.40×) | 56.0 (5.71×) | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | 142.5 | 6315.5 (44.33×) | 61.5 (0.43×) | 143.3 (1.01×) | 160.0 (1.12×) | — | 183.6 (1.29×) | — | — | — | — |
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
| sparse | interpret | 11.3 | 170.1 (15.03×) | 32.6 (2.88×) | 40.6 (3.58×) | 35.6 (3.15×) | 52.7 (4.66×) | 60.0 (5.31×) | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | 137.8 | 2966.5 (21.53×) | 59.2 (0.43×) | 128.7 (0.93×) | 127.8 (0.93×) | — | 171.9 (1.25×) | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | sparse | interpret | stock | 9.810 (9.753–10.085) | 1.00 | 15280.3 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | interpret | stock-interop | 193.728 (191.686–225.537) | 19.75 | 773.8 | 193.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | interpret | generated-owned | 71.023 (32.193–94.818) | 7.24 | 2110.7 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | interpret | slab | 36.851 (34.933–37.936) | 3.76 | 4067.9 | 2.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | interpret | slab-native | 41.827 (32.310–45.984) | 4.26 | 3583.9 | 2.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | interpret | slab-projected | 52.956 (52.652–56.587) | 5.40 | 2830.8 | 2.00 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | interpret | slab-lazy | 55.986 (52.699–433.661) | 5.71 | 2677.5 | 2.00 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | holdout | peak | stock | 142.469 (135.412–149.505) | 1.00 | 3579.9 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | peak | stock-interop | 6315.454 (3035.014–7063.586) | 44.33 | 80.8 | 5319.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | peak | generated-owned | 61.478 (56.880–70.667) | 0.43 | 8296.2 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | peak | slab | 143.319 (130.931–147.472) | 1.01 | 3558.7 | 1.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout | peak | slab-native | 160.029 (145.037–353.242) | 1.12 | 3187.1 | 1.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout | peak | slab-lazy | 183.568 (166.126–197.899) | 1.29 | 2778.4 | 1.00 | 0.0 | 528.5 / 2471.0 | 0.0 | 510.0 |
| baseline | sparse | interpret | stock | 11.314 (10.193–11.359) | 1.00 | 13249.5 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | interpret | stock-interop | 170.053 (168.620–184.429) | 15.03 | 881.5 | 193.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | interpret | generated-owned | 32.603 (31.920–33.257) | 2.88 | 4597.9 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | interpret | slab | 40.555 (37.658–52.419) | 3.58 | 3696.3 | 2.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | interpret | slab-native | 35.618 (34.145–40.032) | 3.15 | 4208.7 | 2.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | interpret | slab-projected | 52.729 (51.372–57.255) | 4.66 | 2843.0 | 2.00 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| baseline | sparse | interpret | slab-lazy | 60.035 (54.941–76.734) | 5.31 | 2497.0 | 2.00 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| baseline | holdout | peak | stock | 137.811 (136.551–142.109) | 1.00 | 3700.9 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | peak | stock-interop | 2966.473 (2947.381–3087.475) | 21.53 | 171.9 | 5319.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | peak | generated-owned | 59.241 (58.271–66.609) | 0.43 | 8609.4 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | peak | slab | 128.702 (128.017–129.282) | 0.93 | 3962.9 | 1.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| baseline | holdout | peak | slab-native | 127.801 (126.295–132.483) | 0.93 | 3990.8 | 1.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| baseline | holdout | peak | slab-lazy | 171.913 (163.556–181.202) | 1.25 | 2966.8 | 1.00 | 0.0 | 528.5 / 2471.0 | 0.0 | 510.0 |
