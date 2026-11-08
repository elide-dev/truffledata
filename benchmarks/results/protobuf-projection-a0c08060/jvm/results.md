# Consumer comparison (jvm)

Fresh-process fork medians; ranges are not confidence intervals. Java allocation excludes native storage, reported separately as arena, retained input and slab capacity. Stock guest projection has an explicit boundary. MB/s divides wire input bytes by the whole cell (decode cells: decode and release only). Native transient allocation peaks are not measured.

## candidate: ns/row (× stock)

| Mode | Stage | stock | stock-interop | generated-owned | slab | slab-projected | slab-lazy | slab-session | slab-session-projected | native-view | native-eager |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| sparse | decode | — | — | — | — | — | — | — | — | — | — |
| sparse | resident | 245.7 | — | — | 442.4 (1.80×) | 531.5 (2.16×) | 709.1 (2.89×) | 637.9 (2.60×) | 571.8 (2.33×) | — | — |
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
| holdout | resident | 998.9 | — | — | 1703.1 (1.71×) | — | 11476.2 (11.49×) | 2219.3 (2.22×) | — | — | — |
| holdout | peak | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | resident | 955.0 | — | — | 1278.2 (1.34×) | 942.2 (0.99×) | 1536.4 (1.61×) | 1574.4 (1.65×) | 1304.0 (1.37×) | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | sparse | resident | stock | 245.683 (245.308–304.832) | 1.00 | 610.2 | 1393.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | resident | slab | 442.388 (442.011–447.689) | 1.80 | 338.9 | 991.00 | 0.0 | 547.2 / 547.2 | 0.0 | 0.0 |
| candidate | sparse | resident | slab-projected | 531.486 (517.202–557.065) | 2.16 | 282.1 | 1796.00 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | resident | slab-lazy | 709.101 (693.883–723.590) | 2.89 | 211.4 | 2914.75 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | sparse | resident | slab-session | 637.947 (631.593–644.876) | 2.60 | 235.0 | 138.75 | 0.0 | 547.2 / 0.0 | 855.6 | 0.0 |
| candidate | sparse | resident | slab-session-projected | 571.799 (561.896–583.751) | 2.33 | 262.2 | 138.75 | 0.0 | 289.2 / 0.0 | 855.6 | 149.9 |
| candidate | holdout | resident | stock | 998.858 (997.589–1009.082) | 1.00 | 510.6 | 4706.75 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | resident | slab | 1703.149 (1696.215–1709.556) | 1.71 | 299.5 | 3902.00 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout | resident | slab-lazy | 11476.237 (6572.611–13435.079) | 11.49 | 44.4 | 11845.25 | 0.0 | 528.5 / 2471.0 | 0.0 | 510.0 |
| candidate | holdout | resident | slab-session | 2219.250 (2211.336–2225.341) | 2.22 | 229.8 | 1303.25 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
| candidate | holdout-sparse | resident | stock | 955.016 (948.477–969.267) | 1.00 | 534.1 | 4706.75 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab | 1278.249 (1266.612–1282.766) | 1.34 | 399.0 | 2729.00 | 0.0 | 1638.5 / 1638.5 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab-projected | 942.200 (903.440–955.408) | 0.99 | 541.3 | 1796.00 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | holdout-sparse | resident | slab-lazy | 1536.448 (1513.168–1560.872) | 1.61 | 332.0 | 5424.00 | 0.0 | 528.5 / 528.5 | 0.0 | 510.0 |
| candidate | holdout-sparse | resident | slab-session | 1574.443 (1565.204–1578.002) | 1.65 | 323.9 | 138.75 | 0.0 | 1638.5 / 0.0 | 2296.1 | 0.0 |
| candidate | holdout-sparse | resident | slab-session-projected | 1304.032 (1302.131–1316.153) | 1.37 | 391.1 | 138.75 | 0.0 | 528.5 / 0.0 | 2296.1 | 510.0 |
