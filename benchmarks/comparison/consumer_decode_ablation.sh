#!/usr/bin/env bash
# P0.1 decode pipeline ablation (plan 2026-11-08-beat-stock-svm), one worker, one binary:
# probe engines, production decode references and the standalone native decoder on the same
# fixture, then diagnostic samples (perf when the worker has it, JFR otherwise). Usage:
# consumer_decode_ablation.sh CONSUMER_SVM CONSUMER_PROFILE NATIVE_PROBE
set -euo pipefail
cd "${TEST_SRCDIR}/${TEST_WORKSPACE}"
binary="$PWD/$1"
profile="$PWD/$2"
native="$PWD/$3"
out="${TEST_UNDECLARED_OUTPUTS_DIR}"
sha256sum "$binary" "$profile" "$native" > "$out/binary.sha256"
probes=$(python3 -c 'import sys; sys.path.insert(0, "tools/bench"); import consumer; print(",".join(consumer.PROBE_ENGINES))')
for mode in full holdout; do
  "$binary" -Xmx1g --dump-fixture "$mode" "$out/fixture-$mode"
done
# Every probe and reference cell, three fresh forks, interleaved and pinned like consumer_measure.
python3 tools/bench/consumer.py --runtime svm --pin --binary "candidate=$binary" \
  --engines "candidate=stock,slab,slab-session,$probes" --groups full:decode,holdout:decode \
  --source "${TRUFFLEDATA_BENCH_REVISION:-unrecorded}" --output "$out/probes"
# The standalone native decoder on the same worker and CPU, three fresh processes per fixture.
python3 - "$native" "$out" <<'EOF'
import os, subprocess, sys
native, out = sys.argv[1], sys.argv[2]
cpus = sorted(os.sched_getaffinity(0))
os.sched_setaffinity(0, {cpus[-1]})
with open(os.path.join(out, 'native.jsonl'), 'w') as log:
    for fork in range(3):
        for mode in ('full', 'holdout'):
            result = subprocess.run([native, os.path.join(out, f'fixture-{mode}'), mode],
                                    capture_output=True, text=True, check=True)
            for line in result.stdout.splitlines():
                log.write(line.rstrip('}') + f',"fork":{fork},"cpu":{cpus[-1]}}}\n')
EOF
# Diagnostic samples of the decode and session cells; never timings.
{
  echo "perf: $(command -v perf || echo 'not installed')"
  cat /proc/sys/kernel/perf_event_paranoid 2>/dev/null | sed 's/^/perf_event_paranoid: /' || true
} > "$out/perf.txt"
for cell in "slab full" "slab-session full" "probe-plan holdout" "probe-session holdout"; do
  set -- $cell
  name="$1-$2-decode"
  if command -v perf >/dev/null && perf record -q -g -o "$out/$name.perf.data" -- \
      "$binary" -Xmx1g "$1" "$2" decode time > "$out/$name.perf.jsonl" 2>> "$out/perf.txt"; then
    perf report -i "$out/$name.perf.data" --stdio --no-children --percent-limit 0.5 \
      > "$out/$name.perf.txt" 2>> "$out/perf.txt" || true
    rm -f "$out/$name.perf.data"
  else
    echo "$name: perf record unavailable" >> "$out/perf.txt"
  fi
  "$profile" -Xmx1g "$1" "$2" decode time "$out/$name.jfr" > "$out/$name.jfr.jsonl"
  test -s "$out/$name.jfr"
done
