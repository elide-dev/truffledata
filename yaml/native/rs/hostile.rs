//! Hostile-input pins (design step 3): generated shapes scale linearly, deep nesting stops with a
//! syntax error at granit's limit, and the C++ session fuzzer's corpus returns cleanly.
#![allow(clippy::unwrap_used, clippy::indexing_slicing)]

use crate::events::{Batcher, Failure, RsEvent, STATUS_DECODER_SYNTAX, STATUS_INTERNAL, STATUS_SYNTAX};
use std::fmt::Write as _;
use std::time::{Duration, Instant};

// Test binary only: counts this thread's heap allocations so the Rust-side allocation of a parse
// (outside the decoder's index budget, by the 2026-11-14 ruling) can be pinned as linear in the
// input. Production builds set no global allocator (#37).
struct Counting;
thread_local! {
    static COUNTS: std::cell::Cell<(u64, u64)> = const { std::cell::Cell::new((0, 0)) };
}
#[allow(unsafe_code)]
unsafe impl std::alloc::GlobalAlloc for Counting {
    unsafe fn alloc(&self, layout: std::alloc::Layout) -> *mut u8 {
        let _ = COUNTS.try_with(|c| {
            let (n, b) = c.get();
            c.set((n + 1, b + layout.size() as u64));
        });
        // SAFETY: forwarded unchanged to the system allocator.
        unsafe { std::alloc::System.alloc(layout) }
    }
    unsafe fn dealloc(&self, ptr: *mut u8, layout: std::alloc::Layout) {
        // SAFETY: as above.
        unsafe { std::alloc::System.dealloc(ptr, layout) }
    }
    unsafe fn realloc(&self, ptr: *mut u8, layout: std::alloc::Layout, size: usize) -> *mut u8 {
        let _ = COUNTS.try_with(|c| {
            let (n, b) = c.get();
            c.set((n + 1, b + size as u64));
        });
        // SAFETY: as above.
        unsafe { std::alloc::System.realloc(ptr, layout, size) }
    }
}
#[global_allocator]
static ALLOCATOR: Counting = Counting;

fn allocations(input: &str) -> (u64, u64) {
    let before = COUNTS.with(std::cell::Cell::get);
    let _ = run(input);
    let after = COUNTS.with(std::cell::Cell::get);
    (after.0 - before.0, after.1 - before.1)
}

/// The parser's own heap use per parse (allocations and bytes requested) grows at most linearly:
/// each shape at 4n allocates at most 6x what it allocates at n (floors of 64 allocations and
/// 64 KiB). This is the guard for the parser-side allocation the decoder's budget does not see.
#[test]
fn parser_allocation_is_linear() {
    let mut problems = Vec::new();
    for case in SHAPES {
        let n = if *case == "deep-block-map" { 500 } else { 20_000 };
        let (small, large) = (allocations(&shape(case, n)), allocations(&shape(case, 4 * n)));
        if large.0 > small.0.max(64) * 6 || large.1 > small.1.max(65_536) * 6 {
            problems.push(format!("{case}: n {small:?}, 4n {large:?}"));
        }
    }
    assert!(problems.is_empty(), "non-linear parser allocation:\n{}", problems.join("\n"));
}

fn run(input: &str) -> Result<usize, Failure> {
    let mut batcher = Batcher::new(input);
    let mut out = vec![RsEvent::default(); 256];
    let mut scratch = Vec::new();
    let mut total = 0;
    loop {
        match batcher.next_batch(&mut out, &mut scratch)? {
            0 => return Ok(total),
            n => total += n,
        }
    }
}

/// The scratchpad stress shapes of the design's evaluation (34 cases).
fn shape(case: &str, n: usize) -> String {
    match case {
        "deep-flow-seq-open" => "[".repeat(n),
        "deep-flow-seq" => "[".repeat(n) + &"]".repeat(n),
        "deep-flow-map" => "{a: ".repeat(n) + &"}".repeat(n),
        "deep-block-seq" => "- ".repeat(n) + "a\n",
        "deep-block-map" => (0..n).map(|i| " ".repeat(i) + "a:\n").collect(),
        "deep-explicit-key" => "? ".repeat(n) + "a\n",
        "long-flow-line" => format!("[{}]\n", vec!["{a: 1, b: x}"; n].join(", ")),
        "long-flow-multiline" => format!("[\n{}]\n", "  {a: 1, b: x},\n".repeat(n)),
        "long-plain-line" => "word ".repeat(n) + "\n",
        "long-plain-multiline" => "key: word\n".to_string() + &"  word word\n".repeat(n),
        "long-dquote-escapes" => format!("\"{}\"\n", "a\\n\\u00e9 ".repeat(n)),
        "unterminated-dquote" => format!("\"{}", "a b\n".repeat(n)),
        "unterminated-squote" => format!("'{}", "a b\n".repeat(n)),
        "many-anchors" => (0..n).fold(String::new(), |mut s, i| {
            let _ = write!(s, "- &a{i} x\n- *a{i}\n");
            s
        }),
        "same-anchor" => "- &a x\n- *a\n".repeat(n),
        "many-docs" => "---\na: 1\n".repeat(n),
        "many-doc-ends" => "a\n...\n".repeat(n),
        "comments" => "# c\n".repeat(n) + "a: 1\n",
        "blank-lines" => "a:\n".to_string() + &"\n".repeat(n) + "  b\n",
        "long-key" => "k".repeat(n) + ": v\n",
        "long-flow-key" => format!("{{{}: v}}\n", "k".repeat(n)),
        "many-tags" => "!a ".repeat(n) + "x\n",
        "many-anchor-props" => "&a ".repeat(n) + "x\n",
        "commas" => format!("[{}]\n", ", ".repeat(n)),
        "colons" => "a: ".repeat(n) + "\n",
        "question-flow" => format!("[{}]\n", "? ".repeat(n)),
        "tabs" => "a:\t".to_string() + &"\t".repeat(n) + "b\n",
        "literal-indent" => "a: |\n".to_string() + &(" ".repeat(64) + "x\n").repeat(n),
        "folded-blank" => "a: >\n".to_string() + &" x\n\n".repeat(n),
        "implicit-key-lines" => "a: b\n".repeat(n),
        "dup-directives" => "%TAG !a! tag:x,2000:\n".repeat(n) + "---\na\n",
        "tag-directives-docs" => "%TAG !a! tag:x,2000:\n--- !a!b x\n...\n".repeat(n),
        "seq-of-empty" => "-\n".repeat(n),
        "flow-in-block-keys" => "[a]: b\n".repeat(n),
        _ => panic!("unknown case {case}"),
    }
}

