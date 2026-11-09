#include <simdutf.h>
#include <cstddef>
#include <cstdint>
#include <limits>

// The input and output stay borrowed for the call. Capacity is measured in UTF-16 code units.
extern "C" int64_t td_tape_utf8_to_utf16(const uint8_t* input, uint64_t length, uint16_t* output, uint64_t capacity) {
  if ((!input && length) || (!output && capacity) || length > std::numeric_limits<size_t>::max()) return -1;
  if (!length) return 0;
  const auto* bytes = reinterpret_cast<const char*>(input);
  if (!simdutf::validate_utf8(bytes, size_t(length))) return -1;
  size_t needed = simdutf::utf16_length_from_utf8(bytes, size_t(length));
  if (needed > capacity || needed > uint64_t(INT64_MAX)) return -1;
  return int64_t(simdutf::convert_valid_utf8_to_utf16le(bytes, size_t(length), reinterpret_cast<char16_t*>(output)));
}
