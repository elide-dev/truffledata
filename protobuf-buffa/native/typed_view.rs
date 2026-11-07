//! ABI 9 binary views. A plan compiles its schema program once into checked native descriptors;
//! each decode then reads no schema bytes. The result is one native allocation holding the
//! typed index followed by per-message field directories, so Java never rediscovers fields.
//!
//! Directory layout (u32 words after `count` 24-byte entries): every message instance owns one
//! word per schema field, in pre-order, starting with the root at word 0. A singular word is the
//! last occurrence's entry index plus one (zero: absent). A repeated word is zero when absent,
//! otherwise the word offset of `[count, entry indices...]`. A message entry's `value` is the
//! word offset of its own directory. Decoding reuses retained scratch; the result is copied into
//! one exact allocation, or the scratch itself is transferred when only it fits the budget.
use super::super::{ALLOCATION, BUDGET, Field, MALFORMED, PANIC};
use super::{Schema, scalar, take_bytes, varint, wire};
use buffa::encoding::Tag;
use std::mem::size_of;

type Result<T> = std::result::Result<T, i32>;

const MAGIC: u64 = 0x3957_4549_5642_4454; // "TDBVIEW9", little-endian
const DIRECT_LOOKUP: u32 = 2048;
const ENTRY_WORDS: usize = size_of::<Field>() / size_of::<u32>();

enum Values {
    Any,
    Range(i32, i32),
    Sorted(Box<[i32]>),
}

struct FieldDesc {
    kind: u32,
    wire: u32,
    repeated: bool,
    child: usize,
    values: Values,
}

impl FieldDesc {
    fn accepts(&self, value: u64) -> bool {
        let value = value as i32;
        match &self.values {
            Values::Any => true,
            Values::Range(low, high) => *low <= value && value <= *high,
            Values::Sorted(values) => values.binary_search(&value).is_ok(),
        }
    }
}

struct TypeDesc {
    fields: Box<[FieldDesc]>,
    numbers: Box<[u32]>,
    lookup: Box<[u8]>,
    certified: bool,
}

impl TypeDesc {
    /// A direct table covers every declared number when all are small; otherwise first match.
    #[inline]
    fn find(&self, number: u32) -> Option<usize> {
        if self.lookup.is_empty() {
            return self.numbers.iter().position(|&n| n == number);
        }
        match self.lookup.get(number as usize) {
            Some(&ordinal) if ordinal != 0 => Some(ordinal as usize - 1),
            _ => None,
        }
    }
}

/// Immutable compiled schema, owned by a Java plan through an explicit handle.
pub struct Compiled {
    magic: u64,
    types: Box<[TypeDesc]>,
}

