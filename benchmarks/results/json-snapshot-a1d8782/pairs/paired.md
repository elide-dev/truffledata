# Paired JSON SVM results

Medians of three fresh-process fork medians; parentheses are fork-median ranges. Every before/after cell shares its host and pinned CPU; different shards may use different hosts. "Disjoint" says the two fork-median ranges do not overlap (a sample fact, not a confidence interval).

| Shard | Workload | Engine | Stage | Baseline ns/doc | Candidate ns/doc | Before/after | Disjoint | Baseline Java B/doc | Candidate Java B/doc |
|---:|---|---|---|---:|---:|---:|---|---:|---:|
| 4 | json-scalar | simdjson-typed | ingest | 237.382 (236.289–239.541) | 236.006 (234.917–236.885) | 1.006x | no | 112.000 (112.000–112.000) | 112.000 (112.000–112.000) |
| 4 | json-scalar | simdjson-typed | peak | 4.364 (4.362–4.411) | 4.379 (4.359–4.395) | 0.996x | no | 1.000 (1.000–1.000) | 1.000 (1.000–1.000) |
| 4 | json-scalar | simdjson-specialized | ingest | 236.256 (234.935–238.798) | 235.388 (235.378–237.839) | 1.004x | no | 112.000 (112.000–112.000) | 112.000 (112.000–112.000) |
| 4 | json-scalar | simdjson-specialized | peak | 2.752 (2.749–2.761) | 2.751 (2.739–2.771) | 1.000x | no | 1.000 (1.000–1.000) | 1.000 (1.000–1.000) |
| 4 | json-scalar | gson-stream | ingest | 2978.075 (2020.336–3690.738) | 10526.314 (1913.839–24272.645) | 0.283x | no | 11288.000 (11288.000–11288.000) | 11288.000 (11288.000–11288.000) |
| 4 | json-scalar | gson-stream | peak | 1.360 (1.349–1.362) | 1.354 (1.350–1.356) | 1.004x | no | 1.000 (1.000–1.000) | 1.000 (1.000–1.000) |
| 6 | json-generic | simdjson-snapshot | ingest | 1491.890 (1479.910–1505.824) | 1297.703 (1294.471–1316.641) | 1.150x | yes | 1183.500 (1183.500–1183.500) | 1183.500 (1183.500–1183.500) |
| 6 | json-generic | simdjson-snapshot | read-aot | 165.871 (165.631–166.833) | 166.216 (165.809–167.779) | 0.998x | no | 240.000 (240.000–240.000) | 240.000 (240.000–240.000) |
| 6 | json-generic | simdjson-snapshot | interpret | 167.902 (166.806–169.659) | 170.366 (169.399–173.055) | 0.986x | no | 242.000 (242.000–242.000) | 242.000 (242.000–242.000) |
| 6 | json-generic | simdjson-snapshot | peak | 41.605 (41.124–44.032) | 41.185 (41.022–41.473) | 1.010x | no | 1.000 (1.000–1.000) | 1.000 (1.000–1.000) |
| 6 | json-generic | simdjson-stream-interop | ingest | 620.549 (617.420–624.007) | 593.623 (592.915–607.643) | 1.045x | yes | 304.750 (304.750–304.750) | 304.750 (304.750–304.750) |
| 6 | json-generic | simdjson-generic-interop | ingest | 880.635 (867.042–881.346) | 862.821 (860.580–887.282) | 1.021x | no | 384.000 (384.000–384.000) | 384.000 (384.000–384.000) |
| 6 | json-generic | simdjson-generic-interop | peak | 28.431 (28.340–28.444) | 28.361 (28.276–28.421) | 1.002x | no | 1.000 (1.000–1.000) | 1.000 (1.000–1.000) |
| 6 | json-generic | simdjson-generic-decode | ingest | (candidate only) | 588.795 (580.250–20863.977) | — | — | — | 120.000 (120.000–120.000) |
| 7 | json-scalar | simdjson-typed | read-aot | 4.602 (4.598–4.634) | 4.588 (4.570–4.654) | 1.003x | no | 0.000 (0.000–0.000) | 0.000 (0.000–0.000) |
| 7 | json-scalar | simdjson-typed | interpret | 5.129 (5.126–5.278) | 5.140 (5.133–5.266) | 0.998x | no | 2.000 (2.000–2.000) | 2.000 (2.000–2.000) |
| 7 | json-scalar | simdjson-specialized | read-aot | 6.686 (6.642–6.809) | 6.670 (6.621–6.681) | 1.002x | no | 0.000 (0.000–0.000) | 0.000 (0.000–0.000) |
| 7 | json-scalar | simdjson-specialized | interpret | 7.450 (7.438–7.482) | 7.614 (7.532–7.665) | 0.978x | yes | 2.000 (2.000–2.000) | 2.000 (2.000–2.000) |
| 7 | json-scalar | gson-stream | read-aot | 1.340 (1.338–1.350) | 1.354 (1.341–29.559) | 0.990x | no | 0.000 (0.000–0.000) | 0.000 (0.000–0.000) |
| 7 | json-scalar | gson-stream | interpret | 1.744 (1.743–1.744) | 1.751 (1.746–12.435) | 0.996x | yes | 2.000 (2.000–2.000) | 2.000 (2.000–2.000) |
