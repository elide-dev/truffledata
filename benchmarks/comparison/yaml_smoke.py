"""Fail closed on missing YAML checksums, compiler evidence and allocation bounds."""
import json
import os
from pathlib import Path
import re
import subprocess
import sys

process = subprocess.run([sys.argv[1], '-Dpolyglot.compiler.TraceMethodExpansion=truffleTier', '--smoke'], capture_output=True, text=True)
print(process.stdout, end='')
print(process.stderr, end='', file=sys.stderr)
output = Path(os.environ['TEST_UNDECLARED_OUTPUTS_DIR'])
output.mkdir(parents=True, exist_ok=True)
(output / 'yaml-compilation.log').write_text(process.stderr)
process.check_returncode()
rules = {
    'GenericReads[truffledata-yaml-interop]': (2000, 1, 0),
    'GenericReads[truffledata-yaml-export]': (1000, 2, 0),
}
seen = set()
name = None
for line in process.stderr.splitlines():
    header = re.match(r'^\[engine\] Expansion tree for (\S+) after truffleTier:', line)
    if header:
        name = header.group(1)
    if re.match(r'^<root>\s', line):
        columns = line.split('|')
        if len(columns) < 3 or name not in rules:
            raise AssertionError('Unexpected YAML expansion: ' + line)
        values = tuple(map(int, columns[1].split()))
        if len(values) != 7:
            raise AssertionError('Changed compiler trace: ' + line)
        nodes, _, _, _, loops, invokes, allocations = values
        limit, expected_loops, max_allocations = rules[name]
        if not (0 < nodes <= limit and loops == expected_loops and invokes == 0 and allocations <= max_allocations):
            raise AssertionError(f'{name} exceeded {rules[name]}: {line}')
        seen.add(name)
        name = None
if seen != rules.keys():
    raise AssertionError(f'Missing compiler evidence: {rules.keys() - seen}')
rows = [json.loads(line) for line in process.stdout.splitlines() if line.startswith('{')]
for engine in ('truffledata-yaml-interop', 'truffledata-yaml-export'):
    matches = [r for r in rows if r['engine'] == engine and r['stage'] == 'peak']
    if len(matches) != 1 or not matches[0]['guest_compiled'] or max(matches[0]['allocated_bytes_per_record']) > 1.0:
        raise AssertionError(f'YAML warm read/export budget: {matches}')
# Dedicated 32-document borrowed parse cell; same logical fixtures as JSON's sink gate.
sinks = [r for r in rows if r['workload'] == 'yaml-sink']
if len(sinks) != 1 or max(sinks[0]['allocated_bytes_per_record']) > 2400:
    raise AssertionError('YAML TruffleSink allocation budget: ' + str(sinks))
expected_cells = 4 * 5  # typed streams have no API; every other cell must execute
if sum(r['stage'] == 'ingest' and r['workload'] != 'yaml-sink' for r in rows) != expected_cells:
    raise AssertionError('Missing YAML ingestion cells')
print('YAML checksums, compiled graph and allocation gates passed')
