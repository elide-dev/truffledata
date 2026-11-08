# SVM JSON comparison

Medians of three fresh-process fork medians; ranges are fork medians. Java heap excludes native memory.

## json-scalar

| Stage | Engine | ns/record (range) | Java bytes/record (range) |
|---|---|---:|---:|
| ingest | gson-stream | 864.683 (860.126–901.286) | 11288.000 (11288.000–11288.000) |
| ingest | simdjson-typed | 144.919 (136.077–144.964) | 112.000 (112.000–112.000) |
| ingest | simdjson-specialized | 139.789 (138.279–145.838) | 112.000 (112.000–112.000) |
| read-aot | gson-stream | 1.032 (0.982–1.045) | 0.000 (0.000–0.000) |
| read-aot | simdjson-typed | 3.552 (3.546–3.588) | 0.000 (0.000–0.000) |
| read-aot | simdjson-specialized | 5.145 (4.851–5.167) | 0.000 (0.000–0.000) |
| interpret | gson-stream | 1.336 (1.336–1.345) | 2.000 (2.000–2.000) |
| interpret | simdjson-typed | 3.706 (3.696–3.711) | 2.000 (2.000–2.000) |
| interpret | simdjson-specialized | 5.471 (5.445–5.547) | 2.000 (2.000–2.000) |
| peak | gson-stream | 0.964 (0.962–1.040) | 1.000 (1.000–1.000) |
| peak | simdjson-typed | 3.308 (3.064–3.312) | 1.000 (1.000–1.000) |
| peak | simdjson-specialized | 2.002 (2.002–2.143) | 1.000 (1.000–1.000) |

## json-generic

| Stage | Engine | ns/document (range) | Java bytes/document (range) |
|---|---|---:|---:|
| ingest | gson-tree | 1970.692 (1967.361–2059.065) | 13404.000 (13404.000–13404.000) |
| ingest | simdjson-generic-java | 1947.430 (1917.742–2049.226) | 2440.000 (2440.000–2440.000) |
| ingest | simdjson-generic-interop | 820.176 (815.405–896.096) | 384.000 (384.000–384.000) |
| ingest | simdjson-snapshot | 2059.874 (2053.697–2216.778) | 1183.500 (1183.500–1183.500) |
| ingest | simdjson-session-java | 1809.396 (1801.266–1953.852) | 1240.000 (1240.000–1240.000) |
| ingest | simdjson-session-truffle | 1902.973 (1792.499–1918.736) | 1652.500 (1652.500–1652.500) |
| ingest | gson-stream-lines | 2019.623 (2007.637–2025.339) | 13404.000 (13404.000–13404.000) |
| ingest | simdjson-stream-java | 1809.338 (1697.424–1816.001) | 1218.750 (1218.750–1218.750) |
| ingest | simdjson-stream-interop | 1289.081 (1209.223–1302.794) | 304.750 (304.750–304.750) |
| ingest | jackson-tree | 1244.642 (1171.583–1246.274) | 2245.000 (2245.000–2245.000) |
| ingest | jackson-stream | 764.461 (730.026–773.393) | 808.000 (808.000–808.000) |
| ingest | fastjson2-tree | 660.069 (659.417–665.725) | 1372.000 (1372.000–1372.000) |
| ingest | dsljson-map | 723.703 (718.680–774.258) | 1080.000 (1080.000–1080.000) |
| ingest | jsoniter-any | 931.725 (894.087–939.584) | 1508.000 (1508.000–1508.000) |
| read-aot | gson-tree | 223.966 (216.561–237.940) | 80.000 (80.000–80.000) |
| read-aot | simdjson-generic-java | 1242.197 (1227.723–1295.092) | 1176.000 (1176.000–1176.000) |
| read-aot | simdjson-generic-interop | 230.388 (223.314–234.213) | 240.000 (240.000–240.000) |
| read-aot | simdjson-snapshot | 206.348 (197.517–207.641) | 240.000 (240.000–240.000) |
| read-aot | jackson-tree | 65.297 (60.986–66.034) | 0.000 (0.000–0.000) |
| read-aot | fastjson2-tree | 88.914 (81.248–93.208) | 32.000 (32.000–32.000) |
| read-aot | dsljson-map | 86.738 (86.664–89.702) | 0.000 (0.000–0.000) |
| read-aot | jsoniter-any | 111.237 (108.466–117.427) | 80.000 (80.000–80.000) |
| interpret | gson-tree | 231.159 (226.161–231.644) | 82.000 (82.000–82.000) |
| interpret | simdjson-generic-java | 1255.421 (1223.457–1267.573) | 1178.000 (1178.000–1178.000) |
| interpret | simdjson-generic-interop | 235.097 (223.385–237.755) | 242.000 (242.000–242.000) |
| interpret | simdjson-snapshot | 207.266 (203.425–207.513) | 242.000 (242.000–242.000) |
| interpret | jackson-tree | 62.255 (62.125–66.351) | 2.000 (2.000–2.000) |
| interpret | fastjson2-tree | 82.569 (82.519–83.226) | 34.000 (34.000–34.000) |
| interpret | dsljson-map | 87.869 (84.269–89.096) | 2.000 (2.000–2.000) |
| interpret | jsoniter-any | 117.375 (115.255–124.452) | 82.000 (82.000–82.000) |
| peak | gson-tree | 233.075 (221.397–242.828) | 81.000 (81.000–81.000) |
| peak | simdjson-generic-java | 1182.964 (1087.961–1185.600) | 1177.000 (1177.000–1177.000) |
| peak | simdjson-generic-interop | 27.124 (26.124–27.430) | 1.000 (1.000–1.000) |
| peak | simdjson-snapshot | 36.852 (36.738–39.672) | 1.000 (1.000–1.000) |
| peak | jackson-tree | 64.728 (61.452–65.701) | 1.000 (1.000–1.000) |
| peak | fastjson2-tree | 85.424 (81.477–86.253) | 33.000 (33.000–33.000) |
| peak | dsljson-map | 88.031 (87.988–89.446) | 1.000 (1.000–1.000) |
| peak | jsoniter-any | 112.880 (110.033–123.607) | 81.000 (81.000–81.000) |
