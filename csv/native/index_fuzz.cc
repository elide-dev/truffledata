#include "csv/native/index.h"
#include <algorithm>
#include <cstdlib>
#include <vector>
using namespace truffledata::csv;
extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  if (size < 5 || size > 65536) return 0;
  Format format{data[0], data[1], (data[2] & 1) != 0};
  if (!valid(format)) return 0;
  const size_t cut = data[3];
  const size_t second_cut = data[4];
  data += 5; size -= 5;
  std::vector<uint32_t> expected(size + 1), actual(size + 1);
  for (Kernel kernel : {SWAR, NEON, AVX2, AVX512, AUTO}) {
    if (!supported(kernel)) continue;
    uint64_t scalar_carry = 0, carry = 0;
    // Carry must arise from preceding bytes under the same format, starting at zero.
    // Cuts can end inside quotes or SIMD blocks, and can produce an empty call.
    for (size_t begin = 0, pass = 0; pass < 3; ++pass) {
      size_t length = pass == 0 ? std::min(cut, size)
          : pass == 1 ? std::min(second_cut, size - begin) : size - begin;
      uint64_t n = index(SCALAR, data + begin, length, expected.data(), format, &scalar_carry);
      uint64_t m = index(kernel, data + begin, length, actual.data(), format, &carry);
      if (n != m || carry != scalar_carry || !std::equal(expected.begin(), expected.begin() + n, actual.begin()))
        std::abort();
      begin += length;
    }
  }
  return 0;
}
