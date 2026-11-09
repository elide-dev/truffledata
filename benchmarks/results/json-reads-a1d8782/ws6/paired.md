# Paired JSON SVM results

Medians of three fresh-process fork medians; parentheses are fork-median ranges. Every before/after cell shares its host and pinned CPU; different shards may use different hosts. "Disjoint" says the two fork-median ranges do not overlap (a sample fact, not a confidence interval).

| Shard | Workload | Engine | Stage | Baseline ns/doc | Candidate ns/doc | Before/after | Disjoint | Baseline Java B/doc | Candidate Java B/doc |
|---:|---|---|---|---:|---:|---:|---|---:|---:|
| 4 | json-scalar | simdjson-typed | ingest | 238.410 (235.530–239.676) | 236.178 (234.044–236.582) | 1.009x | no | 112.000 (112.000–112.000) | 112.000 (112.000–112.000) |
| 4 | json-scalar | simdjson-typed | peak | 4.380 (4.377–4.384) | 4.938 (4.928–4.947) | 0.887x | yes | 1.000 (1.000–1.000) | 1.000 (1.000–1.000) |
| 4 | json-scalar | simdjson-specialized | ingest | 238.999 (238.035–240.492) | 238.563 (237.756–239.513) | 1.002x | no | 112.000 (112.000–112.000) | 112.000 (112.000–112.000) |
| 4 | json-scalar | simdjson-specialized | peak | 2.774 (2.760–2.775) | 3.501 (3.429–3.557) | 0.792x | yes | 1.000 (1.000–1.000) | 1.000 (1.000–1.000) |
| 4 | json-scalar | gson-stream | ingest | 2155.836 (1831.610–3999.583) | 2606.046 (1569.480–2872.936) | 0.827x | no | 11288.000 (11288.000–11288.000) | 11288.000 (11288.000–11288.000) |
| 4 | json-scalar | gson-stream | peak | 1.348 (1.344–1.363) | 1.351 (1.345–1.623) | 0.997x | no | 1.000 (1.000–1.000) | 1.000 (1.000–1.000) |
| 6 | json-generic | simdjson-snapshot | ingest | 3043.730 (2690.989–5179.927) | 3008.871 (3006.345–5214.956) | 1.012x | no | 1183.500 (1183.500–1183.500) | 1183.500 (1183.500–1183.500) |
| 6 | json-generic | simdjson-snapshot | read-aot | 349.510 (347.281–351.165) | 356.253 (355.687–356.425) | 0.981x | yes | 240.000 (240.000–240.000) | 240.000 (240.000–240.000) |
| 6 | json-generic | simdjson-snapshot | interpret | 344.523 (344.228–352.436) | 357.478 (355.868–358.380) | 0.964x | yes | 242.000 (242.000–242.000) | 242.000 (242.000–242.000) |
| 6 | json-generic | simdjson-snapshot | peak | 80.701 (80.440–148.237) | 79.980 (79.970–80.618) | 1.009x | no | 1.000 (1.000–1.000) | 1.000 (1.000–1.000) |
| 6 | json-generic | simdjson-stream-interop | ingest | 1278.693 (1278.336–1286.706) | 1272.096 (1266.173–1278.781) | 1.005x | no | 304.750 (304.750–304.750) | 304.750 (304.750–304.750) |
| 6 | json-generic | simdjson-generic-interop | ingest | 1710.903 (1702.211–1717.746) | 1672.651 (1669.879–2060.456) | 1.023x | no | 384.000 (384.000–384.000) | 384.000 (384.000–384.000) |
| 6 | json-generic | simdjson-generic-interop | peak | 56.894 (56.239–57.097) | 57.171 (56.644–57.568) | 0.995x | no | 1.000 (1.000–1.000) | 1.000 (1.000–1.000) |
| 6 | json-generic | simdjson-generic-decode | ingest | (candidate only) | 1013.831 (1002.226–1015.945) | — | — | — | 120.000 (120.000–120.000) |
| 7 | json-scalar | simdjson-typed | read-aot | 4.751 (4.597–4.760) | 4.537 (4.526–4.679) | 1.047x | no | 0.000 (0.000–0.000) | 0.000 (0.000–0.000) |
| 7 | json-scalar | simdjson-typed | interpret | 5.189 (5.181–5.227) | 4.932 (4.902–4.939) | 1.052x | yes | 2.000 (2.000–2.000) | 2.000 (2.000–2.000) |
| 7 | json-scalar | simdjson-specialized | read-aot | 6.685 (6.656–6.735) | 6.371 (6.370–6.520) | 1.049x | yes | 0.000 (0.000–0.000) | 0.000 (0.000–0.000) |
| 7 | json-scalar | simdjson-specialized | interpret | 7.783 (7.741–7.813) | 7.261 (7.231–7.506) | 1.072x | yes | 2.000 (2.000–2.000) | 2.000 (2.000–2.000) |
| 7 | json-scalar | gson-stream | read-aot | 1.358 (1.340–1.359) | 1.350 (1.340–1.352) | 1.006x | no | 0.000 (0.000–0.000) | 0.000 (0.000–0.000) |
| 7 | json-scalar | gson-stream | interpret | 1.829 (1.758–1.959) | 1.839 (1.823–1.858) | 0.995x | no | 2.000 (2.000–2.000) | 2.000 (2.000–2.000) |
