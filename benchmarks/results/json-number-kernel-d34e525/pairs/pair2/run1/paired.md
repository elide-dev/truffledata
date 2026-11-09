# Paired JSON SVM results

Medians of three fresh-process fork medians; parentheses are fork-median ranges. Every before/after cell shares its host and pinned CPU; different shards may use different hosts. "Disjoint" says the two fork-median ranges do not overlap (a sample fact, not a confidence interval).

| Shard | Workload | Engine | Stage | Baseline ns/doc | Candidate ns/doc | Before/after | Disjoint | Baseline Java B/doc | Candidate Java B/doc |
|---:|---|---|---|---:|---:|---:|---|---:|---:|
| 8 | json-numeric | gson-numeric | ingest | 60101.406 (55580.760–61937.671) | 56711.962 (55945.150–62023.584) | 1.060x | no | 59398.500 (59398.500–59398.500) | 59398.500 (59398.500–59398.500) |
| 8 | json-numeric | simdjson-numeric-interop | ingest | 16392.576 (15071.639–16710.164) | 17371.781 (17306.610–20921.203) | 0.944x | yes | 2144.000 (2144.000–2144.000) | 2144.000 (2144.000–2144.000) |
| 8 | json-numeric | simdjson-numeric-decode | ingest | 13663.933 (12461.992–14099.276) | 14534.697 (13334.336–15513.577) | 0.940x | no | 120.000 (120.000–120.000) | 120.000 (120.000–120.000) |
| 8 | json-numeric | jackson-numeric | ingest | 163484.691 (161518.906–256924.582) | 230333.633 (160499.734–248443.082) | 0.710x | no | 106516.500 (106516.500–106516.500) | 106516.500 (106516.500–106516.500) |
| 8 | json-numeric | fastjson2-numeric | ingest | 37100.483 (35396.157–39484.807) | 36743.182 (35496.500–38084.004) | 1.010x | no | 23201.000 (23201.000–23201.000) | 23201.000 (23201.000–23201.000) |
| 8 | json-generic | simdjson-generic-interop | ingest | 2289.320 (1997.487–2765.278) | 1999.114 (1866.118–2336.339) | 1.145x | no | 384.000 (384.000–384.000) | 384.000 (384.000–384.000) |
| 8 | json-generic | simdjson-generic-decode | ingest | 1078.007 (1052.313–1079.152) | 1132.657 (1091.577–1166.265) | 0.952x | yes | 120.000 (120.000–120.000) | 120.000 (120.000–120.000) |
