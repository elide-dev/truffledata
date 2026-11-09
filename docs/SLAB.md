# Protobuf slabs (format `TDSLAB1`)

A slab is the fixed-layout decode result of one protobuf message: one little-endian byte array in
which every message instance is a **record** whose fields sit at constant offsets computed from the
schema. A field read is a presence-bit test and a load at `record + slot`. This document is the
byte-exact contract that every producer (the Rust decoder, the generated and table-driven Java
decoders) and every reader follows. The architecture and the rationale are in
`docs/superpowers/plans/2026-11-07-protobuf-performance.md` §2.

Sources: `protobuf-runtime/.../Layout.java` (layout compiler and the `TDL1` program),
`SlabFormat.java` (constants and accessors), the executable model of this document,
`protobuf-runtime/src/test/.../SlabReference.java`, which the golden slabs in
`protobuf-runtime/src/test/resources/slab/` are checked against, and the native producer
`protobuf-buffa/native/slab.rs` (seam ABI 11, below), which reproduces every golden byte for byte.

## Java ownership

`ProtoPlan.decodeSlab` is native-free. Generated `SlabDecoder_<Type>` classes select
constant field-number and slot dispatch after checking the supplied schema. Both produce
the same owning bytes as the native decoder. `BuffaPlan.decode` returns the runtime
`SlabMessage` owner using the selected producer.

Owning `SlabMessage`/`SlabRow` values are immutable and may be read across threads.
`row()` and `snapshot()` return the root owner itself; `close()` has no effect.
Children, strings, byte arrays and repeated arrays are lazy cached views of immutable
slab bytes. Byte/repeated values implement `OwnedArray`; a `String` copies UTF-8 once
when materialized. UTF-16 lengths are available without materializing strings.

`copyOf` defensively copies an external buffer. `takeOwnership` is a trusted bridge
operation: it transfers exclusive ownership of a freshly produced array, and the caller
must discard every writable alias. Both validate structure before publishing a row.
A capacity-backed array may be larger than the header's valid extent. Offset validation
uses that extent, `slabCopy()` returns exactly it, and `retainedSlabBytes()` accounts
for the full array against `maxSlabBytes`.

### Producer selection

Generated contract models bind their Java slab decoder once; runtime-created models use
`SlabCodec`. `ProtoPlan.decodeSlab` always follows this native-free route. Buffa JVM `AUTO`
uses Java, while SVM `AUTO` uses the linked native archive when present and otherwise the
same Java fallback. Explicit native mode can use FFM on the JVM. Generated Java also supports
eligible lazy projections; other selected Java decodes use the table producer. Native selected
decodes use TDP1 with the compiled layout. Native
sessions reuse caller-owned storage and return epoch-checked borrows rather than changing
ordinary decode ownership. See the [producer matrix](BUFFA.md#producer-and-ownership-matrix)
for text and explicit retention behavior.

The experimental Truffle-compiled decoder is not a production dispatch option: its joint
JVM/SVM speed gate failed. This does not change the generated-contract path or the required
native-free fallback.

## Conventions

- All integers are little-endian. `u16`/`u32`/`u64` are unsigned unless stated; slot values keep
  the bits of their protobuf type (below).
- An **offset** is a `u32` byte offset from the start of the slab. Offset 0 is the header, so
  `0` in a reference slot means *absent*. Presence bits are authoritative: an implicit-presence
  string or bytes field that holds its default keeps the offset of its header with the bit clear.
- Every byte the producer does not write explicitly is **zero**, including all padding.
- Records, lists and the unknown table start at multiples of 8; string headers and unknown spans
  at multiples of 4. The slab length is a multiple of 8.

## Layout of a type

`Layout.compile(plan)` lays out the plan's closure: type 0 is the root, then nested message types
breadth-first in field order (the same order as `NativeSchema.types` without an Any registry). A
schema program uses its own type list (`Layout.compile(List)`), so a slab must always be read with
the layout whose program produced it; the header's layout word (below) checks this.

For a type with `n` fields (ordinals `0..n-1`, declaration order) and `o` oneofs:

| Offset | Size | Content |
|---:|---:|---|
| 0 | 4 | `handle` (`u32`) |
| 4 | `4 × W`, `W = ceil(n / 32)` | presence words (`u32`): bit `i mod 32` of word `i / 32` is field `i` |
| `4 + 4W` | `2 × o` | oneof case entries, `u16` each: ordinal + 1 of the set member, 0 if none |
| … | | padding to a multiple of 8 if the type has an 8-byte slot, else to a multiple of 4 |
| … | 8 each | 8-byte slots, in ordinal order |
| … | 4 each | 4-byte slots, in ordinal order |
| | | padding to a multiple of 8 = `recordBytes` |

`o` is `max(oneof index) + 1` over the type's fields (proto3 `optional` fields are synthetic
oneofs and get entries too). Case entries are `u16` holding the member's **ordinal** plus one, so
a oneof member's ordinal must be at most 65,534 (`Layout` rejects larger ones). Records have no
unknown-field word: the slab's unknown table (below) maps the few records that have unknown
fields to their spans. (Task A2 ruling: this record header and the ASCII string header below
replaced the first `TDSLAB1` draft's `u64` presence words, `unknown` word and 12-byte ASCII
headers, taking `Type` message 1 from 536 B to 448 B.)

