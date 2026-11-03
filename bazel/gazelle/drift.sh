#!/usr/bin/env bash
set -euo pipefail
workspace="$TEST_SRCDIR/$TEST_WORKSPACE"
generator="$workspace/$1"
# Work on copies: never mutate runfile symlinks or the checkout from a remote test.
work="$TEST_TMPDIR/sources"
mkdir -p "$work"
cp -RL "$workspace/." "$work/"
cd "$work"
"$generator" -repo_root "$work" -r=false -index=false core jdbc jdbc-sqlite jdbc-mysql jdbc-postgresql protobuf tests benchmarks benchmarks/jdbc
status=0
for module in core jdbc jdbc-sqlite jdbc-mysql jdbc-postgresql protobuf tests benchmarks benchmarks/jdbc; do
  mkdir -p "$TEST_UNDECLARED_OUTPUTS_DIR/$module"
  cp "$module/BUILD.bazel" "$TEST_UNDECLARED_OUTPUTS_DIR/$module/BUILD.bazel"
  diff -u "$workspace/$module/BUILD.bazel" "$module/BUILD.bazel" || status=1
done
exit "$status"
