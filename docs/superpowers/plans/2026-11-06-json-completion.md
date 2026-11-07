# JSON Backend Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the simdjson backend so arbitrary JSON and known-schema JSON both decode natively on SVM and JVM, expose scoped guest values, export into host object graphs (the Elide `JSON.parse` replacement path), snapshot, and are measured against stock parsers.

**Architecture:** C++ simdjson On-Demand writes Myna ABI v3 tapes (generic) or fixed slot arenas (typed) behind one versioned C ABI (`json.seam`, ABI 4). Java owns lifetime: thread-confined `JsonMessage`/`JsonRecord` owners with Truffle assumptions, explicit snapshots, and adopted native input. Guest access is through `InteropLibrary` exports on `JsonValue` plus a `JsonSink` traversal that lets an embedder (Elide/GraalJS, Komodo/kotlinx) build its own objects in one pass with no per-node native calls.

**Tech Stack:** C++17, simdjson 5.1.1, simdutf (`//bazel:unicode`), Myna values (ABI v3, C++ `value_builder`, Java `ValueTape`), GraalVM 25 (SVM `@CFunction` bindings and JVM FFM, both generated from `json.seam`), Truffle DSL, Bazel with Linux amd64 RBE default.

**Spec:** `JSON_PLAN.md` (delivery steps 2, 3, 4, and the verification section). Steps 5 and 6 (JEP 540, Jackson, JSON-P, Gson) and the kotlinx adapter Komodo will write are follow-on plans; this plan only guarantees the interfaces they need (ordered members, random lookup, raw token text, snapshots).

## Global Constraints

- Only public simdjson 5.1.1 APIs. `get_string()` output is treated as parser scratch, never input-backed. (`JSON_PLAN.md` storage contract)
- Padded parsing requires `SIMDJSON_PADDING` readable bytes beyond the logical input; capacity is never inferred from address/length. Unpadded adopted input is copied, and the result reports copied bytes.
- Native parsing uses normal VM transitions (`native_leaf=false` in `json.seam`); no unbounded no-transition leaf.
- Malformed input or ABI mismatch never silently falls back to another backend.
- Strict JSON only: no comments, trailing commas, NaN or Infinity; duplicate keys rejected after escape decoding; member order preserved; missing and null distinct; all root kinds accepted; trailing input rejected.
- Numbers preserve exact token text. Typed numeric slots only under checked contracts.
- Owners are thread-confined. Closing a message invalidates every descendant view, including compiled cached reads. Sibling messages are independent. Closing the input or decoder does not invalidate a retained message.
- Every new native allocation path gets a failure-injection test and passes ASan/UBSan: `bazel test //json/native:test //json/native:api_test //json/native:typed_test --copt=-fsanitize=address,undefined --copt=-fno-sanitize-recover=all --linkopt=-fsanitize=address,undefined`.
- Java sources compile under NullAway/JSpecify (`@NullMarked` package) and the Truffle DSL processor (`data_java_library`).
- SVM targets are `manual`, Linux x86_64 only, run through RBE: `bazel test //json/tests:native_test`. JVM tests: `bazel test //json/tests:jvm`.
- Toolchain pins stay as-is: Elide GraalVM `25.5.5-elide.24`, elide-dev/toolchain 2026.11.1, Myna source `42d7ab484bef7a944b374a4683922c0a10c4ec1c` (ABI v3).
- Performance claims only from the comparison harness with retained raw samples (`benchmarks/results/json-*`); no claims from shared-host wall timings.
- Commit messages use the repository's existing prefix style (`feat:`, `perf:`, `test:`, `build:`, `docs:`) and end with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.

## Review Focus

1. **A long escaped string in document A read after document B is decoded through the same pooled parser** must still read its decoded text, not B's scratch. Pinned by Task 1's `second_decode_does_not_disturb_first_strings`.
2. **Adopted input whose capacity is exactly `length + SIMDJSON_PADDING - 1`** must be copied, not parsed in place, and report `copied_input_bytes == length`. Pinned by Task 3's `adopted_without_padding_is_copied`.
3. **A guest holding a `JsonValue` across `message.close()` inside compiled code** must get `IllegalStateException`, not a stale native read. Pinned by Task 8's compiled close gate.
4. **A number token like `-0`, `1E400`, or `12345678901234567890`** must not be silently reclassified: `-0` is F64 with raw text `-0`, `1E400` stays DECIMAL, and the 20-digit value is U64. Pinned by Task 5's classification tests.
5. **`JsonSink` export of an object with 10,000 members** must not recurse per member or call native code per node; the loop must be a single tape walk. Pinned by Task 10's wide-object export test and the compiler-trace cell in Task 17.

---

## Phase A — native lifetime completeness (JSON_PLAN step 2)

### Task 1: Message-owned text and pooled parsers

