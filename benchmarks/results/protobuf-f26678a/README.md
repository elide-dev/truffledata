# Protobuf performance cycle, 2026-11-06

This directory measures the TruffleData 0.8.0 release head with the extended harness
(`f26678a`: `86a691a` plus benchmark-only changes). The paired fixes are in
[`protobuf-83e8ebc`](../protobuf-83e8ebc/README.md) (ABI 9 views) and
[`protobuf-a58a36d`](../protobuf-a58a36d/README.md) (directed default decode). The candidate
columns of `protobuf-a58a36d` are the final state.

## What was measured

`//benchmarks/comparison:consumer_measure` (SVM) and `consumer_measure_jvm` (HotSpot, same
GraalVM 25.5.5-elide.24) ran on BuildBuddy remote workers. Each worker was a Firecracker VM
reporting 8 vCPUs of an AMD EPYC host. The runner pinned itself to one vCPU. Ten shards each
measure whole `(mode, stage)` groups for every engine, using three fresh processes per binary,
cell and metric, interleaved by seed. Cells report the median of fork medians. Ranges are
observed fork ranges, not confidence intervals.

The shared consumer audits 32 `google.protobuf.Type` messages through the common `DataLibrary`
and interop access nodes. Stock protobuf-java runs its equivalent computation behind an explicit
AOT boundary.
`generated-owned` decodes into concrete generated records. `native-view` is
`BuffaPlan.decodeView` (scoped native view), and `native-eager` is the default
`BuffaPlan.decode`. Stages:

| Stage | Work |
|---|---|
| decode | decode existing input and release (no guest code) |
| resident | decode existing native/heap input, compiled guest computation, release |
| heap | resident plus native input allocation and heap copy |
| detach | decode, durable snapshot, close, consume |
| first | first compiled guest access to fresh rows (decode outside the window) |
| interpret / peak | warm reads of 32 retained rows, interpreter / last-tier compiled |

Small messages average 149.9 B; `holdout` messages have 12–24 nested fields and
supplementary-plane names; `large` messages average 51,822 B (2,048–2,496 nested fields). MB/s divides
wire bytes by the cell time, in decimal megabytes (1,000 MB/s = 1 GB/s).

## Release head (f26678a), SVM

| Operation | stock | generated-owned | native-view | native-eager |
|---|---:|---:|---:|---:|
| Projected ingestion (sparse resident) | 935.6 | 970.2 (1.04×) | 1,351.3 (1.44×) | 2,951.0 (3.15×) |
| Full ingestion + consumption | 923.9 | 905.2 (0.98×) | 1,670.7 (1.81×) | 2,043.9 (2.21×) |
| Full, heap input | 856.1 | 878.0 (1.03×) | 1,738.4 (2.03×) | 2,152.7 (2.51×) |
| Decode only, ~150 B | 781.4 · 192 MB/s | 855.9 (1.10×) · 175 MB/s | 1,008.0 (1.29×) · 149 MB/s | 1,772.4 (2.27×) · 85 MB/s |
| Decode only, ~52 KB | 228,560.3 · 227 MB/s | 270,507.0 (1.18×) · 192 MB/s | 202,403.3 (0.89×) · 256 MB/s | 553,952.8 (2.42×) · 94 MB/s |
| Large full ingestion | 269,760.1 | 290,281.1 (1.08×) | 443,863.1 (1.65×) | 712,064.0 (2.64×) |
| First full compiled access | 72.4 | 46.9 (0.65×) | 809.3 (11.18×) | 104.7 (1.45×) |
| Interpreter reads (full) | 62.0 | 291.1 (4.70×) | 407.1 (6.57×) | 347.5 (5.61×) |
| Peak reads (full) | 110.1 | 45.2 (0.41×) | 212.5 (1.93×) | 127.1 (1.15×) |
| Peak reads (sparse) | 11.8 | 10.0 (0.85×) | 19.8 (1.68×) | 17.5 (1.48×) |
| Detach + consume | 899.9 | 968.1 (1.08×) | 26,487.7 (29.43×) | 7,555.7 (8.40×) |
| Java B/row, full resident | 1,630 | 1,608 | 1,307 | 1,721 |
| Native index B/row, full | 0 | 0 | 1,512 retained | 1,488 transient |

## Final state (a58a36d), SVM

