# 0.10.0 publication evidence

Released from signed tag `v0.10.0`, commit `32a5f1946f4107831aa68d9594122de0a83c8d7a`.
[GitHub release](https://github.com/elide-dev/truffledata/releases/tag/v0.10.0) and
[Elide Maven BOM](https://maven.elide.dev/dev/elide/truffledata-bom/0.10.0/truffledata-bom-0.10.0.pom)
are public. The original main checkout and remote main received PR #36.

- Both normal candidate and main CI passed. Main's two raw negative-build FAILURE
  contexts are expected controls, validated by the successful JIT gate; see `main-ci/`.
- Five-platform staging passed native member/ABI fingerprint/dependency/export checks.
  The staged Gradle consumer selected exact binary/source/Javadoc bytes and BOM metadata.
- All 153 Maven payloads have verified signatures. All 306 public payload/signature files
  and 22 public metadata files match staging. The isolated public Maven consumer resolved
  all 22 modules, compared bytes, compiled and executed the existing consumer.
- All 112 GitHub asset digests match local bytes. Anonymous downloads of all five CSV SDK
  bundles and signed checksums match; the downloaded checksum signature verifies.
- Charts retain the previously completed 244 paired SVM cells, source/image provenance,
  competitor boundaries and observed losses. No extra performance matrix was run.

## Central deferred by the user

The signed bundle passed Central validation, then publication failed because Sonatype
reported the organization's monthly release-size quota exhausted. The grace period ended
2026-11-07; usage resets 2026-12-01. The user explicitly selected **Finish release; defer
Central**. This is not a code/signature/validation failure and Central is not published.
Issue #28 stays open, alongside #37 (Cargo) and #38 (unknown-field allocation).

`central/deployment.json` preserves deployment ID and exact signed ZIP SHA-256;
`central/failed.json` preserves the service's error. The original artifacts remain in the
integration checkout under `build/release-010-maven`, `build/release-010-maven.central.zip`
and `build/release-010-bundles`. Resume Central only after a new instruction and available
quota. Preserve the failed deployment for diagnosis; if a new deployment is required,
reuse the exact signed ZIP. Do not re-upload or overwrite the immutable Elide Maven version.

`public/`, `staging/`, `signatures/` and the two CI directories contain compact receipts.
Workstation paths are normalized or omitted. `artifacts.json` hashes these archived files;
embedded hashes continue to identify actual release payloads. The performance comparison
and possible follow-up investigations remain in `../svm-tranche-20261108/README.md` and the
owning ledgers; this release does not claim their open goals were achieved.
