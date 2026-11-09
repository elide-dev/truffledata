#!/usr/bin/env python3
"""Summarize a Graal C1Visualizer .cfg dump: node classes and surviving call targets in the
"Final HIR schedule" plus LIR call ops in the last LIR section. Shape evidence only."""
import collections
import re
import sys

path = sys.argv[1]
text = open(path, errors="replace").read()
start = text.index('name "Final HIR schedule"')
end = text.index('name "After LIR generation"')
hir = text[start:end]
instr = re.compile(r"instruction <@([A-Za-z0-9_#$.]+)\|@([^ ]*) ([^<]*)")
classes = collections.Counter()
invokes = collections.Counter()
details = collections.Counter()
for m in instr.finditer(hir):
    short, full, rest = m.group(1), m.group(2), m.group(3).strip()
    classes[short.split("#")[0]] += 1
    if short.startswith("Invoke") or short.startswith("ForeignCall"):
        invokes[short] += 1
    if short.startswith(("Invoke", "ForeignCall", "Monitor", "Membar", "Volatile", "Ordered")):
        details[(short + " " + rest)[:170]] += 1
total = sum(classes.values())
print(f"HIR nodes in final schedule: {total}")
print("node classes (top 40):")
for name, count in classes.most_common(40):
    print(f"  {count:6d} {name}")
print("call-like nodes:")
for name, count in details.most_common(100):
    print(f"  {count:4d} {name}")
# Final LIR calls.
lir_start = text.rfind('name "After FinalCodeAnalysisStage"')
lir = text[lir_start:]
calls = collections.Counter()
for m in re.finditer(r"(DIRECT_CALL|INDIRECT_CALL|FOREIGN_CALL|CALL)[^\n]*", lir):
    calls[m.group(0)[:150]] += 1
print(f"LIR call ops after final code analysis: {sum(calls.values())}")
for name, count in calls.most_common(60):
    print(f"  {count:4d} {name}")
