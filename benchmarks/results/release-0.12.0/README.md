# 0.12.0 publication evidence

Released from signed tag `v0.12.0`, commit `c378d6d837a248f19fcc12fcff8799415695f244` (#96 on
main, green `CI` and `Untrusted input`). The version bump, notes and lockfile refresh (#97) and
the benchmark cards (#98) landed on main after the tag; the published artifacts take their
version from `--//tools/release:version=0.12.0`, not from `MODULE.bazel`.
[GitHub release](https://github.com/elide-dev/truffledata/releases/tag/v0.12.0) and
[Elide Maven BOM](https://maven.elide.dev/dev/elide/truffledata-bom/0.12.0/truffledata-bom-0.12.0.pom)
are public. Maven Central was not attempted (#28, quota resets 2026-12-01).

- **Gates.** At Sam's instruction the publish skipped the gates main CI had already run on this
  code (`//...`, the x86-64 native lanes, the JIT gate, the musl CSV/YAML tests). It kept the
  per-platform consumer gates CI never runs (Linux AArch64 on RBE, macOS locally), which load
  every new native against the regenerated bindings and their ABI-class fingerprints.
  `publish-as-run.sh` is the exact script (the repository's `tools/release/publish.sh` minus
  those gates and the Central steps).
- **Build and checks.** Natives for five platforms, the staged Gradle consumer,
  `check_natives.py` (members, ABI fingerprints, dependencies, exports), five CSV SDK bundles,
  signed payloads, then the Elide Maven upload with `rclone check` and a byte comparison of every
  public payload (`publish-excerpt.log`).
- **Public check afterwards.** All 612 public 0.12.0 files of the 30 modules (payloads,
  signatures, SHA-256 sidecars, POMs; MD5/SHA-1/SHA-512 not re-fetched) were downloaded
  anonymously and equal the staged bytes; `maven-metadata.xml` reports `latest`/`release`
  0.12.0. All 114 GitHub asset digests equal the local bytes (`gh-digests.txt`,
  `release-assets.json`). Anonymous downloads of the five CSV SDK bundles match the signed
  `SHA256SUMS`, whose signature verifies.
- **Charts** attached to the release are the README cards of the fresh capture at the tag
  (`../bench-0.12.0/`).

Still open from 0.11.0: the rapidyaml fuzz finding (`parse_engine.def.hpp:8310`, UBSan).
