# SVM JSON comparison

Medians of three fresh-process fork medians; ranges are fork medians. Java heap excludes native memory.

## json-scalar

| Stage | Engine | ns/record (range) | Java bytes/record (range) |
|---|---|---:|---:|
| ingest | gson-stream | 3675.313 (3197.807–3966.073) | 11288.000 (11288.000–11288.000) |
| ingest | simdjson-typed | 596.706 (536.272–615.702) | 112.000 (112.000–112.000) |
| ingest | simdjson-specialized | 577.805 (529.993–606.421) | 112.000 (112.000–112.000) |
| read-aot | gson-stream | 2.283 (2.215–3.250) | 0.000 (0.000–0.000) |
| read-aot | simdjson-typed | 12.654 (9.492–14.452) | 0.000 (0.000–0.000) |
| read-aot | simdjson-specialized | 17.196 (12.236–20.888) | 0.000 (0.000–0.000) |
| interpret | gson-stream | 2.790 (2.616–4.922) | 2.000 (2.000–2.000) |
| interpret | simdjson-typed | 15.248 (8.755–15.637) | 2.000 (2.000–2.000) |
| interpret | simdjson-specialized | 13.954 (13.574–24.398) | 2.000 (2.000–2.000) |
| peak | gson-stream | 2.138 (2.037–2.385) | 1.000 (1.000–1.000) |
| peak | simdjson-typed | 7.373 (7.095–8.037) | 1.000 (1.000–1.000) |
| peak | simdjson-specialized | 4.485 (4.379–4.599) | 1.000 (1.000–1.000) |

## json-generic

| Stage | Engine | ns/document (range) | Java bytes/document (range) |
|---|---|---:|---:|
| ingest | gson-tree | 4733.108 (4684.796–5236.964) | 13404.000 (13404.000–13404.000) |
| ingest | simdjson-generic-java | 5930.810 (4724.510–18608.076) | 2520.000 (2520.000–2520.000) |
| ingest | simdjson-generic-interop | 5164.822 (3103.510–5603.916) | 472.000 (472.000–472.000) |
| ingest | simdjson-snapshot | 4631.124 (4333.359–5932.562) | 1997.500 (1997.500–1997.500) |
| ingest | simdjson-session-java | 4457.509 (4440.617–4986.576) | 1360.000 (1360.000–1360.000) |
| ingest | simdjson-session-truffle | 5048.613 (4579.279–6078.876) | 1772.500 (1772.500–1772.500) |
| read-aot | gson-tree | 740.658 (506.767–886.958) | 80.000 (80.000–80.000) |
| read-aot | simdjson-generic-java | 2942.936 (2917.548–3053.465) | 1168.000 (1168.000–1168.000) |
| read-aot | simdjson-generic-interop | 1836.559 (1783.555–2063.382) | 240.000 (240.000–240.000) |
| read-aot | simdjson-snapshot | 1980.573 (1889.299–2332.634) | 240.000 (240.000–240.000) |
| interpret | gson-tree | 561.933 (510.224–730.578) | 82.000 (82.000–82.000) |
| interpret | simdjson-generic-java | 3504.539 (3000.284–4075.611) | 1170.000 (1170.000–1170.000) |
| interpret | simdjson-generic-interop | 1787.652 (1748.684–1847.894) | 242.000 (242.000–242.000) |
| interpret | simdjson-snapshot | 2828.918 (2688.058–3060.959) | 242.000 (242.000–242.000) |
| peak | gson-tree | 526.506 (516.780–534.964) | 81.000 (81.000–81.000) |
| peak | simdjson-generic-java | 3063.770 (2973.779–5192.120) | 1169.000 (1169.000–1169.000) |
| peak | simdjson-generic-interop | 1681.850 (1490.400–2253.670) | 1.000 (1.000–1.000) |
| peak | simdjson-snapshot | 1547.441 (1544.755–1582.380) | 1.000 (1.000–1.000) |
