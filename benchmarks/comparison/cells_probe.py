#!/usr/bin/env python3
"""Run the WS11 Myna cell probe: every route, three fresh forks each, one CPU, raw samples kept.

Each process measures one route of the same binary (benchmarks/comparison/cells/CellsProbe.java)
and prints one JSON record; its stderr carries the Truffle compilation trace and the engine's
compilation statistics (graph node counts and node-class histogram, including surviving invokes),
which are retained per process. Routes are interleaved per fork from a seeded order. Times are
ns per decoded message (row); memory is current-thread Java allocation per row.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import random
import re
import statistics
import subprocess

ROUTES = ('boundary', 'boundary-body', 'direct', 'cell', 'cell-interop')
SEED = 20261109


def summarize(records):
    summary = {}
    for route in ROUTES:
        forks = [r for r in records if r['route'] == route]
        if not forks:
            continue
        if not all(r.get('compiled') for r in forks):
            summary[route] = dict(compiled=False, failures=[r.get('failure', '') for r in forks])
            continue
        time = [statistics.median(r['ns_per_row']) for r in forks]
        memory = [statistics.median(r['java_bytes_per_row']) for r in forks]
        summary[route] = dict(
            compiled=True, forks=len(forks), checksum=forks[0]['checksum'],
            ns_per_row=dict(median=statistics.median(time), min=min(time), max=max(time), forks=time),
            java_bytes_per_row=dict(median=statistics.median(memory), min=min(memory), max=max(memory)),
            ast_nodes=forks[0]['ast_nodes'], graph=forks[0].get('graph'))
    return summary


TRACE = re.compile(r'opt done.*?\|AST\s+(\d+).*?\|IR\s+(\d+)/\s*(\d+).*?\|CodeSize\s+(\d+)', re.S)
# CompilationStatisticDetails prints the node-class histogram after the engine closes; `count` is
# the number of compilations, `sum` the number of nodes. Only the final (Graal tier) histogram is
# kept: it follows the "after Graal tier" heading.
HISTOGRAM = re.compile(r'^\s*(\S+)\s*:\s*count=\s*\d+,\s*sum=\s*(\d+)', re.M)


def graph_facts(stderr):
    """Graph facts of the probe root's compilation from the trace and statistics."""
    facts = {}
    traces = TRACE.findall(stderr)
    if traces:
        ast, pe, final, code = traces[-1]
        facts.update(ast_nodes=int(ast), graal_nodes_after_pe=int(pe),
                     graal_nodes_final=int(final), code_bytes=int(code), compilations=len(traces))
    final = stderr.rfind('Graal nodes after Graal tier')
    invokes = {}
    for name, total in HISTOGRAM.findall(stderr[final:] if final >= 0 else ''):
        if ('Invoke' in name or 'ForeignCall' in name or 'Allocat' in name
                or 'Deoptimize' in name) and int(total):
            invokes[name] = int(total)
    if invokes:
        facts['invoke_nodes'] = invokes
    return facts


def markdown(summary, meta):
    lines = ['# WS11 Myna cell probe', '',
             f"Binary sha256 `{meta['binary_sha256']}`, source `{meta['source']}`, CPU `{meta['cpu_model']}`, "
             f"affinity {meta['affinity']}, load before/after {meta['load_average']} / {meta['load_average_after']}.",
             '', 'ns per decoded message, median of per-fork medians (fork range); Java bytes per message; '
             'graph facts from the root\'s last compilation (AST nodes, Graal nodes after partial evaluation / final, '
             'code bytes, invoke-class node counts).', '',
             '| Route | ns/row | range | vs boundary | Java B/row | AST | IR after PE / final | code B | invokes |',
             '|---|---:|---|---:|---:|---:|---|---:|---|']
    base = summary.get('boundary', {}).get('ns_per_row', {}).get('median')
    for route in ROUTES:
        s = summary.get(route)
        if not s:
            continue
        if not s['compiled']:
            lines.append(f"| `{route}` | not compiled | | | | | | | {'; '.join(s['failures'])} |")
            continue
        t = s['ns_per_row']
        ratio = f"{t['median'] / base:.3f}×" if base else ''
        g = s.get('graph') or {}
        ir = f"{g.get('graal_nodes_after_pe', '')} / {g.get('graal_nodes_final', '')}"
        invokes = ', '.join(f'{k} {v}' for k, v in sorted((g.get('invoke_nodes') or {}).items()))
        lines.append(f"| `{route}` | {t['median']:.1f} | {t['min']:.1f}–{t['max']:.1f} | {ratio} | "
                     f"{s['java_bytes_per_row']['median']:.1f} | {s['ast_nodes']} | {ir} | {g.get('code_bytes', '')} | {invokes} |")
    return '\n'.join(lines) + '\n'


