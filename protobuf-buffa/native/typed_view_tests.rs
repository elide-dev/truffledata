//! Differential, budget, allocation and failure-injection tests for ABI 9 views.
use super::super::super::allocation_probe;
use super::super::decode_reusing_with_status;
use super::*;

/// (number, kind, flags, child, closed enum values)
type FieldSpec<'a> = (u32, u32, u32, u32, &'a [i32]);

/// Build a schema program in NativeSchema's layout: one shared one-byte name, optional enum
/// tables appended after it.
fn program(types: &[(u32, &[FieldSpec])]) -> Vec<u8> {
    let mut words: Vec<u32> = vec![0x54444233, types.len() as u32];
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
        for &(number, kind, field_flags, child, values) in fields.iter() {
            let (table, n) = if values.is_empty() {
                (0, 0)
            } else {
                let at = tables;
                tables += 12 * values.len();
                enums.extend(values.iter().copied());
                (at as u32, values.len() as u32)
            };
            words.extend([number, kind, field_flags, child, table, n, name, 1, name, 1]);
        }
    }
    let mut bytes: Vec<u8> = words.iter().flat_map(|w| w.to_le_bytes()).collect();
    bytes.push(b'x');
    for value in enums {
        for word in [value as u32, name, 1] {
            bytes.extend_from_slice(&word.to_le_bytes());
        }
    }
    bytes
}

const NONE: &[i32] = &[];

/// Root and a recursive child: scalars, packed/unpacked repeated, strings, closed enums (range
/// and sparse), singular/repeated messages and a group, plus a large field number.
fn nested(certified: bool) -> Vec<u8> {
    let flags = if certified { 512 } else { 0 };
    program(&[
        (
            flags,
            &[
                (1, 5, 0, u32::MAX, NONE),
                (2, 3, 1, u32::MAX, NONE),
                (3, 11, 0, 1, NONE),
                (4, 11, 1, 1, NONE),
                (5, 9, 0, u32::MAX, NONE),
                (6, 14, 0, u32::MAX, &[7, 0, 2]),
                (7, 10, 0, 1, NONE),
                (9, 14, 1, u32::MAX, &[3, 1, 2, 0]),
                (5000, 12, 1, u32::MAX, NONE),
            ],
        ),
        (
            512,
            &[
                (1, 5, 0, u32::MAX, NONE),
                (2, 9, 1, u32::MAX, NONE),
                (3, 11, 0, 1, NONE),
            ],
        ),
    ])
}

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

fn child(rng: &mut Rng, depth: u32) -> Vec<u8> {
    let mut out = Vec::new();
    for _ in 0..rng.below(4) {
        match rng.below(4) {
            0 => {
                tag(&mut out, 1, 0);
                varint(&mut out, rng.below(300));
            }
            1 => {
                tag(&mut out, 2, 2);
                let text = ["", "a", "λ", "😀x"][rng.below(4) as usize];
                varint(&mut out, text.len() as u64);
                out.extend_from_slice(text.as_bytes());
            }
            2 if depth < 6 => {
                let inner = child(rng, depth + 1);
                tag(&mut out, 3, 2);
                varint(&mut out, inner.len() as u64);
                out.extend(inner);
            }
            _ => {
                tag(&mut out, 40 + rng.below(3) as u32, 0);
                varint(&mut out, rng.next() >> rng.below(64));
            }
        }
    }
    out
}

