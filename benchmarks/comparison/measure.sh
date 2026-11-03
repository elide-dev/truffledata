#!/usr/bin/env bash
set -euo pipefail
cd "${TEST_SRCDIR}/${TEST_WORKSPACE}"
exec python3 tools/bench/compare.py run --binary "$1" --output "${TEST_UNDECLARED_OUTPUTS_DIR}"
