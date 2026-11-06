#!/usr/bin/env python3
"""Summarize coverage gaps and enforce complete method coverage of core access surfaces."""
import json
from pathlib import Path
import sys

ACCESS_CLASSES = {
    'AbstractRow', 'DataArray', 'DataLibrary', 'DynamicRecord', 'ExactNumber',
    'Missing', 'PayloadBudget', 'ReadBooleanNode', 'ReadDoubleNode', 'ReadLengthNode', 'ReadLongNode',
}


ACCESS_SOURCES = {f'core/src/main/java/dev/elide/truffledata/{name}.java' for name in ACCESS_CLASSES} | {
    'protobuf-runtime/src/main/java/dev/elide/truffledata/protobuf/ProtoRow.java',
    'protobuf-runtime/src/main/java/dev/elide/truffledata/protobuf/ProtoAccess.java',
    'protobuf-runtime/src/main/java/dev/elide/truffledata/protobuf/ProtoCodec.java',
    'protobuf-runtime/src/main/java/dev/elide/truffledata/protobuf/GeneratedMessage.java',
    'protobuf-runtime/src/main/java/dev/elide/truffledata/protobuf/GeneratedInput.java',
    'protobuf-runtime/src/main/java/dev/elide/truffledata/protobuf/GeneratedGraphInput.java',
    'protobuf-runtime/src/main/java/dev/elide/truffledata/protobuf/GeneratedOutput.java',
    'protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/BorrowedBytes.java',
    'protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/WireField.java',
    'protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/WireInput.java',
    'protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/WireMessage.java',
    'protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/ViewOwner.java',
    'protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/TypedRow.java',
    'protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/NativeRow.java',
    'protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/ScalarRow.java',
    'protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/TypedArray.java',
    'protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/TypedMessage.java',
} | {
    # Every hand-written JSON source (//json and //json:jvm); generated bindings live in //json:bindings.
    f'json/src/main/java/dev/elide/truffledata/json/{name}.java' for name in (
        'HeapValueTape', 'JavaSink', 'JsonAccess', 'JsonArray', 'JsonDecoder', 'JsonExportNode',
        'JsonJvm', 'JsonLimits', 'JsonMessage', 'JsonOwner', 'JsonPlan', 'JsonRecord', 'JsonSink',
        'JsonSnapshot', 'JsonSvm', 'JsonTypedOwner', 'JsonValue', 'JvmTypedOwner', 'SegmentValueTape',
        'SvmTypedOwner', 'SvmValueTape', 'TapeSource',
    )
}

# Interfaces with only abstract methods have an LCOV record but no executable method. Any method
# they gain is enforced like every other access method.
DECLARATION_ONLY_SOURCES = {
    'json/src/main/java/dev/elide/truffledata/json/JsonSink.java',
    'json/src/main/java/dev/elide/truffledata/json/TapeSource.java',
}

JSON = 'json/src/main/java/dev/elide/truffledata/json/'
JSON_CLASS = 'dev/elide/truffledata/json/'


def _json_methods(source, *signatures):
    return {JSON + source + '.java': {JSON_CLASS + signature for signature in signatures}}


# JVM LCOV cannot execute an SVM pointer intrinsic. Keep the exact method visible in gap reports;
# native_views_test and consumer_native_smoke exercise these loads under SVM's JIT checks.
# JSON: these run only in a static image (//json/tests:native_test, json_native_smoke and
# //tools/release:json_native_test). On the JVM, JsonSvm.requireRuntime rejects every entry point
# before native code runs, and the word-typed tapes and owners below exist only behind it.
SVM_ONLY_METHODS = {
    'protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/NativeRow.java': {
        'dev/elide/truffledata/buffa/NativeRow::entry '
        '(Ldev/elide/truffledata/buffa/BuffaWire$Tape;I)Lorg/graalvm/word/Pointer;',
    },
    # Static entry points: each throws from requireRuntime (a callee) on the JVM, so JaCoCo records
    # no probe even though JvmScopedTest and FfmAcceptanceTest call them; the rest is the native
    # decode, ABI check, result publication and release that only an image can link.
    **_json_methods(
        'JsonSvm',
        'JsonSvm::open ()Ldev/elide/truffledata/json/JsonDecoder;',
        'JsonSvm::decode ([B)Ldev/elide/truffledata/json/JsonMessage;',
        'JsonSvm::decode ([BLdev/elide/truffledata/json/JsonLimits;)Ldev/elide/truffledata/json/JsonMessage;',
        'JsonSvm::adopt (JJJLjava/lang/Runnable;)Ldev/elide/truffledata/json/JsonMessage;',
        'JsonSvm::verifyAbi ()V',
        'JsonSvm::publish (Lorg/graalvm/nativeimage/c/type/VoidPointer;Ljava/lang/Runnable;)'
        'Ldev/elide/truffledata/json/JsonMessage;',
        'JsonSvm::release (J)V',
        'JsonSvm::lambda$decode$0 ()V',
        'JsonSvm::lambda$publish$0 (JLjava/lang/Runnable;)V',
        # The decoder handle returned by open() and the lexical table built by publish().
        'JsonSvm$1::decode ([BLdev/elide/truffledata/json/JsonLimits;)Ldev/elide/truffledata/json/JsonMessage;',
        'JsonSvm$1::adopt (Ljava/lang/foreign/MemorySegment;JLjava/lang/Runnable;)'
        'Ldev/elide/truffledata/json/JsonMessage;',
        'JsonSvm$1::compile (Ldev/elide/truffledata/Schema;Ljava/util/Map;)Ldev/elide/truffledata/json/JsonPlan;',
        'JsonSvm$1::close ()V',
        'JsonSvm$2::span (J)J',
        'JsonSvm$2::inputByte (J)B',
    ),
    # Static typed plans: compile throws from requireRuntime on the JVM (JvmScopedTest asserts it);
    # JVM plans compile through JsonJvm and its own backend instead.
    **_json_methods(
        'JsonPlan',
        'JsonPlan::compile (Ldev/elide/truffledata/Schema;)Ldev/elide/truffledata/json/JsonPlan;',
        'JsonPlan::compile (Ldev/elide/truffledata/Schema;Ljava/util/Map;)Ldev/elide/truffledata/json/JsonPlan;',
        'JsonPlan$SvmBackend::create (Ldev/elide/truffledata/json/JsonPlan$Layout;)'
        'Ldev/elide/truffledata/json/JsonPlan$SvmBackend;',
        'JsonPlan$SvmBackend::decode ([B[Ldev/elide/truffledata/Schema;)Ldev/elide/truffledata/json/JsonTypedOwner;',
        'JsonPlan$SvmBackend::release ()V',
    ),
    # Word-pointer tape over a native descriptor; constructed only by JsonSvm.publish.
    **_json_methods(
        'SvmValueTape',
        'SvmValueTape::nodeInt (J)I',
        'SvmValueTape::nodeLong (J)J',
        'SvmValueTape::regionCount ()J',
        'SvmValueTape::regionSize (J)J',
        'SvmValueTape::copyRegion (JJ[BII)V',
    ),
    # Word-accessor typed owner; constructed only by the static plan backend.
    **_json_methods(
        'SvmTypedOwner',
        'SvmTypedOwner::bits (J)J',
        'SvmTypedOwner::text (J)Ljava/lang/String;',
        'SvmTypedOwner::copyText (J)Ljava/lang/String;',
        'SvmTypedOwner::present (J)J',
        'SvmTypedOwner::nulls (J)J',
        'SvmTypedOwner::slotBase (J)I',
        'SvmTypedOwner::plan (J)I',
        'SvmTypedOwner::slot (J)J',
        'SvmTypedOwner::entry (J)J',
        'SvmTypedOwner::release ()V',
    ),
}

