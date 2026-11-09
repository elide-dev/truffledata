#!/usr/bin/env bash
# Diagnostic sampling only; these timings never enter consumer performance reports.
set -euo pipefail
cd "${TEST_SRCDIR}/${TEST_WORKSPACE}"
binary="$1"
sha256sum "$binary" > "${TEST_UNDECLARED_OUTPUTS_DIR}/binary.sha256"
for engine in stock stock-interop generated-owned slab; do
  output="${TEST_UNDECLARED_OUTPUTS_DIR}/${engine}-full-interpret"
  "$binary" -Xmx1g "$engine" full interpret time "${output}.jfr" > "${output}.jsonl"
  test -s "${output}.jfr"
done
# AOT method sizes are supporting diagnosis, not a new size budget. Some remote images strip nm.
if command -v nm >/dev/null; then
  nm -S --size-sort --defined-only "$binary" > "${TEST_UNDECLARED_OUTPUTS_DIR}/symbols.txt"
fi
