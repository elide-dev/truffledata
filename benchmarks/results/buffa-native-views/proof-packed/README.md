# Compact native certificate metadata

Store the native certificate in the sign bit of the nonnegative Java entry count; mask it through `count()` and expose `viewReady()` separately. Negative input counts are rejected. No native ABI or index layout changes.

JVM and SVM view checks passed: https://app.buildbuddy.io/invocation/628dec18-2309-4238-a6bc-cccebd1ef79e. The SVM comparison image built with JIT gates. Same five-fork randomized CPU-2 protocol as `../full/run.py`; remote `/tmp/truffledata-native-views.G2ATYf/proof-packed`. Source: local tape-metadata packing on `67005a3`.

This removes the extra 8 Java bytes introduced by certification: projected 485.50 and full 1801.00 bytes/row (native storage excluded). Projected generated ingestion is 836.2 ns versus stock 633.9 ns; full generated ingestion 1707.4 ns versus stock 687.9 ns. These are effectively the same throughput as the unpacked certificate experiment; no speedup is attributed to metadata packing. Retained generated reads remain about 20 ns interpreter and 10.9 ns peak.
