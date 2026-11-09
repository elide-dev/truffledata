# F2 / F3 — Full Myna 0.14.0 Truffle-cell adoption

Owners: two Fable workers, each in its own git worktree.
- **F2:** Buffa (`protobuf-buffa/native/buffa.seam`, the existing WS12 cells route) and the
  CSV kernel (`csv/native/csv.seam`, `csv/kernel`).
- **F3:** JSON (`json/native/json.seam`) and YAML (`yaml/native/yaml.seam`).

Base: the lead's `deps/myna-0.14.0` after F1 (the aarch64-Linux ABI-class split) has merged.
Read F1's ledger entry in `docs/superpowers/plans/2026-11-09-myna-0.14-ledger.md` first.

## Sam's rulings (2026-11-09)

- "It's important that we fully and totally adopt all features, especially for `NativeCell`."
  The reference is Myna's `docs/truffle-cells.md` (Myna source unpacked at
  `/private/tmp/claude-501/-Users-sam-workspace-graalvm-truffledata/3fb12e8e-77a4-4ef6-8f8a-02a89b912978/scratchpad/myna-src/myna-a284fd6e19133a9fe9b0821d9c9390f8ca3bf3d2`;
  also `docs/releases/0.14.0.md` and `docs/truffle-guests.md`).
- Scope: all four seams.
- Default: **cells become the default route for a format if they beat the baseline on paired
  SVM measurement; otherwise default off. The flag exists either way.** The flag follows WS12:
  build-time on SVM (folded in a hosted Feature, no runtime branch), `static final` on the JVM,
  zero overhead when off (prove it with symbol-identical budgeted AOT shapes).
- Fable owns the critical sections, where complexity matters most for perf or safety.

## What "full adoption" means here (every item applies where the seam has the shape)

1. **Cells** for every import a guest-facing path calls (`truffle_cells=true`, transitions
   `direct`, backend `native` on SVM and `ffm` on the JVM). The WS12 derived-seam approach
   (production seam unchanged, cells from a build-derived seam) is an option, not a
   requirement. 0.14.0 changes every fingerprint anyway, so choose the simplest correct
   layout and record why.