/// Compile the same program used by text/JSON decoding. Views require at most 64 fields per type
/// (Java's eligibility rule) and no map entries; other programs are rejected rather than guessed.
pub fn compile(bytes: &[u8]) -> Result<Compiled> {
    let schema = Schema(bytes);
    if schema.word(0)? != 0x54444233 {
        return Err(MALFORMED);
    }
    let count = schema.word(4)?;
    let mut types = Vec::new();
    types.try_reserve_exact(count as usize).map_err(|_| ALLOCATION)?;
    for ty in 0..count {
        let base = schema.ty(ty)?;
        let size = schema.word(base)?;
        let flags = schema.word(base + 4)?;
        schema.table(base, size)?;
        if size > 64 || flags & 1 != 0 {
            return Err(MALFORMED);
        }
        let mut fields = Vec::new();
        fields.try_reserve_exact(size as usize).map_err(|_| ALLOCATION)?;
        let mut numbers = Vec::new();
        numbers.try_reserve_exact(size as usize).map_err(|_| ALLOCATION)?;
        for ordinal in 0..size {
            let f = schema.field(ty, ordinal)?;
            let kind = f.kind()?;
            let child = if kind == 10 || kind == 11 {
                let child = f.word(12)?;
                if child >= count {
                    return Err(MALFORMED);
                }
                child as usize
            } else {
                usize::MAX
            };
            let values = if kind == 14 && f.word(8)? & 16 == 0 {
                let table = f.word(16)? as usize;
                let n = f.word(20)? as usize;
                let mut values = Vec::new();
                values.try_reserve_exact(n).map_err(|_| ALLOCATION)?;
                for i in 0..n {
                    let at = table
                        .checked_add(i.checked_mul(12).ok_or(MALFORMED)?)
                        .ok_or(MALFORMED)?;
                    values.push(schema.word(at)? as i32);
                }
                values.sort_unstable();
                values.dedup();
                match (values.first(), values.last()) {
                    (Some(&low), Some(&high))
                        if (high as i64 - low as i64) as usize + 1 == values.len() =>
                    {
                        Values::Range(low, high)
                    }
                    _ => Values::Sorted(values.into_boxed_slice()),
                }
            } else {
                Values::Any
            };
            numbers.push(f.word(0)?);
            fields.push(FieldDesc {
                kind,
                wire: wire(kind)?,
                repeated: f.repeated()?,
                child,
                values,
            });
        }
        // First declaration wins, exactly like the interpreted linear search.
        let largest = numbers.iter().copied().max().unwrap_or(0);
        let mut lookup = Vec::new();
        if largest <= DIRECT_LOOKUP {
            lookup
                .try_reserve_exact(largest as usize + 1)
                .map_err(|_| ALLOCATION)?;
            lookup.resize(largest as usize + 1, 0u8);
            for (ordinal, &number) in numbers.iter().enumerate().rev() {
                lookup[number as usize] = ordinal as u8 + 1;
            }
        }
        types.push(TypeDesc {
            fields: fields.into_boxed_slice(),
            numbers: numbers.into_boxed_slice(),
            lookup: lookup.into_boxed_slice(),
            // Mirrors typed.rs: bit 9 states that the type has no required fields.
            certified: flags & 512 != 0,
        });
    }
    if types.is_empty() {
        return Err(MALFORMED);
    }
    Ok(Compiled {
        magic: MAGIC,
        types: types.into_boxed_slice(),
    })
}

impl Compiled {
    pub fn checked(&self) -> Result<&Self> {
        if self.magic == MAGIC {
            Ok(self)
        } else {
            Err(MALFORMED)
        }
    }
}

struct Decoder<'a> {
    types: &'a [TypeDesc],
    input: &'a [u8],
    fields: Vec<Field>,
    cells: u64,
    strings: u64,
    depth: u32,
    native: u64,
    words: u64,
    certified: bool,
}

impl<'a> Decoder<'a> {
    #[inline]
    fn charge(&mut self, n: u64) -> Result<()> {
        self.cells = self.cells.checked_sub(n).ok_or(BUDGET)?;
        Ok(())
    }

    /// Identical growth and budget policy to the interpreted decoder's index.
    #[inline]
    fn push(&mut self, ordinal: usize, kind: u32, value: u64, length: u64) -> Result<usize> {
        self.charge(1)?;
        let i = self.fields.len();
        if i == self.fields.capacity() {
            let maximum = (self.native / size_of::<Field>() as u64) as usize;
            let capacity = i.saturating_mul(2).max(4).min(maximum.saturating_sub(i));
            if capacity <= i {
                return Err(BUDGET);
            }
            self.fields
                .try_reserve_exact(capacity - i)
                .map_err(|_| ALLOCATION)?;
        }
        self.fields.push(Field {
            number: ordinal as u32 + 1,
            wire: kind,
            value,
            length: super::super::index_length(length)?,
            end: (i + 1) as u32,
        });
        Ok(i)
    }

    #[inline]
    fn repeated(&mut self, seen: &mut u64, ordinal: usize) {
        let bit = 1u64 << ordinal;
        self.words += 1 + u64::from(*seen & bit == 0);
        *seen |= bit;
    }

    fn bytes(&mut self, kind: u32, bytes: &[u8]) -> Result<()> {
        if kind == 9 {
            let n = 2 * super::super::unicode::utf16_length(bytes)? as u64;
            self.strings = self.strings.checked_sub(n).ok_or(BUDGET)?;
        } else {
            self.charge(bytes.len() as u64)?;
        }
        Ok(())
    }

