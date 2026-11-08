#!/usr/bin/env python3
"""Run serialized, interleaved consumer comparisons and retain validated samples.

Every binary measures every cell assigned to this process, so a before/after pair is always
measured on one host (or one remote worker). Bazel test sharding partitions whole (mode, stage)
groups; all engines and binaries of one group stay in one shard.
"""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import platform
import random
import statistics
import subprocess

LEGACY_ENGINES = ('stock', 'generated-owned', 'native-view', 'native-eager')
ENGINES = ('stock', 'stock-interop', 'generated-owned', 'slab', 'slab-native', 'slab-projected',
           'slab-lazy', 'slab-session', 'slab-session-projected')
KNOWN_ENGINES = tuple(dict.fromkeys((*ENGINES, *LEGACY_ENGINES)))
STAGES = ('decode', 'resident', 'heap', 'detach', 'first', 'interpret', 'peak')
MODE_STAGES = (('sparse', STAGES), ('full', STAGES), ('holdout', ('resident', 'peak')),
               ('holdout-sparse', ('resident', 'peak')), ('large', ('decode', 'resident', 'peak')))
MODES = tuple(mode for mode, _ in MODE_STAGES)
GROUPS = [(m, s) for m, stages in MODE_STAGES for s in stages]


def supported(engine, stage, mode):
    if engine.startswith('slab-session') and stage not in ('decode', 'resident'):
        return False
    if engine == 'stock-interop' and stage not in ('interpret', 'peak'):
        return False
    return engine not in ('slab-projected', 'slab-session-projected') or mode.endswith('sparse')


CELLS = [(e, m, s) for m, s in GROUPS for e in ENGINES if supported(e, s, m)]
SEED = 20261105
# HotSpot needs a longer host warmup than a closed-world image before samples are meaningful.
JVM_PROPERTIES = ('-Dconsumer.warmupMillis=1500', '-Dconsumer.sampleMillis=100')


def validate(record, cell, metric, checksums, runtime='svm'):
    """Reject wrong work, tiers or counters before a sample can enter a comparison."""
    engine, mode, stage = cell
    guest = stage != 'decode'
    expected = dict(zip(('engine', 'mode', 'stage'), cell))
    expected.update(runtime=runtime, rows=32,
                    guest_compiled=stage not in ('interpret', 'decode'),
                    shared_access=engine != 'stock' and guest,
                    stock_boundary=engine == 'stock' and guest,
                    native_index_retained=engine == 'native-view')
    for key, value in expected.items():
        if record.get(key) != value:
            raise ValueError(f'{cell}: expected {key}={value!r}, got {record.get(key)!r}')
    checksum = record.get('checksum')
    if not isinstance(checksum, int) or (mode in checksums and checksums[mode] != checksum):
        raise ValueError(f'{cell}: inconsistent consumer checksum')
    key = 'ns_per_row' if metric == 'time' else 'java_bytes_per_row'
    values = record.get(key, [])
    if len(values) != 3 or not all(isinstance(v, (float, int)) and math.isfinite(v)
                                   and v >= 0 for v in values):
        raise ValueError(f'{cell}: invalid {key} samples')
    for name in ('input_bytes_per_row', 'copied_input_bytes_per_row',
                 'decoded_native_index_capacity_bytes_per_row',
                 'decoded_native_result_header_bytes_per_row'):
        value = record.get(name)
        if not isinstance(value, (float, int)) or not math.isfinite(value) or value < 0:
            raise ValueError(f'{cell}: invalid memory observation {name}')
    if engine.startswith('slab') and not engine.startswith('slab-session'):
        extent = record.get('slab_bytes_per_row')
        retained = record.get('retained_slab_capacity_bytes_per_row')
        if (not isinstance(extent, (float, int)) or not math.isfinite(extent) or extent <= 0
                or not isinstance(retained, (float, int)) or not math.isfinite(retained)
                or retained < extent):
            raise ValueError(f'{cell}: invalid slab extent/capacity accounting')
    if engine == 'slab-native':
        if record.get('producer_backend') != ('JVM_FFM' if runtime == 'jvm' else 'SVM_STATIC'):
            raise ValueError(f'{cell}: explicit native producer must identify its backend')
        if record.get('native_input') != ('heap' if stage == 'heap' else 'native'):
            raise ValueError(f'{cell}: explicit native producer must identify input ownership')
    if engine.startswith('slab-session'):
        extent = record.get('slab_bytes_per_row')
        capacity = record.get('session_arena_capacity_bytes_per_row')
        if (not isinstance(extent, (float, int)) or not math.isfinite(extent) or extent <= 0
                or not isinstance(capacity, (float, int)) or not math.isfinite(capacity)
                or capacity < extent or record.get('retained_slab_capacity_bytes_per_row') != 0):
            raise ValueError(f'{cell}: invalid native session arena accounting')
    if engine == 'stock-interop':
        if record.get('host_interop') is not True:
            raise ValueError(f'{cell}: stock interop must execute host messages')
        if record.get('host_interop_boundary') is not (runtime == 'svm'):
            raise ValueError(f'{cell}: host reflection boundary must identify its runtime')
    if engine in ('slab-projected', 'slab-lazy', 'slab-session-projected'):
        retained_input = record.get('retained_input_bytes_per_row')
        if (not isinstance(retained_input, (float, int)) or not math.isfinite(retained_input)
                or retained_input < 0):
            raise ValueError(f'{cell}: invalid retained immutable input accounting')
    checksums[mode] = checksum


