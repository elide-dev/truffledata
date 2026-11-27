# 0.18.0 publication evidence

Released from signed tag `v0.18.0`, commit `bc4078df0fd95de7fd696858af839ec307ff12c5` (#132 on main,
green `CI` and `Untrusted input`).
[GitHub release](https://github.com/elide-dev/truffledata/releases/tag/v0.18.0) and
[Elide Maven BOM](https://maven.elide.dev/dev/elide/truffledata-bom/0.18.0/truffledata-bom-0.18.0.pom)
are public. Maven Central was not attempted (#28).

A bindings and toolchain release: Myna 0.22.0 → 0.23.0 (new ABI fingerprints for every seam; the JSON
and CSV Cargo crates re-vendored) and Elide toolchain 2026.11.7 → 2026.11.8. GraalVM stays at
25.5.5-elide.33. Myna's new upcall features are not adopted (no seam declares a callback). The branch
first built against the GitHub archive of Myna's signed `v0.23.0` tag commit (`6dcdd38`); Myna then
published 0.23.0, and the release tarball (matching its `SHA256SUMS` and the tag archive's contents)
was pinned before the PR. Before merge: `bazel build //...` and `bazel test //...` (91/91), and the
JSON/CSV `cargo test` passed locally, and all 17 PR checks passed. The sanitizer job's first run hit
its 60-minute limit while rebuilding against the new toolchain from a cold cache (one of two Rust ASan
tests had passed); its rerun passed. The PR also fixed the ASan add-on digest, which is tied to the
toolchain version.

- **Gates.** As for 0.17.0, the publish skipped the gates main CI had already run on this commit and
  kept the per-platform consumer gates (linux-aarch64 on RBE and macos-aarch64 on the self-hosted
  executor, both 12/12). `publish-as-run.sh` is the exact script (unchanged from 0.17.0).
- **macOS natives.** Built from the tag commit on the self-hosted executor
  (`bazel build --config=remote_macos //tools/release:natives`) and passed through `MACOS_NATIVES`;
  all four dylibs are arm64 Mach-O.
- **Build and checks.** Natives for five platforms, the staged Gradle consumer, `check_natives.py`,
  five CSV SDK bundles, signed payloads, then the Elide Maven upload. All 62 `rclone check` runs
  found 0 differences, and every public payload was byte-compared (`publish-excerpt.log`).
- **Public check afterwards.** All 1,254 public 0.18.0 files of the 31 modules were downloaded
  anonymously and equal the staged bytes; every module's `maven-metadata.xml` reports
  `latest`/`release` 0.18.0. All 92 GitHub asset digests equal the local bytes (`gh-digests.txt`,
  `release-assets.json`). The downloaded `SHA256SUMS` verifies against its signature, and a sampled
  asset (`truffledata-yaml-0.18.0-macos-aarch64.dylib`) matches it.
- **Charts.** None: no benchmark capture was taken and there is no performance change.

The publisher used the 0.16.0 host workarounds: Bazel's output root on `/build`, and a temporary
lower-MTU route for `uploads.github.com` (removed after the upload). The release stayed a draft
until all 92 assets were uploaded and verified.
