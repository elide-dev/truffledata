# SVM JSON comparison

Medians of three fresh-process fork medians; ranges are fork medians. Java heap excludes native memory.

## json-scalar

| Stage | Engine | ns/record (range) | Java bytes/record (range) |
|---|---|---:|---:|
| ingest | gson-stream | 886.385 (885.433–886.601) | 11288.000 (11288.000–11288.000) |
| ingest | simdjson-typed | 178.356 (175.775–186.575) | 112.000 (112.000–112.000) |
| ingest | simdjson-specialized | 179.720 (178.552–192.080) | 112.000 (112.000–112.000) |
| read-aot | gson-stream | 0.968 (0.963–1.038) | 0.000 (0.000–0.000) |
| read-aot | simdjson-typed | 3.329 (3.322–3.437) | 0.000 (0.000–0.000) |
| read-aot | simdjson-specialized | 4.847 (4.826–5.168) | 0.000 (0.000–0.000) |
| interpret | gson-stream | 1.331 (1.258–1.340) | 2.000 (2.000–2.000) |
| interpret | simdjson-typed | 3.991 (3.990–4.005) | 2.000 (2.000–2.000) |
| interpret | simdjson-specialized | 6.067 (5.710–6.235) | 2.000 (2.000–2.000) |
| peak | gson-stream | 1.023 (0.962–1.024) | 1.000 (1.000–1.000) |
| peak | simdjson-typed | 3.051 (3.050–3.313) | 1.000 (1.000–1.000) |
| peak | simdjson-specialized | 2.165 (2.157–2.185) | 1.000 (1.000–1.000) |

## json-generic

| Stage | Engine | ns/document (range) | Java bytes/document (range) |
|---|---|---:|---:|
| ingest | gson-tree | 1840.427 (1818.620–1899.526) | 13404.000 (13404.000–13404.000) |
| ingest | simdjson-generic-java | 1734.140 (1636.809–1747.511) | 2528.000 (2528.000–2528.000) |
| ingest | simdjson-generic-interop | 794.995 (755.059–807.394) | 480.000 (480.000–480.000) |
| ingest | simdjson-snapshot | 1474.065 (1467.043–1535.042) | 2101.500 (2101.500–2101.500) |
| ingest | simdjson-session-java | 1567.579 (1558.909–1665.674) | 1240.000 (1240.000–1240.000) |
| ingest | simdjson-session-truffle | 1626.399 (1600.951–1774.410) | 1652.500 (1652.500–1652.500) |
| ingest | gson-stream-lines | 2020.714 (1978.481–2065.092) | 13404.000 (13404.000–13404.000) |
| ingest | simdjson-stream-java | 1623.017 (1620.388–1661.266) | 1218.500 (1218.500–1218.500) |
| ingest | simdjson-stream-interop | 676.909 (661.470–698.650) | 312.750 (312.750–312.750) |
| ingest | jackson-tree | 1141.704 (1125.928–1211.752) | 2245.000 (2245.000–2245.000) |
| ingest | jackson-stream | 671.264 (661.690–698.824) | 808.000 (808.000–808.000) |
| ingest | fastjson2-tree | 579.667 (561.280–601.366) | 1372.000 (1372.000–1372.000) |
| ingest | dsljson-map | 706.548 (700.759–755.406) | 1080.000 (1080.000–1080.000) |
| ingest | jsoniter-any | 1120.666 (1086.850–1169.020) | 1508.000 (1508.000–1508.000) |
| ingest | simdjson-java | 21885.037 (20690.413–21978.689) | 33428.000 (33428.000–33428.000) |
| read-aot | gson-tree | 239.576 (233.453–243.369) | 80.000 (80.000–80.000) |
| read-aot | simdjson-generic-java | 933.324 (890.666–938.451) | 1168.000 (1168.000–1168.000) |
| read-aot | simdjson-generic-interop | 153.572 (151.445–165.464) | 240.000 (240.000–240.000) |
| read-aot | simdjson-snapshot | 159.076 (158.156–159.344) | 240.000 (240.000–240.000) |
| read-aot | jackson-tree | 60.324 (60.040–63.581) | 0.000 (0.000–0.000) |
| read-aot | fastjson2-tree | 81.459 (78.672–86.415) | 32.000 (32.000–32.000) |
| read-aot | dsljson-map | 87.446 (82.755–87.842) | 0.000 (0.000–0.000) |
| read-aot | jsoniter-any | 109.126 (108.515–112.678) | 80.000 (80.000–80.000) |
| interpret | gson-tree | 235.588 (232.732–239.236) | 82.000 (82.000–82.000) |
| interpret | simdjson-generic-java | 901.633 (887.603–944.012) | 1170.000 (1170.000–1170.000) |
| interpret | simdjson-generic-interop | 150.592 (147.570–160.662) | 242.000 (242.000–242.000) |
| interpret | simdjson-snapshot | 169.524 (167.428–169.731) | 242.000 (242.000–242.000) |
| interpret | jackson-tree | 61.693 (61.091–65.680) | 2.000 (2.000–2.000) |
| interpret | fastjson2-tree | 80.410 (80.300–86.836) | 34.000 (34.000–34.000) |
| interpret | dsljson-map | 89.011 (83.869–90.116) | 2.000 (2.000–2.000) |
| interpret | jsoniter-any | 114.677 (109.279–115.049) | 82.000 (82.000–82.000) |
| peak | gson-tree | 220.398 (219.320–239.409) | 81.000 (81.000–81.000) |
| peak | simdjson-generic-java | 935.480 (892.820–941.192) | 1169.000 (1169.000–1169.000) |
| peak | simdjson-generic-interop | 85.708 (85.675–85.978) | 1.000 (1.000–1.000) |
| peak | simdjson-snapshot | 101.624 (94.618–101.851) | 1.000 (1.000–1.000) |
| peak | jackson-tree | 61.208 (57.737–61.725) | 1.000 (1.000–1.000) |
| peak | fastjson2-tree | 84.311 (80.645–86.521) | 33.000 (33.000–33.000) |
| peak | dsljson-map | 83.069 (83.059–84.847) | 1.000 (1.000–1.000) |
| peak | jsoniter-any | 115.815 (115.411–125.011) | 81.000 (81.000–81.000) |
