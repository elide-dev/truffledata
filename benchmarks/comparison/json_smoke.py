"""Require JSON read and export loops to survive partial evaluation within fixed graph budgets."""
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
(output / 'json-compilation.log').write_text(process.stderr)
process.check_returncode()

# Per root: (max nodes, loops, max graph allocations). Every rule requires zero invokes.
# The trace's Invokes column counts unresolved Java calls only; @TruffleBoundary calls (Gson
# reads, JavaSink events, member lookup) do not appear there. Generic rules are in JSON.md.
SCALAR = (1000, 1, 0)
RULES = {
    'GenericReads[simdjson-generic-interop]': (2000, 1, 0),
    'GenericReads[simdjson-snapshot]': (2000, 1, 0),
    # Document loop plus the tape walk, with no allocation: the owner's reusable text cursor holds
    # the container stack, and a first, nested or unconfined export allocates its cursor behind a
    # boundary (was (1000, 2, 2) with the stack allocated inline, before Task D1).
    'GenericReads[simdjson-export-walk]': (1000, 2, 0),
    # JavaSink events are boundaries that receive the text cursor and build host objects; their
    # allocations are bounded at runtime instead of in the graph.
    'GenericReads[simdjson-generic-java]': (1000, 2, None),
    # Stock baseline: one boundary call per document into Gson's tree API.
    'GenericReads[gson-tree]': (1000, 1, 0),
}
# Java bytes per document in the compiled read loop. 1.0 is the harness's own per-call boxing,
# identical for every peak cell. JavaSink budgets are fixed in JSON.md: every JavaSink cell shares
# one JavaSink.KeyCache, and the text cursor replaced a view per key/string (Task D1): measured
# 1,169 peak and 1,168 read-aot (2,357 before D1; the plan's target was 1,900).
ALLOCATION = {
    ('simdjson-generic-interop', 'peak'): 1.0,
    ('simdjson-export-walk', 'peak'): 1.0,
    ('simdjson-generic-java', 'peak'): 1200.0,
    ('simdjson-generic-java', 'read-aot'): 1200.0,
    # Ingest through an explicit JsonSession (performance plan Task B2), fixed in JSON.md. Interop:
    # session decode + ten AOT interop reads (240 B of views, as read-aot) + close; measured 472
    # (544 before sessions), 480 with Myna ABI v4 (ledger V1: a 112 B reader, a 40 B owner holding
    # one storage reference, a 40 B result without a callback field). Session JavaSink: borrowed
    # parse into a key-cached JavaSink; measured 1,360 with the D1 text cursor (3,596 before; the
    # plan's target was 1,900), 1,240 since sessions rebind one reader (ledger V1; was 1,400).
    ('simdjson-generic-interop', 'ingest'): 480.0,
    ('simdjson-session-java', 'ingest'): 1280.0,
    # Borrowed parse into a TruffleSink over the session's shared text arena (Task D2): the host
    # tree, one TruffleString per string value and one 2 × length + 16 B text array per document;
    # measured 1,772.5, 1,652.5 with the rebound reader (ledger V1; was 1,800).
    ('simdjson-session-truffle', 'ingest'): 1680.0,
    # Streams (Task F2), one NDJSON buffer of the 32 documents per operation through a JsonStream
    # that is never finished. JavaSink: next(JavaSink) per document; measured 1,338.5, 1,218.5 with
    # the rebound reader (ledger V1; was 1,400). Interop: next() lends the root (a borrow owner per
    # document) + ten AOT interop reads (240 B); measured 432.75, 312.75 once the session's reader
    # and lexical accessor are rebound instead of allocated per document (elide-dev/myna#47, ledger
    # V1; was 440). The plan's 1.0 B/document is a compiled read root, which these ingest cells are
    # not: 240 B are the AOT views; the rest is the borrow owner, its root view and the feed.
    ('simdjson-stream-java', 'ingest'): 1280.0,
    ('simdjson-stream-interop', 'ingest'): 320.0,
}

# Columns: Count Size Cycles Ifs Loops Invokes Allocs. Fail closed if trace format changes.
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
        if name.startswith('JsonComparison.Reads@'):
            key, rule = 'scalar', SCALAR
        elif name in RULES:
            key, rule = name, RULES[name]
        else:
            raise AssertionError('Unexpected compiled JSON root: ' + name)
        nodes, _, _, _, loops, invokes, allocations = values
        limit, expected_loops, max_allocations = rule
        if not (0 < nodes <= limit and loops == expected_loops and invokes == 0
                and (max_allocations is None or allocations <= max_allocations)):
            raise AssertionError(f'JSON graph {name} exceeded {rule} or retained calls: {line}')
        seen[key] = seen.get(key, 0) + 1
        name = None
# Each engine must compile; smoke's helper-level marker also checks the actual executed tier.
if seen.get('scalar', 0) < 3 or any(root not in seen for root in RULES):
    raise AssertionError(f'Missing compiler expansion evidence for JSON engines: {seen}')

records = [json.loads(line) for line in process.stdout.splitlines() if line.startswith('{')]
for (engine, stage), budget in ALLOCATION.items():
    rows = [r for r in records if r['engine'] == engine and r['stage'] == stage]
    if len(rows) != 1 or len(rows[0]['allocated_bytes_per_row']) != 3:
        raise AssertionError(f'Missing allocation record for {engine} {stage}')
    if max(rows[0]['allocated_bytes_per_row']) > budget:
        raise AssertionError(f'{engine} {stage} exceeded {budget} Java B/document: {rows[0]}')
print(f'JSON graph checks passed: {sum(seen.values())} compilations; '
      f'allocation budgets passed for {len(ALLOCATION)} cells')
