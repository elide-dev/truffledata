"""Interleaved, same-worker SVM CSV comparisons with three fresh forks per binary.

Usage: csv_pair.py <candidate> <baseline>. Eight shards cover the four fixtures at
1/128 MiB; shard zero also checks retained typed reads. CSV_PAIR_SHARDS and
CSV_PAIR_ENGINES may select a recorded subset (recorded explicitly in provenance).
Raw records include binary identity; these are not csv_measure schema-1 reports.
"""
import hashlib
import json
import math
import os
from pathlib import Path
import platform
import random
import re
import statistics
import subprocess
import sys
import time


DATASETS = ('numeric', 'mixed', 'quoted', 'escaped')
GROUPS = [(dataset, size) for size in (1 << 20, 128 << 20) for dataset in DATASETS]
ENGINES = {
    'strings': ('truffledata-batch', 'truffledata-batch-native',
                'truffledata-batch-owned', 'truffledata-batch-owned-native',
                'kernel-native', 'fastcsv', 'jackson'),
    'typed': ('truffledata-plan', 'truffledata-plan-native', 'kernel-native', 'fastcsv'),
}


def validate(row, cell, checks):
    workload, engine, dataset, size, stage = cell
    for key, expected in zip(('workload', 'engine', 'dataset', 'stage', 'runtime'),
                             (workload, engine, dataset, stage, 'svm')):
        if row.get(key) != expected:
            raise ValueError(f'{cell}: wrong {key}: {row.get(key)!r}')
    metric = 'ns_per_byte' if stage == 'ingest' else 'ns_per_row'
    for name in (metric, 'allocated_bytes_per_row'):
        values = row.get(name)
        expected_length = 5 if name == 'ns_per_byte' else 3
        if (not isinstance(values, list) or len(values) != expected_length
                or not all(isinstance(v, (int, float)) and math.isfinite(v) and v >= 0
                           for v in values)):
            raise ValueError(f'{cell}: invalid {name} samples')
    if not isinstance(row.get('checksum'), int) or row.get('rows', 0) <= 0:
        raise ValueError(f'{cell}: missing checksum/rows')
    if stage == 'ingest':
        if (row.get('target') != size or row.get('bytes', 0) <= 0
                or not re.fullmatch('[0-9a-f]{64}', row.get('sha256', ''))):
            raise ValueError(f'{cell}: invalid input provenance')
        observed = (row['checksum'], row['rows'], row['bytes'], row['sha256'])
    else:
        if row.get('guest_compiled') is not (stage == 'peak'):
            raise ValueError(f'{cell}: wrong execution tier')
        observed = (row['checksum'], row['rows'])
    key = (workload, dataset, size, stage)
    if checks.setdefault(key, observed) != observed:
        raise ValueError(f'{cell}: checksum/input differs between engines or binaries')


def summary(records):
    grouped = {}
    for row in records:
        key = (row['workload'], row['engine'], row['dataset'], row['stage'])
        grouped.setdefault(key, {}).setdefault(row['variant'], []).append(row)
    result = []
    for cell, variants in sorted(grouped.items()):
        entry = dict(zip(('workload', 'engine', 'dataset', 'stage'), cell))
        metric = 'ns_per_byte' if cell[-1] == 'ingest' else 'ns_per_row'
        entry['metric'] = metric
        for label, rows in variants.items():
            values = [statistics.median(row[metric]) for row in rows]
            memory = [statistics.median(row['allocated_bytes_per_row']) for row in rows]
            entry[label] = {'median': statistics.median(values), 'min': min(values),
                            'max': max(values), 'forks': len(rows),
                            'allocated_bytes_per_row': statistics.median(memory)}
        if 'baseline' in entry:
            entry['candidate_over_baseline'] = (
                entry['candidate']['median'] / entry['baseline']['median'])
        result.append(entry)
    return result


def main(argv):
    candidate, baseline = map(lambda p: Path(p).resolve(), argv)
    binaries = {'candidate': candidate, 'baseline': baseline}
    shard = int(os.environ.get('TEST_SHARD_INDEX', '0'))
    if int(os.environ.get('TEST_TOTAL_SHARDS', '8')) != 8 or not 0 <= shard < 8:
        raise ValueError('expected eight fixture shards')
    if 'TEST_SHARD_STATUS_FILE' in os.environ:
        Path(os.environ['TEST_SHARD_STATUS_FILE']).touch()
    selected = os.environ.get('CSV_PAIR_SHARDS', '')
    if selected and str(shard) not in selected.split(','):
        print('shard not selected')
        return
    dataset, size = GROUPS[shard]
    select = os.environ.get('CSV_PAIR_ENGINES', '')
    cells = [(w, e, dataset, size, 'ingest') for w, engines in ENGINES.items()
             if w != 'typed' or dataset in ('numeric', 'mixed') for e in engines]
    if shard == 0:
        cells += [('typed-reads', e, dataset, size, stage)
                  for e in ('truffledata-plan', 'batch-fastcsv')
                  for stage in ('read-aot', 'interpret', 'peak')]
    if select:
        cells = [cell for cell in cells if re.search(select, '/'.join(cell[:2]))]
    if not cells:
        raise ValueError('selection contains no cells')
    jobs = [(cell, fork) for cell in cells for fork in range(3)]
    rng = random.Random(20261108 + shard)
    rng.shuffle(jobs)
    affinity = sorted(os.sched_getaffinity(0))
    measured_cpu = affinity[shard % len(affinity)]
    os.sched_setaffinity(0, {measured_cpu})
    output = Path(os.environ['TEST_UNDECLARED_OUTPUTS_DIR'])
    output.mkdir(parents=True, exist_ok=True)
    report = {'schema': 'csv-pair-1', 'runtime': 'svm', 'group': [dataset, size],
              'platform': platform.platform(), 'hostname': platform.node(),
              'cpu': Path('/proc/cpuinfo').read_text(), 'started_unix': time.time(),
              'original_cpu_affinity': affinity, 'measured_cpu': measured_cpu,
              'selection': select, 'forks': 3,
              'binary_sha256': {k: hashlib.sha256(p.read_bytes()).hexdigest()
                                for k, p in binaries.items()},
              'source': {k: os.environ.get('CSV_PAIR_' + k.upper() + '_REVISION', 'unspecified')
                         for k in binaries}}
    records, checks = [], {}
    # Keep partially completed evidence if a later fork fails.
    (output / 'provenance.json').write_text(json.dumps(report, indent=2) + '\n')
    with (output / 'raw.jsonl').open('w') as raw:
        for cell, fork in jobs:
            labels = list(binaries)
            rng.shuffle(labels)
            for label in labels:
                process = subprocess.run([str(binaries[label]), *map(str, cell)],
                                         text=True, capture_output=True)
                if process.returncode:
                    raise RuntimeError(f'{label} {cell}: {process.stdout}\n{process.stderr}')
                lines = [line for line in process.stdout.splitlines() if line.startswith('{')]
                if len(lines) != 1:
                    raise ValueError(f'{label} {cell}: expected one measurement record')
                row = json.loads(lines[0])
                validate(row, cell, checks)
                row.update(variant=label, fork=fork)
                records.append(row)
                raw.write(json.dumps(row) + '\n')
                raw.flush()
                print(label, *cell, fork, flush=True)
    report.update(finished_unix=time.time(), measurements=records, summary=summary(records))
    (output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report['summary'], indent=2))


if __name__ == '__main__':
    main(sys.argv[1:])
