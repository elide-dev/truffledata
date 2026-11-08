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
| full | decode | 790.8 | — | 825.2 (1.04×) | 972.2 (1.23×) | 977.5 (1.24×) | — | 1209.8 (1.53×) | 783.2 (0.99×) | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | 12.2 | 139.3 (11.44×) | 9.7 (0.80×) | 20.9 (1.72×) | 20.7 (1.70×) | 26.9 (2.21×) | 25.8 (2.12×) | — | — | — | — |
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
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | 788.1 | — | 835.0 (1.06×) | 978.4 (1.24×) | 972.4 (1.23×) | — | 1240.3 (1.57×) | 794.0 (1.01×) | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | 11.9 | 137.3 (11.56×) | 9.7 (0.82×) | 30.3 (2.55×) | 29.6 (2.49×) | 33.3 (2.80×) | 33.9 (2.85×) | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | full | decode | stock | 790.838 (787.502–791.726) | 1.00 | 189.6 | 1629.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | generated-owned | 825.242 (817.703–846.377) | 1.04 | 181.7 | 1607.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | slab | 972.152 (969.829–981.753) | 1.23 | 154.2 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | decode | slab-native | 977.476 (968.352–986.137) | 1.24 | 153.4 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | decode | slab-lazy | 1209.795 (1199.564–1211.262) | 1.53 | 123.9 | 972.25 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | full | decode | slab-session | 783.230 (774.525–828.806) | 0.99 | 191.4 | 40.00 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | holdout-sparse | peak | stock | 12.177 (11.665–13.811) | 1.00 | 41885.7 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout-sparse | peak | stock-interop | 139.326 (138.766–245.505) | 11.44 | 3660.7 | 192.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout-sparse | peak | generated-owned | 9.728 (9.699–9.753) | 0.80 | 52429.6 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout-sparse | peak | slab | 20.910 (20.731–22.748) | 1.72 | 24391.4 | 1.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | peak | slab-native | 20.715 (20.712–31.413) | 1.70 | 24621.5 | 1.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | peak | slab-projected | 26.917 (25.953–27.364) | 2.21 | 18948.3 | 1.00 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | peak | slab-lazy | 25.849 (25.659–26.798) | 2.12 | 19731.2 | 1.00 | 0.0 | 528.5 / 528.5 | 0.0 | 510.0 |
| baseline | full | decode | stock | 788.111 (784.456–834.940) | 1.00 | 190.2 | 1629.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | decode | generated-owned | 834.950 (831.674–841.734) | 1.06 | 179.5 | 1607.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | decode | slab | 978.408 (972.307–980.106) | 1.24 | 153.2 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | decode | slab-native | 972.378 (970.632–982.989) | 1.23 | 154.2 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | decode | slab-lazy | 1240.269 (1237.214–1286.614) | 1.57 | 120.9 | 972.25 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| baseline | full | decode | slab-session | 794.012 (783.248–796.418) | 1.01 | 188.8 | 40.00 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| baseline | holdout-sparse | peak | stock | 11.883 (11.809–12.912) | 1.00 | 42921.0 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout-sparse | peak | stock-interop | 137.311 (136.150–140.808) | 11.56 | 3714.4 | 192.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout-sparse | peak | generated-owned | 9.726 (9.589–9.740) | 0.82 | 52440.7 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout-sparse | peak | slab | 30.297 (29.747–30.320) | 2.55 | 16834.5 | 1.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| baseline | holdout-sparse | peak | slab-native | 29.567 (29.412–405.545) | 2.49 | 17250.1 | 1.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| baseline | holdout-sparse | peak | slab-projected | 33.252 (33.184–33.392) | 2.80 | 15338.2 | 1.00 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| baseline | holdout-sparse | peak | slab-lazy | 33.890 (33.721–36.766) | 2.85 | 15049.7 | 1.00 | 0.0 | 528.5 / 528.5 | 0.0 | 510.0 |
