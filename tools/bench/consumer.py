#!/usr/bin/env python3
"""Run serialized, interleaved SVM consumer comparisons and retain validated samples."""
import argparse
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

ENGINES = ('stock', 'generated-owned', 'native-view')
STAGES = ('resident', 'heap', 'detach', 'first', 'interpret', 'peak')
CELLS = [(e, m, s) for e in ENGINES for m in ('sparse', 'full') for s in STAGES]
CELLS += [(e, 'holdout', s) for e in ENGINES for s in ('resident', 'peak')]
SEED = 20261105


def validate(record, cell, metric, checksums):
    """Reject wrong work, tiers or counters before a sample can enter a comparison."""
    engine, mode, stage = cell
    expected = dict(zip(('engine', 'mode', 'stage'), cell))
    expected.update(runtime='svm', rows=32, guest_compiled=stage != 'interpret',
                    shared_access=engine != 'stock', stock_boundary=engine == 'stock')
    for key, value in expected.items():
        if record.get(key) != value:
            raise ValueError(f'{cell}: expected {key}={value!r}, got {record.get(key)!r}')
    checksum = record.get('checksum')
    if not isinstance(checksum, int) or (mode in checksums and checksums[mode] != checksum):
        raise ValueError(f'{cell}: inconsistent consumer checksum')
    key = 'ns_per_row' if metric == 'time' else 'java_bytes_per_row'
    values = record.get(key, [])
    if len(values) != 3 or not all(isinstance(v, (float, int)) and math.isfinite(v)
                                   and v >= 0 for v in values):
        raise ValueError(f'{cell}: invalid {key} samples')
    for name in ('input_bytes_per_row', 'copied_input_bytes_per_row',
                 'decoded_native_index_capacity_bytes_per_row',
                 'decoded_native_result_header_bytes_per_row'):
        value = record.get(name)
        if not isinstance(value, (float, int)) or not math.isfinite(value) or value < 0:
            raise ValueError(f'{cell}: invalid memory observation {name}')
    checksums[mode] = checksum


def markdown(rows, labels):
    lines = ['# Consumer comparison', '',
             'Fresh-process fork medians; ranges are not confidence intervals. Java allocation '
             'excludes native storage. Stock guest projection has an explicit boundary. Native '
             'transient allocation peaks are not measured.', '',
             '| Binary | Mode | Stage | Engine | ns/row (fork range) | Java B/row |',
             '|---|---|---|---|---:|---:|']
    for label in labels:
        for mode, stage in dict.fromkeys((m, s) for _, m, s in CELLS):
            for engine in ENGINES:
                chosen = [r for r in rows if (r['binary'], r['engine'], r['mode'], r['stage'])
                          == (label, engine, mode, stage)]
                times = [statistics.median(r['ns_per_row']) for r in chosen if r['metric'] == 'time']
                memory = [statistics.median(r['java_bytes_per_row']) for r in chosen
                          if r['metric'] == 'memory']
                lines.append(f'| {label} | {mode} | {stage} | {engine} | '
                             f'{statistics.median(times):.3f} ({min(times):.3f}–{max(times):.3f}) | '
                             f'{statistics.median(memory):.3f} |')
    return '\n'.join(lines) + '\n'


def run():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--binary', action='append', required=True, help='label=/absolute/executable')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--forks', type=int, default=3)
    parser.add_argument('--source', required=True, help='Source commit plus saved patch hash(es)')
    args = parser.parse_args()
    binaries = {}
    for spec in args.binary:
        label, path = spec.split('=', 1)
        if not label or label in binaries:
            parser.error('Binary labels must be nonempty and unique')
        binaries[label] = Path(path).resolve(strict=True)
    if args.forks < 3:
        parser.error('At least three fresh forks are required')
    affinity = sorted(os.sched_getaffinity(0))
    if len(affinity) != 1:
        parser.error('Pin the runner to one CPU with taskset')
    args.output.mkdir(parents=True, exist_ok=False)
    cpu_info = Path('/proc/cpuinfo').read_text()
    report = dict(schema=1, source=args.source, seed=SEED, forks=args.forks,
                  binary_sha256={k: hashlib.sha256(v.read_bytes()).hexdigest()
                                 for k, v in binaries.items()},
                  host=platform.uname()._asdict(), affinity=affinity,
                  cpu_model=next(line.split(':', 1)[1].strip() for line in cpu_info.splitlines()
                                 if line.startswith('model name')), measurements=[])
    jobs = list(itertools.product(binaries, CELLS, range(args.forks), ('time', 'memory')))
    random.Random(SEED).shuffle(jobs)
    checksums = {}
    for label, cell, fork, metric in jobs:
        result = subprocess.run([str(binaries[label]), '-Xmx1g', *cell, metric],
                                capture_output=True, text=True, timeout=90, check=False)
        if result.returncode:
            raise RuntimeError(f'{label} {cell} failed:\n{result.stdout}\n{result.stderr}')
        records = [json.loads(line) for line in result.stdout.splitlines() if line.startswith('{')]
        if len(records) != 1:
            raise ValueError(f'{label} {cell}: expected one result record')
        record = records[0]
        validate(record, cell, metric, checksums)
        record.update(binary=label, fork=fork, metric=metric)
        report['measurements'].append(record)
        # Preserve completed cells even if a later process fails; never summarize a partial run.
        (args.output / 'partial.json').write_text(json.dumps(report, indent=2) + '\n')
        print(label, *cell, fork, metric, flush=True)
    (args.output / 'partial.json').rename(args.output / 'results.json')
    (args.output / 'results.md').write_text(markdown(report['measurements'], binaries))


if __name__ == '__main__':
    run()
