# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | 814.9 | — | — | 1613.2 (1.98×) | — | 1071.8 (1.32×) | 1259.8 (1.55×) | 888.7 (1.09×) | 702.3 (0.86×) | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | 2590.4 | — | — | 4581.6 (1.77×) | — | — | 6849.5 (2.64×) | 2609.5 (1.01×) | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | 2422.1 | — | — | 4077.6 (1.68×) | — | 1712.1 (0.71×) | 2280.4 (0.94×) | 2162.4 (0.89×) | 1436.4 (0.59×) | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | sparse | resident | stock | 814.918 (812.793–818.149) | 1.00 | 184.0 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | resident | slab | 1613.168 (1612.986–1614.978) | 1.98 | 92.9 | 1118.25 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | sparse | resident | slab-projected | 1071.833 (1053.652–1074.609) | 1.32 | 139.9 | 721.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | resident | slab-lazy | 1259.776 (1256.834–1271.464) | 1.55 | 119.0 | 1116.50 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | sparse | resident | slab-session | 888.710 (886.511–1367.583) | 1.09 | 168.7 | 151.25 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | sparse | resident | slab-session-projected | 702.330 (697.339–706.414) | 0.86 | 213.4 | 151.25 | 0.0 | 289.2 / 0.0 | 855.6 | 149.9 |
| candidate | holdout | resident | stock | 2590.366 (2568.856–2610.537) | 1.00 | 196.9 | 5310.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | resident | slab | 4581.642 (4568.807–4635.774) | 1.77 | 111.3 | 3425.75 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout | resident | slab-lazy | 6849.507 (6797.398–6875.723) | 2.64 | 74.5 | 7914.50 | 0.0 | 528.5 / 2471.0 | 0.0 | 510.0 |
| candidate | holdout | resident | slab-session | 2609.542 (2584.129–2737.215) | 1.01 | 195.4 | 1197.00 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
| candidate | holdout-sparse | resident | stock | 2422.096 (2397.882–2424.039) | 1.00 | 210.6 | 5310.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab | 4077.613 (4039.576–4108.004) | 1.68 | 125.1 | 2460.50 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab-projected | 1712.114 (1675.676–1921.641) | 0.71 | 297.9 | 721.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab-lazy | 2280.449 (2244.879–2284.112) | 0.94 | 223.7 | 1839.75 | 0.0 | 528.5 / 528.5 | 0.0 | 510.0 |
| candidate | holdout-sparse | resident | slab-session | 2162.378 (2158.005–2166.507) | 0.89 | 235.9 | 151.25 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
| candidate | holdout-sparse | resident | slab-session-projected | 1436.391 (1434.955–1446.847) | 0.59 | 355.1 | 151.25 | 0.0 | 528.5 / 0.0 | 2296.1 | 510.0 |
