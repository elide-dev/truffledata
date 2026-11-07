# JSON Maximum-Performance Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Record rulings in `docs/superpowers/plans/2026-11-06-json-performance-ledger.md` (create it at Task 1).

**Goal:** Make the simdjson backend the fastest way to get JSON into guest objects, host objects and typed slots on SVM and JVM, with three explicit API surfaces (small messages, streams, large documents), while keeping every semantic rule of the completion plan: strict JSON, exact numbers, duplicate-key rejection after escape decoding, thread-confined owners, no silent fallback, padded parsing only.

**Architecture:** One native **session** per thread owns a simdjson On-Demand parser, a padded input buffer, and an index arena. The decoder walks On-Demand once with deferred unescaping, number parsing before grammar checks, and a per-document duplicate-key table whose hashes are published as a per-node side table next to the existing lexical spans. Small messages are decoded into the session arena and either exported straight into host objects (the `JSON.parse` path, borrowed, zero retained allocation) or detached as a retained `JsonMessage` by moving arena blocks. Streams run `iterate_many` over a bounded carry-over buffer in the same session. Large documents validate once, keep a frame index of the root's children, and materialize subtrees on demand. Guest reads resolve members through partially-evaluable position hints over key hashes, with the linear scan only on a miss behind a static boundary. Export passes a reusable text cursor instead of a view per string and, for Truffle guests, a per-document UTF-16/Latin-1 text arena written by simdutf in the same native call as the parse.

**Tech Stack:** C++17, simdjson 5.1.1 (On-Demand, `iterate_many`, `raw_json_token`, `escaped_key`, `get_number`), simdutf (`//bazel:unicode`), Myna values ABI v3 (`value_builder`, `ValueTape`, `SimdNativeValueTape`; Myna 0.9.0 FFM `CriticalLibraryBinding` and borrows for the JVM), GraalVM 25 SVM `@CFunction` and JVM FFM from `json.seam`, Truffle DSL, Bazel with Linux amd64 RBE.

**Spec:** `JSON_PLAN.md` (storage contract, ownership, verification), `docs/superpowers/plans/2026-11-06-json-completion.md` and its ledger (every current ruling stands unless a task below says otherwise), Myna `docs/values.md` sections "Direct producers, exact numbers, and C++ simdjson" and "SIMD validation and transcoding", Myna `docs/releases/0.9.0.md` and `docs/jvm-roadmap.md`.

**Baseline:** `861dff1` on `feat/json-completion` (Tasks 1–14, 17, 18 of the completion plan). Task 15 (JVM typed backend) lands in parallel; Task E1 below depends on it only for the JVM session's typed decode entry point.

## Global Constraints

Everything in the completion plan's Global Constraints, plus:

- Public simdjson 5.1.1 APIs only. No simdjson upgrade is required by this plan: every API it uses (`iterate_many`, `document_stream::truncated_bytes`, `escaped_key`, `key_raw_json_token`, `raw_json_token`, `raw_json`, `get_number`, `at_pointer`, `is_scalar`) exists in the pinned single header (verified by grep in `singleheader/simdjson.h`). Upgrading is an open question, not a dependency.
- `SIMDJSON_THREADS_ENABLED` stays undefined. The pinned build's `document_stream` is single-threaded; this plan does not add simdjson threading (see Diagnosis: stage 1 is about 1% of decode time).
- Native leaf / critical calls are allowed only for operations with an explicit, documented bound: session `reset` (O(1), no frees) and the JVM size-gated small decode (Task E2, input ≤ `JsonSession.CRITICAL_LIMIT` bytes, default 4 KiB, no allocation beyond the pre-grown arena, no locks, no exception can escape). Everything else keeps `native_leaf=false`.
- Strict JSON invariants are unchanged: deferred unescaping may skip simdjson's `get_string()` only for string bodies with no `\` and no byte below 0x20; UTF-8 validity of the whole input is established by simdjson stage 1 before any string is referenced. Numbers are typed only when `get_number()` succeeds; otherwise the RFC 8259 grammar check runs before the token is referenced as DECIMAL. Duplicate keys are rejected after escape decoding at every size.
- A document whose decode fails leaves the session arena reset and publishes nothing. A borrowed document is valid until the next session call; the Java side enforces this with the owner's open/pin state.
- Compiled read gates stay at ≤ 0.05 B/read; the interop smoke root keeps one loop and zero graph allocations; export budgets are per-task and recorded in `benchmarks/comparison/JSON.md`.
- Performance claims only from `json_smoke.py`, `json_measure.py` and `CompiledReads`, with raw samples retained under `benchmarks/results/json-*`. Numbers in this plan are **hypotheses to test**, labelled measured (with source) or estimated.
- Memory bounds: a session never retains more than `JsonLimits.maxIndexBytes` of arena plus the largest padded input it has seen (bounded by `maxInputBytes`); a stream never buffers more than `2 × maxDocumentBytes`; big-document mode retains the input, a frame index (16 B per root child) and at most `materializedSubtrees` subtree tapes.

## Review Focus

