#!/usr/bin/env bash
# Usage: consumer_measure.sh svm|jvm CANDIDATE BASELINE. Identical paths measure one binary.
set -euo pipefail
cd "${TEST_SRCDIR}/${TEST_WORKSPACE}"
runtime=$1
scope=${4:-full}
measure=()
if [[ "$scope" == "projection" ]]; then
  measure+=(--projection-gates --groups "sparse:resident,holdout:resident,holdout-sparse:resident"
    --engines "candidate=stock,slab,slab-projected,slab-lazy,slab-session,slab-session-projected")
fi
binaries=(--binary "candidate=$PWD/$2")
if [[ "$3" != "$2" ]]; then
  # Historical images expose the retired native-view/native-eager engines. A current-revision
  # before/after pair must declare its actual engines instead of silently using that old matrix.
  baseline_engines=${TRUFFLEDATA_CONSUMER_BASELINE_ENGINES:-stock,generated-owned,native-view,native-eager}
  baseline_modes=${TRUFFLEDATA_CONSUMER_BASELINE_MODES:-sparse,full,holdout,large}
  binaries+=(--binary "baseline=$PWD/$3" --engines "baseline=$baseline_engines" --modes "baseline=$baseline_modes")
fi
# java_binary launchers resolve their classpath through the enclosing test's runfiles.
export JAVA_RUNFILES="${TEST_SRCDIR}"
exec python3 tools/bench/consumer.py --runtime "$runtime" --pin "${binaries[@]}" "${measure[@]}" \
  --source "${TRUFFLEDATA_BENCH_REVISION:-unrecorded}" \
  --output "${TEST_UNDECLARED_OUTPUTS_DIR}/consumer"
