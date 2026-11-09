use core::ffi::CStr;

/// The fingerprint linked from the compiled C++ must equal the constant in the vendored seam.h,
/// which is what the published bindings jars check at load time.
#[test]
fn fingerprint_matches_vendored_seam() {
    let header = include_str!("../vendor/csv/native/generated/seam.h");
    let expected = header
        .lines()
        .find_map(|l| l.strip_prefix("#define TRUFFLEDATA_CSV_SEAM_ABI \"")?.strip_suffix('"'))
        .expect("ABI define in seam.h");
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
