//! Static-image bridge for the JSON SVM tape (performance plan Task D1).
//!
//! `SvmValueTape` transcodes long strings with `myna_utf8_to_utf16_v2`, Myna's simdutf-backed
//! UTF-8 to UTF-16 conversion, exactly as Myna's `SimdNativeValueTape` does. The symbol lives in
//! `myna-values`; this crate makes it a root of the static library so every JSON SVM image
//! resolves it. `td_json_bridge_present` lets the SVM acceptance test prove the link.

pub use myna_values::text::myna_utf8_to_utf16_v2;

/// Link probe: the bridge revision (1). Holds the transcoder's address so the linker keeps it.
#[unsafe(no_mangle)]
pub extern "C" fn td_json_bridge_present() -> u32 {
    let transcoder: unsafe extern "C" fn(*const u8, u64, *mut u16, u64) -> i64 = myna_utf8_to_utf16_v2;
    std::hint::black_box(transcoder);
    1
}
