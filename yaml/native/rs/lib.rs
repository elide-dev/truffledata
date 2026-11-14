//! Pure-Rust YAML parser backend for TruffleData's native YAML decoder (opt-in; rapidyaml is the
//! default). The crate replaces only the parser: it turns granit-parser events into flat C records
//! that `yaml/native/rust_source.h` feeds to the same C++ `yaml::handler` the rapidyaml backend
//! drives. Scalar resolution, keys, aliases, limits and the tape stay in C++.
//!
//! No global allocator, panic strategy or profile is set here (#37): the crate builds as a plain
//! rlib for embedders that already link Rust.
#![deny(unsafe_code)]

pub use granit_parser;

/// Parser options used for every decode: spec indentation rules (yaml-test-suite 9C9N) and anchor
/// names delivered as events. Nesting limits stay at granit's defaults (255), above the decoder's
/// own depth limit (at most 128), so the handler's limit error is the one users see.
pub fn options() -> granit_parser::Options {
    granit_parser::options! { strict_indentation: true, emit_anchor_events: true }
}

#[cfg(test)]
mod tests {
    use super::*;
    use granit_parser::{Event, Parser};

    #[test]
    fn parses_a_mapping() {
        let events: Vec<_> = Parser::new_from_str_with_options("a: b\n", options())
            .map(|r| r.map(|(e, _)| e))
            .collect::<Result<_, _>>()
            .expect("parse");
        assert!(matches!(events[0], Event::StreamStart));
        assert!(matches!(events.last(), Some(Event::StreamEnd)));
        assert_eq!(events.iter().filter(|e| matches!(e, Event::Scalar(..))).count(), 2);
    }
}
