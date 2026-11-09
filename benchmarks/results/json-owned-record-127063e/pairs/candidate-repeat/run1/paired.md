# Paired JSON SVM results

Medians of three fresh-process fork medians; parentheses are fork-median ranges. Every before/after cell shares its host and pinned CPU; different shards may use different hosts. "Disjoint" says the two fork-median ranges do not overlap (a sample fact, not a confidence interval).

| Shard | Workload | Engine | Stage | Baseline ns/doc | Candidate ns/doc | Before/after | Disjoint | Baseline Java B/doc | Candidate Java B/doc |
|---:|---|---|---|---:|---:|---:|---|---:|---:|
| 4 | json-scalar | simdjson-typed | ingest | 230.153 (227.840–318.471) | 251.284 (229.088–266.621) | 0.916x | no | 112.000 (112.000–112.000) | 120.000 (120.000–120.000) |
| 4 | json-scalar | simdjson-typed | peak | 6.186 (3.923–6.837) | 3.864 (3.731–5.835) | 1.601x | no | 1.000 (1.000–1.000) | 1.000 (1.000–1.000) |
| 4 | json-scalar | simdjson-specialized | ingest | 212.249 (212.146–225.102) | 212.311 (204.299–222.355) | 1.000x | no | 112.000 (112.000–112.000) | 120.000 (120.000–120.000) |
| 4 | json-scalar | simdjson-specialized | peak | 2.623 (2.568–2.626) | 2.761 (2.523–4.029) | 0.950x | no | 1.000 (1.000–1.000) | 1.000 (1.000–1.000) |
| 4 | json-scalar | gson-stream | ingest | 2998.623 (2821.016–3854.067) | 2060.876 (1898.723–2671.600) | 1.455x | yes | 11288.000 (11288.000–11288.000) | 11288.000 (11288.000–11288.000) |
| 4 | json-scalar | gson-stream | peak | 1.445 (1.423–1.929) | 1.350 (1.302–1.509) | 1.070x | no | 1.000 (1.000–1.000) | 1.000 (1.000–1.000) |
| 7 | json-scalar | simdjson-typed | read-aot | 5.096 (4.884–5.163) | 5.082 (4.879–5.095) | 1.003x | no | 0.000 (0.000–0.000) | 0.000 (0.000–0.000) |
| 7 | json-scalar | simdjson-typed | interpret | 6.035 (5.682–7.560) | 6.143 (6.011–6.167) | 0.982x | no | 2.000 (2.000–2.000) | 2.000 (2.000–2.000) |
| 7 | json-scalar | simdjson-specialized | read-aot | 9.084 (7.172–10.487) | 8.803 (7.178–10.411) | 1.032x | no | 0.000 (0.000–0.000) | 0.000 (0.000–0.000) |
| 7 | json-scalar | simdjson-specialized | interpret | 8.834 (8.145–9.154) | 9.412 (8.469–10.925) | 0.939x | no | 2.000 (2.000–2.000) | 2.000 (2.000–2.000) |
| 7 | json-scalar | gson-stream | read-aot | 1.512 (1.482–2.090) | 1.508 (1.464–1.576) | 1.003x | no | 0.000 (0.000–0.000) | 0.000 (0.000–0.000) |
| 7 | json-scalar | gson-stream | interpret | 1.908 (1.897–2.164) | 1.915 (1.847–2.347) | 0.996x | no | 2.000 (2.000–2.000) | 2.000 (2.000–2.000) |