**Files:**
- Modify: `json/native/decoder.h` (class `document`)
- Create: `json/native/parser_pool.h`
- Modify: `json/native/decoder_test.cc`, `json/native/api_test.cc`
- Modify: `json/native/BUILD.bazel` (add `parser_pool.h` to `:decoder` hdrs)

**Interfaces:**
- Produces: `class parser_pool { static parser_lease acquire(); }` where `parser_lease` is a move-only RAII holder exposing `simdjson::ondemand::parser& get()`; returning to the pool keeps at most 8 parsers, deletes extras. Global, mutex-guarded, header-only.
- Produces: `document` no longer owns a parser member; its constructor takes the lease, and the lease is released before the constructor returns. `allocation_stats.parser_input_capacity` becomes the capacity observed during the lease.
- Produces: text policy in `document::text`: raw token with no `\` byte → `builder_.string_ref(input_region_, raw_offset + 1, raw_len - 2)`; otherwise `builder_.string(decoded)` (copied into message-owned bytes). Keys use the same policy. Strings of ≤16 bytes stay inline.

- [ ] **Step 1: Write the failing tests in `decoder_test.cc`**

```cpp
TEST(decoder, second_decode_does_not_disturb_first_strings) {
  // 40-byte escaped string forces region storage.
  auto a = make_document(R"({"s":"中中中中中中中中中中中中中中"})");
  auto first = string_at(*a, 2);          // helper already present in the test file
  auto b = make_document(R"({"s":"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})");
  EXPECT_EQ(string_at(*a, 2), first);
}
TEST(decoder, unescaped_long_string_references_input_region) {
  auto d = make_document(R"(["abcdefghijklmnopqrstuvwxyz0123456789"])");
  auto view = d->view();
  // node 1 is the string; region 1 is the retained input; offset 2 skips `["`.
  EXPECT_EQ(view.nodes[1].payload >> 32, 1u);
  EXPECT_EQ(view.nodes[1].payload & 0xffffffffu, 2u);
}
TEST(decoder, parser_pool_caps_retained_parsers) {
  std::vector<parser_lease> leases;
  for (int i = 0; i < 10; ++i) leases.push_back(parser_pool::acquire());
  leases.clear();
  EXPECT_LE(parser_pool::retained(), 8u);
}
```

- [ ] **Step 2: Run to verify failure**

Run: `bazel test //json/native:test --test_output=errors`
Expected: compile failure, `parser_pool` undeclared.

- [ ] **Step 3: Implement `parser_pool.h` and change `document`**

Keep `index_memory` accounting unchanged; the copied escaped bytes are charged through `value_builder::charge` automatically. Keep the existing `lexical_` capture.

- [ ] **Step 4: Run native tests and sanitizers**

Run: `bazel test //json/native:test //json/native:api_test` then the ASan/UBSan invocation from Global Constraints.
Expected: PASS for both.

- [ ] **Step 5: Commit**

```bash
git add json/native
git commit -m "perf: retain JSON text in message storage and pool simdjson parsers"
```

### Task 2: Lexical spans and number classification in the ABI (ABI 4)

**Files:**
- Modify: `json/native/json.seam` (`abi=4`; `JsonResult` grows to size 88: add `lexical type=ptr<void> offset=72`, `lexical_count type=u64 offset=80`)
- Modify: `json/native/decoder.h`, `json/native/api.cc`, `json/native/api_test.cc`, `json/native/decoder_test.cc`
- Modify: `json/tests/FfmAcceptanceTest.java`, `json/tests/SvmAcceptance.java` (offsets/struct size)

**Interfaces:**
- Produces: per-node lexical span array, `uint64_t` per node: high 32 bits offset into the retained input region, low 32 bits length; zero for containers, booleans, and null. Keys have spans (including quotes). Published at `JsonResult.lexical` with `lexical_count == node count`.
- Produces: number classification in `document::visit`, using `value.get_number()`:
  - integer token fitting `int64_t` → `builder_.integer`
  - integer token fitting only `uint64_t` → `builder_.unsigned_integer`
  - any other token for which `get_double()` yields a finite value → `builder_.floating`
  - otherwise (overflow such as `1E400`, or `get_number()` error) → `validated_number_ref` (DECIMAL) after the existing grammar check
  - `-0` → F64 with bits of `-0.0`.
- Produces: `document::lexical_spans()` returning `const std::pmr::vector<uint64_t>&` replacing `lexical_tokens()`.

- [ ] **Step 1: Write failing tests**

