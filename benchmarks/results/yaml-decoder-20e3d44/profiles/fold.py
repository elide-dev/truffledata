#!/usr/bin/env python3
"""Symbolize sampler.h output against a binary with llvm-symbolizer (inlined frames expanded) and
print (1) self time by function, (2) inclusive time by function, (3) folded stacks to a file.

usage: fold.py <binary> <samples.txt> <folded-out.txt>"""
import collections, subprocess, sys

binary, samples_path, folded_path = sys.argv[1:4]
samples = [l.split() for l in open(samples_path) if l.strip()]
addrs = sorted({a for s in samples for a in s})
proc = subprocess.run(['/opt/homebrew/opt/llvm/bin/llvm-symbolizer', '--obj=' + binary, '--inlining',
                       '--functions=short', '--demangle', '--no-untag-addresses'] if False else
                      ['/opt/homebrew/opt/llvm/bin/llvm-symbolizer', '--obj=' + binary, '--inlining',
                       '--functions=linkage', '--demangle'],
                      input='\n'.join('0x' + a for a in addrs) + '\n', capture_output=True, text=True)
blocks = proc.stdout.strip('\n').split('\n\n')
sym = {}
for a, block in zip(addrs, blocks):
    lines = block.split('\n')
    funcs = []
    for i in range(0, len(lines) - 1, 2):
        name = lines[i]
        # shorten templates/args
        short = name.split('(')[0]
        short = short.replace('c4::yml::ParseEngine<truffledata::yaml::handler>::', 'Engine::')
        short = short.replace('c4::yml::ParseEngine<c4::yml::extra::EventHandlerInts>::', 'Engine::')
        short = short.replace('truffledata::yaml::', 'yaml::').replace('truffledata::tape::', 'tape::')
        funcs.append(short)
    sym[a] = funcs  # innermost first (inlined chain)

self_t = collections.Counter()
incl = collections.Counter()
folded = collections.Counter()
for s in samples:
    stack = []
    for a in s:
        stack.extend(sym.get(a, ['?' + a]))
    self_t[stack[0]] += 1
    for f in set(stack):
        incl[f] += 1
    folded[';'.join(reversed(stack))] += 1
n = len(samples)
print(f'{n} samples')
print('--- self')
for f, c in self_t.most_common(40):
    print(f'{100.0*c/n:6.2f}%  {c:6d}  {f}')
print('--- inclusive')
for f, c in incl.most_common(45):
    print(f'{100.0*c/n:6.2f}%  {c:6d}  {f}')
with open(folded_path, 'w') as out:
    for k, c in folded.most_common():
        out.write(f'{k} {c}\n')
