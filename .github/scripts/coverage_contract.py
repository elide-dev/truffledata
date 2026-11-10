#!/usr/bin/env python3
"""Summarize coverage gaps and enforce complete method coverage of core access surfaces."""
import json
from pathlib import Path
import sys

ACCESS_CLASSES = {
    'AbstractRow', 'DataArray', 'DataLibrary', 'DynamicRecord', 'ExactNumber',
    'Missing', 'OwnedArray', 'PayloadBudget', 'ReadBooleanNode', 'ReadDoubleNode', 'ReadLengthNode', 'ReadLongNode',
    # Layout-bound accessors (protobuf performance plan Task B2): AccessorTest and
    # TypedAcceptance.accessors run every method against the uncached DataLibrary.
    'Accessor', 'LibraryAccessor',
}


ACCESS_SOURCES = {f'core/src/main/java/dev/elide/truffledata/{name}.java' for name in ACCESS_CLASSES} | {
    'protobuf-runtime/src/main/java/dev/elide/truffledata/protobuf/ProtoRow.java',
    'protobuf-runtime/src/main/java/dev/elide/truffledata/protobuf/SlabRow.java',
    'protobuf-runtime/src/main/java/dev/elide/truffledata/protobuf/SlabMessage.java',
    'protobuf-runtime/src/main/java/dev/elide/truffledata/protobuf/SlabArray.java',
    'protobuf-runtime/src/main/java/dev/elide/truffledata/protobuf/SlabBytes.java',
    'protobuf-runtime/src/main/java/dev/elide/truffledata/protobuf/ProtoAccess.java',
    'protobuf-runtime/src/main/java/dev/elide/truffledata/protobuf/ProtoCodec.java',
    'protobuf-runtime/src/main/java/dev/elide/truffledata/protobuf/GeneratedMessage.java',
    'protobuf-runtime/src/main/java/dev/elide/truffledata/protobuf/GeneratedInput.java',
    'protobuf-runtime/src/main/java/dev/elide/truffledata/protobuf/GeneratedGraphInput.java',
    'protobuf-runtime/src/main/java/dev/elide/truffledata/protobuf/GeneratedOutput.java',
    'protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/BorrowedBytes.java',
    'protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/BorrowedSlabRow.java',
    'protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/BorrowedSlabMessage.java',
    'protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/BorrowedSlabArray.java',
    'protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/BorrowedMaterializedArray.java',
    'protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/BorrowedMaterializedRow.java',
    'protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/SlabSession.java',
    'protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/WireField.java',
    'protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/WireInput.java',
    'protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/WireMessage.java',
    'protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/ViewOwner.java',
    'protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/TypedRow.java',
    'protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/TypedArray.java',
    'protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/TypedMessage.java',
} | {
    # Every hand-written JSON source (//json and //json:jvm); generated bindings live in //json:bindings.
    # The format-neutral classes moved to //tape (YAML plan Task 4) and are listed below.
    f'json/src/main/java/dev/elide/truffledata/json/{name}.java' for name in (
        'JsonDecoder', 'JsonJvm', 'JsonLimits', 'JsonMessage', 'JsonPlan',
        'JsonSession', 'JsonStream', 'JsonStreamOptions', 'JsonSvm',
        # JvmSessionBackend was missing before Task 4 (test_coverage_contract failed on it).
        'JvmSessionBackend', 'JvmTypedOwner', 'SvmTypedOwner', 'SvmJsonMessage',
    )
} | {
    # Every hand-written shared tape source (//tape and //tape:jvm), run on this lane through the JSON
    # suites: JsonOwner → TapeOwner, JsonValue → TapeValue, JsonSink → TapeSink, JsonText → TapeText,
    # JsonKey → TapeKey, JsonSnapshot → TapeSnapshot, JsonExportNode → TapeExportNode, JsonTextArena →
    # TapeTextArena, JsonBorrow → TapeBorrow, JsonTypedOwner → TypedOwner, JsonRecord → TypedRecord,
    # JsonArray → TypedArray, JsonAccess → TypedAccess, JsonPlan.Layout → TypedLayout,
    # JsonMessage.Lexical → TapeLexical; JavaSink, TruffleSink and the tapes keep their names;
    # TapeSession, TapeStream and TapeFormat hold what JsonSession and JsonStream shared.
    f'tape/src/main/java/dev/elide/truffledata/tape/{name}.java' for name in (
        'HeapTape', 'TapeReader', 'SvmTape', 'SegmentTape', 'AliasExport', 'HeapValueTape', 'JavaSink', 'OwnerStateException', 'TapeBorrow', 'TapeExportNode',
        'TapeFormat', 'TapeKey', 'TapeLexical', 'TapeOwner', 'TapeSession', 'TapeSink', 'TapeSnapshot',
        'TapeStream', 'TapeText', 'TapeTextArena', 'TapeValue', 'TruffleSink',
        'TypedAccess', 'TypedArray', 'TypedLayout', 'TypedOwner', 'TypedRecord',
        # Java producers' compact-tape writer (the Java YAML kernel, #41).
        'TapeBuilder',
    )
} | {
    # Native YAML's JVM integration covers the common API; SVM intrinsics are tested in its image.
    f'yaml/src/main/java/dev/elide/truffledata/yaml/{name}.java' for name in (
        'Aliases', 'DuplicateKeys', 'KeyKind', 'Style', 'UnknownTags', 'VersionDirective',
        'Yaml', 'YamlSchema', 'YamlOptions', 'YamlLimits', 'YamlDirectives', 'YamlMetadata',
        'ResultMetadata', 'YamlSession', 'YamlMessage', 'YamlStream', 'YamlStreamOptions',
        'YamlJvm', 'YamlSvm', 'JvmSessionBackend', 'SvmSessionBackend', 'YamlPlan',
        'JvmTypedOwner', 'SvmTypedOwner', 'JvmPlanBackend', 'SvmYamlMessage',
        # Static-archive provider and plan (//yaml:svm, truffledata-yaml-native; #41).
        'SvmPlanBackend', 'SvmSessionProvider', 'YamlSessionProvider', 'YamlStatus',
        # Shared scalar resolution and the emitter (#42).
        'ScalarResolver', 'YamlScalars', 'YamlEmitOptions', 'YamlEmitter', 'YamlWriter',
        # The pure-Java kernel (//yaml:java), differentially tested against native (#41), its
        # parser-neutral event boundary and the SnakeYAML Engine event source (//yaml:snakeyaml).
        'JavaDecoder', 'JavaFailure', 'JavaInput', 'JavaPlanBackend', 'JavaSessionBackend',
        'JavaSessionProvider', 'JavaStream', 'JavaTypedOwner', 'YamlJava',
        'events/YamlEvents', 'events/YamlEventSource', 'events/YamlMark', 'events/YamlScalarStyle',
        'events/YamlSyntaxException', 'snakeyaml/SnakeYamlEventSource',
    )
} | {
    # Every CSV source: the scanner, ingest and typed conversions run on every read path, and the
    # batch, row and text views are the guest and embedder access surfaces.
    f'csv/src/main/java/dev/elide/truffledata/csv/{name}.java' for name in (
        'BatchBuilder', 'Column', 'CommonsScanner', 'Csv', 'CsvBatch', 'CsvFormat', 'CsvIndexing',
        'CsvKernel', 'CsvLimits', 'CsvMapping', 'CsvPlan', 'CsvReader', 'CsvRow', 'CsvScanner',
        'CsvSink', 'CsvText', 'CsvWriter', 'Dialect', 'IndexScan', 'Nulls', 'Numbers', 'Utf8',
        # The structural index every parse path frames records over, and its built-in kernel.
        'kernel/BlockStep', 'kernel/CsvIndex', 'kernel/CsvIndexer', 'kernel/SwarIndexer',
    )
}

