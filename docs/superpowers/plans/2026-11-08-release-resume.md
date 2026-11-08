# Release 0.10.0 Resume Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax for tracking. The ledger is `docs/superpowers/plans/2026-11-07-distribution-ledger.md` (append "Resume" lines).

**Goal:** Take `feat/followup-v5` from the 2026-11-07 pause checkpoint to a merged, green main and a published 0.10.0, closing the open issues (#24–#37) and the recorded performance gates or ruling on each with evidence.

**Architecture:** No new subsystems. Fix the CI blockers first, then the measured performance gates per format (protobuf B4/E1, JSON retained and GB/s, YAML floors, CodSpeed JSON regressions), then the Cargo-buildable crates (#37), then qualification and publication exactly as the distribution ledger prescribes.

**Spec:** `docs/superpowers/plans/2026-11-07-distribution-ledger.md` (pause checkpoint, resume order, release authorization), plus the per-format plans it references.

## Global Constraints

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

### Task R1: CI coverage contract (DONE for the inline readers; remaining gaps in progress)
- [x] Reorder the inline-reader parity test so each reader meets its field first (commit efdc900).
- [ ] Cover Csv/CsvPlan overloads, Buffa borrowed/materialized/session surface, TypedRow text-path accessors and cached ViewOwner checks, runtime GeneratedMessage unknown queries, SlabMessage projected/copyProjectedRecordOf/Usage, HeapValueTape region bytes, TapeReader node text word, YamlMessage confined; exempt `SlabSession::outside` with its reason.
- [ ] Verify: `bazelisk coverage <nine JVM targets> --config=codecov && python3 .github/scripts/reports.py coverage && python3 .github/scripts/coverage_contract.py` → no `Uncovered access methods`.
- [ ] Commit.

### Task R2: macOS CSV CI (DONE)
- [x] `bazel/patches/elide-toolchain-cc.patch` canonicalizes the SDK path (commit 9c53f2e); local `--config=local //csv/native:api_test //csv/native:index_test` pass.
- [ ] Confirm on CI after push (Native targets / CSV / aarch64-apple-darwin).

### Task R3: legacy `native_smoke` compact-scalar allocation (416 B > 128 B)
- [ ] Decode SVM slabs into a pinned heap array and `SlabMessage.takeOwnership` (drops the DirectByteBuffer wrapper and the second copy); size the first attempt from the learned `slabCapacity` with a small floor so the retained array is the slab's exact extent after the first message.
- [ ] Run `bazel test //benchmarks/comparison:native_smoke` and record `ALLOCATION compact-scalar buffa ingest bytes/message`; re-base the budget in `benchmarks/comparison/Comparison.java` to the measured design cost with the breakdown in the protobuf ledger.

### Task R4: CodSpeed JSON regressions
- [ ] Reproduce `json-generic-java-export` (-12%) and `json-generic-interop-read` (-10%) on the simulation image against main `223edf7`; bisect across the branch's tape/JSON commits; fix or rule with evidence.

### Task R5: Performance gates
- [ ] Protobuf B4 (interpreter ≤ 2.5× stock; `Accessor.bound` 18.7% of samples) and E1 JVM selected-small (0.864 vs 0.85) — design note first, then measured change.
- [ ] YAML throughput floors (config/block/flow/text at 0.51–0.77).
- [ ] JSON retained big/huge and the 1.5× logical-memory criterion; duplicate-check price qualification.

### Task R6: #37 Cargo-buildable crates
- [ ] `json/native` crate: `build.rs` compiles `api.cc`, `tape/native/text.cc`, vendored simdjson 5.1.1 and simdutf 0.7.0 with `cc`, honoring `CXXSTDLIB`; generated `seam.h` vendored with a CI check against the Myna generator output.
- [ ] `csv/native` crate with per-file ISA flags (`index_avx2.cc` -mavx2, `index_avx512.cc` -mavx512f/bw, `index_neon.cc`).
- [ ] Buffa crate consumable as a git dependency at release tags; no `#[global_allocator]`; simdutf stays in the crate graph.
- [ ] CI: `cargo build -p` on Linux x86-64/AArch64 and macOS AArch64; a link test combining two crates with a dummy Rust staticlib; ABI fingerprint check against the bindings jars.

### Task R7: Qualification and release
- [ ] Push, PR #36 ready, CI green; update the isolated qualification checkout; full native/negative-JIT/sanitizer gates; five-platform staging; consumers; signatures; merge; green main; publish 0.10.0; verify public artifacts; close issues.
