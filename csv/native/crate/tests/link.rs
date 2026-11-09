use core::ffi::CStr;

/// The fingerprint linked from the compiled C++ must equal the constant in the vendored seam.h,
/// which is what the published bindings jars check at load time.
#[test]
fn fingerprint_matches_vendored_seam() {
    let header = include_str!("../vendor/csv/native/generated/seam.h");
    // One define per Myna ABI class, `#ifdef __CHAR_UNSIGNED__` first (aarch64 Linux), then `#else`.
    let defines: Vec<&str> = header
        .lines()
        .filter_map(|l| l.strip_prefix("#define TRUFFLEDATA_CSV_SEAM_ABI \"")?.strip_suffix('"'))
        .collect();
    assert_eq!(defines.len(), 2, "ABI defines in seam.h");
    assert_ne!(defines[0], defines[1]);
    let char_unsigned = cfg!(all(target_os = "linux", target_arch = "aarch64"));
    let expected = if char_unsigned { defines[0] } else { defines[1] };
    // The compiled C++ took the compiler's `__CHAR_UNSIGNED__` branch; build.rs mirrored it from the target.
    let actual = unsafe { CStr::from_ptr(truffledata_csv_native::td_csv_fingerprint().cast()) };
    assert_eq!(actual.to_str().unwrap(), expected);
    assert_eq!(truffledata_csv_native::SEAM_ABI, expected);
    assert_eq!(expected.len(), 64);
}

/// The kernel selected for AUTO is one the build compiled, and the simdutf-backed validator links.
#[test]
fn kernel_and_utf8_link() {
    let kernel = unsafe { truffledata_csv_native::td_csv_kernel(0) };
    assert!(kernel >= 0, "td_csv_kernel(AUTO) = {kernel}");
    let text = "a,b\n\u{e9},c\n".as_bytes();
    let valid = unsafe { truffledata_csv_native::td_csv_validate_utf8(text.as_ptr(), text.len() as u64) };
    let invalid = unsafe { truffledata_csv_native::td_csv_validate_utf8(b"a\xff".as_ptr(), 2) };
    assert_eq!(valid, -1);
    assert_eq!(invalid, 1);
}