| Operation | stock | generated-owned | native-view | native-eager |
|---|---:|---:|---:|---:|
| Projected ingestion (sparse resident) | 785.5 | 815.4 (1.04×) | 803.9 (1.02×) | 1,563.7 (1.99×) |
| Full ingestion + consumption | 847.8 | 880.5 (1.04×) | 1,223.9 (1.44×) | 1,721.7 (2.03×) |
| Full, heap input | 854.3 | 851.0 (1.00×) | 1,301.8 (1.52×) | 1,831.3 (2.14×) |
| Decode only, ~150 B | 772.5 · 194 MB/s | 816.1 (1.06×) · 184 MB/s | 689.6 (0.89×) · 217 MB/s | 1,439.0 (1.86×) · 104 MB/s |
| Decode only, ~52 KB | 225,996.0 · 229 MB/s | 261,080.1 (1.16×) · 198 MB/s | 282,883.0 (1.25×)† · 183 MB/s | 460,382.1 (2.04×) · 113 MB/s |
| Large full ingestion | 249,459.5 | 269,380.6 (1.08×) | 367,449.6 (1.47×) | 561,701.4 (2.25×) |
| First full compiled access | 64.6 | 33.9 (0.52×) | 551.7 (8.54×) | 82.1 (1.27×) |
| Interpreter reads (full) | 62.7 | 298.6 (4.77×) | 399.1 (6.37×) | 341.0 (5.44×) |
| Interpreter reads (sparse) | 9.9 | 40.1 (4.07×) | 62.7 (6.36×) | 58.3 (5.91×) |
| Peak reads (full) | 105.6 | 44.8 (0.42×) | 222.8 (2.11×)† | 122.0 (1.15×) |
| Peak reads (sparse) | 11.8 | 9.4 (0.80×) | 20.5 (1.75×) | 17.1 (1.46×) |
| Detach + consume | 841.5 | 851.3 (1.01×) | 5,546.7 (6.59×) | 6,321.0 (7.51×) |
| Java B/row, full resident | 1,630 | 1,608 | 1,132 | 1,424 |
| Native index B/row, full | 0 | 0 | 1,142 retained | 1,142 transient |

† The view code is identical in both binaries of that paired run. In the same shard, its
baseline measured 198,510 ns for large decode and 171.9 ns for full peak. Treat those two cells
as noise-limited: about 0.89× and 1.6× stock respectively.

JVM tables (`*/jvm/results.md`) show stock protobuf-java is much stronger under HotSpot C2:
281.8 ns and 532 MB/s for small full decode, and 131,558 ns and 394 MB/s for large. In the final
state, JVM native-view full resident is 3.90× stock and native-eager 4.70×. The JVM adapter is
not a performance target in this cycle. JVM processes warm for 1.5 s and sample for 100 ms, so
the results remain sensitive to C2 timing.

## Historical generated-getter harness (`compare.py`, schema 12)

`comparison/` (default eager `buffa`) and `comparison-views/` (`--views`, the configuration of
PERFORMANCE_REVIEW.md's table) are single unpaired runs. The two views runs used different
workers, and stock ingestion differs by about 1.8× from the default run. Ratios to stock:

| Operation | Review (unclemax) | f26678a views | a58a36d views | a58a36d default |
|---|---:|---:|---:|---:|
| Projected ingestion, generated getters | 1.01× | 1.13× | 1.09× | 1.92× |
| Full ingestion + consumption | 1.90× | 2.08× | 1.92× | 1.97× |
| Interpreter reads (generated) | 3.01× | 3.09× | 3.33× | 2.74× |
| Peak reads (generated) | 1.58× | 1.55× | 1.87× | 1.25× |

This harness consumes nested rows through generated getters and uncached array accessors. It
gains less than the shared consumer, and its unpaired peak ratio worsened. The paired consumer
shows no peak improvement either. Native directory loads replaced Java `long[]`/packed-offset
lookups, so warm reads did not get cheaper.

## Caveats

- These timings come from shared remote hosts and are advisory. Only within-shard comparisons
  are paired. Repeated cells for identical code can differ by 20–40% (large messages, some peak
  cells). Some native-view heap/detach processes were bimodal (about 1.2 µs versus 9.4 µs per
  row) and stable within a process. This is consistent with allocator trimming and page-fault
  behaviour on these VMs.
- Native bytes are the decoded index/view allocation only. Allocator overhead, transient
  scratch peaks, compiled plans and input regions are excluded. The default decode's index is
  transient: it is released before `decode()` returns.
- Stock guest computation crosses an explicit boundary in every guest stage, so interpreter
  cells compare Truffle-interpreted shared access with AOT-compiled stock code.
- The JVM baseline (`f26678a`/`83e8ebc` deploy jar plus its own native library) ran through a
  wrapper launcher with the same JDK, heap, GC and timing properties as the candidate
  `java_binary`. Toolchain-default launcher flags may differ.

## Reproduction

```sh
bazel test //benchmarks/comparison:consumer_measure //benchmarks/comparison:consumer_measure_jvm \
  --test_env=TRUFFLEDATA_BENCH_REVISION=<sha> --test_timeout=3600
# Paired: stage an earlier image (and JVM deploy jar + libtruffledata_buffa.so behind a launcher)
# in an untracked package and override the baselines:
#   --//benchmarks/comparison:consumer_svm_baseline=//<pkg>:truffledata-consumer
#   --//benchmarks/comparison:consumer_jvm_baseline=//<pkg>:consumer_jvm
python3 tools/bench/consumer.py merge --output svm svm-raw/*.json
bazel test //benchmarks/comparison:measure //benchmarks/comparison:measure_views
```

Invocations: release head
<https://app.buildbuddy.io/invocation/776cae94-4d58-4c1c-abb4-39875154f75d>; release-head
views harness <https://app.buildbuddy.io/invocation/909d53d8-6d88-4864-ba46-8ab043ede2ea>; final
historical harness <https://app.buildbuddy.io/invocation/41194349-1ef7-47a7-90e7-0963a18c1e58>.
