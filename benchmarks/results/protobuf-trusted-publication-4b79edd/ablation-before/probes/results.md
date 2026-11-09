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
| full | decode | 852.7 | — | — | 1050.2 (1.23×) | — | — | — | 935.9 (1.10×) | — | — | — | 7.6 (0.01×) | 20.2 (0.02×) | 15.4 (0.02×) | 9.4 (0.01×) | 32.8 (0.04×) | 344.9 (0.40×) | 314.7 (0.37×) | 328.3 (0.38×) | 375.5 (0.44×) | 363.4 (0.43×) | 882.5 (1.04×) | 979.3 (1.15×) | 944.5 (1.11×) | 501.2 (0.59×) | 841.9 (0.99×) | 649.4 (0.76×) | 366.5 (0.43×) | 373.1 (0.44×) | 920.0 (1.08×) | 959.2 (1.12×) | 373.3 (0.44×) | 316.7 (0.37×) | 1271.0 (1.49×) | 1029.5 (1.21×) | 863.3 (1.01×) |
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
| holdout | decode | — | — | — | — | — | — | — | — | — | — | — | 7.5 | 21.1 | 14.1 | 8.5 | 31.0 | 775.3 | 793.3 | 1049.0 | 1124.5 | 1101.9 | 2838.2 | 3323.3 | 3310.9 | 1048.3 | 2699.6 | 1084.1 | 1024.8 | 1001.8 | 3357.0 | 3545.7 | 1063.1 | 864.0 | 4341.3 | 3473.3 | 2268.8 |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | full | decode | stock | 852.699 (820.980–1467.711) | 1.00 | 175.8 | 1629.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | slab | 1050.231 (949.119–1166.416) | 1.23 | 142.7 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | decode | slab-session | 935.897 (906.523–1050.908) | 1.10 | 160.2 | 40.00 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | full | decode | probe-empty | 7.561 (7.410–10.317) | 0.01 | 19825.6 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-lease | 20.186 (19.190–31.229) | 0.02 | 7426.3 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-transition | 15.355 (14.422–16.451) | 0.02 | 9762.6 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-leaf-call | 9.360 (8.877–12.922) | 0.01 | 16015.9 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-pin-only | 32.769 (30.930–35.763) | 0.04 | 4574.7 | 24.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-native-leaf | 344.903 (327.092–366.704) | 0.40 | 434.6 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-native | 314.671 (285.472–387.791) | 0.37 | 476.4 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-alloc | 328.273 (323.355–347.924) | 0.38 | 456.7 | 736.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-pin | 375.510 (357.949–391.013) | 0.44 | 399.2 | 760.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-trim | 363.360 (350.147–374.128) | 0.43 | 412.6 | 760.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-verify | 882.549 (876.890–1673.942) | 1.04 | 169.9 | 760.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-publish-generic | 979.321 (851.337–1461.780) | 1.15 | 153.1 | 808.00 | 0.0 | 547.2 / 728.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-publish | 944.527 (932.124–1003.465) | 1.11 | 158.7 | 808.00 | 0.0 | 547.2 / 728.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-trusted | 501.180 (416.351–607.567) | 0.59 | 299.1 | 808.00 | 0.0 | 547.2 / 728.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-internal | 841.918 (836.960–897.133) | 0.99 | 178.1 | 840.00 | 0.0 | 547.2 / 728.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-heap-pin2 | 649.447 (394.833–655.140) | 0.76 | 230.8 | 784.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-heap-copy-in | 366.517 (351.984–484.726) | 0.43 | 409.0 | 760.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-copy-out | 373.149 (327.381–375.568) | 0.44 | 401.7 | 555.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-copy-out-publish | 920.027 (878.230–939.490) | 1.08 | 162.9 | 603.25 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | decode | probe-copy-out-leaf-publish | 959.163 (891.657–1264.722) | 1.12 | 156.3 | 603.25 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | decode | probe-trusted-copy-out-leaf | 373.296 (355.842–408.453) | 0.44 | 401.6 | 603.25 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | decode | probe-session-raw | 316.740 (307.599–357.559) | 0.37 | 473.3 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-plan | 1270.991 (938.288–1680.490) | 1.49 | 117.9 | 808.00 | 0.0 | 547.2 / 728.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-plan-heap | 1029.494 (1020.373–1033.240) | 1.21 | 145.6 | 832.00 | 0.0 | 547.2 / 728.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-session | 863.314 (856.491–879.346) | 1.01 | 173.6 | 40.00 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | holdout | decode | probe-empty | 7.455 (7.427–7.909) | — | 68414.3 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-lease | 21.060 (19.720–22.476) | — | 24218.2 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-transition | 14.131 (13.209–15.307) | — | 36092.3 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-leaf-call | 8.481 (8.270–8.577) | — | 60139.0 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-pin-only | 30.965 (30.803–37.046) | — | 16471.4 | 24.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-native-leaf | 775.260 (748.180–850.780) | — | 657.9 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-native | 793.317 (778.711–944.107) | — | 642.9 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-alloc | 1049.044 (987.010–1495.892) | — | 486.2 | 2208.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-pin | 1124.542 (1063.031–1630.262) | — | 453.5 | 2232.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-trim | 1101.860 (1005.756–1405.717) | — | 462.9 | 2232.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-verify | 2838.161 (2587.870–4458.426) | — | 179.7 | 2232.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-publish-generic | 3323.314 (3204.614–4444.671) | — | 153.5 | 2294.00 | 0.0 | 1638.5 / 2200.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-publish | 3310.888 (3214.684–5230.753) | — | 154.0 | 2294.00 | 0.0 | 1638.5 / 2200.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-trusted | 1048.280 (1041.035–1067.900) | — | 486.5 | 2280.00 | 0.0 | 1638.5 / 2200.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-internal | 2699.582 (2207.066–2838.744) | — | 188.9 | 2312.00 | 0.0 | 1638.5 / 2200.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-heap-pin2 | 1084.083 (1050.369–1177.628) | — | 470.5 | 2256.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-heap-copy-in | 1024.780 (993.869–1086.721) | — | 497.7 | 2232.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-copy-out | 1001.837 (997.704–1011.851) | — | 509.1 | 1646.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-copy-out-publish | 3356.962 (3307.659–3389.487) | — | 151.9 | 1708.50 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-copy-out-leaf-publish | 3545.749 (3404.694–3862.237) | — | 143.8 | 1708.50 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-trusted-copy-out-leaf | 1063.100 (1015.129–1084.614) | — | 479.8 | 1694.50 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-session-raw | 863.952 (777.248–1401.074) | — | 590.3 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-plan | 4341.316 (3384.100–5676.759) | — | 117.5 | 2294.00 | 0.0 | 1638.5 / 2200.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-plan-heap | 3473.343 (3466.869–3758.334) | — | 146.8 | 2318.00 | 0.0 | 1638.5 / 2200.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-session | 2268.788 (2264.112–2723.690) | — | 224.8 | 40.00 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
