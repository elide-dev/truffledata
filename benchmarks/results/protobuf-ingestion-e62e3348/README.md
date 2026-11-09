# Prepared source and slab probe, before generated specialization

Bazel invocation: `e62e3348-a1c0-492b-bd3e-c0f2708904b0`; Linux x86-64 remote execution, GraalVM `25.5.5-elide.27`. Raw output is retained in `jvm.log` and `svm.log`. This is one worker/process per runtime with five samples, an advisory implementation probe rather than paired release qualification.

Source provenance: followup worktree after checkpoint `91dc043`, with uncommitted default slab migration, source ingestion fixture/probe and ownership changes. The generated producer in this run still shares runtime field handlers; subsequent generated fast loops and generated-contract dispatch are absent. `Source.fields` still constructs a populated-field map for non-extensible messages; its later optimization is absent.

JVM probe uses 3 s warmup per operation, 250 ms samples, fixed 512 MiB heap and Serial GC. Native Image uses its probe defaults (500 ms warmup, 100 ms samples) and 512 MiB maximum heap. The SVM binding operation has a substantial first-sample startup outlier. Complete raw samples take precedence over approximate medians.

Measured medians in ns/op: JVM generated snapshot 5315, dynamic snapshot 2818, wire snapshot 1855; stock parse 219, table slab 656, generated shared-handler slab 744. SVM generated snapshot 12897, dynamic snapshot 6524, wire snapshot 3783; stock parse 643, table slab 1921, generated shared-handler slab 1963. These producer and direct-source results do **not** meet the performance targets. Closed-enum map normalization is explicitly measured as a wire fallback, not a direct-ingestion success.

The same invocation passed all twelve runtime/Buffa/JVM/SVM consumer, ingestion and native JSON/tape targets. Consumer smoke verifies correctness and record shape; it is not a release performance claim.