/// Mostly valid nested wire, with wrong wire types, unknown fields and rare corruption.
fn wire(rng: &mut Rng) -> Vec<u8> {
    let mut out = Vec::new();
    for _ in 0..rng.below(12) {
        match rng.below(12) {
            0 => {
                tag(&mut out, 1, 0);
                varint(&mut out, rng.next() >> rng.below(64));
            }
            1 => {
                tag(&mut out, 2, 0);
                varint(&mut out, rng.below(1000));
            }
            2 => {
                let mut packed = Vec::new();
                for _ in 0..rng.below(5) {
                    varint(&mut packed, rng.below(100000));
                }
                tag(&mut out, 2, 2);
                varint(&mut out, packed.len() as u64);
                out.extend(packed);
            }
            3 | 4 => {
                let inner = child(rng, 1);
                tag(&mut out, 3 + rng.below(2) as u32, 2);
                varint(&mut out, inner.len() as u64);
                out.extend(inner);
            }
            5 => {
                tag(&mut out, 5, 2);
                let text: &[u8] = match rng.below(20) {
                    0 => &[0xff, 0x41],
                    1..=9 => "λ€".as_bytes(),
                    _ => b"name",
                };
                varint(&mut out, text.len() as u64);
                out.extend_from_slice(text);
            }
            6 => {
                tag(&mut out, 6, 0);
                varint(&mut out, rng.below(9));
            }
            7 => {
                tag(&mut out, 7, 3);
                let inner = child(rng, 1);
                out.extend(inner);
                tag(&mut out, if rng.below(30) == 0 { 8 } else { 7 }, 4);
            }
            8 => {
                let mut packed = Vec::new();
                for _ in 0..rng.below(6) {
                    varint(&mut packed, rng.below(6));
                }
                tag(&mut out, 9, 2);
                varint(&mut out, packed.len() as u64);
                out.extend(packed);
            }
            9 => {
                tag(&mut out, 5000, 2);
                varint(&mut out, 2);
                out.extend([1, 2]);
            }
            10 => {
                // Wrong wire type for a known field, or an unknown field: fixed64 or fixed32.
                if rng.below(2) == 0 {
                    tag(&mut out, 1 + rng.below(10) as u32, 1);
                    out.extend([0u8; 8]);
                } else {
                    tag(&mut out, 1 + rng.below(10) as u32, 5);
                    out.extend([0u8; 4]);
                }
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

fn interpreted(p: &[u8], input: &[u8], cells: u64) -> (Result<Vec<Field>>, i32) {
    let mut status = 0;
    let result = decode_reusing_with_status(
        p,
        input,
        3,
        &mut [],
        cells,
        4096,
        8,
        1 << 24,
        Vec::new(),
        &mut status,
    );
    (result, status)
}

/// Naively recompute and compare every directory reachable from one message.
fn check(
    compiled: &Compiled,
    entries: &[Field],
    words: &[u32],
    ty: usize,
    start: usize,
    end: usize,
    dir: usize,
) {
    let t = &compiled.types[ty];
    for (ordinal, f) in t.fields.iter().enumerate() {
        let mut positions = Vec::new();
        let mut i = start;
        while i < end {
            if entries[i].number as usize == ordinal + 1 {
                positions.push(i as u32);
            }
            i = entries[i].end as usize;
        }
        let word = words[dir + ordinal];
        if f.repeated {
            if positions.is_empty() {
                assert_eq!(word, 0);
            } else {
                let list = word as usize;
                assert_eq!(words[list] as usize, positions.len());
                assert_eq!(&words[list + 1..list + 1 + positions.len()], &positions[..]);
            }
        } else {
            assert_eq!(word, positions.last().map_or(0, |p| p + 1));
        }
    }
    let mut i = start;
    while i < end {
        let f = &t.fields[entries[i].number as usize - 1];
        if f.kind == 10 || f.kind == 11 {
            let next = entries[i].end as usize;
            check(compiled, entries, words, f.child, i + 1, next, entries[i].value as usize);
        }
        i = entries[i].end as usize;
    }
}

fn parts(v: &View) -> (&[Field], &[u32]) {
    // SAFETY: a live view's entries and directory words lie within its allocation.
    unsafe {
        (
            std::slice::from_raw_parts(v.data, v.count),
            std::slice::from_raw_parts(v.data.add(v.count).cast::<u32>(), v.words),
        )
    }
}

fn free(v: View) {
    // SAFETY: each view is released exactly once.
    unsafe { release(v.data, v.capacity) };
}

#[test]
fn compiled_views_match_interpreted_index_and_directories() {
    for certified in [true, false] {
        let p = nested(certified);
        let compiled = compile(&p).unwrap();
        let mut rng = Rng(0x9e37_79b9_7f4a_7c15);
        let mut scratch = Vec::new();
        let mut accepted = 0;
        for round in 0..4000 {
            let input = wire(&mut rng);
            let cells = if round % 7 == 0 { rng.below(40) } else { 100_000 };
            let (expected, expected_status) = interpreted(&p, &input, cells);
            let mut status = -1;
            let (actual, back) =
                view(&compiled, &input, cells, 4096, 8, 1 << 24, scratch, &mut status);
            scratch = back;
            match (expected, actual) {
                (Ok(index), Ok(v)) => {
                    accepted += 1;
                    assert_eq!(status, expected_status, "{input:?}");
                    if index.is_empty() {
                        assert!(v.data.is_null() && v.count == 0 && v.capacity == 0);
                        continue;
                    }
                    let (entries, words) = parts(&v);
                    assert_eq!(entries.len(), index.len());
                    for (a, b) in entries.iter().zip(&index) {
                        assert_eq!(
                            (a.number, a.wire, a.length, a.end),
                            (b.number, b.wire, b.length, b.end)
                        );
                        if a.wire != 10 && a.wire != 11 {
                            assert_eq!(a.value, b.value);
                        }
                    }
                    check(&compiled, entries, words, 0, 0, entries.len(), 0);
                    free(v);
                }
                (Err(a), Err(b)) => assert_eq!(a, b, "{input:?}"),
                (a, b) => panic!("divergence on {input:?}: {:?} vs {:?}", a.err(), b.err()),
            }
        }
        assert!(accepted > 1000, "corpus too often malformed: {accepted}");
    }
}

#[test]
fn duplicate_singular_messages_are_not_certified() {
    let compiled = compile(&nested(true)).unwrap();
    for (input, expected) in [
        (&[26, 0][..], 5),
        (&[26, 0, 26, 0][..], 0),
        (&[34, 0, 34, 0][..], 5),
        (&[26, 2, 26, 0][..], 5),
        (&[26, 4, 26, 0, 26, 0][..], 0),
    ] {
        let mut status = -1;
        let (v, _) = view(&compiled, input, 100, 100, 8, 4096, Vec::new(), &mut status);
        assert_eq!(status, expected);
        free(v.unwrap());
    }
    let mut status = -1;
    let uncertified = compile(&nested(false)).unwrap();
    let (v, _) = view(&uncertified, &[8, 1], 100, 100, 8, 4096, Vec::new(), &mut status);
    assert_eq!(status, 0);
    free(v.unwrap());
    // Empty messages allocate nothing, even under a zero budget, and remain certified.
    let ((v, _), count) = allocation_probe::allocations(|| {
        view(&compiled, &[], 100, 100, 8, 0, Vec::new(), &mut status)
    });
    let v = v.unwrap();
    assert_eq!((count, status, v.count, v.capacity), (0, 5, 0, 0));
    assert!(v.data.is_null());
}

#[test]
fn retained_scratch_never_rejects_a_fresh_valid_budget() {
    let compiled = compile(&nested(true)).unwrap();
    for count in [0usize, 1, 3, 9, 30] {
        let mut input = Vec::new();
        for i in 0..count {
            input.extend([16, i as u8]);
        }
        for budget in (0..=4096u64).step_by(8) {
            let mut status = 0;
            let (fresh, _) = view(&compiled, &input, 1000, 0, 8, budget, Vec::new(), &mut status);
            for previous in [0usize, 1, 4, 8, 16, 32, 64, 128] {
                let (reused, scratch) = view(
                    &compiled,
                    &input,
                    1000,
                    0,
                    8,
                    budget,
                    Vec::with_capacity(previous),
                    &mut status,
                );
                // Larger retained scratch can transfer in place where fresh growth cannot.
                assert!(fresh.is_err() || reused.is_ok(), "{count} {budget} {previous}");
                if let Ok(v) = reused {
                    let retained = (v.capacity * size_of::<Field>()) as u64;
                    let kept = (scratch.capacity() * size_of::<Field>()) as u64;
                    assert!(retained + kept <= budget, "{count} {budget} {previous}");
                    free(v);
                }
            }
            if let Ok(v) = fresh {
                free(v);
            }
        }
    }
}

#[test]
fn warmed_view_makes_one_allocation_and_failures_never_leak() {
    let p = nested(true);
    let compiled = compile(&p).unwrap();
    let input = [8, 1, 18, 2, 1, 2, 26, 4, 8, 7, 18, 0, 42, 1, b'x', 34, 0];
    let mut status = 0;
    let (warm, scratch) = view(&compiled, &input, 1000, 100, 8, 1 << 20, Vec::new(), &mut status);
    free(warm.unwrap());
    let ((v, scratch), count) = allocation_probe::allocations(|| {
        view(&compiled, &input, 1000, 100, 8, 1 << 20, scratch, &mut status)
    });
    assert_eq!(count, 1, "steady-state view allocations");
    free(v.unwrap());
    drop(scratch);
    // Twenty repeated scalars grow scratch to 32 entries; their 25-slot view fits only by
    // transferring that scratch in place under a 1,200-byte (50-entry) budget.
    let repeated: Vec<u8> = (0..20).flat_map(|i| [16u8, i]).collect();
    let mut status = 0;
    let (transferred, scratch) =
        view(&compiled, &repeated, 1000, 100, 8, 1200, Vec::new(), &mut status);
    let transferred = transferred.unwrap();
    assert_eq!((transferred.count, transferred.capacity, scratch.capacity()), (20, 32, 0));
    free(transferred);
    // Fail every allocation from the n-th on: index growth, the exact copy, and the in-place
    // transfer path. Each failure must surface and release everything.
    for (input, budget) in [(&input[..], 1 << 20), (&repeated[..], 1200)] {
        let mut successes = 0;
        for n in 0..16 {
            let outcome = allocation_probe::failing(n, || {
                let mut status = 0;
                let (v, scratch) =
                    view(&compiled, input, 1000, 100, 8, budget, Vec::new(), &mut status);
                drop(scratch);
                match v {
                    Ok(v) => {
                        free(v);
                        0
                    }
                    Err(code) => code,
                }
            });
            assert!(outcome == 0 || outcome == ALLOCATION, "{budget} {n}: {outcome}");
            successes += usize::from(outcome == 0);
        }
        assert!(successes > 0 && successes < 16, "{budget}: {successes}");
    }
    let mut compiled_ok = 0;
    for n in 0..64 {
        let outcome = allocation_probe::failing(n, || compile(&p).err());
        assert!(outcome.is_none() || outcome == Some(ALLOCATION));
        compiled_ok += usize::from(outcome.is_none());
    }
    assert!(compiled_ok > 0 && compiled_ok < 64);
}

#[test]
fn compilation_checks_every_extent_and_view_eligibility() {
    let p = nested(true);
    // Binary views read every header, field and closed-enum value word, but no names: every
    // truncation before the last enum value's end must be rejected.
    let last_value_end = p.len() - 12 + 4;
    for end in 0..last_value_end {
        assert!(compile(&p[..end]).is_err(), "{end}");
    }
    assert!(compile(&p[..last_value_end]).is_ok());
    let wide: Vec<FieldSpec> = (1..=65).map(|n| (n, 5, 0, u32::MAX, NONE)).collect();
    assert_eq!(compile(&program(&[(512, &wide)])).err(), Some(MALFORMED));
    assert!(compile(&program(&[(512, &wide[..64])])).is_ok());
    assert_eq!(compile(&program(&[(513, &wide[..1])])).err(), Some(MALFORMED));
    assert_eq!(compile(&program(&[(512, &[(1, 11, 0, 1, NONE)])])).err(), Some(MALFORMED));
    assert_eq!(compile(&program(&[])).err(), Some(MALFORMED));
    // Large numbers use the declared-order search; the first duplicate number wins.
    let sparse = compile(&program(&[(
        512,
        &[
            (70000, 5, 0, u32::MAX, NONE),
            (3, 5, 0, u32::MAX, NONE),
            (3, 3, 0, u32::MAX, NONE),
        ],
    )]))
    .unwrap();
    assert_eq!(sparse.types[0].find(70000), Some(0));
    assert_eq!(sparse.types[0].find(3), Some(1));
    assert_eq!(sparse.types[0].find(4), None);
    let dense =
        compile(&program(&[(512, &[(3, 5, 0, u32::MAX, NONE), (3, 3, 0, u32::MAX, NONE)])]))
            .unwrap();
    assert_eq!(dense.types[0].find(3), Some(0));
    assert_eq!(dense.types[0].find(4), None);
    assert_eq!(dense.types[0].find(u32::MAX >> 3), None);
}
