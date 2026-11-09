#!/bin/bash
# bzl: run one Bazel invocation at a time across worktrees (shared Bazel volume).
# Usage: bzl <bazel args...>   e.g.  bzl test //protobuf-runtime:test
LOCK=/private/tmp/claude-501/-Users-sam-workspace-graalvm-truffledata/be7493c3-484b-4def-bce7-2f623fdfb385/scratchpad/bazel.lock
while ! mkdir "$LOCK" 2>/dev/null; do
  holder=$(cat "$LOCK/pid" 2>/dev/null || echo 0)
  if [ "$holder" != 0 ] && ! kill -0 "$holder" 2>/dev/null; then rm -rf "$LOCK"; continue; fi
  sleep 5
done
echo $$ > "$LOCK/pid"
export JAVA_HOME=/Library/Java/JavaVirtualMachines/gvm.jdk25/Contents/Home
export PATH=/usr/bin:$PATH
bazelisk "$@"
rc=$?
rm -rf "$LOCK"
exit $rc
