"""Fresh-process CSV comparison measurements; keeps raw samples, fork identity and host identity.

One Bazel shard runs one group, so every comparison inside a group shares a worker:
shards 0-3 are the 1 MiB fixtures, 4-7 the 128 MiB fixtures (numeric, mixed, quoted, escaped),
and shard 8 is the size sweep for kernel crossovers. Every fixture group also runs the native
probes (csv/native kernels from C++, the Rust csv crate) on the same bytes, checked by SHA-256.

usage: csv_measure.py <svm|jvm> <comparison binary> <fixture binary> <csv_probe> <csv_probe_rs>

Probe runs (never a recorded result) may narrow a run with --test_env: CSV_MEASURE_ENGINES (a regex
on workload/engine), CSV_MEASURE_SHARDS (comma-separated shard indices) and CSV_MEASURE_FORKS.
"""
import hashlib
import json
import math
import os
from pathlib import Path
import platform
import random
import subprocess
import sys

runtime, binary, fixture_binary, probe, probe_rs = sys.argv[1:6]
binary = str(Path(binary).resolve())
output = Path(os.environ['TEST_UNDECLARED_OUTPUTS_DIR'])
output.mkdir(parents=True, exist_ok=True)
temp = Path(os.environ.get('TEST_TMPDIR', '/tmp'))
if 'TEST_SHARD_STATUS_FILE' in os.environ:
    Path(os.environ['TEST_SHARD_STATUS_FILE']).touch()
shard = int(os.environ.get('TEST_SHARD_INDEX', '0'))
shards = int(os.environ.get('TEST_TOTAL_SHARDS', '1'))

DATASETS = ('numeric', 'mixed', 'quoted', 'escaped')
SMALL, LARGE = 1 << 20, 128 << 20
# truffledata-<path>: the shipped entry points with the default kernel (java-swar), -native with
# the native provider installed, -bytes with the index path off (the library before the kernels).
ENGINES = {
    'index': ('index-scalar', 'index-table', 'index-swar', 'index-vector', 'index-native',
              'index-native-small', 'index-native-swar', 'index-native-avx2', 'index-native-avx512'),
    'scan': ('truffledata-scanner', 'truffledata-scanner-native', 'truffledata-scanner-bytes',
             'kernel-swar', 'kernel-vector', 'kernel-native', 'kernel-native-small', 'fastcsv',
             'jackson'),
    'strings': ('truffledata-batch', 'truffledata-batch-native', 'truffledata-batch-bytes',
                'truffledata-mapped', 'truffledata-mapped-native', 'kernel-swar', 'kernel-native',
                'fastcsv', 'univocity', 'commons-csv', 'jackson'),
    'typed': ('truffledata-plan', 'truffledata-plan-native', 'truffledata-plan-bytes', 'kernel-swar',
              'kernel-native', 'fastcsv', 'univocity', 'commons-csv', 'jackson'),
}
# Down to 256 B: the index-path threshold (sweep-* force the index path at every size).
SWEEP_SIZES = (256, 1 << 10, 4 << 10, 16 << 10, 64 << 10, 256 << 10, 1 << 20, 4 << 20, 16 << 20)
SWEEP = {
    'index': ('index-swar', 'index-native', 'index-native-small'),
    'scan': ('truffledata-scanner-bytes', 'sweep-swar', 'sweep-native', 'kernel-native-small'),
    'strings': ('truffledata-batch-bytes', 'sweep-batch-swar', 'sweep-batch-native'),
}
GROUPS = [('fixture', d, SMALL) for d in DATASETS] + [('fixture', d, LARGE) for d in DATASETS] + [('sweep',)]
if shards != len(GROUPS):
    raise ValueError(f'expected {len(GROUPS)} shards, got {shards}')
group = GROUPS[shard]
SELECT = os.environ.get('CSV_MEASURE_ENGINES', '')
SHARDS = os.environ.get('CSV_MEASURE_SHARDS', '')
if SHARDS and str(shard) not in SHARDS.split(','):
    print('shard not selected')
    sys.exit(0)

jobs = []
forks = int(os.environ.get('CSV_MEASURE_FORKS', '2'))
if group[0] == 'fixture':
    _, dataset, size = group
    for workload, engines in ENGINES.items():
        if workload == 'typed' and dataset not in ('numeric', 'mixed'):
            continue
        for engine in engines:
            for fork in range(forks):
                jobs.append((workload, engine, dataset, size, 'ingest', fork))
    if dataset == 'numeric' and size == SMALL:
        for engine in ('truffledata-plan', 'batch-fastcsv'):
            for stage in ('read-aot', 'interpret', 'peak'):
                for fork in range(forks):
                    jobs.append(('typed-reads', engine, dataset, size, stage, fork))
