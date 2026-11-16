// C ABI for the CSV structural indexer (csv/native/csv.seam). Nothing here allocates.
#include "csv/native/api.h"

#include "csv/native/index.h"
#include "csv/native/utf8.h"
#include "simdutf.h"

namespace {

using truffledata::csv::Format;
using truffledata::csv::Kernel;

// index.h is the kernels' own vocabulary; csv.seam (seam.h) is the ABI's. They must agree.
static_assert(truffledata::csv::AUTO == TRUFFLEDATA_CSV_KERNEL_AUTO, "Kernel");
static_assert(truffledata::csv::SCALAR == TRUFFLEDATA_CSV_KERNEL_SCALAR, "Kernel");
static_assert(truffledata::csv::SWAR == TRUFFLEDATA_CSV_KERNEL_SWAR, "Kernel");
static_assert(truffledata::csv::NEON == TRUFFLEDATA_CSV_KERNEL_NEON, "Kernel");
static_assert(truffledata::csv::AVX2 == TRUFFLEDATA_CSV_KERNEL_AVX2, "Kernel");
static_assert(truffledata::csv::AVX512 == TRUFFLEDATA_CSV_KERNEL_AVX512, "Kernel");
static_assert(truffledata::csv::FLAG_QUOTED == TRUFFLEDATA_CSV_ENTRY_QUOTED, "Entry");
static_assert(truffledata::csv::FLAG_ESCAPED == TRUFFLEDATA_CSV_ENTRY_ESCAPED, "Entry");
static_assert(truffledata::csv::FLAG_UNQUOTED == TRUFFLEDATA_CSV_ENTRY_UNQUOTED, "Entry");
static_assert(truffledata::csv::OFFSET_MASK == TRUFFLEDATA_CSV_ENTRY_OFFSET_MASK, "Entry");
static_assert(truffledata::csv::MAX_CALL_BYTES == TRUFFLEDATA_CSV_MAX_CALL_BYTES, "MAX_CALL_BYTES");
static_assert(truffledata::csv::CARRY_INSIDE == TRUFFLEDATA_CSV_CARRY_INSIDE, "Carry");
static_assert(truffledata::csv::CARRY_CLOSING == TRUFFLEDATA_CSV_CARRY_CLOSING, "Carry");
static_assert(truffledata::csv::CARRY_Q == TRUFFLEDATA_CSV_CARRY_QUOTED, "Carry");
static_assert(truffledata::csv::CARRY_E == TRUFFLEDATA_CSV_CARRY_ESCAPED, "Carry");
static_assert(truffledata::csv::CARRY_U == TRUFFLEDATA_CSV_CARRY_UNQUOTED, "Carry");
static_assert(truffledata::csv::CARRY_MASK == static_cast<uint64_t>(TRUFFLEDATA_CSV_CARRY_ALL),
              "Carry");

int64_t index(const uint8_t* data, uint64_t length, int32_t* out, int32_t format, int32_t kernel,
              int64_t carry) {
  Format f{static_cast<uint8_t>(truffledata_csv_format_delimiter(format)),
           static_cast<uint8_t>(truffledata_csv_format_quote(format)),
           (format & TRUFFLEDATA_CSV_FORMAT_QUOTING) != 0};
  if ((format & ~TRUFFLEDATA_CSV_FORMAT_ALL) != 0 || !truffledata::csv::valid(f))
    return TRUFFLEDATA_CSV_INDEX_BAD_FORMAT;
  if (kernel < 0 || !truffledata::csv::supported(static_cast<Kernel>(kernel)))
    return TRUFFLEDATA_CSV_INDEX_BAD_KERNEL;
  if (length > TRUFFLEDATA_CSV_MAX_CALL_BYTES) return TRUFFLEDATA_CSV_INDEX_BAD_LENGTH;
  if ((carry & ~TRUFFLEDATA_CSV_CARRY_ALL) != 0) return TRUFFLEDATA_CSV_INDEX_BAD_CARRY;
  uint64_t c = static_cast<uint64_t>(carry);
  uint64_t count = truffledata::csv::index(static_cast<Kernel>(kernel), data, length,
                                           reinterpret_cast<uint32_t*>(out), f, &c);
  return static_cast<int64_t>(count << TRUFFLEDATA_CSV_INDEX_COUNT_SHIFT | c);
}

}  // namespace

extern "C" uint8_t* td_csv_fingerprint() {
  static char fingerprint[] = TRUFFLEDATA_CSV_SEAM_ABI;
  return reinterpret_cast<uint8_t*>(fingerprint);
}

extern "C" int32_t td_csv_kernel(int32_t requested) {
  if (requested < 0 || !truffledata::csv::supported(static_cast<Kernel>(requested)))
    return TRUFFLEDATA_CSV_KERNEL_UNSUPPORTED;
  return requested == TRUFFLEDATA_CSV_KERNEL_AUTO ? truffledata::csv::best_kernel() : requested;
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
  if (simdutf::validate_utf8(reinterpret_cast<const char*>(data), length))
    return TRUFFLEDATA_CSV_UTF8_VALID;
  return truffledata::csv::utf8_invalid_offset(data, length);
}
