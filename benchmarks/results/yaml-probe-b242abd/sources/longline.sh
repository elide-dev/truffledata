#!/bin/bash
cd /private/tmp/claude-501/-Users-sam-workspace-graalvm-truffledata/107505d2-50ff-4334-8569-b06acaf924d5/scratchpad
for f in oneline-50 oneline-100 oneline-200; do
  ./build/cprobe bench 1 probe/fixtures/$f.yaml 2>&1 | grep -E "scan"
done | tee -a results-longline-arm64.txt
echo "--- fy-tool on records-flow-lines.yaml"
./build/libfyaml/fy-tool --testsuite probe/fixtures/records-flow-lines.yaml 2>&1 | tail -3
echo "--- libyaml on records-flow-lines.yaml"
./build/libyaml/run-parser-test-suite probe/fixtures/records-flow-lines.yaml 2>&1 | tail -2
