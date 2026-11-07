# Layout-bound accessors: before/after (Task B2)

`svm/` holds one paired SVM run of `consumer_measure`: `baseline` is `f5e2d97` (TruffleData at the
plan's base, code identical to `fecb39f`), `candidate` is `b7b84ab`, the Task B2 code tree (the
commit that retains this directory adds only documentation and these results to it). Both images
ran in every shard on the same BuildBuddy worker; raw per-shard samples are in `svm-raw/`.
Invocation: <https://app.buildbuddy.io/invocation/7dc90243-22d7-4558-bb18-2d37d24b8170>. Method and
caveats are in [the cycle README](../protobuf-f26678a/README.md): shared remote hosts, advisory
timings, compare only within a shard, large-message cells vary 20–40 % between identical binaries.

The `slab` engine does not exist yet (Task B1 lands after this task), so the plan's `slab` cells
are not measured here. `consumer_measure_jvm` was not run for this task.

Interpreter and peak cells (ns/row, × stock in the same shard; fork ranges in `svm/results.md`):

| Cell | Engine | Before | After |
|---|---|---:|---:|
| Full interpret | generated-owned | 288.7 (4.69×) | 256.8 (4.18×) |
| Full interpret | native-eager | 342.8 (5.57×) | 238.3 (3.88×) |
| Sparse interpret | generated-owned | 41.5 (4.07×) | 36.8 (3.44×) |
| Sparse interpret | native-eager | 61.2 (5.99×) | 38.4 (3.60×) |
| Full peak | generated-owned | 44.8 (0.43×) | 48.9 (0.45×) |
| Full peak | native-eager | 122.2 (1.16×) | 116.9 (1.08×) |
| Holdout peak | generated-owned | 53.5 (0.39×) | 57.4 (0.40×) |
| Holdout peak | native-eager | 132.8 (0.96×) | 126.1 (0.89×) |
| Large peak | generated-owned | 5,348.8 (0.32×) | 6,374.3 (0.39×) |
| Large peak | native-eager | 18,033.5 (1.09×) | 16,717.9 (1.03×) |

Reference cells: `native-view` rows keep the library path (no accessor) and measured full peak
171.0 → 170.8 and full interpret 398.8 → 407.8; stock full peak 105.1 → 108.2 in the same shard.

Reading: interpreter reads improve 11–37 % (native-eager most: the eager row's library export had
a per-read owner node, class profile and plan cache in front of the load). The plan's ≤ 2.5×
interpreter hypothesis is **not** met (4.18× / 3.88× full); the remaining gap is the AOT code of
the DSL dispatch and of the guest loop, which Task F1 (PGO) measures. Peak is better for
native-eager and 4–9 % slower for generated-owned in full/holdout/sparse (non-overlapping fork
ranges in the full cell; large differs by more but sits in the noisy large cohort), so the plan's
"peak unchanged or better" holds for native-eager only; generated-owned stays at 0.40–0.45× stock.
Decode, resident, first and detach cells do not use the changed code and are within the run's
noise.
