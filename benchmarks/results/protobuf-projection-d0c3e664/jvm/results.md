# Consumer comparison (jvm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | 254.3 | — | — | 487.3 (1.92×) | — | 404.1 (1.59×) | 1302.0 (5.12×) | 647.8 (2.55×) | 595.0 (2.34×) | — | — |
| sparse | heap | — | — | — | — | — | — | — | — | — | — | — |
| sparse | detach | — | — | — | — | — | — | — | — | — | — | — |
| sparse | first | — | — | — | — | — | — | — | — | — | — | — |
| sparse | interpret | — | — | — | — | — | — | — | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | 1015.6 | — | — | 1700.1 (1.67×) | — | — | 7058.6 (6.95×) | 2355.8 (2.32×) | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | 949.4 | — | — | 1285.8 (1.35×) | — | 779.5 (0.82×) | 1352.5 (1.42×) | 1594.6 (1.68×) | 1331.6 (1.40×) | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | sparse | resident | stock | 254.289 (244.778–295.909) | 1.00 | 589.5 | 1393.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | resident | slab | 487.324 (448.021–489.913) | 1.92 | 307.6 | 991.00 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | sparse | resident | slab-projected | 404.149 (400.165–420.183) | 1.59 | 370.9 | 1185.50 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | resident | slab-lazy | 1301.963 (453.589–1516.033) | 5.12 | 115.1 | 1958.25 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | sparse | resident | slab-session | 647.778 (629.997–657.479) | 2.55 | 231.4 | 138.75 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | sparse | resident | slab-session-projected | 595.040 (578.623–684.455) | 2.34 | 251.9 | 138.75 | 0.0 | 289.2 / 0.0 | 855.6 | 149.9 |
| candidate | holdout | resident | stock | 1015.613 (996.757–1015.938) | 1.00 | 502.2 | 4706.75 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | resident | slab | 1700.129 (1693.012–3293.177) | 1.67 | 300.0 | 3902.00 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout | resident | slab-lazy | 7058.596 (3123.933–10136.827) | 6.95 | 72.3 | 10043.00 | 0.0 | 528.5 / 2471.0 | 0.0 | 510.0 |
| candidate | holdout | resident | slab-session | 2355.772 (2221.639–10088.736) | 2.32 | 216.5 | 1303.25 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
| candidate | holdout-sparse | resident | stock | 949.408 (944.700–963.298) | 1.00 | 537.2 | 4706.75 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab | 1285.822 (1283.671–1302.918) | 1.35 | 396.7 | 2729.00 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab-projected | 779.531 (769.821–922.804) | 0.82 | 654.3 | 1185.50 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab-lazy | 1352.490 (1337.013–2393.235) | 1.42 | 377.1 | 3621.75 | 0.0 | 528.5 / 528.5 | 0.0 | 510.0 |
| candidate | holdout-sparse | resident | slab-session | 1594.644 (1585.940–1629.198) | 1.68 | 319.8 | 138.75 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
| candidate | holdout-sparse | resident | slab-session-projected | 1331.622 (1328.456–1418.244) | 1.40 | 383.0 | 138.75 | 0.0 | 528.5 / 0.0 | 2296.1 | 510.0 |
