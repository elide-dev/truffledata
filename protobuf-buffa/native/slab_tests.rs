//! Task A2: the native slab decoder against the Task A1 golden slabs, the ABI 9 view decoder
//! (differential), and targeted semantics; capacity, budgets, allocation and failure injection.
use super::*;
use crate::allocation_probe;
use crate::{Field, PANIC, seam};

// ---- Schema programs (NativeSchema's TDB3 tables plus the TDL1 section) ----

/// One field: number, TDB3 kind (ProtoPlan.Kind ordinal + 1), flags (1 repeated, 2 map,
/// 4 explicit presence, 8 required, 16 open enum), child type, closed enum values, oneof index.
#[derive(Clone)]
struct F {
    number: u32,
    kind: u32,
    flags: u32,
    child: u32,
    values: Vec<i32>,
    oneof: u32,
}

const DOUBLE: u32 = 1;
const FLOAT: u32 = 2;
const INT64: u32 = 3;
const UINT64: u32 = 4;
const INT32: u32 = 5;
const FIXED32: u32 = 7;
const BOOL: u32 = 8;
const STRING: u32 = 9;
const GROUP: u32 = 10;
const MESSAGE: u32 = 11;
const BYTES: u32 = 12;
const ENUM: u32 = 14;
const SINT32: u32 = 17;
const SINT64: u32 = 18;
const REPEATED: u32 = 1;
const MAP: u32 = 2;
const EXPLICIT: u32 = 4;
const REQUIRED: u32 = 8;
const OPEN: u32 = 16;

fn f(number: u32, kind: u32, flags: u32) -> F {
    F {
        number,
        kind,
        flags,
        child: u32::MAX,
        values: Vec::new(),
        oneof: NONE,
    }
}

fn m(number: u32, kind: u32, flags: u32, child: u32) -> F {
    F {
        child,
        ..f(number, kind, flags)
    }
}

fn e(number: u32, flags: u32, values: &[i32]) -> F {
    F {
        values: values.to_vec(),
        ..f(number, ENUM, flags)
    }
}

fn o(field: F, oneof: u32) -> F {
    F { oneof, ..field }
}

/// (type flags: 1 map entry, 512 no required fields; fields)
type T = (u32, Vec<F>);

fn kind_code(field: &F) -> u32 {
    let scalar = match field.kind {
        5 | 13 | 17 | 14 | 7 | 15 => 1,
        3 | 4 | 18 | 6 | 16 => 2,
        2 => 3,
        1 => 4,
        8 => 5,
        9 => 6,
        12 => 7,
        _ => 8,
    };
    if field.flags & MAP != 0 {
        17
    } else if field.flags & REPEATED != 0 {
        scalar + 8
    } else {
        scalar
    }
}

/// The layout of docs/SLAB.md, written independently of `compile` (which recomputes it).
fn layout_section(types: &[T]) -> Vec<u8> {
    let mut tables: Vec<u32> = Vec::new();
    for (_, fields) in types {
        let n = fields.len() as u32;
        let words = n.div_ceil(32);
        let cases = fields
            .iter()
            .filter(|x| x.oneof != NONE)
            .map(|x| x.oneof + 1)
            .max()
            .unwrap_or(0);
        let wide = |x: &F| matches!(kind_code(x), 2 | 4);
        let mut cursor = 4 + 4 * words + 2 * cases;
        cursor = if fields.iter().any(wide) {
            (cursor + 7) & !7
        } else {
            (cursor + 3) & !3
        };
        let mut slots = vec![0u32; fields.len()];
        for pass in [8, 4] {
            for (i, x) in fields.iter().enumerate() {
                if (if wide(x) { 8 } else { 4 }) == pass {
                    slots[i] = cursor;
                    cursor += pass;
                }
            }
        }
        tables.extend([(cursor + 7) & !7, words, cases, n]);
        for (i, x) in fields.iter().enumerate() {
            tables.extend([slots[i], kind_code(x), x.oneof]);
        }
    }
    let table_bytes: Vec<u8> = tables.iter().flat_map(|w| w.to_le_bytes()).collect();
    let mut out = Vec::new();
    for w in [TDL1, 1, types.len() as u32, crc32(&table_bytes)] {
        out.extend_from_slice(&w.to_le_bytes());
    }
    out.extend(table_bytes);
    out
}

fn program(types: &[T]) -> Vec<u8> {
    let mut words: Vec<u32> = vec![0x5444_4233, types.len() as u32];
    let mut offset = 8 + 4 * types.len();
    for (_, fields) in types {
        words.push(offset as u32);
        offset += 16 + 40 * fields.len();
    }
    let name = offset as u32;
    let mut tables = name as usize + 1;
    let mut enums = Vec::new();
    for (flags, fields) in types {
        words.extend([fields.len() as u32, *flags, name, 1]);
        for x in fields {
            let (table, n) = if x.values.is_empty() {
                (0, 0)
            } else {
                let at = tables;
                tables += 12 * x.values.len();
                enums.extend(x.values.iter().copied());
                (at as u32, x.values.len() as u32)
            };
            words.extend([x.number, x.kind, x.flags, x.child, table, n, name, 1, name, 1]);
        }
    }
    let mut bytes: Vec<u8> = words.iter().flat_map(|w| w.to_le_bytes()).collect();
    bytes.push(b'x');
    for value in enums {
        for word in [value as u32, name, 1] {
            bytes.extend_from_slice(&word.to_le_bytes());
        }
    }
    while bytes.len() % 4 != 0 {
        bytes.push(0);
    }
    let section = bytes.len() as u32;
    bytes.extend(layout_section(types));
    bytes.extend_from_slice(&section.to_le_bytes());
    bytes.extend_from_slice(&TDL1.to_le_bytes());
    bytes
}

// ---- The LayoutTest golden schemas ----

fn timestamp() -> Vec<T> {
    vec![(512, vec![f(1, INT64, 0), f(2, INT32, 0)])]
}

fn choice() -> Vec<T> {
    vec![
        (
            512,
            vec![
                o(f(1, INT32, EXPLICIT), 0),
                o(f(2, STRING, EXPLICIT), 0),
                o(m(3, MESSAGE, EXPLICIT, 1), 0),
                o(f(4, INT64, EXPLICIT), 1),
                o(f(5, BOOL, EXPLICIT), 1),
                o(f(6, BYTES, EXPLICIT), 1),
                f(7, INT32, 0),
            ],
        ),
        (512, vec![f(1, INT32, 0)]),
    ]
}

fn unknowns() -> Vec<T> {
    vec![(
        512,
        vec![
            f(1, INT32, EXPLICIT),
            e(2, EXPLICIT, &[0, 1]),
            e(3, REPEATED, &[0, 1]),
        ],
    )]
}

fn merge_schema() -> Vec<T> {
    vec![
        (512, vec![m(1, MESSAGE, EXPLICIT, 1)]),
        (
            512,
            vec![f(1, INT32, 0), f(2, INT32, REPEATED), f(3, STRING, 0)],
        ),
    ]
}

fn maps() -> Vec<T> {
    vec![
        (
            512,
            vec![
                m(1, MESSAGE, REPEATED | MAP, 1),
                m(2, MESSAGE, REPEATED | MAP, 2),
            ],
        ),
        (513, vec![f(1, STRING, 0), f(2, INT32, 0)]),
        (513, vec![f(1, INT64, 0), m(2, MESSAGE, EXPLICIT, 3)]),
        (512, vec![f(1, INT32, 0)]),
    ]
}

fn lists() -> Vec<T> {
    vec![(
        512,
        vec![
            f(1, INT32, REPEATED),
            f(2, INT64, REPEATED),
            f(3, DOUBLE, REPEATED),
            f(4, BOOL, REPEATED),
            f(5, FIXED32, REPEATED),
            f(6, SINT64, REPEATED),
            f(7, FLOAT, REPEATED),
            f(8, UINT64, REPEATED),
        ],
    )]
}

fn texts() -> Vec<T> {
    vec![(
        512,
        vec![
            f(1, STRING, 0),
            f(2, STRING, 0),
            f(3, STRING, 0),
            f(4, STRING, 0),
            f(5, STRING, EXPLICIT),
            f(6, STRING, REPEATED),
            f(7, BYTES, 0),
            f(8, BYTES, REPEATED),
            f(9, STRING, 0),
            f(10, INT32, 0),
        ],
    )]
}

fn wide() -> Vec<T> {
    vec![(
        512,
        (0..130)
            .map(|i| f(i + 1, if i % 2 == 0 { INT32 } else { INT64 }, EXPLICIT))
            .collect(),
    )]
}

fn legacy() -> Vec<T> {
    vec![
        (
            0,
            vec![
                f(1, INT32, EXPLICIT | REQUIRED),
                m(2, GROUP, EXPLICIT, 1),
                m(4, MESSAGE, EXPLICIT, 0),
                f(5, FLOAT, EXPLICIT),
                f(6, UINT64, EXPLICIT),
            ],
        ),
        (512, vec![f(3, SINT32, EXPLICIT)]),
    ]
}

/// google.protobuf.Type's closure in Layout.compile order: Type, Field, Option, SourceContext, Any.
fn google_type() -> Vec<T> {
    vec![
        (
            512,
            vec![
                f(1, STRING, 0),
                m(2, MESSAGE, REPEATED, 1),
                f(3, STRING, REPEATED),
                m(4, MESSAGE, REPEATED, 2),
                m(5, MESSAGE, EXPLICIT, 3),
                e(6, OPEN, &[0, 1, 2]),
                f(7, STRING, 0),
            ],
        ),
        (
            512,
            vec![
                e(1, OPEN, &[0]),
                e(2, OPEN, &[0]),
                f(3, INT32, 0),
                f(4, STRING, 0),
                f(6, STRING, 0),
                f(7, INT32, 0),
                f(8, BOOL, 0),
                m(9, MESSAGE, REPEATED, 2),
                f(10, STRING, 0),
                f(11, STRING, 0),
            ],
        ),
        (512, vec![f(1, STRING, 0), m(2, MESSAGE, EXPLICIT, 4)]),
        (512, vec![f(1, STRING, 0)]),
        (512, vec![f(1, STRING, 0), f(2, BYTES, 0)]),
    ]
}

