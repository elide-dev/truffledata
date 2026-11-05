"""Fresh-process SVM scalar JSON comparisons; keep raw samples and fork identity."""
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
jobs = list(itertools.product(('gson-stream', 'simdjson-typed'),
    ('ingest', 'read-aot', 'interpret', 'peak'), ('time', 'memory'), range(3)))
random.Random(20261105).shuffle(jobs)
results = []
for engine, stage, mode, fork in jobs:
    process = subprocess.run([str(binary), engine, stage, mode], capture_output=True, text=True, check=True)
    lines = [line for line in process.stdout.splitlines() if line.startswith('{')]
    if len(lines) != 1:
        raise ValueError('Expected one measurement record')
    row = json.loads(lines[0])
    metric = 'ns_per_row' if mode == 'time' else 'allocated_bytes_per_row'
    if (row['engine'], row['stage'], row['runtime'], row['checksum'], row['rows']) != (engine, stage, 'svm', 35984, 32):
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
    'platform': platform.platform(), 'cpu': Path('/proc/cpuinfo').read_text(), 'measurements': results}
(output / 'json-comparison.json').write_text(json.dumps(report, indent=2) + '\n')
lines = ['# SVM scalar JSON comparison', '',
    'Medians of three fresh-process fork medians; ranges are fork medians. Java heap excludes native memory.', '',
    '| Stage | Engine | ns/record (range) | Java bytes/record (range) |', '|---|---|---:|---:|']
for stage in ('ingest', 'read-aot', 'interpret', 'peak'):
    for engine in ('gson-stream', 'simdjson-typed'):
        cells = []
        for metric in ('ns_per_row', 'allocated_bytes_per_row'):
            values = [statistics.median(r[metric]) for r in results if r['engine'] == engine and r['stage'] == stage and metric in r]
            cells.append(f'{statistics.median(values):.3f} ({min(values):.3f}–{max(values):.3f})')
        lines.append(f'| {stage} | {engine} | ' + ' | '.join(cells) + ' |')
(output / 'json-comparison.md').write_text('\n'.join(lines) + '\n')
