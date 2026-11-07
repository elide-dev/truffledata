//! YAML parser probe (temporary): saphyr-parser 0.3.3, yaml-rust2 0.13.0, unsafe-libyaml 0.2.11.
//!   rprobe events saphyr|yamlrust2 <file>   yaml-test-suite events
//!   rprobe bench <iters> <file>...          best-of-7 scan-only and Myna-tape timings
use myna_values::{Builder, Bump};
use std::time::Instant;

// ---- core-schema plain scalar resolution into a Myna builder (copying text).
fn plain<A: myna_values::Allocator + Clone>(b: &mut Builder<'_, A>, s: &str) {
    match s {
        "" | "~" | "null" | "Null" | "NULL" => return b.value(&()).unwrap(),
        "true" | "True" | "TRUE" => return b.value(&true).unwrap(),
        "false" | "False" | "FALSE" => return b.value(&false).unwrap(),
        _ => {}
    }
    let c = s.as_bytes()[0];
    if c.is_ascii_digit() || c == b'-' || c == b'+' || c == b'.' {
        if let Ok(v) = s.parse::<i64>() {
            return b.value(&v).unwrap();
        }
        if let Some(h) = s.strip_prefix("0x") {
            if let Ok(v) = i64::from_str_radix(h, 16) {
                return b.value(&v).unwrap();
            }
        }
        if let Some(o) = s.strip_prefix("0o") {
            if let Ok(v) = i64::from_str_radix(o, 8) {
                return b.value(&v).unwrap();
            }
        }
        let bytes = s.as_bytes();
        let ok = bytes.iter().all(|c| c.is_ascii_digit() || b"+-.eE".contains(c))
            && bytes.iter().any(|c| c.is_ascii_digit());
        if ok {
            if let Ok(v) = s.parse::<f64>() {
                if v.is_finite() {
                    return b.value(&v).unwrap();
                }
            }
        }
    }
    b.value(s).unwrap()
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

fn idx(i: usize) -> String {
    if i > 0 { format!(" &{i}") } else { String::new() }
}

mod sap {
    use super::*;
    use saphyr_parser::{Event, Parser, ScalarStyle};
    pub fn events(src: &str) -> Result<String, String> {
        let mut out = String::new();
        for x in Parser::new_from_str(src) {
            let (ev, _) = x.map_err(|e| e.to_string())?;
            let tag = |t: &Option<std::borrow::Cow<'_, saphyr_parser::Tag>>| match t {
                Some(t) => format!(" <{}{}>", t.handle, t.suffix),
                None => String::new(),
            };
            let line = match ev {
                Event::StreamStart => "+STR".into(),
                Event::StreamEnd => "-STR".into(),
                Event::DocumentStart(_) => "+DOC".into(),
                Event::DocumentEnd => "-DOC".into(),
                Event::SequenceStart(i, t) => format!("+SEQ{}{}", idx(i), tag(&t)),
                Event::SequenceEnd => "-SEQ".into(),
                Event::MappingStart(i, t) => format!("+MAP{}{}", idx(i), tag(&t)),
                Event::MappingEnd => "-MAP".into(),
                Event::Scalar(text, style, i, t) => {
                    let k = match style {
                        ScalarStyle::Plain => ":",
                        ScalarStyle::SingleQuoted => "'",
                        ScalarStyle::DoubleQuoted => "\"",
                        ScalarStyle::Literal => "|",
                        ScalarStyle::Folded => ">",
                    };
                    format!("=VAL{}{} {k}{}", idx(i), tag(&t), escape(&text))
                }
                Event::Alias(i) => format!("=ALI *{i}"),
                Event::Nothing => continue,
            };
            out.push_str(&line);
            out.push('\n');
        }
        Ok(out)
    }
    pub fn scan(src: &str) -> u64 {
        let mut n = 0u64;
        for x in Parser::new_from_str(src) {
            let (ev, _) = x.unwrap();
            n += 1;
            if let Event::Scalar(t, ..) = ev {
                n += t.len() as u64;
            }
        }
        n
    }
    pub fn tape(src: &str, arena: &Bump) -> u64 {
        let mut b = Builder::new_in(arena);
        let mut nodes = 0u64;
        for x in Parser::new_from_str(src) {
            let (ev, _) = x.unwrap();
            match ev {
                Event::DocumentStart(_) => b.clear(),
                Event::DocumentEnd => nodes += b.finish().unwrap().nodes().len() as u64,
                Event::SequenceStart(..) => b.begin_array().unwrap(),
                Event::MappingStart(..) => b.begin_object().unwrap(),
                Event::SequenceEnd | Event::MappingEnd => b.end().unwrap(),
                Event::Scalar(t, ScalarStyle::Plain, _, None) => plain(&mut b, &t),
                Event::Scalar(t, ..) => b.value(&*t).unwrap(),
                Event::Alias(_) => b.value(&()).unwrap(),
                _ => {}
            }
        }
        nodes
    }
}

