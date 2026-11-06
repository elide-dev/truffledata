// AArch64 NEON kernel: four 16-byte comparisons per block, PMULL prefix XOR.
#if defined(__aarch64__)
#include <arm_neon.h>

#define TD_CSV_ISA neon
#include "stage.h"

namespace truffledata::csv {
namespace neon {
namespace {

TD_CSV_INLINE uint64_t prefix_xor(uint64_t x) {
#if defined(__ARM_FEATURE_AES) || defined(__ARM_FEATURE_CRYPTO)
  return static_cast<uint64_t>(vmull_p64(x, ~uint64_t{0}));
#else
  return prefix_xor_shift(x);
#endif
}

struct Masks {
  uint8x16_t d, q, lf, cr, weights;
  bool quoting;
  explicit Masks(Format f)
      : d(vdupq_n_u8(f.delimiter)), q(vdupq_n_u8(f.quote)), lf(vdupq_n_u8('\n')),
        cr(vdupq_n_u8('\r')), quoting(f.quoting) {
    static const uint8_t w[16] = {1, 2, 4, 8, 16, 32, 64, 128, 1, 2, 4, 8, 16, 32, 64, 128};
    weights = vld1q_u8(w);
  }

  /** simdjson's NEON movemask over four 16-byte comparison results. */
  TD_CSV_INLINE uint64_t bits(uint8x16_t a, uint8x16_t b, uint8x16_t c, uint8x16_t e) const {
    uint8x16_t s0 = vpaddq_u8(vandq_u8(a, weights), vandq_u8(b, weights));
    uint8x16_t s1 = vpaddq_u8(vandq_u8(c, weights), vandq_u8(e, weights));
    s0 = vpaddq_u8(s0, s1);
    s0 = vpaddq_u8(s0, s0);
    return vgetq_lane_u64(vreinterpretq_u64_u8(s0), 0);
  }

  TD_CSV_INLINE uint8x16_t separators(uint8x16_t v) const {
    return vorrq_u8(vorrq_u8(vceqq_u8(v, d), vceqq_u8(v, lf)), vceqq_u8(v, cr));
  }

  TD_CSV_INLINE void load(const uint8_t* p, uint64_t* sep, uint64_t* quote) const {
    uint8x16_t v0 = vld1q_u8(p), v1 = vld1q_u8(p + 16), v2 = vld1q_u8(p + 32),
               v3 = vld1q_u8(p + 48);
    *sep = bits(separators(v0), separators(v1), separators(v2), separators(v3));
    *quote = quoting ? bits(vceqq_u8(v0, q), vceqq_u8(v1, q), vceqq_u8(v2, q), vceqq_u8(v3, q))
                     : 0;
  }
};

}  // namespace
}  // namespace neon

uint64_t index_neon(const uint8_t* data, uint64_t length, uint32_t* out, Format format,
                    uint64_t* carry) {
  return neon::run<neon::Masks, neon::prefix_xor>(data, length, out, format, carry);
}

}  // namespace truffledata::csv
#endif
