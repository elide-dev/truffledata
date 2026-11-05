# Shared consumer cycle — 2026-11-05

The accepted protobuf change binds immutable field metadata in the common primitive exports. Before this change, the new nested shared consumer failed JVM compilation at the existing 10,000-node limit (`GraphTooBigBailoutException`, reported size 10,003). The repaired consumer passes JVM and SVM checks without raising the limit. This establishes supported compilation, **not a measured before/after speedup** for that fix: the original full consumer could not complete the required tier.

Typed JSON scalar records also gained the same common primitive protocol and ordinary interop while preserving scoped ownership. SVM tests exercise constant/dynamic receivers, more than three schema identities, close/thread/null/presence semantics and allocation budgets. They report 0.000032 Java B/read in the million-read checks, including invocation overhead; this is an allocation acceptance result, not a JSON latency comparison. The scoped JSON API remains SVM-only and experimental.

## Current protobuf consumer results

These are the `baseline` cells in [the interleaved raw results](results.json), which contain the accepted protobuf implementation. [All cells and observed fork ranges](results.md). This fixture and operation differ from the historical generated-getter comparison, so do not interpret differences from those older scores as speedups.

| Operation | Stock | Generated owned | Native view |
|---|---:|---:|---:|
| Sparse resident decode + guest, ns/row | 579.610 | 612.783 | 739.480 |
| Full resident decode + guest, ns/row | 612.654 | 627.719 | 1,140.120 |
| First sparse compiled access, ns/row | 7.670 | 6.221 | 128.842 |
| First full compiled access, ns/row | 43.476 | 20.719 | 533.036 |
| Warm sparse interpreter access, ns/row | 6.534 | 33.762 | 54.709 |
| Warm full interpreter access, ns/row | 42.505 | 210.371 | 286.204 |
| Warm sparse peak access, ns/row | 6.502 | 4.704 | 12.187 |
| Warm full peak access, ns/row | 42.114 | 18.250 | 58.873 |
| Full detachment + guest, ns/row | 609.418 | 622.094 | 3,907.254 |
| Sparse resident Java allocation, B/row | 1,630.5 | 1,608.5 | 541.25 |
| Full resident Java allocation, B/row | 1,630.5 | 1,608.5 | 1,307.25 |

Native full consumption is **1.86× stock latency**, and snapshot/reparse is expensive. Generated owned records are within 2.5% of stock full ingestion in this run and perform well in the compiled shared computation. Stock's computation crosses an explicit boundary, so the peak figures are a guest-integration comparison, not equivalently inlined implementations. The shared API's interpreter costs remain substantial on both TD representations.

The native view additionally retains an average **1,488 bytes of native index capacity plus a 24-byte result header**, for an average 149.906-byte input in sparse/full fixtures. Java allocation alone cannot establish total memory superiority. These figures exclude native transient peaks, allocation counts, allocator overhead, shared plans and total retained-heap accounting. Sparse and full decoding produce the same native index; laziness defers Java work. See [the methodology](../../comparison/CONSUMER.md) for input/ownership and stage definitions.

## Rejected count-capability experiment

The `candidate` binary added a checked `DataLibrary.readArraySize` capability to avoid creating native array wrappers for size-only access. It passed correctness, graph, allocation and coverage checks, but had mixed performance:

| Native view operation | Baseline | Candidate | Change |
|---|---:|---:|---:|
| Sparse first access, ns/row | 128.842 | 116.060 | −9.9% |
| Sparse interpreter, ns/row | 54.709 | 42.491 | −22.3% |
| Sparse peak, ns/row | 12.187 | 10.786 | −11.5% |
| Sparse resident Java allocation, B/row | 541.25 | 463.50 | −77.75 B |
| Sparse resident ingestion, ns/row | 739.480 | 766.019 | +3.6% |
| Holdout resident ingestion, ns/row | 3,079.507 | 3,134.889 | +1.8% |

Generated-owned sparse interpreter access also regressed **33.762 → 36.438 ns (+7.9%)**. Sparse ingestion and that interpreter regression have non-overlapping observed fork ranges. These ranges are not confidence intervals. A separate default-capability dispatch plausibly contributes to the owned interpreter regression, but this run does not isolate the causes of the native ingestion or holdout changes. Stock controls and all cells are retained; no selective normalization hides these effects.

The count-capability implementation was removed. Its [source patch](rejected-count-capability.patch) is retained as an experiment, not installed production code. After removal, the rebuilt protobuf consumer is byte-for-byte identical to the measured baseline (`c74f7cf…`), so another timing cycle was unnecessary. This does not require undoing the primitive-specialization fix, JSON bridge or semantic tests.

