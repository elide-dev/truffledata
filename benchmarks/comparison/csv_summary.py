"""Shipped-path summary tables from csv_measure raw reports (no new measurements).

usage: python3 csv_summary.py <report.json>... > summary.md

Per (runtime, dataset, size) row, all from one shard: the shipped paths with the default kernel
(java-swar) and with the native provider, the same entry points with the index path off (the
library before the kernels), the best reference kernel path, the best Java competitor and the
native references. GB/s is the median of per-fork medians of 1 / (ns per byte).
"""
import json
import statistics
import sys
from collections import defaultdict

COMPETITORS = ('fastcsv', 'jackson', 'univocity', 'commons-csv')
RUNTIMES = {'csv-comparison': 'SVM (compat)', 'csv-comparison-v3': 'SVM (x86-64-v3)', 'csv_jvm': 'JVM'}
NATIVE = {'scan': ('csv-core-scan', 'native-indexed-scan'), 'strings': ('csv-string-record',),
          'typed': ('csv-typed',)}
PATH = {'scan': 'truffledata-scanner', 'strings': 'truffledata-batch', 'typed': 'truffledata-plan'}


def gbps(rows):
    return statistics.median(1.0 / statistics.median(r['ns_per_byte']) for r in rows)


def main(paths):
    cells = defaultdict(lambda: defaultdict(list))
    natives = defaultdict(dict)
    sweep = defaultdict(dict)
    for path in paths:
        report = json.load(open(path))
        runtime = RUNTIMES[report['binary']]
        for row in report['measurements']:
            if row['workload'] == 'typed-reads':
                continue
            if report['group'][0] == 'sweep':
                sweep[(runtime, row['workload'], row['dataset'], row['engine'])][row['target']] = gbps([row])
            else:
                cells[(runtime, row['workload'], row['dataset'], row['target'])][row['engine']].append(row)
        for row in report['native']:
            natives[(runtime, row['workload'], row['dataset'], report['group'][2])][row['engine']] = (
                1.0 / statistics.median(row['ns_per_byte']) if isinstance(row['ns_per_byte'], list)
                else 1.0 / row['ns_per_byte'])
    out = []
    for workload in ('scan', 'strings', 'typed'):
        refs = NATIVE[workload]
        out += ['', f'### {workload} (GB/s)', '',
                '| Runtime | Dataset | Size | Before (per-byte) | Shipped (java-swar) | Shipped (native) | '
                + ('Mapped (native) | ' if workload == 'strings' else '')
                + 'Best kernel path | Best Java competitor | ' + ' | '.join(refs) + ' |',
                '|---|---|---|' + '---:|' * (6 + len(refs) + (1 if workload == 'strings' else 0))]
        keys = sorted(k for k in cells if k[1] == workload)
        for runtime, _, dataset, size in sorted(keys, key=lambda k: (list(RUNTIMES.values()).index(k[0]), k[2], k[3])):
            engines = {e: gbps(rows) for e, rows in cells[(runtime, workload, dataset, size)].items()}
            p = PATH[workload]
            kernels = {e: v for e, v in engines.items() if e.startswith('kernel-')}
            best_kernel = max(kernels, key=kernels.get) if kernels else None
            comp = {e: v for e, v in engines.items() if e in COMPETITORS}
            best_comp = max(comp, key=comp.get)
            native = natives.get((runtime, workload, dataset, size), {})
            fmt = lambda v: '—' if v is None else f'{v:.2f}'
            cols = [runtime, dataset, '1 MiB' if size == 1 << 20 else '128 MiB',
                    fmt(engines.get(p + '-bytes')), fmt(engines.get(p)), f'**{fmt(engines.get(p + "-native"))}**']
            if workload == 'strings':
                cols.append(fmt(engines.get('truffledata-mapped-native')))
            cols += [f'{fmt(kernels[best_kernel])} ({best_kernel})' if best_kernel else '—',
                     f'{fmt(comp[best_comp])} ({best_comp})'] + [fmt(native.get(r)) for r in refs]
            out.append('| ' + ' | '.join(cols) + ' |')
    if sweep:
        sizes = sorted({s for v in sweep.values() for s in v})
        out += ['', '### Size sweep (GB/s, one fork)', '',
                '| Runtime | Workload | Dataset | Engine | '
                + ' | '.join(f'{s // 1024} KiB' if s >= 1024 else f'{s} B' for s in sizes) + ' |',
                '|---|---|---|---|' + '---:|' * len(sizes)]
        for (runtime, workload, dataset, engine), values in sorted(sweep.items()):
            out.append(f'| {runtime} | {workload} | {dataset} | {engine} | '
                       + ' | '.join(f'{values[s]:.2f}' if s in values else '—' for s in sizes) + ' |')
    print('\n'.join(out))


if __name__ == '__main__':
    main(sys.argv[1:])
