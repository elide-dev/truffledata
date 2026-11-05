"""Require JSON read loops and their getters to survive partial evaluation as one graph."""
import os
from pathlib import Path
import re
import subprocess
import sys

process = subprocess.run(
    [sys.argv[1], '-Dpolyglot.compiler.TraceMethodExpansion=truffleTier', '--smoke'],
    capture_output=True, text=True)
print(process.stdout, end='')
print(process.stderr, end='', file=sys.stderr)
output = Path(os.environ['TEST_UNDECLARED_OUTPUTS_DIR'])
output.mkdir(parents=True, exist_ok=True)
(output / 'json-compilation.log').write_text(process.stderr)
process.check_returncode()
# Columns: Count Size Cycles Ifs Loops Invokes Allocs. Fail closed if trace format changes.
graphs = []
for line in process.stderr.splitlines():
    if re.match(r'^<root>\s', line):
        columns = line.split('|')
        if len(columns) < 3:
            raise AssertionError('Malformed compiler expansion row: ' + line)
        values = tuple(map(int, columns[1].split()))
        if len(values) != 7:
            raise AssertionError('Unexpected compiler expansion columns: ' + line)
        graphs.append(values)
        nodes, _, _, _, loops, invokes, allocations = values
        if not (0 < nodes <= 1000 and loops == 1 and invokes == 0 and allocations == 0):
            raise AssertionError('JSON read graph exceeded budget or retained calls: ' + line)
# Each engine must compile; smoke's helper-level marker also checks the actual executed tier.
if len(graphs) < 3:
    raise AssertionError('Missing compiler expansion evidence for JSON engines')
print(f'JSON graph checks passed: {len(graphs)} compilations, one loop, no calls or allocations')
