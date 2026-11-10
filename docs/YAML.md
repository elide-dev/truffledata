# YAML (in development)

The native YAML backend uses rapidyaml 0.16.0 with strict YAML 1.2 core resolution. The Java integration is under development and is not yet a published artifact. Typed plans and static-image compiled reads are implemented; the compact producer is implemented, while full performance and release qualification remains unfinished; the [implementation ledger](superpowers/plans/2026-11-06-yaml-ledger.md) records verified scope.

For JVM use, open a session with `YamlJvm.openSession(pathToNativeLibrary)`. For a static image linked against `//yaml/native:svm`, add `truffledata-yaml-native` (`//yaml:svm`) and use `YamlSession.open()`. Sessions and retained messages are thread confined.

```java
try (YamlSession session = YamlJvm.openSession(library);
     YamlMessage message = session.decode("answer: 0x2A".getBytes(StandardCharsets.UTF_8))) {
  TapeValue answer = message.root().member("answer");
  assert answer.longValue() == 42;
  assert answer.numberText().equals("42");
  assert answer.rawText().equals("0x2A");
}
```

`parse(input, sink)` exports a borrowed document, and `with(input, reader)` provides a scoped view. Borrowed values become invalid when the callback returns. `decode` retains a message that outlives its session; closing the message invalidates its views. A full `snapshot()` remains usable after close, including aliases and metadata. A subtree snapshot cannot yet follow an alias to a target outside the copied subtree.

Empty input yields null. `decode` accepts at most one document. Use `documents(byte[])` for a bounded complete buffer or `stream(options)` for incremental feeds. A stream's current view lasts until its next operation; `retain()` detaches it. `next()` requests lookup directories for member reads; `next(sink)` traverses sequentially without them. Both honor the session's source-retention option.

`YamlOptions.DEFAULT` rejects duplicate keys after scalar resolution, including keys that stringify identically. `YamlOptions.yaml11()` opts into YAML 1.1 resolution and merge keys; individual policies can be changed explicitly. The JSON schema rejects plain scalars outside JSON spelling. Invalid UTF input, recursive/undefined aliases, complex keys and incompatible tags fail with an exception. Resource limits cover native input/index/text storage, depth, node count, aliases and materialized expansion.

Aliases remain references during decode. Reads resolve them without expansion. JavaSink and TruffleSink reuse anchored objects; custom sinks may decline alias reuse, in which case export expands within `maxAliasExpansion`. The exporter reuses traversal frames and tag cursors after warm-up; compiled export and throughput gates are recorded in the implementation ledger.

`Yaml.tag`, `style`, `anchor`, `isAlias` and `keyKind` expose metadata. Tags follow the resolved alias target; style, anchor and alias provenance describe the source occurrence. `Yaml.binary` materializes a validated `!!binary` scalar, and `Yaml.timestamp` produces LocalDate or OffsetDateTime (UTC for an absent zone, nanosecond precision). Quoted/block scalar source spelling requires `withRetainSource(true)`; number spelling is available regardless. Nonfinite YAML floats use the compact tape's nonfinite flag and preserve Myna's allow-nonfinite flag in compatibility copies.

Compile a known record schema with `session.compile(schema, recordTypes)` and call `plan.decode(bytes)` to obtain a `TypedRecord`. Plans copy the session's options and limits; plans and results each remain valid after the session closes. Results also survive their plan closing. Closing a record invalidates all views of that result, while `snapshot()` creates an independent copy.

Typed fields resolve plain scalars according to their contract: `1.0` is preserved in a string field, `0x1f` is 31 in an integer field, and `.inf` is valid in a floating field. Quoted scalars require string fields. Nested and repeated records, aliases, enabled merge keys, integer/float width and string-length constraints use the shared typed layout and `TypedAccess`. Unknown, duplicate and missing required fields are rejected; typed plans always reject duplicates even when generic decoding uses last-wins.

Native ABI 5 publishes compact words with embedded scalar spans and one YAML metadata word per tape word. Lookup directories are optional per decode or streamed document: retained and scoped member reads request them, while validation and sink traversal omit them. Retain builds missing directories without moving tape words, and failure leaves the borrow available for retry. Duplicate-key policy applies with or without directories.

