#!/usr/bin/env python3
"""P0.3: class the self Ir of a simulation callgrind split (tools/bench/callgrind_split.py --json)
into native decode, Java publication, release, directory-rank lookups, interop export bodies,
harness dispatch/views, snapshot copy and the benchmark loop. Instructions per document use
rows x iterations (256 x 8). Shares are of self Ir; inclusive figures are read from the split.

Usage: classify.py SPLIT.json [--docs 2048]
"""
import json
import re
import sys

CLASSES = [
    ('Java release (JsonMessage.close, markClosed, release, native free)',
     r'JsonMessage::close|JsonMessage::release|TapeOwner::markClosed|SvmJsonMessage::release|'
     r'PosixLibC::free|^td_json_release'),
    ('snapshot copy (HeapTape.copy, rawSpan/word/copyText/directoryEntry it calls)',
     r'HeapTape::copy|TapeReader::rawSpan|TapeReader::word\(|SvmJsonMessage::copyText|'
     r'SvmJsonMessage::directoryEntry|TapeSnapshot::copy|TapeFormat::slice|TapeOwner::snapshot'),
    ('native decode (td_json_decode: stage1, stage2 walk, directories, arenas, malloc, mutex)',
     r'simdjson::|truffledata::|td_json_decode|make_unique|anonymous namespace|handle::|publish\(|'
     r'^malloc|^free|_int_malloc|_int_free|^calloc|^realloc|pthread_mutex|__pthread|std::__1|'
     r'operator new|operator delete|__memcpy|__memset|__memmove|__strlen|parser_lease'),
    ('Java publication (JsonSvm.decode, pins, status, descriptor, message construction)',
     r'JsonSvm::|PinnedObject|SvmJsonMessage::<init>|JsonMessage::<init>|TapeOwner::<init>|'
     r'UnmanagedMemory|CTypeConversion|PosixLibC::calloc'),
    ('directory-rank lookups (directoryKey, find, hinted)',
     r'directoryKey|TapeOwner::find|TapeOwner::hinted'),
    ('interop export bodies (TapeValue/TapeValueGen reads, kinds, owner checks, readWord)',
     r'TapeValue|TapeOwner::|TapeReader::|TapeSnapshot|HeapTape::|JsonMessage::aliases|'
     r'SvmJsonMessage::readWord|SvmJsonMessage::'),
    ('harness dispatch and views (InteropReader.read, InteropLibraryGen dispatch)',
     r'InteropReader|InteropLibraryGen|InteropLibrary::|LibraryFactory'),
    ('benchmark loop (SimulationBenchmark)', r'SimulationBenchmark|databench|JsonFixture'),
]


def main():
    path = sys.argv[1]
    docs = int(sys.argv[sys.argv.index('--docs') + 1]) if '--docs' in sys.argv else 2048
    split = json.load(open(path))
    functions = [{'name': name, 'self': ir} for name, ir in split['self'].items()]
    total = split['total']
    classed = {name: 0 for name, _ in CLASSES}
    other = 0
    unmatched = []
    for entry in functions:
        name, self_ir = entry['name'], entry['self']
        for cls, pattern in CLASSES:
            if re.search(pattern, name):
                classed[cls] += self_ir
                break
        else:
            other += self_ir
            unmatched.append((self_ir, name))
    print(f'total Ir {total:,}  ({total / docs:,.0f} Ir/document over {docs} documents)')
    print()
    print('| Class | self Ir | share | Ir/document |')
    print('|---|---:|---:|---:|')
    for cls, _ in CLASSES:
        if classed[cls]:
            print(f'| {cls} | {classed[cls]:,} | {100 * classed[cls] / total:.2f}% | {classed[cls] / docs:,.0f} |')
    print(f'| other (unclassed) | {other:,} | {100 * other / total:.2f}% | {other / docs:,.0f} |')
    unmatched.sort(reverse=True)
    if unmatched:
        print()
        print('Largest unclassed symbols:')
        for self_ir, name in unmatched[:15]:
            print(f'  {self_ir:>10,}  {name[:110]}')


if __name__ == '__main__':
    main()
