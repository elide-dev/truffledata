# SVM scalar JSON comparison

Medians of three fresh-process fork medians; ranges are fork medians. Java heap excludes native memory.

| Stage | Engine | ns/record (range) | Java bytes/record (range) |
|---|---|---:|---:|
| ingest | gson-stream | 887.799 (852.230–900.683) | 11288.000 (11288.000–11288.000) |
| ingest | simdjson-typed | 194.699 (176.814–198.482) | 56.000 (56.000–56.000) |
| ingest | simdjson-specialized | 158.280 (157.764–178.661) | 56.000 (56.000–56.000) |
| read-aot | gson-stream | 1.405 (1.362–1.407) | 0.000 (0.000–0.000) |
| read-aot | simdjson-typed | 20.533 (20.457–20.605) | 0.000 (0.000–0.000) |
| read-aot | simdjson-specialized | 8.931 (8.885–9.440) | 0.000 (0.000–0.000) |
| interpret | gson-stream | 1.344 (1.327–1.407) | 2.000 (2.000–2.000) |
| interpret | simdjson-typed | 19.966 (19.933–20.122) | 2.000 (2.000–2.000) |
| interpret | simdjson-specialized | 4.216 (4.117–4.365) | 2.000 (2.000–2.000) |
| peak | gson-stream | 1.601 (1.523–1.610) | 1.000 (1.000–1.000) |
| peak | simdjson-typed | 21.125 (19.932–21.332) | 1.000 (1.000–1.000) |
| peak | simdjson-specialized | 10.259 (9.759–10.278) | 1.000 (1.000–1.000) |
