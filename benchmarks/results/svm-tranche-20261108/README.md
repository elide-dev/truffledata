# SVM tranche — 2026-11-08

In progress. Production baseline: `9a1dcec` (restored B4 plus integrated JSON and
slab work). Candidate admission and the final combined checks will be recorded
here when complete. SVM is the production performance target; JVM correctness
and coverage remain required, while JVM timings do not veto an SVM gain.

## Preserved baselines

`baseline/` retains binary SHA-256 manifests and compressed build logs for:

- `bad1392b-f949-4900-8507-9832f2112339`: original consumer, profiling consumer,
  JSON and CSV portable SVM images from `9a1dcec` production/harness.
- `8bad3434-d179-4ab9-9d0f-56a1d1d1822c`: expanded JSON/CSV workloads at harness
  `d3f40cf`, still `9a1dcec` production. CSV adds owned-input string batches;
  JSON adds nested/repeated/mixed/churn typed documents. This invocation also
  passed the restored protobuf interpreter profile test.

Both builds use the repository's pinned `25.5.5-elide.27` SDK, Linux x86-64,
`-march=compatibility`, `-O2`, and no PGO. The vendor banner is
`GraalVM CE 25.5.5-dev+1.1`, Java `25.0.4.1.1+1`.
The local executables remain under ignored `build/svm-tranche-baseline/` in the
integration checkout. Candidate pairs must copy these artifacts, not rebuild an
approximation of the old production source with different workloads.

New CSV and JSON paired runners interleave three fresh forks of each image on a
single pinned logical CPU per shard, verify result checksums, and retain raw
samples and timing/allocation ranges. Separate shards are separate same-host
comparisons; their hosts may differ. Measurements remain subject to shared-host
noise. Instruction/sample profiles are diagnosis, not elapsed-time speedups.