### Slot kinds

| Kind (code) | Fields | Slot | Value bits |
|---|---|---:|---|
| `I32` (1) | int32, sint32, sfixed32, enum, uint32, fixed32 | 4 | the 32-bit value; signed kinds as two's complement (sint32 zigzag-decoded), unsigned kinds as their bits |
| `I64` (2) | int64, sint64, sfixed64, uint64, fixed64 | 8 | the 64-bit value (sint64 zigzag-decoded; unsigned as bits) |
| `F32` (3) | float | 4 | IEEE-754 single bits |
| `F64` (4) | double | 8 | IEEE-754 double bits |
| `BOOL` (5) | bool | 4 | `0` or `1` (the varint is non-zero) |
| `STRING` (6), `BYTES` (7) | string, bytes | 4 | offset of a string header |
| `MESSAGE` (8) | message, group | 4 | offset of the child record |
| `LIST_I32` … `LIST_MESSAGE` (9–16) | repeated fields | 4 | offset of a list |
| `MAP` (17) | map fields | 4 | offset of a list of entry-record offsets |

`LIST_*` codes are `9 + scalar kind code − 1` in the order `I32, I64, F32, F64, BOOL, STRING,
BYTES, MESSAGE`. List element widths: 4 (`I32`, `F32`, string/bytes header offsets, record
offsets), 8 (`I64`, `F64`), 1 (`BOOL`).

## Slab

```
header (32 B)
  0  magic    u32  'TDS1' = 0x31534454
  4  flags    u32  OWNING 1 · BORROWING 2 · LAX 4 · CERTIFIED 8 · PROJECTED 16
  8  bytes    u32  slab length (a multiple of 8)
 12  records  u32  record allocations, including orphans
 16  handles  u32  handles assigned (records + string/bytes + lazy-child headers)
 20  root     u32  offset of the root record: always 32
 24  layout   u32  Layout.fingerprint() of the producing program
 28  unknowns u32  offset of the unknown table, 0 if no record has unknown fields
32  root record, then every other allocation in decode order (below)
```

Exactly one of `OWNING` and `BORROWING` is set. `OWNING`: no string or bytes header references
input bytes or lazy child spans; the slab needs no source input. `BORROWING`: an external
payload or lazy child was emitted and the reader must retain immutable source input.
`PROJECTED` requires the matching immutable selection for reads. `LAX`: strings inside omitted subtrees were
not validated. `CERTIFIED`: every record's required fields are present and no singular message
was merged; when it is clear, the owner checks required fields from presence before exposing the
slab (producers never reject missing required fields themselves).

**String header** (start aligned to 4):

```
0  length u32   bit 31 ASCII · bit 30 EXTERNAL · bits 0..29 byte length
4  handle u32
8  utf16  u32   only when ASCII is clear: UTF-16 units (strings); 0 for bytes
then           length bytes (owned), or input offset u32 (EXTERNAL)
```

