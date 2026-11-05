# Direct bounded SVM index reads

Native rows on SVM use Myna-generated field accessors over the owned index address. Every native entry access checks its index against the tape count, and existing scoped-owner checks precede row access. JVM and address-free paths retain exact-class-profiled ByteBuffer reads. Index ownership, detached lifetime and ABI 8 are unchanged.

JVM views, SVM views and the Java-only SVM fallback passed: https://app.buildbuddy.io/invocation/ab1f85d6-8efe-43e4-8edc-8ec62134109d. SVM JIT blocklist, graph and performance-warning gates remain enabled. Source: local direct-index change on `347f6bf`.

Same five-fork randomized CPU-2 protocol as `../full/run.py`; remote `/tmp/truffledata-native-views.G2ATYf/direct`. Projected generated ingestion is 808.9 ns (previous 836.2; stock 630.3). Full generated ingestion is 1672.1 ns (previous 1707.4; stock 666.9). These small ingestion gains can include cross-build effects; retained peak improves 10.85 to 9.66 ns with non-overlapping fork ranges. Interpreter reads are 19.19 ns.

The address costs eight Java bytes per tape, including eager calls: projected 493.5 and full 1809.0 bytes/row. Native storage is excluded. This tradeoff does not establish parity and motivates reducing per-child cache allocations next.