1. **A borrowed `JsonSink` export must never observe memory from the next decode.** `JsonSession.parse` exports with the session owner pinned; any `decode`/`parse`/`reset` while pinned throws, and the session owner's assumption is invalidated at the end of every borrow so a cached compiled read of a borrowed view cannot survive it. Pinned by Task B2's `borrow_invalidated_by_next_parse`.
2. **Deferred unescaping must not admit invalid strings.** A string body containing a raw byte < 0x20 or a `\` must still go through `get_string()`; a string with invalid UTF-8 must be rejected by stage 1 before the walk. Pinned by Task A1's `raw_control_byte_rejected` and `invalid_utf8_rejected_before_walk`.
3. **Number typing without the grammar pre-check must not accept what simdjson rejects or reclassify what it accepts.** `-0`, `1E400`, `18446744073709551615`, `12345678901234567890123`, `01`, `1.`, `.5`, `1e` keep today's classification or rejection. Pinned by Task A1's `number_classification_unchanged`.
4. **Position hints must never return a stale member.** A hint is a node offset relative to the object; it is validated by tag, length and bytes before use, and never by identity of the owner. Pinned by Task C1's `hint_miss_after_shape_change`.
5. **`iterate_many` carry-over must not duplicate or drop a document at a batch boundary**, and a truncated final document must surface as an error, not as silence. Pinned by Task F1's `stream_boundary_exactness`.
6. **Big-document frames must not publish unvalidated subtrees.** The frame pass validates everything (including duplicates and numbers) before any frame is readable. Pinned by Task G1's `frame_mode_rejects_late_error`.

---

## 1. Diagnosis

All timings here are from shared RBE Xeon @ 2.70 GHz workers (5 vCPU, simdjson active implementation `icelake`) unless stated; they are advisory and are reproduced in `benchmarks/results/json-perf-probe-861dff1/` with the probe sources. "Measured" cites the file; "estimated" is arithmetic over measured values or an engineering estimate.

### 1.1 Native decode, small document (210 B, 9 members, ~40 nodes)

Measured (`benchmarks/results/json-perf-probe-861dff1/native.txt`, best of 7 rounds of 200,000; this worker ran about 1.7× slower than the one behind the brief's table, so compare rows within this table, not against the brief's absolute values):

| Variant | ns | MB/s | What it adds |
|---|---:|---:|---|
| iterate only (stage 1) | 43 | 4,894 | structural index + UTF-8 validation |
| W0 bare On-Demand walk | 674 | 312 | every value consumed: `unescaped_key`, `get_string`, `get_double` |
| W1 W0 + `std::pmr::unordered_set` per object | 1,237 | 170 | today's duplicate-key structure |
| W2 lean walk, no duplicate check | 528 | 398 | `escaped_key`, raw tokens with a clean scan, `get_number` first |
| W3 lean walk + linear (≤16) / open-addressing duplicate table | 611 | 344 | the proposed duplicate policy |
| D0 current `document` (default resource) | 2,235 | 94 | 31 nodes, **33 index allocations**, peak 1,784 B |
| D1 current `document` over a monotonic arena | 2,077 | 101 | allocator only |
| D2 lean walk → `value_builder` in an arena, with spans | 1,659 | 127 | the walk of W3 plus Myna's builder and the span vector |
| D3 D2 without spans | 1,604 | 131 | spans cost ≈ 55 ns per document |
| D4 `dom::parser::parse` only | 258 | 815 | simdjson's own tape, numbers and strings |
| D5 D4 + walk → `value_builder` in an arena | 1,320 | 159 | the builder again |
| A0 C ABI `td_json_decode` + `td_json_release` | 2,314 | 91 | handle, publish, delete: ≈ 80 ns over D0 |

(The probe's `A1` line placed clock reads inside the loop and is not comparable; ignore it.)

Reading the table:

- **simdjson's ceiling for this shape** is the bare On-Demand walk (W0): every value consumed, strings unescaped into parser scratch, numbers converted. Stage 1 alone is ~2% of it. Nothing we build can be faster than W0 without skipping work simdjson does.
- **The duplicate-key set is the single largest avoidable cost** (W1 − W0). A `std::pmr::unordered_set` per object allocates buckets and nodes through the counted allocator for every object, including three-member `dims`. The lean variant (W3: linear compare up to 16 keys, open addressing beyond) costs within noise of W0.
- **Deferred unescaping and number-first typing (W2 vs W0)** recover the cost of copying every unescaped string into parser scratch and of the hand-written grammar check that precedes `get_number()`.
- **The Myna `value_builder` is the second largest cost.** D2 − W3 ≈ 1,050 ns for 31 nodes (~34 ns per node): the per-node `operation()` lambda with `try/catch`, `slot()` bookkeeping, four `std::pmr::vector`s growing geometrically (copying inside the arena), the span vector, and constructing/destroying the builder and arena per document. D5 shows the same ~1,000 ns on top of a 258 ns DOM parse, so the builder dominates regardless of how simdjson is driven. A presized raw tape writer (Task A2) replaces it on the production path.
- **Allocation count matters more for larger documents** (D0 vs D1): 33 index allocations per small document, 17,047 for the 228 KB array (every builder vector growth and every key set). An arena saves 7% on the small document but 33% on the big one.
- **DOM is not a shortcut.** `dom::parser::parse` (D4) is fast (815 MB/s) but still needs the tape walk (D5), and DOM cannot carry DECIMAL tokens (it rejects integers beyond `uint64` and overflowing doubles as parse errors), so it would need a fallback to On-Demand on `NUMBER_ERROR`, which the constraints forbid as a silent path. On-Demand stays.
- **The C ABI (A0) adds ≈ 80 ns over the C++ document**: `make_unique<handle>`, the result publish and `delete`. The session ABI (Task B1) replaces all three with an embedded result and an arena reset.
- **Estimated production target for this host:** W3 (611) + raw tape writes (31 nodes × ~4 ns ≈ 125) + spans and hashes (~70) + session bookkeeping (~50) ≈ **850–900 ns**, i.e. ≈ 0.4 × D0. Tasks A1 and A2 are gated on that ratio measured in the same probe run, not on absolute nanoseconds.

### 1.2 Native decode, medium (4.7 KB, 20 records) and big (228 KB, 1,000 records)

Measured (same file; medium best of 7 × 20,000, big best of 7 × 200):

| Variant | medium 4,330 B | big 228,485 B |
|---|---:|---:|
| iterate only (stage 1) | 454 ns (9.5 GB/s) | 35 µs (6.5 GB/s) |
| W0 bare walk | 12.0 µs (360 MB/s) | 766 µs (298 MB/s) |
| W1 + pmr set | 23.9 µs (181 MB/s) | 1,607 µs (142 MB/s) |
| W3 lean + duplicate table | 11.4 µs (380 MB/s) | 658 µs (347 MB/s) |
| D0 current document | 44.0 µs (98 MB/s), 371 allocations | 2,548 µs (90 MB/s), 17,047 allocations |
| D1 current document, arena | 34.3 µs (126 MB/s) | 1,714 µs (133 MB/s) |
| D2 lean → builder, arena, spans | 28.4 µs (152 MB/s) | 1,368 µs (167 MB/s) |
| D4 DOM parse only | 4.5 µs (960 MB/s) | 222 µs (1,030 MB/s) |
| D5 DOM + builder walk | 22.7 µs (191 MB/s) | 1,121 µs (204 MB/s) |
| A0 C ABI decode+release | 38.9 µs (111 MB/s) | 2,376 µs (96 MB/s) |

Throughput scales the same way: simdjson's ceiling for consuming every value with duplicate checking is ≈ 350 MB/s on this host (W3), today's decoder reaches 90 MB/s, and the lean walk through Myna's builder reaches 167 MB/s. The estimated production target (lean walk + raw tape writer) is ≈ 280–320 MB/s. Stage 1 runs at 6.5 GB/s and is 1.4% of today's decode, so parallelizing stage 1 (simdjson's threaded `document_stream`) cannot help and is not pursued.

### 1.3 Streaming and lazy access (native)

Measured (same file; 1,000 NDJSON lines of the fixture documents, 228,484 B; big-document queries over the 228 KB array):

| Shape | Variant | Result |
|---|---|---:|
| NDJSON | per-document `iterate` over line slices + lean walk | 625 ns/doc (366 MB/s) |
| NDJSON | `iterate_many(batch = 64 KiB)` + lean walk | 590 ns/doc (366 MB/s) |
| NDJSON | `iterate_many(batch = 1 MiB)` + lean walk | 604 ns/doc (390 MB/s) |
| NDJSON | `iterate_many(batch = 4 MiB)` + lean walk | 593 ns/doc (382 MB/s) |
| big | `at_pointer("/999/id")`, fresh iterate | 71.5 µs |
| big | `at_pointer("/0/id")`, fresh iterate | 27.3 µs |
| big | frame index of 1,000 root children by `raw_json()` skipping | 73.7 µs (3.1 GB/s) |
| big | materialize one 228-B element from its frame (lean walk → builder) | 1.7 µs |

- Per-document `iterate` over line slices and `iterate_many` over the same buffer cost the same within noise for 210-B lines: stage 1 per document is ~40 ns against ~600 ns of stage 2 and walk work. `iterate_many` is adopted for its truncation handling and single padded buffer, not for throughput; batch size does not matter above 64 KiB.
- A fresh `at_pointer("/999/id")` costs as much as building the whole frame index (both skip 999 elements after stage 1). A frame index therefore costs one pointer query and pays for every later access; materializing one element costs less than 2% of a full-tape decode of the document (2.5 ms). That is the basis of big-document mode: validate and frame once, materialize what is read. Note that `at_pointer("/0/id")` still costs 27 µs: stage 1 over the whole buffer is unavoidable for any access to an un-indexed document.

### 1.4 The Java side on SVM: where the unattributed ~1.6 µs goes

Measured (`benchmarks/results/json-perf-probe-861dff1/svm.txt`, AOT code, best of 7 rounds of 100,000; allocation from the thread allocation counter):

| Operation (SVM, AOT) | ns | Java B |
|---|---:|---:|
| `JsonSvm.decode` + `close`, 210 B document | 2,341 | 296 |
| `JsonSvm.decode` + `close`, `{}` | 300 | 296 |
| `JsonSvm.decode` + `close`, 228 KB array | 2,070,514 | 0 |
| raw generated ABI `decode` + `release`, 210 B, pin per call | 2,139 | 24 |
| raw ABI, 210 B, pin hoisted | 2,159 | 0 |
| raw ABI, `{}`, pin hoisted | 181 | 0 |
| `Json.fingerprint()` (native leaf) | 2.3 | 0 |
| `UnmanagedMemory.calloc(88)` + `free` | 43 | 0 |
| `PinnedObject.create` + `close` | 32 | 0 |

Attribution (estimated by subtraction over the measured rows; the native probe on the same worker class measured `A0 = 2,314 ns` for the same document):

| Component of `JsonSvm.decode`+`close` (210 B) | ns | Source |
|---|---:|---|
| native decode + release (parse, builder, 33 allocations, handle, pool mutex) | ≈ 2,150 | raw ABI row ≈ native `A0` |
| two VM transitions + status checks | < 30 | `{}` raw row (181) minus `{}` native work (estimated ≈ 150: `padded_string`, handle, builder, parser lease) |
| `calloc`/`free`, `PinnedObject` | ≈ 75 | measured rows |
| Java owners: `SvmValueTape` validation, `Lexical` and release lambdas, `JsonMessage`, `ConcurrentHashMap` | ≈ 120 (296 B) | `{}` Java row minus `{}` raw row |

**The "unattributed ~1.6 µs Java-side gap" in the brief does not exist.** It came from subtracting a native probe on one worker from a harness run on another, slower worker (this run's native `D0` is 1.7× the brief's). On one host, the Java layer costs ≈ 180 ns and ≈ 300 B per message over the raw ABI, and the raw ABI costs what the C++ `document` costs. The money is in the native decoder: the duplicate-key set (≈ 25% of `D0`), the Myna builder (≈ 45%), and 33 allocations per message. The session design (Tasks B1/B2) still removes the Java fixed cost and the 300 B, but the ordering of the plan follows the measured weights: Phase A first.

### 1.5 Reads

Measured in the same probe (AOT, `svm.txt`):

| Read | ns | Java B |
|---|---:|---:|
| `ValueTape.find` first key (`id`) | 94 | 0 |
| `ValueTape.find` ninth key (`parent`) | 518 | 0 |
| `member(id).longValue()` | 116 | 24 (the view) |
| `member(parent).kind()` | 537 | 0 |
| uncached interop `readMember(id)` + `asLong` | 273 | 272 (uncached key encoding) |
| uncached interop `readMember(parent)` + `isNull` | 746 | 0 |
| position-hint prototype, hit on `parent` (tag, length, packed-word compare) | 56 | 0 |
| `element(999)` on the indexed 1,000-element array | 32 | 0 |

`ValueTape.find` costs ≈ 53 ns per member stepped over: each step is `next()` (tag read, length read, inline check, bounds check), `tag()` again, `stringEquals` (length, then words or bytes). The compiled harness number (270 ns per read, `json-generic-c6e7abc`, ten reads over nine-member objects) matches a mean scan depth of ~5 plus the boundary. The hint prototype, still using Myna's bounds-checked public API from AOT code, is 9× cheaper than the ninth-key scan; partially evaluated over primitives (Task C1) it is a handful of loads. This is the basis for the ≤ 0.6 µs/doc hypothesis for ten reads.

### 1.6 Export and strings

Measured in the same probe (AOT, `svm.txt`, 210 B document unless stated):

| Operation | ns | Java B |
|---|---:|---:|
| export into a counting sink (walk + events only) | 622 | 408 |
| export materializing every key and string as `String` | 2,215 | 1,616 |
| export into `JavaSink` | 2,680 | 2,456 |
| `stringValue()` of the 60-byte region string (`note`) | 815 | 464 |
| `stringValue()` of the inline string (`name`) | 247 | 72 |
| `asTruffleString()` of `note` | 698 | 120 |
| `snapshot()` | 976 | 1,456 |
| export into a counting sink, 228 KB array | 603,225 | 0 (stack reused) |
| export into `JavaSink`, 228 KB array | 2,965,874 | 2,328,352 |

The counting export's 408 B are exactly the 17 `JsonValue` views (24 B each) handed to `key`/`stringValue` events: the walk itself allocates nothing. Strings are the next cost: 1,208 B and 1.6 µs of the `JavaSink` export are the `String`s for 12 keys and 5 strings, and the 60-byte escaped string alone costs 815 ns and 464 B through `ValueTape.stringValue` (a `byte[]` copy, a CharsetDecoder for non-ASCII, then the `String`). Note that the big-array export walk runs at 603 µs for 31,001 nodes (≈ 19 ns per node, 380 MB/s of input), already faster than the native decode that produced the tape, so once the decoder is fixed the export walk is not the bottleneck; strings are.

### 1.7 Per-use-case summary

| Use case | Dominant cost today | Ceiling (simdjson W0) | What this plan removes |
|---|---|---|---|
| Small (210 B) | native decode (2.2 µs on the probe host: duplicate set ≈ 25%, builder ≈ 45%, 33 allocations); Java layer ≈ 180 ns + 300 B | 674 ns (W0 on the same host) | lean walk, raw tape writer, arena; then session (no calloc/pin/handle/mutex/owner objects) |
| Streaming (NDJSON) | same per-document costs, plus caller-side buffer splitting | ~650 MB/s | `iterate_many` in a session with carry-over |
| Big (228 KB) | full tape for everything (187 MB/s, 5.7× input in memory) | ~650 MB/s to consume, ~17 GB/s to index | frame index + lazy subtrees; full tape only when asked |
| Reads | linear key scan behind a boundary per read (270 ns) | a few loads (~5 ns) | hashed side table + position hints, PE-able hit path |
| Export | views per string, `String` per key/string, boxed numbers | sink-defined | text cursor, UTF-16/Latin-1 text arena, key cache |

## 2. Architecture and API surfaces

### 2.1 Native layer

```
json/native/
  session.h      td_json_session: parser, padded input, arena, embedded JsonResult, key-hash table
  decoder.h      lean visit() writing nodes, spans and key hashes into the session arena
  frames.h       big-document validation pass producing a frame index
  stream.h       iterate_many over the session's carry-over buffer
  typed.h        unchanged contracts; decodes through a session parser (no pool)
  api.cc         td_json_session_* and the existing td_json_* (kept, implemented over a thread-local session)
  json.seam      abi=5
