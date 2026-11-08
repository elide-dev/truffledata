# YAML follow-up qualification at 53bf978

Pinned Linux x86-64 invocation `a0c08060-b429-4a00-829e-4d2b4c726df6`
includes the SSE2 short-plain classifier (`368784d`), immutable member-rank hints,
and the released 0.9.0 changes merged into the 0.10.0 candidate. Neither YAML
performance gate is complete. Correctness targets pass; the native comparison
and compiled graph budgets still fail.

| Native fixture | Current best ratio to integer-handler scan | Required prior ratio |
| --- | ---: | ---: |
| config | 0.560 | 0.597 |
| records-block | 0.669 | 0.715 |
| records-flow | 0.578 | 0.724 |
| records-stream | 0.484 | 0.497 |
| text-heavy | 0.767 | 0.895 |

Block passes are 0.677, 0.669 and 0.671. The acceptance calculation uses each
engine's best time across all three passes, hence 0.669. This improves the prior
0.616 ratio but does not clear the unchanged 0.715 floor. The other fixture
comparisons remain applicable; none is silently dropped.

The SVM retained-read graph is 2,177 nodes first / 2,120 warm against 2,000, with
one loop, zero calls and zero allocation nodes. Export passes at 283 / 224 nodes.
Both read and export allocation measure 1 B/record from harness return boxing;
TruffleSink ingestion remains 2,101 B/record against 2,400.

The local macOS paired-library harness described in `../yaml-ctape-c9b9884`
measures quoted ASCII-byte reuse at 1.091× on flow and 0.999× on block. It reuses
existing short-text loads as a UTF-8 proof; all other text still receives strict
validation and surrogate repair. Its 9,093 ASan/UBSan parity decodes pass. This
change and the fused retained SVM owner are submitted for the next formal gate;
these logs do not qualify them on Linux or Native Image.

An isolated directoryless prototype reuses YAML's two independently checked key
templates. Only an exact complete key sequence skips duplicate sorting; misses
still rescan, sort and reject duplicates. It measures 1.048× on block and passes
9,093 ASan/UBSan parity decodes. It remains outside production: directory policy,
retained directory rebuilding and native API changes are not yet integrated.
A shared one-shape-per-depth cache regresses to 0.889× because this fixture
alternates key order. All local timings are exploratory, with raw noisy passes
preserved; they do not override the pinned RBE floor.
