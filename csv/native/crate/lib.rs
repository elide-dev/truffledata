//! Raw C ABI of the TruffleData CSV structural indexer, compiled from vendored sources.
//!
//! Declarations mirror `vendor/csv/native/generated/seam.h` (Myna seam, binding ABI version 2);
//! the exported symbol set is csv/native/exports.map.
#![no_std]

#[cfg(feature = "simdutf-crate")]
extern crate simdutf as _;

include!(concat!(env!("OUT_DIR"), "/seam_abi.rs"));

/// `TRUFFLEDATA_CSV_BINDING_ABI_VERSION` in seam.h.
pub const BINDING_ABI_VERSION: u32 = 2;

unsafe extern "C" {
    /// NUL-terminated ABI fingerprint; equals [`SEAM_ABI`].
    pub fn td_csv_fingerprint() -> *mut u8;
    pub fn td_csv_index(
        data: *const u8,
        length: u64,
        out: *mut i32,
        format: i32,
        kernel: i32,
        carry: i64,
    ) -> i64;
    pub fn td_csv_index_leaf(
        data: *const u8,
        length: u64,
        out: *mut i32,
        format: i32,
        kernel: i32,
        carry: i64,
    ) -> i64;
    pub fn td_csv_kernel(requested: i32) -> i32;
    pub fn td_csv_validate_utf8(data: *const u8, length: u64) -> i64;
}
