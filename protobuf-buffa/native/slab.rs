//! Seam ABI 11: protobuf decoded into layout-compiled slabs (format `TDSLAB1`, docs/SLAB.md).
//!
//! A schema program (`TDB3` tables plus the `TDL1` layout section that Java appends) compiles
//! once into checked descriptors; the layout is recomputed here and a program whose section
//! disagrees is rejected. Decoding is one table-driven pass that writes the slab into a
//! caller-provided buffer with a bump cursor, in exactly the placement order docs/SLAB.md fixes,
//! so that this producer and the Java producers agree byte for byte.
//!
//! The pass is one loop over an explicit record stack (`Decoder::run`; the stack index is the
//! depth), not recursion: the read position and body end (pointers), the record base, its type
//! and presence word 0 stay in locals, one-byte tags dispatch through a per-type 128-entry table
//! straight to a 16-byte
//! `Entry` (slot, child, handler), and plain scalars, strings, short ASCII strings and messages
//! are handled inline. Everything rare (repeated scalars, oneofs, closed enums, map keys,
//! groups, packed runs, unknown fields, merges, record close with lists) is out of line.
//!
//! Capacity: allocations that do not fit the caller's buffer are not written, but the walk goes
//! on (records that do not fit are kept in decoder frames, because later fields read their
//! presence and slots), so `NEEDS_CAPACITY` reports the exact slab length. Scratch (pending list
//! elements, merge payloads, unknown bytes, frames) starts in fixed stack arrays; the normal entry
//! point spills to the heap when they overflow, the small entry point returns `BUDGET` instead and
//! never allocates.
use crate::unicode::validate_utf16_units;
use crate::{ALLOCATION, BUDGET, MALFORMED};
use std::mem::MaybeUninit;

type Result<T> = std::result::Result<T, i32>;

/// The slab did not fit `capacity`; `result.bytes` is the exact length needed.
pub const NEEDS_CAPACITY: i32 = 6;

/// Decode flags (`td_buffa_decode_slab`).
pub const FLAG_OWNING: u32 = 1;
/// Opt-in projection validation that defers UTF-8 checks for omitted/lazy subtrees.
pub const FLAG_LAX: u32 = 2;
/// Walk and size only: nothing is written and the result is `NEEDS_CAPACITY` (or an error).
pub const FLAG_SIZING_ONLY: u32 = 4;
/// The caller retains the input for external bytes and lazy child payloads.
pub const FLAG_BORROWING: u32 = 8;

/// Slab header flags (docs/SLAB.md).
pub const SLAB_OWNING: u32 = 1;
pub const SLAB_CERTIFIED: u32 = 8;
pub const SLAB_BORROWING: u32 = 2;
pub const SLAB_LAX: u32 = 4;
pub const SLAB_PROJECTED: u32 = 16;
pub const LAZY_CHILD: u32 = 1 << 31;
const EXTERNAL: u32 = 1 << 30;
const COUNTS_ONLY: u32 = 1;

pub const SLAB_MAGIC: u32 = 0x3153_4454;
const TDB3: u32 = 0x5444_4233;
const TDL1: u32 = 0x314C_4454;
const COMPILED_MAGIC: u64 = 0x3031_4241_4C53_4454; // "TDSLAB10"
pub const HEADER_BYTES: usize = 32;
const ASCII: u32 = 1 << 31;
const MAX_LENGTH: usize = (1 << 30) - 1;
const DIRECT_LOOKUP: u32 = 2048;
const NONE: u32 = u32::MAX;
/// Deepest nesting accepted (the ABI 9 bound).
pub const MAX_DEPTH: u32 = 128;
/// `td_buffa_decode_slab_small` bound on input length and output capacity.
pub const SMALL_LIMIT: u64 = 65536;

// Slot kind codes (Layout.SlotKind ordinal + 1).
const K_I32: u32 = 1;
const K_I64: u32 = 2;
const K_F32: u32 = 3;
const K_F64: u32 = 4;
const K_BOOL: u32 = 5;
const K_STRING: u32 = 6;
const K_BYTES: u32 = 7;
const K_MESSAGE: u32 = 8;
const K_LIST: u32 = 9;
const K_MAP: u32 = 17;

// Field operations: how one wire value is read.
const OP_VARINT32: u8 = 0; // int32, uint32, enum: the low 32 bits
const OP_VARINT64: u8 = 1;
const OP_SINT32: u8 = 2;
const OP_SINT64: u8 = 3;
const OP_BOOL: u8 = 4;
const OP_FIXED32: u8 = 5;
const OP_FIXED64: u8 = 6;
const OP_STRING: u8 = 7;
const OP_BYTES: u8 = 8;
const OP_MESSAGE: u8 = 9;
const OP_GROUP: u8 = 10;

enum Values {
    Any,
    Range(i32, i32),
    /// Sorted and deduplicated (a Vec: shrinking it to a box could fail without a status).
    Sorted(Vec<i32>),
}

impl Values {
    #[inline]
    fn accepts(&self, value: i32) -> bool {
        match self {
            Values::Any => true,
            Values::Range(low, high) => *low <= value && value <= *high,
            Values::Sorted(values) => values.binary_search(&value).is_ok(),
        }
    }
}

pub(crate) struct FieldDesc {
    /// `number << 3 | wire type` of the declared (unpacked) encoding.
    tag: u32,
    slot: u32,
    child: u32,
    oneof: u32,
    pub(crate) kind: u32,
    op: u8,
    /// Slot width (scalars) or list element width (repeated).
    width: u8,
    repeated: bool,
    packable: bool,
    explicit: bool,
    map: bool,
    /// Index of a repeated field among its type's list fields (`TypeDesc::lists`), else NONE.
    list: u32,
    /// Not a plain singular value: repeated, a oneof member, a closed enum, or a map entry's key
    /// or enum value (the slow path of `body`).
    special: bool,
    /// Ordinal 1 of a map entry with an enum value: its out-of-set values drop the entry.
    map_value_enum: bool,
    closed: bool,
    values: Values,
}

// Hot handlers (`Entry::op`): the decode loop handles these inline.
const H_V32: u8 = 0; // int32, uint32, open enum: the low 32 bits of a varint
const H_V64: u8 = 1;
const H_S32: u8 = 2;
const H_S64: u8 = 3;
const H_BOOL: u8 = 4;
const H_F32: u8 = 5; // fixed32, sfixed32, float
const H_F64: u8 = 6; // fixed64, sfixed64, double
const H_STR: u8 = 7;
const H_BYTES: u8 = 8;
/// Singular, non-oneof, length-delimited message.
const H_MSG: u8 = 9;
/// Repeated length-delimited message or map entry.
const H_RMSG: u8 = 10;
/// Everything else goes through `Decoder::slow`.
const H_SLOW: u8 = 11;

// `Entry::flags`.
const E_EXPLICIT: u8 = 1;
const E_REPEATED: u8 = 2;
const E_MAP: u8 = 4;

/// The decode loop's per-field descriptor (16 bytes, indexed by ordinal like `fields`): the
/// declared tag the wire-order prediction compares, the slot, the child type and a handler.
#[derive(Clone, Copy)]
#[repr(C)]
pub(crate) struct Entry {
    tag: u32,
    slot: u32,
    child: u32,
    op: u8,
    flags: u8,
    /// Presence word (`ordinal / 32`); word 0 lives in a register while the record is on top.
    word: u16,
}

pub(crate) struct TypeDesc {
    pub(crate) fields: Box<[FieldDesc]>,
    entries: Box<[Entry]>,
    /// One-byte tags (field numbers 1..=15 with their declared wire type) to ordinal + 1; 0 for
    /// anything else (unknown numbers, other wire types, packed runs, end tags).
    dispatch: [u16; 128],
    /// Records of this type always run the record close (a map value default or required
    /// fields to check).
    closes: bool,
    /// Neither `closes` nor a map entry: a record with no lists, unknown fields or merges
    /// closes inline in the decode loop.
    simple: bool,
    has_map: bool,
    /// Ordinals of the repeated and map fields, ascending.
    lists: Box<[u32]>,
    lookup: Box<[u16]>,
    pub(crate) record_bytes: u32,
    pub(crate) words: u32,
    members: Box<[Box<[u32]>]>,
    required: Box<[u32]>,
    map_entry: bool,
    value_message: bool,
    required_closure: bool,
}

impl TypeDesc {
    /// A direct table covers every declared number when all are small; otherwise first match.
    #[inline]
    fn find(&self, number: u32) -> Option<usize> {
        if self.lookup.is_empty() {
            return self.fields.iter().position(|f| f.tag >> 3 == number);
        }
        match self.lookup.get(number as usize) {
            Some(&ordinal) if ordinal != 0 => Some(ordinal as usize - 1),
            _ => None,
        }
    }
}

/// Immutable compiled layout, owned by a Java plan through an explicit handle.
pub struct Compiled {
    magic: u64,
    pub(crate) types: Box<[TypeDesc]>,
    pub(crate) fingerprint: u32,
    max_record: u32,
}

impl Compiled {
    pub fn checked(&self) -> Result<&Self> {
        if self.magic == COMPILED_MAGIC {
            Ok(self)
        } else {
            Err(MALFORMED)
        }
    }
}

/// A validated, borrowed TDP1 mask. No allocation is needed on the critical decode path.
/// Header words: magic, byte length, layout fingerprint, flags, type count, borrow threshold.
/// Each type contributes field count followed by ceil(field count/32) wanted-bit words.
#[derive(Clone, Copy)]
pub struct Projection<'a> {
    bytes: &'a [u8],
    pub flags: u32,
    pub borrow_bytes: u32,
}

impl<'a> Projection<'a> {
    pub const MAGIC: u32 = 0x3150_4454;
    pub const LAX: u32 = 1;
    pub const LAZY: u32 = 2;
    pub const BORROW_BYTES: u32 = 4;

    pub fn parse(c: &Compiled, bytes: &'a [u8]) -> Result<Self> {
        let words = Words(bytes);
        if words.word(0)? != Self::MAGIC || words.word(4)? as usize != bytes.len()
            || words.word(8)? != c.fingerprint || words.word(16)? as usize != c.types.len() {
            return Err(MALFORMED);
        }
        let flags = words.word(12)?;
        let borrow_bytes = words.word(20)?;
        if flags & !(Self::LAX | Self::LAZY | Self::BORROW_BYTES) != 0
            || (flags & Self::BORROW_BYTES != 0) != (borrow_bytes != 0) {
            return Err(MALFORMED);
        }
        let mut at = 24usize;
        for t in &c.types {
            let count = t.fields.len();
            if words.word(at)? as usize != count { return Err(MALFORMED); }
            at += 4;
            let n = count.div_ceil(32);
            if n != 0 {
                let last = words.word(at + 4 * (n - 1))?;
                if count & 31 != 0 && last >> (count & 31) != 0 { return Err(MALFORMED); }
            }
            at = at.checked_add(4 * n).ok_or(MALFORMED)?;
        }
        if at != bytes.len() { return Err(MALFORMED); }
        Ok(Self { bytes, flags, borrow_bytes })
    }

    /// The C contract requires an aligned readable header and its declared complete buffer.
    /// The size must equal the compiled layout's independently derived mask extent.
    pub unsafe fn from_raw(c: &Compiled, data: *const u8) -> Result<Self> {
        if data.is_null() || data as usize & 3 != 0 { return Err(MALFORMED); }
        let header = unsafe { std::slice::from_raw_parts(data, 24) };
        let length = Words(header).word(4)? as usize;
        let expected = c.types.iter().try_fold(24usize, |sum, t| sum.checked_add(4 + 4 * t.fields.len().div_ceil(32))).ok_or(MALFORMED)?;
        if length != expected { return Err(MALFORMED); }
        Self::parse(c, unsafe { std::slice::from_raw_parts(data, length) })
    }

    #[inline]
    fn wanted(self, ty: u32, ordinal: usize) -> bool {
        let mut at = 24usize;
        for _ in 0..ty {
            let fields = read32(unsafe { self.bytes.as_ptr().add(at) }) as usize;
            at += 4 + 4 * fields.div_ceil(32);
        }
        read32(unsafe { self.bytes.as_ptr().add(at + 4 + 4 * (ordinal >> 5)) }) & (1 << (ordinal & 31)) != 0
    }
}

// ---- Schema program ----

#[derive(Clone, Copy)]
struct Words<'a>(&'a [u8]);

