# SVM JSON comparison

Medians of three fresh-process fork medians; ranges are fork medians. Java heap excludes native memory.

## json-scalar

| Stage | Engine | ns/record (range) | Java bytes/record (range) |
|---|---|---:|---:|
| ingest | gson-stream | 892.632 (884.792–894.230) | 11288.000 (11288.000–11288.000) |
| ingest | simdjson-typed | 181.640 (179.734–181.895) | 112.000 (112.000–112.000) |
| ingest | simdjson-specialized | 187.742 (180.072–190.743) | 112.000 (112.000–112.000) |
| read-aot | gson-stream | 0.975 (0.965–0.975) | 0.000 (0.000–0.000) |
| read-aot | simdjson-typed | 3.389 (3.319–3.576) | 0.000 (0.000–0.000) |
| read-aot | simdjson-specialized | 4.838 (4.825–5.137) | 0.000 (0.000–0.000) |
| interpret | gson-stream | 1.261 (1.256–1.358) | 2.000 (2.000–2.000) |
| interpret | simdjson-typed | 3.723 (3.710–3.995) | 2.000 (2.000–2.000) |
| interpret | simdjson-specialized | 5.234 (5.225–5.719) | 2.000 (2.000–2.000) |
| peak | gson-stream | 1.029 (0.971–1.032) | 1.000 (1.000–1.000) |
| peak | simdjson-typed | 3.306 (3.053–3.309) | 1.000 (1.000–1.000) |
| peak | simdjson-specialized | 2.140 (1.999–2.157) | 1.000 (1.000–1.000) |

## json-generic

| Stage | Engine | ns/document (range) | Java bytes/document (range) |
|---|---|---:|---:|
| ingest | gson-tree | 1985.821 (1978.595–2036.663) | 13404.000 (13404.000–13404.000) |
| ingest | simdjson-generic-java | 2094.063 (1991.016–2125.870) | 2520.000 (2520.000–2520.000) |
| ingest | simdjson-generic-interop | 1449.415 (1447.383–1583.579) | 472.000 (472.000–472.000) |
| ingest | simdjson-snapshot | 2180.350 (2180.117–2324.128) | 1997.500 (1997.500–1997.500) |
| ingest | simdjson-session-java | 1930.701 (1907.907–1944.010) | 1360.000 (1360.000–1360.000) |
| ingest | simdjson-session-truffle | 2002.080 (1885.728–2022.801) | 1772.500 (1772.500–1772.500) |
| ingest | gson-stream-lines | 2116.000 (2010.629–2175.628) | 13404.000 (13404.000–13404.000) |
| ingest | simdjson-stream-java | 2036.273 (2006.623–2044.636) | 1338.500 (1338.500–1338.500) |
| ingest | simdjson-stream-interop | 1389.125 (1374.389–1501.696) | 432.750 (432.750–432.750) |
| read-aot | gson-tree | 222.597 (213.437–224.051) | 80.000 (80.000–80.000) |
| read-aot | simdjson-generic-java | 1309.357 (1305.676–1328.864) | 1168.000 (1168.000–1168.000) |
| read-aot | simdjson-generic-interop | 879.029 (864.312–962.896) | 240.000 (240.000–240.000) |
| read-aot | simdjson-snapshot | 934.409 (910.241–942.498) | 240.000 (240.000–240.000) |
| interpret | gson-tree | 225.759 (225.731–228.047) | 82.000 (82.000–82.000) |
| interpret | simdjson-generic-java | 1344.806 (1328.579–1401.917) | 1170.000 (1170.000–1170.000) |
| interpret | simdjson-generic-interop | 891.486 (878.581–933.645) | 242.000 (242.000–242.000) |
| interpret | simdjson-snapshot | 939.180 (909.178–960.408) | 242.000 (242.000–242.000) |
| peak | gson-tree | 221.700 (211.100–222.900) | 81.000 (81.000–81.000) |
| peak | simdjson-generic-java | 1395.578 (1372.148–1395.622) | 1169.000 (1169.000–1169.000) |
| peak | simdjson-generic-interop | 811.063 (773.180–835.177) | 1.000 (1.000–1.000) |
| peak | simdjson-snapshot | 813.038 (786.458–839.898) | 1.000 (1.000–1.000) |
