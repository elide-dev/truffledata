#!/usr/bin/env python3
"""cg_edges.py CALLGRIND_OUT(.gz) ROWS [MIN_PER_ROW] : caller -> callee call counts per row."""
import collections
import gzip
import re
import sys

HASH = re.compile(r'_[A-Za-z0-9]{22}$')


def main():
    path, rows = sys.argv[1], int(sys.argv[2])
    floor = float(sys.argv[3]) if len(sys.argv) > 3 else 0.05
    edges = collections.Counter()
    fn = cfn = None
    opener = gzip.open if path.endswith('.gz') else open
    with opener(path, 'rt', errors='replace') as f:
        for line in f:
            if line.startswith('fn='):
                fn = HASH.sub('', line[3:].strip())
            elif line.startswith('cfn='):
                cfn = HASH.sub('', line[4:].strip())
            elif line.startswith('calls='):
                edges[(fn, cfn)] += int(line[6:].split()[0])
                next(f)
    roots = sum(v for (a, b), v in edges.items() if b.endswith('Root_execute'))
    n = roots * rows
    total = 0.0
    for (a, b), v in edges.most_common():
        if v / n < floor:
            break
        total += v / n
        print(f'{v / n:7.2f}  {a[-60:]:60s} -> {b[-70:]}')
    print(f'total calls/row over floor: {total:.1f}')


if __name__ == '__main__':
    main()