/// The golden wires and slabs, from runfiles (`data` of the test target; the test runs in the
/// workspace runfiles directory).
fn goldens() -> Vec<(&'static str, Vec<T>, Vec<u8>, Vec<u8>)> {
    let read = |name: &str, ext: &str| {
        let path = format!("protobuf-runtime/src/test/resources/slab/{name}.{ext}");
        std::fs::read(&path).unwrap_or_else(|e| panic!("{path}: {e}"))
    };
    [
        ("timestamp", timestamp()),
        ("type", google_type()),
        ("map", maps()),
        ("oneof", choice()),
        ("unknown", unknowns()),
        ("merge", merge_schema()),
        ("lists", lists()),
        ("strings", texts()),
        ("wide", wide()),
        ("legacy", legacy()),
    ]
    .into_iter()
    .map(|(name, schema)| (name, schema, read(name, "wire"), read(name, "bin")))
    .collect()
}

// ---- Decoding helpers ----

const DEPTH: u32 = 32;
const MAX_SLAB: u64 = 1 << 26;

fn compiled(types: &[T]) -> Compiled {
    compile(&program(types)).expect("program compiles")
}

/// Decode through the normal entry point into an exactly sized buffer (sizing first).
fn slab(c: &Compiled, input: &[u8]) -> std::result::Result<Vec<u8>, i32> {
    slab_depth(c, input, DEPTH)
}

fn slab_depth(c: &Compiled, input: &[u8], depth: u32) -> std::result::Result<Vec<u8>, i32> {
    let mut at = 0;
    let sized = decode(
        c,
        input,
        FLAG_OWNING | FLAG_SIZING_ONLY,
        std::ptr::null_mut(),
        0,
        depth,
        MAX_SLAB,
        true,
        &mut at,
    )?;
    let mut out = vec![0xA5u8; sized.bytes as usize];
    let written = decode(
        c,
        input,
        FLAG_OWNING,
        out.as_mut_ptr(),
        out.len(),
        depth,
        MAX_SLAB,
        true,
        &mut at,
    )
    .unwrap();
    assert_eq!(written, sized, "sizing and writing agree");
    Ok(out)
}

/// The rejecting status and error offset.
fn rejection(c: &Compiled, input: &[u8]) -> (i32, usize) {
    let mut at = 0;
    let mut out = vec![0u8; 1 << 16];
    match decode(
        c,
        input,
        FLAG_OWNING,
        out.as_mut_ptr(),
        out.len(),
        DEPTH,
        MAX_SLAB,
        true,
        &mut at,
    ) {
        Ok(_) => (0, 0),
        Err(code) => (code, at),
    }
}

fn u32_at(s: &[u8], at: usize) -> u32 {
    u32::from_le_bytes(s[at..at + 4].try_into().unwrap())
}

fn u64_at(s: &[u8], at: usize) -> u64 {
    u64::from_le_bytes(s[at..at + 8].try_into().unwrap())
}

fn present(s: &[u8], record: usize, ordinal: usize) -> bool {
    u32_at(s, record + 4 + 4 * (ordinal / 32)) & (1 << (ordinal % 32)) != 0
}

fn slot(c: &Compiled, ty: usize, ordinal: usize) -> usize {
    c.types[ty].fields[ordinal].slot as usize
}

/// (payload bytes, ascii, utf16) of a string header.
fn string_at(s: &[u8], header: usize) -> (&[u8], bool, u32) {
    let word = u32_at(s, header);
    let len = (word & 0x3FFF_FFFF) as usize;
    let ascii = word & ASCII != 0;
    let (start, utf16) = if ascii {
        (header + 8, len as u32)
    } else {
        (header + 12, u32_at(s, header + 8))
    };
    (&s[start..start + len], ascii, utf16)
}

fn list_at(s: &[u8], list: usize, width: usize) -> Vec<u64> {
    let n = u32_at(s, list) as usize;
    (0..n)
        .map(|i| {
            let at = list + 8 + width * i;
            match width {
                1 => s[at] as u64,
                4 => u32_at(s, at) as u64,
                _ => u64_at(s, at),
            }
        })
        .collect()
}

fn unknown_span(s: &[u8], record: usize) -> Option<&[u8]> {
    let table = u32_at(s, 28) as usize;
    if table == 0 {
        return None;
    }
    let n = u32_at(s, table) as usize;
    (0..n).find_map(|i| {
        let entry = table + 8 + 8 * i;
        (u32_at(s, entry) as usize == record).then(|| {
            let span = u32_at(s, entry + 4) as usize;
            &s[span + 4..span + 4 + u32_at(s, span) as usize]
        })
    })
}

// ---- Wire helpers ----

struct Rng(u64);
impl Rng {
    fn next(&mut self) -> u64 {
        self.0 ^= self.0 << 13;
        self.0 ^= self.0 >> 7;
        self.0 ^= self.0 << 17;
        self.0
    }
    fn below(&mut self, n: u64) -> u64 {
        self.next() % n
    }
}

fn varint(out: &mut Vec<u8>, mut n: u64) {
    while n >= 128 {
        out.push((n as u8) | 128);
        n >>= 7;
    }
    out.push(n as u8);
}

fn tag(out: &mut Vec<u8>, number: u32, wire: u32) {
    varint(out, ((number as u64) << 3) | wire as u64);
}

fn field(out: &mut Vec<u8>, number: u32, value: u64) {
    tag(out, number, 0);
    varint(out, value);
}

fn bytes(out: &mut Vec<u8>, number: u32, payload: &[u8]) {
    tag(out, number, 2);
    varint(out, payload.len() as u64);
    out.extend_from_slice(payload);
}

// ---- Tests ----

/// Every Task A1 golden slab, byte for byte (header fingerprint included), through the normal
/// and the small entry points, and the TDL1 section equals Java's for TypedAcceptance's g.Node.
#[test]
fn golden_slabs_match() {
    for (name, schema, wire, expected) in goldens() {
        let (wire, expected) = (&wire[..], &expected[..]);
        let c = compiled(&schema);
        assert_eq!(c.fingerprint, u32_at(expected, 24), "{name}: layout fingerprint");
        let actual = slab(&c, wire).unwrap_or_else(|code| panic!("{name}: status {code}"));
        assert_eq!(actual, expected, "{name}");
        let mut out = vec![0u8; expected.len()];
        let mut at = 0;
        let mut inline = Inline::new();
        let outcome = decode_with(
            &c,
            wire,
            FLAG_OWNING,
            out.as_mut_ptr(),
            out.len(),
            DEPTH,
            MAX_SLAB,
            false,
            &mut at,
            &mut inline,
        )
        .unwrap();
        assert_eq!(outcome.bytes as usize, expected.len());
        assert_eq!(out, expected, "{name} (small)");
    }
    // Type message 1 (Task A2 gate): at most 4 × its input.
    let (_, _, wire, bin) = goldens().into_iter().nth(1).unwrap();
    assert!(bin.len() <= 4 * wire.len(), "{} > 4 × {}", bin.len(), wire.len());
    // TypedAcceptance.schemaProgramGolden's g.Node: identical TDL1 bytes.
    let node = vec![(
        0,
        vec![
            f(1, INT64, EXPLICIT),
            f(2, STRING, 0),
            e(3, 0, &[0, 1]),
            o(f(4, INT32, EXPLICIT), 0),
            o(f(5, STRING, EXPLICIT), 0),
            m(6, MESSAGE, EXPLICIT, 0),
            f(7, STRING, REPEATED),
        ],
    )];
    let hex: String = layout_section(&node).iter().map(|b| format!("{b:02x}")).collect();
    assert_eq!(
        hex,
        "54444c3101000000010000003e701ecb300000000100000001000000070000001000000002000000ffffffff\
         1800000006000000ffffffff1c00000001000000ffffffff200000000100000000000000240000000600\
         0000000000002800000008000000ffffffff2c0000000e000000ffffffff"
    );
}

#[test]
fn compile_rejects_inconsistent_programs() {
    let p = program(&choice());
    assert!(compile(&p).is_ok());
    // Every truncation fails (the trailer moves or the tables run out).
    for end in 0..p.len() {
        assert_eq!(compile(&p[..end]).err(), Some(MALFORMED), "{end}");
    }
    // Any changed TDL1 byte fails: a table word no longer matches the recomputed layout, or the
    // fingerprint no longer matches the tables.
    let section = u32_at(&p, p.len() - 8) as usize;
    for at in section..p.len() {
        let mut corrupt = p.clone();
        corrupt[at] ^= 0x10;
        assert!(compile(&corrupt).is_err(), "{at}");
    }
    // A self-consistent section (valid fingerprint) for other field kinds fails the recomputation
    // from the TDB3 tables. (Oneof membership exists only in the section: a section that differs
    // only there is a different, consistent layout of the same tables.)
    let mut types = choice();
    types[0].1[6].kind = INT64;
    let other = program(&types);
    let mut spliced = p[..section].to_vec();
    let other_section = u32_at(&other, other.len() - 8) as usize;
    spliced.extend_from_slice(&other[other_section..other.len() - 8]);
    spliced.extend_from_slice(&(section as u32).to_le_bytes());
    spliced.extend_from_slice(&TDL1.to_le_bytes());
    assert_eq!(compile(&spliced).err(), Some(MALFORMED));
    // No section at all (an ABI 9 program).
    assert_eq!(compile(&p[..section]).err(), Some(MALFORMED));
    // Duplicate numbers, a map field whose child is no map entry, a oneof on a repeated field.
    let duplicate = vec![(512, vec![f(1, INT32, 0), f(1, INT64, 0)])];
    assert_eq!(compile(&program(&duplicate)).err(), Some(MALFORMED));
    let sparse = vec![(512, vec![f(5000, INT32, 0), f(5000, INT64, 0)])];
    assert_eq!(compile(&program(&sparse)).err(), Some(MALFORMED));
    let not_entry = vec![(512, vec![m(1, MESSAGE, REPEATED | MAP, 0)])];
    assert_eq!(compile(&program(&not_entry)).err(), Some(MALFORMED));
    let repeated_member = vec![(512, vec![o(f(1, INT32, REPEATED), 0)])];
    assert_eq!(compile(&program(&repeated_member)).err(), Some(MALFORMED));
    // Wide and sparse schemas compile; lookups find every number.
    let c = compiled(&wide());
    for n in 1..=130 {
        assert_eq!(c.types[0].find(n), Some(n as usize - 1));
    }
    assert_eq!(c.types[0].find(131), None);
    let c = compiled(&[(512, vec![f(70000, INT32, 0), f(3, INT64, 0)])]);
    assert_eq!(c.types[0].find(70000), Some(0));
    assert_eq!(c.types[0].find(3), Some(1));
    assert_eq!(c.types[0].find(4), None);
}

