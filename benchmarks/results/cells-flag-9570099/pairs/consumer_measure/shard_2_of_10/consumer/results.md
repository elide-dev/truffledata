# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | 844.0 | — | 969.3 (1.15×) | 472.5 (0.56×) | 503.0 (0.60×) | 1222.9 (1.45×) | 1412.4 (1.67×) | 941.0 (1.11×) | 795.4 (0.94×) | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | 81.1 | — | 52.0 (0.64×) | 305.9 (3.77×) | 309.5 (3.81×) | — | 1697.7 (20.93×) | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## baseline: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | 798.2 | — | 940.7 (1.18×) | 511.3 (0.64×) | 476.1 (0.60×) | 1888.8 (2.37×) | 1424.0 (1.78×) | 932.1 (1.17×) | 921.9 (1.15×) | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | 71.5 | — | 47.4 (0.66×) | 293.6 (4.11×) | 296.0 (4.14×) | — | 1699.7 (23.77×) | — | — | — | — |
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
| candidate | sparse | resident | stock | 843.985 (824.524–874.346) | 1.00 | 177.6 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | resident | generated-owned | 969.323 (884.377–1044.614) | 1.15 | 154.7 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | resident | slab | 472.529 (469.801–483.781) | 0.56 | 317.2 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | resident | slab-native | 503.019 (477.380–779.002) | 0.60 | 298.0 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | resident | slab-projected | 1222.874 (1165.250–1306.446) | 1.45 | 122.6 | 689.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | resident | slab-lazy | 1412.376 (1406.085–1939.981) | 1.67 | 106.1 | 1084.50 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | sparse | resident | slab-session | 940.985 (897.020–968.317) | 1.11 | 159.3 | 151.25 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | sparse | resident | slab-session-projected | 795.413 (736.526–804.435) | 0.94 | 188.5 | 151.25 | 0.0 | 289.2 / 0.0 | 855.6 | 149.9 |
| candidate | full | first | stock | 81.121 (72.489–81.660) | 1.00 | 1847.9 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | first | generated-owned | 51.961 (47.353–78.835) | 0.64 | 2885.0 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | full | first | slab | 305.935 (305.181–383.048) | 3.77 | 490.0 | 632.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | first | slab-native | 309.455 (296.158–319.740) | 3.81 | 484.4 | 632.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | full | first | slab-lazy | 1697.739 (1574.270–1957.966) | 20.93 | 88.3 | 2075.00 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
| baseline | sparse | resident | stock | 798.230 (796.831–813.700) | 1.00 | 187.8 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | resident | generated-owned | 940.707 (913.296–961.405) | 1.18 | 159.4 | 1608.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | resident | slab | 511.254 (507.167–514.397) | 0.64 | 293.2 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | resident | slab-native | 476.126 (472.497–476.799) | 0.60 | 314.8 | 934.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | resident | slab-projected | 1888.845 (1266.096–4342.075) | 2.37 | 79.4 | 689.75 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| baseline | sparse | resident | slab-lazy | 1424.036 (1351.941–1434.398) | 1.78 | 105.3 | 1084.50 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| baseline | sparse | resident | slab-session | 932.133 (912.824–932.713) | 1.17 | 160.8 | 151.25 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| baseline | sparse | resident | slab-session-projected | 921.918 (771.911–2005.686) | 1.15 | 162.6 | 151.25 | 0.0 | 289.2 / 0.0 | 855.6 | 149.9 |
| baseline | full | first | stock | 71.513 (70.488–77.419) | 1.00 | 2096.2 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | first | generated-owned | 47.422 (36.358–48.424) | 0.66 | 3161.1 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | full | first | slab | 293.631 (291.100–322.048) | 4.11 | 510.5 | 632.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | first | slab-native | 295.998 (295.845–304.170) | 4.14 | 506.4 | 632.50 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | full | first | slab-lazy | 1699.660 (1646.649–2450.699) | 23.77 | 88.2 | 2075.00 | 0.0 | 289.2 / 805.2 | 0.0 | 149.9 |
