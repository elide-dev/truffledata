//! The C ABI (`td_yaml_rs_*`, declared in `yaml/native/rust_source.h`). The only unsafe code in
//! the crate: raw pointers from C++ become slices here. Every entry point runs its body under
//! `catch_unwind`, so a Rust panic becomes `STATUS_INTERNAL` and never unwinds into C++; the
//! parser is pull-based, so no C++ exception ever unwinds through Rust.
#![allow(unsafe_code)]

use crate::events::{Batcher, Failure, RsEvent, STATUS_ARGUMENT, STATUS_SYNTAX};
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::ptr;

pub const MESSAGE_BYTES: usize = 256;

/// `td_yaml_rs_error`.
#[repr(C)]
pub struct RsError {
    pub status: i32,
    pub line: u32,
    pub column: u32,
    pub offset: u32,
    pub message_length: u32,
    pub message: [u8; MESSAGE_BYTES],
}

/// A reusable parser: the batcher borrows the input between `begin` and `end`; the scratch buffer
/// and its capacity survive across documents.
pub struct Handle {
    // 'static is a lie confined to this module: `begin` documents that the caller keeps the input
    // alive and unmodified until `end`, `begin` or `destroy`, which drop the batcher.
    batcher: Option<Batcher<'static>>,
    scratch: Vec<u8>,
    #[cfg(test)]
    pub(crate) panic_next: bool,
}

fn report(error: *mut RsError, failure: &Failure) -> i32 {
    if !error.is_null() {
        // SAFETY: the caller passes a valid, writable td_yaml_rs_error or null.
        let e = unsafe { &mut *error };
        e.status = failure.status;
        e.line = failure.line;
        e.column = failure.column;
        e.offset = failure.offset;
        let mut n = failure.message.len().min(MESSAGE_BYTES - 1);
        while !failure.message.is_char_boundary(n) {
            n -= 1;
        }
        let source = failure.message.as_bytes().iter().take(n).chain(std::iter::once(&0u8));
        for (to, from) in e.message.iter_mut().zip(source) {
            *to = *from;
        }
        e.message_length = n as u32;
    }
    failure.status
}

fn panicked(error: *mut RsError) -> i32 {
    report(error, &Failure::internal("YAML parser panicked"))
}

/// A new parser, or null if construction panicked.
#[unsafe(no_mangle)]
pub extern "C" fn td_yaml_rs_parser_create() -> *mut Handle {
    catch_unwind(|| {
        Box::into_raw(Box::new(Handle {
            batcher: None,
            scratch: Vec::new(),
            #[cfg(test)]
            panic_next: false,
        }))
    })
    .unwrap_or(ptr::null_mut())
}

/// Starts a document stream over `input[0, length)`, which must stay alive and unmodified until
/// the next `begin`, `end` or `destroy`. Checks UTF-8. 0 on success.
///
/// # Safety
/// `handle` comes from `td_yaml_rs_parser_create`; `input` is readable for `length` bytes (or null
/// with length 0); `error` is writable or null.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn td_yaml_rs_parser_begin(
    handle: *mut Handle,
    input: *const u8,
    length: u64,
    error: *mut RsError,
) -> i32 {
    if handle.is_null() || (input.is_null() && length != 0) || u32::try_from(length).is_err() {
        return report(
            error,
            &Failure {
                status: STATUS_ARGUMENT,
                line: 0,
                column: 0,
                offset: 0,
                message: "invalid YAML parser arguments".into(),
            },
        );
    }
    // SAFETY: non-null handle from create, used by one thread at a time.
    let h = unsafe { &mut *handle };
    h.batcher = None;
    let bytes: &'static [u8] = if length == 0 {
        &[]
    } else {
        // SAFETY: the caller guarantees `length` readable bytes that outlive the batcher (above).
        unsafe { std::slice::from_raw_parts(input, length as usize) }
    };
    let result = catch_unwind(AssertUnwindSafe(|| match std::str::from_utf8(bytes) {
        Ok(text) => {
            h.batcher = Some(Batcher::new(text));
            0
        }
        Err(e) => report(
            error,
            &Failure {
                status: STATUS_SYNTAX,
                line: 0,
                column: 0,
                offset: e.valid_up_to() as u32,
                message: "invalid UTF-8 in YAML input".into(),
            },
        ),
    }));
    result.unwrap_or_else(|_| {
        h.batcher = None;
        panicked(error)
    })
}

/// Fills up to `capacity` records into `out`. Returns the count (0 at the end of the stream) or a
/// negative status with `error` filled. `*scratch` receives the batch's scratch bytes, valid until
/// the next call on this handle. After an error or panic the stream is over.
///
/// # Safety
/// `handle` is a begun parser; `out` is writable for `capacity` records; `scratch` and `error`
/// are writable (error may be null).
#[unsafe(no_mangle)]
pub unsafe extern "C" fn td_yaml_rs_parser_next(
    handle: *mut Handle,
    out: *mut RsEvent,
    capacity: u32,
    scratch: *mut *const u8,
    error: *mut RsError,
) -> i32 {
    if handle.is_null() || out.is_null() || scratch.is_null() || capacity == 0 || capacity > i32::MAX as u32 {
        return report(
            error,
            &Failure {
                status: STATUS_ARGUMENT,
                line: 0,
                column: 0,
                offset: 0,
                message: "invalid YAML parser arguments".into(),
            },
        );
    }
    // SAFETY: non-null handle from create; `out` holds `capacity` records (caller contract).
    let h = unsafe { &mut *handle };
    let records = unsafe { std::slice::from_raw_parts_mut(out, capacity as usize) };
    let result = catch_unwind(AssertUnwindSafe(|| {
        #[cfg(test)]
        if std::mem::take(&mut h.panic_next) {
            panic!("injected YAML parser panic");
        }
        let Some(batcher) = h.batcher.as_mut() else {
            return Err(Failure::internal("YAML parser not started"));
        };
        batcher.next_batch(records, &mut h.scratch)
    }));
    // SAFETY: `scratch` is writable (checked non-null).
    unsafe { *scratch = h.scratch.as_ptr() };
    match result {
        Ok(Ok(count)) => count as i32,
        Ok(Err(failure)) => {
            h.batcher = None;
            report(error, &failure)
        }
        Err(_) => {
            h.batcher = None;
            panicked(error)
        }
    }
}

/// Drops the borrow of the input (the scratch capacity is kept).
///
/// # Safety
/// `handle` is null or comes from `td_yaml_rs_parser_create`.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn td_yaml_rs_parser_end(handle: *mut Handle) {
    if handle.is_null() {
        return;
    }
    // SAFETY: as above.
    let h = unsafe { &mut *handle };
    let _ = catch_unwind(AssertUnwindSafe(|| h.batcher = None));
}

/// Frees a parser.
///
/// # Safety
/// `handle` is null or comes from `td_yaml_rs_parser_create` and is not used afterwards.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn td_yaml_rs_parser_destroy(handle: *mut Handle) {
    if handle.is_null() {
        return;
    }
    // SAFETY: ownership returns from the raw pointer made by create.
    let owned = unsafe { Box::from_raw(handle) };
    let _ = catch_unwind(AssertUnwindSafe(move || drop(owned)));
}
