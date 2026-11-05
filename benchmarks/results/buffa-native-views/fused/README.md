# Fused native-view semantic validation

Single-pass required-field and merge checks, with immutable schema masks/child links and native enforcement of wire/depth/cell/string budgets. The strict eager audit remains. Required-field errors are deferred until merge detection finishes; unsupported merges fall back to eager decoding.

Build and checks: https://app.buildbuddy.io/invocation/b6ec4eab-280a-4532-b637-49cb004ce627. Both JVM view suites and the SVM view suite passed, including exact native cell-budget, required-child, scalar and repeated-scalar tests. Production source is a local change on `6a9f4da`.

Same protocol/script as `../initial/run.py`: same-binary eager/view switch, five randomized fresh forks per metric/cell, CPU 2 on unclemax. Raw samples include binary hash, tier/backend and checksums. Remote artifacts: `/tmp/truffledata-native-views.G2ATYf/fused`.

Generated-getter projected ingestion is 1014.5 ns with views versus 1511.9 ns eager and 641.7 ns stock (view-flag control). Java allocation falls from 1735.75 to 477.50 bytes/row (72.5%). Native allocations are excluded. Full consumption is 2343.7 ns versus 2304.8 ns eager and 668.2 ns stock. Retained interpreter and peak reads still regress. Views remain opt-in.

The initial native-view result was 1500.2 ns projected and 2791.4 ns full. Cross-build changes may include layout effects; same-binary eager/stock controls are retained in both runs.
