# Performance priorities

SVM / GraalVM Native Image is the production performance target. The JVM is a
correctness and test target. This is the user's explicit priority, reaffirmed
2026-11-08, and supersedes older plans that made JVM performance a release or
optimization-admission gate.

- Retain measured SVM improvements even when JVM throughput or latency worsens.
  Record the JVM measurements honestly; they do not veto an SVM improvement.
- Keep JVM correctness, API, ownership/lifetime, and coverage tests. Performance
  priority never authorizes changing semantics or weakening safety checks.
- Judge performance changes with paired SVM measurements and the existing SVM
  allocation, compiled-graph, and workload contracts. Distinguish native producer
  probes, instruction counts, and end-to-end SVM timings.
- A new priority does not turn an unmeasured candidate or a measured SVM loss into
  a win. Preserve existing no-PGO and prebuilt-native distribution constraints.

Current evidence and workflow checkpoints are in
`docs/superpowers/plans/2026-11-08-release-resume.md`. Follow its serialized Bazel
and stay-local rules; do not push, dispatch CI, or publish without user instruction.