def throughput(input_bytes, ns):
    """Decimal MB/s of wire input per row; 1,000 MB/s is 1 GB/s."""
    return input_bytes / ns * 1000 if ns > 0 else float('nan')


def select_engines(labels, specifications):
    """Each binary declares its supported engines; historical baselines can omit newer ones."""
    selected = {label: ENGINES for label in labels}
    for specification in specifications:
        label, raw = specification.split('=', 1)
        engines = tuple(raw.split(','))
        if label not in selected or not engines or len(set(engines)) != len(engines):
            raise ValueError('Engine selection requires a known binary and unique engines')
        if any(engine not in KNOWN_ENGINES for engine in engines):
            raise ValueError('Unknown consumer engine')
        selected[label] = engines
    return selected


def select_modes(labels, specifications):
    """Historical binaries explicitly omit fixture modes added after they were built."""
    selected = {label: MODES for label in labels}
    for specification in specifications:
        label, raw = specification.split('=', 1)
        modes = tuple(raw.split(','))
        if (label not in selected or not modes or len(set(modes)) != len(modes)
                or any(mode not in MODES for mode in modes)):
            raise ValueError('Mode selection requires a known binary and unique known modes')
        selected[label] = modes
    return selected


def select_groups(specification):
    if specification is None:
        return GROUPS
    groups = [tuple(group.split(':')) for group in specification.split(',')]
    if len(set(groups)) != len(groups) or any(group not in GROUPS for group in groups):
        raise ValueError('Groups must be unique known mode:stage pairs')
    return groups


def summarize(rows, labels):
    """Median of fork medians per (binary, engine, mode, stage), with observed fork ranges."""
    summary = []
    for label in labels:
        for mode, stage in GROUPS:
            for engine in KNOWN_ENGINES:
                chosen = [r for r in rows if (r['binary'], r['engine'], r['mode'], r['stage'])
                          == (label, engine, mode, stage)]
                times = [statistics.median(r['ns_per_row']) for r in chosen
                         if r['metric'] == 'time']
                memory = [statistics.median(r['java_bytes_per_row']) for r in chosen
                          if r['metric'] == 'memory']
                if not times or not memory:
                    continue
                first = chosen[0]
                summary.append(dict(
                    binary=label, engine=engine, mode=mode, stage=stage,
                    ns_per_row=statistics.median(times), ns_min=min(times), ns_max=max(times),
                    java_bytes_per_row=statistics.median(memory),
                    producer_backend=first.get('producer_backend', 'historical-unreported'),
                    native_input=first.get('native_input', ''),
                    input_bytes_per_row=first['input_bytes_per_row'],
                    native_index_bytes_per_row=first['decoded_native_index_capacity_bytes_per_row'],
                    native_header_bytes_per_row=first['decoded_native_result_header_bytes_per_row'],
                    slab_bytes_per_row=first.get('slab_bytes_per_row', 0),
                    retained_slab_capacity_bytes_per_row=first.get('retained_slab_capacity_bytes_per_row', 0),
                    session_arena_capacity_bytes_per_row=first.get('session_arena_capacity_bytes_per_row', 0),
                    retained_input_bytes_per_row=first.get('retained_input_bytes_per_row', 0),
                    mb_per_s=throughput(first['input_bytes_per_row'], statistics.median(times))))
    return summary


