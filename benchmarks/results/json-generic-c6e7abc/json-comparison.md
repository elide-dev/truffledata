# SVM JSON comparison

Medians of three fresh-process fork medians; ranges are fork medians. Java heap excludes native memory.

## json-scalar

| Stage | Engine | ns/record (range) | Java bytes/record (range) |
|---|---|---:|---:|
| ingest | gson-stream | 2209.271 (2156.845–3172.513) | 11288.000 (11288.000–11288.000) |
| ingest | simdjson-typed | 462.038 (421.239–559.839) | 56.000 (56.000–56.000) |
| ingest | simdjson-specialized | 423.562 (423.487–428.652) | 56.000 (56.000–56.000) |
| read-aot | gson-stream | 2.306 (2.270–2.311) | 0.000 (0.000–0.000) |
| read-aot | simdjson-typed | 7.351 (7.071–9.717) | 0.000 (0.000–0.000) |
| read-aot | simdjson-specialized | 8.420 (8.401–8.426) | 0.000 (0.000–0.000) |
| interpret | gson-stream | 2.671 (2.658–2.700) | 2.000 (2.000–2.000) |
| interpret | simdjson-typed | 8.409 (8.402–8.429) | 2.000 (2.000–2.000) |
| interpret | simdjson-specialized | 9.896 (9.668–17.990) | 2.000 (2.000–2.000) |
| peak | gson-stream | 2.014 (1.987–2.167) | 1.000 (1.000–1.000) |
| peak | simdjson-typed | 6.159 (6.088–6.754) | 1.000 (1.000–1.000) |
| peak | simdjson-specialized | 3.584 (3.580–3.617) | 1.000 (1.000–1.000) |

## json-generic

| Stage | Engine | ns/document (range) | Java bytes/document (range) |
|---|---|---:|---:|
| ingest | gson-tree | 4707.460 (4528.101–8117.252) | 13404.000 (13404.000–13404.000) |
| ingest | simdjson-generic-java | 5887.642 (5807.362–8961.825) | 3660.000 (3660.000–3660.000) |
| ingest | simdjson-generic-interop | 5622.973 (5600.537–5705.116) | 528.000 (528.000–528.000) |
| ingest | simdjson-snapshot | 45531.989 (41504.903–49488.931) | 32769.500 (32769.500–32769.500) |
| read-aot | gson-tree | 524.560 (521.226–536.702) | 80.000 (80.000–80.000) |
| read-aot | simdjson-generic-java | 2784.669 (2782.150–2860.108) | 2356.000 (2356.000–2356.000) |
| read-aot | simdjson-generic-interop | 2933.026 (2918.711–4000.105) | 240.000 (240.000–240.000) |
| read-aot | simdjson-snapshot | 28024.519 (27977.874–28133.590) | 22988.000 (22988.000–22988.000) |
| interpret | gson-tree | 538.670 (534.093–1010.973) | 82.000 (82.000–82.000) |
| interpret | simdjson-generic-java | 2910.207 (2835.518–2969.908) | 2358.000 (2358.000–2358.000) |
| interpret | simdjson-generic-interop | 2928.353 (2921.476–2934.047) | 242.000 (242.000–242.000) |
| interpret | simdjson-snapshot | 28512.480 (28002.995–33272.129) | 22990.000 (22990.000–22990.000) |
| peak | gson-tree | 530.433 (527.960–535.188) | 81.000 (81.000–81.000) |
| peak | simdjson-generic-java | 2935.126 (2911.443–3148.256) | 2357.000 (2357.000–2357.000) |
| peak | simdjson-generic-interop | 2741.339 (2701.412–2764.508) | 1.000 (1.000–1.000) |
| peak | simdjson-snapshot | 28260.919 (28085.045–28667.988) | 22749.000 (22749.000–22749.000) |
