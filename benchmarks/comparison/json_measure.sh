#!/usr/bin/env bash
set -euo pipefail
cd "${TEST_SRCDIR}/${TEST_WORKSPACE}"
exec python3 benchmarks/comparison/json_measure.py "$1"