// ---- Differential against the ABI 9 view decoder ----

/// The ABI 9 `nested` test schema (typed_view_tests.rs) with a TDL1 section. Field flags 0:
/// implicit presence, which the slab keeps as ProtoRow's non-default rule.
fn nested() -> Vec<T> {
    vec![
        (
            512,
            vec![
                f(1, INT32, 0),
                f(2, INT64, REPEATED),
                m(3, MESSAGE, 0, 1),
                m(4, MESSAGE, REPEATED, 1),
                f(5, STRING, 0),
                e(6, 0, &[7, 0, 2]),
                m(7, GROUP, 0, 1),
                e(9, REPEATED, &[3, 1, 2, 0]),
                f(5000, BYTES, REPEATED),
            ],
        ),
        (
            512,
            vec![f(1, INT32, 0), f(2, STRING, REPEATED), m(3, MESSAGE, 0, 1)],
        ),
    ]
}

fn view_child(rng: &mut Rng, depth: u32) -> Vec<u8> {
    let mut out = Vec::new();
    for _ in 0..rng.below(4) {
        match rng.below(4) {
            0 => field(&mut out, 1, rng.below(300)),
            1 => bytes(
                &mut out,
                2,
                ["", "a", "λ", "😀x"][rng.below(4) as usize].as_bytes(),
            ),
            2 if depth < 9 => {
                let inner = view_child(rng, depth + 1);
                bytes(&mut out, 3, &inner);
            }
            _ => field(&mut out, 40 + rng.below(3) as u32, rng.next() >> rng.below(64)),
        }
    }
    out
}

/// Mostly valid nested wire with repeated singular messages (merges), wrong wire types, unknown
/// fields, deep nesting and rare corruption.
fn view_wire(rng: &mut Rng) -> Vec<u8> {
    let mut out = Vec::new();
    for _ in 0..rng.below(12) {
        match rng.below(13) {
            0 => field(&mut out, 1, rng.next() >> rng.below(64)),
            1 => field(&mut out, 2, rng.below(1000)),
            2 => {
                let mut packed = Vec::new();
                for _ in 0..rng.below(5) {
                    varint(&mut packed, rng.below(100_000));
                }
                bytes(&mut out, 2, &packed);
            }
            3 | 4 => {
                let inner = view_child(rng, 1);
                bytes(&mut out, 3 + rng.below(2) as u32, &inner);
            }
            5 => {
                let text: &[u8] = match rng.below(20) {
                    0 => &[0xff, 0x41],
                    1..=9 => "λ€".as_bytes(),
                    _ => b"name",
                };
                bytes(&mut out, 5, text);
            }
            6 => field(&mut out, 6, rng.below(9)),
            7 => {
                tag(&mut out, 7, 3);
                out.extend(view_child(rng, 1));
                tag(&mut out, if rng.below(30) == 0 { 8 } else { 7 }, 4);
            }
            8 => {
                let mut packed = Vec::new();
                for _ in 0..rng.below(6) {
                    varint(&mut packed, rng.below(6));
                }
                bytes(&mut out, 9, &packed);
            }
            9 => bytes(&mut out, 5000, &[1, 2]),
            10 => {
                if rng.below(2) == 0 {
                    tag(&mut out, 1 + rng.below(10) as u32, 1);
                    out.extend([0u8; 8]);
                } else {
                    tag(&mut out, 1 + rng.below(10) as u32, 5);
                    out.extend([0u8; 4]);
                }
            }
            11 => {
                // An unknown group with a nested unknown group.
                tag(&mut out, 60, 3);
                field(&mut out, 1, 5);
                tag(&mut out, 61, 3);
                tag(&mut out, 61, 4);
                tag(&mut out, 60, 4);
            }
            _ => {
                if rng.below(8) == 0 {
                    out.push(rng.next() as u8);
                }
            }
        }
    }
    if rng.below(25) == 0 && !out.is_empty() {
        out.truncate(rng.below(out.len() as u64) as usize);
    }
    out
}

/// One view message (entries, directory words) against one slab record.
#[allow(clippy::too_many_arguments)]
fn same_record(
    schema: &[T],
    c: &Compiled,
    input: &[u8],
    entries: &[Field],
    words: &[u32],
    ty: usize,
    dir: usize,
    range: (usize, usize),
    s: &[u8],
    record: usize,
) {
    let fields = &schema[ty].1;
    for (ordinal, x) in fields.iter().enumerate() {
        let word = words[dir + ordinal] as usize;
        let at = record + slot(c, ty, ordinal);
        let repeated = x.flags & REPEATED != 0;
        if !repeated {
            if word == 0 {
                assert!(!present(s, record, ordinal), "{ty}.{ordinal} absent");
                continue;
            }
            let entry = entries[word - 1];
            match x.kind {
                MESSAGE | GROUP => {
                    assert!(present(s, record, ordinal));
                    // Merged occurrences are covered by merge_matches_java_concatenation.
                    let mut occurrences = 0;
                    let mut i = range.0;
                    while i < range.1 {
                        occurrences += usize::from(entries[i].number as usize == ordinal + 1);
                        i = entries[i].end as usize;
                    }
                    if occurrences == 1 {
                        same_record(
                            schema,
                            c,
                            input,
                            entries,
                            words,
                            x.child as usize,
                            entry.value as usize,
                            (word, entry.end as usize),
                            s,
                            u32_at(s, at) as usize,
                        );
                    }
                }
                STRING | BYTES => {
                    let expected =
                        &input[entry.value as usize..entry.value as usize + entry.length as usize];
                    assert_eq!(present(s, record, ordinal), !expected.is_empty());
                    let (payload, ascii, units) = string_at(s, u32_at(s, at) as usize);
                    assert_eq!(payload, expected);
                    if x.kind == STRING {
                        let text = std::str::from_utf8(expected).unwrap();
                        assert_eq!(ascii, text.is_ascii());
                        assert_eq!(units as usize, text.encode_utf16().count());
                    }
                }
                _ => {
                    let value = entry.value as u32;
                    assert_eq!(u32_at(s, at), value, "{ty}.{ordinal}");
                    assert_eq!(present(s, record, ordinal), value != 0);
                }
            }
            continue;
        }
        if word == 0 {
            assert!(!present(s, record, ordinal));
            continue;
        }
        assert!(present(s, record, ordinal));
        let n = words[word] as usize;
        let list = u32_at(s, at) as usize;
        let width = match x.kind {
            INT64 => 8,
            _ => 4,
        };
        let items = list_at(s, list, width);
        assert_eq!(items.len(), n, "{ty}.{ordinal} count");
        for (k, item) in items.iter().enumerate() {
            let index = words[word + 1 + k] as usize;
            let entry = entries[index];
            match x.kind {
                MESSAGE => same_record(
                    schema,
                    c,
                    input,
                    entries,
                    words,
                    x.child as usize,
                    entry.value as usize,
                    (index + 1, entry.end as usize),
                    s,
                    *item as usize,
                ),
                STRING | BYTES => {
                    let (payload, _, _) = string_at(s, *item as usize);
                    assert_eq!(
                        payload,
                        &input[entry.value as usize..entry.value as usize + entry.length as usize]
                    );
                }
                INT64 => assert_eq!(*item, entry.value),
                _ => assert_eq!(*item, entry.value as u32 as u64),
            }
        }
    }
}

#[test]
fn entries_match_view_decoder() {
    use crate::typed::view;
    let schema = nested();
    let p = program(&schema);
    let v = view::compile(&p).unwrap();
    let c = compile(&p).unwrap();
    let mut rng = Rng(0xA2A2_5EED_0BAD_F00D);
    let (mut accepted, mut merged) = (0, 0);
    for _ in 0..8000 {
        let input = view_wire(&mut rng);
        let mut status = -1;
        let (result, _) = view::view(&v, &input, 1 << 40, 1 << 40, 8, 1 << 26, Vec::new(), &mut status);
        let ours = slab_depth(&c, &input, 7);
        match (result, ours) {
            (Ok(result), Ok(s)) => {
                accepted += 1;
                let certified = u32_at(&s, 4) & SLAB_CERTIFIED != 0;
                assert_eq!(certified, status == 5, "certification of {input:?}");
                merged += usize::from(!certified);
                if result.count == 0 {
                    // No field occurred: every presence word is zero.
                    assert!((0..schema[0].1.len()).all(|i| !present(&s, 32, i)));
                    continue;
                }
                let (entries, words) = unsafe {
                    let e = std::slice::from_raw_parts(result.data, result.count);
                    let w = std::slice::from_raw_parts(
                        result.data.add(result.count).cast::<u32>(),
                        result.words,
                    );
                    (e, w)
                };
                same_record(&schema, &c, &input, entries, words, 0, 0, (0, entries.len()), &s, 32);
                unsafe { view::release(result.data, result.capacity) };
            }
            (Err(x), Err(y)) => assert_eq!(x, y, "status of {input:?}"),
            (x, y) => panic!(
                "view {:?} vs slab {:?} for {input:?}",
                x.map(|r| {
                    unsafe { view::release(r.data, r.capacity) };
                    0
                }),
                y.map(|s| s.len())
            ),
        }
    }
    assert!(accepted > 4000 && merged > 100, "{accepted} accepted, {merged} merged");
}

// ---- Capacity and budgets ----

fn corpus() -> Vec<(Vec<T>, Vec<u8>)> {
    let mut all: Vec<(Vec<T>, Vec<u8>)> = goldens()
        .into_iter()
        .map(|(_, schema, wire, _)| (schema, wire))
        .collect();
    let mut rng = Rng(0x5157_0000_0000_0001);
    for _ in 0..300 {
        all.push((nested(), view_wire(&mut rng)));
    }
    all
}

