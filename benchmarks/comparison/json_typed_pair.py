"""Same-host, fresh-process paired SVM measurements for the structural typed tranche."""
import hashlib
import itertools
import json
import math
import os
from pathlib import Path
import platform
import random
import statistics
import subprocess
import sys

binary = {'candidate': Path(sys.argv[1]).resolve(), 'baseline': Path(sys.argv[2]).resolve()}
output = Path(os.environ['TEST_UNDECLARED_OUTPUTS_DIR'])
output.mkdir(parents=True, exist_ok=True)
shard = int(os.environ.get('TEST_SHARD_INDEX', '0'))
shards = int(os.environ.get('TEST_TOTAL_SHARDS', '1'))
if os.environ.get('TEST_SHARD_STATUS_FILE'):
    Path(os.environ['TEST_SHARD_STATUS_FILE']).touch()
# Pin the orchestrator and every child to one allowed logical CPU. Each complete cell pair
# therefore shares its host and CPU; different shards are intentionally separate experiments.
affinity = sorted(os.sched_getaffinity(0))
os.sched_setaffinity(0, {affinity[shard % len(affinity)]})
engines = ['simdjson-typed-' + kind for kind in ('nested', 'repeated', 'mixed', 'churn')]
checksums = {}
for mode, engine in enumerate(engines):
    total = 0
    for i in range(32):
        count = 0 if mode == 0 else 16 if mode == 1 else 4 if mode == 2 else i % 9
        point = lambda n: 2 * n + 2 + (1 if n % 2 == 0 else 0)
        total += i + point(100 + i)
        total += sum(point(1000 + i * 17 + j) + j - i for j in range(count))
    checksums[engine] = total
jobs = [(engine, stage, metric, fork)
        for at, engine in enumerate(engines) if at % shards == shard
        for stage, metric, fork in itertools.product(
            ('ingest', 'read-aot', 'interpret', 'peak'), ('time', 'memory'), range(3))]
rng = random.Random(2026110801 + shard)
rng.shuffle(jobs)
records = []
report = {
    'schema': 1, 'complete': False, 'source': os.environ.get('TRUFFLEDATA_BENCH_REVISION', 'unrecorded'),
    'platform': platform.platform(), 'cpu': Path('/proc/cpuinfo').read_text(),
    'affinity': sorted(os.sched_getaffinity(0)), 'shard': shard, 'shards': shards,
    'binary_sha256': {name: hashlib.sha256(path.read_bytes()).hexdigest() for name, path in binary.items()},
    # Both images must be built from the identical fixture commit, with only production differing.
    # The checksums below independently derive from this fixture's exact 32-document formulas.
    'fixture_source_sha256': hashlib.sha256(Path('benchmarks/comparison/JsonComparison.java').read_bytes()).hexdigest(),
    'expected_checksums': checksums, 'measurements': records,
}
def save():
    (output / 'json-typed-pair.json').write_text(json.dumps(report, indent=2) + '\n')

# Prove both old and new images execute every public read tier before timing this shard.
# Preserve compiler/runtime output separately so failures cannot disappear into timing data.
for role, path in binary.items():
    smoke = subprocess.run([str(path), '--typed-smoke'], capture_output=True, text=True)
    (output / (role + '-typed-smoke.stdout')).write_text(smoke.stdout)
    (output / (role + '-typed-smoke.stderr')).write_text(smoke.stderr)
    smoke.check_returncode()

for engine, stage, mode, fork in jobs:
    order = ['candidate', 'baseline']
    rng.shuffle(order)
    for role in order:
        command = [str(binary[role]), engine, stage, mode]
        process = subprocess.run(command, capture_output=True, text=True)
        if process.returncode:
            (output / 'failure.json').write_text(json.dumps({
                'command': command, 'role': role, 'fork': fork,
                'stdout': process.stdout, 'stderr': process.stderr,
            }, indent=2) + '\n')
            process.check_returncode()
        lines = [line for line in process.stdout.splitlines() if line.startswith('{')]
        if len(lines) != 1:
            raise AssertionError('Expected one measurement record')
        row = json.loads(lines[0])
        if (row['workload'], row['engine'], row['stage'], row['runtime'], row['checksum'], row['rows']) != (
                'json-' + engine.removeprefix('simdjson-'), engine, stage, 'svm', checksums[engine], 32):
            raise AssertionError('Fixture mismatch: ' + repr(row))
        if row['guest_compiled'] != (stage == 'peak'):
            raise AssertionError('Execution tier mismatch')
        metric = 'ns_per_row' if mode == 'time' else 'allocated_bytes_per_row'
        samples = row[metric]
        if len(samples) != 3 or any(not math.isfinite(v) or v < 0 or mode == 'time' and v == 0 for v in samples):
            raise AssertionError('Invalid samples')
        row.update(role=role, fork=fork, order=order)
        records.append(row)
        save()
        print(role, engine, stage, mode, fork, samples, flush=True)
report['complete'] = True
summary = []
for engine in engines:
    for stage in ('ingest', 'read-aot', 'interpret', 'peak'):
        for metric in ('ns_per_row', 'allocated_bytes_per_row'):
            cells = {}
            for role in binary:
                values = [statistics.median(row[metric]) for row in records
                          if row['role'] == role and row['engine'] == engine and row['stage'] == stage and metric in row]
                if values:
                    if len(values) != 3:
                        raise AssertionError('Incomplete paired forks')
                    cells[role] = {'median': statistics.median(values), 'min': min(values), 'max': max(values), 'fork_medians': values}
            if cells:
                if len(cells) != 2:
                    raise AssertionError('Missing paired image')
                summary.append({'engine': engine, 'stage': stage, 'metric': metric, **cells,
                    'baseline_over_candidate': cells['baseline']['median'] / cells['candidate']['median'] if cells['candidate']['median'] else None})
report['summary'] = summary
save()
