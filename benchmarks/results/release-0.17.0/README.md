# 0.17.0 publication evidence

Released from signed tag `v0.17.0`, commit `4d4c47488ecf2b6a675de57b3811528c8a92fc3b` (#129 on main,
green `CI` and `Untrusted input`).
[GitHub release](https://github.com/elide-dev/truffledata/releases/tag/v0.17.0) and
[Elide Maven BOM](https://maven.elide.dev/dev/elide/truffledata-bom/0.17.0/truffledata-bom-0.17.0.pom)
are public. Maven Central was not attempted (#28).

A bindings release: Myna 0.20.0 → 0.22.0, with new ABI fingerprints for every seam and the JSON and
CSV Cargo crates re-vendored. GraalVM stays at 25.5.5-elide.33 and the Elide toolchain at 2026.11.7.
Before merge: `bazel test //...` (91/91), the JSON/CSV Cargo link tests and `test_check_natives.py`
passed locally, and all 17 PR checks passed. The PR also refreshed `MODULE.bazel.lock`: the 0.17.0
module version changes the GraalVM extension's usages digest, and the benchmark simulation job
rejects a lockfile rewritten mid-build.

- **Gates.** As for 0.16.0, the publish skipped the gates main CI had already run on this commit and
  kept the per-platform consumer gates (linux-aarch64 on RBE and macos-aarch64 on the self-hosted
  executor, both 12/12). `publish-as-run.sh` is the exact script.
- **macOS natives.** Built from the tag on the self-hosted executor
  (`bazel build --config=remote_macos //tools/release:natives`, BuildBuddy `69a953d8`) and passed
  through `MACOS_NATIVES`; all four dylibs are arm64 Mach-O.
- **Build and checks.** Natives for five platforms, the staged Gradle consumer, `check_natives.py`,
  five CSV SDK bundles, signed payloads, then the Elide Maven upload. All 62 `rclone check` runs
  found 0 differences, and every public payload was byte-compared (`publish-excerpt.log`).
- **Public check afterwards.** All 1,254 public 0.17.0 files of the 31 modules were downloaded
  anonymously and equal the staged bytes; every module's `maven-metadata.xml` reports
  `latest`/`release` 0.17.0. All 92 GitHub asset digests equal the local bytes (`gh-digests.txt`,
  `release-assets.json`). The downloaded `SHA256SUMS` verifies against its signature, and a sampled
  asset (`truffledata-yaml-0.17.0-macos-aarch64.dylib`) matches it.
- **Charts.** None: no benchmark capture was taken and there is no performance change.

The publisher used the 0.16.0 host workarounds: Bazel's output root on `/build`, and a temporary
lower-MTU route for `uploads.github.com` (removed after the upload). The release stayed a draft
until all 92 assets were uploaded and verified.
