#!/usr/bin/env python3
"""Summarize coverage gaps and enforce complete method coverage of core access surfaces."""
import json
from pathlib import Path
import sys

ACCESS_CLASSES = {
    'AbstractRow', 'DataArray', 'DataLibrary', 'DynamicRecord', 'ExactNumber',
    'Missing', 'ReadBooleanNode', 'ReadDoubleNode', 'ReadLengthNode', 'ReadLongNode',
}


def analyze(text):
    summary = {}
    for record in text.split('end_of_record'):
        lines = record.splitlines()
        source = next((line[3:] for line in lines if line.startswith('SF:')), None)
        if source is None:
            continue
        hits = {int(line[3:].split(',')[0]): int(line.split(',')[1])
                for line in lines if line.startswith('DA:')}
        starts = {line.split(',', 1)[1]: int(line[3:].split(',', 1)[0])
                  for line in lines if line.startswith('FN:')}
        functions = {line.split(',', 1)[1]: int(line[5:].split(',', 1)[0])
                     for line in lines if line.startswith('FNDA:')}
        # Ignore constructors and abstract/interface declarations (LCOV start line zero).
        methods = {name: count for name, count in functions.items()
                   if '::<init>' not in name and '::<clinit>' not in name
                   and starts.get(name, 1) != 0}
        branches = [line[5:].split(',') for line in lines if line.startswith('BRDA:')]
        summary[source] = {
            'branches_hit': sum(parts[3] not in ('0', '-') for parts in branches),
            'branches_total': len(branches),
            'uncovered_branches': [parts[:3] for parts in branches if parts[3] in ('0', '-')],
            'lines_hit': sum(count > 0 for count in hits.values()),
            'lines_total': len(hits),
            'uncovered_lines': [line for line, count in hits.items() if count == 0],
            'methods_hit': sum(count > 0 for count in methods.values()),
            'methods_total': len(methods),
            'uncovered_methods': [name for name, count in methods.items() if count == 0],
        }
    return summary


def enforce(summary):
    for name in sorted(ACCESS_CLASSES):
        source = f'core/src/main/java/dev/elide/truffledata/{name}.java'
        entry = summary.get(source)
        if not entry or not entry['methods_total']:
            raise ValueError('Missing access-method coverage: ' + source)
        if entry['uncovered_methods']:
            raise ValueError('Uncovered access methods in ' + source + ': ' + ', '.join(entry['uncovered_methods']))


def main():
    summary = analyze(Path('build/reports/coverage/lcov.info').read_text())
    Path('build/reports/coverage/gaps.json').write_text(json.dumps(summary, indent=2) + '\n')
    for source, counts in sorted(summary.items()):
        print(f"{counts['lines_hit']}/{counts['lines_total']} lines, "
              f"{counts['methods_hit']}/{counts['methods_total']} methods: {source}")
    enforce(summary)


if __name__ == '__main__':
    main()
