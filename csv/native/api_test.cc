// C ABI tests: argument validation, packed results, kernel selection, and strict UTF-8 offsets
// that match the scalar rules for every input.
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <random>
#include <string>
#include <vector>

#include "csv/native/api.h"
#include "csv/native/index.h"
#include "csv/native/utf8.h"

using namespace truffledata::csv;

#define CHECK(condition)                                                                   \
  do {                                                                                     \
    if (!(condition)) {                                                                    \
      std::fprintf(stderr, "%s:%d: CHECK failed: %s\n", __FILE__, __LINE__, #condition); \
      std::abort();                                                                        \
    }                                                                                      \
  } while (0)

namespace {

int32_t format(uint8_t delimiter, uint8_t quote, bool quoting) {
  return delimiter | quote << 8 | (quoting ? 1 << 16 : 0);
}

int64_t validate(const std::string& s) {
  return td_csv_validate_utf8(reinterpret_cast<const uint8_t*>(s.data()), s.size());
}

}  // namespace

int main() {
  CHECK(std::strcmp(reinterpret_cast<const char*>(td_csv_fingerprint()), TRUFFLEDATA_CSV_SEAM_ABI) == 0);
  // Kernel selection: AUTO resolves to the best kernel; unsupported kernels are refused.
  CHECK(td_csv_kernel(AUTO) == static_cast<int32_t>(best_kernel()));
  CHECK(td_csv_kernel(SCALAR) == SCALAR && td_csv_kernel(SWAR) == SWAR);
  CHECK(td_csv_kernel(-1) == -1 && td_csv_kernel(99) == -1);
#if defined(__x86_64__)
  CHECK(td_csv_kernel(NEON) == -1);
#else
  CHECK(td_csv_kernel(AVX2) == -1 && td_csv_kernel(AVX512) == -1);
#endif

  std::string s = "a,\"b\"\"c\"\r\nd";
  std::vector<int32_t> out(s.size());
  const uint8_t* data = reinterpret_cast<const uint8_t*>(s.data());
  int32_t rfc = format(',', '"', true);
  for (int32_t kernel = 0; kernel <= AVX512; kernel++) {
    if (td_csv_kernel(kernel) < 0) {
      CHECK(td_csv_index(data, s.size(), out.data(), rfc, kernel, 0) == -2);
      continue;
    }
    for (auto call : {td_csv_index, td_csv_index_leaf}) {
      int64_t r = call(data, s.size(), out.data(), rfc, kernel, 0);
      CHECK(r >= 0);
      CHECK((r >> 8) == 3 && (r & 0xFF) == CARRY_U);
      CHECK(out[0] == 1);
      CHECK(static_cast<uint32_t>(out[1]) == (8u | FLAG_QUOTED | FLAG_ESCAPED));
      CHECK(out[2] == 9);
      // A carry continues the stream: the same bytes inside an open quote yield nothing.
      uint64_t carry = CARRY_INSIDE | CARRY_Q;
      std::vector<uint32_t> expected(s.size());
      uint64_t count = index_scalar(data, s.size(), expected.data(), Format{',', '"', true}, &carry);
      r = call(data, s.size(), out.data(), rfc, kernel, static_cast<int64_t>(CARRY_INSIDE | CARRY_Q));
      CHECK(r >= 0 && static_cast<uint64_t>(r >> 8) == count && static_cast<uint64_t>(r & 0xFF) == carry);
    }
  }
  // Argument validation never touches memory.
  CHECK(td_csv_index(data, s.size(), out.data(), format('\n', '"', true), 0, 0) == -1);
  CHECK(td_csv_index(data, s.size(), out.data(), format(',', ',', true), 0, 0) == -1);
  CHECK(td_csv_index(data, s.size(), out.data(), format(',', '\r', true), 0, 0) == -1);
  CHECK(td_csv_index(data, s.size(), out.data(), rfc | 1 << 17, 0, 0) == -1);
  CHECK(td_csv_index(data, s.size(), out.data(), format(',', ',', false), 0, 0) >= 0);
  CHECK(td_csv_index(data, MAX_CALL_BYTES + 1, out.data(), rfc, 0, 0) == -3);
  CHECK(td_csv_index(data, s.size(), out.data(), rfc, 0, 32) == -4);
  CHECK(td_csv_index(data, s.size(), out.data(), rfc, 0, -1) == -4);
  CHECK(td_csv_index(data, 0, out.data(), rfc, 0, 0) == 0);

  // UTF-8: fixed cases at the Java validator's offsets.
  CHECK(validate("") == -1);
  CHECK(validate("plain ascii, \xC3\xA9 \xE4\xB8\xAD \xF0\x9F\x98\x80 \xF4\x8F\xBF\xBF") == -1);
  CHECK(validate("ab\xC0\x80") == 2);          // overlong
  CHECK(validate("ab\xE0\x80\x80") == 2);      // overlong
  CHECK(validate("a\xED\xA0\x80") == 1);       // surrogate
  CHECK(validate("\xF4\x90\x80\x80") == 0);    // above U+10FFFF
  CHECK(validate("xyz\xF8") == 3);             // invalid lead
  CHECK(validate("xyz\xE4\xB8") == 3);         // truncated at end
  CHECK(validate("x\x80") == 1);               // stray continuation
  // Differential: simdutf's verdict plus the scalar offset equal the scalar rules everywhere,
  // across SIMD block boundaries.
  std::mt19937_64 rng(7);
  static const char* pieces[] = {"a", "\xC3\xA9", "\xE4\xB8\xAD", "\xF0\x9F\x98\x80", "\x80",
                                 "\xC0", "\xED\xA0\x80", "\xF4\x90", "\xE0\xA0\x80", "\xFF"};
  for (int iter = 0; iter < 200000; iter++) {
    std::string t;
    size_t n = rng() % 160;
    int bad = iter % 4 == 0 ? 10 : 4;
    while (t.size() < n) t += pieces[rng() % bad];
    if (iter % 3 == 0 && !t.empty()) t.resize(rng() % t.size());
    int64_t expected = utf8_invalid_offset(reinterpret_cast<const uint8_t*>(t.data()), t.size());
    CHECK(validate(t) == expected);
  }
  std::printf("api_test passed (best kernel %d)\n", static_cast<int>(best_kernel()));
  return 0;
}
