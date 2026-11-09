"""Summarize six preserved json-typed-pair reports without combining different workloads."""
import json
from pathlib import Path
import statistics
import sys

reports = [json.loads(Path(name).read_text()) for name in sys.argv[1:]]
if len(reports) != 6 or {r['shard'] for r in reports} != set(range(6)):
    raise ValueError('Need exactly one complete report from each of six shards')
if any(not r['complete'] for r in reports):
    raise ValueError('Incomplete paired run')
if len({json.dumps(r['binary_sha256'], sort_keys=True) for r in reports}) != 1:
    raise ValueError('Different image pairs')
rows = [row for report in reports for row in report['measurements']]
lines = ['# Paired JSON SVM results', '',
         'Medians of three fresh-process fork medians; parentheses are fork-median ranges. '
         'Every before/after cell shares its host and pinned CPU. Different workload groups may use different hosts.', '',
         '| Workload | Engine | Stage | Baseline ns/doc | Candidate ns/doc | Before/after | Baseline Java B/doc | Candidate Java B/doc |',
         '|---|---|---|---:|---:|---:|---:|---:|']
for report in sorted(reports, key=lambda report: report['shard']):
    for engine in report['group']:
        for stage in report['stages']:
            values = {}
            for role in ('baseline', 'candidate'):
                for metric in ('ns_per_row', 'allocated_bytes_per_row'):
                    cell = [statistics.median(row[metric]) for row in rows
                            if row['engine'] == engine and row['stage'] == stage
                            and row['role'] == role and metric in row]
                    if len(cell) != 3:
                        raise ValueError('Incomplete cell')
                    values[role, metric] = cell
            def display(role, metric):
                cell = values[role, metric]
                return f'{statistics.median(cell):.3f} ({min(cell):.3f}–{max(cell):.3f})'
            ratio = statistics.median(values['baseline', 'ns_per_row']) / statistics.median(values['candidate', 'ns_per_row'])
            workload = next(row['workload'] for row in rows if row['engine'] == engine)
            lines.append(f'| {workload} | {engine} | {stage} | {display("baseline", "ns_per_row")} | '
                         f'{display("candidate", "ns_per_row")} | {ratio:.3f}x | '
                         f'{display("baseline", "allocated_bytes_per_row")} | {display("candidate", "allocated_bytes_per_row")} |')
print('\n'.join(lines))
