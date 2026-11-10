#!/usr/bin/env python3
"""AOT shape of the JSON cells route in an image built with the flag (//json/cells/tests:native_profile).

Brief item 2 ("specialized NativeCell calls"): the route's scalar cells are called through their
typed method, never through the generic execute(Object[]), and the typed methods box nothing.
Prints, for every route symbol the image kept, its instruction and call counts and its call
targets, so a reader can see what the compiler inlined and what still allocates (an allocation's
slow path is a call). Shape only, on the ISA it ran on; no timing, no performance claim.

Usage: aot_shape.py AOT_SYMBOLS_DIR IMAGE
"""
import re
import sys

sys.path.insert(0, sys.argv[1])
import aot_symbols  # noqa: E402

PATTERNS = [
    r'^JsonSvm_(decode|adopt|cellsDecode|release|publish|adoptResult)$',
    r'^SvmJsonMessage(\$Adopted)?_release$',
    r'^CellsSvm_',
    r'^JsonCellsTruffle\$(decode|decode_adopted|release)_Cell_execute',
    r'^JsonCells_(decode|decode_adopted|release)$',
    r'^Json_(decode|decode_adopted|release)$',
    r'^(BytesCoercion|IntegerCoercion|PointerCoercion)_(execute|slow)$',
    r'^Borrow_(direct|release)$',
]
ROUTE = re.compile(r'^(JsonSvm|CellsSvm|SvmJsonMessage)')
GENERIC = re.compile(r'\$(decode|decode_adopted)_Cell_execute$')
TYPED = re.compile(r'\$(decode|decode_adopted)_Cell_executeInt$')
BOX = re.compile(r'^(Integer|Long)_valueOf$')


def main():
    result = aot_symbols.report(sys.argv[2], PATTERNS)
    sys.stdout.write(aot_symbols.markdown(result))
    failures = []
    names = set()
    for row in result['symbols']:
        names.add(row['name'])
        print('CALLS %s: %s' % (row['name'], ', '.join(row['call_targets']) or '(none)'))
        for target in row['call_targets']:
            if ROUTE.search(row['name']) and GENERIC.search(target):
                failures.append('%s calls a scalar cell through the generic %s' % (row['name'], target))
            if TYPED.search(row['name']) and BOX.search(target):
                failures.append('%s boxes through %s' % (row['name'], target))
    if 'JsonSvm_decode' not in names:
        failures.append('no JsonSvm_decode symbol: the image must keep local symbols and compile the route')
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