`ASCII` is set exactly when every payload byte is below `0x80`; it is set for the empty string
and never for `bytes`. An ASCII header has no `utf16` word (its UTF-16 length is its byte length),
so its payload starts at 8; every other header's at 12. Both `ASCII` and `utf16` are written only
after strict UTF-8 validation (the same rule as `ProtoCodec`: no overlongs, surrogates or values
above U+10FFFF). An owned header occupies `8 + length` (ASCII) or `12 + length` bytes, an external
one 12 or 16.

**List** (start aligned to 8): `count u32 · 0 u32 · count elements`. Lists exist only for fields
with at least one element (absent lists are slot 0 with the presence bit clear).

**Unknown span** (start aligned to 4): `length u32 · bytes`. Unknown spans are always copied,
also in borrowing slabs.

**Unknown table** (start aligned to 8; header word 28): `count u32 · 0 u32 · count × (record u32,
span u32)`, ascending by record offset (which is handle order). It lists every record that
collected unknown bytes, orphans included; a record without an entry has no unknown fields.
Readers binary-search it (`SlabFormat.unknown`); it exists only when `count > 0`.

## Decoding into a slab

The producer keeps one bump cursor starting at 32. `alloc(size, alignment)` rounds the cursor up
to the alignment (zero padding), returns it and advances it by `size`. **Handles** are a counter
from 0, taken by every record allocation and every string/bytes header, in allocation order; the
root record therefore has handle 0. Allocations that end up unreferenced (*orphans*: cleared
oneof members, overwritten singular values, merged messages' first records, map entries moved to
unknown fields) stay in the slab with their handles; readers never reach them.

`record(T, input)` decodes one message body:

1. `alloc(recordBytes(T), 8)` (zeroed), take a handle, write it to the handle word (offset 0).
2. For each field in wire order (tag, wire type and budget rules exactly as `ProtoCodec`):
   - **Unknown** number, or a wire type that differs from the field's (other than a packed
     run of a packable repeated field): append the raw field bytes, tag through value, to the
     record's unknown bytes.
   - **Packed run** of a repeated packable field: each element in order is appended to the
     field's pending elements, except an out-of-set closed-enum value, which is appended to
     the unknown bytes **re-encoded** as `varint(number << 3) varint(sign-extended value)`.
   - **Scalar** (including string and bytes): read the value. A string or bytes value
     allocates its header now (`alloc(8 + length, 4)` for ASCII strings, else
     `alloc(12 + length, 4)`; handle; payload copy). If the record's
     type is a map entry, the field is ordinal 1 and is an enum, remember whether the value is
     out of set. An out-of-set closed-enum value appends the raw field bytes to the unknown bytes
     and changes nothing else (no oneof selection). Otherwise, repeated: append to pending;
     singular: *select* (oneof), write the slot, and set the presence bit if the field has
     explicit presence, or else iff the value differs from the field's default (`ProtoRow`
     non-default rule: `-0.0` differs from `0.0`; an empty string equals `""`).
   - **Repeated message or map entry**: `record(child)` now. A map entry whose value was an
     out-of-set closed enum is not listed: the raw field bytes go to the unknown bytes and the
     entry record is an orphan. Otherwise its offset is appended to pending.
   - **Singular message or group**: *select*. If the slot is 0, `record(child)` now, write the
     slot, set the presence bit, and remember the payload as the field's first. If the slot is
     already set, this is a **merge**: validate the payload with a full decode walk that writes
     nothing to the slab (same errors as decoding it) and remember it.
   - *select*(member of oneof `k`): clear every other member's presence bit, zero its slot (4 or
     8 bytes) and forget its remembered payloads; write `ordinal + 1` to case entry `k`.
