# Protobuf Maximum-Performance Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Record rulings in `docs/superpowers/plans/2026-11-07-protobuf-performance-ledger.md` (create it at Task A1).

**Goal:** Make TruffleData the fastest protobuf decoder and reader on SVM and the JVM: below stock protobuf-java on every operation of the shared consumer (decode, projected and full ingestion, heap input, first access, interpreter and peak reads, detach) and on the generated-getter harness, with Java allocation far below stock's 1,630 B/row, decode throughput at or above 1 GB/s on large and packed shapes, and detach without reparsing. Every semantic rule of today's contracts stays: strict UTF-8, presence, oneof last-wins, merges, unknown fields preserved, budgets, thread-confined scoped owners, no silent fallback.

**Architecture:** One **layout-compiled slab** per decoded message replaces both today's generic index (24-byte entry per field occurrence plus directory words) and the Java object tree. A layout compiler in `protobuf-runtime` assigns every message type fixed slots (presence words, inline 4/8-byte scalars, string headers with byte length, ASCII bit and UTF-16 length, child record offsets, repeated lists) so a field read is a constant offset from a record base. Two producers write byte-identical slabs: a table-driven Rust decoder (ASCII-first validation, packed counting, no per-field index) and a schema-generated pure-Java decoder (the JVM default and the no-native fallback). The slab lives in a Java `byte[]` by default (the native decoder writes into a pinned array on SVM and through a bounded critical call on the JVM), so results have no native lifetime, snapshots are identity, and reads are plain loads; a session-owned native slab remains as the zero-allocation borrowed path. Reads go through a core `Accessor` bound to `(layout, ordinal)` that every backend implements, so compiled reads are offset loads and interpreter reads are one guard and one call. Strings materialize lazily; lengths come from the slab.

**Tech Stack:** Rust nightly-2026-10-09 (`buffa` 0.9.2 wire primitives, `simdutf` 0.7.0), Myna 0.11.0 generator (`buffa.seam` ABI 10: SVM `@CFunction`, JVM FFM `LibraryBinding`/`CriticalLibraryBinding`), GraalVM 25.5.5-elide.24 (SVM `PinnedObject`, Truffle DSL, `ByteArraySupport`), Elide toolchain 2026.11.5 (LLVM 23, ThinLTO), Bazel with Linux amd64 RBE, protobuf-java 4.36.1 as the differential oracle only.

**Spec:** `docs/CONTRACTS.md` and `docs/BUFFA.md` (ownership, budgets, validation, ABI policy), `benchmarks/comparison/CONSUMER.md` (the acceptance consumer and its architectural decisions), `PERFORMANCE_REVIEW.md` §3 B/C/D (representation, borrowing, capability model), Myna `docs/binding-abi-2.md` and `docs/jvm-roadmap.md` (leaf/critical calls).

**Baseline:** `fecb39f` on `feat/perf-v4` (TruffleData 0.8.0 + the 2026-11-06 protobuf cycle). Measured state: `benchmarks/results/protobuf-a58a36d/` (consumer, SVM and JVM), probes in `benchmarks/results/protobuf-probe-fecb39f/`.

## Global Constraints

- **Semantics are unchanged**: proto2/proto3 presence, oneof last-wins, singular-message merge by concatenation, repeated append, map last-key-wins, closed-enum filtering to unknown fields, unknown fields preserved for `encode()`, strict UTF-8 for `string`, required-field checks, depth/size budgets, `-Dtruffledata.strict` revalidation. Differential tests against protobuf-java over the existing 1,000-input seeded corpus plus 8,000 generated inputs per schema are mandatory for every producer (Rust and Java) and must compare slab bytes between producers.
- **No silent fallback.** A native failure is an error. The Java slab decoder is selected by `BuffaWire.Mode`/platform, never after a native error.
- **Native-leaf/critical calls only with an explicit bound**: `decodeSlabSmall` (input and output ≤ `SlabLimits.criticalBytes`, default 64 KiB, no allocation, no locks, no panic escapes) and `fingerprint`. Everything else keeps `native_leaf=false@explicit_contract`.
- **Pinned Java arrays** are held only for the duration of one native call, never across calls; inputs above `criticalBytes` on SVM decode from native input or through a native scratch slab plus one copy.
- **Compiled read gates stay at ≤ 0.05 B/read**; the shared consumer root keeps ≤ 256 AST nodes and the 10,000-node graph budget; `consumer_smoke`/`consumer_native_smoke` cover every engine/mode/stage.
- **Evidence only from the retained harnesses** (`consumer_measure`, `consumer_measure_jvm`, `measure`/`measure_views`, `CompiledReads`, native smokes), raw samples under `benchmarks/results/protobuf-<topic>-<sha>/`. Every number below is a **hypothesis**, labelled measured (with source) or estimated.
- **Memory bounds**: a slab is ≤ `SlabLimits.maxSlabBytes` (default 16 × input + 4 KiB, hard cap `Integer.MAX_VALUE`); owning slabs copy payload bytes so retained memory is slab-only; borrowing slabs retain the input lease exactly as views do today.
- The other active worker owns `json/` and `tape/`; this plan does not edit them. Core changes (Task B2) are additive and keep every existing export working.

## Review Focus

1. **Two producers, one slab.** The Rust and Java decoders must produce byte-identical slabs for every input (including unknown fields, merges, oneofs, maps, closed enums, truncated and malformed inputs rejected at the same byte). Pinned by Task A3's `producers_agree_byte_for_byte` over the differential corpus.
2. **Pinned arrays never outlive a call and critical calls never allocate.** The SVM decode path pins input and output for one `@CFunction` call; the JVM critical path rejects inputs or capacities above the bound before entry and the Rust entry point for it performs no heap allocation (asserted by the allocation probe). Pinned by Task B3's `pin_scope_is_one_call` and Task C1's `critical_decode_allocates_nothing`.
3. **Capacity retry must be exact and bounded.** `NEEDS_CAPACITY` reports the exact byte count; the retry succeeds first time; a slab above `maxSlabBytes` fails with `BUDGET` without allocating. Pinned by Task A2's `needed_bytes_exact` and `budget_rejects_before_allocation`.
4. **String headers must be trusted only after validation.** A string's ASCII bit and UTF-16 length are written only after UTF-8 validation; lax projection (unread strings not validated) must mark the slab `LAX` and `strict` mode must reject reading such a string without validating. Pinned by Task A2's `invalid_utf8_rejected_same_byte_as_java` and Task E1's `lax_slab_validates_on_read`.
5. **Accessors are immutable and layout-bound.** A core `Accessor` is keyed by layout identity; a row of another layout must miss the cache and never read a wrong offset. Pinned by Task B2's `accessor_rejects_foreign_layout` and the schema-churn test in `AllocationTest`.
6. **Merges and oneofs keep protobuf-java semantics.** A singular message field occurring twice merges (fields overwrite, repeated append); a later oneof member clears earlier ones; a repeated entry of a map with a duplicate key keeps the last. Pinned by Task A2's `merge_matches_java_concatenation`, `oneof_last_wins`, `map_last_key_wins`.

---

## 1. Diagnosis

All timings are from shared BuildBuddy RBE workers ("AMD EPYC" Firecracker VMs) and are advisory. Consumer-harness numbers come from `benchmarks/results/protobuf-a58a36d/` (paired, ten shards, medians of fork medians); probe numbers from `benchmarks/results/protobuf-probe-fecb39f/` (one worker each, best of 7). "Measured" cites the file; "estimated" is arithmetic over measured values.

### 1.1 Where protobuf stands (SVM, shared consumer, × stock latency)

Measured (`protobuf-f26678a/README.md`, final-state table):

