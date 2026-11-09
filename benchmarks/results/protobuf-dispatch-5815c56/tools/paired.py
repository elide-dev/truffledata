#!/usr/bin/env python3
"""paired.py MERGED_RESULTS.json [--all] : baseline vs candidate ns/row with fork ranges.

Prints a Markdown table of the cells both binaries measured (interpret/peak by default),
with change, whether fork ranges overlap, and Java bytes/row. Advisory shared-worker timings."""
import json
import sys


def main():
    d = json.load(open(sys.argv[1]))
    everything = '--all' in sys.argv
    by = {}
    for s in d['summary']:
        by[(s['binary'], s['engine'], s['mode'], s['stage'])] = s
    rows = []
    for (binary, engine, mode, stage), c in sorted(by.items(), key=lambda kv: kv[0][2:] + kv[0][1:2]):
        if binary != 'candidate':
            continue
        b = by.get(('baseline', engine, mode, stage))
        if not b:
            continue
        if not everything and stage not in ('interpret', 'peak'):
            continue
        change = c['ns_per_row'] / b['ns_per_row'] - 1
        overlap = not (c['ns_max'] < b['ns_min'] or b['ns_max'] < c['ns_min'])
        rows.append((mode, stage, engine, b, c, change, overlap))
    print('| Mode | Stage | Engine | Before ns/row (range) | After ns/row (range) | Change | Ranges | B/row before → after |')
    print('|---|---|---|---:|---:|---:|---|---|')
    for mode, stage, engine, b, c, change, overlap in rows:
        print(f"| {mode} | {stage} | {engine} | {b['ns_per_row']:.3f} ({b['ns_min']:.3f}–{b['ns_max']:.3f}) "
              f"| {c['ns_per_row']:.3f} ({c['ns_min']:.3f}–{c['ns_max']:.3f}) | {100 * change:+.1f}% "
              f"| {'overlap' if overlap else 'disjoint'} | {b['java_bytes_per_row']:.2f} → {c['java_bytes_per_row']:.2f} |")


if __name__ == '__main__':
    main()
