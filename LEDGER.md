# TruffleData — resume ledger (paused 2026-11-09 ~16:00 PST)

Sam paused all work to resume with **Fable 5.2**. This file is the single entry point:
read it, then the linked ledgers, then act. Project rules: `AGENTS.md` (SVM is the
production performance target; never weaken safety checks; serialize local Bazel).

## Resume 2026-11-09 16:05 PST (Fable 5.2 lead) — IN PROGRESS

Sam: "resume this and finish it, your time budget is 30-45mins to wrap up", workers on Opus,
Fable only for the most sensitive critical code. Running now (workflow run `wf_b9eb1329-456`,
worker deadline 16:30 PST):

- Opus worker per worktree: `cells-a` (Buffa tests, then CSV route or trim), `cells-b` (JSON),
  `cells-c` (YAML), and the F1 remaining gates in the F1 worktree (arm64 lane first, then SVM
  amd64, then macOS local). Each cells branch must end as a correct, tested subset; untested
  work is trimmed in a new commit.
- Fable critical-section review of each cells diff (`git diff 0e0c539e...HEAD`) after its worker.
- Bazel lock wrapper for this session:
  `/private/tmp/claude-501/-Users-sam-workspace-graalvm-truffledata/f86c3c78-8f8a-4faf-8736-a1f1ee674e61/scratchpad/bzl.sh`.
- **#57 is no longer held.** Sam merged it to main himself at 16:14 PST (`1158dc93`); main is
  merged into `deps/myna-0.14.0`. 0.11.0 therefore ships the sticky closed-enum map-entry
  rejection: stable round trip, but an invalid-then-valid entry is orphaned where protobuf-java
  generated code keeps it (evidence in the #57 thread). Option (b) there (keep the entry, drop
  invalid occurrences) is open follow-up. Lines below that say "#57 is held" are superseded.
- Sam also asked for: Fable check of the Opus work, then a Fable implementation pass on the
  cells code before merge (hot decode paths first).
- Lead then: merge reviewed branches into `deps/myna-0.14.0`, push, watch PR #67. Outcomes are
  recorded in "Resume outcome" below when the workflow returns; until that section exists,
  treat the worker state below as the last known truth and inspect each worktree's `git log`.

## Goal in flight

Release **truffledata 0.11.0**. Sam's gate: ship after the **Myna 0.14.0 +
Elide toolchain 2026.11.6 + Elide GraalVM 25.5.5-elide.28** bumps AND the **full Myna
Truffle-cell adoption** land and are green. Sam then said "ship and then verify after"
(publish as soon as the code lands; remaining verification may follow the release), and
"no ill wait lets land it right" (do not split the release).

### Sam's rulings this session (all binding)

- Cells: "fully and totally adopt all features especially for `NativeCell`" (reference:
  elide-dev/myna `docs/truffle-cells.md`), on **all four seams** (buffa, json, yaml, csv).
  **Default on for a format only if cells beat baseline on paired SVM; otherwise default off;
  the zero-overhead flag exists either way** (build-time fold on SVM, `static final` on JVM).
- "we should adopt specialized calls present in `NativeCell`": every scalar-result cell
  call site holds the concrete `*_Cell` and calls `executeInt`/`executeLong`/
  `executeDouble`/`executeBoolean`; generic `execute(Object[])` only for pointer/void/resource
  results. Mandatory and checked (see brief).
- "we're adopting `.28`". Fable owns "the most targeted critical sections where complexity is
  most important to get right for perf or safety".
- Approved PRs are squash-merged (admin if necessary). **#57 is held** (Sam chose "Hold,
  comment"): it diverges from protobuf-java generated-code map semantics; evidence posted
  as a PR comment.
- Central publication remains deferred (#28, quota resets 2026-12-01).

## Repository state

- `origin/main` = `7b5388f`, **green** (CI, Untrusted input, combined status success).
  Landed today: #62 (beat-stock-svm tranche + bot fixes #53–#56, #58–#61, CI fixes), #63,
  #64 (library count fixed to twenty-five), #65, #66 (expected-failure JIT controls no longer
  post commit statuses — this was the "main is red" cause).
- Open PRs: **#67** (`deps/myna-0.14.0` → main, the 0.11.0 release PR; CI was running at
  pause) and **#57** (held).
