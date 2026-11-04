#!/usr/bin/env bash
set -euo pipefail
cd "${TEST_SRCDIR}/${TEST_WORKSPACE}"
binary="$1"
settings="$2"
gc="$3"
workload="${4:-proto-mixed}"
rounds=8
[[ "$gc" != epsilon ]] || rounds=1
printf '%s\n' "$gc" > "${TEST_UNDECLARED_OUTPUTS_DIR}/gc.txt"
case "$workload" in
  mysql-int4|postgres-int4) engines=(stock static) ;;
  proto-bytes) engines=(stock stock-aliased owned buffa) ;;
  *) engines=(stock owned buffa) ;;
esac
sha256sum "$binary" > "${TEST_UNDECLARED_OUTPUTS_DIR}/binary.sha256"
printf '%s\n' "${TRUFFLEDATA_BENCH_REVISION:-unknown}" > "${TEST_UNDECLARED_OUTPUTS_DIR}/revision.txt"
for engine in "${engines[@]}"; do
  for fork in 0 1 2; do
    output="${TEST_UNDECLARED_OUTPUTS_DIR}/${workload}-${engine}-${fork}"
    "$binary" -Xmx1g "-Dtruffledata.profile.rounds=${rounds}" "-XX:StartFlightRecording=filename=${output}.jfr,settings=${settings},dumponexit=true" \
      "$workload" "$engine" ingest time > "${output}.jsonl"
    test -s "${output}.jfr"
  done
done
