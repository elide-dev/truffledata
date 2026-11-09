# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager | probe-empty | probe-lease | probe-transition | probe-leaf-call | probe-pin-only | probe-native-leaf | probe-native | probe-alloc | probe-pin | probe-trim | probe-verify | probe-publish-generic | probe-publish | probe-trusted | probe-internal | probe-heap-pin2 | probe-heap-copy-in | probe-copy-out | probe-copy-out-publish | probe-copy-out-leaf-publish | probe-trusted-copy-out-leaf | probe-session-raw | probe-plan | probe-plan-heap | probe-session |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | 849.1 | — | — | 466.1 (0.55×) | — | — | — | 858.0 (1.01×) | — | — | — | 7.2 (0.01×) | 22.0 (0.03×) | 13.6 (0.02×) | 9.8 (0.01×) | 31.8 (0.04×) | 329.1 (0.39×) | 350.4 (0.41×) | 366.3 (0.43×) | 441.5 (0.52×) | 415.9 (0.49×) | 932.0 (1.10×) | 1144.4 (1.35×) | 1330.1 (1.57×) | 414.2 (0.49×) | 916.0 (1.08×) | 408.1 (0.48×) | 472.3 (0.56×) | 396.4 (0.47×) | 956.2 (1.13×) | 942.4 (1.11×) | 434.3 (0.51×) | 396.5 (0.47×) | 452.5 (0.53×) | 445.2 (0.52×) | 905.1 (1.07×) |
| full | resident | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| holdout | decode | — | — | — | — | — | — | — | — | — | — | — | 7.4 | 21.3 | 14.4 | 9.4 | 30.8 | 955.0 | 829.8 | 1101.7 | 1047.4 | 1149.2 | 3321.4 | 4219.4 | 3548.9 | 1358.7 | 2968.3 | 1109.7 | 1235.5 | 1066.1 | 4171.0 | 3385.6 | 1101.1 | 827.9 | 1472.3 | 1165.3 | 2320.7 |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | full | decode | stock | 849.138 (812.163–923.864) | 1.00 | 176.5 | 1629.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | slab | 466.071 (413.226–596.799) | 0.55 | 321.6 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | decode | slab-session | 857.953 (853.758–1578.892) | 1.01 | 174.7 | 40.00 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | full | decode | probe-empty | 7.201 (7.052–9.393) | 0.01 | 20816.7 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-lease | 22.003 (19.473–32.467) | 0.03 | 6812.8 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-transition | 13.634 (13.306–14.194) | 0.02 | 10995.3 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-leaf-call | 9.786 (8.566–12.043) | 0.01 | 15318.8 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-pin-only | 31.818 (29.583–31.852) | 0.04 | 4711.4 | 24.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-native-leaf | 329.088 (311.331–358.470) | 0.39 | 455.5 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-native | 350.407 (299.965–528.916) | 0.41 | 427.8 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-alloc | 366.328 (337.849–413.821) | 0.43 | 409.2 | 736.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-pin | 441.471 (379.718–683.411) | 0.52 | 339.6 | 760.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-trim | 415.882 (412.859–544.359) | 0.49 | 360.5 | 760.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-verify | 931.990 (929.938–965.707) | 1.10 | 160.8 | 760.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-publish-generic | 1144.399 (943.169–1841.166) | 1.35 | 131.0 | 808.00 | 0.0 | 547.2 / 728.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-publish | 1330.072 (947.886–1332.539) | 1.57 | 112.7 | 808.00 | 0.0 | 547.2 / 728.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-trusted | 414.225 (382.358–469.247) | 0.49 | 361.9 | 808.00 | 0.0 | 547.2 / 728.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-internal | 916.013 (875.812–1328.658) | 1.08 | 163.7 | 840.00 | 0.0 | 547.2 / 728.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-heap-pin2 | 408.077 (394.092–469.108) | 0.48 | 367.3 | 784.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-heap-copy-in | 472.263 (376.326–578.993) | 0.56 | 317.4 | 760.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-copy-out | 396.353 (357.024–2427.123) | 0.47 | 378.2 | 555.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-copy-out-publish | 956.249 (930.328–986.112) | 1.13 | 156.8 | 603.25 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | decode | probe-copy-out-leaf-publish | 942.367 (920.983–1195.714) | 1.11 | 159.1 | 603.25 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | decode | probe-trusted-copy-out-leaf | 434.252 (389.801–510.046) | 0.51 | 345.2 | 603.25 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | decode | probe-session-raw | 396.455 (322.311–993.058) | 0.47 | 378.1 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-plan | 452.539 (442.511–471.704) | 0.53 | 331.3 | 808.00 | 0.0 | 547.2 / 728.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-plan-heap | 445.242 (427.487–448.289) | 0.52 | 336.7 | 832.00 | 0.0 | 547.2 / 728.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-session | 905.146 (893.225–929.051) | 1.07 | 165.6 | 40.00 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | holdout | decode | probe-empty | 7.420 (7.115–7.676) | — | 68740.1 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-lease | 21.344 (19.090–28.570) | — | 23895.8 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-transition | 14.415 (13.704–19.577) | — | 35381.1 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-leaf-call | 9.367 (8.585–10.079) | — | 54450.2 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-pin-only | 30.788 (30.245–31.154) | — | 16566.2 | 24.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-native-leaf | 955.000 (885.425–1145.984) | — | 534.1 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-native | 829.838 (827.777–868.985) | — | 614.6 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-alloc | 1101.683 (1051.693–1790.169) | — | 463.0 | 2208.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-pin | 1047.425 (1044.401–1195.890) | — | 486.9 | 2232.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-trim | 1149.152 (1119.555–1289.061) | — | 443.8 | 2232.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-verify | 3321.376 (2655.062–3615.692) | — | 153.6 | 2232.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-publish-generic | 4219.371 (3924.731–4823.956) | — | 120.9 | 2294.00 | 0.0 | 1638.5 / 2200.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-publish | 3548.927 (3484.134–3744.036) | — | 143.7 | 2294.00 | 0.0 | 1638.5 / 2200.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-trusted | 1358.650 (1097.349–1470.791) | — | 375.4 | 2280.00 | 0.0 | 1638.5 / 2200.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-internal | 2968.251 (2884.879–3322.628) | — | 171.8 | 2312.00 | 0.0 | 1638.5 / 2200.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-heap-pin2 | 1109.711 (1025.771–1841.181) | — | 459.6 | 2256.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-heap-copy-in | 1235.502 (1029.027–1424.975) | — | 412.8 | 2232.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-copy-out | 1066.079 (1043.550–1119.577) | — | 478.4 | 1646.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-copy-out-publish | 4171.023 (3279.209–6022.739) | — | 122.3 | 1708.50 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-copy-out-leaf-publish | 3385.601 (3338.341–3637.778) | — | 150.6 | 1708.50 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-trusted-copy-out-leaf | 1101.122 (1041.913–1142.435) | — | 463.2 | 1694.50 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-session-raw | 827.854 (786.886–964.690) | — | 616.1 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-plan | 1472.347 (1153.595–1624.166) | — | 346.4 | 2280.00 | 0.0 | 1638.5 / 2200.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-plan-heap | 1165.258 (1135.188–1358.593) | — | 437.7 | 2304.00 | 0.0 | 1638.5 / 2200.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-session | 2320.703 (2169.019–2506.278) | — | 219.8 | 40.00 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
