# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | 804.8 | — | 846.7 (1.05×) | 1526.8 (1.90×) | 1539.7 (1.91×) | 1038.1 (1.29×) | 1192.8 (1.48×) | 792.4 (0.98×) | 606.3 (0.75×) | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | 866.4 | — | 887.1 (1.02×) | 1836.7 (2.12×) | 1858.0 (2.14×) | — | 2708.1 (3.13×) | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | 16130.2 | 437136.3 (27.10×) | 6001.4 (0.37×) | 41587.1 (2.58×) | 41860.3 (2.60×) | — | 55210.6 (3.42×) | — | — | — | — |

## baseline: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | 774.9 | — | 812.5 (1.05×) | — | — | — | — | — | — | 693.5 (0.89×) | 1439.1 (1.86×) |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | 855.8 | — | 860.3 (1.01×) | — | — | — | — | — | — | 5464.4 (6.38×) | 6249.0 (7.30×) |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | 16058.7 | — | 5162.8 (0.32×) | — | — | — | — | — | — | 27182.6 (1.69×) | 17536.8 (1.09×) |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | sparse | decode | stock | 804.800 (801.280–807.643) | 1.00 | 186.3 | 1629.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | decode | generated-owned | 846.743 (838.558–855.895) | 1.05 | 177.0 | 1607.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | decode | slab | 1526.758 (1525.055–1549.005) | 1.90 | 98.2 | 991.75 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | sparse | decode | slab-native | 1539.735 (1518.759–1542.525) | 1.91 | 97.4 | 991.75 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | sparse | decode | slab-projected | 1038.090 (989.986–1052.579) | 1.29 | 144.4 | 626.25 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | decode | slab-lazy | 1192.791 (1176.301–1196.865) | 1.48 | 125.7 | 1004.25 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | sparse | decode | slab-session | 792.386 (792.014–793.191) | 0.98 | 189.2 | 40.00 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | sparse | decode | slab-session-projected | 606.328 (601.133–648.980) | 0.75 | 247.2 | 40.00 | 0.0 | 289.2 / 0.0 | 855.6 | 149.9 |
| candidate | full | detach | stock | 866.351 (860.534–876.611) | 1.00 | 173.0 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | detach | generated-owned | 887.108 (880.588–899.963) | 1.02 | 169.0 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | detach | slab | 1836.726 (1820.040–1842.072) | 2.12 | 81.6 | 1664.25 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | detach | slab-native | 1857.992 (1806.287–1922.440) | 2.14 | 80.7 | 1664.25 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | detach | slab-lazy | 2708.056 (2685.660–2724.060) | 3.13 | 55.4 | 3162.25 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| candidate | large | peak | stock | 16130.164 (16123.524–16663.271) | 1.00 | 3212.8 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | peak | stock-interop | 437136.328 (436258.516–442260.938) | 27.10 | 118.5 | 666098.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | peak | generated-owned | 6001.375 (5978.018–6698.928) | 0.37 | 8635.1 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | peak | slab | 41587.105 (41554.342–41797.788) | 2.58 | 1246.1 | 17.00 | 0.0 | 172771.8 / 172771.8 | 0.0 | 0.0 |
| candidate | large | peak | slab-native | 41860.271 (41832.344–41962.673) | 2.60 | 1238.0 | 17.00 | 0.0 | 172771.8 / 172771.8 | 0.0 | 0.0 |
| candidate | large | peak | slab-lazy | 55210.625 (52918.917–57847.095) | 3.42 | 938.6 | 17.00 | 0.0 | 45619.8 / 281827.8 | 0.0 | 51822.3 |
| baseline | sparse | decode | stock | 774.880 (773.299–779.097) | 1.00 | 193.5 | 1629.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | decode | generated-owned | 812.485 (806.871–822.860) | 1.05 | 184.5 | 1607.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | decode | native-view | 693.458 (686.060–726.985) | 0.89 | 216.2 | 144.00 | 1165.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | decode | native-eager | 1439.144 (1417.401–1444.165) | 1.86 | 104.2 | 1214.25 | 1141.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | detach | stock | 855.834 (848.660–868.499) | 1.00 | 175.2 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | detach | generated-owned | 860.280 (852.334–862.240) | 1.01 | 174.3 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | detach | native-view | 5464.445 (5435.130–5531.525) | 6.38 | 27.4 | 4019.75 | 1165.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | detach | native-eager | 6249.031 (6216.423–6250.913) | 7.30 | 24.0 | 5090.00 | 1141.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | peak | stock | 16058.693 (15991.473–16067.503) | 1.00 | 3227.1 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | peak | generated-owned | 5162.786 (5154.646–5378.630) | 0.32 | 10037.7 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | peak | native-view | 27182.597 (27182.004–28958.854) | 1.69 | 1906.5 | 1.00 | 409153.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | peak | native-eager | 17536.778 (17508.358–17638.363) | 1.09 | 2955.1 | 1.00 | 409129.5 | 0.0 / 0.0 | 0.0 | 0.0 |
