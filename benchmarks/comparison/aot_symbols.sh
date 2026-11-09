#!/usr/bin/env bash
# AOT symbol gate (plan 2026-11-08-beat-stock-svm P0.6): code-shape budgets for named methods of a
# symbol-bearing (*-profile) image. Usage: aot_symbols.sh IMAGE BUDGETS.json. Shape only, no timing.
set -euo pipefail
cd "${TEST_SRCDIR}/${TEST_WORKSPACE}"
sha256sum "$1" > "${TEST_UNDECLARED_OUTPUTS_DIR}/binary.sha256"
exec python3 tools/bench/aot_symbols.py "$1" --budgets "$2" \
  --json "${TEST_UNDECLARED_OUTPUTS_DIR}/aot-symbols.json" \
  --markdown "${TEST_UNDECLARED_OUTPUTS_DIR}/aot-symbols.md"
