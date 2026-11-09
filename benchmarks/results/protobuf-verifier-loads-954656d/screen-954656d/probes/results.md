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
| full | decode | 795.0 | — | — | 921.9 (1.16×) | — | — | — | 795.1 (1.00×) | — | — | — | 6.8 (0.01×) | 18.2 (0.02×) | 13.0 (0.02×) | 7.9 (0.01×) | 28.0 (0.04×) | 273.0 (0.34×) | 278.6 (0.35×) | 305.7 (0.38×) | 332.1 (0.42×) | 333.8 (0.42×) | 818.4 (1.03×) | 842.0 (1.06×) | 838.5 (1.05×) | 344.8 (0.43×) | 786.9 (0.99×) | 344.5 (0.43×) | 340.8 (0.43×) | 314.6 (0.40×) | 845.9 (1.06×) | 856.6 (1.08×) | 322.7 (0.41×) | 286.7 (0.36×) | 883.9 (1.11×) | 910.3 (1.15×) | 800.6 (1.01×) |
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
| holdout | decode | — | — | — | — | — | — | — | — | — | — | — | 6.9 | 18.2 | 13.0 | 7.9 | 27.8 | 735.8 | 747.1 | 899.4 | 951.0 | 929.5 | 2429.6 | 3124.0 | 3087.4 | 950.7 | 2121.3 | 959.7 | 946.3 | 901.2 | 3027.4 | 3061.2 | 929.5 | 746.6 | 3170.7 | 3196.1 | 2041.2 |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | full | decode | stock | 795.035 (794.801–40399.120) | 1.00 | 188.6 | 1629.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | slab | 921.898 (910.873–942.119) | 1.16 | 162.6 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | decode | slab-session | 795.059 (792.448–807.176) | 1.00 | 188.5 | 40.00 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | full | decode | probe-empty | 6.818 (6.817–6.966) | 0.01 | 21986.6 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-lease | 18.191 (18.065–18.244) | 0.02 | 8240.9 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-transition | 12.978 (12.954–13.005) | 0.02 | 11550.6 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-leaf-call | 7.869 (7.808–8.007) | 0.01 | 19049.1 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-pin-only | 27.967 (27.872–28.148) | 0.04 | 5360.2 | 24.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-native-leaf | 273.029 (271.687–291.837) | 0.34 | 549.0 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-native | 278.623 (276.179–285.211) | 0.35 | 538.0 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-alloc | 305.666 (305.513–305.745) | 0.38 | 490.4 | 736.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-pin | 332.076 (328.514–336.520) | 0.42 | 451.4 | 760.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-trim | 333.840 (326.072–342.619) | 0.42 | 449.0 | 760.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-verify | 818.449 (817.939–840.068) | 1.03 | 183.2 | 760.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-publish-generic | 842.018 (830.759–847.957) | 1.06 | 178.0 | 808.00 | 0.0 | 547.2 / 728.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-publish | 838.523 (824.683–841.732) | 1.05 | 178.8 | 808.00 | 0.0 | 547.2 / 728.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-trusted | 344.798 (340.229–345.325) | 0.43 | 434.8 | 808.00 | 0.0 | 547.2 / 728.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-internal | 786.944 (780.778–795.353) | 0.99 | 190.5 | 840.00 | 0.0 | 547.2 / 728.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-heap-pin2 | 344.463 (342.965–351.111) | 0.43 | 435.2 | 784.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-heap-copy-in | 340.753 (338.484–353.813) | 0.43 | 439.9 | 760.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-copy-out | 314.572 (314.469–320.860) | 0.40 | 476.5 | 555.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-copy-out-publish | 845.894 (843.575–847.406) | 1.06 | 177.2 | 603.25 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | decode | probe-copy-out-leaf-publish | 856.625 (841.460–856.965) | 1.08 | 175.0 | 603.25 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | decode | probe-trusted-copy-out-leaf | 322.716 (321.055–323.936) | 0.41 | 464.5 | 603.25 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | decode | probe-session-raw | 286.683 (285.867–288.105) | 0.36 | 522.9 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-plan | 883.936 (879.654–884.935) | 1.11 | 169.6 | 808.00 | 0.0 | 547.2 / 728.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-plan-heap | 910.344 (910.327–922.461) | 1.15 | 164.7 | 832.00 | 0.0 | 547.2 / 728.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-session | 800.596 (799.227–806.323) | 1.01 | 187.2 | 40.00 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | holdout | decode | probe-empty | 6.862 (6.750–6.896) | — | 74327.2 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-lease | 18.179 (18.023–18.350) | — | 28055.7 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-transition | 13.015 (12.990–13.040) | — | 39186.6 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-leaf-call | 7.906 (7.881–7.941) | — | 64510.3 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-pin-only | 27.821 (27.821–27.891) | — | 18332.3 | 24.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-native-leaf | 735.771 (735.569–742.774) | — | 693.2 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-native | 747.051 (738.169–781.385) | — | 682.7 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-alloc | 899.433 (887.318–972.894) | — | 567.1 | 2208.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-pin | 950.968 (939.490–987.084) | — | 536.3 | 2232.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-trim | 929.498 (921.490–929.590) | — | 548.7 | 2232.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-verify | 2429.582 (2411.923–2640.410) | — | 209.9 | 2232.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-publish-generic | 3123.958 (3080.853–3136.857) | — | 163.3 | 2294.00 | 0.0 | 1638.5 / 2200.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-publish | 3087.354 (3048.925–3096.254) | — | 165.2 | 2294.00 | 0.0 | 1638.5 / 2200.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-trusted | 950.734 (929.568–954.795) | — | 536.5 | 2280.00 | 0.0 | 1638.5 / 2200.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-internal | 2121.309 (2114.629–2123.454) | — | 240.4 | 2312.00 | 0.0 | 1638.5 / 2200.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-heap-pin2 | 959.707 (945.665–984.424) | — | 531.4 | 2256.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-heap-copy-in | 946.340 (938.583–981.567) | — | 539.0 | 2232.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-copy-out | 901.225 (899.353–903.912) | — | 565.9 | 1646.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-copy-out-publish | 3027.414 (3018.252–3046.495) | — | 168.5 | 1708.50 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-copy-out-leaf-publish | 3061.210 (3054.531–3231.267) | — | 166.6 | 1708.50 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-trusted-copy-out-leaf | 929.462 (915.171–932.052) | — | 548.7 | 1694.50 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-session-raw | 746.600 (743.462–748.604) | — | 683.1 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-plan | 3170.659 (3164.477–3176.883) | — | 160.9 | 2294.00 | 0.0 | 1638.5 / 2200.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-plan-heap | 3196.110 (3146.501–3259.437) | — | 159.6 | 2318.00 | 0.0 | 1638.5 / 2200.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-session | 2041.187 (2037.797–2058.410) | — | 249.9 | 40.00 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
