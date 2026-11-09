use core::ffi::CStr;

/// The fingerprint linked from the compiled C++ must equal the constant in the vendored seam.h,
/// which is what the published bindings jars check at load time.
#[test]
fn fingerprint_matches_vendored_seam() {
    let header = include_str!("../vendor/json/native/generated/seam.h");
    let expected = header
        .lines()
        .find_map(|l| l.strip_prefix("#define TRUFFLEDATA_JSON_SEAM_ABI \"")?.strip_suffix('"'))
        .expect("ABI define in seam.h");
    let actual = unsafe { CStr::from_ptr(truffledata_json_native::td_json_fingerprint().cast()) };
    assert_eq!(actual.to_str().unwrap(), expected);
    assert_eq!(truffledata_json_native::SEAM_ABI, expected);
    assert_eq!(expected.len(), 64);
}
