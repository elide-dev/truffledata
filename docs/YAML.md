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
