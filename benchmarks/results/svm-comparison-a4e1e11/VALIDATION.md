The comparison smoke test and all 62 measurement cells passed at `a4e1e11`.
The concurrent acceptance run failed because the new test schema accidentally set the map
flag instead of explicit presence. Commit `e277052` corrects that fixture and strengthens
snapshot presence checks without changing production code. Both JVM acceptance targets
then passed: https://app.buildbuddy.io/invocation/4132e9e9-a8ed-4fc6-8143-41b5bb84cbb3
Corrected static SVM acceptance remains required before merging this optimization.
