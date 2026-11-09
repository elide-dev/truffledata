// Strict scalar UTF-8 validation with the exact rules and offsets of the Java validator
// (dev.elide.truffledata.csv.Utf8): no overlongs, surrogates, values above U+10FFFF or truncation.
#ifndef TRUFFLEDATA_CSV_UTF8_H
#define TRUFFLEDATA_CSV_UTF8_H

#include <cstdint>

namespace truffledata::csv {

/** -1 when [0, length) is strict UTF-8, else the offset of the first invalid sequence. */
inline int64_t utf8_invalid_offset(const uint8_t* bytes, uint64_t length) {
  uint64_t i = 0;
  while (i < length) {
    uint32_t lead = bytes[i];
    if (lead < 0x80) {
      i++;
      continue;
    }
    uint32_t trailing, low = 0x80, high = 0xBF;
    if (lead >= 0xC2 && lead <= 0xDF) {
      trailing = 1;
    } else if (lead >= 0xE0 && lead <= 0xEF) {
      trailing = 2;
      if (lead == 0xE0) low = 0xA0;
      else if (lead == 0xED) high = 0x9F;
    } else if (lead >= 0xF0 && lead <= 0xF4) {
      trailing = 3;
      if (lead == 0xF0) low = 0x90;
      else if (lead == 0xF4) high = 0x8F;
    } else {
      return static_cast<int64_t>(i);
    }
    if (i + trailing >= length) return static_cast<int64_t>(i);
    uint32_t second = bytes[i + 1];
    if (second < low || second > high) return static_cast<int64_t>(i);
    for (uint32_t k = 2; k <= trailing; k++)
      if ((bytes[i + k] & 0xC0) != 0x80) return static_cast<int64_t>(i);
    i += trailing + 1;
  }
  return -1;
}

}  // namespace truffledata::csv

#endif
