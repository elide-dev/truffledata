#!/usr/bin/env python3
"""cg_summary.py CALLGRIND_OUT[.gz] ROWS_PER_CALL [TOP] : exclusive Ir per function per row.

Parses an uncompressed-names callgrind file (--compress-strings=no), sums self cost per fn,
counts calls into *SharedRoot_execute* / *StockRoot_execute* to normalise per row, and also
reports per-row call counts of each callee (calls= lines). Shape only, no timing."""
import collections
import gzip
import re
import sys

HASH = re.compile(r'_[A-Za-z0-9]{22}$')


def strip(n):
    return HASH.sub('', n.strip())


def main():
    path, rows = sys.argv[1], int(sys.argv[2])
    top = int(sys.argv[3]) if len(sys.argv) > 3 else 40
    opener = gzip.open if path.endswith('.gz') else open
    self_cost = collections.Counter()
    calls = collections.Counter()
    fn = None
    pending_call = None
    with opener(path, 'rt', errors='replace') as f:
        for line in f:
            if line.startswith('fn='):
                fn = strip(line[3:])
                continue
            if line.startswith('cfn='):
                pending_call = strip(line[4:])
                continue
            if line.startswith('calls='):
                n = int(line[6:].split()[0])
                calls[pending_call] += n
                skip_next = True
                # the next cost line is inclusive cost of the call; consume it
                nxt = next(f)
                continue
            if line[:1].isdigit() or line[:1] in '+-*' or line.startswith('0x'):
                parts = line.split()
                if len(parts) >= 2 and fn is not None:
                    try:
                        self_cost[fn] += int(parts[-1])
                    except ValueError:
                        pass
    roots = sum(v for k, v in calls.items() if k.endswith('Root_execute'))
    total = sum(self_cost.values())
    n = max(roots * rows, 1)
    print(f'root executions {roots}, rows {n}, total Ir {total}, Ir/row {total / n:.1f}')
    for name, cost in self_cost.most_common(top):
        print(f'{cost / n:9.1f} Ir/row {100 * cost / total:5.1f}%  calls/row {calls[name] / n:6.2f}  {name}')


if __name__ == '__main__':
    main()
