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

# 0.10.0 release authorization

Sam subsequently authorized releasing the current work as `0.10.0` to unblock
TruffleCEL and Elide `1.6.3`. This authorizes the necessary branch push, PR/CI,
merge, signed tag, Maven/Central publication and GitHub release, followed by public
artifact verification. It supersedes the preceding stay-local/pause restriction
for this release. No additional permission round is needed for these steps.

Record measured performance gains and open gaps honestly. The remaining
performance goals are deferred from this unblock release; future approaches are
hypotheses, not committed outcomes. Standalone Buffa Cargo completion remains
follow-up work and must not be advertised as complete. Correctness, ownership,
safety, artifact integrity and existing graph/allocation contracts remain intact.

Keep bench charts current using the preserved measured data. Reuse the recent
qualification; do not repeat broad benchmarks or extensive validation without a
new change or failure that warrants it. Run focused checks for release fixes and
validate the staged/published artifacts. Resume hill-climbing after the release
as a separate tranche.

## Release completion

0.10.0 is released on Elide Maven and GitHub from signed tag `v0.10.0` at
`32a5f1946f4107831aa68d9594122de0a83c8d7a`. Normal candidate/main CI and actual
staged/public artifact checks passed. Sonatype validated the signed bundle, then
blocked publication on the organization's monthly release-size quota. Sam explicitly
selected “Finish release; defer Central.” Keep #28 open; do not retry Central or
start another hill-climbing tranche without a new instruction. #37/#38 and the
recorded performance investigations remain future work. Release receipts are in
`benchmarks/results/release-0.10.0/`.

# 0.11.0 release authorization

On 2026-11-09 Sam instructed: "i want to get truffledata out, let's settle and green
and merge and release". That authorizes settling the beat-stock-svm tranche (CSV wave
4 and WS12 merged; WS13 deferred), including the reviewed bot fixes #53–#56 and
#58–#61 (#57 was excluded: protobuf-java's generated code keeps the last map value,
and #57 deviates from that), PR/CI, merge, signed tag `v0.11.0`, Elide Maven and
GitHub publication, and public artifact verification. Central stays deferred under
#28 until the quota resets (2026-12-01). The same evidence, safety and
honest-reporting rules as 0.10.0 apply.
