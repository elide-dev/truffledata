"""Randomized fresh-process YAML comparisons with raw samples and runtime identity."""
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

svm, jvm, library = map(lambda p: Path(p).resolve(), sys.argv[1:4])
output = Path(os.environ['TEST_UNDECLARED_OUTPUTS_DIR'])
output.mkdir(parents=True, exist_ok=True)
workloads = ('yaml-config', 'yaml-records', 'yaml-flow', 'yaml-stream')
ours = ('truffledata-yaml-java', 'truffledata-yaml-truffle', 'truffledata-yaml-interop', 'truffledata-yaml-typed', 'truffledata-yaml-snapshot')
foreign = ('snakeyaml-engine-load', 'snakeyaml-engine-events', 'snakeyaml-1.1-load', 'jackson-yaml-tree')
forks = int(os.environ.get('YAML_FORKS', '3'))
if forks < 1:
    raise ValueError('YAML_FORKS must be positive')
jobs = [(runtime, workload, engine, fork)
        for runtime in ('svm', 'jvm') for workload in workloads
        for engine in (ours if runtime == 'svm' else ours + foreign) for fork in range(forks)
        if not (workload == 'yaml-stream' and engine == 'truffledata-yaml-typed')]
jobs += [("svm", "yaml-flow", "truffledata-json-interop", fork) for fork in range(forks)]
random.Random(20261107).shuffle(jobs)
results = []
for runtime, workload, engine, fork in jobs:
    command = [str(svm), '-Xms512m', '-Xmx512m'] if runtime == 'svm' else [str(jvm), '--jvm_flag=-Dyaml.library=' + str(library)]
    process = subprocess.run(command + [workload, engine], capture_output=True, text=True)
    if process.returncode:
        (output / 'yaml-failure.log').write_text(process.stdout + process.stderr)
        process.check_returncode()
    records = [json.loads(line) for line in process.stdout.splitlines() if line.startswith('{')]
    if len(records) != 1:
        raise AssertionError('Expected one measurement record')
    row = records[0]
    count = 8 if workload == 'yaml-config' else 1000
    expected = sum((1000 + 7*i) + i + (i % 3 == 0) + 3*i + i + (1+i % 4) + (3+(i & 1)) + 2 for i in range(count))
    if engine == 'snakeyaml-engine-events':
        expected = (2 if workload == 'yaml-stream' else 9) + sum(29 + (1+i % 4) + (3+(i & 1)) + (2 if workload == 'yaml-stream' else 0) for i in range(count))
    if (row['runtime'], row['workload'], row['engine'], row['stage'], row['checksum'], row['records']) != (runtime, workload, engine, 'ingest', expected, count):
        raise AssertionError('Mislabeled fixture: ' + str(row))
    if workload == 'yaml-config' and row['input_bytes'] != 3800:
        raise AssertionError('Config byte size changed')
    for metric in ('ns_per_operation', 'allocated_bytes_per_operation'):
        samples = row[metric]
        if len(samples) != 3 or any(not math.isfinite(v) or v < 0 or metric.startswith('ns_') and v == 0 for v in samples):
            raise AssertionError('Invalid samples')
    row['fork'] = fork
    results.append(row)
    (output / 'yaml-comparison-partial.json').write_text(json.dumps(results, indent=2) + '\n')
    print(runtime, workload, engine, fork, row['ns_per_operation'], flush=True)
report = {'schema': 1, 'forks': forks, 'svm_sha256': hashlib.sha256(svm.read_bytes()).hexdigest(),
          'jvm_launcher_sha256': hashlib.sha256(jvm.read_bytes()).hexdigest(), 'library_sha256': hashlib.sha256(library.read_bytes()).hexdigest(),
          'platform': platform.platform(), 'cpu': Path('/proc/cpuinfo').read_text() if Path('/proc/cpuinfo').exists() else platform.processor(), 'measurements': results}
(output / 'yaml-comparison.json').write_text(json.dumps(report, indent=2) + '\n')
lines = ['# YAML comparison', '', f'Medians of {forks} fresh-process fork medians. Java allocation excludes native allocations; JVM and SVM rows are labeled separately.', '',
         '| Workload | Runtime | Engine | ns/operation | MB/s | Java bytes/record |', '|---|---|---|---:|---:|---:|']
for workload, runtime, engine in itertools.product(workloads, ('svm', 'jvm'), ours + foreign + ('truffledata-json-interop',)):
    rows = [r for r in results if (r['workload'], r['runtime'], r['engine']) == (workload, runtime, engine)]
    if not rows:
        continue
    ns = statistics.median(statistics.median(r['ns_per_operation']) for r in rows)
    allocated = statistics.median(statistics.median(r['allocated_bytes_per_operation']) for r in rows) / rows[0]['records']
    lines.append(f'| {workload} | {runtime} | {engine} | {ns:.2f} | {rows[0]["input_bytes"] * 1000 / ns:.2f} | {allocated:.2f} |')
(output / 'yaml-comparison.md').write_text('\n'.join(lines) + '\n')
