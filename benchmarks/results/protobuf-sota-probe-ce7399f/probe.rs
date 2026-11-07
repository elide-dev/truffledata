//! Temporary decode probe for the protobuf performance plan. Not production code: included
//! from lib.rs only while probing, retained under benchmarks/results/protobuf-probe-<sha>/.
//!
//! Variants over the consumer harness fixture (32 `google.protobuf.Type` messages, bytes
//! identical to ConsumerComparison's generator):
//!   scan_flat    structural walk, length-delimited fields opaque (the wire floor)
//!   scan_nested  structural walk recursing into every known message field
//!   view         production ABI 9 `typed::view` decode with scratch reuse and release
//!   hand         hand-written schema-specialized decoder into a flat u64 slab (spans into input)
//!   hand_novf    same without UTF-8 validation / UTF-16 sizing
//!   hand_ascii   same with an ASCII fast path before the production validator
//!   table        table-driven generic decoder into the same slab layout (full projection)
//!   table_sparse table-driven with parse-time projection (sparse consumer: name, counts,
//!                source_context, syntax; nested Field messages counted, not decoded)
//!   table_none   table-driven projecting nothing (validate-and-count only)
//!   varint_*     packed varint decoding strategies over 4,096 values
//!   slab         Task A2: the production ABI 10 slab decoder (slab.rs) into a reused buffer
//!   slab_small   the same through the allocation-free small entry point's fixed scratch
//!   hand_slab    SOTA probe: hand-written per-schema decoder writing the production TDSLAB1 format
//!                (payload copies, string headers, handles, zeroed records, lists)
//!   fast         SOTA probe: table-driven, explicit record stack, packed entries, predicted next tag
//!   fast2        the same with upb's state discipline (pointer cursors, record base pointer,
//!                hasbits in a register, one-word string return)
//!   fast_tail    fast2's handlers chained with `become` (explicit tail calls, upb's musttail shape)
#![allow(dead_code, static_mut_refs, unused_unsafe)]
use crate::Field;
use crate::typed::view::{compile, release, view};
use std::convert::TryInto;
use std::hint::black_box;
use std::time::Instant;

// ---------------------------------------------------------------- fixture

fn put_varint(out: &mut Vec<u8>, mut v: u64) {
    while v >= 0x80 {
        out.push((v as u8) | 0x80);
        v >>= 7;
    }
    out.push(v as u8);
}
fn put_tag(out: &mut Vec<u8>, number: u32, wt: u32) {
    put_varint(out, ((number << 3) | wt) as u64);
}
fn put_bytes(out: &mut Vec<u8>, number: u32, data: &[u8]) {
    put_tag(out, number, 2);
    put_varint(out, data.len() as u64);
    out.extend_from_slice(data);
}
fn put_int(out: &mut Vec<u8>, number: u32, v: u64) {
    put_tag(out, number, 0);
    put_varint(out, v);
}

/// One `google.protobuf.Type`, serialized in field-number order like protobuf-java.
fn type_message(i: usize, mode: &str) -> Vec<u8> {
    let count = match mode {
        "holdout" => 12 + i % 13,
        "large" => 2048 + (i % 8) * 64,
        _ => 3 + i % 6,
    };
    let mut out = Vec::new();
    put_bytes(&mut out, 1, format!("catalog-{i}-λ").as_bytes());
    for j in 0..count {
        let mut f = Vec::new();
        put_int(&mut f, 1, 3); // kind = TYPE_INT64
        put_int(&mut f, 2, 3); // cardinality = CARDINALITY_REPEATED
        put_int(&mut f, 3, (j + 1) as u64);
        let name = if mode == "holdout" {
            format!("column-{j}-λ😀")
        } else {
            format!("column-{j}")
        };
        put_bytes(&mut f, 4, name.as_bytes());
        if (i + j) % 3 != 0 {
            put_int(&mut f, 8, 1);
        }
        put_bytes(&mut out, 2, &f);
    }
    put_bytes(&mut out, 3, b"identity");
    if i & 1 == 0 {
        put_bytes(&mut out, 3, "contact-😀".as_bytes());
    }
    if i % 3 != 0 {
        let mut sc = Vec::new();
        put_bytes(&mut sc, 1, format!("schema-{i}.proto").as_bytes());
        put_bytes(&mut out, 5, &sc);
    }
    put_int(&mut out, 6, 1); // syntax = SYNTAX_PROTO3
    out
}

fn fixture(mode: &str) -> Vec<Vec<u8>> {
    (0..32).map(|i| type_message(i, mode)).collect()
}

// ---------------------------------------------------------------- schema program (NativeSchema layout)

type FieldSpec = (u32, u32, u32, u32);
fn program(types: &[(u32, &[FieldSpec])]) -> Vec<u8> {
    let mut words: Vec<u32> = vec![0x54444233, types.len() as u32];
    let mut offset = 8 + 4 * types.len();
    for (_, fields) in types {
        words.push(offset as u32);
        offset += 16 + 40 * fields.len();
    }
    let name = offset as u32;
    for (flags, fields) in types {
        words.extend([fields.len() as u32, *flags, name, 1]);
        for &(number, kind, field_flags, child) in fields.iter() {
            words.extend([number, kind, field_flags, child, 0, 0, name, 1, name, 1]);
        }
    }
    let mut bytes: Vec<u8> = words.iter().flat_map(|w| w.to_le_bytes()).collect();
    bytes.push(b'x');
    bytes
}

const NONE: u32 = u32::MAX;
// Kind codes are ProtoPlan.Kind.ordinal()+1: INT32=5, BOOL=8, STRING=9, MESSAGE=11, BYTES=12, ENUM=14.
// Field flags: 1 repeated, 16 open enum. Type flag 512: no required fields (certified).
fn type_program() -> Vec<u8> {
    program(&[
        // 0: Type
        (
            512,
            &[
                (1, 9, 0, NONE),
                (2, 11, 1, 1),
                (3, 9, 1, NONE),
                (4, 11, 1, 2),
                (5, 11, 0, 3),
                (6, 14, 16, NONE),
                (7, 9, 0, NONE),
            ],
        ),
        // 1: Field
        (
            512,
            &[
                (1, 14, 16, NONE),
                (2, 14, 16, NONE),
                (3, 5, 0, NONE),
                (4, 9, 0, NONE),
                (6, 9, 0, NONE),
                (7, 5, 0, NONE),
                (8, 8, 0, NONE),
                (9, 11, 1, 2),
                (10, 9, 0, NONE),
                (11, 9, 0, NONE),
            ],
        ),
        // 2: Option
        (512, &[(1, 9, 0, NONE), (2, 11, 0, 4)]),
        // 3: SourceContext
        (512, &[(1, 9, 0, NONE)]),
        // 4: Any
        (512, &[(1, 9, 0, NONE), (2, 12, 0, NONE)]),
    ])
}

/// `type_program()` plus the TDL1 layout section and trailer that NativeSchema appends.
fn slab_program() -> Vec<u8> {
    // (kind code, oneof) per field of Type, Field, Option, SourceContext, Any, in program order.
    let types: [&[(u32, u32)]; 5] = [
        &[(6, NONE), (16, NONE), (14, NONE), (16, NONE), (8, NONE), (1, NONE), (6, NONE)],
        &[(1, NONE), (1, NONE), (1, NONE), (6, NONE), (6, NONE), (1, NONE), (5, NONE), (16, NONE), (6, NONE), (6, NONE)],
        &[(6, NONE), (8, NONE)],
        &[(6, NONE)],
        &[(6, NONE), (7, NONE)],
    ];
    let mut tables: Vec<u32> = Vec::new();
    for fields in types {
        // No 8-byte slots and no oneofs in this closure: 4-byte slots right after presence.
        let n = fields.len() as u32;
        let words = n.div_ceil(32);
        let start = 4 + 4 * words;
        tables.extend([(start + 4 * n + 7) & !7, words, 0, n]);
        for (i, &(kind, oneof)) in fields.iter().enumerate() {
            tables.extend([start + 4 * i as u32, kind, oneof]);
        }
    }
    let table_bytes: Vec<u8> = tables.iter().flat_map(|w| w.to_le_bytes()).collect();
    let mut out = type_program();
    while out.len() % 4 != 0 {
        out.push(0);
    }
    let section = out.len() as u32;
    for w in [0x314C_4454u32, 1, 5, crate::slab::crc32(&table_bytes)] {
        out.extend_from_slice(&w.to_le_bytes());
    }
    out.extend(table_bytes);
    out.extend_from_slice(&section.to_le_bytes());
    out.extend_from_slice(&0x314C_4454u32.to_le_bytes());
    out
}

// ---------------------------------------------------------------- wire primitives

#[inline(always)]
fn read_varint(input: &[u8], pos: &mut usize) -> Result<u64, ()> {
    let b = *input.get(*pos).ok_or(())?;
    if b < 0x80 {
        *pos += 1;
        return Ok(b as u64);
    }
    let mut v = (b & 0x7f) as u64;
    let mut shift = 7;
    loop {
        *pos += 1;
        let b = *input.get(*pos).ok_or(())?;
        v |= ((b & 0x7f) as u64) << shift;
        if b < 0x80 {
            *pos += 1;
            return Ok(v);
        }
        shift += 7;
        if shift > 63 {
            return Err(());
        }
    }
}

#[inline(always)]
fn read_len(input: &[u8], pos: &mut usize) -> Result<(usize, usize), ()> {
    let n = read_varint(input, pos)? as usize;
    let start = *pos;
    let end = start.checked_add(n).ok_or(())?;
    if end > input.len() {
        return Err(());
    }
    *pos = end;
    Ok((start, n))
}

fn scan_flat(input: &[u8]) -> Result<u64, ()> {
    let mut pos = 0;
    let mut fields = 0u64;
    while pos < input.len() {
        let tag = read_varint(input, &mut pos)?;
        fields += 1;
        match tag & 7 {
            0 => {
                read_varint(input, &mut pos)?;
            }
            1 => pos += 8,
            2 => {
                read_len(input, &mut pos)?;
            }
            5 => pos += 4,
            _ => return Err(()),
        }
        if pos > input.len() {
            return Err(());
        }
    }
    Ok(fields)
}

// ---------------------------------------------------------------- table-driven decoder into a slab

#[derive(Clone, Copy)]
struct Entry {
    kind: u8, // 0 varint, 1 string/bytes span, 2 message, 3 bool
    repeated: bool,
    wanted: bool,
    child: u8,
    slot: u8,
}
struct TypeTable {
    slots: usize,
    entries: Vec<Entry>,
    lookup: [u8; 16], // number -> entry index + 1 (all fixture numbers < 16)
}
struct Tables {
    types: Vec<TypeTable>,
    validate_skipped: bool,
    validate_utf8: bool,
}

