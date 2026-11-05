# SVM scalar JSON comparison

Medians of three fresh-process fork medians; ranges are fork medians. Java heap excludes native memory.

| Stage | Engine | ns/record (range) | Java bytes/record (range) |
|---|---|---:|---:|
| ingest | gson-stream | 869.197 (863.752–914.539) | 11288.000 (11288.000–11288.000) |
| ingest | simdjson-typed | 166.570 (157.723–172.020) | 64.000 (64.000–64.000) |
| ingest | simdjson-specialized | 159.561 (158.420–168.128) | 64.000 (64.000–64.000) |
| read-aot | gson-stream | 1.168 (1.166–1.261) | 0.000 (0.000–0.000) |
| read-aot | simdjson-typed | 3.410 (3.191–3.410) | 0.000 (0.000–0.000) |
| read-aot | simdjson-specialized | 3.485 (3.401–3.495) | 0.000 (0.000–0.000) |
| interpret | gson-stream | 1.309 (1.304–1.324) | 2.000 (2.000–2.000) |
| interpret | simdjson-typed | 3.445 (3.444–3.487) | 2.000 (2.000–2.000) |
| interpret | simdjson-specialized | 3.684 (3.672–3.858) | 2.000 (2.000–2.000) |
| peak | gson-stream | 1.006 (0.949–1.010) | 1.000 (1.000–1.000) |
| peak | simdjson-typed | 2.713 (2.549–2.719) | 1.000 (1.000–1.000) |
| peak | simdjson-specialized | 1.429 (1.429–1.520) | 1.000 (1.000–1.000) |