```

`json.seam` additions (sizes are illustrative; the generator fixes offsets):

```
struct JsonSessionResult size=120 align=8    # JsonResult (88) + key_hashes ptr + text_arena ptr + text_count + flags
import session_create  symbol=td_json_session_create  return=ptr<void>  native_leaf=false
  param limits ptr<void> borrowed read              # JsonLimitsStruct
import session_decode  symbol=td_json_session_decode  return=i32 native_leaf=false
  param session ptr<void> borrowed read_write
  param data ptr<u8> borrowed read                  # copied into the session's padded buffer
  param length u64
  param flags u32                                   # WANT_UTF16_TEXT, FRAME_MODE, UTF16_INPUT
  param text_out ptr<u8> nullable borrowed read_write   # pinned Java byte[] for the text arena
  param text_capacity u64
import session_decode_small symbol=td_json_session_decode_small return=i32 native_leaf=true@explicit_contract
  # identical to session_decode but rejects length > TD_JSON_CRITICAL_LIMIT with TD_JSON_ARGUMENT; JVM critical path only
import session_retain  symbol=td_json_session_retain  return=i32 native_leaf=false
  param session ptr<void> borrowed read_write
  param result ptr<void> borrowed read_write        # JsonResult: receives moved arena blocks, owns them
import session_reset   symbol=td_json_session_reset   return=void native_leaf=true@explicit_contract
import session_release symbol=td_json_session_release return=void native_leaf=false
import stream_feed / stream_next / stream_truncated  (Task F1)
import frames_decode / frame_materialize           (Task G1)
```

`td_json_session_decode` leaves the session's `JsonSessionResult` pointing at arena memory that stays valid until the next `session_*` call. `td_json_session_retain` moves the arena blocks that hold the current document (nodes, bytes, regions, spans, hashes, copied input) into a fresh `JsonResult` owner; the session takes new blocks from its free list (≤ 4 retained). Retain is O(blocks), not O(nodes).

### 2.2 Java layer

```java
/** Thread-confined decode context: one native parser, buffers and arena. Replaces per-call pooling. */
public final class JsonSession implements AutoCloseable {
  public static JsonSession open();                              // SVM
  public static JsonSession open(JsonLimits limits);
  // JVM: JsonJvm.openSession(Path library, JsonLimits) (Task E1)

  /** Borrow: decode into the session arena, export, release. Nothing is retained. */
  public void parse(byte[] utf8, JsonSink sink);
  public void parse(byte[] utf8, int offset, int length, JsonSink sink);
  public void parse(TruffleString text, JsonSink sink);           // UTF-8-compatible bytes used directly, else UTF-16 transcoded natively (Task D3)
  public void parse(MemorySegment padded, long length, JsonSink sink);   // JVM; adopted in place when padded

  /** Borrow with scoped reads: valid until the lambda returns; views must not escape. */
  public <R> R with(byte[] utf8, Function<JsonValue, R> reader);

  /** Retain: same decode, then arena blocks move into a message that outlives the session. */
  public JsonMessage decode(byte[] utf8);
  public JsonMessage decode(byte[] utf8, int offset, int length);

  /** Large documents: validate once, frame the root, materialize on read (Task G2). */
  public JsonDocument open(byte[] utf8, JsonDocumentOptions options);
  public JsonDocument adopt(MemorySegment padded, long length, Runnable release, JsonDocumentOptions options);

  /** Streams of concatenated/NDJSON documents over this session (Task F2). */
  public JsonStream stream(JsonStreamOptions options);

  public void close();
}

