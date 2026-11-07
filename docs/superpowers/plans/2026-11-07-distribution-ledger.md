# Distribution follow-up ledger

The user authorized every open TruffleData issue and the unfinished JSON,
protobuf and YAML plans, with an intermediate release to unblock TruffleCEL.
The user selected `MIT OR Apache-2.0` for this follow-up.

## Intermediate release: complete

- PR #34 merged to main `223edf71f66f33c37ea2eab4caf677a698074c72`.
- Main CI [42424551778](https://github.com/elide-dev/truffledata/actions/runs/42424551778) passed all four jobs.
- `0.9.0` published at maven.elide.dev; all payloads were checked against public downloads, then metadata updated.
- Publication suite: 76/76, invocation `eb9723ad-c733-4135-8b50-d9ea66e05f82`.
- Negative probes rejected the expected call paths: `01d42d26-dfdc-4966-8582-c336fa15f138` and `c177c362-fae8-44f6-957a-2774874f58e7`.
- Linux AArch64 and macOS consumer lanes passed; archive, dependency and fingerprint checks passed for all three native modules on all three platforms.
- [GitHub v0.9.0](https://github.com/elide-dev/truffledata/releases/tag/v0.9.0) has a signed OpenPGP tag, 22 native payloads, their SHA-256 sidecars and a signed SHA256SUMS file. All 46 GitHub asset digests match local bytes.
- Issues #2 and #27 closed by the merged release PR. Nine issues remain open.

## Implemented, still awaiting full follow-up qualification

- #32: eager immutable name-to-ordinal index; duplicate names resolve to -1. Collision and wide-schema checks added.
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
