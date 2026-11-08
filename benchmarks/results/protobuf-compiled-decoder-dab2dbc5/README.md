# A4 compiled runtime decoder ruling

Invocation [dab2dbc5-ae9d-4a8e-b29c-5d43be2cadc3](https://app.buildbuddy.io/invocation/dab2dbc5-ae9d-4a8e-b29c-5d43be2cadc3), Linux x86-64, GraalVM 25.5.5-elide.27. This records the A4 isolated producer experiment, not the full consumer or A3 writer diagnosis.

Both JVM and Native Image compile the Type and 64-field roots within the unchanged 10,000-node graph limit and pass producer parity/malformed-input checks. Median ns/op:

| Runtime | Generated | Table | Compiled | Compiled/generated |
|---|---:|---:|---:|---:|
| JVM |155.6|626.5|181.1|1.164×|
| SVM |586.2|2919.3|9594.6|16.367×|

The gate requires compiled ≤ 1.2×generated on both runtimes. JVM passes in this run; SVM fails by a wide margin. SVM samples vary 6.48–11.99µs, but every sample misses the ceiling. Per the amended Task A4 stop rule, close this experiment with table-driven Java as the runtime-plan floor. Do not install an automatic compiled-decoder dispatch or claim a production speedup. Generated contracts keep their generated producer, and SVM keeps the native default. Raw samples and exact computed ratios are retained alongside this note.
