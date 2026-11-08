"""Before/after table from retained json_measure reports: medians of the three fork medians.

Usage: python3 pairs.py baseline.json [baseline2.json] candidate.json
"""
import json
import statistics
import sys


def cells(path):
    out = {}
    for row in json.load(open(path))['measurements']:
        metric = 'ns' if 'ns_per_row' in row else 'bytes'
        values = row['ns_per_row'] if metric == 'ns' else row['allocated_bytes_per_row']
        out.setdefault((row['workload'], row['engine'], row['stage'], metric), []).append(statistics.median(values))
    return {key: statistics.median(v) for key, v in out.items()}


*bases, candidate = sys.argv[1:]
before = [cells(p) for p in bases]
after = cells(candidate)
print('| Workload | Engine | Stage | ' + ' | '.join(f'before{i + 1} ns' for i in range(len(before)))
      + ' | after ns | after/before1 | ' + ' | '.join(f'before{i + 1} B' for i in range(len(before))) + ' | after B |')
print('|---|---|---|' + '---:|' * (2 * len(before) + 3))
keys = sorted({k[:3] for k in after})
for workload, engine, stage in keys:
    row = [workload, engine, stage]
    ns = [b.get((workload, engine, stage, 'ns')) for b in before]
    a = after.get((workload, engine, stage, 'ns'))
    row += [f'{v:,.1f}' if v is not None else '—' for v in ns]
    row.append(f'{a:,.1f}' if a is not None else '—')
    row.append(f'{a / ns[0]:.2f}' if a is not None and ns[0] else '—')
    by = [b.get((workload, engine, stage, 'bytes')) for b in before]
    ab = after.get((workload, engine, stage, 'bytes'))
    row += [f'{v:,.2f}' if v is not None else '—' for v in by]
    row.append(f'{ab:,.2f}' if ab is not None else '—')
    print('| ' + ' | '.join(row) + ' |')
