# TruffleData — resume ledger (updated 2026-11-09 20:05 PST)

Sam paused all work to resume with **Fable 5.2**. This file is the single entry point:
read it, then the linked ledgers, then act. Project rules: `AGENTS.md` (SVM is the
production performance target; never weaken safety checks; serialize local Bazel).

## Current state — 2026-11-09 19:45 PST — READ THIS FIRST

Sam's instructions, newest first (all binding):
- 20:26 "i don't care about JVM/Hotspot performance at this time, for this release, only SVM
  performance. so don't roll anything back on JVM's account." So: JVM allocation or timing
  regressions (e.g. the Buffa flag-on session decode at 528 B on Myna 0.16.0) are recorded, not
  fixed and not blocking; HotSpot route conversions to `invoke` are optional. JVM correctness
  tests still must pass.
- 19:49 "it's fine, adjust codecov ignores/settings if needed to pass".
- 19:22 "get this landed": merge PR #67 to main now, with what is verified. DONE 20:01 PST.
- 18:55 "hold on final benchmarks, as myna is releasing fixes in 0.16.0": no paired SVM
  measurement until Myna 0.16.0 is pinned. Measurement decides each flag's default.
- 17:15 "let's finish and then publish": publish 0.11.0 only after adoption is finished.
- 17:16 "myna 0.15.0 is out" (superseded by 0.16.0 before it was merged).
- After release: re-bench, much prettier benchmark cards for the README, README cleanup.
- Workers on Opus; Fable reviews their work and does its own implementation pass.

The lead reads these together as: land the code now with every cells flag default off; publish
after the 0.16.0 pin, the measurements and the release packaging. A session restart at 19:16
killed every worker; nothing is running in the background.

### Follow-up branch `release/0.11.0-finish` — state at 20:05 PST

THIS COPY OF THE LEDGER (on `release/0.11.0-finish`, worktree `.claude/worktrees/finish-011`) is
the current one; the copy on `deps/myna-0.14.0` stops at the PR #67 push.

- **PR #67 IS MERGED** (20:01 PST): squash commit `02c8c247` on main, 16/16 checks green on
  head `1c858284` first, including `codecov/patch` after `codecov.yml` was added at Sam's request
  ("adjust codecov ignores/settings if needed to pass": Native Image-only sources ignored, patch
  status informational; `coverage_contract.py` stays the enforced gate). Main now has all four
  cells routes, flags default off, on Myna 0.14.0. **Main is green at `02c8c247`** (20:13 PST:
  CI success, Untrusted input success, combined status success). This branch has main merged in (`-s ours`:
  main's tree is exactly `1c858284`, which this branch already contained).
- The main checkout sits on `deps/myna-0.14.0` (tree identical to main). Its LOCAL `main` branch
  is stale and diverged (1 local commit `59508196`, 174 behind): left untouched, do not reset it
  without asking Sam.
- HotSpot finding from the 0.16.0 re-pin: Buffa flag-on session decode allocates 528 B/message
  on Myna 0.16.0 against 240 B on 0.14.0 (flag off: 0 B on both). The worker raised the test
  ceiling to the measured value and recorded it as a regression. The lead told Sam's Myna
  session. Fix on our side: move every route entry whose buffers are native addresses (already
  scope-checked by the route) to `invoke` on HotSpot too; keep typed `execute` only where a heap
  array is lent.
  Sam's Myna session replied at 20:05 PST: cause found (a recursive `BytesCoercion.execute`
  path that host JITs compile, so each byte parameter's `Borrow` is no longer scalar-replaced
  when several cells share a JVM); fixed under the Graal JIT by elide-dev/myna#105, which ships
  as **0.16.1**, not in 0.16.0; under C2 the `execute` path stays heavier than 0.14.0 even then.
  It confirms `invoke` is the right entry for host code on HotSpot too (a plain static call).
  0.16.0 publication is held only for Myna main CI's native Linux and macOS jobs.
