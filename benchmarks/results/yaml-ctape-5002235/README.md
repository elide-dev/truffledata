# YAML compact ABI 5 qualification, source 5002235

Invocation: [4065dd6f-2b23-40f9-bb50-bfec5e1a448f](https://app.buildbuddy.io/invocation/4065dd6f-2b23-40f9-bb50-bfec5e1a448f).

The full qualification run retains four native throughput floor failures. `ratios.json` calculates each per-pass ratio from the printed nanosecond observations; `best_times_ratio` uses the benchmark's final best-times table. No threshold is relaxed.

| Workload | Best-times ratio | Per-pass range | Required floor | Result |
| --- | ---: | ---: | ---: | --- |
| Config | 0.560986 | 0.556177–0.561722 | 0.597 | Fail |
| Block records | 0.692907 | 0.687265–0.694248 | 0.715 | Fail |
| Flow records | 0.691620 | 0.687237–0.691620 | 0.724 | Fail |
| Stream records | 0.506864 | 0.505079–0.506864 | 0.497 | Pass |
| Text-heavy | 0.770839 | 0.745359–0.770839 | 0.895 | Fail |

`svm-comparison-smoke.txt` passes the compiled read and export gates: retained reads have 1,943/1,886 graph nodes, one loop, no calls and no allocation nodes; exports have 290/231 nodes, one loop, no calls and no allocation nodes. Retained reads and export each measure 1 B/record including harness boxing; reusable TruffleSink ingestion is 2,101 B/record against the 2,400 budget.

The short profile samples each engine/fixture for five wall-clock seconds using SIGPROF process-CPU sampling. `profile-runs.txt` records decode counts and samples; compressed files preserve every sampled stack. Symbolization requires the exact `bench_profile_short` binary from this qualification invocation; it will be recorded when materialized. Normalize sample counts by decode counts, not just percentages of each separately timed cell.

The native decoder test exposed a stale assertion that all filtered strings must use owned pool storage. Commit a94ec5b checks both ABI 5 storage forms, retained-source raw spans, compact validation and exact Myna/render parity. `decoder-asan-a94ec5b.txt` is the local macOS arm64 ASan/UBSan result using Homebrew clang 23.1.1, `-O1 -g -fsanitize=address,undefined -fno-sanitize-recover=all`; it does not replace the pinned RBE retry.

Full three-fork measurement was blocked by the subsequently corrected native library label. Final publication and corrected full-suite evidence remain pending. This directory does not claim release readiness.
