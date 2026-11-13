# 0.13.0 publication evidence

Released from signed tag `v0.13.0`, commit `6b6c78d09812f54e04a9e8afa571a393390720b8` (#102 on
main, green `CI` and `Untrusted input`).
[GitHub release](https://github.com/elide-dev/truffledata/releases/tag/v0.13.0) and
[Elide Maven BOM](https://maven.elide.dev/dev/elide/truffledata-bom/0.13.0/truffledata-bom-0.13.0.pom)
are public. Maven Central was not attempted (#28, quota resets 2026-12-01).

0.13.0 adds the experimental `truffledata-bebop` as the thirtieth library. Every other library and
its natives are built from the same sources as 0.12.0.

- **Gates.** Following the 0.12.0 precedent and the rule to reuse recent qualification, the
  publish skipped the gates main CI had already run on this commit (`//...`, the x86-64 native
  lanes, the JIT gate, the musl CSV/YAML tests). It kept the per-platform consumer gates CI never
  runs (Linux AArch64 on RBE, macOS locally). `publish-as-run.sh` is the exact script (the
  repository's `tools/release/publish.sh` minus those gates and the Central steps).
- **Build and checks.** Natives for five platforms, the staged Gradle consumer,
  `check_natives.py`, five CSV SDK bundles, 209 signed payloads, then the Elide Maven upload with
  `rclone check` and a byte comparison of every public payload (`publish-excerpt.log`).
- **Public check afterwards.** All 627 public 0.13.0 files of the 31 modules (payloads,
  signatures, SHA-256 sidecars, POMs; MD5/SHA-1/SHA-512 not re-fetched) were downloaded
  anonymously and equal the staged bytes; every module's `maven-metadata.xml` reports
  `latest`/`release` 0.13.0. All 92 GitHub asset digests equal the local bytes (`gh-digests.txt`,
  `release-assets.json`). An anonymous download of `SHA256SUMS` verifies against its signature,
  and two sampled assets match it.
- **Bebop consumer check.** `BebopConsumerCheck.java` was compiled and run against the public
  `truffledata-bebop` and `truffledata-core` 0.13.0 jars (with the Truffle API and JSpecify from
  the build cache): encode, decode, re-encode, validate, a column view, a guest row and a
  rejected malformed input, under the general profile and the rules 0.11 strict profile.
- **Charts.** None: no benchmark capture was taken and there is no performance change.

Not published: the native Bebop validator and its JVM and Native Image backends.

Still open from 0.11.0: the rapidyaml fuzz finding (`parse_engine.def.hpp:8310`, UBSan).
