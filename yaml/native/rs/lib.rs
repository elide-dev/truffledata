//! Pure-Rust YAML parser backend for TruffleData's native YAML decoder (opt-in; rapidyaml is the
//! default). The crate replaces only the parser: it turns granit-parser events into flat C records
//! (`events`) that `yaml/native/rust_source.h` feeds to the same C++ `yaml::handler` the rapidyaml
//! backend drives. Scalar resolution, keys, aliases, limits and the tape stay in C++.
//!
//! `unsafe` is confined to the C ABI (`ffi`). No global allocator, panic strategy or profile is
//! set here (#37): the crate builds as a plain rlib for embedders that already link Rust.
#![deny(unsafe_code)]
#![warn(clippy::unwrap_used, clippy::indexing_slicing)]

pub mod events;
pub mod ffi;

pub use events::options;
pub use granit_parser;

#[cfg(test)]
mod hostile;
#[cfg(test)]
mod tests;
