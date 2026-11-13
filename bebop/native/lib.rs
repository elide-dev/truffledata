//! Bebop validation kernel: checks one encoded value against a compiled schema program.
//!
//! The program is emitted by the Java codec (`BebopProgram`), which is also the reference: this
//! validator accepts exactly the inputs the Java validator accepts and reports the same reason.
//! It reads the input in place, never outside `[data, data + length)`, and allocates only for
//! duplicate detection in non-strict maps.

#[allow(dead_code, non_camel_case_types)]
mod seam {
    include!("generated/seam.rs");
}

mod validate;

/// Program or input that is not what the Java side produces.
const INVALID_PROGRAM: i64 = 255;

#[unsafe(no_mangle)]
pub extern "C" fn td_bebop_fingerprint() -> *mut u8 {
    seam::ABI_FINGERPRINT.as_ptr().cast_mut()
}

/// Returns 0 when `data` is a valid encoding, else the reason code (`BebopValidator.REASONS`).
///
/// # Safety
/// `program` and `data` are live, immutable regions of the given lengths for this call. A region
/// of length zero is never read. No callback.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn td_bebop_validate(
    program: *const u8,
    program_length: u64,
    data: *const u8,
    length: u64,
) -> i64 {
    let program = unsafe { region(program, program_length) };
    let data = unsafe { region(data, length) };
    match std::panic::catch_unwind(|| validate::run(program, data)) {
        Ok(Ok(())) => 0,
        Ok(Err(reason)) => reason as i64,
        Err(_) => INVALID_PROGRAM,
    }
}

/// The same check for bounded inputs, callable without a thread-state transition.
///
/// # Safety
/// As `td_bebop_validate`.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn td_bebop_validate_leaf(
    program: *const u8,
    program_length: u64,
    data: *const u8,
    length: u64,
) -> i64 {
    unsafe { td_bebop_validate(program, program_length, data, length) }
}

unsafe fn region<'a>(pointer: *const u8, length: u64) -> &'a [u8] {
    if length == 0 || pointer.is_null() {
        &[]
    } else {
        unsafe { std::slice::from_raw_parts(pointer, length as usize) }
    }
}
