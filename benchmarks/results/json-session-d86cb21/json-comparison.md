# SVM JSON comparison

Medians of three fresh-process fork medians; ranges are fork medians. Java heap excludes native memory.

## json-scalar

| Stage | Engine | ns/record (range) | Java bytes/record (range) |
|---|---|---:|---:|
| ingest | gson-stream | 3569.593 (2210.946–3769.188) | 11288.000 (11288.000–11288.000) |
| ingest | simdjson-typed | 526.305 (523.838–566.724) | 112.000 (112.000–112.000) |
| ingest | simdjson-specialized | 532.374 (531.532–554.157) | 112.000 (112.000–112.000) |
| read-aot | gson-stream | 2.049 (2.034–2.087) | 0.000 (0.000–0.000) |
| read-aot | simdjson-typed | 6.837 (6.820–12.502) | 0.000 (0.000–0.000) |
| read-aot | simdjson-specialized | 12.004 (11.474–20.982) | 0.000 (0.000–0.000) |
| interpret | gson-stream | 2.781 (2.779–4.474) | 2.000 (2.000–2.000) |
| interpret | simdjson-typed | 9.183 (8.147–14.936) | 2.000 (2.000–2.000) |
| interpret | simdjson-specialized | 13.229 (13.139–13.235) | 2.000 (2.000–2.000) |
| peak | gson-stream | 2.001 (1.990–6.232) | 1.000 (1.000–1.000) |
| peak | simdjson-typed | 7.595 (7.211–7.772) | 1.000 (1.000–1.000) |
| peak | simdjson-specialized | 4.520 (4.426–7.884) | 1.000 (1.000–1.000) |

## json-generic

| Stage | Engine | ns/document (range) | Java bytes/document (range) |
|---|---|---:|---:|
| ingest | gson-tree | 5445.843 (5361.886–5625.613) | 13404.000 (13404.000–13404.000) |
| ingest | simdjson-generic-java | 4488.017 (4468.996–7949.123) | 3604.000 (3604.000–3604.000) |
| ingest | simdjson-generic-interop | 5017.642 (4603.770–7300.035) | 472.000 (472.000–472.000) |
| ingest | simdjson-snapshot | 6357.776 (5549.694–6791.521) | 1989.500 (1989.500–1989.500) |
| ingest | simdjson-session-java | 4268.835 (4198.482–4326.966) | 3596.000 (3596.000–3596.000) |
| read-aot | gson-tree | 513.544 (512.637–791.156) | 80.000 (80.000–80.000) |
| read-aot | simdjson-generic-java | 2781.015 (2777.133–2797.354) | 2356.000 (2356.000–2356.000) |
| read-aot | simdjson-generic-interop | 3171.473 (3167.978–3660.446) | 240.000 (240.000–240.000) |
| read-aot | simdjson-snapshot | 3689.161 (3358.529–4801.109) | 240.000 (240.000–240.000) |
| interpret | gson-tree | 527.074 (519.601–776.622) | 82.000 (82.000–82.000) |
| interpret | simdjson-generic-java | 3345.967 (2783.031–3986.796) | 2358.000 (2358.000–2358.000) |
| interpret | simdjson-generic-interop | 3168.187 (3166.914–3169.506) | 242.000 (242.000–242.000) |
| interpret | simdjson-snapshot | 3282.606 (3274.142–3283.465) | 242.000 (242.000–242.000) |
| peak | gson-tree | 535.742 (522.732–558.843) | 81.000 (81.000–81.000) |
| peak | simdjson-generic-java | 2840.352 (2839.419–4688.830) | 2357.000 (2357.000–2357.000) |
| peak | simdjson-generic-interop | 2992.791 (2965.760–3834.698) | 1.000 (1.000–1.000) |
| peak | simdjson-snapshot | 3005.762 (2999.618–3023.173) | 1.000 (1.000–1.000) |
