# Prepared protobuf source qualification (#33)

Invocation: <https://app.buildbuddy.io/invocation/a0c08060-b429-4a00-829e-4d2b4c726df6>.
The candidate contains the 0.10 implementation at `53bf978`, GraalVM `25.5.5-elide.27`,
and Myna `0.13.0`. Both `protobuf_ingestion_jvm` and `protobuf_ingestion_svm` passed.
These are ordinary HotSpot/Native Image host calls; no Truffle guest compilation is credited.
Each case has a separate warmup followed by five timed samples. This is one process per runtime,
not a three-process performance gate. Raw output and all samples are retained alongside this file.

| Operation | JVM median ns/op | SVM median ns/op |
|---|---:|---:|
| Prepared snapshot, generated message | 3,687.9 | 8,033.8 |
| Prepared snapshot, dynamic message | 2,514.8 | 5,635.5 |
| Encode generated message then decode wire | 1,893.6 | 3,879.0 |
| Closed-enum map snapshot with wire normalization | 1,644.6 | 3,624.1 |
| Closed-enum map encode then decode | 1,201.1 | 2,569.6 |

The generated direct snapshot is **1.95× JVM / 2.07× SVM** the wire route's time on this
fixture; the explicit wire-normalized fallback is **1.37× / 1.41×**. Direct ingestion removes
the intermediate wire buffer and preserves explicit-plan semantics, but this measurement does
**not** establish a throughput improvement. Binding is measured separately: the JVM samples
range from 41,422 to 1,709,771 ns and remain strongly affected by compilation/startup, so its
856,006 ns median is not a steady-state claim. SVM binding has a 5,997 ns median with a 13,313 ns
first sample. `summary.json` retains these distributions without dropping outliers.

The same invocation passes `//tests:test`, including `ProtoExplicitSourceTest`: explicit
Editions binding, scalar/repeated/message extensions, known and unknown nested MessageSet,
closed-enum map normalization, rejection at preparation of the unsupported MessageSet plus
closed-enum-map combination, and budget rejection before recursive host sizing. Prepared
sources report `DIRECT`, `WIRE_NORMALIZED` or `UNSUPPORTED`; automatic descriptor compilation
still rejects editions/extensions/MessageSet when no explicit plan supplies their semantics.
Issue #33 defines no numeric speed threshold. Its functional and disclosure requirements are
qualified here; no universal speedup is claimed. Producer rows in the raw logs are supplementary
and do not replace the separate three-fork A3 writer experiment.
