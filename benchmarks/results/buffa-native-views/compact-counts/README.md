# Rejected compact-count representation

The candidate packs a 16-bit relative position and 16-bit occurrence count into one int for spans <=65535 entries, using two ints per field for larger spans. It keeps the tiny unique-field packed-offset path. The experiment patch is retained as rejected.patch; production code returns to the faster long[] count directory from af47d51. Added large nested-message tests remain useful and are retained separately.

Five interleaved fresh forks per binary, cell, and metric on unclemax CPU 2; no concurrent timing/profiling run. Both binaries request native views. previous.json retains raw measurements and binary hashes.

Generated projected ingestion regresses 621.271 → 645.080 ns/row while Java allocation improves 341.5 → 317.5 bytes/row. Full ingestion regresses 1276.348 → 1306.973 ns/row while Java allocation improves 1406.25 → 1382.25 bytes/row. Peak reads regress 8.430 → 8.952 ns; interpreter reads 16.236 → 16.676 ns. Stock controls are faster in the candidate binary (projected 629.640 vs 641.728 ns), so they do not explain away the adapter regressions. This tradeoff does not support retaining the representation.

[JVM views, strict JVM views, generated getter smoke/JIT checks, SVM views, and benchmark build passed](https://app.buildbuddy.io/invocation/a5f8488d-85a2-4344-9208-3aa8beb29c2e). The initial test used an integer field where trailing text was intended; both Java fallback and native paths rejected the assertion. It was corrected to an explicit string contract before this successful run. Boundary cases place a repeated field and trailing text in a nested row around spans of 65535/65536 entries.

Measurements precede the Rust nightly/Elide toolchain alignment. Java allocation excludes native memory.
