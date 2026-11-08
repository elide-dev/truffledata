# Release 0.10.0 Resume Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax for tracking. The ledger is `docs/superpowers/plans/2026-11-07-distribution-ledger.md` (append "Resume" lines).

**Goal:** Take `feat/followup-v5` from the 2026-11-07 pause checkpoint to a merged, green main and a published 0.10.0, closing the open issues (#24–#37) and the recorded performance gates or ruling on each with evidence.

**Architecture:** No new subsystems. Fix the CI blockers first, then the measured performance gates per format (protobuf B4/E1, JSON retained and GB/s, YAML floors, CodSpeed JSON regressions), then the Cargo-buildable crates (#37), then qualification and publication exactly as the distribution ledger prescribes.

**Spec:** `docs/superpowers/plans/2026-11-07-distribution-ledger.md` (pause checkpoint, resume order, release authorization), plus the per-format plans it references.

## Global Constraints

- **SVM is the production performance target; JVM is a correctness/test target.** Sam reaffirmed this on 2026-11-08 after the local pause. JVM timing regressions do not veto measured SVM wins, and older JVM performance release gates are informational. Correctness, API, lifetime, coverage, and SVM allocation/graph contracts remain required. See `AGENTS.md` and `2026-11-08-svm-next-tranche.md`.
- One local Bazel invocation at a time across all worktrees; `JAVA_HOME=/Library/Java/JavaVirtualMachines/gvm.jdk25/Contents/Home`; real `bazelisk` for `coverage` (the aspect CLI has no coverage verb).
- Keep the shared Bazel volume above a few GiB free; delete only orphaned output bases (workspace directory gone), never shared caches, baselines (`build/protobuf-baseline-fecb39f`, `/private/tmp/td-json-ce7399f-baseline`) or release evidence.
- No budget relaxation without a measured breakdown recorded in the owning ledger.
- Publication only after: method coverage contract, explicit native/negative-JIT and sanitizer gates, five-platform staging, published Java/native/Gradle consumers, signatures/metadata, full CI green on main. Then `CENTRAL_CREDENTIALS_FILE="$HOME/.m2/settings-publishing.xml"`.
- Commit messages end with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.

## Review Focus

1. Coverage tests must exercise success paths: JaCoCo's probe follows a delegated call, so a method whose only call always throws is reported uncovered (Task R1 ruling).
2. The macOS `cc` wrapper rewrite must never switch SDK majors silently: it only aliases the same SDK or falls back to the CommandLineTools SDK the script already prefers.
3. The compact-scalar allocation gate must be re-based from a measured breakdown of the durable slab design, not relaxed to pass.
4. Cargo crates must not force `#[global_allocator]`, panic or profile choices on embedders, and must build against `CXXSTDLIB`.
5. CodSpeed's two JSON regressions are instruction-count deltas on the simulation image; confirm them on the native image before changing the tape paths.

---

### Task R1: CI coverage contract (DONE locally)
- [x] Reorder the inline-reader parity test so each reader meets its field first (commit efdc900).
- [x] Cover Csv/CsvPlan overloads, Buffa borrowed/materialized/session surface, TypedRow text-path accessors and cached ViewOwner checks, runtime GeneratedMessage unknown queries, SlabMessage projected/copyProjectedRecordOf/Usage, HeapValueTape region bytes, TapeReader node text word, YamlMessage confined; exempt `SlabSession::outside` with its reason.
- [x] Verify: `bazelisk coverage <nine JVM targets> --config=codecov && python3 .github/scripts/reports.py coverage && python3 .github/scripts/coverage_contract.py` → no `Uncovered access methods`.
- [x] Commit.

### Task R2: macOS CSV CI (DONE)
- [x] `bazel/patches/elide-toolchain-cc.patch` canonicalizes the SDK path (commit 9c53f2e); local `--config=local //csv/native:api_test //csv/native:index_test` pass.
- [ ] Confirm on CI after push (Native targets / CSV / aarch64-apple-darwin).

### Task R3: legacy `native_smoke` compact-scalar allocation (DONE locally)
- [x] Decode SVM slabs into a pinned heap array and `SlabMessage.takeOwnership` (drops the DirectByteBuffer wrapper and the second copy); size the first attempt from the learned `slabCapacity` with a small floor so the retained array is the slab's exact extent after the first message.
- [x] Run `bazel test //benchmarks/comparison:native_smoke` and record `ALLOCATION compact-scalar buffa ingest bytes/message`; re-base the budget in `benchmarks/comparison/Comparison.java` to the measured design cost with the breakdown in the protobuf ledger. Final 168 B generic / 136 B typed/generated, invocation `7b240f5f`; evidence: `benchmarks/results/protobuf-slab-allocation-20261108/`.

### Task R4: CodSpeed JSON regressions
- [x] Reproduce `json-generic-java-export` (-12%) and `json-generic-interop-read` (-10%) on the simulation image against main `223edf7`; bisect across the branch's tape/JSON commits; fix or rule with evidence. Fresh final-image callgrind counts improve versus main by 15.7% / 8.4%; native/JVM checks pass in `cdcbc5cc`. Evidence: `benchmarks/results/json-codspeed-20261108/`. This is instruction-count evidence, not a new full throughput qualification.

### Task R5: Performance gates
- [ ] Protobuf B4 on SVM (interpreter ≤ 2.5× stock; reprofile after Phase 1 restoration) — design note first, then measured change. E1's old JVM selected-small 0.864-vs-0.85 miss is informational under the reaffirmed SVM priority, not a release blocker.
- [ ] YAML throughput floors (config/block/flow/text at 0.51–0.77).
- [ ] JSON retained big/huge and the 1.5× logical-memory criterion; duplicate-check price qualification.

### Task R6: #37 Cargo-buildable crates
- [ ] `json/native` crate: `build.rs` compiles `api.cc`, `tape/native/text.cc`, vendored simdjson 5.1.1 and simdutf 0.7.0 with `cc`, honoring `CXXSTDLIB`; generated `seam.h` vendored with a CI check against the Myna generator output.
- [ ] `csv/native` crate with per-file ISA flags (`index_avx2.cc` -mavx2, `index_avx512.cc` -mavx512f/bw, `index_neon.cc`).
- [ ] Buffa crate consumable as a git dependency at release tags; no `#[global_allocator]`; simdutf stays in the crate graph.
- [ ] CI: `cargo build -p` on Linux x86-64/AArch64 and macOS AArch64; a link test combining two crates with a dummy Rust staticlib; ABI fingerprint check against the bindings jars.

### Task R7: Qualification and release
- [ ] Push, PR #36 ready, CI green; update the isolated qualification checkout; full native/negative-JIT/sanitizer gates; five-platform staging; consumers; signatures; merge; green main; publish 0.10.0; verify public artifacts; close issues.

Resume 2026-11-08 (hill climb, three Opus workers, Fable briefing/review): worktrees td-climb-slab (climb/slab: allocation-free slab verification, plan-cached SlabLimits, no per-message Budget, leaf `decodeSlabSmall` without PinnedObject, then native_smoke gates from the measured floor), td-climb-b4 (climb/b4: B4 phase 1 per 2026-11-08-protobuf-b4-design.md, TDD, consumer_measure), td-climb-json (climb/json: CodSpeed JSON −12%/−10% reproduced locally via RBE simulation image + valgrind in an amd64 container, bisect 223edf7..1cfcb37, zero-cost fix). Bazel runs serialized through a scratchpad lock wrapper; nothing pushed. Results merge into feat/followup-v5 after review.

Pause 2026-11-08 10:55 PST (hill-climb handoff; Sam ordered wrap-up). Nothing pushed. Three worker branches hold unmerged work, each in its own worktree; merge into feat/followup-v5 only after review and a green JVM run:
- climb/slab (/Users/sam/workspace/graalvm/td-climb-slab): d529c56 "protobuf: verify owning slabs in one allocation-free walk" (single verifier walk, budget folded, no per-message SlabVerifier/long[]/byte[0]/Budget; protobuf-runtime tests pass) then 961d479 wip: SlabLimits cached per plan, SlabMessage shrunk 56->48 B (extent read from HEADER_BYTES_USED, arrays/unsigned maps merged); JVM suites pass (https://app.buildbuddy.io/invocation/41836f9e-1b08-4639-b46e-59b47897d353). Baseline native_smoke at 1cfcb37 confirmed 312 B (https://app.buildbuddy.io/invocation/ad1f0190-4918-4deb-bb98-c80cfc8fde0a). Ruling recorded: keep the two PinnedObjects (Word.objectToUntrackedPointer and ObjectLayout are qualified-export GraalVM internals, not usable); worker's arithmetic says the pins cost 0 B and the 32 B ingest-minus-typed difference is two harness-boxed Longs from the generic DataLibrary read (engines are not read uniformly, so the harness stays; the ingest gate is to be set at its measured floor with the boxing called out). Expected after: ingest-typed 120 B (72 + 48), ingest 152 B (plus two harness-boxed Longs). native_smoke before/after runs were started but NOT yet reported — rerun `bzl test //benchmarks/comparison:native_smoke --test_output=all` in that worktree and set the Comparison.java gates from the numbers.
- climb/b4 (/Users/sam/workspace/graalvm/td-climb-b4): d182a5d tests pinned (exactLayoutGuardMatchesAccepts, boundHooksOnlySkipTheLayoutCheck, foreignLayoutStillRejectedThroughNodes, interpreter-tier Buffa lifetime, strict misuse); then 38f7399 wip: Phase 1 implemented (exactRowClass guard on Accessor.bound for Generated, ProtoRow.Owned, TypedRow.Eager, Batch.ColumnAccessor; bound hooks overridden by every representation, slab keeps requireValue, borrowed/typed keep owner.check; PolicyTest allows hook call sites only in the four read nodes). //tests:test //tests:allocation //protobuf-runtime:test //protobuf-buffa/tests:jvm :jvm_non_strict PASS (https://app.buildbuddy.io/invocation/9a78db31-5057-48ea-83cc-0394218d39d4). Not run: consumer_smoke, consumer_native_smoke, coverage lane, consumer_measure, consumer_interpreter_profile; no performance number yet. Review focus before merge: the exact-class guard must never accept a schema-sharing plan (design §4); TypedRow/Batch were added beyond the design's list, so check their schema-uniqueness claim. Rejection criteria in docs/superpowers/plans/2026-11-08-protobuf-b4-design.md §4 apply.
- climb/json (/Users/sam/workspace/graalvm/td-climb-json): simulation image build for the valgrind/amd64-container measurement was running at wrap-up; uncommitted edits to tape/native/ctape.h, ctape_test.cc, shape_fuzz_test.cc, SvmTape/SvmJsonMessage/TapeExportNode/TapeOwner/TapeReader/TapeText/TapeValue (committed by the lead as a wip on climb/json at wrap-up; the worker's own findings were not yet reported). No instruction counts reported yet. Method: build //benchmarks:simulation on RBE per commit (223edf7, 7e0167b^, 7e0167b, 1cfcb37), run `valgrind --tool=callgrind --toggle-collect=__codspeed_root_frame__databench truffledata-bench json-generic-interop-read` in a `--platform linux/amd64` Debian container (Docker emulation verified working), compare Ir.
Bazel lock wrapper for serialized runs across worktrees: /private/tmp/claude-501/-Users-sam-workspace-graalvm-truffledata/ec457008-2747-4509-875f-1a245e27aa8e/scratchpad/bzl (mkdir lock + JAVA_HOME + PATH=/usr/bin first); recreate if the scratchpad is gone.
Worker reports after the handoff confirm the above (slab: 961d479, JVM suites green, after-run unmeasured; B4: 38f7399, five JVM suites green, unmeasured). New pre-existing risk noted by the slab worker, to triage before release: an untrusted slab whose records repeatedly point at the same child record can make verification exponential in time (the new walk keeps the old per-handle table for a record reached twice; the cost was already there before d529c56). Candidate fix: memoize verified records by handle id in the walk.
Final checkpoint 2026-11-08 10:58 PST (context at 98%): feat/followup-v5 head is the commit after 1b5a4ec; tree clean; nothing pushed; no Bazel run active; three worker worktrees retained (td-climb-slab 961d479, td-climb-b4 38f7399, td-climb-json 57a4215). Resume order: (1) climb/slab native_smoke after-run → set gates → merge; (2) climb/b4 consumer_smoke/native_smoke + consumer_measure before/after → ledger entry → merge or revert per design §4; (3) climb/json: inspect 57a4215 diff, measure 7e0167b^ vs 7e0167b under valgrind in the amd64 container, then fix. Standing rules unchanged: stay local, no push, no CI until Sam says.

## Recovery 2026-11-08 (Codex)

The integration checkout is `/Users/sam/workspace/graalvm/truffledata-followup`;
the original checkout remains on `main`. Three workers resumed the retained
branches with serialized Bazel access and independent source reviews. The JSON
worker had continued after the checkpoint: three uncommitted source edits and
completed simulation/test evidence were recovered and preserved, rather than
discarded as unexpected changes. Nine output bases belonging to deleted worker
workspaces were removed after checking workspace absence and preservation of
their binaries/logs, increasing shared-volume headroom from 2.1 to 3.9 GiB;
shared caches and historical baselines were left intact.

The slab candidate first measured 168 B/message versus the saved 312 B baseline
(`226e5f97-f7d7-4994-b66e-dcdc577089a0`), above the handoff's unverified 152 B
estimate. Separate allocation probes were required before setting new gates.
The previously recorded shared-subgraph verification complexity remains a
separate release blocker; a bounded alias fixture pins its logical accounting.
This recovery does not authorize a push, CI dispatch, main merge or publication.

Slab completion `6e65ff7` is merged locally with its measured 168/136/136 B smoke
gates. B4 completion `c6474e4` is also merged, but **the Phase 1 production
optimization is rejected and restored**: complete paired JVM/SVM captures show
a 17.7% SVM generated-interpreter gain alongside 20.2% slower JVM generated
interpreter reads and 40.7% slower JVM slab resident work, with nonoverlapping
observed ranges for the regressions. Modern-baseline harness support, useful
contract/lifetime tests, and all 20 measurement shards are retained in
`benchmarks/results/protobuf-b4-213a289b/`. The rejected protected Accessor API
does not ship. B4's original performance gate remains open; no Phase 2 work was
started.


## Requested pause after local landing — 2026-11-08

All three worker branches are integrated into `feat/followup-v5`: slab completion
`6e65ff7`, B4 measured rejection `c6474e4`, and JSON completion `6e1cfc1`.
Integration production tip is `151c1d7`; this checkpoint adds the final ASCII
reader/cursor parity test and documentation. Worker branches and evidence remain
available. The original checkout remains on `main`; the integration checkout is
`/Users/sam/workspace/graalvm/truffledata-followup`.

JSON final native/JVM checks pass 9/9 (`cdcbc5cc`), including 24 compiled graphs
and nine allocation budgets. Fresh final-image instruction counts versus main
`223edf7`: export 71,313,261 → 60,138,974 (−15.67%); interop read
34,545,961 → 31,656,185 (−8.36%). The suspected `7e0167b` regression was disproved.
Full throughput and logical-memory qualification are not implied by these counts.

Final combined coverage passes all nine JVM suites (`018a698d`), LCOV validation
for 201 production files across 12 modules, and the access-method coverage contract.
The initial combined run exposed only `TapeReader.isAscii`; an input/pooled,
ASCII/non-ASCII reader/cursor parity test closes it without production changes.
All 14 reporting-tool tests pass. Raw final coverage evidence and test provenance:
`benchmarks/results/followup-integration-20261108/`.

Pause requested by Sam after the performance comparison. No push, CI dispatch,
remote merge, publication or new optimization cycle was performed. All workers
and active build/benchmark invocations are stopped. Future work remains explicit:
B4 interpreter target, E1 JVM selected-small qualification, JSON retained and
validation floors/logical-memory criterion/duplicate-price qualification, YAML
throughput floors, the pre-existing slab shared-subgraph verification time risk,
and complete platform/release qualification. These are not waived or marked done.


## SVM priority reaffirmed; B4 reinstated — 2026-11-08

Sam clarified after the pause that SVM is the main target and JVM is simply a test
target, and instructed restoration of optimizations that help SVM even at a JVM
performance cost. This supersedes the preceding B4 rejection and older JVM
performance release/admission criteria. The policy is now in the root `AGENTS.md`.
JVM semantic, lifetime/API and coverage tests remain required.

The exact B4 production delta measured at `38f7399` is reinstated in `29aa408` alongside the
integrated slab/JSON work, including API-specific tests and the release note for
its additive protected hooks. Existing paired SVM evidence shows full generated
interpretation 259.36 → 213.56 ns (−17.7%), sparse 36.36 → 31.83 ns (−12.4%), and
no demonstrated full/sparse peak regression (overlapping ranges). These are the
prior paired measurements, not a fresh timing capture of the combined branch.
The 2.5x full interpreter/stock target is still open (3.501x generated, 6.724x slab).

Integrated restoration validation passes seven targets in `d19a0431`, including
both native smokes, JVM correctness and allocation tests. The 168/136/136 B scalar
slab allocation gates remain unchanged. All nine coverage targets and the access-
method contract pass in `f2c1f687`, without adding coverage exemptions. Evidence
is retained under `benchmarks/results/protobuf-b4-213a289b/`.

No other proven JSON/CSV SVM winner was found rejected solely on JVM performance.
The proposed next work is recorded in `2026-11-08-svm-next-tranche.md`: nested
structural typed JSON, plan-specialized CSV batch construction, and protobuf
warm-read caches/dispatch, followed by evidence-driven snapshot/streaming/bulk
experiments. Those new optimizations are not implemented by this restoration.
The stay-local/no-push/no-CI/no-publication rule remains in force.

## SVM tranche integrated; final Arm acceptance running — 2026-11-08

The first authorized tranche is integrated locally. Production/test tip is
`7fca64b`; protobuf evidence through `a259275` retains the initial and refined
candidates. CSV, JSON and protobuf owning ledgers are updated. The common
comparison is `benchmarks/results/svm-tranche-20261108/README.md`; it separates
workloads, includes competitors and preserves measured regressions. Baseline
production remains `9a1dcec`, including restored B4 Phase 1.

All paired experiments are complete: CSV `07bb26fb` (78 cells), JSON `950032e5`
(34 cells), final protobuf `1159ca42` (264 cells). JSON typed ingestion improves
9.5–15.5%; CSV string aggregates improve about 1.5–2%; protobuf full slab reads
improve 16.2% interpreted and 32.1% peak. All allocation/retention comparisons
match. Generic JSON ingest is 2.2% slower; some sparse protobuf first reads are
3–4.5% slower. B4 remains above its 2.5× direct-stock interpreter target.

Combined coverage `b436036f` passes all nine targets (455 tests), LCOV validation
for 201 production files across 12 modules and the access-method contract, with
no new exclusions. All five x86 native targets pass in `6227eb3c`, including
protobuf strict, non-strict and Java fallback publication races. The 14 reporting
tests and four CSV paired-runner tests pass. All artifacts are saved in the common
results directory.

Arm acceptance is currently active in `0de92e3b-a02f-4532-ac98-eed245afec71`; CSV
has passed and four remaining targets are building. Root owns the serialized
Bazel slot. Log: `/tmp/td-tranche-native-arm64.log`. A new case-sensitive sparse
cache is mounted at `/private/tmp/truffledata-svm-arm64-20261108` (64 GiB sparse
image alongside it) because the shared volume had only 2.6 GiB free. Use that
`--output_user_root`, its `repository-cache` directory and `--config=remote_arm64`.
An initial ordinary-APFS output override was correctly rejected before Bazel ran;
the guard output and setup are preserved. No Arm throughput claim is made.

Remaining work: finish/archive Arm acceptance, update this checkpoint and the
common report to final status, commit, verify clean state/no active invocation,
then give Sam the requested performance table and pause. Workers have finished
and require no further work. Original `main` stays clean at `223edf7`. Nothing
has been pushed, dispatched to CI, merged remotely or published. No further
optimization tranche or release qualification is authorized by this checkpoint.
