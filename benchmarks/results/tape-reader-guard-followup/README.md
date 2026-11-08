# Reader guard qualification checkpoint

[Remote run dab2dbc5](https://app.buildbuddy.io/invocation/dab2dbc5-ae9d-4a8e-b29c-5d43be2cadc3) includes the class-constant confinement and fused-owner storage hook. It measures retained interop 2,181/2,124 nodes and snapshots 2,089/2,033 (first/warm), one loop, no calls or allocations. Both exceed the unchanged 2,000-node limit.

Later local probes retain one immutable directory rank per interop specialization. They never retain the first owner; a changed key set uses the checked search without mutating compiled rank constants. Missing-first, changed-key-set and reordered-key regressions pass in the 61 JSON and 19 tape tests recorded here. The fused owner also proves its storage identity before eliminating the redundant reader class guard; separate owners still compare their actual reader class.

The full-fixture local JVM probe rotates 32 owners: confined separate storage 1,985/1,928 nodes; fused storage 1,981/1,924, one loop, zero calls, one harness result box. The confined owners still check current-thread identity and closure on every read. `JsonGraphProbe.java` preserves the diagnostic fixture; native qualification remains pending. The local heap-backed probe cannot substitute for the Native Image gate.