#[test]
fn needed_bytes_exact() {
    let mut checked = 0;
    for (schema, input) in corpus() {
        let c = compiled(&schema);
        let p = Box::into_raw(vec![c].into_boxed_slice()) as *mut u8;
        let call = |out: &mut [u8], capacity: u64, flags: u32, small: bool| {
            let mut r = seam::SlabResult {
                bytes: 0,
                records: 0,
                handles: 0,
                flags: 0,
                error: 0,
                reserved: 0,
            };
            let rp = (&mut r as *mut seam::SlabResult).cast();
            let f = if small {
                crate::td_buffa_decode_slab_small
            } else {
                crate::td_buffa_decode_slab
            };
            let status = unsafe {
                f(
                    p,
                    input.as_ptr(),
                    input.len() as u64,
                    flags,
                    std::ptr::null(),
                    if capacity == 0 {
                        std::ptr::null_mut()
                    } else {
                        out.as_mut_ptr()
                    },
                    capacity,
                    7,
                    MAX_SLAB,
                    rp,
                )
            };
            (status, r)
        };
        let mut none = [0u8; 0];
        let (status, sized) = call(&mut none, 0, FLAG_OWNING, false);
        if status != NEEDS_CAPACITY {
            assert!(status == MALFORMED || status == BUDGET, "{status}");
            unsafe { crate::td_buffa_release_layout(p) };
            continue;
        }
        checked += 1;
        let bytes = sized.bytes as usize;
        assert!(bytes >= 32 + 8 && bytes % 8 == 0);
        // Exactly `bytes`: success, nothing written past it; the same slab as with more room.
        let mut exact = vec![0xA5u8; bytes + 64];
        let (status, r) = call(&mut exact, bytes as u64, FLAG_OWNING, false);
        assert_eq!(status, 0);
        assert_eq!(r.bytes as usize, bytes);
        assert_eq!((r.records, r.handles, r.flags), (sized.records, sized.handles, sized.flags));
        assert!(exact[bytes..].iter().all(|&b| b == 0xA5), "wrote past the slab");
        let mut roomy = vec![0x5Au8; bytes + 4096];
        assert_eq!(call(&mut roomy, (bytes + 4096) as u64, FLAG_OWNING, false).0, 0);
        assert_eq!(&roomy[..bytes], &exact[..bytes]);
        // Any smaller capacity: NEEDS_CAPACITY with the same count, nothing past the capacity.
        for capacity in [bytes - 1, bytes - 8, bytes / 2, 33, 32, 1] {
            let mut short = vec![0xA5u8; bytes];
            let (status, r) = call(&mut short, capacity as u64, FLAG_OWNING, false);
            assert_eq!((status, r.bytes as usize), (NEEDS_CAPACITY, bytes), "{capacity}");
            assert!(short[capacity..].iter().all(|&b| b == 0xA5));
            let (status, r) = call(&mut short, capacity as u64, FLAG_OWNING, true);
            assert!(status == BUDGET || (status, r.bytes as usize) == (NEEDS_CAPACITY, bytes));
        }
        // Sizing only writes nothing even with room.
        let mut untouched = vec![0xA5u8; bytes];
        let (status, r) = call(&mut untouched, bytes as u64, FLAG_OWNING | FLAG_SIZING_ONLY, false);
        assert_eq!((status, r.bytes as usize), (NEEDS_CAPACITY, bytes));
        assert!(untouched.iter().all(|&b| b == 0xA5));
        // The small entry point agrees.
        let mut small = vec![0u8; bytes];
        let (status, _) = call(&mut small, bytes as u64, FLAG_OWNING, true);
        if status == 0 {
            assert_eq!(small, &exact[..bytes]);
        } else {
            assert_eq!(status, BUDGET);
        }
        unsafe { crate::td_buffa_release_layout(p) };
    }
    assert!(checked > 150, "{checked}");
}

#[test]
fn budget_rejects_before_allocation() {
    let c = compiled(&google_type());
    let (_, _, wire, bin) = goldens().into_iter().nth(1).unwrap();
    let wire = &wire[..];
    let mut at = 0;
    let run = |max: u64, capacity: usize| {
        let mut out = vec![0u8; capacity];
        let mut at = 0;
        allocation_probe::allocations(|| {
            decode(&c, wire, FLAG_OWNING, out.as_mut_ptr(), capacity, DEPTH, max, true, &mut at)
        })
    };
    // A budget one byte short fails with BUDGET (never NEEDS_CAPACITY), allocating nothing.
    for capacity in [0, bin.len() - 1, bin.len()] {
        let (result, allocations) = run(bin.len() as u64 - 1, capacity);
        assert_eq!(result.err(), Some(BUDGET));
        assert_eq!(allocations, 0);
    }
    let (result, allocations) = run(bin.len() as u64, bin.len());
    assert_eq!(result.unwrap().bytes as usize, bin.len());
    assert_eq!(allocations, 0);
    // The header alone exceeds tiny budgets; depth and budget arguments are checked up front.
    assert_eq!(run(31, 0).0.err(), Some(BUDGET));
    for (depth, max) in [(MAX_DEPTH + 1, MAX_SLAB), (DEPTH, i32::MAX as u64 + 1)] {
        assert_eq!(
            decode(&c, wire, FLAG_OWNING, std::ptr::null_mut(), 0, depth, max, true, &mut at).err(),
            Some(BUDGET)
        );
    }
    // Budget failures on a large input also allocate nothing in the slab path's sizing (the
    // pending list spills: that is scratch, released before returning).
    let mut big = Vec::new();
    let mut packed = Vec::new();
    for i in 0..5000 {
        varint(&mut packed, i);
    }
    bytes(&mut big, 1, &packed);
    let c = compiled(&lists());
    let needed = slab(&c, &big).unwrap().len();
    let mut out = vec![0u8; needed];
    assert_eq!(
        decode(&c, &big, FLAG_OWNING, out.as_mut_ptr(), needed, DEPTH, needed as u64 - 8, true, &mut at)
            .err(),
        Some(BUDGET)
    );
}

// ---- Strings ----

/// 300 malformed strings (every ill-formed class, at offsets around the word and SIMD
/// boundaries), placed after valid strings in root, nested and repeated fields: the input is
/// rejected at the first invalid string in wire order, as protobuf-java rejects it, and the
/// error offset is that string's first payload byte. The same bytes in `bytes` fields pass.
#[test]
fn invalid_utf8_rejected_same_byte_as_java() {
    let bad: [&[u8]; 12] = [
        &[0x80],
        &[0xBF],
        &[0xC0, 0x80],
        &[0xC1, 0xBF],
        &[0xE0, 0x80, 0x80],
        &[0xE0, 0x9F, 0xBF],
        &[0xED, 0xA0, 0x80],
        &[0xF0, 0x80, 0x80, 0x80],
        &[0xF4, 0x90, 0x80, 0x80],
        &[0xF5, 0x80, 0x80, 0x80],
        &[0xE2, 0x82],
        &[0xFF],
    ];
    let schema = merge_schema();
    let c = compiled(&schema);
    let lists_c = compiled(&texts());
    let mut cases = 0;
    let mut rng = Rng(0x0BAD_C0DE_0BAD_C0DE);
    for (k, &sequence) in bad.iter().enumerate() {
        for &prefix in &[0usize, 1, 7, 8, 9, 62, 63, 64, 65, 100] {
            for placement in 0..2 + (k + prefix) % 2 {
                let mut text = "é".repeat(rng.below(3) as usize).into_bytes();
                text.extend(std::iter::repeat_n(b'a', prefix));
                text.extend_from_slice(sequence);
                text.extend_from_slice(b"tail");
                assert!(std::str::from_utf8(&text).is_err());
                cases += 1;
                let (input, at, ours) = match placement {
                    0 => {
                        // Root Texts: a valid string first, the bad one in a repeated field.
                        let mut w = Vec::new();
                        bytes(&mut w, 1, b"ok");
                        bytes(&mut w, 6, "fine-λ".as_bytes());
                        tag(&mut w, 6, 2);
                        varint(&mut w, text.len() as u64);
                        let at = w.len();
                        w.extend_from_slice(&text);
                        bytes(&mut w, 2, &[0xFF]); // later, also invalid: never reached
                        (w, at, &lists_c)
                    }
                    1 => {
                        // Nested: Outer.inner.s after a valid inner field.
                        let mut inner = Vec::new();
                        field(&mut inner, 1, 9);
                        tag(&mut inner, 3, 2);
                        varint(&mut inner, text.len() as u64);
                        let inner_at = inner.len();
                        inner.extend_from_slice(&text);
                        let mut w = Vec::new();
                        tag(&mut w, 1, 2);
                        varint(&mut w, inner.len() as u64);
                        let at = w.len() + inner_at;
                        w.extend(inner);
                        (w, at, &c)
                    }
                    _ => {
                        // A later (merged) occurrence is validated when it is read.
                        let mut good = Vec::new();
                        bytes(&mut good, 3, b"good");
                        let mut inner = Vec::new();
                        tag(&mut inner, 3, 2);
                        varint(&mut inner, text.len() as u64);
                        let inner_at = inner.len();
                        inner.extend_from_slice(&text);
                        let mut w = Vec::new();
                        bytes(&mut w, 1, &good);
                        tag(&mut w, 1, 2);
                        varint(&mut w, inner.len() as u64);
                        let at = w.len() + inner_at;
                        w.extend(inner);
                        (w, at, &c)
                    }
                };
                assert_eq!(rejection(ours, &input), (MALFORMED, at), "{text:x?} {placement}");
                // As bytes, the same payload is data.
                let mut as_bytes = Vec::new();
                bytes(&mut as_bytes, 7, &text);
                bytes(&mut as_bytes, 8, &text);
                let s = slab(&lists_c, &as_bytes).unwrap();
                let (payload, ascii, units) = string_at(&s, u32_at(&s, 32 + slot(&lists_c, 0, 6)) as usize);
                assert_eq!((payload, ascii, units), (&text[..], false, 0));
            }
        }
    }
    assert!(cases >= 300, "{cases}");
}

