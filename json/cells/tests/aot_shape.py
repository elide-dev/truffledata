#!/usr/bin/env python3
"""AOT shape of the JSON cells route in an image built with the flag (//json/cells/tests:native_profile).

The route is host code compiled ahead of time, so it calls each cell's static invoke entry (Myna
0.16.0: native carriers and raw addresses, nothing allocated), never an execute method, whose
argument array, boxes, NativePointers and Borrow are not scalar-replaced there. Fails if a route
symbol calls any `*_Cell_execute*` symbol or any cells coercion, borrow or boxing helper, if a kept
invoke symbol boxes, or if the route does not reach each routed import (decode, decode_adopted,
release) through its cell's invoke. Where the compiler inlined invoke (then the image has no invoke
symbol and no binding symbol of its own), the route symbol makes the native call itself: an
indirect call through the binding's address followed by the C call's native-to-Java transition;
that is required of the symbol that owns each import. Prints, for every route symbol the image
kept, its instruction and call counts and its call targets, so a reader can see what the compiler
inlined and what still allocates (an allocation's slow path is a call). Shape only, on the ISA it
ran on; no timing, no performance claim.

Usage: aot_shape.py AOT_SYMBOLS_DIR IMAGE
"""
import re
import sys

sys.path.insert(0, sys.argv[1])
import aot_symbols  # noqa: E402

CELLS = ('decode', 'decode_adopted', 'release')
PATTERNS = [
    r'^JsonSvm_(decode|adopt|cellsDecode|release|publish|adoptResult)$',
    r'^SvmJsonMessage(\$Adopted)?_release$',
    r'^CellsSvm_',
    r'^JsonCellsTruffle\$(decode|decode_adopted|release)_Cell_(execute\w*|invoke)$',
    r'^JsonCells_(decode|decode_adopted|release)$',
    r'^Json_(decode|decode_adopted|release)$',
    r'^(BytesCoercion|IntegerCoercion|PointerCoercion)_(execute|slow)$',
    r'^Borrow_(direct|release)$',
]
ROUTE = re.compile(r'^(JsonSvm|CellsSvm|SvmJsonMessage)')
EXECUTE = re.compile(r'_Cell_execute\w*$')
INVOKE = re.compile(r'^JsonCellsTruffle\$(decode|decode_adopted|release)_Cell_invoke$')
BOX = re.compile(r'^(Integer|Long)_valueOf$|^NativePointer_of$|^Borrow_')
# What an execute path would call from host code; the invoke path calls none of it.
CELLS_MACHINERY = re.compile(
    r'^(Integer|Long)_valueOf$|^NativePointer_of$|^Borrow_|^(Bytes|Integer|Pointer)Coercion_|^NativeCell_')
# invoke inlined into a route symbol: an indirect native call and the native-to-Java transition.
INDIRECT = re.compile(r'^\*%|^x[0-9]+$|^blr')
TRANSITION = re.compile(r'SafepointSlowpath_enterSlowPathTransitionFromNativeTo')
# The route symbol that makes each routed import's call once its helpers are inlined. Release is
# reached from both message kinds.
OWNERS = {
    'decode': ('JsonSvm_cellsDecode',),
    'decode_adopted': ('JsonSvm_adopt',),
    'release': ('SvmJsonMessage_release', 'SvmJsonMessage$Adopted_release'),
}


def main():
    result = aot_symbols.report(sys.argv[2], PATTERNS)
    sys.stdout.write(aot_symbols.markdown(result))
    failures = []
    names = set()
    reached = set()
    native_call = set()
    for row in result['symbols']:
        names.add(row['name'])
        targets = row['call_targets']
        if any(INDIRECT.search(t) for t in targets) and any(TRANSITION.search(t) for t in targets):
            native_call.add(row['name'])
        print('CALLS %s: %s' % (row['name'], ', '.join(row['call_targets']) or '(none)'))
        route = ROUTE.search(row['name'])
        for target in row['call_targets']:
            if route:
                reached.add(target)
                if EXECUTE.search(target):
                    failures.append('%s calls %s: host code must call the cell\'s invoke' % (row['name'], target))
                if CELLS_MACHINERY.search(target):
                    failures.append('%s calls %s: the invoke path coerces, borrows and boxes nothing'
                                    % (row['name'], target))
            if INVOKE.search(row['name']) and BOX.search(target):
                failures.append('%s allocates through %s' % (row['name'], target))
    if 'JsonSvm_decode' not in names:
        failures.append('no JsonSvm_decode symbol: the image must keep local symbols and compile the route')
    # Each routed import is reached from the route through its cell's invoke (or the derived binding
    # or native symbol that invoke calls). With invoke inlined, the image keeps none of those
    # symbols and the symbol that owns the import makes the native call itself.
    kept = sorted(name for name in names if INVOKE.search(name))
    print('INVOKE symbols in the image: %s' % (', '.join(kept) or 'none (inlined)'))
    for cell in CELLS:
        entries = {'JsonCellsTruffle$%s_Cell_invoke' % cell, 'JsonCells_%s' % cell, 'td_json_%s' % cell}
        if reached & entries:
            print('ROUTE %s via %s' % (cell, ', '.join(sorted(reached & entries))))
            continue
        owners = OWNERS[cell]
        missing = [owner for owner in owners if owner not in native_call]
        if entries & names or missing:
            failures.append('the route does not reach %s through its cell\'s invoke: none of %s is called, and '
                            '%s does not make the inlined native call (indirect call and transition)'
                            % (cell, ', '.join(sorted(entries)), ', '.join(missing or owners)))
        else:
            print('ROUTE %s via invoke inlined into %s (native call, transition)' % (cell, ', '.join(owners)))
    executes = sorted(name for name in names if EXECUTE.search(name))
    print('CELL execute symbols compiled into the image (not called by the route): %s'
          % (', '.join(executes) or '(none)'))
    # The production bindings of the routed imports must not be compiled into this image: every
    # decode and release goes through the cells' derived bindings.
    for name in sorted(names):
        if re.match(r'^Json_(decode|decode_adopted|release)$', name):
            failures.append('%s (the production binding) is compiled into the cells image' % name)
    for failure in failures:
        print('SHAPE FAIL ' + failure, file=sys.stderr)
    return 1 if failures else 0


if __name__ == '__main__':
    sys.exit(main())
