# Consumer comparison (jvm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-native | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | 260.1 | — | — | 463.5 (1.78×) | — | 400.4 (1.54×) | 246.3 (0.95×) | 684.0 (2.63×) | 686.6 (2.64×) | — | — |
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
| holdout | resident | 1029.0 | — | — | 1704.8 (1.66×) | — | — | 3575.6 (3.47×) | 2218.4 (2.16×) | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | 939.3 | — | — | 1293.2 (1.38×) | — | 834.1 (0.89×) | 589.9 (0.63×) | 1589.9 (1.69×) | 1306.6 (1.39×) | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | sparse | resident | stock | 260.119 (252.591–269.577) | 1.00 | 576.3 | 1393.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | resident | slab | 463.528 (445.253–480.936) | 1.78 | 323.4 | 991.00 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | sparse | resident | slab-projected | 400.413 (394.127–401.200) | 1.54 | 374.4 | 1185.50 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | resident | slab-lazy | 246.298 (243.565–254.901) | 0.95 | 608.6 | 944.25 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | sparse | resident | slab-session | 683.996 (661.144–713.370) | 2.63 | 219.2 | 138.75 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | sparse | resident | slab-session-projected | 686.641 (572.198–694.881) | 2.64 | 218.3 | 138.75 | 0.0 | 289.2 / 0.0 | 855.6 | 149.9 |
| candidate | holdout | resident | stock | 1029.004 (1021.578–1075.822) | 1.00 | 495.7 | 4706.75 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | resident | slab | 1704.837 (1695.371–1712.183) | 1.66 | 299.2 | 3902.00 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout | resident | slab-lazy | 3575.638 (2692.118–9513.822) | 3.47 | 142.6 | 8401.50 | 0.0 | 528.5 / 2471.0 | 0.0 | 510.0 |
| candidate | holdout | resident | slab-session | 2218.392 (2202.115–2401.712) | 2.16 | 229.9 | 1303.25 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
| candidate | holdout-sparse | resident | stock | 939.281 (936.114–947.710) | 1.00 | 543.0 | 4706.75 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab | 1293.247 (1280.096–1432.763) | 1.38 | 394.4 | 2729.00 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab-projected | 834.080 (778.363–1392.257) | 0.89 | 611.5 | 1185.50 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab-lazy | 589.903 (583.688–765.349) | 0.63 | 864.6 | 1980.25 | 0.0 | 528.5 / 528.5 | 0.0 | 510.0 |
| candidate | holdout-sparse | resident | slab-session | 1589.889 (1575.907–1614.208) | 1.69 | 320.8 | 138.75 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
| candidate | holdout-sparse | resident | slab-session-projected | 1306.649 (1301.132–1307.183) | 1.39 | 390.3 | 138.75 | 0.0 | 528.5 / 0.0 | 2296.1 | 510.0 |
