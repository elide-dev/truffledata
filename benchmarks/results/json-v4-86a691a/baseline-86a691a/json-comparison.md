# SVM JSON comparison

Medians of three fresh-process fork medians; ranges are fork medians. Java heap excludes native memory.

## json-scalar

| Stage | Engine | ns/record (range) | Java bytes/record (range) |
|---|---|---:|---:|
| ingest | gson-stream | 872.691 (846.360–876.626) | 11288.000 (11288.000–11288.000) |
| ingest | simdjson-typed | 179.765 (178.548–184.821) | 112.000 (112.000–112.000) |
| ingest | simdjson-specialized | 180.939 (179.190–181.761) | 112.000 (112.000–112.000) |
| read-aot | gson-stream | 0.964 (0.963–1.034) | 0.000 (0.000–0.000) |
| read-aot | simdjson-typed | 3.330 (3.326–3.582) | 0.000 (0.000–0.000) |
| read-aot | simdjson-specialized | 4.862 (4.833–5.149) | 0.000 (0.000–0.000) |
| interpret | gson-stream | 1.269 (1.266–1.336) | 2.000 (2.000–2.000) |
| interpret | simdjson-typed | 3.735 (3.727–3.988) | 2.000 (2.000–2.000) |
| interpret | simdjson-specialized | 5.562 (5.206–5.706) | 2.000 (2.000–2.000) |
| peak | gson-stream | 0.967 (0.965–1.028) | 1.000 (1.000–1.000) |
| peak | simdjson-typed | 3.054 (3.049–3.062) | 1.000 (1.000–1.000) |
| peak | simdjson-specialized | 2.006 (2.002–2.108) | 1.000 (1.000–1.000) |

## json-generic

| Stage | Engine | ns/document (range) | Java bytes/document (range) |
|---|---|---:|---:|
| ingest | gson-tree | 1967.806 (1963.879–1974.192) | 13404.000 (13404.000–13404.000) |
| ingest | simdjson-generic-java | 2109.670 (1990.676–2131.534) | 2520.000 (2520.000–2520.000) |
| ingest | simdjson-generic-interop | 1484.879 (1455.657–1506.045) | 472.000 (472.000–472.000) |
| ingest | simdjson-snapshot | 2315.485 (2193.621–2327.010) | 1997.500 (1997.500–1997.500) |
| ingest | simdjson-session-java | 1937.705 (1923.947–2049.416) | 1360.000 (1360.000–1360.000) |
| ingest | simdjson-session-truffle | 2001.554 (1898.129–2006.868) | 1772.500 (1772.500–1772.500) |
| ingest | gson-stream-lines | 2003.871 (1997.559–2095.521) | 13404.000 (13404.000–13404.000) |
| ingest | simdjson-stream-java | 1880.724 (1875.263–1914.419) | 1338.500 (1338.500–1338.500) |
| ingest | simdjson-stream-interop | 1479.249 (1367.625–1480.215) | 432.750 (432.750–432.750) |
| read-aot | gson-tree | 218.964 (210.887–226.085) | 80.000 (80.000–80.000) |
| read-aot | simdjson-generic-java | 1382.147 (1306.708–1386.783) | 1168.000 (1168.000–1168.000) |
| read-aot | simdjson-generic-interop | 888.505 (876.737–934.870) | 240.000 (240.000–240.000) |
| read-aot | simdjson-snapshot | 1008.656 (973.330–1013.289) | 240.000 (240.000–240.000) |
| interpret | gson-tree | 216.563 (211.867–235.004) | 82.000 (82.000–82.000) |
| interpret | simdjson-generic-java | 1311.320 (1309.339–1335.238) | 1170.000 (1170.000–1170.000) |
| interpret | simdjson-generic-interop | 879.114 (877.702–944.308) | 242.000 (242.000–242.000) |
| interpret | simdjson-snapshot | 994.821 (901.246–994.840) | 242.000 (242.000–242.000) |
| peak | gson-tree | 223.859 (213.789–230.774) | 81.000 (81.000–81.000) |
| peak | simdjson-generic-java | 1310.358 (1309.103–1327.492) | 1169.000 (1169.000–1169.000) |
| peak | simdjson-generic-interop | 819.532 (798.830–845.716) | 1.000 (1.000–1.000) |
| peak | simdjson-snapshot | 824.842 (795.900–892.517) | 1.000 (1.000–1.000) |
