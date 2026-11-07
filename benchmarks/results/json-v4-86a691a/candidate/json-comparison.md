# SVM JSON comparison

Medians of three fresh-process fork medians; ranges are fork medians. Java heap excludes native memory.

## json-scalar

| Stage | Engine | ns/record (range) | Java bytes/record (range) |
|---|---|---:|---:|
| ingest | gson-stream | 892.590 (868.680–899.166) | 11288.000 (11288.000–11288.000) |
| ingest | simdjson-typed | 185.396 (183.023–185.710) | 112.000 (112.000–112.000) |
| ingest | simdjson-specialized | 182.039 (181.377–187.027) | 112.000 (112.000–112.000) |
| read-aot | gson-stream | 0.965 (0.964–1.025) | 0.000 (0.000–0.000) |
| read-aot | simdjson-typed | 3.558 (3.547–3.702) | 0.000 (0.000–0.000) |
| read-aot | simdjson-specialized | 5.077 (4.919–5.114) | 0.000 (0.000–0.000) |
| interpret | gson-stream | 1.335 (1.262–1.336) | 2.000 (2.000–2.000) |
| interpret | simdjson-typed | 3.962 (3.697–4.062) | 2.000 (2.000–2.000) |
| interpret | simdjson-specialized | 5.323 (5.251–5.895) | 2.000 (2.000–2.000) |
| peak | gson-stream | 0.967 (0.965–1.026) | 1.000 (1.000–1.000) |
| peak | simdjson-typed | 3.047 (3.044–3.311) | 1.000 (1.000–1.000) |
| peak | simdjson-specialized | 2.073 (2.011–2.141) | 1.000 (1.000–1.000) |

## json-generic

| Stage | Engine | ns/document (range) | Java bytes/document (range) |
|---|---|---:|---:|
| ingest | gson-tree | 1970.780 (1958.411–2015.271) | 13404.000 (13404.000–13404.000) |
| ingest | simdjson-generic-java | 1690.083 (1679.051–1777.537) | 2528.000 (2528.000–2528.000) |
| ingest | simdjson-generic-interop | 769.432 (764.390–806.053) | 480.000 (480.000–480.000) |
| ingest | simdjson-snapshot | 1551.321 (1491.383–1605.605) | 2101.500 (2101.500–2101.500) |
| ingest | simdjson-session-java | 1688.954 (1577.931–1693.518) | 1240.000 (1240.000–1240.000) |
| ingest | simdjson-session-truffle | 1767.926 (1681.140–1793.253) | 1652.500 (1652.500–1652.500) |
| ingest | gson-stream-lines | 1998.565 (1997.144–2020.197) | 13404.000 (13404.000–13404.000) |
| ingest | simdjson-stream-java | 1631.117 (1559.415–1633.649) | 1218.500 (1218.500–1218.500) |
| ingest | simdjson-stream-interop | 660.800 (660.723–701.577) | 312.750 (312.750–312.750) |
| ingest | jackson-tree | 1251.718 (1193.103–1261.453) | 2245.000 (2245.000–2245.000) |
| ingest | jackson-stream | 693.560 (693.288–698.945) | 808.000 (808.000–808.000) |
| ingest | fastjson2-tree | 648.473 (611.788–654.024) | 1372.000 (1372.000–1372.000) |
| ingest | dsljson-map | 750.898 (720.803–766.094) | 1080.000 (1080.000–1080.000) |
| ingest | jsoniter-any | 1138.347 (1135.124–1140.041) | 1508.000 (1508.000–1508.000) |
| read-aot | gson-tree | 212.194 (211.852–233.377) | 80.000 (80.000–80.000) |
| read-aot | simdjson-generic-java | 930.711 (926.234–978.890) | 1168.000 (1168.000–1168.000) |
| read-aot | simdjson-generic-interop | 158.826 (149.582–159.480) | 240.000 (240.000–240.000) |
| read-aot | simdjson-snapshot | 165.252 (164.492–165.256) | 240.000 (240.000–240.000) |
| read-aot | jackson-tree | 59.950 (59.941–63.581) | 0.000 (0.000–0.000) |
| read-aot | fastjson2-tree | 89.397 (82.628–89.594) | 32.000 (32.000–32.000) |
| read-aot | dsljson-map | 83.356 (82.883–89.285) | 0.000 (0.000–0.000) |
| read-aot | jsoniter-any | 116.739 (113.576–117.300) | 80.000 (80.000–80.000) |
| interpret | gson-tree | 227.793 (224.128–235.038) | 82.000 (82.000–82.000) |
| interpret | simdjson-generic-java | 923.582 (920.156–930.331) | 1170.000 (1170.000–1170.000) |
| interpret | simdjson-generic-interop | 150.900 (150.196–152.997) | 242.000 (242.000–242.000) |
| interpret | simdjson-snapshot | 159.555 (156.976–160.645) | 242.000 (242.000–242.000) |
| interpret | jackson-tree | 63.056 (61.544–66.068) | 2.000 (2.000–2.000) |
| interpret | fastjson2-tree | 83.327 (82.215–88.891) | 34.000 (34.000–34.000) |
| interpret | dsljson-map | 89.711 (86.582–96.662) | 2.000 (2.000–2.000) |
| interpret | jsoniter-any | 117.235 (108.970–120.804) | 82.000 (82.000–82.000) |
| peak | gson-tree | 223.849 (216.788–228.463) | 81.000 (81.000–81.000) |
| peak | simdjson-generic-java | 930.555 (926.886–973.408) | 1169.000 (1169.000–1169.000) |
| peak | simdjson-generic-interop | 90.925 (86.056–91.304) | 1.000 (1.000–1.000) |
| peak | simdjson-snapshot | 95.306 (94.180–101.283) | 1.000 (1.000–1.000) |
| peak | jackson-tree | 61.226 (60.261–65.883) | 1.000 (1.000–1.000) |
| peak | fastjson2-tree | 86.326 (83.969–86.743) | 33.000 (33.000–33.000) |
| peak | dsljson-map | 84.659 (83.787–88.176) | 1.000 (1.000–1.000) |
| peak | jsoniter-any | 116.131 (110.803–116.722) | 81.000 (81.000–81.000) |
