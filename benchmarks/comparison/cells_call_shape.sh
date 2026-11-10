#!/usr/bin/env bash
# AOT call-shape gate for the Buffa cells route (docs/BUFFA.md, "Myna Truffle cells"): in a
# symbol-bearing image built with -Dtruffledata.buffa.cells=true, the cells backend reaches the
# slab producer through the generated cell's static invoke (decodeSlab_Cell.invoke or
# decodeSlabSmall_Cell.invoke, Myna 0.16.0), or has invoke inlined and calls the native binding
# directly; no CellsSvmBackend method calls any _Cell_execute* symbol (the guest entries, which
# coerce an Object[] of guest values and allocate in ahead-of-time host code), the generic
# NativeCell.execute, or anything about UnexpectedResultException. Usage: cells_call_shape.sh IMAGE.
# Shape only, no timing; the source-level twin is //protobuf-buffa/cells/tests:call_shape.
set -euo pipefail
cd "${TEST_SRCDIR}/${TEST_WORKSPACE}"
report="${TEST_UNDECLARED_OUTPUTS_DIR}/cells-call-shape.json"
sha256sum "$1" > "${TEST_UNDECLARED_OUTPUTS_DIR}/binary.sha256"
python3 tools/bench/aot_symbols.py "$1" \
  --pattern '^CellsSvmBackend_' --pattern '_Cell_execute' --pattern '_Cell_invoke$' \
  --json "$report"
python3 - "$report" <<'PY'
import json, re, sys

rows = json.load(open(sys.argv[1]))['symbols']
backend = [row for row in rows if row['name'].startswith('CellsSvmBackend_')]
for row in rows:
    print('%s: %d instructions, calls %s' % (row['name'], row['instructions'], ', '.join(row['call_targets'])))
failures = []
if not backend:
    failures.append('no CellsSvmBackend symbol: the image was not built with the cells flag')
# Any execute entry of a cell (typed or generic), the generic NativeCell entry, or a result unboxing.
forbidden = re.compile(r'_Cell_execute|NativeCell_execute|UnexpectedResultException')
invoke = re.compile(r'decodeSlab(Small)?_Cell_invoke$')
# invoke inlined into the backend: the backend calls the native binding itself (the cells' static
# @CFunction binding, BuffaCells, with its direct transition; or the producer's C symbol).
native = re.compile(r'^BuffaCells_decodeSlab(Small)?$|(^|[^A-Za-z0-9])td_buffa_decode_slab(_small)?$')
reached = []
for row in backend:
    for target in row['call_targets']:
        if forbidden.search(target):
            failures.append('%s calls %s' % (row['name'], target))
        if invoke.search(target) or native.search(target):
            reached.append('%s -> %s' % (row['name'], target))
if backend and not reached:
    failures.append(
        'no CellsSvmBackend method calls decodeSlab_Cell.invoke / decodeSlabSmall_Cell.invoke '
        'or, with invoke inlined, the native binding')
for edge in reached:
    print('CALL SHAPE producer edge ' + edge)
for failure in failures:
    print('CALL SHAPE FAIL ' + failure, file=sys.stderr)
sys.exit(1 if failures else 0)
PY
