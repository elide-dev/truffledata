#!/usr/bin/env bash
set -euo pipefail
cd /tmp/truffledata-010-json.5QiP2w
trap 'printf "Failed %s\n" "$(date -u +%FT%TZ)" >> compatibility-status.txt' ERR
printf 'Waiting for scoreboard %s\n' "$(date -u +%FT%TZ)" > compatibility-status.txt
while [[ ! -f measurements/candidate-v3/json-comparison.json ]]; do
  if [[ -f measurements/candidate-v3/json-comparison-failure.json ]]; then exit 1; fi
  sleep 5
done
for pair in baseline-ce7399f-rerun:baseline-ce7399f candidate:candidate; do
  output=${pair%%:*}
  executable=${pair#*:}
  mkdir -p "measurements/$output"
  printf 'Running %s %s\n' "$output" "$(date -u +%FT%TZ)" >> compatibility-status.txt
  TEST_UNDECLARED_OUTPUTS_DIR="$PWD/measurements/$output" taskset -c 2-9 python3 json_measure.py "$PWD/images/$executable" > "measurements/$output/runner.log" 2>&1
  printf 'Completed %s %s\n' "$output" "$(date -u +%FT%TZ)" >> compatibility-status.txt
done
