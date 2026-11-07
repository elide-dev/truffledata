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
use crate::Field;
use crate::typed::view::{compile, release, view};
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
