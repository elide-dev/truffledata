#ifndef TRUFFLEDATA_YAML_INPUT_H
#define TRUFFLEDATA_YAML_INPUT_H
// YAML input preparation (docs/superpowers/plans/2026-11-06-yaml.md, D2 "Input encoding"):
//
//   - encoding: UTF-8 (a leading BOM is skipped), or UTF-16LE/BE or UTF-32LE/BE with a BOM,
//     transcoded natively to UTF-8; without a BOM the input is UTF-8;
//   - strict UTF-8 validation (simdutf) over the whole input before rapidyaml sees it;
//   - the YAML printable set (c-printable, YAML 1.2.2 §5.1): TAB, LF, CR, U+0020-U+007E, U+0085,
//     U+00A0-U+D7FF, U+E000-U+FFFD and U+10000-U+10FFFF. Rejected: other C0 controls, DEL, C1
//     controls except NEL, and the noncharacters U+FFFE and U+FFFF. Surrogates and code points past
//     U+10FFFF are already invalid UTF-8.
//
// The printable scan also records a newline bitmap (bit i set when byte i is LF). rapidyaml filters
// scalars in place, so after parsing the buffer no longer shows where a plain scalar's line breaks
// were; the decoder uses the bitmap to tell single-line plain scalars (whose lexical spans are
// always published) from folded multi-line ones.
#include "yaml/native/errors.h"

#include <simdutf.h>

// TD_YAML_SCAN_SWAR forces the portable SWAR block scan (tests of other architectures' path).
#if defined(__SSE2__) && !defined(TD_YAML_SCAN_SWAR)
#define TD_YAML_SCAN_SSE2 1
#include <emmintrin.h>
#elif (defined(__ARM_NEON) || defined(__aarch64__)) && !defined(TD_YAML_SCAN_SWAR)
#define TD_YAML_SCAN_NEON 1
#include <arm_neon.h>
#endif

#include <cstddef>
#include <cstdint>
#include <cstring>
#include <string_view>