/** Existing entry points remain and delegate to a per-thread session. */
public final class JsonSvm { public static JsonMessage decode(byte[] utf8); ... }   // unchanged signatures
```

```java
/** Streaming: bounded carry-over buffer, one document per next(). */
public final class JsonStream implements AutoCloseable {
  /** Appends input; returns bytes accepted (< length means apply backpressure). */
  public int feed(byte[] chunk, int offset, int length);
  public int feed(MemorySegment chunk);                 // JVM borrow (Myna FFM `borrow` overload)
  public void finish();                                 // no more input; a trailing partial document is an error on next()
  /** Decodes the next complete document into the session arena and exports it. False when no complete document is buffered. */
  public boolean next(JsonSink sink);
  /** Same, exposing a borrowed root valid until the next call. */
  public @Nullable JsonValue next();
  /** Detaches the current borrowed document. */
  public JsonMessage retain();
  public long documents(); public long truncatedBytes();
  public void close();
}
public record JsonStreamOptions(int maxDocumentBytes, int batchBytes, boolean allowComma) {
  public static final JsonStreamOptions DEFAULT = new JsonStreamOptions(1 << 20, 1 << 20, false);
}
```

```java
/** Large documents: validated once, root children framed, subtrees materialized on demand. */
public final class JsonDocument extends JsonOwner implements AutoCloseable {
  public JsonValue root();                               // frame-backed container view
  public long size();                                    // root child count
  public JsonValue element(long index);                  // materializes (and caches) the subtree tape
  public JsonValue member(String key);                   // root object: key-hash lookup in the frame table
  public JsonValue at(String jsonPointer);               // "/999/id": frame hop, then tape walk
  public void export(JsonSink sink);                     // streams frames through the sink without a full tape
  public void exportElement(long index, JsonSink sink);
  public JsonMessage materialize();                      // explicit full tape when wanted
  public long materializedSubtrees(); public void release(long index);
}
public record JsonDocumentOptions(long frameThresholdBytes, int materializedSubtrees, boolean fullTape) {
  public static final JsonDocumentOptions DEFAULT = new JsonDocumentOptions(1 << 20, 64, false);
}
```

```java
/** Export sink v2: reusable text cursor instead of a view per string. */
public interface JsonSink {
  void beginObject(long members); void beginArray(long elements); void end();
  void key(JsonText key);                 // cursor valid only during the call
  void stringValue(JsonText text);
  void decimalValue(JsonText token);
  void nullValue(); void booleanValue(boolean v); void longValue(long v); void doubleValue(double v); void unsignedValue(long bits);
}
/** Flyweight over (owner, node): one instance per export, rebound per event. */
public final class JsonText {
  public int utf8Length(); public boolean isAscii();                 // ascii bit from the native side table
  public int hash();                                                 // native key hash (keys only; 0 otherwise)
  public String toJavaString();                                      // SimdNativeValueTape path for ≥ 256 B
  public TruffleString toTruffleString(TruffleString.Encoding target);   // from the text arena when present, else copy
  public int copyUtf8(byte[] dst, int offset); public boolean equals(byte[] utf8);
  public JsonValue view();                                           // explicit escape for sinks that need a view
}
```

Representation per use case:

| Use case | Representation | Why |
|---|---|---|
| Small, `JSON.parse`-style | session arena tape, **borrowed**, exported in one walk | fixed cost is the whole cost; nothing survives the call |
| Small, retained (RPC payload read later) | session decode, arena blocks **moved** into `JsonMessage` | keeps today's ownership model with zero copies and no per-message mallocs |
| Streaming | session + `iterate_many`; each document borrowed, `retain()` on demand | bounded memory, one transition per document, no per-document buffers |
| Big | retained input + frame index + bounded cache of subtree tapes | memory ∝ input, not ∝ nodes; reads touch only what is read |
| Known schema | typed slots (unchanged), decoded through the session parser | already optimal shape; only the parser/arena plumbing changes |

### 2.3 The GraalJS `JSON.parse` path end to end

1. Guest string arrives as a `TruffleString`. If its code range is ASCII (or its bytes are UTF-8-compatible), take the internal byte array without copying; otherwise pass the UTF-16 bytes and set `UTF16_INPUT` so the native side transcodes with simdutf into the padded buffer (Task D3).
2. Pin the array (SVM) or pass the heap segment through the critical path when ≤ 4 KiB (JVM, Task E2); otherwise copy into the session buffer.
3. `td_json_session_decode(session, data, len, WANT_UTF16_TEXT, textArray, cap)`: parse, build the tape, and write every key and string ≥ 1 byte into the pinned text array as Latin-1 (ASCII strings) or UTF-16 (others) with a per-node (offset, length, kind) table in the side arrays. Capacity is presized to `2 × len + 16` bytes, which bounds the output for any input, so there is never a retry.
4. The `JsSink` walks the tape once: `key(JsonText)` resolves a property key through a per-session key cache keyed by `(hash, bytes)` (no allocation for repeated keys); `stringValue(JsonText)` builds a `TruffleString` over the shared text array (`fromByteArrayUncached(array, offset, length, encoding, copy=false)`), so each string costs one object and no copy; numbers follow `JSON.parse` coercion.
5. Return; the session arena is reset. Retained Java allocation per document = the guest objects + one `TruffleString` per string + the text array (shared by all strings of that document).

## 3. Reads

**Side tables.** The decoder already publishes one `uint64` lexical span per node. Task A1 adds one `uint32` per node: for key nodes the 32-bit hash of the decoded key bytes (the same hash the duplicate table uses; FNV-1a over bytes); bit 31 is an "ASCII only" flag for every string and key node; 0 otherwise. This is a JSON side table, not a Myna layout change, so the tape stays ABI v3 and every existing reader works.

**Hit path (partially evaluable, no boundary, no loop).** `ReadMember`/`IsMemberReadable` cached specializations hold `@Cached("keyOf(name)") JsonKey key` (bytes, packed low/high words, hash) and `@Cached("new int[1]") int[] hint`. `JsonValue.memberNode` becomes a static over primitives:

```java
static long memberNode(JsonOwner owner, long object, JsonKey key, int[] hint) {
  long end = owner.next(object);                       // one long load
  long k = object + hint[0];
  if (hint[0] > 0 && k < end && owner.keyMatches(k, key)) return k + 1;   // tag, length, two words (≤16 B keys)
  long found = scan(owner, object, end, key);          // @TruffleBoundary static: hash scan over key nodes
  if (found >= 0) hint[0] = (int) (found - 1 - object);
  return found;
}
```

`keyMatches` for keys ≤ 16 bytes compares the packed words inline (Myna's inline text layout: payload and next hold the bytes); for longer keys it compares the side-table hash inline and confirms bytes behind the same static boundary. The miss path scans key nodes comparing `hash[k]` first (one int load per member), then bytes. Objects with more than 32 members get a per-object open-addressing directory built lazily by the owner (like `arrayIndex`), keyed by hash.

**Why hints and not shapes.** Documents of one schema arrive with members in a few orders (the fixture reverses odd documents). A hint per site costs one compare on a hit and self-corrects on a miss; the fixture's two orders settle into two hints at most (the DSL cache has three entries per name). Shape identification would need a per-object shape hash computed natively (possible: hash of key hashes in order) and a shape→layout cache; it is a follow-on if hint misses are measured above 10% in a real workload.

**Deopt-target rule.** Every inline helper on the hit path reads through `TapeSource.nodeInt/nodeLong` (the completion plan's Task 9 ruling) and no helper is a trivially inlined foreign one-liner. `owner.next(node)` is a JSON-side final method that reads the node word directly.

**Cost model.** On SVM a tape read is one `Pointer.readLong` (a load). The hit path is ~6 loads, 4 compares and the owner check; the probe's AOT prototype (56 ns through Myna's bounds-checked public API, against 518 ns for the ninth-key scan) bounds it from above. On the JVM, `MemorySegment.get` on a global (`reinterpret`) segment carries bounds checks but no liveness check; Graal JIT hoists them within a compilation unit. No `Unsafe` is used.

**Snapshots** reuse the same hint path over `HeapValueTape` (long-word nodes, no VarHandles), and get the side tables copied alongside spans.

**Element reads** keep the Task 17 follow-up structure (static element boundary, one-entry most-recent index); Task C1 adds a hint for `readArrayElement` sequential patterns (`index == lastIndex + 1` → `next(lastNode)` inline).

## 4. simdjson integration to maximum effect

| Topic | Decision | Basis |
|---|---|---|
| On-Demand vs DOM | On-Demand everywhere. DOM parses in 258 ns but the tape walk still costs ~1,000 ns (D5), and DOM cannot represent DECIMAL tokens. | §1.1 |
| Parser reuse | One parser per session (thread-confined), never pooled through a mutex; capacity grows to the largest input seen and is bounded by `maxInputBytes`. `td_json_decode` keeps working over a thread-local session. | parser pool mutex and `make_unique` are per-message fixed cost (§1.4) |
| Double number parsing | `get_number()` first; its success is simdjson's full grammar validation, so the hand grammar check runs only when `get_number()` fails (overflow, big integer) before `validated_number_ref`. `-0` detection keeps reading `raw_json_token()[0]`. | Myna `validated_number_ref` contract requires a validated token; simdjson validates on success |
| Raw tokens | `raw_json_token()` (values) and `key_raw_json_token()` (keys) keep feeding lexical spans. `escaped_key()` gives the key body without unescaping; `unescaped_key()` runs only when the body contains `\`. | W2 vs W0 |
| String unescaping | Bodies with no `\` and no byte < 0x20 are referenced in the input region (`string_ref`) without `get_string()`; stage 1 already validated UTF-8 for the buffer. Escaped bodies go through `get_string()` (into parser scratch) and are copied into the arena once. Inline (≤ 16 B) stays inline. | `get_string()` output is scratch (plan constraint); copying twice is what Task 1 removed |
| `is_scalar` / `count_fields` / `count_elements` | `is_scalar` only at the root (as today). Counting is never used on the generic path (it re-scans); typed arrays keep `count_elements()` because contiguous reservation needs the count. | simdjson's performance notes |
| Duplicate keys | Linear compare for ≤ 16 keys over `(hash, ptr, len)` kept on a stack array; open-addressing table in the arena beyond; the hash is kept per key node for Java lookups. Keys are compared after escape decoding (unescaped only when escaped). | W1 − W0 is the largest avoidable cost |
| Minify / validate-only | `JsonSession.validate(byte[])`: lean walk with duplicate check, no tape (W3 rate). No minify API (not requested). | §1.1 |
| Builder ABI cost | Nodes stay 24 B (Myna ABI v3). Spans stay eager (8 B/node, bump-allocated: ≈ 55 ns per 31-node document, D2 − D3). Key hashes add 4 B/node. Lazy spans would need a second structural pass; rejected. The builder *implementation* is replaced by a presized writer (Task A2) because it costs ~34 ns/node today. | §1.1 |
| One pass vs simdjson's tape | One On-Demand pass into our tape. simdjson's DOM tape is a private layout. | constraint: public APIs only |
| Threading | None in simdjson. Parallelism is sessions on threads; `JsonStream.split(byte[])` helper finds NDJSON boundaries so an embedder can shard. | stage 1 ≈ 1% of decode |
| Newer features | Nothing in 5.1.1 beyond the above is needed. simdjson ≥ 4 `builder` (serialization) is relevant only to a future generator facade. An upgrade is not justified by this plan. | header grep |
| `iterate_many` | Streaming only, batch = `min(bufferBytes, batchBytes)`, carry-over of `truncated_bytes()`; the stream format is NDJSON/whitespace-separated (comma-separated optional via `stream_format`). | §1.3 |
| `at_pointer` / `at_path` | Only inside `JsonDocument.at` for the *first* hop when no frame index exists yet (one-shot queries on an un-framed document); framed documents hop through frames, then walk the subtree tape. | `at_pointer` is linear in the document (§1.3) |

## 5. Myna integrations and upstream requests

Myna's value layer in the parallel 0.9.0 upgrade is byte-identical to the current pin, so everything below is available now.

**`value_builder` contract.** `validated_number`/`validated_number_ref` require a lexically validated token and the string methods require valid UTF-8. With Task A1: typed numbers come from `get_number()` success (validated by simdjson), DECIMAL references run `number()` first (kept), strings are validated by simdjson stage 1 (input-referenced) or by `get_string()` (escaped). The redundant unconditional `number()` scan is removed; nothing else validates twice. The builder's per-node `operation()` wrapper and geometric growth remain the builder's cost; Task A2 introduces a JSON-side `tape_writer` that writes `myna_value_node` records into a presized arena span and exposes the same `myna_value_view`, until Myna provides reservation (request M2).

**`SimdNativeValueTape`.** Adopt it as the SVM tape class (`SvmValueTape extends SimdNativeValueTape`) so `stringValue()`/`asString` of strings ≥ 256 B transcode with simdutf straight into a pinned `char[]` (Task D1). This needs the Rust `myna-values` static library linked into SVM images for `myna_utf8_to_utf16_v2`; Task D1 wires it through crate_universe (`myna-values` as a dependency of a thin bridge crate, `rust_static_library`, added to `cc_deps` of the SVM targets). Because JSON strings are mostly short, the per-string path is not where `JSON.parse` time goes; the batch text arena (Task D2) uses the same simdutf conversion in C++ for *all* strings of a document in the decode call. That batch form is proposed upstream as request M4 so Myna owns both.

**Rust `myna_values::text`.** `Utf8::from_bytes` and `to_utf16` are the Rust equivalents for the follow-on Rust producers (CSV/YAML); the C++ JSON path calls simdutf directly through `//bazel:unicode` (already linked for typed length contracts). No JSON task depends on them.

