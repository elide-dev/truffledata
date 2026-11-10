"""README benchmark cards from one release's raw benchmark reports; never runs a benchmark.

  plot_cards.py collect <outputs> <results-dir>   copy the four suites' raw reports
  plot_cards.py render <results-dir> <image-dir> --label 0.11.0 --invocation <id>

`collect` copies the reports of `//benchmarks/comparison:{json_typed_pair,consumer_measure,
yaml_pair,csv_pair}` unchanged (large ones gzipped), from either a Bazel testlogs tree
(`<outputs>/benchmarks/comparison/<test>/shard_N_of_M/test.outputs`) or a dedicated-host run
(`<outputs>/<test>/shard-<index>/`, see benchmarks/comparison/host_run.sh). `render` validates them with each
harness's own validator, recomputes every plotted statistic from the per-fork samples and
writes a light and a dark SVG per card, the normalized data and a manifest of hashes.

When a paired harness ran the same image as candidate and baseline (a release measurement,
not a before/after pair), both roles are forks of one image on one host and CPU, so they are
pooled: each bar is the median of six fork medians and its whisker spans all six. Engines are
compared only inside one shard (remote shards can land on different hosts).
"""
import argparse
import gzip
import hashlib
import json
import math
from pathlib import Path
import shutil
import statistics
import sys

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(ROOT / 'benchmarks/comparison'))
from cards import Card, Panel, Row, render as render_card  # noqa: E402

SUITES = {'json_typed_pair': ('json', 'json-typed-pair.json'),
          'consumer_measure': ('protobuf', 'consumer/results.json'),
          'yaml_pair': ('yaml', 'yaml-pair.json'),
          'csv_pair': ('csv', 'report.json')}


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def read(path):
    payload = path.read_bytes()
    return json.loads(gzip.decompress(payload) if path.suffix == '.gz' else payload)


def collect(testlogs, results):
    for test, (name, report) in SUITES.items():
        bazel = sorted((testlogs / 'benchmarks/comparison' / test).glob('shard_*_of_*'),
                       key=lambda p: int(p.name.split('_')[1]))
        host = sorted((testlogs / test).glob('shard-*'), key=lambda p: int(p.name.split('-')[1]))
        shards = [(int(p.name.split('_')[1]) - 1, p / 'test.outputs') for p in bazel] + \
                 [(int(p.name.split('-')[1]), p) for p in host if (p / '.done').exists()]
        if len(host) != len([p for p in host if (p / '.done').exists()]):
            raise SystemExit(f'{test}: unfinished shards under {testlogs / test}')
        if not shards:
            raise SystemExit(f'{test}: no shard outputs under {testlogs}')
        out = results / name
        out.mkdir(parents=True, exist_ok=True)
        for index, outputs in shards:
            files = [outputs / report] if report else sorted(outputs.glob('*.json'))
            if not files or not all(f.is_file() for f in files):
                raise SystemExit(f'{outputs}: missing report')
            for f in files:
                stem = f'shard-{index}' if report else f.stem
                data = f.read_bytes()
                if len(data) > 256 << 10:
                    (out / f'{stem}.json.gz').write_bytes(gzip.compress(data, 9, mtime=0))
                else:
                    (out / f'{stem}.json').write_bytes(data)
        print(f'{test}: {len(shards)} shards -> {out}')


def cpu_model(cpuinfo):
    return cpuinfo.split('model name')[1].split('\n')[0].strip(' :\t')


def stat(values):
    if not values or not all(math.isfinite(v) and v >= 0 for v in values):
        raise ValueError(f'invalid fork values {values}')
    return dict(median=statistics.median(values), min=min(values), max=max(values),
                forks=sorted(values), n=len(values))


