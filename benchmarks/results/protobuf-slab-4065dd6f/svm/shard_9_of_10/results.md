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
| full | resident | 859.2 | — | 870.9 (1.01×) | 1844.1 (2.15×) | 1860.1 (2.16×) | — | 2756.4 (3.21×) | 1106.3 (1.29×) | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | 229460.3 | — | 269102.8 (1.17×) | 415512.2 (1.81×) | 414159.5 (1.80×) | — | 157123.4 (0.68×) | 211339.7 (0.92×) | — | — | — |
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
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | 848.4 | — | 855.4 (1.01×) | — | — | — | — | — | — | 1253.6 (1.48×) | 1750.4 (2.06×) |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | 223389.7 | — | 253644.1 (1.14×) | — | — | — | — | — | — | 268012.4 (1.20×) | 459139.6 (2.06×) |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | full | resident | stock | 859.222 (858.423–11952.909) | 1.00 | 174.5 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | resident | generated-owned | 870.868 (863.229–872.237) | 1.01 | 172.1 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | resident | slab | 1844.098 (1843.379–20136.000) | 2.15 | 81.3 | 1664.25 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | resident | slab-native | 1860.143 (1831.896–21944.030) | 2.16 | 80.6 | 1664.25 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | full | resident | slab-lazy | 2756.375 (2747.826–2756.490) | 3.21 | 54.4 | 3162.25 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| candidate | full | resident | slab-session | 1106.302 (1095.689–1168.544) | 1.29 | 135.5 | 718.25 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | large | decode | stock | 229460.259 (214654.910–231432.223) | 1.00 | 225.8 | 433008.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | decode | generated-owned | 269102.759 (265098.052–271224.513) | 1.17 | 192.6 | 460179.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | decode | slab | 415512.180 (414528.328–416581.047) | 1.81 | 124.7 | 209482.25 | 0.0 | 172771.8 / 172771.8 | 0.0 | 0.0 |
| candidate | large | decode | slab-native | 414159.461 (412639.125–415051.398) | 1.80 | 125.1 | 209482.25 | 0.0 | 172771.8 / 172771.8 | 0.0 | 0.0 |
| candidate | large | decode | slab-lazy | 157123.366 (154672.048–160048.178) | 0.68 | 329.8 | 116139.75 | 0.0 | 45619.8 / 45619.8 | 0.0 | 51822.3 |
| candidate | large | decode | slab-session | 211339.656 (210059.316–212378.348) | 0.92 | 245.2 | 40.00 | 0.0 | 172771.8 / 0.0 | 207545.1 | 0.0 |
| baseline | full | resident | stock | 848.438 (837.596–851.773) | 1.00 | 176.7 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | resident | generated-owned | 855.415 (853.294–857.974) | 1.01 | 175.2 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | resident | native-view | 1253.630 (1226.912–12403.247) | 1.48 | 119.6 | 1131.50 | 1165.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | resident | native-eager | 1750.440 (1746.183–1751.893) | 2.06 | 85.6 | 1423.75 | 1141.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | decode | stock | 223389.652 (223187.555–240141.746) | 1.00 | 232.0 | 433008.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | decode | generated-owned | 253644.125 (246434.116–254185.290) | 1.14 | 204.3 | 460179.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | decode | native-view | 268012.385 (209327.020–268037.984) | 1.20 | 193.4 | 144.00 | 409153.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | decode | native-eager | 459139.586 (453069.586–459371.391) | 2.06 | 112.9 | 316593.75 | 409129.5 | 0.0 / 0.0 | 0.0 | 0.0 |
