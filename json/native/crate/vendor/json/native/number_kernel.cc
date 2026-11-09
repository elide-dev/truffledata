// SSE4.2 (westmere) and AVX2 (haswell) instantiations of simdjson's generic number parser.
//
// simdjson 5.1.1 (https://github.com/simdjson/simdjson, Apache-2.0 OR MIT; Copyright 2018-2025
// The simdjson authors). The parser itself is simdjson's include/simdjson/generic/
// {base,jsoncharutils,atomparsing,numberparsing}.h from the pinned release, included unmodified
// (Bazel: @simdjson//:generic_numberparsing; Cargo: vendor/simdjson/simdjson_generic/). The
// per-kernel definitions below (parse_eight_digits_unrolled, full_multiplication,
// leading_zeroes) are copied from include/simdjson/{westmere,haswell}/{numberparsing_defs,
// bitmanipulation}.h with only the namespace changed, under the same license.
//
// Each kernel lives in its own namespace (simdjson::td_num_westmere, simdjson::td_num_haswell),
// never simdjson::westmere/haswell, so no inline definition here can collide (ODR) with the
// kernels compiled into simdjson.cpp. Each is compiled inside simdjson's own target region for
// that kernel; callers dispatch through number_kernel::active() (simdjson's CPU detection).
#include "json/native/number_kernel.h"

#if defined(__x86_64__)
#include <x86intrin.h>
#include <cstring>
#include <string>

namespace truffledata::json::number_kernel {
namespace {
// The parser's writer interface, recorded for number_kernel::replay.
struct recorder {
  result out{0, emitted_none, 0};
  void append_s64(int64_t value) noexcept { out.bits = uint64_t(value); out.kind = emitted_s64; }
  void append_u64(uint64_t value) noexcept { out.bits = value; out.kind = emitted_u64; }
  void append_double(double value) noexcept {
    std::memcpy(&out.bits, &value, sizeof value);
    out.kind = emitted_double;
  }
};
} // namespace
} // namespace truffledata::json::number_kernel

// The amalgamated simdjson.h undefines it after its own builtin kernel; the generic headers then
// skip their (non-amalgamated) include lines and expand once per SIMDJSON_IMPLEMENTATION.
#define SIMDJSON_CONDITIONAL_INCLUDE

// ---- westmere: simdjson/westmere/begin.h's target region ------------------------------------
#define SIMDJSON_IMPLEMENTATION td_num_westmere
SIMDJSON_TARGET_REGION("sse4.2,pclmul,popcnt")
namespace simdjson {
namespace td_num_westmere {
namespace {
// simdjson/westmere/bitmanipulation.h
/* result might be undefined when input_num is zero */
simdjson_inline int leading_zeroes(uint64_t input_num) {
  return __builtin_clzll(input_num);
}
} // unnamed namespace
namespace numberparsing {
// simdjson/westmere/numberparsing_defs.h
/** @private */
static simdjson_inline uint32_t parse_eight_digits_unrolled(const uint8_t *chars) {
  // this actually computes *16* values so we are being wasteful.
  const __m128i ascii0 = _mm_set1_epi8('0');
  const __m128i mul_1_10 =
      _mm_setr_epi8(10, 1, 10, 1, 10, 1, 10, 1, 10, 1, 10, 1, 10, 1, 10, 1);
  const __m128i mul_1_100 = _mm_setr_epi16(100, 1, 100, 1, 100, 1, 100, 1);
  const __m128i mul_1_10000 =
      _mm_setr_epi16(10000, 1, 10000, 1, 10000, 1, 10000, 1);
  const __m128i input = _mm_sub_epi8(
      _mm_loadu_si128(reinterpret_cast<const __m128i *>(chars)), ascii0);
  const __m128i t1 = _mm_maddubs_epi16(input, mul_1_10);
  const __m128i t2 = _mm_madd_epi16(t1, mul_1_100);
  const __m128i t3 = _mm_packus_epi32(t2, t2);
  const __m128i t4 = _mm_madd_epi16(t3, mul_1_10000);
  return _mm_cvtsi128_si32(
      t4); // only captures the sum of the first 8 digits, drop the rest
}
/** @private */
simdjson_inline internal::value128 full_multiplication(uint64_t value1, uint64_t value2) {
  internal::value128 answer;
  __uint128_t r = (static_cast<__uint128_t>(value1)) * value2;
  answer.low = uint64_t(r);
  answer.high = uint64_t(r >> 64);
  return answer;
}
} // namespace numberparsing
} // namespace td_num_westmere
} // namespace simdjson
#include "simdjson_generic/base.h"
#include "simdjson_generic/jsoncharutils.h"
#include "simdjson_generic/atomparsing.h"
#include "simdjson_generic/numberparsing.h"
namespace truffledata::json::number_kernel {
result parse_westmere(const uint8_t* src) noexcept {
  recorder writer;
  writer.out.error = uint32_t(simdjson::td_num_westmere::numberparsing::parse_number(src, writer));
  return writer.out;
}
} // namespace truffledata::json::number_kernel
SIMDJSON_UNTARGET_REGION
#undef SIMDJSON_IMPLEMENTATION

