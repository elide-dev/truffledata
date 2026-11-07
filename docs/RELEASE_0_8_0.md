# TruffleData 0.8.0

This release adds two native data formats and the shared layer they run on.

**JSON** (`truffledata-json`, `-json-bindings`, `-json-jvm`) decodes with simdjson
5.1.1 On-Demand into Myna value tapes behind one generated C ABI, on SVM through
direct `@CFunction` bindings and on the JVM through FFM. Strict JSON throughout:
duplicate keys rejected after unescaping, exact number tokens, `-0` as F64, no
silent fallback. Thread-confined owners, explicit sessions (borrowed `parse`,
retained `decode`), bounded NDJSON streams, snapshots, `InteropLibrary` guest
values, `JsonSink` single-pass export with a reusable text cursor, UTF-16 input,
per-document UTF-16/Latin-1 text for `TruffleString`, and typed plans with nested
and repeated records on both runtimes. Compiled guest reads allocate nothing
(gated at ≤ 0.05 B/read on SVM).

**CSV** (`truffledata-csv`, plus optional `-csv-bindings`, `-csv-native`,
`-csv-jvm`) matches Apache Commons CSV 1.14.1's parsing, writing and predefined
formats, differential-tested against it, with a strict RFC 4180 default,
configurable null tokens mapped onto nullability, typed plans, streaming,
memory-mapped input and one-pass export. Parsing runs over SIMD structural-index
kernels: pure-Java SWAR by default, or the AVX-512/AVX2/NEON native kernel after an
explicit `CsvIndexing.install(CsvIndexing.loadNative(...))`.

**Tape** (`truffledata-tape`, `-tape-jvm`) holds the format-neutral owners,
values, sinks, snapshots, sessions, streams and typed records that JSON uses now
and YAML will use next. The JSON types were renamed to shared `Tape*` names; they
were unreleased, so no published API changed.

Pins: Myna 0.11.0 (value ABI v3 in use; v4 adoption is next), Elide toolchain
2026.11.5 (fixes Bazel distributed ThinLTO, elide-dev/toolchain#18), Elide GraalVM
25.5.5-elide.24, Rust nightly-2026-10-09. The JSON seam is application ABI 5.
Upgrade each format's JAR, bindings and native attachments together.

## Measured, with gaps

Shared-host timings are advisory; see each results README.

- **JSON**: native decode of small documents dropped from about 2.2 µs to 693 ns
  (1.43× a bare simdjson walk), large documents reach about 395 MB/s, NDJSON
  streams about 396 MB/s. Session parse into Java trees beats Gson's tree parser
  on the 32-document fixture. Compiled member reads are about 2× a Gson tree on
  that fixture (alternating member orders defeat the one-entry position hint);
  export to `TruffleString` is not yet faster than Gson. See
  `benchmarks/comparison/JSON.md`.
- **CSV**: with the native kernel, scanning leads every Java parser measured
  (2.4–18×) and Rust `csv-core`; typed decode leads the Java parsers in every row
  and Rust `csv` in most; strings lead the Java parsers in 13 of 24 rows (FastCSV
  wins short fields). An SVM scan-only regression after batch fusion is under
  investigation. See `benchmarks/comparison/CSV.md`.
- **Protobuf** is unchanged from 0.7.0: native views remain about 1.9× stock
  latency for full consumption.

YAML has a conformant native decoder in the tree (402/402 yaml-test-suite through
our own rapidyaml handler) but no published Java API yet.

Eighteen libraries and the BOM are published at https://maven.elide.dev, with
source JARs, POMs, checksums and Linux x86-64 static/shared native attachments for
Buffa, JSON and CSV. See [consumption and publication verification](PUBLISHING.md).
