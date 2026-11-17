# 0.14.0 publication evidence

Released from signed tag `v0.14.0`, commit `55de716347ed347ac6739f9124aea8125b8b10c2` (#123 on main,
green `CI` and `Untrusted input`; the hosted macOS YAML job first hit its timeout on a slow runner and
passed on rerun, before #124 moved those jobs to the self-hosted macOS executor).
[GitHub release](https://github.com/elide-dev/truffledata/releases/tag/v0.14.0) and
[Elide Maven BOM](https://maven.elide.dev/dev/elide/truffledata-bom/0.14.0/truffledata-bom-0.14.0.pom)
are public. Maven Central was not attempted (#28, quota resets 2026-12-01).

The release moves the bindings to Myna 0.19.0 (#121, then the released tarball), adopts trufflecheck
0.1.1 (#122), and fixes #112 and the bugs of #104 and #115-#120; see `docs/RELEASE_0_14_0.md`.

- **Gates.** As for 0.12.0 through 0.13.1, the publish skipped the gates main CI had already run on
  this commit and kept the per-platform consumer gates. `publish-as-run.sh` is the exact script,
  identical to 0.13.1's (the repository's `tools/release/publish.sh` minus those gates and the
  Central steps).
- **Build and checks.** Natives for five platforms, the staged Gradle consumer, `check_natives.py`,
  five CSV SDK bundles, signed payloads, then the Elide Maven upload with `rclone check` and a byte
  comparison of every public payload (`publish-excerpt.log`).
- **Public check afterwards.** All 1,254 public 0.14.0 files of the 31 modules (payloads, signatures
  and checksum sidecars) were downloaded anonymously and equal the staged bytes; every module's
  `maven-metadata.xml` reports `latest`/`release` 0.14.0. All 92 GitHub asset digests equal the local
  bytes (`gh-digests.txt`, `release-assets.json`). The downloaded `SHA256SUMS` verifies against its
  OpenPGP signature (key `A166AC62F6DE8C63732277731D5903AF4582BCF4`), and a sampled asset matches it.
- **Charts.** None: no benchmark capture was taken and no performance claim is made.

Still open: elide-dev/myna#159 and elide-dev/trufflecheck#9 (adoption feedback); the upstream
rapidyaml report for the same-line-annotation finding.