**Myna 0.9.0 FFM.** `CriticalLibraryBinding` is used for exactly two imports on the JVM: `session_reset` and `session_decode_small` (Task E2), both satisfying the native-leaf contract by construction. `LibraryBinding` `borrow` overloads back `JsonStream.feed(MemorySegment)` and `JsonSession.parse(MemorySegment, …)` (no retention, length derived). Upcalls are **not** used: a native-driven stream with one upcall per document crosses as often as a Java-driven `next()` loop and complicates lifetimes; Java stays the driver.

**Upstream requests to Myna (frame these as issues; nothing here blocks the plan):**

- **M1 — ABI v4 container directories.** Allow a container node's `payload` (currently required to be 0) to reference an optional per-object directory `(hash32, child_offset32)[]` emitted by the builder for objects above a threshold, and give `ValueTape.find` a hash-first path. Until then the JSON side table carries hashes (Task A1) and owners build directories lazily (Task C1).
- **M2 — `value_builder::reserve(nodes, bytes, regions)`** or a public raw node writer, so a producer that knows the input size can presize in an arena without geometric growth.
- **M3 — PE-friendly readers.** Public `keyMatches(node, Utf8Key)`-style helpers that are not trivially inlined one-liners, `Utf8Key` accessors for its packed words and a precomputed hash, and a rebindable reader (`rebind(descriptor)`) so a session can reuse one reader object per thread.
- **M4 — batch text export.** `myna_tape_text_to_utf16_v1(descriptor, hashes, out, cap, table)` in `myna-values`, the batch form of `myna_utf8_to_utf16_v2`, writing Latin-1 for ASCII runs.
- **M5 — `SimdNativeValueTape` threshold** tuned with the JSON fixture; the 256 B crossover is documented as conservative.

## 6. Allocation

| Path | Today | Plan |
|---|---|---|
| Native per small message | 33 index allocations + padded input malloc + handle + parser lease (mutex) + result calloc | 0 mallocs in steady state: session arena blocks reused; input copied into the session's padded buffer; result embedded |
| Native per retained message | same | arena blocks moved (≤ 4 blocks), no copy |
| Java per `JsonSvm.decode`+close | 296 B (tape reader, lexical and release lambdas, message, `ConcurrentHashMap`) | ≤ 160 B (tape reader + message); `ConcurrentHashMap` created lazily on first indexed array |
| Java per borrowed `parse` | n/a | 0 B beyond what the sink builds (session-owned borrow owner and text cursor reused) |
| Export views | 16–19 views/doc ≈ 400–450 B | 0 (one `JsonText` per export, rebound) |
| Keys in `JavaSink` | `String` per key | per-sink key cache by hash+bytes (opt-in `JavaSink(KeyCache)`) |
| Strings for Truffle guests | `byte[]` + `TruffleString` + encoding switch | one `TruffleString` over the shared per-document text array |
| Lookups | 0 (already) | 0; hint arrays live in the DSL cache |
| Snapshot | copies spans + nodes + regions | adds the 4 B/node hash table; otherwise unchanged |

Pooling of result/control structs: not needed once results are embedded in sessions; the only pooled object is the session itself (one per thread, created by the embedder or lazily by `JsonSvm.decode`).

---

## 7. Implementation tasks

Task format follows the completion plan. Every task ends with the standard verification set: `bazel test //json/native:test //json/native:api_test //json/native:typed_test //json/native:unicode_test`, the ASan/UBSan invocation from the completion plan, `bazel test //json/tests:jvm`, `bazel test //json/tests:native_test`, and `bazel test //benchmarks/comparison:json_native_smoke`; tasks that add cells also run `json_measure` once and retain results under `benchmarks/results/json-<topic>-<sha>/` with the standard README caveats.

### Phase A — native decoder

#### Task A1: Lean visit: number-first typing, deferred unescaping, hashed duplicate table, key-hash side table

**Files:**
- Modify: `json/native/decoder.h` (`document::visit`, `document::text`, new `key_table`), `json/native/decoder_test.cc`, `json/native/api_test.cc`
- Modify: `json/native/json.seam` (`JsonResult` += `key_hashes ptr<void>`; `abi=5`), `json/native/api.cc` (publish), `json/native/README.md`
- Modify: `json/src/main/java/dev/elide/truffledata/json/JsonSvm.java`, `JsonJvm.java`, `JsonMessage.java` (read `key_hashes` next to `lexical`; expose `int keyHash(long node)` and `boolean ascii(long node)` on `JsonOwner`), `JsonSnapshot.java` (copy the table)

