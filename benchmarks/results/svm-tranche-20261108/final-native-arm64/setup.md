# Arm acceptance cache setup

The first launch stopped in `tools/bazel` before starting Bazel: the proposed
`/private/tmp` output directory was on case-insensitive APFS. The guard prevents
case-distinct Linux sysroot files from being overwritten. Its original output
is preserved in `initial-cache-guard.log.gz`; it was not a failed native test.

The existing shared case-sensitive cache had about 2.6 GiB free. A separate
64 GiB sparse APFS image was created for this qualification (it grows as used):

```sh
hdiutil create -size 64g -type SPARSEBUNDLE -fs 'Case-sensitive APFS' \
  -volname truffledata-svm-arm64 /private/tmp/truffledata-svm-arm64-20261108.sparsebundle
hdiutil attach -nobrowse -mountpoint /private/tmp/truffledata-svm-arm64-20261108 \
  /private/tmp/truffledata-svm-arm64-20261108.sparsebundle
```

A case-distinct filename probe passed before rerunning. The final build's
exact command, platform, pinned SDK archive hash, source hashes and invocation
will be recorded in `manifest.json` when the run completes. Both target and execution platforms use
`--config=remote_arm64`; the output and repository caches are on this separate
volume. The cache and original shared volume are preserved. This is native
semantic/graph qualification, not an Arm performance measurement.
