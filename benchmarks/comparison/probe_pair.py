"""Interleaved, same-worker pairs of two single-record probe images, three fresh forks each.

Usage: probe_pair.py <probe-name> <candidate> <baseline>. Each binary prints one JSON record with
`probe`, `runtime`, `checksum`, three `ns_per_operation` samples and three
`allocated_bytes_per_operation` samples. Both run on one pinned CPU, in a seeded order shuffled per
fork; the checksum must be the same for every run of both binaries. Raw records carry binary
identity.
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

METRICS = ('ns_per_operation', 'allocated_bytes_per_operation')
FORKS = 3


def main(argv):
    probe = argv[0]
    binaries = {'candidate': Path(argv[1]).resolve(), 'baseline': Path(argv[2]).resolve()}
    affinity = sorted(os.sched_getaffinity(0))
    measured_cpu = affinity[0]
    os.sched_setaffinity(0, {measured_cpu})
    output = Path(os.environ['TEST_UNDECLARED_OUTPUTS_DIR'])
    output.mkdir(parents=True, exist_ok=True)
    records = []
    report = {'schema': 'probe-pair-1', 'probe': probe, 'forks': FORKS, 'complete': False,
              'platform': platform.platform(), 'hostname': platform.node(),
              'cpu': Path('/proc/cpuinfo').read_text(), 'started_unix': time.time(),
              'original_cpu_affinity': affinity, 'measured_cpu': measured_cpu,
              'binary_sha256': {k: hashlib.sha256(p.read_bytes()).hexdigest()
                                for k, p in binaries.items()},
              'source': os.environ.get('TRUFFLEDATA_BENCH_REVISION', 'unspecified'),
              'measurements': records}

    def save():
        (output / 'probe-pair.json').write_text(json.dumps(report, indent=2) + '\n')

    save()
    rng = random.Random(20261109)
    checksum = None
    for fork in range(FORKS):
        order = list(binaries)
        rng.shuffle(order)
        for role in order:
            process = subprocess.run([str(binaries[role])], capture_output=True, text=True)
            if process.returncode:
                (output / 'failure.json').write_text(json.dumps({
                    'role': role, 'fork': fork, 'stdout': process.stdout,
                    'stderr': process.stderr}, indent=2) + '\n')
                process.check_returncode()
            lines = [line for line in process.stdout.splitlines() if line.startswith('{')]
            if len(lines) != 1:
                raise AssertionError(f'{role}: expected one measurement record')
            row = json.loads(lines[0])
            if row.get('probe') != probe or row.get('runtime') != 'svm':
                raise AssertionError('Mislabeled probe: ' + repr(row))
            checksum = row['checksum'] if checksum is None else checksum
            if row['checksum'] != checksum:
                raise AssertionError('Checksum differs between runs or binaries: ' + repr(row))
            for metric in METRICS:
                samples = row[metric]
                if (len(samples) != 3 or any(not math.isfinite(v) or v < 0 for v in samples)
                        or metric.startswith('ns_') and 0 in samples):
                    raise AssertionError('Invalid samples: ' + repr(row))
            row.update(role=role, fork=fork, order=order)
            records.append(row)
            save()
            print(role, fork, row['ns_per_operation'], row['allocated_bytes_per_operation'], flush=True)
    summary = {}
    for role in binaries:
        rows = [r for r in records if r['role'] == role]
        summary[role] = {}
        for metric in METRICS:
            values = [statistics.median(r[metric]) for r in rows]
            summary[role][metric] = {'median': statistics.median(values), 'min': min(values),
                                     'max': max(values), 'fork_medians': values}
    summary['candidate_over_baseline'] = (summary['candidate']['ns_per_operation']['median']
                                          / summary['baseline']['ns_per_operation']['median'])
    report.update(complete=True, finished_unix=time.time(), summary=summary)
    save()
    print(json.dumps(summary, indent=2))


if __name__ == '__main__':
    main(sys.argv[1:])