def projection_gates(summary):
    """Compare E1 cells measured by one unsharded, interleaved three-fork invocation."""
    values = {(r['engine'], r['mode'], r['stage']): r['ns_per_row']
              for r in summary if r['binary'] == 'candidate'}
    comparisons = (
        ('selected-small', ('slab-projected', 'sparse', 'resident'),
         ('slab', 'sparse', 'resident'), 0.85),
        ('selected-holdout', ('slab-projected', 'holdout-sparse', 'resident'),
         ('slab', 'holdout', 'resident'), 0.5),
        ('lazy-holdout', ('slab-lazy', 'holdout-sparse', 'resident'),
         ('slab', 'holdout', 'resident'), 0.5),
        ('session-lazy-holdout', ('slab-session-projected', 'holdout-sparse', 'resident'),
         ('slab-session', 'holdout', 'resident'), 0.5),
    )
    results = []
    for name, numerator, denominator, ceiling in comparisons:
        if numerator not in values or denominator not in values or values[denominator] <= 0:
            raise ValueError(f'Missing or invalid E1 measurement: {name}')
        ratio = values[numerator] / values[denominator]
        results.append(dict(name=name, numerator=numerator, denominator=denominator,
                            ratio=ratio, ceiling=ceiling, passed=ratio <= ceiling,
                            required=name != 'session-lazy-holdout'))
    return results


def markdown(rows, labels, runtime='svm'):
    summary = summarize(rows, labels)
    stock = {(s['binary'], s['mode'], s['stage']): s['ns_per_row'] for s in summary
             if s['engine'] == 'stock'}
    lines = [f'# Consumer comparison ({runtime})', '',
             'Fresh-process fork medians; ranges are not confidence intervals. Java allocation '
             'excludes native storage, reported separately as arena, retained input and slab capacity. Stock '
             'guest projection has an explicit boundary. MB/s divides wire input bytes by the '
             'whole cell (decode cells: decode and release only). Native transient allocation '
             'peaks are not measured.', '']
    index = {(s['binary'], s['engine'], s['mode'], s['stage']): s for s in summary}
    for label in labels:
        lines += [f'## {label}: ns/row (× stock)', '',
                  '| Mode | Stage | ' + ' | '.join(KNOWN_ENGINES) + ' |',
                  '|---|---|' + '---:|' * len(KNOWN_ENGINES)]
        for mode, stage in GROUPS:
            base = stock.get((label, mode, stage))
            cells = []
            for engine in KNOWN_ENGINES:
                s = index.get((label, engine, mode, stage))
                if s is None:
                    cells.append('—')
                elif engine == 'stock' or not base:
                    cells.append(f"{s['ns_per_row']:.1f}")
                else:
                    cells.append(f"{s['ns_per_row']:.1f} ({s['ns_per_row'] / base:.2f}×)")
            lines.append(f'| {mode} | {stage} | ' + ' | '.join(cells) + ' |')
        lines.append('')
    lines += ['## All cells', '',
              '| Binary | Mode | Stage | Engine | ns/row (fork range) | × stock | MB/s | '
              'Java B/row | Native index+header B/row | Slab extent / heap capacity B/row | Native arena B/row | Retained input B/row |',
              '|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|']
    for s in summary:
        base = stock.get((s['binary'], s['mode'], s['stage']))
        ratio = f"{s['ns_per_row'] / base:.2f}" if base else '—'
        native = s['native_index_bytes_per_row'] + s['native_header_bytes_per_row']
        lines.append(f"| {s['binary']} | {s['mode']} | {s['stage']} | {s['engine']} | "
                     f"{s['ns_per_row']:.3f} ({s['ns_min']:.3f}–{s['ns_max']:.3f}) | {ratio} | "
                     f"{s['mb_per_s']:.1f} | {s['java_bytes_per_row']:.2f} | {native:.1f} | "
                     f"{s['slab_bytes_per_row']:.1f} / {s['retained_slab_capacity_bytes_per_row']:.1f} | "
                     f"{s['session_arena_capacity_bytes_per_row']:.1f} | {s['retained_input_bytes_per_row']:.1f} |")
    return '\n'.join(lines) + '\n'