impl Words<'_> {
    fn word(self, at: usize) -> Result<u32> {
        let end = at.checked_add(4).ok_or(MALFORMED)?;
        Ok(u32::from_le_bytes(
            self.0.get(at..end).ok_or(MALFORMED)?.try_into().unwrap(),
        ))
    }
}

/// CRC-32 (IEEE 802.3, reflected; `java.util.zip.CRC32`).
pub(crate) fn crc32(bytes: &[u8]) -> u32 {
    const TABLE: [u32; 256] = {
        let mut table = [0u32; 256];
        let mut i = 0;
        while i < 256 {
            let mut c = i as u32;
            let mut k = 0;
            while k < 8 {
                c = if c & 1 != 0 { 0xEDB8_8320 ^ (c >> 1) } else { c >> 1 };
                k += 1;
            }
            table[i] = c;
            i += 1;
        }
        table
    };
    let mut crc = !0u32;
    for &b in bytes {
        crc = TABLE[((crc ^ b as u32) & 0xFF) as usize] ^ (crc >> 8);
    }
    !crc
}

/// Slot kind of a TDB3 field (kind = ProtoPlan.Kind ordinal + 1; flags bit 0 repeated, 1 map).
fn slot_kind(kind: u32, repeated: bool, map: bool) -> Result<u32> {
    let scalar = match kind {
        5 | 13 | 17 | 14 | 7 | 15 => K_I32,
        3 | 4 | 18 | 6 | 16 => K_I64,
        2 => K_F32,
        1 => K_F64,
        8 => K_BOOL,
        9 => K_STRING,
        12 => K_BYTES,
        10 | 11 => K_MESSAGE,
        _ => return Err(MALFORMED),
    };
    Ok(if map {
        K_MAP
    } else if repeated {
        scalar + K_LIST - 1
    } else {
        scalar
    })
}

fn op(kind: u32) -> Result<(u8, u32)> {
    // (operation, wire type)
    Ok(match kind {
        5 | 13 | 14 => (OP_VARINT32, 0),
        3 | 4 => (OP_VARINT64, 0),
        17 => (OP_SINT32, 0),
        18 => (OP_SINT64, 0),
        8 => (OP_BOOL, 0),
        2 | 7 | 15 => (OP_FIXED32, 5),
        1 | 6 | 16 => (OP_FIXED64, 1),
        9 => (OP_STRING, 2),
        12 => (OP_BYTES, 2),
        11 => (OP_MESSAGE, 2),
        10 => (OP_GROUP, 3),
        _ => return Err(MALFORMED),
    })
}

fn width(slot_kind: u32) -> u32 {
    match slot_kind {
        K_I64 | K_F64 => 8,
        _ => 4,
    }
}

fn element_width(slot_kind: u32) -> u32 {
    match slot_kind {
        k if k == K_I64 + K_LIST - 1 || k == K_F64 + K_LIST - 1 => 8,
        k if k == K_BOOL + K_LIST - 1 => 1,
        _ => 4,
    }
}

fn align(value: u64, to: u64) -> u64 {
    (value + to - 1) & !(to - 1)
}

/// Compile a schema program: TDB3 tables plus the trailing TDL1 section (docs/SLAB.md). The
/// layout is recomputed from the TDB3 kinds and flags and the section's oneof indices; any
/// difference from the section, a bad fingerprint or a bad extent is `MALFORMED`.
pub fn compile(bytes: &[u8]) -> Result<Compiled> {
    let p = Words(bytes);
    if p.word(0)? != TDB3 {
        return Err(MALFORMED);
    }
    let count = p.word(4)?;
    if count == 0 || bytes.len() < 8 {
        return Err(MALFORMED);
    }
    // Trailer: [section offset][TDL1] as the program's last 8 bytes.
    let trailer = bytes.len() - 8;
    if p.word(trailer + 4)? != TDL1 {
        return Err(MALFORMED);
    }
    let section = p.word(trailer)? as usize;
    if section % 4 != 0 || section.checked_add(16).is_none_or(|end| end > trailer) {
        return Err(MALFORMED);
    }
    if p.word(section)? != TDL1 || p.word(section + 4)? != 1 || p.word(section + 8)? != count {
        return Err(MALFORMED);
    }
    let fingerprint = p.word(section + 12)?;
    let mut types = Vec::new();
    types
        .try_reserve_exact(count as usize)
        .map_err(|_| ALLOCATION)?;
    let mut at = section + 16;
    let mut max_record = 0u32;
    for ty in 0..count as usize {
        let base = p.word(8 + 4 * ty)? as usize;
        let size = p.word(base)?;
        let type_flags = p.word(base + 4)?;
        let (record_bytes, words, cases, fields) = (
            p.word(at)?,
            p.word(at + 4)?,
            p.word(at + 8)?,
            p.word(at + 12)?,
        );
        at += 16;
        if fields != size {
            return Err(MALFORMED);
        }
        let n = size as usize;
        // Every field entry and table entry must lie inside the program.
        if base
            .checked_add(16)
            .and_then(|b| b.checked_add(n.checked_mul(40)?))
            .is_none_or(|end| end > section)
            || at
                .checked_add(n.checked_mul(12).ok_or(MALFORMED)?)
                .is_none_or(|end| end > trailer)
        {
            return Err(MALFORMED);
        }
        let mut descs = Vec::new();
        descs.try_reserve_exact(n).map_err(|_| ALLOCATION)?;
        let mut oneofs = 0u32;
        let mut wide = false;
        let mut required = Vec::new();
        {
            let mut count = 0;
            for ordinal in 0..n {
                count += usize::from(p.word(base + 16 + 40 * ordinal + 8)? & 8 != 0);
            }
            required.try_reserve_exact(count).map_err(|_| ALLOCATION)?;
        }
        for ordinal in 0..n {
            let f = base + 16 + 40 * ordinal;
            let (number, kind, flags, child) =
                (p.word(f)?, p.word(f + 4)?, p.word(f + 8)?, p.word(f + 12)?);
            let repeated = flags & 1 != 0;
            let map = flags & 2 != 0;
            let explicit = flags & 4 != 0;
            let is_required = flags & 8 != 0;
            let sk = slot_kind(kind, repeated, map)?;
            let (operation, wire) = op(kind)?;
            if number == 0 || number > (u32::MAX >> 3) || (map && (!repeated || kind != 11)) {
                return Err(MALFORMED);
            }
            let (slot, code, oneof) = (
                p.word(at + 12 * ordinal)?,
                p.word(at + 12 * ordinal + 4)?,
                p.word(at + 12 * ordinal + 8)?,
            );
            if code != sk {
                return Err(MALFORMED);
            }
            if oneof != NONE {
                if repeated || ordinal > 0xFFFE {
                    return Err(MALFORMED);
                }
                oneofs = oneofs.max(oneof.checked_add(1).ok_or(MALFORMED)?);
            }
            let child = if kind == 10 || kind == 11 {
                if child >= count {
                    return Err(MALFORMED);
                }
                child
            } else {
                NONE
            };
            let values = if kind == 14 && flags & 16 == 0 {
                let table = p.word(f + 16)? as usize;
                let size = p.word(f + 20)? as usize;
                let mut values = Vec::new();
                values.try_reserve_exact(size).map_err(|_| ALLOCATION)?;
                for i in 0..size {
                    let entry = table
                        .checked_add(i.checked_mul(12).ok_or(MALFORMED)?)
                        .ok_or(MALFORMED)?;
                    values.push(p.word(entry)? as i32);
                }
                values.sort_unstable();
                values.dedup();
                match (values.first(), values.last()) {
                    (Some(&low), Some(&high))
                        if (high as i64 - low as i64) as usize + 1 == values.len() =>
                    {
                        Values::Range(low, high)
                    }
                    _ => Values::Sorted(values),
                }
            } else {
                Values::Any
            };
            if !repeated && width(sk) == 8 {
                wide = true;
            }
            if is_required {
                required.push(ordinal as u32);
            }
            descs.push(FieldDesc {
                tag: number << 3 | wire,
                slot,
                child,
                oneof,
                kind,
                op: operation,
                width: if repeated {
                    element_width(sk) as u8
                } else {
                    width(sk) as u8
                },
                repeated,
                list: NONE,
                special: repeated
                    || oneof != NONE
                    || (kind == 14 && flags & 16 == 0)
                    || (type_flags & 1 != 0 && (ordinal == 0 || kind == 14)),
                packable: repeated && wire != 2 && wire != 3,
                explicit,
                map,
                map_value_enum: type_flags & 1 != 0 && ordinal == 1 && kind == 14,
                closed: kind == 14 && flags & 16 == 0,
                values,
            });
        }
        at += 12 * n;
        // Recompute the layout (docs/SLAB.md "Layout of a type") and compare with the section.
        let expected_words = size.div_ceil(32);
        let mut cursor = 4 + 4 * expected_words as u64 + 2 * oneofs as u64;
        cursor = align(cursor, if wide { 8 } else { 4 });
        for pass in [8u64, 4] {
            for f in descs.iter() {
                let w = if f.repeated || f.map { 4 } else { f.width as u64 };
                if w == pass {
                    if f.slot as u64 != cursor {
                        return Err(MALFORMED);
                    }
                    cursor += pass;
                }
            }
        }
        let expected_record = align(cursor, 8);
        if expected_record > i32::MAX as u64
            || record_bytes as u64 != expected_record
            || words != expected_words
            || cases != oneofs
        {
            return Err(MALFORMED);
        }
        max_record = max_record.max(record_bytes);
        let mut members: Vec<Box<[u32]>> = Vec::new();
        members
            .try_reserve_exact(oneofs as usize)
            .map_err(|_| ALLOCATION)?;
        for k in 0..oneofs {
            let mut list = Vec::new();
            let count = descs.iter().filter(|f| f.oneof == k).count();
            list.try_reserve_exact(count).map_err(|_| ALLOCATION)?;
            for (ordinal, f) in descs.iter().enumerate() {
                if f.oneof == k {
                    list.push(ordinal as u32);
                }
            }
            members.push(list.into_boxed_slice());
        }
        let map_entry = type_flags & 1 != 0;
        if map_entry
            && (n != 2 || descs[0].repeated || descs[1].repeated || descs[0].kind == 10 || descs[0].kind == 11)
        {
            return Err(MALFORMED);
        }
        // Field numbers are unique (ProtoPlan rejects duplicates; wire-order prediction relies
        // on it). A direct table covers every number when all are small.
        let largest = descs.iter().map(|f| f.tag >> 3).max().unwrap_or(0);
        let mut lookup = Vec::new();
        if largest <= DIRECT_LOOKUP && n < u16::MAX as usize {
            lookup
                .try_reserve_exact(largest as usize + 1)
                .map_err(|_| ALLOCATION)?;
            lookup.resize(largest as usize + 1, 0u16);
            for (ordinal, f) in descs.iter().enumerate() {
                let entry = &mut lookup[(f.tag >> 3) as usize];
                if *entry != 0 {
                    return Err(MALFORMED);
                }
                *entry = ordinal as u16 + 1;
            }
        } else {
            for (i, f) in descs.iter().enumerate() {
                if descs[..i].iter().any(|g| g.tag >> 3 == f.tag >> 3) {
                    return Err(MALFORMED);
                }
            }
        }
        let value_message = map_entry && descs[1].kind == 11;
        let mut lists = Vec::new();
        lists
            .try_reserve_exact(descs.iter().filter(|f| f.repeated).count())
            .map_err(|_| ALLOCATION)?;
        for (ordinal, f) in descs.iter_mut().enumerate() {
            if f.repeated {
                f.list = lists.len() as u32;
                lists.push(ordinal as u32);
            }
        }
        let mut entries = Vec::new();
        entries.try_reserve_exact(n).map_err(|_| ALLOCATION)?;
        for (ordinal, f) in descs.iter().enumerate() {
            let word = ordinal >> 5;
            let op = if word > u16::MAX as usize {
                H_SLOW
            } else if f.op == OP_MESSAGE && f.repeated && f.oneof == NONE {
                H_RMSG
            } else if f.special {
                H_SLOW
            } else {
                match f.op {
                    OP_VARINT32 => H_V32,
                    OP_VARINT64 => H_V64,
                    OP_SINT32 => H_S32,
                    OP_SINT64 => H_S64,
                    OP_BOOL => H_BOOL,
                    OP_FIXED32 => H_F32,
                    OP_FIXED64 => H_F64,
                    OP_STRING => H_STR,
                    OP_BYTES => H_BYTES,
                    OP_MESSAGE => H_MSG,
                    _ => H_SLOW,
                }
            };
            entries.push(Entry {
                tag: f.tag,
                slot: f.slot,
                child: f.child,
                op,
                flags: if f.explicit { E_EXPLICIT } else { 0 }
                    | if f.repeated { E_REPEATED } else { 0 }
                    | if f.map { E_MAP } else { 0 },
                word: word.min(u16::MAX as usize) as u16,
            });
        }
        let mut dispatch = [0u16; 128];
        for (ordinal, f) in descs.iter().enumerate() {
            if f.tag < 128 && ordinal < u16::MAX as usize {
                dispatch[f.tag as usize] = ordinal as u16 + 1;
            }
        }
        let required_closure = !required.is_empty();
        let closes = value_message || required_closure;
        let has_map = descs.iter().any(|f| f.map);
        types.push(TypeDesc {
            entries: entries.into_boxed_slice(),
            dispatch,
            closes,
            simple: !closes && !map_entry,
            has_map,
            fields: descs.into_boxed_slice(),
            lists: lists.into_boxed_slice(),
            lookup: lookup.into_boxed_slice(),
            record_bytes,
            words,
            members: members.into_boxed_slice(),
            required: required.into_boxed_slice(),
            map_entry,
            value_message,
            required_closure,
        });
    }
    if at != trailer || crc32(&bytes[section + 16..at]) != fingerprint {
        return Err(MALFORMED);
    }
    // Map fields must name map-entry types.
    for t in types.iter() {
        for f in t.fields.iter() {
            if f.map && !types[f.child as usize].map_entry {
                return Err(MALFORMED);
            }
        }
    }
    // Required children need full reachable-state validation (oneof clears and map replacement
    // can orphan an incomplete child). Projection conservatively materializes these closures.
    loop {
        let mut changed = false;
        for i in 0..types.len() {
            if !types[i].required_closure && types[i].fields.iter().any(|f|
                matches!(f.op, OP_MESSAGE | OP_GROUP) && types[f.child as usize].required_closure) {
                types[i].required_closure = true; changed = true;
            }
        }
        if !changed { break; }
    }
    Ok(Compiled {
        magic: COMPILED_MAGIC,
        types: types.into_boxed_slice(),
        fingerprint,
        max_record,
    })
}