3. **Close** the record, in this order:
   1. **Merges**: for each singular message field (ordinal order) with two or more remembered
      payloads, `record(child, concatenation of the payloads)` (group payloads are their bodies
      without the end tag) and point the slot at it; clear `CERTIFIED`. Protobuf merge semantics
      are exactly concatenation, so later scalars win, repeated fields append and nested messages
      merge.
   2. **Map value default**: a map-entry record whose value field is a message and whose slot is
      0 gets `record(value type, empty input)`; slot and presence bit are set.
   3. **Lists**, for each repeated field with pending elements, in ordinal order:
      `alloc(8 + count × width, 8)`, count, elements in wire order; slot and presence bit are
      set. A **map** list keeps one entry per key — the position of the key's first entry and the
      record of its last — keyed by the entry's field 0 (an absent key is the default key).
   4. **Unknown span**: if the record collected unknown bytes, `alloc(4 + length, 4)` and remember
      `(record, span)` for the unknown table.
   5. **Required fields**: a required field without its presence bit clears `CERTIFIED`.

Groups end at their matching end tag; a mismatched end tag, an unterminated group, depth beyond
the plan's limit and every other `ProtoCodec` error reject the input. After the root closes, if any
record has unknown fields, the producer allocates the unknown table (`alloc(8 + 8 × count, 8)`,
the last allocation); then it writes the header; `bytes` is the cursor rounded up to 8.

Depth: the root record is at depth 0 and a record at depth `d` may contain messages and groups
only while `d < maxDepth` (also unknown groups, which are skipped; and the empty value record a
map entry gets at close). Deeper nesting is a budget error.

The header's `records` and `handles` count orphans. Producers must agree byte for byte, orphans
and padding included; this is what `producers_agree_byte_for_byte` (Task A3) compares.

## Native producer (seam ABI 11)

`protobuf-buffa/native/slab.rs` exposes four `buffa.seam` imports. ABI 11 retires the
earlier native view and scalar-only producers; `TDS1` and `TDP1` remain unchanged:

- `compileLayout(schema, length, result)`: compiles a schema program (`TDB3` + `TDL1`) once into
  an owned handle (`result.data`; `result.count` types). The layout is recomputed from the TDB3
  kinds and flags and the section's oneof indices; a section whose tables, extents or
  fingerprint disagree is `MALFORMED` (1). Oneof membership exists only in the section, so a
  section that differs from Java's only there describes another consistent layout of the same
  tables; programs come from `NativeSchema`, never from input. `releaseLayout(handle)` frees it.
- `decodeSlab(compiled, data, length, flags, projection, out, capacity, maxDepth, maxSlabBytes,
  result)`: `flags` exactly one of `OWNING = 1` or `BORROWING = 8`, optional `LAX = 2`
  and `SIZING_ONLY = 4`. `projection` is null for an ordinary owning decode, or a
  checked TDP1 program as specified below. Lazy children and external bytes require BORROWING;
  LAX must agree with the program. Writes the slab to `out[..capacity]` and returns 0,
  or `NEEDS_CAPACITY` (6) with `result.bytes` the **exact** slab length when it does not fit (or
  under `SIZING_ONLY`); a retry with that capacity succeeds. Allocations that do not fit are not
  written, but the walk continues to the end (records that do not fit are kept in decoder
  frames), so the count is exact and errors are reported exactly as with room. A slab longer
  than `maxSlabBytes` is `BUDGET` (2), never `NEEDS_CAPACITY`, so the caller never allocates for
  it; `maxDepth` above the native bound of 128 and `maxSlabBytes` above `Integer.MAX_VALUE` are
  `BUDGET` up front. `result` (`SlabResult`, 32 B): `bytes u64 · records u32 · handles u32 · flags u32 · error u32 ·
  reserved u64`; on a rejection `error` is the input offset of the innermost field being decoded
  (for invalid UTF-8, of the string's first payload byte, which is where protobuf-java fails:
  strings are validated when read, in wire order). Other statuses: `ALLOCATION` (3), `PANIC` (4;
  no unwind crosses the ABI).
- `decodeSlabSmall(...)`: the same, as a bounded native leaf: `length` and `capacity` above
  64 KiB are `BUDGET` before any work; no heap allocation and no lock. Its scratch (pending list
  elements, merge payloads, unknown bytes, unknown-table entries, frames) is fixed stack storage
  (about 20 KiB); an input that overflows it is `BUDGET`, and the caller retries through
  `decodeSlab`, whose scratch starts in the same stack storage and spills to the heap.

