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
| sparse | interpret | 9.7 | 176.5 (18.11×) | 31.8 (3.26×) | 44.8 (4.59×) | 44.2 (4.53×) | 50.8 (5.21×) | 49.9 (5.12×) | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | 133.3 | 2882.7 (21.63×) | 57.1 (0.43×) | 223.2 (1.67×) | 214.0 (1.61×) | — | 279.6 (2.10×) | — | — | — | — |
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
| sparse | interpret | 9.8 | 171.0 (17.50×) | 32.2 (3.30×) | 52.3 (5.35×) | 52.3 (5.36×) | 58.8 (6.02×) | 57.8 (5.92×) | — | — | — | — |
| sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| full | decode | — | — | — | — | — | — | — | — | — | — | — |
| full | resident | — | — | — | — | — | — | — | — | — | — | — |
| full | heap | — | — | — | — | — | — | — | — | — | — | — |
| full | detach | — | — | — | — | — | — | — | — | — | — | — |
| full | first | — | — | — | — | — | — | — | — | — | — | — |
| full | interpret | — | — | — | — | — | — | — | — | — | — | — |
| full | peak | — | — | — | — | — | — | — | — | — | — | — |
| holdout | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout | peak | 133.9 | 2964.9 (22.15×) | 56.2 (0.42×) | 347.3 (2.59×) | 340.4 (2.54×) | — | 392.5 (2.93×) | — | — | — | — |
| holdout-sparse | resident | — | — | — | — | — | — | — | — | — | — | — |
| holdout-sparse | peak | — | — | — | — | — | — | — | — | — | — | — |
| large | decode | — | — | — | — | — | — | — | — | — | — | — |
| large | resident | — | — | — | — | — | — | — | — | — | — | — |
| large | peak | — | — | — | — | — | — | — | — | — | — | — |

## All cells

| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| candidate | sparse | interpret | stock | 9.747 (9.737–10.868) | 1.00 | 15379.7 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | interpret | stock-interop | 176.544 (173.744–186.847) | 18.11 | 849.1 | 193.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | interpret | generated-owned | 31.793 (31.693–31.879) | 3.26 | 4715.0 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | sparse | interpret | slab | 44.777 (43.942–46.465) | 4.59 | 3347.9 | 2.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | interpret | slab-native | 44.186 (44.008–46.639) | 4.53 | 3392.6 | 2.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| candidate | sparse | interpret | slab-projected | 50.830 (49.531–51.266) | 5.21 | 2949.2 | 2.00 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| candidate | sparse | interpret | slab-lazy | 49.929 (49.896–50.353) | 5.12 | 3002.4 | 2.00 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| candidate | holdout | peak | stock | 133.297 (133.271–141.864) | 1.00 | 3826.3 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | peak | stock-interop | 2882.737 (2869.925–2890.914) | 21.63 | 176.9 | 5319.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | peak | generated-owned | 57.059 (55.983–58.473) | 0.43 | 8938.6 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| candidate | holdout | peak | slab | 223.201 (220.449–248.983) | 1.67 | 2285.1 | 17.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout | peak | slab-native | 214.037 (211.788–214.285) | 1.61 | 2382.9 | 17.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| candidate | holdout | peak | slab-lazy | 279.617 (276.387–280.437) | 2.10 | 1824.0 | 17.00 | 0.0 | 528.5 / 2471.0 | 0.0 | 510.0 |
| baseline | sparse | interpret | stock | 9.772 (9.685–9.823) | 1.00 | 15341.1 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | interpret | stock-interop | 170.984 (169.472–171.531) | 17.50 | 876.7 | 193.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | interpret | generated-owned | 32.219 (32.173–33.101) | 3.30 | 4652.7 | 2.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | sparse | interpret | slab | 52.297 (52.233–52.807) | 5.35 | 2866.5 | 2.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | interpret | slab-native | 52.329 (52.056–52.706) | 5.36 | 2864.7 | 2.00 | 0.0 | 547.2 / 702.2 | 0.0 | 0.0 |
| baseline | sparse | interpret | slab-projected | 58.847 (57.454–59.474) | 6.02 | 2547.4 | 2.00 | 0.0 | 179.8 / 179.8 | 0.0 | 0.0 |
| baseline | sparse | interpret | slab-lazy | 57.839 (57.417–57.970) | 5.92 | 2591.8 | 2.00 | 0.0 | 289.2 / 289.2 | 0.0 | 149.9 |
| baseline | holdout | peak | stock | 133.866 (133.193–138.232) | 1.00 | 3810.0 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | peak | stock-interop | 2964.870 (2896.042–2967.621) | 22.15 | 172.0 | 5319.25 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | peak | generated-owned | 56.233 (55.908–56.401) | 0.42 | 9070.0 | 1.00 | 0.0 | 0.0 / 0.0 | 0.0 | 0.0 |
| baseline | holdout | peak | slab | 347.272 (337.336–3305.567) | 2.59 | 1468.7 | 17.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| baseline | holdout | peak | slab-native | 340.376 (338.393–350.524) | 2.54 | 1498.4 | 17.00 | 0.0 | 1638.5 / 2002.8 | 0.0 | 0.0 |
| baseline | holdout | peak | slab-lazy | 392.463 (391.948–394.280) | 2.93 | 1299.6 | 17.00 | 0.0 | 528.5 / 2471.0 | 0.0 | 510.0 |
