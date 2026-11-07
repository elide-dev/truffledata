# Protobuf slabs (format `TDSLAB1`)

A slab is the fixed-layout decode result of one protobuf message: one little-endian byte array in
which every message instance is a **record** whose fields sit at constant offsets computed from the
schema. A field read is a presence-bit test and a load at `record + slot`. This document is the
byte-exact contract that every producer (the Rust decoder, the generated and table-driven Java
decoders) and every reader follows. The architecture and the rationale are in
`docs/superpowers/plans/2026-11-07-protobuf-performance.md` §2.

Sources: `protobuf-runtime/.../Layout.java` (layout compiler and the `TDL1` program),
`SlabFormat.java` (constants and accessors), and the executable model of this document,
`protobuf-runtime/src/test/.../SlabReference.java`, which the golden slabs in
`protobuf-runtime/src/test/resources/slab/` are checked against.

## Conventions

- All integers are little-endian. `u16`/`u32`/`u64` are unsigned unless stated; slot values keep
  the bits of their protobuf type (below).
- An **offset** is a `u32` byte offset from the start of the slab. Offset 0 is the header, so
  `0` in a reference slot means *absent*. Presence bits are authoritative: an implicit-presence
  string or bytes field that holds its default keeps the offset of its header with the bit clear.
- Every byte the producer does not write explicitly is **zero**, including all padding.
- Records and lists start at multiples of 8; string headers and unknown spans at multiples of 4.
  The slab length is a multiple of 8.

## Layout of a type

`Layout.compile(plan)` lays out the plan's closure: type 0 is the root, then nested message types
breadth-first in field order (the same order as `NativeSchema.types` without an Any registry). A
schema program uses its own type list (`Layout.compile(List)`), so a slab must always be read with
the layout whose program produced it; the header's layout word (below) checks this.

For a type with `n` fields (ordinals `0..n-1`, declaration order) and `o` oneofs:

| Offset | Size | Content |
|---:|---:|---|
| 0 | `8 × W`, `W = ceil(n / 64)` | presence words: bit `i mod 64` of word `i / 64` is field `i` |
| `8W` | 4 | `handle` (`u32`) |
| `8W + 4` | 4 | `unknown`: offset of the record's unknown span, 0 if none |
| `8W + 8` | `align8(2 × o)` | oneof case entries, `u16` each: ordinal + 1 of the set member, 0 if none |
| … | 8 each | 8-byte slots, in ordinal order |
| … | 4 each | 4-byte slots, in ordinal order |
| | | padding to a multiple of 8 = `recordBytes` |

`o` is `max(oneof index) + 1` over the type's fields (proto3 `optional` fields are synthetic
oneofs and get entries too). Case entries are `u16` holding the member's **ordinal** plus one, so
a oneof member's ordinal must be at most 65,534 (`Layout` rejects larger ones). The case area is
padded to 8 so that 8-byte slots stay aligned.

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
  4  flags    u32  OWNING 1 · BORROWING 2 · LAX 4 · CERTIFIED 8
  8  bytes    u32  slab length (a multiple of 8)
 12  records  u32  record allocations, including orphans
 16  handles  u32  handles assigned (records + string/bytes headers)
 20  root     u32  offset of the root record: always 32
 24  layout   u32  Layout.fingerprint() of the producing program
 28  reserved u32  0
32  root record, then every other allocation in decode order (below)
```

Exactly one of `OWNING` and `BORROWING` is set. `OWNING`: no string or bytes header references
input bytes; the slab depends on nothing. `BORROWING`: at least one header is `EXTERNAL` and the
reader must hold an input lease. `LAX` (Phase E): strings inside projected-away subtrees were
not validated. `CERTIFIED`: every record's required fields are present and no singular message
was merged; when it is clear, the owner checks required fields from presence before exposing the
slab (producers never reject missing required fields themselves).

**String header** (start aligned to 4):

```
0  length u32   bit 31 ASCII · bit 30 EXTERNAL · bits 0..29 byte length
4  utf16  u32   UTF-16 units (strings); 0 for bytes
8  handle u32
12 payload      length bytes (owned), or input offset u32 (EXTERNAL)
```

`ASCII` is set exactly when every payload byte is below `0x80`; it is set for the empty string
and never for `bytes`. Both `ASCII` and `utf16` are written only after strict UTF-8 validation
(the same rule as `ProtoCodec`: no overlongs, surrogates or values above U+10FFFF). An owned
header occupies `12 + length` bytes, an external one 16.

**List** (start aligned to 8): `count u32 · 0 u32 · count elements`. Lists exist only for fields
with at least one element (absent lists are slot 0 with the presence bit clear).

**Unknown span** (start aligned to 4): `length u32 · bytes`. Unknown spans are always copied,
also in borrowing slabs.

## Decoding into a slab

The producer keeps one bump cursor starting at 32. `alloc(size, alignment)` rounds the cursor up
to the alignment (zero padding), returns it and advances it by `size`. **Handles** are a counter
from 0, taken by every record allocation and every string/bytes header, in allocation order; the
root record therefore has handle 0. Allocations that end up unreferenced (*orphans*: cleared
oneof members, overwritten singular values, merged messages' first records, map entries moved to
unknown fields) stay in the slab with their handles; readers never reach them.

`record(T, input)` decodes one message body:

1. `alloc(recordBytes(T), 8)`, take a handle, write it to the handle word.
2. For each field in wire order (tag, wire type and budget rules exactly as `ProtoCodec`):
   - **Unknown** number, or a wire type that differs from the field's (other than a packed
     run of a packable repeated field): append the raw field bytes, tag through value, to the
     record's unknown bytes.
   - **Packed run** of a repeated packable field: each element in order is appended to the
     field's pending elements, except an out-of-set closed-enum value, which is appended to
     the unknown bytes **re-encoded** as `varint(number << 3) varint(sign-extended value)`.
   - **Scalar** (including string and bytes): read the value. A string or bytes value
     allocates its header now (`alloc(12 + length, 4)`, handle, payload copy). If the record's
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
   4. **Unknown span**: if the record collected unknown bytes, `alloc(4 + length, 4)` and point the
      unknown word at it.
   5. **Required fields**: a required field without its presence bit clears `CERTIFIED`.

Groups end at their matching end tag; a mismatched end tag, an unterminated group, depth beyond
the plan's limit and every other `ProtoCodec` error reject the input. After the root closes the
producer writes the header; `bytes` is the cursor rounded up to 8.

The header's `records` and `handles` count orphans. Producers must agree byte for byte, orphans
and padding included; this is what `producers_agree_byte_for_byte` (Task A3) compares.

## Schema program section (`TDL1`)

`NativeSchema` appends the layout of its type list to the ABI 9 `TDB3` schema program, after the
last TDB3 byte (enum tables and strings), at the next multiple of 4 (zero padding). TDB3 readers
reach data only through header offsets and never look past it, so the ABI 9 library is unaffected
(`appended_layout_section_is_invisible_to_abi9` in `typed_view_tests.rs`).

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
| `type` | `google.protobuf.Type` message 1 of the shared consumer fixture (122 B → 536 B) |
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
