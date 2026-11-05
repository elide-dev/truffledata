# Scalar JSON comparison

Run `bazel test //benchmarks/comparison:json_native_smoke` for correctness and tier
checks; run `bazel test //benchmarks/comparison:json_measure` for three fresh forks
per timing/allocation cell. Reports are in that test's undeclared outputs. The
reusable native CI job runs the smoke target. No Jackson or protobuf dependency
is needed by this separate JSON executable.

The fixture contains 32 varying UTF-8 objects with shuffled keys and three required
non-null fields: long, double, boolean. Native simdjson decodes through a reusable
`JsonScalarPlan`. Stock uses a hand-written Gson streaming decoder from UTF-8 bytes
through `InputStreamReader`, producing an owned Java record. It checks duplicate,
unknown, missing, and trailing fields. This is a valid-input performance fixture,
not proof of identical malformed-number semantics or general JSON compliance.
Gson's reader buffers are included; this is not a comparison with Jackson's byte
parser, a reused reader, or a hand-written simdjson C++ baseline.

Ingestion includes parsing, three-field consumption, escape of the returned record,
and native result release. Plan compilation is excluded. Retained-read cases reuse
32 live records and rotate the starting index. Both use the same guest loop for
interpreter and peak tests; direct AOT reads bypass the guest call. Peak requires a
stable last-tier target before and after sampling. Allocation counters are calibrated
with an escaped megabyte array. Java allocation excludes all native memory; native
input padding, slot buffers, parser scratch, and allocator overhead are not included.

The initial `d2191a5` results expose a retained-read gap: native reads took roughly
22–35 ns per record versus about 2 ns for owned stock values. Native ingestion used
56 Java bytes, versus 11,288 for the Gson reader-based path. Timing on shared RBE
workers was highly variable, especially ingestion and interpretation; do not use
these results as a universal speedup claim. Raw samples, fork ranges, binary hashes,
and source provenance are retained under `benchmarks/results/json-svm-*`.

An experiment caching the native slot address and presence/null masks in the Java
record raised ingestion allocation to 80 bytes. Its timing ranges overlapped the
baseline and peak reads remained well behind stock. That change was reverted;
schema-specialized access remains the next optimization to investigate.
