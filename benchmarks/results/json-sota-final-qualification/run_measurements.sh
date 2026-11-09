#!/usr/bin/env bash
set -euo pipefail
cd /tmp/truffledata-010-json.5QiP2w
printf '%s\n' "Started $(date -u +%FT%TZ)" > measurement-status.txt
for variant in baseline-ce7399f candidate candidate-v3; do
  mkdir -p "measurements/$variant"
  printf '%s\n' "Running $variant $(date -u +%FT%TZ)" >> measurement-status.txt
  TEST_UNDECLARED_OUTPUTS_DIR="$PWD/measurements/$variant" taskset -c 2-9 python3 json_measure.py "$PWD/images/$variant" > "measurements/$variant/runner.log" 2>&1
  printf '%s\n' "Completed $variant $(date -u +%FT%TZ)" >> measurement-status.txt
done
printf '%s\n' "Complete $(date -u +%FT%TZ)" >> measurement-status.txt
