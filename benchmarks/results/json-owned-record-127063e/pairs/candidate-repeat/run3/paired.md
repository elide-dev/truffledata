# Paired JSON SVM results

Medians of three fresh-process fork medians; parentheses are fork-median ranges. Every before/after cell shares its host and pinned CPU; different shards may use different hosts. "Disjoint" says the two fork-median ranges do not overlap (a sample fact, not a confidence interval).

| Shard | Workload | Engine | Stage | Baseline ns/doc | Candidate ns/doc | Before/after | Disjoint | Baseline Java B/doc | Candidate Java B/doc |
|---:|---|---|---|---:|---:|---:|---|---:|---:|
| 4 | json-scalar | simdjson-typed | ingest | 260.136 (250.297–329.092) | 238.529 (231.506–260.622) | 1.091x | no | 112.000 (112.000–112.000) | 120.000 (120.000–120.000) |
| 4 | json-scalar | simdjson-typed | peak | 4.533 (4.434–4.563) | 4.388 (4.376–4.403) | 1.033x | yes | 1.000 (1.000–1.000) | 1.000 (1.000–1.000) |
| 4 | json-scalar | simdjson-specialized | ingest | 258.589 (239.135–355.166) | 238.998 (221.762–244.658) | 1.082x | no | 112.000 (112.000–112.000) | 120.000 (120.000–120.000) |
| 4 | json-scalar | simdjson-specialized | peak | 3.055 (2.972–3.055) | 2.968 (2.862–3.127) | 1.029x | no | 1.000 (1.000–1.000) | 1.000 (1.000–1.000) |
| 4 | json-scalar | gson-stream | ingest | 2292.153 (1484.222–3722.388) | 13498.866 (2841.864–122779.750) | 0.170x | no | 11288.000 (11288.000–11288.000) | 11288.000 (11288.000–11288.000) |
| 4 | json-scalar | gson-stream | peak | 1.459 (1.377–1.487) | 1.460 (1.395–1.509) | 0.999x | no | 1.000 (1.000–1.000) | 1.000 (1.000–1.000) |
| 7 | json-scalar | simdjson-typed | read-aot | 6.218 (5.429–6.454) | 6.476 (5.844–7.817) | 0.960x | no | 0.000 (0.000–0.000) | 0.000 (0.000–0.000) |
| 7 | json-scalar | simdjson-typed | interpret | 6.354 (5.528–8.736) | 5.661 (5.621–8.860) | 1.122x | no | 2.000 (2.000–2.000) | 2.000 (2.000–2.000) |
| 7 | json-scalar | simdjson-specialized | read-aot | 7.325 (7.320–9.978) | 9.124 (7.455–11.966) | 0.803x | no | 0.000 (0.000–0.000) | 0.000 (0.000–0.000) |
| 7 | json-scalar | simdjson-specialized | interpret | 8.554 (8.246–12.476) | 9.330 (8.936–13.430) | 0.917x | no | 2.000 (2.000–2.000) | 2.000 (2.000–2.000) |
| 7 | json-scalar | gson-stream | read-aot | 1.626 (1.431–2.327) | 2.215 (1.450–2.349) | 0.734x | no | 0.000 (0.000–0.000) | 0.000 (0.000–0.000) |
| 7 | json-scalar | gson-stream | interpret | 3.022 (2.473–3.026) | 1.995 (1.978–2.614) | 1.515x | no | 2.000 (2.000–2.000) | 2.000 (2.000–2.000) |
