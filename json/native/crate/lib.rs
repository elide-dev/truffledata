//! Raw C ABI of the TruffleData JSON decoder, compiled from vendored sources.
//!
//! Declarations mirror `vendor/json/native/generated/seam.h` (Myna seam, binding ABI version 2).
//! Struct arguments are passed as untyped pointers here; their layouts are fixed by seam.h.
#![no_std]

#[cfg(feature = "simdutf-crate")]
extern crate simdutf as _;

include!(concat!(env!("OUT_DIR"), "/seam_abi.rs"));

/// `TRUFFLEDATA_JSON_BINDING_ABI_VERSION` in seam.h.
pub const BINDING_ABI_VERSION: u32 = 2;

use core::ffi::c_void;

unsafe extern "C" {
    pub fn td_json_decode(
        data: *const u8,
        length: u64,
        max_input_bytes: u64,
        max_index_bytes: u64,
        max_nodes: u64,
        max_text_bytes: u64,
        max_depth: u32,
        result: *mut c_void,
    ) -> i32;
    pub fn td_json_decode_adopted(
        data: *const u8,
        length: u64,
        capacity: u64,
        max_input_bytes: u64,
        max_index_bytes: u64,
        max_nodes: u64,
        max_text_bytes: u64,
        max_depth: u32,
        result: *mut c_void,
    ) -> i32;
    /// NUL-terminated ABI fingerprint; equals [`SEAM_ABI`].
    pub fn td_json_fingerprint() -> *mut u8;
    pub fn td_json_plan_create(
        records: *const c_void,
        record_count: u64,
        fields: *const c_void,
        field_count: u64,
        max_name_bytes: u64,
        result: *mut c_void,
    ) -> i32;
    pub fn td_json_plan_release(plan: *mut c_void);
    pub fn td_json_release(result: *mut c_void);
    pub fn td_json_result_myna(result: *const c_void, output: *mut c_void) -> i32;
    pub fn td_json_session_create(limits: *const c_void) -> *mut c_void;
    pub fn td_json_session_decode(
        session: *mut c_void,
        data: *const u8,
        length: u64,
        flags: u32,
        text_out: *mut u8,
        text_capacity: u64,
        result: *mut c_void,
    ) -> i32;
    pub fn td_json_session_decode_small(
        session: *mut c_void,
        data: *const u8,
        length: u64,
        flags: u32,
        text_out: *mut u8,
        text_capacity: u64,
        result: *mut c_void,
    ) -> i32;
    pub fn td_json_session_release(session: *mut c_void);
    pub fn td_json_session_reset(session: *mut c_void);
    pub fn td_json_session_retain(session: *mut c_void, result: *mut c_void) -> i32;
    pub fn td_json_stream_create(
        session: *mut c_void,
        max_document_bytes: u64,
        batch_bytes: u64,
    ) -> *mut c_void;
    pub fn td_json_stream_feed(stream: *mut c_void, data: *const u8, length: u64) -> u64;
    pub fn td_json_stream_finish(stream: *mut c_void);
    pub fn td_json_stream_next(stream: *mut c_void, flags: u32, result: *mut c_void) -> i32;
    pub fn td_json_stream_release(stream: *mut c_void);
    pub fn td_json_stream_skip(stream: *mut c_void) -> i32;
    pub fn td_json_stream_truncated_bytes(stream: *mut c_void) -> u64;
    pub fn td_json_typed_decode(
        plan: *const c_void,
        data: *const u8,
        length: u64,
        max_input_bytes: u64,
        max_index_bytes: u64,
        result: *mut c_void,
    ) -> i32;
    pub fn td_json_typed_release(result: *mut c_void);
}
