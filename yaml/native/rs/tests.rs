//! Tests over the C records, read back through the C ABI exactly as `rust_source.h` reads them.
#![allow(clippy::unwrap_used, clippy::indexing_slicing, unsafe_code)]

use crate::events::*;
use crate::ffi::*;
use std::collections::HashMap;

/// A record with its text, anchor and tag resolved against the input and the batch scratch.
#[derive(Debug, Clone)]
struct Rec {
    e: RsEvent,
    text: String,
    anchor: String,
    tag: String,
}

#[derive(Debug, PartialEq)]
struct Err {
    status: i32,
    line: u32,
    column: u32,
    message: String,
}

fn bytes<'s>(input: &'s [u8], scratch: &'s [u8], in_input: bool, offset: u32, length: u32) -> &'s [u8] {
    let from = if in_input { input } else { scratch };
    &from[offset as usize..(offset + length) as usize]
}

/// All records of `input` pulled `capacity` at a time, plus the terminating error if any.
fn pull_with(input: &[u8], capacity: u32, inject_panic: bool) -> (Vec<Rec>, Option<Err>) {
    let h = td_yaml_rs_parser_create();
    assert!(!h.is_null());
    let mut error = RsError {
        status: 0,
        line: 0,
        column: 0,
        offset: 0,
        message_length: 0,
        message: [0; MESSAGE_BYTES],
    };
    let mut out = Vec::new();
    let mut failure = None;
    unsafe {
        let begun = td_yaml_rs_parser_begin(h, input.as_ptr(), input.len() as u64, &mut error);
        (*h).panic_next = inject_panic;
        let mut records = vec![RsEvent::default(); capacity as usize];
        if begun != 0 {
            failure = Some(Err {
                status: begun,
                line: error.line,
                column: error.column,
                message: String::new(),
            });
        }
        while failure.is_none() {
            let mut scratch: *const u8 = std::ptr::null();
            let n = td_yaml_rs_parser_next(h, records.as_mut_ptr(), capacity, &mut scratch, &mut error);
            if n < 0 {
                failure = Some(Err {
                    status: n,
                    line: error.line,
                    column: error.column,
                    message: String::from_utf8(error.message[..error.message_length as usize].to_vec()).unwrap(),
                });
                break;
            }
            if n == 0 {
                break;
            }
            // The scratch pointer is valid for this batch only; copy what the records reference.
            let scratch_len = records[..n as usize]
                .iter()
                .flat_map(|e| {
                    let t = if e.flags & FLAG_TEXT_IN_INPUT == 0 {
                        e.text_offset + e.text_length
                    } else {
                        0
                    };
                    let a = if e.kind == KIND_TAG_DIRECTIVE || e.flags & FLAG_ANCHOR_IN_SCRATCH != 0 {
                        e.anchor_offset + e.anchor_length
                    } else {
                        0
                    };
                    [t, a, e.tag_offset + e.tag_length]
                })
                .max()
                .unwrap_or(0) as usize;
            let sc: &[u8] = if scratch_len == 0 {
                &[]
            } else {
                std::slice::from_raw_parts(scratch, scratch_len)
            };
            for e in &records[..n as usize] {
                let text = if e.kind == KIND_SCALAR {
                    bytes(
                        input,
                        sc,
                        e.flags & FLAG_TEXT_IN_INPUT != 0,
                        e.text_offset,
                        e.text_length,
                    )
                } else {
                    &[]
                };
                let anchor_in_input = e.kind != KIND_TAG_DIRECTIVE && e.flags & FLAG_ANCHOR_IN_SCRATCH == 0;
                let anchor = bytes(input, sc, anchor_in_input, e.anchor_offset, e.anchor_length);
                let tag = bytes(input, sc, false, e.tag_offset, e.tag_length);
                out.push(Rec {
                    e: *e,
                    text: String::from_utf8(text.to_vec()).unwrap(),
                    anchor: String::from_utf8(anchor.to_vec()).unwrap(),
                    tag: String::from_utf8(tag.to_vec()).unwrap(),
                });
            }
        }
        td_yaml_rs_parser_end(h);
        td_yaml_rs_parser_destroy(h);
    }
    (out, failure)
}

fn pull(input: &str) -> (Vec<Rec>, Option<Err>) {
    pull_with(input.as_bytes(), 3, false)
}

fn ok(input: &str) -> Vec<Rec> {
    let (records, failure) = pull(input);
    assert!(failure.is_none(), "{input:?}: {failure:?}");
    records
}

