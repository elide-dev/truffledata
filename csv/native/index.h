// CSV structural indexer: simdjson stage-1 style quote masks and separator bitmaps.
//
// Contract (identical for every kernel, native and Java; csv/native/README.md has the full text):
//
// For the byte stream formed by concatenating every call's input, with delimiter d, quote q
// (when quoting) and the record separators LF and CR:
//   inside[i]   = XOR of quote[0..i] (inclusive): 1 from an opening quote up to the byte before
//                 its closing quote.
//   separator   = a delimiter, LF or CR byte with inside[i] == 0.
//   reopen[i]   = quote[i] && quote[i-1] && !inside[i-1]: an opening quote right after a
//                 closing one, i.e. the second byte of a doubled quote.
//   unquoted[i] = !inside[i] && !quote[i] && byte i is not a separator.
// A field is the byte range between consecutive separators (or the stream start). Each
// separator emits one 32-bit entry: its offset relative to the call's first byte, plus flags
// describing the field that ends at it:
//   Q (bit 31) the field contains a quote byte,
//   E (bit 30) the field contains a reopen (escaped content when the field is well formed),
//   X (bit 29) Q and the field contains an unquoted byte (malformed, or blanks when trimming).
// Bits 0..28 hold the offset, so one call indexes at most 2^29 - 1 bytes.
//
// The carry word connects calls: bit 0 inside quotes after the last byte, bit 1 the last byte
// was a closing quote, bits 2..4 the pending Q, E and unquoted flags of the trailing field.
// A call indexes indexable(length, capacity) bytes, so output never exceeds capacity and every
// kernel consumes the same prefix. Indexing allocates nothing.
#ifndef TRUFFLEDATA_CSV_INDEX_H
#define TRUFFLEDATA_CSV_INDEX_H

#include <cstdint>

namespace truffledata::csv {

constexpr uint32_t FLAG_QUOTED = 1u << 31;
constexpr uint32_t FLAG_ESCAPED = 1u << 30;
constexpr uint32_t FLAG_UNQUOTED = 1u << 29;
constexpr uint32_t OFFSET_MASK = (1u << 29) - 1;
constexpr uint64_t MAX_CALL_BYTES = OFFSET_MASK;

constexpr uint64_t CARRY_INSIDE = 1;
constexpr uint64_t CARRY_CLOSING = 2;
constexpr uint64_t CARRY_Q = 4;
constexpr uint64_t CARRY_E = 8;
constexpr uint64_t CARRY_U = 16;
constexpr uint64_t CARRY_MASK = 31;

enum Kernel : uint32_t {
  AUTO = 0,
  SCALAR = 1,
  SWAR = 2,
  NEON = 3,
  AVX2 = 4,
  AVX512 = 5,
};

struct Format {
  uint8_t delimiter;
  uint8_t quote;
  bool quoting;
};

/** Bytes one call indexes: all of them when capacity allows, else whole 64-byte blocks. */
inline uint64_t indexable(uint64_t length, uint64_t capacity) {
  return capacity >= length ? length : capacity & ~uint64_t{63};
}

/** Whether a format can be indexed: separators are distinct from the quote. */
inline bool valid(Format format) {
  if (format.delimiter == '\n' || format.delimiter == '\r') return false;
  if (!format.quoting) return true;
  return format.quote != format.delimiter && format.quote != '\n' && format.quote != '\r';
}

/** Byte-at-a-time reference: the oracle for every other kernel. */
inline uint64_t index_scalar(const uint8_t* data, uint64_t length, uint32_t* out, Format format,
                             uint64_t* carry) {
  uint64_t c = *carry;
  bool inside = c & CARRY_INSIDE, closing = c & CARRY_CLOSING;
  bool fq = c & CARRY_Q, fe = c & CARRY_E, fu = c & CARRY_U;
  uint64_t count = 0;
  for (uint64_t i = 0; i < length; i++) {
    uint8_t b = data[i];
    if (format.quoting && b == format.quote) {
      inside = !inside;
      if (inside && closing) fe = true;
      closing = !inside;
      fq = true;
      continue;
    }
    closing = false;
    if (inside) continue;
    if (b == format.delimiter || b == '\n' || b == '\r') {
      uint32_t flags = (fq ? FLAG_QUOTED : 0) | (fe ? FLAG_ESCAPED : 0) |
                       (fq && fu ? FLAG_UNQUOTED : 0);
      out[count++] = static_cast<uint32_t>(i) | flags;
      fq = fe = fu = false;
    } else {
      fu = true;
    }
  }
  *carry = (inside ? CARRY_INSIDE : 0) | (closing ? CARRY_CLOSING : 0) | (fq ? CARRY_Q : 0) |
           (fe ? CARRY_E : 0) | (fu ? CARRY_U : 0);
  return count;
}

// Per-ISA kernels, each compiled in its own translation unit with its own instruction set.
uint64_t index_swar(const uint8_t* data, uint64_t length, uint32_t* out, Format format,
                    uint64_t* carry);
#if defined(__aarch64__)
uint64_t index_neon(const uint8_t* data, uint64_t length, uint32_t* out, Format format,
                    uint64_t* carry);
#endif
#if defined(__x86_64__)
uint64_t index_avx2(const uint8_t* data, uint64_t length, uint32_t* out, Format format,
                    uint64_t* carry);
uint64_t index_avx512(const uint8_t* data, uint64_t length, uint32_t* out, Format format,
                      uint64_t* carry);
#endif

/** The fastest kernel this build and CPU support (runtime dispatch on x86-64). */
Kernel best_kernel();

/** Whether this build and CPU can run `kernel`; AUTO is always supported. */
bool supported(Kernel kernel);

/**
 * Indexes exactly `length` bytes (callers apply indexable()) with a supported kernel, AUTO
 * meaning best_kernel(). `out` must hold `length` entries. Returns the entry count.
 */
uint64_t index(Kernel kernel, const uint8_t* data, uint64_t length, uint32_t* out, Format format,
               uint64_t* carry);

}  // namespace truffledata::csv

#endif
