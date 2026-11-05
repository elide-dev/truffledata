# SVM scalar JSON comparison

Medians of three fresh-process fork medians; ranges are fork medians. Java heap excludes native memory.

| Stage | Engine | ns/record (range) | Java bytes/record (range) |
|---|---|---:|---:|
| ingest | gson-stream | 6789.852 (5953.781–7120.751) | 11288.000 (11288.000–11288.000) |
| ingest | simdjson-typed | 415.657 (414.642–1384.379) | 80.000 (80.000–80.000) |
| read-aot | gson-stream | 2.299 (1.883–3.026) | 0.000 (0.000–0.000) |
| read-aot | simdjson-typed | 20.717 (15.120–21.660) | 0.000 (0.000–0.000) |
| interpret | gson-stream | 26.425 (2.255–31.092) | 2.000 (2.000–2.000) |
| interpret | simdjson-typed | 17.428 (14.913–23.360) | 2.000 (2.000–2.000) |
| peak | gson-stream | 3.084 (2.131–3.221) | 1.000 (1.000–1.000) |
| peak | simdjson-typed | 32.593 (19.407–35.928) | 1.000 (1.000–1.000) |