class Data:
    """Every plotted cell, keyed by (suite, shard, workload, engine, stage, metric)."""

    def __init__(self, results):
        self.results = results
        self.sources, self.images, self.hosts, self.cells = {}, {}, {}, {}
        self.models = set()

    def host(self, suite, shard, hostname, model=None):
        self.hosts[(suite, shard)] = hostname
        if model:
            self.models.add(model)

    def load(self, relative):
        path = self.results / relative
        self.sources[relative] = digest(path)
        return read(path)

    def put(self, key, values, checksum):
        if key in self.cells:
            raise ValueError(f'duplicate cell {key}')
        self.cells[key] = dict(stat(values), checksum=checksum)

    def get(self, *key):
        if key not in self.cells:
            raise KeyError(f'missing cell {key}')
        return self.cells[key]

    def image(self, suite, binaries):
        hashes = set(binaries.values())
        if len(hashes) != 1:
            raise ValueError(f'{suite}: candidate and baseline differ; this is a pair, not a release run')
        if self.images.setdefault(suite, hashes.pop()) != next(iter(set(binaries.values()))):
            raise ValueError(f'{suite}: shards ran different images')


def forks(rows, metric):
    """One median per fork (and role, pooled) from the raw per-process samples."""
    seen = set()
    for r in rows:
        identity = (r.get('role') or r.get('variant') or r.get('binary'), r['fork'])
        if identity in seen:
            raise ValueError(f'duplicate fork {identity}')
        seen.add(identity)
    return [statistics.median(r[metric]) for r in rows]


def load_json(data):
    for path in sorted((data.results / 'json').glob('shard-*.json*')):
        report = data.load(str(path.relative_to(data.results)))
        if not report['complete']:
            raise ValueError(f'{path}: incomplete')
        data.image('json', report['binary_sha256'])
        shard = report['shard']
        data.host('json', shard, report['hostname'], cpu_model(report['cpu']))
        groups = {}
        for row in report['measurements']:
            if row['runtime'] != 'svm' or row['checksum'] != report['expected_checksums'][row['engine']]:
                raise ValueError(f'{path}: wrong runtime or checksum {row["engine"]}')
            if row['guest_compiled'] != (row['stage'] == 'peak'):
                raise ValueError(f'{path}: wrong tier {row["engine"]} {row["stage"]}')
            for metric in ('ns_per_row', 'allocated_bytes_per_row'):
                if metric in row:
                    groups.setdefault((row['workload'], row['engine'], row['stage'], metric), []).append(row)
        for (workload, engine, stage, metric), rows in groups.items():
            values = forks(rows, metric)
            if len(values) != 6:
                raise ValueError(f'{path}: {engine} {stage} {metric} has {len(values)} forks')
            data.put(('json', shard, workload, engine, stage, metric), values, rows[0]['checksum'])


def load_protobuf(data):
    from consumer import validate
    for path in sorted((data.results / 'protobuf').glob('shard-*.json*')):
        report = data.load(str(path.relative_to(data.results)))
        data.image('protobuf', report['binary_sha256'])
        shard = report['shard']['index']
        data.host('protobuf', shard, report['host']['node'], report['cpu_model'])
        checks, groups = {}, {}
        for row in report['measurements']:
            validate(row, (row['engine'], row['mode'], row['stage']), row['metric'], checks)
            metric = 'ns_per_row' if row['metric'] == 'time' else 'java_bytes_per_row'
            groups.setdefault((row['mode'], row['engine'], row['stage'], metric), []).append(row)
        for (mode, engine, stage, metric), rows in groups.items():
            values = forks(rows, metric)
            if len(values) != report['forks']:
                raise ValueError(f'{path}: {engine} {mode} {stage} incomplete')
            data.put(('protobuf', shard, mode, engine, stage, metric), values, rows[0]['checksum'])


