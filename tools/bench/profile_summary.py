#!/usr/bin/env python3
"""Summarize optional SVM JFR diagnostics; never substitute samples for benchmark scores."""
import argparse
import collections
import hashlib
import json
from pathlib import Path
import subprocess


def summarize(events):
    leaf, inclusive, allocation, counts = (collections.Counter() for _ in range(4))
    for event in events:
        kind, values = event['type'], event['values']
        if kind not in ('jdk.ExecutionSample', 'jdk.ObjectAllocationSample'):
            continue
        frames = []
        for frame in (values.get('stackTrace') or {}).get('frames', []):
            method = frame['method']
            frames.append(method['type']['name'].replace('/', '.') + '.' + method['name'])
        counts[kind + ' total'] += 1
        if 'dev.elide.truffledata.comparison.Comparison$Fixture.ingest' not in frames:
            continue
        counts[kind + ' ingestion'] += 1
        if kind == 'jdk.ExecutionSample':
            if frames:
                leaf[frames[0]] += 1
            inclusive.update(set(frames))
            if any('RecurringCallbackSupport' in frame for frame in frames):
                counts['safepoint_callback_ingestion'] += 1
        else:
            allocation[(values.get('objectClass') or {}).get('name', 'unknown')] += 1
    selected = counts['jdk.ExecutionSample ingestion']
    return {'counts': dict(counts), 'safepoint_dominated': selected > 0 and
            counts['safepoint_callback_ingestion'] / selected > .9,
            'leaf': leaf.most_common(), 'inclusive': inclusive.most_common(),
            'allocation_samples': allocation.most_common()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('recordings', type=Path)
    parser.add_argument('--workload', default='proto-mixed')
    args = parser.parse_args()
    root = args.recordings
    engines = ('stock', 'static') if args.workload.endswith('int4') else (
        ('stock', 'stock-aliased', 'owned', 'buffa') if args.workload == 'proto-bytes'
        else ('stock', 'owned', 'buffa'))
    report = {'schema': 1, 'diagnostic_only': True, 'workload': args.workload,
              'gc': (root / 'gc.txt').read_text().strip(),
              'revision': (root / 'revision.txt').read_text().strip(),
              'binary_sha256': (root / 'binary.sha256').read_text().split()[0],
              'scope': 'Java-visible ingestion stacks; sampled allocation counts are not bytes; '
                       'absent allocation samples do not imply zero allocation',
              'engines': {}}
    for engine in engines:
        events, recordings = [], []
        for fork in range(3):
            path = root / f'{args.workload}-{engine}-{fork}.jfr'
            output = subprocess.check_output(['jfr', 'print', '--json', '--stack-depth', '128',
                '--events', 'jdk.ExecutionSample,jdk.ObjectAllocationSample', str(path)])
            events.extend(json.loads(output)['recording']['events'])
            recordings.append({'name': path.name, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
        report['engines'][engine] = {'recordings': recordings, **summarize(events)}
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