#[test]
fn ascii_bit_and_utf16_units_exact() {
    let c = compiled(&texts());
    for length in [0, 1, 7, 8, 9, 31, 32, 33, 63, 64, 65, 127, 128, 200] {
        for pattern in ["a", "é", "数", "😀", "aé数😀", "\u{7f}"] {
          for text in [
            pattern.repeat(length),
            // Exactly `length` bytes: an ASCII prefix, then the pattern at the end.
            "a".repeat(length.saturating_sub(pattern.len())) + if length >= pattern.len() { pattern } else { "" },
          ] {
            let mut w = Vec::new();
            bytes(&mut w, 1, text.as_bytes());
            bytes(&mut w, 6, text.as_bytes());
            let s = slab(&c, &w).unwrap();
            let header = u32_at(&s, 32 + slot(&c, 0, 0)) as usize;
            let (payload, ascii, units) = string_at(&s, header);
            assert_eq!(payload, text.as_bytes());
            assert_eq!(ascii, text.is_ascii(), "{text:?}");
            assert_eq!(units as usize, text.encode_utf16().count(), "{text:?}");
            // ASCII headers are 8 bytes (no utf16 word); others 12.
            let next = if ascii { header + 8 } else { header + 12 } + text.len();
            assert!(next <= s.len());
            assert_eq!(present(&s, 32, 0), !text.is_empty());
            let list = list_at(&s, u32_at(&s, 32 + slot(&c, 0, 5)) as usize, 4);
            assert_eq!(string_at(&s, list[0] as usize), (text.as_bytes(), ascii, units));
          }
        }
    }
}

// ---- Lists ----

#[test]
fn packed_lists_sized_exactly() {
    let c = compiled(&lists());
    let mut rng = Rng(0xFACE_FEED_1234_5678);
    for n in [0usize, 1, 2, 7, 100, 513, 3000] {
        let values: Vec<u64> = (0..n).map(|_| rng.next() >> rng.below(64)).collect();
        let mut packed = Vec::new();
        for &v in &values {
            varint(&mut packed, v);
        }
        let mut w = Vec::new();
        bytes(&mut w, 2, &packed); // int64, packed run
        field(&mut w, 2, 77); // and one unpacked occurrence
        let mut fixed = Vec::new();
        for &v in &values {
            fixed.extend_from_slice(&(v as u32).to_le_bytes());
        }
        bytes(&mut w, 5, &fixed);
        let mut flags = Vec::new();
        for &v in &values {
            varint(&mut flags, v & 3);
        }
        bytes(&mut w, 4, &flags);
        let s = slab(&c, &w).unwrap();
        let mut expected = values.clone();
        expected.push(77);
        let i64s = u32_at(&s, 32 + slot(&c, 0, 1)) as usize;
        assert_eq!(list_at(&s, i64s, 8), expected);
        let fx = u32_at(&s, 32 + slot(&c, 0, 4)) as usize;
        if n == 0 {
            assert!(!present(&s, 32, 4) && fx == 0);
            continue;
        }
        assert_eq!(
            list_at(&s, fx, 4),
            values.iter().map(|&v| v as u32 as u64).collect::<Vec<_>>()
        );
        let bools = u32_at(&s, 32 + slot(&c, 0, 3)) as usize;
        assert_eq!(
            list_at(&s, bools, 1),
            values.iter().map(|&v| (v & 3 != 0) as u64).collect::<Vec<_>>()
        );
        // Lists are allocated at close in ordinal order, not wire order: i64 (ordinal 1), flags
        // (3), fx (4), each exactly 8 + count × width, padded to 8.
        assert_eq!(bools, i64s + 8 + 8 * (n + 1));
        assert_eq!(fx, (bools + 8 + n + 7) & !7);
        assert_eq!(s.len(), fx + 8 + 4 * n + if (fx + 8 + 4 * n) % 8 != 0 { 4 } else { 0 });
    }
    // Truncated last varint; a fixed32 run whose length is not a multiple of 4.
    for w in [vec![10u8, 2, 1, 0x80], vec![42u8, 3, 1, 2, 3], vec![10u8, 5, 1, 2]] {
        assert_eq!(rejection(&c, &w).0, MALFORMED, "{w:?}");
    }
}

// ---- Semantics ----

/// Outer.inner seen several times decodes the concatenation of its payloads, exactly as decoding
/// the concatenation as an Inner root (protobuf merge semantics); CERTIFIED is cleared.
#[test]
fn merge_matches_java_concatenation() {
    let schema = merge_schema();
    let c = compiled(&schema);
    let inner_root = compiled(&[schema[1].clone()]);
    let mut rng = Rng(0x3E46_E000_0000_0003);
    for round in 0..2000 {
        let mut w = Vec::new();
        let mut joined = Vec::new();
        let occurrences = 1 + rng.below(4);
        for _ in 0..occurrences {
            let mut inner = Vec::new();
            for _ in 0..rng.below(5) {
                match rng.below(4) {
                    0 => field(&mut inner, 1, rng.below(5)),
                    1 => field(&mut inner, 2, rng.below(1000)),
                    2 => bytes(&mut inner, 3, ["", "x", "λλ", "plain"][rng.below(4) as usize].as_bytes()),
                    _ => field(&mut inner, 9, rng.below(10)),
                }
            }
            bytes(&mut w, 1, &inner);
            joined.extend(inner);
            if rng.below(4) == 0 {
                field(&mut w, 2, 1); // unknown between occurrences
            }
        }
        let s = slab(&c, &w).unwrap();
        let expected = slab(&inner_root, &joined).unwrap();
        let record = u32_at(&s, 32 + slot(&c, 0, 0)) as usize;
        assert!(present(&s, 32, 0));
        assert_eq!(u32_at(&s, 4) & SLAB_CERTIFIED != 0, occurrences == 1, "{round}");
        // Same presence, scalar, string and list contents as the direct decode.
        for ordinal in 0..3 {
            assert_eq!(present(&s, record, ordinal), present(&expected, 32, ordinal));
        }
        let a = slot(&c, 1, 0);
        assert_eq!(u32_at(&s, record + a), u32_at(&expected, 32 + a));
        let r = slot(&c, 1, 1);
        if present(&expected, 32, 1) {
            assert_eq!(
                list_at(&s, u32_at(&s, record + r) as usize, 4),
                list_at(&expected, u32_at(&expected, 32 + r) as usize, 4)
            );
        }
        let st = slot(&c, 1, 2);
        if u32_at(&expected, 32 + st) != 0 {
            assert_eq!(
                string_at(&s, u32_at(&s, record + st) as usize),
                string_at(&expected, u32_at(&expected, 32 + st) as usize)
            );
        }
        assert_eq!(unknown_span(&s, record), unknown_span(&expected, 32));
    }
}

/// The last member of each oneof wins; earlier members' bits and slots are cleared and the case
/// entry names the winner; a merged message member that is cleared and set again starts fresh.
#[test]
fn oneof_last_wins() {
    let c = compiled(&choice());
    let mut rng = Rng(0x0E0F_0000_1111_2222);
    for _ in 0..3000 {
        let mut w = Vec::new();
        let mut last = [None::<usize>; 2];
        let mut child_x = None::<u64>;
        for _ in 0..rng.below(8) {
            let member = rng.below(6) as usize;
            match member {
                0 => field(&mut w, 1, rng.below(3)),
                1 => bytes(&mut w, 2, b"s"),
                2 => {
                    let x = rng.below(3);
                    let mut inner = Vec::new();
                    field(&mut inner, 1, x);
                    bytes(&mut w, 3, &inner);
                    // A repeated occurrence merges (last x wins); after a clear it starts over.
                    child_x = Some(x);
                }
                3 => field(&mut w, 4, rng.below(3)),
                4 => field(&mut w, 5, rng.below(2)),
                _ => bytes(&mut w, 6, &[]),
            }
            last[member / 3] = Some(member);
        }
        let s = slab(&c, &w).unwrap();
        for (k, winner) in last.iter().enumerate() {
            let case = u16::from_le_bytes([s[32 + 8 + 2 * k], s[32 + 9 + 2 * k]]);
            assert_eq!(case as usize, winner.map_or(0, |m| m + 1));
            for member in 3 * k..3 * k + 3 {
                let set = Some(member) == *winner;
                assert_eq!(present(&s, 32, member), set);
                if !set {
                    let at = 32 + slot(&c, 0, member);
                    let width = if member == 3 { 8 } else { 4 };
                    assert!(s[at..at + width].iter().all(|&b| b == 0));
                }
            }
        }
        if last[0] == Some(2) {
            let child = u32_at(&s, 32 + slot(&c, 0, 2)) as usize;
            assert_eq!(u32_at(&s, child + 8) as u64, child_x.unwrap());
        }
    }
}

/// Map lists keep one entry per key at the key's first position with the last entry's record.
#[test]
fn map_last_key_wins() {
    let c = compiled(&maps());
    let mut rng = Rng(0x4A9_0000_DEAD_BEEF);
    for round in 0..600 {
        let mut w = Vec::new();
        let mut order: Vec<String> = Vec::new();
        let mut values = std::collections::HashMap::new();
        let mut children: Vec<i64> = Vec::new();
        let mut child_values = std::collections::HashMap::new();
        let keys = 1 + rng.below(if round % 10 == 0 { 300 } else { 6 });
        for _ in 0..rng.below(if round % 10 == 0 { 400 } else { 12 }) {
            let key = format!("k{}", rng.below(keys));
            let value = rng.below(100);
            let mut entry = Vec::new();
            if key != "k0" {
                bytes(&mut entry, 1, key.as_bytes());
            }
            field(&mut entry, 2, value);
            bytes(&mut w, 1, &entry);
            if !values.contains_key(&key) {
                order.push(key.clone());
            }
            values.insert(key, value);
            let child_key = rng.below(4) as i64 - 1;
            let mut entry = Vec::new();
            field(&mut entry, 1, child_key as u64);
            let x = rng.below(9);
            if x != 0 {
                let mut child = Vec::new();
                field(&mut child, 1, x);
                bytes(&mut entry, 2, &child);
            }
            bytes(&mut w, 2, &entry);
            if !child_values.contains_key(&child_key) {
                children.push(child_key);
            }
            child_values.insert(child_key, x);
        }
        let s = slab(&c, &w).unwrap();
        if order.is_empty() {
            assert!(!present(&s, 32, 0));
            continue;
        }
        let counts = list_at(&s, u32_at(&s, 32 + slot(&c, 0, 0)) as usize, 4);
        assert_eq!(counts.len(), order.len());
        for (entry, key) in counts.iter().zip(&order) {
            let entry = *entry as usize;
            let k = if present(&s, entry, 0) {
                std::str::from_utf8(string_at(&s, u32_at(&s, entry + slot(&c, 1, 0)) as usize).0)
                    .unwrap()
                    .to_string()
            } else {
                "k0".to_string()
            };
            // "k0" is written with its key absent: the default key "" stands for it.
            assert_eq!(&k, key);
            assert_eq!(u32_at(&s, entry + slot(&c, 1, 1)) as u64, values[key]);
        }
        let list = list_at(&s, u32_at(&s, 32 + slot(&c, 0, 1)) as usize, 4);
        assert_eq!(list.len(), children.len());
        for (entry, key) in list.iter().zip(&children) {
            let entry = *entry as usize;
            assert_eq!(u64_at(&s, entry + slot(&c, 2, 0)) as i64, *key);
            // An entry without a value has an empty value record (present, x absent).
            assert!(present(&s, entry, 1));
            let child = u32_at(&s, entry + slot(&c, 2, 1)) as usize;
            assert_eq!(u32_at(&s, child + 8) as u64, child_values[key]);
        }
    }
}

