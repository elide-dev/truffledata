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
| full | heap | 859.6 | — | 868.6 (1.01×) | 1901.1 (2.21×) | 1889.7 (2.20×) | — | 2826.4 (3.29×) | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | 225560.6 | — | 280767.2 (1.24×) | 450276.9 (2.00×) | 462061.1 (2.05×) | — | 792198.3 (3.51×) | 262523.9 (1.16×) | — | — | — |
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
| full | heap | 868.2 | — | 844.9 (0.97×) | — | — | — | — | — | — | 1289.8 (1.49×) | 1791.0 (2.06×) |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | 264644.3 | — | 267473.9 (1.01×) | — | — | — | — | — | — | 369065.2 (1.39×) | 553035.3 (2.09×) |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | full | heap | stock | 859.588 (859.470–864.474) | 1.00 | 174.4 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | heap | generated-owned | 868.597 (860.423–871.468) | 1.01 | 172.6 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | heap | slab | 1901.138 (1873.398–1939.047) | 2.21 | 78.9 | 1907.50 | 0.0 | 547.2 / 867.4 | 0.0 | 0.0 |
| candidate | full | heap | slab-native | 1889.699 (1873.489–1922.010) | 2.20 | 79.3 | 1907.50 | 0.0 | 547.2 / 867.4 | 0.0 | 0.0 |
| candidate | full | heap | slab-lazy | 2826.374 (2824.772–2851.213) | 3.29 | 53.0 | 3370.25 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| candidate | large | resident | stock | 225560.625 (222304.297–248889.509) | 1.00 | 229.7 | 433009.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | resident | generated-owned | 280767.240 (264825.208–282603.328) | 1.24 | 184.6 | 460180.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | resident | slab | 450276.875 (447600.391–465832.578) | 2.00 | 115.1 | 300819.75 | 0.0 | 172771.8 / 172771.8 | 0.0 | 0.0 |
| candidate | large | resident | slab-native | 462061.094 (452266.797–462310.234) | 2.05 | 112.2 | 300819.75 | 0.0 | 172771.8 / 172771.8 | 0.0 | 0.0 |
| candidate | large | resident | slab-lazy | 792198.281 (753932.198–828324.688) | 3.51 | 65.4 | 853061.25 | 0.0 | 45619.8 / 281827.8 | 0.0 | 51822.3 |
| candidate | large | resident | slab-session | 262523.854 (261733.646–264915.786) | 1.16 | 197.4 | 91383.25 | 0.0 | 172771.8 / 0.0 | 207545.1 | 0.0 |
| baseline | full | heap | stock | 868.205 (865.465–875.479) | 1.00 | 172.7 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | heap | generated-owned | 844.898 (840.488–849.073) | 0.97 | 177.4 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | heap | native-view | 1289.845 (1271.895–15888.163) | 1.49 | 116.2 | 1339.50 | 1165.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | heap | native-eager | 1791.036 (1786.926–1793.458) | 2.06 | 83.7 | 1631.75 | 1141.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | resident | stock | 264644.323 (237207.768–270836.042) | 1.00 | 195.8 | 433009.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | resident | generated-owned | 267473.854 (260446.478–267798.177) | 1.01 | 193.7 | 460180.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | resident | native-view | 369065.250 (364898.750–408084.688) | 1.39 | 140.4 | 263984.00 | 409153.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | resident | native-eager | 553035.312 (550129.688–562013.958) | 2.09 | 93.7 | 389261.25 | 409129.5 | 0.0 / 0.0 | 0.0 | 0.0 |
