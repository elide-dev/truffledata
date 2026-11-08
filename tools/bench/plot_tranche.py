"""Render the recorded 2026-11-08 format pairs; never invoke a benchmark.

Called by plot_comparison.py --tranche. Each plotted statistic is recomputed from
all three raw process medians. The normalized data and manifest preserve the
source files, image identities, workload boundaries and every fork range.
"""
import gzip
import hashlib
import json
import math
from pathlib import Path
import statistics
import sys

BASELINE = '#8795a6'
CANDIDATE = '#087e8b'
REFERENCE = '#69559c'
WARNING = '#bd542d'
INK = '#233443'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load(results):
    root = results.resolve().parents[1]
    sources, images, cells = {}, {}, []

    def read(relative):
        path = results / relative
        sources[str(path.resolve().relative_to(root))] = digest(path)
        payload = path.read_bytes()
        return json.loads(gzip.decompress(payload) if path.suffix == '.gz' else payload)

    def add(format_name, workload, engine, stage, metric, records, role_key, memory_metric,
            extra=None):
        cell = dict(format=format_name, workload=workload, engine=engine, stage=stage,
                    metric=metric, **(extra or {}))
        for role in ('baseline', 'candidate'):
            selected = [r for r in records if r[role_key] == role]
            cell[role] = {}
            for destination, key in (('time', metric), ('java_bytes_per_row', memory_metric)):
                rows = [r for r in selected if key in r]
                if len(rows) != 3 or {r['fork'] for r in rows} != {0, 1, 2}:
                    raise ValueError(f'Incomplete cell: {cell} {role} {key}')
                forks = [statistics.median(r[key]) for r in sorted(rows, key=lambda r: r['fork'])]
                if not all(math.isfinite(v) and v >= 0 for v in forks):
                    raise ValueError('Invalid measurement')
                cell[role][destination] = dict(median=statistics.median(forks),
                                               min=min(forks), max=max(forks), forks=forks)
            # Native observations are retained separately from Java allocation counters.
            cell[role]['native_observations'] = {
                k: v for k, v in selected[0].items()
                if k.endswith('_bytes_per_row') and not isinstance(v, list)
            }
        checks = {(r['checksum'], r['rows']) for r in records}
        if len(checks) != 1:
            raise ValueError('Work changed between images/forks')
        cell['checksum'], cell['rows'] = checks.pop()
        cells.append(cell)

    json_manifest = read('json-typed-tranche-20261108/paired/manifest.json')
    for number in range(1, 7):
        relative = f'json-typed-tranche-20261108/paired/shard-{number}.json'
        report = read(relative)
        expected = json_manifest['artifacts'][number - 1]
        assert digest(results / relative) == expected['sha256']
        assert report['complete'] and report['shard'] == number - 1
        assert report['binary_sha256'] == json_manifest['binary_sha256']
        images['json'] = report['binary_sha256']
        for row in report['measurements']:
            assert row['runtime'] == 'svm' and row['rows'] == 32
            assert row['checksum'] == report['expected_checksums'][row['engine']]
            assert row['guest_compiled'] == (row['stage'] == 'peak')
        for summary in report['summary']:
            if summary['metric'] != 'ns_per_row':
                continue
            rows = [r for r in report['measurements']
                    if (r['engine'], r['stage']) == (summary['engine'], summary['stage'])]
            add('json', summary['workload'], summary['engine'], summary['stage'],
                'ns_per_row', rows, 'role', 'allocated_bytes_per_row')
            for role in ('baseline', 'candidate'):
                assert cells[-1][role]['time']['median'] == summary[role]['median']

    # Reuse the measurement runners' existing work/tier validators.
    sys.path.insert(0, str(root / 'benchmarks/comparison'))
    from csv_pair import validate as validate_csv
    from consumer import validate as validate_protobuf
    for size in (1, 128):
        for dataset in ('numeric', 'mixed', 'quoted', 'escaped'):
            report = read(f'csv-span-only-20261108/pair/{dataset}-{size}MiB-report.json.gz')
            assert report['forks'] == 3
            assert images.setdefault('csv', report['binary_sha256']) == report['binary_sha256']
            checks = {}
            for row in report['measurements']:
                validate_csv(row, (row['workload'], row['engine'], dataset, size << 20, row['stage']), checks)
            for summary in report['summary']:
                rows = [r for r in report['measurements'] if (r['workload'], r['engine'], r['stage'])
                        == (summary['workload'], summary['engine'], summary['stage'])]
                add('csv', summary['workload'], summary['engine'], summary['stage'],
                    summary['metric'], rows, 'variant', 'allocated_bytes_per_row',
                    dict(dataset=dataset, MiB=size))
                for role in ('baseline', 'candidate'):
                    assert cells[-1][role]['time']['median'] == summary[role]['median']

    report = read('protobuf-warm-cache-9a1dcec/refined-svm/merged/results.json.gz')
    manifest = read('protobuf-warm-cache-9a1dcec/refined-svm/pair-manifest.json')
    assert report['complete'] and report['forks'] == 3
    images['protobuf'] = report['binary_sha256']
    for role in ('baseline', 'candidate'):
        assert images['protobuf'][role] == manifest[role + '_sha256']
    checks = {}
    for row in report['measurements']:
        validate_protobuf(row, (row['engine'], row['mode'], row['stage']), row['metric'], checks)
    for summary in report['summary']:
        if summary['binary'] != 'candidate':
            continue
        rows = [r for r in report['measurements'] if (r['engine'], r['mode'], r['stage'])
                == (summary['engine'], summary['mode'], summary['stage'])]
        add('protobuf', summary['mode'], summary['engine'], summary['stage'],
            'ns_per_row', rows, 'binary', 'java_bytes_per_row')
        assert cells[-1]['candidate']['time']['median'] == summary['ns_per_row']
    assert len(cells) == 34 + 78 + 132
    assert all(c['baseline']['java_bytes_per_row']['median'] ==
               c['candidate']['java_bytes_per_row']['median'] for c in cells)
    return dict(schema=1, baseline_production='9a1dcec',
                candidate_production=dict(json='a966cf2', csv='234304c', protobuf='e4cec9f'),
                measurement_invocations=dict(json='950032e5-dc4f-40f9-a67f-0a73f3b5dce3',
                    csv='07bb26fb-e67e-4d68-b0fe-a2eb4cdf5d8b',
                    protobuf='1159ca42-a11f-41d3-8f5f-4ef20be794c2'),
                source_sha256=sources, binary_sha256=images, cells=cells)


