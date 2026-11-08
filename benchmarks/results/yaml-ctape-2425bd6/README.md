# YAML qualification after quoted ASCII reuse

Invocation `d0c3e664-9f56-466e-8460-4443a1e231e9` captures `2425bd6` on pinned
Linux x86-64. Its JSON package analysis failure prevented the YAML comparison
image and JVM integration target from running. Native YAML correctness and
conformance ran, and Native Image acceptance passed; the native throughput floor
still fails. Do not treat this partial invocation as full qualification.

| Fixture | Current best ratio | Previous required ratio |
| --- | ---: | ---: |
| config | 0.549 | 0.597 |
| records-block | 0.645 | 0.715 |
| records-flow | 0.587 | 0.724 |
| records-stream | 0.475 | 0.497 |
| text-heavy | 0.785 | 0.895 |

The block passes range 0.616–0.645 (median 0.640), below the prior run's 0.669.
Flow and text improve modestly. The local block comparison was neutral, so this
run cannot establish whether added branch/code-layout cost or worker variation
causes the block regression. All floors remain unchanged.

The separately integrated directory-policy prototype measures 1.052× local block
speedup. ABI4 now exposes `TD_YAML_WANT_DIRECTORIES` (4): scan/export borrows omit
directories, lookup reads request them, and retain builds missing directories
without moving words. Exact completed key templates prove distinctness; misses
still sort and reject duplicates. Current-tree ASan/UBSan passes 14 shared compact
writer groups, all 9,093 YAML fast/general/fused comparisons, and native API checks
including 14 injected allocation sites plus directory-build failure/retry. The
regenerated ABI4 JVM integration package passes 29 tests. Formal RBE qualification
of this new policy remains pending.

Additional local probes remain unpromoted: persistent owned key-template bytes
alone improve stream time 1.019×; initializing metadata in chunks reaches 1.012×
on text-heavy input; a four-digit integer specialization reaches 1.001×. Raw
paired samples preserve noise. None is substituted for a pinned gate result.
