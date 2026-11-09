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
| sparse | peak | 12.8 | 168.3 (13.16×) | 11.6 (0.91×) | 18.5 (1.45×) | 14.9 (1.16×) | 34.2 (2.68×) | 31.7 (2.48×) | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | 2568.8 | — | 3231.3 (1.26×) | 1240.8 (0.48×) | 1225.4 (0.48×) | 2255.8 (0.88×) | 5087.0 (1.98×) | 2583.5 (1.01×) | 1655.7 (0.64×) | — | — |
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
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | 13.8 | — | 11.5 (0.83×) | 16.6 (1.20×) | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | sparse | peak | stock | 12.789 (12.609–17.289) | 1.00 | 11721.7 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | peak | stock-interop | 168.333 (157.072–171.469) | 13.16 | 890.5 | 192.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | peak | generated-owned | 11.642 (11.115–14.990) | 0.91 | 12876.8 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | peak | slab | 18.515 (17.838–18.782) | 1.45 | 8096.3 | 1.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | peak | slab-native | 14.889 (14.728–16.587) | 1.16 | 10068.2 | 1.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | peak | slab-projected | 34.248 (29.614–37.062) | 2.68 | 4377.1 | 1.00 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | peak | slab-lazy | 31.652 (31.603–41.811) | 2.48 | 4736.0 | 1.00 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | holdout-sparse | resident | stock | 2568.823 (2568.307–2710.513) | 1.00 | 198.5 | 5310.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | generated-owned | 3231.282 (2951.247–3273.186) | 1.26 | 157.8 | 5427.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab | 1240.781 (1211.021–1376.214) | 0.48 | 411.1 | 2466.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab-native | 1225.382 (1168.420–1230.873) | 0.48 | 416.2 | 2466.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab-projected | 2255.803 (1915.731–13484.905) | 0.88 | 226.1 | 689.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab-lazy | 5086.950 (3471.800–28073.344) | 1.98 | 100.3 | 1807.75 | 0.0 | 528.5 / 528.5 | 0.0 | 510.0 |
| candidate | holdout-sparse | resident | slab-session | 2583.455 (2382.448–2844.520) | 1.01 | 197.4 | 151.25 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
| candidate | holdout-sparse | resident | slab-session-projected | 1655.671 (1631.279–1908.216) | 0.64 | 308.1 | 151.25 | 0.0 | 528.5 / 0.0 | 2296.1 | 510.0 |
| baseline | sparse | peak | stock | 13.793 (13.125–21.429) | 1.00 | 10868.4 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | peak | generated-owned | 11.456 (11.102–11.765) | 0.83 | 13085.4 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | peak | slab | 16.578 (15.178–19.305) | 1.20 | 9042.6 | 1.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