// ---- Scratch ----

/// A stack that starts in caller-provided (stack) storage and, when allowed, spills to the heap.
pub(crate) struct Buf<'s, T: Copy> {
    inline: &'s mut [MaybeUninit<T>],
    heap: Vec<T>,
    len: usize,
    spill: bool,
}

impl<'s, T: Copy> Buf<'s, T> {
    pub(crate) fn new(inline: &'s mut [MaybeUninit<T>], spill: bool) -> Self {
        Buf {
            inline,
            heap: Vec::new(),
            len: 0,
            spill,
        }
    }

    #[inline]
    fn len(&self) -> usize {
        self.len
    }

    #[inline]
    fn push(&mut self, value: T) -> Result<()> {
        if self.heap.capacity() == 0 && self.len < self.inline.len() {
            self.inline[self.len].write(value);
            self.len += 1;
            return Ok(());
        }
        self.push_slow(value)
    }

    #[cold]
    fn push_slow(&mut self, value: T) -> Result<()> {
        self.reserve(1)?;
        self.heap.push(value);
        self.len += 1;
        Ok(())
    }

    /// Room for `more` elements on the heap (moving inline elements there first).
    fn reserve(&mut self, more: usize) -> Result<()> {
        if self.heap.capacity() == 0 {
            if !self.spill {
                return Err(BUDGET);
            }
            let wanted = (self.len + more).max(2 * self.inline.len()).max(16);
            self.heap.try_reserve_exact(wanted).map_err(|_| ALLOCATION)?;
            // SAFETY: the first `len` inline elements were initialized by `push`/`extend`.
            self.heap.extend(
                self.inline[..self.len]
                    .iter()
                    .map(|v| unsafe { v.assume_init() }),
            );
        } else if self.heap.capacity() - self.heap.len() < more {
            let wanted = more.max(self.heap.len());
            self.heap.try_reserve(wanted).map_err(|_| ALLOCATION)?;
        }
        Ok(())
    }

    #[inline]
    fn get(&self, i: usize) -> T {
        if self.heap.capacity() == 0 {
            debug_assert!(i < self.len);
            // SAFETY: elements below `len` are initialized.
            unsafe { self.inline[i].assume_init() }
        } else {
            self.heap[i]
        }
    }

    #[inline]
    fn set(&mut self, i: usize, value: T) {
        if self.heap.capacity() == 0 {
            assert!(i < self.len);
            self.inline[i].write(value);
        } else {
            self.heap[i] = value;
        }
    }

    fn as_slice(&self) -> &[T] {
        if self.heap.capacity() == 0 {
            // SAFETY: the first `len` inline elements are initialized.
            unsafe { std::slice::from_raw_parts(self.inline.as_ptr().cast::<T>(), self.len) }
        } else {
            &self.heap
        }
    }

    fn as_mut_slice(&mut self) -> &mut [T] {
        if self.heap.capacity() == 0 {
            // SAFETY: the first `len` inline elements are initialized.
            unsafe {
                std::slice::from_raw_parts_mut(self.inline.as_mut_ptr().cast::<T>(), self.len)
            }
        } else {
            &mut self.heap
        }
    }

    #[inline]
    fn truncate(&mut self, len: usize) {
        if len < self.len {
            self.len = len;
            if self.heap.capacity() != 0 {
                self.heap.truncate(len);
            }
        }
    }

    fn extend(&mut self, values: &[T]) -> Result<()> {
        if self.heap.capacity() == 0 && self.len + values.len() <= self.inline.len() {
            for (slot, &value) in self.inline[self.len..].iter_mut().zip(values) {
                slot.write(value);
            }
            self.len += values.len();
            return Ok(());
        }
        self.reserve(values.len())?;
        self.heap.extend_from_slice(values);
        self.len += values.len();
        Ok(())
    }
}

/// Frames hold records that do not fit the output (or that a merge walk decodes without
/// writing). Open records form a chain of strictly increasing depth, so at most `max_depth + 1`
/// frames are live; the heap region is sized for that bound once and never moves.
pub(crate) struct Frames<'s> {
    inline: &'s mut [MaybeUninit<u64>],
    top: usize,
    heap: Vec<u64>,
    heap_top: usize,
    spill: bool,
}

#[derive(Clone, Copy)]
enum Mark {
    Output,
    Inline(u32),
    Heap(u32),
}

impl<'s> Frames<'s> {
    pub(crate) fn new(inline: &'s mut [MaybeUninit<u64>], spill: bool) -> Self {
        Frames {
            inline,
            top: 0,
            heap: Vec::new(),
            heap_top: 0,
            spill,
        }
    }

    /// A zeroed frame of `bytes` (a multiple of 8).
    fn push(&mut self, bytes: usize, bound: usize) -> Result<(*mut u8, Mark)> {
        let words = bytes / 8;
        if self.heap.capacity() == 0 {
            if self.top + words <= self.inline.len() {
                let mark = Mark::Inline(self.top as u32);
                for word in &mut self.inline[self.top..self.top + words] {
                    word.write(0);
                }
                let at = self.inline[self.top..].as_mut_ptr().cast::<u8>();
                self.top += words;
                return Ok((at, mark));
            }
            if !self.spill {
                return Err(BUDGET);
            }
            let total = bound / 8;
            self.heap.try_reserve_exact(total).map_err(|_| ALLOCATION)?;
            self.heap.resize(total, 0);
        }
        if self.heap_top + words > self.heap.len() {
            return Err(BUDGET);
        }
        let mark = Mark::Heap(self.heap_top as u32);
        self.heap[self.heap_top..self.heap_top + words].fill(0);
        let at = self.heap[self.heap_top..].as_mut_ptr().cast::<u8>();
        self.heap_top += words;
        Ok((at, mark))
    }

    fn pop(&mut self, mark: Mark) {
        match mark {
            Mark::Output => {}
            Mark::Inline(top) => self.top = top as usize,
            Mark::Heap(top) => self.heap_top = top as usize,
        }
    }
}

#[derive(Clone, Copy)]
pub(crate) struct Pending {
    ordinal: u32,
    /// Map entries: key byte length, or `u32::MAX` for an integer key in `key`.
    klen: u32,
    value: u64,
    /// Map entries: integer key bits, or the input offset of a string/bytes key.
    key: u64,
}

#[derive(Clone, Copy)]
pub(crate) struct Payload {
    ordinal: u32,
    start: u32,
    end: u32,
}

/// Fixed scratch storage for one decode, kept on the caller's stack.
pub(crate) struct Inline {
    pending: [MaybeUninit<Pending>; 512],
    merges: [MaybeUninit<Payload>; 64],
    unknown: [MaybeUninit<u8>; 2048],
    table: [MaybeUninit<(u32, u32)>; 128],
    frames: [MaybeUninit<u64>; 512],
    stack: [MaybeUninit<Frame>; MAX_DEPTH as usize + 2],
}

impl Inline {
    #[inline(always)]
    pub(crate) fn new() -> Self {
        Inline {
            pending: [const { MaybeUninit::uninit() }; 512],
            merges: [const { MaybeUninit::uninit() }; 64],
            unknown: [const { MaybeUninit::uninit() }; 2048],
            table: [const { MaybeUninit::uninit() }; 128],
            frames: [const { MaybeUninit::uninit() }; 512],
            stack: [const { MaybeUninit::uninit() }; MAX_DEPTH as usize + 2],
        }
    }
}

// ---- Decoder ----

/// One open record on the explicit record stack; the stack index is the record's depth. The
/// decode loop keeps the top record's hot state (base, body end, type, presence word 0) in
/// locals and touches a frame only when it enters or closes a record. 64 bytes: input offsets
/// fit `u32` (inputs are at most `i32::MAX` bytes), and so do the scratch marks (pending
/// elements and merge payloads each take at least one input byte; unknown bytes are bounded by
/// `UNKNOWN_LIMIT`).
#[derive(Clone, Copy)]
struct Frame {
    base: *mut u8,
    /// Body end: the record's end, or the enclosing body's end for a group.
    end: u32,
    /// Error offset of failures charged to the record as a whole: its field's tag in the
    /// parent (NO_START for the root).
    start: u32,
    pending: u32,
    merges: u32,
    unknown: u32,
    ty: u32,
    at: u32,
    /// Group number; 0 for length-delimited bodies.
    group: u32,
    /// The parent's field that opened the record; NONE for records the decode loop does not
    /// close itself (the root, walks, merged and default value records).
    ordinal: u32,
    /// Body start (a singular message's remembered payload).
    body: u32,
    key: (u32, u32),
    mark: Mark,
    invalid_map: bool,
    merged: bool,
    /// An omitted lazy child that requires eager handling exposes its complete subtree.
    full: bool,
}

/// `Frame::start` of the root: no field of a parent to charge.
const NO_START: u32 = u32::MAX;

/// Unknown bytes collected but not yet placed stay below this (frames record their length as
/// `u32`); more is a budget failure, far beyond any slab `maxSlabBytes` admits.
const UNKNOWN_LIMIT: usize = u32::MAX as usize - 64;

impl Frame {
    /// The error offset for failures of the record as a whole (usize::MAX: none).
    #[inline(always)]
    fn start(&self) -> usize {
        if self.start == NO_START { usize::MAX } else { self.start as usize }
    }
}

struct Closed {
    at: u32,
    invalid_map: bool,
    klen: u32,
    key: u64,
    /// Offset of a group's end tag (the end of its body); the body end otherwise.
    end: usize,
}

/// What the out-of-line field path did: moved past the field, or entered a child record (whose
/// frame it pushed) at its body start.
enum Step {
    Next(usize),
    Enter(usize),
}

/// Input reads at position `p`, returning the value and the next position (by value, so the
/// position stays in a register). Callers guarantee `end <= input.len()`. Varints follow the
/// reference rule: at most ten bytes, the tenth only 0 or 1. The loop is inline and makes no call,
/// so the decode loop's hot handlers keep their registers (an out-of-line slow path forced
/// spills on x86-64 around every varint).
#[inline(always)]
fn get_varint(input: &[u8], p: usize, end: usize) -> Result<(u64, usize)> {
    let mut q = p;
    let mut value = 0u64;
    let mut shift = 0u32;
    loop {
        if q >= end {
            return Err(MALFORMED);
        }
        // SAFETY: q < end <= input.len().
        let b = unsafe { *input.get_unchecked(q) };
        q += 1;
        value |= ((b & 0x7F) as u64) << shift;
        if b < 0x80 {
            if shift == 63 && b > 1 {
                return Err(MALFORMED);
            }
            return Ok((value, q));
        }
        shift += 7;
        if shift > 63 {
            return Err(MALFORMED);
        }
    }
}

