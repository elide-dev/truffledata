#!/usr/bin/env bash
# AOT call-shape gate for the Buffa cells route (docs/BUFFA.md, "Myna Truffle cells"): in a
# symbol-bearing image built with -Dtruffledata.buffa.cells=true, the cells backend reaches the
# slab producer through the generated cell's static invoke (decodeSlab_Cell.invoke or
# decodeSlabSmall_Cell.invoke, Myna 0.16.0), or has invoke inlined and calls the native binding
# directly (no invoke symbol in the image; the backend method makes an indirect native call followed
# by the C call's native-to-Java transition) — required of both slab overloads and writeSlab; no
# CellsSvmBackend method calls any _Cell_execute* symbol (the guest entries, which
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
# invoke inlined into the backend (then the image has no invoke symbol of its own): the backend
# makes the native call itself, an indirect call through the binding's address with the C call's
# native-to-Java transition after it. No other native call is made by these methods.
indirect = re.compile(r'^\*%|^x[0-9]+$|^blr')
transition = re.compile(r'SafepointSlowpath_enterSlowPathTransitionFromNativeTo')
invoke_symbols = [row['name'] for row in rows if invoke.search(row['name'])]
executes = [row['name'] for row in rows if forbidden.search(row['name'])]
print('CALL SHAPE invoke symbols in the image: %s' % (invoke_symbols or 'none (inlined)'))
print('CALL SHAPE _Cell_execute* symbols in the image: %d' % len(executes))
entries = {'CellsSvmBackend_slab': 0, 'CellsSvmBackend_writeSlab': 0}
for row in backend:
    targets = row['call_targets']
    for target in targets:
        if forbidden.search(target):
            failures.append('%s calls %s' % (row['name'], target))
    called = [t for t in targets if invoke.search(t)]
    inlined = (not invoke_symbols and any(indirect.search(t) for t in targets)
               and any(transition.search(t) for t in targets))
    if called or inlined:
        print('CALL SHAPE producer edge %s -> %s' % (
            row['name'], ', '.join(called) if called else 'inlined invoke (native call, transition)'))
        if row['name'] in entries:
            entries[row['name']] += 1
# Each slab producer entry reaches the producer this way: both slab overloads and writeSlab.
if backend:
    if entries['CellsSvmBackend_slab'] < 2:
        failures.append('%d of 2 CellsSvmBackend_slab overloads reach decodeSlab_Cell.invoke '
                        '(called, or inlined as a native call)' % entries['CellsSvmBackend_slab'])
    if entries['CellsSvmBackend_writeSlab'] < 1:
        failures.append('CellsSvmBackend_writeSlab does not reach decodeSlab_Cell.invoke '
                        '(called, or inlined as a native call)')
for failure in failures:
    print('CALL SHAPE FAIL ' + failure, file=sys.stderr)
sys.exit(1 if failures else 0)
PY