- Branch `deps/myna-0.14.0` (pushed to origin at `83de0227`; this ledger adds one local
  commit on top): GVM .28 + EE dists (sha256-pinned), toolchain 2026.11.6 bundles, Rust
  unchanged (`nightly-2026-10-09`), Maven repinned, **Myna pinned to the published
  `myna-source-0.14.0.tar.gz`** (sha256 `14b45b73…5e20f`, verified against `SHA256SUMS`;
  content-identical to release commit `a284fd6`), F1 merged, 0.11.0 notes updated
  (`docs/RELEASE_0_11_0.md`). Version is already 0.11.0 (`MODULE.bazel`, release default).
- Release plumbing proven on 0.10.0: `MAVEN_BUCKET=r2:elide-maven SSL_CERT_FILE=/etc/ssl/cert.pem
  tools/release/publish.sh 0.11.0 --publish` from a clean checkout of the merged main commit
  (Apple silicon host; builds 5 native platforms; runs full tests; signs; validates Central;
  uploads R2). Then signed tag `v0.11.0`, GitHub release with native bundles + checksums,
  public verification (receipts pattern: `benchmarks/results/release-0.10.0/`). A clean
  release worktree exists at `/Users/sam/workspace/graalvm/td-release-0.11.0` but is at
  the OLD commit `916eff6` — recreate it at the final main commit.
- Local Bazel lock wrapper: `docs/superpowers/plans/briefs/2026-11-08-beat-stock-svm/bzl.sh`
  (copy to a scratch path, `chmod +x`, set `LOCK=`; one local invocation at a time).

## Workstreams and worker state at pause

Briefs: `docs/superpowers/plans/briefs/2026-11-09-myna-0.14/` (`f1-abi-class-split.md`,
`f2-f3-cells-adoption.md`). Detailed ledger: `docs/superpowers/plans/2026-11-09-myna-0.14-ledger.md`.

### F1 — Myna 0.14.0 ABI-class split (Fable) — MERGED into `deps/myna-0.14.0`

