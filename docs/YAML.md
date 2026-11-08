# YAML (in development)

The native YAML backend uses rapidyaml 0.16.0 with strict YAML 1.2 core resolution. The Java integration is under development and is not yet a published artifact. Typed plans and static-image compiled reads are implemented; the compact producer is implemented, while full performance and release qualification remains unfinished; the [implementation ledger](superpowers/plans/2026-11-06-yaml-ledger.md) records verified scope.

For JVM use, open a session with `YamlJvm.openSession(pathToNativeLibrary)`. For a static image linked against `//yaml/native:svm`, use `YamlSession.open()`. Sessions and retained messages are thread confined.

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

Empty input yields null. `decode` accepts at most one document. Use `documents(byte[])` for a bounded complete buffer or `stream(options)` for incremental feeds. A stream's current view lasts until its next operation; `retain()` detaches it.

`YamlOptions.DEFAULT` rejects duplicate keys after scalar resolution, including keys that stringify identically. `YamlOptions.yaml11()` opts into YAML 1.1 resolution and merge keys; individual policies can be changed explicitly. The JSON schema rejects plain scalars outside JSON spelling. Invalid UTF input, recursive/undefined aliases, complex keys and incompatible tags fail with an exception. Resource limits cover native input/index/text storage, depth, node count, aliases and materialized expansion.

Aliases remain references during decode. Reads resolve them without expansion. JavaSink and TruffleSink reuse anchored objects; custom sinks may decline alias reuse, in which case export expands within `maxAliasExpansion`. The exporter reuses traversal frames and tag cursors after warm-up; compiled export and throughput gates are recorded in the implementation ledger.

`Yaml.tag`, `style`, `anchor`, `isAlias` and `keyKind` expose metadata. Tags follow the resolved alias target; style, anchor and alias provenance describe the source occurrence. `Yaml.binary` materializes a validated `!!binary` scalar, and `Yaml.timestamp` produces LocalDate or OffsetDateTime (UTC for an absent zone, nanosecond precision). Quoted/block scalar source spelling requires `withRetainSource(true)`; number spelling is available regardless. Nonfinite YAML floats use the compact tape's nonfinite flag and preserve Myna's allow-nonfinite flag in compatibility copies.

Compile a known record schema with `session.compile(schema, recordTypes)` and call `plan.decode(bytes)` to obtain a `TypedRecord`. Plans copy the session's options and limits; plans and results each remain valid after the session closes. Results also survive their plan closing. Closing a record invalidates all views of that result, while `snapshot()` creates an independent copy.

Typed fields resolve plain scalars according to their contract: `1.0` is preserved in a string field, `0x1f` is 31 in an integer field, and `.inf` is valid in a floating field. Quoted scalars require string fields. Nested and repeated records, aliases, enabled merge keys, integer/float width and string-length constraints use the shared typed layout and `TypedAccess`. Unknown, duplicate and missing required fields are rejected; typed plans always reject duplicates even when generic decoding uses last-wins.

The native ABI publishes compact words with embedded scalar spans and object directories, plus one YAML metadata word per tape word. Value aliases use native REF words. Double-quoted surrogate pairs decode to one Unicode scalar; unpaired surrogates are rejected before text reaches the tape.

The JVM differential suite compares 5,000 seeded YAML documents and the pinned yaml-test-suite with SnakeYAML Engine 3.2, explicitly disabling Engine's merge resolver. It also compares 2,000 shared JSON benchmark documents and the suite's 282 JSON fixture files against the JSON backend, including numeric kinds, member order, Unicode and escaped duplicate keys. The 45 measured Engine parser exclusions remain pinned; 14 custom-tag cases separately assert the documented preserve-versus-construction policy.
