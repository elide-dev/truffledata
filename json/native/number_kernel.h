#ifndef TRUFFLEDATA_JSON_NUMBER_KERNEL_H
#define TRUFFLEDATA_JSON_NUMBER_KERNEL_H
// Runtime-dispatched instantiations of simdjson 5.1.1's generic number parser (number_kernel.cc).
//
// Under -march=compatibility, simdjson::builtin is the fallback implementation, so stage2's
// inline parse_number uses the SWAR eight-digit step. number_kernel.cc compiles the same
// generic/numberparsing.h under the westmere (SSE4.2) and haswell (AVX2) target regions with
// those kernels' eight-digit step, leading-zero count and multiplication. The tier follows the
// implementation simdjson selected for stage 1 (get_active_implementation, which also honors
// SIMDJSON_FORCE_IMPLEMENTATION), so one CPU check governs both. Token rules are the pinned
// parser's: the kernels differ only in how they compute the same value.
#include <simdjson.h>
#include <atomic>
#include <cstdint>
#include <cstring>

namespace truffledata::json::number_kernel {

enum class tier : uint8_t { builtin, westmere, haswell };

// One parse_number call's writer events and status, returned in two registers. The parser emits
// at most one value; some paths emit it before reporting an error (the caller rolls it back).
struct result {
  uint64_t bits;    // the emitted value: int64/uint64 bits, or the double's bits
  uint32_t kind;    // 0: nothing emitted, 1: append_s64, 2: append_u64, 3: append_double
  uint32_t error;   // simdjson::error_code
};
enum : uint32_t { emitted_none = 0, emitted_s64 = 1, emitted_u64 = 2, emitted_double = 3 };

#if defined(__x86_64__)
// Both require the CPU features of simdjson's westmere/haswell kernels; call only for that tier.
result parse_westmere(const uint8_t* src) noexcept;
result parse_haswell(const uint8_t* src) noexcept;
tier detect(const simdjson::implementation* active) noexcept;
#endif

// The process-wide tier, detected once from simdjson's active implementation. Tests may store
// any tier the CPU supports (and `unknown` to re-detect) to run whole decodes through a kernel.
inline constexpr uint8_t unknown = 0xff;
inline std::atomic<uint8_t> selected{unknown};

inline tier active() noexcept {
#if defined(__x86_64__)
  uint8_t current = selected.load(std::memory_order_relaxed);
  if (__builtin_expect(current != unknown, 1)) return tier(current);
  tier detected = detect(simdjson::get_active_implementation());
  selected.store(uint8_t(detected), std::memory_order_relaxed);
  return detected;
#else
  return tier::builtin;
#endif
}

// Replays one kernel result into a simdjson number writer (append_s64/append_u64/append_double).
template<class W> inline simdjson::error_code replay(result parsed, W& writer) {
  switch (parsed.kind) {
    case emitted_s64: writer.append_s64(int64_t(parsed.bits)); break;
    case emitted_u64: writer.append_u64(parsed.bits); break;
    case emitted_double: {
      double value;
      std::memcpy(&value, &parsed.bits, sizeof value);
      writer.append_double(value);
      break;
    }
    default: break;
  }
  return simdjson::error_code(parsed.error);
}

// parse_number through the given tier, with exactly the builtin parser's writer events.
template<class W> [[gnu::always_inline]] inline simdjson::error_code parse_number(
    tier kernel, const uint8_t* src, W& writer) {
#if defined(__x86_64__)
  if (kernel == tier::haswell) return replay(parse_haswell(src), writer);
  if (kernel == tier::westmere) return replay(parse_westmere(src), writer);
#else
  (void)kernel;
#endif
  return simdjson::builtin::numberparsing::parse_number(src, writer);
}

} // namespace truffledata::json::number_kernel
#endif
