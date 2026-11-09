# Paired JSON SVM results

Medians of three fresh-process fork medians; parentheses are fork-median ranges. Every before/after cell shares its host and pinned CPU; different shards may use different hosts. "Disjoint" says the two fork-median ranges do not overlap (a sample fact, not a confidence interval).

| Shard | Workload | Engine | Stage | Baseline ns/doc | Candidate ns/doc | Before/after | Disjoint | Baseline Java B/doc | Candidate Java B/doc |
|---:|---|---|---|---:|---:|---:|---|---:|---:|
| 4 | json-scalar | simdjson-typed | ingest | 272.869 (263.950–326.913) | 318.324 (267.381–339.824) | 0.857x | no | 112.000 (112.000–112.000) | 120.000 (120.000–120.000) |
| 4 | json-scalar | simdjson-typed | peak | 7.041 (6.689–7.606) | 7.207 (5.667–7.258) | 0.977x | no | 1.000 (1.000–1.000) | 1.000 (1.000–1.000) |
| 4 | json-scalar | simdjson-specialized | ingest | 471.860 (453.630–663.279) | 275.336 (273.708–364.550) | 1.714x | yes | 112.000 (112.000–112.000) | 120.000 (120.000–120.000) |
| 4 | json-scalar | simdjson-specialized | peak | 4.214 (3.391–4.577) | 3.974 (3.683–4.048) | 1.061x | no | 1.000 (1.000–1.000) | 1.000 (1.000–1.000) |
| 4 | json-scalar | gson-stream | ingest | 3173.471 (2275.361–5197.807) | 2533.453 (1887.597–5207.724) | 1.253x | no | 11288.000 (11288.000–11288.000) | 11288.000 (11288.000–11288.000) |
| 4 | json-scalar | gson-stream | peak | 1.768 (1.646–2.441) | 2.328 (1.852–2.731) | 0.760x | no | 1.000 (1.000–1.000) | 1.000 (1.000–1.000) |
| 7 | json-scalar | simdjson-typed | read-aot | 9.888 (5.293–11.202) | 10.645 (7.873–10.694) | 0.929x | no | 0.000 (0.000–0.000) | 0.000 (0.000–0.000) |
| 7 | json-scalar | simdjson-typed | interpret | 8.008 (6.450–11.822) | 8.396 (6.046–13.287) | 0.954x | no | 2.000 (2.000–2.000) | 2.000 (2.000–2.000) |
| 7 | json-scalar | simdjson-specialized | read-aot | 8.579 (8.238–14.589) | 12.850 (9.113–14.773) | 0.668x | no | 0.000 (0.000–0.000) | 0.000 (0.000–0.000) |
| 7 | json-scalar | simdjson-specialized | interpret | 10.585 (9.417–18.431) | 10.813 (10.023–11.451) | 0.979x | no | 2.000 (2.000–2.000) | 2.000 (2.000–2.000) |
| 7 | json-scalar | gson-stream | read-aot | 1.466 (1.439–2.305) | 2.422 (1.474–2.429) | 0.605x | no | 0.000 (0.000–0.000) | 0.000 (0.000–0.000) |
| 7 | json-scalar | gson-stream | interpret | 2.403 (1.937–3.825) | 2.451 (2.149–2.937) | 0.980x | no | 2.000 (2.000–2.000) | 2.000 (2.000–2.000) |
