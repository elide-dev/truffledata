# Paired JSON SVM results

Medians of three fresh-process fork medians; parentheses are fork-median ranges. Every before/after cell shares its host and pinned CPU; different shards may use different hosts. "Disjoint" says the two fork-median ranges do not overlap (a sample fact, not a confidence interval).

| Shard | Workload | Engine | Stage | Baseline ns/doc | Candidate ns/doc | Before/after | Disjoint | Baseline Java B/doc | Candidate Java B/doc |
|---:|---|---|---|---:|---:|---:|---|---:|---:|
| 6 | json-generic | simdjson-snapshot | ingest | 1770.531 (1690.158–1879.114) | 1656.548 (1636.591–1851.518) | 1.069x | no | 1183.500 (1183.500–1183.500) | 1183.500 (1183.500–1183.500) |
| 6 | json-generic | simdjson-snapshot | read-aot | 200.992 (185.908–235.773) | 200.270 (192.117–211.860) | 1.004x | no | 240.000 (240.000–240.000) | 240.000 (240.000–240.000) |
| 6 | json-generic | simdjson-snapshot | interpret | 192.452 (192.291–198.877) | 199.006 (189.054–240.217) | 0.967x | no | 242.000 (242.000–242.000) | 242.000 (242.000–242.000) |
| 6 | json-generic | simdjson-snapshot | peak | 48.184 (45.568–101.212) | 50.460 (43.854–51.425) | 0.955x | no | 1.000 (1.000–1.000) | 1.000 (1.000–1.000) |
| 6 | json-generic | simdjson-stream-interop | ingest | 676.513 (668.368–691.985) | 725.864 (706.618–754.296) | 0.932x | yes | 304.750 (304.750–304.750) | 304.750 (304.750–304.750) |
| 6 | json-generic | simdjson-generic-interop | ingest | 972.284 (964.636–993.877) | 986.008 (888.532–1000.312) | 0.986x | no | 384.000 (384.000–384.000) | 384.000 (384.000–384.000) |
| 6 | json-generic | simdjson-generic-interop | peak | 37.463 (32.698–40.516) | 34.272 (33.494–41.188) | 1.093x | no | 1.000 (1.000–1.000) | 1.000 (1.000–1.000) |
| 6 | json-generic | simdjson-generic-decode | ingest | 654.893 (643.280–718.150) | 705.632 (646.079–754.360) | 0.928x | no | 120.000 (120.000–120.000) | 120.000 (120.000–120.000) |
| 8 | json-numeric | gson-numeric | ingest | 56364.465 (55958.607–60167.898) | 56975.506 (55845.970–57110.810) | 0.989x | no | 59398.500 (59398.500–59398.500) | 59398.500 (59398.500–59398.500) |
| 8 | json-numeric | simdjson-numeric-interop | ingest | 15811.242 (15242.855–18472.685) | 16553.803 (16374.863–16899.709) | 0.955x | no | 2144.000 (2144.000–2144.000) | 2144.000 (2144.000–2144.000) |
| 8 | json-numeric | simdjson-numeric-decode | ingest | 12520.034 (12477.037–12604.447) | 13449.027 (13380.699–14200.219) | 0.931x | yes | 120.000 (120.000–120.000) | 120.000 (120.000–120.000) |
| 8 | json-numeric | jackson-numeric | ingest | 169547.643 (162964.281–177609.418) | 200705.801 (163565.271–216128.676) | 0.845x | no | 106516.500 (106516.500–106516.500) | 106516.500 (106516.500–106516.500) |
| 8 | json-numeric | fastjson2-numeric | ingest | 36025.379 (35990.958–37165.672) | 41138.920 (36809.902–65199.876) | 0.876x | no | 23201.000 (23201.000–23201.000) | 23201.000 (23201.000–23201.000) |
| 8 | json-generic | simdjson-generic-interop | ingest | 1858.661 (1726.906–2233.913) | 1744.405 (1734.420–1853.088) | 1.065x | no | 384.000 (384.000–384.000) | 384.000 (384.000–384.000) |
| 8 | json-generic | simdjson-generic-decode | ingest | 1097.419 (1070.044–1911.345) | 1265.219 (1063.085–1723.437) | 0.867x | no | 120.000 (120.000–120.000) | 120.000 (120.000–120.000) |
