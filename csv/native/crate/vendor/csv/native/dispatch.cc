// Runtime kernel selection.
#include "index.h"

namespace truffledata::csv {

Kernel best_kernel() {
#if defined(__x86_64__)
  static const Kernel best = [] {
    __builtin_cpu_init();
    bool avx2 = __builtin_cpu_supports("avx2") && __builtin_cpu_supports("pclmul") &&
                __builtin_cpu_supports("bmi") && __builtin_cpu_supports("bmi2") &&
                __builtin_cpu_supports("popcnt") && __builtin_cpu_supports("lzcnt");
    if (avx2 && __builtin_cpu_supports("avx512f") && __builtin_cpu_supports("avx512bw"))
      return AVX512;
    return avx2 ? AVX2 : SWAR;
  }();
  return best;
#elif defined(__aarch64__)
  return NEON;
#else
  return SWAR;
#endif
}

bool supported(Kernel kernel) {
  switch (kernel) {
    case AUTO:
    case SCALAR:
    case SWAR:
      return true;
#if defined(__aarch64__)
    case NEON:
      return true;
#endif
#if defined(__x86_64__)
    case AVX2:
      return best_kernel() == AVX2 || best_kernel() == AVX512;
    case AVX512:
      return best_kernel() == AVX512;
#endif
    default:
      return false;
  }
}

uint64_t index(Kernel kernel, const uint8_t* data, uint64_t length, uint32_t* out, Format format,
               uint64_t* carry) {
  if (kernel == AUTO) kernel = best_kernel();
  switch (kernel) {
#if defined(__x86_64__)
    case AVX512:
      return index_avx512(data, length, out, format, carry);
    case AVX2:
      return index_avx2(data, length, out, format, carry);
#endif
#if defined(__aarch64__)
    case NEON:
      return index_neon(data, length, out, format, carry);
#endif
    case SWAR:
      return index_swar(data, length, out, format, carry);
    default:
      return index_scalar(data, length, out, format, carry);
  }
}

}  // namespace truffledata::csv
