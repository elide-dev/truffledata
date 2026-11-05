#!/usr/bin/env python3
"""Summarize post-warmup JFR stacks; optional second argument selects the main class."""
import collections
import hashlib
import json
from pathlib import Path
import subprocess
import sys

root = Path(sys.argv[1])
main_class = sys.argv[2] if len(sys.argv) > 2 else 'dev.elide.truffledata.comparison.JsonComparison'
report = {'diagnostic_only': True, 'scope': 'Java-visible stacks after warmup; allocation samples are counts, not bytes; no native allocation accounting',
          'binary_sha256': (root / 'binary.sha256').read_text().split()[0], 'cells': {}}
for path in sorted(root.glob('*.jfr')):
    events = json.loads(subprocess.check_output(['jfr', 'print', '--json', '--stack-depth', '128',
        '--events', 'jdk.ExecutionSample,jdk.ObjectAllocationSample', str(path)]))['recording']['events']
    counts, leaf, inclusive, alloc = (collections.Counter() for _ in range(4))
    for event in events:
        kind, values = event['type'], event['values']
        frames = [f['method']['type']['name'].replace('/', '.') + '.' + f['method']['name']
                  for f in (values.get('stackTrace') or {}).get('frames', [])]
        counts[kind + ' total'] += 1
        if not any(main_class in f for f in frames):
            continue
        counts[kind + ' application'] += 1
        if kind == 'jdk.ExecutionSample':
            if frames: leaf[frames[0]] += 1
            inclusive.update(set(frames))
            if any('RecurringCallbackSupport' in f for f in frames): counts['safepoint_callback'] += 1
        else:
            alloc[(values.get('objectClass') or {}).get('name', 'unknown')] += 1
    report['cells'][path.stem] = {'recording_sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
        'counts': dict(counts), 'leaf': leaf.most_common(), 'inclusive': inclusive.most_common(),
        'allocation_sample_counts': alloc.most_common()}
print(json.dumps(report, indent=2))
