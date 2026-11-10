#!/usr/bin/env python3
"""Small SVM comparisons: fresh processes, raw samples, verified tiers and allocation counters."""
import argparse
import datetime
import hashlib
import itertools
import json
import math
import os
from pathlib import Path
import platform
import random
import statistics
import subprocess

WORKLOADS = ('proto-scalar', 'proto-bytes', 'timestamp-json', 'mysql-int4', 'postgres-int4', 'proto-mixed', 'proto-mixed-full')
SERIALIZE_CHECKSUMS = {'proto-scalar': 288, 'proto-bytes': 2097776, 'proto-mixed': 5257}
CHECKSUMS = {'proto-scalar': 54400032992, 'proto-bytes': 2097648, 'timestamp-json': 54400032992,
             'mysql-int4': 35472, 'postgres-int4': 35472, 'proto-mixed': 563, 'proto-mixed-full': 4866}


def cells(schema=12):
    for workload in WORKLOADS:
        if schema < 5 and workload == 'proto-mixed-full':
            continue
        if schema < 3 and workload == 'proto-mixed':
            continue
        engines = ('stock', 'stock-aliased', 'owned', 'buffa') if workload == 'proto-bytes' else (
            ('stock', 'owned', 'buffa') if workload.startswith('proto-') or workload == 'timestamp-json' else ('stock', 'static'))
        if (schema >= 7 and workload in ('proto-scalar', 'proto-bytes')
                or schema >= 11 and workload.startswith('proto-mixed')):
            engines += ('generated-owned',)
        for engine in engines:
            if engine == 'generated-owned':
                for stage in (('ingest',) if workload == 'proto-mixed-full'
                              else ('ingest', 'read-aot', 'interpret', 'peak', 'serialize')):
                    yield workload, engine, stage
                if schema >= 8 and workload == 'proto-scalar':
                    for stage in ('project-aot', 'project-interpret', 'project-peak'):
                        yield workload, engine, stage
                continue
            if workload in ('timestamp-json', 'proto-mixed-full'):
                stages = ('ingest',)
            elif engine.startswith('stock'):
                stages = ('ingest', 'read-aot')
                if schema >= 4:
                    stages += (('interpret-boundary', 'peak-boundary') if workload == 'proto-mixed'
                               else ('interpret', 'peak'))
            else:
                stages = ('ingest', 'interpret', 'peak')
            if schema >= 2 and not engine.startswith('stock') and workload not in ('proto-bytes', 'proto-mixed-full'):
                stages = ('ingest', 'ingest-typed', *stages[1:])
            if schema >= 6 and engine in ('owned', 'buffa') and workload != 'proto-mixed-full':
                stages += ('ingest-generated',)
                if workload != 'timestamp-json':
                    stages += ('read-generated-aot', 'interpret-generated', 'peak-generated')
            if schema >= 7 and (workload in ('proto-scalar', 'proto-bytes')
                                or schema >= 11 and workload == 'proto-mixed'):
                stages += ('serialize',)
            if schema >= 8 and workload == 'proto-scalar' and engine == 'stock':
                stages += ('project-aot', 'project-interpret', 'project-peak')
            if schema >= 12 and workload == 'proto-mixed-full' and engine in ('owned', 'buffa'):
                stages += ('ingest-generated',)
            for stage in stages:
                yield workload, engine, stage


def validate_row(row, cell, metric):
    workload, engine, stage = cell
    if (row['workload'], row['engine'], row['stage']) != cell or row['runtime'] != 'svm':
        raise ValueError('Mislabeled measurement')
    if row['guest_compiled'] != ('peak' in stage):
        raise ValueError('Wrong guest execution tier')
    if row['backend'] != ('SVM_STATIC' if engine == 'buffa' else 'none'):
        raise ValueError('Wrong backend')
    if row['rows'] != 32 or row['checksum'] != (SERIALIZE_CHECKSUMS if stage == 'serialize' else CHECKSUMS)[workload]:
        raise ValueError('Wrong dataset/checksum')
    samples = row[metric]
    if len(samples) != 3 or any(type(n) not in (int, float) or not math.isfinite(n)
                               or n < 0 or (metric == 'ns_per_row' and n == 0) for n in samples):
        raise ValueError('Invalid measurement samples')


def validate(data):
    if data['schema'] not in (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12) or data['forks'] != 3:
        raise ValueError('Expected three isolated processes per cell')
    expected = set(itertools.product(cells(data['schema']), range(3)))
    seen = set()
    for row in data['results']:
        cell = row['workload'], row['engine'], row['stage']
        key = cell, row['fork']
        if key not in expected or key in seen:
            raise ValueError('Duplicate or unexpected cell')
        seen.add(key)
        for metric in ('ns_per_row', 'allocated_bytes_per_row'):
            validate_row(row, cell, metric)
    if seen != expected:
        raise ValueError('Incomplete comparison matrix')


