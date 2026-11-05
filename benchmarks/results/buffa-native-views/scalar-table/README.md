# Hoist the compact scalar table

Source: scalar-loop table hoisting on `1a68cbe`. Resolve the checked table once before decoding compact scalar fields, matching the general binary path. Native tests and SVM view tests passed: https://app.buildbuddy.io/invocation/b4157708-b8d1-482d-8460-af4795ac917c.

Five fresh forks per metric/cell, randomized and pinned to unclemax CPU 2, same protocol as `../full/run.py`. Remote artifacts: `/tmp/truffledata-native-views.G2ATYf/scalar-table`. Scalar ingestion improves from 56.7 to 53.3 ns, with disjoint fork ranges and unchanged 88 Java bytes/row. This recovers the preceding lookup change's scalar regression. Mixed projected/full view ingestion is 833/1644 ns with overlapping ranges against the preceding build; no mixed-workload speedup is claimed. Native memory remains excluded from Java allocation counts.