mod yr2 {
    use super::*;
    use yaml_rust2::parser::{Event, Parser};
    use yaml_rust2::scanner::TScalarStyle;
    pub fn events(src: &str) -> Result<String, String> {
        let mut out = String::new();
        let mut p = Parser::new_from_str(src);
        loop {
            let (ev, _) = p.next_token().map_err(|e| e.to_string())?;
            let tag = |t: &Option<yaml_rust2::parser::Tag>| match t {
                Some(t) => format!(" <{}{}>", t.handle, t.suffix),
                None => String::new(),
            };
            let end = matches!(ev, Event::StreamEnd);
            let line = match ev {
                Event::StreamStart => "+STR".into(),
                Event::StreamEnd => "-STR".into(),
                Event::DocumentStart => "+DOC".into(),
                Event::DocumentEnd => "-DOC".into(),
                Event::SequenceStart(i, t) => format!("+SEQ{}{}", idx(i), tag(&t)),
                Event::SequenceEnd => "-SEQ".into(),
                Event::MappingStart(i, t) => format!("+MAP{}{}", idx(i), tag(&t)),
                Event::MappingEnd => "-MAP".into(),
                Event::Scalar(text, style, i, t) => {
                    let k = match style {
                        TScalarStyle::Plain => ":",
                        TScalarStyle::SingleQuoted => "'",
                        TScalarStyle::DoubleQuoted => "\"",
                        TScalarStyle::Literal => "|",
                        TScalarStyle::Folded => ">",
                    };
                    format!("=VAL{}{} {k}{}", idx(i), tag(&t), escape(&text))
                }
                Event::Alias(i) => format!("=ALI *{i}"),
                Event::Nothing => continue,
            };
            out.push_str(&line);
            out.push('\n');
            if end {
                break;
            }
        }
        Ok(out)
    }
    pub fn scan(src: &str) -> u64 {
        let mut n = 0u64;
        let mut p = Parser::new_from_str(src);
        loop {
            let (ev, _) = p.next_token().unwrap();
            n += 1;
            match ev {
                Event::Scalar(t, ..) => n += t.len() as u64,
                Event::StreamEnd => break,
                _ => {}
            }
        }
        n
    }
    pub fn tape(src: &str, arena: &Bump) -> u64 {
        let mut b = Builder::new_in(arena);
        let mut nodes = 0u64;
        let mut p = Parser::new_from_str(src);
        loop {
            let (ev, _) = p.next_token().unwrap();
            match ev {
                Event::DocumentStart => b.clear(),
                Event::DocumentEnd => nodes += b.finish().unwrap().nodes().len() as u64,
                Event::SequenceStart(..) => b.begin_array().unwrap(),
                Event::MappingStart(..) => b.begin_object().unwrap(),
                Event::SequenceEnd | Event::MappingEnd => b.end().unwrap(),
                Event::Scalar(t, TScalarStyle::Plain, _, None) => plain(&mut b, &t),
                Event::Scalar(t, ..) => b.value(&*t).unwrap(),
                Event::Alias(_) => b.value(&()).unwrap(),
                Event::StreamEnd => break,
                _ => {}
            }
        }
        nodes
    }
}