def render(results, output, label):
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    from matplotlib.ticker import FuncFormatter, LogLocator, NullFormatter
    import numpy as np

    data = load(results)
    output.mkdir(parents=True, exist_ok=True)
    (output / 'comparison-data.json').write_text(json.dumps(data, indent=2) + '\n')
    plt.rcParams.update({'font.family': 'DejaVu Sans', 'font.size': 10,
                         'text.color': INK, 'axes.labelcolor': INK,
                         'svg.fonttype': 'none', 'svg.hashsalt': 'truffledata-010-tranche'})
    artifacts = []

    def cell(format_name, workload, engine, stage='ingest', **extra):
        matches = [r for r in data['cells'] if (r['format'], r['workload'], r['engine'], r['stage'])
                   == (format_name, workload, engine, stage) and all(r[k] == v for k, v in extra.items())]
        if len(matches) != 1:
            raise ValueError(f'Ambiguous/missing plot cell: {format_name} {workload} {engine} {stage} {extra}')
        return matches[0]

    def bars(ax, entries, title, unit='ns / document', log=True, inverse=False, allocation=False):
        medians, lows, highs, names, colors = [], [], [], [], []
        for name, entry, role, color in entries:
            stat = entry[role]['java_bytes_per_row' if allocation else 'time']
            mid, lo, hi = stat['median'], stat['min'], stat['max']
            if inverse:
                mid, lo, hi = 1 / mid, 1 / hi, 1 / lo
            medians.append(mid); lows.append(lo); highs.append(hi)
            names.append(name); colors.append(color)
        ax.barh(range(len(names)), medians, color=colors, height=.57,
                xerr=[[m - lo for m, lo in zip(medians, lows)],
                      [hi - m for m, hi in zip(medians, highs)]],
                error_kw=dict(ecolor=INK, capsize=3, linewidth=1))
        ax.set_yticks(range(len(names)), names, fontsize=9)
        ax.invert_yaxis()
        if log:
            ax.set_xscale('log')
            ax.set_xlim(min(lows) * .68, max(highs) * 2.3)
            ax.xaxis.set_major_locator(LogLocator(base=10, subs=(1, 2, 5), numticks=7))
            ax.xaxis.set_minor_formatter(NullFormatter())
        else:
            ax.set_xlim(0, max(highs) * 1.3)
        for y, (mid, high) in enumerate(zip(medians, highs)):
            ax.text(high * 1.035 if log else high + max(highs) * .025, y,
                    f'{mid:,.4f}' if inverse else f'{mid:,.2f}', va='center', fontsize=9)
        ax.xaxis.set_major_formatter(FuncFormatter(lambda n, _: f'{n:,.2f}' if n < 1 else f'{n:,.0f}'))
        ax.set_title(title, loc='left', weight='bold', fontsize=11, pad=12)
        ax.set_xlabel(unit + (' · log scale' if log else '') +
                      (' · higher is better' if inverse else ' · lower is better'), fontsize=9)
        ax.grid(axis='x', alpha=.17); ax.set_axisbelow(True)
        ax.tick_params(axis='both', length=0)
        for spine in ax.spines.values():
            spine.set_visible(False)

    def pair(entry):
        return [('Baseline', entry, 'baseline', BASELINE), ('Candidate', entry, 'candidate', CANDIDATE)]

    def change(entry):
        return 100 * (entry['candidate']['time']['median'] / entry['baseline']['time']['median'] - 1)

    def finish(fig, name, title, footnote):
        fig.patch.set_facecolor('#fafbfc')
        height = fig.get_size_inches()[1]
        format_name = name.split('-')[0]
        revision = data['candidate_production'][format_name]
        fig.suptitle(f'TruffleData {label} release evidence  |  {title}', x=.025, y=1 - .16 / height,
                     ha='left', fontsize=16, weight='bold')
        fig.text(.025, 1 - .62 / height,
                 f'2026-11-08 · paired Linux x86-64 SVM · O2 · serial GC · no PGO · production 9a1dcec → {revision}',
                 fontsize=10, color='#526273', va='top')
        footer = ('Bars: median of three fresh-process medians; whiskers: full min–max, not confidence intervals.\n'
                  'Isolated preserved images; separate workloads/hosts. These captures predate final release fixes and packaging.\n' + footnote + '\n'
                  f'Image SHA-256 prefixes: baseline {data["binary_sha256"][format_name]["baseline"][:12]} / '
                  f'candidate {data["binary_sha256"][format_name]["candidate"][:12]}; exact hashes in comparison-manifest.json.')
        fig.text(.025, .14 / height, footer, fontsize=8.5, color='#465361', linespacing=1.5)
        fig.tight_layout(rect=(.01, 1.2 / height, .99, 1 - .95 / height), h_pad=3.3, w_pad=2.3)
        for extension in ('svg', 'png'):
            path = output / f'comparison-{name}.{extension}'
            metadata = {'Creator': 'TruffleData comparison renderer', 'Date': None} if extension == 'svg' else None
            fig.savefig(path, dpi=150, facecolor=fig.get_facecolor(), metadata=metadata)
            if extension == 'svg':
                path.write_text('\n'.join(line.rstrip() for line in path.read_text().splitlines()) + '\n')
            artifacts.append(path)
        plt.close(fig)

    fig, axes = plt.subplots(3, 2, figsize=(13, 11))
    cases = [(kind.title() + ' typed', 'json-typed-' + kind, 'simdjson-typed-' + kind)
             for kind in ('nested', 'repeated', 'mixed', 'churn')]
    cases += [('Flat typed control', 'json-scalar', 'simdjson-typed'),
              ('Generic interop control', 'json-generic', 'simdjson-generic-interop')]
    for ax, (title, workload, engine) in zip(axes.flat, cases):
        entry = cell('json', workload, engine)
        bars(ax, pair(entry), f'{title} · {change(entry):+.2f}% time')
    finish(fig, 'json-typed', 'JSON typed ingest and controls',
           'Four typed gains; generic control +2.20% has disjoint ranges. Flat +1.50% overlaps. Churn outlier retained.\n'
           'Workloads differ across panels. All 34 Java allocation cells unchanged. Sources: json-typed-tranche-20261108.')

    generic = [('TruffleData interop', 'simdjson-generic-interop'), ('fastjson2', 'fastjson2-tree'),
               ('DSL-JSON', 'dsljson-map'), ('jsoniter', 'jsoniter-any'),
               ('Jackson', 'jackson-tree'), ('Gson (noisy)', 'gson-tree')]
    fig, axes = plt.subplots(1, 3, figsize=(17, 7))
    for ax, stage, allocation, title in zip(axes, ('ingest', 'peak', 'ingest'), (False, False, True),
            ('Ingest + ten AOT reads', 'Compiled ten-member reads', 'Ingest Java allocations')):
        entries = [(name, cell('json', 'json-generic', engine, stage), 'candidate',
                    CANDIDATE if engine.startswith('simdjson') else REFERENCE) for name, engine in generic]
        bars(ax, entries, title, unit='Java B / document' if allocation else 'ns / document', allocation=allocation)
    finish(fig, 'json-generic', 'One generic JSON fixture · current comparisons',
           'Candidate image a966cf2; every engine consumes the same generic fixture. Gson ingest ranges are noisy and retained.\n'
           'Java heap excludes native input/index; TruffleData 384 B includes 240 B of AOT read views. Not total retained memory.')

    fig, axes = plt.subplots(2, 2, figsize=(14, 10))
    for ax, stage, title in zip(axes[0], ('interpret', 'peak'), ('Full interpreter reads', 'Full peak guest JIT reads')):
        slab = cell('protobuf', 'full', 'slab', stage)
        entries = [('Direct stock getters*', cell('protobuf', 'full', 'stock', stage), 'candidate', REFERENCE),
                   ('Stock guest interop', cell('protobuf', 'full', 'stock-interop', stage), 'candidate', '#a18ebc'),
                   ('Generated-owned', cell('protobuf', 'full', 'generated-owned', stage), 'candidate', '#d57525'),
                   ('Slab baseline', slab, 'baseline', BASELINE), ('Slab candidate', slab, 'candidate', CANDIDATE)]
        bars(ax, entries, f'{title} · slab {change(slab):+.1f}%', 'ns / row')
    entries = []
    for stage, title in (('interpret', 'Interpreter'), ('peak', 'Peak JIT')):
        entry = cell('protobuf', 'sparse', 'slab', stage)
        entries += [(title + ' baseline', entry, 'baseline', BASELINE),
                    (title + ' candidate', entry, 'candidate', CANDIDATE)]
    bars(axes[1, 0], entries, 'Sparse slab warm reads', 'ns / row')
    entries = []
    for engine, title in (('slab-native', 'Native'), ('slab-lazy', 'Lazy'), ('slab-projected', 'Projected')):
        entry = cell('protobuf', 'sparse', engine, 'first')
        entries += [(title + ' baseline', entry, 'baseline', BASELINE),
                    (f'{title} candidate ({change(entry):+.1f}%)', entry, 'candidate', WARNING)]
    bars(axes[1, 1], entries, 'Sparse first-read costs remain', 'ns / row')
    finish(fig, 'protobuf', 'Protobuf warm reads and cold tradeoffs',
           '* Direct stock getter work stays AOT behind a Truffle boundary; shared guest access and stock guest interop are distinct.\n'
           'Full slab interpreter is still 5.652× direct stock time. Baseline includes B4 Phase 1. All 132 memory pairs unchanged.')

    fig, axes = plt.subplots(1, 3, figsize=(17, 7))
    for ax, stage, allocation, title in zip(axes, ('decode', 'resident', 'decode'), (False, False, True),
            ('Full decode + release', 'Full decode + reads + release', 'Decode Java allocations')):
        slab = cell('protobuf', 'full', 'slab', stage)
        entries = [('Stock', cell('protobuf', 'full', 'stock', stage), 'candidate', REFERENCE),
                   ('Generated-owned', cell('protobuf', 'full', 'generated-owned', stage), 'candidate', '#d57525'),
                   ('Scoped slab session', cell('protobuf', 'full', 'slab-session', stage), 'candidate', '#5b8294'),
                   ('Owning slab baseline', slab, 'baseline', BASELINE),
                   ('Owning slab candidate', slab, 'candidate', CANDIDATE)]
        bars(ax, entries, title, 'Java B / row' if allocation else 'ns / row', allocation=allocation)
    finish(fig, 'protobuf-controls', 'Protobuf decode and ownership controls',
           'Warm-read gains do not imply decode gains. Owning resident ranges retain the 17.4 µs candidate outlier.\n'
           'Scoped session retains an 855.6 B native arena/row in this fixture; Java allocation excludes native storage and transient peaks.')

    fig = plt.figure(figsize=(15, 12))
    grid = fig.add_gridspec(2, 2, height_ratios=(1.2, 1))
    ax = fig.add_subplot(grid[0, :])
    variants = [('Copied / Java-SWAR', 'truffledata-batch'), ('Owned / Java-SWAR', 'truffledata-batch-owned'),
                ('Copied / native index', 'truffledata-batch-native'), ('Owned / native index', 'truffledata-batch-owned-native')]
    fixtures = [(dataset, size) for size in (1, 128) for dataset in ('numeric', 'mixed', 'quoted', 'escaped')]
    changes = np.array([[change(cell('csv', 'strings', engine, dataset=dataset, MiB=size))
                         for _, engine in variants] for dataset, size in fixtures])
    ax.imshow(changes, cmap='BrBG_r', vmin=-7, vmax=7, aspect='auto')
    ax.set_xticks(range(4), [name for name, _ in variants], fontsize=10)
    ax.set_yticks(range(8), [f'{dataset.title()} · {size} MiB' for dataset, size in fixtures], fontsize=10)
    for (y, x), value in np.ndenumerate(changes):
        ax.text(x, y, f'{value:+.2f}%', ha='center', va='center',
                color='white' if abs(value) > 4 else INK, fontsize=11)
    ax.set_title('All retained-string cells · candidate / baseline elapsed-time change', loc='left', weight='bold', pad=14)
    ax.set_xlabel('Negative = faster; positive = slower. Median ratios only; full fork ranges remain in the source report.', fontsize=9)
    ax.tick_params(length=0)
    for spine in ax.spines.values():
        spine.set_visible(False)
    for index, workload, title in ((0, 'typed', 'Mixed 1 MiB · typed consumption'),
                                   (1, 'strings', 'Mixed 1 MiB · every String consumed')):
        ax = fig.add_subplot(grid[1, index])
        entries = []
        variants = [('Retained batch', 'truffledata-plan-native')] if workload == 'typed' else [
            ('Copied retained', 'truffledata-batch-native'), ('Owned retained', 'truffledata-batch-owned-native')]
        for name, engine in variants:
            entry = cell('csv', workload, engine, dataset='mixed', MiB=1)
            entries += [(name + ' baseline', entry, 'baseline', BASELINE),
                        (name + ' candidate', entry, 'candidate', CANDIDATE)]
        refs = [('FastCSV consumer', 'fastcsv'), ('Kernel consumer*', 'kernel-native')]
        if workload == 'strings':
            refs.insert(1, ('Jackson consumer', 'jackson'))
        entries += [(name, cell('csv', workload, engine, dataset='mixed', MiB=1), 'candidate', REFERENCE)
                    for name, engine in refs]
        bars(ax, entries, title, 'GB / second', log=False, inverse=True)
    finish(fig, 'csv', 'CSV retained construction and reference consumers',
           'Unweighted string aggregates improve 1.5–2.0% in time. Some cells regress/overlap; allocation is unchanged in all 78 cells.\n'
           '* Kernel = Java consumer over native indexes, not retained materialization. Owned API predates this change. AVX-512 selected; no Arm claim.')

    manifest = dict(label=label, measured_at='2026-11-08', baseline_production=data['baseline_production'],
                    candidate_production=data['candidate_production'], binary_sha256=data['binary_sha256'],
                    measurement_invocations=data['measurement_invocations'], source_sha256=data['source_sha256'],
                    data_sha256=digest(output / 'comparison-data.json'), matplotlib=matplotlib.__version__,
                    renderer_sha256={name: digest(Path(__file__).with_name(name))
                                     for name in ('plot_tranche.py', 'plot_comparison.py')},
                    artifact_sha256={path.name: digest(path) for path in artifacts})
    (output / 'comparison-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(f'Rendered {len(artifacts)} SVG/PNG artifacts from {len(data["cells"])} validated paired cells.')
