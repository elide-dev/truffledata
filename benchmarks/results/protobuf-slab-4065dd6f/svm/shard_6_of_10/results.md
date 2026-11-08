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
| sparse | interpret | 9.7 | 164.1 (16.94×) | 38.6 (3.99×) | 53.1 (5.48×) | 53.0 (5.48×) | 57.2 (5.91×) | 62.2 (6.42×) | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | 133.6 | 2880.2 (21.56×) | 60.4 (0.45×) | 337.3 (2.52×) | 338.9 (2.54×) | — | 392.8 (2.94×) | — | — | — | — |
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
| sparse | interpret | 10.4 | — | 42.4 (4.09×) | — | — | — | — | — | — | 62.1 (5.99×) | 58.0 (5.59×) |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | 133.5 | — | 52.1 (0.39×) | — | — | — | — | — | — | 192.2 (1.44×) | 128.2 (0.96×) |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | sparse | interpret | stock | 9.684 (9.657–26.288) | 1.00 | 15479.7 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | interpret | stock-interop | 164.057 (163.528–166.249) | 16.94 | 913.7 | 193.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | interpret | generated-owned | 38.619 (36.398–115.063) | 3.99 | 3881.7 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | interpret | slab | 53.055 (53.000–53.602) | 5.48 | 2825.5 | 2.00 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | sparse | interpret | slab-native | 53.049 (52.894–53.645) | 5.48 | 2825.8 | 2.00 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | sparse | interpret | slab-projected | 57.220 (57.078–60.895) | 5.91 | 2619.8 | 2.00 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | interpret | slab-lazy | 62.186 (57.553–65.706) | 6.42 | 2410.6 | 2.00 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | holdout | peak | stock | 133.595 (133.228–133.923) | 1.00 | 3817.7 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | peak | stock-interop | 2880.174 (2877.322–11656.504) | 21.56 | 177.1 | 5319.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | peak | generated-owned | 60.425 (56.185–61.047) | 0.45 | 8440.8 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | peak | slab | 337.255 (336.476–535.348) | 2.52 | 1512.3 | 17.00 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout | peak | slab-native | 338.859 (337.617–341.884) | 2.54 | 1505.1 | 17.00 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout | peak | slab-lazy | 392.760 (392.483–426.928) | 2.94 | 1298.6 | 17.00 | 0.0 | 528.5 / 2471.0 | 0.0 | 510.0 |
| baseline | sparse | interpret | stock | 10.373 (9.709–11.100) | 1.00 | 14451.9 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | interpret | generated-owned | 42.442 (39.547–43.167) | 4.09 | 3532.0 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | interpret | native-view | 62.092 (62.031–62.095) | 5.99 | 2414.3 | 2.00 | 1165.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | interpret | native-eager | 58.004 (57.870–60.718) | 5.59 | 2584.4 | 2.00 | 1141.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | peak | stock | 133.520 (132.486–134.849) | 1.00 | 3819.9 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | peak | generated-owned | 52.137 (51.936–59.001) | 0.39 | 9782.5 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | peak | native-view | 192.236 (192.011–192.386) | 1.44 | 2653.1 | 1.00 | 3315.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | peak | native-eager | 128.250 (127.694–139.011) | 0.96 | 3976.9 | 1.00 | 3291.0 | 0.0 / 0.0 | 0.0 | 0.0 |