In `decoder_test.cc`:
```cpp
TEST(decoder, classifies_numbers) {
  auto d = make_document(R"([1, -0, 18446744073709551615, 1.5, 1E400, 12345678901234567890])");
  auto n = d->view().nodes;
  EXPECT_EQ(n[1].tag, MYNA_VALUE_I64);
  EXPECT_EQ(n[2].tag, MYNA_VALUE_F64); EXPECT_EQ(n[2].payload, 0x8000000000000000ull);
  EXPECT_EQ(n[3].tag, MYNA_VALUE_U64);
  EXPECT_EQ(n[4].tag, MYNA_VALUE_F64);
  EXPECT_EQ(n[5].tag & 0xff, MYNA_VALUE_DECIMAL);
  EXPECT_EQ(n[6].tag, MYNA_VALUE_U64);
  EXPECT_EQ(lexical_text(*d, 5), "1E400");
  EXPECT_EQ(lexical_text(*d, 2), "-0");
}
TEST(decoder, key_and_string_spans_include_quotes) {
  auto d = make_document(R"({"kA":"v"})");
  EXPECT_EQ(lexical_text(*d, 1), "\"k\\u0041\"");
  EXPECT_EQ(lexical_text(*d, 2), "\"v\"");
}
```
In `api_test.cc`: assert `JsonResult` size 88, `lexical_count` equals node count after `td_json_decode`, and `lexical` is null after release.

- [ ] **Step 2: Run to verify failure**

Run: `bazel test //json/native:test //json/native:api_test`
Expected: FAIL (tags are DECIMAL; no `lexical` field).

- [ ] **Step 3: Implement**

Regenerate bindings by building `//json/native:bindings`. `FfmAcceptanceTest` keeps its exact-text assertion: `1e999999999999999999` overflows `get_double()`, so it remains a DECIMAL node; only the struct-size and offset constants in that test change.

- [ ] **Step 4: Run all JSON tests**

Run: `bazel test //json/native:test //json/native:api_test //json/native:typed_test //json:bindings //json/tests:jvm` and the sanitizer invocation; then `bazel test //json/tests:native_test`.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add json
git commit -m "feat: publish JSON lexical spans and typed number nodes (ABI 4)"
```

### Task 3: Adopted padded input

**Files:**
- Modify: `json/native/json.seam` (add `import decode_adopted symbol=td_json_decode_adopted` with params `data ptr<u8> borrowed read`, `length u64`, `capacity u64`, then the same limit params and `result` as `decode`)
- Modify: `json/native/decoder.h` (`document` accepts either an owned `padded_string` or a `padded_string_view` over caller memory), `json/native/api.cc`, `json/native/api_test.cc`
- Modify: `json/native/README.md`

**Interfaces:**
- Produces: `td_json_decode_adopted(data, length, capacity, ...)`: if `capacity >= length + SIMDJSON_PADDING` parse in place and report `copied_input_bytes = 0`, `padded_input_bytes = capacity`; else copy exactly as `decode`. The library never frees adopted memory; the caller keeps it valid until `td_json_release` returns.
- Produces: `JsonResult.copied_input_bytes == 0` is the Java-visible proof of in-place adoption.

- [ ] **Step 1: Write failing tests in `api_test.cc`**

```cpp
TEST(api, adopted_padded_input_is_not_copied) { /* buffer of length+SIMDJSON_PADDING; expect copied_input_bytes==0, string node references region 1 */ }
TEST(api, adopted_without_padding_is_copied) { /* capacity = length + SIMDJSON_PADDING - 1; expect copied_input_bytes==length */ }
TEST(api, adopted_capacity_below_length_is_argument_error) { /* expect TD_JSON_ARGUMENT */ }
```

- [ ] **Step 2: Run to verify failure** — `bazel test //json/native:api_test`, expected compile failure on missing symbol.

- [ ] **Step 3: Implement**; regenerate bindings; add failure injection for the adopted path to the existing allocation-injection loop.

- [ ] **Step 4: Run `//json/native:...` plus sanitizers.** Expected: PASS.

- [ ] **Step 5: Commit** — `feat: adopt padded native JSON input without copying`

## Phase B — backends and owners (JSON_PLAN step 3)

### Task 4: `JsonOwner`, assumptions, and generic ownership tests on SVM

**Files:**
- Create: `json/src/main/java/dev/elide/truffledata/json/JsonOwner.java`
- Modify: `json/src/main/java/dev/elide/truffledata/json/JsonMessage.java`, `JsonValue.java`, `JsonSvm.java`
- Modify: `json/tests/SvmAcceptance.java`, `json/BUILD.bazel`

**Interfaces:**
- Produces: `abstract sealed class JsonOwner permits JsonMessage` (Task 6 adds `JsonSnapshot` to the permits list) with `final ValueTape tape`, `abstract long lexical(long node)` (packed span or 0), `abstract String lexicalText(long node)`, `final void check()`, `final Assumption assumption()`, nested `@GenerateUncached abstract static class CheckOpenNode extends Node { abstract void execute(JsonOwner owner); }` mirroring `protobuf-buffa/.../ViewOwner.java:30-50`, and `final JsonValue root()`.
- Produces: `JsonMessage extends JsonOwner` keeps the thread guard, `close()` invalidates the assumption before releasing native memory, and reads `lexical` from the `JsonResult` pointer.
- Produces: `JsonValue` constructed with `(JsonOwner, long node)`; `numberText()` reads the lexical span for any number kind; new `String rawText()` returns the span for strings/keys/numbers and throws `IllegalStateException` for containers.

