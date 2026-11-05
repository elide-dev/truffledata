# SVM scalar JSON comparison

Medians of three fresh-process fork medians; ranges are fork medians. Java heap excludes native memory.

| Stage | Engine | ns/record (range) | Java bytes/record (range) |
|---|---|---:|---:|
| ingest | gson-stream | 5017.334 (4601.386–5497.634) | 11288.000 (11288.000–11288.000) |
| ingest | simdjson-typed | 299.416 (291.573–386.241) | 56.000 (56.000–56.000) |
| ingest | simdjson-specialized | 272.568 (261.605–305.017) | 56.000 (56.000–56.000) |
| read-aot | gson-stream | 1.682 (1.507–2.026) | 0.000 (0.000–0.000) |
| read-aot | simdjson-typed | 27.083 (22.144–28.673) | 0.000 (0.000–0.000) |
| read-aot | simdjson-specialized | 10.519 (10.473–14.607) | 0.000 (0.000–0.000) |
| interpret | gson-stream | 2.292 (1.562–20.139) | 2.000 (2.000–2.000) |
| interpret | simdjson-typed | 24.879 (22.761–37.419) | 2.000 (2.000–2.000) |
| interpret | simdjson-specialized | 5.040 (4.895–7.699) | 2.000 (2.000–2.000) |
| peak | gson-stream | 2.022 (2.006–2.305) | 1.000 (1.000–1.000) |
| peak | simdjson-typed | 23.618 (22.799–25.499) | 1.000 (1.000–1.000) |
| peak | simdjson-specialized | 17.189 (12.977–21.052) | 1.000 (1.000–1.000) |
