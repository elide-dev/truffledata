"""A3 writer diagnosis with fresh VMs, exact output parity and unfiltered raw samples."""
import itertools
import json
import math
import os
from pathlib import Path
import random
import re
import statistics
import subprocess
import sys

jvm, svm = map(lambda p: Path(p).resolve(), sys.argv[1:3])
output = Path(os.environ['TEST_UNDECLARED_OUTPUTS_DIR'])
output.mkdir(parents=True, exist_ok=True)
forks = int(os.environ.get('SLAB_PROBE_FORKS', '3'))
if forks < 1:
    raise ValueError('SLAB_PROBE_FORKS must be positive')
jobs = [('jvm', variant, fork) for variant in range(5) for fork in range(forks)]
jobs += [('svm', 0, fork) for fork in range(forks)]
random.Random(20261107).shuffle(jobs)
rows = []
digests = {}
for runtime, variant, fork in jobs:
    command = [str(svm), '-Xms512m', '-Xmx512m'] if runtime == 'svm' else [str(jvm), '--jvm_flag=-Dslab.writer.variant=' + str(variant)]
    process = subprocess.run(command, capture_output=True, text=True)
    (output / f'{runtime}-{variant}-{fork}.log').write_text(process.stdout + process.stderr)
    process.check_returncode()
    hashes = dict(re.findall(r'^WRITER_BYTES (\S+) ([0-9a-f]{64})$', process.stdout, re.M))
    if hashes.keys() != {'small', 'holdout', 'large'}:
        raise AssertionError('Missing writer byte parity')
    if digests and hashes != digests:
        raise AssertionError('Writer variants disagree on bytes')
    digests = hashes
    cells = [json.loads(line) for line in process.stdout.splitlines() if line.startswith('{')]
    expected = 15 if variant == 0 else 3
    if len(cells) != expected:
        raise AssertionError(f'Expected {expected} cells, got {len(cells)}')
    for cell in cells:
        if cell['runtime'] != runtime or cell['variant'] != variant:
            raise AssertionError('Mislabeled runtime/variant')
        for metric in ('ns_per_message', 'bytes_per_message'):
            values = cell[metric]
            if len(values) != 7 or any(not math.isfinite(v) or v < 0 or metric.startswith('ns') and v == 0 for v in values):
                raise AssertionError('Invalid samples')
        cell['fork'] = fork
        rows.append(cell)
    (output / 'slab-writer-partial.json').write_text(json.dumps(rows, indent=2) + '\n')
    print(runtime, variant, fork, 'complete', flush=True)
report = {'forks': forks, 'writer_sha256': digests, 'measurements': rows}
(output / 'slab-writer.json').write_text(json.dumps(report, indent=2) + '\n')
lines = ['# Java slab producer and writer diagnosis', '',
         f'{forks} fresh-process forks. Ratios use medians of fork medians; every raw sample is retained. Reused probe storage and fresh owning production storage are labeled separately.', '',
         '| Runtime | Mode | Variant | Case | ns/message | bytes/message | Within-fork max/min |', '|---|---|---:|---|---:|---:|---:|']
for runtime, mode, variant, case in sorted({(r['runtime'], r['mode'], r['variant'], r['case']) for r in rows}):
    cells = [r for r in rows if (r['runtime'], r['mode'], r['variant'], r['case']) == (runtime, mode, variant, case)]
    ns = statistics.median(statistics.median(r['ns_per_message']) for r in cells)
    allocated = statistics.median(statistics.median(r['bytes_per_message']) for r in cells)
    spread = max(max(r['ns_per_message']) / min(r['ns_per_message']) for r in cells)
    lines.append(f'| {runtime} | {mode} | {variant} | {case} | {ns:.2f} | {allocated:.2f} | {spread:.3f} |')
(output / 'slab-writer.md').write_text('\n'.join(lines) + '\n')
