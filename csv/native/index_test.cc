// Differential tests: every supported kernel must produce the scalar oracle's entries and carry,
// for any partition of the stream into calls.
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <new>
#include <random>
#include <string>
#include <vector>

#include "csv/native/index.h"

using namespace truffledata::csv;

namespace {

// Kernels must not allocate: count every global operator new during indexing.
long allocations = 0;

#define CHECK(condition)                                                  \
  do {                                                                    \
    if (!(condition)) {                                                   \
      std::fprintf(stderr, "%s:%d: CHECK failed: %s\n", __FILE__, __LINE__, #condition); \
      std::abort();                                                       \
    }                                                                     \
  } while (0)

struct Result {
  std::vector<uint32_t> entries;  // absolute offsets plus flags
  uint64_t carry;
  bool operator==(const Result& o) const { return entries == o.entries && carry == o.carry; }
};

/** Indexes `s` in calls of the given lengths (the last call takes the rest). */
Result run(Kernel kernel, const std::string& s, Format format, const std::vector<uint64_t>& cuts) {
  Result r{{}, 0};
  std::vector<uint32_t> out(s.size() + 1);
  uint64_t pos = 0;
  size_t c = 0;
  while (pos < s.size() || (pos == 0 && s.empty() && c == 0)) {
    uint64_t len = c < cuts.size() ? std::min<uint64_t>(cuts[c], s.size() - pos) : s.size() - pos;
    c++;
    long before = allocations;
    uint64_t n = index(kernel, reinterpret_cast<const uint8_t*>(s.data()) + pos, len, out.data(),
                       format, &r.carry);
    CHECK(allocations == before);
    CHECK(n <= len);
    for (uint64_t i = 0; i < n; i++) {
      uint32_t e = out[i];
      CHECK((e & OFFSET_MASK) < len);
      r.entries.push_back(((e & OFFSET_MASK) + static_cast<uint32_t>(pos)) | (e & ~OFFSET_MASK));
    }
    pos += len;
    if (s.empty()) break;
  }
  return r;
}

std::vector<Kernel> kernels() {
  std::vector<Kernel> all;
  for (Kernel k : {SWAR, NEON, AVX2, AVX512})
    if (supported(k)) all.push_back(k);
  return all;
}

const char* name(Kernel k) {
  switch (k) {
    case SCALAR: return "scalar";
    case SWAR: return "swar";
    case NEON: return "neon";
    case AVX2: return "avx2";
    case AVX512: return "avx512";
    default: return "auto";
  }
}

std::string random_input(std::mt19937_64& rng, size_t length, Format f, int mode) {
  // Structural bytes, blanks, ordinary ASCII and multi-byte UTF-8 pieces.
  static const char* pieces[] = {"a", "b", "1", " ", "\t", "\n", "\r", "\r\n", "\xC3\xA9",
                                 "\xE4\xB8\xAD", "\xF0\x9F\x98\x80", "x"};
  std::string s;
  std::string delimiter(1, static_cast<char>(f.delimiter)), quote(1, static_cast<char>(f.quote));
  while (s.size() < length) {
    uint64_t pick = rng() % 100;
    if (mode == 0) {  // dense structure
      if (pick < 25) s += delimiter;
      else if (pick < 45) s += quote;
      else s += pieces[rng() % 12];
    } else if (mode == 1) {  // long quoted text with doubled quotes
      s += quote;
      size_t n = rng() % 150;
      for (size_t i = 0; i < n; i++) s += rng() % 20 == 0 ? quote + quote : pieces[rng() % 12];
      s += quote;
      s += rng() % 4 == 0 ? "\n" : delimiter;
    } else {  // realistic records
      s += pick < 30 ? quote + "q" + delimiter + quote : std::to_string(rng() % 100000);
      s += rng() % 6 == 0 ? (rng() % 2 ? "\r\n" : "\n") : delimiter;
    }
  }
  s.resize(length);
  return s;
}

void check_equal(const char* what, Kernel k, const std::string& s, const Result& expected,
                 const Result& actual) {
  if (expected == actual) return;
  std::fprintf(stderr, "%s: kernel %s differs on %zu bytes (entries %zu vs %zu, carry %llu vs %llu)\n",
               what, name(k), s.size(), expected.entries.size(), actual.entries.size(),
               static_cast<unsigned long long>(expected.carry),
               static_cast<unsigned long long>(actual.carry));
  std::abort();
}

const Format FORMATS[] = {
    {',', '"', true}, {';', '\'', true}, {'\t', '"', true}, {',', '"', false},
    {'"', '\'', true}, {'|', 'a', true},  {0x80, 0xFF, true}, {',', 0xC3, true},
};

}  // namespace

void* operator new(std::size_t size) {
  allocations++;
  void* p = std::malloc(size ? size : 1);
  if (!p) throw std::bad_alloc();
  return p;
}
void operator delete(void* p) noexcept { std::free(p); }
void operator delete(void* p, std::size_t) noexcept { std::free(p); }

int main() {
  std::mt19937_64 rng(20261106);
  std::vector<Kernel> ks = kernels();
  std::printf("best kernel: %s; testing scalar against", name(best_kernel()));
  for (Kernel k : ks) std::printf(" %s", name(k));
  std::printf("\n");
  CHECK(supported(AUTO) && supported(SCALAR) && supported(SWAR));
#if defined(__x86_64__)
  CHECK(!supported(NEON));
#else
  CHECK(!supported(AVX2) && !supported(AVX512));
#endif

  // indexable(): whole calls when capacity allows, else whole blocks.
  CHECK(indexable(100, 100) == 100 && indexable(100, 200) == 100 && indexable(200, 100) == 64);
  CHECK(indexable(0, 0) == 0 && indexable(64, 63) == 0 && indexable(1000, 130) == 128);

  long cases = 0;
  // Every cut position over short inputs: one call boundary anywhere, including inside CRLF,
  // between doubled quotes and inside multi-byte sequences.
  for (int iter = 0; iter < 400; iter++) {
    Format f = FORMATS[iter % 8];
    std::string s = random_input(rng, rng() % 200, f, iter % 3);
    Result expected = run(SCALAR, s, f, {});
    for (Kernel k : ks) {
      check_equal("whole", k, s, expected, run(k, s, f, {}));
      for (uint64_t cut = 0; cut <= s.size(); cut++) {
        check_equal("cut", k, s, expected, run(k, s, f, {cut}));
        cases++;
      }
    }
  }
  // Random multi-call partitions, including many tiny calls and 64-byte block multiples.
  for (int iter = 0; iter < 3000; iter++) {
    Format f = FORMATS[iter % 8];
    std::string s = random_input(rng, rng() % 2000, f, iter % 3);
    Result expected = run(SCALAR, s, f, {});
    std::vector<uint64_t> cuts;
    for (uint64_t total = 0; total < s.size();) {
      uint64_t c = rng() % 4 == 0 ? 64 * (rng() % 5) : rng() % 130;
      cuts.push_back(c);
      total += c;
    }
    for (Kernel k : ks) {
      check_equal("partition", k, s, expected, run(k, s, f, cuts));
      cases++;
    }
  }
  // Every byte value at every position of a block next to structure.
  for (int b = 0; b < 256; b++) {
    for (Format f : FORMATS) {
      std::string s(130, 'z');
      for (size_t i = 0; i < s.size(); i += 7) s[i] = static_cast<char>(b);
      s[3] = static_cast<char>(f.quote);
      s[64] = static_cast<char>(f.delimiter);
      Result expected = run(SCALAR, s, f, {});
      for (Kernel k : ks) check_equal("bytes", k, s, expected, run(k, s, f, {63, 1, 2}));
      cases++;
    }
  }
  // Large inputs through the capacity rule: calls of indexable(length, capacity) bytes.
  for (int iter = 0; iter < 6; iter++) {
    Format f = FORMATS[iter % 3];
    std::string s = random_input(rng, (1u << 20) + rng() % 1000, f, iter % 3);
    Result expected = run(SCALAR, s, f, {});
    uint64_t capacity = 64 * (1 + rng() % 4000) + rng() % 64;
    std::vector<uint64_t> cuts;
    for (uint64_t pos = 0; pos < s.size();) {
      uint64_t n = indexable(s.size() - pos, capacity);
      cuts.push_back(n);
      pos += n;
    }
    for (Kernel k : ks) check_equal("capacity", k, s, expected, run(k, s, f, cuts));
    cases++;
  }
  // Contract examples: offsets, flags and carries.
  {
    Format f{',', '"', true};
    std::string s = "a,\"b,c\",\"x\"\"y\",\"z\"w,q\"r\"\n\"\"\r\n";
    Result r = run(SCALAR, s, f, {});
    std::vector<uint32_t> expected = {
        1,                                // a
        7 | FLAG_QUOTED,                  // "b,c"
        14 | FLAG_QUOTED | FLAG_ESCAPED,  // "x""y"
        19 | FLAG_QUOTED | FLAG_UNQUOTED, // "z"w
        24 | FLAG_QUOTED | FLAG_UNQUOTED, // q"r"
        27 | FLAG_QUOTED,                 // "" before CR
        28,                               // empty field before LF
    };
    CHECK(r.entries == expected);
    CHECK(r.carry == 0);
    CHECK(run(SCALAR, std::string("\"ab"), f, {}).carry == (CARRY_INSIDE | CARRY_Q));
    CHECK(run(SCALAR, std::string("\"ab\""), f, {}).carry == (CARRY_CLOSING | CARRY_Q));
    CHECK(run(SCALAR, std::string("\"a\"\""), f, {}).carry ==
          (CARRY_INSIDE | CARRY_Q | CARRY_E));
    CHECK(run(SCALAR, std::string("ab"), f, {}).carry == CARRY_U);
  }
  std::printf("index_test: %ld differential cases passed, no allocations\n", cases);
  return 0;
}