// ---- haswell: simdjson/haswell/begin.h's target region (clang) ------------------------------
#define SIMDJSON_IMPLEMENTATION td_num_haswell
SIMDJSON_TARGET_REGION("avx2,bmi,bmi2,pclmul,lzcnt,popcnt")
namespace simdjson {
namespace td_num_haswell {
namespace {
// simdjson/haswell/bitmanipulation.h
/* result might be undefined when input_num is zero */
simdjson_inline int leading_zeroes(uint64_t input_num) {
  return int(_lzcnt_u64(input_num));
}
} // unnamed namespace
namespace numberparsing {
// simdjson/haswell/numberparsing_defs.h
/** @private */
static simdjson_inline uint32_t parse_eight_digits_unrolled(const uint8_t *chars) {
  // this actually computes *16* values so we are being wasteful.
  const __m128i ascii0 = _mm_set1_epi8('0');
  const __m128i mul_1_10 =
      _mm_setr_epi8(10, 1, 10, 1, 10, 1, 10, 1, 10, 1, 10, 1, 10, 1, 10, 1);
  const __m128i mul_1_100 = _mm_setr_epi16(100, 1, 100, 1, 100, 1, 100, 1);
  const __m128i mul_1_10000 =
      _mm_setr_epi16(10000, 1, 10000, 1, 10000, 1, 10000, 1);
  const __m128i input = _mm_sub_epi8(
      _mm_loadu_si128(reinterpret_cast<const __m128i *>(chars)), ascii0);
  const __m128i t1 = _mm_maddubs_epi16(input, mul_1_10);
  const __m128i t2 = _mm_madd_epi16(t1, mul_1_100);
  const __m128i t3 = _mm_packus_epi32(t2, t2);
  const __m128i t4 = _mm_madd_epi16(t3, mul_1_10000);
  return _mm_cvtsi128_si32(
      t4); // only captures the sum of the first 8 digits, drop the rest
}
/** @private */
simdjson_inline internal::value128 full_multiplication(uint64_t value1, uint64_t value2) {
  internal::value128 answer;
  __uint128_t r = (static_cast<__uint128_t>(value1)) * value2;
  answer.low = uint64_t(r);
  answer.high = uint64_t(r >> 64);
  return answer;
}
} // namespace numberparsing
} // namespace td_num_haswell
} // namespace simdjson
#include "simdjson_generic/base.h"
#include "simdjson_generic/jsoncharutils.h"
#include "simdjson_generic/atomparsing.h"
#include "simdjson_generic/numberparsing.h"
namespace truffledata::json::number_kernel {
result parse_haswell(const uint8_t* src) noexcept {
  recorder writer;
  writer.out.error = uint32_t(simdjson::td_num_haswell::numberparsing::parse_number(src, writer));
  return writer.out;
}
} // namespace truffledata::json::number_kernel
SIMDJSON_UNTARGET_REGION
#undef SIMDJSON_IMPLEMENTATION

#undef SIMDJSON_CONDITIONAL_INCLUDE

namespace truffledata::json::number_kernel {
tier detect(const simdjson::implementation* active) noexcept {
  // simdjson's own CPU check chose this kernel for stage 1; icelake implies haswell's features.
  std::string name = active->name();
  if (name == "haswell" || name == "icelake") return tier::haswell;
  if (name == "westmere") return tier::westmere;
  return tier::builtin;
}
} // namespace truffledata::json::number_kernel
#endif