- [ ] **Step 1: Add assertions to `SvmAcceptance.scoped()`**: `root.member("n").numberText()` still `1e9999999999999999`; `rawText()` of a string member equals its quoted spelling; after `message.close()`, `child.kind()` throws `IllegalStateException`; `message.assumption().isValid()` is false after close.
- [ ] **Step 2: Run `bazel test //json/tests:native_test`** — expected FAIL (no `rawText`, no assumption).
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run `bazel test //json/tests:native_test //json:json`** — expected PASS.
- [ ] **Step 5: Commit** — `feat: give JSON messages Truffle assumptions and raw token access`

### Task 5: JVM FFM backend

**Files:**
- Create: `json/src/main/java/dev/elide/truffledata/json/SegmentValueTape.java`, `JsonJvm.java`, `JsonDecoder.java`
- Modify: `json/src/main/java/dev/elide/truffledata/json/JsonSvm.java` (implement `JsonDecoder`), `json/BUILD.bazel`
- Create: `json/tests/JvmScopedTest.java`; Modify: `json/tests/BUILD.bazel` (add to `:jvm` srcs)

**Interfaces:**
- Produces: `public interface JsonDecoder extends AutoCloseable { JsonMessage decode(byte[] utf8); JsonMessage decode(byte[] utf8, JsonLimits limits); JsonMessage adopt(MemorySegment data, long length, Runnable release); void close(); }` with `record JsonLimits(long maxInputBytes, long maxIndexBytes, long maxNodes, long maxTextBytes, int maxDepth) { static final JsonLimits DEFAULT = new JsonLimits(64<<20, 64<<20, 1_000_000, 64<<20, 128); }` in `JsonDecoder.java`.
- Produces: `JsonJvm.open(Path library)` → `JsonDecoder` (confined library arena like `FfmBackend`; fingerprint checked against `JsonFFM.ABI_FINGERPRINT`). `decode` copies into a fresh confined arena with `SIMDJSON_PADDING` slack and calls `decode_adopted`; `adopt(MemorySegment data, long length, Runnable release)` treats `data.byteSize()` as capacity and `length` as the logical extent; the native side decides whether to copy (Task 3).
- Produces: `SegmentValueTape extends ValueTape` reading nodes/bytes/regions through `MemorySegment` reinterpreted from the descriptor; `JsonMessage` gets a second constructor path from a JVM result.
- Produces: `JsonSvm.open()` → `JsonDecoder` whose `adopt(MemorySegment, …)` throws `UnsupportedOperationException` (SVM adoption uses `JsonSvm.adopt(long address, long length, long capacity, Runnable release)` added here).

- [ ] **Step 1: Write `JvmScopedTest`** (JUnit, library path from `TEST_SRCDIR` as in `FfmAcceptanceTest`): retained children after input mutation; `numberText` exact; duplicate key rejected with `IllegalArgumentException`; cross-thread read rejected; `close()` idempotent; adopted segment release runnable runs exactly once after close; sibling message survives first close.
- [ ] **Step 2: Run `bazel test //json/tests:jvm`** — expected FAIL (classes missing).
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run `bazel test //json/tests:jvm //json/tests:native_test`** — expected PASS.
- [ ] **Step 5: Commit** — `feat: add JVM FFM scoped JSON backend and shared decoder interface`

### Task 6: Snapshots

**Files:**
- Create: `json/src/main/java/dev/elide/truffledata/json/JsonSnapshot.java`, `HeapValueTape.java`
- Modify: `JsonOwner.java`, `JsonMessage.java`, `json/BUILD.bazel`
- Modify: `json/tests/JvmScopedTest.java`, `json/tests/SvmAcceptance.java`

**Interfaces:**
- Produces: `JsonSnapshot extends JsonOwner` is immutable, thread-safe, never closes; `check()` is a no-op; `assumption()` returns `Assumption.ALWAYS_VALID`.
- Produces: `JsonMessage.snapshot()` copies nodes (`long[]`), bytes and every referenced region (`byte[]`, regions re-based into one buffer with a rebuilt region table), and lexical spans (`long[]`, re-based to the copied input region) into `HeapValueTape extends ValueTape`.
- Produces: `JsonValue.snapshot()` for a subtree (root re-based to node 0).