#[inline(always)]
fn get_tag(input: &[u8], p: usize, end: usize) -> Result<(u32, usize)> {
    let (tag, p) = get_varint(input, p, end)?;
    if tag > u32::MAX as u64 || tag >> 3 == 0 || tag & 7 > 5 {
        return Err(MALFORMED);
    }
    Ok((tag as u32, p))
}

#[inline(always)]
fn get_length(input: &[u8], p: usize, end: usize) -> Result<(usize, usize)> {
    let (n, p) = get_varint(input, p, end)?;
    if n > (end - p) as u64 {
        return Err(MALFORMED);
    }
    Ok((n as usize, p))
}

#[inline(always)]
fn get_fixed32(input: &[u8], p: usize, end: usize) -> Result<(u32, usize)> {
    if end - p < 4 {
        return Err(MALFORMED);
    }
    // SAFETY: p + 4 <= end <= input.len().
    let v = unsafe { input.as_ptr().add(p).cast::<[u8; 4]>().read_unaligned() };
    Ok((u32::from_le_bytes(v), p + 4))
}

#[inline(always)]
fn get_fixed64(input: &[u8], p: usize, end: usize) -> Result<(u64, usize)> {
    if end - p < 8 {
        return Err(MALFORMED);
    }
    // SAFETY: p + 8 <= end <= input.len().
    let v = unsafe { input.as_ptr().add(p).cast::<[u8; 8]>().read_unaligned() };
    Ok((u64::from_le_bytes(v), p + 8))
}

#[inline(always)]
fn zigzag32(n: u64) -> u32 {
    let n = n as u32;
    ((n >> 1) as i32 ^ -((n & 1) as i32)) as u32
}

#[inline(always)]
fn zigzag64(n: u64) -> u64 {
    ((n >> 1) as i64 ^ -((n & 1) as i64)) as u64
}

#[inline(always)]
fn get_scalar(op: u8, input: &[u8], p: usize, end: usize) -> Result<(u64, usize)> {
    Ok(match op {
        OP_VARINT32 => {
            let (v, p) = get_varint(input, p, end)?;
            (v as u32 as u64, p)
        }
        OP_VARINT64 => get_varint(input, p, end)?,
        OP_SINT32 => {
            let (n, p) = get_varint(input, p, end)?;
            (zigzag32(n) as u64, p)
        }
        OP_SINT64 => {
            let (n, p) = get_varint(input, p, end)?;
            (zigzag64(n), p)
        }
        OP_BOOL => {
            let (v, p) = get_varint(input, p, end)?;
            ((v != 0) as u64, p)
        }
        OP_FIXED32 => {
            let (v, p) = get_fixed32(input, p, end)?;
            (v as u64, p)
        }
        _ => get_fixed64(input, p, end)?,
    })
}

// Pointer forms of the readers for the decode loop: one register per position instead of an
// (input, offset) pair. Callers guarantee `q <= end` and that `[q, end)` lies in the input.

#[inline(always)]
fn ptr_varint(mut q: *const u8, end: *const u8) -> Result<(u64, *const u8)> {
    let mut value = 0u64;
    let mut shift = 0u32;
    loop {
        if q >= end {
            return Err(MALFORMED);
        }
        // SAFETY: q < end, inside the input.
        let b = unsafe { *q };
        q = unsafe { q.add(1) };
        value |= ((b & 0x7F) as u64) << shift;
        if b < 0x80 {
            if shift == 63 && b > 1 {
                return Err(MALFORMED);
            }
            return Ok((value, q));
        }
        shift += 7;
        if shift > 63 {
            return Err(MALFORMED);
        }
    }
}

#[inline(always)]
fn ptr_length(q: *const u8, end: *const u8) -> Result<(usize, *const u8)> {
    let (n, q) = ptr_varint(q, end)?;
    if n > (end as usize - q as usize) as u64 {
        return Err(MALFORMED);
    }
    Ok((n as usize, q))
}

#[inline(always)]
fn ptr_fixed32(q: *const u8, end: *const u8) -> Result<(u32, *const u8)> {
    if (end as usize - q as usize) < 4 {
        return Err(MALFORMED);
    }
    // SAFETY: four bytes remain before `end`.
    unsafe { Ok((u32::from_le_bytes(q.cast::<[u8; 4]>().read_unaligned()), q.add(4))) }
}

#[inline(always)]
fn ptr_fixed64(q: *const u8, end: *const u8) -> Result<(u64, *const u8)> {
    if (end as usize - q as usize) < 8 {
        return Err(MALFORMED);
    }
    // SAFETY: eight bytes remain before `end`.
    unsafe { Ok((u64::from_le_bytes(q.cast::<[u8; 8]>().read_unaligned()), q.add(8))) }
}

/// A short ASCII payload at `at` as two (possibly overlapping) little-endian words: `a` holds
/// the first eight bytes (zero-extended below eight), `b` the last eight. None when the payload is
/// longer than 16 bytes, not ASCII, or too close to `limit` (the input's end) for word loads,
/// which may read past the record's end but never past the input.
#[inline(always)]
fn ptr_short_ascii(at: *const u8, n: usize, limit: *const u8) -> Option<(u64, u64)> {
    if n > 16 || (limit as usize) < at as usize + 8 {
        return None;
    }
    // SAFETY: at + 8 <= limit, and for n >= 8, at + n <= limit.
    let load = |q: *const u8| u64::from_le_bytes(unsafe { q.cast::<[u8; 8]>().read_unaligned() });
    let (a, b) = if n >= 8 {
        (load(at), load(unsafe { at.add(n - 8) }))
    } else {
        let a = load(at) & ((1u64 << (8 * n)) - 1);
        (a, a)
    };
    if (a | b) & 0x8080_8080_8080_8080 != 0 {
        return None;
    }
    Some((a, b))
}

#[inline(always)]
fn read32(p: *const u8) -> u32 {
    // SAFETY: callers pass a pointer into a live record (output or frame) with 4 bytes left.
    u32::from_le_bytes(unsafe { p.cast::<[u8; 4]>().read_unaligned() })
}

#[inline(always)]
fn write32(p: *mut u8, value: u32) {
    // SAFETY: as read32, writable.
    unsafe { p.cast::<[u8; 4]>().write_unaligned(value.to_le_bytes()) }
}

#[inline(always)]
fn write64(p: *mut u8, value: u64) {
    // SAFETY: as read32, with 8 bytes left.
    unsafe { p.cast::<[u8; 8]>().write_unaligned(value.to_le_bytes()) }
}

/// Copy a payload; short ones with (overlapping) word moves instead of a libc call. Never
/// writes outside `dst[..src.len()]`.
#[inline(always)]
fn copy_bytes(dst: *mut u8, src: &[u8]) {
    let n = src.len();
    // SAFETY: the caller guarantees `n` writable bytes at `dst`, disjoint from `src`.
    unsafe {
        if (8..=16).contains(&n) {
            let a = src.as_ptr().cast::<[u8; 8]>().read_unaligned();
            let b = src.as_ptr().add(n - 8).cast::<[u8; 8]>().read_unaligned();
            dst.cast::<[u8; 8]>().write_unaligned(a);
            dst.add(n - 8).cast::<[u8; 8]>().write_unaligned(b);
        } else if (4..8).contains(&n) {
            let a = src.as_ptr().cast::<[u8; 4]>().read_unaligned();
            let b = src.as_ptr().add(n - 4).cast::<[u8; 4]>().read_unaligned();
            dst.cast::<[u8; 4]>().write_unaligned(a);
            dst.add(n - 4).cast::<[u8; 4]>().write_unaligned(b);
        } else if n < 4 {
            for (i, &byte) in src.iter().enumerate() {
                *dst.add(i) = byte;
            }
        } else {
            std::ptr::copy_nonoverlapping(src.as_ptr(), dst, n);
        }
    }
}

/// Zero a record of `size` bytes (a multiple of 8). Small records are cleared with a fixed
/// number of straight-line 16-byte stores when `room` (writable bytes from `p`) allows rounding
/// up: the extra bytes belong to allocations that follow and are always written in full.
#[inline(always)]
fn zero_record(p: *mut u8, size: usize, room: usize) {
    let zero = [0u8; 16];
    // SAFETY: the caller guarantees `room >= size` writable bytes at `p`.
    unsafe {
        let store = |at: usize| p.add(at).cast::<[u8; 16]>().write_unaligned(zero);
        if size <= 32 && room >= 32 {
            store(0);
            store(16);
        } else if size <= 64 && room >= 64 {
            store(0);
            store(16);
            store(32);
            store(48);
        } else {
            std::ptr::write_bytes(p, 0, size);
        }
    }
}

fn offset(at: usize) -> u32 {
    // Real allocations are below max_slab_bytes <= i32::MAX; a merge walk's virtual cursor may
    // run further, and only needs non-zero offsets (it never writes them out).
    u32::try_from(at).unwrap_or(u32::MAX)
}

pub(crate) struct Decoder<'a, 's> {
    c: &'a Compiled,
    input: &'a [u8],
    out: *mut u8,
    limit: usize,
    max: usize,
    cursor: usize,
    pos: usize,
    max_depth: u32,
    records: u64,
    handles: u64,
    certified: bool,
    projection: Option<Projection<'a>>,
    skip_utf8: bool,
    external: bool,
    /// Input offset of the field being decoded (the string payload for invalid UTF-8).
    pub(crate) error_at: usize,
    frame_bound: usize,
    /// The record stack (`MAX_DEPTH + 2` frames of `Inline::stack`).
    stack: *mut Frame,
    pending: Buf<'s, Pending>,
    merges: Buf<'s, Payload>,
    unknown: Buf<'s, u8>,
    table: Buf<'s, (u32, u32)>,
    frames: Frames<'s>,
}

/// Outcome of a decode: the slab length (written, or needed), counts and header flags.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct Outcome {
    pub bytes: u64,
    pub records: u32,
    pub handles: u32,
    pub flags: u32,
}

impl<'a, 's> Decoder<'a, 's> {
    #[inline(always)]
    fn alloc(&mut self, size: usize, alignment: usize) -> Result<usize> {
        let at = (self.cursor + alignment - 1) & !(alignment - 1);
        let end = at + size;
        if end > self.max {
            return Err(BUDGET);
        }
        if self.cursor < at && at <= self.limit {
            self.pad(at);
        }
        self.cursor = end;
        Ok(at)
    }

    /// Zero the alignment padding before `at` (at most 7 bytes).
    #[inline(always)]
    fn pad(&mut self, at: usize) {
        if self.cursor + 8 <= self.limit {
            // One 8-byte zero store from the old cursor. Bytes past `at` belong to this
            // allocation, which its caller writes in full.
            write64(unsafe { self.out.add(self.cursor) }, 0);
        } else {
            for i in self.cursor..at {
                // SAFETY: [cursor, at) lies inside the caller's `limit` writable bytes.
                unsafe { *self.out.add(i) = 0 };
            }
        }
    }

    #[inline(always)]
    fn fits(&self, at: usize, size: usize) -> bool {
        at + size <= self.limit
    }

    #[inline(always)]
    fn frame(&self, k: usize) -> *mut Frame {
        // SAFETY: k <= max_depth + 1 < the stack's MAX_DEPTH + 2 frames.
        unsafe { self.stack.add(k) }
    }

    /// Open a record of type `ty` as frame `k` (depth `k`): allocate it (or a decoder frame when
    /// it does not fit), zero it and take a handle.
    #[inline(always)]
    #[allow(clippy::too_many_arguments)]
    fn open(
        &mut self,
        k: usize,
        ty: u32,
        end: usize,
        group: u32,
        ordinal: u32,
        start: usize,
        body: usize,
    ) -> Result<*mut u8> {
        if k > self.max_depth as usize {
            return Err(BUDGET);
        }
        let size = self.c.types[ty as usize].record_bytes as usize;
        let at = self.alloc(size, 8)?;
        let (base, mark) = if self.fits(at, size) {
            // SAFETY: the record lies inside the output's writable prefix.
            let base = unsafe { self.out.add(at) };
            zero_record(base, size, self.limit - at);
            (base, Mark::Output)
        } else {
            self.frames.push(size, self.frame_bound)?
        };
        self.records += 1;
        write32(base, self.handles as u32);
        self.handles += 1;
        let full = self.projection.is_some_and(|p| k > 0 && unsafe {
            let parent = &*self.frame(k - 1);
            parent.full || (ordinal != NONE && p.flags & Projection::LAZY != 0
                && !p.wanted(parent.ty, ordinal as usize))
        });
        // SAFETY: frame `k` lies inside the stack (see `frame`).
        unsafe {
            self.frame(k).write(Frame {
                base,
                end: end as u32,
                start: if start == usize::MAX { NO_START } else { start as u32 },
                pending: self.pending.len() as u32,
                merges: self.merges.len() as u32,
                unknown: self.unknown.len() as u32,
                ty,
                at: offset(at),
                group,
                ordinal,
                body: body as u32,
                key: (0, 0),
                mark,
                invalid_map: false,
                merged: false,
                full,
            })
        };
        Ok(base)
    }