# Interfaces with only abstract methods have an LCOV record but no executable method. Any method
# they gain is enforced like every other access method.
DECLARATION_ONLY_SOURCES = {
    'tape/src/main/java/dev/elide/truffledata/tape/TapeLexical.java',
    'tape/src/main/java/dev/elide/truffledata/tape/TapeSink.java',
    'csv/src/main/java/dev/elide/truffledata/csv/CsvSink.java',
    # The Java YAML kernel's parser SPI: one abstract method, implemented by each event source.
    'yaml/src/main/java/dev/elide/truffledata/yaml/events/YamlEventSource.java',
}

JSON = 'json/src/main/java/dev/elide/truffledata/json/'
JSON_CLASS = 'dev/elide/truffledata/json/'


def _json_methods(source, *signatures):
    return {JSON + source + '.java': {JSON_CLASS + signature for signature in signatures}}


TAPE = 'tape/src/main/java/dev/elide/truffledata/tape/'
TAPE_CLASS = 'dev/elide/truffledata/tape/'


def _tape_methods(source, *signatures):
    return {TAPE + source + '.java': {TAPE_CLASS + signature for signature in signatures}}


def _yaml_methods(source, *signatures):
    return {'yaml/src/main/java/dev/elide/truffledata/yaml/' + source + '.java':
            {'dev/elide/truffledata/yaml/' + signature for signature in signatures}}