**Interfaces:**
- Produces: `uint32_t` per node: bits 0–30 = FNV-1a hash of the decoded key bytes for key nodes (0 for non-keys), bit 31 = ASCII-only for string and key nodes. Published as `JsonResult.key_hashes` with `lexical_count` entries.
- Produces: `visit()` order for numbers: `raw_json_token()` → `get_number()`; on success classify as today (`-0` → F64 −0.0); on error or `big_integer` → `number(raw)` grammar check → `validated_number_ref`.
- Produces: strings: `raw_json_token()`; body = token minus quotes; if `clean(body)` (no `\`, no byte < 0x20) → `string_ref` into the input region (or inline ≤ 16 B) without `get_string()`; else `get_string()` then copy. Keys: `escaped_key()`; `unescaped_key()` only if the body contains `\`.
- Produces: `key_table`: stack array of 16 `(hash, string_view)` entries with linear compare; beyond 16, an open-addressing table allocated from `index_memory` (counted). The hash is computed once per key and stored in the side table.

- [ ] **Step 1: Failing tests in `decoder_test.cc`**

```cpp
TEST(decoder, raw_control_byte_rejected) { EXPECT_THROW(make_document("[\"a\x01b\"]"), std::invalid_argument); }
TEST(decoder, invalid_utf8_rejected_before_walk) { EXPECT_THROW(make_document("[\"\xff\"]"), simdjson::simdjson_error); }
TEST(decoder, number_classification_unchanged) {
  auto d = make_document(R"([1, -0, 18446744073709551615, 1.5, 1E400, 12345678901234567890123])");
  // same tags as Task 2's classifies_numbers; DECIMAL for the last two
  for (auto bad : {"[01]", "[1.]", "[.5]", "[1e]", "[-]"}) EXPECT_THROW(make_document(bad), simdjson::simdjson_error);
}
TEST(decoder, unescaped_key_is_referenced_not_copied) { /* key of 20 ASCII bytes: node payload region == 1 */ }
TEST(decoder, escaped_duplicate_still_rejected_in_large_object) { /* 40 keys, the 39th is "kA", the 2nd is "kA" → throws */ }
TEST(decoder, key_hashes_published) { /* hash(node of key "id") == fnv("id") & 0x7fffffff; ascii bit set on "note" even doc, clear on odd */ }
```

- [ ] **Step 2: Run** `bazel test //json/native:test` — FAIL on the new assertions (hash table missing).
- [ ] **Step 3: Implement**, regenerate bindings (`bazel build //json/native:bindings`), update Java readers for the new field and `ABI_FINGERPRINT`.
- [ ] **Step 4: Measure** with the retained probe (copy `benchmarks/results/json-perf-probe-861dff1/perf_probe_test.cc` back as a temporary `cc_test`, run on RBE, compare the `D0 current document` line against `W0`/`W3` in the same run). Acceptance hypothesis: small-document `D0` ≤ 0.75 × its value at `861dff1` measured in the same run (the duplicate set alone is 25% of D0), `index_allocations` lower (no per-object sets); big-document `D0` ≥ 120 MB/s. Record the output under `benchmarks/results/json-lean-walk-<sha>/`.
- [ ] **Step 5: Full verification set and sanitizers.** Expected: PASS; `JsonResult` size assertion updated in `api_test.cc`.
- [ ] **Step 6: Commit** — `perf: type JSON numbers before grammar checks, defer unescaping and hash keys`

#### Task A2: Arena-backed index with presized tape writer

**Files:**
- Create: `json/native/arena.h` (`block_arena : std::pmr::memory_resource` over a reusable block list with upstream fallback), `json/native/tape_writer.h` (presized node/byte/region writer producing `myna_value_view`)
- Modify: `json/native/decoder.h` (use `tape_writer` when a capacity hint is given; `value_builder` path kept for tests until parity is proven), `json/native/index_memory.h` (charge arena blocks, not individual allocations), `decoder_test.cc`, `api_test.cc`

**Interfaces:**
- Produces: `block_arena(size_t first_block, index_memory& accounting)`: `allocate` bumps; on exhaustion takes the next block (geometric, capped by `storage_limits.index_bytes`); `release()` keeps ≤ 4 blocks; `detach()` returns the block list (for retain).
- Produces: `tape_writer`: `reserve(nodes, bytes)` from a size hint `nodes ≈ input_bytes / 4 + 8`, `bytes ≈ input_bytes / 8 + 64`; grows by moving to a larger arena span (rare). Same semantic checks as `value_builder` (depth, node and text limits, object key/value pairing) but without a per-node `try/catch`.
- Produces: the lexical span and key-hash arrays live in the same arena.

- [ ] **Step 1: Tests**: a document built with `tape_writer` is byte-identical (nodes, bytes, regions) to one built with `value_builder` for the fixture corpus (200 generated documents); `index_allocations ≤ 3` for the small fixture; limit violations still throw `std::length_error` with no retained bytes under ASan; allocation-failure injection at every block acquisition.
- [ ] **Step 2–3: red/green** on `//json/native:test //json/native:api_test`.
- [ ] **Step 4: Measure** (probe: production `D0` against `W3` in the same run): hypothesis small `D0` ≤ 1.5 × `W3` (≈ 0.4 × today's D0), big ≥ 280 MB/s, `index_allocations` ≤ 3.
- [ ] **Step 5: Verification set, sanitizers.**
- [ ] **Step 6: Commit** — `perf: write JSON tapes into presized arena blocks`

### Phase B — sessions and the small-message path

#### Task B1: Session ABI

**Files:**
- Create: `json/native/session.h`
- Modify: `json/native/json.seam` (`JsonSessionResult`, `JsonLimitsStruct`, `session_create/decode/decode_small/retain/reset/release`), `json/native/api.cc` (new entry points; `td_json_decode`/`td_json_decode_adopted` reimplemented over a thread-local session for compatibility), `json/native/api_test.cc`, `json/native/parser_pool.h` (deleted once nothing uses it), `json/native/README.md`
- Modify: `bazel/myna_contract.py` expected symbol set

**Interfaces:**
- Produces: `td_json_session_create(const JsonLimitsStruct*) → void*` (null on allocation failure); `td_json_session_decode(session, data, len, flags, text_out, text_cap) → status` filling the session's embedded `JsonSessionResult` (valid until the next session call); `td_json_session_retain(session, JsonResult* out) → status` moving the blocks; `td_json_session_reset(session)` (leaf: resets bump pointers, no frees); `td_json_session_release(session)`; `td_json_session_decode_small` identical to decode but `TD_JSON_ARGUMENT` above `TD_JSON_CRITICAL_LIMIT` (4096) and declared `native_leaf=true@explicit_contract` with the bound documented in `api.h`.
- Produces: the session owns `simdjson::ondemand::parser` (capacity grows to the largest input, bounded by `maxInputBytes`), a padded input buffer (same bound), the `block_arena`, and the key table scratch.
- Produces: `flags`: `TD_JSON_WANT_UTF16_TEXT` (Task D2), `TD_JSON_UTF16_INPUT` (Task D3), `TD_JSON_FRAME_MODE` (Task G1); unknown flags → `TD_JSON_ARGUMENT`.

- [ ] **Step 1: Tests in `api_test.cc`**: decode twice through one session and read the first result's strings after the second decode only via a retained result (`retain` then decode again; strings intact); a failed decode leaves `session_decode` reusable and reports no retained bytes; `decode_small` rejects 4097 bytes; retain moves without copying (node pointer identity equals the session's view before retain); allocation-failure injection at session creation and at every block; 10,000 sequential decodes make no net allocations (ASan leak check plus `index_allocations == 0` after warm-up).
- [ ] **Step 2–3: red/green**, regenerate bindings, sanitizers.
- [ ] **Step 4: Measure**: probe `A0` through the session API: hypothesis small decode+reset ≤ 700 ns native.
- [ ] **Step 5: Commit** — `feat: add native JSON sessions with arena-backed results and detachable retain`

#### Task B2: `JsonSession` on SVM: borrowed parse, retained decode, per-thread default

**Files:**
- Create: `json/src/main/java/dev/elide/truffledata/json/JsonSession.java`, `JsonBorrow.java` (package-private `JsonOwner` for borrowed documents: reusable, re-armed per decode, assumption invalidated at the end of every borrow)
- Modify: `JsonSvm.java` (static `decode` delegates to a thread-local session; `JsonLimits` carried by the session), `JsonMessage.java` (constructed from a retained `JsonResult`), `JsonOwner.java` (lazy `ConcurrentHashMap`), `json/BUILD.bazel`
- Modify: `json/tests/SvmAcceptance.java`, `json/tests/CompiledReads.java` (borrow gate), `docs/JSON.md`

**Interfaces:**
- Produces: `JsonSession.open()`, `open(JsonLimits)`, `parse(byte[], JsonSink)`, `parse(byte[], int, int, JsonSink)`, `with(byte[], Function<JsonValue,R>)`, `decode(byte[])`, `decode(byte[], int, int)`, `validate(byte[])`, `close()`. Thread-confined; `decode`/`parse` while a borrow is pinned throws `IllegalStateException("JSON session is borrowed")`.
- Produces: `JsonBorrow` reuses one `SvmValueTape` reader per decode (a new reader object per decode until Myna M3 lands; budget below) and one `JsonText` cursor (Task D1).
- Consumes: Task B1 ABI.

- [ ] **Step 1: Tests (`SvmAcceptance`)**: `borrow_invalidated_by_next_parse` (a `JsonValue` captured from `with` throws `IllegalStateException` after the next `parse`, in interpreted and compiled code via the assumption); `parse` inside a sink's event throws; `decode` after `parse` returns a message whose strings survive the session's next decode and the session's close; thread mismatch rejected; `JsonSvm.decode` behaviour unchanged for the existing assertions.
- [ ] **Step 2: Gates**: `json_smoke.py` ingest allocation for `simdjson-generic-interop` ≤ 160 B/doc and for a new `simdjson-session-java` cell (borrowed `parse` into `JavaSink`) ≤ 1,900 B/doc; `CompiledReads` adds a borrowed-read root under the ≤ 0.05 B/read gate.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Measure** (`json_measure`, cells `simdjson-generic-interop` ingest, `simdjson-session-java` ingest, each run against the `861dff1` binary on the same worker in the same session): hypothesis decode+close ≤ 0.45 × baseline (Phase A's native savings plus ≈ 300 ns of Java and ABI fixed cost), session parse + `JavaSink` ingest below `gson-tree` ingest in the same run. Retain under `benchmarks/results/json-session-<sha>/`.
- [ ] **Step 5: Commit** — `feat: decode JSON through thread-confined sessions with borrowed and retained results`

### Phase C — reads

#### Task C1: Position-hinted, hash-first member lookup

**Files:**
- Create: `json/src/main/java/dev/elide/truffledata/json/JsonKey.java` (bytes, low/high words, hash; `of(String)` rejects unpaired surrogates like `keyOf`)
- Modify: `JsonValue.java` (`ReadMember`, `IsMemberReadable`, `member(JsonKey)`, static `memberNode(owner, node, key, hint)`, static boundary `scan`), `JsonOwner.java` (`next`, `keyMatches`, lazy directory for objects > 32 members), `HeapValueTape.java`/`SvmValueTape.java`/`SegmentValueTape.java` (hash table access), `docs/JSON.md`
- Modify: `json/tests/CompiledReads.java`, `json/tests/JsonInteropTest.java`, `benchmarks/comparison/json_smoke.py` (interop root still 1 loop, 0 allocations)

**Interfaces:**
- Produces: `JsonValue.member(JsonKey)` (public; `member(Utf8Key)` kept as a thin adapter), `memberNode(JsonOwner, long, JsonKey, int[] hint)` as in §3.
- Produces: `JsonOwner.directory(long object)` → `int[]` open-addressing table `(hash → key node offset)` for objects with more than 32 members, cached like `arrayIndex`.

- [ ] **Step 1: Tests**: `hint_miss_after_shape_change` (same site reads `id` from a document where `id` is first, then from one where it is last, then from one without `id`; results exact, `isMemberReadable` false for the last); 1,000-member object lookups hit the directory (diagnostic `directories()` count); escaped keys match their decoded bytes; keys > 16 bytes; snapshot parity.
- [ ] **Step 2: Gates**: `CompiledReads` interop root ≤ 0.05 B/read and ≤ 10,000 graph nodes; `json_smoke` `GenericReads[simdjson-generic-interop]` keeps `(2000, 1, 0)`.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Measure** (`json_measure` peak `simdjson-generic-interop` and `simdjson-snapshot`): hypothesis ≤ 0.6 µs/doc for ten reads (from 2.7 µs; Gson 0.53 µs). Retain under `benchmarks/results/json-reads-<sha>/`.
- [ ] **Step 5: Commit** — `perf: resolve JSON members through position hints and key hashes in compiled code`

### Phase D — export and the `JSON.parse` path

#### Task D1: `JsonSink` v2 with a reusable text cursor; `SimdNativeValueTape` on SVM

**Files:**
- Create: `json/src/main/java/dev/elide/truffledata/json/JsonText.java`, `json/native/bridge/` (thin Rust crate `truffledata-json-bridge` depending on `myna-values`, `rust_static_library`), `bazel/myna_values.bzl` or `MODULE.bazel` crate_universe entries
- Modify: `JsonSink.java` (`key(JsonText)`, `stringValue(JsonText)`, `decimalValue(JsonText)`), `JsonExportNode.java` (one cursor per export, rebound), `JavaSink.java` (+ optional `KeyCache`), `SvmValueTape.java` (`extends SimdNativeValueTape`), `json/tests/JsonExportTest.java`, `json/tests/BUILD.bazel` and `benchmarks/comparison/BUILD.bazel` (`cc_deps` += bridge), `benchmarks/comparison/JsonFixture.java` (`CountingSink`), `docs/JSON.md`
- Note: `JsonSink` v1 is pre-release (docs say "unreleased"); no compatibility shim.

**Interfaces:**
- Produces: `JsonText` as in §2.2; `toJavaString()` uses the tape's `stringValue` (SIMD path ≥ 256 B on SVM).
- Produces: `JavaSink.KeyCache` (per sink or shared, bounded 1,024 entries, keyed by hash then bytes) so repeated keys reuse one `String`.

- [ ] **Step 1: Tests**: export parity with Task 10's differential test; `JsonText` escaping check: retaining the cursor past the event and calling it throws `IllegalStateException`; a 300-byte non-ASCII string decodes identically through the SIMD path and the local path; key cache returns the same `String` instance for equal keys across documents.
- [ ] **Step 2: Gates**: `json_smoke` `simdjson-generic-java` peak budget lowered to 1,900 B/doc (from 2,400; measured headroom to be set from the first run and recorded in `JSON.md`); `simdjson-export-walk` stays ≤ 1.0 B/doc.
- [ ] **Step 3: Implement**; build wiring for `myna_utf8_to_utf16_v2` verified by a link-time symbol check in `SvmAcceptance`.
- [ ] **Step 4: Measure**: hypothesis `JavaSink` peak ≤ 1,900 B/doc and ≤ 2.4 µs/doc; retained under `benchmarks/results/json-export-<sha>/`.
- [ ] **Step 5: Commit** — `perf: export JSON through a reusable text cursor and SIMD string materialization`

#### Task D2: Per-document UTF-16/Latin-1 text arena and a `TruffleString` sink

**Files:**
- Modify: `json/native/session.h`/`decoder.h` (`TD_JSON_WANT_UTF16_TEXT`: for every key/string node ≥ 1 byte write Latin-1 (ASCII) or UTF-16LE (simdutf `convert_valid_utf8_to_utf16le`) into `text_out`, with a `(offset:32, length:31, utf16:1)` entry per node in a side array; `TD_JSON_TEXT_TOO_SMALL` status if `text_cap` is insufficient), `json.seam`, `api.cc`, `api_test.cc`
- Create: `json/src/main/java/dev/elide/truffledata/json/TruffleSink.java` (reference sink building `TruffleString`s; the GraalJS sink in Elide follows it), `JsonTextArena.java` (pinned `byte[]` presized to `2 × len + 16`, reused per session)
- Modify: `JsonSession.java` (`parse(byte[], TruffleSink)` overload requesting the arena), `JsonText.java` (`toTruffleString` from the arena: `TruffleString.fromByteArrayUncached(array, offset, length, encoding, false)`), `benchmarks/comparison/JsonComparison.java`/`JsonFixture.java` (`simdjson-session-truffle` cell: parse into `TruffleString` keys/values and a `LinkedHashMap`, read ten members), `json_smoke.py`, `json_measure.py`, `JSON.md`

**Interfaces:**
- Produces: the text arena contract: ASCII strings as one byte per char (`Encoding.ISO_8859_1`-compatible bytes, promoted to UTF-16 by TruffleString without a copy when the code range is ASCII), non-ASCII strings as UTF-16LE (`Encoding.UTF_16LE`). The array is owned by the sink after `parse` returns (the session allocates a fresh one if the sink kept it: `JsonTextArena.take()`).
- Produces: `TruffleSink.KeyCache` keyed by the native hash and bytes, returning interned `TruffleString` keys.

- [ ] **Step 1: Tests**: every string of the 200-document corpus round-trips through the arena to the same code points as `stringValue()`; surrogate-pair escapes land as two UTF-16 units; a document whose strings exceed `2 × len` cannot exist (assert the bound by construction with a 1-byte string of 2 UTF-16 units: `"é"` is 6 input bytes → 2 output bytes); `TD_JSON_TEXT_TOO_SMALL` is reachable only with a caller-shrunk capacity and leaves the session reusable.
- [ ] **Step 2: Gate**: new `simdjson-session-truffle` peak/ingest cells with a per-document budget recorded in `JSON.md` (expected: guest objects + one `TruffleString` per string + the arena); graph gate `(1000, 2, None)`.
- [ ] **Step 3: Implement.** Verify the `copy=false` sharing behaviour of `TruffleString.fromByteArrayUncached` on the pinned GraalVM revision with a unit test; if sharing is not permitted for `UTF_16` strings created from Latin-1 bytes, fall back to `fromByteArrayUncached(..., UTF_16LE, false)` over a UTF-16-only arena (still one object per string, 2× arena bytes for ASCII) and record the ruling.
- [ ] **Step 4: Measure**: hypothesis ingest ≤ 3.0 µs/doc for parse + `TruffleString` objects (Gson tree 4.7 µs; `JavaSink` ~5.9 µs today). Retain under `benchmarks/results/json-truffle-<sha>/`.
- [ ] **Step 5: Commit** — `feat: write per-document UTF-16 text arenas for TruffleString export`

#### Task D3: UTF-16 input (`parse(TruffleString)`)

**Files:**
- Modify: `json/native/session.h` (`TD_JSON_UTF16_INPUT`: `simdutf::convert_utf16le_to_utf8` into the padded buffer, rejecting unpaired surrogates with `TD_JSON_SYNTAX`), `json.seam` docs, `api_test.cc`, `JsonSession.java` (`parse(TruffleString, JsonSink)`: ASCII code range → internal byte array; otherwise `UTF_16` bytes with the flag), `json/tests/JsonInteropTest.java`, `docs/JSON.md`

- [ ] **Step 1: Tests**: a UTF-16 input with non-ASCII keys parses to the same tape as its UTF-8 spelling; an unpaired surrogate in the input is rejected; ASCII `TruffleString` input makes no Java copy (allocation counter).
- [ ] **Step 2–3: red/green.**
- [ ] **Step 4: Measure** in the SVM probe style (retained): transcode+parse of the 210-B document from UTF-16 vs from UTF-8: hypothesis ≤ +150 ns.
- [ ] **Step 5: Commit** — `feat: parse UTF-16 guest strings through native transcoding`

### Phase E — JVM

#### Task E1: JVM sessions through Myna 0.9.0 FFM

**Depends on:** completion plan Task 15 (JVM typed backend) merged, Myna 0.9.0 pin merged (regenerated `JsonFFM` with `LibraryBinding`).

**Files:**
- Modify: `JsonJvm.java` (`openSession(Path, JsonLimits)` → `JsonSession` backed by `JsonFFM.LibraryBinding`; `decode` delegates), `SegmentValueTape.java` (hash table), `JsonSession.java` (backend split: `SvmSessionBackend`/`JvmSessionBackend` package-private), `json/tests/JvmScopedTest.java`, `json/BUILD.bazel`
- Produces: `JsonSession.parse(MemorySegment padded, long length, JsonSink)` using the generated `borrow` overload (no retention; copied natively only when unpadded).

- [ ] **Step 1: Tests**: mirror Task B2's assertions on the JVM; heap segments rejected by the borrow overload; closed/wrong-thread arena rejected before entry.
- [ ] **Step 2–4: red/green on `//json/tests:jvm`.**
- [ ] **Step 5: Commit** — `feat: run JSON sessions on the JVM through Myna FFM bindings`

#### Task E2: Size-gated critical decode on the JVM

**Files:**
- Modify: `JsonJvm.java`/`JvmSessionBackend` (`CriticalLibraryBinding` for `session_reset` and `session_decode_small`; `parse(byte[])` passes the heap array directly with `Linker.Option.critical(true)` semantics when `length ≤ CRITICAL_LIMIT`, else the normal path), `json/tests/JvmScopedTest.java`, `docs/JSON.md` (the safety argument: bounded input, pre-grown arena, no locks, no exceptions, worst case ≈ 4 KiB / 150 MB/s ≈ 30 µs)

- [ ] **Step 1: Tests**: 4,096-byte input takes the critical path and 4,097 the normal path (observable via a diagnostic counter); results identical; a concurrent GC-heavy thread does not deadlock (stress test with allocation on another thread for 2 s).
- [ ] **Step 2–3: red/green.**
- [ ] **Step 4: Measure**: a JMH-free in-test timing is not evidence; add a JVM row to `json_measure`? No: JVM is secondary (JSON_PLAN). Record a `JvmScopedTest` diagnostic timing in the ledger only.
- [ ] **Step 5: Commit** — `perf: decode small JSON messages on the JVM through bounded critical calls`

### Phase F — streaming

#### Task F1: Native stream over `iterate_many` with carry-over

**Files:**
- Create: `json/native/stream.h`
- Modify: `json.seam` (`stream_create(session, options)`, `stream_feed(stream, data, len) → accepted`, `stream_finish`, `stream_next(stream) → status` (OK = document ready in the session result, `TD_JSON_STREAM_EMPTY`, `TD_JSON_STREAM_TRUNCATED`), `stream_truncated_bytes`, `stream_release`), `api.cc`, `api_test.cc`, `README.md`

**Interfaces:**
- Produces: buffer capacity `2 × maxDocumentBytes` (+ padding); `feed` copies up to the free space and returns the accepted count; `next` runs `parser.iterate_many(buffer, filled, min(filled, batchBytes))` lazily (one `document_stream` per fill), decodes the next document into the session arena via the lean visit, and on stream exhaustion moves `truncated_bytes()` to the front of the buffer. A document larger than `maxDocumentBytes` is `TD_JSON_LIMIT`. Documents are whitespace/newline separated; `allowComma` selects `stream_format` accordingly.

- [ ] **Step 1: Tests**: `stream_boundary_exactness`: 10,000 documents fed in chunks of 1, 7, 64, 4,096 and 1,000,000 bytes produce exactly 10,000 results with the same checksums; a final truncated document reports `TD_JSON_STREAM_TRUNCATED` after `finish`; an invalid document mid-stream reports `TD_JSON_SYNTAX` and the stream resumes at the next document only if the caller asks (`stream_skip`), else stays failed; duplicate keys inside a streamed document are rejected; no net allocation across 10,000 documents.
- [ ] **Step 2–3: red/green, sanitizers.**
- [ ] **Step 4: Measure** with the retained probe's NDJSON section: hypothesis ≥ 300 MB/s for 210-B lines with full tape per document.
- [ ] **Step 5: Commit** — `feat: stream concatenated JSON documents through session-owned iterate_many buffers`

#### Task F2: `JsonStream` Java API and NDJSON cells

**Files:**
- Create: `json/src/main/java/dev/elide/truffledata/json/JsonStream.java`, `JsonStreamOptions.java`
- Modify: `JsonSession.java`, `JsonJvm.java` (borrow overload for `feed(MemorySegment)`), `json/tests/SvmAcceptance.java`, `json/tests/JvmScopedTest.java`, `benchmarks/comparison/JsonComparison.java`/`JsonFixture.java` (`json-stream` workload: 1,000-line NDJSON fixture; engines `gson-stream-lines` (Gson `JsonReader` with lenient=false reading concatenated docs), `simdjson-stream-java` (`next(JavaSink)`), `simdjson-stream-interop` (`next()` + ten reads)), `json_smoke.py`, `json_measure.py`, `JSON.md`, `benchmarks/cases.json`, `docs/JSON.md`

- [ ] **Step 1: Tests**: Java mirrors of F1; `retain()` survives the stream and session close; `next()` while a retained borrow is pinned throws; backpressure: `feed` returns less than offered when the buffer is full and `next()` drains it.
- [ ] **Step 2: Gates**: stream interop root `(2000, 1, 0)`, ≤ 1.0 B/doc; stream `JavaSink` ≤ the Task D1 budget.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Measure**: hypothesis `simdjson-stream-interop` ingest ≤ 1.5 µs/doc (decode + ten reads) against Gson streaming.
- [ ] **Step 5: Commit** — `feat: expose JSON streams with bounded buffers and borrowed documents`

### Phase G — big documents

#### Task G1: Frame-mode validation pass

**Files:**
- Create: `json/native/frames.h` (lean walk with duplicate check and no tape; records `(offset, length, key_hash)` per root child; root scalars have one frame)
- Modify: `session.h` (`TD_JSON_FRAME_MODE` result: `frames ptr`, `frame_count`, root kind, retained input), `json.seam` (`frame_materialize(session, result, index, out_result)`: decodes the frame's slice as an adopted padded view into a fresh arena-backed `JsonResult`; capacity for the slice is `buffer_end − slice_start`, always ≥ `length + SIMDJSON_PADDING` because the buffer is padded), `api.cc`, `api_test.cc`, `README.md`

- [ ] **Step 1: Tests**: `frame_mode_rejects_late_error` (a duplicate key or `1e` in element 999 fails the whole decode, no frames published); frames of the 1,000-record fixture have exact offsets; materializing frame 999 yields the same tape as decoding that element standalone; frames of a root object carry key hashes; a scalar root has one frame.
- [ ] **Step 2–3: red/green, sanitizers.**
- [ ] **Step 4: Measure** (probe section "big"): hypothesis frame pass ≥ 500 MB/s (it is W3 without tape), materialize one 228-B element ≤ 1.2 µs.
- [ ] **Step 5: Commit** — `feat: validate large JSON documents once and index root frames`

#### Task G2: `JsonDocument` with lazy subtrees, pointer queries and sink export

**Files:**
- Create: `json/src/main/java/dev/elide/truffledata/json/JsonDocument.java`, `JsonDocumentOptions.java`
- Modify: `JsonOwner.java` (`permits` += `JsonDocument`), `JsonValue.java` (frame-backed container: `getArraySize`/`readArrayElement`/`readMember` materialize through the document's bounded LRU of subtree tapes; a materialized subtree is a `JsonMessage`-like owner whose views are invalidated when evicted (assumption)), `JsonSession.java` (`open`, `adopt`), `benchmarks/comparison/JsonComparison.java` (`json-big` workload: 228 KB array; engines `gson-tree-big`, `simdjson-big-full` (full tape, read element 999's ten members), `simdjson-big-frames` (frame mode, same reads), `simdjson-big-export` (frames → `CountingSink`)), scripts, `JSON.md`, `docs/JSON.md`

- [ ] **Step 1: Tests**: element reads through frames equal full-tape reads for every element; LRU eviction invalidates evicted views (`IllegalStateException`) and re-materialization works; `at("/999/dims/w")` exact; `export` streams every element through the sink without exceeding `materializedSubtrees` live tapes; `fullTape=true` returns a `JsonMessage`-equivalent; documents below `frameThresholdBytes` take the full-tape path automatically.
- [ ] **Step 2: Gates**: `json_smoke` rules for the three new roots `(2000, 1, 0)` with per-document allocation budgets for materialization recorded in `JSON.md`.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Measure**: hypotheses: `simdjson-big-frames` ingest (validate + frame + read one element) ≤ 0.6 ms per 228 KB document vs full tape ≥ 1.2 ms today; memory ≤ input + 16 KB frames + one subtree.
- [ ] **Step 5: Commit** — `feat: read large JSON documents through frames and on-demand subtrees`

### Phase H — typed alignment and evidence

#### Task H1: Typed decode through sessions

**Files:** `json/native/typed.h` (take the session's parser and arena; drop the private `parser_` member), `api.cc` (`td_json_typed_decode` unchanged signature, thread-local session), `JsonPlan.java` (`decode` through the session; `JsonSession.decode(JsonPlan, byte[])`), tests.

- [ ] Steps as above; gate: scalar `CompiledReads` unchanged; `json-scalar` ingest hypothesis −15% (parser allocation per decode removed).
- [ ] **Commit** — `perf: decode typed JSON records through session parsers`

#### Task H2: Documentation, results and ledger

- [ ] Update `docs/JSON.md` (sessions, streams, documents, sink v2, text arenas, JVM critical path), `json/native/README.md`, `benchmarks/comparison/JSON.md` (every new cell and gate), `JSON_PLAN.md` status.
- [ ] Run `json_measure` once more on the final branch; retain `benchmarks/results/json-final-<sha>/`; record every ruling in the ledger.
- [ ] **Commit** — `docs: record the JSON performance architecture delivery`

#### Task H3: Final verification

- [ ] `bazel test //...`; SVM lanes; sanitizers; `tools/release` JSON consumers; coverage inventory (`.github/scripts/coverage_contract.py`) includes the new sources.

---

## 8. Risks and non-goals

**Risks**

- *Deferred unescaping correctness.* Skipping `get_string()` for clean bodies relies on stage 1 UTF-8 validation plus our control-byte scan. Mitigation: Review Focus 2 tests and a differential fuzz (200 generated documents plus the simdjson `jsonchecker` corpus if vendored) comparing node-by-node with the Task 1 decoder.
- *`TruffleString` array sharing.* `fromByteArrayUncached(..., copy=false)` semantics on the pinned GraalVM must be verified (Task D2 Step 3 ruling). Fallback keeps one object per string but doubles ASCII arena bytes.
- *Session thread-locality.* `JsonSvm.decode` becomes a thread-local session; embedders with many short-lived threads will create many sessions. Mitigation: sessions release arena blocks above 1 MiB on `reset`, and `JsonSession.open()` is the documented path.
- *Hint pollution.* A site reading many shapes misses constantly and pays scan + boundary (today's cost). Mitigation: measured in Task C1; a shape cache is the documented follow-on.
- *Myna bridge build.* Linking `myna-values` through crate_universe adds Rust to the JSON SVM image. Mitigation: Task D1 keeps `NativeValueTape` as the fallback class behind a build flag until the link is proven on RBE.
- *Critical calls on the JVM.* Even bounded, they delay safepoints. Mitigation: 4 KiB default, configurable to 0 (disabled).
- *RBE noise.* All targets are hypotheses; acceptance is by the retained harness with ranges, never a single run.

**Non-goals**

- simdjson threading or an upgrade beyond 5.1.1 (no API need; stage 1 is ~1% of cost).
- Exposing On-Demand cursors as guest values (JSON_PLAN forbids it); frames are validated offsets, not cursors.
- A JSON generator/serializer, JEP 540/Jackson/JSON-P facades, kotlinx adapter (follow-on plans).
- Cross-thread ownership transfer of messages or sessions.
- Changing Myna's tape layout in this plan (requests M1–M5 are upstream; the side tables make them optional).
- JVM comparison cells in `json_measure` (JVM remains secondary per `JSON_PLAN.md`).
- Minify or pretty-print APIs.

## 9. Open questions for the user

1. **`TruffleString` sharing policy** (Task D2): is sharing one per-document array across all strings acceptable for Elide's `JSON.parse` (a single large string retained keeps the whole array alive), or should strings above a size (say 4 KiB) be copied out? Default proposed: share, copy strings ≥ 64 KiB.
2. **JVM critical path** (Task E2): accept the 4 KiB bounded critical decode, or keep every JVM call on normal transitions? Default proposed: enabled at 4 KiB, configurable.
3. **Myna bridge linkage** (Task D1): link the Rust `myna-values` static library into JSON SVM images now, or wait for the 0.9.0 upgrade branch to wire crate_universe and then adopt `SimdNativeValueTape`? Default proposed: wire it in Task D1 behind a flag.
4. **`JsonSvm.decode` default**: thread-local session (proposed) versus requiring embedders to open a `JsonSession` explicitly (no hidden per-thread state).
5. **Big-document threshold**: 1 MiB default for frame mode, or always explicit via `JsonSession.open`? Default proposed: explicit `open` only; `decode` always builds a full tape.
6. **Upstream Myna requests M1–M5**: file now so ABI v4 can be scheduled, or after this plan proves the side-table approach?
