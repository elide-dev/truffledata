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
| full | decode | 1038.7 | — | — | 552.8 (0.53×) | — | — | — | 1109.9 (1.07×) | — | — | — | 9.7 (0.01×) | 21.9 (0.02×) | 15.0 (0.01×) | 8.4 (0.01×) | 31.4 (0.03×) | 335.6 (0.32×) | 355.3 (0.34×) | 451.0 (0.43×) | 410.6 (0.40×) | 477.6 (0.46×) | 1526.1 (1.47×) | 1383.5 (1.33×) | 1499.6 (1.44×) | 598.8 (0.58×) | 1366.5 (1.32×) | 603.6 (0.58×) | 512.8 (0.49×) | 368.3 (0.35×) | 947.9 (0.91×) | 1096.5 (1.06×) | 618.8 (0.60×) | 365.9 (0.35×) | 631.3 (0.61×) | 535.0 (0.52×) | 941.8 (0.91×) |
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
| holdout | decode | — | — | — | — | — | — | — | — | — | — | — | 10.6 | 28.1 | 12.9 | 10.2 | 32.2 | 896.2 | 798.1 | 1043.4 | 2347.1 | 1192.2 | 3158.5 | 4893.2 | 4307.9 | 1357.0 | 2361.0 | 1280.5 | 1459.5 | 1527.0 | 3875.9 | 3815.0 | 1055.7 | 1160.0 | 1096.7 | 1220.3 | 2288.1 |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | full | decode | stock | 1038.745 (990.885–1219.500) | 1.00 | 144.3 | 1629.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | slab | 552.790 (421.395–723.673) | 0.53 | 271.2 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | decode | slab-session | 1109.909 (839.267–1824.229) | 1.07 | 135.1 | 40.00 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | full | decode | probe-empty | 9.680 (6.947–10.143) | 0.01 | 15486.4 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-lease | 21.864 (20.751–26.933) | 0.02 | 6856.3 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-transition | 15.002 (14.151–16.785) | 0.01 | 9992.1 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-leaf-call | 8.383 (7.385–8.642) | 0.01 | 17882.4 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-pin-only | 31.372 (29.491–34.831) | 0.03 | 4778.3 | 24.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-native-leaf | 335.635 (321.296–392.171) | 0.32 | 446.6 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-native | 355.286 (271.972–945.863) | 0.34 | 421.9 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-alloc | 451.014 (372.236–711.534) | 0.43 | 332.4 | 736.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-pin | 410.584 (362.198–415.435) | 0.40 | 365.1 | 760.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-trim | 477.559 (411.655–708.459) | 0.46 | 313.9 | 760.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-verify | 1526.078 (929.572–1783.106) | 1.47 | 98.2 | 760.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-publish-generic | 1383.515 (1140.039–1696.001) | 1.33 | 108.4 | 808.00 | 0.0 | 547.2 / 728.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-publish | 1499.626 (1010.379–1519.295) | 1.44 | 100.0 | 808.00 | 0.0 | 547.2 / 728.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-trusted | 598.840 (376.616–602.194) | 0.58 | 250.3 | 808.00 | 0.0 | 547.2 / 728.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-internal | 1366.471 (1355.276–1764.035) | 1.32 | 109.7 | 840.00 | 0.0 | 547.2 / 728.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-heap-pin2 | 603.579 (423.145–7741.458) | 0.58 | 248.4 | 784.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-heap-copy-in | 512.755 (378.145–549.883) | 0.49 | 292.4 | 760.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-copy-out | 368.256 (332.880–469.122) | 0.35 | 407.1 | 555.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-copy-out-publish | 947.909 (844.122–1494.173) | 0.91 | 158.1 | 603.25 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | decode | probe-copy-out-leaf-publish | 1096.496 (827.270–1217.044) | 1.06 | 136.7 | 603.25 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | decode | probe-trusted-copy-out-leaf | 618.812 (360.647–759.935) | 0.60 | 242.2 | 603.25 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | decode | probe-session-raw | 365.942 (326.428–470.274) | 0.35 | 409.6 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-plan | 631.264 (467.573–753.694) | 0.61 | 237.5 | 808.00 | 0.0 | 547.2 / 728.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-plan-heap | 534.969 (486.799–668.790) | 0.52 | 280.2 | 832.00 | 0.0 | 547.2 / 728.0 | 0.0 | 0.0 |
| candidate | full | decode | probe-session | 941.798 (861.921–1240.321) | 0.91 | 159.2 | 40.00 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | holdout | decode | probe-empty | 10.579 (6.845–10.913) | — | 48209.4 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-lease | 28.132 (18.093–35.623) | — | 18129.7 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-transition | 12.899 (12.892–14.034) | — | 39539.3 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-leaf-call | 10.202 (8.778–11.283) | — | 49995.0 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-pin-only | 32.217 (28.517–94.299) | — | 15831.1 | 24.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-native-leaf | 896.208 (737.867–1181.736) | — | 569.1 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-native | 798.142 (697.982–1090.746) | — | 639.0 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-alloc | 1043.444 (1042.450–1511.419) | — | 488.8 | 2208.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-pin | 2347.110 (1096.868–3420.285) | — | 217.3 | 2232.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-trim | 1192.248 (1124.202–1478.694) | — | 427.8 | 2232.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-verify | 3158.541 (2646.539–4881.103) | — | 161.5 | 2232.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-publish-generic | 4893.242 (4650.565–6809.918) | — | 104.2 | 2294.00 | 0.0 | 1638.5 / 2200.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-publish | 4307.861 (3284.733–4428.459) | — | 118.4 | 2294.00 | 0.0 | 1638.5 / 2200.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-trusted | 1356.972 (1066.009–1392.044) | — | 375.9 | 2280.00 | 0.0 | 1638.5 / 2200.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-internal | 2361.018 (2203.063–5486.822) | — | 216.0 | 2312.00 | 0.0 | 1638.5 / 2200.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-heap-pin2 | 1280.496 (1155.958–1479.037) | — | 398.3 | 2256.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-heap-copy-in | 1459.497 (1439.726–1716.983) | — | 349.5 | 2232.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-copy-out | 1526.958 (1125.266–1759.821) | — | 334.0 | 1646.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-copy-out-publish | 3875.857 (3564.702–5309.797) | — | 131.6 | 1708.50 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-copy-out-leaf-publish | 3815.014 (3337.906–3916.441) | — | 133.7 | 1708.50 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-trusted-copy-out-leaf | 1055.748 (1032.354–1772.990) | — | 483.1 | 1694.50 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-session-raw | 1159.961 (871.994–1288.730) | — | 439.7 | 0.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-plan | 1096.723 (1072.617–1362.073) | — | 465.0 | 2280.00 | 0.0 | 1638.5 / 2200.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-plan-heap | 1220.286 (1051.641–1711.380) | — | 418.0 | 2304.00 | 0.0 | 1638.5 / 2200.0 | 0.0 | 0.0 |
| candidate | holdout | decode | probe-session | 2288.065 (2061.089–3456.720) | — | 222.9 | 40.00 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
