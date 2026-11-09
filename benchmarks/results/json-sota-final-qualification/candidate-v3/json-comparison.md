# SVM JSON comparison

Medians of three fresh-process fork medians; ranges are fork medians. Java heap excludes native memory.

## json-scalar

| Stage | Engine | ns/record (range) | Java bytes/record (range) |
|---|---|---:|---:|
| ingest | gson-stream | 861.933 (857.461–864.455) | 11288.000 (11288.000–11288.000) |
| ingest | simdjson-typed | 138.319 (136.450–147.721) | 112.000 (112.000–112.000) |
| ingest | simdjson-specialized | 147.612 (145.532–147.830) | 112.000 (112.000–112.000) |
| read-aot | gson-stream | 0.967 (0.964–1.028) | 0.000 (0.000–0.000) |
| read-aot | simdjson-typed | 3.335 (3.332–3.344) | 0.000 (0.000–0.000) |
| read-aot | simdjson-specialized | 5.069 (4.783–5.078) | 0.000 (0.000–0.000) |
| interpret | gson-stream | 1.333 (1.256–1.333) | 2.000 (2.000–2.000) |
| interpret | simdjson-typed | 3.981 (3.946–3.981) | 2.000 (2.000–2.000) |
| interpret | simdjson-specialized | 5.310 (5.304–5.543) | 2.000 (2.000–2.000) |
| peak | gson-stream | 0.964 (0.961–1.024) | 1.000 (1.000–1.000) |
| peak | simdjson-typed | 3.062 (3.059–3.068) | 1.000 (1.000–1.000) |
| peak | simdjson-specialized | 2.012 (2.010–2.017) | 1.000 (1.000–1.000) |

## json-generic

| Stage | Engine | ns/document (range) | Java bytes/document (range) |
|---|---|---:|---:|
| ingest | gson-tree | 1798.070 (1782.065–1943.681) | 13404.000 (13404.000–13404.000) |
| ingest | simdjson-generic-java | 1946.218 (1886.397–1986.134) | 2440.000 (2440.000–2440.000) |
| ingest | simdjson-generic-interop | 809.164 (797.090–850.886) | 384.000 (384.000–384.000) |
| ingest | simdjson-snapshot | 2137.594 (2069.240–2172.836) | 1183.500 (1183.500–1183.500) |
| ingest | simdjson-session-java | 1909.864 (1902.281–1977.125) | 1240.000 (1240.000–1240.000) |
| ingest | simdjson-session-truffle | 1873.307 (1778.368–1875.336) | 1652.500 (1652.500–1652.500) |
| ingest | gson-stream-lines | 1881.905 (1833.699–2000.744) | 13404.000 (13404.000–13404.000) |
| ingest | simdjson-stream-java | 1785.894 (1763.707–1793.398) | 1218.750 (1218.750–1218.750) |
| ingest | simdjson-stream-interop | 1220.304 (1217.085–1249.032) | 304.750 (304.750–304.750) |
| ingest | jackson-tree | 1161.280 (1155.409–1239.416) | 2245.000 (2245.000–2245.000) |
| ingest | jackson-stream | 692.403 (689.947–725.410) | 808.000 (808.000–808.000) |
| ingest | fastjson2-tree | 606.262 (578.285–606.970) | 1372.000 (1372.000–1372.000) |
| ingest | dsljson-map | 729.762 (715.560–754.105) | 1080.000 (1080.000–1080.000) |
| ingest | jsoniter-any | 976.067 (971.428–987.075) | 1508.000 (1508.000–1508.000) |
| ingest | simdjson-java | 21746.723 (19848.118–21817.904) | 33428.000 (33428.000–33428.000) |
| read-aot | gson-tree | 219.518 (216.046–222.668) | 80.000 (80.000–80.000) |
| read-aot | simdjson-generic-java | 1311.237 (1301.133–1324.801) | 1176.000 (1176.000–1176.000) |
| read-aot | simdjson-generic-interop | 235.983 (226.818–249.043) | 240.000 (240.000–240.000) |
| read-aot | simdjson-snapshot | 209.983 (202.207–210.050) | 240.000 (240.000–240.000) |
| read-aot | jackson-tree | 59.709 (59.610–63.673) | 0.000 (0.000–0.000) |
| read-aot | fastjson2-tree | 87.762 (81.977–88.204) | 32.000 (32.000–32.000) |
| read-aot | dsljson-map | 88.212 (86.528–90.409) | 0.000 (0.000–0.000) |
| read-aot | jsoniter-any | 111.278 (110.393–117.031) | 80.000 (80.000–80.000) |
| interpret | gson-tree | 226.359 (215.618–232.578) | 82.000 (82.000–82.000) |
| interpret | simdjson-generic-java | 1225.948 (1221.594–1291.426) | 1178.000 (1178.000–1178.000) |
| interpret | simdjson-generic-interop | 236.188 (227.532–238.715) | 242.000 (242.000–242.000) |
| interpret | simdjson-snapshot | 201.920 (201.191–220.016) | 242.000 (242.000–242.000) |
| interpret | jackson-tree | 63.138 (62.301–63.382) | 2.000 (2.000–2.000) |
| interpret | fastjson2-tree | 86.195 (83.346–86.912) | 34.000 (34.000–34.000) |
| interpret | dsljson-map | 91.233 (87.896–91.246) | 2.000 (2.000–2.000) |
| interpret | jsoniter-any | 114.953 (113.192–116.128) | 82.000 (82.000–82.000) |
| peak | gson-tree | 209.324 (208.340–213.669) | 81.000 (81.000–81.000) |
| peak | simdjson-generic-java | 1051.622 (1046.140–1102.180) | 1177.000 (1177.000–1177.000) |
| peak | simdjson-generic-interop | 25.741 (25.586–27.570) | 1.000 (1.000–1.000) |
| peak | simdjson-snapshot | 37.070 (36.883–39.431) | 1.000 (1.000–1.000) |
| peak | jackson-tree | 59.367 (59.336–65.324) | 1.000 (1.000–1.000) |
| peak | fastjson2-tree | 83.231 (80.185–84.050) | 33.000 (33.000–33.000) |
| peak | dsljson-map | 88.343 (86.686–92.396) | 1.000 (1.000–1.000) |
| peak | jsoniter-any | 118.893 (113.042–119.204) | 81.000 (81.000–81.000) |