- [ ] **Step 1: Tests**: snapshot taken, message closed, decoder closed, then every scalar/key/raw text reads identically from the snapshot and from a background thread; snapshot of a nested subtree has `nodeCount == subtree extent`.
- [ ] **Step 2: Run** `//json/tests:jvm` — FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `//json/tests:jvm //json/tests:native_test` — PASS.
- [ ] **Step 5: Commit** — `feat: snapshot JSON messages into heap-owned immutable tapes`

### Task 7: Array random access

**Files:**
- Modify: `JsonValue.java`, `JsonOwner.java`
- Modify: `json/tests/JvmScopedTest.java`

**Interfaces:**
- Produces: `JsonOwner` lazily builds `long[] childIndex(long arrayNode)` (child node offsets) on first random access and caches in a `HashMap<Long, long[]>` guarded by `check()`; arrays with `length <= 8` are walked without caching. `JsonValue.element(long)` uses it. New `JsonValue.elements()` returns an `Iterable<JsonValue>` for sequential traversal without the index. New `JsonValue.memberAt(long i)` / `keyAt(long i)` provide ordered object access (needed by kotlinx and JEP 540 member order).

- [ ] **Step 1: Tests**: 10,000-element array, `element(9_999)` twice, second read makes no additional tape `next` walk (assert via a counting `ValueTape` subclass in the test, or by timing-free structural check that the cache map has one entry); `memberAt`/`keyAt` preserve source order with shuffled keys.
- [ ] **Step 2–4:** standard red/green on `//json/tests:jvm`.
- [ ] **Step 5: Commit** — `perf: index JSON arrays for random element access`

### Task 8: Compiled generic reads gate

**Files:**
- Modify: `json/tests/CompiledReads.java`, `json/tests/SvmAcceptance.java`

**Interfaces:**
- Consumes: `JsonOwner.CheckOpenNode`, `JsonValue` scalar reads.

- [ ] **Step 1: Add a second compiled root** that loops over two retained `JsonMessage`s reading `member("n").longValue()`-style primitives through a cached `CheckOpenNode`, with the same requirements as the scalar gate: observed compiled execution, no performance warnings, ≤10,000 graph nodes, ≤0.05 Java B/iteration (measure with the existing allocation counter). After `second.close()`, the compiled root must throw `IllegalStateException` on its next read and `first` must still read.
- [ ] **Step 2: Run `bazel test //json/tests:native_test`** — FAIL until `JsonValue` primitive reads avoid allocation (add `long longValue()` / `double doubleValue()` on `JsonValue` for I64/F64 nodes and a `JsonValue.member(Utf8Key)` path that returns a node index via new `long memberNode(Utf8Key)` so the loop holds longs, not `JsonValue`s).
- [ ] **Step 3: Implement** the allocation-free primitives.
- [ ] **Step 4: Run** — PASS.
- [ ] **Step 5: Commit** — `test: gate compiled generic JSON reads on allocation and invalidation`

## Phase C — guest interop and export

### Task 9: `InteropLibrary` on `JsonValue`

**Files:**
- Modify: `JsonValue.java` (`@ExportLibrary(InteropLibrary.class)`, implements `TruffleObject`), `json/BUILD.bazel` (already depends on truffle-api)
- Create: `json/tests/JsonInteropTest.java` (JUnit, uses `Context` with the existing `TestLanguage` or host interop via `Value.asValue`)

**Interfaces:**
- Produces, per kind: OBJECT → `hasMembers`, `getMembers` (ordered `DataArray` of key strings), `isMemberReadable`, `readMember`; ARRAY → `hasArrayElements`, `getArraySize`, `isArrayElementReadable`, `readArrayElement`; NULL → `isNull`; BOOL → `isBoolean`/`asBoolean`; STRING → `isString`/`asString` (via `tape.stringValue`), plus `asTruffleString` (UTF-8 bytes copied with `copyStringUtf8`); I64 → `isNumber`, `fitsInLong`, `asLong`, `fitsInDouble` per JLS exactness; U64 → `fitsInBigInteger`/`asBigInteger`, `fitsInLong` only when bit 63 clear; F64 → `asDouble`, `fitsInLong` when integral and exact; DECIMAL → `fitsInBigInteger` when the token is integral, else none of the primitive fits, `asDouble` via `Double.parseDouble(numberText())` only when finite.
- Produces: every export calls `owner.check()` first (through a cached `CheckOpenNode` where the DSL permits).

- [ ] **Step 1: Tests** through `org.graalvm.polyglot.Value`: `getMemberKeys()` order, `getArrayElement`, `asLong` on `9007199254740993`, `asBigInteger` on `18446744073709551615`, `asDouble` on `1.5`, `isNull`, `asString` on a 40-byte escaped string, member read after close throws `IllegalStateException` wrapped as `PolyglotException`.
- [ ] **Step 2–4:** red/green on `//json/tests:jvm`.
- [ ] **Step 5: Commit** — `feat: export scoped JSON values through InteropLibrary`

### Task 10: `JsonSink` single-pass export