## Profiling and next experiment

[Saved profile summaries](profiles/summary.json) and hardware reports use the baseline implementation. JFR starts after warmup; hardware samples also include startup. Native full-consumption hardware self samples include `Decoder::binary` 15.55%, `Decoder::push` 5.73%, `NativeRow.index` 4.22%, `NativeRow.decode` 3.07%, `NativeRow.link` 3.01%, and native-array materialization 2.37%. These sampled shares are diagnostic, not additive speedup ceilings. Index growth/reallocation and Java reconstruction both deserve attention.

JFR corroborates field indexing, string/child materialization and wrapper allocation. Its allocation counts are sampled events, not bytes. The peak-read profile is dominated by runtime stack-walker frames; it does not attribute the compiled guest's instruction costs reliably. The saved compiler trace is a separate diagnostic: the accepted full consumer reports a Truffle-tier graph size of 4,533. Its graph allocation-node count is not a runtime allocation count.

The next representation experiment should decode one nested schema directly into scalar slots, presence, child spans and repeated locations/counts, avoiding reconstruction of parse-time facts. Compare it through this same shared consumer against the native index and generated-owned controls, including heap input, first/full reads, detachment and holdout data. Account for native storage, and preserve unsupported-shape fallback. This cycle does **not** implement that representation, claim the stock-performance objective is met, or qualify a release. Actual Elide/TruffleCEL integration and unrelated-schema holdouts remain required.

## Reproduction and provenance

- Base source: `bfcf22ffa1885c3f196a0478122ed5bcfec78ca0`, local `perf/sparse-finalization`.
- [Baseline patch](baseline.patch), SHA-256 `e01b3c6a45ddf05486c74dbc67732ea38a4ad2afa546cdba40e487ae906c57c5`.
- [Rejected candidate patch](rejected-count-capability.patch), SHA-256 `d3f4bbcd9ae4a6ab2152693e5c551512b53d440ca47288f862e9200e21bacf3e`.
- Both patches apply independently to that base. They capture the built source; later documentation/CI edits are outside the benchmark image. The candidate patch also captures the independent JSON/test work at that point; JSON is not linked into this protobuf consumer.
- Baseline executable SHA-256: `c74f7cfbacda7cf01d20817462afe719a5ce07056c2f16ae46894ecf768a8cad`.
- Rejected candidate executable SHA-256: `9cf57a789fc35be8de3713a07241dfb7f78d6fda7d190c094193008918c456ca`.
- Frozen tools: Elide toolchain 2026.11.1, recommended Rust nightly-2026-10-09, Elide GraalVM 25.5.5-elide.24, Bazel 9.2. Rust/C++ ThinLTO defaults unchanged.
- Host: unclemax, AMD Ryzen Threadripper PRO 9965WX, Linux x86-64, CPU 2. Serial GC, 1 GiB maximum heap. Three fresh forks per cell/binary/metric, 504 interleaved processes with seed 20261105. Timing and profiling serialized.
- Executables and raw JFR/perf recordings remain at `sam@10.5.0.25:/tmp/truffledata-shared-consumer.xtcBTw`. Recording hashes are in the profile summary. This is temporary diagnostic storage; checked-in raw benchmark samples and summaries are the durable evidence.
- [Initial graph failure](https://app.buildbuddy.io/invocation/2762640c-63ea-4337-a702-eb20b87c94c9); [primitive-cache JVM verification](https://app.buildbuddy.io/invocation/553cfa4d-d470-4250-ad21-d7a9bb8e7edd); [baseline SVM verification](https://app.buildbuddy.io/invocation/ddf50596-f221-4829-9e80-778b2ebde83c).
- [Count candidate JVM checks](https://app.buildbuddy.io/invocation/233eddc2-d8f9-4b1d-9ebe-ae07b0c90298); [count candidate and JSON SVM checks](https://app.buildbuddy.io/invocation/0c4951b4-1cd3-4c43-8395-fd199df0ed18); [candidate coverage](https://app.buildbuddy.io/invocation/b593e580-84f0-497e-8067-d673f22fffa0). Final accepted-state verification is recorded separately in `verification.txt`.

The [accepted source patch](accepted.patch) captures the final local implementation, tests, runner, CI wiring and methodology against the same base (SHA-256 `1b89dd7ae97052a885b461172b2d23b26e8cabb87e74da71192fc5a41c9f74ba`). It excludes the root review and JSON plan. [Final verification](verification.txt) passes the extended coverage inventory: `NativeRow` has 218/231 covered JVM lines and 36/37 methods, with only the named SVM pointer helper excluded; `TypedRow` has 20/20 methods.