    /// Close frame `k`, whose body ended at `end`: record close (docs/SLAB.md) when it has
    /// anything to close, then the map key a parent list needs, then the frame is released.
    #[inline(always)]
    fn finish(&mut self, k: usize, end: usize) -> Result<Closed> {
        let c = self.c;
        let f = self.frame(k);
        // SAFETY: frame `k` is open.
        let (ty, merged, pending, merges, unknown) = unsafe {
            let f = &*f;
            (f.ty, f.merged, f.pending as usize, f.merges as usize, f.unknown as usize)
        };
        let t = &c.types[ty as usize];
        if merged || t.closes || self.has_projected_counts(k) || self.pending.len() > pending || self.unknown.len() > unknown {
            self.close(k)?;
        } else {
            self.merges.truncate(merges);
        }
        let (klen, key) = if t.map_entry { self.entry_key(k) } else { (NONE, 0) };
        // SAFETY: as above.
        let (at, invalid_map, mark) = unsafe { ((*f).at, (*f).invalid_map, (*f).mark) };
        self.frames.pop(mark);
        Ok(Closed {
            at,
            invalid_map,
            klen,
            key,
            end,
        })
    }

    /// A map entry's key, for deduplication at the parent's close: captured from the frame and
    /// the input, never read back from the slab.
    #[inline(never)]
    fn entry_key(&self, k: usize) -> (u32, u64) {
        // SAFETY: frame `k` is open.
        let o = unsafe { *self.frame(k) };
        let f = &self.c.types[o.ty as usize].fields[0];
        let present = read32(unsafe { o.base.add(4) }) & 1 != 0;
        match f.op {
            OP_STRING | OP_BYTES if present => (o.key.1, o.key.0 as u64),
            OP_STRING | OP_BYTES => (0, 0),
            _ if !present => (NONE, 0),
            _ if f.width == 8 => (NONE, unsafe {
                u64::from_le_bytes(o.base.add(f.slot as usize).cast::<[u8; 8]>().read_unaligned())
            }),
            _ => (NONE, read32(unsafe { o.base.add(f.slot as usize) }) as u64),
        }
    }

    fn skip(&mut self, tag: u32, end: usize, depth: usize) -> Result<()> {
        let input = self.input;
        let p = self.pos;
        self.pos = match tag & 7 {
            0 => get_varint(input, p, end)?.1,
            1 => get_fixed64(input, p, end)?.1,
            2 => {
                let (n, p) = get_length(input, p, end)?;
                p + n
            }
            3 => {
                if depth >= self.max_depth as usize {
                    return Err(BUDGET);
                }
                loop {
                    if self.pos >= end {
                        return Err(MALFORMED);
                    }
                    let (child, p) = get_tag(input, self.pos, end)?;
                    self.pos = p;
                    if child & 7 == 4 {
                        if child >> 3 != tag >> 3 {
                            return Err(MALFORMED);
                        }
                        return Ok(());
                    }
                    self.skip(child, end, depth + 1)?;
                }
            }
            5 => get_fixed32(input, p, end)?.1,
            _ => return Err(MALFORMED),
        };
        Ok(())
    }

    #[inline]
    fn keep_unknown(&mut self, start: usize, end: usize) -> Result<()> {
        if self.unknown.len() + (end - start) > UNKNOWN_LIMIT {
            return Err(BUDGET);
        }
        let input = self.input;
        self.unknown.extend(&input[start..end])
    }

    fn unknown_varint(&mut self, mut value: u64) -> Result<()> {
        let mut bytes = [0u8; 10];
        let mut n = 0;
        while value >= 0x80 {
            bytes[n] = value as u8 | 0x80;
            value >>= 7;
            n += 1;
        }
        bytes[n] = value as u8;
        if self.unknown.len() + n + 1 > UNKNOWN_LIMIT {
            return Err(BUDGET);
        }
        self.unknown.extend(&bytes[..=n])
    }

    /// Validate and place one string or bytes header with its owned payload.
    #[inline(never)]
    fn string(&mut self, start: usize, n: usize, text: bool) -> Result<u32> {
        if !text && self.projection.is_some_and(|p| p.flags & Projection::BORROW_BYTES != 0 && n >= p.borrow_bytes as usize) {
            return self.external_bytes(start, n);
        }
        let input = self.input;
        let bytes = &input[start..start + n];
        let (units, ascii) = if text && !self.skip_utf8 {
            match validate_utf16_units(bytes) {
                Some(found) => found,
                None => return Err(self.fail(MALFORMED, start)),
            }
        } else {
            (0, false)
        };
        if n > MAX_LENGTH {
            return Err(BUDGET);
        }
        let header = if ascii { 8 } else { 12 };
        let at = self.alloc(header + n, 4)?;
        let handle = self.handles as u32;
        self.handles += 1;
        if self.fits(at, header + n) {
            // SAFETY: the header and payload lie inside the output's writable prefix.
            unsafe {
                let p = self.out.add(at);
                write32(p, n as u32 | if ascii { ASCII } else { 0 });
                write32(p.add(4), handle);
                if !ascii {
                    write32(p.add(8), units as u32);
                }
                copy_bytes(p.add(header), bytes);
            }
        }
        Ok(offset(at))
    }

    /// Place a short ASCII string already checked by `short_ascii` (`a`, `b` its payload words).
    #[inline(always)]
    fn ascii(&mut self, n: usize, a: u64, b: u64) -> Result<u32> {
        let at = self.alloc(8 + n, 4)?;
        let handle = self.handles as u32;
        self.handles += 1;
        if self.fits(at, 8 + n.max(8)) {
            // SAFETY: header and the payload words lie inside the output's writable prefix;
            // below eight bytes the word's zero tail lands in bytes that later allocations or
            // the final zero fill overwrite.
            unsafe {
                let p = self.out.add(at);
                write64(p, (n as u64 | ASCII as u64) | (handle as u64) << 32);
                write64(p.add(8), a);
                if n >= 8 {
                    write64(p.add(n), b);
                }
            }
        } else if self.fits(at, 8 + n) {
            self.ascii_exact(at, n, handle, a, b);
        }
        Ok(offset(at))
    }

    #[cold]
    fn ascii_exact(&mut self, at: usize, n: usize, handle: u32, a: u64, b: u64) {
        let mut bytes = [0u8; 16];
        bytes[..8].copy_from_slice(&a.to_le_bytes());
        if n >= 8 {
            bytes[n - 8..n].copy_from_slice(&b.to_le_bytes());
        }
        // SAFETY: the header and payload lie inside the output's writable prefix.
        unsafe {
            let p = self.out.add(at);
            write32(p, n as u32 | ASCII);
            write32(p.add(4), handle);
            copy_bytes(p.add(8), &bytes[..n]);
        }
    }

    #[inline(always)]
    fn set_presence(base: *mut u8, ordinal: usize, present: bool) {
        // SAFETY: presence words lie inside the record.
        let word = unsafe { base.add(4 + 4 * (ordinal >> 5)) };
        let bit = 1u32 << (ordinal & 31);
        let value = read32(word);
        write32(word, if present { value | bit } else { value & !bit });
    }

    #[inline(always)]
    fn present(base: *mut u8, ordinal: usize) -> bool {
        read32(unsafe { base.add(4 + 4 * (ordinal >> 5)) }) & (1 << (ordinal & 31)) != 0
    }

    /// Oneof last-wins: clear the other members' bits, slots and remembered payloads.
    #[inline]
    fn select(&mut self, k: usize, t: &TypeDesc, ordinal: usize) {
        let oneof = t.fields[ordinal].oneof;
        if oneof == NONE {
            return;
        }
        self.select_slow(k, t, ordinal, oneof);
    }

    #[inline(never)]
    fn select_slow(&mut self, k: usize, t: &TypeDesc, ordinal: usize, oneof: u32) {
        // SAFETY: frame `k` is open.
        let (base, merges) = unsafe { ((*self.frame(k)).base, (*self.frame(k)).merges as usize) };
        for &member in t.members[oneof as usize].iter() {
            let m = member as usize;
            if m == ordinal {
                continue;
            }
            Self::set_presence(base, m, false);
            let f = &t.fields[m];
            let slot = unsafe { base.add(f.slot as usize) };
            if f.width == 8 {
                write64(slot, 0);
            } else {
                write32(slot, 0);
            }
            for i in merges..self.merges.len() {
                let mut payload = self.merges.get(i);
                if payload.ordinal == member {
                    payload.ordinal = NONE;
                    self.merges.set(i, payload);
                }
            }
        }
        let case = unsafe { base.add(4 + 4 * t.words as usize + 2 * oneof as usize) };
        // SAFETY: the case entry lies inside the record.
        unsafe { case.cast::<[u8; 2]>().write_unaligned((ordinal as u16 + 1).to_le_bytes()) };
    }

    /// Record the innermost rejecting offset (the first one set wins: inner frames fail first).
    #[cold]
    fn fail(&mut self, code: i32, at: usize) -> i32 {
        if self.error_at == usize::MAX {
            self.error_at = at;
        }
        code
    }

    #[inline]
    fn counts_only(&self, ty: u32, ordinal: usize) -> bool {
        let Some(p) = self.projection else { return false; };
        let f = &self.c.types[ty as usize].fields[ordinal];
        p.flags & Projection::LAZY == 0 && !p.wanted(ty, ordinal)
            && f.op == OP_MESSAGE && f.repeated && !f.map && f.oneof == NONE
            && !self.c.types[f.child as usize].required_closure
    }

    fn has_projected_counts(&self, k: usize) -> bool {
        let frame = unsafe { &*self.frame(k) };
        self.projection.is_some() && !frame.full && self.c.types[frame.ty as usize].lists.iter()
            .any(|&ordinal| self.counts_only(frame.ty, ordinal as usize))
    }

    /// Validate an omitted payload without allocating output. Ordinary merge validation keeps
    /// its old behavior; only this explicit projection walk may defer UTF-8.
    fn projected_walk(&mut self, k: usize, ty: u32, start: usize, body: usize, end: usize) -> Result<()> {
        let projection = self.projection.take();
        let saved = self.skip_utf8;
        self.skip_utf8 = projection.is_some_and(|p| p.flags & Projection::LAX != 0);
        let result = self.walk(k, ty, end, 0, start, body);
        self.projection = projection;
        self.skip_utf8 = saved;
        result.map(|_| ())
    }

    fn lazy_child(&mut self, body: usize, end: usize, depth: usize) -> Result<u32> {
        let at = self.alloc(16, 8)?;
        let handle = self.handles as u32;
        self.handles += 1;
        self.external = true;
        if self.fits(at, 16) {
            unsafe {
                let p = self.out.add(at);
                write32(p, body as u32); write32(p.add(4), (end - body) as u32);
                write32(p.add(8), handle); write32(p.add(12), depth as u32);
            }
        }
        Ok(offset(at) | LAZY_CHILD)
    }

    fn external_bytes(&mut self, body: usize, length: usize) -> Result<u32> {
        if length > MAX_LENGTH { return Err(BUDGET); }
        let at = self.alloc(16, 4)?;
        let handle = self.handles as u32;
        self.handles += 1;
        self.external = true;
        if self.fits(at, 16) {
            unsafe {
                let p = self.out.add(at);
                write32(p, length as u32 | EXTERNAL); write32(p.add(4), handle);
                write32(p.add(8), 0); write32(p.add(12), body as u32);
            }
        }
        Ok(offset(at))
    }

