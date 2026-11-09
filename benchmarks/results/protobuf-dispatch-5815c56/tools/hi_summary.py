#!/usr/bin/env python3
"""hi_summary.py BUILD_LOG : compact Truffle host-inlining decisions per root.

Prints, for each Root[...] tree in a -H:Log=HostInliningPhase build log, each INLINE/CUTOFF
line as: depth, decision, method, monomorphic, invoke, cost, subTreeCost, treeInvokes, reason.
Duplicate roots (the same method logged twice) are printed once."""
import re
import sys

ROOT = re.compile(r'^(\s*)Root\[(.+)\]\s*$')
LINE = re.compile(r'^(\s*)(INLINE|CUTOFF)\s+(\S+)\((?:[^)]*)\)\s+\[(.*)\]\s*$')


def field(attrs, name):
    m = re.search(name + r'\s+([^,]+)', attrs)
    return m.group(1).strip() if m else '-'


def main():
    seen = set()
    current = None
    base = 0
    printing = False
    with open(sys.argv[1], errors='replace') as f:
        for line in f:
            m = ROOT.match(line)
            if m:
                current = m.group(2)
                base = len(m.group(1))
                printing = current not in seen
                seen.add(current)
                if printing:
                    print(f'\n## {current}')
                continue
            m = LINE.match(line)
            if m and printing:
                depth = (len(m.group(1)) - base) // 2
                attrs = m.group(4)
                reason = field(attrs, 'reason')
                print(f"{'  ' * depth}{m.group(2):6s} {m.group(3).split('.', 3)[-1][-70:]}"
                      f"  mono={field(attrs, 'monomorphic')} invoke={field(attrs, 'invoke')}"
                      f" cost={field(attrs, 'cost')} sub={field(attrs, 'subTreeCost')}"
                      f" inv={field(attrs, 'treeInvokes')}" + (f"  | {reason[:90]}" if reason != '-' else ''))
            elif printing and line.strip() == '':
                pass


if __name__ == '__main__':
    main()
