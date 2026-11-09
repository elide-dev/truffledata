# Consumer comparison (svm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | 662.8 | — | — | 1571.9 (2.37×) | 1057.8 (1.60×) | 1301.8 (1.96×) | 714.9 (1.08×) | 534.1 (0.81×) | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | 2121.0 | — | — | 4528.6 (2.14×) | — | 5990.6 (2.82×) | 2090.8 (0.99×) | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | 1994.9 | — | — | 4128.4 (2.07×) | 1552.2 (0.78×) | 2279.0 (1.14×) | 1747.2 (0.88×) | 1163.1 (0.58×) | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | sparse | resident | stock | 662.781 (658.596–690.863) | 1.00 | 226.2 | 1630.50 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | resident | slab | 1571.915 (1567.944–1576.270) | 2.37 | 95.4 | 2184.00 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | sparse | resident | slab-projected | 1057.783 (1055.407–1071.437) | 1.60 | 141.7 | 1158.00 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | resident | slab-lazy | 1301.793 (1301.724–1305.626) | 1.96 | 115.2 | 1767.25 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | sparse | resident | slab-session | 714.869 (712.944–719.825) | 1.08 | 209.7 | 151.25 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | sparse | resident | slab-session-projected | 534.115 (526.989–663.236) | 0.81 | 280.7 | 151.25 | 0.0 | 289.2 / 0.0 | 855.6 | 149.9 |
| candidate | holdout | resident | stock | 2120.959 (2105.691–2130.121) | 1.00 | 240.5 | 5310.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | resident | slab | 4528.641 (4508.988–4566.789) | 2.14 | 112.6 | 6399.50 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout | resident | slab-lazy | 5990.578 (5969.407–6031.471) | 2.82 | 85.1 | 8977.25 | 0.0 | 528.5 / 2471.0 | 0.0 | 510.0 |
| candidate | holdout | resident | slab-session | 2090.773 (2072.365–2096.134) | 0.99 | 243.9 | 1197.00 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
| candidate | holdout-sparse | resident | stock | 1994.865 (1975.160–2001.563) | 1.00 | 255.7 | 5310.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab | 4128.440 (4085.648–4150.687) | 2.07 | 123.5 | 5434.25 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab-projected | 1552.155 (1536.567–1572.127) | 0.78 | 328.6 | 1158.00 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab-lazy | 2278.959 (2267.757–2280.595) | 1.14 | 223.8 | 3041.25 | 0.0 | 528.5 / 528.5 | 0.0 | 510.0 |
| candidate | holdout-sparse | resident | slab-session | 1747.228 (1742.553–1748.118) | 0.88 | 291.9 | 151.25 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
| candidate | holdout-sparse | resident | slab-session-projected | 1163.112 (1162.288–1258.849) | 0.58 | 438.5 | 151.25 | 0.0 | 528.5 / 0.0 | 2296.1 | 510.0 |