fn tables(sparse: bool, none: bool) -> Tables {
    fn t(spec: &[(u32, u8, bool, u8, bool)]) -> TypeTable {
        let mut entries = Vec::new();
        let mut lookup = [0u8; 16];
        for (i, &(number, kind, repeated, child, wanted)) in spec.iter().enumerate() {
            entries.push(Entry { kind, repeated, wanted, child, slot: (i + 1) as u8 });
            lookup[number as usize] = (i + 1) as u8;
        }
        TypeTable { slots: spec.len() + 1, entries, lookup }
    }
    let w = |full: bool| !none && (full || !sparse);
    Tables {
        types: vec![
            t(&[
                (1, 1, false, 0, w(false)),
                (2, 2, true, 1, w(true)),
                (3, 1, true, 0, w(false)),
                (4, 2, true, 2, w(true)),
                (5, 2, false, 3, w(false)),
                (6, 0, false, 0, w(false)),
                (7, 1, false, 0, w(true)),
            ]),
            t(&[
                (1, 0, false, 0, w(true)),
                (2, 0, false, 0, w(true)),
                (3, 0, false, 0, w(true)),
                (4, 1, false, 0, w(true)),
                (6, 1, false, 0, w(true)),
                (7, 0, false, 0, w(true)),
                (8, 3, false, 0, w(true)),
                (9, 2, true, 2, w(true)),
                (10, 1, false, 0, w(true)),
                (11, 1, false, 0, w(true)),
            ]),
            t(&[(1, 1, false, 0, w(true)), (2, 2, false, 4, w(true))]),
            t(&[(1, 1, false, 0, w(false))]),
            t(&[(1, 1, false, 0, w(true)), (2, 1, false, 0, w(true))]),
        ],
        validate_skipped: true,
        validate_utf8: true,
    }
}

struct Slab {
    words: Vec<u64>,
    pending: Vec<(u8, u64)>,
}

#[inline(always)]
fn span(off: usize, len: usize) -> u64 {
    ((off as u64) << 32) | len as u64
}

fn validate_text(bytes: &[u8]) -> Result<(), ()> {
    crate::unicode::utf16_length(bytes).map(|_| ()).map_err(|_| ())
}

#[inline(always)]
fn ascii_or_validate(bytes: &[u8]) -> Result<(), ()> {
    let mut i = 0;
    let mut acc = 0u64;
    while i + 8 <= bytes.len() {
        acc |= u64::from_le_bytes(bytes[i..i + 8].try_into().unwrap());
        i += 8;
    }
    let mut tail = 0u8;
    while i < bytes.len() {
        tail |= bytes[i];
        i += 1;
    }
    if (acc & 0x8080808080808080) == 0 && tail < 0x80 {
        return Ok(());
    }
    validate_text(bytes)
}

fn table_decode(
    tables: &Tables,
    ty: usize,
    input: &[u8],
    base: usize,
    slab: &mut Slab,
) -> Result<usize, ()> {
    let table = &tables.types[ty];
    let record = slab.words.len();
    slab.words.resize(record + table.slots, 0);
    let pending_base = slab.pending.len();
    let mut presence = 0u64;
    let mut pos = 0usize;
    while pos < input.len() {
        let tag = read_varint(input, &mut pos)?;
        let number = (tag >> 3) as usize;
        let wt = tag & 7;
        let idx = if number < 16 { table.lookup[number] } else { 0 };
        if idx == 0 {
            match wt {
                0 => {
                    read_varint(input, &mut pos)?;
                }
                1 => pos += 8,
                2 => {
                    read_len(input, &mut pos)?;
                }
                5 => pos += 4,
                _ => return Err(()),
            }
            continue;
        }
        let e = table.entries[idx as usize - 1];
        presence |= 1 << e.slot;
        match e.kind {
            0 | 3 => {
                if wt != 0 {
                    return Err(());
                }
                let v = read_varint(input, &mut pos)?;
                if e.repeated {
                    slab.pending.push((e.slot, v));
                } else {
                    slab.words[record + e.slot as usize] = v;
                }
            }
            1 => {
                if wt != 2 {
                    return Err(());
                }
                let (start, n) = read_len(input, &mut pos)?;
                if e.wanted && tables.validate_utf8 {
                    validate_text(&input[start..start + n])?;
                }
                let value = span(base + start, n);
                if e.repeated {
                    slab.pending.push((e.slot, value));
                } else {
                    slab.words[record + e.slot as usize] = value;
                }
            }
            _ => {
                if wt != 2 {
                    return Err(());
                }
                let (start, n) = read_len(input, &mut pos)?;
                let value = if e.wanted {
                    table_decode(tables, e.child as usize, &input[start..start + n], base + start, slab)?
                        as u64
                } else {
                    if tables.validate_skipped {
                        scan_flat(&input[start..start + n])?;
                    }
                    0
                };
                if e.repeated {
                    slab.pending.push((e.slot, value));
                } else {
                    slab.words[record + e.slot as usize] = value;
                }
            }
        }
    }
    // Flush repeated fields as [count, values...] lists after the record.
    for e in &table.entries {
        if e.repeated && presence & (1 << e.slot) != 0 {
            let at = slab.words.len();
            slab.words.push(0);
            let mut count = 0u64;
            for k in pending_base..slab.pending.len() {
                let (slot, v) = slab.pending[k];
                if slot == e.slot {
                    slab.words.push(v);
                    count += 1;
                }
            }
            slab.words[at] = count;
            slab.words[record + e.slot as usize] = at as u64;
        }
    }
    slab.pending.truncate(pending_base);
    slab.words[record] = presence;
    Ok(record)
}

// ---------------------------------------------------------------- hand-written decoder

// Type record: [presence, name, fields(list), oneofs(list), options(list), source_context, syntax]
// Field record: [presence, kind, cardinality, number, name, type_url, oneof_index, packed, options, json_name, default_value]
#[derive(Clone, Copy, PartialEq)]
enum Validate {
    Production,
    Ascii,
    None,
}

#[inline(always)]
fn text(v: Validate, bytes: &[u8]) -> Result<(), ()> {
    match v {
        Validate::Production => validate_text(bytes),
        Validate::Ascii => ascii_or_validate(bytes),
        Validate::None => Ok(()),
    }
}

fn hand_field(v: Validate, input: &[u8], base: usize, slab: &mut Slab) -> Result<usize, ()> {
    let record = slab.words.len();
    slab.words.resize(record + 11, 0);
    let mut presence = 0u64;
    let mut pos = 0usize;
    let mut options = 0u64;
    while pos < input.len() {
        let tag = read_varint(input, &mut pos)?;
        match tag {
            0x08 => {
                slab.words[record + 1] = read_varint(input, &mut pos)?;
                presence |= 2;
            }
            0x10 => {
                slab.words[record + 2] = read_varint(input, &mut pos)?;
                presence |= 4;
            }
            0x18 => {
                slab.words[record + 3] = read_varint(input, &mut pos)?;
                presence |= 8;
            }
            0x22 => {
                let (s, n) = read_len(input, &mut pos)?;
                text(v, &input[s..s + n])?;
                slab.words[record + 4] = span(base + s, n);
                presence |= 16;
            }
            0x32 => {
                let (s, n) = read_len(input, &mut pos)?;
                text(v, &input[s..s + n])?;
                slab.words[record + 5] = span(base + s, n);
                presence |= 32;
            }
            0x38 => {
                slab.words[record + 6] = read_varint(input, &mut pos)?;
                presence |= 64;
            }
            0x40 => {
                slab.words[record + 7] = read_varint(input, &mut pos)?;
                presence |= 128;
            }
            0x4a => {
                let (s, n) = read_len(input, &mut pos)?;
                scan_flat(&input[s..s + n])?;
                options += 1;
                presence |= 256;
            }
            0x52 => {
                let (s, n) = read_len(input, &mut pos)?;
                text(v, &input[s..s + n])?;
                slab.words[record + 9] = span(base + s, n);
                presence |= 512;
            }
            0x5a => {
                let (s, n) = read_len(input, &mut pos)?;
                text(v, &input[s..s + n])?;
                slab.words[record + 10] = span(base + s, n);
                presence |= 1024;
            }
            _ => match tag & 7 {
                0 => {
                    read_varint(input, &mut pos)?;
                }
                1 => pos += 8,
                2 => {
                    read_len(input, &mut pos)?;
                }
                5 => pos += 4,
                _ => return Err(()),
            },
        }
    }
    slab.words[record + 8] = options;
    slab.words[record] = presence;
    Ok(record)
}

fn hand_type(
    v: Validate,
    input: &[u8],
    slab: &mut Slab,
    fields: &mut Vec<u64>,
    oneofs: &mut Vec<u64>,
) -> Result<usize, ()> {
    let record = slab.words.len();
    slab.words.resize(record + 7, 0);
    fields.clear();
    oneofs.clear();
    let mut presence = 0u64;
    let mut pos = 0usize;
    while pos < input.len() {
        let tag = read_varint(input, &mut pos)?;
        match tag {
            0x0a => {
                let (s, n) = read_len(input, &mut pos)?;
                text(v, &input[s..s + n])?;
                slab.words[record + 1] = span(s, n);
                presence |= 2;
            }
            0x12 => {
                let (s, n) = read_len(input, &mut pos)?;
                let child = hand_field(v, &input[s..s + n], s, slab)?;
                fields.push(child as u64);
                presence |= 4;
            }
            0x1a => {
                let (s, n) = read_len(input, &mut pos)?;
                text(v, &input[s..s + n])?;
                oneofs.push(span(s, n));
                presence |= 8;
            }
            0x22 => {
                let (s, n) = read_len(input, &mut pos)?;
                scan_flat(&input[s..s + n])?;
                slab.words[record + 4] += 1;
                presence |= 16;
            }
            0x2a => {
                let (s, n) = read_len(input, &mut pos)?;
                // SourceContext { file_name = 1 }
                let child = slab.words.len();
                slab.words.resize(child + 2, 0);
                let sub = &input[s..s + n];
                let mut p = 0;
                while p < sub.len() {
                    let t = read_varint(sub, &mut p)?;
                    if t == 0x0a {
                        let (ss, nn) = read_len(sub, &mut p)?;
                        text(v, &sub[ss..ss + nn])?;
                        slab.words[child + 1] = span(s + ss, nn);
                        slab.words[child] = 2;
                    } else {
                        return Err(());
                    }
                }
                slab.words[record + 5] = child as u64;
                presence |= 32;
            }
            0x30 => {
                slab.words[record + 6] = read_varint(input, &mut pos)?;
                presence |= 64;
            }
            _ => match tag & 7 {
                0 => {
                    read_varint(input, &mut pos)?;
                }
                1 => pos += 8,
                2 => {
                    read_len(input, &mut pos)?;
                }
                5 => pos += 4,
                _ => return Err(()),
            },
        }
    }
    if !fields.is_empty() {
        let at = slab.words.len();
        slab.words.push(fields.len() as u64);
        slab.words.extend_from_slice(fields);
        slab.words[record + 2] = at as u64;
    }
    if !oneofs.is_empty() {
        let at = slab.words.len();
        slab.words.push(oneofs.len() as u64);
        slab.words.extend_from_slice(oneofs);
        slab.words[record + 3] = at as u64;
    }
    slab.words[record] = presence;
    Ok(record)
}

// ---------------------------------------------------------------- input readers (by value)

