# SVM scalar JSON comparison

Medians of three fresh-process fork medians; ranges are fork medians. Java heap excludes native memory.

| Stage | Engine | ns/record (range) | Java bytes/record (range) |
|---|---|---:|---:|
| ingest | gson-stream | 893.663 (890.094–894.864) | 11288.000 (11288.000–11288.000) |
| ingest | simdjson-typed | 174.757 (157.851–175.365) | 56.000 (56.000–56.000) |
| ingest | simdjson-specialized | 177.693 (158.443–179.131) | 56.000 (56.000–56.000) |
| read-aot | gson-stream | 1.079 (1.015–1.079) | 0.000 (0.000–0.000) |
| read-aot | simdjson-typed | 3.081 (3.072–3.297) | 0.000 (0.000–0.000) |
| read-aot | simdjson-specialized | 3.407 (3.405–3.608) | 0.000 (0.000–0.000) |
| interpret | gson-stream | 1.276 (1.274–1.350) | 2.000 (2.000–2.000) |
| interpret | simdjson-typed | 3.583 (3.355–3.633) | 2.000 (2.000–2.000) |
| interpret | simdjson-specialized | 3.698 (3.663–4.243) | 2.000 (2.000–2.000) |
| peak | gson-stream | 0.948 (0.945–1.003) | 1.000 (1.000–1.000) |
| peak | simdjson-typed | 2.568 (2.565–2.738) | 1.000 (1.000–1.000) |
| peak | simdjson-specialized | 1.497 (1.487–1.581) | 1.000 (1.000–1.000) |