/// Unknown numbers, wire-type mismatches and out-of-set closed-enum values are kept in wire
/// order (packed out-of-set elements re-encoded); records without unknowns have no table entry.
#[test]
fn unknown_fields_preserved_in_order() {
    let c = compiled(&unknowns());
    let mut rng = Rng(0x0C0F_FEE0_0000_0007);
    for _ in 0..3000 {
        let mut w = Vec::new();
        let mut expected = Vec::new();
        for _ in 0..rng.below(10) {
            let start = w.len();
            match rng.below(7) {
                0 => {
                    field(&mut w, 1, rng.below(9)); // known
                    continue;
                }
                1 => {
                    let v = rng.below(4);
                    field(&mut w, 2, v);
                    if v > 1 {
                        expected.extend_from_slice(&w[start..]);
                    }
                }
                2 => {
                    let mut packed = Vec::new();
                    let mut dropped = Vec::new();
                    for _ in 0..rng.below(4) {
                        let v = rng.below(4) as i64 - 1;
                        varint(&mut packed, v as u64);
                        if !(0..=1).contains(&v) {
                            field(&mut dropped, 3, v as i32 as i64 as u64);
                        }
                    }
                    bytes(&mut w, 3, &packed);
                    expected.extend(dropped);
                }
                3 => {
                    field(&mut w, 100 + rng.below(50) as u32, rng.next() >> rng.below(64));
                    expected.extend_from_slice(&w[start..]);
                }
                4 => {
                    bytes(&mut w, 1, b"mismatch"); // known number, wrong wire type
                    expected.extend_from_slice(&w[start..]);
                }
                5 => {
                    tag(&mut w, 7, 3);
                    field(&mut w, 1, 1);
                    tag(&mut w, 7, 4);
                    expected.extend_from_slice(&w[start..]);
                }
                _ => {
                    tag(&mut w, 9, 5);
                    w.extend([1, 2, 3, 4]);
                    expected.extend_from_slice(&w[start..]);
                }
            }
        }
        let s = slab(&c, &w).unwrap();
        match unknown_span(&s, 32) {
            None => assert!(expected.is_empty()),
            Some(span) => assert_eq!(span, &expected[..]),
        }
    }
}

/// Nesting deeper than `max_depth` fails with BUDGET: messages, groups, unknown groups and the
/// empty value record of a map entry at the limit.
#[test]
fn depth_limit_enforced() {
    let c = compiled(&legacy());
    let nest = |levels: usize| {
        let mut w = Vec::new();
        field(&mut w, 1, 1);
        for _ in 0..levels {
            let mut outer = Vec::new();
            field(&mut outer, 1, 1);
            bytes(&mut outer, 4, &w);
            w = outer;
        }
        w
    };
    for max in [0u32, 1, 2, 5, 31] {
        for levels in 0..(max as usize + 3) {
            let result = slab_depth(&c, &nest(levels), max);
            assert_eq!(result.is_ok(), levels <= max as usize, "{levels} at {max}");
            if levels > max as usize {
                assert_eq!(result.err(), Some(BUDGET));
            }
        }
    }
    // A group is a nesting level too; so is an unknown group (skipped, never decoded).
    let mut grouped = Vec::new();
    tag(&mut grouped, 2, 3);
    field(&mut grouped, 3, 2);
    tag(&mut grouped, 2, 4);
    assert!(slab_depth(&c, &grouped, 1).is_ok());
    assert_eq!(slab_depth(&c, &grouped, 0).err(), Some(BUDGET));
    let mut unknown = Vec::new();
    tag(&mut unknown, 50, 3);
    tag(&mut unknown, 51, 3);
    tag(&mut unknown, 51, 4);
    tag(&mut unknown, 50, 4);
    assert!(slab_depth(&c, &unknown, 2).is_ok());
    assert_eq!(slab_depth(&c, &unknown, 1).err(), Some(BUDGET));
    assert_eq!(slab_depth(&c, &unknown, 0).err(), Some(BUDGET));
    // A map entry without its value needs one more level for the empty value record.
    let maps_c = compiled(&maps());
    let mut entry = Vec::new();
    field(&mut entry, 1, 1);
    let mut w = Vec::new();
    bytes(&mut w, 2, &entry);
    assert!(slab_depth(&maps_c, &w, 2).is_ok());
    assert_eq!(slab_depth(&maps_c, &w, 1).err(), Some(BUDGET));
    // Required fields: absent ones only clear CERTIFIED.
    let s = slab(&c, &nest(0)).unwrap();
    assert_ne!(u32_at(&s, 4) & SLAB_CERTIFIED, 0);
    let s = slab(&c, &[]).unwrap();
    assert_eq!(u32_at(&s, 4) & SLAB_CERTIFIED, 0);
    // Mismatched and unterminated groups, truncated fields and bad tags are MALFORMED.
    for w in [
        vec![0x13u8, 0x18, 0x01, 0x1C],  // group 2 closed by end tag 3
        vec![0x13, 0x18, 0x01],          // unterminated group
        vec![0x14],                      // end group at the root
        vec![0x08],                      // truncated varint
        vec![0x00, 0x01],                // field number 0
        vec![0x0E, 0x01],                // wire type 6
        vec![0x2A, 0x05, 0x01],          // truncated length-delimited
        vec![0x08, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x02], // 10th byte > 1
    ] {
        assert_eq!(rejection(&c, &w).0, MALFORMED, "{w:x?}");
    }
}

// ---- Allocation and failure injection ----

#[test]
fn small_entry_allocates_nothing() {
    let mut inputs = corpus();
    // A merge, a map with duplicate keys and an unknown field: every scratch kind in use.
    let mut w = Vec::new();
    bytes(&mut w, 1, &[8, 1]);
    bytes(&mut w, 1, &[8, 2]);
    inputs.push((merge_schema(), w));
    inputs.push((maps(), goldens()[2].2.clone()));
    let mut decoded = 0;
    for (schema, input) in inputs {
        let c = compiled(&schema);
        let handle = Box::into_raw(vec![c].into_boxed_slice()) as *mut u8;
        let mut out = vec![0u8; 1 << 16];
        let mut r = seam::SlabResult {
            bytes: 0,
            records: 0,
            handles: 0,
            flags: 0,
            error: 0,
            reserved: 0,
        };
        let rp = (&mut r as *mut seam::SlabResult).cast();
        for small in [true, false] {
            let f = if small {
                crate::td_buffa_decode_slab_small
            } else {
                crate::td_buffa_decode_slab
            };
            let (status, allocations) = allocation_probe::allocations(|| unsafe {
                f(
                    handle,
                    input.as_ptr(),
                    input.len() as u64,
                    FLAG_OWNING,
                    std::ptr::null(),
                    out.as_mut_ptr(),
                    out.len() as u64,
                    7,
                    MAX_SLAB,
                    rp,
                )
            });
            assert_eq!(allocations, 0, "status {status}");
            decoded += usize::from(status == 0);
        }
        unsafe { crate::td_buffa_release_layout(handle) };
    }
    assert!(decoded > 300, "{decoded}");
    // Inputs that overflow fixed scratch: the small entry point returns BUDGET, still allocating
    // nothing, and the normal entry point (which may spill) decodes them.
    let c = compiled(&lists());
    let mut packed = Vec::new();
    for i in 0..2000 {
        varint(&mut packed, i);
    }
    let mut w = Vec::new();
    bytes(&mut w, 1, &packed);
    let mut out = vec![0u8; 1 << 16];
    let mut at = 0;
    let mut inline = Inline::new();
    let (result, allocations) = allocation_probe::allocations(|| {
        decode_with(&c, &w, FLAG_OWNING, out.as_mut_ptr(), out.len(), DEPTH, MAX_SLAB, false, &mut at, &mut inline)
    });
    assert_eq!((result.err(), allocations), (Some(BUDGET), 0));
    assert!(slab(&c, &w).is_ok());
    // Oversized inputs or capacities are rejected before any work.
    let big = vec![0u8; SMALL_LIMIT as usize + 1];
    let handle = Box::into_raw(vec![c].into_boxed_slice()) as *mut u8;
    let mut r = seam::SlabResult {
        bytes: 0,
        records: 0,
        handles: 0,
        flags: 0,
        error: 0,
        reserved: 0,
    };
    let rp = (&mut r as *mut seam::SlabResult).cast();
    unsafe {
        let small = crate::td_buffa_decode_slab_small;
        let null = std::ptr::null();
        assert_eq!(small(handle, big.as_ptr(), big.len() as u64, 1, null, out.as_mut_ptr(), 64, 7, MAX_SLAB, rp), BUDGET);
        assert_eq!(small(handle, big.as_ptr(), 2, 1, null, out.as_mut_ptr(), SMALL_LIMIT + 1, 7, MAX_SLAB, rp), BUDGET);
        // Argument checks: null result, compiled or data; a projection; LAX; borrowing.
        let normal = crate::td_buffa_decode_slab;
        assert_eq!(normal(handle, big.as_ptr(), 2, 1, null, out.as_mut_ptr(), 64, 7, MAX_SLAB, std::ptr::null_mut()), MALFORMED);
        assert_eq!(normal(null, big.as_ptr(), 2, 1, null, out.as_mut_ptr(), 64, 7, MAX_SLAB, rp), MALFORMED);
        assert_eq!(normal(handle, null, 2, 1, null, out.as_mut_ptr(), 64, 7, MAX_SLAB, rp), MALFORMED);
        assert_eq!(normal(handle, big.as_ptr(), 2, 1, big.as_ptr(), out.as_mut_ptr(), 64, 7, MAX_SLAB, rp), MALFORMED);
        assert_eq!(normal(handle, big.as_ptr(), 2, 1 | FLAG_LAX, null, out.as_mut_ptr(), 64, 7, MAX_SLAB, rp), MALFORMED);
        assert_eq!(normal(handle, big.as_ptr(), 2, 0, null, out.as_mut_ptr(), 64, 7, MAX_SLAB, rp), MALFORMED);
        assert_eq!(normal(handle, big.as_ptr(), 2, 1, null, std::ptr::null_mut(), 64, 7, MAX_SLAB, rp), MALFORMED);
        // A handle that is not a compiled layout (wrong magic) is rejected.
        let fake = [0u64; 8];
        assert_eq!(normal(fake.as_ptr().cast(), big.as_ptr(), 2, 1, null, out.as_mut_ptr(), 64, 7, MAX_SLAB, rp), MALFORMED);
        crate::td_buffa_release_layout(handle);
        crate::td_buffa_release_layout(std::ptr::null_mut());
    }
    let _ = PANIC;
}