def shard(groups):
    """Assign whole (mode, stage) groups round-robin to this Bazel test shard."""
    total = int(os.environ.get('TEST_TOTAL_SHARDS', '1'))
    index = int(os.environ.get('TEST_SHARD_INDEX', '0'))
    status = os.environ.get('TEST_SHARD_STATUS_FILE')
    if status:
        Path(status).touch()
    return [g for i, g in enumerate(groups) if i % total == index]


def command(binary, runtime, cell, metric):
    if runtime == 'svm':
        return [str(binary), '-Xmx1g', *cell, metric]
    # java_binary launcher: JVM options travel as --jvm_flag; Serial GC and heap match SVM.
    flags = ['--jvm_flag=-Xmx1g', '--jvm_flag=-XX:+UseSerialGC',
             *(f'--jvm_flag={p}' for p in JVM_PROPERTIES)]
    return [str(binary), *flags, *cell, metric]


def merge(argv):
    """Combine shard outputs (each a results.json directory) into one validated report."""
    parser = argparse.ArgumentParser(description='Merge consumer shard results')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('shards', type=Path, nargs='+')
    args = parser.parse_args(argv)
    reports = [json.loads((s if s.is_file() else s / 'results.json').read_text())
               for s in args.shards]
    runtime = reports[0]['runtime']
    labels = list(reports[0]['binary_sha256']) or ['candidate']
    seen = set()
    measurements = []
    for report in reports:
        if report['runtime'] != runtime or list(report['binary_sha256']) != labels:
            raise ValueError('Shards measured different runtimes or binaries')
        for group in report['groups']:
            if tuple(group) in seen:
                raise ValueError(f'Duplicate shard group {group}')
            seen.add(tuple(group))
        measurements += report['measurements']
    labels = list(dict.fromkeys(m['binary'] for m in measurements))
    merged = dict(schema=2, runtime=runtime, source=reports[0]['source'], seed=SEED,
                  binary_sha256=reports[0]['binary_sha256'],
                  shards=[{k: r[k] for k in ('shard', 'groups', 'host', 'cpu_model', 'affinity',
                                             'logical_cpus', 'load_average',
                                             'load_average_after') if k in r}
                          for r in reports],
                  complete=seen == set(GROUPS), measurements=measurements,
                  summary=summarize(measurements, labels))
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / 'results.json').write_text(json.dumps(merged, indent=2) + '\n')
    (args.output / 'results.md').write_text(markdown(measurements, labels, runtime))