def render(data, path):
    validate(data)
    text = ['# SVM data access comparison', '',
            'Median of three process medians; ranges are min–max process medians, not confidence intervals.',
            'Time and allocations use separate fresh processes. Lower is better.', '',
            '| Workload | Engine | Stage | ns/row (range) | Java heap B/row |',
            '|---|---|---|---:|---:|']
    for cell in cells(data['schema']):
        rows = [r for r in data['results'] if (r['workload'], r['engine'], r['stage']) == cell]
        times = [statistics.median(r['ns_per_row']) for r in rows]
        allocations = [statistics.median(r['allocated_bytes_per_row']) for r in rows]
        text.append('| %s | %s | %s | %.2f (%.2f–%.2f) | %.2f |' % (
            *cell, statistics.median(times), min(times), max(times), statistics.median(allocations)))
    text += ['', 'Stock read-aot is the native-image host baseline. Schema 4 adds stock guest tiers; mixed protobuf uses explicitly labeled boundary projections because its collection getters fail the SVM JIT blocklist.',
             'Ingest includes parsing/materialization and an uncached projected read. Setup is excluded.',
             'ingest-typed uses primitive readLong projections; ingest retains generic read projections.',
             'Generated stages use schema-bound generated getters; read-generated-aot excludes guest calls. Original library-dispatch stages remain.',
             'Schema 7 adds generated-owned concrete scalar/bytes records and serialization. Serialization emits a fresh byte array; Buffa binary views forward the validated original wire, while owned and stock messages encode fields. These operations have equivalent wire semantics, but differ in work performed.',
             ('Schema 9 measures parse-and-project with both parsers eligible for guest compilation (no explicit parser boundary); only invalid-input exception construction crosses a boundary. Both engines use a 10,000 graph-size budget. AOT and interpreter measurements use the same operation.' if data['schema'] >= 9 else 'Schema 8 adds parse-and-project without retaining messages: generated code can fuse in guest compilation; stock parsing stays behind an explicit boundary. AOT and interpreter measurements use the same operation.'),
             ('Schema 10 uses direct concrete generated getter loops matching the stock retained-read harness, without the generic projection adapter.' if data['schema'] >= 10 else 'Earlier generated-record reads use the shared projection adapter.'),
             ('Schema 11 adds concrete generated nested/repeated decoding, full consumption, and matched mixed-message serialization.' if data['schema'] >= 11 else 'Concrete generated nested decoding is not measured in this schema.'),
             ('Schema 12 adds full nested/repeated consumption through generated schema getters, retaining the uncached-interop full-consumption case.' if data['schema'] >= 12 else 'Full borrowed consumption uses uncached interop in earlier schemas.'),
             'Buffa binary input is already in unmanaged memory; owned engines start with Java byte arrays.',
             'Buffa results are scoped borrowed views; stock-aliased borrows immutable ByteString input; stock/owned copy payloads.',
             'The direct stock AOT baseline excludes guest interop and contract enforcement.',
             'MySQL/Postgres measure driver INT4 decoder functions and retained scalar rows, not JDBC queries, transport, or servers.',
             'Java heap counters exclude native allocations and compiler/background threads. Native allocation is not reported as zero.',
             'Shared execution hardware and short samples make these diagnostic comparisons, not performance guarantees.', '',
             f"Binary SHA-256: `{data['binary_sha256']}`", f"Revision: `{data['revision']}`", '']
    path.write_text('\n'.join(text))


def measure(args):
    if platform.system() != 'Linux' or platform.machine() != 'x86_64':
        raise ValueError('Use the Linux amd64 SVM image (or bazel test //benchmarks/comparison:measure)')
    binary = args.binary.resolve()
    args.output.mkdir(parents=True, exist_ok=True)
    cpu = next((line.split(':', 1)[1].strip() for line in Path('/proc/cpuinfo').read_text().splitlines()
                if line.startswith('model name')), platform.processor())
    data = {'schema': 12, 'forks': 3, 'captured_at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
            'revision': args.revision, 'binary_sha256': hashlib.sha256(binary.read_bytes()).hexdigest(),
            'native_views': False,
            'host': {'system': platform.system(), 'machine': platform.machine(), 'cpu': cpu,
                     'kernel': platform.release(), 'logical_cpus': os.cpu_count()},
            'allocation_scope': 'current-thread Java heap; excludes native heap and compiler/background threads',
            'versions': {'graalvm': '25.5.5-elide.28', 'protobuf-java': '4.36.1',
                         'mysql-connector-j': '26.10.0', 'postgresql': '42.7.14'},
            'results': []}
    jobs = list(itertools.product(cells(data['schema']), range(3)))
    random.Random(20261103).shuffle(jobs)
    for cell, fork in jobs:
        print('/'.join(cell) + f' fork {fork + 1}/3', flush=True)
        merged = None
        for mode, metric in [('time', 'ns_per_row'), ('memory', 'allocated_bytes_per_row')]:
            result = subprocess.run([str(binary), *cell, mode], capture_output=True, text=True,
                                    timeout=90)
            if result.returncode:
                raise RuntimeError(result.stdout + '\n' + result.stderr)
            records = [json.loads(line) for line in result.stdout.splitlines() if line.startswith('{')]
            if len(records) != 1:
                raise ValueError('Expected exactly one measurement')
            row = records[0]
            validate_row(row, cell, metric)
            if merged is None:
                merged = row
            else:
                if any(merged[k] != v for k, v in row.items() if k != metric):
                    raise ValueError('Timing/allocation identity mismatch')
                merged[metric] = row[metric]
        merged['fork'] = fork
        data['results'].append(merged)
        (args.output / 'comparison.partial.json').write_text(json.dumps(data, indent=2) + '\n')
    validate(data)
    (args.output / 'comparison.json').write_text(json.dumps(data, indent=2) + '\n')
    render(data, args.output / 'comparison.md')
    (args.output / 'comparison.partial.json').unlink()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    run = sub.add_parser('run')
    run.add_argument('--binary', type=Path, required=True)
    run.add_argument('--output', type=Path, default=Path('build/comparison'))
    run.add_argument('--revision', default=os.getenv('TRUFFLEDATA_BENCH_REVISION', 'unrecorded'))
    report = sub.add_parser('render')
    report.add_argument('input', type=Path)
    report.add_argument('output', type=Path)
    args = parser.parse_args()
    if args.command == 'run':
        measure(args)
    else:
        render(json.loads(args.input.read_text()), args.output)


if __name__ == '__main__':
    main()
