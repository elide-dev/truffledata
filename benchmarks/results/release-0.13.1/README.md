# 0.13.1 publication evidence

Released from signed tag `v0.13.1`, commit `1f3845bd60b8c658bf833f300e09caad49923c21` (#105 on main,
green `CI` and `Untrusted input`).
[GitHub release](https://github.com/elide-dev/truffledata/releases/tag/v0.13.1) and
[Elide Maven BOM](https://maven.elide.dev/dev/elide/truffledata-bom/0.13.1/truffledata-bom-0.13.1.pom)
are public. Maven Central was not attempted (#28, quota resets 2026-12-01).

A fixes-only release: the six open fix PRs (#79-#83 and the rapidyaml patch for #72/#73), the doc PRs,
the 0.12.0 cards (#98) and the 0.13.0 receipts (#103), integrated on one branch and merged as #105.

- **Gates.** As for 0.12.0 and 0.13.0, the publish skipped the gates main CI had already run on this
  commit and kept the per-platform consumer gates. `publish-as-run.sh` is the exact script (the
  repository's `tools/release/publish.sh` minus those gates and the Central steps).
- **Build and checks.** Natives for five platforms, the staged Gradle consumer, `check_natives.py`,
  five CSV SDK bundles, signed payloads, then the Elide Maven upload with `rclone check` and a byte
  comparison of every public payload (`publish-excerpt.log`).
- **Public check afterwards.** All 1,254 public 0.13.1 files of the 31 modules (payloads, signatures,
  and the MD5/SHA-1/SHA-256/SHA-512 sidecars) were downloaded anonymously and equal the staged
  bytes; every module's `maven-metadata.xml` reports `latest`/`release` 0.13.1. All 92 GitHub asset
  digests equal the local bytes (`gh-digests.txt`, `release-assets.json`). A downloaded `SHA256SUMS`
  verifies against its signature, and a sampled asset matches it.
- **Charts.** None: no benchmark capture was taken and there is no performance change.

Note: fetching `maven.elide.dev` with Python's default user agent returns 403; use curl or set a
user agent.

Still open: the upstream rapidyaml report for the same-line-annotation finding; #89 (Myna latest).