else:
    for dataset in ('numeric', 'mixed'):
        for size in SWEEP_SIZES:
            for workload, engines in SWEEP.items():
                for engine in engines:
                    jobs.append((workload, engine, dataset, size, 'ingest', 0))
if SELECT:
    import re
    jobs = [job for job in jobs if re.search(SELECT, f'{job[0]}/{job[1]}')]
random.Random(20261106 + shard).shuffle(jobs)


def command(*args):
    if runtime == 'jvm':
        return [binary, '--jvm_flag=-Xss16m', *args]
    return [binary, *args]


results, unavailable = [], []
for workload, engine, dataset, size, stage, fork in jobs:
    process = subprocess.run(command(workload, engine, dataset, str(size), stage),
                             capture_output=True, text=True)
    if process.returncode != 0:
        if ('CSV native kernel' in process.stderr and 'native' in engine) or (
                'vector' in engine and 'VectorIndexer' in process.stderr) or (
                'CSV engine unavailable' in process.stderr):
            unavailable.append({'workload': workload, 'engine': engine, 'reason': process.stderr[-400:]})
            print('unavailable', workload, engine, flush=True)
            continue
        print(process.stdout, process.stderr, file=sys.stderr)
        raise RuntimeError(f'{workload} {engine} {dataset} {size} {stage} failed')
    lines = [line for line in process.stdout.splitlines() if line.startswith('{')]
    if len(lines) != 1:
        raise ValueError('Expected one measurement record')
    row = json.loads(lines[0])
    if (row['workload'], row['engine'], row['dataset'], row['stage'], row['runtime']) != (
            workload, engine, dataset, stage, runtime):
        raise ValueError('Mislabeled cell')
    for metric in ('ns_per_byte', 'ns_per_row', 'allocated_bytes_per_row'):
        if metric in row and any(not math.isfinite(v) or v < 0 for v in row[metric]):
            raise ValueError('Invalid samples')
    if stage != 'ingest' and row['guest_compiled'] != (stage == 'peak'):
        raise ValueError('Wrong execution tier')
    row['fork'] = fork
    results.append(row)
    print(workload, engine, dataset, size, stage, fork, row.get('ns_per_byte', row.get('ns_per_row')), flush=True)

# Checksums must agree within a workload, dataset and size (the index workload counts entries).
checks = {}
for row in results:
    if row['workload'] in ('typed-reads',):
        continue
    key = (row['workload'], row['dataset'], row['bytes'])
    if checks.setdefault(key, row['checksum']) != row['checksum']:
        raise ValueError(f'Checksum mismatch in {key}: {row["engine"]}')

natives = []
if group[0] == 'fixture':
    _, dataset, size = group
    path = temp / f'{dataset}-{size}.csv'
    written = subprocess.run([fixture_binary, '--fixture', dataset, str(size), str(path)],
                             capture_output=True, text=True, check=True)
    digest = json.loads(written.stdout.strip().splitlines()[-1])['sha256']
    java_digests = {row['sha256'] for row in results if row.get('sha256')}
    if java_digests != {digest}:
        raise ValueError(f'Fixture digests differ: {java_digests} vs {digest}')
    for tool in (probe, probe_rs):
        run = subprocess.run([tool, dataset, str(path)], capture_output=True, text=True, check=True)
        for line in run.stdout.splitlines():
            if line.startswith('{'):
                row = json.loads(line)
                row['sha256'] = digest
                key = (row['workload'], row['dataset'], row['bytes'])
                if row['workload'] in ('scan', 'typed') and key in checks and checks[key] != row['checksum']:
                    raise ValueError(f'Native checksum mismatch in {key}: {row["engine"]}')
                natives.append(row)
                print('native', row['engine'], row['ns_per_byte'], flush=True)
    path.unlink()

report = {
    'schema': 1,
    'runtime': runtime,
    'binary': Path(binary).name,
    'group': list(group),
    'binary_sha256': hashlib.sha256(Path(binary).read_bytes()).hexdigest() if runtime == 'svm' else None,
    'platform': platform.platform(),
    'cpu': Path('/proc/cpuinfo').read_text() if Path('/proc/cpuinfo').exists() else platform.processor(),
    'measurements': results,
    'native': natives,
    'unavailable': unavailable,
}
name = '-'.join(str(part) for part in group)
(output / f'csv-{Path(binary).name}-{name}.json').write_text(json.dumps(report, indent=1) + '\n')
print(f'{len(results)} cells, {len(natives)} native rows, {len(unavailable)} unavailable')
