"""Collect the paired cells measurements from bazel-testlogs and print one table per format.

Usage: summarize_pairs.py <bazel-testlogs dir> <output dir>
Extracts every shard's undeclared outputs under <output dir>/pairs/<test>/shard_N/ and writes
<output dir>/summary.json. A cell is "routed" when the flag-on image makes its native calls through
the cells route; the other cells run the same code in both images (A/A controls).
"""
import json
import statistics
import sys
import zipfile
from pathlib import Path

logs, out = Path(sys.argv[1]), Path(sys.argv[2])
TESTS = ('csv_pair', 'json_typed_pair', 'yaml_pair', 'consumer_measure', 'json_document_pair')


def extract(test):
    """Copy each shard's undeclared outputs (a directory, or outputs.zip) under the output dir."""
    import shutil
    shards = []
    base = logs / 'benchmarks/comparison' / test
    if not base.exists():
        return shards
    dirs = sorted(base.glob('shard_*_of_*')) or [base]
    for d in dirs:
        outputs = d / 'test.outputs'
        if not outputs.exists():
            continue
        name = d.name if d.name.startswith('shard_') else 'shard_1_of_1'
        target = out / 'pairs' / test / name
        if target.exists():
            shutil.rmtree(target)
        target.mkdir(parents=True)
        archive = outputs / 'outputs.zip'
        if archive.exists():
            with zipfile.ZipFile(archive) as z:
                z.extractall(target)
        else:
            for f in outputs.rglob('*'):
                if f.is_file():
                    dest = target / f.relative_to(outputs)
                    dest.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copyfile(f, dest)
        shards.append(target)
    return shards


def overlap(a, b):
    return not (a['max'] < b['min'] or b['max'] < a['min'])


rows = []

# ---- CSV: report.json summary entries
for shard in extract('csv_pair'):
    f = shard / 'report.json'
    if not f.exists():
        continue
    report = json.loads(f.read_text())
    dataset, size = report['group']
    for e in report['summary']:
        if 'baseline' not in e or 'candidate' not in e:
            continue
        routed = e['engine'].startswith('truffledata-') and e['engine'].endswith('-native')
        rows.append(dict(format='csv', cell=f"{e['workload']}/{e['engine']}/{dataset}/{size >> 20}MiB/{e['stage']}",
                         unit=e['metric'], routed=routed,
                         base=e['baseline']['median'], cand=e['candidate']['median'],
                         base_range=(e['baseline']['min'], e['baseline']['max']),
                         cand_range=(e['candidate']['min'], e['candidate']['max']),
                         overlap=overlap(e['baseline'], e['candidate']),
                         base_alloc=e['baseline']['allocated_bytes_per_row'],
                         cand_alloc=e['candidate']['allocated_bytes_per_row']))

# ---- JSON typed pair: json-typed-pair.json summary (all session-based: controls)
for shard in extract('json_typed_pair'):
    f = shard / 'json-typed-pair.json'
    if not f.exists():
        continue
    report = json.loads(f.read_text())
    if not report.get('complete'):
        continue
    by = {}
    for e in report['summary']:
        by.setdefault((e['engine'], e['stage']), {})[e['metric']] = e
    for (engine, stage), m in sorted(by.items()):
        t, a = m.get('ns_per_row'), m.get('allocated_bytes_per_row')
        if not t or 'baseline' not in t or 'candidate' not in t:
            continue
        rows.append(dict(format='json', cell=f"{t['workload']}/{engine}/{stage}", unit='ns_per_row', routed=False,
                         base=t['baseline']['median'], cand=t['candidate']['median'],
                         base_range=(t['baseline']['min'], t['baseline']['max']),
                         cand_range=(t['candidate']['min'], t['candidate']['max']),
                         overlap=overlap(t['baseline'], t['candidate']),
                         base_alloc=a['baseline']['median'] if a else None,
                         cand_alloc=a['candidate']['median'] if a else None))

# ---- JSON document probe: probe-pair.json
for shard in extract('json_document_pair'):
    f = shard / 'probe-pair.json'
    if not f.exists():
        continue
    report = json.loads(f.read_text())
    s = report.get('summary')
    if not s:
        continue
    b, c = s['baseline'], s['candidate']
    rows.append(dict(format='json', cell='json-document-decode (JsonSvm.decode + close)', unit='ns_per_document',
                     routed=True, base=b['ns_per_operation']['median'], cand=c['ns_per_operation']['median'],
                     base_range=(b['ns_per_operation']['min'], b['ns_per_operation']['max']),
                     cand_range=(c['ns_per_operation']['min'], c['ns_per_operation']['max']),
                     overlap=overlap(b['ns_per_operation'], c['ns_per_operation']),
                     base_alloc=b['allocated_bytes_per_operation']['median'],
                     cand_alloc=c['allocated_bytes_per_operation']['median']))

