#!/usr/bin/env bash
# Usage: consumer_measure.sh svm|jvm CANDIDATE BASELINE. Identical paths measure one binary.
set -euo pipefail
cd "${TEST_SRCDIR}/${TEST_WORKSPACE}"
runtime=$1
binaries=(--binary "candidate=$PWD/$2")
if [[ "$3" != "$2" ]]; then
  binaries+=(--binary "baseline=$PWD/$3" --engines "baseline=stock,generated-owned,native-view,native-eager")
fi
# java_binary launchers resolve their classpath through the enclosing test's runfiles.
export JAVA_RUNFILES="${TEST_SRCDIR}"
exec python3 tools/bench/consumer.py --runtime "$runtime" --pin "${binaries[@]}" \
  --source "${TRUFFLEDATA_BENCH_REVISION:-unrecorded}" \
  --output "${TEST_UNDECLARED_OUTPUTS_DIR}/consumer"