fn escape(text: &str) -> String {
    let mut o = String::with_capacity(text.len());
    for ch in text.chars() {
        match ch {
            '\\' => o.push_str("\\\\"),
            '\n' => o.push_str("\\n"),
            '\r' => o.push_str("\\r"),
            '\x08' => o.push_str("\\b"),
            '\t' => o.push_str("\\t"),
            c => o.push(c),
        }
    }
    o
}

/// yaml-test-suite event text from the records.
fn render(records: &[Rec]) -> String {
    let mut out = String::from("+STR\n");
    let props = |r: &Rec| {
        let mut s = String::new();
        if r.e.flags & FLAG_ANCHOR != 0 {
            s += &format!(" &{}", r.anchor);
        }
        if r.e.flags & FLAG_TAG != 0 {
            s += &format!(" <{}>", r.tag);
        }
        s
    };
    for r in records {
        let e = &r.e;
        let line = match e.kind {
            KIND_TAG_DIRECTIVE => continue,
            KIND_DOC_START => {
                if e.flags & FLAG_EXPLICIT != 0 {
                    "+DOC ---".into()
                } else {
                    "+DOC".into()
                }
            }
            KIND_DOC_END => {
                if e.flags & FLAG_EXPLICIT != 0 {
                    "-DOC ...".into()
                } else {
                    "-DOC".into()
                }
            }
            KIND_SEQ_START => format!("+SEQ{}{}", if e.style == STYLE_FLOW { " []" } else { "" }, props(r)),
            KIND_SEQ_END => "-SEQ".into(),
            KIND_MAP_START => format!("+MAP{}{}", if e.style == STYLE_FLOW { " {}" } else { "" }, props(r)),
            KIND_MAP_END => "-MAP".into(),
            KIND_SCALAR => {
                let k = [":", "'", "\"", "|", ">"][e.style as usize];
                format!("=VAL{} {k}{}", props(r), escape(&r.text))
            }
            KIND_ALIAS => format!("=ALI *{}", r.anchor),
            other => panic!("unknown record kind {other}"),
        };
        out += &line;
        out.push('\n');
    }
    out + "-STR\n"
}

/// An alias naming no anchor defined before it in its document.
fn undefined_alias(records: &[Rec]) -> bool {
    let mut defined = std::collections::HashSet::new();
    for r in records {
        match r.e.kind {
            KIND_DOC_START => defined.clear(),
            KIND_ALIAS if !defined.contains(&r.anchor) => return true,
            KIND_SCALAR | KIND_SEQ_START | KIND_MAP_START if r.e.flags & FLAG_ANCHOR != 0 => {
                defined.insert(r.anchor.clone());
            }
            _ => {}
        }
    }
    false
}

/// The probe's conformance.py normalization with strict flow and document markers.
fn normalize(text: &str) -> Vec<String> {
    let mut anchors: HashMap<String, usize> = HashMap::new();
    let mut out = Vec::new();
    for l in text.lines() {
        let l = l.trim_end();
        if l.trim().is_empty() {
            continue;
        }
        let mut l = l.to_string();
        if ["=VAL", "+MAP", "+SEQ", "=ALI"].iter().any(|h| l.starts_with(h)) {
            let mut at = 4;
            if l[at..].starts_with(" {}") || l[at..].starts_with(" []") {
                at += 3;
            }
            if l[at..].starts_with(" &") || l[at..].starts_with(" *") {
                let sigil = l.as_bytes()[at + 1];
                let end = l[at + 2..].find(' ').map(|i| i + at + 2).unwrap_or(l.len());
                let name = l[at + 2..end].to_string();
                let n = if sigil == b'&' {
                    let n = anchors.len() + 1;
                    anchors.insert(name, n);
                    n.to_string()
                } else {
                    anchors
                        .get(&name)
                        .map(|n| n.to_string())
                        .unwrap_or_else(|| "None".into())
                };
                l = format!("{}A{}{}", &l[..at + 2], n, &l[end..]);
            }
        }
        out.push(l);
    }
    out
}

fn suite_inputs() -> Option<Vec<std::path::PathBuf>> {
    let list = std::env::var("YAML_TEST_SUITE_INPUTS").ok()?;
    Some(list.split_whitespace().map(std::path::PathBuf::from).collect())
}

