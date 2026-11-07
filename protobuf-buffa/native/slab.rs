//! Seam ABI 10: protobuf decoded into layout-compiled slabs (format `TDSLAB1`, docs/SLAB.md).
//!
//! A schema program (`TDB3` tables plus the `TDL1` layout section that Java appends) compiles
//! once into checked descriptors; the layout is recomputed here and a program whose section
//! disagrees is rejected. Decoding is one table-driven pass that writes the slab into a
//! caller-provided buffer with a bump cursor, in exactly the placement order docs/SLAB.md fixes,
//! so that this producer and the Java producers agree byte for byte.
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
/// Phase E (projection without validating unread strings): rejected until Task E1.
pub const FLAG_LAX: u32 = 2;
/// Walk and size only: nothing is written and the result is `NEEDS_CAPACITY` (or an error).
pub const FLAG_SIZING_ONLY: u32 = 4;

/// Slab header flags (docs/SLAB.md).
pub const SLAB_OWNING: u32 = 1;
pub const SLAB_CERTIFIED: u32 = 8;

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

pub(crate) struct TypeDesc {
    pub(crate) fields: Box<[FieldDesc]>,
    /// Ordinals of the repeated and map fields, ascending.
    lists: Box<[u32]>,
    lookup: Box<[u16]>,
    pub(crate) record_bytes: u32,
    pub(crate) words: u32,
    members: Box<[Box<[u32]>]>,
    required: Box<[u32]>,
    map_entry: bool,
    value_message: bool,
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
        types.push(TypeDesc {
            fields: descs.into_boxed_slice(),
            lists: lists.into_boxed_slice(),
            lookup: lookup.into_boxed_slice(),
            record_bytes,
            words,
            members: members.into_boxed_slice(),
            required: required.into_boxed_slice(),
            map_entry,
            value_message,
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
    Inline(usize),
    Heap(usize),
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
                let mark = Mark::Inline(self.top);
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
        let mark = Mark::Heap(self.heap_top);
        self.heap[self.heap_top..self.heap_top + words].fill(0);
        let at = self.heap[self.heap_top..].as_mut_ptr().cast::<u8>();
        self.heap_top += words;
        Ok((at, mark))
    }

    fn pop(&mut self, mark: Mark) {
        match mark {
            Mark::Output => {}
            Mark::Inline(top) => self.top = top,
            Mark::Heap(top) => self.heap_top = top,
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
        }
    }
}

// ---- Decoder ----

struct Open {
    ty: usize,
    at: u32,
    base: *mut u8,
    mark: Mark,
    pending: usize,
    merges: usize,
    unknown: usize,
    invalid_map: bool,
    merged: bool,
    key: (u32, u32),
}

struct Closed {
    at: u32,
    invalid_map: bool,
    klen: u32,
    key: u64,
    /// Offset of a group's end tag (the end of its body); the body end otherwise.
    end: usize,
}

/// Input reads at position `p`, returning the value and the next position (by value, so the
/// position stays in a register). `end <= input.len()` is checked once per record body.
#[inline(always)]
fn get_varint(input: &[u8], p: usize, end: usize) -> Result<(u64, usize)> {
    if p + 1 < end {
        // SAFETY: p + 1 < end <= input.len().
        let (b, c) = unsafe { (*input.get_unchecked(p), *input.get_unchecked(p + 1)) };
        if b < 0x80 {
            return Ok((b as u64, p + 1));
        }
        if c < 0x80 {
            return Ok(((b & 0x7F) as u64 | (c as u64) << 7, p + 2));
        }
    } else if p < end && input[p] < 0x80 {
        return Ok((input[p] as u64, p + 1));
    }
    get_varint_slow(input, p, end)
}

/// The reference rule: at most ten bytes; the tenth may only be 0 or 1.
#[inline(never)]
fn get_varint_slow(input: &[u8], mut p: usize, end: usize) -> Result<(u64, usize)> {
    let mut value = 0u64;
    let mut shift = 0u32;
    loop {
        if p >= end {
            return Err(MALFORMED);
        }
        let b = input[p];
        p += 1;
        if shift == 63 && b & 0xFE != 0 {
            return Err(MALFORMED);
        }
        value |= ((b & 0x7F) as u64) << shift;
        if b & 0x80 == 0 {
            return Ok((value, p));
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
fn get_scalar(op: u8, input: &[u8], p: usize, end: usize) -> Result<(u64, usize)> {
    Ok(match op {
        OP_VARINT32 => {
            let (v, p) = get_varint(input, p, end)?;
            (v as u32 as u64, p)
        }
        OP_VARINT64 => get_varint(input, p, end)?,
        OP_SINT32 => {
            let (n, p) = get_varint(input, p, end)?;
            let n = n as u32;
            (((n >> 1) as i32 ^ -((n & 1) as i32)) as u32 as u64, p)
        }
        OP_SINT64 => {
            let (n, p) = get_varint(input, p, end)?;
            (((n >> 1) as i64 ^ -((n & 1) as i64)) as u64, p)
        }
        OP_BOOL => {
            let (v, p) = get_varint(input, p, end)?;
            ((v != 0) as u64, p)
        }
        OP_FIXED32 => {
            if end - p < 4 {
                return Err(MALFORMED);
            }
            (u32::from_le_bytes(input[p..p + 4].try_into().unwrap()) as u64, p + 4)
        }
        _ => {
            if end - p < 8 {
                return Err(MALFORMED);
            }
            (u64::from_le_bytes(input[p..p + 8].try_into().unwrap()), p + 8)
        }
    })
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
        if n >= 8 && n <= 16 {
            let a = src.as_ptr().cast::<[u8; 8]>().read_unaligned();
            let b = src.as_ptr().add(n - 8).cast::<[u8; 8]>().read_unaligned();
            dst.cast::<[u8; 8]>().write_unaligned(a);
            dst.add(n - 8).cast::<[u8; 8]>().write_unaligned(b);
        } else if n >= 4 && n < 8 {
            let a = src.as_ptr().cast::<[u8; 4]>().read_unaligned();
            let b = src.as_ptr().add(n - 4).cast::<[u8; 4]>().read_unaligned();
            dst.cast::<[u8; 4]>().write_unaligned(a);
            dst.add(n - 4).cast::<[u8; 4]>().write_unaligned(b);
        } else if n < 4 {
            for i in 0..n {
                *dst.add(i) = src[i];
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
    walking: u32,
    /// Input offset of the field being decoded (the string payload for invalid UTF-8).
    pub(crate) error_at: usize,
    frame_bound: usize,
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
    #[inline]
    fn alloc(&mut self, size: usize, alignment: usize) -> Result<usize> {
        let at = (self.cursor + alignment - 1) & !(alignment - 1);
        let end = at + size;
        if end > self.max && self.walking == 0 {
            return Err(BUDGET);
        }
        if at <= self.limit && self.cursor < at {
            if self.cursor + 8 <= self.limit {
                // At most 7 padding bytes: one 8-byte zero store from the old cursor. Bytes past
                // `at` belong to this allocation, which its caller writes in full.
                write64(unsafe { self.out.add(self.cursor) }, 0);
            } else {
                for i in self.cursor..at {
                    // SAFETY: [cursor, at) lies inside the caller's `limit` writable bytes.
                    unsafe { *self.out.add(i) = 0 };
                }
            }
        }
        self.cursor = end;
        Ok(at)
    }

    #[inline]
    fn fits(&self, at: usize, size: usize) -> bool {
        at + size <= self.limit
    }

    #[inline(always)]
    fn open(&mut self, ty: usize, depth: u32) -> Result<Open> {
        if depth > self.max_depth {
            return Err(BUDGET);
        }
        let c = self.c;
        let size = c.types[ty].record_bytes as usize;
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
        Ok(Open {
            ty,
            at: offset(at),
            base,
            mark,
            pending: self.pending.len(),
            merges: self.merges.len(),
            unknown: self.unknown.len(),
            invalid_map: false,
            merged: false,
            key: (0, 0),
        })
    }

    #[inline(always)]
    fn record(&mut self, ty: usize, end: usize, group: u32, depth: u32) -> Result<Closed> {
        let mut open = self.open(ty, depth)?;
        let body_end = self.body(&mut open, end, group, depth)?;
        self.finish(open, depth, body_end)
    }

    #[inline(always)]
    fn finish(&mut self, mut open: Open, depth: u32, end: usize) -> Result<Closed> {
        let t = &self.c.types[open.ty];
        if open.merged
            || t.value_message
            || !t.required.is_empty()
            || self.pending.len() > open.pending
            || self.unknown.len() > open.unknown
        {
            self.close(&mut open, depth)?;
        } else {
            self.merges.truncate(open.merges);
        }
        let (klen, key) = if t.map_entry {
            let f = &t.fields[0];
            let present = read32(unsafe { open.base.add(4) }) & 1 != 0;
            match f.op {
                OP_STRING | OP_BYTES if present => (open.key.1, open.key.0 as u64),
                OP_STRING | OP_BYTES => (0, 0),
                _ if !present => (NONE, 0),
                _ if f.width == 8 => (NONE, unsafe {
                    u64::from_le_bytes(open.base.add(f.slot as usize).cast::<[u8; 8]>().read_unaligned())
                }),
                _ => (NONE, read32(unsafe { open.base.add(f.slot as usize) }) as u64),
            }
        } else {
            (NONE, 0)
        };
        self.frames.pop(open.mark);
        Ok(Closed {
            at: open.at,
            invalid_map: open.invalid_map,
            klen,
            key,
            end,
        })
    }

    #[inline(always)]
    fn varint(&mut self, end: usize) -> Result<u64> {
        let (v, p) = get_varint(self.input, self.pos, end)?;
        self.pos = p;
        Ok(v)
    }

    #[inline(always)]
    fn tag(&mut self, end: usize) -> Result<u32> {
        let (v, p) = get_tag(self.input, self.pos, end)?;
        self.pos = p;
        Ok(v)
    }

    #[inline(always)]
    fn length(&mut self, end: usize) -> Result<usize> {
        let (v, p) = get_length(self.input, self.pos, end)?;
        self.pos = p;
        Ok(v)
    }

    #[inline(always)]
    fn fixed(&mut self, n: usize, end: usize) -> Result<u64> {
        let (v, p) = get_scalar(if n == 4 { OP_FIXED32 } else { OP_FIXED64 }, self.input, self.pos, end)?;
        self.pos = p;
        Ok(v)
    }

    fn skip(&mut self, tag: u32, end: usize, depth: u32) -> Result<()> {
        match tag & 7 {
            0 => {
                self.varint(end)?;
            }
            1 => {
                self.fixed(8, end)?;
            }
            2 => {
                let n = self.length(end)?;
                self.pos += n;
            }
            3 => {
                if depth >= self.max_depth {
                    return Err(BUDGET);
                }
                loop {
                    if self.pos >= end {
                        return Err(MALFORMED);
                    }
                    let child = self.tag(end)?;
                    if child & 7 == 4 {
                        if child >> 3 != tag >> 3 {
                            return Err(MALFORMED);
                        }
                        return Ok(());
                    }
                    self.skip(child, end, depth + 1)?;
                }
            }
            5 => {
                self.fixed(4, end)?;
            }
            _ => return Err(MALFORMED),
        }
        Ok(())
    }

    #[inline]
    fn keep_unknown(&mut self, start: usize, end: usize) -> Result<()> {
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
        self.unknown.extend(&bytes[..=n])
    }

    /// Validate and place one string or bytes header with its owned payload.
    #[inline(never)]
    fn string(&mut self, start: usize, n: usize, text: bool) -> Result<u32> {
        let input = self.input;
        let bytes = &input[start..start + n];
        let (units, ascii) = if text {
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
    fn select(&mut self, o: &Open, t: &TypeDesc, ordinal: usize) {
        let oneof = t.fields[ordinal].oneof;
        if oneof == NONE {
            return;
        }
        self.select_slow(o, t, ordinal, oneof);
    }

    #[inline(never)]
    fn select_slow(&mut self, o: &Open, t: &TypeDesc, ordinal: usize, oneof: u32) {
        for &member in t.members[oneof as usize].iter() {
            let m = member as usize;
            if m == ordinal {
                continue;
            }
            Self::set_presence(o.base, m, false);
            let f = &t.fields[m];
            let slot = unsafe { o.base.add(f.slot as usize) };
            if f.width == 8 {
                write64(slot, 0);
            } else {
                write32(slot, 0);
            }
            for i in o.merges..self.merges.len() {
                let mut payload = self.merges.get(i);
                if payload.ordinal == member {
                    payload.ordinal = NONE;
                    self.merges.set(i, payload);
                }
            }
        }
        let case = unsafe { o.base.add(4 + 4 * t.words as usize + 2 * oneof as usize) };
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

    /// One record body: fields in wire order until `end` or the group's end tag. The read
    /// position and the first presence word live in locals; both are synchronized with the
    /// decoder and the record around every call that may read or change them.
    fn body(&mut self, o: &mut Open, end: usize, group: u32, depth: u32) -> Result<usize> {
        let c = self.c;
        let t = &c.types[o.ty];
        let fields = &t.fields[..];
        let input = self.input;
        assert!(end <= input.len());
        let base = o.base;
        // SAFETY: a record with fields has presence word 0 at offset 4 (a record without fields
        // has none, and the word is then never read or written).
        let word0 = unsafe { base.add(4) };
        let mut presence = if fields.is_empty() { 0 } else { read32(word0) };
        let mut pos = self.pos;
        let mut next = 0usize;
        macro_rules! sync {
            () => {
                if !fields.is_empty() {
                    write32(word0, presence);
                }
                self.pos = pos;
            };
        }
        macro_rules! resync {
            () => {
                if !fields.is_empty() {
                    presence = read32(word0);
                }
                pos = self.pos;
            };
        }
        macro_rules! tri {
            ($e:expr, $at:expr) => {
                match $e {
                    Ok(value) => value,
                    Err(code) => {
                        sync!();
                        return Err(self.fail(code, $at));
                    }
                }
            };
        }
        while pos < end {
            let start = pos;
            let (raw, after) = tri!(get_varint(input, pos, end), start);
            pos = after;
            // Wire-order prediction: writers emit fields in number order. A match with the next
            // declared field's tag needs no further tag checks (declared tags are valid).
            let ordinal = match fields.get(next) {
                Some(f) if f.tag as u64 == raw => next,
                _ => match {
                    if raw > u32::MAX as u64 || raw >> 3 == 0 || raw & 7 > 5 {
                        sync!();
                        return Err(self.fail(MALFORMED, start));
                    }
                    let tag = raw as u32;
                    if tag & 7 == 4 {
                        sync!();
                        if tag >> 3 != group {
                            return Err(self.fail(MALFORMED, start));
                        }
                        return Ok(start);
                    }
                    t.find(tag >> 3)
                } {
                    Some(ordinal) => ordinal,
                    None => {
                        sync!();
                        tri!(self.skip(raw as u32, end, depth), start);
                        tri!(self.keep_unknown(start, self.pos), start);
                        resync!();
                        continue;
                    }
                },
            };
            let tag = raw as u32;
            let f = &fields[ordinal];
            if f.tag != tag {
                sync!();
                if tag & 7 == 2 && f.packable {
                    let after = tri!(self.packed(f, ordinal, pos, end), start);
                    self.pos = after;
                    next = ordinal;
                } else {
                    tri!(self.skip(tag, end, depth), start);
                    tri!(self.keep_unknown(start, self.pos), start);
                }
                resync!();
                continue;
            }
            next = ordinal + usize::from(!f.repeated);
            match f.op {
                OP_MESSAGE | OP_GROUP => {
                    sync!();
                    tri!(self.message(o, t, ordinal, start, end, depth), start);
                    resync!();
                }
                OP_STRING | OP_BYTES => {
                    let (n, at) = tri!(get_length(input, pos, end), start);
                    pos = at + n;
                    let header = tri!(self.string(at, n, f.op == OP_STRING), start);
                    let present = f.explicit || n != 0;
                    if f.special {
                        sync!();
                        tri!(self.string_special(o, t, ordinal, at, n, header), start);
                        resync!();
                    } else {
                        write32(unsafe { base.add(f.slot as usize) }, header);
                        if ordinal < 32 {
                            let bit = 1u32 << ordinal;
                            presence = if present { presence | bit } else { presence & !bit };
                        } else {
                            Self::set_presence(base, ordinal, present);
                        }
                    }
                }
                op => {
                    let (bits, after) = tri!(get_scalar(op, input, pos, end), start);
                    pos = after;
                    if f.special {
                        sync!();
                        tri!(self.scalar_special(o, t, ordinal, start, pos, bits), start);
                        resync!();
                    } else {
                        let slot = unsafe { base.add(f.slot as usize) };
                        let nonzero = if f.width == 8 {
                            write64(slot, bits);
                            bits != 0
                        } else {
                            write32(slot, bits as u32);
                            bits as u32 != 0
                        };
                        let present = f.explicit || nonzero;
                        if ordinal < 32 {
                            let bit = 1u32 << ordinal;
                            presence = if present { presence | bit } else { presence & !bit };
                        } else {
                            Self::set_presence(base, ordinal, present);
                        }
                    }
                }
            }
        }
        sync!();
        if group != 0 {
            return Err(self.fail(MALFORMED, pos));
        }
        Ok(end)
    }

    /// A string or bytes field that is repeated, a oneof member or a map entry's key.
    #[inline(never)]
    fn string_special(
        &mut self,
        o: &mut Open,
        t: &TypeDesc,
        ordinal: usize,
        at: usize,
        n: usize,
        header: u32,
    ) -> Result<()> {
        let f = &t.fields[ordinal];
        if t.map_entry && ordinal == 0 {
            o.key = (at as u32, n as u32);
        }
        if f.repeated {
            return self.pending.push(Pending {
                ordinal: ordinal as u32,
                klen: 0,
                value: header as u64,
                key: 0,
            });
        }
        self.select(o, t, ordinal);
        write32(unsafe { o.base.add(f.slot as usize) }, header);
        Self::set_presence(o.base, ordinal, f.explicit || n != 0);
        Ok(())
    }

    /// A scalar that is repeated, a oneof member or a closed enum (or a map entry's enum value).
    #[inline(never)]
    fn scalar_special(
        &mut self,
        o: &mut Open,
        t: &TypeDesc,
        ordinal: usize,
        start: usize,
        end: usize,
        bits: u64,
    ) -> Result<()> {
        let f = &t.fields[ordinal];
        if f.closed && !f.values.accepts(bits as i32) {
            if f.map_value_enum {
                o.invalid_map = true;
            }
            return self.keep_unknown(start, end);
        }
        if f.map_value_enum {
            o.invalid_map = false;
        }
        if f.repeated {
            return self.pending.push(Pending {
                ordinal: ordinal as u32,
                klen: 0,
                value: bits,
                key: 0,
            });
        }
        self.select(o, t, ordinal);
        let slot = unsafe { o.base.add(f.slot as usize) };
        let present = if f.width == 8 {
            write64(slot, bits);
            bits != 0
        } else {
            write32(slot, bits as u32);
            bits as u32 != 0
        };
        Self::set_presence(o.base, ordinal, f.explicit || present);
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

    #[inline(never)]
    fn message(
        &mut self,
        o: &mut Open,
        t: &TypeDesc,
        ordinal: usize,
        start: usize,
        end: usize,
        depth: u32,
    ) -> Result<()> {
        let f = &t.fields[ordinal];
        if depth >= self.max_depth {
            return Err(BUDGET);
        }
        let (child_end, group) = if f.op == OP_MESSAGE {
            let n = self.length(end)?;
            (self.pos + n, 0)
        } else {
            (end, f.tag >> 3)
        };
        let body = self.pos;
        let child = f.child as usize;
        if f.repeated {
            let closed = self.record(child, child_end, group, depth + 1)?;
            if f.map && closed.invalid_map {
                return self.keep_unknown(start, self.pos);
            }
            return self.pending.push(Pending {
                ordinal: ordinal as u32,
                klen: closed.klen,
                value: closed.at as u64,
                key: closed.key,
            });
        }
        self.select(o, t, ordinal);
        let slot = unsafe { o.base.add(f.slot as usize) };
        let payload_end = if read32(slot) == 0 {
            let closed = self.record(child, child_end, group, depth + 1)?;
            write32(slot, closed.at);
            Self::set_presence(o.base, ordinal, true);
            closed.end
        } else {
            // A later occurrence: validate it with a walk that writes nothing (docs/SLAB.md).
            o.merged = true;
            self.walk(child, child_end, group, depth + 1)?
        };
        self.merges.push(Payload {
            ordinal: ordinal as u32,
            start: body as u32,
            end: payload_end as u32,
        })
    }

    fn walk(&mut self, ty: usize, end: usize, group: u32, depth: u32) -> Result<usize> {
        let saved = (
            self.cursor,
            self.records,
            self.handles,
            self.certified,
            self.limit,
            self.table.len(),
        );
        self.limit = 0;
        self.walking += 1;
        let result = self.record(ty, end, group, depth);
        self.walking -= 1;
        (
            self.cursor,
            self.records,
            self.handles,
            self.certified,
            self.limit,
        ) = (saved.0, saved.1, saved.2, saved.3, saved.4);
        self.table.truncate(saved.5);
        result.map(|closed| closed.end)
    }

    /// Record close, in docs/SLAB.md order: merges, map value default, lists, unknown span,
    /// required fields.
    fn close(&mut self, o: &mut Open, depth: u32) -> Result<()> {
        let c = self.c;
        let t = &c.types[o.ty];
        let resume = self.pos;
        if o.merged {
            self.merge(o, t, depth)?;
        }
        if t.value_message {
            let f = &t.fields[1];
            let slot = unsafe { o.base.add(f.slot as usize) };
            if read32(slot) == 0 {
                if depth >= self.max_depth {
                    return Err(BUDGET);
                }
                let open = self.open(f.child as usize, depth + 1)?;
                let closed = self.finish(open, depth + 1, self.pos)?;
                write32(slot, closed.at);
                Self::set_presence(o.base, 1, true);
            }
        }
        if self.pending.len() > o.pending {
            self.lists(o, t)?;
        }
        if self.unknown.len() > o.unknown {
            let n = self.unknown.len() - o.unknown;
            let at = self.alloc(4 + n, 4)?;
            if self.fits(at, 4 + n) {
                let bytes = &self.unknown.as_slice()[o.unknown..];
                // SAFETY: the span lies inside the output's writable prefix.
                unsafe {
                    let p = self.out.add(at);
                    write32(p, n as u32);
                    std::ptr::copy_nonoverlapping(bytes.as_ptr(), p.add(4), n);
                }
            }
            self.unknown.truncate(o.unknown);
            self.table.push((o.at, offset(at)))?;
        }
        for &ordinal in t.required.iter() {
            if !Self::present(o.base, ordinal as usize) {
                self.certified = false;
            }
        }
        self.pending.truncate(o.pending);
        self.merges.truncate(o.merges);
        self.pos = resume;
        Ok(())
    }

    /// Singular messages seen more than once (ordinal order): one record decoded from the
    /// concatenation of every payload since the last clear, here fed payload by payload.
    #[inline(never)]
    fn merge(&mut self, o: &mut Open, t: &TypeDesc, depth: u32) -> Result<()> {
        let (first, last) = (o.merges, self.merges.len());
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
            if count < 2 {
                continue;
            }
            let f = &t.fields[ordinal as usize];
            let mut open = self.open(f.child as usize, depth + 1)?;
            for i in first..last {
                let p = self.merges.get(i);
                if p.ordinal == ordinal {
                    self.pos = p.start as usize;
                    self.body(&mut open, p.end as usize, 0, depth + 1)?;
                }
            }
            let closed = self.finish(open, depth + 1, self.pos)?;
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
    fn lists(&mut self, o: &mut Open, t: &TypeDesc) -> Result<()> {
        const DENSE_LISTS: usize = 16;
        let (first, last) = (o.pending, self.pending.len());
        if t.lists.len() > DENSE_LISTS {
            return self.lists_sparse(o, t);
        }
        for &ordinal in t.lists.iter() {
            if t.fields[ordinal as usize].map {
                self.dedup(first, last, ordinal)?;
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
            let count = counts[index] as usize;
            if count == 0 {
                continue;
            }
            let f = &t.fields[ordinal as usize];
            let size = 8 + count * f.width as usize;
            let at = self.alloc(size, 8)?;
            if self.fits(at, size) {
                // SAFETY: the list lies inside the output's writable prefix.
                let p = unsafe { self.out.add(at) };
                write32(p, count as u32);
                write32(unsafe { p.add(4) }, 0);
                cursors[index] = at + 8;
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
    fn lists_sparse(&mut self, o: &mut Open, t: &TypeDesc) -> Result<()> {
        let (first, last) = (o.pending, self.pending.len());
        let mut floor = 0u32;
        loop {
            let mut ordinal = NONE;
            for i in first..last {
                let p = self.pending.get(i);
                if p.ordinal >= floor && p.ordinal < ordinal {
                    ordinal = p.ordinal;
                }
            }
            if ordinal == NONE {
                return Ok(());
            }
            floor = ordinal + 1;
            let f = &t.fields[ordinal as usize];
            if f.map {
                self.dedup(first, last, ordinal)?;
            }
            let mut count = 0usize;
            for i in first..last {
                count += usize::from(self.pending.get(i).ordinal == ordinal);
            }
            if count == 0 {
                continue;
            }
            let w = f.width as usize;
            let size = 8 + count * w;
            let at = self.alloc(size, 8)?;
            if self.fits(at, size) {
                // SAFETY: the list lies inside the output's writable prefix.
                let p = unsafe { self.out.add(at) };
                write32(p, count as u32);
                write32(unsafe { p.add(4) }, 0);
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
    }

    /// Fold later entries with an equal key into the first entry of that key (which keeps its
    /// position and takes the later record); folded entries are dropped from the list.
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
    *error_at = 0;
    if flags & FLAG_OWNING == 0 || flags & !(FLAG_OWNING | FLAG_SIZING_ONLY) != 0 {
        // Borrowing slabs (Phase D) and LAX projection (Phase E) are not produced yet.
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
        walking: 0,
        error_at: usize::MAX,
        frame_bound,
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
    let root = d.record(0, d.input.len(), 0, 0)?;
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
    let flags = SLAB_OWNING | if d.certified { SLAB_CERTIFIED } else { 0 };
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