def run():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--binary', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--forks', type=int, default=3)
    parser.add_argument('--routes', default=','.join(ROUTES))
    parser.add_argument('--pin', action='store_true', help='Pin to the highest available CPU')
    parser.add_argument('--source', required=True)
    parser.add_argument('--seed', type=int, default=SEED,
                        help='Route-order seed; vary it between runs so a different route runs first')
    args = parser.parse_args()
    if args.forks < 3:
        parser.error('At least three fresh forks are required')
    routes = [r for r in args.routes.split(',') if r]
    unknown = set(routes) - set(ROUTES)
    if unknown:
        parser.error(f'Unknown routes {sorted(unknown)}')
    affinity = sorted(os.sched_getaffinity(0))
    if args.pin and len(affinity) > 1:
        os.sched_setaffinity(0, {affinity[-1]})
        affinity = sorted(os.sched_getaffinity(0))
    binary = args.binary.resolve(strict=True)
    args.output.mkdir(parents=True, exist_ok=False)
    logs = args.output / 'logs'
    logs.mkdir()
    cpu_info = Path('/proc/cpuinfo').read_text() if Path('/proc/cpuinfo').exists() else ''
    meta = dict(schema=1, source=args.source, seed=args.seed, forks=args.forks, routes=routes,
                binary_sha256=hashlib.sha256(binary.read_bytes()).hexdigest(),
                host=platform.uname()._asdict(), affinity=affinity, logical_cpus=os.cpu_count(),
                load_average=os.getloadavg(),
                cpu_model=next((line.split(':', 1)[1].strip() for line in cpu_info.splitlines()
                                if line.startswith('model name')), platform.processor()))
    rng = random.Random(args.seed)
    jobs = []
    for fork in range(args.forks):
        order = list(routes)
        rng.shuffle(order)
        jobs.extend((route, fork) for route in order)
    records = []
    for route, fork in jobs:
        result = subprocess.run([str(binary), '-Xmx1g', '-XX:+PrintGC', route],
                                capture_output=True, text=True, timeout=600, check=False)
        (logs / f'{route}-{fork}.stderr').write_text(result.stderr)
        (logs / f'{route}-{fork}.stdout').write_text(result.stdout)
        if result.returncode:
            raise RuntimeError(f'{route} fork {fork} failed ({result.returncode}):\n{result.stdout}\n{result.stderr}')
        lines = [json.loads(line) for line in result.stdout.splitlines() if line.startswith('{')]
        if len(lines) != 1 or lines[0].get('route') != route:
            raise ValueError(f'{route} fork {fork}: expected one result record')
        record = lines[0]
        record.update(fork=fork, graph=graph_facts(result.stderr))
        records.append(record)
        (args.output / 'partial.json').write_text(json.dumps(dict(meta, measurements=records), indent=2) + '\n')
        print(route, fork, record.get('ns_per_row', record.get('failure')), flush=True)
    checksums = {r['checksum'] for r in records if r.get('compiled')}
    if len(checksums) > 1:
        raise ValueError(f'Routes disagree on the decoded slabs: {checksums}')
    meta['load_average_after'] = os.getloadavg()
    report = dict(meta, measurements=records, summary=summarize(records))
    (args.output / 'partial.json').write_text(json.dumps(report, indent=2) + '\n')
    (args.output / 'partial.json').rename(args.output / 'results.json')
    (args.output / 'results.md').write_text(markdown(report['summary'], meta))
    print((args.output / 'results.md').read_text())


if __name__ == '__main__':
    run()
