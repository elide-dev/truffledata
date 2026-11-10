"""Interleaved, same-worker SVM YAML comparisons of two images, three fresh forks per binary.

Usage: yaml_pair.py <candidate> <baseline>. Four shards, one per workload; every engine of the
shard runs on both binaries on one pinned CPU, in a seeded random order with the two binaries'
order shuffled per job. Checksums and record counts are derived here from the fixture formulas,
so both images must produce the fixture's exact result. Raw records carry binary identity.
"""
import hashlib
import json
import math
import os
from pathlib import Path
import platform
import random
import statistics
import subprocess
import sys
import time

WORKLOADS = ('yaml-config', 'yaml-records', 'yaml-flow', 'yaml-stream')
ENGINES = ('truffledata-yaml-java', 'truffledata-yaml-truffle', 'truffledata-yaml-interop',
           'truffledata-yaml-typed', 'truffledata-yaml-snapshot')
METRICS = ('ns_per_operation', 'allocated_bytes_per_operation')
FORKS = 3


def expected_checksum(workload):
    """YamlFixture's checksum for the workload (the formula yaml_measure.py checks)."""
    count = 8 if workload == 'yaml-config' else 1000
    return count, sum((1000 + 7 * i) + i + (i % 3 == 0) + 3 * i + i + (1 + i % 4) + (3 + (i & 1)) + 2
                      for i in range(count))


def summary(records, count):
    result = []
    for engine in ENGINES:
        entry = {'engine': engine}
        for role in ('candidate', 'baseline'):
            rows = [r for r in records if r['role'] == role and r['engine'] == engine]
            if not rows:
                continue
            if len(rows) != FORKS:
                raise AssertionError('Incomplete paired forks')
            cell = {}
            for metric in METRICS:
                values = [statistics.median(r[metric]) for r in rows]
                cell[metric] = {'median': statistics.median(values), 'min': min(values),
                                'max': max(values), 'fork_medians': values}
            cell['allocated_bytes_per_record'] = cell['allocated_bytes_per_operation']['median'] / count
            entry[role] = cell
        if 'candidate' in entry and 'baseline' in entry:
            entry['candidate_over_baseline'] = (
                entry['candidate']['ns_per_operation']['median']
                / entry['baseline']['ns_per_operation']['median'])
            result.append(entry)
    return result


def main(argv):
    binaries = {'candidate': Path(argv[0]).resolve(), 'baseline': Path(argv[1]).resolve()}
    shard = int(os.environ.get('TEST_SHARD_INDEX', '0'))
    shards = int(os.environ.get('TEST_TOTAL_SHARDS', '1'))
    if shards != len(WORKLOADS) or not 0 <= shard < len(WORKLOADS):
        raise ValueError('expected one shard per workload')
    if 'TEST_SHARD_STATUS_FILE' in os.environ:
        Path(os.environ['TEST_SHARD_STATUS_FILE']).touch()
    workload = WORKLOADS[shard]
    count, checksum = expected_checksum(workload)
    engines = [e for e in ENGINES if not (workload == 'yaml-stream' and e == 'truffledata-yaml-typed')]
    jobs = [(engine, fork) for engine in engines for fork in range(FORKS)]
    rng = random.Random(20261109 + shard)
    rng.shuffle(jobs)
    affinity = sorted(os.sched_getaffinity(0))
    measured_cpu = affinity[shard % len(affinity)]
    os.sched_setaffinity(0, {measured_cpu})
    output = Path(os.environ['TEST_UNDECLARED_OUTPUTS_DIR'])
    output.mkdir(parents=True, exist_ok=True)
    records = []
    report = {'schema': 'yaml-pair-1', 'runtime': 'svm', 'workload': workload, 'forks': FORKS,
              'complete': False, 'platform': platform.platform(), 'hostname': platform.node(),
              'cpu': Path('/proc/cpuinfo').read_text(), 'started_unix': time.time(),
              'original_cpu_affinity': affinity, 'measured_cpu': measured_cpu,
              'binary_sha256': {k: hashlib.sha256(p.read_bytes()).hexdigest()
                                for k, p in binaries.items()},
              'source': {k: os.environ.get('YAML_PAIR_' + k.upper() + '_REVISION', 'unspecified')
                         for k in binaries},
              'measurements': records}

    def save():
        (output / 'yaml-pair.json').write_text(json.dumps(report, indent=2) + '\n')

    save()
    for engine, fork in jobs:
        order = list(binaries)
        rng.shuffle(order)
        for role in order:
            command = [str(binaries[role]), '-Xms512m', '-Xmx512m', workload, engine]
            process = subprocess.run(command, capture_output=True, text=True)
            if process.returncode:
                (output / 'failure.json').write_text(json.dumps({
                    'command': command, 'role': role, 'fork': fork,
                    'stdout': process.stdout, 'stderr': process.stderr}, indent=2) + '\n')
                process.check_returncode()
            lines = [line for line in process.stdout.splitlines() if line.startswith('{')]
            if len(lines) != 1:
                raise AssertionError(f'{role} {engine}: expected one measurement record')
            row = json.loads(lines[0])
            observed = (row['runtime'], row['workload'], row['engine'], row['stage'],
                        row['checksum'], row['records'])
            if observed != ('svm', workload, engine, 'ingest', checksum, count):
                raise AssertionError('Mislabeled fixture: ' + repr(row))
            for metric in METRICS:
                samples = row[metric]
                if (len(samples) != 3 or any(not math.isfinite(v) or v < 0 for v in samples)
                        or metric.startswith('ns_') and 0 in samples):
                    raise AssertionError('Invalid samples: ' + repr(row))
            row.update(role=role, fork=fork, order=order)
            records.append(row)
            save()
            print(role, workload, engine, fork, row['ns_per_operation'], flush=True)
    report.update(complete=True, finished_unix=time.time(), summary=summary(records, count))
    save()
    print(json.dumps(report['summary'], indent=2))


if __name__ == '__main__':
    main(sys.argv[1:])