# JVM LCOV cannot execute an SVM pointer intrinsic. Keep the exact method visible in gap reports;
# native_test and consumer_native_smoke exercise these loads under SVM's JIT checks.
# JSON: these run only in a static image (//json/tests:native_test, json_native_smoke and
# //tools/release:json_native_test). On the JVM, JsonSvm.requireRuntime rejects every entry point
# before native code runs, and the word-typed tapes and owners below exist only behind it.
SVM_ONLY_METHODS = {
    # YAML has the same static-image split as JSON. These entry points reject the JVM before
    # JaCoCo reaches a probe; YamlContractTest checks rejection, and //yaml/tests:native_test
    # exercises decoding, borrowed and retained results, metadata, streams and release.
    **_yaml_methods('YamlSvm',
        'YamlSvm::open ()Ldev/elide/truffledata/yaml/YamlSession;',
        'YamlSvm::decode ([B)Ldev/elide/truffledata/yaml/YamlMessage;',
        'YamlSvm::decode ([BLdev/elide/truffledata/yaml/YamlOptions;)Ldev/elide/truffledata/yaml/YamlMessage;',
        'YamlSvm::openSession (Ldev/elide/truffledata/yaml/YamlOptions;)Ldev/elide/truffledata/yaml/YamlSession;',
        'YamlSvm::compile (Ldev/elide/truffledata/Schema;Ljava/util/Map;)Ldev/elide/truffledata/yaml/YamlPlan;'),
    # The static-archive provider opens sessions only where it is available: in an image that links
    # the archive (//yaml/tests:native_test). On the JVM YamlSession.open() ranks it, sees it is
    # unavailable and opens the Java kernel instead.
    **_yaml_methods('SvmSessionProvider',
        'SvmSessionProvider::open (Ldev/elide/truffledata/yaml/YamlOptions;)Ldev/elide/truffledata/yaml/YamlSession;'),
    **_yaml_methods('YamlSession',
        'YamlSession::open ()Ldev/elide/truffledata/yaml/YamlSession;'),
    **_yaml_methods('SvmSessionBackend',
        'SvmSessionBackend::compile (Ldev/elide/truffledata/tape/TypedLayout;Ldev/elide/truffledata/yaml/YamlOptions;)Ldev/elide/truffledata/yaml/YamlPlan;',
        'SvmSessionBackend::writeOptions (Lorg/graalvm/word/Pointer;Ldev/elide/truffledata/yaml/YamlOptions;)V',
        'SvmSessionBackend::checkTyped (ILorg/graalvm/nativeimage/c/type/VoidPointer;)V',
        'SvmSessionBackend$1::integer (JJ)I',
        'SvmSessionBackend$1::octet (JJ)B',
        'SvmSessionBackend$1::pointer (JI)J',
        'SvmSessionBackend$1::word (JJ)J',
        'SvmSessionBackend$2::run ()V',
        'SvmSessionBackend$Storage::inputByte (J)B',
        'SvmSessionBackend$Storage::run ()V',
        'SvmSessionBackend$Storage::span (J)J',
        'SvmSessionBackend$NativeText::entry (J)J',
        'SvmSessionBackend$NativeText::write (Lorg/graalvm/nativeimage/c/type/VoidPointer;)V',
        'SvmSessionBackend::borrow ()Ldev/elide/truffledata/tape/TapeBorrow;',
        'SvmSessionBackend::check (ILorg/graalvm/nativeimage/c/type/VoidPointer;)V',
        'SvmSessionBackend::directories (Z)V',
        'SvmSessionBackend::decode (Ljava/lang/foreign/MemorySegment;J)V',
        'SvmSessionBackend::decode ([BIIILdev/elide/truffledata/tape/TapeTextArena;)V',
        'SvmSessionBackend::release ()V',
        'SvmSessionBackend::reset ()V',
        'SvmSessionBackend::retain ()Ldev/elide/truffledata/tape/TapeOwner;',
        'SvmSessionBackend::retain ()Ldev/elide/truffledata/yaml/YamlMessage;',
        'SvmSessionBackend::streamCreate (II)J',
        'SvmSessionBackend::streamFeed (JLjava/lang/foreign/MemorySegment;)J',
        'SvmSessionBackend::streamFeed (J[BII)I',
        'SvmSessionBackend::streamFinish (J)V',
        'SvmSessionBackend::streamNext (J)I',
        'SvmSessionBackend::streamRelease (J)V',
        'SvmSessionBackend::tape (Lorg/graalvm/nativeimage/c/type/VoidPointer;)Ldev/elide/truffledata/tape/SvmTape;',
        'SvmSessionBackend::textArena ([B)Ldev/elide/truffledata/tape/TapeTextArena;'),
    **_yaml_methods('SvmYamlMessage',
        'SvmYamlMessage::validate (Lorg/graalvm/word/Pointer;)J',
        'SvmYamlMessage::copyWords (J[JII)V',
        'SvmYamlMessage::copyDirectory (J[JII)V',
        'SvmYamlMessage::create (Lorg/graalvm/word/Pointer;JLdev/elide/truffledata/yaml/YamlMetadata;)Ldev/elide/truffledata/yaml/YamlMessage;',
        'SvmYamlMessage::reader ()Ldev/elide/truffledata/tape/TapeReader;',
        'SvmYamlMessage::lexical (J)J',
        'SvmYamlMessage::lexicalByte (J)B',
        'SvmYamlMessage::release ()V',
        'SvmYamlMessage::nodeCount ()J',
        'SvmYamlMessage::poolSize ()J',
        'SvmYamlMessage::inputSize ()J',
        'SvmYamlMessage::directorySize ()J',
        'SvmYamlMessage::flags ()I',
        'SvmYamlMessage::readWord (J)J',
        'SvmYamlMessage::textByte (ZJ)B',
        'SvmYamlMessage::copyText (ZJ[BII)V',
        'SvmYamlMessage::textWord (ZJI)J',
        'SvmYamlMessage::directoryEntry (J)J',
        'SvmYamlMessage::metadata (J)I',
        'SvmYamlMessage::stringValue (J)Ljava/lang/String;',
        'SvmYamlMessage$Plain::aliases ()Z'),
    # The static-archive plan (truffledata-yaml-native); YamlPlan.compile itself now runs on the
    # session backend of the runtime (the Java kernel on the JVM test class path).
    **_yaml_methods('SvmPlanBackend',
        'SvmPlanBackend::create (Ldev/elide/truffledata/tape/TypedLayout;Ldev/elide/truffledata/yaml/YamlOptions;)Ldev/elide/truffledata/yaml/YamlPlan;',
        'SvmPlanBackend::decode ([B[Ldev/elide/truffledata/Schema;)Ldev/elide/truffledata/tape/TypedOwner;',
        'SvmPlanBackend::release ()V'),
    **_yaml_methods('SvmTypedOwner',
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
        'SvmTypedOwner::format ()Ldev/elide/truffledata/tape/TapeFormat;'),
    # The no-argument form is the static-image entry point (the kernel is linked; no library path).
    # It delegates to loadNative(null), which on the JVM test class path always throws because no
    # native backend is present, so JaCoCo records no probe although CsvFusedTest calls it.
    # //tools/release:csv_kernel_native_test runs it in an image.
    'csv/src/main/java/dev/elide/truffledata/csv/CsvIndexing.java': {
        'dev/elide/truffledata/csv/CsvIndexing::loadNative ()Ldev/elide/truffledata/csv/CsvIndexing$Provider;',
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
        # The opt-in cells route's pinned decode (//json/cells/tests:native_test; flag off it folds away).
        'JsonSvm::cellsDecode ([BLdev/elide/truffledata/json/JsonLimits;Lorg/graalvm/nativeimage/c/type/VoidPointer;)I',
        'JsonSvm::adoptResult (Lorg/graalvm/nativeimage/c/type/VoidPointer;)Ldev/elide/truffledata/json/JsonMessage;',
        # Result accessors (Myna v4 descriptor, side tables, retained input) and the session's
        # rebindable borrow reader (JSON performance ledger V1; was JsonSvm::borrow).
        'JsonSvm::descriptor (Lorg/graalvm/nativeimage/c/type/VoidPointer;)Lorg/graalvm/word/Pointer;',
        'JsonSvm$Borrows::borrow (Lorg/graalvm/nativeimage/c/type/VoidPointer;)Ldev/elide/truffledata/tape/TapeBorrow;',
        # The decoder handle returned by open() and the lexical table built by publish().
        'JsonSvm$1::decode ([BLdev/elide/truffledata/json/JsonLimits;)Ldev/elide/truffledata/json/JsonMessage;',
        'JsonSvm$1::adopt (Ljava/lang/foreign/MemorySegment;JLjava/lang/Runnable;)'
        'Ldev/elide/truffledata/json/JsonMessage;',
        'JsonSvm$1::compile (Ldev/elide/truffledata/Schema;Ljava/util/Map;)Ldev/elide/truffledata/json/JsonPlan;',
        'JsonSvm$1::close ()V',
    ),
    # The fused retained JSON owner embeds native pointers; its storage operations run in
    # //json/tests:native_test and the retained/interop allocation cells of json_native_smoke.
    **_json_methods(
        'SvmJsonMessage',
        'SvmJsonMessage::reader ()Ldev/elide/truffledata/tape/TapeReader;',
        'SvmJsonMessage::release ()V',
        'SvmJsonMessage::nodeCount ()J',
        'SvmJsonMessage::poolSize ()J',
        'SvmJsonMessage::inputSize ()J',
        'SvmJsonMessage::directorySize ()J',
        'SvmJsonMessage::flags ()I',
        'SvmJsonMessage::readWord (J)J',
        # Native pointer hint edge cases run in SvmAcceptance.nativeDirectoryHints.
        'SvmJsonMessage::directoryKey (JILdev/elide/truffledata/tape/TapeKey;)J',
        'SvmJsonMessage::textByte (ZJ)B',
        'SvmJsonMessage::copyText (ZJ[BII)V',
        'SvmJsonMessage::textWord (ZJI)J',
        'SvmJsonMessage::directoryEntry (J)J',
        'SvmJsonMessage::copyWords (J[JII)V',
        'SvmJsonMessage::copyDirectory (J[JII)V',
        'SvmJsonMessage::metadata (J)I',
        'SvmJsonMessage::stringValue (J)Ljava/lang/String;',
        'SvmJsonMessage$Adopted::release ()V',
    ),
    # Static-image session backend: open() throws from requireRuntime on the JVM (JvmScopedTest
    # asserts it), so the SVM backend and its text arenas run only in //json/tests:native_test
    # (SvmAcceptance.sessions/textArenas/utf16Input, CompiledReads.borrowed). The backend-neutral
    # session methods run on the JVM through JsonJvm.openSession (Task E1).
    **_json_methods(
        'JsonSession',
        'JsonSession::open ()Ldev/elide/truffledata/json/JsonSession;',
        'JsonSession::open (Ldev/elide/truffledata/json/JsonLimits;)Ldev/elide/truffledata/json/JsonSession;',
        'JsonSession$SvmBackend::create (Ldev/elide/truffledata/json/JsonLimits;)'
        'Ldev/elide/truffledata/json/JsonSession$SvmBackend;',
        'JsonSession$SvmBackend::decode ([BIIILdev/elide/truffledata/tape/TapeTextArena;)V',
        'JsonSession$SvmBackend::decode (Ljava/lang/foreign/MemorySegment;J)V',
        'JsonSession$SvmBackend::textArena ([B)Ldev/elide/truffledata/tape/TapeTextArena;',
        'JsonSession$SvmBackend::borrow ()Ldev/elide/truffledata/tape/TapeBorrow;',
        'JsonSession$SvmBackend::retain ()Ldev/elide/truffledata/json/JsonMessage;',
        # javac's bridge for TapeSession.Backend<JsonMessage>.retain (erased to TapeOwner).
        'JsonSession$SvmBackend::retain ()Ldev/elide/truffledata/tape/TapeOwner;',
        'JsonSession$SvmBackend::reset ()V',
        'JsonSession$SvmBackend::release ()V',
        'JsonSession$SvmTextArena::entry (J)J',
        'JsonSession$SvmTextArena::bytes ()[B',
        # Streams through the static-image backend (Task F2; SvmAcceptance.streams). The JVM lane
        # runs the same JsonStream/JsonSession stream methods through JvmSessionBackend.
        'JsonSession$SvmBackend::streamCreate (II)J',
        'JsonSession$SvmBackend::streamFeed (J[BII)I',
        'JsonSession$SvmBackend::streamFeed (JLjava/lang/foreign/MemorySegment;)J',
        'JsonSession$SvmBackend::streamFinish (J)V',
        'JsonSession$SvmBackend::streamNext (J)I',
        'JsonSession$SvmBackend::streamSkip (J)I',
        'JsonSession$SvmBackend::streamTruncatedBytes (J)J',
        'JsonSession$SvmBackend::streamRelease (J)V',
    ),
    # The default (0) is the static-image backend's: only JvmSessionBackend overrides it (Task E2).
    # Moved with the shared session (was JsonSession$Backend::criticalDecodes).
    **_tape_methods('TapeSession', 'TapeSession$Backend::criticalDecodes ()J'),
    # A borrow's assumption is requested only by cached compiled reads of a borrowed view
    # (CompiledReads.borrowed on SVM); the JVM coverage lane runs no compiled code. (Was JsonBorrow.)
    **_tape_methods('TapeBorrow', 'TapeBorrow::assumption ()Lcom/oracle/truffle/api/Assumption;'),
    # Strings over a session text arena: written only by static-image session parses whose sink
    # asks for shared text (SvmAcceptance.textArenas); JVM sessions write no text arena. (Was
    # JsonTextArena::string; array() is the static-image backend's view of the same array.)
    **_tape_methods(
        'TapeTextArena',
        'TapeTextArena::string (JLcom/oracle/truffle/api/strings/TruffleString$Encoding;)'
        'Lcom/oracle/truffle/api/strings/TruffleString;',
        'TapeTextArena::array ()[B',
    ),
    # Static typed plans: compile throws from requireRuntime on the JVM (JvmScopedTest asserts it);
    # JVM plans compile through JsonJvm and its own backend instead.
    **_json_methods(
        'JsonPlan',
        'JsonPlan::compile (Ldev/elide/truffledata/Schema;)Ldev/elide/truffledata/json/JsonPlan;',
        'JsonPlan::compile (Ldev/elide/truffledata/Schema;Ljava/util/Map;)Ldev/elide/truffledata/json/JsonPlan;',
        'JsonPlan$SvmBackend::create (Ldev/elide/truffledata/tape/TypedLayout;)'
        'Ldev/elide/truffledata/json/JsonPlan$SvmBackend;',
        'JsonPlan$SvmBackend::decode ([B[Ldev/elide/truffledata/Schema;)Ldev/elide/truffledata/tape/TypedOwner;',
        'JsonPlan$SvmBackend::release ()V',
    ),
    # The compact reader's pointer operations and bulk transcoder execute only in native images.
    **_tape_methods(
        'SvmTape',
        'SvmTape::rebind (Lorg/graalvm/word/Pointer;)V',
        'SvmTape::nodeCount ()J',
        'SvmTape::poolSize ()J',
        'SvmTape::inputSize ()J',
        'SvmTape::directorySize ()J',
        'SvmTape::flags ()I',
        'SvmTape::decodeUtf8 (Lorg/graalvm/word/Pointer;I)Ljava/lang/String;',
        'SvmTape::readWord (J)J',
        # Both the standalone reader and fused owner are covered by nativeDirectoryHints.
        'SvmTape::directoryKey (JILdev/elide/truffledata/tape/TapeKey;)J',
        'SvmTape::directoryKey (Lorg/graalvm/word/Pointer;Lorg/graalvm/word/Pointer;'
        'Lorg/graalvm/word/Pointer;JILdev/elide/truffledata/tape/TapeKey;)J',
        'SvmTape::textByte (ZJ)B',
        'SvmTape::copyText (ZJ[BII)V',
        'SvmTape::copyText (Lorg/graalvm/word/Pointer;JJ[BII)V',
        'SvmTape::textWord (ZJI)J',
        'SvmTape::directoryEntry (J)J',
        'SvmTape::copyWords (J[JII)V',
        'SvmTape::copyDirectory (J[JII)V',
        'SvmTape::copyMetadata (J[III)V',
        'SvmTape::copyWords (Lorg/graalvm/word/Pointer;JJ[JII)V',
        'SvmTape::metadata (J)I',
        'SvmTape::stringValue (J)Ljava/lang/String;',
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
        # Format name for its closed/thread messages (YAML plan Task 4).
        'SvmTypedOwner::format ()Ldev/elide/truffledata/tape/TapeFormat;',
    ),
}

