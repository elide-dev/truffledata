#!/usr/bin/env bash
# WS11 Myna cell probe (plan 2026-11-08-beat-stock-svm): every route in one binary, one worker,
# three fresh forks per route, pinned to one CPU. Usage: cells_probe.sh CELLS_PROBE_NATIVE
set -euo pipefail
cd "${TEST_SRCDIR}/${TEST_WORKSPACE}"
binary="$PWD/$1"
out="${TEST_UNDECLARED_OUTPUTS_DIR}"
sha256sum "$binary" > "$out/binary.sha256"
python3 benchmarks/comparison/cells_probe.py --binary "$binary" --forks 3 --pin \
  --seed "${TRUFFLEDATA_CELLS_SEED:-20261109}" \
  --source "${TRUFFLEDATA_BENCH_REVISION:-unrecorded}" --output "$out/cells"
