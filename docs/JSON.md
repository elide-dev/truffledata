# Native JSON backend (unreleased)

The `//json` module parses general JSON with simdjson 5.1.1 On-Demand directly
into a Myna ABI v3 value index, with no DOM, Serde value or Java object tree in
between. Guests read that index in place through `InteropLibrary`, embedders can
export it into their own objects in one pass, and `snapshot()` detaches it into
immutable heap storage. Known-schema scalar records decode into fixed native slots
instead. Native details and ABI notes are in [`json/native/README.md`](../json/native/README.md);
the delivery plan is [`JSON_PLAN.md`](../JSON_PLAN.md).

The module publishes (from the release after 0.7.0) as `dev.elide:truffledata-json`
(`//json:json`: shared API, SVM backend, typed plans, plus Linux x86-64 `.so`/`.a`
attachments), `truffledata-json-jvm` (`//json:jvm`: FFM backend) and
`truffledata-json-bindings`; see [publishing](PUBLISHING.md#json-native-distribution).

```java
try (JsonDecoder decoder = JsonJvm.open(libraryPath);
     JsonMessage message = decoder.decode(utf8)) {
  JsonValue root = message.root();
  long id = root.member("id").longValue();
  JavaSink sink = new JavaSink();
  root.member("items").export(sink);     // one walk, no per-node native calls
  JsonSnapshot kept = message.snapshot(); // outlives the message and decoder
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
| Binding fingerprint or Myna ABI mismatch | `IllegalStateException` |

## Backend selection

Choose the backend explicitly; nothing searches for a library or falls back to Java.

| Runtime | Entry point | Notes |
| --- | --- | --- |
| Native Image (SVM) | `JsonSvm.open()`, `JsonSvm.decode(...)` | Links `//json/native:truffledata_json` statically. Throws `UnsupportedOperationException` outside image runtime code. |
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

## Sessions (SVM)

A `JsonSession` is an explicit, thread-confined decode context: one native
simdjson parser, a padded input buffer and a block arena reused across documents,
so a small document makes no native allocation in steady state. Nothing opens a
session implicitly: `JsonSvm.decode` keeps decoding each call independently (no
hidden per-thread state); embedders that decode repeatedly open a session per
thread and close it when done. JVM sessions are not available yet
(`JsonSession.open()` throws `UnsupportedOperationException` outside an image).

```java
try (JsonSession session = JsonSession.open()) {            // or open(JsonLimits)
  session.parse(utf8, sink);                                // borrowed: decode, export, done
  long id = session.with(utf8, root -> root.member("id").longValue());
  JsonMessage kept = session.decode(utf8);                  // retained: outlives the session
  session.validate(utf8);                                   // strict check, nothing retained
}
```

- `parse(byte[], JsonSink)` and `parse(byte[], offset, length, JsonSink)` decode
  into session storage and export through the sink. Nothing is retained.
- `with(byte[], Function<JsonValue, R>)` lends the root to a reader. The root and
  every view derived from it belong to a borrowed owner that is closed (and its
  assumption invalidated, so cached compiled reads fail too) when the call
  returns; a view that escapes throws `IllegalStateException` afterwards.
- `decode(...)` moves the decoded storage (tape, side tables and input) into a
  `JsonMessage` without copying the tape. The message has the ownership rules
  below and survives the session's later decodes and its `close()`.
- While a borrow is in progress (inside a sink event or a reader), every other call
  on the session, including `close()`, throws `IllegalStateException`.
- Limits apply per document. Parsing semantics are identical to `JsonSvm.decode`.

## Limits

`JsonLimits(maxInputBytes, maxIndexBytes, maxNodes, maxTextBytes, maxDepth)` bounds
one decode. All values must be positive and `maxDepth` must be within 1..128.

| Limit | `DEFAULT` | Bounds |
| --- | --- | --- |
| `maxInputBytes` | 64 MiB | Logical input, checked before copying |
| `maxIndexBytes` | 64 MiB | Myna nodes, regions, lexical spans, traversal and duplicate-key storage, including reallocation overlap |
| `maxNodes` | 1,000,000 | Index nodes (keys count) |
| `maxTextBytes` | 64 MiB | Myna text bytes for strings and keys |
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
- `close()` is idempotent on the owning thread. It invalidates every `JsonValue`
  derived from the message, the message's `assumption()` and therefore cached
  compiled reads, then releases native memory.
- `close()` throws `IllegalStateException("JSON message is being exported")` while
  an export of that message is running, so a sink cannot free the tape under the
  walk. Close after the export returns.
- Sibling messages have independent lifetimes.
- There is no finalizer or Cleaner; close messages explicitly.

`JsonValue` is a reusable view of one node. Every read checks its owner first, and
that check is inlined into compiled code. `JsonOwner.CheckOpenNode` caches up to
three owners behind their assumptions for nodes that hoist the liveness check.
Retain a value beyond its message only through a snapshot.

## Snapshots

`message.snapshot()` and `value.snapshot()` return a `JsonSnapshot`: immutable,
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

| Token | Node | `JsonSink` event | `JavaSink` | Interop |
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

`JsonValue` exports `InteropLibrary`. It is read-only: no member or element writes,
removals or invocations. Every message checks liveness and thread first.

| Kind | Messages |
| --- | --- |
| OBJECT | `hasMembers`, `getMembers` (source-ordered key strings), `isMemberReadable`, `readMember` |
| ARRAY | `hasArrayElements`, `getArraySize`, `isArrayElementReadable`, `readArrayElement` |
| STRING | `isString`, `asString`, `asTruffleString` (UTF-8, copied once without an intermediate `String`) |
| NUMBER | `isNumber`, `fitsIn*` / `as*` per the number policy above |
| BOOLEAN | `isBoolean`, `asBoolean` |
| NULL | `isNull` |

`readMember` and `readArrayElement` return `JsonValue` views, including for scalars.
Member reads cache the encoded name for up to three name identities per site, so
compiled reads by constant name neither re-encode nor allocate. A member name with
an unpaired surrogate has no UTF-8 encoding and matches nothing: `isMemberReadable`
is false and `readMember` throws `UnknownIdentifierException`. It is never encoded
lossily as `?`. Reads after close fail with `IllegalStateException`, which polyglot
callers see as a `PolyglotException`.

## Exporting to host objects

`JsonValue.export(JsonSink)` and `JsonOwner.export(JsonSink)` (the whole document)
walk the tape once, in source order. A `JsonExportNode` does the same inside guest
code; adopt it as a child node and call `execute(value, sink)`. The walk is a loop
with an explicit container stack: no recursion, no per-node native calls, and one
liveness check per export.

The sink receives:

- `beginObject(members)` or `beginArray(elements)` with the child count, so it can
  presize. Each is matched by one `end()`.
- Inside an object, a `key(JsonValue)` before each member value.
- One event per scalar: `nullValue`, `booleanValue`, `longValue` (I64),
  `doubleValue` (F64, including `-0.0`), `unsignedValue` (U64 as unsigned
  two's-complement bits), `decimalValue(JsonValue)` or `stringValue(JsonValue)`.

Keys, strings and decimals arrive as `JsonValue` views, so the sink chooses how to
materialize them: `stringValue()`, `asTruffleString` through `InteropLibrary`,
`numberText()` or `rawText()`. The views are valid only while the owner is open; do
not retain them. The owner cannot close until the export returns. An exception
from the sink ends the export and releases that hold.

The outermost export on a confined message reuses one 129-slot container stack held
by the message. Nested exports and snapshot exports allocate their own. `JavaSink`
builds `LinkedHashMap` (source order), `ArrayList`, `String`, `Long`, `Double`,
`BigInteger`, `BigDecimal`, `Boolean` and `null`; one sink builds one tree.

### Sketch: a GraalJS `JSON.parse` sink

This shows the intended shape for an embedder such as Elide. GraalJS internal
factory names are illustrative; check the pinned GraalJS revision for exact
signatures.

```java
final class JsSink implements JsonSink {
  private static final InteropLibrary INTEROP = InteropLibrary.getUncached();
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

  public void key(JsonValue name) { key = js(name); }
  public void stringValue(JsonValue text) { add(js(text)); }
  public void nullValue() { add(Null.instance); }
  public void booleanValue(boolean v) { add(v); }
  public void longValue(long v) { add(v == (int) v ? (Object) (int) v : (double) v); }
  public void doubleValue(double v) { add(v); }                  // keeps -0
  public void unsignedValue(long bits) { add(Double.parseDouble(Long.toUnsignedString(bits))); }
  public void decimalValue(JsonValue t) { add(Double.parseDouble(t.numberText())); } // 1E400 -> Infinity

  // UTF-8 bytes are copied once into a TruffleString; JS strings are UTF-16.
  private static TruffleString js(JsonValue v) {
    try {
      return INTEROP.asTruffleString(v).switchEncodingUncached(TruffleString.Encoding.UTF_16);
    } catch (UnsupportedMessageException e) {
      throw new IllegalStateException(e);
    }
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
`plan.decode(utf8)` returns the scoped root `JsonRecord`: validated slots with
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

Every record and array of one decode shares a `JsonTypedOwner` (thread guard,
assumption, close). `record(i)` returns a nested `JsonRecord`, `array(i)` a
`JsonArray` (`BorrowedArray` with interop array exports), and `DataLibrary.read`
returns those for RECORD and ARRAY fields. Closing any record closes the owner and
invalidates all of them. `JsonRecord.snapshot()` and `JsonArray.snapshot()` copy into
owned immutable `Batch` rows and `DataArray`s that survive close.

`JsonAccess` binds a schema and an ordinal path (`new JsonAccess(root, recordTypes,
1, 0, 2)`: RECORD hops, then an INT64/FLOAT64/BOOLEAN leaf) for allocation-free
compiled primitive reads that walk the record table without intermediate
`JsonRecord`s; keep it in a final node field. Records outlive their plans.

On the JVM the plan table is written through the generated FFM layouts from the
same backend-neutral layout the SVM plan uses, and slot and record-entry reads go
through bounded `MemorySegment`s with an exact-class cast. A JVM plan rejects
decoding once its decoder closes; records it returned stay valid, the plan must
still be closed, and the library stays loaded until the last plan and record close.

## Remaining for facades

These interfaces exist for follow-on adapters: ordered members (`keyAt`,
`memberAt`), random lookup, exact and raw token text (`numberText`, `rawText`),
immutable snapshots, single-pass export and typed plans. Still to do:

- **JEP 540**: durable, thread-safe JDK `JsonValue` implementations on a pinned
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