    /// Mirrors typed.rs `Decoder::binary`; compilation rejected map entries (no invalid-map flag).
    fn binary(&mut self, ty: usize, input: &mut &'a [u8], group: u32, depth: u32) -> Result<()> {
        if depth > self.depth {
            return Err(BUDGET);
        }
        let types = self.types;
        let t = &types[ty];
        let count = t.fields.len();
        self.charge(count as u64)?;
        self.certified &= t.certified;
        self.words += count as u64;
        let mut seen_messages = 0u64;
        let mut seen_repeated = 0u64;
        while !input.is_empty() {
            let tag = Tag::decode(input).map_err(|_| MALFORMED)?;
            let number = tag.field_number();
            let wt = tag.wire_type() as u32;
            if wt == 4 {
                return if group == number && group != 0 {
                    Ok(())
                } else {
                    Err(MALFORMED)
                };
            }
            let Some(ordinal) = t.find(number) else {
                skip(input, number, wt, depth, self.depth)?;
                continue;
            };
            let f = &t.fields[ordinal];
            let kind = f.kind;
            if f.repeated && !(9..=12).contains(&kind) && wt == 2 {
                let mut packed = take_bytes(input)?;
                while !packed.is_empty() {
                    let value = scalar(kind, &mut packed)?;
                    if f.accepts(value) {
                        self.push(ordinal, kind, value, 0)?;
                        self.repeated(&mut seen_repeated, ordinal);
                    }
                }
                continue;
            }
            if f.wire != wt {
                skip(input, number, wt, depth, self.depth)?;
                continue;
            }
            if kind == 10 || kind == 11 {
                if f.repeated {
                    self.repeated(&mut seen_repeated, ordinal);
                } else {
                    let bit = 1u64 << ordinal;
                    if seen_messages & bit != 0 {
                        self.certified = false;
                    }
                    seen_messages |= bit;
                }
                let i = self.push(ordinal, kind, 0, 0)?;
                if kind == 11 {
                    let mut nested = take_bytes(input)?;
                    self.binary(f.child, &mut nested, 0, depth + 1)?;
                } else {
                    self.binary(f.child, input, number, depth + 1)?;
                }
                self.fields[i].end = self.fields.len() as u32;
            } else if kind == 9 || kind == 12 {
                let bytes = take_bytes(input)?;
                self.bytes(kind, bytes)?;
                self.push(
                    ordinal,
                    kind,
                    (bytes.as_ptr() as usize - self.input.as_ptr() as usize) as u64,
                    bytes.len() as u64,
                )?;
                if f.repeated {
                    self.repeated(&mut seen_repeated, ordinal);
                }
            } else {
                let value = scalar(kind, input)?;
                if f.accepts(value) {
                    self.push(ordinal, kind, value, 0)?;
                    if f.repeated {
                        self.repeated(&mut seen_repeated, ordinal);
                    }
                }
            }
        }
        if group != 0 { Err(MALFORMED) } else { Ok(()) }
    }
}

fn skip(input: &mut &[u8], number: u32, wt: u32, depth: u32, maximum: u32) -> Result<()> {
    match wt {
        0 => {
            varint(input)?;
        }
        1 => {
            super::take(input, 8)?;
        }
        2 => {
            take_bytes(input)?;
        }
        5 => {
            super::take(input, 4)?;
        }
        3 => {
            if depth >= maximum {
                return Err(BUDGET);
            }
            loop {
                let t = Tag::decode(input).map_err(|_| MALFORMED)?;
                if t.wire_type() as u32 == 4 {
                    if t.field_number() != number {
                        return Err(MALFORMED);
                    }
                    break;
                }
                skip(input, t.field_number(), t.wire_type() as u32, depth + 1, maximum)?;
            }
        }
        _ => return Err(MALFORMED),
    }
    Ok(())
}

