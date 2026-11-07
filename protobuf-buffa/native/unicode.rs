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

/// Strict UTF-8 validation (no overlongs, surrogates or values above U+10FFFF) that also counts
/// UTF-16 units: `Some((units, ascii))`, or None for malformed input. ASCII is checked eight bytes
/// at a time first (the common case: `units == len`); a non-ASCII tail shorter than
/// `SIMD_THRESHOLD` goes through a scalar validator that counts units in the same pass, longer
/// tails through simdutf.
#[inline]
pub(crate) fn validate_utf16_units(bytes: &[u8]) -> Option<(usize, bool)> {
    let mut i = 0;
    let n = bytes.len();
    while i + 8 <= n {
        let word = u64::from_le_bytes(bytes[i..i + 8].try_into().unwrap());
        if word & 0x8080_8080_8080_8080 != 0 {
            break;
        }
        i += 8;
    }
    while i < n && bytes[i] < 0x80 {
        i += 1;
    }
    if i == n {
        return Some((n, true));
    }
    let tail = &bytes[i..];
    if tail.len() >= SIMD_THRESHOLD {
        if !simdutf::validate_utf8(tail) {
            return None;
        }
        return Some((i + simdutf::utf16_length_from_utf8(tail), false));
    }
    scalar_units(tail).map(|units| (i + units, false))
}

/// Scalar strict UTF-8 validation counting UTF-16 units (Unicode Table 3-7 well-formed ranges).
#[inline(never)]
fn scalar_units(bytes: &[u8]) -> Option<usize> {
    let n = bytes.len();
    let mut i = 0;
    let mut units = 0;
    let continuation = |i: usize| i < n && bytes[i] & 0xC0 == 0x80;
    while i < n {
        let b = bytes[i];
        if b < 0x80 {
            i += 1;
            units += 1;
            continue;
        }
        let (width, low, high) = match b {
            0xC2..=0xDF => (2, 0x80, 0xBF),
            0xE0 => (3, 0xA0, 0xBF),
            0xE1..=0xEC | 0xEE..=0xEF => (3, 0x80, 0xBF),
            0xED => (3, 0x80, 0x9F),
            0xF0 => (4, 0x90, 0xBF),
            0xF1..=0xF3 => (4, 0x80, 0xBF),
            0xF4 => (4, 0x80, 0x8F),
            _ => return None,
        };
        let second = *bytes.get(i + 1)?;
        if second < low || second > high {
            return None;
        }
        for k in 2..width {
            if !continuation(i + k) {
                return None;
            }
        }
        i += width;
        units += if width == 4 { 2 } else { 1 };
    }
    Some(units)
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

    /// ASCII bit and UTF-16 units agree with std at every length around the word and SIMD
    /// boundaries, for every lead byte and for random byte strings.
    #[test]
    fn validate_utf16_units_matches_std() {
        let check = |bytes: &[u8]| {
            let expected = std::str::from_utf8(bytes)
                .ok()
                .map(|s| (s.encode_utf16().count(), s.is_ascii()));
            assert_eq!(validate_utf16_units(bytes), expected, "{bytes:x?}");
        };
        for length in [0, 1, 7, 8, 9, 15, 16, 17, 63, 64, 65, 127, 128, 129, 300] {
            for pattern in ["a", "é", "中", "😀", "aé中😀", "\u{7f}"] {
                let text = pattern.repeat(length);
                for cut in [text.len(), text.len().saturating_sub(1)] {
                    check(&text.as_bytes()[..cut]);
                }
                let mut prefixed = "x".repeat(length).into_bytes();
                prefixed.extend_from_slice(pattern.as_bytes());
                check(&prefixed);
            }
        }
        for lead in 0..=255u8 {
            for second in [0x00, 0x7F, 0x80, 0x8F, 0x90, 0x9F, 0xA0, 0xBF, 0xC0, 0xFF] {
                for third in [0x80, 0xBF, 0x41] {
                    check(&[lead, second, third, 0x80]);
                    check(&[lead, second, third]);
                    check(&[lead, second]);
                    check(&[b'a', lead, second, third, 0x80, b'b']);
                }
            }
        }
        let mut state = 0x9E37_79B9_7F4A_7C15u64;
        for _ in 0..20_000 {
            state ^= state << 13;
            state ^= state >> 7;
            state ^= state << 17;
            let len = (state % 80) as usize;
            let mut bytes: Vec<u8> = (0..len)
                .map(|i| (state.rotate_left(i as u32 * 7) >> 3) as u8)
                .collect();
            // Mostly valid text with occasional corruption.
            if state % 3 != 0 {
                bytes = String::from_utf8_lossy(&bytes).into_owned().into_bytes();
            }
            check(&bytes);
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
