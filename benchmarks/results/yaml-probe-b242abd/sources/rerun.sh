#!/bin/bash
cd /private/tmp/claude-501/-Users-sam-workspace-graalvm-truffledata/107505d2-50ff-4334-8569-b06acaf924d5/scratchpad
./build/cprobe bench 20 probe/fixtures/config.yaml probe/fixtures/records-block.yaml probe/fixtures/records-flow.yaml probe/fixtures/records-stream.yaml probe/fixtures/text-heavy.yaml > results-cprobe-arm64.txt 2>&1
echo "--- records-flow-lines with fresh libfyaml parsers (fy_parser_reset reuse fails on this input)" >> results-cprobe-arm64.txt
FY_FRESH=1 ./build/cprobe bench 20 probe/fixtures/records-flow-lines.yaml >> results-cprobe-arm64.txt 2>&1
: > results-longline-arm64.txt
for f in oneline-25 oneline-50 oneline-100 oneline-200 records-flow; do
  ./build/cprobe bench 2 probe/fixtures/$f.yaml 2>&1 | grep -E "scan" >> results-longline-arm64.txt
done
cat results-cprobe-arm64.txt results-longline-arm64.txt