A YAML-only WIDE/non-OWNED STRING stores decoded text in the retained filtered input and keeps a separate raw-span word. This avoids a pool copy for quoted and folded text. With pristine-source retention, decoded filtered text stays owned in the pool; snapshots rebase both text and raw spans. JSON and DECIMAL retain their existing allowed flag combinations. Value aliases use native REF words. Double-quoted surrogate pairs decode to one Unicode scalar; unpaired surrogates are rejected before text reaches the tape.

The JVM differential suite compares 5,000 seeded YAML documents and the pinned yaml-test-suite with SnakeYAML Engine 3.2, explicitly disabling Engine's merge resolver. It also compares 2,000 shared JSON benchmark documents and the suite's 282 JSON fixture files against the JSON backend, including numeric kinds, member order, Unicode and escaped duplicate keys. The 45 measured Engine parser exclusions remain pinned; 14 custom-tag cases separately assert the documented preserve-versus-construction policy.

On Native Image, `parse` into `TruffleSink` shares a document text buffer for small strings and
copies large strings. The native producer negotiates capacity from decoded strings, including
expanded canonical mapping keys and reused alias keys. An input-only `2 × length + 16` estimate
is not a universal YAML bound; returned strings remain valid after the session parses again.
The JVM FFM path currently copies text instead of sharing this arena.

## Java kernel (no native library)

