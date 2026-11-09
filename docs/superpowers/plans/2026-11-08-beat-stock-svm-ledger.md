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

## WS1 (protobuf reads worker)

Worktree branched from `perf/beat-stock-svm` at `e650d87`. Before-images built on RBE from that commit (invocation `2a4a6d82-c8ef-46fd-831d-1833d3101af7`; consumer image a remote cache hit, profile image rebuilt) and preserved under ignored `build/before-e650d87/` with `manifest.json` (consumer sha256 `e23372fc…1104`, 43,978,864 B; profile sha256 `a5d1f747…c349`, 53,113,976 B). The consumer image has the same size as the warm-cache refined candidate `c521a028…` but a different hash (harness-only changes since `e4cec9f`); no measurement is reused from that pair.

Task P0.6: Measured (before profile image `a5d1f747…`, x86-64 -O2, llvm-objdump local; `build/before-e650d87/aot-symbols.md`): the lead's budgets pass, but `SlabMessage_row` is 155 instructions / 8 calls and `SlabMessage_text` 116 / 6 against the lead's 447/16 and 436/17 pinned from the pre-warm-cache `9a1dcec` image. Ruling: those two budgets are re-pinned downward from the before image (≈10% headroom) with the after image; the lead's pins described a superseded source — cost if wrong: a re-pin from the wrong image hides a regression of up to 3× on the row/text miss helpers, which the paired first-access cells would still show.
Task P0.6: Measured (same image, `build/before-e650d87/aot-symbols-peak-path.md`, shape not timing): interpreter-path AOT bodies behind the consumer's full read are `SlabArray_get` 1,055 instructions / 72 calls (the per-element list read inlines all of `SlabRow.value`), `SlabRow$Slot_lengthIn` 1,888 / 146 (every string-length read: 3 per row + 1 per field), `SlabRow_get` 1,213 / 80 (the three `DataLibrary.read` calls per row), `SlabRow_value` 840 / 53, `SlabMessage_requireValue` 178 / 20, `SlabMessage_array` 357 / 13, `Accessor_bound` 85 / 3 with the virtual `SlabRow$Slot_accepts` 48 / 0. Harness bodies: `SharedRoot_score` 2,993 / 197, `FieldAudit_read` 2,297 / 139. These bound the interpreter cell's per-call work; the compiled cell is attributed separately by the pre-check dump and the P0.2 peak samples.
Task WS1 pre-check: Measured (local JVM proxy, libgraal 25.0.4.1, `-Djdk.graal.Dump=Truffle:1 -Djdk.graal.PrintBackendCFG=true` plus `engine.CompilationStatisticDetails` over the real consumer `SharedRoot` for `slab full peak` and `generated-owned full peak`; dumps and `cfg_summary.py` under `build/precheck/`; shape only, this host makes no timing claims and SVM inlining may differ): the slab root is 4,267 Graal nodes after partial evaluation / 7,742 after the Graal tier / 45,220 B of code, with 62 `InvokeWithException` + 2 `Invoke` + 15 `ForeignCall`/`Membar` pairs (15 allocation sites) surviving and 237 LIR call sites; generated-owned is 457 / 867 / 4,912 B with 6 invokes and 21 LIR call sites. Final HIR: 7,106 nodes (853 `Read`, 574 `If`, 120 `Deopt`, 12 `IntegerSwitch`) versus 756. The surviving slab call sites by target: 57 `Projection.invalidField` and 39 `SlabMessage.omitted` (projection slow paths kept alive because `requireValue` reads `owner.projected` at run time), 21 `createText`, 21 `createBlob`, 18 `createRow`, 18 `unsigned`, 12 `SlabMessage.array` (the `@TruffleBoundary synchronized` boxed-`HashMap` list lookup: two dynamic calls per row), 6 safepoint handshakes. The generated root's only calls are 12 `Accessor.foreignLength` slow paths, 6 handshakes and 3 allocation stubs.
Task WS1 pre-check: Ruling: the peak gap is not the virtual `accepts` (PE devirtualizes it; `bound` folds to an `instanceof` plus two field compares) and not `repeated()/kind()/defaultValue()` on the accessor (PE-constant, folded). It lives in (1) the generic `SlabRow.get`/`SlabRow.value` expansions behind the three `DataLibrary.read` calls per row and behind every `SlabArray.get` element read, whose `layout.plan(type).field(ordinal)` chain is a run-time load, so every kind branch, projection branch and flyweight miss helper stays in the graph; (2) the `SlabMessage.array` boundary call with its monitor, `HashMap` probe and `Integer` boxing (the 17 B/row the peak cell allocates); (3) `requireValue`'s run-time `projected` load at every read; (4) the flyweight `cached(handle)` acquire loads, which are inherent to a slab-backed row and stay. Class identity in the narrow B4-(a) sense (exact hub guard only) would remove almost none of this; class identity per (root layout, type) with a generated constant-ordinal `get`/`has`/list dispatch and generated per-field accessors on unprojected owners removes (1) and (3), and WS4's list table/specialized lists remove (2) and the per-element switch. The peak claim stands only for WS1 + WS4 together and is a HYPOTHESIS until paired: slab full peak 2.0× stock → ≤1.0× (the retained acquire loads and owner→bytes→record chains make the generated-owned 0.46× an unlikely floor). Interpreter and first access remain in scope (P0.6: `SlabArray_get` 1,055 instructions, `Slot_lengthIn` 1,888, `SlabRow_get` 1,213 AOT bodies). Kill rule unchanged: <5% peak with overlapping forks — cost if wrong: the JVM dump mis-predicts SVM inlining and the mechanism lands as interpreter-only; the paired SVM cells decide, and the P0.2 peak samples on the before image are the SVM-side cross-check.
