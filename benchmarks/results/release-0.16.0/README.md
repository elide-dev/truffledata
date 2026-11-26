# 0.16.0 publication evidence

Released from signed tag `v0.16.0`, commit `a46989237c491a0ecb7731c352c2a30441c14c3f` (#128 on main,
green `CI` and `Untrusted input`).
[GitHub release](https://github.com/elide-dev/truffledata/releases/tag/v0.16.0) and
[Elide Maven BOM](https://maven.elide.dev/dev/elide/truffledata-bom/0.16.0/truffledata-bom-0.16.0.pom)
are public. Maven Central was not attempted (#28).

A runtime-baseline release: Elide GraalVM 25.5.5-elide.31 → 25.5.5-elide.33 (CE and EE), with
`maven_install.json` repinned. The Elide toolchain stays at 2026.11.7 and Myna at 0.20.0.

- **Gates.** As for 0.12.0 through 0.13.1, the publish skipped the gates main CI had already run on
  this commit and kept the per-platform consumer gates (linux-aarch64 on RBE; macos-aarch64 on the
  self-hosted executor with `--config=remote_macos`, both 12/12). `publish-as-run.sh` is the exact
  script: the repository's `tools/release/publish.sh` minus those gates and the Central steps, with
  the macOS gates run remotely.
- **macOS natives.** Built from the tag on the self-hosted executor
  (`bazel build --config=remote_macos //tools/release:natives`, BuildBuddy `4851ca06`) and passed
  to the publisher through `MACOS_NATIVES`; all four dylibs are arm64 Mach-O.
- **Build and checks.** Natives for five platforms, the staged Gradle consumer, `check_natives.py`,
  five CSV SDK bundles, signed payloads, then the Elide Maven upload. All 62 `rclone check` runs
  found 0 differences, and every public payload was byte-compared (`publish-excerpt.log`).
- **Public check afterwards.** All 1,254 public 0.16.0 files of the 31 modules were downloaded
  anonymously and equal the staged bytes; every module's `maven-metadata.xml` reports
  `latest`/`release` 0.16.0. All 92 GitHub asset digests equal the local bytes (`gh-digests.txt`,
  `release-assets.json`). The downloaded `SHA256SUMS` verifies against its signature, and a sampled
  asset (`truffledata-json-0.16.0-macos-aarch64.dylib`) matches it.
- **Charts.** None: no benchmark capture was taken and there is no performance change.

Host issues on the Linux publisher (not release defects):
- The first two publish attempts stopped before any upload. Extracting the arm64 toolchain failed
  with `No space left on device` on the root volume, which refused writes past ~8 GB while `df` showed
  121 GB free. The successful run used `--output_user_root=/build/caches/bazel/truffledata`.
- GitHub asset uploads from this host stalled at ~10 KB/s and were cut off (408, empty replies), from
  a path-MTU black hole on the route to `uploads.github.com`. A route to `172.182.252.0/24` with
  `mtu lock 1200 advmss 1100` fixed it. The release stayed a draft until all 92 assets were uploaded
  and verified.
