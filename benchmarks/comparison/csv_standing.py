"""Where the shipped paths stand against every competitor and native reference (no new measurements).

usage: python3 csv_standing.py <raw dir> > standing.md

For each workload and runtime, the shipped path (native kernel and the java-swar default) as a
ratio to each Java competitor, the reference kernel consumer and each Rust/C++ probe of the same
shard: the number of rows where the shipped path is ahead, and the range of ratios. GB/s are
medians of fork medians (csv_delta.load); ratios only ever compare cells of one shard.
"""
import sys

from csv_delta import COMPETITORS, NATIVE, PATH, RUNTIMES, load

DEFAULT = {'scan': 'truffledata-scanner', 'strings': 'truffledata-batch', 'typed': 'truffledata-plan'}


def main(directory):
    cells, natives = load(directory)
    out = ['Each cell: rows ahead / rows, then the min–max ratio (shipped GB/s ÷ reference GB/s, '
           'same shard). Rows are dataset × size (1 MiB, 128 MiB).']
    for workload in ('scan', 'strings', 'typed'):
        refs = list(COMPETITORS) + ['kernel-native'] + list(NATIVE[workload])
        out += ['', f'### {workload}', '',
                '| Runtime | Shipped path | ' + ' | '.join(f'vs {r}' for r in refs) + ' |',
                '|---|---|' + '---|' * len(refs)]
        for runtime in RUNTIMES.values():
            keys = sorted(k for k in cells if k[0] == runtime and k[1] == workload)
            for path in (PATH[workload], DEFAULT[workload]):
                row = [runtime, f'`{path}`']
                for ref in refs:
                    rs = []
                    for key in keys:
                        mine = cells[key].get(path)
                        other = cells[key].get(ref, natives.get(key, {}).get(ref))
                        if mine and other:
                            rs.append(mine / other)
                    row.append(f'{sum(r > 1 for r in rs)}/{len(rs)} ({min(rs):.2f}–{max(rs):.2f})'
                               if rs else '—')
                out.append('| ' + ' | '.join(row) + ' |')
    print('\n'.join(out))


if __name__ == '__main__':
    main(sys.argv[1])
