#!/usr/bin/env python3
"""AOT symbol gate: code-shape facts for named methods in a Native Image.

Reports instruction count, call count, stack-frame size and stack traffic for every AOT
symbol whose demangled name matches a pattern, and optionally asserts budgets. It is the
admission check for read-path changes (plan 2026-11-08-beat-stock-svm P0.6): the same
method diagnosed the CSV fused-framing regression (csv-3f23d0c/probes/frame-disassembly.md).

Only images built with `-H:-DeleteLocalSymbols` (the `*-profile` images) carry symbols;
the production images report "no symbols" and are rejected explicitly. Native Image names
a method `Owner_method_<22-char hash>`; `%%D` marks the deoptimization-target twin, which
is skipped unless `--deopt` is given. Patterns are regular expressions matched with
`re.search` against the stripped `Owner_method` name, so `^SlabFormat_getInt$` is exact
and `SlabRow\\$.*Slot_longOf` matches every slot-flavoured long read.

Budgets are a JSON object `{pattern: {"instructions": N, "calls": N, "frame": N}}` (keys
starting with `_` are comments); a pattern that matches several symbols applies to each. Missing symbols fail a budget run
(a renamed or dead method must be re-pinned, never silently passed). Timings never come
from this tool: it describes code shape on one ISA, and only the ISA it was run on.

Usage:
  aot_symbols.py IMAGE --pattern 'SlabFormat_get' --pattern '^Accessor_bound$' [--json out]
  aot_symbols.py IMAGE --budgets budgets.json [--json out] [--markdown out]
  aot_symbols.py IMAGE --list 'ReadLongNode'      # enumerate matching symbol names only
"""

import argparse
import json
import re
import shutil
import subprocess
import sys

HASH = re.compile(r'_[A-Za-z0-9]{22}$')
DEOPT = '%%D'
NM_LINE = re.compile(r'^([0-9a-f]+)\s+([0-9a-f]+)\s+([tTwW])\s+(\S+)$')
INSN = re.compile(r'^\s*[0-9a-f]+:\s*(?:(?:[0-9a-f]{2}\s)+\s*)?([a-z][a-z0-9.]*)\b(.*)$')
CALL = re.compile(r'^(call[lq]?|bl|blr)$')
X86_FRAME_CHECK = re.compile(r'leaq?\s+-0x([0-9a-f]+)\(%rsp\),\s*%r')
X86_SUB_RSP = re.compile(r'subq?\s+\$0x([0-9a-f]+),\s*%rsp')
ARM_SUB_SP = re.compile(r'sub\s+sp,\s*sp,\s*#((?:0x)?[0-9a-f]+)')
X86_STACK = re.compile(r'\((%rsp|%rbp)\)')
ARM_STACK = re.compile(r'\[sp(?:,|\])')
ARM_STORE = re.compile(r'^st(p|r|ur|rb|rh)$')
ARM_LOAD = re.compile(r'^ld(p|r|ur|rb|rh|rsw|rsb|rsh)$')


def strip(symbol):
    """`Owner_method_<hash>` -> (`Owner_method`, deopt?)."""
    deopt = symbol.endswith(DEOPT) or DEOPT + '_' in symbol
    base = HASH.sub('', symbol)
    if base.endswith(DEOPT):
        base = base[: -len(DEOPT)]
    return base, deopt


def parse_nm(text):
    """`nm -S --defined-only` lines -> [(symbol, address, size)] for code symbols."""
    symbols = []
    for line in text.splitlines():
        match = NM_LINE.match(line.strip())
        if match:
            address, size, _, name = match.groups()
            symbols.append((name, int(address, 16), int(size, 16)))
    return symbols


def select(symbols, patterns, deopt=False):
    """Symbols whose stripped name matches any pattern, keyed by the pattern they matched."""
    compiled = [(pattern, re.compile(pattern)) for pattern in patterns]
    chosen = {}
    for name, address, size in symbols:
        base, is_deopt = strip(name)
        if is_deopt and not deopt:
            continue
        for pattern, regex in compiled:
            if regex.search(base):
                chosen.setdefault(pattern, []).append({'symbol': name, 'name': base, 'address': address, 'size': size})
    return chosen


def analyze_disassembly(text):
    """Code-shape counts for one symbol's objdump/llvm-objdump disassembly."""
    instructions = calls = loads = stores = 0
    frame = None
    call_targets = []
    for line in text.splitlines():
        match = INSN.match(line)
        if not match:
            continue
        mnemonic, operands = match.group(1), match.group(2)
        instructions += 1
        if CALL.match(mnemonic):
            calls += 1
            target = re.search(r'<([^>+]+)', operands)
            call_targets.append(strip(target.group(1))[0] if target else operands.strip())
        if frame is None:
            text_line = mnemonic + ' ' + operands
            check = X86_FRAME_CHECK.search(text_line) if mnemonic.startswith('lea') else None
            if check:
                frame = int(check.group(1), 16)
            elif mnemonic.startswith('sub'):
                sub = X86_SUB_RSP.search(text_line)
                if sub:
                    frame = int(sub.group(1), 16)
                else:
                    sub = ARM_SUB_SP.search(text_line)
                    if sub:
                        frame = int(sub.group(1), 16 if sub.group(1).startswith('0x') else 10)
        if X86_STACK.search(operands) and not mnemonic.startswith('lea'):
            # AT&T syntax: the destination is the last operand.
            if re.search(r'\((%rsp|%rbp)\)\s*$', operands):
                stores += 1
            else:
                loads += 1
        elif ARM_STACK.search(operands):
            if ARM_STORE.match(mnemonic):
                stores += 1
            elif ARM_LOAD.match(mnemonic):
                loads += 1
    return {
        'instructions': instructions,
        'calls': calls,
        'frame': frame,
        'stack_loads': loads,
        'stack_stores': stores,
        'call_targets': sorted(set(call_targets)),
    }


