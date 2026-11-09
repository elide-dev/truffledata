#!/usr/bin/env python3
"""Preserve consumer images: preserve.py DEST REVISION BUILD_LOG BAZEL_BIN.

Copies truffledata-consumer and truffledata-consumer-profile out of bazel-bin into an ignored
directory with a sha256/size manifest and the Bazel invocation id, following the preserved
baselines of benchmarks/results/svm-tranche-20261108/README.md, and writes an exports_files BUILD
so the images can be named as --//benchmarks/comparison:consumer_svm_baseline labels.
"""
import hashlib
import json
import pathlib
import re
import shutil
import sys

dest, revision, log, bazel_bin = sys.argv[1:5]
out = pathlib.Path(dest)
out.mkdir(parents=True, exist_ok=True)
names = ("truffledata-consumer", "truffledata-consumer-profile")
for name in names:
    source = pathlib.Path(bazel_bin) / "benchmarks" / "comparison" / name
    target = out / name
    shutil.copyfile(source, target)
    target.chmod(0o555)
text = pathlib.Path(log).read_text(errors="replace")
match = re.search(r"invocation/([0-9a-f-]{36})", text)
manifest = {
    "production_revision": revision,
    "build_invocation": match.group(1) if match else None,
    "binaries": {
        name: {
            "sha256": hashlib.sha256((out / name).read_bytes()).hexdigest(),
            "bytes": (out / name).stat().st_size,
        }
        for name in names
    },
}
(out / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
(out / "BUILD.bazel").write_text(
    'exports_files(["truffledata-consumer", "truffledata-consumer-profile"],'
    ' visibility = ["//visibility:public"])\n'
)
print(json.dumps(manifest, indent=2))