/// Inputs that spill every scratch kind to the heap (pending lists, hashed map dedup, unknown
/// bytes and the table, merge payloads, heap frames): with every allocation from the n-th on
/// failing, decoding returns ALLOCATION (or succeeds) and leaks nothing. Compilation likewise.
#[test]
fn failure_injection_leaks_nothing() {
    let mut rng = Rng(0xFA11_0000_0000_0009);
    let mut cases: Vec<(Vec<T>, Vec<u8>, usize)> = Vec::new();
    let mut w = Vec::new();
    let mut packed = Vec::new();
    for i in 0..3000 {
        varint(&mut packed, i);
    }
    bytes(&mut w, 2, &packed);
    for i in 0..2000 {
        field(&mut w, 900 + (i % 7), i as u64); // unknown bytes spill
    }
    cases.push((lists(), w, 1 << 20));
    let mut w = Vec::new();
    for i in 0..700 {
        let mut entry = Vec::new();
        bytes(&mut entry, 1, format!("key{}", rng.below(300) + i % 2).as_bytes());
        field(&mut entry, 2, i);
        bytes(&mut w, 1, &entry);
        field(&mut w, 99, 1);
    }
    cases.push((maps(), w, 1 << 20));
    // Wide recursive records that do not fit a small capacity spill frames to the heap; the
    // recursive field occurs twice at every level (a merge walk and a merged decode each).
    let mut fields: Vec<F> = (1..=200).map(|n| f(n, INT64, EXPLICIT)).collect();
    fields.push(m(201, MESSAGE, EXPLICIT, 0));
    let deep_wide = vec![(512, fields)];
    let mut deep = Vec::new();
    for level in 0..6 {
        let mut outer = Vec::new();
        field(&mut outer, 1 + level, 1);
        bytes(&mut outer, 201, &deep);
        bytes(&mut outer, 201, &deep);
        deep = outer;
    }
    cases.push((deep_wide, deep, 64));
    for (schema, input, capacity) in cases {
        let c = compiled(&schema);
        let mut out = vec![0u8; capacity];
        let mut at = 0;
        let full = decode(&c, &input, FLAG_OWNING, out.as_mut_ptr(), capacity, 100, MAX_SLAB, true, &mut at);
        assert!(full.is_ok(), "{full:?}");
        let mut outcomes = [0usize; 2];
        for n in 0..200 {
            let outcome = allocation_probe::failing(n, || {
                decode(&c, &input, FLAG_OWNING, out.as_mut_ptr(), capacity, 100, MAX_SLAB, true, &mut at)
            });
            match outcome {
                Ok(o) => {
                    assert_eq!(o, full.unwrap());
                    outcomes[0] += 1;
                }
                Err(code) => {
                    assert_eq!(code, ALLOCATION);
                    outcomes[1] += 1;
                }
            }
        }
        assert!(outcomes[0] > 0 && outcomes[1] > 0, "{outcomes:?}");
    }
    let p = program(&google_type());
    let mut compiled_ok = 0;
    for n in 0..200 {
        let outcome = allocation_probe::failing(n, || compile(&p).err());
        assert!(outcome.is_none() || outcome == Some(ALLOCATION));
        compiled_ok += usize::from(outcome.is_none());
    }
    assert!(compiled_ok > 0 && compiled_ok < 200);
    // Through the FFI as well: a failed compile leaves no handle behind.
    for n in 0..40 {
        allocation_probe::failing(n, || {
            let mut r = seam::Result {
                data: std::ptr::null_mut(),
                count: 0,
                capacity: 0,
            };
            let rp = (&mut r as *mut seam::Result).cast();
            let status = unsafe { crate::td_buffa_compile_layout(p.as_ptr(), p.len() as u64, rp) };
            assert!(status == 0 || status == ALLOCATION);
            assert_eq!(status == 0, !r.data.is_null());
            if status == 0 {
                assert_eq!(r.count, 5);
            }
            unsafe { crate::td_buffa_release_layout(r.data) };
        });
    }
}

// ---- Parse-time projection and explicit input borrowing ----
fn projection_program(c: &Compiled, wanted: &[&[usize]], flags: u32, borrow: u32) -> Vec<u8> {
    assert_eq!(wanted.len(), c.types.len());
    let mut words = vec![Projection::MAGIC, 0, c.fingerprint, flags, wanted.len() as u32, borrow];
    for (ty, ordinals) in c.types.iter().zip(wanted) {
        words.push(ty.fields.len() as u32);
        let start = words.len();
        words.resize(start + ty.fields.len().div_ceil(32), 0);
        for &ordinal in ordinals.iter() { words[start + ordinal / 32] |= 1 << (ordinal % 32); }
    }
    words[1] = (words.len() * 4) as u32;
    words.into_iter().flat_map(u32::to_le_bytes).collect()
}

fn projected_slab(c: &Compiled, wire: &[u8], program: &[u8], flags: u32) -> std::result::Result<Vec<u8>, i32> {
    let projection = Projection::parse(c, program)?;
    let mut at = 0;
    let sized = decode_projected(c, wire, flags | FLAG_SIZING_ONLY, Some(projection), std::ptr::null_mut(), 0, DEPTH, MAX_SLAB, true, &mut at)?;
    let mut bytes = vec![0xA5; sized.bytes as usize];
    let result = decode_projected(c, wire, flags, Some(projection), bytes.as_mut_ptr(), bytes.len(), DEPTH, MAX_SLAB, true, &mut at)?;
    assert_eq!(sized, result);
    Ok(bytes)
}

fn projected_schema() -> Compiled {
    compiled(&[
        (0, vec![f(1, INT64, 0), f(2, STRING, 0), m(3, MESSAGE, EXPLICIT, 1), m(4, MESSAGE, REPEATED, 1), f(5, BYTES, 0)]),
        (0, vec![f(1, STRING, 0), f(2, INT64, 0)]),
    ])
}
fn projected_word(bytes: &[u8], offset: usize) -> u32 { u32::from_le_bytes(bytes[offset..offset + 4].try_into().unwrap()) }
fn projected_slot(c: &Compiled, bytes: &[u8], ordinal: usize) -> u32 { projected_word(bytes, HEADER_BYTES + c.types[0].fields[ordinal].slot as usize) }

#[test]
fn projection_omits_payloads_and_counts_without_records() {
    let c = projected_schema();
    let mask = projection_program(&c, &[&[0], &[]], 0, 0);
    let wire = [8,7,18,4,b't',b'e',b'x',b't',26,3,10,1,b'a',34,3,10,1,b'b',34,3,10,1,b'c'];
    let full = slab(&c, &wire).unwrap();
    let projected = projected_slab(&c, &wire, &mask, FLAG_OWNING).unwrap();
    assert!(projected.len() < full.len());
    assert_eq!(projected_word(&projected, 12), 1);
    assert_eq!(projected_slot(&c, &projected, 0), 7);
    assert_eq!(projected_slot(&c, &projected, 1), 0);
    assert_eq!(projected_slot(&c, &projected, 2), 0);
    let list = projected_slot(&c, &projected, 3) as usize;
    assert_eq!((projected_word(&projected, list), projected_word(&projected, list + 4)), (2, COUNTS_ONLY));
    assert_eq!(projected_word(&projected, 4), SLAB_OWNING | SLAB_PROJECTED | SLAB_CERTIFIED);
}

#[test]
fn projection_lax_defers_only_omitted_utf8() {
    let c = projected_schema();
    let strict = projection_program(&c, &[&[0], &[]], 0, 0);
    let lax = projection_program(&c, &[&[0], &[]], Projection::LAX, 0);
    let malformed_text = [26,3,10,1,0xff];
    assert_eq!(projected_slab(&c, &malformed_text, &strict, FLAG_OWNING), Err(MALFORMED));
    let accepted = projected_slab(&c, &malformed_text, &lax, FLAG_OWNING | FLAG_LAX).unwrap();
    assert_ne!(projected_word(&accepted, 4) & SLAB_LAX, 0);
    for wire in [&[26,3,10,3,0xff][..], &[26,2,16,0x80][..], &[26,1,0][..]] {
        assert_eq!(projected_slab(&c, wire, &lax, FLAG_OWNING | FLAG_LAX), Err(MALFORMED));
    }
    let wanted = projection_program(&c, &[&[2], &[0]], Projection::LAX, 0);
    assert_eq!(projected_slab(&c, &malformed_text, &wanted, FLAG_OWNING | FLAG_LAX), Err(MALFORMED));
}