| Operation | stock (ns/row) | generated-owned | native view | default decode |
|---|---:|---:|---:|---:|
| Decode only, ~150 B | 772.5 (194 MB/s) | 1.06× | 0.89× (217 MB/s) | 1.86× |
| Decode only, ~52 KB | 225,996 (229 MB/s) | 1.16× | 0.89–1.25× | 2.04× |
| Projected ingestion | 785.5 | 1.04× | 1.02× | 1.99× |
| Full ingestion + consumption | 847.8 | 1.04× | 1.44× | 2.03× |
| Full, heap input | 854.3 | 1.00× | 1.52× | 2.14× |
| First full access | 64.6 | 0.52× | 8.54× | 1.27× |
| Interpreter reads (full) | 62.7 | 4.77× | 6.37× | 5.44× |
| Peak reads (full) | 105.6 | 0.42× | 1.6–2.1× | 1.15× |
| Detach + consume | 841.5 | 1.01× | 6.59× | 7.51× |
| Java B/row, full resident | 1,630 | 1,608 | 1,132 | 1,424 |
| Native B/row | 0 | 0 | 1,142 retained | 1,142 transient |

JVM (`protobuf-a58a36d/jvm/results.md`, candidate): stock decodes the small fixture in 281.8 ns (532 MB/s) and the large one in 131.6 µs (394 MB/s); native-view full resident is 3.90× stock, native-eager 4.70×, generated-owned 1.42×; interpreter reads are 1.36× for generated-owned against 4.77× on SVM for the same code.

### 1.2 The native decoder, not the Java layer, is the ingestion cost

Measured (`protobuf-probe-fecb39f/rust.txt`, one worker, ns per message; MB/s of wire):

| Variant (small 150 B / holdout 510 B / large 52 KB) | small | holdout | large |
|---|---:|---:|---:|
| structural scan, length-delimited opaque (floor) | 28 · 5,341 | 51 · 10,003 | 4,141 · 12,516 |
| **production ABI 9 view decoder** (scratch reuse, exact copy, release) | **634 · 236** | 1,878 · 272 | 204,721 · 253 |
| hand-written flat slab, production validator | 174 · 860 | 632 · 807 | 62,453 · 830 |
| hand-written flat slab, ASCII fast path then validator | 115 · 1,307 | 689 · 740 | 26,351 · 1,967 |
| hand-written flat slab, no validation | 80 · 1,863 | 211 · 2,422 | 23,126 · 2,241 |
| naive table-driven flat slab, validated | 275 · 546 | 856 · 596 | 98,676 · 525 |
| table-driven, parse-time projection (sparse consumer) | 226 · 662 | 798 · 639 | 93,945 · 552 |
| table-driven, project nothing (validate only) | 81 · 1,855 | 266 · 1,917 | 21,924 · 2,364 |

Reading the table:

- **The harness's SVM decode-only cell (690 ns) is the native decoder (634 ns) plus ≈ 56 ns of transition, lease and owner.** The Java/ABI layer is not the problem; the generic index representation is. For the same bytes, a flat schema-specialized result costs 174 ns with exactly the same validator (3.6×), and a naive table-driven generic decoder writing that layout costs 275 ns (2.3×). The ABI 9 decoder pays per field occurrence: a 24-byte entry push with capacity and budget checks, directory words, a second pass to write directories, one exact allocation and one free per message.
- **String validation is the second cost.** With the production validator (`std::str::from_utf8` plus `encode_utf16().count()` below 64 bytes, simdutf above) the hand decoder spends 94 ns of 174 on ≈ 10 short strings (5.4 ns per 8–9-byte ASCII name, measured). An 8-bytes-at-a-time ASCII check before the validator cuts that to 1.0 ns per name (hand 174 → 115 ns; large 62 → 26 µs). On the Unicode-heavy holdout the validator is the whole gap (632 vs 211 ns): short non-ASCII strings need a better scalar validator (Task A2 gate).
- **Varint decoding is not a bottleneck and SIMD varints lose.** Packed varints decode at 0.75 ns each in a byte loop (3.1 GB/s at 2.35 B average), 0.89 ns through `buffa::encoding::decode_varint`, and 2.6 ns through 8-byte shift-and-mask or `pext` extraction (measured). Counting varints by continuation bits runs at 0.33 ns each, which is what makes exact-size packed lists cheap. Fixed-width packed fields are a `memcpy`. No SIMD varint work is pursued.
- **Parse-time projection is second-order on this schema.** Skipping the nested `Field` messages while still validating their structure saves 18% (275 → 226 ns); the floor when nothing is materialized is 81 ns. Projection is Phase E, after the representation.
- **Representation size**: the flat slab for message 1 is 488 B with 8-byte slots everywhere against 892 B for the ABI 9 view (28 entries + 55 words); the layout compiler's 4-byte slots and string headers bring the estimate to ≈ 400–460 B (≈ 3.5× input) for owning slabs that also copy payload bytes.

### 1.3 Ceiling and competitors

Measured (`cpp.txt`, a worker ≈ 1.3× faster than the Rust worker on `scan_flat`; `jvm.txt`, HotSpot with the Graal JIT):

| Decoder | small 150 B | holdout 510 B | large 52 KB |
|---|---:|---:|---:|
| protobuf C++ 33.4, generated classes, reused message | 289 ns · 519 MB/s | 839 · 608 | 82,912 · 625 |
| protobuf C++, arena-allocated messages | 342 · 439 | 968 · 527 | 192,338 · 269 |
| upb 33.4 (mini-table interpreter; fast table not built) | 366 · 409 | 1,076 · 474 | 124,907 · 415 |
| protobuf-java 4.36.1 on HotSpot/Graal JIT (tight loop) | 191 · 784 | 801 · 637 | 49,740 · 1,042 |
| pure-Java schema-specialized slab decoder, validated (JIT) | 89 · 1,689 | 245 · 2,081 | 26,461 · 1,959 |
| protobuf-java on SVM (harness decode cell) | 772 · 194 | — | 225,996 · 229 |
| TruffleData native view today (harness decode cell, SVM) | 690 · 217 | — | 282,883 · 183 |

Conclusions for Question 1:

- **Materializing decoders (C++ generated, upb, protobuf-java) sit at 0.4–0.8 GB/s on the small nested shape and 0.4–1.0 GB/s on the large shape.** They build object graphs or arena messages and copy strings. A flat layout that keeps strings as spans and writes presence bits and inline scalars is 2–3× faster than all of them on the same host class (hand 115–174 ns on the slower worker vs 289–366 ns on the faster one).
- **Shapes that reach GB/s**: large nested messages (hand_ascii 1.97 GB/s measured; Java slab 1.96 GB/s), packed scalars (3.1 GB/s varints, memcpy for fixed), string-heavy ASCII (validation at 1 ns per short string). **Shapes that cannot**: 150-byte messages end to end, because per-message fixed costs (transition ≈ 30 ns, pin ≈ 32 ns, owner object, slab allocation) are of the order of the decode itself; the decoder alone reaches 1.3 GB/s but the SVM cell will land around 0.4–0.6 GB/s. Short non-ASCII strings are validator-bound at ≈ 0.75 GB/s until the scalar validator improves.
- **The JVM result changes the JVM strategy**: HotSpot/Graal compiles a schema-specialized Java decoder to 89 ns per message, 2.2× faster than protobuf-java and faster than the Rust decoder measured on its (slower) worker; downcalls cost 7.9 ns (4.5 ns critical) and a 150 B heap-to-native copy 4.3 ns, so a native decode on the JVM would save nothing. The JVM default is the Java slab decoder; the native path stays available for parity testing and large inputs.

### 1.4 Reads

