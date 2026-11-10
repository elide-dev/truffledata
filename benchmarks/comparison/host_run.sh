#!/usr/bin/env bash
# Release benchmarks on one quiet dedicated host: every shard of every suite, in sequence, on one
# pinned CPU (docs/COMPARISON_BENCHMARKS.md, "Release 0.11.0 benchmark cards").
#
# Layout: this script next to runfiles/ (the merged runfiles trees of json_typed_pair, csv_pair,
# consumer_measure and yaml_pair, as listed in their .runfiles_manifest files). Outputs land in
# out/<test>/shard-<index>/ with a .done marker; a rerun skips finished shards.
#   REVISION=<sha> CPU=12 ./host_run.sh
set -uo pipefail
R=$(cd "$(dirname "$0")" && pwd)
CPU=${CPU:-12}
export TEST_SRCDIR=$R/runfiles TEST_WORKSPACE=_main
export TRUFFLEDATA_BENCH_REVISION=${REVISION:?} TRUFFLEDATA_BENCH_BASELINE_REVISION=$REVISION
export CSV_PAIR_CANDIDATE_REVISION=$REVISION CSV_PAIR_BASELINE_REVISION=$REVISION
export YAML_PAIR_CANDIDATE_REVISION=$REVISION YAML_PAIR_BASELINE_REVISION=$REVISION
run() {
  local name=$1 shards=$2; shift 2
  for ((i = 0; i < shards; i++)); do
    local out=$R/out/$name/shard-$i
    [[ -f $out/.done ]] && continue
    rm -rf "$out"; mkdir -p "$out"
    echo "$(date -u +%FT%TZ) start $name $i load $(cut -d' ' -f1-3 /proc/loadavg)" | tee -a "$R/progress.log"
    if TEST_SHARD_INDEX=$i TEST_TOTAL_SHARDS=$shards TEST_UNDECLARED_OUTPUTS_DIR=$out \
        taskset -c "$CPU" bash "$R/runfiles/_main/benchmarks/comparison/$name.sh" "$@" > "$out/log" 2>&1; then
      touch "$out/.done"; echo "$(date -u +%FT%TZ) done $name $i" | tee -a "$R/progress.log"
    else
      echo "$(date -u +%FT%TZ) FAILED $name $i" | tee -a "$R/progress.log"
    fi
  done
}
B=benchmarks/comparison
run json_typed_pair 9 $B/json-comparison $B/json-comparison
run consumer_measure 10 svm $B/truffledata-consumer $B/truffledata-consumer
run yaml_pair 4 $B/yaml-comparison $B/yaml-comparison
run csv_pair 8 $B/csv-comparison $B/csv-comparison
echo "$(date -u +%FT%TZ) ALL FINISHED" | tee -a "$R/progress.log"
