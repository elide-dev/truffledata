"""Fresh-process SVM JSON comparisons; keep raw samples and fork identity."""
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

binary = Path(sys.argv[1]).resolve()
output = Path(os.environ['TEST_UNDECLARED_OUTPUTS_DIR'])
output.mkdir(parents=True, exist_ok=True)
# Workload per engine and its 32-document checksum (JsonComparison and JsonFixture).
WORKLOADS = {
    'json-scalar': (('gson-stream', 'simdjson-typed', 'simdjson-specialized'), 35984),
    'json-generic': (('gson-tree', 'simdjson-generic-java', 'simdjson-generic-interop', 'simdjson-snapshot',
                      'simdjson-session-java', 'simdjson-session-truffle',
                      'gson-stream-lines', 'simdjson-stream-java', 'simdjson-stream-interop',
                      # Benchmark-only competitors (JsonCompetitors; the JSON scoreboard).
                      'jackson-tree', 'jackson-stream', 'fastjson2-tree', 'dsljson-map', 'jsoniter-any',
                      'simdjson-java'), 38219),
}
STAGES = ('ingest', 'read-aot', 'interpret', 'peak')
# Borrowed session parses and streams (Task F2) retain nothing to read later: ingest cells only.
INGEST_ONLY = {'simdjson-session-java', 'simdjson-session-truffle',
               'gson-stream-lines', 'simdjson-stream-java', 'simdjson-stream-interop',
               'jackson-stream', 'simdjson-java'}
# Engines this image cannot run (simdjson-java needs 256-bit vector species: the x86-64-v3 image).
UNAVAILABLE = {engine for engines, _ in WORKLOADS.values() for engine in engines
               if subprocess.run([str(binary), '--available', engine]).returncode != 0}
WORKLOADS = {name: (tuple(e for e in engines if e not in UNAVAILABLE), checksum)
             for name, (engines, checksum) in WORKLOADS.items()}
workload_of = {engine: (name, checksum) for name, (engines, checksum) in WORKLOADS.items() for engine in engines}
jobs = [job for job in itertools.product(workload_of, STAGES, ('time', 'memory'), range(3))
        if job[0] not in INGEST_ONLY or job[1] == 'ingest']
random.Random(20261105).shuffle(jobs)
results = []
for engine, stage, mode, fork in jobs:
    process = subprocess.run([str(binary), engine, stage, mode], capture_output=True, text=True, check=True)
    lines = [line for line in process.stdout.splitlines() if line.startswith('{')]
    if len(lines) != 1:
        raise ValueError('Expected one measurement record')
    row = json.loads(lines[0])
    metric = 'ns_per_row' if mode == 'time' else 'allocated_bytes_per_row'
    workload, checksum = workload_of[engine]
    if (row['workload'], row['engine'], row['stage'], row['runtime'], row['checksum'], row['rows']) != (
            workload, engine, stage, 'svm', checksum, 32):
        raise ValueError('Mislabeled fixture')
    if row['guest_compiled'] != (stage == 'peak'):
        raise ValueError('Wrong execution tier')
    samples = row[metric]
    if len(samples) != 3 or any(not math.isfinite(v) or v < 0 or mode == 'time' and v == 0 for v in samples):
        raise ValueError('Invalid samples')
    row['fork'] = fork
    results.append(row)
    print(engine, stage, mode, fork, samples, flush=True)
report = {'schema': 1, 'binary_sha256': hashlib.sha256(binary.read_bytes()).hexdigest(),
    'unavailable': sorted(UNAVAILABLE),
    'platform': platform.platform(), 'cpu': Path('/proc/cpuinfo').read_text(), 'measurements': results}
(output / 'json-comparison.json').write_text(json.dumps(report, indent=2) + '\n')
lines = ['# SVM JSON comparison', '',
    'Medians of three fresh-process fork medians; ranges are fork medians. Java heap excludes native memory.']
for workload, (engines, _) in WORKLOADS.items():
    unit = 'record' if workload == 'json-scalar' else 'document'
    lines += ['', f'## {workload}', '',
        f'| Stage | Engine | ns/{unit} (range) | Java bytes/{unit} (range) |', '|---|---|---:|---:|']
    for stage in STAGES:
        for engine in engines:
            cells = []
            for metric in ('ns_per_row', 'allocated_bytes_per_row'):
                values = [statistics.median(r[metric]) for r in results if r['engine'] == engine and r['stage'] == stage and metric in r]
                if not values:
                    break
                cells.append(f'{statistics.median(values):.3f} ({min(values):.3f}–{max(values):.3f})')
            if len(cells) == 2:
                lines.append(f'| {stage} | {engine} | ' + ' | '.join(cells) + ' |')
(output / 'json-comparison.md').write_text('\n'.join(lines) + '\n')
