//! CPU-dispatched Unicode validation and caller-buffer transcoding, matching Myna's policy.
use crate::MALFORMED;

const SIMD_THRESHOLD: usize = 64;

pub(crate) fn validated(bytes: &[u8]) -> Result<&str, i32> {
    if bytes.len() < SIMD_THRESHOLD {
        return std::str::from_utf8(bytes).map_err(|_| MALFORMED);
    }
    if !simdutf::validate_utf8(bytes) {
        return Err(MALFORMED);
    }
    // SAFETY: simdutf validated this exact, immutable slice, without replacing invalid text.
    Ok(unsafe { std::str::from_utf8_unchecked(bytes) })
}

pub(crate) fn utf16_length(bytes: &[u8]) -> Result<usize, i32> {
    let text = validated(bytes)?;
    Ok(if bytes.len() < SIMD_THRESHOLD {
        text.encode_utf16().count()
    } else {
        simdutf::utf16_length_from_utf8(bytes)
    })
}

/// Validate and transcode directly into caller-owned UTF-16 storage. Neither pointer escapes.
/// Returns units written, -1 for malformed UTF-8, or -2 for invalid extents/alignment.
///
/// # Safety
/// Both allocations must be live for their declared extents and disjoint; the output must be
/// writable. The output can be partially written for malformed input and must not be published.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn td_buffa_utf8_to_utf16(
    data: *const u8,
    length: u64,
    output: *mut u16,
    capacity: u64,
) -> i64 {
    if length == 0 {
        return 0;
    }
    if data.is_null()
        || output.is_null()
        || !(output as usize).is_multiple_of(2)
        || length > isize::MAX as u64
        || capacity > (isize::MAX / 2) as u64
        || capacity < length
    {
        return -2;
    }
    // SAFETY: the caller supplies disjoint valid allocations. One UTF-16 unit per UTF-8 byte is
    // a sufficient bound even on malformed input; conversion validates rather than replacing.
    let written = unsafe { simdutf::convert_utf8_to_utf16(data, length as usize, output) };
    if written == 0 { -1 } else { written as i64 }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn unicode_parity_at_short_and_simd_boundaries() {
        for length in [0, 1, 63, 64, 65, 255, 256, 257, 4096] {
            for pattern in ["a\0", "é", "中", "😀", "aé中😀"] {
                let text = pattern.repeat(length);
                let bytes = text.as_bytes();
                assert_eq!(validated(bytes).unwrap(), text);
                let expected: Vec<u16> = text.encode_utf16().collect();
                assert_eq!(utf16_length(bytes).unwrap(), expected.len());
                let mut output = vec![0xabba; bytes.len() + 2];
                let written = unsafe {
                    td_buffa_utf8_to_utf16(
                        bytes.as_ptr(),
                        bytes.len() as u64,
                        output.as_mut_ptr().add(1),
                        bytes.len() as u64,
                    )
                };
                assert_eq!(written, expected.len() as i64);
                assert_eq!(&output[1..1 + expected.len()], expected);
                assert_eq!(output[0], 0xabba);
                assert_eq!(output[1 + expected.len()], 0xabba);
            }
        }
    }

    #[test]
    fn malformed_unicode_and_invalid_extents_never_escape_as_text() {
        for bad in [
            &[0x80][..],
            &[0xc0, 0x80],
            &[0xe2, 0x82],
            &[0xed, 0xa0, 0x80],
            &[0xf4, 0x90, 0x80, 0x80],
            &[0xff],
        ] {
            for padding in [0, 63, 64, 256] {
                for suffix in [false, true] {
                    let mut bytes = vec![b'a'; padding];
                    if suffix {
                        bytes.splice(0..0, bad.iter().copied());
                    } else {
                        bytes.extend_from_slice(bad);
                    }
                    assert_eq!(validated(&bytes).unwrap_err(), MALFORMED);
                    assert_eq!(utf16_length(&bytes).unwrap_err(), MALFORMED);
                    let mut output = vec![0xabba; bytes.len() + 2];
                    assert_eq!(
                        unsafe {
                            td_buffa_utf8_to_utf16(
                                bytes.as_ptr(),
                                bytes.len() as u64,
                                output.as_mut_ptr().add(1),
                                bytes.len() as u64,
                            )
                        },
                        -1
                    );
                    assert_eq!(output[0], 0xabba);
                    assert_eq!(*output.last().unwrap(), 0xabba);
                }
            }
        }
        let mut output = [0xabba; 4];
        assert_eq!(
            unsafe { td_buffa_utf8_to_utf16(b"abc".as_ptr(), 3, output.as_mut_ptr(), 2) },
            -2
        );
        assert_eq!(output, [0xabba; 4]);
        assert_eq!(
            unsafe { td_buffa_utf8_to_utf16(std::ptr::null(), 1, output.as_mut_ptr(), 4) },
            -2
        );
        assert_eq!(
            unsafe { td_buffa_utf8_to_utf16(std::ptr::null(), 0, std::ptr::null_mut(), 0) },
            0
        );
    }
}
