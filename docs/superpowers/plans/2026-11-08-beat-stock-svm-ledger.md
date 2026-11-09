# SDD ledger — plan: docs/superpowers/plans/2026-11-08-beat-stock-svm.md

Started 2026-11-08 on user instruction ("load the plan, we're going to work on it"). Branch
`perf/beat-stock-svm` from `main` at `5950819`; workstreams run in per-agent worktrees branched
from it and merge back after review. Serialized Bazel through the session wrapper
`/private/tmp/claude-501/-Users-sam-workspace-graalvm-truffledata/be7493c3-484b-4def-bce7-2f623fdfb385/scratchpad/bzl`
(recreate from `docs/superpowers/plans/2026-11-08-release-resume.md` if the scratchpad is gone).
Measurement images are Linux x86-64 built on RBE (`--config=remote`); the local host is an Apple
M5 Max and makes no timing claims. Preserved before-images:
`/Users/sam/workspace/graalvm/truffledata-followup/build/svm-tranche-baseline/{9a1dcec,expanded}/`.
Stay local: no push, no CI dispatch beyond RBE build/test invocations, no publication.

Every entry: `Task X: Ruling|Measured|Deviation|Open: ... — cost if wrong: ...`. Numbers are
MEASURED (cite the result directory), PRIOR-ART or HYPOTHESIS, as in the plan.

## Common (lead)

Task P0.6: Ruling: the AOT symbol gate is `tools/bench/aot_symbols.py` (llvm-nm/llvm-objdump; x86-64 AT&T and AArch64) with budgets in `benchmarks/comparison/consumer_aot_budgets.json` and the manual remote target `//benchmarks/comparison:consumer_aot_symbols`. It runs over the `*-profile` images (`-H:-DeleteLocalSymbols`) because the production images are stripped ("no symbols", verified on the preserved `9a1dcec` consumer image); it is a code-shape check only, never a timing — cost if wrong: a method inlined differently in the profile image than in the production image is misjudged; both are the same source at -O2 and the gate is a budget, not a claim.
Task P0.6: Ruling: `%%D` deoptimization-target twins are excluded by default; a missing/renamed symbol fails a budget run rather than passing silently; budget keys starting with `_` are comments — cost if wrong: a workstream that renames a method must re-pin, which is the intent.
Task P0.6: Measured (preserved `9a1dcec` consumer profile image, x86-64 -O2 `-march=compatibility`, llvm-objdump 2026-11-08; budgets pinned at these values plus ~10% headroom):

| Symbol | Instructions | Calls | Frame B | Stack loads | Stack stores |
|---|---:|---:|---:|---:|---:|
| `Accessor_bound` | 85 | 3 | 24 | 0 | 0 |
| `ReadLongNode_accessed` | 83 | 5 | 24 | 1 | 3 |
| `SlabFormat_getInt` | 57 | 3 | 40 | 0 | 6 |
| `SlabMessage_array` | 357 | 13 | 72 | 24 | 27 |
| `SlabMessage_row` | 447 | 16 | 56 | 34 | 26 |
| `SlabMessage_text` | 436 | 17 | 72 | 35 | 24 |
| `SlabRow$Slot_accepts` | 48 | 0 | 8 | 0 | 0 |
| `SlabRow$Slot_longOf` | 481 | 34 | 72 | 9 | 68 |
| `SlabRow$Slot_readLongBound` | 486 | 35 | 72 | 10 | 69 |

Observation (shape, not timing): the slab long read helper is 481 instructions with 34 call sites against 48/0 for the exact-class check alone, consistent with the plan's WS1 diagnosis (the bytes are right; the binding is wrong). Call sites include slow paths, so this bounds, not measures, the warm path.
Task P0.6: complete (`//tools/bench:test` passes locally; `consumer_aot_symbols` not yet run on RBE — the first protobuf worker runs it on its own before/after profile images).

## Fan-out (lead)

Ruling: two workers at a time. Wave 1: WS1 protobuf one-load reads (keystone, with WS4's no-ABI list table and P0.2/P0.6 as its admission evidence) on Fable; WS10 CSV monomorphic writers/plain framing (P0.5 first) on Opus. Wave 2 (after a wave-1 slot frees): P0.1 decode ablation then WS2 evidence (WS2 needs Sam's verifier-trust ruling before any product change); JSON P0.3/P0.4 then WS6/WS7. WS3/WS5/WS8/WS9/WS11 only on the evidence the plan requires — cost if wrong: a worker idles on a long RBE queue; the lock serializes Bazel anyway.
Open (Sam): WS2 trusts slabs produced by our own in-process decoder (skip `verifyOwning`); the plan says this changes the verifier contract for internally produced slabs only and needs a ruling before implementation. Evidence from P0.1 will be attached before asking.
Open (Sam): WS8 numeric-heavy admission identity (big/huge are mixed fixtures); only a C++ probe runs before a ruling.
