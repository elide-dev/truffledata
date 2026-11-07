# Shared consumer experiment

This small comparison asks whether representation improvements reach a guest using the common access API. It complements the historical generated-getter microbenchmarks; it is not an Elide or TruffleCEL application integration test.

`ConsumerComparison` audits 32 varied `google.protobuf.Type` messages. Sparse work reads string lengths, repeated counts, syntax and an optional source context. Full work also visits every field and oneof, reads names, and conditionally combines field number, kind and cardinality. Generated-owned records, native views (`decodeView`) and default eager decodes (`decode`, engine `native-eager`) all execute the **same** `SharedRoot`, through `DataLibrary`, the common typed nodes and `InteropLibrary`. No format-specific getters participate in that computation. Protobuf-java supplies an independent decode and reference computation.

The holdout changes nested cardinality from 3–8 to 12–24 and uses longer supplementary-Unicode names. It is a data-distribution holdout on the same schema, not proof across unrelated schemas, maps, real oneofs, constraints or fallback routes. Every native-view cell rejects eager fallback.

| Stage | Measured work |
|---|---|
| decode | Decode and release only; no guest code runs (throughput cohort) |
| resident | Decode existing unmanaged input (native) or existing heap bytes (owned/stock), run compiled guest, close |
| heap | Same, including native input allocation and heap-to-native copy |
| detach | Decode, produce durable snapshot, close the native owner, then consume; owned/stock need no snapshot |
| first | First compiled guest access to fresh decoded rows; preparation/release are outside the measured window |
| interpret | Repeated warm access; guest compilation disabled |
| peak | Repeated warm access; guest helper confirmed compiled and target stable in last tier |

Unlike the historical ingestion comparison, ingestion here includes a compiled shared guest computation. Stock's computation has an explicit boundary because stock collection traversal cannot enter the pinned SVM JIT. Its helper is AOT in every stage; this is a guest-integration comparison, not fully inlined stock versus TD. The shared helper itself emits the compiled marker; checking only its caller is insufficient.

Plan creation, fixture creation, context startup and compilation are outside the measured work. All fixtures prepare the same native plan/input setup, including controls that do not use it. No total process-memory claim is made. Read-only cells warm 32 retained rows; larger working sets and fresh schemas remain separate questions. The caller rotates row order and verifies a reference checksum on every batch.

The `large` data set (2,048–2,496 nested fields, about 52 KB per message) measures decode, resident and peak throughput. Results report MB/s of wire input next to the ratio to stock.

## Remote paired measurement

`consumer_measure` (SVM) and `consumer_measure_jvm` run ten shards on BuildBuddy workers. Each shard measures whole `(mode, stage)` groups for every engine and binary on one worker, with the runner pinned to one vCPU. Override `consumer_svm_baseline` / `consumer_jvm_baseline` with an earlier executable for a before/after pair. Merge shards with `python3 tools/bench/consumer.py merge --output DIR shard*.json`. Shared remote hosts make these timings advisory, so compare only within a shard.

## Run and preserve evidence

```sh
bazel test //benchmarks/comparison:consumer_smoke \
  //benchmarks/comparison:consumer_native_smoke
bazel build //benchmarks/comparison:consumer_svm --remote_download_outputs=toplevel
```

Preserve the baseline executable before rebuilding. On a quiet Linux host, run the comparison **serially**, with both executables and all cells interleaved:

```sh
taskset -c 2 python3 tools/bench/consumer.py \
  --binary baseline=/absolute/baseline --binary candidate=/absolute/candidate \
  --source 'commit + saved source patch hashes' --output /new/result/directory
```

The 42-cell matrix uses three fresh forks per binary and separate timing/allocation processes. Each process warms for 100 ms and records three 50 ms timing samples, or three allocation batches. The runner uses a 1 GiB maximum heap and Serial GC, preserves raw samples and hashes, rejects wrong checksums/tiers/counters, and reports medians of fork medians. Ranges are observed ranges, not confidence intervals. The execution is deterministic by seed; timing/profiling jobs must not overlap. Failed runs leave `partial.json` and never produce a completed summary.

Current-thread Java allocation is calibrated and includes call/harness allocations. The result also records input bytes, copied input bytes, native index capacity and native result-header bytes. These **do not** measure native transient peaks, allocation counts, allocator overhead, plan storage or total retained memory. The native index and header observations describe the decoded view before detachment, not the detached result. No GC disabling is needed.

`consumer_smoke` and `consumer_native_smoke` cover all 54 engine/mode/stage combinations, including additional holdout stages. They require helper-level compiled execution, correct checksums, closed-view rejection, an AST ceiling of 256 and the unchanged 10,000-node graph budget. Native image construction uses the repository's blocklist/reachability checks. These are scoped acceptance checks, not a universal JIT guarantee for arbitrary guests.

## Profile separately

```sh
bazel build //benchmarks/comparison:consumer_profile --remote_download_outputs=toplevel
# On the measurement host, after all timing runs finish:
taskset -c 2 perf record -F 997 --call-graph fp -o full.perf -- \
  /absolute/truffledata-consumer-profile -Xmx1g \
  native-view full resident time full.jfr
```

The diagnostic executable enables JFR only after warmup and takes three one-second samples. Do not use its timing output for comparisons. Hardware sampling includes startup and may not resolve compiled guest addresses. JFR sees Java-visible stacks; sampled allocation-event counts are not bytes. The existing `tools/bench/json_profile_summary.py` accepts `dev.elide.truffledata.comparison.ConsumerComparison` as its class filter. Inspect runtime stack-walker/safepoint artifacts before attributing compiled costs.

## Architectural decisions from this cycle

* Bind immutable field descriptors to the plan and ordinal at shared primitive call sites; never mark mutable row caches as compilation-final. The original nested native consumer exceeded the graph budget before this specialization.
* A checked array-count capability was tested and rejected: it reduced sparse read latency and Java allocation, but regressed sparse ingestion and generated-owned interpreter reads. The current API retains ordinary value access for lengths. Any future count/length optimization must preserve UTF-16/code-point units, lifetime checks and performance on representations without native metadata.
* Keep storage specialized. JSON scalar records now export the common primitive API and ordinary interop while retaining native slots and explicit scoped ownership. The JSON compiled tests cover required primitives, constant/dynamic receivers, cache saturation, close/thread checks, null/presence and the existing allocation/graph budgets. This does not complete the scoped JVM JSON API or nested typed JSON.
* Keep generated-owned records as a first-class dense/durable control. Native view plus reparsing snapshot is not an efficient substitute for decoding directly into an owned result.
* A direct schema-specific native result remains the next representation experiment: construct scalar slots, presence, child spans and repeated locations/counts while decoding; avoid rediscovering those facts in Java. First use this consumer and preserve the generic path as control. Require a full-consumption improvement without sparse/heap-input/holdout regressions and account for native storage before expanding eligibility or changing defaults.

Still required for broader production claims: unrelated-schema/fallback holdouts, complete native allocation/retention accounting, an actual Elide/TruffleCEL consumer integration, and full release qualification. The experiments here keep the pinned compiler/toolchain unchanged.