    /// Common projected messages. Maps, groups and oneofs retain their complete eager semantics.
    fn project_message(&mut self, k: usize, ordinal: usize, start: usize, pos: usize, end: usize) -> Result<usize> {
        let o = unsafe { *self.frame(k) };
        let f = &self.c.types[o.ty as usize].fields[ordinal];
        if k >= self.max_depth as usize { return Err(BUDGET); }
        let (n, body) = get_length(self.input, pos, end)?;
        let child_end = body + n;
        let (child, repeated, slot) = (f.child, f.repeated, f.slot);
        self.projected_walk(k + 1, child, start, body, child_end)?;
        let lazy = self.projection.is_some_and(|p| p.flags & Projection::LAZY != 0);
        let base = unsafe { o.base.add(slot as usize) };
        if repeated {
            if lazy {
                let value = self.lazy_child(body, child_end, k + 1)?;
                self.pending.push(Pending { ordinal: ordinal as u32, klen: 0, value: value as u64, key: 0 })?;
            } else {
                write32(base, read32(base).checked_add(1).ok_or(BUDGET)?);
            }
        } else if lazy {
            if read32(base) == 0 {
                write32(base, self.lazy_child(body, child_end, k + 1)?);
            } else {
                // Multiple lazy occurrences conservatively materialize their merged value.
                unsafe { (*self.frame(k)).merged = true; }
            }
            self.merges.push(Payload { ordinal: ordinal as u32, start: body as u32, end: child_end as u32 })?;
        }
        // Non-lazy omitted children need no merge scratch: each payload was fully validated,
        // and required-field closures are always eager. No skipped child value will be read.
        Self::set_presence(o.base, ordinal, true);
        Ok(child_end)
    }