/// Fill one message's directory and, recursively, its children's. Returns its word offset.
fn directory(
    types: &[TypeDesc],
    entries: &mut [Field],
    words: &mut [u32],
    ty: usize,
    start: usize,
    end: usize,
    cursor: &mut usize,
) -> Result<u32> {
    let t = &types[ty];
    let base = *cursor;
    let n = t.fields.len();
    if base.checked_add(n).is_none_or(|last| last > words.len()) {
        return Err(PANIC);
    }
    *cursor += n;
    let mut i = start;
    while i < end {
        let e = entries[i];
        let ordinal = e.number as usize - 1;
        if t.fields[ordinal].repeated {
            words[base + ordinal] += 1; // count, replaced by the list offset below
        } else {
            words[base + ordinal] = i as u32 + 1;
        }
        i = e.end as usize;
    }
    for ordinal in 0..n {
        if t.fields[ordinal].repeated && words[base + ordinal] != 0 {
            let count = words[base + ordinal] as usize;
            let list = *cursor;
            *words.get_mut(list).ok_or(PANIC)? = 0; // fill position, ends at count
            *cursor = list.checked_add(count + 1).ok_or(PANIC)?;
            if *cursor > words.len() {
                return Err(PANIC);
            }
            words[base + ordinal] = list as u32;
        }
    }
    let mut i = start;
    while i < end {
        let e = entries[i];
        let ordinal = e.number as usize - 1;
        let f = &t.fields[ordinal];
        if f.repeated {
            let list = words[base + ordinal] as usize;
            let k = words[list] as usize;
            words[list + 1 + k] = i as u32;
            words[list] = k as u32 + 1;
        }
        if f.kind == 10 || f.kind == 11 {
            let child = directory(types, entries, words, f.child, i + 1, e.end as usize, cursor)?;
            entries[i].value = child as u64;
        }
        i = e.end as usize;
    }
    Ok(base as u32)
}

/// A completed view allocation: `Vec<Field>` storage of `capacity` entries, `count` of them
/// index entries followed by `words` directory words. Released by `release`.
pub struct View {
    pub data: *mut Field,
    pub count: usize,
    pub words: usize,
    pub capacity: usize,
}

/// # Safety
/// `data`/`capacity` come from one `View` not yet released.
pub unsafe fn release(data: *mut Field, capacity: usize) {
    if !data.is_null() && capacity != 0 {
        drop(unsafe { Vec::from_raw_parts(data, 0, capacity) });
    }
}

/// Write directories into `storage`'s spare capacity after its first `count` entries.
fn finish(types: &[TypeDesc], storage: &mut Vec<Field>, count: usize, words: usize) -> Result<()> {
    let slots = count + words.div_ceil(ENTRY_WORDS);
    if storage.capacity() < slots || storage.len() != count {
        return Err(PANIC);
    }
    let base = storage.as_mut_ptr();
    // SAFETY: both regions lie within the allocation's capacity and are disjoint. The word region
    // is zero-initialized before a slice exists; Field and u32 have no invalid bit patterns.
    let (entries, directory) = unsafe {
        let words_ptr = base.add(count).cast::<u32>();
        std::ptr::write_bytes(words_ptr, 0, (slots - count) * ENTRY_WORDS);
        (
            std::slice::from_raw_parts_mut(base, count),
            std::slice::from_raw_parts_mut(words_ptr, words),
        )
    };
    let mut cursor = 0;
    directory_root(types, entries, directory, &mut cursor)?;
    if cursor != words {
        return Err(PANIC);
    }
    Ok(())
}

fn directory_root(
    types: &[TypeDesc],
    entries: &mut [Field],
    words: &mut [u32],
    cursor: &mut usize,
) -> Result<()> {
    let count = entries.len();
    directory(types, entries, words, 0, 0, count, cursor).map(|_| ())
}

