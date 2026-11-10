# 0.11.0 publication evidence

Released from signed tag `v0.11.0`, commit `ae32c97df70795bf5e3f9ee3909a3724fd23ce1f`.
[GitHub release](https://github.com/elide-dev/truffledata/releases/tag/v0.11.0) and
[Elide Maven BOM](https://maven.elide.dev/dev/elide/truffledata-bom/0.11.0/truffledata-bom-0.11.0.pom)
are public. Maven Central is not published (below).

- PRs #68 (Myna 0.16.0, cells routes on `invoke`, 29-library packaging), #69 and #70 (release
  tool fixes) each passed all 16 PR checks before their squash merge. Main `CI` passed on
  `ae32c97d` (`main-ci.json`).
- `tools/release/publish.sh 0.11.0 --publish` ran from the clean tag commit: the full suite and
  every native lane, five-platform native builds, the staged Gradle consumer, the native
  member/ABI-fingerprint/dependency/export check (4 modules, 5 platforms), five CSV SDK bundles,
  204 signed payloads, Central validation, then the Elide Maven upload with a byte comparison
  of every payload (`staging/publish-excerpt.log`).
- Public check afterwards (`public/maven.json`, `public/verify_public.py`): all 1,374 public
  files of the 30 modules (204 payloads, 204 signatures, 816 checksums, 150 metadata files)
  were downloaded anonymously and equal the staged bytes. The Gradle consumer was then run
  against a repository made only of those downloads and passed
  (`public/gradle-consumer-public-mirror.log`). This is the staged-consumer tool pointed at
  public bytes, not the separate isolated Maven consumer that 0.10.0 used.
- All 116 GitHub asset digests equal the local bytes (`public/github.json`). Anonymous downloads
  of the five CSV SDK bundles match the signed `SHA256SUMS`, whose signature verifies
  (`signatures/`).
- Charts attached to the release are the preserved `svm-tranche-20261108` evidence. No fresh
  benchmark was run for the tag. The cells flags are default off on the paired measurements in
  `../cells-flag-9570099/`; the JSON cells route has no timing. Re-benchmarking follows the release.

## Two earlier publish attempts stopped before publishing anything

Runs from `be6f1094` (#68) and `fec21152` (#69) passed every gate and native build, then failed
in release tools that had not been run since the Myna 0.14.0 and GraalVM `.28` changes:
the Gradle consumer pointed at the GraalVM `.27` repository; the SDK bundler expected one header
fingerprint instead of one per ABI class; the native check missed a fingerprint that sits
directly against another Rust string constant in the macOS Buffa archive. The artifacts were
correct in each case. #69 and #70 fix the tools.

## Open finding: YAML fuzzing on main

The `Untrusted input` run on `fec21152` failed: `//yaml/native:session_fuzz` reached an
unreachable point in rapidyaml v0.16.0 (`parse_engine.def.hpp:8310`, UBSan) on a 22-byte input.
The parser and its pin are the same as in 0.10.0, and the PR runs of the same tree passed, so
this is a new random-fuzzing find rather than a change in this release; it is not fixed in
0.11.0. The reproducer is in the CI log of run 42769247320. It needs a bounded parse error like
the existing `rapidyaml-annotation-bounds.patch`, and a patch release.

## Central deferred

The signed bundle passed Central validation; publication then failed on the organization's
monthly release-size quota (resets 2026-12-01), as for 0.10.0 (`central/failed.json`,
`central/deployment.json` with the deployment ID and signed ZIP SHA-256). Issue #28 stays open.
The staged repository, signed ZIP, natives and bundles remain in the release checkout under
`build/release-repo.AXZ6kA*`. Do not re-upload or overwrite the immutable Elide Maven version.