2. **Specialized `NativeCell` calls (Sam, 2026-11-09: "we should adopt specialized calls
   present in `NativeCell`").** Mandatory, not just for hot paths. Every TruffleData call site
   of a scalar-result cell holds the concrete generated `*_Cell` type and calls the
   specialized method the generator overrides (`TruffleCells.typed`): `i32` status returns
   (`decode`, `typed`, `session_decode`, `plan_create`, `index_leaf`'s peers, …) through
   `executeInt`; `i64`/`u64`/`usize`/`u32` returns (`stream_feed`, `stream_truncated_bytes`,
   `index`, `index_leaf`, `validate_utf8`, `utf8_to_utf16`, …) through `executeLong`; `bool`
   through `executeBoolean`; floats through `executeDouble`. Generic `execute(Object[])` is
   used only for pointer/`void`/resource results, and never where a typed method exists.
   There must be no result box and no `UnexpectedResultException` handler on any decode path.
   Prove it: the AOT symbol inspection of each flag-on image shows no `execute(Object[])` call
   and no boxing (`Integer.valueOf`/`Long.valueOf`) on those call sites, and a unit test fails
   if a scalar cell call site is routed through `execute` (for example, a source scan or a
   wrapper that rejects it).
3. **`resource` declarations** for every create/release pair (sessions, streams, plans,
   layouts, typed owners, decode results that own native memory): the create cell returns a
   `NativeResource` (exactly-once close, thread confinement where the owner is confined). Map
   this onto the existing Java owners (`TapeOwner`, sessions, `SlabSession`, …) without
   weakening their lifetime checks. Decide per owner whether `registerCleanup(Cleaner)` is
   right (shared only; confined owners refuse it).
4. **`borrow … lifetime=call` declarations** for every input pointer+length pair, so heap
   inputs pin (≥ `Borrow.pinThreshold()`) or use the per-thread scratch stack (≤16 KiB), and
   native inputs (`NativeBytes`, mapped `MemorySegment`s) lend in place with no copy. Writable
   borrows (output buffers) must write back correctly; prove the write-back and the
   release-order invariants with tests.
5. **`NativeBytes` / `HeapBytes`** wherever native or heap byte regions cross the boundary
   (mapped CSV input, slab arenas, tape/text regions), instead of raw addresses.
6. **Build-time initialization**: the generated `native-image.properties` and `myna-truffle`'s
   own; every TruffleData node class around a cell initializes at build time.
7. **Blocklist-safe code**: no try-with-resources (`addSuppressed`) on partially evaluated
   paths; `try`/`finally` as the generated wrappers now do. Verify with
   `-H:+TruffleCheckBlockListMethods` and a runtime-compiled probe
   (`CompilerDirectives.inCompiledCode()` observed), as Myna's own `truffle_integration` does.
8. **Null / coercion semantics** (`truffle_coercion=strict`): host `null` and out-of-range
   values raise `NativeCellException` and reach guests as ordinary guest errors. Add guest-level
   tests.

Leaves stay leaves (`native_leaf=true` imports must not change status). Do not change the C ABI,
`abi=` numbers, slab/tape formats or goldens.

## Critical sections (the reason Fable owns this; get these exactly right)

- The decode hot paths: per-call allocation must be scalar-replaced, or at most equal to the
  myna#85 floor, measured and recorded. Keep every existing allocation budget and AOT symbol
  budget on the flag-off route byte-for-byte.
- Ownership: a `NativeResource` must never double-free, leak, or outlive its batch/owner, and
  closed-owner reads must still fail with the existing messages. Concurrency: shared resources
  closed concurrently must surface the guest-visible closed error (myna#78/#80), never a VM
  crash.
- Borrow lifetimes: nothing borrowed may be retained past the call. Results that keep native
  memory go through `resource`, never a borrow.

## Measurement and the default decision

Paired SVM, like the beat-stock tranche (same harnesses: `consumer_measure`, `csv_pair`,
`json_pair`/the JSON comparison, the YAML comparison). Compare flag-off after vs before
(within fork range, symbol-identical), then flag-on vs flag-off on every cell of each
format's matrix. Cells become the default for a format only if they win: no loss outside fork
range on any matrix cell, and a disjoint-range gain on the format's primary cells. Record the
numbers whatever the sign; a loss is a recorded finding, not a failure.

## Gates

Each format's JVM lanes, native tests, SVM acceptance/smoke, `*_aot_symbols` budgets,
`//tests:test` (PolicyTest: register any new exported interop surface and boundary budget with
a reason), `//tools/release:test` (new or changed artifacts), the coverage contract
(`.github/scripts/coverage_contract.py`; SVM-only methods are listed in `SVM_ONLY_METHODS` with
a reason, JVM-reachable ones get tests), and Linux arm64 native jobs.

## Rules

Read AGENTS.md. Serialize every local Bazel invocation through the lock wrapper (copy
`docs/superpowers/plans/briefs/2026-11-08-beat-stock-svm/bzl.sh` to a scratch path, `chmod +x`,
and set `LOCK=/private/tmp/claude-501/-Users-sam-workspace-graalvm-truffledata/3fb12e8e-77a4-4ef6-8f8a-02a89b912978/scratchpad/bazel.lock`).
Do not push or open PRs. Commit often. Append ledger entries (`Task F2/F3: Measured … /
Ruling …`, with invocation ids) to `docs/superpowers/plans/2026-11-09-myna-0.14-ledger.md`. If
your session runs low, commit and write an interim entry that says exactly what remains.
Report in under 500 words: what landed, the gate results, the measured flag-on vs flag-off
numbers, your default-on recommendation per format, and anything the lead must rule on.
