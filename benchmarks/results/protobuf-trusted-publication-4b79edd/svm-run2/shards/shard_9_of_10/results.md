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
| full | resident | 1033.3 | — | 963.0 (0.93×) | 773.8 (0.75×) | 895.7 (0.87×) | — | 3157.6 (3.06×) | 1171.8 (1.13×) | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | 239350.7 | — | 344268.6 (1.44×) | 101531.2 (0.42×) | 100601.0 (0.42×) | — | 171821.6 (0.72×) | 231550.8 (0.97×) | — | — | — |
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
| full | resident | 913.9 | — | 995.2 (1.09×) | 1297.3 (1.42×) | — | — | — | 1195.2 (1.31×) | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | 277279.4 | — | 328976.5 (1.19×) | 290918.5 (1.05×) | — | — | — | 225047.3 (0.81×) | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | full | resident | stock | 1033.324 (942.722–1056.380) | 1.00 | 145.1 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | resident | generated-owned | 962.996 (911.243–1333.071) | 0.93 | 155.7 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | resident | slab | 773.843 (717.733–21683.158) | 0.75 | 193.7 | 1440.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | resident | slab-native | 895.693 (818.991–18531.803) | 0.87 | 167.4 | 1440.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | resident | slab-lazy | 3157.577 (3006.483–3233.091) | 3.06 | 47.5 | 3047.25 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| candidate | full | resident | slab-session | 1171.821 (1166.296–1279.503) | 1.13 | 127.9 | 718.25 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | large | decode | stock | 239350.665 (224415.701–244422.371) | 1.00 | 216.5 | 433008.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | decode | generated-owned | 344268.612 (277265.393–365678.425) | 1.44 | 150.5 | 460179.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | decode | slab | 101531.225 (99550.289–111740.212) | 0.42 | 510.4 | 189880.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| candidate | large | decode | slab-native | 100600.971 (100575.717–104880.119) | 0.42 | 515.1 | 189880.00 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| candidate | large | decode | slab-lazy | 171821.594 (170646.528–230158.297) | 0.72 | 301.6 | 116107.75 | 0.0 | 45619.8 / 45619.8 | 0.0 | 51822.3 |
| candidate | large | decode | slab-session | 231550.772 (224262.746–308440.526) | 0.97 | 223.8 | 40.00 | 0.0 | 172771.8 / 0.0 | 207545.1 | 0.0 |
| baseline | full | resident | stock | 913.887 (894.848–997.659) | 1.00 | 164.0 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | resident | generated-owned | 995.158 (966.910–1660.725) | 1.09 | 150.6 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | resident | slab | 1297.303 (1253.268–1415.083) | 1.42 | 115.6 | 1440.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | resident | slab-session | 1195.180 (1191.489–1818.436) | 1.31 | 125.4 | 718.25 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| baseline | large | decode | stock | 277279.406 (261728.781–345097.256) | 1.00 | 186.9 | 433008.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | decode | generated-owned | 328976.513 (328527.981–425447.969) | 1.19 | 157.5 | 460179.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | decode | slab | 290918.458 (280230.630–312764.052) | 1.05 | 178.1 | 226294.50 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| baseline | large | decode | slab-session | 225047.286 (219274.035–226644.379) | 0.81 | 230.3 | 40.00 | 0.0 | 172771.8 / 0.0 | 207545.1 | 0.0 |