/// yaml-test-suite data-2022-01-17 through the C records, at batch capacities 1, 3 and 256 (which
/// moves anchors, errors and directives across batch boundaries): 402/402 with strict markers.
#[test]
fn yaml_test_suite() {
    let Some(inputs) = suite_inputs() else {
        assert!(
            std::env::var_os("TEST_SRCDIR").is_none(),
            "YAML_TEST_SUITE_INPUTS is required under Bazel"
        );
        eprintln!("yaml_test_suite skipped: set YAML_TEST_SUITE_INPUTS to the in.yaml paths");
        return;
    };
    let (mut passed, mut problems) = (0, Vec::new());
    for input in &inputs {
        let dir = input.parent().unwrap();
        let src = std::fs::read(input).unwrap();
        let must_fail = dir.join("error").exists();
        let mut outcomes = Vec::new();
        for capacity in [1, 3, 256] {
            let (records, failure) = pull_with(&src, capacity, false);
            outcomes.push(match failure {
                // The decoder rejects aliases to unknown anchors (granit delivers them).
                None if undefined_alias(&records) => Err(STATUS_SYNTAX),
                None => Ok(render(&records)),
                Some(f) => Err(f.status),
            });
        }
        let pass = if must_fail {
            outcomes.iter().all(|o| o.is_err())
        } else {
            let expected = std::fs::read_to_string(dir.join("test.event")).unwrap();
            outcomes
                .iter()
                .all(|o| matches!(o, Ok(events) if normalize(events) == normalize(&expected)))
        };
        if pass {
            passed += 1;
        } else {
            problems.push(format!("{}: {:?}", dir.display(), outcomes[2]));
        }
    }
    eprintln!("yaml_test_suite (granit records): {passed}/{} passed", inputs.len());
    assert_eq!(inputs.len(), 402, "unexpected yaml-test-suite case count");
    assert!(
        problems.is_empty(),
        "{} failures:\n{}",
        problems.len(),
        problems.join("\n")
    );
}

#[test]
fn empty_scalars_and_tilde() {
    let r = ok("a:\nb: ~\nc: !!str\nd: ''\n");
    let s: Vec<_> = r.iter().filter(|r| r.e.kind == KIND_SCALAR).collect();
    assert_eq!(s.len(), 8);
    assert!(s[1].e.flags & FLAG_EMPTY != 0 && s[1].text.is_empty());
    assert!(s[3].e.flags & FLAG_EMPTY == 0 && s[3].text == "~");
    assert!(s[5].e.flags & FLAG_EMPTY != 0 && s[5].text.is_empty() && s[5].tag == "tag:yaml.org,2002:str");
    assert!(s[7].e.flags & FLAG_EMPTY == 0 && s[7].text.is_empty() && s[7].e.style == STYLE_SINGLE);
}

#[test]
fn anchors_aliases_and_tags() {
    let r = ok("%TAG !e! tag:example.com,2000:\n--- !e!m\na: &x1 !local v\nb: *x1\nc: !<tag:y> &s [!, !!int 1]\n");
    let d: Vec<_> = r.iter().filter(|r| r.e.kind == KIND_TAG_DIRECTIVE).collect();
    assert_eq!(
        (d.len(), d[0].anchor.as_str(), d[0].tag.as_str()),
        (1, "!e!", "tag:example.com,2000:")
    );
    let map = r.iter().find(|r| r.e.kind == KIND_MAP_START).unwrap();
    assert_eq!(map.tag, "tag:example.com,2000:m");
    let v = r.iter().find(|r| r.text == "v").unwrap();
    assert_eq!((v.anchor.as_str(), v.tag.as_str()), ("x1", "!local"));
    assert!(v.e.flags & FLAG_ANCHOR != 0 && v.e.flags & FLAG_ANCHOR_IN_SCRATCH == 0);
    let alias = r.iter().find(|r| r.e.kind == KIND_ALIAS).unwrap();
    assert_eq!(alias.anchor, "x1");
    let seq = r.iter().find(|r| r.e.kind == KIND_SEQ_START).unwrap();
    assert_eq!(
        (seq.anchor.as_str(), seq.tag.as_str(), seq.e.style),
        ("s", "tag:y", STYLE_FLOW)
    );
    let empty = r.iter().find(|r| r.tag == "!").unwrap();
    assert!(empty.e.flags & FLAG_EMPTY != 0);
    let one = r.iter().find(|r| r.text == "1").unwrap();
    assert_eq!(one.tag, "tag:yaml.org,2002:int");
}

#[test]
fn documents_versions_and_markers() {
    let r = ok("%YAML 1.1\n--- a\n...\n");
    assert_eq!(r[0].e.kind, KIND_DOC_START);
    assert_eq!(
        (r[0].e.flags & FLAG_EXPLICIT, r[0].e.version),
        (FLAG_EXPLICIT, (1 << 16) | 1)
    );
    assert_eq!(
        (r[2].e.kind, r[2].e.flags & FLAG_EXPLICIT),
        (KIND_DOC_END, FLAG_EXPLICIT)
    );
    let r = ok("a\n");
    assert_eq!((r[0].e.flags, r[0].e.version, r[2].e.flags), (0, 0, 0));
    assert!(ok("").is_empty());
    assert!(ok("# only a comment\n").is_empty());
}

