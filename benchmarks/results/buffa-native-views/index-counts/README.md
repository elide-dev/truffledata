# Count-aware field indexes

NativeRow builds last-field positions and occurrence counts in a single sibling traversal. Size-only generated getters use the count without materializing NativeArray wrappers. Array construction reuses that count. Small, unique field sets retain packed offsets; duplicate or larger spans use long[] entries with a count and position. This first implementation increases wider directory storage relative to the prior int[] representation.

`previous.py` compares the ThinLTO baseline with this candidate using five fresh interleaved forks per cell and metric on unclemax CPU 2. Both binaries request native views; stock controls remain in the matrix. Raw measurements and binary hashes are in previous.json. No concurrent timing/profiling run was active.

Generated projected ingestion: 702.951 → 616.153 ns/row; Java allocation 421.5 → 341.5 bytes/row. Candidate stock is 641.543 ns/row / 1776.75 Java bytes/row. This fixture's projected native-view ingestion is faster than stock, but the overall objective remains unmet.

Generated full ingestion: 1337.572 → 1284.491 ns/row, versus stock 681.066 ns/row. Full Java allocation increases 1382.25 → 1406.25 bytes/row. Generic projected/full allocation also grows by 24 bytes because those operations materialize arrays. Retained generated reads improve from 16.810 → 16.280 ns interpreter and 9.341 → 8.441 ns peak, still behind stock boundary reads at 5.428 / 5.273 ns. Scalar and generated-owned controls also vary between binaries despite unchanged paths; attribute those differences cautiously.

Java allocation excludes unmanaged input, native index capacity, and detached native metadata. No total allocation parity claim is made.

[JVM views, strict JVM views, generated getter smoke/JIT gates, SVM views, and benchmark build passed](https://app.buildbuddy.io/invocation/365e38d0-9820-4e9b-b6a3-18f8acdad010). Added tests cover count-first reads, subsequent element materialization, empty/single/duplicate/multi-entry indexes, packed/unpacked scalar arrays, bounds, and closed owners. Existing host-mutation testing explicitly materializes an array to continue exercising cache promotion.

Next: preserve count lookup while reducing the wider-directory allocation cost, then continue work on full-read and retained-access gaps.