**Files:**
- Create: `json/src/main/java/dev/elide/truffledata/json/JsonSink.java`, `JavaSink.java`, `JsonExportNode.java`
- Modify: `JsonOwner.java` (`export(JsonSink)`), `JsonValue.java` (`export(JsonSink)`)
- Create: `json/tests/JsonExportTest.java`

**Interfaces:**
- Produces: `public interface JsonSink { void beginObject(long members); void key(JsonValue key); void beginArray(long elements); void end(); void nullValue(); void booleanValue(boolean v); void longValue(long v); void doubleValue(double v); void unsignedValue(long bits); void decimalValue(JsonValue token); void stringValue(JsonValue text); }`. String and decimal payloads are passed as `JsonValue` so the sink chooses materialization (`String`, `TruffleString`, `BigDecimal`).
- Produces: `JsonExportNode extends Node` with `void execute(JsonOwner owner, long root, JsonSink sink)`: an iterative walk using an explicit depth stack (`long[] ends`, `int depth`, max 128) over `tape.next`, no recursion, one `check()` per call, sink calls through a `@Cached InteropLibrary`-free direct interface call. Also a `@TruffleBoundary` uncached path `JsonOwner.export(JsonSink)`.
- Produces: `JavaSink implements JsonSink` building `LinkedHashMap<String,Object>` / `ArrayList<Object>` / `String` / `Long` / `Double` / `BigInteger` (U64) / `BigDecimal` (DECIMAL) / `Boolean` / `null`, with `Object result()`.

- [ ] **Step 1: Tests**: export of a mixed document equals a hand-built expected Java tree; a 10,000-member object exports with depth stack size 1 (expose `JsonExportNode.maxDepthSeen()` for tests) and the sink receives exactly 10,000 `key` calls; a 128-deep array exports; 129-deep is rejected at decode (`maxDepth`). Differential test: for 200 generated documents (Gson `JsonParser` in `tests` only, `com.google.code.gson` is already in `maven_install.json`), `JavaSink` output equals Gson's tree converted with the same number policy.
- [ ] **Step 2–4:** red/green on `//json/tests:jvm`; add the export loop as a third compiled root in `CompiledReads` with the same gate (Task 8 style) and run `//json/tests:native_test`.
- [ ] **Step 5: Commit** — `feat: export JSON tapes into host objects through JsonSink in one pass`

### Task 11: Embedder documentation and sample

**Files:**
- Create: `docs/JSON.md`
- Modify: `json/native/README.md` (reconcile the chronological notes into current state), `README.md` (architecture list and artifact table mention JSON)

- [ ] **Step 1: Write `docs/JSON.md`**: ownership rules (copied from JSON_PLAN "Ownership and guest execution", now true), backend selection (`JsonSvm.open()` on SVM, `JsonJvm.open(Path)` on JVM, no Java fallback), limits, number policy table (Task 2), interop table (Task 9), the `JsonSink` contract with a worked GraalJS-style sink sketch showing `stringValue(JsonValue)` → `TruffleString.fromByteArrayUncached` and `key(JsonValue)` → property write, snapshot semantics, and what remains for JEP 540/Jackson/kotlinx (follow-on plan).
- [ ] **Step 2: Run `bazel test //tests:test`** (the target that runs `PolicyTest`) so inventory and documentation checks still pass.
- [ ] **Step 3: Commit** — `docs: describe the JSON backend, ownership, and embedder export contract`

## Phase D — known-schema completion (JSON_PLAN step 4)

### Task 12: Rename scalar API to the planned names

**Files:**
- Rename: `JsonScalarPlan.java → JsonPlan.java`, `JsonScalarRecord.java → JsonRecord.java`, `JsonScalarAccess.java → JsonAccess.java`
- Modify: all references in `json/tests/*.java`, `benchmarks/comparison/JsonComparison.java`, `JsonProfile.java`, `json/BUILD.bazel`, `benchmarks/comparison/BUILD.bazel`, `benchmarks/comparison/JSON.md`

- [ ] **Step 1: Rename with `git mv`, fix references, build**: `bazel build //json/... //benchmarks/comparison:json_comparison`.
- [ ] **Step 2: Run** `bazel test //json/tests:jvm //json/tests:native_test //benchmarks/comparison:json_native_smoke` — PASS, no behavior change.
- [ ] **Step 3: Commit** — `refactor: adopt JsonPlan, JsonRecord and JsonAccess names`

### Task 13: Nested records and repeated fields in native typed plans

**Files:**
- Modify: `json/native/typed.h`, `json/native/typed_test.cc`, `json/native/json.seam`, `json/native/api.cc`, `json/native/api_test.cc`

