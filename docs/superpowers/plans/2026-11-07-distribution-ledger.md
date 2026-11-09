# Distribution follow-up ledger

The user authorized every open TruffleData issue and the unfinished JSON,
protobuf and YAML plans, with an intermediate release to unblock TruffleCEL.
The user selected `MIT OR Apache-2.0` for this follow-up.

## Current release decision — 2026-11-08

Sam authorized releasing the completed work as **0.10.0** to unblock TruffleCEL
and Elide **1.6.3**, then continuing hill-climbing separately. This supersedes the
historical pause/stay-local checkpoints below for the release lifecycle: branch
push, PR/CI, merge, signed tag, Maven/Central publication, GitHub release and
public verification. It does not authorize releasing the downstream projects.

Remaining protobuf/JSON/YAML performance goals and standalone Buffa Cargo work
are deferred, not declared achieved. The measured gains, regressions, comparisons
and possible next investigations are in `docs/RELEASE_0_10_0.md`, the common
`benchmarks/results/svm-tranche-20261108/README.md` report and the format ledgers.
The five refreshed SVM charts use the preserved 244 paired cells, with exact
source/image provenance and an explicit pre-final-release-fix measurement scope.

Sam requested updated charts and no extensive re-verification. Reuse the recent
455-test coverage, both Linux ISA native acceptance runs and prior native safety
qualification. Run focused checks for the external serialized-slab verifier fix,
normal candidate/main CI, five-platform artifact staging and actual staged/public
integrity and consumer checks. Do not rerun broad benchmarks for packaging.

Release preflight: existing `r2:elide-maven` is accessible; the selected signing
key passes a detached-sign/verify check; authenticated Central preflight returns
404 for a missing deployment (not an authentication failure). Credential values
are not recorded. Python publication calls use the system CA bundle at
`/etc/ssl/cert.pem`. No 0.10.0 artifact has been uploaded at this checkpoint.
Native release packaging is in progress; the verifier fix and final CI/publication
remain pending. Record concrete completion below rather than inferring it from
these preflight checks.

## Intermediate release: complete

