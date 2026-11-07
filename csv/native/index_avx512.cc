// x86-64 AVX-512BW kernel: one 64-byte comparison per mask. Compiled with -mavx512f -mavx512bw
// plus the AVX2 flags, and dispatched only when the CPU reports those features.
#if defined(__x86_64__)
#include <immintrin.h>

#define TD_CSV_ISA avx512
#include "stage.h"

namespace truffledata::csv {
namespace avx512 {
namespace {

TD_CSV_INLINE uint64_t prefix_xor(uint64_t x) {
  __m128i r = _mm_clmulepi64_si128(_mm_set_epi64x(0, static_cast<long long>(x)),
                                   _mm_set1_epi8(static_cast<char>(0xFF)), 0);
  return static_cast<uint64_t>(_mm_cvtsi128_si64(r));
}

struct Masks {
  __m512i d, q, lf, cr;
  bool quoting;
  explicit Masks(Format f)
      : d(_mm512_set1_epi8(static_cast<char>(f.delimiter))),
        q(_mm512_set1_epi8(static_cast<char>(f.quote))),
        lf(_mm512_set1_epi8('\n')),
        cr(_mm512_set1_epi8('\r')),
        quoting(f.quoting) {}

  TD_CSV_INLINE void load(const uint8_t* p, uint64_t* sep, uint64_t* quote) const {
    __m512i v = _mm512_loadu_si512(p);
    *sep = _mm512_cmpeq_epi8_mask(v, d) | _mm512_cmpeq_epi8_mask(v, lf) |
           _mm512_cmpeq_epi8_mask(v, cr);
    *quote = quoting ? _mm512_cmpeq_epi8_mask(v, q) : 0;
  }
};

}  // namespace
}  // namespace avx512

uint64_t index_avx512(const uint8_t* data, uint64_t length, uint32_t* out, Format format,
                      uint64_t* carry) {
  return avx512::run<avx512::Masks, avx512::prefix_xor>(data, length, out, format, carry);
}

}  // namespace truffledata::csv
#endif
