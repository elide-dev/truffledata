# Paired JSON SVM results

Medians of three fresh-process fork medians; parentheses are fork-median ranges. Every before/after cell shares its host and pinned CPU; different shards may use different hosts. "Disjoint" says the two fork-median ranges do not overlap (a sample fact, not a confidence interval).

| Shard | Workload | Engine | Stage | Baseline ns/doc | Candidate ns/doc | Before/after | Disjoint | Baseline Java B/doc | Candidate Java B/doc |
|---:|---|---|---|---:|---:|---:|---|---:|---:|
| 4 | json-scalar | simdjson-typed | ingest | 290.377 (265.832–291.258) | 265.399 (252.821–348.956) | 1.094x | no | 112.000 (112.000–112.000) | 120.000 (120.000–120.000) |
| 4 | json-scalar | simdjson-typed | peak | 5.164 (5.062–5.321) | 2.715 (2.330–3.148) | 1.902x | yes | 1.000 (1.000–1.000) | 1.000 (1.000–1.000) |
| 4 | json-scalar | simdjson-specialized | ingest | 280.586 (274.496–456.379) | 305.188 (274.968–381.441) | 0.919x | no | 112.000 (112.000–112.000) | 120.000 (120.000–120.000) |
| 4 | json-scalar | simdjson-specialized | peak | 4.933 (3.299–5.110) | 3.041 (2.358–3.640) | 1.623x | no | 1.000 (1.000–1.000) | 1.000 (1.000–1.000) |
| 4 | json-scalar | gson-stream | ingest | 2017.234 (1852.458–3682.845) | 2095.234 (1825.229–3766.252) | 0.963x | no | 11288.000 (11288.000–11288.000) | 11288.000 (11288.000–11288.000) |
| 4 | json-scalar | gson-stream | peak | 1.619 (1.610–1.861) | 1.934 (1.539–2.113) | 0.837x | no | 1.000 (1.000–1.000) | 1.000 (1.000–1.000) |
| 7 | json-scalar | simdjson-typed | read-aot | 7.339 (6.202–7.507) | 3.386 (2.617–3.566) | 2.168x | yes | 0.000 (0.000–0.000) | 0.000 (0.000–0.000) |
| 7 | json-scalar | simdjson-typed | interpret | 7.933 (6.336–8.875) | 3.925 (3.879–4.916) | 2.021x | yes | 2.000 (2.000–2.000) | 2.000 (2.000–2.000) |
| 7 | json-scalar | simdjson-specialized | read-aot | 10.429 (7.946–11.693) | 4.378 (4.251–5.986) | 2.382x | yes | 0.000 (0.000–0.000) | 0.000 (0.000–0.000) |
| 7 | json-scalar | simdjson-specialized | interpret | 9.292 (8.884–12.730) | 5.162 (4.601–5.761) | 1.800x | yes | 2.000 (2.000–2.000) | 2.000 (2.000–2.000) |
| 7 | json-scalar | gson-stream | read-aot | 1.713 (1.612–2.054) | 1.726 (1.555–2.306) | 0.992x | no | 0.000 (0.000–0.000) | 0.000 (0.000–0.000) |
| 7 | json-scalar | gson-stream | interpret | 2.174 (1.989–2.559) | 2.255 (2.013–2.316) | 0.964x | no | 2.000 (2.000–2.000) | 2.000 (2.000–2.000) |