#[inline(always)]
fn get_varint(input: &[u8], p: usize, end: usize) -> Result<(u64, usize), ()> {
    if p + 1 < end {
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

#[inline(never)]
fn get_varint_slow(input: &[u8], mut p: usize, end: usize) -> Result<(u64, usize), ()> {
    let mut value = 0u64;
    let mut shift = 0u32;
    loop {
        if p >= end {
            return Err(());
        }
        let b = input[p];
        p += 1;
        if shift == 63 && b & 0xFE != 0 {
            return Err(());
        }
        value |= ((b & 0x7F) as u64) << shift;
        if b & 0x80 == 0 {
            return Ok((value, p));
        }
        shift += 7;
        if shift > 63 {
            return Err(());
        }
    }
}

#[inline(always)]
fn get_length(input: &[u8], p: usize, end: usize) -> Result<(usize, usize), ()> {
    let (n, p) = get_varint(input, p, end)?;
    if n > (end - p) as u64 {
        return Err(());
    }
    Ok((n as usize, p))
}

#[inline(always)]
fn skip_field(tag: u32, input: &[u8], p: usize, end: usize) -> Result<usize, ()> {
    match tag & 7 {
        0 => Ok(get_varint(input, p, end)?.1),
        1 => if p + 8 <= end { Ok(p + 8) } else { Err(()) },
        2 => {
            let (n, p) = get_length(input, p, end)?;
            Ok(p + n)
        }
        5 => if p + 4 <= end { Ok(p + 4) } else { Err(()) },
        _ => Err(()),
    }
}

// ---------------------------------------------------------------- slab output (TDSLAB1 subset)

#[inline(always)]
fn write32(p: *mut u8, v: u32) {
    unsafe { p.cast::<u32>().write_unaligned(v) }
}
#[inline(always)]
fn read32(p: *const u8) -> u32 {
    unsafe { p.cast::<u32>().read_unaligned() }
}

struct Out {
    buf: Vec<u8>,
    cursor: usize,
    handles: u32,
    records: u32,
}

impl Out {
    fn new(cap: usize) -> Out {
        Out { buf: vec![0u8; cap], cursor: 32, handles: 0, records: 0 }
    }
    #[inline(always)]
    fn reset(&mut self) {
        self.cursor = 32;
        self.handles = 0;
        self.records = 0;
    }
    #[inline(always)]
    fn ptr(&mut self, at: usize) -> *mut u8 {
        unsafe { self.buf.as_mut_ptr().add(at) }
    }
    /// Bump allocation; padding bytes zeroed with one 8-byte store (the cursor is never more than
    /// 7 below `at`). Capacity is checked here, once per allocation.
    #[inline(always)]
    fn alloc(&mut self, size: usize, align: usize) -> Result<usize, ()> {
        let at = (self.cursor + align - 1) & !(align - 1);
        let end = at + size;
        if end + 8 > self.buf.len() {
            return Err(());
        }
        if at != self.cursor {
            unsafe { self.buf.as_mut_ptr().add(self.cursor).cast::<u64>().write_unaligned(0) };
        }
        self.cursor = end;
        Ok(at)
    }
    /// Open a record: zeroed, handle written.
    #[inline(always)]
    fn record(&mut self, bytes: usize) -> Result<usize, ()> {
        let at = self.alloc(bytes, 8)?;
        let p = self.ptr(at);
        unsafe {
            let mut i = 0;
            while i < bytes {
                p.add(i).cast::<u64>().write_unaligned(0);
                i += 8;
            }
        }
        write32(p, self.handles);
        self.handles += 1;
        self.records += 1;
        Ok(at)
    }
    /// A string or bytes header with the payload copied: 8 + n (ASCII) or 12 + n.
    #[inline(never)]
    fn string(&mut self, input: &[u8], at: usize, n: usize, text: bool) -> Result<u32, ()> {
        let payload = &input[at..at + n];
        let (units, ascii) = if text { crate::unicode::validate_utf16_units(payload).ok_or(())? } else { (0, false) };
        let header = if ascii { 8 } else { 12 };
        let h = self.alloc(header + n, 4)?;
        let p = self.ptr(h);
        write32(p, n as u32 | if ascii { 1 << 31 } else { 0 });
        write32(unsafe { p.add(4) }, self.handles);
        self.handles += 1;
        if !ascii {
            write32(unsafe { p.add(8) }, units as u32);
        }
        unsafe { std::ptr::copy_nonoverlapping(payload.as_ptr(), p.add(header), n) };
        // The allocation ends at a 4-byte boundary for the next header; zero the tail word.
        let tail = (h + header + n + 3) & !3;
        if tail > h + header + n {
            unsafe {
                let z = self.ptr(h + header + n);
                for k in 0..(tail - (h + header + n)) {
                    *z.add(k) = 0;
                }
            }
        }
        Ok(h as u32)
    }
    /// A list of 4-byte elements.
    #[inline(always)]
    fn list4(&mut self, elements: &[u32]) -> Result<u32, ()> {
        let at = self.alloc(8 + 4 * elements.len(), 8)?;
        let p = self.ptr(at);
        write32(p, elements.len() as u32);
        write32(unsafe { p.add(4) }, 0);
        unsafe { std::ptr::copy_nonoverlapping(elements.as_ptr().cast::<u8>(), p.add(8), 4 * elements.len()) };
        Ok(at as u32)
    }
    #[inline(always)]
    fn finish(&mut self) -> usize {
        let bytes = (self.cursor + 7) & !7;
        let p = self.ptr(0);
        write32(p, 0x31534454);
        write32(unsafe { p.add(4) }, 1 | 8);
        write32(unsafe { p.add(8) }, bytes as u32);
        write32(unsafe { p.add(12) }, self.records);
        write32(unsafe { p.add(16) }, self.handles);
        write32(unsafe { p.add(20) }, 32);
        bytes
    }
}

// ---------------------------------------------------------------- hand_slab: per-schema decoder, production slab format

// Type: handle, presence, name(8) fields(12) oneofs(16) options(20) source_context(24) syntax(28) = 32 B
// Field: handle, presence, kind(8) cardinality(12) number(16) name(20) type_url(24) oneof_index(28)
//        packed(32) options(36) json_name(40) default_value(44) = 48 B
// SourceContext: handle, presence, file_name(8) = 16 B
const TYPE_BYTES: usize = 32;
const FIELD_BYTES: usize = 48;
const SC_BYTES: usize = 16;

#[inline(always)]
fn set_slot32(out: &mut Out, rec: usize, slot: usize, v: u32) {
    write32(out.ptr(rec + slot), v);
}

fn slab_field(out: &mut Out, input: &[u8], start: usize, end: usize) -> Result<u32, ()> {
    let rec = out.record(FIELD_BYTES)?;
    let mut presence = 0u32;
    let mut pos = start;
    let mut options = 0usize;
    while pos < end {
        let (tag, p) = get_varint(input, pos, end)?;
        pos = p;
        match tag {
            0x08 => {
                let (v, p) = get_varint(input, pos, end)?;
                pos = p;
                set_slot32(out, rec, 8, v as u32);
                presence |= (v as u32 != 0) as u32;
            }
            0x10 => {
                let (v, p) = get_varint(input, pos, end)?;
                pos = p;
                set_slot32(out, rec, 12, v as u32);
                presence |= ((v as u32 != 0) as u32) << 1;
            }
            0x18 => {
                let (v, p) = get_varint(input, pos, end)?;
                pos = p;
                set_slot32(out, rec, 16, v as u32);
                presence |= ((v as u32 != 0) as u32) << 2;
            }
            0x22 => {
                let (n, p) = get_length(input, pos, end)?;
                pos = p + n;
                let h = out.string(input, p, n, true)?;
                set_slot32(out, rec, 20, h);
                presence |= ((n != 0) as u32) << 3;
            }
            0x32 => {
                let (n, p) = get_length(input, pos, end)?;
                pos = p + n;
                let h = out.string(input, p, n, true)?;
                set_slot32(out, rec, 24, h);
                presence |= ((n != 0) as u32) << 4;
            }
            0x38 => {
                let (v, p) = get_varint(input, pos, end)?;
                pos = p;
                set_slot32(out, rec, 28, v as u32);
                presence |= ((v as u32 != 0) as u32) << 5;
            }
            0x40 => {
                let (v, p) = get_varint(input, pos, end)?;
                pos = p;
                set_slot32(out, rec, 32, (v != 0) as u32);
                presence |= ((v != 0) as u32) << 6;
            }
            0x4a => {
                // options: repeated Option; structurally skipped and counted (as hand_ascii does)
                let (n, p) = get_length(input, pos, end)?;
                pos = p + n;
                options += 1;
                presence |= 1 << 7;
            }
            0x52 => {
                let (n, p) = get_length(input, pos, end)?;
                pos = p + n;
                let h = out.string(input, p, n, true)?;
                set_slot32(out, rec, 40, h);
                presence |= ((n != 0) as u32) << 8;
            }
            0x5a => {
                let (n, p) = get_length(input, pos, end)?;
                pos = p + n;
                let h = out.string(input, p, n, true)?;
                set_slot32(out, rec, 44, h);
                presence |= ((n != 0) as u32) << 9;
            }
            _ => {
                if tag > u32::MAX as u64 || tag >> 3 == 0 {
                    return Err(());
                }
                pos = skip_field(tag as u32, input, pos, end)?;
            }
        }
    }
    if options != 0 {
        set_slot32(out, rec, 36, options as u32);
    }
    write32(out.ptr(rec + 4), presence);
    Ok(rec as u32)
}

fn slab_type(out: &mut Out, input: &[u8], fields: &mut Vec<u32>, oneofs: &mut Vec<u32>) -> Result<usize, ()> {
    out.reset();
    let rec = out.record(TYPE_BYTES)?;
    fields.clear();
    oneofs.clear();
    let mut presence = 0u32;
    let mut pos = 0usize;
    let end = input.len();
    let mut options = 0u32;
    while pos < end {
        let (tag, p) = get_varint(input, pos, end)?;
        pos = p;
        match tag {
            0x0a => {
                let (n, p) = get_length(input, pos, end)?;
                pos = p + n;
                let h = out.string(input, p, n, true)?;
                set_slot32(out, rec, 8, h);
                presence |= (n != 0) as u32;
            }
            0x12 => {
                let (n, p) = get_length(input, pos, end)?;
                pos = p + n;
                let child = slab_field(out, input, p, p + n)?;
                fields.push(child);
                presence |= 2;
            }
            0x1a => {
                let (n, p) = get_length(input, pos, end)?;
                pos = p + n;
                let h = out.string(input, p, n, true)?;
                oneofs.push(h);
                presence |= 4;
            }
            0x22 => {
                let (n, p) = get_length(input, pos, end)?;
                pos = p + n;
                options += 1;
                presence |= 8;
            }
            0x2a => {
                let (n, p) = get_length(input, pos, end)?;
                pos = p + n;
                let sc = out.record(SC_BYTES)?;
                let sub_end = p + n;
                let mut q = p;
                let mut sp = 0u32;
                while q < sub_end {
                    let (t, qq) = get_varint(input, q, sub_end)?;
                    q = qq;
                    if t == 0x0a {
                        let (nn, qq) = get_length(input, q, sub_end)?;
                        q = qq + nn;
                        let h = out.string(input, qq, nn, true)?;
                        set_slot32(out, sc, 8, h);
                        sp |= (nn != 0) as u32;
                    } else {
                        q = skip_field(t as u32, input, q, sub_end)?;
                    }
                }
                write32(out.ptr(sc + 4), sp);
                set_slot32(out, rec, 24, sc as u32);
                presence |= 16;
            }
            0x30 => {
                let (v, p) = get_varint(input, pos, end)?;
                pos = p;
                set_slot32(out, rec, 28, v as u32);
                presence |= ((v as u32 != 0) as u32) << 5;
            }
            _ => {
                if tag > u32::MAX as u64 || tag >> 3 == 0 {
                    return Err(());
                }
                pos = skip_field(tag as u32, input, pos, end)?;
            }
        }
    }
    if !fields.is_empty() {
        let l = out.list4(fields)?;
        set_slot32(out, rec, 12, l);
    }
    if !oneofs.is_empty() {
        let l = out.list4(oneofs)?;
        set_slot32(out, rec, 16, l);
    }
    if options != 0 {
        set_slot32(out, rec, 20, options);
    }
    write32(out.ptr(rec + 4), presence);
    Ok(out.finish())
}

// ---------------------------------------------------------------- fast: table-driven, explicit stack

const K_VARINT32: u8 = 0; // int32/uint32/enum (open): write u32, presence if non-zero
const K_VARINT64: u8 = 1;
const K_BOOL: u8 = 2;
const K_STRING: u8 = 3;
const K_MESSAGE: u8 = 4; // singular message
const K_RMESSAGE: u8 = 5; // repeated message
const K_RSTRING: u8 = 6; // repeated string
const K_RVARINT: u8 = 7; // repeated varint, unpacked (packed handled by wire-type mismatch path)
const K_FIXED32: u8 = 8;
const K_FIXED64: u8 = 9;
const K_RMESSAGE_SKIP: u8 = 10; // repeated message projected to a count (options) — like hand

/// One packed entry: everything a handler needs in one 64-bit load plus the tag.
#[derive(Clone, Copy, Default)]
struct FEntry {
    tag: u32,
    slot: u16,
    child: u8,
    kind: u8,
    ordinal: u8,
    next: u8,
    explicit: bool,
    packable: bool,
}

struct FType {
    entries: Vec<FEntry>,
    lookup: [u8; 32], // field number -> entry index + 1, 0 = unknown
    record_bytes: u32,
    repeated: Vec<(u8, u16)>, // (ordinal, slot) of repeated fields for close
}

fn ftype(record_bytes: u32, specs: &[(u32, u8, u8, bool)]) -> FType {
    // (number, kind, child, explicit)
    let mut entries = Vec::new();
    let mut lookup = [0u8; 32];
    let mut repeated = Vec::new();
    for (i, &(number, kind, child, explicit)) in specs.iter().enumerate() {
        let wt = match kind {
            K_STRING | K_MESSAGE | K_RMESSAGE | K_RSTRING | K_RMESSAGE_SKIP => 2,
            K_FIXED32 => 5,
            K_FIXED64 => 1,
            _ => 0,
        };
        let is_repeated = matches!(kind, K_RMESSAGE | K_RSTRING | K_RVARINT | K_RMESSAGE_SKIP);
        entries.push(FEntry {
            tag: number << 3 | wt,
            slot: (8 + 4 * i) as u16,
            child,
            kind,
            ordinal: i as u8,
            next: if is_repeated { i as u8 } else { (i + 1).min(specs.len() - 1) as u8 },
            explicit,
            packable: kind == K_RVARINT,
        });
        lookup[number as usize] = (i + 1) as u8;
        if is_repeated {
            repeated.push((i as u8, (8 + 4 * i) as u16));
        }
    }
    FType { entries, lookup, record_bytes, repeated }
}

fn fast_tables() -> Vec<FType> {
    vec![
        // 0: Type: name, fields(repeated Field), oneofs(repeated string), options(repeated Option, counted), source_context, syntax
        ftype(TYPE_BYTES as u32, &[
            (1, K_STRING, 0, false),
            (2, K_RMESSAGE, 1, false),
            (3, K_RSTRING, 0, false),
            (4, K_RMESSAGE_SKIP, 3, false),
            (5, K_MESSAGE, 2, false),
            (6, K_VARINT32, 0, false),
        ]),
        // 1: Field
        ftype(FIELD_BYTES as u32, &[
            (1, K_VARINT32, 0, false),
            (2, K_VARINT32, 0, false),
            (3, K_VARINT32, 0, false),
            (4, K_STRING, 0, false),
            (6, K_STRING, 0, false),
            (7, K_VARINT32, 0, false),
            (8, K_BOOL, 0, false),
            (9, K_RMESSAGE_SKIP, 3, false),
            (10, K_STRING, 0, false),
            (11, K_STRING, 0, false),
        ]),
        // 2: SourceContext
        ftype(SC_BYTES as u32, &[(1, K_STRING, 0, false)]),
        // 3: Option (never decoded here)
        ftype(16, &[(1, K_STRING, 0, false)]),
    ]
}

#[derive(Clone, Copy, Default)]
struct Frame {
    ty: u8,
    next: u8,
    ordinal: u8, // ordinal of this record in its parent (for repeated: pending push)
    singular: bool,
    end: u32,
    rec: u32,
    presence: u32,
    pending: u32,
    parent_slot: u32, // absolute slab offset of the parent's slot for a singular child
}

struct Fast {
    tables: Vec<FType>,
    frames: Vec<Frame>,
    pending: Vec<(u32, u32)>, // (ordinal, value)
}

impl Fast {
    fn new() -> Fast {
        Fast { tables: fast_tables(), frames: Vec::with_capacity(64), pending: Vec::with_capacity(4096) }
    }

    /// Flush the pending elements since `mark` into lists (grouped by ordinal in arrival order);
    /// interleaved ordinals take the slow path.
    #[inline(never)]
    fn close_lists(&mut self, tables: &[FType], out: &mut Out, ty: usize, rec: usize, mark: usize, presence: &mut u32) -> Result<(), ()> {
        let n = self.pending.len();
        let mut k = mark;
        let mut written = 0u32;
        while k < n {
            let ordinal = self.pending[k].0;
            let start = k;
            while k < n && self.pending[k].0 == ordinal {
                k += 1;
            }
            if written & (1 << ordinal) != 0 {
                return self.close_lists_slow(tables, out, ty, rec, mark, presence);
            }
            written |= 1 << ordinal;
            let count = k - start;
            let at = out.alloc(8 + 4 * count, 8)?;
            let p = out.ptr(at);
            write32(p, count as u32);
            write32(unsafe { p.add(4) }, 0);
            for (j, e) in self.pending[start..k].iter().enumerate() {
                write32(unsafe { p.add(8 + 4 * j) }, e.1);
            }
            let slot = tables[ty].entries[ordinal as usize].slot as usize;
            write32(out.ptr(rec + slot), at as u32);
            *presence |= 1 << ordinal;
        }
        self.pending.truncate(mark);
        Ok(())
    }

    #[inline(never)]
    fn close_lists_slow(&mut self, tables: &[FType], out: &mut Out, ty: usize, rec: usize, mark: usize, presence: &mut u32) -> Result<(), ()> {
        let n = self.pending.len();
        for &(ordinal, slot) in &tables[ty].repeated {
            let count = self.pending[mark..n].iter().filter(|e| e.0 == ordinal as u32).count();
            if count == 0 {
                continue;
            }
            let at = out.alloc(8 + 4 * count, 8)?;
            let p = out.ptr(at);
            write32(p, count as u32);
            write32(unsafe { p.add(4) }, 0);
            let mut j = 0;
            for e in &self.pending[mark..n] {
                if e.0 == ordinal as u32 {
                    write32(unsafe { p.add(8 + 4 * j) }, e.1);
                    j += 1;
                }
            }
            write32(out.ptr(rec + slot as usize), at as u32);
            *presence |= 1 << ordinal;
        }
        self.pending.truncate(mark);
        Ok(())
    }

    fn decode(&mut self, out: &mut Out, input: &[u8]) -> Result<usize, ()> {
        out.reset();
        self.frames.clear();
        self.pending.clear();
        let tables = std::mem::take(&mut self.tables);
        let r = self.run(&tables, out, input);
        self.tables = tables;
        r
    }

    #[inline(never)]
    fn run(&mut self, tables: &[FType], out: &mut Out, input: &[u8]) -> Result<usize, ()> {
        // Live state of the current record, kept in locals (the frame is only written on
        // push/pop).
        let mut ty = 0usize;
        let mut t: &FType = &tables[0];
        let mut rec = out.record(t.record_bytes as usize)?;
        let mut presence = 0u32;
        let mut pos = 0usize;
        let mut end = input.len();
        let mut next = 0usize;
        let mut mark = 0usize;
        let mut depth = 0usize;
        loop {
            if pos >= end {
                // Close this record.
                if self.pending.len() > mark {
                    self.close_lists(tables, out, ty, rec, mark, &mut presence)?;
                }
                write32(out.ptr(rec + 4), presence);
                if depth == 0 {
                    return Ok(out.finish());
                }
                depth -= 1;
                let f = self.frames[depth];
                let child = rec as u32;
                // Restore the parent.
                ty = f.ty as usize;
                t = &tables[ty];
                rec = f.rec as usize;
                presence = f.presence;
                pos = end; // the child's end is where the parent resumes
                end = f.end as usize;
                next = f.next as usize;
                mark = f.pending as usize;
                if f.singular {
                    write32(out.ptr(f.parent_slot as usize), child);
                    presence |= 1 << f.ordinal;
                } else {
                    self.pending.push((f.ordinal as u32, child));
                }
                continue;
            }
            let start = pos;
            let (raw, p) = get_varint(input, pos, end)?;
            pos = p;
            // Expected-tag prediction, then direct lookup by number, then the unknown path.
            let e: FEntry = {
                let cand = unsafe { *t.entries.get_unchecked(next) };
                if cand.tag as u64 == raw {
                    cand
                } else {
                    if raw > u32::MAX as u64 || raw >> 3 == 0 || raw & 7 > 5 {
                        return Err(());
                    }
                    let number = (raw >> 3) as usize;
                    let idx = if number < 32 { t.lookup[number] } else { 0 };
                    if idx == 0 {
                        pos = skip_field(raw as u32, input, pos, end)?;
                        let _ = start; // unknown bytes would be appended here
                        continue;
                    }
                    let cand = t.entries[idx as usize - 1];
                    if cand.tag as u64 != raw {
                        // wire-type mismatch: packed run of a packable field, or unknown
                        if raw & 7 == 2 && cand.packable {
                            let (n, p) = get_length(input, pos, end)?;
                            let packed_end = p + n;
                            let mut q = p;
                            while q < packed_end {
                                let (v, qq) = get_varint(input, q, packed_end)?;
                                q = qq;
                                self.pending.push((cand.ordinal as u32, v as u32));
                            }
                            pos = packed_end;
                            next = cand.ordinal as usize;
                            continue;
                        }
                        pos = skip_field(raw as u32, input, pos, end)?;
                        continue;
                    }
                    cand
                }
            };
            next = e.next as usize;
            match e.kind {
                K_VARINT32 => {
                    let (v, p) = get_varint(input, pos, end)?;
                    pos = p;
                    write32(out.ptr(rec + e.slot as usize), v as u32);
                    presence |= ((e.explicit | (v as u32 != 0)) as u32) << e.ordinal;
                }
                K_VARINT64 => {
                    let (v, p) = get_varint(input, pos, end)?;
                    pos = p;
                    unsafe { out.ptr(rec + e.slot as usize).cast::<u64>().write_unaligned(v) };
                    presence |= ((e.explicit | (v != 0)) as u32) << e.ordinal;
                }
                K_BOOL => {
                    let (v, p) = get_varint(input, pos, end)?;
                    pos = p;
                    write32(out.ptr(rec + e.slot as usize), (v != 0) as u32);
                    presence |= ((e.explicit | (v != 0)) as u32) << e.ordinal;
                }
                K_FIXED32 => {
                    if pos + 4 > end {
                        return Err(());
                    }
                    let v = read32(unsafe { input.as_ptr().add(pos) });
                    pos += 4;
                    write32(out.ptr(rec + e.slot as usize), v);
                    presence |= ((e.explicit | (v != 0)) as u32) << e.ordinal;
                }
                K_FIXED64 => {
                    if pos + 8 > end {
                        return Err(());
                    }
                    let v = unsafe { input.as_ptr().add(pos).cast::<u64>().read_unaligned() };
                    pos += 8;
                    unsafe { out.ptr(rec + e.slot as usize).cast::<u64>().write_unaligned(v) };
                    presence |= ((e.explicit | (v != 0)) as u32) << e.ordinal;
                }
                K_STRING => {
                    let (n, p) = get_length(input, pos, end)?;
                    pos = p + n;
                    let h = out.string(input, p, n, true)?;
                    write32(out.ptr(rec + e.slot as usize), h);
                    presence |= ((e.explicit | (n != 0)) as u32) << e.ordinal;
                }
                K_RSTRING => {
                    let (n, p) = get_length(input, pos, end)?;
                    pos = p + n;
                    let h = out.string(input, p, n, true)?;
                    self.pending.push((e.ordinal as u32, h));
                }
                K_RVARINT => {
                    let (v, p) = get_varint(input, pos, end)?;
                    pos = p;
                    self.pending.push((e.ordinal as u32, v as u32));
                }
                K_RMESSAGE_SKIP => {
                    let (n, p) = get_length(input, pos, end)?;
                    pos = p + n;
                    let slot = out.ptr(rec + e.slot as usize);
                    write32(slot, read32(slot) + 1);
                    presence |= 1 << e.ordinal;
                }
                K_MESSAGE | K_RMESSAGE => {
                    let (n, p) = get_length(input, pos, end)?;
                    if depth >= 63 {
                        return Err(());
                    }
                    // Push the parent and enter the child.
                    let f = Frame {
                        ty: ty as u8,
                        next: next as u8,
                        ordinal: e.ordinal,
                        singular: e.kind == K_MESSAGE,
                        end: end as u32,
                        rec: rec as u32,
                        presence,
                        pending: mark as u32,
                        parent_slot: (rec + e.slot as usize) as u32,
                    };
                    if self.frames.len() <= depth {
                        self.frames.push(f);
                    } else {
                        self.frames[depth] = f;
                    }
                    depth += 1;
                    ty = e.child as usize;
                    t = &tables[ty];
                    rec = out.record(t.record_bytes as usize)?;
                    presence = 0;
                    pos = p;
                    end = p + n;
                    next = 0;
                    mark = self.pending.len();
                }
                _ => return Err(()),
            }
        }
    }
}

// ---------------------------------------------------------------- fast2: pointer-state loop, and fast_tail: become
//
// upb's state discipline: the input cursor and limit are pointers; the record base is a
// pointer; hasbits live in a register until the record closes; the output bump cursor is a
// pointer; the string path returns one word. Frames and pending elements are fixed arrays.

#[derive(Clone, Copy)]
struct F2 {
    t: *const FType,
    base: *mut u8,
    limit: *const u8,
    hasbits: u32,
    next: u32,
    mark: u32,
    ordinal: u8,
    singular: bool,
    slot: u16,
}

struct Ctx {
    out: *mut u8,
    out_end: *mut u8,
    cursor: *mut u8,
    handles: u32,
    records: u32,
    frames: [F2; 64],
    depth: usize,
    pending: Vec<(u32, u32)>,
}

#[inline(always)]
unsafe fn align_up(p: *mut u8, a: usize) -> *mut u8 {
    ((p as usize + a - 1) & !(a - 1)) as *mut u8
}

/// Bump-allocate a zeroed record; returns its base. Pads with one 8-byte zero store.
#[inline(always)]
unsafe fn alloc_record(ctx: &mut Ctx, bytes: usize) -> Result<*mut u8, ()> {
    let at = align_up(ctx.cursor, 8);
    if at.add(bytes + 8) > ctx.out_end {
        return Err(());
    }
    if at != ctx.cursor {
        ctx.cursor.cast::<u64>().write_unaligned(0);
    }
    let mut i = 0;
    while i < bytes {
        at.add(i).cast::<u64>().write_unaligned(0);
        i += 8;
    }
    at.cast::<u32>().write_unaligned(ctx.handles);
    ctx.handles += 1;
    ctx.records += 1;
    ctx.cursor = at.add(bytes);
    Ok(at)
}

/// String header + payload copy at the aligned cursor; returns the header's slab offset.
#[inline(never)]
unsafe fn put_string(ctx: &mut Ctx, src: *const u8, n: usize) -> Result<u32, ()> {
    let payload = std::slice::from_raw_parts(src, n);
    let (units, ascii) = crate::unicode::validate_utf16_units(payload).ok_or(())?;
    let header = if ascii { 8 } else { 12 };
    let h = align_up(ctx.cursor, 4);
    let end = h.add(header + n);
    if end.add(8) > ctx.out_end {
        return Err(());
    }
    if h != ctx.cursor {
        ctx.cursor.cast::<u32>().write_unaligned(0);
    }
    h.cast::<u32>().write_unaligned(n as u32 | if ascii { 1 << 31 } else { 0 });
    h.add(4).cast::<u32>().write_unaligned(ctx.handles);
    ctx.handles += 1;
    if !ascii {
        h.add(8).cast::<u32>().write_unaligned(units as u32);
    }
    std::ptr::copy_nonoverlapping(src, h.add(header), n);
    // zero the tail up to the next 4-byte boundary (one word store, inside capacity)
    end.cast::<u32>().write_unaligned(0);
    ctx.cursor = end;
    Ok(h.offset_from(ctx.out) as u32)
}

#[inline(always)]
unsafe fn varint_p(p: *const u8, limit: *const u8) -> Result<(u64, *const u8), ()> {
    if p.add(1) < limit {
        let b = *p;
        if b < 0x80 {
            return Ok((b as u64, p.add(1)));
        }
        let c = *p.add(1);
        if c < 0x80 {
            return Ok(((b & 0x7F) as u64 | (c as u64) << 7, p.add(2)));
        }
    } else if p < limit && *p < 0x80 {
        return Ok((*p as u64, p.add(1)));
    }
    varint_p_slow(p, limit)
}

#[inline(never)]
unsafe fn varint_p_slow(mut p: *const u8, limit: *const u8) -> Result<(u64, *const u8), ()> {
    let mut value = 0u64;
    let mut shift = 0u32;
    loop {
        if p >= limit {
            return Err(());
        }
        let b = *p;
        p = p.add(1);
        if shift == 63 && b & 0xFE != 0 {
            return Err(());
        }
        value |= ((b & 0x7F) as u64) << shift;
        if b & 0x80 == 0 {
            return Ok((value, p));
        }
        shift += 7;
        if shift > 63 {
            return Err(());
        }
    }
}

#[inline(always)]
unsafe fn length_p(p: *const u8, limit: *const u8) -> Result<(usize, *const u8), ()> {
    let (n, p) = varint_p(p, limit)?;
    if n > limit.offset_from(p) as u64 {
        return Err(());
    }
    Ok((n as usize, p))
}

#[inline(never)]
unsafe fn skip_p(tag: u32, p: *const u8, limit: *const u8) -> Result<*const u8, ()> {
    match tag & 7 {
        0 => Ok(varint_p(p, limit)?.1),
        1 => if p.add(8) <= limit { Ok(p.add(8)) } else { Err(()) },
        2 => {
            let (n, p) = length_p(p, limit)?;
            Ok(p.add(n))
        }
        5 => if p.add(4) <= limit { Ok(p.add(4)) } else { Err(()) },
        _ => Err(()),
    }
}

#[inline(never)]
unsafe fn close_lists2(ctx: &mut Ctx, t: &FType, base: *mut u8, mark: usize, hasbits: &mut u32) -> Result<(), ()> {
    let n = ctx.pending.len();
    let mut k = mark;
    let mut written = 0u32;
    while k < n {
        let ordinal = ctx.pending[k].0;
        let start = k;
        while k < n && ctx.pending[k].0 == ordinal {
            k += 1;
        }
        if written & (1 << ordinal) != 0 {
            return Err(()); // interleaved repeated fields: not on the probe's path
        }
        written |= 1 << ordinal;
        let count = k - start;
        let at = align_up(ctx.cursor, 8);
        if at.add(8 + 4 * count + 8) > ctx.out_end {
            return Err(());
        }
        if at != ctx.cursor {
            ctx.cursor.cast::<u64>().write_unaligned(0);
        }
        at.cast::<u32>().write_unaligned(count as u32);
        at.add(4).cast::<u32>().write_unaligned(0);
        for (j, e) in ctx.pending[start..k].iter().enumerate() {
            at.add(8 + 4 * j).cast::<u32>().write_unaligned(e.1);
        }
        ctx.cursor = at.add(8 + 4 * count);
        let slot = t.entries[ordinal as usize].slot as usize;
        base.add(slot).cast::<u32>().write_unaligned(at.offset_from(ctx.out) as u32);
        *hasbits |= 1 << ordinal;
    }
    ctx.pending.truncate(mark);
    Ok(())
}

unsafe fn finish2(ctx: &mut Ctx) -> usize {
    let bytes = (ctx.cursor.offset_from(ctx.out) as usize + 7) & !7;
    let p = ctx.out;
    p.cast::<u32>().write_unaligned(0x31534454);
    p.add(4).cast::<u32>().write_unaligned(1 | 8);
    p.add(8).cast::<u32>().write_unaligned(bytes as u32);
    p.add(12).cast::<u32>().write_unaligned(ctx.records);
    p.add(16).cast::<u32>().write_unaligned(ctx.handles);
    p.add(20).cast::<u32>().write_unaligned(32);
    bytes
}

struct Fast2 {
    tables: Vec<FType>,
    ctx: Box<Ctx>,
}

impl Fast2 {
    fn new(out: &mut Out) -> Fast2 {
        let ptr = out.buf.as_mut_ptr();
        let end = unsafe { ptr.add(out.buf.len()) };
        Fast2 {
            tables: fast_tables(),
            ctx: Box::new(Ctx {
                out: ptr,
                out_end: end,
                cursor: unsafe { ptr.add(32) },
                handles: 0,
                records: 0,
                frames: [F2 { t: std::ptr::null(), base: std::ptr::null_mut(), limit: std::ptr::null(), hasbits: 0, next: 0, mark: 0, ordinal: 0, singular: false, slot: 0 }; 64],
                depth: 0,
                pending: Vec::with_capacity(4096),
            }),
        }
    }

    fn decode(&mut self, input: &[u8]) -> Result<usize, ()> {
        let ctx = &mut *self.ctx;
        ctx.cursor = unsafe { ctx.out.add(32) };
        ctx.handles = 0;
        ctx.records = 0;
        ctx.depth = 0;
        ctx.pending.clear();
        unsafe { run2(&self.tables, ctx, input.as_ptr(), input.as_ptr().add(input.len())) }
    }

    fn decode_tail(&mut self, input: &[u8]) -> Result<usize, ()> {
        let ctx = &mut *self.ctx;
        ctx.cursor = unsafe { ctx.out.add(32) };
        ctx.handles = 0;
        ctx.records = 0;
        ctx.depth = 0;
        ctx.pending.clear();
        unsafe {
            let t: *const FType = &self.tables[0];
            let base = alloc_record(ctx, (*t).record_bytes as usize)?;
            let limit = input.as_ptr().add(input.len());
            let r = dispatch(ctx, input.as_ptr(), limit, base, 0, t, (*t).entries.as_ptr());
            r?;
            Ok(finish2(ctx))
        }
    }
}

#[inline(never)]
unsafe fn run2(tables: &[FType], ctx: &mut Ctx, mut p: *const u8, mut limit: *const u8) -> Result<usize, ()> {
    let mut t: *const FType = &tables[0];
    let mut base = alloc_record(ctx, (*t).record_bytes as usize)?;
    let mut hasbits = 0u32;
    let mut e: *const FEntry = (*t).entries.as_ptr();
    let mut mark = 0usize;
    loop {
        if p >= limit {
            if ctx.pending.len() > mark {
                close_lists2(ctx, &*t, base, mark, &mut hasbits)?;
            }
            base.add(4).cast::<u32>().write_unaligned(hasbits);
            if ctx.depth == 0 {
                return Ok(finish2(ctx));
            }
            ctx.depth -= 1;
            let f = ctx.frames[ctx.depth];
            let child = base.offset_from(ctx.out) as u32;
            t = f.t;
            base = f.base;
            hasbits = f.hasbits;
            limit = f.limit;
            e = (*t).entries.as_ptr().add(f.next as usize);
            mark = f.mark as usize;
            if f.singular {
                base.add(f.slot as usize).cast::<u32>().write_unaligned(child);
                hasbits |= 1 << f.ordinal;
            } else {
                ctx.pending.push((f.ordinal as u32, child));
            }
            continue;
        }
        let (raw, q) = varint_p(p, limit)?;
        p = q;
        if (*e).tag as u64 != raw {
            // Not the predicted field: lookup by number; unknown or mismatched wire type.
            if raw > u32::MAX as u64 || raw >> 3 == 0 || raw & 7 > 5 {
                return Err(());
            }
            let number = (raw >> 3) as usize;
            let idx = if number < 32 { (*t).lookup[number] } else { 0 };
            if idx == 0 {
                p = skip_p(raw as u32, p, limit)?;
                continue;
            }
            let cand = (*t).entries.as_ptr().add(idx as usize - 1);
            if (*cand).tag as u64 != raw {
                if raw & 7 == 2 && (*cand).packable {
                    let (n, q) = length_p(p, limit)?;
                    let packed_end = q.add(n);
                    let mut r = q;
                    while r < packed_end {
                        let (v, rr) = varint_p(r, packed_end)?;
                        r = rr;
                        ctx.pending.push(((*cand).ordinal as u32, v as u32));
                    }
                    p = packed_end;
                    e = cand;
                    continue;
                }
                p = skip_p(raw as u32, p, limit)?;
                continue;
            }
            e = cand;
        }
        let ent = *e;
        e = (*t).entries.as_ptr().add(ent.next as usize);
        let slot = base.add(ent.slot as usize);
        match ent.kind {
            K_VARINT32 => {
                let (v, q) = varint_p(p, limit)?;
                p = q;
                slot.cast::<u32>().write_unaligned(v as u32);
                hasbits |= ((ent.explicit | (v as u32 != 0)) as u32) << ent.ordinal;
            }
            K_VARINT64 => {
                let (v, q) = varint_p(p, limit)?;
                p = q;
                slot.cast::<u64>().write_unaligned(v);
                hasbits |= ((ent.explicit | (v != 0)) as u32) << ent.ordinal;
            }
            K_BOOL => {
                let (v, q) = varint_p(p, limit)?;
                p = q;
                slot.cast::<u32>().write_unaligned((v != 0) as u32);
                hasbits |= ((ent.explicit | (v != 0)) as u32) << ent.ordinal;
            }
            K_FIXED32 => {
                if p.add(4) > limit {
                    return Err(());
                }
                let v = p.cast::<u32>().read_unaligned();
                p = p.add(4);
                slot.cast::<u32>().write_unaligned(v);
                hasbits |= ((ent.explicit | (v != 0)) as u32) << ent.ordinal;
            }
            K_FIXED64 => {
                if p.add(8) > limit {
                    return Err(());
                }
                let v = p.cast::<u64>().read_unaligned();
                p = p.add(8);
                slot.cast::<u64>().write_unaligned(v);
                hasbits |= ((ent.explicit | (v != 0)) as u32) << ent.ordinal;
            }
            K_STRING => {
                let (n, q) = length_p(p, limit)?;
                p = q.add(n);
                let h = put_string(ctx, q, n)?;
                slot.cast::<u32>().write_unaligned(h);
                hasbits |= ((ent.explicit | (n != 0)) as u32) << ent.ordinal;
            }
            K_RSTRING => {
                let (n, q) = length_p(p, limit)?;
                p = q.add(n);
                let h = put_string(ctx, q, n)?;
                ctx.pending.push((ent.ordinal as u32, h));
            }
            K_RVARINT => {
                let (v, q) = varint_p(p, limit)?;
                p = q;
                ctx.pending.push((ent.ordinal as u32, v as u32));
            }
            K_RMESSAGE_SKIP => {
                let (n, q) = length_p(p, limit)?;
                p = q.add(n);
                slot.cast::<u32>().write_unaligned(slot.cast::<u32>().read_unaligned() + 1);
                hasbits |= 1 << ent.ordinal;
            }
            K_MESSAGE | K_RMESSAGE => {
                let (n, q) = length_p(p, limit)?;
                if ctx.depth >= 63 {
                    return Err(());
                }
                ctx.frames[ctx.depth] = F2 {
                    t,
                    base,
                    limit,
                    hasbits,
                    next: ent.next as u32,
                    mark: mark as u32,
                    ordinal: ent.ordinal,
                    singular: ent.kind == K_MESSAGE,
                    slot: ent.slot,
                };
                ctx.depth += 1;
                t = tables.as_ptr().add(ent.child as usize);
                base = alloc_record(ctx, (*t).record_bytes as usize)?;
                hasbits = 0;
                p = q;
                limit = q.add(n);
                e = (*t).entries.as_ptr();
                mark = ctx.pending.len();
            }
            _ => return Err(()),
        }
    }
}

// ---- tail-call form: each handler is a function with the upb parameter set, chained with `become`.

type R = Result<(), ()>;

/// Read the next tag and jump to its handler (the dispatch loop of upb's fast table).
#[inline(always)]
unsafe fn dispatch(ctx: &mut Ctx, p: *const u8, limit: *const u8, base: *mut u8, hasbits: u32, t: *const FType, e: *const FEntry) -> R {
    if p >= limit {
        become h_close(ctx, p, limit, base, hasbits, t, e);
    }
    let (raw, q) = match varint_p(p, limit) {
        Ok(v) => v,
        Err(()) => return Err(()),
    };
    if (*e).tag as u64 != raw {
        become h_miss(ctx, p, limit, base, hasbits, t, e);
    }
    match (*e).kind {
        K_VARINT32 => become h_varint32(ctx, q, limit, base, hasbits, t, e),
        K_BOOL => become h_bool(ctx, q, limit, base, hasbits, t, e),
        K_STRING => become h_string(ctx, q, limit, base, hasbits, t, e),
        K_RSTRING => become h_rstring(ctx, q, limit, base, hasbits, t, e),
        K_RMESSAGE_SKIP => become h_skipmsg(ctx, q, limit, base, hasbits, t, e),
        K_MESSAGE | K_RMESSAGE => become h_message(ctx, q, limit, base, hasbits, t, e),
        K_VARINT64 => become h_varint64(ctx, q, limit, base, hasbits, t, e),
        K_RVARINT => become h_rvarint(ctx, q, limit, base, hasbits, t, e),
        _ => Err(()),
    }
}

#[inline(never)]
unsafe fn h_miss(ctx: &mut Ctx, p: *const u8, limit: *const u8, base: *mut u8, hasbits: u32, t: *const FType, _e: *const FEntry) -> R {
    let (raw, p) = match varint_p(p, limit) { Ok(v) => v, Err(()) => return Err(()) };
    if raw > u32::MAX as u64 || raw >> 3 == 0 || raw & 7 > 5 {
        return Err(());
    }
    let number = (raw >> 3) as usize;
    let idx = if number < 32 { (*t).lookup[number] } else { 0 };
    if idx == 0 {
        let p = skip_p(raw as u32, p, limit)?;
        let e = (*t).entries.as_ptr();
        become dispatch(ctx, p, limit, base, hasbits, t, e);
    }
    let cand = (*t).entries.as_ptr().add(idx as usize - 1);
    if (*cand).tag as u64 != raw {
        if raw & 7 == 2 && (*cand).packable {
            let (n, q) = length_p(p, limit)?;
            let packed_end = q.add(n);
            let mut r = q;
            while r < packed_end {
                let (v, rr) = varint_p(r, packed_end)?;
                r = rr;
                ctx.pending.push(((*cand).ordinal as u32, v as u32));
            }
            become dispatch(ctx, packed_end, limit, base, hasbits, t, cand);
        }
        let p = skip_p(raw as u32, p, limit)?;
        become dispatch(ctx, p, limit, base, hasbits, t, cand);
    }
    // Known field out of predicted order: re-dispatch on it directly.
    match (*cand).kind {
        K_VARINT32 => become h_varint32(ctx, p, limit, base, hasbits, t, cand),
        K_BOOL => become h_bool(ctx, p, limit, base, hasbits, t, cand),
        K_STRING => become h_string(ctx, p, limit, base, hasbits, t, cand),
        K_RSTRING => become h_rstring(ctx, p, limit, base, hasbits, t, cand),
        K_RMESSAGE_SKIP => become h_skipmsg(ctx, p, limit, base, hasbits, t, cand),
        K_MESSAGE | K_RMESSAGE => become h_message(ctx, p, limit, base, hasbits, t, cand),
        K_VARINT64 => become h_varint64(ctx, p, limit, base, hasbits, t, cand),
        K_RVARINT => become h_rvarint(ctx, p, limit, base, hasbits, t, cand),
        _ => Err(()),
    }
}

#[inline(never)]
unsafe fn h_varint32(ctx: &mut Ctx, p: *const u8, limit: *const u8, base: *mut u8, hasbits: u32, t: *const FType, e: *const FEntry) -> R {
    let (v, p) = match varint_p(p, limit) { Ok(v) => v, Err(()) => return Err(()) };
    base.add((*e).slot as usize).cast::<u32>().write_unaligned(v as u32);
    let hasbits = hasbits | (((*e).explicit | (v as u32 != 0)) as u32) << (*e).ordinal;
    become dispatch(ctx, p, limit, base, hasbits, t, (*t).entries.as_ptr().add((*e).next as usize));
}

#[inline(never)]
unsafe fn h_varint64(ctx: &mut Ctx, p: *const u8, limit: *const u8, base: *mut u8, hasbits: u32, t: *const FType, e: *const FEntry) -> R {
    let (v, p) = match varint_p(p, limit) { Ok(v) => v, Err(()) => return Err(()) };
    base.add((*e).slot as usize).cast::<u64>().write_unaligned(v);
    let hasbits = hasbits | (((*e).explicit | (v != 0)) as u32) << (*e).ordinal;
    become dispatch(ctx, p, limit, base, hasbits, t, (*t).entries.as_ptr().add((*e).next as usize));
}

#[inline(never)]
unsafe fn h_bool(ctx: &mut Ctx, p: *const u8, limit: *const u8, base: *mut u8, hasbits: u32, t: *const FType, e: *const FEntry) -> R {
    let (v, p) = match varint_p(p, limit) { Ok(v) => v, Err(()) => return Err(()) };
    base.add((*e).slot as usize).cast::<u32>().write_unaligned((v != 0) as u32);
    let hasbits = hasbits | (((*e).explicit | (v != 0)) as u32) << (*e).ordinal;
    become dispatch(ctx, p, limit, base, hasbits, t, (*t).entries.as_ptr().add((*e).next as usize));
}

#[inline(never)]
unsafe fn h_rvarint(ctx: &mut Ctx, p: *const u8, limit: *const u8, base: *mut u8, hasbits: u32, t: *const FType, e: *const FEntry) -> R {
    let (v, p) = match varint_p(p, limit) { Ok(v) => v, Err(()) => return Err(()) };
    ctx.pending.push(((*e).ordinal as u32, v as u32));
    become dispatch(ctx, p, limit, base, hasbits, t, (*t).entries.as_ptr().add((*e).next as usize));
}

#[inline(never)]
unsafe fn h_string(ctx: &mut Ctx, p: *const u8, limit: *const u8, base: *mut u8, hasbits: u32, t: *const FType, e: *const FEntry) -> R {
    let (n, q) = match length_p(p, limit) { Ok(v) => v, Err(()) => return Err(()) };
    let h = match put_string(ctx, q, n) { Ok(v) => v, Err(()) => return Err(()) };
    base.add((*e).slot as usize).cast::<u32>().write_unaligned(h);
    let hasbits = hasbits | (((*e).explicit | (n != 0)) as u32) << (*e).ordinal;
    become dispatch(ctx, q.add(n), limit, base, hasbits, t, (*t).entries.as_ptr().add((*e).next as usize));
}

#[inline(never)]
unsafe fn h_rstring(ctx: &mut Ctx, p: *const u8, limit: *const u8, base: *mut u8, hasbits: u32, t: *const FType, e: *const FEntry) -> R {
    let (n, q) = match length_p(p, limit) { Ok(v) => v, Err(()) => return Err(()) };
    let h = match put_string(ctx, q, n) { Ok(v) => v, Err(()) => return Err(()) };
    ctx.pending.push(((*e).ordinal as u32, h));
    become dispatch(ctx, q.add(n), limit, base, hasbits, t, (*t).entries.as_ptr().add((*e).next as usize));
}

#[inline(never)]
unsafe fn h_skipmsg(ctx: &mut Ctx, p: *const u8, limit: *const u8, base: *mut u8, hasbits: u32, t: *const FType, e: *const FEntry) -> R {
    let (n, q) = match length_p(p, limit) { Ok(v) => v, Err(()) => return Err(()) };
    let slot = base.add((*e).slot as usize).cast::<u32>();
    slot.write_unaligned(slot.read_unaligned() + 1);
    let hasbits = hasbits | 1 << (*e).ordinal;
    become dispatch(ctx, q.add(n), limit, base, hasbits, t, (*t).entries.as_ptr().add((*e).next as usize));
}

#[inline(never)]
unsafe fn h_message(ctx: &mut Ctx, p: *const u8, limit: *const u8, base: *mut u8, hasbits: u32, t: *const FType, e: *const FEntry) -> R {
    let (n, q) = match length_p(p, limit) { Ok(v) => v, Err(()) => return Err(()) };
    if ctx.depth >= 63 {
        return Err(());
    }
    ctx.frames[ctx.depth] = F2 {
        t,
        base,
        limit,
        hasbits,
        next: (*e).next as u32,
        mark: ctx.pending.len() as u32, // the parent's mark is restored from the child's frame below
        ordinal: (*e).ordinal,
        singular: (*e).kind == K_MESSAGE,
        slot: (*e).slot,
    };
    ctx.depth += 1;
    let tables_base = t.sub((t as usize - ctx_tables_base(ctx)) / std::mem::size_of::<FType>());
    let ct = tables_base.add((*e).child as usize);
    let cbase = match alloc_record(ctx, (*ct).record_bytes as usize) { Ok(v) => v, Err(()) => return Err(()) };
    become dispatch(ctx, q, q.add(n), cbase, 0, ct, (*ct).entries.as_ptr());
}

// The tail form has no `tables` argument (upb keeps the table pointer in the entry); the probe
// stores the tables base in the context instead.
static mut TABLES_BASE: usize = 0;
#[inline(always)]
unsafe fn ctx_tables_base(_ctx: &Ctx) -> usize {
    TABLES_BASE
}

#[inline(never)]
unsafe fn h_close(ctx: &mut Ctx, _p: *const u8, limit: *const u8, base: *mut u8, mut hasbits: u32, t: *const FType, _e: *const FEntry) -> R {
    // mark: pending elements of this record start where the parent's frame recorded the length
    let mark = if ctx.depth == 0 { 0 } else { ctx.frames[ctx.depth - 1].mark as usize };
    if ctx.pending.len() > mark {
        if close_lists2(ctx, &*t, base, mark, &mut hasbits).is_err() {
            return Err(());
        }
    }
    base.add(4).cast::<u32>().write_unaligned(hasbits);
    if ctx.depth == 0 {
        return Ok(());
    }
    ctx.depth -= 1;
    let f = ctx.frames[ctx.depth];
    let child = base.offset_from(ctx.out) as u32;
    let mut phas = f.hasbits;
    if f.singular {
        f.base.add(f.slot as usize).cast::<u32>().write_unaligned(child);
        phas |= 1 << f.ordinal;
    } else {
        ctx.pending.push((f.ordinal as u32, child));
    }
    become dispatch(ctx, limit, f.limit, f.base, phas, f.t, (*f.t).entries.as_ptr().add(f.next as usize));
}


// ---------------------------------------------------------------- varint strategies

fn varint_fixture() -> (Vec<u8>, u64) {
    let mut x = 0x9E3779B97F4A7C15u64;
    let mut out = Vec::new();
    let mut sum = 0u64;
    for _ in 0..4096 {
        x ^= x << 13;
        x ^= x >> 7;
        x ^= x << 17;
        let r = x % 100;
        let v = if r < 50 {
            x >> 57
        } else if r < 75 {
            x >> 50
        } else if r < 90 {
            x >> 43
        } else {
            x >> 2
        };
        sum = sum.wrapping_add(v);
        put_varint(&mut out, v);
    }
    out.extend_from_slice(&[0u8; 16]); // slack for 8-byte loads
    (out, sum)
}

fn varint_byte_loop(data: &[u8], n: usize) -> u64 {
    let mut pos = 0;
    let mut sum = 0u64;
    for _ in 0..n {
        sum = sum.wrapping_add(read_varint(data, &mut pos).unwrap());
    }
    sum
}

fn varint_buffa(data: &[u8], n: usize) -> u64 {
    let mut buf = data;
    let mut sum = 0u64;
    for _ in 0..n {
        sum = sum.wrapping_add(buffa::encoding::decode_varint(&mut buf).unwrap());
    }
    sum
}

/// 8-byte load, continuation mask, shift-and-mask extraction; falls back for > 8 bytes.
#[inline(always)]
fn varint_wide_one(data: &[u8], pos: &mut usize) -> u64 {
    let x = u64::from_le_bytes(data[*pos..*pos + 8].try_into().unwrap());
    let m = !x & 0x8080808080808080;
    if m == 0 {
        return read_varint(data, pos).unwrap();
    }
    let len = (m.trailing_zeros() / 8 + 1) as usize;
    let masked = if len == 8 { x } else { x & ((1u64 << (8 * len)) - 1) };
    *pos += len;
    let v = masked & 0x7f7f7f7f7f7f7f7f;
    let v = (v & 0x007f007f007f007f) | ((v & 0x7f007f007f007f00) >> 1);
    let v = (v & 0x00003fff00003fff) | ((v & 0x3fff00003fff0000) >> 2);
    (v & 0x000000000fffffff) | ((v & 0x0fffffff00000000) >> 4)
}

fn varint_wide(data: &[u8], n: usize) -> u64 {
    let mut pos = 0;
    let mut sum = 0u64;
    for _ in 0..n {
        sum = sum.wrapping_add(varint_wide_one(data, &mut pos));
    }
    sum
}

#[cfg(target_arch = "x86_64")]
#[target_feature(enable = "bmi2")]
unsafe fn varint_pext(data: &[u8], n: usize) -> u64 {
    use std::arch::x86_64::_pext_u64;
    let mut pos = 0;
    let mut sum = 0u64;
    for _ in 0..n {
        let x = u64::from_le_bytes(data[pos..pos + 8].try_into().unwrap());
        let m = !x & 0x8080808080808080;
        if m == 0 {
            sum = sum.wrapping_add(read_varint(data, &mut pos).unwrap());
            continue;
        }
        let len = (m.trailing_zeros() / 8 + 1) as usize;
        let masked = if len == 8 { x } else { x & ((1u64 << (8 * len)) - 1) };
        pos += len;
        sum = sum.wrapping_add(unsafe { _pext_u64(masked, 0x7f7f7f7f7f7f7f7f) });
    }
    sum
}

/// Count varints by counting bytes below 0x80 eight at a time (the packed-field index rate).
fn varint_count(data: &[u8], bytes: usize) -> u64 {
    let mut i = 0;
    let mut count = 0u64;
    while i + 8 <= bytes {
        let x = u64::from_le_bytes(data[i..i + 8].try_into().unwrap());
        count += (!x & 0x8080808080808080).count_ones() as u64;
        i += 8;
    }
    while i < bytes {
        count += (data[i] < 0x80) as u64;
        i += 1;
    }
    count
}

// ---------------------------------------------------------------- harness

fn bench(name: &str, bytes: usize, reps: usize, mut f: impl FnMut() -> u64) {
    let mut best = u128::MAX;
    let mut sink = 0u64;
    for _ in 0..7 {
        let start = Instant::now();
        for _ in 0..reps {
            sink = sink.wrapping_add(black_box(f()));
        }
        best = best.min(start.elapsed().as_nanos());
    }
    let ns_per_rep = best as f64 / reps as f64;
    let mb_s = bytes as f64 / ns_per_rep * 1e3;
    println!(
        "PROBE {name:<28} {ns_per_rep:>12.1} ns/rep {:>9.1} ns/msg {mb_s:>9.1} MB/s sink={sink:#x}",
        ns_per_rep / 32.0
    );
}

fn run_mode(mode: &str, reps: usize) {
    let msgs = fixture(mode);
    let bytes: usize = msgs.iter().map(|m| m.len()).sum();
    println!("PROBE mode={mode} messages=32 bytes={bytes} avg={}", bytes / 32);
    let compiled = compile(&type_program()).expect("compile");
    let compiled = compiled.checked().expect("magic");

    bench(&format!("{mode}/scan_flat"), bytes, reps, || {
        msgs.iter().map(|m| scan_flat(m).unwrap()).sum()
    });
    let none = Tables { validate_skipped: true, validate_utf8: false, ..tables(false, true) };
    let mut slab = Slab { words: Vec::with_capacity(1 << 16), pending: Vec::with_capacity(4096) };
    bench(&format!("{mode}/scan_nested"), bytes, reps, || {
        let mut s = 0;
        for m in &msgs {
            slab.words.clear();
            s += table_decode(&none, 0, m, 0, &mut slab).unwrap() as u64 + slab.words[0];
        }
        s
    });

    let mut scratch: Vec<Field> = Vec::new();
    bench(&format!("{mode}/view"), bytes, reps, || {
        let mut s = 0u64;
        for m in &msgs {
            let mut status = 0;
            let (r, sc) = view(compiled, m, 8 << 20, 8 << 20, 32, 8 << 20, std::mem::take(&mut scratch), &mut status);
            scratch = sc;
            let v = r.expect("view");
            s += v.count as u64 + status as u64;
            unsafe { release(v.data, v.capacity) };
        }
        s
    });

    let mut fields = Vec::with_capacity(4096);
    let mut oneofs = Vec::with_capacity(16);
    for (label, v) in [("hand", Validate::Production), ("hand_ascii", Validate::Ascii), ("hand_novf", Validate::None)] {
        bench(&format!("{mode}/{label}"), bytes, reps, || {
            let mut s = 0u64;
            for m in &msgs {
                slab.words.clear();
                hand_type(v, m, &mut slab, &mut fields, &mut oneofs).unwrap();
                s += slab.words[0] + slab.words.len() as u64;
            }
            s
        });
    }
    for (label, t) in [
        ("table", tables(false, false)),
        ("table_novf", Tables { validate_utf8: false, ..tables(false, false) }),
        ("table_sparse", tables(true, false)),
        ("table_sparse_noskipv", Tables { validate_skipped: false, ..tables(true, false) }),
        ("table_none", tables(false, true)),
    ] {
        bench(&format!("{mode}/{label}"), bytes, reps, || {
            let mut s = 0u64;
            for m in &msgs {
                slab.words.clear();
                table_decode(&t, 0, m, 0, &mut slab).unwrap();
                s += slab.words[0] + slab.words.len() as u64;
            }
            s
        });
    }
    // Task A2: the production slab decoder, normal entry point (scratch may spill) and the small
    // entry point's fixed scratch, into one reused output buffer.
    let layout = crate::slab::compile(&slab_program()).expect("slab layout");
    let mut out = vec![0u8; 16 << 20];
    let mut sizes = Vec::new();
    for m in &msgs {
        let mut at = 0;
        let o = crate::slab::decode(&layout, m, 1, out.as_mut_ptr(), out.len(), 32, 1 << 30, true, &mut at)
            .expect("slab");
        sizes.push(o.bytes as usize);
    }
    bench(&format!("{mode}/slab"), bytes, reps, || {
        let mut s = 0u64;
        let mut at = 0;
        for m in &msgs {
            let o = crate::slab::decode(&layout, m, 1, out.as_mut_ptr(), out.len(), 32, 1 << 30, true, &mut at)
                .unwrap();
            s += o.bytes + out[40] as u64;
        }
        s
    });
    if mode != "large" {
        let mut inline = crate::slab::Inline::new();
        bench(&format!("{mode}/slab_small"), bytes, reps, || {
            let mut s = 0u64;
            let mut at = 0;
            for m in &msgs {
                let o = crate::slab::decode_with(
                    &layout, m, 1, out.as_mut_ptr(), 65536, 32, 1 << 30, false, &mut at, &mut inline,
                )
                .unwrap();
                s += o.bytes + out[40] as u64;
            }
            s
        });
    }

    // SOTA probe: slab-format hand decoder and table-driven explicit-stack decoders.
    let mut sout = Out::new(16 << 20);
    let mut sfields = Vec::with_capacity(4096);
    let mut soneofs = Vec::with_capacity(16);
    let mut hs_sizes = Vec::new();
    for m in &msgs {
        hs_sizes.push(slab_type(&mut sout, m, &mut sfields, &mut soneofs).unwrap());
    }
    bench(&format!("{mode}/hand_slab"), bytes, reps, || {
        let mut s = 0u64;
        for m in &msgs {
            s += slab_type(&mut sout, m, &mut sfields, &mut soneofs).unwrap() as u64 + sout.buf[40] as u64;
        }
        s
    });
    let mut fast = Fast::new();
    let mut f_sizes = Vec::new();
    for m in &msgs {
        f_sizes.push(fast.decode(&mut sout, m).unwrap());
    }
    bench(&format!("{mode}/fast"), bytes, reps, || {
        let mut s = 0u64;
        for m in &msgs {
            s += fast.decode(&mut sout, m).unwrap() as u64 + sout.buf[40] as u64;
        }
        s
    });
    let mut fast2 = Fast2::new(&mut sout);
    unsafe { TABLES_BASE = fast2.tables.as_ptr() as usize; }
    let mut f2_sizes = Vec::new();
    for m in &msgs {
        f2_sizes.push(fast2.decode(m).unwrap());
    }
    bench(&format!("{mode}/fast2"), bytes, reps, || {
        let mut s = 0u64;
        for m in &msgs {
            s += fast2.decode(m).unwrap() as u64 + sout.buf[40] as u64;
        }
        s
    });
    let mut ft_sizes = Vec::new();
    for m in &msgs {
        ft_sizes.push(fast2.decode_tail(m).unwrap());
    }
    bench(&format!("{mode}/fast_tail"), bytes, reps, || {
        let mut s = 0u64;
        for m in &msgs {
            s += fast2.decode_tail(m).unwrap() as u64 + sout.buf[40] as u64;
        }
        s
    });
    println!(
        "PROBE {mode}/sota_size message[1]={} B hand_slab={} B fast={} fast2={} fast_tail={} all={} {} {} {}",
        msgs[1].len(), hs_sizes[1], f_sizes[1], f2_sizes[1], ft_sizes[1],
        hs_sizes.iter().sum::<usize>(), f_sizes.iter().sum::<usize>(), f2_sizes.iter().sum::<usize>(), ft_sizes.iter().sum::<usize>()
    );
    let total: usize = sizes.iter().sum();
    println!(
        "PROBE {mode}/slab_size message[1]={} B slab={} B ({:.2}x) all={} B over {} B input ({:.2}x) max_ratio={:.2}x",
        msgs[1].len(),
        sizes[1],
        sizes[1] as f64 / msgs[1].len() as f64,
        total,
        bytes,
        total as f64 / bytes as f64,
        sizes.iter().zip(&msgs).map(|(s, m)| *s as f64 / m.len() as f64).fold(0.0, f64::max)
    );

    // Slab size for the full table decode (words × 8 bytes) against the view's index bytes.
    slab.words.clear();
    table_decode(&tables(false, false), 0, &msgs[1], 0, &mut slab).unwrap();
    let mut status = 0;
    let (r, sc) = view(compiled, &msgs[1], 8 << 20, 8 << 20, 32, 8 << 20, Vec::new(), &mut status);
    let v = r.unwrap();
    println!(
        "PROBE {mode}/size message[1]={} B slab={} B view={} B (entries={} words={})",
        msgs[1].len(),
        slab.words.len() * 8,
        v.count * 24 + v.words * 4,
        v.count,
        v.words
    );
    unsafe { release(v.data, v.capacity) };
    drop(sc);
}

#[test]
fn probe() {
    println!("PROBE host={}", std::fs::read_to_string("/proc/cpuinfo").map(|s| s.lines().find(|l| l.starts_with("model name")).unwrap_or("").to_string()).unwrap_or_default());
    run_mode("small", 2000);
    run_mode("holdout", 1000);
    run_mode("large", 8);

    let (data, expected) = varint_fixture();
    let n = 4096;
    let bytes = data.len() - 16;
    println!("PROBE varints n={n} bytes={bytes} avg={:.2} B", bytes as f64 / n as f64);
    let check = |v: u64| assert_eq!(v, expected);
    check(varint_byte_loop(&data, n));
    check(varint_buffa(&data[..bytes], n));
    check(varint_wide(&data, n));
    let mut vb = |name: &str, mut f: Box<dyn FnMut() -> u64>| {
        let mut best = u128::MAX;
        let mut sink = 0;
        for _ in 0..7 {
            let start = Instant::now();
            for _ in 0..200 {
                sink ^= black_box(f());
            }
            best = best.min(start.elapsed().as_nanos());
        }
        let ns = best as f64 / 200.0;
        println!(
            "PROBE varint_{name:<16} {:>8.3} ns/varint {:>9.1} MB/s sink={sink:#x}",
            ns / n as f64,
            bytes as f64 / ns * 1e3
        );
    };
    let d = data.clone();
    vb("byte_loop", Box::new(move || varint_byte_loop(&d, n)));
    let d = data.clone();
    vb("buffa", Box::new(move || varint_buffa(&d[..bytes], n)));
    let d = data.clone();
    vb("wide8", Box::new(move || varint_wide(&d, n)));
    #[cfg(target_arch = "x86_64")]
    if std::is_x86_feature_detected!("bmi2") {
        check(unsafe { varint_pext(&data, n) });
        let d = data.clone();
        vb("pext", Box::new(move || unsafe { varint_pext(&d, n) }));
    }
    let d = data.clone();
    vb("count_only", Box::new(move || varint_count(&d, bytes)));

    // Per-string validation cost on fixture-sized names.
    let names: Vec<Vec<u8>> = (0..32).map(|j| format!("column-{j}").into_bytes()).collect();
    let unicode: Vec<Vec<u8>> = (0..32).map(|j| format!("catalog-{j}-λ").into_bytes()).collect();
    for (label, set) in [("ascii", &names), ("unicode", &unicode)] {
        let total: usize = set.iter().map(|s| s.len()).sum();
        bench(&format!("utf8_{label}/production"), total, 2000, || {
            set.iter().map(|s| crate::unicode::utf16_length(s).unwrap() as u64).sum()
        });
        bench(&format!("utf8_{label}/ascii_fast"), total, 2000, || {
            set.iter().map(|s| ascii_or_validate(s).map(|_| 1u64).unwrap()).sum()
        });
    }
}