Myna 0.14.0 (myna#71) makes C `char` signedness part of the ABI class; aarch64 Linux gets its
own fingerprint even without `char`, so multi-target `validate` failed and arm64 natives
would refuse to load. F1: bindings generated per ABI class into **one shared jar**
(`<Class>Abi` constants for `LP64_LITTLE` and `LP64_LITTLE_AAPCS64`); every consumer verifies
natives against the host's class once (`TargetAbi`), mismatch still throws (no fallback);
`check_natives.py` and `bazel/myna_contract.py` enforce per-platform classes (CSV now covered);
Cargo crates select the class in `build.rs` and vendor copies were refreshed; per-format
"other class is rejected" tests and `TargetAbiTest`.
Worktree `.claude/worktrees/agent-adedc1adc03b4dcae`, branch `worktree-agent-adedc1adc03b4dcae`
head `b171d7ea` (merged, including its interim gate ledger). **Passed** (RBE linux-x86_64):
bindings for every seam + cells variants (`1988cbbf`), all Java consumers compile (`b6695d43`),
`myna_contract`, `//csv/tests:{jvm,jvm_native_abi}`, `//json/tests:jvm`, `//yaml/tests:jvm`,
`//tests:test`, Buffa JVM lanes, cells JVM lanes, `//tools/release:test` (`d9e98827`,
`b1e8023c`); Cargo crate link tests locally on macOS (needs `CC=/usr/bin/cc CXX=/usr/bin/c++`;
the Elide `cc` shim is not a compiler driver); `test_check_natives.py`. **Not run:** SVM amd64
native tests + `*_aot_symbols` budgets; the `--config=remote_arm64` lanes (the only lane that
exercises the AAPCS64 class for real); macOS local native lanes. Full list in the 0.14 ledger.

### Cells A — Buffa + CSV kernel (Opus, time-boxed)

Worktree `.claude/worktrees/agent-afb0432b0ff937887`, branch **`cells-a` @ `02929c87`**
(base `0e0c539e`), clean.
- `ab37e713` Buffa: both cells backends call `executeInt` on the concrete `*_Cell` types; the
  `(Integer) execute(...)` casts are gone; flag still default off. **Untested**:
  `//protobuf-buffa/cells/tests:{jvm,jvm_off}` were running and were stopped at pause.
- `f8fae309` CSV: work-in-progress JVM-only `//csv/cells` route behind `truffledata.csv.cells`
  (default off). **Never compiled, no test.**
- Not started: CSV SVM variant + hosted Feature, CSV flag-on parity test, borrow/resource
  declarations, measurement.

### Cells B — JSON (Opus, time-boxed)

Worktree `.claude/worktrees/agent-ae6bd3a251a07bc94`, branch **`cells-b` @ `343e5d09`**
(work in `63536c55`, ledger note in `343e5d09`), clean.
- `json/cells/BUILD.bazel`: a derived copy of `json.seam` (module line only) with cells on,
  FFM backend, class `JsonCellsJvm`; JVM cell bindings generated from it;
  `json/native/BUILD.bazel` exports `json.seam`. Production seam/fingerprint unchanged.
- **Nothing built or tested** (first bindings build stopped at pause).
- Not done: `truffledata.json.cells` flag, routing decode through cells, SVM variant, parity
  test, `myna_contract --derived` registration, resource/borrow declarations.
- Note: on the JVM `decode(byte[])` calls native `decode_adopted`, not `decode`; that is the
  cell to route.

### Cells C — YAML (Opus, time-boxed)

Worktree `.claude/worktrees/agent-aa2239fd5faece156`, branch **`cells-c` @ `ba10b754`**, clean.
- `yaml/cells/BUILD.bazel`: two derived seams from `yaml.seam` (module line only), `YamlCells`
  (SVM) and `YamlCellsJvm` (JVM), cell bindings at `//yaml/cells:bindings`;
  `yaml/native/BUILD.bazel` exports `yaml.seam`. Production seam/fingerprint unchanged.
- **Never built** (first build stopped at pause); generated file names in the BUILD are guessed
  from the Buffa layout. `//yaml/tests:jvm` not run.
- Not done: cells backends for decode entries, `truffledata.yaml.cells` flag,
  `myna_contract --derived` registration, parity test, resource/borrow declarations.

The time-boxed workers were told: derived cells seams (production seams/fingerprints
unchanged), default-off flags `truffledata.{buffa,csv,json,yaml}.cells`, specialized
`execute*` calls, an identical-results JVM test per format; resource/borrow declarations only
if time allowed. The full brief (resources for every create/release pair, call-scoped
borrows, `NativeBytes`/`HeapBytes`, build-time init, blocklist-safe code, guest-error
semantics, paired SVM measurement and the per-format default decision) is **not yet done** for
any format.

## Open rulings for the lead

- **F1 design (one jar per format, both classes):** Myna 0.14.0's two ABI classes differ only in
  the fingerprint constant (and `charUnsigned` in the descriptor). `bazel/myna.bzl` validates
  each class across its targets; `bazel/myna_abi.py` emits `<Class>Abi` (`JsonAbi`, `YamlAbi`,
  `BuffaAbi`, `CsvNativeAbi`) with both fingerprints and folds both into one `seam.h` selected by
  `__CHAR_UNSIGNED__`, failing the build if the classes ever differ in anything else. JVM:
  `core.TargetAbi` picks once; SVM: `Platform.includedIn(LINUX_AARCH64)` folds at image build.
  Rulings F1 asks for: (1) the post-processed combined `seam.h`, or pristine per-class headers;
  (2) an unknown host defaults to the signed class. Details in the 0.14 ledger.
- **Per-format cells default:** needs paired SVM measurement, flag-on vs flag-off (Sam's rule).

## Next steps (in order)

1. For each cells branch: review against `f2-f3-cells-adoption.md` (critical sections:
   allocation on decode paths, ownership/close semantics, borrow lifetimes; flag-off code and
   AOT budgets byte-identical), finish or trim to a correct, tested subset, merge into
   `deps/myna-0.14.0` (ledger conflicts: keep both sections, delete all markers).
2. Finish F1's remaining gates (above).
3. Paired SVM measurement per format, flag-on vs flag-off; rule default on/off per Sam's rule;
   record numbers whatever the sign.
4. Push `deps/myna-0.14.0`; get PR #67 green (watch: coverage contract — add SVM-only methods
   to `SVM_ONLY_METHODS` with reasons, test JVM-reachable ones; `PolicyTest` exported-surface
   and boundary budgets; `MODULE.bazel.lock` refresh; benchmark `prepare` input stability).
5. Squash/admin-merge #67; publish 0.11.0 from the merged commit (above); tag, GitHub release,
   verify public artifacts; write receipts under `benchmarks/results/release-0.11.0/`;
   update `AGENTS.md` "Release completion".