- **Myna 0.16.0 is PUBLISHED** (20:17 PST, `publishedAt` 2026-11-10T04:16:57Z). The public
  tarball's sha256 is `51149cbf503d60dbe6a3e7178455dfc34b8335dd5fbe6b803423d409138b51e3`, equal to
  `SHA256SUMS` and to the pin on `deps/myna-0.16.0`, so the pin can go through CI.
  0.16.0 adds `public static invoke(...)` on every generated cell (native carriers, raw `long`
  addresses; no `Object[]`, boxes, `NativePointer` or `Borrow`): "the entry for host code
  compiled ahead of time", measured by Myna at the cost of the direct call, 0 B.
- **Design ruling (lead):** Native Image routes are host code, so they call `X_Cell.invoke(...)`
  and hold no cell instance, argument array or pointer carrier. HotSpot routes use `invoke` for
  entries whose buffers are all native addresses the route has scope-checked, and keep the typed
  `executeInt`/`executeLong` calls only where a heap array is lent (Myna copies it). Every
  call-shape gate must then REQUIRE `invoke` on the Native Image sources, and on HotSpot allow
  only `invoke` and typed calls.
- On this branch now (`git log 1c858284..`):
  - `17c8620b` YAML Native Image cells shared image-wide + four-thread parity. Verified 7/7
    (BuildBuddy `bbff5392-f8a0-4dc7-870f-9ca38a454e2c`). Becomes moot for the Native Image route
    once YAML moves to `invoke` (no cell instances), but stays correct meanwhile.
  - `cells2-release` @ `2a33a8c0` merged: release packaging done by Opus worker
    `cells-release-2`: `truffledata-cells` (Myna's cells runtime, once) plus
    `truffledata-{protobuf-buffa,json,yaml,csv}-cells`; 25 → 29 libraries in every module list,
    the BOM, README and `docs/PUBLISHING.md`; per-format docs sections. `//tools/release:test`
    passes (BuildBuddy `ae8b8d87-c4e6-4386-9596-95b9fd465690`). Not run: a staged release or a
    Maven-resolved consumer of the new artifacts. Caveat for Sam: `truffledata-yaml-cells`' POM
    brings the JVM-only `truffledata-yaml-jvm` and `truffledata-tape-jvm` onto a Native Image
    class path, because the route jar holds both runtimes' backends (the Buffa precedent).
  - `af678315` CSV Native Image route converted to `invoke`, with its tests. NOT COMPILED yet:
    this branch is on Myna 0.14.0 until `deps/myna-0.16.0` merges. It is the reference shape for
    the other three routes.
- `deps/myna-0.16.0` @ `d9bc0e34` (pin + vendored headers) is merged here (`e6f186e5`). Opus
  worker `myna-016` is still finishing in its worktree: Native Image lanes on 0.16.0 with call
  sites unchanged, and the Buffa HotSpot ceiling recorded at 528 B. Merge its later commits.
- `f3545f09`: the shared scan `//protobuf-buffa/cells/tests:call_shape` now REQUIRES
  `X_Cell.invoke(...)` in Native Image route sources (file name contains `Svm`), forbids every
  `execute*` call there, and checks that an `invoke` names a cell the generated sources declare
  with that entry. It fails until Buffa, JSON and YAML are converted.
- Conversion workers (Opus, launched 20:20 PST, each from `f3545f09` in its own worktree
  `.claude/worktrees/cells3-<fmt>`, branch `cells3-<fmt>`): `invoke-json`, `invoke-yaml`,
  `invoke-buffa`. Each converts its Native Image route to `invoke` (no cell instances, argument
  arrays or pointer carriers), its HotSpot route where buffers are native addresses, flips its
  gates from "typed execute" to "invoke", re-measures allocation ceilings, and runs its JVM and
  Native Image lanes. Generated `invoke` signatures are in each worker's brief and can be
  re-read from `bazel-bin/<fmt>/cells/generated/{svm,jvm}/*Truffle.java` after
  `bzl.sh build --remote_download_outputs=all //<fmt>/cells:generated_sources`.
- CSV conversion (`af678315`) verification on 0.16.0 queued: `<scratchpad>/finish-csv1.log`.
- Next, in order: Fable review of each worker's diff; merge the three branches and `myna-016`'s
  remaining commits here; run every lane (`bazel test //...` plus the manual Native Image
  targets, the negative controls, the coverage lane with `//json/cells/tests:jvm` added); LIFT
  THE BENCHMARK HOLD and run the four paired measurements (commands in the 19:10 note below);
  rule each default; update `docs/RELEASE_0_11_0.md` (Myna 0.16.0, measured numbers) and
  `AGENTS.md`; open the follow-up PR; CI; merge; publish (commands under "Repository state");
  then the post-release README and benchmark-card work.

### What is on `deps/myna-0.14.0` (local head; see `git log`), being landed through PR #67

- Everything in "What landed" below (Myna 0.14.0, GVM .28, toolchain 2026.11.6, F1, #57).
- **Cells routes on all four seams, flags default off**: `truffledata.{buffa,json,yaml,csv}.cells`.
  Opus implemented each; a Fable pass reviewed and fixed each (CSV and Buffa passes reported;
  the JSON and YAML passes had committed their fixes but died before reporting, and the lead
  reviewed those commits). Notable fixes from the passes: use-after-free on the Buffa JVM route
  (raw address lent without the FFM scope check), a native-memory leak and per-parse cell
  construction on CSV, OOM-safe release and once-per-limits boxing on JSON, caller-segment calls
  kept on the FFM binding and complete symbol verification on YAML.
- **Lead's pass**: integrated the four branches; CSV and YAML now fail the image build when the
  flag is set without the route (JSON already did) — holders `csv/kernel/.../svm/CellsRoute`,
  `yaml/.../SvmRoute`, `json/.../JsonCellsRoute`; CSV joined the call-shape scan; coverage
  contract inventories the new YAML holder; flag-on benchmark images
  `//benchmarks/comparison:{csv,json,yaml}_svm_cells`, negative controls
  `…_svm_cells_unregistered`, and a paired YAML harness `:yaml_pair`.

### Evidence on the integrated tree (`9194c035`, RBE linux-x86_64)

- `bazel test //...` plus every manual Native Image target for cells and baselines:
  100/100 pass (BuildBuddy `62c0a34e-6f32-4725-beb6-7d351c0ddec1`). Includes
  `//{csv,json,yaml,protobuf-buffa}/cells/tests:native_test`, `//yaml/cells/tests:flag_off_image_test`,
  `//json/cells/tests:aot_shape`, the four baseline `native_test`s,
  `//benchmarks/comparison:{json,csv,consumer}_aot_symbols` and `:consumer_cells_call_shape`.
- Negative controls: all three `…_svm_cells_unregistered` images fail to build with the guard's
  message (BuildBuddy `1f9d9352-7d03-4b00-8a93-5c214119d378`).
- CI coverage lane (ten targets, `--config=codecov`) passes and `coverage_contract.py` exits 0
  (BuildBuddy `9eaf8d4f-20e6-4bc0-a026-49182ef961bf`); `.github/scripts` unit tests pass.
- F1 linux arm64 11/11 (BuildBuddy `c4ea276d-35c8-4c03-ac9f-61be5894987b`), on `d1ca534c`.
- NOT run on the integrated tree: arm64 lanes, macOS `--config=local` lanes, a staged release.

### Not done — needed before 0.11.0 is published

1. **Myna 0.16.0** (not released at 19:40 PST; watch `gh release list -R elide-dev/myna`). Re-pin
   with the procedure in `docs/superpowers/plans/2026-11-09-myna-0.14-ledger.md` ("Myna re-pin
   procedure"), rebuild every cells route, adapt to what 0.16.0 changes (a cheaper call shape
   would replace the reused argument arrays), rerun all lanes. Branch `deps/myna-0.15.0`
   (`0cc11e36`) is the proven dry run; do not merge it.
2. **Paired SVM measurements**, flag on vs off, then each flag's default (Sam's rule: on only if
   it beats baseline). Commands are in the 19:10 state note below. Expect no win on 0.14.0:
   cells run in host code below a `@TruffleBoundary`, so Myna's `execute*(Object[])` entry and
   its `Borrow` objects allocate per call (Buffa 144–176 B/call vs 0 B).
3. **Release packaging** for `truffledata-{json,yaml,csv}-cells` plus a shared `truffledata-cells`
   runtime (25 → 29 libraries) and per-format docs: branch `cells2-release` @ `1cff1f2c`, an
   UNTESTED WIP commit of the dead worker's files. Finish, run `//tools/release:test`, merge.
4. **YAML Native Image route builds nine cells per session**; `YamlSvm.decode` opens a session per
   call. Share the cells image-wide (as CSV and JSON do) before measuring YAML.
5. AOT call-shape gate for the CSV and YAML flag-on images (Buffa and JSON have one).
6. macOS local lanes; arm64 lanes on the final tree; staged release; then publish as in
   "Repository state". Central stays deferred (#28).
7. Upstream: the lead sent Sam's Myna session the measured allocation breakdown and the
   validator limits; file issues only if 0.16.0 does not cover them.
8. F1 rulings still open for Sam: combined `seam.h` vs per-class headers; unknown host defaults
   to the signed-char class.

## History: resume 16:05–19:10 PST (superseded by "Current state" wherever it disagrees)

This section is current. Everything from "Goal in flight" down is the state at the 16:00 pause
and is superseded wherever it disagrees (worker state, "#57 is held", next steps).

Sam's instructions this session: "resume this and finish it, your time budget is 30-45mins to
wrap up"; Opus workers, Fable only for the most sensitive code; Fable checks the Opus work and
does its own implementation pass; "frontier, SoTA performance" is expected.

### IN PROGRESS since 17:15 PST — full adoption, then publish

Sam ruled at 17:15 PST: "let's finish and then publish" — finish full cells adoption, then
release 0.11.0 (publication is authorized; no further permission round). The gate question
below is therefore answered: finish first.

Running: workflow `wf_aeb0d186-edf` (script and journal under
`~/.claude/projects/-Users-sam-workspace-graalvm-truffledata/f86c3c78-8f8a-4faf-8736-a1f1ee674e61/`).
- Worktrees `.claude/worktrees/cells2-{json,yaml,csv,buffa}`, branches `cells2-<format>`, all
  cut from `e6767595`. Per format: Opus implements (flag, JVM + SVM routes, parity tests incl. a
  manual `native_test`, resource/borrow declarations, contract registration), then a Fable pass
  reviews and fixes critical sections in the same worktree and re-runs JVM + SVM lanes.
  `cells2-buffa` also adds the scalar-call scan test and investigates per-call argument cost.
- F1 gates worker in the F1 worktree (detached at `d1ca534c`): arm64 `native_test`s, then macOS
  local lanes; fixes, if any, on branch `f1-gate-fixes`.
- **Myna 0.15.0** (Sam, 17:16 PST: "myna 0.15.0 is out"): version-only release over 0.14.0 (one
  commit: `VERSION` constant, Myna's toolchain pin → 2026.11.6; binding ABI version still 2),
  but the version is folded into every ABI fingerprint, so all seam fingerprints and vendored
  crate headers change. Opus worker `myna-015` is doing the bump in
  `.claude/worktrees/myna-015`, branch `deps/myna-0.15.0` (source tarball sha256
  `b3a22121…1978483e`). Merge it AFTER the cells branches; then re-run the contract and native
  lanes on the merged tree, since cells branches were built against 0.14.0 fingerprints.
- If this session dies: inspect each `cells2-*` branch (`git log deps/myna-0.14.0..cells2-X`),
  trust only what its tests show, and continue from "Next steps" below.

**HOLD (Sam, 18:55 PST): "hold on final benchmarks, as myna is releasing fixes in 0.16.0".**
No paired SVM measurement until Myna 0.16.0 is pinned and the cells are rebuilt on it. The CSV
`csv_pair` run was interrupted before it measured anything (BuildBuddy
`c6ee03ef-be77-4018-8a0d-d44fd858d70c`, cancelled). The release therefore waits on 0.16.0:
measurement decides each flag's default, and Sam's instruction is finish, then publish.
`myna-015` was told to commit its 0.15.0 refresh as a proven procedure, skip the long
verification runs, and wait to be re-tasked for 0.16.0. The lead sent the Myna session
(`myna-6c`, Sam's) the measured host-code allocation breakdown and the validator limits.

**State at 19:10 PST (lead's own pass, all local on `deps/myna-0.14.0`):**
- Integrated tree `f4669d4d`, RBE linux-x86_64, 11/11 pass (BuildBuddy
  `e6ed721a-9017-4912-af15-69e98dd2bbaf`): `//{csv,json,yaml,protobuf-buffa}/cells/tests:{jvm,jvm_off}`,
  `//protobuf-buffa/cells/tests:call_shape` (now scanning all four formats),
  `//bazel:myna_contract` (four derived groups), `//tests:test`.
- `13b2bf97` CSV: the static backend goes through a build-time-initialized holder
  (`csv/kernel/.../svm/CellsRoute.java`, the JSON route's shape); an image built with the flag
  but without `truffledata-csv-cells` now fails the build. Verification (CSV cells + baseline
  native tests, `csv_aot_symbols`, JVM lanes) running: `<scratchpad>/integ-csv-guard.log`.
  TODO same guard for YAML once its Fable pass reports (YAML still builds the baseline silently
  when the flag is set without `--features=...YamlFeature`). Buffa cannot be silent: its Feature
  registers the baseline factory too.
- `098a66aa` bench: `//benchmarks/comparison:{csv_svm_cells,json_svm_cells,yaml_svm_cells}` flag-on
  images and `:yaml_pair` (new paired YAML harness, `yaml_pair.py`). Paired runs, once unheld:
  `csv_pair` with `--//benchmarks/comparison:csv_svm_candidate=…:csv_svm_cells`,
  `json_typed_pair` with `…json_typed_svm_candidate=…:json_svm_cells`, `yaml_pair` with
  `…yaml_svm_candidate=…:yaml_svm_cells`, `consumer_measure` with
  `…consumer_svm_candidate=…:consumer_svm_cells`. Only the `-native` CSV engines, the JSON
  document-decode engines and the YAML static-backend engines take the cells route; the others
  are A/A controls.
- Buffa Fable pass done (`9595eab1`, merged): fixed a use-after-free on the JVM cells route
  (raw address lent without the FFM downcall's scope check; proven by mutation), SVM
  allocation 192 → 144 B/call (176 B when buffers alternate; the rest is Myna's three `Borrow`
  objects), parity now varies depth limits, new `//benchmarks/comparison:consumer_cells_call_shape`
  (disassembles the flag-on image: `executeInt` is called directly). Open: the same AOT
  call-shape gate for CSV/JSON/YAML flag-on images (needs symbol-bearing flag-on images; do it
  after the 0.16.0 bump, since generated cells will change).
- JSON Fable pass has committed `308dd55c` (not yet reported, not yet re-merged); YAML's is
  still editing.

**State at 19:00 PST:** the lead merged `cells2-{csv,buffa,json,yaml}` at their then-current
heads into `deps/myna-0.14.0` locally (`f4669d4d`; conflicts in `bazel/BUILD.bazel`,
`json/cells/BUILD.bazel`, `yaml/cells/BUILD.bazel` resolved by keeping both sides; CSV joined
the call-shape scan). Fable passes for buffa/json/yaml are still committing in their worktrees:
re-merge each branch when its pass reports. Opus worker `cells-release` (worktree
`.claude/worktrees/cells2-release`, branch `cells2-release`) is wiring release packaging: a
shared `truffledata-cells` runtime artifact plus `truffledata-{protobuf-buffa,json,yaml,csv}-cells`
(25 → 29 libraries) and per-format docs. Integration JVM run over all four routes is queued
(`<scratchpad>/integ-jvm1.log`).

**State at 18:55 PST** (workflow journal has every worker's full report):
- F1 gates: linux arm64 `--config=remote_arm64` 11/11 pass (BuildBuddy
  `c4ea276d-35c8-4c03-ac9f-61be5894987b`): `//{json,csv}/tests:native_test`,
  `//protobuf-buffa/tests:native_test` and the eight `//tools/release:native_consumers` C tests —
  the SVM-side exercise of `LP64_LITTLE_AAPCS64`. `//yaml/tests:native_test` is x86_64-only by
  design (`yaml/tests/BUILD.bazel`, since `5cbd7ab7`), so YAML SVM has no arm64 native test; its
  C consumers did pass on arm64. macOS `--config=local` lanes did NOT run: the Bazel volume was
  full (848 MiB free). The lead removed five orphaned output bases (workspaces gone; ~7 GiB);
  8.6 GiB free at 18:50. The 100 GiB `root/cache/repos` shared repository cache was not touched.
- `cells2-csv` @ `1c9f4f0c`: Opus implementation + Fable pass done, verdict merge; lead reviewed
  the SVM route (equivalent to `SvmIndexer` apart from per-call `NativePointer`×2, a boxed
  length and Myna's `Borrow`) and added `//benchmarks/comparison:csv_svm_cells`. The paired
  measurement is running: `csv_pair` with candidate `csv_svm_cells`, log
  `<scratchpad>/csv_pair.log`.
- `cells2-buffa`, `cells2-json`, `cells2-yaml`: Opus implementations done and green on JVM +
  SVM lanes per their reports; Fable passes running in the same worktrees.
- Findings that shape the rest (from the implementers, confirmed by reading Myna):
  1. `resource`/`borrow` declarations are not expressible for almost every import without a C
     ABI change (Myna's `OwnershipValidator`: a resource create takes only scalars and returns
     the pointer; a call borrow allows no other pointer parameter). Where a borrow is possible
     it would copy heap inputs under 256 B on SVM where the baseline pins. Lead ruling: not
     adopted; recorded as a Myna-side gap, not a truffledata omission. Sam may overrule.
  2. Cells are called from host code below `@TruffleBoundary`, outside partial evaluation, so
     nothing is scalar-replaced: Buffa measured 192 B/call on SVM against 0 B baseline after
     reusing the argument array. This is Myna's `execute*(Object[])` entry plus `Borrow`
     objects (elide-dev/myna#85). Expect "no win" on paired timing; measure anyway.
  3. YAML and CSV build the baseline silently when the flag is set without the Feature / cells
     jar; JSON fails the image build. Lead to make all four fail the build (port JSON's
     build-time-initialized route holder) before merge.
- `myna-015`: stalled idle after one commit; nudged at 18:45.

Then, by the lead: merge the branches that pass; paired SVM measurement per format, flag on vs
off, serial (nothing else running); rule each default per Sam's rule; push; CI green; merge #67;
publish; tag; GitHub release; verify; receipts; post-release README/bench work.

PR #67 CI on `d1ca534c` finished: all pass except `codecov/patch`.

### What landed on `deps/myna-0.14.0` (PR #67)

- **main merged in, including #57.** Sam merged #57 himself at 16:14 PST (`1158dc93`). 0.11.0
  ships the sticky closed-enum map-entry rejection: stable round trip, but an out-of-set value
  followed by a valid one orphans the entry where protobuf-java generated code keeps it
  (evidence in the #57 thread; option (b) there — keep the entry, drop invalid occurrences — is
  open follow-up). Recorded in `docs/RELEASE_0_11_0.md` and `AGENTS.md`.
- **Buffa cells (`cells-a`, merged):** both cells backends hold the concrete `decodeSlab_Cell` /
  `decodeSlabSmall_Cell` and call `executeInt`. Flag `truffledata.buffa.cells` stays default off.
- **JSON cells (`cells-b`, merged): bindings only.** `//json/cells:bindings` (JVM, FFM-backed)
  builds from a seam derived from `json.seam`. No flag, no route, no test calls it.
- **YAML cells (`cells-c`, merged): bindings only.** `//yaml/cells:bindings` (SVM + JVM) builds.
  No flag, no route. `bazel/myna_contract.py` now takes several `--derived` groups.
- **CSV cells: nothing.** The never-compiled WIP (`f8fae309`) was removed (`c3ee0142`); it failed
  analysis on a visibility error for `//csv/native:csv.seam`.
- **Lead pass (`ade9b453` + follow-up):** JSON derived seam registered with
  `//bazel:myna_contract --derived`; BUILD comments no longer describe JSON/YAML flags that do
  not exist; `json/cells` visibility narrowed to `//bazel` and `//json`; release notes rewritten
  to say only Buffa has a cells route.

### Evidence

- Merged branch, RBE linux-x86_64, 7/7 pass (BuildBuddy `f2659e70-212a-4699-ae5f-6d7ee36d812f`):
  `//bazel:myna_contract`, `//protobuf-buffa/cells/tests:{jvm,jvm_off}`, `//json/tests:jvm`,
  `//yaml/tests:jvm` (both cached), `//tests:test`, `//protobuf-runtime:test` (the last two
  cover #57 on this branch); `//json/cells:bindings` and `//yaml/cells:bindings` build.
- F1, linux arm64 (`--config=remote_arm64`), 6/6 pass (BuildBuddy
  `0c975af1-855c-40e2-8f8d-c4de1b9f1da4`): `//bazel:myna_contract`, `//csv/tests:{jvm,
  jvm_native_abi}`, `//json/tests:jvm`, `//yaml/tests:jvm`, `//tests:test`. This is the first
  real load of natives under the `LP64_LITTLE_AAPCS64` class (JVM side).
- SVM linux amd64 on pushed head `d1ca534c`, 8/8 pass (BuildBuddy
  `64a1b297-e7b5-40a1-bc54-1255d267e78d`, finished 16:40 PST):
  `//protobuf-buffa/cells/tests:native_test` (first execution of the SVM `executeInt` route),
  `//protobuf-buffa/tests:native_test`, `//{json,yaml,csv}/tests:native_test` (cached),
  `//benchmarks/comparison:{json,csv,consumer}_aot_symbols` (budgets hold).
- PR #67 CI on `d1ca534c` at 16:41 PST: 12 pass, 2 pending (Benchmarks / simulation,
  Sanitizers and fuzzing), `codecov/patch` fails (it also failed on #57, which merged; check
  whether the uncovered lines are SVM-only before merging). This ledger commit is local only:
  pushing it would restart CI, so push it together with the next real change.
- Fable reviews of all three cells diffs: no blocking findings. They were read-only.

### NOT done — the release gate is NOT met

Sam's gate is full cells adoption on all four seams. What exists is one Buffa route plus
JSON/YAML bindings. Do not publish 0.11.0 on the strength of this section; ask Sam whether to
release with cells as-is (notes already say so honestly) or finish adoption first.

- Routes + flags + flag-on parity tests: JSON (route `decode_adopted`), YAML, CSV (JVM and SVM).
- SVM cells variants and hosted Features for JSON and CSV.
- Resource declarations for create/release pairs, call-scoped borrows, `NativeBytes`/`HeapBytes`.
- A test that fails when a scalar cell call goes through `execute()` (brief item 2).
- **No paired SVM measurement for any format**, so no per-format default decision. All flags off.
- Not run: arm64 `native_test`s (the SVM-side exercise of the AAPCS64 class); macOS local
  lanes; staged release.
- Performance lead for the measurement tranche (hypothesis, unmeasured): both Buffa cells
  backends build an `Object[10]` with boxed `long`/`int` and four `NativePointer.of` per call,
  from `@TruffleBoundary` entries. Myna's docs say native cells scalar-replace this under
  partial evaluation; behind a boundary nothing guarantees it. Check allocation with the flag on
  before any default-on ruling.
- F1 rulings still open for Sam: combined `seam.h` vs per-class headers; unknown host defaults
  to the signed-char class.

### Next steps (in order)

1. Watch PR #67 CI on the pushed head; fix what it finds (first Linux build of `//json/cells`
   and `//yaml/cells` under `bazel test //...`; coverage contract; `PolicyTest` budgets).
2. Get Sam's ruling on the gate (above). If "finish adoption": JSON route first, then YAML, CSV;
   one format at a time, each with parity test, then paired SVM measurement.
3. Run the not-run gates above (arm64 `native_test`s first).
4. Merge #67, publish from the merged commit (commands under "Repository state"), tag, GitHub
   release, verify, receipts in `benchmarks/results/release-0.11.0/`, update `AGENTS.md`.
5. **After the release (Sam, 16:21 PST):** re-run the benchmarks, cut much prettier benchmark
   cards for the README, and do a README cleanup pass.

Session facts: Bazel lock wrapper
`/private/tmp/claude-501/-Users-sam-workspace-graalvm-truffledata/f86c3c78-8f8a-4faf-8736-a1f1ee674e61/scratchpad/bzl.sh`
(recreate from `docs/superpowers/plans/briefs/2026-11-08-beat-stock-svm/bzl.sh` if gone). Worker
worktrees (`cells-a`, `cells-b`, `cells-c`, F1) are merged and clean; safe to remove.

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
