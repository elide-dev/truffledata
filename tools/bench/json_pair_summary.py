#!/usr/bin/env python3
"""Summarize preserved `json-typed-pair.json` shard reports into one paired table.

Each report is one `//benchmarks/comparison:json_typed_pair` shard: a complete paired group of
engines and stages, three fresh forks of each image per cell on one pinned CPU. The table
keeps every group separate (different shards may run on different hosts) and never averages
across workloads. Cells that a group does not measure (an ingest-only engine's read tiers) are
left out instead of failing, so a subset of shards can be summarized (plan
2026-11-08-beat-stock-svm P0.4: `JSON_PAIR_GROUPS` selects shards).

Per cell: median of fork medians and the fork-median range for both images, the before/after
time ratio, and whether the two fork-median ranges are disjoint (a shape fact about the samples,
not a confidence interval). Instruction counts never come from this tool.

Usage: json_pair_summary.py REPORT.json ... [--markdown OUT] [--json OUT]
"""

import argparse
import json
import statistics
import sys

METRICS = ('ns_per_row', 'allocated_bytes_per_row')


def cells(reports):
    """Paired cells from the shards' raw measurement rows, in shard/engine/stage order."""
    if not reports:
        raise ValueError('No reports')
    if any(not report.get('complete') for report in reports):
        raise ValueError('Incomplete paired run')
    if len({json.dumps(report['binary_sha256'], sort_keys=True) for report in reports}) != 1:
        raise ValueError('Different image pairs')
    if len({report['shard'] for report in reports}) != len(reports):
        raise ValueError('Duplicate shard')
    out = []
    for report in sorted(reports, key=lambda report: report['shard']):
        rows = report['measurements']
        for engine in report['group']:
            for stage in report['stages']:
                values = {}
                for role in ('baseline', 'candidate'):
                    for metric in METRICS:
                        cell = [statistics.median(row[metric]) for row in rows
                                if row['engine'] == engine and row['stage'] == stage
                                and row['role'] == role and metric in row]
                        if cell:
                            if len(cell) != 3:
                                raise ValueError(f'Incomplete cell {engine} {stage} {role} {metric}')
                            values[role, metric] = cell
                if not values:
                    continue  # the group does not measure this stage for this engine
                unpaired = engine in report.get('candidate_only', ())
                if len(values) != (1 if unpaired else 2) * len(METRICS):
                    raise ValueError(f'Missing paired image for {engine} {stage}')
                workload = next(row['workload'] for row in rows if row['engine'] == engine)
                after = values['candidate', 'ns_per_row']
                cell = {'shard': report['shard'], 'workload': workload, 'engine': engine, 'stage': stage,
                        'candidate': summary(after),
                        'candidate_bytes': summary(values['candidate', 'allocated_bytes_per_row'])}
                if not unpaired:
                    before = values['baseline', 'ns_per_row']
                    cell.update({
                        'baseline': summary(before),
                        'baseline_bytes': summary(values['baseline', 'allocated_bytes_per_row']),
                        'baseline_over_candidate': statistics.median(before) / statistics.median(after),
                        'disjoint': max(before) < min(after) or max(after) < min(before)})
                out.append(cell)
    return out


def summary(fork_medians):
    return {'median': statistics.median(fork_medians), 'min': min(fork_medians),
            'max': max(fork_medians), 'fork_medians': list(fork_medians)}


def display(cell, digits=3):
    return f'{cell["median"]:.{digits}f} ({cell["min"]:.{digits}f}–{cell["max"]:.{digits}f})'


def markdown(paired):
    lines = ['# Paired JSON SVM results', '',
             'Medians of three fresh-process fork medians; parentheses are fork-median ranges. '
             'Every before/after cell shares its host and pinned CPU; different shards may use '
             'different hosts. "Disjoint" says the two fork-median ranges do not overlap '
             '(a sample fact, not a confidence interval).', '',
             '| Shard | Workload | Engine | Stage | Baseline ns/doc | Candidate ns/doc | '
             'Before/after | Disjoint | Baseline Java B/doc | Candidate Java B/doc |',
             '|---:|---|---|---|---:|---:|---:|---|---:|---:|']
    for cell in paired:
        paired_cell = 'baseline' in cell
        lines.append(f'| {cell["shard"]} | {cell["workload"]} | {cell["engine"]} | {cell["stage"]} | '
                     f'{display(cell["baseline"]) if paired_cell else "(candidate only)"} | '
                     f'{display(cell["candidate"])} | '
                     + (f'{cell["baseline_over_candidate"]:.3f}x | {"yes" if cell["disjoint"] else "no"} | '
                        f'{display(cell["baseline_bytes"])} | ' if paired_cell else '— | — | — | ')
                     + f'{display(cell["candidate_bytes"])} |')
    return '\n'.join(lines) + '\n'


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('reports', nargs='+')
    parser.add_argument('--markdown')
    parser.add_argument('--json')
    args = parser.parse_args(argv)
    reports = []
    for name in args.reports:
        with open(name) as handle:
            reports.append(json.load(handle))
    paired = cells(reports)
    text = markdown(paired)
    if args.markdown:
        with open(args.markdown, 'w') as handle:
            handle.write(text)
    if args.json:
        with open(args.json, 'w') as handle:
            json.dump(paired, handle, indent=2)
            handle.write('\n')
    if not args.markdown:
        sys.stdout.write(text)
    return 0


if __name__ == '__main__':
    sys.exit(main())
