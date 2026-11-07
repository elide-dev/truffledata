# SVM JSON comparison

Medians of three fresh-process fork medians; ranges are fork medians. Java heap excludes native memory.

## json-scalar

| Stage | Engine | ns/record (range) | Java bytes/record (range) |
|---|---|---:|---:|
| ingest | gson-stream | 3207.984 (2422.303–4544.440) | 11288.000 (11288.000–11288.000) |
| ingest | simdjson-typed | 757.108 (694.621–868.385) | 112.000 (112.000–112.000) |
| ingest | simdjson-specialized | 533.654 (529.872–537.605) | 112.000 (112.000–112.000) |
| read-aot | gson-stream | 3.249 (2.007–3.961) | 0.000 (0.000–0.000) |
| read-aot | simdjson-typed | 6.884 (6.825–6.937) | 0.000 (0.000–0.000) |
| read-aot | simdjson-specialized | 11.742 (11.452–17.017) | 0.000 (0.000–0.000) |
| interpret | gson-stream | 4.475 (3.169–4.662) | 2.000 (2.000–2.000) |
| interpret | simdjson-typed | 8.174 (8.104–8.236) | 2.000 (2.000–2.000) |
| interpret | simdjson-specialized | 13.352 (13.315–24.361) | 2.000 (2.000–2.000) |
| peak | gson-stream | 1.989 (1.987–6.124) | 1.000 (1.000–1.000) |
| peak | simdjson-typed | 10.390 (7.149–12.703) | 1.000 (1.000–1.000) |
| peak | simdjson-specialized | 7.630 (4.503–8.718) | 1.000 (1.000–1.000) |

## json-generic

| Stage | Engine | ns/document (range) | Java bytes/document (range) |
|---|---|---:|---:|
| ingest | gson-tree | 7149.231 (5353.854–7595.224) | 13404.000 (13404.000–13404.000) |
| ingest | simdjson-generic-java | 4859.204 (4741.776–8505.997) | 2520.000 (2520.000–2520.000) |
| ingest | simdjson-generic-interop | 5109.718 (4890.887–5118.031) | 472.000 (472.000–472.000) |
| ingest | simdjson-snapshot | 7192.202 (5453.891–8134.396) | 1997.500 (1997.500–1997.500) |
| ingest | simdjson-session-java | 4399.548 (4383.542–4429.055) | 1360.000 (1360.000–1360.000) |
| read-aot | gson-tree | 781.796 (500.884–831.182) | 80.000 (80.000–80.000) |
| read-aot | simdjson-generic-java | 2939.351 (2855.469–5375.931) | 1168.000 (1168.000–1168.000) |
| read-aot | simdjson-generic-interop | 2512.357 (2096.031–2662.712) | 240.000 (240.000–240.000) |
| read-aot | simdjson-snapshot | 2725.705 (2003.330–2839.038) | 240.000 (240.000–240.000) |
| interpret | gson-tree | 507.374 (506.740–891.890) | 82.000 (82.000–82.000) |
| interpret | simdjson-generic-java | 4933.142 (2895.598–5364.592) | 1170.000 (1170.000–1170.000) |
| interpret | simdjson-generic-interop | 2047.613 (1737.482–2912.648) | 242.000 (242.000–242.000) |
| interpret | simdjson-snapshot | 1867.221 (1852.602–1935.046) | 242.000 (242.000–242.000) |
| peak | gson-tree | 512.949 (508.466–518.063) | 81.000 (81.000–81.000) |
| peak | simdjson-generic-java | 2867.203 (2854.040–3490.499) | 1169.000 (1169.000–1169.000) |
| peak | simdjson-generic-interop | 1774.237 (1621.151–2639.416) | 1.000 (1.000–1.000) |
| peak | simdjson-snapshot | 1803.902 (1557.614–2602.645) | 1.000 (1.000–1.000) |