#[allow(clippy::too_many_arguments)]
fn attempt(
    compiled: &Compiled,
    input: &[u8],
    cells: u64,
    strings: u64,
    depth: u32,
    native: u64,
    mut fields: Vec<Field>,
    status: &mut i32,
    retry: &mut bool,
) -> (Result<View>, Vec<Field>) {
    fields.clear();
    let maximum = native / size_of::<Field>() as u64;
    let capacity = fields.capacity() as u64;
    if capacity > maximum || (capacity < maximum && (capacity < 4 || !capacity.is_power_of_two()))
    {
        fields = Vec::new();
    }
    let mut decoder = Decoder {
        types: &compiled.types,
        input,
        fields,
        cells,
        strings,
        depth,
        native,
        words: 0,
        certified: true,
    };
    if let Err(code) = decoder.binary(0, &mut &input[..], 0, 1) {
        return (Err(code), decoder.fields);
    }
    let certified = decoder.certified;
    let mut scratch = decoder.fields;
    let count = scratch.len();
    let words = decoder.words as usize;
    if count == 0 {
        // No field occurred: every directory word would be zero. Java treats an empty span as
        // all-absent, so an empty message needs no allocation and fits a zero budget.
        if certified {
            *status = 5;
        }
        let empty = View {
            data: std::ptr::null_mut(),
            count: 0,
            words: 0,
            capacity: 0,
        };
        return (Ok(empty), scratch);
    }
    let Some(slots) = words
        .checked_add(ENTRY_WORDS - 1)
        .map(|w| w / ENTRY_WORDS)
        .and_then(|w| w.checked_add(count))
    else {
        return (Err(BUDGET), scratch);
    };
    let bytes = |n: usize| (n as u64).saturating_mul(size_of::<Field>() as u64);
    let result = if bytes(scratch.capacity()).saturating_add(bytes(slots)) <= native {
        // Steady state: one exact allocation; the scratch index stays with the decoder.
        let mut out = Vec::new();
        if out.try_reserve_exact(slots).is_err() {
            return (Err(ALLOCATION), scratch);
        }
        out.extend_from_slice(&scratch);
        if let Err(code) = finish(&compiled.types, &mut out, count, words) {
            return (Err(code), scratch);
        }
        let mut out = std::mem::ManuallyDrop::new(out);
        View {
            data: out.as_mut_ptr(),
            count,
            words,
            capacity: out.capacity(),
        }
    } else if slots <= scratch.capacity() {
        // Only one allocation fits the budget: transfer the scratch index itself.
        if let Err(code) = finish(&compiled.types, &mut scratch, count, words) {
            return (Err(code), scratch);
        }
        let mut out = std::mem::ManuallyDrop::new(std::mem::take(&mut scratch));
        View {
            data: out.as_mut_ptr(),
            count,
            words,
            capacity: out.capacity(),
        }
    } else {
        // Only this storage decision depends on retained (rather than canonical) capacity.
        *retry = true;
        return (Err(BUDGET), scratch);
    };
    if certified {
        *status = 5;
    }
    (Ok(result), scratch)
}

/// Decode a binary view. `scratch` is retained decoder index storage; it is returned for reuse
/// (possibly emptied) on success and failure alike. Status 5 certifies, as for ABI 8 format 3,
/// that visited types have no required fields and no singular message needs merging.
#[allow(clippy::too_many_arguments)]
pub fn view(
    compiled: &Compiled,
    input: &[u8],
    cells: u64,
    strings: u64,
    depth: u32,
    native: u64,
    scratch: Vec<Field>,
    status: &mut i32,
) -> (Result<View>, Vec<Field>) {
    *status = 0;
    if depth == 0 || depth > 128 || native > i32::MAX as u64 {
        return (Err(BUDGET), scratch);
    }
    let reused = scratch.capacity() != 0;
    let mut retry = false;
    let (result, scratch) = attempt(
        compiled, input, cells, strings, depth, native, scratch, status, &mut retry,
    );
    if retry && reused {
        // Retained capacity must never reject an input that a fresh decoder accepts. Release it
        // first (keeping the peak accounting), then decode once more from empty scratch.
        drop(scratch);
        *status = 0;
        return attempt(
            compiled,
            input,
            cells,
            strings,
            depth,
            native,
            Vec::new(),
            status,
            &mut retry,
        );
    }
    (result, scratch)
}

#[cfg(test)]
#[path = "typed_view_tests.rs"]
mod tests;
