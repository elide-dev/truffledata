# Consumer comparison (jvm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | 8.4 | 84.5 (10.07×) | 12.4 (1.47×) | 40.1 (4.77×) | 32.8 (3.91×) | 37.3 (4.45×) | 37.5 (4.47×) | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | 56.2 | 123.6 (2.20×) | 47.7 (0.85×) | 362.4 (6.44×) | 354.4 (6.30×) | — | 398.4 (7.08×) | — | — | — | — |
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
| sparse | interpret | 7.9 | — | 13.0 (1.64×) | — | — | — | — | — | — | 36.5 (4.61×) | 26.6 (3.36×) |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | 53.6 | — | 48.1 (0.90×) | — | — | — | — | — | — | 265.6 (4.95×) | 125.8 (2.34×) |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | sparse | interpret | stock | 8.395 (7.406–9.205) | 1.00 | 17857.6 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | interpret | stock-interop | 84.504 (73.149–91.557) | 10.07 | 1774.0 | 279.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | interpret | generated-owned | 12.372 (12.333–13.198) | 1.47 | 12117.0 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | interpret | slab | 40.082 (31.978–40.442) | 4.77 | 3740.0 | 1.50 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | sparse | interpret | slab-native | 32.838 (31.072–35.602) | 3.91 | 4565.1 | 1.50 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | sparse | interpret | slab-projected | 37.331 (36.715–40.500) | 4.45 | 4015.6 | 1.50 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | interpret | slab-lazy | 37.514 (37.400–37.894) | 4.47 | 3996.0 | 1.50 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | holdout | peak | stock | 56.242 (55.115–56.248) | 1.00 | 9068.4 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | peak | stock-interop | 123.582 (121.670–129.293) | 2.20 | 4127.1 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | peak | generated-owned | 47.722 (44.618–57.156) | 0.85 | 10687.5 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | peak | slab | 362.408 (355.080–554.267) | 6.44 | 1407.3 | 33.50 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout | peak | slab-native | 354.428 (350.059–355.711) | 6.30 | 1439.0 | 33.50 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout | peak | slab-lazy | 398.359 (392.534–403.968) | 7.08 | 1280.3 | 33.50 | 0.0 | 528.5 / 2471.0 | 0.0 | 510.0 |
| baseline | sparse | interpret | stock | 7.913 (7.617–8.182) | 1.00 | 18944.4 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | interpret | generated-owned | 12.968 (11.478–13.109) | 1.64 | 11559.9 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | interpret | native-view | 36.481 (33.738–37.720) | 4.61 | 4109.1 | 1.50 | 1165.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | interpret | native-eager | 26.555 (21.630–28.003) | 3.36 | 5645.1 | 1.50 | 1141.5 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | peak | stock | 53.642 (53.590–55.446) | 1.00 | 9508.1 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | peak | generated-owned | 48.080 (47.736–48.316) | 0.90 | 10608.0 | 1.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | peak | native-view | 265.585 (264.886–266.334) | 4.95 | 1920.4 | 1.50 | 3315.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | peak | native-eager | 125.762 (125.142–499.648) | 2.34 | 4055.5 | 1.50 | 3291.0 | 0.0 / 0.0 | 0.0 | 0.0 |