const SHAPES: &[&str] = &[
    "deep-flow-seq-open", "deep-flow-seq", "deep-flow-map", "deep-block-seq", "deep-block-map", "deep-explicit-key",
    "long-flow-line", "long-flow-multiline", "long-plain-line", "long-plain-multiline", "long-dquote-escapes",
    "unterminated-dquote", "unterminated-squote", "many-anchors", "same-anchor", "many-docs", "many-doc-ends",
    "comments", "blank-lines", "long-key", "long-flow-key", "many-tags", "many-anchor-props", "commas", "colons",
    "question-flow", "tabs", "literal-indent", "folded-blank", "implicit-key-lines", "dup-directives",
    "tag-directives-docs", "seq-of-empty", "flow-in-block-keys",
];

fn best_of_three(input: &str) -> (Duration, Result<usize, Failure>) {
    let mut best = Duration::MAX;
    let mut outcome = None;
    for _ in 0..3 {
        let start = Instant::now();
        let r = run(input);
        best = best.min(start.elapsed());
        outcome = Some(r);
    }
    (best, outcome.unwrap())
}

/// Each shape at n and 4n: the larger input takes at most 6x as long (linear is 4x; quadratic would
/// be 16x). Times below a 2 ms floor are compared against the floor, so noise on tiny inputs cannot
/// fail the test. No outcome is an internal error.
#[test]
fn shapes_scale_linearly() {
    let floor = Duration::from_millis(2);
    let mut problems = Vec::new();
    for case in SHAPES {
        // deep-block-map grows quadratically in n by construction; its input is linear in bytes.
        let n = if *case == "deep-block-map" { 500 } else { 20_000 };
        let mut verdict = None;
        for _attempt in 0..2 {
            let (small, a) = best_of_three(&shape(case, n));
            let (large, b) = best_of_three(&shape(case, 4 * n));
            for r in [&a, &b] {
                if let Err(f) = r {
                    assert_ne!(f.status, STATUS_INTERNAL, "{case}: {f:?}");
                }
            }
            let ok = large <= small.max(floor) * 6;
            verdict = Some((ok, small, large));
            if ok {
                break;
            }
        }
        let (ok, small, large) = verdict.unwrap();
        if !ok {
            problems.push(format!("{case}: n {small:?}, 4n {large:?}"));
        }
    }
    assert!(problems.is_empty(), "non-linear shapes:\n{}", problems.join("\n"));
}

#[test]
fn deep_nesting_is_a_syntax_error() {
    for case in ["deep-flow-seq-open", "deep-flow-seq", "deep-flow-map", "deep-block-seq", "deep-explicit-key"] {
        let f = run(&shape(case, 50_000)).unwrap_err();
        assert_eq!(f.status, STATUS_SYNTAX, "{case}: {f:?}");
        assert!(f.message.contains("recursion limit"), "{case}: {f:?}");
    }
    let f = run(&shape("deep-block-map", 1000)).unwrap_err();
    assert_eq!(f.status, STATUS_SYNTAX, "{f:?}");
}

/// The session fuzzer's seed corpus (`yaml/native/corpus/session`, two control bytes then the
/// document), including the inputs of rapidyaml's fuzz findings: every input returns a result or a
/// syntax error, never an internal error.
#[test]
fn session_corpus_returns_cleanly() {
    let files: Vec<std::path::PathBuf> = match std::env::var("YAML_SESSION_CORPUS") {
        Ok(list) => list.split_whitespace().map(Into::into).collect(),
        Err(_) => {
            let dir = std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("../corpus/session");
            std::fs::read_dir(dir).unwrap().map(|e| e.unwrap().path()).collect()
        }
    };
    assert!(files.len() >= 17, "session corpus not found");
    let mut seen = Vec::new();
    for path in &files {
        let bytes = std::fs::read(path).unwrap();
        let document = bytes.get(2..).unwrap_or(&[]);
        let outcome = match std::str::from_utf8(document) {
            Ok(text) => run(text).map(|_| ()),
            Err(_) => Ok(()),  // the session rejects invalid UTF-8 before parsing
        };
        if let Err(f) = &outcome {
            assert!(f.status == STATUS_SYNTAX || f.status == STATUS_DECODER_SYNTAX, "{}: {f:?}", path.display());
        }
        seen.push(path.file_name().unwrap().to_string_lossy().into_owned());
    }
    assert!(seen.iter().any(|n| n == "annotation-split-lines-unreachable"));
}