## Schema program section (`TDL1`)

`NativeSchema` appends the layout of its type list to the `TDB3` schema program, after the
last TDB3 byte (enum tables and strings), at the next multiple of 4 (zero padding).
Ordinary typed-index readers reach data through TDB3 offsets; the slab compiler reads and
validates the appended layout section. No retired view decoder is required.

```
section
  0  magic        u32  'TDL1' = 0x314C4454
  4  version      u32  1
  8  types        u32  same count and order as TDB3
 12  fingerprint  u32  CRC-32 (java.util.zip.CRC32) of the bytes from offset 16 to the end
                       of the type tables
 16  per type:    recordBytes u32 · presenceWords u32 · oneofs u32 · fields u32
                  then per field: slot u32 · kind code u32 · oneof index u32 (0xFFFFFFFF none)
trailer (last 8 bytes of the program)
     section offset u32 · 'TDL1' u32
```

Child type indices are not repeated here: they are the TDB3 field entries' child words, which
use the same type order. A producer recomputes the layout from the TDB3 tables and rejects a
program whose recomputation differs from the section (Task A2, `MALFORMED`). The slab header's
`layout` word is the section's fingerprint.

## Golden slabs

`protobuf-runtime/src/test/resources/slab/<name>.wire` is an input and `<name>.bin` its owning
slab. Schemas are the `LayoutTest` factories of the same name (`timestamp`, `googleType` for
`type`, `maps` for `map`, `choice` for `oneof`, `unknowns`, `merge`, `lists`, `texts` for
`strings`, `wide`, `legacy`); the root type is index 0 of `plan.layout()`.

| Golden | Shows |
|---|---|
| `timestamp` | the smallest record: implicit presence, one 8-byte and one 4-byte slot |
| `type` | `google.protobuf.Type` message 1 of the shared consumer fixture (122 B → 448 B) |
| `map` | first-key order with the last entry per key; an entry without a value gets an empty value record |
| `oneof` | two oneofs, last member wins, cleared slots zeroed, an orphaned string |
| `unknown` | unknown fields in wire order, an out-of-set closed enum, a packed out-of-set element re-encoded |
| `merge` | a singular message twice: the first record is orphaned, the concatenation is decoded at close, `CERTIFIED` clear |
| `lists` | packed and unpacked occurrences of one field merged into one list; 1-, 4- and 8-byte elements |
| `strings` | ASCII, Latin-1, CJK and supplementary strings (ASCII bit, UTF-16 units), empty strings, bytes |
| `wide` | 130 fields: three presence words |
| `legacy` | proto2: required fields, a group, recursion, float NaN, uint64 above `Long.MAX_VALUE` |

`LayoutTest.golden_slabs_decode_by_hand` holds the timestamp, map, oneof, unknown and merge slabs
as hand-written words, compares every golden with the reference model, and reads every golden
back field by field against `ProtoRow`.

### Generated decoder selection

`ContractPlugin.create` supplies immutable per-type decoder factories in the generated `ProtoPlan.Model`. The first slab decode binds that type's generated decoder once; subsequent `SlabMessage.decode` and Buffa Java-backend calls reuse the binding. Runtime-created models retain the table producer. Generated fast loops cover ordinary scalar, oneof, repeated and nested-message cases and restart through the table producer for unknown fields, maps, groups and singular-message merges. Both paths write the same format and preserve byte parity. A bounded 64-entry atomic hint table retains wire-length/slab-extent pairs, avoiding repeated growth for varied input sizes. Each candidate capacity is checked against the current limits before allocation; collisions or concurrent updates affect capacity estimates only. Input and output arrays are never cached, and every returned slab owns fresh storage.

Generated lazy projections have a separate checked loop and capacity-hint table. They require
`lazyChildren()` without byte borrowing; omitted children use a validation-only scan before
publishing input-relative spans. Required descendants, maps, groups, oneofs, unknown fields and
singular-message merges restart the table producer when encountered. Required descendant
eligibility comes from the complete type closure, including optional descendants absent from
the wire. Runtime-created models and custom decoder lambdas retain the table fallback. These
routes share strict/lax validation, depth limits, flags and slab-budget behavior.

