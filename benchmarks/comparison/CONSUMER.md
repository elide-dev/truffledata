# Shared consumer experiment

`ConsumerComparison` audits 32 varied `google.protobuf.Type` messages through the common guest access API. Sparse work reads string lengths, repeated counts, syntax and an optional source context. Full work also visits every field and oneof, reads names, and conditionally combines field number, kind and cardinality. Every TruffleData representation and the stock host-interop bridge execute the same `SharedRoot`, using `DataLibrary`, the typed read nodes and `InteropLibrary`.

| Engine | Producer and result |
|---|---|
| stock | protobuf-java and independent reference computation behind a boundary |
| stock-interop | protobuf-java accessed through cached host messages and the shared guest nodes; interpreter/peak only |
| generated-owned | generated Java decoder returning owned object records |
| slab | generated Java owning slab on JVM; native owning slab on SVM |
| slab-projected | selected fields, preserving omitted repeated counts; sparse modes only |
| slab-lazy | selected fields plus deferred omitted message children; full work realizes them |
| slab-session | one reusable native arena per live row slot; decode/resident only |
| slab-session-projected | selected fields and deferred children in native arenas; sparse decode/resident only |

The stock bridge caches immutable child wrappers and array counts, as slab readers do. Its scalar/string reads continue to invoke actual protobuf getters through cached host interop. Indexed protobuf getters avoid making a Java `List` implementation part of the guest graph. Reflection metadata for these exact methods accompanies the native benchmark image. Native Image's reflective host-invocation stub cannot enter the guest JIT. Its real cached host call therefore has an explicit, measured boundary; shared read nodes and value conversion still compile. JVM host calls remain eligible for inlining. The `host_interop_boundary` result field identifies this runtime limitation, and all TD warning/graph limits remain unchanged. The independent whole-computation stock-boundary control remains separate and is explicitly labeled in each result.

The ordinary cohort has 3–8 fields per message. `holdout` has 12–24 fields and longer supplementary-Unicode names; `holdout-sparse` uses identical input with sparse work. This is a distribution holdout on one schema, not a cross-schema generalization. `large` has 2,048–2,496 fields, about 52 KB per message, and measures decode/resident/peak throughput.

| Stage | Measured work |
|---|---|
| decode | Decode and release; no guest code |
| resident | Decode existing unmanaged input (native) or heap bytes (Java), run compiled guest, release |
| heap | Same; SVM owning slab uses heap input, including the producer's required input handling |
| detach | Decode and consume the durable owning result; owning snapshot is identity |
| first | First compiled access to fresh rows; preparation/release outside the window |
| interpret | Repeated warm access with guest compilation disabled |
| peak | Repeated warm access with helper-level compiled execution and stable last tier |

Plan/input setup, context startup and guest compilation are outside measurement. All controls prepare identical native plan/input fixtures even when their engine does not use them. Row order rotates, and every batch is checked against an independent checksum. Session cells consume each borrow before reset, and smoke tests verify expired-row rejection. Owning rows remain durable.

## Acceptance and measurement

```sh
bazel test //benchmarks/comparison:consumer_smoke \
  //benchmarks/comparison:consumer_native_smoke
bazel test //benchmarks/comparison:consumer_projection_measure \
  //benchmarks/comparison:consumer_projection_measure_jvm
```

Both smoke tests cover all 111 supported engine/mode/stage cells. They require correct checksums, helper-level compilation, a 256-node AST ceiling and the unchanged 10,000-node Graal graph budget. Native images use the repository blocklist/reachability checks.

The E1 targets keep sparse/full holdout groups on one worker and run three fresh forks. They preserve complete results and fixed ratio verdicts in `projection-gates.json`: selected small sparse ≤0.85× ordinary sparse, selected/lazy holdout sparse ≤0.5× ordinary full holdout, with the projected/full native-session ratio reported separately as an informational observation. A required gate miss fails the target while retaining its evidence. These targets are intentionally unsharded because their denominators cross mode groups.

`consumer_measure` and `consumer_measure_jvm` run the full matrix in ten remote shards. Each `(mode, stage)` group retains every engine and binary on the same worker. Override `consumer_svm_baseline` / `consumer_jvm_baseline` with a preserved executable for a before/after pair. Historical baselines explicitly select their four legacy engines and omit the new `holdout-sparse` mode. Current candidate images contain no legacy native view engine.

On a quiet Linux host, run serially with one CPU and both binaries interleaved:

```sh
taskset -c 2 python3 tools/bench/consumer.py \
  --binary baseline=/absolute/baseline --binary candidate=/absolute/candidate \
  --engines baseline=stock,generated-owned,native-view,native-eager \
  --modes baseline=sparse,full,holdout,large \
  --source 'commits + saved patch hashes' --output /new/result/directory
python3 tools/bench/consumer.py merge --output /new/merged shard*.json
```

Every cell uses three fresh forks and separate timing/allocation processes. Each process records three samples; JVM host warmup is 1.5 seconds, SVM warmup 100 ms. The runner sets a 1 GiB heap and Serial GC, interleaves cells deterministically, validates checksums/tiers/counters, and reports medians of fork medians plus observed ranges. Shared remote workers make timings advisory: compare within a shard, and do not overlap profiling with measurement. Failed processes retain `partial.json` without a completed report.

Current-thread Java allocation includes harness allocations and excludes native memory. Reports separately retain wire size, copied input, valid slab extent, retained heap slab capacity (including realized children), native session arena capacity, and immutable source input retained by lazy/external references. The original native index/header fields remain zero for current engines and are retained for historical report compatibility. Native transient peaks, allocator overhead, plan storage and total process retention are not measured.

## Profiling

```sh
bazel build //benchmarks/comparison:consumer_profile --remote_download_outputs=toplevel
taskset -c 2 perf record -F 997 --call-graph fp -o full.perf -- \
  /absolute/truffledata-consumer-profile -Xmx1g slab full resident time full.jfr
```

The diagnostic image enables JFR after warmup and takes three one-second samples. Its timing is excluded from comparisons. Hardware sampling includes startup and may not resolve compiled guest addresses; allocation-event counts are not bytes. The JSON profile summarizer accepts `dev.elide.truffledata.comparison.ConsumerComparison` as its class filter.

Broader claims still require unrelated-schema/fallback holdouts, complete native retention accounting, an actual Elide/TruffleCEL integration and release qualification. This harness does not replace those checks.
