# Combined native traversal reads

Source: native traversal change on `a50dbbe`. Read the schema ordinal and next-entry position through one checked native-entry lookup, returning the pair in a primitive long. Row directory construction, repeated-field counting/materialization, and fallback semantic validation use the combined helper. The native index format, lifetime checks, and storage are unchanged; JVM retains checked ByteBuffer reads.

JVM views, strict JVM views, and SVM views passed with existing JIT/performance gates: https://app.buildbuddy.io/invocation/33f4d44d-c038-4b92-86d9-6b42e2655f85.

Five randomized fresh forks per metric/cell, CPU 2 on unclemax, protocol `../full/run.py`. Raw remote artifacts: `/tmp/truffledata-native-views.G2ATYf/fused-links`. Generated projected ingestion improves 833.2 to 765.5 ns (8.1%), full ingestion 1644.2 to 1477.6 ns (10.1%), with non-overlapping fork ranges. Eager controls are approximately stable. Java allocation is unchanged at 501.5 projected / 1572.75 full bytes per row. Native index, header, and unmanaged input memory are not counted. Retained interpreter/peak reads remain approximately 19.0/9.25 ns.

Stock is 627.2 ns projected and 673.2 ns full in this run: view ingestion remains 1.22x and 2.19x stock. This is not parity. Stock mixed guest reads remain explicit boundary projections.
