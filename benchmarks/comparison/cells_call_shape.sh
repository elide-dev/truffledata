#!/usr/bin/env bash
# AOT call-shape gate for the Buffa cells route (docs/BUFFA.md, "Myna Truffle cells"): in a
# symbol-bearing image built with -Dtruffledata.buffa.cells=true, the cells backend reaches the
# slab producer through the concrete cell's executeInt (or has it inlined), and never through the
# generic NativeCell.execute(Object[]), whose result is a box. Usage: cells_call_shape.sh IMAGE.
# Shape only, no timing; the source-level twin is //protobuf-buffa/cells/tests:call_shape.
set -euo pipefail
cd "${TEST_SRCDIR}/${TEST_WORKSPACE}"
report="${TEST_UNDECLARED_OUTPUTS_DIR}/cells-call-shape.json"
sha256sum "$1" > "${TEST_UNDECLARED_OUTPUTS_DIR}/binary.sha256"
python3 tools/bench/aot_symbols.py "$1" \
  --pattern '^CellsSvmBackend_' --pattern '_Cell_execute' --json "$report"
python3 - "$report" <<'PY'
import json, re, sys

rows = json.load(open(sys.argv[1]))['symbols']
backend = [row for row in rows if row['name'].startswith('CellsSvmBackend_')]
for row in rows:
    print('%s: %d instructions, calls %s' % (row['name'], row['instructions'], ', '.join(row['call_targets'])))
failures = []
if not backend:
    failures.append('no CellsSvmBackend symbol: the image was not built with the cells flag')
generic = re.compile(r'(NativeCell|_Cell)_execute$|UnexpectedResultException')
typed = re.compile(r'decodeSlab_Cell_executeInt$')
reached = False
for row in backend:
    for target in row['call_targets']:
        if generic.search(target):
            failures.append('%s calls %s' % (row['name'], target))
        reached = reached or bool(typed.search(target))
# Inlined into the backend: then the cell's own symbol is gone and the producer is called directly.
inlined = not any(typed.search(row['name']) for row in rows) and any(
    'decode_slab' in target or 'decodeSlab' in target for row in backend for target in row['call_targets'])
if backend and not (reached or inlined):
    failures.append('no CellsSvmBackend method calls decodeSlab_Cell.executeInt')
for failure in failures:
    print('CALL SHAPE FAIL ' + failure, file=sys.stderr)
sys.exit(1 if failures else 0)
PY
