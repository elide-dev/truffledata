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
| full | decode | 772.7 | — | 816.8 (1.06×) | 986.7 (1.28×) | 989.4 (1.28×) | — | 1195.3 (1.55×) | 783.3 (1.01×) | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | 11.7 | 138.7 (11.85×) | 9.8 (0.84×) | 20.7 (1.77×) | 21.0 (1.79×) | 26.6 (2.27×) | 25.8 (2.20×) | — | — | — | — |
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
| full | decode | 770.9 | — | 840.8 (1.09×) | 966.0 (1.25×) | 963.1 (1.25×) | — | 1245.3 (1.62×) | 778.4 (1.01×) | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | 11.6 | 135.6 (11.69×) | 9.7 (0.83×) | 29.5 (2.54×) | 29.6 (2.55×) | 33.2 (2.86×) | 34.2 (2.95×) | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | full | decode | stock | 772.720 (770.251–783.409) | 1.00 | 194.0 | 1629.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | generated-owned | 816.819 (812.629–837.627) | 1.06 | 183.5 | 1607.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | decode | slab | 986.708 (985.675–996.186) | 1.28 | 151.9 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | decode | slab-native | 989.393 (982.643–991.005) | 1.28 | 151.5 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | decode | slab-lazy | 1195.323 (1189.172–1207.840) | 1.55 | 125.4 | 972.25 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | full | decode | slab-session | 783.284 (782.779–786.237) | 1.01 | 191.4 | 40.00 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | holdout-sparse | peak | stock | 11.710 (11.666–11.724) | 1.00 | 43554.4 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout-sparse | peak | stock-interop | 138.711 (137.013–235.469) | 11.85 | 3676.9 | 192.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout-sparse | peak | generated-owned | 9.848 (9.695–10.008) | 0.84 | 51790.2 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout-sparse | peak | slab | 20.731 (20.673–20.754) | 1.77 | 24602.9 | 1.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | peak | slab-native | 20.967 (20.784–30.502) | 1.79 | 24325.3 | 1.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | peak | slab-projected | 26.565 (26.408–26.972) | 2.27 | 19199.4 | 1.00 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | peak | slab-lazy | 25.769 (25.716–26.162) | 2.20 | 19792.7 | 1.00 | 0.0 | 528.5 / 528.5 | 0.0 | 510.0 |
| baseline | full | decode | stock | 770.865 (770.215–773.675) | 1.00 | 194.5 | 1629.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | decode | generated-owned | 840.808 (835.096–1001.137) | 1.09 | 178.3 | 1607.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | decode | slab | 965.992 (957.177–968.587) | 1.25 | 155.2 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | decode | slab-native | 963.112 (962.841–963.506) | 1.25 | 155.6 | 808.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | decode | slab-lazy | 1245.289 (1245.008–1248.516) | 1.62 | 120.4 | 972.25 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| baseline | full | decode | slab-session | 778.411 (776.321–778.969) | 1.01 | 192.6 | 40.00 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| baseline | holdout-sparse | peak | stock | 11.597 (11.570–11.705) | 1.00 | 43978.5 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout-sparse | peak | stock-interop | 135.616 (135.427–136.755) | 11.69 | 3760.8 | 192.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout-sparse | peak | generated-owned | 9.683 (9.676–9.772) | 0.83 | 52674.8 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout-sparse | peak | slab | 29.507 (29.451–29.543) | 2.54 | 17284.9 | 1.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| baseline | holdout-sparse | peak | slab-native | 29.594 (29.347–29.596) | 2.55 | 17234.0 | 1.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| baseline | holdout-sparse | peak | slab-projected | 33.176 (33.155–33.431) | 2.86 | 15373.7 | 1.00 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| baseline | holdout-sparse | peak | slab-lazy | 34.163 (34.020–34.628) | 2.95 | 14929.5 | 1.00 | 0.0 | 528.5 / 528.5 | 0.0 | 510.0 |
