# Main release CI evidence

Exact release commit: `32a5f1946f4107831aa68d9594122de0a83c8d7a`. Its Git tree is identical to qualified PR candidate `5d4002399dc2070795ecb8130e65e42bddda885c`: `81ca27abe889474168124e6cab01d583505d396a`.

Normal main Actions run [42538839701](https://github.com/elide-dev/truffledata/actions/runs/42538839701) completed successfully: all eight jobs passed, including full tests/coverage, native correctness/allocation, all CSV platforms, database and Maven consumers, native JIT gates and simulation measurement. The custom [TruffleData main](https://elide-dev.buildbuddy.io/invocation/e75f8845-8c2d-4e6d-b9f1-b11c9a899034) workflow, Codecov and advisory CodSpeed checks succeeded.

`status.json` preserves normalized run/job/step and all commit-status metadata without altering raw statuses. Two raw Bazel build contexts report FAILURE by design: the unsafe blocklist and never-compile probes. Their enclosing JIT gate succeeded after validating the required diagnostic traces. `negative-controls.json` records those classifications and required fragments; `negative-controls-excerpt.log` retains the exact confirmation lines plus the 651-case compiled matrix result. Neither expected negative status was overridden or rerun.

`summary.json` records the final interpretation: no unexpected failure or pending context. Original huge job logs and credentials are not archived. No extra local build, test dispatch or benchmark was performed. This evidence does not claim that deferred performance goals were closed.
