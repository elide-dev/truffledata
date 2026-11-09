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
def numeric_checksum(i):
    """JsonFixture.numericChecksum(i), derived independently from the generator's formulas."""
    mask = (1 << 64) - 1
    points = 160 + 4 * (i % 8)
    state = 0x9E3779B97F4A7C15 ^ (i + 1)
    micros = []
    for _ in range(points - 1):
        state = (state * 6364136223846793005 + 1442695040888963407) & mask
        lon = -141_000_000 + (state >> 11) % 89_000_000
        state = (state * 6364136223846793005 + 1442695040888963407) & mask
        lat = 41_000_000 + (state >> 11) % 43_000_000
        micros += [lon, lat]
    micros += micros[:2]
    return i + points + micros[0] + micros[1] + micros[-2] + micros[-1]


NUMERIC = ('gson-numeric', 'simdjson-numeric-interop', 'simdjson-numeric-decode', 'jackson-numeric',
           'fastjson2-numeric')
NUMERIC_CHECKSUM = sum(numeric_checksum(i) for i in range(32))
structural = ['simdjson-typed-' + kind for kind in ('nested', 'repeated', 'mixed', 'churn')]
checksums = {}
for mode, engine in enumerate(structural):
    total = 0
    for i in range(32):
        count = 0 if mode == 0 else 16 if mode == 1 else 4 if mode == 2 else i % 9
        point = lambda n: 2 * n + 2 + (1 if n % 2 == 0 else 0)
        total += i + point(100 + i)
        total += sum(point(1000 + i * 17 + j) + j - i for j in range(count))
    checksums[engine] = total
workloads = {engine: 'json-' + engine.removeprefix('simdjson-') for engine in structural}
flat = ('simdjson-typed', 'simdjson-specialized', 'gson-stream')
generic = ('simdjson-generic-interop', 'gson-tree', 'jackson-tree', 'fastjson2-tree',
           'dsljson-map', 'jsoniter-any')
# Retained generic forms (plan 2026-11-08-beat-stock-svm P0.4/WS7) beside the generic interop cell
# on the same host: the snapshot cell has every stage; the lent stream root is ingest-only.
retained = ('simdjson-snapshot', 'simdjson-stream-interop', 'simdjson-generic-interop')
for names, workload, checksum in ((flat, 'json-scalar', 35984), (generic, 'json-generic', 38219),
                                  (retained, 'json-generic', 38219)):
    for engine in names:
        workloads[engine] = workload
        checksums[engine] = checksum
# Decode-only (P0.3): session decode and close, no read; one per decoded document.
workloads['simdjson-generic-decode'] = 'json-generic'
checksums['simdjson-generic-decode'] = 32
# Numeric-heavy fixture (WS8), ingest only; decode-only counts one per decoded document.
for engine in NUMERIC:
    workloads[engine] = 'json-numeric'
    checksums[engine] = 32 if engine == 'simdjson-numeric-decode' else NUMERIC_CHECKSUM
ALL_STAGES = ('ingest', 'read-aot', 'interpret', 'peak')
# One complete paired group per shard, each engine with its stages: the four structural typed
# workloads, the flat and generic controls (ingest/peak), the retained generic forms, and the flat
# typed read tiers (WS6).
groups = [{engine: ALL_STAGES} for engine in structural] + [
    {engine: ('ingest', 'peak') for engine in flat},
    {engine: ('ingest', 'peak') for engine in generic},
    {'simdjson-snapshot': ALL_STAGES, 'simdjson-stream-interop': ('ingest',),
     'simdjson-generic-interop': ('ingest', 'peak'), 'simdjson-generic-decode': ('ingest',)},
    {engine: ('read-aot', 'interpret') for engine in flat},
    # WS8: the numeric family beside the mixed generic ingest cells as same-host controls.
    {**{engine: ('ingest',) for engine in NUMERIC},
     'simdjson-generic-interop': ('ingest',), 'simdjson-generic-decode': ('ingest',)}]
if shards != len(groups) or not 0 <= shard < len(groups):
    raise ValueError('expected nine complete paired workload groups')
group = groups[shard]
engines = tuple(group)
stages = tuple(dict.fromkeys(stage for stages in group.values() for stage in stages))
# JSON_PAIR_GROUPS=6,7 runs only those shards (the others exit without a report), so a
# workstream can pair the cells it changes without re-measuring every group.
selected = os.environ.get('JSON_PAIR_GROUPS')
if selected and str(shard) not in selected.split(','):
    print('shard', shard, 'not selected by JSON_PAIR_GROUPS', flush=True)
    sys.exit(0)
# JSON_PAIR_CANDIDATE_ONLY names engines the baseline image does not have (a new harness cell):
# they run on the candidate alone and are reported unpaired, never as a ratio.
candidate_only = set(filter(None, os.environ.get('JSON_PAIR_CANDIDATE_ONLY', '').split(',')))
jobs = [(engine, stage, mode, fork) for engine in engines for stage in group[engine]
        for mode in ('time', 'memory') for fork in range(3)]
rng = random.Random(2026110801 + shard)
rng.shuffle(jobs)
records = []
report = {
    'schema': 1, 'complete': False, 'source': os.environ.get('TRUFFLEDATA_BENCH_REVISION', 'unrecorded'),
    'baseline_source': os.environ.get('TRUFFLEDATA_BENCH_BASELINE_REVISION', 'unrecorded'),
    'platform': platform.platform(), 'hostname': platform.node(), 'cpu': Path('/proc/cpuinfo').read_text(),
    'affinity': sorted(os.sched_getaffinity(0)), 'shard': shard, 'shards': shards,
    'binary_sha256': {name: hashlib.sha256(path.read_bytes()).hexdigest() for name, path in binary.items()},
    # Both images must be built from the identical fixture commit, with only production differing.
    # The checksums below independently derive from this fixture's exact 32-document formulas.
    'fixture_source_sha256': hashlib.sha256(Path('benchmarks/comparison/JsonComparison.java').read_bytes()).hexdigest(),
    'generic_fixture_source_sha256': hashlib.sha256(Path('benchmarks/comparison/JsonFixture.java').read_bytes()).hexdigest(),
    'group': list(engines), 'stages': list(stages), 'engine_stages': {e: list(v) for e, v in group.items()},
    'candidate_only': sorted(candidate_only),
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
    order = ['candidate'] if engine in candidate_only else ['candidate', 'baseline']
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
                workloads[engine], engine, stage, 'svm', checksums[engine], 32):
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
    for stage in stages:
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
                if len(cells) != (1 if engine in candidate_only else 2):
                    raise AssertionError('Missing paired image')
                summary.append({'workload': workloads[engine], 'engine': engine, 'stage': stage, 'metric': metric, **cells,
                    'baseline_over_candidate': cells['baseline']['median'] / cells['candidate']['median']
                    if 'baseline' in cells and cells['candidate']['median'] else None})
report['summary'] = summary
save()
