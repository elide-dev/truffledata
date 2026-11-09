# Paired JSON SVM results

Medians of three fresh-process fork medians; parentheses are fork-median ranges. Every before/after cell shares its host and pinned CPU; different shards may use different hosts. "Disjoint" says the two fork-median ranges do not overlap (a sample fact, not a confidence interval).

| Shard | Workload | Engine | Stage | Baseline ns/doc | Candidate ns/doc | Before/after | Disjoint | Baseline Java B/doc | Candidate Java B/doc |
|---:|---|---|---|---:|---:|---:|---|---:|---:|
| 4 | json-scalar | simdjson-typed | ingest | 241.622 (239.594–242.084) | 233.987 (216.877–240.978) | 1.033x | no | 112.000 (112.000–112.000) | 120.000 (120.000–120.000) |
| 4 | json-scalar | simdjson-typed | peak | 4.463 (4.454–4.587) | 1.929 (1.920–2.028) | 2.313x | yes | 1.000 (1.000–1.000) | 1.000 (1.000–1.000) |
| 4 | json-scalar | simdjson-specialized | ingest | 247.497 (245.439–258.525) | 232.662 (229.030–233.258) | 1.064x | yes | 112.000 (112.000–112.000) | 120.000 (120.000–120.000) |
| 4 | json-scalar | simdjson-specialized | peak | 2.868 (2.848–2.877) | 1.906 (1.881–1.965) | 1.505x | yes | 1.000 (1.000–1.000) | 1.000 (1.000–1.000) |
| 4 | json-scalar | gson-stream | ingest | 2454.504 (2174.804–6777.012) | 4720.244 (3316.078–9170.209) | 0.520x | no | 11288.000 (11288.000–11288.000) | 11288.000 (11288.000–11288.000) |
| 4 | json-scalar | gson-stream | peak | 1.384 (1.369–1.413) | 1.362 (1.357–1.401) | 1.016x | no | 1.000 (1.000–1.000) | 1.000 (1.000–1.000) |
| 7 | json-scalar | simdjson-typed | read-aot | 5.072 (4.684–5.092) | 2.163 (2.147–2.178) | 2.345x | yes | 0.000 (0.000–0.000) | 0.000 (0.000–0.000) |
| 7 | json-scalar | simdjson-typed | interpret | 5.260 (5.247–5.395) | 2.556 (2.532–2.629) | 2.058x | yes | 2.000 (2.000–2.000) | 2.000 (2.000–2.000) |
| 7 | json-scalar | simdjson-specialized | read-aot | 6.825 (6.742–7.366) | 3.647 (3.601–3.810) | 1.871x | yes | 0.000 (0.000–0.000) | 0.000 (0.000–0.000) |
| 7 | json-scalar | simdjson-specialized | interpret | 7.929 (7.879–7.939) | 4.014 (4.013–4.035) | 1.975x | yes | 2.000 (2.000–2.000) | 2.000 (2.000–2.000) |
| 7 | json-scalar | gson-stream | read-aot | 1.353 (1.352–1.459) | 1.361 (1.354–1.376) | 0.994x | no | 0.000 (0.000–0.000) | 0.000 (0.000–0.000) |
| 7 | json-scalar | gson-stream | interpret | 1.783 (1.767–1.786) | 1.798 (1.798–1.820) | 0.992x | yes | 2.000 (2.000–2.000) | 2.000 (2.000–2.000) |
