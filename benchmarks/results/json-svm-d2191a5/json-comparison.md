# SVM scalar JSON comparison

Medians of three fresh-process fork medians; ranges are fork medians. Java heap excludes native memory.

| Stage | Engine | ns/record (range) | Java bytes/record (range) |
|---|---|---:|---:|
| ingest | gson-stream | 6082.182 (2739.722–108841.496) | 11288.000 (11288.000–11288.000) |
| ingest | simdjson-typed | 313.389 (295.354–715.581) | 56.000 (56.000–56.000) |
| read-aot | gson-stream | 1.568 (1.537–1.686) | 0.000 (0.000–0.000) |
| read-aot | simdjson-typed | 22.366 (22.261–32.764) | 0.000 (0.000–0.000) |
| interpret | gson-stream | 2.232 (1.883–24.964) | 2.000 (2.000–2.000) |
| interpret | simdjson-typed | 24.856 (23.448–26.921) | 2.000 (2.000–2.000) |
| peak | gson-stream | 1.923 (1.829–1.935) | 1.000 (1.000–1.000) |
| peak | simdjson-typed | 34.568 (27.268–35.342) | 1.000 (1.000–1.000) |
