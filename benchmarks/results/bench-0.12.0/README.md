# 0.12.0 benchmark capture — 2026-11-12

Raw reports behind the README benchmark cards. Methodology, pooling and regeneration:
[Release 0.12.0 benchmark cards](../../../docs/COMPARISON_BENCHMARKS.md#release-0120-benchmark-cards).
Every plotted value with its range: [cards.md](../../../docs/images/bench-0.12.0/cards.md).

- Source: `c378d6d` (the `v0.12.0` tag), every cells flag off.
- Images: built on BuildBuddy, invocation `87042130-904c-4470-b660-b73e70355f56`; SHA-256 in
  [manifest.json](../../../docs/images/bench-0.12.0/manifest.json), equal to the hashes taken on
  the host before the run.
- Host: `unclemax`, AMD Ryzen Threadripper PRO 9965WX (24 cores, SMT), idle apart from this run.
  `host_run.sh` ran all 31 shards in sequence, pinned with `taskset -c 12`, with no failures;
  [progress.log](progress.log) records each shard's start, end and load average.

| Directory | Harness | Shards | Forks per cell |
|---|---|---:|---|
| `json/` | `json_typed_pair` | 9 | 6 (same image as both roles, pooled) |
| `protobuf/` | `consumer_measure` | 10 | 3 |
| `yaml/` | `yaml_pair` | 4 | 6 (pooled) |
| `csv/` | `csv_pair` | 8 | 6 (pooled) |

## Same-image control

The paired harnesses ran one image on both sides. Across all 55 JSON timing cells, candidate/
baseline medians fall within 0.967–1.042: on this host, differences of a few percent are noise.

## Against 0.11.0

0.12.0 changes no default route (Myna 0.18.0 regenerates bindings; cells flags stay off), and the
capture agrees: Java heap figures are identical, and TruffleData timings are within 2.5% of the
0.11.0 capture, inside the control band, except JSON typed records (157 → 149 ns, 0.95×) and the
protobuf session decode (644 → 620 ns, 0.96×). Neither is attributed to a code change.
