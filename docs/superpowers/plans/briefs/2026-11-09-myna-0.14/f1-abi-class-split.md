# F1 — Myna 0.14.0 ABI-class split (aarch64 Linux bindings)

Owner: Fable worker, own git worktree. Lead: the session that wrote this brief.
Base: branch `deps/myna-0.14.0` at `babce1a` or later (GVM `25.5.5-elide.28`, toolchain
`2026.11.6`, Myna pinned to the 0.14.0 release commit `a284fd6`).

## Problem (measured)

Myna 0.14.0 (elide-dev/myna#71, release notes `docs/releases/0.14.0.md` in the Myna source)
makes C `char` signedness part of the target ABI class. x86-64 Linux and Apple arm64 still
share one class. `aarch64-unknown-linux-gnu` (AAPCS64, unsigned `char`) is its own class, so it
gets its own fingerprint even for seams with no `char` carrier. The upstream note: "a project
publishing for it alongside other targets needs a separate set of bindings."

Today every seam's genrule runs `generator validate <seam> --targets
x86_64-unknown-linux-gnu,aarch64-unknown-linux-gnu,aarch64-apple-darwin` and requires one
shared fingerprint, so `//yaml/native:bindings` fails (`targets have different ABI
fingerprints: 4f45fabe… for aarch64-unknown-linux-gnu; f9db8ac8… for x86_64…, aarch64-apple-darwin`).
The same applies to `json`, `csv` and `protobuf-buffa` seams and the Buffa cells' derived seams
(`//protobuf-buffa/cells:seam_svm`, `:seam_jvm`).

## Goal

All four formats (Buffa, JSON, YAML, CSV kernel) build, load and publish on all three native
platforms with Myna 0.14.0. Each native library carries and checks the fingerprint of its own
ABI class, and every Java (FFM and static SVM) consumer verifies a library against the
fingerprint of the ABI class it actually runs on. A mismatch must still fail closed: no
fallback that loads a library with a different fingerprint, and no weakening of any existing
check.

## Constraints

- Safety first: fingerprint checks are an ABI-integrity gate. A wrong-class library must be
  rejected with the same clarity as today. Keep `wrongAbiDoesNotFallBack`-style tests passing,
  and add a test proving an aarch64-Linux-class fingerprint is rejected on the other class and
  vice versa.
- SVM is the production target (AGENTS.md). A static SVM image is built for one target, so it
  should fold to exactly one class's constants at image build time. No runtime branch on the
  SVM decode paths; prove it with the existing `*_aot_symbols` budget targets (no budget key
  may grow).
- JVM/FFM picks the class from the host (`os.arch` / `os.name`) once, in a `static final`.
- Published artifacts: the bindings jars must carry what the aarch64-Linux natives need.
  Decide between (a) one bindings jar with both classes' generated constants/classes and a
  class selector, or (b) per-class classifier artifacts. Prefer (a) unless the generated code
  differs in more than constants, and record why. Keep `tools/release` (`maven_repo.py`,
  `publish.sh`, `check_natives.py`, `test_release.py`) and `bazel/myna_contract.py`
  consistent with the choice.
- The Cargo crates (`json/native/crate`, `csv/native/crate`) vendor the generated `seam.h`.
  Make sure an aarch64-Linux Cargo build gets that class's header/fingerprint, and refresh the
  vendor copies with each crate's `vendor.sh`.
- Do not change the seams' wire/ABI semantics, the `abi=` numbers, slab formats or goldens.
- Bazel: serialize every local invocation through the lock wrapper at
  `docs/superpowers/plans/briefs/2026-11-08-beat-stock-svm/bzl.sh` (copy it to a scratch
  path, `chmod +x`, and point its `LOCK=` at a path shared with the lead:
  `/private/tmp/claude-501/-Users-sam-workspace-graalvm-truffledata/3fb12e8e-77a4-4ef6-8f8a-02a89b912978/scratchpad/bazel.lock`).
  Linux targets build on RBE (`--config=remote`, `--config=remote_arm64`); macOS locally.
- Do not push, open PRs, or touch other worktrees. Commit in your worktree.

## Gates (all must pass; record invocation ids)

- `bazel build` of every bindings target and `//protobuf-buffa/cells/...`.
- `//bazel:myna_contract`, `//tools/release:test`, the native tests and JVM lanes for every
  format (`//protobuf-buffa/tests:{jvm,jvm_non_strict,jvm_no_native_access}`,
  `//json/tests:jvm`, `//yaml/tests:jvm`, `//csv/tests:jvm`, `//tests:test`), and the
  per-target native jobs on Linux arm64 (`--config=remote_arm64`) for each format's native
  test/acceptance targets.
- SVM: each format's `native_test` / smoke target on linux amd64, and the `*_aot_symbols`
  budget targets unchanged.
- A focused test per format: the aarch64-Linux-class fingerprint is accepted on that class and
  rejected on the other.

## Deliverables

Commits in your worktree branch; a ledger entry appended to
`docs/superpowers/plans/2026-11-09-myna-0.14-ledger.md` (create it) with the design choice,
what changed, invocation ids, gate results, and anything left open. Report in under 400
words: the design, the gates, and any risk the lead must rule on.
