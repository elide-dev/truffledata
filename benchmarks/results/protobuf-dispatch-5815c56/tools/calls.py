#!/usr/bin/env python3
"""calls.py IMAGE PATTERN... : for each matching AOT symbol, instruction count and call targets.

Direct call targets are resolved to stripped symbol names; indirect calls are shown as
'indirect <operand>'. Shape only (x86-64 AT&T from llvm-objdump)."""
import collections
import re
import subprocess
import sys

HASH = re.compile(r'_[A-Za-z0-9]{22}$')
TOOLS = '/opt/homebrew/opt/llvm/bin/'


def strip(n):
    return HASH.sub('', n)


def main():
    image, pats = sys.argv[1], sys.argv[2:]
    nm = subprocess.run([TOOLS + 'llvm-nm', '-S', '--defined-only', image],
                        capture_output=True, text=True).stdout
    syms = []
    for line in nm.splitlines():
        p = line.split()
        if len(p) == 4 and p[2] in 'tTwW':
            syms.append((p[3], int(p[0], 16), int(p[1], 16)))
    for pat in pats:
        rx = re.compile(pat)
        for name, addr, size in syms:
            b = strip(name)
            if '%%D' in name or not rx.search(b):
                continue
            out = subprocess.run([TOOLS + 'llvm-objdump', '-d', '--no-show-raw-insn',
                                  f'--start-address={addr}', f'--stop-address={addr + size}', image],
                                 capture_output=True, text=True).stdout
            insns = 0
            calls = collections.Counter()
            for line in out.splitlines():
                m = re.match(r'^\s*([0-9a-f]+):\s+(\S+)\s*(.*)$', line)
                if not m:
                    continue
                insns += 1
                op, rest = m.group(2), m.group(3)
                if op.startswith('call'):
                    t = re.search(r'<([^>+]+)(\+0x[0-9a-f]+)?>', rest)
                    if t and not rest.strip().startswith('*'):
                        calls[strip(t.group(1))] += 1
                    else:
                        calls['indirect ' + rest.strip()[:40]] += 1
            print(f'== {b}  instr={insns} calls={sum(calls.values())}')
            for c, k in calls.most_common():
                print(f'   {k:3d}  {c}')


if __name__ == '__main__':
    main()