Measured in the consumer: first full access is 8.5× stock for views (552 ns: each child `NativeRow`, `NativeArray` and position cache is built on first touch) and 0.52× for generated-owned records (plain fields). Peak full reads are 0.42× for generated-owned and 1.6–2.1× for views (directory word load, `Objects.checkIndex`, lease check, `Object[]` value cache per row, string materialization for `len(name)`). Interpreter reads are 4.8–6.4× on SVM for every engine but 1.36× for generated-owned on the JVM: the SVM gap is AOT code quality of the dispatch chain (five layers per read: node state and cache walk, `accepts`, `getSchema` guard, `readLong`, the row's own `(plan, ordinal)` cache; `core/.../ReadLongNode.java`, `protobuf-buffa/.../TypedRow.java:131-174`) compiled without profiles, not the representation. The CSV cycle documented the same Native Image behaviour (`benchmarks/results/csv-3f23d0c/README.md`).

### 1.5 Detach, allocation, JVM

Detach reparses the wire with the Java codec (`TypedMessage.java:62-66`): 6.6–7.5× stock. Full-resident allocation is 1,132 B/row for views and 1,424 B for the default decode against 1,630 B for stock; the view's bytes are child rows, arrays, value caches and Strings created on access. On the JVM the directory is copied into an `int[]` per view and reads go through `ByteBuffer`, which is why views are 3.9× stock there.

### 1.6 Per-operation summary

| Operation | Dominant cost today | Ceiling (probe) | What this plan removes |
|---|---|---|---|
| Decode only | 24-byte entries, directory pass, exact copy, per-string `utf16_length` | 115–174 ns native; 89 ns Java on JVM | generic index, second pass, allocation, validator on ASCII |
| Projected / full ingestion | decode + child rows, arrays and Strings on access | decode + ~50–150 ns of offset reads | Java materialization; lengths from the slab |
| First access | `NativeRow`/`NativeArray`/cache creation | one `Object[]` + flyweights | per-row discovery |
| Peak reads | directory loads, lease, value cache, Strings | offset loads (generated-owned 0.42×) | indirection and lifetime checks |
| Interpreter reads | five dispatch layers, AOT without profiles | one guard + one call | DSL layers; PGO as evidence task |
| Detach | reparse | memcpy or identity | the reparse |
| JVM | FFM view, `int[]` copy, `ByteBuffer` reads | 89 ns pure Java | the native hop |

## 2. Architecture

### 2.1 The slab (format `TDSLAB1`, seam ABI 10)

A slab is one little-endian byte array. Offsets are `u32` from the slab start; records are 8-byte aligned.

```
header (32 B): magic u32 'TDS1' · flags u32 (OWNING, BORROWING, LAX, CERTIFIED) · bytes u32 · records u32 ·
               handles u32 · byteRegion u32 · unknownRegion u32 · reserved u32
record(T):     presence u64 × ceil(fields/64) · oneofCase u8 × oneofs (padded to 4) · handle u32 · unknown u32
               · 8-byte slots (int64/uint64/sint64/double/fixed64/sfixed64) · 4-byte slots (everything else)
4-byte slot:   int32/uint32/sint32/enum/float/fixed32/sfixed32 inline · bool u8 in a u32 · string/bytes = offset of a
               string header (0 = absent) · message = child record offset (0 = absent) · repeated/map = list offset
string header: length u32 (bit 31 = ASCII) · utf16 u32 · handle u32 · [payload bytes] (OWNING) or input offset u32 (BORROWING)
list:          count u32 · pad u32 · elements (4-byte scalars, 8-byte scalars, string-header offsets, child offsets)
unknown:       per record, raw bytes of unknown fields in wire order, appended to the unknown region (offset, length)
```

- **Presence** is bit `ordinal` of the presence words; proto3 implicit-presence scalars set the bit when the field is present on the wire (default values never are), which keeps `isPresent` exact and `has()` free.
- **Handles** number every record and every present string/bytes value in decode order; Java keeps one `Object[handles]` per message, lazily allocated, for child flyweights and materialized strings.
- **Owning vs borrowing**: owning slabs copy payload bytes into the byte region (≈ 10 ns per message at memcpy speed, estimated) and depend on nothing after decode; borrowing slabs keep input offsets and an input lease (the current view semantics). Owning is the default for `decode`; borrowing is `decodeBorrowed` (Phase D) and is also used for `bytes` payloads ≥ `SlabLimits.borrowBytes` (default 4 KiB) when the input is retained.
- **Layout program**: the layout compiler (`protobuf-runtime`, Java) emits per type: slot offsets by ordinal, presence word count, oneof case index, string/child/list kinds, record size; it is appended to the schema program as a `TDL1` section so the Rust decoder reads the same offsets. Compile-time checks compare the Rust-side recomputation with the Java program (Task A2 rejects a mismatch as `MALFORMED`).
- **Eligibility** is every schema `ProtoPlan` accepts: > 64 fields (more presence words), real oneofs (case byte), maps (list of entry records plus a lazily built Java directory for lookups), required fields (checked from presence after decode), groups. The current view exclusions disappear; the eager `State` path remains only for text/ProtoJSON input and `strict` audits until Phase E ports them.

### 2.2 Producers

**Rust (`protobuf-buffa/native/slab.rs`)**, table-driven over the compiled layout:

- Per type: `FieldDesc { wire, kind, ordinal, slot, repeated, packed, child, oneof, closed_values }`, direct number lookup ≤ 2,048 (as today), linear beyond.
- Hot loop: read tag; **wire-order prediction** (compare against the next declared field's tag before the table lookup; protobuf writers emit in number order); per-kind handlers; one slab-capacity check per record and per list, not per field; unknown and mismatched-wire fields appended to the record's unknown span; closed-enum values outside the set go to unknown fields.
- Strings: 8-byte ASCII word check → set ASCII bit, `utf16 = length`; otherwise a scalar DFA validator computing UTF-16 units in the same pass below 64 bytes and simdutf (`validate_utf8` + `utf16_length_from_utf8`) above.
- Packed scalars: count by continuation bits (varints) or divide (fixed), reserve `[count]` exactly, decode in place; unpacked repeated and repeated messages use a per-decoder pending stack `(ordinal, value)` flushed into lists when the record closes.
- Singular-message merge: a second occurrence appends its payload to a per-decoder merge scratch keyed by `(record, ordinal)`; when the parent closes, merged children are decoded once from the concatenated scratch (protobuf semantics are exactly concatenation), bounded by `maxSlabBytes`.
- Output goes to a caller-provided buffer (`out`, `capacity`); on overflow the decoder finishes a **sizing pass** (same walk, no writes) and returns `NEEDS_CAPACITY` with the exact byte count. No heap allocation in the steady state (the pending and merge scratch are decoder-owned and reused).

**Java (`protobuf-runtime/.../SlabCodec.java` + generated `SlabDecoder_<Type>`)**: the same algorithm over `byte[]` writing a `byte[]` through `ByteArraySupport`/`VarHandle`; generated per contract type by `ContractPlugin` (constant tags and slots, like the probe's decoder) and interpreted from the layout program for runtime plans. Byte-identical output is a test invariant.

### 2.3 Java owner and rows

```java
/** Durable, immutable, GC-managed decode result. Never scoped; snapshot() is identity. */
public final class SlabMessage extends SlabRow implements AutoCloseable {   // close() is a no-op kept for API symmetry
  final byte[] slab; final BuffaPlan plan; final Layout layout; Object[] handles;   // handles allocated on first child/string read
  public SlabMessage snapshot();            // this
  public byte[] encode();                   // re-serialize from the slab (known fields + unknown spans)
  public int slabBytes();                   // for allocation accounting
}
/** Flyweight (owner, record offset, type layout); 24 B; cached in owner.handles[recordHandle] when created. */
public class SlabRow extends AbstractRow { @ExportLibrary(DataLibrary.class) ... }
public final class SlabArray implements TruffleObject { /* list view: (owner, list offset, element kind) */ }
/** Zero-Java-allocation borrowed path (Phase D): slab in the session arena, valid until the next decode. */
public final class SlabSession implements AutoCloseable { public SlabRow decode(WireInput in); public SlabMessage retain(); }
```

`BuffaPlan.decode(byte[]|WireInput|ByteBuffer)` returns a `SlabMessage` on every backend. `decodeView` and the `TypedMessage`/`NativeRow` view machinery are retired once Task G1 shows parity; `ProtoRow` (owned Java codec) stays as the strict-audit oracle.

### 2.4 Representation per use case

| Use case | Representation | Why |
|---|---|---|
| Decode and read later (RPC payloads, records) | owning heap slab (`SlabMessage`) | no lifetime, no release, detach free, shareable after publication |
| Parse-consume-release hot loop | borrowed session slab (`SlabSession.decode`) | zero Java allocation, arena reset per call |
| Sparse consumer with known projection | heap slab + parse-time projection (Phase E) | skipped subtrees cost the scan floor |
| Huge payloads (`bytes` ≥ 4 KiB, large inputs) | borrowing slab with input lease | avoids copying payloads |
| Text / ProtoJSON input | eager `State` → `ArrayRow` today; slab producer for text in Phase E | correctness first; text is not a throughput target |

## 3. Reads

**Core `Accessor` (Task B2).** `core` gains:

```java
public abstract class Accessor {                       // immutable, one per (schema/layout identity, ordinal), PE-constant
  public abstract boolean present(Object row);
  public abstract long readLong(Object row); public abstract double readDouble(Object row); public abstract boolean readBoolean(Object row);
  public abstract Object read(Object row);              // boxed/record/array; today's DataLibrary.read semantics
  public abstract int length(Object row);               // UTF-16 units for strings, element count for arrays; no materialization
}
public abstract class Schema { public Accessor accessor(int ordinal); ... }   // backends override; default wraps DataLibrary (uncached)
```

`ReadLongNode.required` becomes `guards = {"row instanceof AbstractRow", "((AbstractRow) row).schema() == schema", "index == cachedIndex"}` with `@Cached("schema.accessor(cachedIndex)") Accessor accessor` and the body `accessor.readLong(row)`: one identity compare, one devirtualized call, a checkcast and the loads. The generic specialization keeps the `DataLibrary` path. `ReadLengthNode` calls `accessor.length(row)` so `len(name)` never builds a `String`. Every backend implements `accessor()`: `SlabLayout` (offset + presence bit), `Batch` (column), `StaticLayout` (property), `GeneratedMessage` (ordinal switch), JSON/CSV/tape via the default wrapper until their owners adopt it (not in this plan).

**Compiled reads over a slab** are `presenceWord & bit` and `ByteArraySupport.getInt/getLong(slab, base + constOffset)`, with no lifetime check for heap slabs (the owner is final and immutable). On SVM this is the same instruction count as generated-owned's field load. Target: peak full reads ≤ 0.5× stock (generated-owned measures 0.42×).

**Interpreter reads**: the accessor path removes three of five dispatch layers (estimated ≤ 2.5× stock from 4.8–6.4×). Task F1 measures the remaining AOT gap with a PGO-instrumented consumer image (the EE toolchain profile is already configured in `MODULE.bazel`); if interpreter cells improve ≥ 1.5× the plan records how Elide builds images with profiles. The comparison stays honest: stock's interpreter cell is AOT code behind a boundary; Task G1 adds a `stock-interop` cell that reads stock messages through the same `SharedRoot` via host interop so the interpreter comparison is like for like.

**Generated accessors at build time**: `ContractPlugin` emits `SlabAccess_<Type>` with constant offsets (`static long number(SlabRow row)`) next to the existing `Access_<Type>`; runtime plans use `Layout` objects, both PE-constant. Elide can call either.

## 4. Strings and allocation

| Path | Today | Plan |
|---|---|---|
| String length (`len(name)`) | materialize `String`, then `length()` | header: ASCII → byte length, else `utf16` from the header; 2 loads |
| String value (host) | `byte[]` copy + `new String` per read | lazily `new String(slab, off, len, ISO_8859_1 or UTF_8)` cached in `handles[h]`; ASCII strings take the Latin-1 constructor (no decoder) |
| String value (guest) | same | `TruffleString.fromByteArrayUncached(slab, off, len, UTF_8/ISO_8859_1, copy=false)` cached in `handles[h]`; ruling on sharing semantics recorded in Task B1 (JSON plan Task D2 holds the same question) |
| Bytes | `BorrowedBytes` with lease | `SlabBytes` over the slab (owning) or `BorrowedBytes` (borrowing) |
| Child rows | `NativeRow` + directory + value cache per row | 24-byte flyweight, cached in `handles[recordHandle]` |
| Arrays | `NativeArray` + `Object[]` elements | `SlabArray` flyweight; elements read from the list in place |
| Decode only | tape owner + lease + `TypedMessage` ≈ 336 B (JVM view) | `SlabMessage` (40 B) + slab `byte[]` (≈ 3.5× input; 150 B → ≈ 480 B incl. header) |
| Full resident | 1,132 B (view) / 1,630 (stock) | ≤ 700 B/row: slab + handles `Object[]` (≈ 16 + 4 × handles) + ≈ 8 flyweights; no Strings for length reads |
| Projected | 1,017 B (JVM view) | ≤ 450 B/row |

Allocation gates (Task B1/B2): `CompiledReads` slab root ≤ 0.05 B/read; consumer `decode` cell for `slab` ≤ 560 B/row small; `resident` full ≤ 700 B/row; `resident` sparse ≤ 450 B/row.

## 5. JVM

1. **Default: the Java slab decoder** (Task A3) — no downcall, no native library, no pinning; measured 89 ns vs 191 ns stock in the probe (2.2×), holdout 245 vs 801 ns (3.3×), large 26.5 vs 49.7 µs (1.9×). Hypothesis for the harness: decode-only ≤ 0.6× stock, full resident ≤ 0.8×, peak ≤ 1.0×, interpreter ≤ 2.0×, detach ≤ 1.0×.
2. **Native path for parity and large inputs** (Task C1): `CriticalLibraryBinding` for `decodeSlabSmall` with heap `byte[]` input and output (`critical(true)`, ≤ 64 KiB each), normal `LibraryBinding` with native scratch and one copy above; both produce the same `SlabMessage`. This also lets the differential suite run the Rust producer on the JVM.
3. **No `int[]` copies and no `ByteBuffer` reads**: slab reads on the JVM are `byte[]` loads through `ByteArraySupport` (Truffle) or a `VarHandle`; `MemorySegment` only for borrowed session slabs.

## 6. Detach and snapshots

An owning `SlabMessage` is already durable: `snapshot()` returns `this`, `Values.normalize` admits it as immutable, and cross-thread handoff is safe after publication (no leases, no close). A borrowed session row's `retain()` is one `byte[]` copy of the slab bytes (≈ 50 ns for 500 B, estimated) plus payload bytes for borrowing slabs. `encode()` serializes from the slab (known fields in number order, unknown spans verbatim) instead of keeping the input. Target: detach + consume ≤ 0.8× stock (from 6.6×).

---

## 7. Implementation tasks

Task format follows the JSON performance plan. Every task ends with the standard verification set: `bazel test //protobuf-buffa/native:test //protobuf-buffa/tests:jvm //protobuf-buffa/tests:jvm_views //protobuf-runtime/... //protobuf/... //core/... //tests/...`, `bazel test //protobuf-buffa/tests:native_test //protobuf-buffa/tests:fallback_test`, `bazel test //benchmarks/comparison:consumer_smoke //benchmarks/comparison:consumer_native_smoke //benchmarks/comparison:smoke //benchmarks/comparison:native_smoke`, the Rust allocation/failure-injection probes, and `bazel/myna_contract` for ABI changes. Tasks that add cells run `consumer_measure` (and `consumer_measure_jvm` where the JVM is affected) paired against the previous image and retain results under `benchmarks/results/protobuf-<topic>-<sha>/` with the standard README caveats.

### Phase A — layout and producers

#### Task A1: Layout compiler and slab format

**Files:**
- Create: `protobuf-runtime/src/main/java/dev/elide/truffledata/protobuf/Layout.java` (per-type slot table, presence words, oneof cases, record size, handle kinds; `Layout.compile(ProtoPlan)`; `Layout.program()` → `TDL1` bytes), `SlabFormat.java` (header constants, string-header and list helpers over `byte[]` via `ByteArraySupport`), `docs/SLAB.md` (the format of §2.1, byte-exact)
- Modify: `protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/NativeSchema.java` (append the `TDL1` section after the enum tables; the `TDB3` magic and every existing offset are unchanged, so the ABI 9 library keeps working until Task A2 replaces it), `protobuf-runtime/.../ProtoPlan.java` (`layout()` accessor, lazily compiled, identity-stable per plan)
- Tests: `protobuf-runtime/src/test/.../LayoutTest.java` (new), `protobuf-buffa/tests/TypedAcceptance.java` (schema program golden)

**Interfaces:**
- Produces: `Layout { int recordBytes(type); int presenceWords(type); int slot(type, ordinal); SlotKind kind(type, ordinal); int oneofIndex(type, ordinal); int childType(type, ordinal); }` with `SlotKind { I32, I64, F32, F64, BOOL, STRING, BYTES, MESSAGE, LIST_I32, LIST_I64, LIST_F32, LIST_F64, LIST_BOOL, LIST_STRING, LIST_BYTES, LIST_MESSAGE, MAP }`.
- Produces: `TDL1` section: per type `[recordBytes u32][presenceWords u32][oneofs u32][fields u32]` then per field `[slot u32][kind u32]`; the Rust side recomputes and compares.
- Produces: golden slabs for ten hand-built messages (`protobuf-runtime/src/test/resources/slab/*.bin`) used by both producers.

- [x] **Step 1: Failing tests** (`LayoutTest`): `offsets_are_aligned_and_disjoint` (8-byte slots first, record size multiple of 8, no overlap across 200 generated schemas), `presence_words_scale_past_64_fields` (65 and 130 fields), `oneof_case_bytes_assigned` (two oneofs, three members each), `layout_identity_stable` (same plan → same `Layout` instance), `program_round_trips` (parse the `TDL1` bytes back and compare), `golden_slabs_decode_by_hand` (hand-written expected bytes for `Timestamp`, `Type` message 1 of the consumer fixture, a map, an oneof, an unknown field, a merged singular message).
- [x] **Step 2: Run** `bazel test //protobuf-runtime/...` — FAIL (classes missing).
- [x] **Step 3: Implement.** Slot assignment: 8-byte kinds in ordinal order, then 4-byte kinds; `handle` and `unknown` words after the presence words; record size padded to 8.
- [x] **Step 4: Verification set.** Expected: PASS, including every existing view test: the ABI 9 `compile` reads types through the header offsets and never looks past the enum tables, so the appended section is invisible to it (assert this with a Rust test that compiles a program with and without the section and compares the descriptors; ruling recorded).
- [x] **Step 5: Commit** — `feat: compile protobuf plans into fixed slab layouts`

#### Task A2: Rust slab decoder and seam ABI 10

**Files:**
- Create: `protobuf-buffa/native/slab.rs` (compiled layout, decoder, sizing pass, merge scratch, string validation with ASCII fast path and scalar DFA, packed counting), `protobuf-buffa/native/slab_tests.rs` (differential against `typed::view` entries and against golden slabs; failure injection; allocation probe)
- Modify: `protobuf-buffa/native/buffa.seam` (`abi=10`; `struct SlabResult size=32`; imports `compileLayout`, `decodeSlab` (`native_leaf=false@explicit_contract`), `decodeSlabSmall` (`native_leaf=true@explicit_contract`, `error=no_failure`), `releaseLayout`; `decodeView`/`releaseView`/`compileView` removed in Task G1, kept until then), `lib.rs` (FFI, `catch_unwind`, status codes: `NEEDS_CAPACITY = 6`), `unicode.rs` (`validate_utf16_units(bytes) -> Option<(usize units, bool ascii)>`), `BUILD.bazel` (`slab.rs` in every crate target), `bazel/myna_contract.py` expectations
- Modify: `protobuf-buffa/tests/wrong_abi.c` fingerprint expectations

**Interfaces:**
- Produces: `td_buffa_decode_slab(compiled, data, length, flags, projection|null, out, capacity, max_depth, max_slab_bytes, result) -> i32`; `flags`: `OWNING = 1`, `LAX = 2` (Phase E), `SIZING_ONLY = 4`; `result.bytes` = written or needed; `result.records`, `result.handles`, `result.flags` (`CERTIFIED` when no required field is missing and no merge occurred).
- Produces: `td_buffa_decode_slab_small`: identical, rejects `length > 65536 || capacity > 65536` with `BUDGET` before any work, performs no allocation (pending/merge scratch are fixed-size stack arrays; a merge that overflows them returns `BUDGET`, which the Java side retries through `decodeSlab`).
- Produces: unknown-field policy: unknown numbers, wire-type mismatches and out-of-set closed-enum values are copied verbatim into the record's unknown span (owning) or recorded as input spans (borrowing).

- [ ] **Step 1: Failing tests** (`slab_tests.rs`): `golden_slabs_match` (Task A1 resources), `entries_match_view_decoder` (8,000 generated inputs over the ABI 9 test schema: every present field, value, repeated order and nested record agrees with `typed::view` entries, reinterpreted through the layout), `needed_bytes_exact` (capacity 0 → `NEEDS_CAPACITY` with `bytes`; capacity `bytes` → OK; capacity `bytes − 1` → `NEEDS_CAPACITY` again with the same count), `budget_rejects_before_allocation`, `invalid_utf8_rejected_same_byte_as_java` (corpus of 300 malformed strings; the failing offset equals protobuf-java's), `ascii_bit_and_utf16_units_exact` (ASCII, Latin, CJK, supplementary, 63/64/65-byte boundaries), `packed_lists_sized_exactly` (varint and fixed, including a truncated last varint → `MALFORMED`), `merge_matches_java_concatenation`, `oneof_last_wins`, `map_last_key_wins`, `unknown_fields_preserved_in_order`, `depth_limit_enforced`, `small_entry_allocates_nothing` (allocation probe), `failure_injection_leaks_nothing`.
- [ ] **Step 2: red/green** on `bazel test //protobuf-buffa/native:test`; regenerate bindings (`bazel build //protobuf-buffa/native:bindings`).
- [ ] **Step 3: Measure** with the retained probe (copy `benchmarks/results/protobuf-probe-fecb39f/probe.rs` back temporarily, add a `slab` variant calling the production decoder into a reused buffer, same run as `view` and `hand`). Acceptance hypotheses (same worker, ratios): small `slab ≤ 1.3 × hand_ascii` (≈ 150 ns) and `≤ 0.3 × view`; holdout `slab ≤ 1.5 × hand_novf` (the scalar validator gate); large ≥ 1.5 GB/s; slab bytes for message 1 ≤ 4 × input. Retain under `benchmarks/results/protobuf-slab-native-<sha>/`.
- [ ] **Step 4: Verification set, sanitizers (`--config=asan` lane as in the JSON plan), `myna_contract`.**
- [ ] **Step 5: Commit** — `feat: decode protobuf into layout-compiled slabs natively (seam ABI 10)`

#### Task A3: Pure-Java slab producer (generated and table-driven)

**Files:**
- Create: `protobuf-runtime/src/main/java/dev/elide/truffledata/protobuf/SlabCodec.java` (table-driven decoder over `Layout`; `SlabWriter` with growth by exact resize; UTF-8 validator with ASCII words; packed counting), `protobuf/src/main/java/dev/elide/truffledata/protobuf/SlabGenerator.java` (emits `SlabDecoder_<Type>` with constant tags/slots and `SlabAccess_<Type>`), tests `protobuf-runtime/src/test/.../SlabCodecTest.java`, `protobuf/src/test/.../SlabGeneratorTest.java`
- Modify: `ContractPlugin.java` (emit the slab decoder and accessors next to `Message_`/`Access_`), `protobuf-buffa/tests/NativeTapeAcceptance.java` (producer agreement on the JVM through the Task C1 binding; until C1 lands, through a `rust_binary` oracle run in a `sh_test` over the corpus files)

**Interfaces:**
- Produces: `SlabCodec.decode(Layout, byte[] in, int off, int len, SlabLimits) -> byte[]` and the generated `SlabDecoder_<Type>.decode(byte[], int, int, SlabLimits) -> byte[]`, both returning a trimmed slab (`bytes` exact).

- [ ] **Step 1: Failing tests**: `producers_agree_byte_for_byte` (golden slabs; 8,000 generated inputs per schema compared against the Rust decoder's output files), `generated_matches_table_driven`, `rejects_same_inputs_same_status` (malformed corpus), `utf8_validation_parity` (the Task A2 string corpus).
- [ ] **Step 2–3: red/green.**
- [ ] **Step 4: Measure** (temporary `java_test` in the probe style, retained): hypothesis generated decoder ≤ 0.55 × `Type.parseFrom` on the small fixture, table-driven ≤ 0.8 ×; retain under `benchmarks/results/protobuf-slab-java-<sha>/`.
- [ ] **Step 5: Commit** — `feat: generate pure-Java slab decoders and table-driven slab decoding for runtime plans`

### Phase B — owner, reads, SVM path

#### Task B1: `SlabMessage`, `SlabRow`, `SlabArray`; `BuffaPlan.decode` returns slabs

**Files:**
- Create: `protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/SlabMessage.java`, `SlabRow.java` (`@ExportLibrary(DataLibrary.class)`, `@ExportLibrary(InteropLibrary.class)` via `AbstractRow`; `ReadLong/ReadDouble/ReadBoolean` cached on `row.layout() == cachedLayout` with constant slot; `read` materializing child/array/string through `handles`), `SlabArray.java`, `SlabBytes.java`, `SlabMap.java` (lazy key directory), `SlabStrings.java` (String/TruffleString materialization and the sharing ruling)
- Modify: `BuffaPlan.java` (`decode(...)` → `SlabMessage` through the Java producer when `selected() == JAVA` and through native otherwise; `decodeView` deprecated, delegating to `decode`), `BuffaWire.java` (`SlabLimits` plumbing), `TypedMessage.java` (`snapshot()` of a slab-backed message returns the message), `docs/BUFFA.md`
- Tests: `protobuf-buffa/tests/TypedAcceptance.java` (every existing semantic case now runs over slabs), `CompiledReads.java` (slab root, ≤ 0.05 B/read, ≤ 192 AST nodes), `tests/.../AllocationTest.java` (slab rows in the shared gates), `WellKnownAcceptance.java` unchanged expectations

- [ ] **Step 1: Failing tests**: all of `TypedAcceptance` over `decode()` with `nativeBacked()` replaced by `slabBacked()`; `length_reads_do_not_allocate_strings` (allocation counter over 1e6 `ReadLengthNode` reads of `name`); `child_flyweights_cached` (same `SlabRow` instance twice); `strings_cached_per_handle`; `snapshot_is_identity`; `encode_round_trips_unknown_fields`; `map_lookup_last_wins`; `oneof_case_reads`; `strict_mode_revalidates_slab` (`-Dtruffledata.strict=true` audits every string and presence against `ProtoRow`).
- [ ] **Step 2: Gates**: `CompiledReads` slab root ≤ 0.05 B/read; `consumer_smoke` with engine `slab` (Task G1 adds the engine; here a minimal `slab` engine runs the existing `SharedRoot`).
- [ ] **Step 3: Implement.** Record the `TruffleString` sharing ruling (copy=false over the slab; fallback copy per string) in the ledger.
- [ ] **Step 4: Measure** (`consumer_measure` paired against the `fecb39f` image; cells `decode`, `resident`, `first`, `peak`, `detach`, allocation): hypotheses — decode-only small ≤ 0.5× stock, large ≤ 0.35× and ≥ 1 GB/s; projected ≤ 0.6×; full resident ≤ 0.7×; first full access ≤ 1.2×; peak full ≤ 0.5×; detach ≤ 0.8×; Java B/row full ≤ 700, sparse ≤ 450. Retain under `benchmarks/results/protobuf-slab-<sha>/`.
- [ ] **Step 5: Commit** — `feat: expose protobuf slabs as durable rows with lazy children and strings`

#### Task B2: Core `Accessor` and PE-constant reads for every backend

**Files:**
- Create: `core/src/main/java/dev/elide/truffledata/Accessor.java`, `core/.../LibraryAccessor.java` (default wrapper over uncached `DataLibrary`)
- Modify: `Schema.java` (`accessor(int)`, overridable; cached array), `ReadLongNode.java`, `ReadDoubleNode.java`, `ReadBooleanNode.java`, `ReadLengthNode.java` (accessor specializations before the library ones), `AbstractRow.java` (`schema()` final accessor), `Batch.java`, `StaticLayout.java`, `protobuf-runtime/.../GeneratedMessage.java`, `ProtoRow.java`, `protobuf-buffa/.../SlabRow.java`, `ScalarRow.java` (accessor implementations), `docs/TRUFFLE.md`
- Tests: `core/src/test/.../AccessorTest.java`, `tests/.../AllocationTest.java` (accessor roots), `tests/.../PolicyTest.java` inventory

- [ ] **Step 1: Failing tests**: `accessor_rejects_foreign_layout` (two plans with equal schemas, one site; results exact, cache holds two entries), `accessor_is_pe_constant` (`CompilerAsserts.partialEvaluationConstant` in a compiled root), `length_accessor_counts_utf16` (ASCII, BMP, supplementary), existing schema-churn tests keep bounded fallback.
- [ ] **Step 2: Gates**: every existing `AllocationTest`/`CompiledReads` budget unchanged; `consumer_smoke` AST ≤ 256.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Measure** (`consumer_measure`, `interpret` and `peak` cells for `slab`, `generated-owned`, `native-eager`): hypothesis interpreter full ≤ 2.5× stock for `slab` and `generated-owned` (from 4.8–6.4×), peak unchanged or better. Retain under `benchmarks/results/protobuf-accessor-<sha>/`.
- [ ] **Step 5: Commit** — `perf: read rows through layout-bound accessors in every backend`

#### Task B3: SVM pinned-array decode and ownership capabilities

**Files:**
- Modify: `protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/SvmBackend.java` (`decodeSlab(byte[] in, off, len)`: pin input and output `byte[]`, call `decodeSlab` under a normal transition, unpin; capacity policy: per-plan high-water mark starting at `4 × len + 256`, retry once on `NEEDS_CAPACITY`; inputs > 64 KiB decode from native scratch and copy), `BuffaSvm.java`, `BuffaFeature.java` (reachability), `docs/BUFFA.md` (capability model: scoped borrowed / durable immutable / live mutable; slabs are durable immutable), `core/.../Values.java` (`normalize` admits `SlabMessage`/`SlabRow` of owning slabs; rejects borrowed session rows)
- Tests: `protobuf-buffa/tests/Acceptance.java`, `NativeTapeAcceptance.java` (`pin_scope_is_one_call`: no pinned object outlives `decode`; concurrent GC stress), `tests/.../OwnershipTest` additions

- [ ] **Step 1: Failing tests**: `heap_input_decodes_without_copy` (allocation counter: slab bytes + message only), `capacity_retry_once` (diagnostic counter), `large_input_uses_scratch`, `owning_slab_admitted_by_values_normalize`, `borrowed_row_rejected`, `cross_thread_read_of_durable_slab_allowed`.
- [ ] **Step 2–3: red/green** on `//protobuf-buffa/tests:native_test`.
- [ ] **Step 4: Measure**: `heap` cells ≤ `resident` + 5% (no copy); retained with Task B1's run or a new pair.
- [ ] **Step 5: Commit** — `feat: decode protobuf from heap arrays into heap slabs on SVM`

### Phase C — JVM

#### Task C1: FFM critical decode and JVM selection

**Files:**
- Modify: `FfmBackend.java` (`CriticalLibraryBinding` for `decodeSlabSmall`/`fingerprint`; `LibraryBinding` for `decodeSlab` with a native scratch slab and one copy above 64 KiB), `BuffaJvm.java`, `BuffaWire.java` (`Mode.AUTO` on the JVM selects the Java producer unless `-Dtruffledata.buffa.jvmNative=true`), `docs/BUFFA.md` (critical-call safety argument: bounded input, no allocation, no locks, worst case ≈ 64 KiB at 1 GB/s ≈ 64 µs)
- Tests: `protobuf-buffa/tests/AcceptanceTest.java` (`critical_decode_allocates_nothing` (native allocation counter through a diagnostic export), `above_bound_takes_normal_path`, `producers_agree_byte_for_byte` on the JVM over the full corpus, GC stress during critical calls)

- [ ] **Step 1–3: red/green** on `//protobuf-buffa/tests:jvm`.
- [ ] **Step 4: Measure** (`consumer_measure_jvm`, engines `slab` (Java producer) and `slab-native`): hypotheses decode-only small ≤ 0.6× stock, large ≤ 0.6×; full resident ≤ 0.8×; peak ≤ 1.0×; interpreter ≤ 2.0×; detach ≤ 1.0×; `slab-native` within 1.3× of `slab`. Retain under `benchmarks/results/protobuf-jvm-<sha>/`.
- [ ] **Step 5: Commit** — `feat: decode protobuf slabs on the JVM in Java by default and natively through bounded critical calls`

### Phase D — sessions (borrowed slabs)

#### Task D1: `SlabSession` with arena slabs and `retain()`

**Files:**
- Create: `protobuf-buffa/src/main/java/dev/elide/truffledata/buffa/SlabSession.java`, `BorrowedSlabRow.java` (lease + assumption re-armed per decode, like `ViewOwner`), native `session.rs` (arena buffer sized to the largest slab seen, reset per call; `td_buffa_session_create/decode/reset/release`), seam additions
- Modify: `docs/BUFFA.md`, `benchmarks/comparison/ConsumerComparison.java` (engine `slab-session` for `decode`/`resident` cells; retains nothing)
- Tests: `NativeTapeAcceptance.java` (`borrow_invalidated_by_next_decode` in interpreted and compiled code, `retain_survives_session_close`, `decode_while_pinned_throws`)

- [ ] **Step 1–3: red/green.**
- [ ] **Step 4: Measure**: `slab-session` resident cells ≤ `slab` resident − slab allocation cost; Java B/row for `decode` ≤ 48 (the message flyweight). Retain under `benchmarks/results/protobuf-session-<sha>/`.
- [ ] **Step 5: Commit** — `feat: decode protobuf into session-owned borrowed slabs with explicit retain`

### Phase E — projection, text, codegen (evidence-gated)

#### Task E1: Parse-time projection

**Files:** `protobuf-runtime/.../Projection.java` (wanted bitset per type, `lax` flag), `BuffaPlan.project(Projection)` (compiled once; native `projection` pointer per call), `slab.rs` (unwanted singular fields skipped after structural validation; unwanted repeated messages counted into `[count]` only; strings in skipped subtrees validated unless `LAX`; `LAX` sets the slab flag and `SlabRow` validates such strings on read), `SlabCodec.java` (same), tests `lax_slab_validates_on_read`, `projection_counts_without_decoding`, `projection_results_equal_full_decode_on_wanted_fields`.

- [ ] Steps as above. Gate: sparse resident with projection ≤ 0.85 × sparse resident without (probe: 226 vs 275 ns); no change to full cells. **Commit** — `perf: push projections into the protobuf slab decoder`

#### Task E2: Text and ProtoJSON through the slab (optional; only if Elide needs text throughput)

Port `typed.rs` text/JSON walks to write slabs (`State` retired). Gate: text acceptance unchanged; allocation ≤ eager path. **Commit** — `feat: parse protobuf text and ProtoJSON into slabs`

#### Task E3: Per-schema native codegen (optional; gated)

Only if Task A2's measurement shows the table-driven decoder ≥ 1.25 × the hand-written probe on the same run: emit Rust per contract type from `ContractPlugin` into the Bazel build (`rust_library` per contract set), selected by plan identity. **Commit** — `perf: generate native slab decoders per protobuf contract`

### Phase F — SVM code quality

#### Task F1: PGO evidence for interpreter and AOT paths

**Files:** `benchmarks/comparison/BUILD.bazel` (`consumer_svm_pgo_instrument` and `consumer_svm_pgo` `native_image` targets on the EE toolchain profile; profile collected by running `--smoke` plus the interpreter cells), `docs/BENCHMARKS.md` (procedure), `tools/bench/consumer.py` (binary label `pgo`).

- [ ] Measure interpreter and peak cells for `slab`, `generated-owned` and `stock` with and without profiles. Ruling: if interpreter cells improve ≥ 1.5×, document the profile workflow for Elide images and keep the targets; otherwise record and remove. Retain under `benchmarks/results/protobuf-pgo-<sha>/`. **Commit** — `bench: measure protobuf consumer images built with profiles`

### Phase G — evidence, retirement, docs

#### Task G1: Harness engines, like-for-like stock cell, retirement of ABI 9 views

**Files:** `ConsumerComparison.java` (engines `slab`, `slab-session`, `stock-interop` (stock messages read through `SharedRoot` via host interop, interpreter and peak only); `nativeBacked()` → `slabBacked()`; allocation accounting adds `slabBytes()`), `Comparison.java` (`buffa` engine over slabs; `--views` retired), `tools/bench/consumer.py` (validation flags), `CONSUMER.md`, `COMPARISON_BENCHMARKS.md`; delete `NativeRow.java`, `TypedArray.java` view paths, `ViewOwner` view code, `typed_view.rs`, `typed_view_tests.rs`, ABI 9 imports (seam ABI 10 final), `docs/BUFFA.md` ABI 9 section replaced by `docs/SLAB.md` reference.

- [ ] Full paired `consumer_measure` + `consumer_measure_jvm` against the `fecb39f` images; `measure`/`measure_views` once; retain under `benchmarks/results/protobuf-final-<sha>/`; update `PERFORMANCE_REVIEW.md` with a dated section. **Commit** — `bench: measure protobuf slabs against stock and retire ABI 9 views`

#### Task G2: Documentation, ledger, release notes

- [ ] `docs/BUFFA.md`, `docs/SLAB.md`, `docs/CONTRACTS.md` (capability model), `docs/RELEASE_0_9_0.md` draft (ABI 10, bindings and native attachments upgraded together), ledger complete. **Commit** — `docs: record the protobuf slab architecture delivery`

#### Task G3: Final verification

- [ ] `bazel test //...`; SVM lanes (`native_test`, `fallback_test`, `native_views` removed); sanitizers; `tools/release` consumers; coverage inventory (`.github/scripts/coverage_contract.py`) lists `SlabRow`, `SlabArray`, `SlabMap`, `SlabSession`; `PolicyTest` module inventory includes Buffa.

---

## 8. Targets (hypotheses, SVM shared consumer unless stated)

| Operation | Today (best engine) | Target | Basis |
|---|---:|---:|---|
| Decode only, ~150 B | 0.89× (view) | ≤ 0.5×, ≥ 400 MB/s | probe: 634 → 115–174 ns native; +≈ 100 ns fixed |
| Decode only, ~52 KB | 0.89–1.25× | ≤ 0.35×, ≥ 1 GB/s | probe: 205 → 26 µs (1.97 GB/s) |
| Projected ingestion | 1.02× | ≤ 0.6× | decode gain + lengths from slab |
| Full ingestion + consumption | 1.04× (owned) / 1.44× (view) | ≤ 0.7× | decode gain + no Strings for lengths + flyweights |
| Full, heap input | 1.00× (owned) | ≤ 0.7× | pinned input, no copy |
| First full access | 0.52× (owned) / 8.5× (view) | ≤ 1.2× (stretch 0.8×) | one `Object[]` + ≈ 8 flyweights (≈ 100 ns est.) |
| Interpreter reads (full) | 4.77× (owned) | ≤ 2.5× (≤ 1.5× with PGO, F1) | dispatch layers 5 → 2; JVM shows 1.36× for the same code |
| Peak reads (full) | 0.42× (owned) / 1.6–2.1× (view) | ≤ 0.5× | offset loads, no lifetime check |
| Detach + consume | 1.01× (owned) / 6.6× (view) | ≤ 0.8× | identity snapshot |
| Java B/row (full / sparse / decode) | 1,132 / 1,017 / 336 (view) | ≤ 700 / 450 / 560 | slab + handles + flyweights |
| JVM decode only small / large | 1.25× / 1.07× (owned); 2.47× / 2.16× (view) | ≤ 0.6× / ≤ 0.6× | probe: 89 vs 191 ns; 26 vs 50 µs |
| JVM full resident / peak / interpret / detach | 1.42× / 0.96× / 1.36× / 1.33× (owned) | ≤ 0.8× / ≤ 1.0× / ≤ 2.0× / ≤ 1.0× | Java producer, no native hop |

## 9. Risks and non-goals

**Risks**

- *SVM AOT quality for the Java producer and the read path.* Native Image without profiles compiled a CSV fused path 50× larger than its plain loop (`csv-3f23d0c`). Mitigation: the Rust producer is the SVM default; hot methods stay small with cold paths out of line; Task F1 measures PGO; the consumer smoke checks graph size.
- *Slab amplification.* ≈ 3.5× input for this schema (estimated from the probe's 488 B with 8-byte slots); wide schemas with many absent string fields cost 4 B per absent field. Mitigation: 4-byte slots, string headers only for present strings, `maxSlabBytes` budget, borrowing for large payloads; Task A2 gate ≤ 4× input.
- *Merge scratch complexity.* Singular-message merges are rare but must be exact. Mitigation: concatenation semantics implemented once in both producers, differential corpus with forced merges, `BUDGET` on scratch overflow in the critical path with a normal-path retry.
- *Pinned arrays and critical calls.* A pinned `byte[]` blocks compaction for the call; a critical downcall delays safepoints. Mitigation: 64 KiB bound, native scratch above it, GC stress tests, bound configurable to 0.
- *`TruffleString` over slab bytes.* Sharing semantics of `copy=false` must be verified on the pinned GraalVM (same question as the JSON plan). Fallback: one copy per string, still cached per handle.
- *Core API change.* `Accessor` touches every backend's `Schema`. Mitigation: default `LibraryAccessor` keeps today's behaviour for backends that do not override; JSON/tape owners adopt later.
- *RBE noise.* Every target is a hypothesis; acceptance only from paired runs with ranges, never one run. Large-message cells vary 20–40% between identical binaries (`protobuf-f26678a/README.md`).
- *Semantic drift between producers.* Mitigated by byte-for-byte agreement tests on every corpus and by `strict` mode auditing slabs against `ProtoRow`.

**Non-goals**

- SIMD varint decoding (measured slower than the byte loop on this host).
- Changing the wire format, editions/extensions/MessageSet support, or protobuf-java feature parity beyond today's documented subset.
- A universal intermediate representation across formats; the slab is protobuf's physical layout, the `Accessor` is the only shared contract.
- Cross-thread *mutable* ownership; durable slabs are immutable and shareable, sessions remain thread-confined.
- Text/ProtoJSON throughput (Task E2 is optional), serialization performance, Any registries.
- JVM native decode as the default (the Java producer is faster on HotSpot); FFM upcalls.
- Upgrading Buffa, Myna or protobuf; only `buffa::encoding` primitives and simdutf are used natively.

## 10. Open questions for the user

1. **Owning by default?** Owning slabs copy payload bytes (≈ 10 ns per small message) so results have no input dependency and detach is free; borrowing keeps the view's lease model and avoids copies for large `bytes`. Proposed: owning by default, borrowing for `bytes` ≥ 4 KiB and for `decodeBorrowed`.
2. **Rust or Java producer on SVM?** Both produce identical slabs. Proposed: Rust until Task F1 (PGO) shows the AOT Java producer within 1.1× on the decode cells; then the Java producer becomes the universal path and the native library optional.
3. **Validation policy for projected-away strings** (Task E1 `LAX`): validate everything (today's contract) or defer validation to the read? Proposed: strict by default; `Projection.lax()` opt-in with on-read validation.
4. **Core `Accessor`** (Task B2): change the shared API for every backend now, or protobuf-only first? Proposed: now, with the default wrapper so other backends are unaffected until adopted.
5. **JVM critical bound**: 64 KiB proposed; 0 disables critical calls.
6. **Retire ABI 9 views and the `typed`/`typedReuse` index** in this plan (Task G1), or keep them one release for consumers? Proposed: retire; bindings and attachments ship together as ABI 10 in 0.9.0.
7. **PGO images for Elide** (Task F1): is a profile-build step acceptable in Elide's image pipeline if it pays ≥ 1.5× on interpreter cells?
8. **upb fast table**: run a follow-up probe with `--@protobuf//upb:fasttable_enabled` to record the tail-call parser's number (not built here), or accept the mini-table figure as the competitor reference?

**Decided (user, 2026-11-07): every proposed default.** Q1 owning slabs by default; borrowing for `bytes` ≥ 4 KiB and `decodeBorrowed`. Q2 Rust producer on SVM until F1 shows the AOT Java producer within 1.1 ×. Q3 strict by default, `Projection.lax()` opt-in with on-read validation. Q4 change the core `Accessor` now (additive, default wrapper). Q5 64 KiB critical bound. Q6 retire ABI 9 views; ABI 10 ships in 0.9.0. Q7 a PGO step is acceptable for Elide images if it pays ≥ 1.5 × on interpreter cells. Q8 run the upb fast-table probe as a follow-up record.