**Interfaces:**
- Produces (seam): `JsonScalarField` gains `child type=u32 offset=80` (record ordinal, or `UINT32_MAX`) and `flags` bit 2 = repeated; size 88. `kind` 5 = record. `td_json_plan_create(records, record_count, fields, field_count, max_name_bytes, result)`: `records` is an array of `JsonRecordPlan { fields_offset u32, field_count u32 }` (size 8); record 0 is the root. `JsonTypedResult` gains `records type=ptr<void> offset=96` and `record_count u64 offset=104` (size 112). A record entry is `JsonRecordEntry { present u64, nulls u64, slots u32, plan u32 }` (size 24). A slot for a RECORD field holds the child entry index in `bits`; a slot for a repeated field holds `(first << 32) | count` in `bits`, where `first` indexes the slot arena for scalar elements or the record table for record elements.
- Produces (C++): `typed_plan` (vector of `record_plan`, each the existing `scalar_plan` logic plus child links), `typed_document` replaces `scalar_document`: one slot arena `std::pmr::vector<scalar_slot>` and one record table, both reserved from `index_memory`; recursion bounded by plan depth ≤ 32 (reject deeper plans at creation); unknown fields, duplicates, missing required, type mismatch and trailing input rejected exactly as today; arrays of scalars validate each element against the element contract.

- [ ] **Step 1: Tests in `typed_test.cc`**: nested record read through the entry table; repeated int64 with range contract rejects an out-of-range element; repeated record of 3 entries; empty array yields count 0; null repeated field only when nullable; plan depth 33 rejected; allocation failure injected at every arena growth leaves no leak under ASan.
- [ ] **Step 2–4:** red/green on `//json/native:typed_test //json/native:api_test`, sanitizers, bindings regenerate.
- [ ] **Step 5: Commit** — `feat: decode nested and repeated JSON records into native slot arenas`

### Task 14: Java nested/repeated typed API

**Files:**
- Modify: `JsonPlan.java` (`compile(Schema root, Map<String, Schema> recordTypes)` and `compile(Schema)`), `JsonRecord.java`, `JsonAccess.java`
- Create: `json/src/main/java/dev/elide/truffledata/json/JsonArray.java`
- Modify: `json/tests/SvmAcceptance.java`, `json/tests/CompiledReads.java`

**Interfaces:**
- Consumes: core `Field.Kind.RECORD` with `Constraints.recordType()` naming a key in `recordTypes`; `Field.Kind.ARRAY` with `Constraints.element()` as the element contract (scalar or RECORD).
- Produces: `JsonRecord` now wraps `(JsonTypedOwner owner, int entry)` where `JsonTypedOwner` is the result owner (thread guard, assumption, close); the root record from `plan.decode(bytes)` is `JsonRecord` with `close()` delegating to its owner. `JsonRecord.record(int index)` → `JsonRecord`; `JsonRecord.array(int index)` → `JsonArray implements BorrowedArray` with `size()`, `element(int)`, `InteropLibrary` array exports, and `snapshot()` → `DataArray`. `DataLibrary.read` returns `JsonRecord`/`JsonArray` for those kinds. `JsonRecord.snapshot()` returns an owned immutable `AbstractRow` tree (`Batch`-compatible rows) via the existing core snapshot storage.
- Produces: `JsonAccess` accepts a path `int[] ordinals` (record hops then a scalar) so generated/cached accessors read nested primitives without intermediate `JsonRecord` allocation.

- [ ] **Step 1: Tests** in `SvmAcceptance`: nested primitive through `JsonAccess` path; repeated record iteration; `array(i).snapshot()` after owner close throws; `record.snapshot()` survives close. In `CompiledReads`: nested-path read root under the same gate.
- [ ] **Step 2–4:** red/green on `//json/tests:native_test`.
- [ ] **Step 5: Commit** — `feat: expose nested and repeated JSON records with scoped arrays`

### Task 15: JVM typed backend

**Files:**
- Modify: `JsonPlan.java`, `JsonRecord.java`, `JsonJvm.java`, `JsonDecoder.java`
- Modify: `json/tests/JvmScopedTest.java`

**Interfaces:**
- Produces: `JsonDecoder.compile(Schema, Map<String,Schema>)` → `JsonPlan`; on JVM the plan holds FFM handles and slot reads go through `MemorySegment` with the same `INDEX_BUFFER_TYPE` exact-cast trick as `FfmBackend.readIndexLong`. `JsonSvm.open().compile(...)` returns the existing SVM plan.

- [ ] **Step 1: Tests**: mirror the SVM typed assertions on JVM; a plan compiled from one decoder rejects use after that decoder closes.
- [ ] **Step 2–4:** red/green on `//json/tests:jvm`.
- [ ] **Step 5: Commit** — `feat: run typed JSON plans on the JVM through FFM`

## Phase E — evidence and project hygiene

### Task 16: Coverage and policy inventories

