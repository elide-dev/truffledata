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
| full | heap | 885.2 | — | 915.1 (1.03×) | 739.0 (0.83×) | 735.9 (0.83×) | — | 3167.8 (3.58×) | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | 278912.4 | — | 282133.1 (1.01×) | 152051.9 (0.55×) | 152991.5 (0.55×) | — | 923150.0 (3.31×) | 270904.9 (0.97×) | — | — | — |
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
| full | heap | 859.9 | — | 919.2 (1.07×) | 733.2 (0.85×) | 768.8 (0.89×) | — | 3017.0 (3.51×) | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | 254397.9 | — | 288795.1 (1.14×) | 163727.5 (0.64×) | 152055.0 (0.60×) | — | 851875.0 (3.35×) | 270261.5 (1.06×) | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | full | heap | stock | 885.202 (870.448–888.782) | 1.00 | 169.3 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | heap | generated-owned | 915.060 (897.644–997.068) | 1.03 | 163.8 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | heap | slab | 739.007 (723.805–41270.378) | 0.83 | 202.8 | 1464.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | heap | slab-native | 735.915 (718.719–758.212) | 0.83 | 203.7 | 1464.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | heap | slab-lazy | 3167.816 (2955.769–3280.757) | 3.58 | 47.3 | 3255.25 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| candidate | large | resident | stock | 278912.396 (267339.693–321593.625) | 1.00 | 185.8 | 433009.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | resident | generated-owned | 282133.125 (267936.823–292337.812) | 1.01 | 183.7 | 460180.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | large | resident | slab | 152051.932 (149238.949–153852.131) | 0.55 | 340.8 | 281177.50 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| candidate | large | resident | slab-native | 152991.477 (151004.688–156473.188) | 0.55 | 338.7 | 281177.50 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| candidate | large | resident | slab-lazy | 923150.000 (840756.094–940795.000) | 3.31 | 56.1 | 834813.25 | 0.0 | 45619.8 / 281827.8 | 0.0 | 51822.3 |
| candidate | large | resident | slab-session | 270904.896 (263855.260–300917.865) | 0.97 | 191.3 | 91383.25 | 0.0 | 172771.8 / 0.0 | 207545.1 | 0.0 |
| baseline | full | heap | stock | 859.922 (856.239–882.635) | 1.00 | 174.3 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | heap | generated-owned | 919.232 (900.482–974.900) | 1.07 | 163.1 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | heap | slab | 733.242 (726.131–18226.690) | 0.85 | 204.4 | 1464.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | heap | slab-native | 768.814 (744.540–783.688) | 0.89 | 195.0 | 1464.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | heap | slab-lazy | 3016.991 (2979.594–3370.954) | 3.51 | 49.7 | 3255.25 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| baseline | large | resident | stock | 254397.902 (252000.000–257575.982) | 1.00 | 203.7 | 433009.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | resident | generated-owned | 288795.052 (274565.521–297306.458) | 1.14 | 179.4 | 460180.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | large | resident | slab | 163727.531 (154697.983–234550.223) | 0.64 | 316.5 | 281177.50 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| baseline | large | resident | slab-native | 152054.972 (147085.795–153611.761) | 0.60 | 340.8 | 281177.50 | 0.0 | 172771.8 / 186502.6 | 0.0 | 0.0 |
| baseline | large | resident | slab-lazy | 851875.000 (813924.062–923079.359) | 3.35 | 60.8 | 834813.25 | 0.0 | 45619.8 / 281827.8 | 0.0 | 51822.3 |
| baseline | large | resident | slab-session | 270261.458 (268393.750–290867.396) | 1.06 | 191.7 | 91383.25 | 0.0 | 172771.8 / 0.0 | 207545.1 | 0.0 |