    /// Decode frame `floor`'s body from `self.pos`, and every record nested in it, until that
    /// body ends; returns the body end (a group's end tag offset) with `self.pos` after it.
    /// Child records are entered and closed on the explicit stack (no recursion); `floor` stays
    /// open. The top record's hot state lives in locals; presence word 0 is written back to the
    /// record whenever anything else may read or change it.
    fn run(&mut self, floor: usize) -> Result<usize> {
        let c = self.c;
        let input = self.input;
        let origin = input.as_ptr();
        // SAFETY: one past the input's end.
        let limit = unsafe { origin.add(input.len()) };
        let mut k = floor;
        // The top record's registers: the read position and body end as pointers, the record
        // base, its type and presence word 0. Everything else (group, scratch marks, the parent's
        // field) stays in its frame until the record closes; input offsets are computed only on
        // the paths that need them.
        // SAFETY: self.pos <= input.len().
        let mut p: *const u8 = unsafe { origin.add(self.pos) };
        let mut base: *mut u8;
        let mut end: *const u8;
        let mut t: &TypeDesc;
        let mut presence: u32;
        macro_rules! off {
            ($q:expr) => {
                $q as usize - origin as usize
            };
        }
        macro_rules! load {
            () => {{
                // SAFETY: frame `k` is open.
                let f = unsafe { &*self.frame(k) };
                base = f.base;
                if f.end as usize > input.len() {
                    return Err(crate::PANIC);
                }
                // SAFETY: f.end <= input.len().
                end = unsafe { origin.add(f.end as usize) };
                t = &c.types[f.ty as usize];
                // Every record has a word at offset 4 (presence word 0, or zero padding for a
                // type without fields, which is never set).
                presence = read32(unsafe { base.add(4) });
            }};
        }
        macro_rules! bail {
            ($code:expr, $at:expr) => {
                return Err(self.fail($code, $at))
            };
        }
        macro_rules! tri {
            ($e:expr, $at:expr) => {
                match $e {
                    Ok(value) => value,
                    Err(code) => bail!(code, $at),
                }
            };
        }
        macro_rules! flush {
            () => {
                write32(unsafe { base.add(4) }, presence)
            };
        }
        // Presence of a singular field: explicit fields are always present, implicit ones when
        // the value differs from the default.
        macro_rules! mark {
            ($ordinal:expr, $e:expr, $nonzero:expr) => {{
                let set = $e.flags & E_EXPLICIT != 0 || $nonzero;
                if $e.word == 0 {
                    let bit = 1u32 << ($ordinal & 31);
                    presence = if set { presence | bit } else { presence & !bit };
                } else {
                    Self::set_presence(base, $ordinal, set);
                }
            }};
        }
        load!();
        'records: loop {
            let body_end;
            'field: {
                if p >= end {
                    // SAFETY: frame `k` is open.
                    if unsafe { (*self.frame(k)).group } != 0 {
                        bail!(MALFORMED, off!(p));
                    }
                    body_end = off!(end);
                    break 'field;
                }
                let start = p;
                // SAFETY: p < end.
                let b = unsafe { *p };
                // One-byte tags dispatch through the type's table: a hit is a declared field with
                // its declared wire type and needs no further tag checks.
                let hit = t.dispatch[(b & 0x7F) as usize];
                let ordinal = if b < 0x80 && hit != 0 {
                    p = unsafe { p.add(1) };
                    hit as usize - 1
                } else {
                    let (raw, q) = tri!(ptr_varint(p, end), off!(start));
                    p = q;
                    if raw > u32::MAX as u64 || raw >> 3 == 0 || raw & 7 > 5 {
                        bail!(MALFORMED, off!(start));
                    }
                    let tag = raw as u32;
                    if tag & 7 == 4 {
                        // SAFETY: frame `k` is open.
                        if tag >> 3 != unsafe { (*self.frame(k)).group } {
                            bail!(MALFORMED, off!(start));
                        }
                        body_end = off!(start);
                        break 'field;
                    }
                    match t.find(tag >> 3) {
                        Some(ordinal) if t.entries[ordinal].tag == tag => ordinal,
                        found => {
                            flush!();
                            let (s, q, e) = (off!(start), off!(p), off!(end));
                            p = unsafe { origin.add(tri!(self.mismatch(k, found, tag, s, q, e), s)) };
                            presence = read32(unsafe { base.add(4) });
                            continue 'records;
                        }
                    }
                };
                // SAFETY: ordinal < entries.len() (a table hit or a lookup result).
                let e = unsafe { t.entries.get_unchecked(ordinal) };
                // SAFETY: slots lie inside the record.
                let slot = unsafe { base.add(e.slot as usize) };
                if let Some(projection) = self.projection {
                    let ty = unsafe { (*self.frame(k)).ty };
                    if !unsafe { (*self.frame(k)).full } && !projection.wanted(ty, ordinal) && !t.map_entry {
                        if matches!(e.op, H_MSG | H_RMSG) && e.flags & E_MAP == 0
                            && !self.c.types[e.child as usize].required_closure {
                            flush!();
                            p = unsafe { origin.add(tri!(self.project_message(k, ordinal, off!(start), off!(p), off!(end)), off!(start))) };
                            presence = read32(unsafe { base.add(4) });
                            continue 'records;
                        }
                        if matches!(e.op, H_STR | H_BYTES) {
                            let (n, at) = tri!(ptr_length(p, end), off!(start));
                            if e.op == H_STR && projection.flags & Projection::LAX == 0
                                && validate_utf16_units(&input[off!(at)..off!(at) + n]).is_none() {
                                bail!(MALFORMED, off!(at));
                            }
                            p = unsafe { at.add(n) };
                            mark!(ordinal, e, n != 0);
                            continue 'records;
                        }
                    }
                }
                match e.op {
                    H_V32 => {
                        let (v, q) = tri!(ptr_varint(p, end), off!(start));
                        p = q;
                        write32(slot, v as u32);
                        mark!(ordinal, e, v as u32 != 0);
                    }
                    H_V64 => {
                        let (v, q) = tri!(ptr_varint(p, end), off!(start));
                        p = q;
                        write64(slot, v);
                        mark!(ordinal, e, v != 0);
                    }
                    H_S32 => {
                        let (v, q) = tri!(ptr_varint(p, end), off!(start));
                        p = q;
                        let v = zigzag32(v);
                        write32(slot, v);
                        mark!(ordinal, e, v != 0);
                    }
                    H_S64 => {
                        let (v, q) = tri!(ptr_varint(p, end), off!(start));
                        p = q;
                        let v = zigzag64(v);
                        write64(slot, v);
                        mark!(ordinal, e, v != 0);
                    }
                    H_BOOL => {
                        let (v, q) = tri!(ptr_varint(p, end), off!(start));
                        p = q;
                        write32(slot, (v != 0) as u32);
                        mark!(ordinal, e, v != 0);
                    }
                    H_F32 => {
                        let (v, q) = tri!(ptr_fixed32(p, end), off!(start));
                        p = q;
                        write32(slot, v);
                        mark!(ordinal, e, v != 0);
                    }
                    H_F64 => {
                        let (v, q) = tri!(ptr_fixed64(p, end), off!(start));
                        p = q;
                        write64(slot, v);
                        mark!(ordinal, e, v != 0);
                    }
                    H_STR | H_BYTES => {
                        let (n, at) = tri!(ptr_length(p, end), off!(start));
                        p = unsafe { at.add(n) };
                        let short = if e.op == H_STR { ptr_short_ascii(at, n, limit) } else { None };
                        let external = e.op == H_BYTES && self.projection.is_some_and(|p| p.flags & Projection::BORROW_BYTES != 0 && n >= p.borrow_bytes as usize);
                        let header = if external { tri!(self.external_bytes(off!(at), n), off!(start)) } else { match short {
                            Some((a, b)) => tri!(self.ascii(n, a, b), off!(start)),
                            None => tri!(self.string(off!(at), n, e.op == H_STR), off!(start)),
                        }};
                        write32(slot, header);
                        mark!(ordinal, e, n != 0);
                    }
                    H_MSG | H_RMSG => {
                        if k >= self.max_depth as usize {
                            bail!(BUDGET, off!(start));
                        }
                        let (n, body) = tri!(ptr_length(p, end), off!(start));
                        flush!();
                        let (s, body, child_end) = (off!(start), off!(body), off!(body) + n);
                        if e.op == H_MSG && read32(slot) != 0 {
                            // A later occurrence of a singular message: a merge (cold).
                            p = unsafe { origin.add(tri!(self.merge_walk(k, ordinal, s, body, child_end), s)) };
                            continue 'records;
                        }
                        tri!(self.open(k + 1, e.child, child_end, 0, ordinal as u32, s, body), s);
                        k += 1;
                        p = unsafe { origin.add(body) };
                        load!();
                    }
                    _ => {
                        flush!();
                        let s = off!(start);
                        match tri!(self.slow(k, ordinal, s, off!(p), off!(end)), s) {
                            Step::Next(q) => {
                                p = unsafe { origin.add(q) };
                                presence = read32(unsafe { base.add(4) });
                            }
                            Step::Enter(q) => {
                                p = unsafe { origin.add(q) };
                                k += 1;
                                load!();
                            }
                        }
                    }
                }
                continue 'records;
            }
            // The top record's body ended at `body_end`; `p` is after it.
            flush!();
            self.pos = off!(p);
            if k == floor {
                return Ok(body_end);
            }
            // SAFETY: frame `k` is open.
            let f = unsafe { &*self.frame(k) };
            let (cstart, cordinal, cbody) = (f.start(), f.ordinal as usize, f.body);
            let closed = if t.simple
                && !self.has_projected_counts(k)
                && !f.merged
                && self.pending.len() == (f.pending as usize)
                && self.unknown.len() == (f.unknown as usize)
            {
                // Nothing to close (docs/SLAB.md record close is empty).
                self.merges.truncate(f.merges as usize);
                if !matches!(f.mark, Mark::Output) {
                    self.frames.pop(f.mark);
                }
                Closed {
                    at: f.at,
                    invalid_map: false,
                    klen: NONE,
                    key: 0,
                    end: body_end,
                }
            } else {
                let closed = tri!(self.finish(k, body_end), cstart);
                p = unsafe { origin.add(self.pos) };
                closed
            };
            k -= 1;
            load!();
            let e = &t.entries[cordinal];
            if e.flags & E_REPEATED != 0 {
                if e.flags & E_MAP != 0 && closed.invalid_map {
                    tri!(self.keep_unknown(cstart, off!(p)), cstart);
                } else {
                    tri!(
                        self.pending.push(Pending {
                            ordinal: cordinal as u32,
                            klen: closed.klen,
                            value: closed.at as u64,
                            key: closed.key,
                        }),
                        cstart
                    );
                }
            } else {
                write32(unsafe { base.add(e.slot as usize) }, closed.at);
                mark!(cordinal, e, true);
                tri!(
                    self.merges.push(Payload {
                        ordinal: cordinal as u32,
                        start: cbody,
                        end: closed.end as u32,
                    }),
                    cstart
                );
            }
        }
    }

    /// A tag that names no field, or a field with another wire type: a packed run of a
    /// packable repeated field, or unknown bytes. Returns the position after the field.
    #[inline(never)]
    fn mismatch(
        &mut self,
        k: usize,
        found: Option<usize>,
        tag: u32,
        start: usize,
        pos: usize,
        end: usize,
    ) -> Result<usize> {
        if let Some(ordinal) = found {
            let c = self.c;
            // SAFETY: frame `k` is open.
            let f = &c.types[unsafe { (*self.frame(k)).ty } as usize].fields[ordinal];
            if tag & 7 == 2 && f.packable {
                return self.packed(f, ordinal, pos, end);
            }
        }
        self.pos = pos;
        self.skip(tag, end, k)?;
        let after = self.pos;
        self.keep_unknown(start, after)?;
        Ok(after)
    }

    /// Every field the decode loop does not handle inline: repeated scalars and strings, oneof
    /// members, closed enums, map entry keys, groups and fields past presence word 65,535.
    #[inline(never)]
    fn slow(&mut self, k: usize, ordinal: usize, start: usize, pos: usize, end: usize) -> Result<Step> {
        let c = self.c;
        let input = self.input;
        // SAFETY: frame `k` is open.
        let o = unsafe { *self.frame(k) };
        let t = &c.types[o.ty as usize];
        let f = &t.fields[ordinal];
        match f.op {
            OP_MESSAGE | OP_GROUP => {
                if k >= self.max_depth as usize {
                    return Err(BUDGET);
                }
                let (child_end, group, body) = if f.op == OP_MESSAGE {
                    let (n, body) = get_length(input, pos, end)?;
                    (body + n, 0, body)
                } else {
                    (end, f.tag >> 3, pos)
                };
                if !f.repeated {
                    self.select(k, t, ordinal);
                    if read32(unsafe { o.base.add(f.slot as usize) }) != 0 {
                        self.pos = body;
                        // SAFETY: frame `k` is open.
                        unsafe { (*self.frame(k)).merged = true };
                        let payload_end = self.walk(k + 1, f.child, child_end, group, start, body)?;
                        self.merges.push(Payload {
                            ordinal: ordinal as u32,
                            start: body as u32,
                            end: payload_end as u32,
                        })?;
                        return Ok(Step::Next(self.pos));
                    }
                }
                self.open(k + 1, f.child, child_end, group, ordinal as u32, start, body)?;
                Ok(Step::Enter(body))
            }
            OP_STRING | OP_BYTES => {
                let (n, at) = get_length(input, pos, end)?;
                let header = self.string(at, n, f.op == OP_STRING)?;
                self.string_special(k, t, ordinal, at, n, header)?;
                Ok(Step::Next(at + n))
            }
            op => {
                let (bits, after) = get_scalar(op, input, pos, end)?;
                self.scalar_special(k, t, ordinal, start, after, bits)?;
                Ok(Step::Next(after))
            }
        }
    }

    /// A later occurrence of a singular, non-oneof message: validate it with a walk that writes
    /// nothing and remember the payload for the merge at close.
    #[inline(never)]
    fn merge_walk(&mut self, k: usize, ordinal: usize, start: usize, body: usize, end: usize) -> Result<usize> {
        let c = self.c;
        // SAFETY: frame `k` is open.
        let child = c.types[unsafe { (*self.frame(k)).ty } as usize].fields[ordinal].child;
        unsafe { (*self.frame(k)).merged = true };
        self.pos = body;
        let payload_end = self.walk(k + 1, child, end, 0, start, body)?;
        self.merges.push(Payload {
            ordinal: ordinal as u32,
            start: body as u32,
            end: payload_end as u32,
        })?;
        Ok(self.pos)
    }

    /// A string or bytes field that is repeated, a oneof member or a map entry's key.
    fn string_special(
        &mut self,
        k: usize,
        t: &TypeDesc,
        ordinal: usize,
        at: usize,
        n: usize,
        header: u32,
    ) -> Result<()> {
        let f = &t.fields[ordinal];
        if t.map_entry && ordinal == 0 {
            // SAFETY: frame `k` is open.
            unsafe { (*self.frame(k)).key = (at as u32, n as u32) };
        }
        if f.repeated {
            return self.pending.push(Pending {
                ordinal: ordinal as u32,
                klen: 0,
                value: header as u64,
                key: 0,
            });
        }
        self.select(k, t, ordinal);
        // SAFETY: frame `k` is open.
        let base = unsafe { (*self.frame(k)).base };
        write32(unsafe { base.add(f.slot as usize) }, header);
        Self::set_presence(base, ordinal, f.explicit || n != 0);
        Ok(())
    }

    /// A scalar that is repeated, a oneof member or a closed enum (or a map entry's enum value).
    fn scalar_special(
        &mut self,
        k: usize,
        t: &TypeDesc,
        ordinal: usize,
        start: usize,
        end: usize,
        bits: u64,
    ) -> Result<()> {
        let f = &t.fields[ordinal];
        let o = self.frame(k);
        if f.closed && !f.values.accepts(bits as i32) {
            if f.map_value_enum {
                // SAFETY: frame `k` is open.
                unsafe { (*o).invalid_map = true };
            }
            return self.keep_unknown(start, end);
        }
        if f.map_value_enum {
            unsafe { (*o).invalid_map = false };
        }
        if f.repeated {
            return self.pending.push(Pending {
                ordinal: ordinal as u32,
                klen: 0,
                value: bits,
                key: 0,
            });
        }
        self.select(k, t, ordinal);
        let base = unsafe { (*o).base };
        let slot = unsafe { base.add(f.slot as usize) };
        let present = if f.width == 8 {
            write64(slot, bits);
            bits != 0
        } else {
            write32(slot, bits as u32);
            bits as u32 != 0
        };
        Self::set_presence(base, ordinal, f.explicit || present);
        Ok(())
    }

    /// A packed run at `pos` (its length prefix): every element in order, out-of-set closed-enum
    /// values re-encoded into the unknown bytes. Returns the position after the run.
    fn packed(&mut self, f: &FieldDesc, ordinal: usize, pos: usize, end: usize) -> Result<usize> {
        let input = self.input;
        let (n, mut p) = get_length(input, pos, end)?;
        let packed_end = p + n;
        while p < packed_end {
            let (bits, after) = get_scalar(f.op, input, p, packed_end)?;
            p = after;
            if f.closed && !f.values.accepts(bits as i32) {
                // Re-encoded as an unpacked varint field (docs/SLAB.md).
                self.unknown_varint((f.tag >> 3) as u64 * 8)?;
                self.unknown_varint(bits as i32 as i64 as u64)?;
            } else {
                self.pending.push(Pending {
                    ordinal: ordinal as u32,
                    klen: 0,
                    value: bits,
                    key: 0,
                })?;
            }
        }
        Ok(p)
    }

    /// Validate one record without writing it (a merged message's later payload): the decoder
    /// with writes disabled, every record framed, and the cursor, counts, `CERTIFIED` and
    /// unknown-table entries restored afterwards. Returns the body end; `self.pos` is after it.
    #[inline(never)]
    fn walk(&mut self, k: usize, ty: u32, end: usize, group: u32, start: usize, body: usize) -> Result<usize> {
        let saved = (
            self.cursor,
            self.records,
            self.handles,
            self.certified,
            self.limit,
            self.table.len(),
            self.max,
        );
        // Nothing is written and the walk's virtual cursor is not budgeted.
        self.limit = 0;
        self.max = usize::MAX;
        let result = self.walk_record(k, ty, end, group, start, body);
        (
            self.cursor,
            self.records,
            self.handles,
            self.certified,
            self.limit,
            self.max,
        ) = (saved.0, saved.1, saved.2, saved.3, saved.4, saved.6);
        self.table.truncate(saved.5);
        result
    }

    fn walk_record(&mut self, k: usize, ty: u32, end: usize, group: u32, start: usize, body: usize) -> Result<usize> {
        self.open(k, ty, end, group, NONE, start, body)?;
        self.pos = body;
        let body_end = self.run(k)?;
        Ok(self.finish(k, body_end)?.end)
    }

    /// Record close, in docs/SLAB.md order: merges, map value default, lists, unknown span,
    /// required fields.
    #[inline(never)]
    fn close(&mut self, k: usize) -> Result<()> {
        let c = self.c;
        // SAFETY: frame `k` is open.
        let o = unsafe { *self.frame(k) };
        let t = &c.types[o.ty as usize];
        let resume = self.pos;
        if o.merged {
            self.merge(k, t)?;
        }
        if t.value_message {
            let f = &t.fields[1];
            let slot = unsafe { o.base.add(f.slot as usize) };
            if read32(slot) == 0 {
                if k >= self.max_depth as usize {
                    return Err(BUDGET);
                }
                self.open(k + 1, f.child, self.pos, 0, NONE, o.start(), self.pos)?;
                let closed = self.finish(k + 1, self.pos)?;
                write32(slot, closed.at);
                Self::set_presence(o.base, 1, true);
            }
        }
        if self.pending.len() > (o.pending as usize) || self.has_projected_counts(k) {
            self.lists(&o, t)?;
        }
        if self.unknown.len() > (o.unknown as usize) {
            let n = self.unknown.len() - (o.unknown as usize);
            let at = self.alloc(4 + n, 4)?;
            if self.fits(at, 4 + n) {
                let bytes = &self.unknown.as_slice()[(o.unknown as usize)..];
                // SAFETY: the span lies inside the output's writable prefix.
                unsafe {
                    let p = self.out.add(at);
                    write32(p, n as u32);
                    std::ptr::copy_nonoverlapping(bytes.as_ptr(), p.add(4), n);
                }
            }
            self.unknown.truncate(o.unknown as usize);
            self.table.push((o.at, offset(at)))?;
        }
        for &ordinal in t.required.iter() {
            if !Self::present(o.base, ordinal as usize) {
                self.certified = false;
            }
        }
        self.pending.truncate(o.pending as usize);
        self.merges.truncate(o.merges as usize);
        self.pos = resume;
        Ok(())
    }

    /// Singular messages seen more than once (ordinal order): one record decoded from the
    /// concatenation of every payload since the last clear, here fed payload by payload.
    #[inline(never)]
    fn merge(&mut self, k: usize, t: &TypeDesc) -> Result<()> {
        // SAFETY: frame `k` is open.
        let o = unsafe { *self.frame(k) };
        let (first, last) = ((o.merges as usize), self.merges.len());
        let mut floor = 0u32;
        loop {
            let mut ordinal = NONE;
            let mut count = 0;
            for i in first..last {
                let p = self.merges.get(i);
                if p.ordinal != NONE && p.ordinal >= floor {
                    if p.ordinal < ordinal {
                        ordinal = p.ordinal;
                        count = 1;
                    } else if p.ordinal == ordinal {
                        count += 1;
                    }
                }
            }
            if ordinal == NONE {
                return Ok(());
            }
            floor = ordinal + 1;
            let f = &t.fields[ordinal as usize];
            let full = o.full || self.projection.is_some_and(|p|
                p.flags & Projection::LAZY != 0 && !p.wanted(o.ty, ordinal as usize) && !t.map_entry);
            if count < 2 { continue; }
            self.open(k + 1, f.child, 0, 0, NONE, o.start(), 0)?;
            if full {
                unsafe { (*self.frame(k + 1)).full = true; }
            }
            for i in first..last {
                let p = self.merges.get(i);
                if p.ordinal == ordinal {
                    self.pos = p.start as usize;
                    // SAFETY: frame `k + 1` is open.
                    unsafe {
                        (*self.frame(k + 1)).end = p.end;
                    }
                    self.run(k + 1)?;
                }
            }
            let closed = self.finish(k + 1, self.pos)?;
            write32(unsafe { o.base.add(f.slot as usize) }, closed.at);
            self.certified = false;
        }
    }

    fn key_equal(&self, a: &Pending, b: &Pending) -> bool {
        a.klen == b.klen
            && if a.klen == NONE {
                a.key == b.key
            } else {
                let (x, y, n) = (a.key as usize, b.key as usize, a.klen as usize);
                self.input[x..x + n] == self.input[y..y + n]
            }
    }

    /// Lists in ordinal order; maps keep first-key order with the last entry per key. One pass
    /// counts the elements of every list field, lists are allocated in ordinal order, and one
    /// pass scatters the elements (types with more list fields than `DENSE_LISTS` scan per field).
    fn lists(&mut self, o: &Frame, t: &TypeDesc) -> Result<()> {
        const DENSE_LISTS: usize = 16;
        let (first, last) = ((o.pending as usize), self.pending.len());
        if t.lists.len() > DENSE_LISTS {
            return self.lists_sparse(o, t);
        }
        if t.has_map {
            for &ordinal in t.lists.iter() {
                if t.fields[ordinal as usize].map {
                    self.dedup(first, last, ordinal)?;
                }
            }
        }
        let mut counts = [0u32; DENSE_LISTS];
        for p in &self.pending.as_slice()[first..last] {
            if p.ordinal != NONE {
                counts[t.fields[p.ordinal as usize].list as usize] += 1;
            }
        }
        // Element write positions per list (0: not written, the list does not fit).
        let mut cursors = [0usize; DENSE_LISTS];
        for (index, &ordinal) in t.lists.iter().enumerate() {
            let f = &t.fields[ordinal as usize];
            let counts_only = !o.full && self.counts_only(o.ty, ordinal as usize);
            let count = if counts_only { read32(unsafe { o.base.add(f.slot as usize) }) as usize } else { counts[index] as usize };
            if count == 0 {
                continue;
            }
            let size = 8 + if counts_only { 0 } else { count * f.width as usize };
            let at = self.alloc(size, 8)?;
            if self.fits(at, size) {
                // SAFETY: the list lies inside the output's writable prefix.
                let p = unsafe { self.out.add(at) };
                write32(p, count as u32);
                write32(unsafe { p.add(4) }, if counts_only { COUNTS_ONLY } else { 0 });
                cursors[index] = if counts_only { 0 } else { at + 8 };
            }
            write32(unsafe { o.base.add(f.slot as usize) }, offset(at));
            Self::set_presence(o.base, ordinal as usize, true);
        }
        let out = self.out;
        for p in &self.pending.as_slice()[first..last] {
            if p.ordinal == NONE {
                continue;
            }
            let f = &t.fields[p.ordinal as usize];
            let index = f.list as usize;
            let at = cursors[index];
            if at == 0 {
                continue;
            }
            // SAFETY: inside the list allocated above.
            let e = unsafe { out.add(at) };
            match f.width {
                1 => unsafe { e.write(p.value as u8) },
                4 => write32(e, p.value as u32),
                _ => write64(e, p.value),
            }
            cursors[index] = at + f.width as usize;
        }
        Ok(())
    }

    #[inline(never)]
    fn lists_sparse(&mut self, o: &Frame, t: &TypeDesc) -> Result<()> {
        let (first, last) = ((o.pending as usize), self.pending.len());
        for &ordinal in t.lists.iter() {
            let f = &t.fields[ordinal as usize];
            if f.map {
                self.dedup(first, last, ordinal)?;
            }
            let mut count = 0usize;
            for i in first..last {
                count += usize::from(self.pending.get(i).ordinal == ordinal);
            }
            let counts_only = !o.full && self.counts_only(o.ty, ordinal as usize);
            if counts_only { count = read32(unsafe { o.base.add(f.slot as usize) }) as usize; }
            if count == 0 {
                continue;
            }
            let w = f.width as usize;
            let size = 8 + if counts_only { 0 } else { count * w };
            let at = self.alloc(size, 8)?;
            if self.fits(at, size) {
                // SAFETY: the list lies inside the output's writable prefix.
                let p = unsafe { self.out.add(at) };
                write32(p, count as u32);
                write32(unsafe { p.add(4) }, if counts_only { COUNTS_ONLY } else { 0 });
                let mut e = unsafe { p.add(8) };
                let items = &self.pending.as_slice()[first..last];
                for item in items.iter().filter(|item| item.ordinal == ordinal) {
                    match w {
                        1 => unsafe { e.write(item.value as u8) },
                        4 => write32(e, item.value as u32),
                        _ => write64(e, item.value),
                    }
                    e = unsafe { e.add(w) };
                }
            }
            write32(unsafe { o.base.add(f.slot as usize) }, offset(at));
            Self::set_presence(o.base, ordinal as usize, true);
        }
        Ok(())
    }

    /// Fold later entries with an equal key into the first entry of that key (which keeps its
    /// position and takes the later record); folded entries are dropped from the list.
    #[inline(never)]
    fn dedup(&mut self, first: usize, last: usize, ordinal: u32) -> Result<()> {
        let count = (first..last)
            .filter(|&i| self.pending.get(i).ordinal == ordinal)
            .count();
        if count > 32 && self.pending.spill {
            return self.dedup_hashed(first, last, ordinal);
        }
        if count > 256 {
            return Err(BUDGET);
        }
        for j in first..last {
            let b = self.pending.get(j);
            if b.ordinal != ordinal {
                continue;
            }
            for i in first..j {
                let mut a = self.pending.get(i);
                if a.ordinal == ordinal && self.key_equal(&a, &b) {
                    a.value = b.value;
                    self.pending.set(i, a);
                    let mut dead = b;
                    dead.ordinal = NONE;
                    self.pending.set(j, dead);
                    break;
                }
            }
        }
        Ok(())
    }

    #[inline(never)]
    fn dedup_hashed(&mut self, first: usize, last: usize, ordinal: u32) -> Result<()> {
        use std::collections::HashMap;
        let input = self.input;
        let key = |p: &Pending| -> (u32, u64, &[u8]) {
            if p.klen == NONE {
                (NONE, p.key, &[])
            } else {
                (p.klen, 0, &input[p.key as usize..p.key as usize + p.klen as usize])
            }
        };
        let mut seen: HashMap<(u32, u64, &[u8]), usize> = HashMap::new();
        seen.try_reserve(last - first).map_err(|_| ALLOCATION)?;
        let items = self.pending.as_mut_slice();
        for j in first..last {
            if items[j].ordinal != ordinal {
                continue;
            }
            match seen.get(&key(&items[j])) {
                Some(&i) => {
                    items[i].value = items[j].value;
                    items[j].ordinal = NONE;
                }
                None => {
                    seen.insert(key(&items[j]), j);
                }
            }
        }
        Ok(())
    }
}

