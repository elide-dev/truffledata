# Initial native-index views

Same SVM executable, toggling `-Dtruffledata.buffa.views=true|false`; five fresh processes per cell and metric, randomized order (seed 20261105), pinned to CPU 2 on unclemax. Raw results include executable SHA-256, CPU affinity, backend, tier, checksums, requested view mode, and all samples. The mixed fixture asserts native backing when requested. Copy `tools/bench/compare.py` beside `run.py` and the executable named `candidate` to reproduce.

Source: local prototype on `8d9e272`, including singleton-oneof eligibility, exact-class index-buffer profiles, and lazy unsigned conversion behind a Truffle boundary. Build invocation: https://app.buildbuddy.io/invocation/a1c2ea1b-8b84-4c07-bb55-d0b628cb3304.

Projected mixed ingestion saves 71.6% of Java allocation (1735.75 to 493.50 bytes/row), but generated-getter ingestion is effectively unchanged at 1.50 microseconds versus stock at 0.65. Full consumption regresses by 21%, and retained interpreter/peak reads also regress. This does not justify enabling views by default.

Java allocation excludes native index storage and the detached result header. Views retain a separate index allocation per live message; eager messages reuse decoder scratch. Stock mixed read measurements cross a Truffle boundary and are not equivalent to fully inlined generated-owned reads.

Remote artifacts: `/tmp/truffledata-native-views.G2ATYf`. No concurrent timing/profiling process was found before this run.
