#!/usr/bin/env bash
# AOT shape of the JSON cells route (brief item 2). Usage: aot_shape.sh IMAGE. Shape only, no timing.
set -euo pipefail
cd "${TEST_SRCDIR}/${TEST_WORKSPACE}"
sha256sum "$1" > "${TEST_UNDECLARED_OUTPUTS_DIR}/binary.sha256"
"$1"
exec python3 json/cells/tests/aot_shape.py tools/bench "$1"