/// Decode `input` into `out` (the first `capacity` bytes are writable; `out` may be null when
/// `capacity` is 0). Ok(outcome) with `outcome.bytes <= capacity` wrote a complete slab; a larger
/// `bytes` means `NEEDS_CAPACITY` and nothing usable was written. `error_at` receives the input
/// offset of the field (or invalid string payload) that rejected the input.
#[allow(clippy::too_many_arguments)]
pub fn decode(
    compiled: &Compiled,
    input: &[u8],
    flags: u32,
    out: *mut u8,
    capacity: usize,
    max_depth: u32,
    max_slab_bytes: u64,
    spill: bool,
    error_at: &mut usize,
) -> Result<Outcome> {
    let mut inline = Inline::new();
    decode_with(
        compiled,
        input,
        flags,
        out,
        capacity,
        max_depth,
        max_slab_bytes,
        spill,
        error_at,
        &mut inline,
    )
}

#[allow(clippy::too_many_arguments)]
pub fn decode_projected(
    compiled: &Compiled, input: &[u8], flags: u32, projection: Option<Projection<'_>>,
    out: *mut u8, capacity: usize, max_depth: u32, max_slab_bytes: u64, spill: bool,
    error_at: &mut usize,
) -> Result<Outcome> {
    let mut inline = Inline::new();
    decode_projected_with(compiled, input, flags, projection, out, capacity, max_depth, max_slab_bytes, spill, error_at, &mut inline)
}

#[allow(clippy::too_many_arguments)]
pub(crate) fn decode_with(
    compiled: &Compiled,
    input: &[u8],
    flags: u32,
    out: *mut u8,
    capacity: usize,
    max_depth: u32,
    max_slab_bytes: u64,
    spill: bool,
    error_at: &mut usize,
    inline: &mut Inline,
) -> Result<Outcome> {
    decode_projected_with(compiled, input, flags, None, out, capacity, max_depth, max_slab_bytes, spill, error_at, inline)
}

#[allow(clippy::too_many_arguments)]
fn decode_projected_with<'a>(
    compiled: &'a Compiled, input: &'a [u8], flags: u32, projection: Option<Projection<'a>>,
    out: *mut u8, capacity: usize, max_depth: u32, max_slab_bytes: u64, spill: bool,
    error_at: &mut usize, inline: &mut Inline,
) -> Result<Outcome> {
    *error_at = 0;
    let ownership = flags & (FLAG_OWNING | FLAG_BORROWING);
    if !matches!(ownership, FLAG_OWNING | FLAG_BORROWING)
        || flags & !(FLAG_OWNING | FLAG_BORROWING | FLAG_LAX | FLAG_SIZING_ONLY) != 0 {
        return Err(MALFORMED);
    }
    let options = projection.map_or(0, |p| p.flags);
    if (flags & FLAG_LAX != 0) != (options & Projection::LAX != 0)
        || options & (Projection::LAZY | Projection::BORROW_BYTES) != 0 && ownership != FLAG_BORROWING {
        return Err(MALFORMED);
    }
    if max_depth > MAX_DEPTH
        || max_slab_bytes > i32::MAX as u64
        || input.len() > i32::MAX as usize
        || max_slab_bytes < HEADER_BYTES as u64
    {
        return Err(BUDGET);
    }
    if out.is_null() && capacity != 0 {
        return Err(MALFORMED);
    }
    let max = max_slab_bytes as usize;
    let limit = if flags & FLAG_SIZING_ONLY != 0 {
        0
    } else {
        capacity.min(max)
    };
    let frame_bound = (max_depth as usize + 2) * compiled.max_record as usize;
    let mut d = Decoder {
        c: compiled,
        input,
        out,
        limit,
        max,
        cursor: HEADER_BYTES,
        pos: 0,
        max_depth,
        records: 0,
        handles: 0,
        certified: true,
        projection,
        skip_utf8: false,
        external: false,
        error_at: usize::MAX,
        frame_bound,
        stack: inline.stack.as_mut_ptr().cast::<Frame>(),
        pending: Buf::new(&mut inline.pending, spill),
        merges: Buf::new(&mut inline.merges, spill),
        unknown: Buf::new(&mut inline.unknown, spill),
        table: Buf::new(&mut inline.table, spill),
        frames: Frames::new(&mut inline.frames, spill),
    };
    let result = decode_root(&mut d);
    *error_at = if result.is_err() && d.error_at != usize::MAX {
        d.error_at
    } else {
        0
    };
    result
}

fn decode_root(d: &mut Decoder) -> Result<Outcome> {
    d.open(0, 0, d.input.len(), 0, NONE, usize::MAX, 0)?;
    d.pos = 0;
    let body_end = d.run(0)?;
    let root = d.finish(0, body_end)?;
    if root.at as usize != HEADER_BYTES {
        return Err(crate::PANIC);
    }
    let mut table_at = 0;
    if d.table.len() != 0 {
        // The unknown table is the last allocation, ascending by record (= handle order).
        d.table.as_mut_slice().sort_unstable_by_key(|entry| entry.0);
        let n = d.table.len();
        let size = 8 + 8 * n;
        let at = d.alloc(size, 8)?;
        if d.fits(at, size) {
            let p = unsafe { d.out.add(at) };
            write32(p, n as u32);
            write32(unsafe { p.add(4) }, 0);
            for (i, &(record, span)) in d.table.as_slice().iter().enumerate() {
                write32(unsafe { p.add(8 + 8 * i) }, record);
                write32(unsafe { p.add(12 + 8 * i) }, span);
            }
        }
        table_at = at as u32;
    }
    let bytes = (d.cursor + 7) & !7;
    if bytes > d.max {
        return Err(BUDGET);
    }
    let flags = (if d.external { SLAB_BORROWING } else { SLAB_OWNING })
        | if d.certified { SLAB_CERTIFIED } else { 0 }
        | if d.projection.is_some() { SLAB_PROJECTED } else { 0 }
        | if d.projection.is_some_and(|p| p.flags & Projection::LAX != 0) { SLAB_LAX } else { 0 };
    let outcome = Outcome {
        bytes: bytes as u64,
        records: d.records as u32,
        handles: d.handles as u32,
        flags,
    };
    if bytes > d.limit {
        return Ok(outcome);
    }
    // SAFETY: the whole slab lies inside the output's writable prefix.
    unsafe {
        std::ptr::write_bytes(d.out.add(d.cursor), 0, bytes - d.cursor);
        let header = [
            SLAB_MAGIC,
            flags,
            bytes as u32,
            outcome.records,
            outcome.handles,
            HEADER_BYTES as u32,
            d.c.fingerprint,
            table_at,
        ];
        for (i, word) in header.iter().enumerate() {
            write32(d.out.add(4 * i), *word);
        }
    }
    Ok(outcome)
}

#[cfg(test)]
#[path = "slab_tests.rs"]
mod tests;