# Methods the JVM coverage lane cannot reach on HotSpot although they are not SVM code. Each entry
# names why; nothing else is exempt.
JVM_UNREACHABLE_METHODS = {
    # Defensive extent guard: every borrowed read follows a native-verified slab and a Java-side
    # verifier, so no public call can present an offset outside the retained extent.
    'protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/SlabSession.java': {
        'dev/elide/truffledata/buffa/SlabSession::outside ()Ljava/lang/IndexOutOfBoundsException;',
    },
    # Synthetic tag cursors expose resolved URI text but no source token: lexical() is always0,
    # so rawText rejects before a byte accessor can run. Tag view/snapshot/lifetime hooks are tested.
    **_tape_methods('AliasExport', 'AliasExport$1::lexicalByte (J)B'),
    # Native typed tables always use HotSpot's exact native segment class; these fallbacks are
    # present for other JVM implementations, matching the JSON typed owner.
    **_yaml_methods('JvmTypedOwner',
        'JvmTypedOwner::readOtherLong (Ljava/lang/foreign/MemorySegment;J)J',
        'JvmTypedOwner::readOtherInt (Ljava/lang/foreign/MemorySegment;J)I'),
    # The borrowed lexical holder owns no native result and is never released independently.
    # Retained results override run() with the actual release, covered by YamlIntegrationTest.
    **_yaml_methods('JvmSessionBackend', 'JvmSessionBackend$Storage::run ()V'),
    # Compiled-code fallback for a non-constant TypedAccess: the interpreter reports every value as a
    # partial-evaluation constant (so it takes walkExploded), and the coverage lane excludes JIT.
    # Exercised on SVM by the dynamic NestedReads root in json/tests/CompiledReads.java. (Was
    # JsonRecord::walk.)
    **_tape_methods('TypedRecord', 'TypedRecord::walk (Ldev/elide/truffledata/tape/TypedAccess;)J'),
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
