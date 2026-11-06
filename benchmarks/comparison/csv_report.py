"""Summarize csv_measure shard reports into Markdown tables (no new measurements).

usage: python3 csv_report.py <report.json>... > csv-comparison.md

Throughput is GB/s = 1 / (ns per byte): the median over forks of each fork's median sample, with
the range of fork medians. Java allocation is bytes per data row (fork medians). Native rows are
the probes from the same worker as the Java cells of that group.
"""
import json
import statistics
import sys
from collections import defaultdict

WORKLOADS = ('index', 'scan', 'strings', 'typed', 'utf8')


def gbps(samples):
    return 1.0 / statistics.median(samples)


def main(paths):
    reports = [json.load(open(path)) for path in paths]
    cells = defaultdict(list)  # (binary, workload, dataset, bytes, engine) -> rows
    natives = defaultdict(list)
    sweep = defaultdict(list)
    reads = defaultdict(list)
    hosts = {}
    for report in reports:
        binary = report['binary']
        cpu = report['cpu']
        model = next((l.split(':', 1)[1].strip() for l in cpu.splitlines() if l.startswith('model name')), cpu[:60])
        hosts[(binary, tuple(report['group']))] = model
        for row in report['measurements']:
            if row['workload'] == 'typed-reads':
                reads[(binary, row['engine'], row['stage'])].append(row)
            elif report['group'][0] == 'sweep':
                sweep[(binary, row['workload'], row['dataset'], row['target'], row['engine'])].append(row)
            else:
                cells[(binary, row['workload'], row['dataset'], row['bytes'], row['engine'])].append(row)
        for row in report['native']:
            natives[(binary, row['workload'], row['dataset'], row['bytes'], row['engine'])].append(row)
    out = ['# CSV comparison', '',
           'GB/s is input bytes per nanosecond: the median of per-fork medians (range of fork medians).',
           'Java B/row is Java heap allocation per data row. Shared-host timings are advisory.', '']
    out += ['## Hosts', '', '| Binary | Group | CPU |', '|---|---|---|']
    for (binary, group), model in sorted(hosts.items()):
        out.append(f'| {binary} | {" ".join(map(str, group))} | {model} |')
    keys = sorted({(b, d, n) for (b, _, d, n, _) in cells}, key=lambda k: (k[0], k[2], k[1]))
    for binary, dataset, size in keys:
        out += ['', f'## {binary}: {dataset}, {size / 2**20:.1f} MiB', '',
                '| Workload | Engine | Kernel | GB/s (range) | Java B/row |', '|---|---|---|---:|---:|']
        for workload in WORKLOADS:
            rows = []
            for (b, w, d, n, engine), runs in cells.items():
                if (b, w, d, n) != (binary, workload, dataset, size):
                    continue
                forks = [gbps(r['ns_per_byte']) for r in runs]
                memory = [statistics.median(r['allocated_bytes_per_row']) for r in runs]
                rows.append((statistics.median(forks), engine, runs[0].get('kernel', ''),
                             f'{statistics.median(forks):.3f} ({min(forks):.3f}–{max(forks):.3f})',
                             f'{statistics.median(memory):.1f}'))
            for (b, w, d, n, engine), runs in natives.items():
                if (b, w, d) != (binary, workload, dataset) or abs(n - size) > 65536:
                    continue
                value = gbps(runs[0]['ns_per_byte'])
                rows.append((value, engine + ' (native)', runs[0].get('kernel', ''), f'{value:.3f}', '—'))
            for _, engine, kernel, rate, memory in sorted(rows, reverse=True):
                out.append(f'| {workload} | {engine} | {kernel} | {rate} | {memory} |')
    if sweep:
        sizes = sorted({k[3] for k in sweep})
        out += ['', '## Size sweep (kernel crossovers; GB/s, one fork)', '',
                '| Binary | Workload | Dataset | Engine | ' + ' | '.join(f'{s // 1024} KiB' for s in sizes) + ' |',
                '|---|---|---|---|' + '---:|' * len(sizes)]
        for binary, workload, dataset, engine in sorted({(k[0], k[1], k[2], k[4]) for k in sweep}):
            values = []
            for size in sizes:
                runs = sweep.get((binary, workload, dataset, size, engine), [])
                values.append(f'{gbps(runs[0]["ns_per_byte"]):.2f}' if runs else '—')
            out.append(f'| {binary} | {workload} | {dataset} | {engine} | ' + ' | '.join(values) + ' |')
    if reads:
        out += ['', '## Retained typed reads (32 numeric rows, 8 typed columns read per row)', '',
                '| Binary | Engine | Stage | ns/row (range) | Java B/row |', '|---|---|---|---:|---:|']
        for (binary, engine, stage), runs in sorted(reads.items()):
            forks = [statistics.median(r['ns_per_row']) for r in runs]
            memory = [statistics.median(r['allocated_bytes_per_row']) for r in runs]
            out.append(f'| {binary} | {engine} | {stage} | {statistics.median(forks):.2f} '
                       f'({min(forks):.2f}–{max(forks):.2f}) | {statistics.median(memory):.2f} |')
    print('\n'.join(out))


if __name__ == '__main__':
    main(sys.argv[1:])
