# TruffleData 0.5.0

This release separates the protobuf runtime from the optional protobuf-java bridge,
upgrades Myna to 0.8.0, and reduces repeated work in Buffa typed decoding.

- `truffledata-protobuf-runtime` supplies plans, binary/text/JSON codecs, runtime
  descriptor compilation, and generated-record support without protobuf-java.
  `truffledata-protobuf` retains Message/descriptor compatibility and build-time generation.
- Myna binding ABI 2 uses explicit native-call policies and packages its Native Image
  metadata. Buffa ships matching seam ABI 6 bindings and Linux x86-64 static/shared
  libraries. Parsing, allocation, cleanup, and Unicode calls retain VM transitions.
- Buffa reuses bounded native index scratch, caches validated defaults, resolves nested
  profile links at plan construction, and avoids redundant standard-contract validation.
  Strict mode retains validation audits. Message/input ownership remains independent
  of reusable decoder scratch; lower budgets, failures, and close paths remain checked.
- SIMD Unicode support, scoped-row detachment, and generated access improvements are
  included. Native index reuse retains memory per decoder and releases it on incompatible
  budgets, native parse failure, generic decoding, or decoder close.

Dedicated Linux/SVM paired measurements reach approximately 1.66–1.71 µs for Buffa
mixed ingestion with generated getters, versus approximately 0.66 µs for stock.
This is progress, not parity; Java allocation remains unchanged in the latest
profile-link experiment. Full measurements, controls, and uncertainty are recorded in
[the comparison report](COMPARISON_BENCHMARKS.md).

Generic and typed simdjson foundations are included in source as experimental work.
JSON Maven artifacts are not published in this release. The newer 24-byte Buffa index
experiment is also excluded; this release retains the validated 32-byte ABI 6 index.

Nine libraries and the BOM are published at https://maven.elide.dev, including source
jars, POMs, checksums, and Buffa native attachments. Keep bindings and native libraries
at the same version. Java 25 / Elide GraalVM 25.5.5-elide.24 remains the target.
See [publishing and consumption](PUBLISHING.md).

Release preparation uses the existing acceptance and coverage evidence; it does not
run a new benchmark cycle or full local verification suite.
