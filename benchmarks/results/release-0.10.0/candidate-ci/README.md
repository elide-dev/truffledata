# Candidate CI evidence

Exact candidate: `5d4002399dc2070795ecb8130e65e42bddda885c`, PR #36, normal CI run `42537051733`.

`status.json` is a normalized GitHub run/job/step and PR status-context snapshot.
`coverage-summary.json` records the normal test, coverage, access-contract and native results with Bazel invocation IDs. `coverage-excerpt.log` retains the corresponding concise job-log lines.

`native_smoke.log.gz` and `.xml.gz` are the exact passing test artifacts downloaded from the run's `buffa-native-reports` artifact. They use gzip mtime=0; `artifacts.json` records original and compressed SHA-256 hashes. No credentials, whole job logs or large report trees are archived here.

These receipts were captured in an ignored build directory after qualification, then archived at release completion; they did not mutate candidate sources or trigger another CI run. The final snapshot records every PR check/context green, including the separate BuildBuddy workflow. `buildbuddy-progress.json` contains filtered read-only API confirmation of COMPLETE/OK for exact candidate head and its child invocation IDs.

Final result: all 8 Actions jobs, codecov/patch, advisory CodSpeed Performance Analysis and the custom TruffleData pull request context succeeded. Native allocation remains 168 B generic / 136 B typed and generated; the inferred owner remains 48 B. This is CI qualification, not a claim that deferred performance goals were closed.
