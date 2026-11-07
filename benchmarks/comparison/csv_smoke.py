"""CSV comparison smoke: every engine agrees on every fixture, and the typed read loops survive
partial evaluation within fixed graph and allocation budgets (CSV.md)."""
import json
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
(output / 'csv-compilation.log').write_text(process.stderr)
process.check_returncode()

# Per root: (max nodes, loops, max graph allocations); every rule requires zero invokes.
RULES = {
    'CsvReads[truffledata-plan]': (1000, 1, 0),
    # Stock baseline (reported, not gated on allocation): core Batch keeps FLOAT64 and BOOLEAN
    # cells boxed, and its read graph keeps four allocation sites.
    'CsvReads[batch-fastcsv]': (1000, 1, None),
}
seen = {}
name = None
for line in process.stderr.splitlines():
    header = re.match(r'^\[engine\] Expansion tree for (\S+) after truffleTier:', line)
    if header:
        name = header.group(1)
    if re.match(r'^<root>\s', line):
        columns = line.split('|')
        if len(columns) < 3 or name is None:
            raise AssertionError('Malformed compiler expansion row: ' + line)
        values = tuple(map(int, columns[1].split()))
        if len(values) != 7:
            raise AssertionError('Unexpected compiler expansion columns: ' + line)
        if name not in RULES:
            raise AssertionError('Unexpected compiled CSV root: ' + name)
        nodes, _, _, _, loops, invokes, allocations = values
        limit, expected_loops, max_allocations = RULES[name]
        if not (0 < nodes <= limit and loops == expected_loops and invokes == 0
                and (max_allocations is None or allocations <= max_allocations)):
            raise AssertionError(f'CSV graph {name} exceeded {RULES[name]} or retained calls: {line}')
        print(f'{name}: {nodes} nodes, {loops} loop(s), {invokes} invokes, {allocations} allocations')
        seen[name] = seen.get(name, 0) + 1
        name = None
if any(root not in seen for root in RULES):
    raise AssertionError(f'Missing compiler expansion evidence for CSV read roots: {seen}')

records = [json.loads(line) for line in process.stdout.splitlines() if line.startswith('{')]
# Peak typed reads allocate only the harness's own per-call boxing (1 B per row at most).
for engine in ('truffledata-plan',):
    rows = [r for r in records if r['workload'] == 'typed-reads' and r['engine'] == engine
            and r['stage'] == 'peak']
    if len(rows) != 1 or not rows[0]['guest_compiled']:
        raise AssertionError(f'Missing compiled typed-reads record for {engine}')
    if max(rows[0]['allocated_bytes_per_row']) > 1.0:
        raise AssertionError(f'{engine} peak exceeded 1.0 Java B/row: {rows[0]}')
throughput = [r for r in records if r['workload'] != 'typed-reads']
if len(throughput) < 60:
    raise AssertionError(f'Expected every throughput engine, saw {len(throughput)}')
print(f'CSV smoke passed: {len(throughput)} throughput cells agree per workload; '
      f'{sum(seen.values())} typed read compilations within budget')
