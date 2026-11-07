# Text-cursor export and SIMD strings (performance plan Task D1), measured on `2c95139` + Task D1

One `json_measure` run of the `json_svm` image built from `2c95139` plus the Task D1 working tree,
on a shared BuildBuddy RBE worker (Intel Xeon @ 2.70 GHz). Invocation (standing lanes, release
consumers and measure): <https://app.buildbuddy.io/invocation/7782fffb-79b2-43c7-9af9-d5d082963465>;
the smoke trace in [`json-compilation.log`](json-compilation.log) is from
<https://app.buildbuddy.io/invocation/36bddfa9-e7c8-4dc3-8d6c-595c8d97b564> (same sources).
[`json-comparison.md`](json-comparison.md) has every cell and [`json-comparison.json`](json-comparison.json)
the raw samples (image SHA-256 `9634744b…f7c8`). **Shared-host timings are advisory**; compare rows
within this run. Allocation counts are exact and identical across forks.

Since this task every `JavaSink` cell shares one `JavaSink.KeyCache` per run (`simdjson-generic-java`
and `simdjson-session-java`), as an embedder decoding one schema would; earlier results
materialized every key.

| Hypothesis / gate (plan Task D1) | Before (`json-reads-14df695`, `json-session-d86cb21`) | Measured | Result |
|---|---|---|---|
| `simdjson-generic-java` peak ≤ 1,900 B/doc | 2,357 | 1,169 (read-aot 1,168; gate now ≤ 1,200) | met |
| session `JavaSink` ingest toward 1,900 B/doc (B2 gate ≤ 3,600) | 3,596 / 3,588 | 1,360 (gate now ≤ 1,400) | met |
| `simdjson-generic-java` peak ≤ 2.4 µs/doc | 2,840 / 2,904 ns | 2,867 ns (2,854–3,490) | **not met** |
| `json_smoke` export walk keeps (1000, 2, 2) | 2 allocation sites | 449–505 nodes, 2 loops, 0 invokes, **0** allocations (gate tightened to (1000, 2, 0)) | met |
| `simdjson-export-walk` ≤ 1.0 B/doc | 1.0 | 1.0 | met |

Session `JavaSink` ingest took 4,400 ns/doc against `gson-tree` ingest 7,149 in this run (4,269
vs 5,446 in `json-session-d86cb21`). The compiled `JavaSink` export did not get faster: its time
is the host tree itself (two `LinkedHashMap`s, two `ArrayList`s, boxed numbers and value
`String`s behind `JavaSink`'s boundaries), not the views and key `String`s D1 removed. The fixture
has no string of 256 bytes or more, so the simdutf path (`myna_utf8_to_utf16_v2`) is exercised by
`SvmAcceptance.textBridge` for correctness, not measured here.
