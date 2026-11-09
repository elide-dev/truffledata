#!/usr/bin/env python3
"""Attribute a callgrind profile of a Native Image to functions and to inlined source files.

Reads one uncompressed callgrind output (`--compress-strings=no --compress-pos=no`; a `.gz`
file is accepted) and reports, for the collected region:

- `functions`: self and inclusive Ir per AOT method, hash suffix stripped (`Owner_method`);
- `files`: self Ir per (method, source file). With a `-g` image the line table maps inlined
  code to its own file, so a host-inlining root such as `IndexScan_frameFused` splits into
  `IndexScan.java`, `BatchBuilder.java`, `Numbers.java`, ... (`fi=`/`fe=` records);
- `lines`: self Ir per (file, line) for the files named with `--lines`.

Instruction counts only; never an elapsed-time claim (plan 2026-11-08-beat-stock-svm P0.5).

Usage: callgrind_split.py CALLGRIND_OUT [--top N] [--lines File.java ...] [--json OUT]
"""

import argparse
import gzip
import json
import re
import sys

HASH = re.compile(r'_[A-Za-z0-9]{22}$')


def stripped(name):
    base = HASH.sub('', name)
    return base[:-3] if base.endswith('%%D') else base


def parse(path):
    opener = gzip.open if path.endswith('.gz') else open
    self_fn, incl_calls, self_file, self_line = {}, {}, {}, {}
    totals = None
    fn = fl = fi = None
    pending_call = False
    events = None
    npos = 1  # position columns before the cost (`positions: instr line` gives two)
    self_addr = {}
    with opener(path, 'rt', errors='replace') as handle:
        for raw in handle:
            line = raw.rstrip('\n')
            if not line:
                continue
            head = line[0]
            if head.isdigit() or head in '+-*':
                fields = line.split()
                if len(fields) <= npos:
                    continue
                cost = int(fields[npos])
                if pending_call:
                    incl_calls[fn] = incl_calls.get(fn, 0) + cost
                    pending_call = False
                    continue
                source = fi or fl
                self_fn[fn] = self_fn.get(fn, 0) + cost
                key = (fn, source)
                self_file[key] = self_file.get(key, 0) + cost
                if npos == 2:
                    ak = (fn, int(fields[0], 16))
                    self_addr[ak] = self_addr.get(ak, 0) + cost
                    lk = (source, int(fields[1]))
                else:
                    lk = (source, int(fields[0]))
                self_line[lk] = self_line.get(lk, 0) + cost
                continue
            if line.startswith('fn='):
                fn = stripped(line[3:].split(') ', 1)[-1] if line[3:].startswith('(') else line[3:])
                fi = None
            elif line.startswith('fl='):
                fl = line[3:].split(') ', 1)[-1] if line[3:].startswith('(') else line[3:]
                fi = None
            elif line.startswith('fi=') or line.startswith('fe='):
                value = line[3:].split(') ', 1)[-1] if line[3:].startswith('(') else line[3:]
                fi = None if value == fl else value
            elif line.startswith('calls='):
                pending_call = True
            elif line.startswith('totals:') or line.startswith('summary:'):
                totals = int(line.split()[1])
            elif line.startswith('events:'):
                events = line.split()[1:]
            elif line.startswith('positions:'):
                npos = len(line.split()) - 1
    if events and events[0] != 'Ir':
        raise SystemExit('expected Ir as the first event, got %s' % events)
    inclusive = {f: self_fn.get(f, 0) + incl_calls.get(f, 0) for f in set(self_fn) | set(incl_calls)}
    return {'totals': totals, 'self': self_fn, 'inclusive': inclusive, 'files': self_file,
            'lines': self_line, 'addresses': self_addr}


def short(path):
    return path.rsplit('/', 1)[-1] if path else '?'


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('profile')
    parser.add_argument('--top', type=int, default=40)
    parser.add_argument('--lines', nargs='*', default=[])
    parser.add_argument('--json')
    args = parser.parse_args(argv)
    result = parse(args.profile)
    total = result['totals'] or sum(result['self'].values())
    print('total Ir %d' % total)
    print('\n# self Ir by method (top %d)' % args.top)
    for name, cost in sorted(result['self'].items(), key=lambda kv: -kv[1])[: args.top]:
        print('%14d %6.2f%%  incl %6.2f%%  %s' % (cost, 100 * cost / total,
                                                  100 * result['inclusive'].get(name, 0) / total, name))
    print('\n# self Ir by (method, inlined source file) (top %d)' % args.top)
    for (name, source), cost in sorted(result['files'].items(), key=lambda kv: -kv[1])[: args.top]:
        print('%14d %6.2f%%  %s  [%s]' % (cost, 100 * cost / total, name, short(source)))
    for wanted in args.lines:
        print('\n# self Ir by line of %s' % wanted)
        rows = [(k, v) for k, v in result['lines'].items() if short(k[0]) == wanted]
        for (source, number), cost in sorted(rows, key=lambda kv: -kv[1])[: args.top]:
            print('%14d %6.2f%%  %s:%d' % (cost, 100 * cost / total, short(source), number))
    if args.json:
        out = {
            'profile': args.profile,
            'total': total,
            'self': result['self'],
            'inclusive': result['inclusive'],
            'files': [{'method': k[0], 'file': short(k[1]), 'ir': v} for k, v in result['files'].items()],
        }
        with open(args.json, 'w') as handle:
            json.dump(out, handle, indent=1, sort_keys=True)
    return 0


if __name__ == '__main__':
    sys.exit(main())