#[test]
fn positions_and_spans() {
    // Lines are 1-based; columns are 1-based code points; offsets are bytes.
    let r = ok("é: [x, 'q', \"e\\n\"]\nk: |\n  lit\n  two\n\nn: 1\n");
    let x = r.iter().find(|r| r.text == "x").unwrap();
    assert_eq!((x.e.line, x.e.column, x.e.raw_offset, x.e.raw_length), (1, 5, 5, 1));
    assert!(x.e.flags & FLAG_TEXT_IN_INPUT != 0 && x.e.text_offset == 5);
    let q = r.iter().find(|r| r.text == "q").unwrap();
    assert_eq!((q.e.raw_offset, q.e.raw_length, q.e.text_offset), (8, 3, 9));
    let e = r.iter().find(|r| r.text == "e\n").unwrap();
    assert!(e.e.flags & FLAG_TEXT_IN_INPUT == 0 && e.e.raw_length == 5 && e.e.column == 13);
    let lit = r.iter().find(|r| r.e.style == STYLE_LITERAL).unwrap();
    assert_eq!(lit.text, "lit\ntwo\n");
    // A block scalar's span starts at its first content character, not at the `|` indicator.
    assert_eq!((lit.e.line, lit.e.column, lit.e.raw_offset), (3, 3, 27));
}

#[test]
fn errors_follow_earlier_records() {
    // The records before a scan error are delivered first, whatever the batch size.
    for capacity in [1, 2, 64] {
        let (records, failure) = pull_with(b"a: 1\nb: [1, 2\n", capacity, false);
        // granit reports the unclosed bracket at the bracket, after the flow sequence's first item
        // (it scans one token ahead, so `2` itself is not delivered).
        assert!(records.iter().any(|r| r.text == "b") && records.iter().any(|r| r.e.kind == KIND_SEQ_START));
        let f = failure.unwrap();
        assert_eq!(
            (f.status, f.line, f.column, f.message.as_str()),
            (STATUS_SYNTAX, 2, 4, "unclosed bracket '['")
        );
    }
    let (_, failure) = pull_with(b"a: \xff\n", 4, false);
    assert_eq!(failure.unwrap().status, STATUS_SYNTAX);
}

#[test]
fn unresolved_aliases_are_delivered() {
    // The decoder, not granit, rejects them, with rapidyaml's "undefined YAML alias".
    let r = ok("a: *missing\nb: &x 1\nc: *x\n");
    let aliases: Vec<_> = r.iter().filter(|r| r.e.kind == KIND_ALIAS).map(|r| r.anchor.as_str()).collect();
    assert_eq!(aliases, ["missing", "x"]);
    assert!(undefined_alias(&r));
}

#[test]
fn panics_become_internal_errors() {
    let (records, failure) = pull_with(b"a: b\n", 4, true);
    assert!(records.is_empty());
    let f = failure.unwrap();
    assert_eq!(
        (f.status, f.message.as_str()),
        (STATUS_INTERNAL, "YAML parser panicked")
    );
    // Nothing global is poisoned: the next parse succeeds.
    assert!(pull_with(b"a: b\n", 4, false).1.is_none());
}

#[test]
fn nesting_limits_are_above_the_decoder_depth() {
    let deep = "[".repeat(200) + &"]".repeat(200);
    assert!(pull(&deep).1.is_none());
    let too_deep = "[".repeat(300) + &"]".repeat(300);
    assert_eq!(pull(&too_deep).1.unwrap().status, STATUS_SYNTAX);
}

#[test]
fn tag_directive_needs_separation() {
    // granit-parser 2.1.0 accepts this (YAML 1.2.2 [88] requires blanks); the adapter rejects it.
    let f = pull("%TAG !e!tag:x,2000:\n--- !e!a b\n").1.unwrap();
    assert_eq!((f.status, f.line, f.column), (STATUS_SYNTAX, 1, 9));
    assert!(pull("%TAG !e! tag:x,2000:\n--- !e!a b\n").1.is_none());
    assert!(pull("%TAG !! tag:x,2000:\n%TAG ! tag:y:\n--- !!a b\n").1.is_none());
    assert!(pull("# c\n%TAG !e!\ttag:x,2000:\n--- !e!a b\n").1.is_none());
}
