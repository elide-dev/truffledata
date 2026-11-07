# SVM JSON comparison

Medians of three fresh-process fork medians; ranges are fork medians. Java heap excludes native memory.

## json-scalar

| Stage | Engine | ns/record (range) | Java bytes/record (range) |
|---|---|---:|---:|
| ingest | gson-stream | 4132.230 (3178.213–5139.893) | 11288.000 (11288.000–11288.000) |
| ingest | simdjson-typed | 738.341 (524.461–935.283) | 112.000 (112.000–112.000) |
| ingest | simdjson-specialized | 1005.957 (916.420–1018.638) | 112.000 (112.000–112.000) |
| read-aot | gson-stream | 3.965 (2.086–4.493) | 0.000 (0.000–0.000) |
| read-aot | simdjson-typed | 13.053 (12.927–13.188) | 0.000 (0.000–0.000) |
| read-aot | simdjson-specialized | 21.495 (18.509–22.451) | 0.000 (0.000–0.000) |
| interpret | gson-stream | 2.867 (2.620–6.151) | 2.000 (2.000–2.000) |
| interpret | simdjson-typed | 14.731 (8.977–15.846) | 2.000 (2.000–2.000) |
| interpret | simdjson-specialized | 23.266 (13.343–28.466) | 2.000 (2.000–2.000) |
| peak | gson-stream | 4.604 (4.125–10.842) | 1.000 (1.000–1.000) |
| peak | simdjson-typed | 7.912 (7.460–15.086) | 1.000 (1.000–1.000) |
| peak | simdjson-specialized | 9.316 (4.479–11.057) | 1.000 (1.000–1.000) |

## json-generic

| Stage | Engine | ns/document (range) | Java bytes/document (range) |
|---|---|---:|---:|
| ingest | gson-tree | 7012.243 (6402.908–10865.678) | 13404.000 (13404.000–13404.000) |
| ingest | simdjson-generic-java | 8012.200 (4674.209–8032.703) | 3604.000 (3604.000–3604.000) |
| ingest | simdjson-generic-interop | 4637.431 (3091.268–5128.280) | 472.000 (472.000–472.000) |
| ingest | simdjson-snapshot | 8895.463 (4599.718–11238.703) | 1997.500 (1997.500–1997.500) |
| ingest | simdjson-session-java | 7965.148 (5945.822–7975.479) | 3588.000 (3588.000–3588.000) |
| read-aot | gson-tree | 534.771 (499.022–837.842) | 80.000 (80.000–80.000) |
| read-aot | simdjson-generic-java | 5412.219 (5152.251–6429.559) | 2356.000 (2356.000–2356.000) |
| read-aot | simdjson-generic-interop | 2720.207 (1838.784–2856.015) | 240.000 (240.000–240.000) |
| read-aot | simdjson-snapshot | 3152.580 (1826.124–3645.993) | 240.000 (240.000–240.000) |
| interpret | gson-tree | 862.610 (504.457–878.168) | 82.000 (82.000–82.000) |
| interpret | simdjson-generic-java | 2847.475 (2808.857–4935.250) | 2358.000 (2358.000–2358.000) |
| interpret | simdjson-generic-interop | 2782.626 (2656.852–3837.315) | 242.000 (242.000–242.000) |
| interpret | simdjson-snapshot | 2874.496 (2040.889–3062.102) | 242.000 (242.000–242.000) |
| peak | gson-tree | 835.717 (824.340–1109.874) | 81.000 (81.000–81.000) |
| peak | simdjson-generic-java | 2903.506 (2849.941–5359.519) | 2357.000 (2357.000–2357.000) |
| peak | simdjson-generic-interop | 1696.378 (1504.759–2619.443) | 1.000 (1.000–1.000) |
| peak | simdjson-snapshot | 1562.472 (1559.577–2485.337) | 1.000 (1.000–1.000) |
