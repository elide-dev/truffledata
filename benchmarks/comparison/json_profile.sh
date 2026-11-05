#!/usr/bin/env bash
set -euo pipefail
cd "${TEST_SRCDIR}/${TEST_WORKSPACE}"
binary="$1"
sha256sum "$binary" > "${TEST_UNDECLARED_OUTPUTS_DIR}/binary.sha256"
for engine in gson-stream simdjson-typed simdjson-specialized; do
  for stage in ingest read-aot interpret peak; do
    output="${TEST_UNDECLARED_OUTPUTS_DIR}/${engine}-${stage}"
    "$binary" -Xmx1g "$engine" "$stage" time "${output}.jfr" > "${output}.jsonl"
    test -s "${output}.jfr"
  done
done
