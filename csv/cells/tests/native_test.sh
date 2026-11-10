#!/usr/bin/env bash
set -euo pipefail
cd "${TEST_SRCDIR}/${TEST_WORKSPACE}"
out=$("$1")
echo "$out"
grep -q '^CSV cells acceptance: OK' <<<"$out"