def load_yaml(data):
    for path in sorted((data.results / 'yaml').glob('shard-*.json*')):
        report = data.load(str(path.relative_to(data.results)))
        if not report['complete'] or report['runtime'] != 'svm':
            raise ValueError(f'{path}: incomplete')
        data.image('yaml', report['binary_sha256'])
        data.host('yaml', report['workload'], report['hostname'])
        groups = {}
        for row in report['measurements']:
            groups.setdefault(row['engine'], []).append(row)
        for engine, rows in groups.items():
            if len({(r['checksum'], r['records'], r['input_bytes']) for r in rows}) != 1 or len(rows) != 6:
                raise ValueError(f'{path}: {engine} work differs or forks missing')
            mb_s = [r['input_bytes'] * 1000 / statistics.median(r['ns_per_operation']) for r in rows]
            heap = [statistics.median(r['allocated_bytes_per_operation']) / r['records'] for r in rows]
            data.put(('yaml', 0, report['workload'], engine, 'ingest', 'mb_per_s'), mb_s, rows[0]['checksum'])
            data.put(('yaml', 0, report['workload'], engine, 'ingest', 'java_bytes_per_record'), heap,
                     rows[0]['checksum'])


def load_csv(data):
    from csv_pair import validate
    for path in sorted((data.results / 'csv').glob('shard-*.json*')):
        report = data.load(str(path.relative_to(data.results)))
        data.image('csv', report['binary_sha256'])
        data.host('csv', path.name, report['hostname'], cpu_model(report['cpu']))
        checks, groups = {}, {}
        for row in report['measurements']:
            size = row.get('target') or report['group'][1]
            validate(row, (row['workload'], row['engine'], row['dataset'], size, row['stage']), checks)
            groups.setdefault((row['workload'], row['engine'], row['dataset'], size, row['stage']), []).append(row)
        for (workload, engine, dataset, size, stage), rows in groups.items():
            key = ('csv', 0, f'{dataset}-{size >> 20}MiB-{workload}', engine, stage)
            if stage == 'ingest':
                data.put(key + ('gb_per_s',), [1 / statistics.median(r['ns_per_byte']) for r in rows], rows[0]['checksum'])
            data.put(key + ('java_bytes_per_row',), forks(rows, 'allocated_bytes_per_row'), rows[0]['checksum'])


MANIFEST_HINT = 'hashes in manifest.json'


def rows_for(data, prefix, engines, metric, ours, scale=1.0):
    out = []
    for label, engine in engines:
        s = data.get(*prefix[:3], engine, prefix[3], metric)
        out.append(Row(label, s['median'] * scale, s['min'] * scale, s['max'] * scale, engine in ours))
    return out


def chip(rows, better, kind='time', exclude=()):
    """The best TruffleData row against the best other implementation, both named."""
    pick = min if better == 'lower' else max
    ours = [r for r in rows if r.ours and r.label not in exclude]
    others = [r for r in rows if not r.ours]
    if not ours or not others:
        return None
    mine, best = pick(ours, key=lambda r: r.value), pick(others, key=lambda r: r.value)
    ratio = best.value / mine.value if better == 'lower' else mine.value / best.value
    word = ('less heap than', 'more heap than') if kind == 'heap' else ('faster than', 'slower than')
    who = mine.label.removeprefix('TruffleData').strip()
    who = f'{who[0].upper()}{who[1:]}: ' if len(ours) > 1 and who else ''
    if ratio >= 1:
        return (f'{who}{ratio:.2f}× {word[0]} {best.label}', 'good')
    return (f'{who}{1 / ratio:.2f}× {word[1]} {best.label}', 'bad')


def panel(data, title, unit, prefix, engines, metric, ours, better='lower', kind='time', scale=1.0, exclude=()):
    rows = rows_for(data, prefix, engines, metric, ours, scale)
    return Panel(title, unit, rows, better, chip(rows, better, kind, exclude))