- PR #34 merged to main `223edf71f66f33c37ea2eab4caf677a698074c72`.
- Main CI [42424551778](https://github.com/elide-dev/truffledata/actions/runs/42424551778) passed all four jobs.
- `0.9.0` published at maven.elide.dev; all payloads were checked against public downloads, then metadata updated.
- Publication suite: 76/76, invocation `eb9723ad-c733-4135-8b50-d9ea66e05f82`.
- Negative probes rejected the expected call paths: `01d42d26-dfdc-4966-8582-c336fa15f138` and `c177c362-fae8-44f6-957a-2774874f58e7`.
- Linux AArch64 and macOS consumer lanes passed; archive, dependency and fingerprint checks passed for all three native modules on all three platforms.
- [GitHub v0.9.0](https://github.com/elide-dev/truffledata/releases/tag/v0.9.0) has a signed OpenPGP tag, 22 native payloads, their SHA-256 sidecars and a signed SHA256SUMS file. All 46 GitHub asset digests match local bytes.
- Issues #2 and #27 closed by the merged release PR. Ten issues remain open after
  the subsequent raw unknown-field query request (#35).

## Implemented, still awaiting full follow-up qualification

- #32: eager immutable name-to-ordinal index; duplicate names resolve to -1. Collision and wide-schema checks added.
- #35: `hasUnknownFields()` and `unknownFieldByteCount()` expose raw retained
  node-local bytes on immutable, generated, owning-slab and borrowed-slab rows.
  Empty groups, wrong wire types, closed enums and rejected map entries retain
  their raw-byte semantics. Native compiled reads pass the unchanged allocation
  budget; borrowed queries check the session epoch before reading.
- #30/#31: caller-owned FileChannel mapping and byte-array windows/owned input APIs. Twenty focused CSV tests passed directly; integrated CSV JVM tests passed with the protobuf check.
- #26: CSV runtime-provider installation, backend, channel and ownership documentation updated.
- #24: CSV release archive links LLVM IR and internalizes everything except five seam entry points. macOS machine-code and bitcode symbol inspection reports exactly those functions. Shared-library export lists added. GNU and macOS static/shared/ThinLTO consumers pass; GNU gate `59c64a55-460e-4dfc-b39d-64ecef3c0387`. Direct inspection confirms the exact five-symbol public set on GNU/macOS machine/shared libraries and the macOS bitcode twin. Full staged matrix qualification remains pending.
- #29: musl toolchain overlays reuse pinned LLVM/sysroot files. x86-64 musl index, ABI and isolated release archive tests pass: `7332a18e-bc0c-4413-919c-4c785826d4e7`. First attempt exposed dynamic dependency selection under static linking; fixed with `--dynamic_mode=off`. AArch64 musl tests also pass: `64178680-c2cc-4bfc-8451-027fc7b48a23`. Myna0.13 does not recognize musl target names; the CSV seam contains only libc-independent LP64 scalars/pointers, so its GNU-resolved binding layout is shared with the tested musl binaries. Native CI jobs and triple aliases added, not yet run in GitHub CI.
- #25/#28: deterministic SDK tarballs, C header, ABI/link/runtime/compiler/source manifests and checksums implemented; synthetic integrity/mismatch tests pass. Prototype CSV bundles exercised the three 0.9.0 native platforms locally; not published as SDK bundles. Integration with five-platform staging and final notices pending qualification.
- #28: dual-license POMs, license resources, Gradle module metadata, real Javadoc generation, signing and staged Central Publisher API workflow implemented. Unit tests reject tampered/unsigned inputs and unvalidated publication. Core/CSV-native/BOM publication targets build, including real Javadoc. An actual Gradle9.5.1 consumer (metadata-only local repository) resolves BOM constraints, compiles/runs core, and verifies exact binary/source/Javadoc bytes. This caught and fixed the requirement that formatVersion be the first JSON member. Actual Central deployment remains pending.

## Credentials

Artifact signing preflight passed. The original Sonatype environment credentials
returned HTTP 401. The user supplied a local Maven publishing settings file; its
credentials passed the authenticated missing-deployment preflight (HTTP 404,
instead of 401). The publisher supports `CENTRAL_CREDENTIALS_FILE` for either a
Maven server/settings XML file or a private JSON username/token pair. No credential
values are recorded here. Namespace permission will be checked by the real staged
deployment.

Next release is 0.10.0 after the follow-up work and all required gates are green on
main. JSON, protobuf and YAML each retain their own plan and evidence ledgers;
this ledger does not mark those incomplete cutovers or performance goals complete.

Follow-up qualification: static musl C consumers now prove both ordinary linking
and ThinLTO consumption of the isolated CSV archive on x86-64 (`ea2430cb-e1d1-4a83-8c9b-008b088973f7`)
and AArch64 (`9a7b9a14-8129-4906-8746-337bb92d2f8e`), two passing tests per architecture.
The first shell-test analysis exposed Bazel 9's default execution-platform libc
constraint; explicit architecture-constrained test toolchains now allow the static
musl executables to run on the GNU Linux executors. CI and publication run these
witnesses. YAML's three Java artifacts and three-platform native attachments are
now included in publication staging, with ABI fingerprint and C consumer gates;
qualification of those additions follows the compact YAML ABI cutover.

Signing publication preflight: the selected public OpenPGP fingerprint is retrievable
and matches on both `keys.openpgp.org` and `keyserver.ubuntu.com`; the signing
identity itself is unchanged. These are supported distribution servers in
[Central's signing requirements](https://central.sonatype.org/publish/requirements/gpg/).
This checks public-key availability, not deployment permission or publication.


## User-requested pause checkpoint

The user asked to pause at a stopping point. The in-flight qualification finished;
no new validation round, merge or publication was started after that request.
The already-built YAML profiler executable was retrieved from cache only for
symbolization. Release authorization remains: finish the follow-up and all open
TruffleData issues, require green main, then publish 0.10.0. The intermediate
0.9.0 is already released. Do not ask again for the selected dual license or the
Central credential-file path.

Work is on `feat/followup-v5` in `/Users/sam/workspace/graalvm/truffledata-followup`.
[Draft PR #36](https://github.com/elide-dev/truffledata/pull/36) is open; its last
pushed commit is `a94ec5b`. Subsequent local correctness and evidence commits have
not been pushed, to avoid starting another CI round during the pause. Main and
release tags are unchanged. The isolated qualification checkout remains at
`5002235` in `/Users/sam/workspace/graalvm/truffledata-qualification`.

### Completed qualification and pending fixes

- Full invocation [4065dd6f](https://app.buildbuddy.io/invocation/4065dd6f-2b23-40f9-bb50-bfec5e1a448f),
  snapshot `5002235`: 86/93 test targets passed, four failed to build and three
  failed remotely. `/tmp/td-010-full-5002235.log` contains the full command output.
  Both 10-shard G1 runtimes completed, as did legacy measurements, native API,
  compiled reader, native-free consumer and allocation suites explicitly listed
  in that invocation. This was not a complete release qualification.
- The four strict Java dependency failures are fixed in `b79a787` and `c11fa85`.
  The YAML measurement target's stale shared-library label is fixed in `c11fa85`;
  the three-fork measurement itself did not run. The stale YAML string-ownership
  assertion is fixed in `a94ec5b`, with exact text/raw-span/Myna parity and ASan/UBSan.
  Those fixes require integrated recapture.
- Protobuf E1: SVM selected-small/selected-holdout/lazy-holdout pass at
  0.66443/0.37369/0.49774. JVM selected-holdout/lazy-holdout pass at
  0.48924/0.34602; selected-small misses 0.85 at 0.86384. `844aafe` adds checked
  generated eager selection and count-only omitted child lists; 29,440 local
  exact byte/status pairs pass, but formal JVM resident recapture is pending.
- Protobuf B4 remains open: generated/slab full interpreter ratios are
  4.358/6.729 versus the required 2.5 ceiling; historical peak comparisons also
  need attention. The historical SDK is .24 and candidate SDK is .27, so these
  are combined implementation/runtime comparisons. `63fd6d4` separately fixes
  cross-root SlabRow accessor caching; local regression tests pass, integrated
  recapture is pending. No speculative B4 unchecked-read hook was implemented.
- JSON `d03ffe1` fixes validate-only duplicate acceptance after a shape-template
  replacement aliases its own stored key bytes. Exact regression, expanded
  million-sequence corpus and sanitizers pass locally. `586db22` removes the
  rejected text-fusion candidate from production and keeps it test-only.
  `b1a90b4` adds the duplicate-check price probe. Native recapture and updated
  compatibility/v3 unclemax measurements are pending. Previous required Task2
  misses remain big/huge retained and small/huge validation; duplicate-price
  qualification is pending. The fixed-format 1.5x logical-memory criterion is
  explicitly not met, not silently waived.
- YAML ABI5 compiled reader/export/allocation gates pass. Native ratios
  config/block/flow/stream/text are 0.561/0.693/0.692/0.507/0.771; only stream
  clears its required floor. Existing profiles and symbol-level findings are
  archived; four required throughput floors remain open. Profiler ELF was
  retrieved in [2b9db0e6](https://app.buildbuddy.io/invocation/2b9db0e6-e0ef-4f44-80c7-e291ed03a218)
  with cache hits only. No new YAML optimization was started after the pause.

### GitHub CI findings to resume

Run [42443797685](https://github.com/elide-dev/truffledata/actions/runs/42443797685)
checks `a94ec5b`, not the later local checkpoints. Linux AArch64 GNU/musl,
x86-64 musl and database driver jobs passed. Pending known failures:

- Access-method coverage: `ReadBooleanNode$InlineReader::accessedInline` is
  uncovered. Full contract reporting may reveal further gaps after it is fixed.
  Log: `/tmp/td-010-core-ci-failure.log`.
- Legacy `//benchmarks/comparison:native_smoke` fails compact scalar allocation,
  416B versus 128B. This manual target was omitted from the broad invocation's
  explicit list; add it to subsequent qualification. The distinct
  `consumer_native_smoke` passed. Log: `/tmp/td-010-buffa-ci-failure.log`.
- macOS CSV CI fails Bazel absolute-header validation: actual SDK headers are
  under `/Applications/Xcode_16.4.app/...`, while the toolchain's builtin include
  list names unversioned Xcode and CommandLineTools paths. The runner is arm64;
  this is SDK discovery/include declaration, not architecture selection.
  No workaround has been implemented. Log: `/tmp/td-010-macos-ci-failure.log`.

### Resume order and operational notes

Review the agents' checked-in evidence/handoffs, finish the mandatory fixes, then
update the isolated qualification checkout to a complete committed snapshot.
Only one local Bazel invocation may run across all worktrees. Use
`JAVA_HOME=/Library/Java/JavaVirtualMachines/gvm.jdk25/Contents/Home` consistently.
The qualification output base is
`/Users/sam/Library/Caches/komodo-bazel-volume/root/f3ea07f1e7d36952c2afa08f82935352`;
the shared cache volume had about 3.4GiB free at pause. Preserve the historical
protobuf baseline under `build/protobuf-baseline-fecb39f` (including SDK symlinks)
and the JSON `ce7399f` baseline. Do not delete shared caches or release evidence.

After focused fixes, complete method coverage, all explicit native/negative-JIT
and sanitizer gates, five-platform staging, published Java/native/Gradle
consumers, signatures/metadata, and full CI. Then mark the PR ready, merge,
require green main, and publish 0.10.0 with
`CENTRAL_CREDENTIALS_FILE="$HOME/.m2/settings-publishing.xml"`. Verify public
Maven/GitHub artifacts and close distribution issues only after publication.
The ten requested GitHub issues remain open; implementation does not establish
successful release. No new upstream Myna/YAML issues were filed.

Final evidence checkpoints before pausing: JSON/tape `b1a90b4`, YAML `65155d1`,
protobuf `5c0d10a` (correctness fix `63fd6d4`). All three workers have stopped.
Their owned source and evidence changes are committed; only this pause record
is added afterward. No B4 performance prototype or new YAML optimization is
left active. JSON candidates under `/tmp` are explicitly unadopted.
Resume 2026-11-08: coverage gap fixed (ReadBooleanNode/Double/Length InlineReader::accessedInline): JaCoCo probe-after-call artifact plus a parity test that exhausted the 3-entry inline cache on mismatched ordinals; test reordered, all four readers now FNDA=1 locally (bazel coverage //tests:test --config=codecov).
Resume 2026-11-08: macOS CSV CI header validation fixed in bazel/patches/elide-toolchain-cc.patch (SDK path canonicalized to the declared unversioned Xcode.app or CommandLineTools directory); local --config=local csv/native tests pass; the versioned-runner branch was unit-tested with a fake /Applications tree.
Resume 2026-11-08: plan docs/superpowers/plans/2026-11-08-release-resume.md records the order: CI blockers (coverage, macOS, native_smoke allocation, CodSpeed) → performance gates → #37 crates → qualification/release. Housekeeping: five orphaned agent-worktree output bases (about 9 GB) removed from the shared volume (13 GiB free); shared caches and baselines untouched.
Resume 2026-11-08: full contract run after the inline-reader fix listed ~60 further uncovered access methods (CSV overloads, Buffa borrowed/materialized/session surface, TypedRow text-path accessors, ViewOwner cached checks, GeneratedMessage unknown queries, SlabMessage projected/Usage, HeapValueTape, TapeReader.textWord, YamlMessage.confined); tests written, SlabSession::outside exempted as a defensive extent guard.
Resume 2026-11-08: CodSpeed check on PR #36 is a performance regression, not a job failure: json-generic-java-export -12% and json-generic-interop-read -10% (simulation image, head a94ec5b vs main 223edf7); YAML simulation cases are new.
Resume 2026-11-08: coverage contract green locally (bazelisk coverage of the nine JVM targets + reports.py + coverage_contract.py); commit 3573225.
Resume 2026-11-08: native_smoke compact-scalar allocation: SVM slab decode now writes into a pinned heap array owned by the SlabMessage (commit 8041c81); measured 416 -> 312 Java B/message (owned Java path: 64 B; typed/generated: 48 B). The remaining 312 B is the durable slab design itself (slab array + SlabMessage); the 128 B gate dates from the retired compact-scalar native view. Still open: print the slab extent in the smoke, record the byte breakdown in the protobuf ledger, and re-base the budget in benchmarks/comparison/Comparison.java from that breakdown (not yet done; the target still fails).

## Pause checkpoint 2026-11-08 09:06 PST (branch feat/followup-v5, local commits unpushed)

Done this session: inline-reader coverage fix (efdc900); macOS SDK path fix (9c53f2e); full access-method coverage contract green locally (3573225); SVM slab decode allocation cut (this commit). Shared volume cleaned to 13 GiB free. Plan: docs/superpowers/plans/2026-11-08-release-resume.md.
Not started: CodSpeed JSON regressions (json-generic-java-export -12%, json-generic-interop-read -10%; needs a JVM runner target for single cases or a Linux image run); performance gates (protobuf B4/E1, YAML floors, JSON retained); #37 Cargo crates (design notes: the simdutf crate exports no headers or link metadata, so a JSON/CSV crate must vendor simdutf.h at the crate's version and rely on the simdutf crate for symbols, or bundle it behind a feature); qualification and release.
Nothing has been pushed; PR #36's last pushed commit remains a94ec5b. No Bazel invocation is running.
Resume 2026-11-08: security review of 8041c81 flagged memory amplification from the plan-wide capacity high-water mark; fixed in 2424f09 by trimming spare capacity larger than max(256, extent). native_smoke unchanged at 312 B/message (gate 128 still open), //protobuf-buffa/tests:jvm passes.
Resume 2026-11-08 (CodSpeed JSON regressions, analysis only): the simulation's JSON cases decode with JsonSvm.decode (SVM-only), so no JVM reproduction exists; json_measure on RBE is the next measurement. Hypothesis to test first: 7e0167b made member directories opt-in (sink/stream omit them, direct decode requests them, retaining a directoryless result rebuilds them by a checked rescan) and its own ledger notes retained/snapshot read graphs over budget; json-generic-interop-read (-10%) reads ten members through cached interop on a directly decoded message and json-generic-java-export (-12%) exports it, both touched by that change and by 85e9b2e (export key words). Verify by building the simulation image at 7e0167b^ and 7e0167b and comparing the two cases under CodSpeed's runner, — locally, not by pushing (no CI runs until the branch is ready).

Resume 2026-11-08 (Codex local landing and requested pause): integrated slab `6e65ff7`, B4 `c6474e4` and JSON `6e1cfc1` into `feat/followup-v5`. Slab native smoke passes at measured 168 B generic / 136 B typed/generated, down from 312 B generic; measured breakdown retained. B4 production prototype rejected after complete paired JVM/SVM evidence (+20.2% JVM generated interpreter and +40.7% JVM slab resident); tests/harness/evidence retained, checked production accessors restored. JSON final simulation export/read instructions beat main by 15.67%/8.36%; native/JVM 9/9, 24 graph and nine allocation gates pass. Combined nine-suite JVM coverage, 201-file LCOV validation, access-method contract and 14 reporting-tool tests pass (`018a698d`). See release-resume pause checkpoint and `benchmarks/results/followup-integration-20261108/`. All workers/builds stopped; nothing pushed or published. Remaining performance, verification-time and release gates stay open.

Resume 2026-11-08 (explicit SVM priority): Sam instructs SVM performance to determine optimization admission, with JVM as correctness/test target. Root AGENTS.md records this and supersedes older JVM timing gates. The prior B4 rejection is superseded: exact measured Phase1 production code/API-specific tests are reinstated, retaining the documented JVM tradeoff. Paired SVM full/sparse generated interpretation improves17.7%/12.4%, with overlapping peak ranges; the2.5x SVM target remains open. Seven integrated validation targets passd19a0431 (both native smokes, correctness/allocation); nine coverage suites and the method contract passf2c1f687 with no added exemptions. JSON/CSV audits found no additional proven SVM winner rejected solely for JVM timing. Next-tranche proposals are in docs/superpowers/plans/2026-11-08-svm-next-tranche.md; no new tranche implementation or remote publication started.


Resume 2026-11-08 — 0.10.0 release candidate: the five SVM chart sets are integrated
in `49c3cc1`, with gains, comparisons, tradeoffs and deferred scope in the release
notes. The external slab verifier fix (`d37d646`, fixture `afa0d3b`) passes independent
source review, 41 runtime tests (`c61145d9`) and 13 allocation tests (`9a6b979f`);
original fixture failures and final evidence are preserved. Normal candidate CI
will supply its SVM allocation/native qualification. All 22 absent-version checks
and historical Maven metadata imports pass. Linux x86-64 native artifacts were
built for 0.10.0 (`808b7550`), copied and hashed before changing configuration;
remaining platform packaging is running. No release artifact is published yet.
The candidate is ready for the authorized PR push and CI, without repeating the
performance matrix.
