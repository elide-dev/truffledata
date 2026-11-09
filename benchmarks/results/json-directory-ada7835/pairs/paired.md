# Paired JSON SVM results

Medians of three fresh-process fork medians; parentheses are fork-median ranges. Every before/after cell shares its host and pinned CPU; different shards may use different hosts. "Disjoint" says the two fork-median ranges do not overlap (a sample fact, not a confidence interval).

| Shard | Workload | Engine | Stage | Baseline ns/doc | Candidate ns/doc | Before/after | Disjoint | Baseline Java B/doc | Candidate Java B/doc |
|---:|---|---|---|---:|---:|---:|---|---:|---:|
| 6 | json-generic | simdjson-snapshot | ingest | 1435.169 (1433.127–1464.728) | 1481.830 (1454.988–1609.064) | 0.969x | no | 1183.500 (1183.500–1183.500) | 1183.500 (1183.500–1183.500) |
| 6 | json-generic | simdjson-snapshot | read-aot | 172.132 (170.081–172.866) | 172.290 (168.242–220.331) | 0.999x | no | 240.000 (240.000–240.000) | 240.000 (240.000–240.000) |
| 6 | json-generic | simdjson-snapshot | interpret | 174.101 (173.663–178.349) | 173.770 (172.049–175.104) | 1.002x | no | 242.000 (242.000–242.000) | 242.000 (242.000–242.000) |
| 6 | json-generic | simdjson-snapshot | peak | 41.762 (41.523–41.774) | 41.724 (41.369–41.820) | 1.001x | no | 1.000 (1.000–1.000) | 1.000 (1.000–1.000) |
| 6 | json-generic | simdjson-stream-interop | ingest | 635.130 (621.299–647.528) | 615.077 (608.212–658.658) | 1.033x | no | 304.750 (304.750–304.750) | 304.750 (304.750–304.750) |
| 6 | json-generic | simdjson-generic-interop | ingest | 897.146 (886.563–1058.992) | 895.696 (892.990–1044.855) | 1.002x | no | 384.000 (384.000–384.000) | 384.000 (384.000–384.000) |
| 6 | json-generic | simdjson-generic-interop | peak | 29.261 (29.104–30.860) | 29.225 (29.074–29.730) | 1.001x | no | 1.000 (1.000–1.000) | 1.000 (1.000–1.000) |
| 6 | json-generic | simdjson-generic-decode | ingest | 594.389 (593.081–597.486) | 588.924 (585.834–590.274) | 1.009x | yes | 120.000 (120.000–120.000) | 120.000 (120.000–120.000) |
