// C ABI for the CSV structural indexer (csv/native/csv.seam). Nothing here allocates.
#include "csv/native/api.h"

#include "csv/native/index.h"
#include "csv/native/utf8.h"
#include "simdutf.h"

namespace {

using truffledata::csv::Format;
using truffledata::csv::Kernel;

// Packed results: count << 8 | carry, or one of these negative codes.
constexpr int64_t BAD_FORMAT = -1;
constexpr int64_t BAD_KERNEL = -2;
constexpr int64_t BAD_LENGTH = -3;
constexpr int64_t BAD_CARRY = -4;

int64_t index(const uint8_t* data, uint64_t length, int32_t* out, int32_t format, int32_t kernel,
              int64_t carry) {
  Format f{static_cast<uint8_t>(format), static_cast<uint8_t>(format >> 8),
           ((format >> 16) & 1) != 0};
  if ((static_cast<uint32_t>(format) >> 17) != 0 || !truffledata::csv::valid(f)) return BAD_FORMAT;
  if (kernel < 0 || !truffledata::csv::supported(static_cast<Kernel>(kernel))) return BAD_KERNEL;
  if (length > truffledata::csv::MAX_CALL_BYTES) return BAD_LENGTH;
  if ((static_cast<uint64_t>(carry) & ~truffledata::csv::CARRY_MASK) != 0) return BAD_CARRY;
  uint64_t c = static_cast<uint64_t>(carry);
  uint64_t count = truffledata::csv::index(static_cast<Kernel>(kernel), data, length,
                                           reinterpret_cast<uint32_t*>(out), f, &c);
  return static_cast<int64_t>(count << 8 | c);
}

}  // namespace

extern "C" uint8_t* td_csv_fingerprint() {
  static char fingerprint[] = TRUFFLEDATA_CSV_SEAM_ABI;
  return reinterpret_cast<uint8_t*>(fingerprint);
}

extern "C" int32_t td_csv_kernel(int32_t requested) {
  if (requested < 0 || !truffledata::csv::supported(static_cast<Kernel>(requested))) return -1;
  return requested == truffledata::csv::AUTO ? truffledata::csv::best_kernel() : requested;
}

extern "C" int64_t td_csv_index(const uint8_t* data, uint64_t length, int32_t* out, int32_t format,
                                int32_t kernel, int64_t carry) {
  return index(data, length, out, format, kernel, carry);
}

extern "C" int64_t td_csv_index_leaf(const uint8_t* data, uint64_t length, int32_t* out,
                                     int32_t format, int32_t kernel, int64_t carry) {
  return index(data, length, out, format, kernel, carry);
}

extern "C" int64_t td_csv_validate_utf8(const uint8_t* data, uint64_t length) {
  // simdutf decides validity; the exact offset of a rare failure comes from the scalar rules the
  // Java validator uses, so both report the same byte.
  if (simdutf::validate_utf8(reinterpret_cast<const char*>(data), length)) return -1;
  return truffledata::csv::utf8_invalid_offset(data, length);
}