**Files:**
- Modify: `.github/scripts/coverage_contract.py` (`ACCESS_SOURCES` += every `json/src/main/java/.../*.java` except `package-info.java` and generated bindings; `SVM_ONLY_METHODS` for `NativeValueTape`-backed reads that JVM LCOV cannot run)
- Modify: `tests/src/test/java/dev/elide/truffledata/PolicyTest.java` module inventory to include `json`
- Modify: `.github/workflows/check.buffa.yml` test list to include `//json/tests:native_test` dependents added above (already present) and `//benchmarks/comparison:json_native_smoke`

- [ ] **Step 1: Run `bazel test //tests/...` and `python3 .github/scripts/coverage_contract.py`** per `docs/BAZEL.md` instructions; fix listed gaps by adding tests, not by shrinking the inventory.
- [ ] **Step 2: Commit** — `test: bring JSON access surfaces under the coverage and policy inventories`

### Task 17: Generic-path comparison cells

**Files:**
- Modify: `benchmarks/comparison/JsonComparison.java`, `json_smoke.py`, `json_measure.py`, `JSON.md`, `benchmarks/comparison/BUILD.bazel`
- Modify: `benchmarks/cases.json` (CodSpeed own-workload cases)

**Interfaces:**
- Produces engines `gson-tree` (Gson `JsonParser.parseReader` from UTF-8 bytes to `JsonElement`), `simdjson-generic-java` (`decode` + `JavaSink`), `simdjson-generic-interop` (decode + guest loop over `InteropLibrary` reads), and `simdjson-snapshot` (decode + `snapshot()` + release) for a 32-document mixed fixture (nested objects, arrays, short/long/escaped strings, integers, doubles), stages `ingest`, `read-aot`, `interpret`, `peak` as today. The smoke script's one-loop/no-call/no-allocation gate applies to the interop read loop and to the export loop (allocations permitted only for sink-created objects: assert via the Java allocation counter with a fixed per-document budget recorded in `JSON.md`).

- [ ] **Step 1: Extend the fixture and engines; run `bazel test //benchmarks/comparison:json_native_smoke`** until gates pass.
- [ ] **Step 2: Run `bazel test //benchmarks/comparison:json_measure`** once on RBE for a sanity table; record it under `benchmarks/results/json-generic-<sha>/` with the README caveats used by prior cycles (shared-host timings are advisory).
- [ ] **Step 3: Update `JSON.md`** with the new cells and their caveats; add `json-generic-*` entries to `cases.json` with checksums.
- [ ] **Step 4: Commit** — `perf: measure generic JSON export, interop reads and snapshots against Gson`

### Task 18: Release surface

**Files:**
- Modify: `tools/release/BUILD.bazel`, `tools/release/test_release.py`, `docs/PUBLISHING.md`, `MODULE.bazel`/`BUILD.bazel` publication lists as `protobuf-buffa` is listed
- Create: `tools/release/JsonConsumer.java`

- [ ] **Step 1: Add `truffledata-json` and `truffledata-json-bindings` artifacts** (jar, sources, POM) plus the shared library as an additional file, mirroring the Buffa bindings artifact; add a `JsonConsumer` isolated consumer that decodes on JVM via `JsonJvm.open` and on SVM via `JsonSvm.open`.
- [ ] **Step 2: Run `bazel test //tools/release:test //tools/release:buffa_native_test`** and the new `json_native_test` target.
- [ ] **Step 3: Update `docs/PUBLISHING.md`** artifact count and the BOM.
- [ ] **Step 4: Commit** — `build: publish the JSON backend artifacts and bindings`

### Task 19: Final verification

- [ ] **Step 1: Run** `bazel test //...` (JVM) and `bazel test //json/tests:native_test //benchmarks/comparison:json_native_smoke //tools/release:json_native_test` (SVM via RBE) and the sanitizer invocation.
- [ ] **Step 2: Reconcile `JSON_PLAN.md`**: mark steps 2–4 delivered with commit hashes; leave 5–6 as planned.
- [ ] **Step 3: Commit** — `docs: record JSON backend delivery status`

---

## Follow-on plans (not part of this plan)

1. **Facades** (`JSON_PLAN.md` steps 5–6): JEP 540 durable values on a pinned JDK 28 EA lane, Jackson 3 parser/generator adapters, then JSON-P; the kotlinx `Decoder` adapter lives in Komodo and consumes `JsonValue.memberAt/keyAt/rawText`, `JsonPlan`, and snapshots from this plan.
2. **CSV**: Rust `csv` crate producer writing Myna tapes through `myna-values`' `builder.rs` (Myna already ships a Rust producer), schema-directed typed row slots for the known-header case, same ownership and interop layers as JSON (`JsonOwner` generalizes to `TapeOwner` at that point).
3. **YAML and TOML**: same Rust producer shape with `toml` and a maintained YAML crate, sharing the CSV plan's generalized owner and sink.
