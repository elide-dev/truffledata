# JSON lean walk (performance plan Task A1), measured on `d9e8873` + Task A1

Plan: `docs/superpowers/plans/2026-11-06-json-performance.md`, Task A1 (number-first typing,
deferred unescaping, hashed duplicate table, key-hash side table).

`native.txt` is the output of the retained probe `perf_probe_test.cc`, run once as a temporary
`cc_test` in `json/native` (removed before commit) on a shared BuildBuddy RBE worker (Intel Xeon,
simdjson `icelake`). Invocation: <https://app.buildbuddy.io/invocation/1a96e7d6-e868-4098-82fa-3b7021e1185f>.

The probe is the one in `json-perf-probe-861dff1` plus one variant: `B0 baseline document 861dff1`
is the unchanged `decoder.h` from `861dff1` (identical at `d9e8873`), compiled into the same binary
under the namespace `truffledata::json::baseline` (`probe_baseline_decoder.h`), so `B0` and `D0`
were measured in the same process on the same worker. `D0` is the Task A1 decoder (still on Myna's
`value_builder`). All timings are advisory: one worker, one run, best of 7 rounds, no confidence
intervals. Compare rows within this file only; this worker was faster than the one behind
`json-perf-probe-861dff1`.

## Task A1 acceptance (hypotheses from the plan)

| Hypothesis | Measured (same run) | Result |
|---|---|---|
| small `D0` ≤ 0.75 × baseline | 1,379 / 1,526 ns = 0.90 | **not met** |
| medium `D0` (no target) | 19,085 / 26,544 ns = 0.72 | — |
| big `D0` ≥ 120 MB/s | 200 MB/s (baseline 149 MB/s; ratio 0.74) | met |
| `index_allocations` lower | small 33 → 22, medium 371 → 42, big 17,047 → 63 | met |

The small-document ratio misses the hypothesis because the remaining cost on 31 nodes is Myna's
`value_builder` (per-node `operation()` wrapper and geometric growth of four vectors from empty),
which Task A1 does not touch: `D2` (lean walk into the builder over an arena) is 1,114 ns against
`W3` 423 ns in the same run. Task A2 replaces the builder on the production path and is gated on
the combined ratio.
