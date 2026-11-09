# Paired JSON SVM results

Medians of three fresh-process fork medians; parentheses are fork-median ranges. Every before/after cell shares its host and pinned CPU; different shards may use different hosts. "Disjoint" says the two fork-median ranges do not overlap (a sample fact, not a confidence interval).

| Shard | Workload | Engine | Stage | Baseline ns/doc | Candidate ns/doc | Before/after | Disjoint | Baseline Java B/doc | Candidate Java B/doc |
|---:|---|---|---|---:|---:|---:|---|---:|---:|
| 8 | json-numeric | gson-numeric | ingest | 70160.833 (41529.656–206462.521) | 38819.466 (38170.768–67952.721) | 1.807x | no | 59398.500 (59398.500–59398.500) | 59398.500 (59398.500–59398.500) |
| 8 | json-numeric | simdjson-numeric-interop | ingest | 11409.794 (11283.149–11748.667) | 12504.802 (11495.551–18808.320) | 0.912x | no | 2144.000 (2144.000–2144.000) | 2144.000 (2144.000–2144.000) |
| 8 | json-numeric | simdjson-numeric-decode | ingest | 9637.946 (9154.341–10626.422) | 10804.326 (9635.476–11907.401) | 0.892x | no | 120.000 (120.000–120.000) | 120.000 (120.000–120.000) |
| 8 | json-numeric | jackson-numeric | ingest | 115979.998 (111597.207–204982.070) | 122314.727 (108743.301–123049.436) | 0.948x | no | 106516.500 (106516.500–106516.500) | 106516.500 (106516.500–106516.500) |
| 8 | json-numeric | fastjson2-numeric | ingest | 39863.062 (22025.816–45028.703) | 26194.268 (22333.950–41251.430) | 1.522x | no | 23201.000 (23201.000–23201.000) | 23201.000 (23201.000–23201.000) |
| 8 | json-generic | simdjson-generic-interop | ingest | 1250.521 (1231.303–1342.818) | 1294.676 (1214.702–1352.580) | 0.966x | no | 384.000 (384.000–384.000) | 384.000 (384.000–384.000) |
| 8 | json-generic | simdjson-generic-decode | ingest | 852.297 (773.989–1036.116) | 810.493 (774.348–835.936) | 1.052x | no | 120.000 (120.000–120.000) | 120.000 (120.000–120.000) |