def tool(*names):
    for name in names:
        path = shutil.which(name)
        if path:
            return path
    raise SystemExit('none of %s on PATH' % ', '.join(names))


def disassemble(image, symbol, objdump):
    command = [objdump, '-d', '--no-show-raw-insn']
    if 'llvm' in objdump:
        command.append('--disassemble-symbols=' + symbol)
    else:
        command.append('--disassemble=' + symbol)
    return subprocess.run(command + [image], check=True, capture_output=True, text=True).stdout


def report(image, patterns, deopt=False, nm=None, objdump=None):
    nm = nm or tool('llvm-nm', 'nm')
    objdump = objdump or tool('llvm-objdump', 'objdump')
    listing = subprocess.run([nm, '-S', '--defined-only', image], capture_output=True, text=True)
    symbols = parse_nm(listing.stdout)
    if not symbols:
        raise SystemExit('%s carries no symbols; use an image built with -H:-DeleteLocalSymbols (the *-profile images)' % image)
    chosen = select(symbols, patterns, deopt)
    rows = []
    for pattern, entries in chosen.items():
        for entry in entries:
            shape = analyze_disassembly(disassemble(image, entry['symbol'], objdump))
            rows.append({'pattern': pattern, **entry, **shape})
    rows.sort(key=lambda row: (row['pattern'], row['name'], row['address']))
    return {'image': image, 'nm': nm, 'objdump': objdump, 'symbols': rows,
            'unmatched': [pattern for pattern in patterns if pattern not in chosen]}


def check_budgets(result, budgets):
    """Budget violations and missing patterns, as human-readable strings."""
    failures = []
    by_pattern = {}
    for row in result['symbols']:
        by_pattern.setdefault(row['pattern'], []).append(row)
    for pattern, limits in budgets.items():
        rows = by_pattern.get(pattern)
        if not rows:
            failures.append('%s: no symbol matched (renamed or dead; re-pin the budget)' % pattern)
            continue
        for row in rows:
            for metric, limit in limits.items():
                value = row.get(metric)
                if value is None:
                    failures.append('%s %s: %s not measured' % (pattern, row['name'], metric))
                elif value > limit:
                    failures.append('%s %s: %s %d > budget %d' % (pattern, row['name'], metric, value, limit))
    return failures


def markdown(result):
    lines = ['| Symbol | Instructions | Calls | Frame B | Stack loads | Stack stores |', '|---|---:|---:|---:|---:|---:|']
    for row in result['symbols']:
        lines.append('| `%s` | %d | %d | %s | %d | %d |' % (
            row['name'], row['instructions'], row['calls'],
            '?' if row['frame'] is None else row['frame'], row['stack_loads'], row['stack_stores']))
    for pattern in result['unmatched']:
        lines.append('| `%s` | (no match) | | | | |' % pattern)
    return '\n'.join(lines) + '\n'


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('image')
    parser.add_argument('--pattern', action='append', default=[], help='regex over Owner_method names (repeatable)')
    parser.add_argument('--budgets', help='JSON {pattern: {instructions|calls|frame: limit}}; failing budgets exit 1')
    parser.add_argument('--list', metavar='PATTERN', help='print matching symbol names and sizes, nothing else')
    parser.add_argument('--deopt', action='store_true', help='include %%%%D deoptimization-target twins')
    parser.add_argument('--json', help='write the full report here')
    parser.add_argument('--markdown', help='write a Markdown table here')
    parser.add_argument('--nm')
    parser.add_argument('--objdump')
    args = parser.parse_args(argv)
    if args.list:
        listing = subprocess.run([args.nm or tool('llvm-nm', 'nm'), '-S', '--defined-only', args.image],
                                 capture_output=True, text=True)
        for pattern, entries in select(parse_nm(listing.stdout), [args.list], args.deopt).items():
            for entry in entries:
                print('%6d  %s' % (entry['size'], entry['symbol']))
        return 0
    budgets = {}
    if args.budgets:
        with open(args.budgets) as handle:
            budgets = {k: v for k, v in json.load(handle).items() if not k.startswith('_')}
    patterns = list(dict.fromkeys(args.pattern + list(budgets)))
    if not patterns:
        parser.error('give --pattern, --budgets or --list')
    result = report(args.image, patterns, args.deopt, args.nm, args.objdump)
    result['budgets'] = budgets
    result['failures'] = check_budgets(result, budgets) if budgets else []
    if args.json:
        with open(args.json, 'w') as handle:
            json.dump(result, handle, indent=2)
    table = markdown(result)
    if args.markdown:
        with open(args.markdown, 'w') as handle:
            handle.write(table)
    sys.stdout.write(table)
    for failure in result['failures']:
        print('BUDGET FAIL ' + failure, file=sys.stderr)
    return 1 if result['failures'] else 0


if __name__ == '__main__':
    sys.exit(main())
