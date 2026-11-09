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
| full | decode | 791.9 | — | — | 1006.0 (1.27×) | — | — | — | 797.8 (1.01×) | — | — | — | 6.9 (0.01×) | 18.2 (0.02×) | 13.1 (0.02×) | 7.9 (0.01×) | 29.0 (0.04×) | 282.3 (0.36×) | 282.8 (0.36×) | 305.2 (0.39×) | 348.9 (0.44×) | 335.0 (0.42×) | 935.8 (1.18×) | 940.5 (1.19×) | 938.7 (1.19×) | 348.9 (0.44×) | 787.9 (1.00×) | 372.4 (0.47×) | 349.3 (0.44×) | 323.7 (0.41×) | 951.2 (1.20×) | 949.9 (1.20×) | 325.9 (0.41×) | 289.0 (0.36×) | 972.1 (1.23×) | 1016.5 (1.28×) | 805.0 (1.02×) |
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
| holdout | decode | — | — | — | — | — | — | — | — | — | — | — | 7.0 | 18.3 | 13.2 | 8.0 | 28.3 | 749.3 | 744.6 | 943.1 | 961.7 | 979.9 | 2779.9 | 3466.4 | 3444.0 | 971.8 | 2175.7 | 997.3 | 972.9 | 931.3 | 3454.8 | 3336.3 | 930.5 | 757.8 | 3479.0 | 3564.1 | 2065.9 |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | full | decode | stock | 791.885 (790.005–803.965) | 1.00 | 189.3 | 1629.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | slab | 1005.972 (991.812–18431.826) | 1.27 | 149.0 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | decode | slab-session | 797.778 (796.251–806.243) | 1.01 | 187.9 | 40.00 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | full | decode | probe-empty | 6.887 (6.881–7.557) | 0.01 | 21766.2 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-lease | 18.233 (18.185–18.388) | 0.02 | 8221.8 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-transition | 13.115 (12.989–13.216) | 0.02 | 11430.4 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-leaf-call | 7.902 (7.848–8.179) | 0.01 | 18971.4 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-pin-only | 28.958 (28.074–45.752) | 0.04 | 5176.7 | 24.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-native-leaf | 282.293 (278.577–288.514) | 0.36 | 531.0 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-native | 282.847 (281.727–289.335) | 0.36 | 530.0 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-alloc | 305.182 (305.015–310.372) | 0.39 | 491.2 | 736.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-pin | 348.931 (339.764–368.352) | 0.44 | 429.6 | 760.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-trim | 335.002 (332.442–340.155) | 0.42 | 447.5 | 760.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-verify | 935.775 (930.832–2951.742) | 1.18 | 160.2 | 760.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-publish-generic | 940.518 (927.835–952.203) | 1.19 | 159.4 | 808.00 | 0.0 | 547.2 / 728.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-publish | 938.741 (933.344–976.954) | 1.19 | 159.7 | 808.00 | 0.0 | 547.2 / 728.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-trusted | 348.921 (347.992–350.917) | 0.44 | 429.6 | 808.00 | 0.0 | 547.2 / 728.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-internal | 787.935 (782.124–1379.202) | 1.00 | 190.3 | 840.00 | 0.0 | 547.2 / 728.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-heap-pin2 | 372.366 (363.159–383.290) | 0.47 | 402.6 | 784.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-heap-copy-in | 349.279 (345.124–355.109) | 0.44 | 429.2 | 760.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-copy-out | 323.660 (321.846–326.850) | 0.41 | 463.2 | 555.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-copy-out-publish | 951.212 (944.111–976.977) | 1.20 | 157.6 | 603.25 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | decode | probe-copy-out-leaf-publish | 949.894 (947.234–16038.901) | 1.20 | 157.8 | 603.25 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | decode | probe-trusted-copy-out-leaf | 325.878 (324.922–424.466) | 0.41 | 460.0 | 603.25 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | decode | probe-session-raw | 288.970 (284.091–290.938) | 0.36 | 518.8 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-plan | 972.067 (963.503–976.848) | 1.23 | 154.2 | 808.00 | 0.0 | 547.2 / 728.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-plan-heap | 1016.527 (1007.008–1034.917) | 1.28 | 147.5 | 832.00 | 0.0 | 547.2 / 728.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-session | 804.971 (800.810–808.990) | 1.02 | 186.2 | 40.00 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | holdout | decode | probe-empty | 6.995 (6.829–7.148) | — | 72914.4 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-lease | 18.326 (18.066–18.716) | — | 27830.4 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-transition | 13.162 (13.016–13.321) | — | 38750.0 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-leaf-call | 7.961 (7.842–8.173) | — | 64067.1 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-pin-only | 28.288 (28.193–31.809) | — | 18029.8 | 24.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-native-leaf | 749.303 (737.240–900.280) | — | 680.7 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-native | 744.591 (739.391–747.216) | — | 685.0 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-alloc | 943.142 (940.112–953.356) | — | 540.8 | 2208.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-pin | 961.669 (961.294–962.599) | — | 530.4 | 2232.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-trim | 979.936 (950.575–1177.766) | — | 520.5 | 2232.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-verify | 2779.901 (2666.181–3562.181) | — | 183.5 | 2232.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-publish-generic | 3466.373 (3302.003–3491.641) | — | 147.1 | 2294.00 | 0.0 | 1638.5 / 2200.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-publish | 3444.011 (3421.104–3448.545) | — | 148.1 | 2294.00 | 0.0 | 1638.5 / 2200.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-trusted | 971.785 (968.889–992.890) | — | 524.8 | 2280.00 | 0.0 | 1638.5 / 2200.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-internal | 2175.664 (2161.434–2176.649) | — | 234.4 | 2312.00 | 0.0 | 1638.5 / 2200.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-heap-pin2 | 997.259 (992.656–1010.230) | — | 511.4 | 2256.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-heap-copy-in | 972.861 (952.352–977.262) | — | 524.3 | 2232.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-copy-out | 931.298 (929.040–956.128) | — | 547.7 | 1646.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-copy-out-publish | 3454.801 (3296.040–3556.265) | — | 147.6 | 1708.50 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-copy-out-leaf-publish | 3336.318 (3318.357–3931.273) | — | 152.9 | 1708.50 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-trusted-copy-out-leaf | 930.546 (928.561–951.320) | — | 548.1 | 1694.50 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-session-raw | 757.758 (749.382–761.453) | — | 673.1 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-plan | 3479.026 (3470.082–3485.650) | — | 146.6 | 2294.00 | 0.0 | 1638.5 / 2200.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-plan-heap | 3564.106 (3502.885–3613.421) | — | 143.1 | 2318.00 | 0.0 | 1638.5 / 2200.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-session | 2065.937 (2065.404–2082.719) | — | 246.9 | 40.00 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
