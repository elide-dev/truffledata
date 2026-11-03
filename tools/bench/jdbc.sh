#!/usr/bin/env bash
# Deliberately local elapsed-time measurements; never label these as CodSpeed simulation.
set -euo pipefail
cd "$(dirname "$0")/../.."
driver=${1:-sqlite}
[[ $# == 0 ]] || shift
case "$driver" in sqlite|mysql|postgresql) ;; *) echo 'Expected sqlite, mysql, or postgresql' >&2; exit 1 ;; esac
base="$PWD/build/reports/jmh/$driver"
mkdir -p "$base"
report=$(mktemp -d "$base/run.XXXXXX")
python3 - "$driver" "$report/environment.json" "$@" <<'PY'
import hashlib, json, platform, subprocess, sys
from pathlib import Path
metadata = {
    'dataset': 'jdbc-retained-four-field-v1', 'rows': 256, 'checksum': 322262,
    'driver': sys.argv[1], 'platform': platform.platform(), 'machine': platform.machine(),
    'commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
    'dirty': bool(subprocess.check_output(['git', 'status', '--porcelain'], text=True)),
    'jmh_overrides': sys.argv[3:],
    'source_sha256': hashlib.sha256(Path('benchmarks/jdbc/src/main/java/dev/elide/truffledata/bench/jdbc/JdbcPipelineBenchmark.java').read_bytes()).hexdigest(),
    'scope': 'local elapsed timing; per-row scores; driver/server versions in jmh.log',
}
Path(sys.argv[2]).write_text(json.dumps(metadata, indent=2) + '\n')
PY
"${BAZEL:-bazel}" run --config=local //benchmarks/jdbc:jmh -- \
  JdbcPipelineBenchmark -p "driver=$driver" -prof gc -rf json -rff "$report/results.json" -foe true "$@" \
  2>&1 | tee "$report/jmh.log"
python3 - "$driver" "$report/results.json" <<'PYVERIFY'
import json, math, sys
from pathlib import Path
results = json.loads(Path(sys.argv[2]).read_text())
expected = {'plainQueryRetain', 'batchQueryRetain', 'staticQueryRetain',
            'plainRetainedRead', 'batchRetainedRead', 'staticRetainedRead'}
names = [result['benchmark'].rsplit('.', 1)[-1] for result in results]
if len(results) != 6 or set(names) != expected:
    raise SystemExit('Expected exactly six JDBC benchmark results')
for result in results:
    metric = result['primaryMetric']
    if result['params']['driver'] != sys.argv[1] or metric['scoreUnit'] != 'ns/op':
        raise SystemExit('Unexpected benchmark driver or unit')
    if not math.isfinite(metric['score']) or metric['score'] <= 0:
        raise SystemExit('Invalid elapsed-time score')
    if 'gc.alloc.rate.norm' not in result['secondaryMetrics']:
        raise SystemExit('Missing allocation measurement')
print('Verified six elapsed-time and allocation results')
PYVERIFY
echo "JMH reports: $report"
