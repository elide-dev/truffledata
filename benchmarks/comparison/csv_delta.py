"""Before/after tables of the shipped native paths from two csv_measure runs (no new measurements).

usage: python3 csv_delta.py <before raw dir> <after raw dir> > delta.md

Absolute GB/s from different runs come from different shared hosts, so each row also gives the
shipped path as a ratio to references measured in the same shard of the same run: the reference
kernel consumer (kernel-native), the best Java competitor, and the Rust/C++ native probes. Ratios
are comparable across runs; absolute figures only within one run.
"""
import json
import statistics
import sys
from collections import defaultdict
from pathlib import Path

COMPETITORS = ('fastcsv', 'jackson', 'univocity', 'commons-csv')
RUNTIMES = {'csv-comparison': 'SVM (compat)', 'csv-comparison-v3': 'SVM (x86-64-v3)', 'csv_jvm': 'JVM'}
PATH = {'scan': 'truffledata-scanner-native', 'strings': 'truffledata-batch-native',
        'typed': 'truffledata-plan-native'}
NATIVE = {'scan': ('csv-core-scan', 'native-indexed-scan'), 'strings': ('csv-string-record',),
          'typed': ('csv-typed',)}


def load(directory):
    cells = defaultdict(lambda: defaultdict(list))
    natives = defaultdict(dict)
    for path in sorted(Path(directory).glob('*fixture*.json')):
        report = json.loads(path.read_text())
        runtime = RUNTIMES[report['binary']]
        size = report['group'][2]
        for row in report['measurements']:
            if row['workload'] in PATH:
                cells[(runtime, row['workload'], row['dataset'], size)][row['engine']].append(
                    1.0 / statistics.median(row['ns_per_byte']))
        for row in report['native']:
            samples = row['ns_per_byte'] if isinstance(row['ns_per_byte'], list) else [row['ns_per_byte']]
            natives[(runtime, row['workload'], row['dataset'], size)][row['engine']] = 1.0 / statistics.median(samples)
    return ({k: {e: statistics.median(v) for e, v in engines.items()} for k, engines in cells.items()}, natives)


def ratios(cells, natives, key):
    workload = key[1]
    engines = cells.get(key)
    if not engines or PATH[workload] not in engines:
        return None
    shipped = engines[PATH[workload]]
    comp = {e: v for e, v in engines.items() if e in COMPETITORS}
    best = max(comp, key=comp.get)
    kernel = engines.get('kernel-native')
    native = natives.get(key, {})
    return {
        'shipped': shipped,
        'kernel': shipped / kernel if kernel else None,
        'competitor': (shipped / comp[best], best),
        'natives': [shipped / native[r] if r in native else None for r in NATIVE[workload]],
    }


def main(before_dir, after_dir):
    before = load(before_dir)
    after = load(after_dir)
    fmt = lambda v: '—' if v is None else f'{v:.2f}'
    out = []
    for workload in ('scan', 'strings', 'typed'):
        refs = NATIVE[workload]
        out += ['', f'### {workload}: `{PATH[workload]}`', '',
                '| Runtime | Dataset | Size | GB/s before | GB/s after | × kernel before | × kernel after | '
                '× best Java before | × best Java after | '
                + ' | '.join(f'× {r} before | × {r} after' for r in refs) + ' |',
                '|---|---|---|' + '---:|' * (6 + 2 * len(refs))]
        keys = sorted({k for k in after[0] if k[1] == workload},
                      key=lambda k: (list(RUNTIMES.values()).index(k[0]), k[2], k[3]))
        for key in keys:
            b = ratios(*before, key)
            a = ratios(*after, key)
            if a is None:
                continue
            row = [key[0], key[2], '1 MiB' if key[3] == 1 << 20 else '128 MiB',
                   fmt(b and b['shipped']), f"**{fmt(a['shipped'])}**",
                   fmt(b and b['kernel']), f"**{fmt(a['kernel'])}**",
                   f"{fmt(b['competitor'][0])} ({b['competitor'][1]})" if b else '—',
                   f"**{fmt(a['competitor'][0])}** ({a['competitor'][1]})"]
            for i in range(len(refs)):
                row += [fmt(b and b['natives'][i]), f"**{fmt(a['natives'][i])}**"]
            out.append('| ' + ' | '.join(row) + ' |')
    print('\n'.join(out))


if __name__ == '__main__':
    main(*sys.argv[1:3])