def build_cards(data, label, only=None):
    want = lambda slug: only is None or slug in only  # noqa: E731
    hosts = set(data.hosts.values())
    if len(hosts) != 1 or len(data.models) != 1:
        raise ValueError(f'cards compare across panels, so every suite must share one host: {hosts} {data.models}')
    common = (f'One quiet host ({next(iter(data.models))}), every shard in sequence on one pinned CPU · Linux x86-64 · '
              'GraalVM Native Image · O2 · serial GC · no PGO.')
    pooled = ('Bars: median of six fresh processes (two interleaved groups of three on one host and CPU); '
              'whiskers: min–max of all six, not confidence intervals.')
    cards = []

    # JSON.
    if want('json'):
        cards.append(json_card(data, label, pooled, common))
    if want('protobuf'):
        cards.append(protobuf_card(data, label, common))
    if want('csv'):
        cards.append(csv_card(data, label, pooled, common))
    if want('yaml'):
        cards.append(yaml_card(data, label, pooled, common))
    return cards


def json_card(data, label, pooled, common):
    generic = [('TruffleData', 'simdjson-generic-interop'), ('fastjson2', 'fastjson2-tree'),
               ('DSL-JSON', 'dsljson-map'), ('jsoniter', 'jsoniter-any'), ('Jackson', 'jackson-tree'),
               ('Gson', 'gson-tree')]
    numeric = [('TruffleData', 'simdjson-numeric-interop'), ('fastjson2', 'fastjson2-numeric'),
               ('Gson', 'gson-numeric'), ('Jackson', 'jackson-numeric')]
    typed = [('TruffleData', 'simdjson-typed'), ('Gson streaming', 'gson-stream')]
    ours = {'simdjson-generic-interop', 'simdjson-numeric-interop', 'simdjson-typed'}
    g, n, t = (('json', 5, 'json-generic'), ('json', 8, 'json-numeric'), ('json', 4, 'json-scalar'))
    return Card(
        'json', f'TruffleData {label} · JSON', 'JSON on Native Image',
        'simdjson underneath, Truffle interop on top. Each row of panels is one fixture.',
        [panel(data, 'Generic documents · parse + ten reads', 'ns / document', g + ('ingest',), generic,
               'ns_per_row', ours),
         panel(data, 'Generic documents · compiled reads', 'ns / document (warm, peak tier)',
               g + ('peak',), generic, 'ns_per_row', ours),
         panel(data, 'Generic documents · Java heap', 'bytes allocated / document', g + ('ingest',), generic,
               'allocated_bytes_per_row', ours, kind='heap'),
         panel(data, 'Number-heavy documents · parse + reads', 'µs / document', n + ('ingest',), numeric,
               'ns_per_row', ours, scale=1e-3),
         panel(data, 'Number-heavy documents · Java heap', 'KB allocated / document', n + ('ingest',), numeric,
               'allocated_bytes_per_row', ours, kind='heap', scale=1e-3),
         panel(data, 'Typed records · decode to a record', 'ns / record', t + ('ingest',), typed,
               'ns_per_row', ours)],
        notes=[pooled,
               'Generic: ten member reads per document; TruffleData heap includes 240 B of read views and excludes '
               'native input and index memory. Number-heavy: canada-style coordinates. Typed: three-field records '
               'through a compiled JsonPlan versus a hand-written Gson streaming decoder.',
               common],
        columns=3)


