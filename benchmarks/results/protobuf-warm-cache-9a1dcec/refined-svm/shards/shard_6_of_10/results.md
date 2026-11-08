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
| sparse | interpret | 10.7 | 173.5 (16.15×) | 31.9 (2.97×) | 44.1 (4.10×) | 44.3 (4.13×) | 48.9 (4.56×) | 50.7 (4.72×) | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | 134.4 | 2906.9 (21.63×) | 56.0 (0.42×) | 208.9 (1.55×) | 212.2 (1.58×) | — | 275.4 (2.05×) | — | — | — | — |
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
| sparse | interpret | 10.8 | 169.4 (15.69×) | 32.5 (3.01×) | 52.9 (4.90×) | 52.6 (4.87×) | 57.3 (5.31×) | 57.8 (5.36×) | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | 133.9 | 2847.8 (21.26×) | 55.9 (0.42×) | 339.7 (2.54×) | 345.0 (2.58×) | — | 395.4 (2.95×) | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | sparse | interpret | stock | 10.742 (10.546–451.493) | 1.00 | 13955.1 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | interpret | stock-interop | 173.489 (172.263–187.077) | 16.15 | 864.1 | 193.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | interpret | generated-owned | 31.871 (31.507–32.723) | 2.97 | 4703.6 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | interpret | slab | 44.072 (43.881–45.068) | 4.10 | 3401.4 | 2.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | interpret | slab-native | 44.339 (43.918–44.404) | 4.13 | 3380.9 | 2.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | interpret | slab-projected | 48.937 (48.702–49.393) | 4.56 | 3063.3 | 2.00 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | interpret | slab-lazy | 50.704 (49.486–50.802) | 4.72 | 2956.5 | 2.00 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | holdout | peak | stock | 134.417 (133.384–138.588) | 1.00 | 3794.4 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | peak | stock-interop | 2906.854 (2905.982–3217.311) | 21.63 | 175.5 | 5319.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | peak | generated-owned | 56.032 (55.937–56.580) | 0.42 | 9102.5 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | peak | slab | 208.892 (208.135–215.647) | 1.55 | 2441.6 | 17.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout | peak | slab-native | 212.235 (210.702–217.983) | 1.58 | 2403.1 | 17.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout | peak | slab-lazy | 275.369 (272.455–321.359) | 2.05 | 1852.2 | 17.00 | 0.0 | 528.5 / 2471.0 | 0.0 | 510.0 |
| baseline | sparse | interpret | stock | 10.796 (9.731–12.735) | 1.00 | 13884.8 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | interpret | stock-interop | 169.378 (168.992–169.489) | 15.69 | 885.0 | 193.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | interpret | generated-owned | 32.499 (31.671–32.732) | 3.01 | 4612.6 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | interpret | slab | 52.867 (52.526–87.108) | 4.90 | 2835.5 | 2.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | interpret | slab-native | 52.562 (52.195–52.790) | 4.87 | 2852.0 | 2.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | interpret | slab-projected | 57.346 (56.730–58.343) | 5.31 | 2614.0 | 2.00 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| baseline | sparse | interpret | slab-lazy | 57.843 (57.362–594.117) | 5.36 | 2591.6 | 2.00 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| baseline | holdout | peak | stock | 133.943 (133.188–140.160) | 1.00 | 3807.8 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | peak | stock-interop | 2847.819 (2835.251–2862.486) | 21.26 | 179.1 | 5319.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | peak | generated-owned | 55.856 (55.522–57.854) | 0.42 | 9131.2 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | peak | slab | 339.720 (338.052–354.010) | 2.54 | 1501.3 | 17.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| baseline | holdout | peak | slab-native | 344.974 (337.709–439.396) | 2.58 | 1478.5 | 17.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| baseline | holdout | peak | slab-lazy | 395.372 (393.522–442.434) | 2.95 | 1290.0 | 17.00 | 0.0 | 528.5 / 2471.0 | 0.0 | 510.0 |