namespace truffledata::yaml {

enum class encoding : uint8_t { utf8, utf8_bom, utf16le, utf16be, utf32le, utf32be };

// Detects the encoding from a byte order mark (UTF-32 before UTF-16: FF FE 00 00 is UTF-32LE).
inline encoding detect(const uint8_t* data, size_t length) {
  if (length >= 4 && data[0] == 0 && data[1] == 0 && data[2] == 0xFE && data[3] == 0xFF) return encoding::utf32be;
  if (length >= 4 && data[0] == 0xFF && data[1] == 0xFE && data[2] == 0 && data[3] == 0) return encoding::utf32le;
  if (length >= 2 && data[0] == 0xFE && data[1] == 0xFF) return encoding::utf16be;
  if (length >= 2 && data[0] == 0xFF && data[1] == 0xFE) return encoding::utf16le;
  if (length >= 3 && data[0] == 0xEF && data[1] == 0xBB && data[2] == 0xBF) return encoding::utf8_bom;
  return encoding::utf8;
}

inline size_t bom_length(encoding e) {
  switch (e) {
    case encoding::utf8: return 0;
    case encoding::utf8_bom: return 3;
    case encoding::utf16le: case encoding::utf16be: return 2;
    case encoding::utf32le: case encoding::utf32be: return 4;
  }
  return 0;
}

// Encodes one scalar value (≤ U+10FFFF, not a surrogate) as UTF-8; returns the byte count.
inline size_t encode_utf8(uint32_t c, char* out) {
  if (c < 0x80) { out[0] = char(c); return 1; }
  if (c < 0x800) { out[0] = char(0xC0 | c >> 6); out[1] = char(0x80 | (c & 0x3F)); return 2; }
  if (c < 0x10000) {
    out[0] = char(0xE0 | c >> 12); out[1] = char(0x80 | (c >> 6 & 0x3F)); out[2] = char(0x80 | (c & 0x3F));
    return 3;
  }
  out[0] = char(0xF0 | c >> 18); out[1] = char(0x80 | (c >> 12 & 0x3F));
  out[2] = char(0x80 | (c >> 6 & 0x3F)); out[3] = char(0x80 | (c & 0x3F));
  return 4;
}

// Upper bound of the UTF-8 length of the text after the BOM: UTF-16 needs at most 3 bytes per
// code unit (a surrogate pair is 4 bytes for 2 units) and UTF-32 at most 4 bytes per code point.
inline size_t utf8_capacity(encoding e, size_t length) {
  size_t body = length - bom_length(e);
  switch (e) {
    case encoding::utf16le: case encoding::utf16be:
      if (body % 2) throw syntax_error("truncated UTF-16 YAML input");
      return body / 2 * 3;
    case encoding::utf32le: case encoding::utf32be:
      if (body % 4) throw syntax_error("truncated UTF-32 YAML input");
      return body;  // 4 bytes in, at most 4 bytes out
    default:
      return body;
  }
}

// Transcodes the text after the BOM into `out` (capacity from utf8_capacity) and returns the
// UTF-8 length. UTF-8 input is copied unchanged (validated later). Invalid UTF-16 (an unpaired
// surrogate) or UTF-32 (a surrogate or a code point past U+10FFFF) is a syntax error.
inline size_t transcode(encoding e, const uint8_t* data, size_t length, char* out) {
  const uint8_t* body = data + bom_length(e);
  size_t size = length - bom_length(e);
  switch (e) {
    case encoding::utf8:
    case encoding::utf8_bom:
      if (size) std::memcpy(out, body, size);
      return size;
    case encoding::utf16le:
    case encoding::utf16be: {
      // simdutf takes char16_t pointers, so it decodes aligned input; unaligned input (a Java
      // array slice at an odd offset) is decoded unit by unit with the same rules.
      size_t units = size / 2;
      if (reinterpret_cast<uintptr_t>(body) % alignof(char16_t) == 0) {
        auto* in = reinterpret_cast<const char16_t*>(body);
        simdutf::result r = e == encoding::utf16le ? simdutf::convert_utf16le_to_utf8_with_errors(in, units, out)
                                                   : simdutf::convert_utf16be_to_utf8_with_errors(in, units, out);
        if (r.error != simdutf::error_code::SUCCESS) throw syntax_error("invalid UTF-16 YAML input (unpaired surrogate)");
        return r.count;
      }
      size_t written = 0;
      auto unit = [&](size_t i) -> uint32_t {
        return e == encoding::utf16le ? uint32_t(body[2 * i]) | uint32_t(body[2 * i + 1]) << 8
                                      : uint32_t(body[2 * i]) << 8 | uint32_t(body[2 * i + 1]);
      };
      for (size_t i = 0; i < units; ++i) {
        uint32_t c = unit(i);
        if (c >= 0xD800 && c <= 0xDBFF) {
          uint32_t low = i + 1 < units ? unit(i + 1) : 0;
          if (low < 0xDC00 || low > 0xDFFF) throw syntax_error("invalid UTF-16 YAML input (unpaired surrogate)");
          c = 0x10000 + ((c - 0xD800) << 10) + (low - 0xDC00);
          ++i;
        } else if (c >= 0xDC00 && c <= 0xDFFF) {
          throw syntax_error("invalid UTF-16 YAML input (unpaired surrogate)");
        }
        written += encode_utf8(c, out + written);
      }
      return written;
    }
    case encoding::utf32le:
    case encoding::utf32be: {
      size_t written = 0;
      for (size_t i = 0; i < size; i += 4) {
        uint32_t c = e == encoding::utf32le
            ? uint32_t(body[i]) | uint32_t(body[i + 1]) << 8 | uint32_t(body[i + 2]) << 16 | uint32_t(body[i + 3]) << 24
            : uint32_t(body[i]) << 24 | uint32_t(body[i + 1]) << 16 | uint32_t(body[i + 2]) << 8 | uint32_t(body[i + 3]);
        if (c > 0x10FFFF || (c >= 0xD800 && c <= 0xDFFF)) throw syntax_error("invalid UTF-32 YAML input");
        written += encode_utf8(c, out + written);
      }
      return written;
    }
  }
  return 0;
}

namespace detail {
constexpr uint64_t ones = 0x0101010101010101ull, highs = 0x8080808080808080ull, lows = 0x7F7F7F7F7F7F7F7Full;
// High bit of each byte of `x` equal to `c` (exact: no borrow between bytes).
inline uint64_t equal(uint64_t x, unsigned char c) {
  uint64_t y = x ^ (ones * c);
  return ~(((y & lows) + lows) | y | lows);
}
// High bit of each byte of `x` below 0x20 (exact).
inline uint64_t below_space(uint64_t x) { return ~((x | highs) - ones * 0x20) & ~x & highs; }
// Packs the high bits of the eight bytes into bits 0-7 (byte k -> bit k).
inline uint64_t pack(uint64_t high_bits) { return ((high_bits >> 7) * 0x0102040810204080ull) >> 56; }

// The c-printable check for one byte at `k` (with its continuation bytes when they matter).
inline void printable(const unsigned char* p, size_t k, size_t n) {
  unsigned char c = p[k];
  if (c < 0x20) {
    if (c != '\t' && c != '\n' && c != '\r') throw syntax_error("non-printable control character in YAML input", k);
  } else if (c == 0x7F) {
    throw syntax_error("non-printable control character in YAML input", k);
  } else if (c == 0xC2 && k + 1 < n && p[k + 1] >= 0x80 && p[k + 1] <= 0x9F && p[k + 1] != 0x85) {
    throw syntax_error("non-printable C1 control character in YAML input", k);
  } else if (c == 0xEF && k + 2 < n && p[k + 1] == 0xBF && (p[k + 2] == 0xBE || p[k + 2] == 0xBF)) {
    throw syntax_error("non-printable noncharacter U+FFFE/U+FFFF in YAML input", k);
  }
}
}  // namespace detail

namespace detail {
// One 64-byte block: the LF bitmap (bit k set when byte k is LF) and, in `suspect`, whether any
// byte may break c-printable: a C0 control other than TAB/LF/CR, DEL, 0xC2 (a C1 control lead)
// or 0xEF (a U+FFFE/U+FFFF lead). Suspect blocks are rechecked exactly by printable().
inline uint64_t scan64(const unsigned char* p, bool& suspect) {
#if defined(TD_YAML_SCAN_SSE2)
  const __m128i lf = _mm_set1_epi8('\n'), tab = _mm_set1_epi8('\t'), cr = _mm_set1_epi8('\r');
  const __m128i c1f = _mm_set1_epi8(0x1F), del = _mm_set1_epi8(0x7F);
  const __m128i c2 = _mm_set1_epi8(char(0xC2)), ef = _mm_set1_epi8(char(0xEF));
  uint64_t bits = 0;
  __m128i any = _mm_setzero_si128();
  for (int k = 0; k < 4; ++k) {
    __m128i v = _mm_loadu_si128(reinterpret_cast<const __m128i*>(p + 16 * k));
    __m128i is_lf = _mm_cmpeq_epi8(v, lf);
    __m128i control = _mm_cmpeq_epi8(_mm_min_epu8(v, c1f), v);  // v <= 0x1F, unsigned
    __m128i space = _mm_or_si128(is_lf, _mm_or_si128(_mm_cmpeq_epi8(v, tab), _mm_cmpeq_epi8(v, cr)));
    __m128i bad = _mm_or_si128(_mm_andnot_si128(space, control),
                               _mm_or_si128(_mm_cmpeq_epi8(v, del),
                                            _mm_or_si128(_mm_cmpeq_epi8(v, c2), _mm_cmpeq_epi8(v, ef))));
    any = _mm_or_si128(any, bad);
    bits |= uint64_t(uint32_t(_mm_movemask_epi8(is_lf))) << (16 * k);
  }
  suspect = _mm_movemask_epi8(any) != 0;
  return bits;
#elif defined(TD_YAML_SCAN_NEON)
  const uint8x16_t lf = vdupq_n_u8('\n'), tab = vdupq_n_u8('\t'), cr = vdupq_n_u8('\r');
  const uint8x16_t c1f = vdupq_n_u8(0x1F), del = vdupq_n_u8(0x7F), c2 = vdupq_n_u8(0xC2), ef = vdupq_n_u8(0xEF);
  static const uint8_t weights[16] = {1, 2, 4, 8, 16, 32, 64, 128, 1, 2, 4, 8, 16, 32, 64, 128};
  const uint8x16_t weight = vld1q_u8(weights);
  uint8x16_t is_lf[4];
  uint8x16_t any = vdupq_n_u8(0);
  for (int k = 0; k < 4; ++k) {
    uint8x16_t v = vld1q_u8(p + 16 * k);
    is_lf[k] = vceqq_u8(v, lf);
    uint8x16_t space = vorrq_u8(is_lf[k], vorrq_u8(vceqq_u8(v, tab), vceqq_u8(v, cr)));
    uint8x16_t bad = vorrq_u8(vbicq_u8(vcleq_u8(v, c1f), space),
                              vorrq_u8(vceqq_u8(v, del), vorrq_u8(vceqq_u8(v, c2), vceqq_u8(v, ef))));
    any = vorrq_u8(any, bad);
  }
  uint8x16_t s0 = vpaddq_u8(vandq_u8(is_lf[0], weight), vandq_u8(is_lf[1], weight));
  uint8x16_t s1 = vpaddq_u8(vandq_u8(is_lf[2], weight), vandq_u8(is_lf[3], weight));
  s0 = vpaddq_u8(s0, s1);
  s0 = vpaddq_u8(s0, s0);
  suspect = vmaxvq_u8(any) != 0;
  return vgetq_lane_u64(vreinterpretq_u64_u8(s0), 0);
#else
  uint64_t bits = 0, flagged = 0;
  for (size_t i = 0; i < 64; i += 8) {
    uint64_t x;
    std::memcpy(&x, p + i, 8);
    uint64_t lf = equal(x, '\n');
    uint64_t ws = equal(x, '\t') | lf | equal(x, '\r');
    flagged |= (below_space(x) & ~ws) | equal(x, 0x7F) | equal(x, 0xC2) | equal(x, 0xEF);
    bits |= pack(lf) << i;
  }
  suspect = flagged != 0;
  return bits;
#endif
}

// The printable scan and LF bitmap over `text`, optionally copying it into `copy` block by block.
// UTF-8 is already validated. Exact rechecks read `text`, so a partial copy never matters.
inline void scan(const char* text, size_t n, char* copy, uint64_t* newlines) {
  const auto* p = reinterpret_cast<const unsigned char*>(text);
  size_t full = n / 64;
  for (size_t w = 0; w < full; ++w) {
    size_t base = w * 64;
    if (copy) std::memcpy(copy + base, text + base, 64);
    bool suspect;
    uint64_t bits = scan64(p + base, suspect);
    if (suspect)
      for (size_t k = base; k < base + 64; ++k) printable(p, k, n);
    if (newlines) newlines[w] = bits;
  }
  size_t base = full * 64;
  if (base < n) {
    // The tail, padded with spaces (printable, not LF).
    unsigned char block[64];
    std::memset(block, ' ', sizeof block);
    std::memcpy(block, p + base, n - base);
    if (copy) std::memcpy(copy + base, text + base, n - base);
    bool suspect;
    uint64_t bits = scan64(block, suspect);
    if (suspect)
      for (size_t k = base; k < n; ++k) printable(p, k, n);
    if (newlines) newlines[full] = bits;
  }
}
}  // namespace detail

// Validates strict UTF-8 and the c-printable set over `text`, and fills `newlines` (one bit per
// byte, (length + 63) / 64 words) with the positions of LF bytes. `newlines` may be null.
// 64 bytes at a time with SSE2 or NEON compares (SWAR elsewhere): blocks holding a C0 control
// other than TAB/LF/CR, DEL, 0xC2 (C1 controls) or 0xEF (U+FFFE/U+FFFF) are rechecked byte by byte.
// UTF-8 errors take precedence over printable errors, which are reported at the first such byte.
inline void validate(std::string_view text, uint64_t* newlines) {
  simdutf::result r = simdutf::validate_utf8_with_errors(text.data(), text.size());
  if (r.error != simdutf::error_code::SUCCESS) throw syntax_error("invalid UTF-8 in YAML input", r.count);
  detail::scan(text.data(), text.size(), nullptr, newlines);
}

// validate() of UTF-8 `text` fused with its copy into `out` (the session buffer, capacity >=
// text.size()): after simdutf's check, one pass reads, copies and scans each block. On a
// validation error `out` holds only a prefix of the text.
inline void validate_copy(std::string_view text, char* out, uint64_t* newlines) {
  simdutf::result r = simdutf::validate_utf8_with_errors(text.data(), text.size());
  if (r.error != simdutf::error_code::SUCCESS) throw syntax_error("invalid UTF-8 in YAML input", r.count);
  detail::scan(text.data(), text.size(), out, newlines);
}

// True when bytes [from, to) hold an LF, by the bitmap from validate().
inline bool has_newline(const uint64_t* newlines, size_t from, size_t to) {
  if (from >= to) return false;
  size_t first = from / 64, last = (to - 1) / 64;
  uint64_t head = newlines[first] & (~uint64_t(0) << (from % 64));
  uint64_t tail_mask = ~uint64_t(0) >> (63 - (to - 1) % 64);
  if (first == last) return (head & tail_mask) != 0;
  if (head) return true;
  for (size_t w = first + 1; w < last; ++w)
    if (newlines[w]) return true;
  return (newlines[last] & tail_mask) != 0;
}

// Number of LF bytes in [from, to).
inline size_t count_newlines(const uint64_t* newlines, size_t from, size_t to) {
  size_t count = 0;
  while (from < to) {
    size_t word = from / 64, bit = from % 64;
    size_t span = 64 - bit < to - from ? 64 - bit : to - from;
    uint64_t mask = span == 64 ? ~uint64_t(0) : ((uint64_t(1) << span) - 1) << bit;
    count += size_t(__builtin_popcountll(newlines[word] & mask));
    from += span;
  }
  return count;
}

}  // namespace truffledata::yaml
#endif