def protobuf_card(data, label, common):
    pb = [('protobuf-java', 'stock'), ('TruffleData generated', 'generated-owned'), ('TruffleData slab', 'slab'),
          ('TruffleData session', 'slab-session')]
    reads = [('protobuf-java*', 'stock'), ('protobuf-java via interop', 'stock-interop'),
             ('TruffleData generated', 'generated-owned'), ('TruffleData slab', 'slab')]
    mine = {'generated-owned', 'slab', 'slab-session'}

    def shard_of(mode, stage):
        found = {k[1] for k in data.cells if k[0] == 'protobuf' and k[2] == mode and k[4] == stage}
        if len(found) != 1:
            raise ValueError(f'protobuf {mode} {stage}: {found}')
        return found.pop()

    def pbp(title, unit, mode, stage, engines, metric='ns_per_row', kind='time', scale=1.0, exclude=()):
        prefix = ('protobuf', shard_of(mode, stage), mode, stage)
        p = panel(data, title, unit, prefix, engines, metric, mine, kind=kind, scale=scale, exclude=exclude)
        return p

    return Card(
        'protobuf', f'TruffleData {label} · Protobuf', 'Protobuf on Native Image',
        'The same binary messages through protobuf-java and TruffleData, decoded and read from guest code.',
        [pbp('Decode + release', 'ns / message', 'full', 'decode', pb),
         pbp('Decode + read every field + release', 'ns / message', 'full', 'resident', pb),
         pbp('Java heap per decode', 'bytes allocated / message', 'full', 'decode', pb, 'java_bytes_per_row', 'heap',
             exclude={'TruffleData session'}),
         pbp('Large message · decode + release', 'µs / message', 'large', 'decode', pb, scale=1e-3),
         pbp('Guest reads · interpreter', 'ns / message (warm)', 'full', 'interpret', reads),
         pbp('Guest reads · compiled', 'ns / message (warm, peak tier)', 'full', 'peak', reads)],
        notes=[f'Bars: median of three fresh processes; whiskers: min–max of the three, not confidence intervals.',
               '* Direct protobuf-java getters run ahead-of-time behind one Truffle boundary per message; '
               '“via interop” exposes the same objects to guest code. The session decodes into a scoped native '
               'arena outside the Java heap, so its heap chip compares the owning slab instead; generated messages own their data.',
               common],
        columns=3)


def csv_card(data, label, pooled, common):
    def csvp(title, dataset, size, workload, engines, mine):
        prefix = ('csv', 0, f'{dataset}-{size}MiB-{workload}', 'ingest')
        return panel(data, title, 'GB / second', prefix, engines, 'gb_per_s', mine, better='higher')
    typed_engines = [('TruffleData batch', 'truffledata-plan-native'), ('TruffleData index scan†', 'kernel-native'),
                     ('FastCSV', 'fastcsv')]
    string_engines = [('TruffleData batch', 'truffledata-batch-native'),
                      ('TruffleData owned batch', 'truffledata-batch-owned-native'),
                      ('TruffleData index scan†', 'kernel-native'), ('FastCSV', 'fastcsv'), ('Jackson CSV', 'jackson')]
    mine_csv = {'truffledata-plan-native', 'truffledata-batch-native', 'truffledata-batch-owned-native', 'kernel-native'}
    return Card(
        'csv', f'TruffleData {label} · CSV', 'CSV on Native Image',
        'Mixed-type CSV through a SIMD structural index. Typed columns and every field as a String.',
        [csvp('Typed columns · 1 MiB', 'mixed', 1, 'typed', typed_engines, mine_csv),
         csvp('Typed columns · 128 MiB', 'mixed', 128, 'typed', typed_engines, mine_csv),
         csvp('Every field as a String · 1 MiB', 'mixed', 1, 'strings', string_engines, mine_csv),
         csvp('Every field as a String · 128 MiB', 'mixed', 128, 'strings', string_engines, mine_csv)],
        notes=[pooled,
               '† Index scan is a Java consumer over the native index, with nothing retained; the batches retain '
               'typed columns or Strings for later reads. FastCSV and Jackson consume each record as it is read.',
               common],
        columns=2)


def yaml_card(data, label, pooled, common):
    yaml_engines = [('Tape + interop reads', 'truffledata-yaml-interop'), ('Typed plan', 'truffledata-yaml-typed'),
                    ('Java Map/List tree', 'truffledata-yaml-java'), ('Truffle object tree', 'truffledata-yaml-truffle'),
                    ('Heap snapshot', 'truffledata-yaml-snapshot')]
    every = {e for _, e in yaml_engines}

    def yp(title, workload, metric, unit, better):
        prefix = ('yaml', 0, workload, 'ingest')
        rows = rows_for(data, prefix, yaml_engines, metric, every)
        return Panel(title, unit, rows, better)
    return Card(
        'yaml', f'TruffleData {label} · YAML', 'YAML on Native Image',
        'One parser, five ways to consume it. No other YAML library is in the native image, so this card '
        'compares TruffleData APIs only.',
        [yp('1,000 block-style records', 'yaml-records', 'mb_per_s', 'MB / second', 'higher'),
         yp('1,000 flow-style records', 'yaml-flow', 'mb_per_s', 'MB / second', 'higher'),
         yp('Java heap · block records', 'yaml-records', 'java_bytes_per_record', 'bytes allocated / record',
            'lower')],
        notes=[pooled, 'Static rapidyaml archive; every engine reads the same ten members '
               'per record.', common],
        columns=3)


