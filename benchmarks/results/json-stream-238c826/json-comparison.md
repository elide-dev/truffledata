# SVM JSON comparison

Medians of three fresh-process fork medians; ranges are fork medians. Java heap excludes native memory.

## json-scalar

| Stage | Engine | ns/record (range) | Java bytes/record (range) |
|---|---|---:|---:|
| ingest | gson-stream | 2652.063 (2103.746–3735.001) | 11288.000 (11288.000–11288.000) |
| ingest | simdjson-typed | 550.546 (511.793–585.279) | 112.000 (112.000–112.000) |
| ingest | simdjson-specialized | 518.561 (517.001–523.307) | 112.000 (112.000–112.000) |
| read-aot | gson-stream | 2.038 (2.031–2.056) | 0.000 (0.000–0.000) |
| read-aot | simdjson-typed | 6.802 (6.799–6.803) | 0.000 (0.000–0.000) |
| read-aot | simdjson-specialized | 11.451 (11.439–11.498) | 0.000 (0.000–0.000) |
| interpret | gson-stream | 2.604 (2.587–2.609) | 2.000 (2.000–2.000) |
| interpret | simdjson-typed | 8.143 (8.128–8.170) | 2.000 (2.000–2.000) |
| interpret | simdjson-specialized | 13.180 (13.130–15.128) | 2.000 (2.000–2.000) |
| peak | gson-stream | 1.980 (1.980–1.990) | 1.000 (1.000–1.000) |
| peak | simdjson-typed | 7.762 (7.118–10.913) | 1.000 (1.000–1.000) |
| peak | simdjson-specialized | 4.397 (4.394–4.615) | 1.000 (1.000–1.000) |

## json-generic

| Stage | Engine | ns/document (range) | Java bytes/document (range) |
|---|---|---:|---:|
| ingest | gson-tree | 4553.725 (4420.546–4582.466) | 13404.000 (13404.000–13404.000) |
| ingest | simdjson-generic-java | 4304.220 (4302.102–4309.700) | 2520.000 (2520.000–2520.000) |
| ingest | simdjson-generic-interop | 3002.884 (2993.342–4114.637) | 472.000 (472.000–472.000) |
| ingest | simdjson-snapshot | 4507.538 (4497.863–4560.191) | 1997.500 (1997.500–1997.500) |
| ingest | simdjson-session-java | 4422.427 (4408.483–4431.566) | 1360.000 (1360.000–1360.000) |
| ingest | simdjson-session-truffle | 4505.441 (4425.125–4773.984) | 1772.500 (1772.500–1772.500) |
| ingest | gson-stream-lines | 4615.124 (4613.985–4677.270) | 13404.000 (13404.000–13404.000) |
| ingest | simdjson-stream-java | 4116.276 (4115.206–4169.641) | 1338.500 (1338.500–1338.500) |
| ingest | simdjson-stream-interop | 2798.200 (2797.219–2829.025) | 432.750 (432.750–432.750) |
| read-aot | gson-tree | 497.678 (497.407–500.073) | 80.000 (80.000–80.000) |
| read-aot | simdjson-generic-java | 2792.859 (2784.945–2919.857) | 1168.000 (1168.000–1168.000) |
| read-aot | simdjson-generic-interop | 1609.564 (1607.269–1610.192) | 240.000 (240.000–240.000) |
| read-aot | simdjson-snapshot | 1822.617 (1819.975–2812.285) | 240.000 (240.000–240.000) |
| interpret | gson-tree | 503.670 (502.873–507.855) | 82.000 (82.000–82.000) |
| interpret | simdjson-generic-java | 2817.774 (2811.142–2821.352) | 1170.000 (1170.000–1170.000) |
| interpret | simdjson-generic-interop | 1620.931 (1614.727–1632.322) | 242.000 (242.000–242.000) |
| interpret | simdjson-snapshot | 1826.424 (1824.451–1840.279) | 242.000 (242.000–242.000) |
| peak | gson-tree | 508.674 (507.835–516.152) | 81.000 (81.000–81.000) |
| peak | simdjson-generic-java | 2848.149 (2841.684–3659.989) | 1169.000 (1169.000–1169.000) |
| peak | simdjson-generic-interop | 1369.745 (1367.505–1376.365) | 1.000 (1.000–1.000) |
| peak | simdjson-snapshot | 1559.450 (1555.938–1569.483) | 1.000 (1.000–1.000) |