def run():
    import sys
    if len(sys.argv) > 1 and sys.argv[1] == 'merge':
        return merge(sys.argv[2:])
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--binary', action='append', required=True, help='label=/absolute/executable')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--forks', type=int, default=3)
    parser.add_argument('--engines', action='append', default=[], metavar='LABEL=ENGINE,...',
                        help='Supported engines for one binary; defaults to all current engines')
    parser.add_argument('--modes', action='append', default=[], metavar='LABEL=MODE,...')
    parser.add_argument('--groups', help='Optional measured subset: MODE:STAGE,...')
    parser.add_argument('--projection-gates', action='store_true',
                        help='Require same-worker E1 cells and report their fixed ratio gates')
    parser.add_argument('--runtime', choices=('svm', 'jvm'), default='svm')
    parser.add_argument('--pin', action='store_true',
                        help='Pin to the highest available CPU (remote workers); else require taskset')
    parser.add_argument('--source', required=True, help='Source commit plus saved patch hash(es)')
    args = parser.parse_args()
    binaries = {}
    for spec in args.binary:
        label, path = spec.split('=', 1)
        if not label or label in binaries:
            parser.error('Binary labels must be nonempty and unique')
        binaries[label] = Path(path).resolve(strict=True)
    if args.projection_gates and int(os.environ.get('TEST_TOTAL_SHARDS', '1')) != 1:
        parser.error('Projection ratios require every group on one worker; do not shard')
    if args.forks < 3:
        parser.error('At least three fresh forks are required')
    affinity = sorted(os.sched_getaffinity(0))
    if args.pin and len(affinity) > 1:
        os.sched_setaffinity(0, {affinity[-1]})
        affinity = sorted(os.sched_getaffinity(0))
    if len(affinity) != 1:
        parser.error('Pin the runner to one CPU with taskset (or --pin)')
    try:
        groups = shard(select_groups(args.groups))
        selected = select_engines(binaries, args.engines)
        modes = select_modes(binaries, args.modes)
    except ValueError as failure:
        parser.error(str(failure))
    args.output.mkdir(parents=True, exist_ok=False)
    cpu_info = Path('/proc/cpuinfo').read_text()
    report = dict(schema=4, engines=selected, modes=modes, source=args.source, seed=SEED, forks=args.forks, runtime=args.runtime,
                  groups=groups, shard=dict(index=int(os.environ.get('TEST_SHARD_INDEX', '0')),
                                            total=int(os.environ.get('TEST_TOTAL_SHARDS', '1'))),
                  binary_sha256={k: hashlib.sha256(v.read_bytes()).hexdigest()
                                 for k, v in binaries.items() if v.is_file()},
                  host=platform.uname()._asdict(), affinity=affinity,
                  logical_cpus=os.cpu_count(),
                  load_average=os.getloadavg(),
                  cpu_model=next((line.split(':', 1)[1].strip() for line in cpu_info.splitlines()
                                  if line.startswith('model name')), platform.processor()),
                  measurements=[])
    jobs = [(label, (engine, mode, stage), fork, metric)
            for label in binaries for engine in selected[label] for mode, stage in groups
            if mode in modes[label] and supported(engine, stage, mode)
            for fork in range(args.forks) for metric in ('time', 'memory')]
    random.Random(SEED).shuffle(jobs)
    checksums = {}
    for label, cell, fork, metric in jobs:
        result = subprocess.run(command(binaries[label], args.runtime, cell, metric),
                                capture_output=True, text=True, timeout=180, check=False)
        if result.returncode:
            raise RuntimeError(f'{label} {cell} failed:\n{result.stdout}\n{result.stderr}')
        records = [json.loads(line) for line in result.stdout.splitlines() if line.startswith('{')]
        if len(records) != 1:
            raise ValueError(f'{label} {cell}: expected one result record')
        record = records[0]
        validate(record, cell, metric, checksums, args.runtime)
        record.update(binary=label, fork=fork, metric=metric)
        report['measurements'].append(record)
        # Preserve completed cells even if a later process fails; never summarize a partial run.
        (args.output / 'partial.json').write_text(json.dumps(report, indent=2) + '\n')
        print(label, *cell, fork, metric, flush=True)
    report['load_average_after'] = os.getloadavg()
    report['summary'] = summarize(report['measurements'], binaries)
    if args.projection_gates:
        report['projection_gates'] = projection_gates(report['summary'])
    (args.output / 'partial.json').write_text(json.dumps(report, indent=2) + '\n')
    (args.output / 'partial.json').rename(args.output / 'results.json')
    (args.output / 'results.md').write_text(
        markdown(report['measurements'], binaries, args.runtime))
    if args.projection_gates:
        (args.output / 'projection-gates.json').write_text(
            json.dumps(report['projection_gates'], indent=2) + '\n')
        if not all(gate['passed'] or not gate['required'] for gate in report['projection_gates']):
            raise SystemExit('E1 performance gate missed; complete measurements and ratios retained')


if __name__ == '__main__':
    run()
