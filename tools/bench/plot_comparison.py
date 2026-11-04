#!/usr/bin/env python3
"""Render release comparison charts from validated raw measurement data."""
import argparse
import hashlib
import json
from pathlib import Path
import statistics

from compare import validate

LABELS = {
    'proto-scalar': 'Timestamp · binary',
    'proto-bytes': 'BytesValue · 64 KiB payload',
    'timestamp-json': 'Timestamp · ProtoJSON',
    'mysql-int4': 'MySQL · INT4 decoder',
    'postgres-int4': 'PostgreSQL · INT4 decoder',
    'proto-mixed': 'Nested protobuf · projection',
    'proto-mixed-full': 'Nested protobuf · full consumption',
}
ENGINES = {'stock': 'Stock', 'stock-aliased': 'Stock · aliased',
           'owned': 'TruffleData · owned', 'buffa': 'TruffleData · Buffa',
           'static': 'TruffleData · static'}
COLORS = {'stock': '#626d78', 'stock-aliased': '#a0aab4', 'owned': '#7866ae',
          'buffa': '#087e8b', 'static': '#7866ae'}


def render(data, output, label):
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt

    validate(data)
    output.mkdir(parents=True, exist_ok=True)
    plt.rcParams.update({'font.family': 'DejaVu Sans', 'font.size': 10,
                         'svg.fonttype': 'none', 'svg.hashsalt': 'truffledata-comparison'})
    charts = [('interpreter', 'Interpreter reads', 'interpret', 'ns_per_row', 'ns / row'),
              ('peak', 'Peak JIT reads', 'peak', 'ns_per_row', 'ns / row'),
              ('allocations', 'Ingestion allocations', 'ingest', 'allocated_bytes_per_row', 'Java heap bytes / message')]
    if data['schema'] >= 6:
        charts += [('generated-' + name, title + ' · generated getters', stage + '-generated', metric, unit)
                   for name, title, stage, metric, unit in list(charts)]
    for name, title, stage, metric, unit in charts:
        generated = stage.endswith('-generated')
        base = stage.removesuffix('-generated')
        if generated:
            supported = {r['workload'] for r in data['results'] if r['stage'] == stage}
            selected = [r for r in data['results'] if r['workload'] in supported and
                        (r['stage'] == stage or (r['engine'].startswith('stock') and
                         r['stage'] in (base, base + '-boundary')))]
        else:
            selected = [r for r in data['results'] if r['stage'] in (stage, stage + '-boundary')]
        workloads = [w for w in LABELS if any(r['workload'] == w for r in selected)]
        fig, axes = plt.subplots(len(workloads), 1, figsize=(10, 1.9 * len(workloads) + 1.3), squeeze=False)
        fig.patch.set_facecolor('#fafbfc')
        for ax, workload in zip(axes[:, 0], workloads):
            rows = [r for r in selected if r['workload'] == workload]
            engines = [e for e in ENGINES if any(r['engine'] == e for r in rows)]
            medians, lows, highs, names = [], [], [], []
            for engine in engines:
                forks = [statistics.median(r[metric]) for r in rows if r['engine'] == engine]
                medians.append(statistics.median(forks))
                lows.append(min(forks))
                highs.append(max(forks))
                boundary = base != 'ingest' and workload == 'proto-mixed' and engine == 'stock'
                names.append(ENGINES[engine] + (' *' if boundary else ''))
            ax.barh(names, medians, color=[COLORS[e] for e in engines], height=0.58,
                    xerr=[[m - lo for m, lo in zip(medians, lows)],
                          [hi - m for m, hi in zip(medians, highs)]],
                    error_kw={'ecolor': '#273444', 'capsize': 3, 'linewidth': 1})
            ax.invert_yaxis()
            ax.set_xscale('log')
            ax.set_xlim(min(v for v in lows if v > 0) * 0.65, max(highs) * 2.5)
            for y, (median, high) in enumerate(zip(medians, highs)):
                ax.text(high * 1.07, y, f'{median:,.2f}', va='center', fontsize=9)
            ax.set_title(LABELS[workload], loc='left', fontweight='bold', fontsize=11)
            ax.set_xlabel(unit + ' · log scale · lower is better', fontsize=9)
            ax.grid(axis='x', alpha=0.15)
            ax.set_axisbelow(True)
            for spine in ax.spines.values():
                spine.set_visible(False)
            ax.tick_params(axis='both', length=0)
        fig.suptitle(f'TruffleData {label}  |  {title}', x=0.025, ha='left', fontsize=18, fontweight='bold')
        footer = ('Median of three process medians; whiskers show min–max process medians, not confidence intervals.\n'
                  'Shared Linux x86-64 SVM runner. SQL: decoder functions only; no server or transport.\n')
        footer += ('Current-thread Java heap only; native allocations are excluded. Generic ingestion projection. '
                   if base == 'ingest' else '* Stock nested-protobuf reads cross an explicitly measured Truffle boundary. ')
        if generated:
            footer = footer.replace('Generic ingestion projection.', 'Generated getter projection.')
        footer += f'Revision: {data["revision"][:12]}.'
        fig.text(0.025, 0.018, footer, fontsize=8, color='#465361')
        fig.tight_layout(rect=(0, 0.075, 1, 0.955), h_pad=1.7)
        for extension in ('svg', 'png'):
            fig.savefig(output / f'comparison-{name}.{extension}', dpi=160,
                        facecolor=fig.get_facecolor(), metadata={'Creator': 'TruffleData comparison renderer'} if extension == 'svg' else None)
        plt.close(fig)
    return matplotlib.__version__


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--label', required=True)
    args = parser.parse_args()
    data = json.loads(args.input.read_text())
    version = render(data, args.output, args.label)
    (args.output / 'comparison-manifest.json').write_text(json.dumps({
        'revision': data['revision'], 'binary_sha256': data['binary_sha256'],
        'data_sha256': hashlib.sha256(args.input.read_bytes()).hexdigest(),
        'label': args.label, 'matplotlib': version,
    }, indent=2) + '\n')


if __name__ == '__main__':
    main()
