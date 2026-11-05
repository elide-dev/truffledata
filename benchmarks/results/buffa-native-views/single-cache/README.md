# One-value native row cache

Experiment on `8bcc48e`: retain the first materialized value directly in a native row and allocate an ordinal-indexed cache only on a second complex field. Lazy cache and directory references are ordinary mutable fields: host-side initialization after guest compilation must not leave compiled readers with stale compilation-final assumptions.

JVM views, strict JVM views, and SVM views passed, including a new regression that compiles a reader capturing a row, grows its cache from host code, checks value identity and continued compiled execution, and rejects access after close: https://app.buildbuddy.io/invocation/dede4797-621c-4548-aa60-87ddf28d90ff.

Five fresh forks per metric and cell, randomized and pinned to unclemax CPU 2, using `../full/run.py`. Remote artifacts: `/tmp/truffledata-native-views.G2ATYf/single-cache`. Both variants use the same binary; raw checksums, tiers, allocation calibration, and binary hash are in `paired.json`.

Full generated ingestion remains approximately 1.67 microseconds while Java allocation falls from 1809 to 1572.75 bytes/row (13.1%). Projected generated ingestion is 843.8 ns versus the previous build's 808.9 ns, with Java allocation increasing eight bytes to 501.5. Stock controls also slowed slightly across builds (630.3 to 640.8 ns projected; 666.9 to 691.0 ns full). Retained generated peak reads improve from 9.66 to 9.24 ns; interpreter reads remain approximately 19.2 ns.

This is an allocation improvement with a projected-read tradeoff, not an ingestion latency win or stock parity. Java allocation counters exclude the retained native index, result header, and unmanaged input. Stock mixed guest reads use explicit boundary projections, not fully inlined generated getters.