### Native arena borrows

`SlabSession` retains a reusable native buffer containing the same owning slab format. Its rows carry the session's decode epoch, and every value read checks that epoch and thread before loading native memory. No assumption object is allocated per decode. A new decode or reset invalidates previous rows, including compiled constant receivers. A pin prevents buffer recycling until released. Root retain copies the valid extent once into an owning slab; child snapshots additionally verify that the requested record/type is reachable in the copied graph. Arbitrary offsets and overwritten orphan records cannot be selected.


## Parse-time projections and deferred payloads

`Projection.builder(plan).select("id").selectType("Child", "name").build()` creates an
immutable wanted mask for the closed layout. Map-entry key/value fields are selected for
structural map handling. `BuffaPlan.project(selection)` creates an independently closeable
plan; its native mask is compiled once in the reusable projected session. Runtime-only
Java plans use the equivalent `SlabCodec` producer. Ordinary decode keeps its null-mask path.

Presence remains visible even for omitted values. Repeated fields keep their cardinality;
reading an omitted element or scalar throws `ContractViolation`. An unselected message list
without lazy children uses an aligned 8-byte `[count, 1]` header and no element slots.
Ordinary omitted strings and message subtrees avoid payload allocation. Fixed scalar slots
and repeated scalars remain physically stored in this implementation, though the read mask
still rejects their values. Groups, maps, oneofs, singular merges and child closures with
required fields conservatively stay eager. Reachable required fields are validated before
publication, including those in eager omitted children.

`lazyChildren()` preserves omitted child wire spans. Each tagged child pointer has its high
bit set; the remaining bits address an aligned 16-byte header containing `[inputOffset,
inputLength, handle, absoluteDepth]`. The first read decodes the complete child strictly,
caches it, and charges additional logical cells, string bytes and retained slab capacity.
Absolute depth constrains the remaining decode budget. Eager fallbacks for an omitted lazy
child also decode its complete subtree, so lazy reads have the same field availability.
`lax()` skips only UTF-8 validation in omitted subtrees; wire structure, numeric and depth
checks still run. Reading a lazy child revalidates UTF-8 strictly, including after a previous
failed read. Omitted scalar text has no readable payload, even in lax mode.

`borrowBytes(threshold)` references selected bytes payloads at or above the threshold (default
4096). Their aligned 16-byte header is `[length | EXTERNAL, handle, 0, inputOffset]`.
Strings retain ordinary validated headers. Offset and length always address the supplied
input window. TDP1 is little endian and 4-byte aligned: six words `[magic 0x31504454,
byteLength, layoutFingerprint, flags, typeCount, borrowBytes]`, followed in closed type order
by a field-count word and `ceil(fieldCount/32)` wanted words per type. Flags are LAX=1,
LAZY_CHILDREN=2 and BORROW_BYTES=4; unused tail bits must be zero and byteLength exact.
These program bits differ from the decode and result flags.

Owning projected messages retain a defensive source-input copy only when the produced slab
contains external references. `retainedInputBytes()` reports that separate capacity;
`retainedSlabBytes()` includes materialized lazy children. `logicalUsage()` reports budgeted
cells/string bytes without materializing text or child wrappers. External projected imports
use `copyProjectedOf` or `copyProjectedRecordOf`; plain `copyOf` and `takeOwnership` reject
projected slabs without their mask and input metadata. All imports check external spans,
count-only markers, lazy depth and handle identities before exposing a row. Partial projections
cannot be re-encoded; an all-field projection supports ordinary encoding, including external
bytes. Closing an owning projected message remains a no-op.

Native sessions retain an immutable source lease only while the current slab references input.
Closing the caller's input handle is safe. Reset, next decode, failed decode or close releases
that lease and invalidates lazy materializations and their nested row/array wrappers. Explicit
root/child/array snapshots copy or own every retained dependency and survive session closure.

An immediate owning decode through a native projected session copies and validates the final
owner before returning. It does not first publish or validate an intermediate borrowed row.
Public session decoding still checks required fields and logical budgets before returning a
borrow; failures in either route release the retained source and invalidate the attempted epoch.