mod uly {
    use super::*;
    use std::mem::MaybeUninit;
    use unsafe_libyaml::*;
    pub fn run(src: &[u8], tape: bool, arena: &Bump) -> u64 {
        unsafe {
            let mut parser = MaybeUninit::<yaml_parser_t>::uninit();
            assert!(yaml_parser_initialize(parser.as_mut_ptr()).ok);
            let parser = parser.as_mut_ptr();
            yaml_parser_set_input_string(parser, src.as_ptr(), src.len() as u64);
            let mut b = Builder::new_in(arena);
            let mut n = 0u64;
            loop {
                let mut ev = MaybeUninit::<yaml_event_t>::uninit();
                assert!(yaml_parser_parse(parser, ev.as_mut_ptr()).ok, "unsafe-libyaml error");
                let e = ev.as_mut_ptr();
                let t = (*e).type_;
                n += 1;
                if tape {
                    match t {
                        YAML_DOCUMENT_START_EVENT => b.clear(),
                        YAML_DOCUMENT_END_EVENT => n += b.finish().unwrap().nodes().len() as u64,
                        YAML_SEQUENCE_START_EVENT => b.begin_array().unwrap(),
                        YAML_MAPPING_START_EVENT => b.begin_object().unwrap(),
                        YAML_SEQUENCE_END_EVENT | YAML_MAPPING_END_EVENT => b.end().unwrap(),
                        YAML_SCALAR_EVENT => {
                            let s = &(*e).data.scalar;
                            let text = std::str::from_utf8_unchecked(std::slice::from_raw_parts(s.value, s.length as usize));
                            if s.style == YAML_PLAIN_SCALAR_STYLE && s.plain_implicit {
                                plain(&mut b, text)
                            } else {
                                b.value(text).unwrap()
                            }
                        }
                        YAML_ALIAS_EVENT => b.value(&()).unwrap(),
                        _ => {}
                    }
                } else if t == YAML_SCALAR_EVENT {
                    n += (*e).data.scalar.length;
                }
                yaml_event_delete(e);
                if t == YAML_STREAM_END_EVENT {
                    break;
                }
            }
            yaml_parser_delete(parser);
            n
        }
    }
}

fn bench(name: &str, file: &str, bytes: usize, iters: usize, mut f: impl FnMut() -> u64) {
    let mut best = f64::MAX;
    let mut sink = 0u64;
    for _ in 0..7 {
        let t = Instant::now();
        for _ in 0..iters {
            sink = sink.wrapping_add(f());
        }
        let ns = t.elapsed().as_nanos() as f64 / iters as f64;
        best = best.min(ns);
    }
    std::hint::black_box(sink);
    println!("{:<22} {:<20} {:>9} B {:>12.0} ns {:>8.1} MB/s", name, file, bytes, best, bytes as f64 / best * 1e3);
}

fn main() {
    let args: Vec<String> = std::env::args().collect();
    if args.len() >= 4 && args[1] == "events" {
        let src = match std::fs::read_to_string(&args[3]) {
            Ok(s) => s,
            Err(e) => {
                eprintln!("error: {e}");
                std::process::exit(1)
            }
        };
        let r = match args[2].as_str() {
            "saphyr" => sap::events(&src),
            "yamlrust2" => yr2::events(&src),
            _ => panic!("parser"),
        };
        match r {
            Ok(s) => print!("{s}"),
            Err(e) => {
                eprintln!("error: {e}");
                std::process::exit(1)
            }
        }
        return;
    }
    let iters: usize = args[2].parse().unwrap();
    for path in &args[3..] {
        let src = std::fs::read_to_string(path).unwrap();
        let file = path.rsplit('/').next().unwrap();
        let n = src.len();
        let it = ((iters as f64 * 200000.0 / (n as f64 + 1000.0)) as usize).max(1);
        let mut arena = Bump::new();
        bench("saphyr(scan)", file, n, it, || sap::scan(&src));
        bench("saphyr->tape", file, n, it, || { arena.reset(); sap::tape(&src, &arena) });
        bench("yaml-rust2(scan)", file, n, it, || yr2::scan(&src));
        bench("yaml-rust2->tape", file, n, it, || { arena.reset(); yr2::tape(&src, &arena) });
        bench("unsafe-libyaml(scan)", file, n, it, || uly::run(src.as_bytes(), false, &arena));
        bench("unsafe-libyaml->tape", file, n, it, || { arena.reset(); uly::run(src.as_bytes(), true, &arena) });
    }
}