# ---- YAML: yaml-pair.json summary
for shard in extract('yaml_pair'):
    f = shard / 'yaml-pair.json'
    if not f.exists():
        continue
    report = json.loads(f.read_text())
    for e in report.get('summary', []):
        b, c = e['baseline'], e['candidate']
        rows.append(dict(format='yaml', cell=f"{report['workload']}/{e['engine']}", unit='ns_per_operation',
                         routed=True, base=b['ns_per_operation']['median'], cand=c['ns_per_operation']['median'],
                         base_range=(b['ns_per_operation']['min'], b['ns_per_operation']['max']),
                         cand_range=(c['ns_per_operation']['min'], c['ns_per_operation']['max']),
                         overlap=overlap(b['ns_per_operation'], c['ns_per_operation']),
                         base_alloc=b['allocated_bytes_per_record'], cand_alloc=c['allocated_bytes_per_record']))

# ---- Buffa consumer: per-shard results.json with measurements; summarize per binary
consumer = []
for shard in extract('consumer_measure'):
    for f in shard.rglob('results.json'):
        consumer += json.loads(f.read_text())['measurements']
cells = {}
for m in consumer:
    key = (m['engine'], m['mode'], m['stage'])
    cells.setdefault(key, {}).setdefault(m['binary'], {}).setdefault(m['metric'], []).append(m)
for (engine, mode, stage), by in sorted(cells.items()):
    if 'candidate' not in by or 'baseline' not in by:
        continue
    stat = {}
    for label, metrics in by.items():
        times = [statistics.median(r['ns_per_row']) for r in metrics.get('time', [])]
        memory = [statistics.median(r['java_bytes_per_row']) for r in metrics.get('memory', [])]
        if not times:
            break
        stat[label] = dict(median=statistics.median(times), min=min(times), max=max(times),
                           alloc=statistics.median(memory) if memory else None,
                           backend=metrics['time'][0].get('producer_backend', ''))
    if len(stat) != 2:
        continue
    routed = engine.startswith('slab')
    rows.append(dict(format='buffa', cell=f"{engine}/{mode}/{stage}", unit='ns_per_row', routed=routed,
                     base=stat['baseline']['median'], cand=stat['candidate']['median'],
                     base_range=(stat['baseline']['min'], stat['baseline']['max']),
                     cand_range=(stat['candidate']['min'], stat['candidate']['max']),
                     overlap=overlap(stat['baseline'], stat['candidate']),
                     base_alloc=stat['baseline']['alloc'], cand_alloc=stat['candidate']['alloc'],
                     backends=(stat['baseline']['backend'], stat['candidate']['backend'])))

for r in rows:
    r['ratio'] = r['cand'] / r['base'] if r['base'] else None
(out / 'summary.json').write_text(json.dumps(rows, indent=1) + '\n')

for fmt in ('buffa', 'json', 'yaml', 'csv'):
    sel = [r for r in rows if r['format'] == fmt]
    if not sel:
        print(f'\n## {fmt}: no paired rows'); continue
    routed = [r for r in sel if r['routed']]
    control = [r for r in sel if not r['routed']]
    def stats(rs):
        if not rs:
            return 'none'
        ratios = sorted(r['ratio'] for r in rs)
        disjoint_faster = sum(1 for r in rs if not r['overlap'] and r['ratio'] < 1)
        disjoint_slower = sum(1 for r in rs if not r['overlap'] and r['ratio'] > 1)
        return (f"{len(rs)} cells; ratio flag-on/flag-off median {statistics.median(ratios):.3f}, "
                f"min {ratios[0]:.3f}, max {ratios[-1]:.3f}; fork ranges disjoint: "
                f"{disjoint_faster} faster, {disjoint_slower} slower")
    print(f'\n## {fmt}\nrouted: {stats(routed)}\ncontrols (same code in both images): {stats(control)}')
    alloc = [(r['cell'], r['base_alloc'], r['cand_alloc']) for r in routed if r['base_alloc'] is not None]
    diff = [(c, b, a) for c, b, a in alloc if abs(a - b) > max(0.5, 0.001 * max(abs(a), abs(b)))]
    print(f"routed allocation: {len(alloc)} cells compared, {len(diff)} differ")
    for c, b, a in diff[:12]:
        print(f"   {c}: flag off {b:.1f} B, flag on {a:.1f} B")
    for r in sorted(routed, key=lambda r: r['ratio'])[:4] + sorted(routed, key=lambda r: -r['ratio'])[:4]:
        print(f"   {r['ratio']:.3f}  {'overlap ' if r['overlap'] else 'DISJOINT'}  {r['cell']}  off {r['base']:.3f} [{r['base_range'][0]:.3f}..{r['base_range'][1]:.3f}]  on {r['cand']:.3f} [{r['cand_range'][0]:.3f}..{r['cand_range'][1]:.3f}] {r['unit']}")
    if fmt == 'buffa':
        print('   backends (off, on):', sorted({r.get('backends') for r in routed}))