def render(results, output, label, revision, invocation, measured):
    data = Data(results)
    load_json(data)
    load_protobuf(data)
    load_yaml(data)
    load_csv(data)
    cards = build_cards(data, label)
    output.mkdir(parents=True, exist_ok=True)
    artifacts = {}
    for card in cards:
        card.source = (f'TruffleData {label} ({revision}) · measured {measured} · '
                       f'image sha256 {data.images[card.slug][:12]} · {MANIFEST_HINT}')
        for theme in ('light', 'dark'):
            path = output / f'{card.slug}-{theme}.svg'
            path.write_text(render_card(card, theme))
            artifacts[path.name] = digest(path)
    lines = [f'# TruffleData {label} benchmark cards', '',
             f'Generated by `tools/bench/plot_cards.py` from the raw reports; measured {measured} at `{revision}`.',
             'Median (min–max) over every fork; see the card notes for fork counts.', '']
    for card in cards:
        lines += [f'## {card.title}', '']
        for p in card.panels:
            better = 'lower' if p.better == 'lower' else 'higher'
            lines += [f'### {p.title}', '', f'{p.unit}, {better} is better' + (f'. Chip: {p.chip[0]}' if p.chip else ''), '',
                      '| Implementation | Median | Range |', '|---|---:|---:|']
            lines += [f'| {r.label} | {r.value:,.4g} | {r.lo:,.4g}–{r.hi:,.4g} |' for r in p.rows]
            lines.append('')
    (output / 'cards.md').write_text('\n'.join(lines))
    cells = [dict(zip(('suite', 'shard', 'workload', 'engine', 'stage', 'metric'), k), **v)
             for k, v in sorted(data.cells.items(), key=lambda kv: tuple(map(str, kv[0])))]
    (output / 'data.json').write_text(json.dumps(dict(
        label=label, revision=revision, measured=measured, image_build_invocation=invocation, images=data.images,
        hosts=sorted(set(data.hosts.values())), cpu_models=sorted(data.models), cells=cells), indent=1) + '\n')
    manifest = dict(label=label, revision=revision, measured=measured, image_build_invocation=invocation,
                    image_sha256=data.images, source_sha256=data.sources,
                    data_sha256=digest(output / 'data.json'),
                    renderer_sha256={n: digest(HERE / n) for n in ('plot_cards.py', 'cards.py')},
                    font_sha256={p.name: digest(p) for p in sorted((HERE / 'fonts').glob('*.woff2'))},
                    artifact_sha256=artifacts)
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(f'Rendered {len(artifacts)} SVGs from {len(data.cells)} validated cells.')


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest='command', required=True)
    c = sub.add_parser('collect')
    c.add_argument('testlogs', type=Path)
    c.add_argument('results', type=Path)
    r = sub.add_parser('render')
    r.add_argument('results', type=Path)
    r.add_argument('output', type=Path)
    r.add_argument('--label', required=True)
    r.add_argument('--revision', required=True)
    r.add_argument('--invocation', required=True, help='BuildBuddy invocation that built the images')
    r.add_argument('--measured', required=True)
    args = parser.parse_args()
    if args.command == 'collect':
        collect(args.testlogs, args.results)
    else:
        render(args.results, args.output, args.label, args.revision, args.invocation, args.measured)


if __name__ == '__main__':
    main()
