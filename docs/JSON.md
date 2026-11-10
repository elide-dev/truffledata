# Native JSON backend

The `//json` module parses general JSON with a checked stage2 walk over simdjson 5.1.1's structural index directly
into a compact word tape, with no DOM, Serde value or Java object tree in
between. Guests read that index in place through `InteropLibrary`, embedders can
export it into their own objects in one pass, and `snapshot()` detaches it into
immutable heap storage. Known-schema scalar records decode into fixed native slots
instead. Native details and ABI notes are in [`json/native/README.md`](../json/native/README.md);
the delivery plan is [`JSON_PLAN.md`](../JSON_PLAN.md).

The module publishes (from 0.8.0) as `dev.elide:truffledata-json`
(`//json:json`: the JSON entry points, SVM backend and typed plans, plus `.a` and `.so`/`.dylib`
attachments for Linux x86-64, Linux AArch64 and macOS AArch64), `truffledata-json-jvm` (`//json:jvm`: FFM backend) and
`truffledata-json-bindings`, on top of the shared `truffledata-tape` and
`truffledata-tape-jvm`; see [publishing](PUBLISHING.md#json-native-distribution).

### Shared tape types

The format-neutral half of this backend lives in `//tape` (package
`dev.elide.truffledata.tape`), which the YAML backend shares. JSON produces its tapes;
the owners, views, sinks, snapshots, session and stream plumbing and typed records
that read them are the same classes for every format. `//json` re-exports `//tape` (and
`//json:jvm` re-exports `//tape:jvm`), so a JSON dependency brings them along.

| Shared (`//tape`) | Was (JSON) |
| --- | --- |
| `TapeOwner` (owners: liveness, confinement, pins, export cursor, array index, directory lookups, alias-resolution hook) | `JsonOwner` |
| `TapeValue`, `TapeSink`, `TapeText`, `TapeKey` | `JsonValue`, `JsonSink`, `JsonText`, `JsonKey` |
| `TapeSnapshot`, `TapeExportNode`, `JavaSink`, `TruffleSink` | `JsonSnapshot`, `JsonExportNode`, `JavaSink`, `TruffleSink` |
| `TypedOwner`, `TypedRecord`, `TypedArray`, `TypedAccess`, `TypedLayout` | `JsonTypedOwner`, `JsonRecord`, `JsonArray`, `JsonAccess`, `JsonPlan.Layout` |
| `TapeSession`, `TapeStream` (borrow, cursor, text-arena and stream bookkeeping) | the shared half of `JsonSession` and `JsonStream` |
| `TapeBorrow`, `TapeLexical`, `TapeTextArena`, `TapeFormat` | `JsonBorrow`, `JsonMessage.Lexical`, `JsonTextArena`, new |
| `HeapValueTape`, `SvmValueTape`, `TapeSource`; `SegmentValueTape` in `//tape:jvm` | same names |

The JSON entry points keep their names and behaviour: `JsonSession` is a
`TapeSession<JsonMessage>` (so `decode` returns `JsonMessage` and `with` lends a
`TapeValue`), `JsonStream` a `TapeStream<JsonMessage>` that adds `skip`,
`truncatedBytes` and `options`, and `JsonMessage`, `JsonPlan`, `JsonLimits`,
`JsonSvm`, `JsonJvm` and `JsonDecoder` are unchanged apart from the renamed types.
Messages still name JSON (`"JSON message closed"`), because every owner reports its
`TapeFormat`; only the format-independent objects (sinks, text cursors and typed
records and accessors) say no format name. `TapeSink` gains YAML's `tag`, `anchor`
and `alias` events with no-op defaults; JSON never emits them, and `TapeOwner.resolve`
is the identity for JSON owners, which carry no alias tables.

```java
try (JsonDecoder decoder = JsonJvm.open(libraryPath);
     JsonMessage message = decoder.decode(utf8)) {
  TapeValue root = message.root();
  long id = root.member("id").longValue();
  JavaSink sink = new JavaSink();
  root.member("items").export(sink);     // one walk, no per-node native calls
  TapeSnapshot kept = message.snapshot(); // outlives the message and decoder
}
```

## Parsing contract

Parsing is strict: no comments, trailing commas, NaN or Infinity. Every value is
visited and validated before a message is published, including fields nobody reads.
Duplicate member names are rejected after escape decoding, trailing input is
rejected, and every root kind is accepted. Member order is preserved; missing and
null are distinct. Malformed input, limit failures and ABI mismatches throw; there
is no fallback to another parser.

| Failure | Exception |
| --- | --- |
| Empty input, syntax, duplicate key, invalid argument | `IllegalArgumentException` |
| `JsonLimits` exceeded | `IllegalArgumentException("JSON resource limit")` |
| Native allocation failure | `OutOfMemoryError` |
| Binding fingerprint or compact tape ABI mismatch | `IllegalStateException` |

## Backend selection

Choose the backend explicitly; nothing searches for a library or falls back to Java.

| Runtime | Entry point | Notes |
| --- | --- | --- |
| Native Image (SVM) | `JsonSvm.open()`, `JsonSvm.decode(...)` | Links `//json/native:svm` statically (the C ABI and shared simdutf transcoder). Throws `UnsupportedOperationException` outside image runtime code. |
| JVM | `JsonJvm.open(Path library)` | Loads `json_shared.so` through FFM. Keep `//json:jvm` out of static images. |

Both check the generated binding fingerprint before the first decode. Both
implement `JsonDecoder`:

- `decode(byte[])` / `decode(byte[], JsonLimits)` copy the input once into padded
  storage the message retains. On SVM the native side makes that copy; on the JVM
  Java copies into a padded confined arena and the native side adopts it.
- `adopt(MemorySegment data, long length, Runnable release)` (JVM) treats
  `data.byteSize()` as capacity. With at least 64 bytes of padding beyond `length`
  the input is parsed in place, otherwise it is copied once. SVM uses the explicitly
  unsafe `JsonSvm.adopt(address, length, capacity, release)`; its `JsonDecoder.adopt`
  throws `UnsupportedOperationException`.

`release` runs exactly once, on the owning thread, when the message closes, even if
the input was copied. If adoption fails, it does not run and the caller keeps the
region. The caller must not mutate or free the region before `release`. Adoption
currently always uses `JsonLimits.DEFAULT`.

The SVM decoder handle holds no state; closing it is a no-op. The JVM decoder is
confined to the thread that opened it. Closing it never invalidates returned
messages or typed records: the library stays loaded until the last message, typed
plan and typed record close.

## Sessions

A `JsonSession` is an explicit, thread-confined decode context: one native
simdjson parser, a padded input buffer and a block arena reused across documents,
so a small document makes no native allocation in steady state. Nothing opens a
session implicitly: `JsonSvm.decode` keeps decoding each call independently (no
hidden per-thread state); embedders that decode repeatedly open a session per
thread and close it when done. In a static image open one with `JsonSession.open()`;
on the JVM with `JsonJvm.openSession(libraryPath, limits)` (`JsonSession.open()` throws
`UnsupportedOperationException` outside an image). Both run the same native session; see
"JVM sessions" below for the differences.

```java
try (JsonSession session = JsonSession.open()) {            // or open(JsonLimits)
  session.parse(utf8, sink);                                // borrowed: decode, export, done
  long id = session.with(utf8, root -> root.member("id").longValue());
  JsonMessage kept = session.decode(utf8);                  // retained: outlives the session
  session.validate(utf8);                                   // strict check, nothing retained
}
```

- `parse(byte[], TapeSink)` and `parse(byte[], offset, length, TapeSink)` decode
  into session storage and export through the sink. Nothing is retained.
- `parse(TruffleString, TapeSink)` decodes a guest string without a Java copy:
  the string's own storage goes to the native session as UTF-8 (ASCII code range),
  Latin-1 (Latin-1 range) or UTF-16LE (anything else), and simdutf validates and
  transcodes it into the padded parser buffer. An unpaired surrogate anywhere is
  invalid JSON (`IllegalArgumentException`); limits apply to the UTF-8 form, which
  lexical spans and `numberText()` describe. A string in another encoding is first
  transcoded to UTF-8 by `TruffleString`, and invalid UTF-8 is rejected.
- Text arenas: when the sink's `sharedText()` returns true (`TruffleSink` does),
  the same native call also writes every key and string once into a per-document
  `byte[]` presized to `2 × length + 16`: Latin-1 when all its code points are at
  most U+00FF, UTF-16LE otherwise. `TapeText.toTruffleString(encoding)` then builds
  each string over that array without a copy (a Latin-1 string becomes UTF-16 without
  one too), so a parse costs one array plus one `TruffleString` per string. Strings
  of 64 KiB or more are copied, so one small retained string never keeps a large
  document's text alive. Once any string shares the array, the session gives the next
  parse a fresh one; an array nobody took is reused.
- `with(byte[], Function<TapeValue, R>)` lends the root to a reader. The root and
  every view derived from it belong to a borrowed owner that is closed (and its
  assumption invalidated, so cached compiled reads fail too) when the call
  returns; a view that escapes throws `IllegalStateException` afterwards.
- `decode(...)` moves the decoded storage (tape, side tables and input) into a
  `JsonMessage` without copying the tape. The message has the ownership rules
  below and survives the session's later decodes and its `close()`.
- While a borrow is in progress (inside a sink event or a reader), every other call
  on the session, including `close()`, throws `IllegalStateException`.
- Limits apply per document. Parsing semantics are identical to `JsonSvm.decode`.

### Streams

`JsonSession.stream(JsonStreamOptions)` reads whitespace-separated documents
(NDJSON, JSON Lines, concatenated JSON) one at a time through the session:

```java
try (JsonSession session = JsonSession.open();
     JsonStream stream = session.stream(JsonStreamOptions.DEFAULT)) {   // 1 MiB documents
  int at = 0;
  while (at < input.length) {
    at += stream.feed(input, at, input.length - at);  // accepted bytes: fewer means drain first
    TapeValue root;
    while ((root = stream.next()) != null) consume(root);  // lent until the next call
  }
  stream.finish();                                     // a final number or literal completes
  while (stream.next(sink)) {}                         // or export each document
}
```

- The carry-over buffer holds at most `2 × maxDocumentBytes`; `batchBytes` (the stage1 streaming batch limit) must be at least `maxDocumentBytes`. `feed` accepts nothing while
  buffered documents are still being read: drain with `next` until it returns `false` or
  `null`, then feed the rest.
- `next(TapeSink)` exports a document; `next()` lends its root until the next call on the
  stream, another stream of the session or the session (views throw afterwards);
  `retain()` detaches the lent document into a `JsonMessage` without copying the tape.
  Streams never share a text array: a `sharedText()` sink gets each string copied.
- Only whitespace separates documents: `[1],[2]` is a syntax error (simdjson's comma
  format accepts doubled and missing commas). A BOM is accepted only at the start.
- An invalid document throws `IllegalArgumentException` from `next`, and the stream stays
  failed until `skip()` resumes after that document. Invalid UTF-8 and raw control bytes
  are reported by simdjson for a whole batch and cannot be skipped. After `finish()`, a
  final incomplete document throws ("truncated") and `truncatedBytes()` is its length.
- Each document is decoded with the session's limits and duplicate-key and number rules;
  closing the session closes its streams.

### JVM sessions

`JsonJvm.openSession(Path library, JsonLimits limits)` opens the same native session
through Myna's generated FFM `LibraryBinding`; the library stays loaded until the
session and every message it retained are closed. Differences from static images:

- Normal FFM downcalls take native memory only, so array input (and a guest string's
  storage) is first copied into a reusable session-owned native staging buffer.
- `parse(MemorySegment data, long length, TapeSink)` borrows a live native segment the
  calling thread can access for the call (copied natively; nothing retained). Heap,
  closed and other threads' segments are rejected before entry.
- No text arenas: a Java array cannot be the target of a normal downcall, so a
  `sharedText()` sink receives strings copied once each.
- `JsonStream.feed(byte[], …)` stages at most `2 × maxDocumentBytes` per call natively;
  `feed(MemorySegment)` passes a live native segment directly (heap segments rejected).
- Inputs of at most `JsonSession.CRITICAL_LIMIT` (4,096) bytes decode through Myna's
  `CriticalLibraryBinding` (`session_decode_small`), and `reset` always does: both are
  declared native leaves. The safety argument: the input is bounded (4 KiB at the
  slowest measured decode rate, ≈ 150 MB/s, is ≈ 30 µs of delayed safepoint), the
  native side takes no lock, makes no callback and lets no exception escape, and the
  staged input is native memory (Myna's critical adapters forbid heap segments). Pass
  a smaller `criticalLimit` to `openSession(Path, JsonLimits, int)`, or 0 to disable;
  `criticalDecodes()` counts the decodes that took the path. A diagnostic timing found
  no measurable difference against normal transitions for the 210-byte fixture
  document on HotSpot.

## Limits

`JsonLimits(maxInputBytes, maxIndexBytes, maxNodes, maxTextBytes, maxDepth)` bounds
one decode. All values must be positive and `maxDepth` must be within 1..128.

| Limit | `DEFAULT` | Bounds |
| --- | --- | --- |
| `maxInputBytes` | 64 MiB | Logical input, checked before copying |
| `maxIndexBytes` | 64 MiB | Compact words, directories, traversal and duplicate-key storage, including reallocation overlap |
| `maxNodes` | 1,000,000 | Index nodes (keys count) |
| `maxTextBytes` | 64 MiB | Decoded text bytes for strings and keys |
| `maxDepth` | 128 | Container nesting |

The index budget is charged per arena block and large allocation (see the native
README), so it also counts unused block tails. Parser scratch is owned by a bounded
process-wide pool (at most eight idle parsers), or by the session, and is not
counted. These limits are not a total native-memory budget. The typed
scalar path does not take `JsonLimits`; it uses fixed 64 MiB input and index caps.

## Ownership

Ownership follows the Buffa model: thread-confined scoped owners, explicit
detachment, and Truffle assumptions invalidated on close.

- A `JsonMessage` retains its input, index, decoded strings and lexical spans.
  Parsers return to the pool before decode completes, so no message depends on
  parser scratch and parser reuse cannot overwrite live data.
- Closing the input source or the decoder does not invalidate a message.
- A message is confined to the thread that decoded it. Cross-thread reads and
  cross-thread `close()` throw `IllegalStateException`. Asynchronous transfer is not
  implemented.
- `close()` is idempotent on the owning thread. It invalidates every `TapeValue`
  derived from the message, the message's `assumption()` and therefore cached
  compiled reads, then releases native memory.
- `close()` throws `IllegalStateException("JSON message is being exported")` while
  an export of that message is running, so a sink cannot free the tape under the
  walk. Close after the export returns.
- Sibling messages have independent lifetimes.
- There is no finalizer or Cleaner; close messages explicitly.

`TapeValue` is a reusable view of one node. Every read checks its owner first, and
that check is inlined into compiled code. `TapeOwner.CheckOpenNode` caches up to
three owners behind their assumptions for nodes that hoist the liveness check.
Retain a value beyond its message only through a snapshot.

## Snapshots

`message.snapshot()` and `value.snapshot()` return a `TapeSnapshot`: immutable,
unconfined and never closed. Its `assumption()` is `Assumption.ALWAYS_VALID`, and its
reads never touch native memory. A snapshot remains valid after its message and
decoder close, and can be read from any thread.

A subtree snapshot copies the subtree's nodes, re-based to node 0, but copies every
string region and the whole retained input, so its cost follows the document's text
size rather than the subtree's. Lexical spans are copied, so `numberText()` and
`rawText()` behave as on the message. `snapshot()` of a whole snapshot returns the
same object. Owners index arrays for random access in a concurrent cache, so a
shared snapshot is safe to read concurrently.

## Reading values from Java

| Method | Behavior |
| --- | --- |
| `kind()` | `NULL`, `BOOLEAN`, `NUMBER`, `STRING`, `ARRAY` or `OBJECT` |
| `longValue()` / `doubleValue()` | I64 or F64 nodes only; other number kinds throw rather than convert |
| `stringValue()` | Decoded Java `String` |
| `numberText()` | Exact token of any number, with no conversion |
| `rawText()` | Source spelling of a number or string (keys included), with quotes and escapes |
| `member(String)` / `member(Utf8Key)` | Lookup; throws `NoSuchElementException` when missing. Retain a `ValueTape.Utf8Key` for repeated lookups. |
| `keyAt(i)` / `memberAt(i)` | Members in source order |
| `element(i)` / `elements()` | Random access, or a sequential iterator that builds no index |
| `size()` | Member or element count |

Arrays with more than eight elements build a child-offset index on first random
access and keep it while the owner lives; smaller arrays are walked.
`member(String)` throws `IllegalArgumentException` for a name with an unpaired
surrogate.

## Number policy

The decoder classifies each token once. `numberText()` always returns the exact
token, whatever its node kind.

| Token | Node | `TapeSink` event | `JavaSink` | Interop |
| --- | --- | --- | --- | --- |
| Integer in `int64` (`-42`) | I64 | `longValue` | `Long` | Integer types by range; `BigInteger`; `double` when \|v\| ≤ 2^53; `float` when also exact |
| `-0` | F64 `-0.0` | `doubleValue(-0.0)` | `Double` `-0.0` | `asDouble` is `-0.0`; no integer type fits (the sign would be lost) |
| Integer above `int64` within `uint64` | U64 | `unsignedValue(bits)` | `BigInteger` | `BigInteger` only |
| Other token simdjson converts to a finite binary64 (`1.5`, `1e2`) | F64 | `doubleValue` | `Double` | `double`; `float` when exact; integer types when integral and in range |
| Anything else (`1E400`, integers beyond `uint64`) | DECIMAL, referencing the input | `decimalValue(token)` | `BigDecimal` | `BigInteger` when integral with at most 4096 integer digits (huge exponents such as `1E999999999` fit nothing and are never expanded); `double` only when exactly representable (`1180591620717411303424`, 2^70); never `long` |

F64 holds simdjson's nearest binary64, so a token such as
`1.0000000000000000001` reads as `1.0`. Interop `fits*` contracts describe the
node value, not the token; use `numberText()` when the spelling matters.
`JavaSink` throws `NumberFormatException` for DECIMAL exponents outside
`BigDecimal`'s scale range.

## Guest interop

`TapeValue` exports `InteropLibrary`. It is read-only: no member or element writes,
removals or invocations. Every message checks liveness and thread first.

| Kind | Messages |
| --- | --- |
| OBJECT | `hasMembers`, `getMembers` (source-ordered key strings), `isMemberReadable`, `readMember` |
| ARRAY | `hasArrayElements`, `getArraySize`, `isArrayElementReadable`, `readArrayElement` |
| STRING | `isString`, `asString`, `asTruffleString` (UTF-8, copied once without an intermediate `String`) |
| NUMBER | `isNumber`, `fitsIn*` / `as*` per the number policy above |
| BOOLEAN | `isBoolean`, `asBoolean` |
| NULL | `isNull` |

`readMember` and `readArrayElement` return `TapeValue` views, including for scalars.
Member reads cache the encoded name for up to three name identities per site, so
compiled reads by constant name neither re-encode nor allocate. A member name with
an unpaired surrogate has no UTF-8 encoding and matches nothing: `isMemberReadable`
is false and `readMember` throws `UnknownIdentifierException`. It is never encoded
lossily as `?`. Reads after close fail with `IllegalStateException`, which polyglot
callers see as a `PolyglotException`.

Member lookup uses compact object directories. Objects with at least two members carry
one `(hash, key word offset)` entry per member, sorted by unsigned FNV-1a hash and then
offset. Objects without directories are scanned. A cached member read remembers a rank
in that directory, verifies the current key's kind, length and bytes before using it, and
falls back to a binary search on a stale hint. Second words are skipped when moving from
an escaped key to its value. `TapeKey.of(name)` prepares a key for repeated lookups.

`JsonMessage.toMyna()` and `TapeSnapshot.toMyna()` create detached Myna ABI v4 tapes on
demand. This copy is for interchange; ordinary reads, exports and snapshots read compact
words directly. Snapshots copy the subtree's referenced input and pool ranges and rebase
its spans and directories. `rawText()` therefore keeps the original spelling after the
source owner closes.

## Exporting to host objects

`TapeValue.export(TapeSink)` and `TapeOwner.export(TapeSink)` (the whole document)
walk the tape once, in source order. A `TapeExportNode` does the same inside guest
code; adopt it as a child node and call `execute(value, sink)`. The walk is a loop
with an explicit container stack: no recursion, no per-node native calls, one
liveness check per export, and no view per key or string.

The sink receives:

- `beginObject(members)` or `beginArray(elements)` with the child count, so it can
  presize. Each is matched by one `end()`.
- Inside an object, a `key(TapeText)` before each member value.
- One event per scalar: `nullValue`, `booleanValue`, `longValue` (I64),
  `doubleValue` (F64, including `-0.0`), `unsignedValue` (U64 as unsigned
  two's-complement bits), `decimalValue(TapeText)` or `stringValue(TapeText)`.

Keys, strings and decimals arrive through one reusable `TapeText` cursor per export,
rebound for each event, so an export creates no object per string. The sink chooses
how to materialize: `toJavaString()` (a decimal gives its exact token),
`toTruffleString(encoding)`, `copyUtf8(byte[], offset)`, `utf8Equals(byte[])`,
`utf8Length()`, `isAscii()` (from the word's ASCII flag, no scan) and `hash()`
(the low 15 bits of the key's FNV-1a hash; 0 for strings and decimals). The cursor is valid
only during the event that passed it: afterwards every method throws
`IllegalStateException`. `view()` returns a `TapeValue` that lives as long as the
owner. The owner cannot close until the export returns. An exception from the sink
ends the export and releases that hold.

`toJavaString()` decodes ASCII text into a Latin-1 `String` from a reusable scratch
buffer (one copy) and other short text with the JDK's UTF-8 decoder. Non-ASCII
strings of 256 bytes or more use the shared native simdutf transcoder
(`td_tape_utf8_to_utf16`) on SVM, writing UTF-16 into a pinned character array.

The outermost export on a confined message reuses one cursor (and its 129-slot
container stack) held by the message; a session's borrowed parses share one cursor.
Nested exports and snapshot exports allocate their own. `JavaSink` builds
`LinkedHashMap` (source order), `ArrayList`, `String`, `Long`, `Double`,
`BigInteger`, `BigDecimal`, `Boolean` and `null`; one sink builds one tree.
`new JavaSink(keyCache)` takes member names from a `JavaSink.KeyCache` shared by the
sinks of one thread: a bounded (1,024 names) table keyed by the key hash and the
decoded bytes, so documents of one schema stop allocating key `String`s.

`TruffleSink` is the reference sink for Truffle guests: the same tree shape with
UTF-16 `TruffleString` keys (from a `TruffleSink.KeyCache`, copied once out of the
document) and string values. It asks for shared text, so through a `JsonSession`
parse its strings share the document's text arena; exporting a retained message or
snapshot (no arena) copies each string once.

### Sketch: a GraalJS `JSON.parse` sink

This shows the intended shape for an embedder such as Elide. GraalJS internal
factory names are illustrative; check the pinned GraalJS revision for exact
signatures.

```java
final class JsSink implements TapeSink {
  private final JSContext context;
  private final JSRealm realm;
  private final ArrayDeque<Object> open = new ArrayDeque<>(); // JSObject or ArrayFrame
  private TruffleString key;
  Object result;

  private record ArrayFrame(Object[] items, int[] size) {}

  public void beginObject(long members) { push(JSOrdinary.create(context, realm)); }
  public void beginArray(long elements) { push(new ArrayFrame(new Object[(int) elements], new int[1])); }

  public void end() {
    Object done = open.pop();
    if (done instanceof ArrayFrame f) add(JSArray.createConstantObjectArray(context, realm, f.items()));
  }

  public void key(TapeText name) { key = js(name); }
  public void stringValue(TapeText text) { add(js(text)); }
  public void nullValue() { add(Null.instance); }
  public void booleanValue(boolean v) { add(v); }
  public void longValue(long v) { add(v == (int) v ? (Object) (int) v : (double) v); }
  public void doubleValue(double v) { add(v); }                  // keeps -0
  public void unsignedValue(long bits) { add(Double.parseDouble(Long.toUnsignedString(bits))); }
  public void decimalValue(TapeText t) { add(Double.parseDouble(t.toJavaString())); } // 1E400 -> Infinity
  public boolean sharedText() { return true; }                   // session parses fill a text arena

  // In a session parse: a UTF-16 view of the document's text arena, no copy. Otherwise the
  // UTF-8 bytes are copied once. (A real sink interns keys, as TruffleSink.KeyCache does.)
  private static TruffleString js(TapeText v) {
    return v.toTruffleString(TruffleString.Encoding.UTF_16);
  }

  private void push(Object container) {
    if (!(container instanceof ArrayFrame)) add(container); // arrays join their parent at end()
    open.push(container);
  }

  private void add(Object value) {
    Object parent = open.peek();
    if (parent == null) result = value;
    else if (parent instanceof ArrayFrame f) f.items()[f.size()[0]++] = value;
    else JSObject.set((JSObject) parent, key, value);         // key -> property write
  }
}
```

Presized arrays, one property write per member, and no intermediate Java tree are
the point. The sink controls number coercion: this one follows `JSON.parse`, which
rounds integers above 2^53 and DECIMAL tokens to `double`.

## Typed records

Known-schema JSON compiles through `decoder.compile(schema)` or
`decoder.compile(root, recordTypes)` on either backend's `JsonDecoder` into a
reusable, thread-confined native plan (`JsonPlan.compile(...)` is the static SVM
shortcut, and `JsonSvm.open().compile(...)` returns the same plan).
`plan.decode(utf8)` returns the scoped root `TypedRecord`: validated slots with
separate presence and null masks, read with primitive getters or through
`DataLibrary`. Strings and unsigned `BigInteger`s materialize on request.

Records hold up to 64 fields of INT64, FLOAT64, BOOLEAN, STRING, unsigned 64-bit
EXACT, RECORD and ARRAY, with required/nullable, integer range, UTF-8/UTF-16/code
point length and binary32 representability contracts. A RECORD field names its
schema with `Constraints.recordType()`, a key of `recordTypes` whose schema has that
same `typeName`. An ARRAY field takes its element contract from
`Constraints.element()`: a scalar or RECORD that is neither nullable nor optional
(JSON array elements are never null; nullability applies to the array). Element
counts (`Length` on ARRAY) and arrays of arrays are not supported. Recursive record
types and nesting deeper than 32 records (the root counts as 1) are rejected when
the plan compiles. Unknown fields, escaped duplicates, missing required fields, type
mismatches and trailing input are rejected at every level.

Every record and array of one decode shares a `TypedOwner` (thread guard,
assumption, close). `record(i)` returns a nested `TypedRecord`, `array(i)` a
`TypedArray` (`BorrowedArray` with interop array exports), and `DataLibrary.read`
returns those for RECORD and ARRAY fields. Closing any record closes the owner and
invalidates all of them. `TypedRecord.snapshot()` and `TypedArray.snapshot()` copy into
owned immutable `Batch` rows and `DataArray`s that survive close.

`TypedAccess` binds a schema and an ordinal path (`new TypedAccess(root, recordTypes,
1, 0, 2)`: RECORD hops, then an INT64/FLOAT64/BOOLEAN leaf) for allocation-free
compiled primitive reads that walk the record table without intermediate
`TypedRecord`s; keep it in a final node field. Records outlive their plans.

On the JVM the plan table is written through the generated FFM layouts from the
same backend-neutral layout the SVM plan uses, and slot and record-entry reads go
through bounded `MemorySegment`s with an exact-class cast. A JVM plan rejects
decoding once its decoder closes; records it returned stay valid, the plan must
still be closed, and the library stays loaded until the last plan and record close.

## Myna Truffle cells (`truffledata.json.cells`)

`-Dtruffledata.json.cells=true` routes the document decode's native calls through Truffle cells
that Myna generates from this module's seam, instead of the generated `@CFunction`/FFM calls: on
Native Image `td_json_decode`, `td_json_decode_adopted` and `td_json_release` (`JsonSvm`), on
HotSpot `td_json_decode_adopted` and `td_json_release` (`JsonJvm`, whose `decode(byte[])` copies
into an adopted arena). Every check, limit, status mapping, error and owner lifetime stays the
backend's own. The route is **off by default**; it is support for Myna cells and makes no
performance claim.

Measured: see docs/RELEASE_0_11_0.md

- **Artifact.** `dev.elide:truffledata-json-cells` (Bazel `//json/cells`) carries the route
  (`CellsSvm`, `CellsFfm`, `JsonCellsFeature`), both generated cell sets and Myna's Native Image
  metadata for the Native Image cells; it depends on `truffledata-cells` (Myna's cells runtime),
  `truffledata-json` and `truffledata-json-bindings`. Nothing in the default JSON artifacts
  depends on it; with the flag off it need not be on the class path. It uses the same natives as
  `truffledata-json`.
- **Native Image.** Pass `-Dtruffledata.json.cells=true` and
  `--features=dev.elide.truffledata.json.JsonCellsFeature` to `native-image`, with the cells
  artifact on the image class path. The Feature installs the cells at image build time and
  `JsonCellsRoute.ENABLED` folds, so an image built without the flag contains no cells code;
  setting the property when running an image has no effect. The flag and the route must agree or
  the image build fails, with no silent fallback: the build-time holder `JsonCellsRoute` of
  `truffledata-json` rejects the flag without the Feature, and the Feature cannot load without the
  artifact. The Feature without the flag installs nothing.
- **JVM.** Pass the property to the JVM with `truffledata-json-jvm` and the cells artifact on the
  class path. It is read once, when `JsonCellsRoute` initializes (a `static final`), and
  `JsonJvm.open` binds the cells; the flag without the artifact fails there. Myna's FFM cells bind
  through the class loader's lookup, so the cells also `System.load` the fingerprint-checked
  library: one library path per class loader.
- **Seam and fingerprint.** The cells come from seams *derived* in the build from
  `native/json.seam`: only the module options change (`truffle_cells=true`, `truffle_backend`
  `native`/`ffm`, `truffle_transitions=direct`, package and class). The production seam, bindings,
  ABI fingerprint and natives are unchanged; the backends check the library against the production
  fingerprint, and `//bazel:myna_contract` checks that each derived descriptor equals the
  production one apart from those options.
- **No `resource` or `borrow` declarations.** Myna 0.14.0's validator cannot express them for
  these C signatures without an ABI change: a resource `create` must take only scalars and return
  `ptr<Opaque>`, and a call borrow allows no other pointer parameter, while every JSON buffer import
  also takes a result, session or plan pointer. `td_json_decode_adopted` also retains its input,
  which a call-lifetime borrow would misdeclare, and either declaration would make the derived
  descriptor differ from the production one (see `json/cells/BUILD.bazel`).

## Remaining for facades

These interfaces exist for follow-on adapters: ordered members (`keyAt`,
`memberAt`), random lookup, exact and raw token text (`numberText`, `rawText`),
immutable snapshots, single-pass export and typed plans. Still to do:

- **JEP 540**: durable, thread-safe JDK `TapeValue` implementations on a pinned
  JDK 28 early-access lane, backed initially by snapshots; JSON generation with
  exact number emission; Java UTF-16 factory semantics for unpaired surrogates; and
  differential conformance against the pinned JDK, including its surrogate policy.
  No JEP compliance is claimed.
- **Jackson 3, then JSON-P, Gson on demand**: parser and generator adapters over
  the completed index. These are token iteration, not bounded-memory incremental
  parsing; stream or batch parsing needs its own buffering and lifetime design.
- **kotlinx**: a `Decoder` adapter in Komodo, consuming ordered members, raw text,
  typed plans and snapshots.
- Cross-thread ownership transfer, caller-chosen limits for adoption, and
  published Maven artifacts.

## Verification

```sh
bazel test //json/tests:jvm                     # JVM FFM, interop, export, snapshots
bazel test //json/tests:native_test             # SVM, compiled read/export gates (Linux x86-64, RBE)
bazel test //json/native:test //json/native:api_test //json/native:typed_test
```

The SVM compiled gates cover typed and generic member reads, interop reads and the
export loop, bound Java allocation per iteration, and require compiled reads to
fail after close. They measure
retained reads, not parse cost or native memory. Comparison results against Gson
are in [`benchmarks/comparison/JSON.md`](../benchmarks/comparison/JSON.md). Its
generic cells (Gson trees, `JavaSink` export, interop reads, snapshots) gate the
compiled interop and export loops on graph shape and a fixed per-document allocation
budget. The first measurement found generic reads slower than reading Gson trees
and snapshot reads allocating heavily; see that page before relying on either path
for speed.

The generic native producer, borrowed sessions and streams share the structural stage2 walk.
Typed plans use the same walk with checked schema-key lookup and typed slot contracts;
nested/repeated plans add bounded record/array frames. On-Demand remains the test oracle
and runs after a failed nested decode to preserve error diagnostics; a structural failure
cannot publish a result. The previous generic producer is a test-only differential oracle.
Production string kernels remain portable: measured AVX2 and AVX-512 variants did not clear
the adoption gate on unclemax. The [nested/repeated admission report](../benchmarks/results/json-typed-tranche-20261108/admission.md)
records paired SVM ingest gains and control regressions. Earlier qualification and explicitly
unmet criteria remain in the [producer ledger](superpowers/plans/2026-11-07-json-sota-ledger.md).
