# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | 789.4 | — | 815.8 (1.03×) | 976.6 (1.24×) | 1020.2 (1.29×) | 980.1 (1.24×) | 1207.5 (1.53×) | 791.3 (1.00×) | 603.1 (0.76×) | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | 848.7 | — | 853.5 (1.01×) | 1301.4 (1.53×) | 1294.5 (1.53×) | — | 2804.7 (3.30×) | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | 16260.3 | 436423.0 (26.84×) | 6201.7 (0.38×) | 23333.3 (1.43×) | 23407.6 (1.44×) | — | 36328.7 (2.23×) | — | — | — | — |

## baseline: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | 782.7 | — | 864.6 (1.10×) | 980.2 (1.25×) | 969.4 (1.24×) | 1040.2 (1.33×) | 1259.2 (1.61×) | 793.9 (1.01×) | 575.2 (0.73×) | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | 853.3 | — | 884.0 (1.04×) | 1277.1 (1.50×) | 1283.0 (1.50×) | — | 2828.5 (3.31×) | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | 16212.3 | 434992.3 (26.83×) | 6452.8 (0.40×) | 41825.8 (2.58×) | 42159.3 (2.60×) | — | 52655.6 (3.25×) | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | sparse | decode | stock | 789.447 (779.108–940.996) | 1.00 | 189.9 | 1629.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | decode | generated-owned | 815.839 (811.312–821.964) | 1.03 | 183.7 | 1607.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | decode | slab | 976.620 (966.584–983.976) | 1.24 | 153.5 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | decode | slab-native | 1020.237 (998.458–1296.964) | 1.29 | 146.9 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | decode | slab-projected | 980.120 (974.798–988.560) | 1.24 | 152.9 | 594.25 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | decode | slab-lazy | 1207.527 (1180.931–1219.703) | 1.53 | 124.1 | 972.25 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | sparse | decode | slab-session | 791.342 (789.209–796.290) | 1.00 | 189.4 | 40.00 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | sparse | decode | slab-session-projected | 603.092 (591.690–608.665) | 0.76 | 248.6 | 40.00 | 0.0 | 289.2 / 0.0 | 855.6 | 149.9 |
| candidate | full | detach | stock | 848.676 (842.202–854.555) | 1.00 | 176.6 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | detach | generated-owned | 853.495 (846.558–857.466) | 1.01 | 175.6 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | detach | slab | 1301.358 (1293.631–1359.027) | 1.53 | 115.2 | 1480.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | detach | slab-native | 1294.494 (1290.672–1301.882) | 1.53 | 115.8 | 1480.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | detach | slab-lazy | 2804.688 (2785.256–2810.323) | 3.30 | 53.4 | 3087.25 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| candidate | large | peak | stock | 16260.338 (16128.405–16273.262) | 1.00 | 3187.0 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | peak | stock-interop | 436422.969 (434114.766–439505.391) | 26.84 | 118.7 | 666098.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | peak | generated-owned | 6201.665 (6114.102–6487.577) | 0.38 | 8356.2 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | peak | slab | 23333.298 (23224.053–23490.210) | 1.43 | 2221.0 | 17.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| candidate | large | peak | slab-native | 23407.645 (23110.823–23481.721) | 1.44 | 2213.9 | 17.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| candidate | large | peak | slab-lazy | 36328.743 (35973.892–38182.973) | 2.23 | 1426.5 | 17.00 | 0.0 | 45619.8 / 281827.8 | 0.0 | 51822.3 |
| baseline | sparse | decode | stock | 782.742 (776.709–789.389) | 1.00 | 191.5 | 1629.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | decode | generated-owned | 864.649 (850.325–900.058) | 1.10 | 173.4 | 1607.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | decode | slab | 980.233 (974.862–1018.527) | 1.25 | 152.9 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | decode | slab-native | 969.383 (963.444–979.774) | 1.24 | 154.6 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | decode | slab-projected | 1040.192 (1022.083–1054.556) | 1.33 | 144.1 | 594.25 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| baseline | sparse | decode | slab-lazy | 1259.161 (1239.443–1282.422) | 1.61 | 119.1 | 972.25 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| baseline | sparse | decode | slab-session | 793.928 (780.902–51956.895) | 1.01 | 188.8 | 40.00 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| baseline | sparse | decode | slab-session-projected | 575.249 (573.487–602.548) | 0.73 | 260.6 | 40.00 | 0.0 | 289.2 / 0.0 | 855.6 | 149.9 |
| baseline | full | detach | stock | 853.256 (838.134–876.392) | 1.00 | 175.7 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | detach | generated-owned | 883.974 (883.048–899.235) | 1.04 | 169.6 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | detach | slab | 1277.120 (1271.796–1291.741) | 1.50 | 117.4 | 1480.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | detach | slab-native | 1283.014 (1266.914–1286.482) | 1.50 | 116.8 | 1480.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | detach | slab-lazy | 2828.515 (2794.256–3038.012) | 3.31 | 53.0 | 3087.25 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| baseline | large | peak | stock | 16212.287 (16071.445–16359.580) | 1.00 | 3196.5 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | peak | stock-interop | 434992.266 (434877.891–435472.891) | 26.83 | 119.1 | 666098.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | peak | generated-owned | 6452.770 (6330.917–6513.509) | 0.40 | 8031.0 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | peak | slab | 41825.798 (41630.855–42120.321) | 2.58 | 1239.0 | 17.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| baseline | large | peak | slab-native | 42159.293 (41973.914–42685.321) | 2.60 | 1229.2 | 17.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| baseline | large | peak | slab-lazy | 52655.552 (51956.431–53195.865) | 3.25 | 984.2 | 17.00 | 0.0 | 45619.8 / 281827.8 | 0.0 | 51822.3 |
