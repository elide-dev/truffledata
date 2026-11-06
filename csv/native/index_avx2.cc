// x86-64 AVX2 kernel, compiled with -mavx2 -mpclmul -mbmi -mbmi2 -mpopcnt -mlzcnt and
// dispatched only when the CPU reports those features.
#if defined(__x86_64__)
#include <immintrin.h>

#define TD_CSV_ISA avx2
#include "stage.h"

namespace truffledata::csv {
namespace avx2 {
namespace {

TD_CSV_INLINE uint64_t prefix_xor(uint64_t x) {
  __m128i r = _mm_clmulepi64_si128(_mm_set_epi64x(0, static_cast<long long>(x)),
                                   _mm_set1_epi8(static_cast<char>(0xFF)), 0);
  return static_cast<uint64_t>(_mm_cvtsi128_si64(r));
}

struct Masks {
  __m256i d, q, lf, cr;
  bool quoting;
  explicit Masks(Format f)
      : d(_mm256_set1_epi8(static_cast<char>(f.delimiter))),
        q(_mm256_set1_epi8(static_cast<char>(f.quote))),
        lf(_mm256_set1_epi8('\n')),
        cr(_mm256_set1_epi8('\r')),
        quoting(f.quoting) {}

  TD_CSV_INLINE uint64_t separators(__m256i v) const {
    __m256i m = _mm256_or_si256(
        _mm256_or_si256(_mm256_cmpeq_epi8(v, d), _mm256_cmpeq_epi8(v, lf)),
        _mm256_cmpeq_epi8(v, cr));
    return static_cast<uint32_t>(_mm256_movemask_epi8(m));
  }

  TD_CSV_INLINE void load(const uint8_t* p, uint64_t* sep, uint64_t* quote) const {
    __m256i a = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(p));
    __m256i b = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(p + 32));
    *sep = separators(a) | separators(b) << 32;
    if (quoting) {
      uint64_t qa = static_cast<uint32_t>(_mm256_movemask_epi8(_mm256_cmpeq_epi8(a, q)));
      uint64_t qb = static_cast<uint32_t>(_mm256_movemask_epi8(_mm256_cmpeq_epi8(b, q)));
      *quote = qa | qb << 32;
    } else {
      *quote = 0;
    }
  }
};

}  // namespace
}  // namespace avx2

uint64_t index_avx2(const uint8_t* data, uint64_t length, uint32_t* out, Format format,
                    uint64_t* carry) {
  return avx2::run<avx2::Masks, avx2::prefix_xor>(data, length, out, format, carry);
}

}  // namespace truffledata::csv
#endif
