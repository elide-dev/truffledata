#!/usr/bin/env python3
"""cg_annotate.py CALLGRIND_OUT(.gz) IMAGE ROWS SYMBOL_REGEX [MIN_PER_ROW]

Per-instruction self Ir (per row) for one AOT symbol from a --dump-instr=yes callgrind file,
printed beside llvm-objdump disassembly. Shape only."""
import collections
import gzip
import re
import subprocess
import sys

HASH = re.compile(r'_[A-Za-z0-9]{22}$')
TOOLS = '/opt/homebrew/opt/llvm/bin/'


def main():
    path, image, rows, pat = sys.argv[1], sys.argv[2], int(sys.argv[3]), re.compile(sys.argv[4])
    floor = float(sys.argv[5]) if len(sys.argv) > 5 else 0.0
    cost = collections.Counter()
    roots = 0
    fn = None
    cfn = None
    opener = gzip.open if path.endswith('.gz') else open
    with opener(path, 'rt', errors='replace') as f:
        for line in f:
            if line.startswith('fn='):
                fn = HASH.sub('', line[3:].strip())
            elif line.startswith('cfn='):
                cfn = HASH.sub('', line[4:].strip())
            elif line.startswith('calls='):
                if cfn and cfn.endswith('Root_execute'):
                    roots += int(line[6:].split()[0])
                next(f)
            elif line.startswith('0x') and fn and pat.search(fn):
                parts = line.split()
                cost[int(parts[0], 16)] += int(parts[-1])
    n = roots * rows
    nm = subprocess.run([TOOLS + 'llvm-nm', '-S', '--defined-only', image], capture_output=True, text=True).stdout
    for line in nm.splitlines():
        p = line.split()
        if len(p) == 4 and pat.search(HASH.sub('', p[3])) and '%%D' not in p[3]:
            a, s = int(p[0], 16), int(p[1], 16)
            out = subprocess.run([TOOLS + 'llvm-objdump', '-d', '--no-show-raw-insn', f'--start-address={a}',
                                  f'--stop-address={a + s}', image], capture_output=True, text=True).stdout
            total = sum(c for k, c in cost.items() if a <= k < a + s)
            if total == 0:
                continue
            print(f'== {p[3]} total {total / n:.1f} Ir/row (rows {n})')
            for l in out.splitlines():
                m = re.match(r'^\s*([0-9a-f]+):', l)
                if not m:
                    continue
                c = cost.get(int(m.group(1), 16), 0) / n
                if c >= floor:
                    print(f'{c:7.2f} {l.strip()}')


if __name__ == '__main__':
    main()