`truffledata-yaml-java` is a pure-Java implementation of the same semantics behind the same
`YamlSession`, `YamlMessage`, `TapeValue`, `TapeSink`, stream and typed-plan API, for plain JVMs
without `yaml_shared` and for native images built without the static YAML archive (Windows,
linux-musl, macOS x86_64). Add `truffledata-yaml-snakeyaml`, which brings the kernel and its first
event source (SnakeYAML Engine 3.2's scanner and parser; no composer, constructor or resolver).

`YamlSession.open(options)` picks among the `YamlSessionProvider` services on the class path: the
static archive (`truffledata-yaml-native`) in a native image, otherwise the kernel
(`YamlJava.openSession(options)` selects it explicitly, and `YamlJava.openSession(options, source)`
takes any event source). `YamlPlan.compile` compiles on that same backend; `YamlSvm` remains the
explicit static-archive entry point.

The kernel sits above a parser-neutral event boundary, `dev.elide.truffledata.yaml.events`: a
`YamlEventSource` reports resolved tags, styles, anchors and byte/line/column positions to
`YamlEvents`. Policies (scalar resolution through the shared `ScalarResolver`, duplicate keys after
resolution, merge order, alias and recursive-alias rules, 1.1 type tags, unknown tags, version
directives, every `YamlLimits` budget), raw spans, the `yaml_meta` side tables, multi-document
streams and typed plans are the kernel's own and depend only on that package; a future
hand-written parser implements `YamlEventSource` alone. Errors have the native types and messages
(`YAML syntax error: duplicate YAML key: a at line 2, column 1`).

Native images without the archive: do not add `truffledata-yaml-native`; add
`truffledata-yaml-snakeyaml`. `truffledata-yaml` holds no code that references the generated
bindings and does not depend on them, so nothing pulls in their `@CLibrary` (which requires the
archive), and images link even with `--link-at-build-time=dev.elide.truffledata`;
`//tools/release:yaml_java_native` builds the published jars this way. Images need `--initialize-at-build-time=dev.elide.truffledata`, as
every TruffleData image does.

`JavaKernelDifferentialTest` compares the kernel with the native decoder on the yaml-test-suite,
the probe fixtures, fuzzed documents, the native tests' policy cases, limits, chunked streams,
guest strings and typed plans. Known differences, all from the parsers rather than the policies,
are listed with reasons in `yaml/tests/java_kernel_excluded.txt`: SnakeYAML Engine's grammar gaps
(the 45 pinned Engine cases, the `\L`/`\P` escapes, NEL as a line break), and raw spans of some quoted
implicit block keys that the native decoder omits. `maxIndexBytes` charges the kernel's published tape, a lower bound of the native arena.

## Writing YAML

`YamlWriter` is a streaming writer and a `TapeSink`, so `value.export(writer)` writes any YAML, JSON or snapshot tape. `YamlEmitter.emit(value, options)` wraps it for one document (`emitAll` for a stream, `emitUtf8` for bytes):

```java
try (YamlMessage message = session.decode(input)) {
  String yaml = YamlEmitter.emit(message.root(),
      YamlEmitOptions.DEFAULT.withSchema(YamlSchema.YAML_1_1).withSortKeys(true));
}

StringBuilder out = new StringBuilder();
try (YamlWriter writer = new YamlWriter(out, YamlEmitOptions.DEFAULT.withFlowDepth(2))) {
  writer.beginMapping();
  writer.key("id");
  writer.value("0o17");           // a string: written "0o17" because CORE reads 0o17 as 15
  writer.key("base");
  writer.anchor("b");
  writer.beginMapping();
  writer.key("x");
  writer.value(1);
  writer.end();
  writer.key("copy");
  writer.alias("b");
  writer.end();
}
```

Every scalar reads back as the same value under `YamlEmitOptions.schema()` (CORE by default). The writer shares the decoder's scalar resolution: a string is plain only when it resolves as a string under the schema (`"123"`, `"true"`, `"0o7"`, `""`, `".inf"` are quoted under CORE; `"yes"` and `"2024-01-01"` under YAML 1.1; every string under JSON) and is syntactically safe where it stands: no leading indicator or white space, no `: `/` #`/trailing `:`, no flow indicators in flow collections, no `---`/`...` document marker, no control, line-break, BOM or non-character code points. Other strings use the preferred quote style (double by default; strings that single quotes cannot carry use double quotes with YAML escapes) or, in block context, a literal block scalar with the matching chomping indicator (`|`, `|-`, `|+`) and an indentation indicator when the first line starts with white space. Keys longer than 1000 characters use the explicit `? key` form. Integers are written in decimal, unsigned and DECIMAL numbers with their exact digits, and floats with the shortest round-trip spelling (`-0.0`, `.inf`, `-.inf`, `.nan`; YAML 1.1 spellings always contain a `.`). Each typed spelling is checked against the resolver; when the schema has no plain spelling the value carries a core tag: every non-string under FAILSAFE (`!!int 1`, `!!null null`) and non-finite floats under JSON (`!!float .inf`). A DECIMAL float token that YAML 1.1 cannot spell with the same text, an unpaired surrogate, or a tag that contradicts its value throws `IllegalArgumentException`.

Options (immutable, `with*`): schema, indent (1..9, default 2), `flowDepth` (collections at that depth or deeper use flow style; `block()`/`flow()` for all-block or all-flow), `sortKeys` (buffers each outermost mapping until it ends; an alias sorted before its anchor takes the anchored node), `indentSequences` (`key:\n  - a` versus `key:\n- a`), `quote`, `blockScalars`, `tildeNull`, `explicitStart`/`explicitEnd` and `preserveMetadata`. An empty collection is written `{}`/`[]`. Lines are never folded and comments are not written.

Explicit calls cover what a sink lacks: `anchor(name)` and `tag(uri)` before a node or key, `alias(name)`, `style(Style)` hints (honored when they represent the value exactly), `key(text, KeyKind)` and `beginDocument()`/`endDocument()`. A second root value starts a new `---` document. With `preserveMetadata`, sink exports keep tags and aliases (anchors are named `a<id>`), and `YamlEmitter` additionally keeps source anchor names, scalar and collection styles and key kinds: `1: x` stays an integer key while `"1": x` stays a string key. An alias whose anchor lies outside the emitted subtree is expanded once and anchored there. Without it, tags are dropped and aliases expanded. Folded scalars are re-emitted as literal ones; anchor names are only renamed when key sorting meets a redefined name.

Writer output is tested by re-parsing it natively and comparing exported events (including `-0.0`, NaN, U64 and DECIMAL) under all four schemas and eight option sets: a hazard corpus in every position, 9,600 seeded random documents with anchors, multi-document streams, 300 JSON fixtures, and the five fixtures plus every decodable pinned yaml-test-suite case re-emitted with and without metadata preservation. Two rapidyaml parse quirks are avoided by construction: a key starting with an indicator or `.` directly after an indentless sequence uses the explicit form, and an anchored or tagged block mapping in a sequence entry whose first key is explicit is written in flow style.

## Myna Truffle cells (`truffledata.yaml.cells`)

`-Dtruffledata.yaml.cells=true` routes a native session's own calls through Truffle cells that
Myna generates from this module's seam, instead of the generated `@CFunction`/FFM calls:
`session_decode`, `session_retain`, `session_reset`, `stream_create`, `stream_feed`,
`stream_next`, `stream_finish`, `stream_release` and a plan's `typed_decode` on both runtimes, and
`session_write_text` on Native Image. Session and plan creation and release, result release and,
on the JVM, calls on a caller-supplied native segment stay on the production bindings
(`yaml/cells/BUILD.bazel` gives the reasons). Lifecycles, staging, limits, errors and their order
stay the backend's own, and the tapes are identical. The route is **off by default**; it is
support for Myna cells and makes no performance claim.

Every routed call uses the generated cell's static `invoke` entry (Myna 0.16.0), for example
`YamlCellsTruffle.session_decode_Cell.invoke(session, data, length, flags, 0, 0, result)`: native
carriers and raw `long` addresses, with no cell instance, argument array, box, `NativePointer` or
`Borrow`. The route is host code below Truffle boundaries, where a cell's `execute(Object[])` would
allocate per call; `invoke` is the entry Myna provides for host code. The caller keeps every
address valid for the call: on Native Image, heap input is pinned around it as the static backend
pins it; on the JVM, `invoke` is passed only memory the backend owns (its staging buffer, result
slot, handles and per-call result segments). It wraps each address with `MemorySegment.ofAddress`
and holds no segment scope, which is why calls on a caller-supplied segment stay on the FFM binding.

Measured: see docs/RELEASE_0_11_0.md

- **Artifact.** `dev.elide:truffledata-yaml-cells` (Bazel `//yaml/cells`) carries the route
  (`CellsSvm`, `CellsSvmSessionBackend`, `CellsSvmPlanBackend`, `CellsJvmSessionBackend`,
  `CellsJvmPlanBackend`), both generated cell sets and Myna's Native Image metadata for the Native
  Image cells. It depends on `truffledata-cells` (Myna's cells runtime), `truffledata-yaml`,
  `truffledata-yaml-bindings`, `truffledata-yaml-native`, `truffledata-yaml-jvm`,
  `truffledata-tape` and `truffledata-tape-jvm`: one jar carries the route for both runtimes, so
  on a native-image class path it also brings the JVM-only `truffledata-yaml-jvm` and
  `truffledata-tape-jvm` (the flag-on test image, `//yaml/cells/tests:native_test`, is built with
  them on its class path). Nothing in the
  default YAML artifacts depends on it; with the flag off it need not be on the class path. It uses
  the same natives as `truffledata-yaml`. The Java kernel (`truffledata-yaml-java`) has no native
  calls and no cells route.
- **Native Image.** Pass `-Dtruffledata.yaml.cells=true` and
  `--features=dev.elide.truffledata.yaml.YamlFeature` to `native-image`, with
  `truffledata-yaml-native` and the cells artifact on the image class path. `YamlFeature` registers
  the cells route at image build time; without the flag it registers nothing and the image contains no
  cells code. The flag and the route must agree or the image build fails, with no fallback: the
  flag without the artifact fails in `YamlFeature`, and the flag without `YamlFeature` fails when
  the build-time holder `SvmRoute` initializes. Setting the property when running an image has no
  effect.
- **JVM.** Pass the property to the JVM with `truffledata-yaml-jvm` and the cells artifact on the
  class path. It is read once, when `CellsRoute` initializes (a `static final`), and selects the
  backend in `YamlJvm.openSession`; the flag without the artifact fails there. Myna's FFM cells
  bind through the class loader's lookup, so the cells also `System.load` the fingerprint-checked
  library: one library path per class loader.
- **Seam and fingerprint.** The cells come from seams *derived* in the build from
  `native/yaml.seam`: only the module options change. The production seam, bindings, ABI
  fingerprint and natives are unchanged, and `//bazel:myna_contract --derived` checks each derived
  descriptor against the production one.
- **No `resource` or `borrow` declarations.** Myna's validator (0.16.0) cannot express them for
  these C signatures without an ABI change: a resource `create` must take only scalars, but
  `session_create` takes the options and `stream_create` the session, and `plan_create` returns a
  status and writes the plan through `result`; a call borrow allows one buffer and scalars only,
  while every YAML buffer import (`session_decode`, `stream_feed`, `typed_decode`) also takes a
  session, stream or plan pointer and a result pointer. Declaring them would change the import
  signatures, that is, the C ABI.
