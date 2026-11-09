# SVM JSON comparison

Medians of three fresh-process fork medians; ranges are fork medians. Java heap excludes native memory.

## json-scalar

| Stage | Engine | ns/record (range) | Java bytes/record (range) |
|---|---|---:|---:|
| ingest | gson-stream | 911.745 (889.698–912.063) | 11288.000 (11288.000–11288.000) |
| ingest | simdjson-typed | 177.763 (177.639–183.894) | 112.000 (112.000–112.000) |
| ingest | simdjson-specialized | 190.597 (184.303–191.759) | 112.000 (112.000–112.000) |
| read-aot | gson-stream | 0.972 (0.967–1.030) | 0.000 (0.000–0.000) |
| read-aot | simdjson-typed | 3.568 (3.561–3.577) | 0.000 (0.000–0.000) |
| read-aot | simdjson-specialized | 4.851 (4.851–5.155) | 0.000 (0.000–0.000) |
| interpret | gson-stream | 1.257 (1.254–1.333) | 2.000 (2.000–2.000) |
| interpret | simdjson-typed | 4.003 (3.720–4.007) | 2.000 (2.000–2.000) |
| interpret | simdjson-specialized | 5.269 (5.249–5.382) | 2.000 (2.000–2.000) |
| peak | gson-stream | 1.029 (0.967–1.034) | 1.000 (1.000–1.000) |
| peak | simdjson-typed | 3.069 (3.064–3.318) | 1.000 (1.000–1.000) |
| peak | simdjson-specialized | 2.140 (2.000–2.143) | 1.000 (1.000–1.000) |

## json-generic

| Stage | Engine | ns/document (range) | Java bytes/document (range) |
|---|---|---:|---:|
| ingest | gson-tree | 2015.116 (1942.558–2021.212) | 13404.000 (13404.000–13404.000) |
| ingest | simdjson-generic-java | 1688.695 (1680.931–1779.259) | 2528.000 (2528.000–2528.000) |
| ingest | simdjson-generic-interop | 826.653 (824.839–827.813) | 480.000 (480.000–480.000) |
| ingest | simdjson-snapshot | 1471.700 (1469.895–1543.192) | 2101.500 (2101.500–2101.500) |
| ingest | simdjson-session-java | 1704.277 (1629.267–1726.729) | 1240.000 (1240.000–1240.000) |
| ingest | simdjson-session-truffle | 1706.550 (1698.902–1853.464) | 1652.500 (1652.500–1652.500) |
| ingest | gson-stream-lines | 2042.524 (2014.111–2061.341) | 13404.000 (13404.000–13404.000) |
| ingest | simdjson-stream-java | 1617.735 (1517.212–1644.516) | 1218.500 (1218.500–1218.500) |
| ingest | simdjson-stream-interop | 658.415 (655.810–693.393) | 312.750 (312.750–312.750) |
| ingest | jackson-tree | 1162.810 (1155.520–1178.773) | 2245.000 (2245.000–2245.000) |
| ingest | jackson-stream | 657.265 (656.538–694.269) | 808.000 (808.000–808.000) |
| ingest | fastjson2-tree | 582.798 (580.989–628.653) | 1372.000 (1372.000–1372.000) |
| ingest | dsljson-map | 752.343 (751.527–755.436) | 1080.000 (1080.000–1080.000) |
| ingest | jsoniter-any | 1061.317 (1058.740–1071.060) | 1508.000 (1508.000–1508.000) |
| read-aot | gson-tree | 227.754 (227.571–232.972) | 80.000 (80.000–80.000) |
| read-aot | simdjson-generic-java | 920.332 (918.909–922.207) | 1168.000 (1168.000–1168.000) |
| read-aot | simdjson-generic-interop | 152.851 (152.274–161.472) | 240.000 (240.000–240.000) |
| read-aot | simdjson-snapshot | 160.780 (159.763–164.536) | 240.000 (240.000–240.000) |
| read-aot | jackson-tree | 59.972 (59.798–62.483) | 0.000 (0.000–0.000) |
| read-aot | fastjson2-tree | 78.693 (77.753–79.048) | 32.000 (32.000–32.000) |
| read-aot | dsljson-map | 85.872 (82.215–86.021) | 0.000 (0.000–0.000) |
| read-aot | jsoniter-any | 110.754 (106.846–113.303) | 80.000 (80.000–80.000) |
| interpret | gson-tree | 218.834 (214.533–220.891) | 82.000 (82.000–82.000) |
| interpret | simdjson-generic-java | 918.688 (917.419–968.321) | 1170.000 (1170.000–1170.000) |
| interpret | simdjson-generic-interop | 153.825 (153.170–165.202) | 242.000 (242.000–242.000) |
| interpret | simdjson-snapshot | 167.644 (160.209–169.807) | 242.000 (242.000–242.000) |
| interpret | jackson-tree | 64.767 (61.449–65.427) | 2.000 (2.000–2.000) |
| interpret | fastjson2-tree | 79.789 (78.478–80.148) | 34.000 (34.000–34.000) |
| interpret | dsljson-map | 88.898 (88.098–89.595) | 2.000 (2.000–2.000) |
| interpret | jsoniter-any | 114.513 (109.239–116.965) | 82.000 (82.000–82.000) |
| peak | gson-tree | 218.036 (215.977–227.362) | 81.000 (81.000–81.000) |
| peak | simdjson-generic-java | 945.081 (941.744–985.376) | 1169.000 (1169.000–1169.000) |
| peak | simdjson-generic-interop | 91.301 (87.065–95.763) | 1.000 (1.000–1.000) |
| peak | simdjson-snapshot | 99.790 (94.713–100.392) | 1.000 (1.000–1.000) |
| peak | jackson-tree | 63.157 (61.588–63.516) | 1.000 (1.000–1.000) |
| peak | fastjson2-tree | 81.711 (78.204–82.067) | 33.000 (33.000–33.000) |
| peak | dsljson-map | 84.356 (82.445–87.753) | 1.000 (1.000–1.000) |
| peak | jsoniter-any | 108.202 (106.563–115.299) | 81.000 (81.000–81.000) |