# Methods the JVM coverage lane cannot reach on HotSpot although they are not SVM code. Each entry
# names why; nothing else is exempt.
JVM_UNREACHABLE_METHODS = {
    # Compiled-code fallback for a non-constant JsonAccess: the interpreter reports every value as a
    # partial-evaluation constant (so it takes walkExploded), and the coverage lane excludes JIT.
    **_json_methods('JsonRecord', 'JsonRecord::walk (Ldev/elide/truffledata/json/JsonAccess;)J'),
    # Fallbacks for a segment that is not HotSpot's native segment class. Typed tables are always
    # reinterpreted native segments, so on HotSpot only the exact-class fast path runs.
    **_json_methods(
        'JvmTypedOwner',
        'JvmTypedOwner::readOtherLong (Ljava/lang/foreign/MemorySegment;J)J',
        'JvmTypedOwner::readOtherInt (Ljava/lang/foreign/MemorySegment;J)I',
    ),
}

def analyze(text):
    summary = {}
    for record in text.split('end_of_record'):
        lines = record.splitlines()
        source = next((line[3:] for line in lines if line.startswith('SF:')), None)
        if source is None:
            continue
        hits = {int(line[3:].split(',')[0]): int(line.split(',')[1])
                for line in lines if line.startswith('DA:')}
        starts = {line.split(',', 1)[1]: int(line[3:].split(',', 1)[0])
                  for line in lines if line.startswith('FN:')}
        functions = {line.split(',', 1)[1]: int(line[5:].split(',', 1)[0])
                     for line in lines if line.startswith('FNDA:')}
        # Ignore constructors and abstract/interface declarations (LCOV start line zero).
        methods = {name: count for name, count in functions.items()
                   if '::<init>' not in name and '::<clinit>' not in name
                   and starts.get(name, 1) != 0}
        branches = [line[5:].split(',') for line in lines if line.startswith('BRDA:')]
        summary[source] = {
            'branches_hit': sum(parts[3] not in ('0', '-') for parts in branches),
            'branches_total': len(branches),
            'uncovered_branches': [parts[:3] for parts in branches if parts[3] in ('0', '-')],
            'lines_hit': sum(count > 0 for count in hits.values()),
            'lines_total': len(hits),
            'uncovered_lines': [line for line, count in hits.items() if count == 0],
            'methods_hit': sum(count > 0 for count in methods.values()),
            'methods_total': len(methods),
            'uncovered_methods': [name for name, count in methods.items() if count == 0],
        }
    return summary


def enforce(summary):
    for source in sorted(ACCESS_SOURCES):
        entry = summary.get(source)
        if not entry or (not entry['methods_total'] and source not in DECLARATION_ONLY_SOURCES):
            raise ValueError('Missing access-method coverage: ' + source)
        exempt = SVM_ONLY_METHODS.get(source, set()) | JVM_UNREACHABLE_METHODS.get(source, set())
        missing = set(entry['uncovered_methods']) - exempt
        if missing:
            raise ValueError('Uncovered access methods in ' + source + ': ' + ', '.join(sorted(missing)))


def main():
    summary = analyze(Path('build/reports/coverage/lcov.info').read_text())
    Path('build/reports/coverage/gaps.json').write_text(json.dumps(summary, indent=2) + '\n')
    for source, counts in sorted(summary.items()):
        print(f"{counts['lines_hit']}/{counts['lines_total']} lines, "
              f"{counts['methods_hit']}/{counts['methods_total']} methods: {source}")
    enforce(summary)


if __name__ == '__main__':
    main()
