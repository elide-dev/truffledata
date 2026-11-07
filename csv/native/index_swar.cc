// Portable kernel: 64-bit SWAR byte comparisons (the same arithmetic as the Java SWAR kernel).
#define TD_CSV_ISA swar
#include "stage.h"

namespace truffledata::csv {
namespace swar {
namespace {

struct Masks {
  uint64_t d, q;
  bool quoting;
  explicit Masks(Format f)
      : d(0x0101010101010101ULL * f.delimiter), q(0x0101010101010101ULL * f.quote),
        quoting(f.quoting) {}

  /** One bit per byte of `x` equal to the byte broadcast in `pattern`, byte 0 lowest. */
  static TD_CSV_INLINE uint64_t eq(uint64_t x, uint64_t pattern) {
    uint64_t t = x ^ pattern;
    uint64_t z =
        ~(((t & 0x7F7F7F7F7F7F7F7FULL) + 0x7F7F7F7F7F7F7F7FULL) | t) & 0x8080808080808080ULL;
    return (z >> 7) * 0x0102040810204080ULL >> 56;
  }

  TD_CSV_INLINE void load(const uint8_t* p, uint64_t* sep, uint64_t* quote) const {
    uint64_t s = 0, qq = 0;
    for (int k = 0; k < 8; k++) {
      uint64_t x;
      std::memcpy(&x, p + 8 * k, 8);
      s |= (eq(x, d) | eq(x, 0x0A0A0A0A0A0A0A0AULL) | eq(x, 0x0D0D0D0D0D0D0D0DULL)) << (8 * k);
      if (quoting) qq |= eq(x, q) << (8 * k);
    }
    *sep = s;
    *quote = qq;
  }
};

}  // namespace
}  // namespace swar

uint64_t index_swar(const uint8_t* data, uint64_t length, uint32_t* out, Format format,
                    uint64_t* carry) {
  return swar::run<swar::Masks, swar::prefix_xor_shift>(data, length, out, format, carry);
}

}  // namespace truffledata::csv