#[test]
fn projection_lazy_headers_borrow_exact_child_spans() {
    let c = projected_schema();
    let mask = projection_program(&c, &[&[0], &[]], Projection::LAZY, 0);
    let wire = [26,3,10,1,b'a',34,3,10,1,b'b',34,3,10,1,b'c'];
    assert_eq!(projected_slab(&c, &wire, &mask, FLAG_OWNING), Err(MALFORMED));
    let result = projected_slab(&c, &wire, &mask, FLAG_BORROWING).unwrap();
    assert_eq!(projected_word(&result, 12), 1);
    assert_eq!(projected_word(&result, 16), 4);
    let child = projected_slot(&c, &result, 2);
    assert_ne!(child & LAZY_CHILD, 0);
    let child = (child & !LAZY_CHILD) as usize;
    assert_eq!((projected_word(&result, child), projected_word(&result, child + 4), projected_word(&result, child + 12)), (2, 3, 1));
    let list = projected_slot(&c, &result, 3) as usize;
    assert_eq!((projected_word(&result, list), projected_word(&result, list + 4)), (2, 0));
    for (i, offset) in [7u32,12].iter().enumerate() {
        let pointer = projected_word(&result, list + 8 + 4 * i);
        assert_ne!(pointer & LAZY_CHILD, 0);
        assert_eq!(projected_word(&result, (pointer & !LAZY_CHILD) as usize), *offset);
    }
    assert_eq!(projected_word(&result, 4), SLAB_BORROWING | SLAB_PROJECTED | SLAB_CERTIFIED);
}

#[test]
fn projection_lazy_singular_merges_materialize_complete_child() {
    let c = projected_schema();
    let mask = projection_program(&c, &[&[], &[]], Projection::LAZY, 0);
    let wire = [26,3,10,1,b'a',26,3,10,1,b'b'];
    let result = projected_slab(&c, &wire, &mask, FLAG_BORROWING).unwrap();
    let child = projected_slot(&c, &result, 2);
    assert_eq!(child & LAZY_CHILD, 0);
    assert_ne!(child, 0);
    let text = projected_word(&result, child as usize + c.types[1].fields[0].slot as usize) as usize;
    assert_eq!(result[text + 8], b'b');
}

#[test]
fn projection_required_closures_stay_eager() {
    let c = compiled(&[(0,vec![m(1,MESSAGE,EXPLICIT,1)]),(0,vec![f(1,INT64,REQUIRED | EXPLICIT)])]);
    let mask = projection_program(&c, &[&[], &[]], Projection::LAZY, 0);
    let result = projected_slab(&c, &[10,0], &mask, FLAG_BORROWING).unwrap();
    let child = projected_slot(&c, &result, 0);
    assert_ne!(child,0); assert_eq!(child & LAZY_CHILD,0);
    assert_eq!(projected_word(&result,4) & SLAB_CERTIFIED,0);
    let merged = projected_slab(&c, &[10,0,10,2,8,9], &mask, FLAG_BORROWING).unwrap();
    let child = projected_slot(&c, &merged, 0) as usize;
    assert_eq!(projected_word(&merged,child + c.types[1].fields[0].slot as usize),9);
}

#[test]
fn lazy_eager_fallback_exposes_complete_descendants() {
    // Required, oneof and group fallbacks must expose the same complete child as lazy decoding,
    // including a grandchild whose own mask is empty. A later merge keeps that expansion.
    for field in [m(1,MESSAGE,EXPLICIT,1),o(m(1,MESSAGE,EXPLICIT,1),0),m(1,GROUP,EXPLICIT,1)] {
        let grouped = field.kind == GROUP;
        let required = if field.oneof == NONE && !grouped { REQUIRED | EXPLICIT } else { 0 };
        let c = compiled(&[
            (0,vec![field]),
            (0,vec![f(1,INT64,required),m(2,MESSAGE,EXPLICIT,2)]),
            (0,vec![f(1,STRING,0)]),
        ]);
        let mask = projection_program(&c,&[&[],&[],&[]],Projection::LAZY,0);
        let wire = if grouped { vec![11,8,1,18,3,10,1,b'x',12] }
            else { vec![10,7,8,1,18,3,10,1,b'x'] };
        for repeated in [false,true] {
            let input = if repeated { [wire.as_slice(),wire.as_slice()].concat() } else { wire.clone() };
            let result=projected_slab(&c,&input,&mask,FLAG_BORROWING).unwrap();
            let child=projected_slot(&c,&result,0) as usize;
            assert_ne!(child,0); assert_eq!(child as u32 & LAZY_CHILD,0);
            let grand=projected_word(&result,child+c.types[1].fields[1].slot as usize) as usize;
            assert_ne!(grand,0); assert_eq!(grand as u32 & LAZY_CHILD,0);
            let text=projected_word(&result,grand+c.types[2].fields[0].slot as usize) as usize;
            assert_ne!(text,0); assert_eq!(result[text+8],b'x');
        }
    }
}

#[test]
fn projection_external_bytes_require_explicit_input_lease() {
    let c = projected_schema();
    let mask = projection_program(&c, &[&[4], &[]], Projection::BORROW_BYTES,4);
    let wire = [42,8,1,2,3,4,5,6,7,8];
    assert_eq!(projected_slab(&c,&wire,&mask,FLAG_OWNING),Err(MALFORMED));
    let result=projected_slab(&c,&wire,&mask,FLAG_BORROWING).unwrap();
    let header=projected_slot(&c,&result,4) as usize;
    assert_eq!(projected_word(&result,header),8 | EXTERNAL);
    assert_eq!(projected_word(&result,header+12),2);
    assert_ne!(projected_word(&result,4) & SLAB_BORROWING,0);
}

#[test]
fn projection_program_rejects_wrong_layout_and_masks() {
    let c = projected_schema();
    let program=projection_program(&c,&[&[0],&[]],0,0);
    for end in 0..program.len() { assert!(Projection::parse(&c,&program[..end]).is_err()); }
    for (at,value) in [(0,0),(4,0),(8,c.fingerprint^1),(12,8),(16,99),(20,4),(24,99),(28,1<<31)] {
        let mut bad=program.clone();bad[at..at+4].copy_from_slice(&value.to_le_bytes());
        assert!(Projection::parse(&c,&bad).is_err(),"{at}");
    }
}

#[test]
fn projection_full_masks_and_strict_sparse_match_reference_corpus() {
    let c = compiled(&nested());
    let all: Vec<Vec<usize>> = c.types.iter().map(|t| (0..t.fields.len()).collect()).collect();
    let all_refs: Vec<&[usize]> = all.iter().map(Vec::as_slice).collect();
    let none: Vec<&[usize]> = c.types.iter().map(|_| &[][..]).collect();
    let full_mask = projection_program(&c,&all_refs,0,0);
    let sparse_mask = projection_program(&c,&none,0,0);
    let lazy_mask = projection_program(&c,&none,Projection::LAZY,0);
    let mut rng = Rng(0x5052_4f4a_4543_5431);
    for _ in 0..8000 {
        let wire = view_wire(&mut rng);
        let expected = slab(&c,&wire);
        let full = projected_slab(&c,&wire,&full_mask,FLAG_OWNING);
        match (&expected,full) {
            (Ok(expected),Ok(mut actual)) => {
                let flags = projected_word(&actual,4) & !SLAB_PROJECTED;
                actual[4..8].copy_from_slice(&flags.to_le_bytes());
                assert_eq!(&actual,expected,"full-mask byte parity {wire:?}");
            }
            (Err(expected),Err(actual)) => assert_eq!(*expected,actual),
            (a,b) => panic!("full mask status {a:?} vs {b:?} for {wire:?}"),
        }
        for (mask,flags) in [(&sparse_mask,FLAG_OWNING),(&lazy_mask,FLAG_BORROWING)] {
            let actual = projected_slab(&c,&wire,mask,flags);
            assert_eq!(expected.as_ref().err(),actual.as_ref().err(),"strict projected status for {wire:?}");
        }
    }
}

#[test]
fn projection_small_path_allocates_nothing_and_checks_budgets() {
    let c = projected_schema();
    let mask = projection_program(&c,&[&[0],&[]],Projection::LAZY,0);
    let projection=Projection::parse(&c,&mask).unwrap();
    let wire=[8,7,26,3,10,1,b'x',34,3,10,1,b'y'];
    let mut output=vec![0;4096];let mut error=0;
    let (result,allocations)=allocation_probe::allocations(|| decode_projected(&c,&wire,FLAG_BORROWING,Some(projection),output.as_mut_ptr(),output.len(),DEPTH,MAX_SLAB,false,&mut error));
    let result=result.unwrap();assert_eq!(allocations,0);
    assert_eq!(decode_projected(&c,&wire,FLAG_BORROWING,Some(projection),output.as_mut_ptr(),output.len(),0,MAX_SLAB,false,&mut error),Err(BUDGET));
    assert_eq!(decode_projected(&c,&wire,FLAG_BORROWING,Some(projection),output.as_mut_ptr(),output.len(),DEPTH,result.bytes-1,false,&mut error),Err(BUDGET));
}

#[test]
fn projection_ffi_normal_and_critical_match_without_allocations() {
    let c=projected_schema();
    let mask=projection_program(&c,&[&[0],&[]],Projection::LAZY,0);
    let wire=[8,7,26,3,10,1,b'x',34,3,10,1,b'y'];
    let expected=projected_slab(&c,&wire,&mask,FLAG_BORROWING).unwrap();
    let mut output=vec![0xa5;expected.len()];
    let mut result=seam::SlabResult {bytes:0,records:0,handles:0,flags:0,error:0,reserved:0};
    for call in [crate::td_buffa_decode_slab,crate::td_buffa_decode_slab_small] {
        let (status,allocations)=allocation_probe::allocations(|| unsafe {
            call((&c as *const Compiled).cast(),wire.as_ptr(),wire.len() as u64,FLAG_BORROWING,
                mask.as_ptr(),output.as_mut_ptr(),output.len() as u64,DEPTH,MAX_SLAB,(&mut result as *mut seam::SlabResult).cast())
        });
        assert_eq!((status,allocations),(0,0)); assert_eq!(output,expected);
        assert_eq!(result.bytes,expected.len() as u64);
    }
}
