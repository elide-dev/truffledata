// Boundary-number parity (plan 2026-11-08-beat-stock-svm WS8): every number kernel this CPU can
// run must emit exactly the builtin (fallback) parser's writer events and status for every token,
// and whole stage-2 decodes through each kernel must publish identical tapes and statuses.
#include "json/native/number_kernel.h"
#include "json/native/stage2.h"
#include "json/native/decoder.h"
#include "json/native/status.h"
#include "tape/native/testing.h"
#include <cinttypes>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <memory_resource>
#include <random>
#include <stdexcept>
#include <string>
#include <vector>
using namespace truffledata;
namespace kernel = truffledata::json::number_kernel;
namespace {
void require(bool condition, const std::string& what) { if (!condition) throw std::runtime_error(what); }

struct recorder {
  kernel::result out{0, kernel::emitted_none, 0};
  void append_s64(int64_t value) { out.bits = uint64_t(value); out.kind = kernel::emitted_s64; }
  void append_u64(uint64_t value) { out.bits = value; out.kind = kernel::emitted_u64; }
  void append_double(double value) { std::memcpy(&out.bits, &value, sizeof value); out.kind = kernel::emitted_double; }
};
kernel::result builtin(const uint8_t* src) {
  recorder writer;
  writer.out.error = uint32_t(simdjson::builtin::numberparsing::parse_number(src, writer));
  return writer.out;
}

struct tier_entry { kernel::tier tier; const char* name; kernel::result (*parse)(const uint8_t*); };
std::vector<tier_entry> runnable() {
  std::vector<tier_entry> tiers;
#if defined(__x86_64__)
  // simdjson's westmere/haswell kernels' own requirements (their begin.h target regions).
  __builtin_cpu_init();
  if (__builtin_cpu_supports("sse4.2") && __builtin_cpu_supports("pclmul") && __builtin_cpu_supports("popcnt"))
    tiers.push_back({kernel::tier::westmere, "westmere", kernel::parse_westmere});
  if (__builtin_cpu_supports("avx2") && __builtin_cpu_supports("bmi") && __builtin_cpu_supports("bmi2")
      && __builtin_cpu_supports("pclmul") && __builtin_cpu_supports("lzcnt") && __builtin_cpu_supports("popcnt"))
    tiers.push_back({kernel::tier::haswell, "haswell", kernel::parse_haswell});
#endif
  return tiers;
}

std::string hex(const std::string& s) {
  std::string out; char b[3];
  for (unsigned char c : s) { std::snprintf(b, sizeof b, "%02x", c); out += b; }
  return out;
}

// One token followed by `terminator`, then SIMDJSON_PADDING bytes of `fill` (the parser may read
// up to 16 bytes past a digit run; padding is load safety, never part of a token).
uint64_t compared_tokens = 0;
void compare_token(const std::vector<tier_entry>& tiers, const std::string& token, char terminator, char fill) {
  std::string buffer = token;
  buffer += terminator;
  buffer.append(simdjson::SIMDJSON_PADDING, fill);
  const uint8_t* src = reinterpret_cast<const uint8_t*>(buffer.data());
  kernel::result expected = builtin(src);
  for (const auto& tier : tiers) {
    kernel::result actual = tier.parse(src);
    if (actual.kind != expected.kind || actual.error != expected.error
        || (expected.kind != kernel::emitted_none && actual.bits != expected.bits)) {
      char detail[256];
      std::snprintf(detail, sizeof detail, " builtin kind=%u error=%u bits=%016" PRIx64 " %s kind=%u error=%u bits=%016" PRIx64,
                    expected.kind, expected.error, expected.bits, tier.name, actual.kind, actual.error, actual.bits);
      throw std::runtime_error("number kernel parity: token " + hex(token) + " terminator " + hex(std::string(1, terminator)) + detail);
    }
  }
  ++compared_tokens;
}
void compare_all(const std::vector<tier_entry>& tiers, const std::string& token) {
  for (char terminator : {',', ']', '}', ' ', '\n', '\r', '\t', 'x', '.', 'e', '0', '\0', '"'})
    for (char fill : {' ', '0', '\0'}) compare_token(tiers, token, terminator, fill);
}

// The numeric comparison fixture's spelling (JsonFixture.numericDocument): 17 significant digits.
std::string canada(double value) { char b[64]; std::snprintf(b, sizeof b, "%.17g", value); return b; }
std::vector<std::string> numeric_documents() {
  std::vector<std::string> out;
  for (int i = 0; i < 32; ++i) {
    int points = 160 + 4 * (i % 8);
    uint64_t state = 0x9E3779B97F4A7C15ull ^ uint64_t(i + 1);
    std::string ring;
    std::vector<int64_t> micros;
    for (int p = 0; p < points - 1; ++p) {
      state = state * 6364136223846793005ull + 1442695040888963407ull;
      micros.push_back(-141000000 + int64_t((state >> 11) % 89000000));
      state = state * 6364136223846793005ull + 1442695040888963407ull;
      micros.push_back(41000000 + int64_t((state >> 11) % 43000000));
    }
    micros.push_back(micros[0]); micros.push_back(micros[1]);
    for (size_t p = 0; p < micros.size(); p += 2)
      ring += (p ? ",[" : "[") + canada(double(micros[p]) / 1e6) + "," + canada(double(micros[p + 1]) / 1e6) + "]";
    out.push_back("{\"type\":\"Feature\",\"properties\":{\"name\":\"ring-" + std::to_string(i) + "\",\"id\":" + std::to_string(i)
                  + "},\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[" + ring + "]]}}");
  }
  return out;
}

// Number tokens of a document: maximal runs of number characters that start a value.
std::vector<std::string> tokens_of(const std::string& document) {
  std::vector<std::string> out;
  auto number_char = [](char c) { return (c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E'; };
  for (size_t i = 0; i < document.size(); ++i) {
    char c = document[i];
    bool starts = (c == '-' || (c >= '0' && c <= '9')) && (i == 0 || std::strchr("[,: \n\r\t", document[i - 1]));
    if (!starts) continue;
    size_t j = i;
    while (j < document.size() && number_char(document[j])) ++j;
    out.push_back(document.substr(i, j - i));
    i = j;
  }
  return out;
}

std::vector<std::string> edge_tokens() {
  std::vector<std::string> out = {
      // Exponent limits, overflow to infinity (rejected), underflow to zero and subnormals.
      "1e308", "1.7976931348623157e308", "1.7976931348623158e308", "1.7976931348623159e308", "1e309", "-1e309",
      "1e-324", "-1e-324", "4.9e-324", "5e-324", "2.4703282292062327e-324", "2.4703282292062328e-324",
      "2.2250738585072011e-308", "2.2250738585072014e-308", "1e-307", "1e-343", "1e-342", "0e999999999999999999999",
      "1e0000000000000000000001", "1e+308", "1E-5", "1e+", "1e-", "1e", "1E", "1e+-1", "1.5e", "-1.5E+",
      "1e999999999999999999", "1e-999999999999999999", "-0.0e-999", "123.456e-78", "1e19", "1e20", "1e22", "1e23",
      // 2^53 ± 1 as integers and decimals; 2^63 and 2^64 boundaries; 19/20-digit integers.
      "9007199254740991", "9007199254740992", "9007199254740993", "-9007199254740993", "9007199254740993.0",
      "9007199254740993e0", "9223372036854775807", "9223372036854775808", "-9223372036854775808", "-9223372036854775809",
      "18446744073709551615", "18446744073709551616", "-18446744073709551615", "99999999999999999999", "10000000000000000000",
      "19999999999999999999", "20000000000000000000", "100000000000000000000", "12345678901234567890123",
      "1000000000000000000", "999999999999999999", "-999999999999999999",
      // Signed zero, leading zeros, trailing dots and bare signs.
      "0", "-0", "0.0", "-0.0", "-0.00000000000000000", "00", "01", "-01", "-00", "0.", "1.", "-1.", "-", "-.", ".5",
      "-.5", "+1", "0x1", "1.e5", "0e", "0e0", "-0e-0", "0.0e0", "1..2", "1.2.3",
      // Long fractions and digit runs around the eight- and four-digit steps.
      "3.1415926535897932384626433832795028841971693993751", "0.12345678901234567890", "0.00000000000000001",
      "0.000000000000000001", "1.00000000000000000", "123456789012345678901234567890e-10", "-65.613616999999977",
      "43.420273000000009", "0." + std::string(400, '0') + "1", "1" + std::string(400, '0'), "1" + std::string(310, '0') + ".0",
  };
  // Every fraction length 0..40 and integer length 1..22, plain and with exponents.
  for (int fraction = 0; fraction <= 40; ++fraction) {
    std::string digits;
    for (int d = 0; d < fraction; ++d) digits += char('0' + (d * 7 + 3) % 10);
    out.push_back("1." + digits); out.push_back("-98765." + digits); out.push_back("0." + digits + "e-5");
  }
  for (int length = 1; length <= 22; ++length) {
    std::string nines(size_t(length), '9'), ones = "1" + std::string(size_t(length - 1), '0');
    out.push_back(nines); out.push_back("-" + nines); out.push_back(ones); out.push_back(ones + ".5");
  }
  return out;
}

std::vector<std::string> random_tokens() {
  std::vector<std::string> out;
  std::mt19937_64 rng(2026110908);
  for (int i = 0; i < 20000; ++i) {
    uint64_t bits = rng();
    double value; std::memcpy(&value, &bits, sizeof value);
    if (!std::isfinite(value)) continue;
    char b[64];
    for (const char* format : {"%.17g", "%.15g", "%g", "%.3f", "%.20e"}) {
      std::snprintf(b, sizeof b, format, value);
      if (std::strlen(b) < 60) out.push_back(b);
    }
    std::snprintf(b, sizeof b, "%" PRId64, int64_t(rng()) >> (rng() % 64));
    out.push_back(b);
  }
  // Grammar fuzz: random strings over the number alphabet.
  const char alphabet[] = "0123456789000-.eE+";
  for (int i = 0; i < 50000; ++i) {
    std::string token;
    size_t length = 1 + rng() % 30;
    for (size_t k = 0; k < length; ++k) token += alphabet[rng() % (sizeof alphabet - 1)];
    out.push_back(token);
  }
  return out;
}

// Whole stage-2 decodes through one tier (number_kernel::selected), structural tape and
// validate-only writer, with the decode status.
struct image { int status = 0; int validate = 0; std::vector<uint64_t> words, directories; std::vector<uint8_t> pool; };
image decode(const std::string& source) {
  image out;
  simdjson::padded_string input(source);
  json::stage1_index index;
  myna::value_limits limits{};
  try {
    std::pmr::monotonic_buffer_resource memory;
    index.run(reinterpret_cast<const uint8_t*>(input.data()), input.size());
    tape::structural_writer writer(&memory, limits);
    writer.input({input.data(), input.size()});
    writer.reserve_structural(index.size(), input.size());
    json::stage2_walk<tape::structural_writer> walk(reinterpret_cast<const uint8_t*>(input.data()), input.size(),
                                                     index.indexes(), index.size(), writer);
    walk.run();
    auto view = writer.view();
    out.words.assign(view.words, view.words + view.word_count);
    out.directories.assign(view.directory, view.directory + view.directory_count);
    out.pool.assign(view.pool, view.pool + view.pool_bytes);
    out.status = json::status_ok;
  } catch (...) { out.status = json::current_exception_status(); }
  try {
    std::pmr::monotonic_buffer_resource memory;
    index.run(reinterpret_cast<const uint8_t*>(input.data()), input.size());
    json::structural_null_writer writer(&memory, limits);
    writer.input({input.data(), input.size()});
    writer.reserve_structural(index.size(), input.size());
    json::stage2_walk<json::structural_null_writer> walk(reinterpret_cast<const uint8_t*>(input.data()), input.size(),
                                                         index.indexes(), index.size(), writer);
    walk.run();
    out.validate = json::status_ok;
  } catch (...) { out.validate = json::current_exception_status(); }
  return out;
}

uint64_t compared_documents = 0;
void compare_documents(const std::vector<tier_entry>& tiers, const std::vector<std::string>& documents) {
  for (const auto& source : documents) {
    kernel::selected.store(uint8_t(kernel::tier::builtin));
    image expected = decode(source);
    for (const auto& tier : tiers) {
      kernel::selected.store(uint8_t(tier.tier));
      image actual = decode(source);
      require(actual.status == expected.status && actual.validate == expected.validate && actual.words == expected.words
                  && actual.directories == expected.directories && actual.pool == expected.pool,
              std::string("number kernel decode parity (") + tier.name + "): " + source.substr(0, 200));
    }
    ++compared_documents;
  }
  kernel::selected.store(kernel::unknown);
}

void run() {
  auto tiers = runnable();
  std::string names = "builtin";
  for (const auto& tier : tiers) names += std::string(",") + tier.name;
  kernel::selected.store(kernel::unknown);
  const char* detected[] = {"builtin", "westmere", "haswell"};
  std::printf("number kernels: simdjson active %s, detected tier %s, parity against builtin for %s\n",
              simdjson::get_active_implementation()->name().c_str(), detected[uint8_t(kernel::active())], names.c_str());
#if defined(__linux__)
  std::ifstream cpuinfo("/proc/cpuinfo");
  for (std::string line; std::getline(cpuinfo, line);)
    if (line.rfind("model name", 0) == 0 || line.rfind("flags", 0) == 0) { std::printf("cpu %s\n", line.c_str()); if (line.rfind("flags", 0) == 0) break; }
#endif
#if defined(__x86_64__)
  // A tier the CPU supports must be the one simdjson's detection selects (or a lower one only
  // when simdjson itself chose lower, e.g. SIMDJSON_FORCE_IMPLEMENTATION).
  std::string active = simdjson::get_active_implementation()->name();
  if (active == "haswell" || active == "icelake") require(kernel::active() == kernel::tier::haswell, "haswell tier not selected");
  if (active == "westmere") require(kernel::active() == kernel::tier::westmere, "westmere tier not selected");
#endif
  std::vector<std::string> documents = tape::testing::corpus();
  for (const auto& document : numeric_documents()) documents.push_back(document);
  for (int i = 0; i < 64; ++i) documents.push_back(tape::testing::fixture(i));
  std::vector<std::string> tokens;
  for (const auto& document : documents) for (auto& token : tokens_of(document)) tokens.push_back(token);
  size_t fixture_tokens = tokens.size();
  for (auto& token : edge_tokens()) tokens.push_back(token);
  size_t edge = tokens.size() - fixture_tokens;
  for (auto& token : random_tokens()) tokens.push_back(token);
  for (const auto& token : tokens) compare_all(tiers, token);
  // Whole decodes: every corpus document, the numeric fixture, and each token as a root, inside an
  // array and followed by trailing input.
  std::vector<std::string> wrapped = documents;
  for (size_t i = 0; i < fixture_tokens + edge; ++i) {
    wrapped.push_back(tokens[i]);
    wrapped.push_back("[" + tokens[i] + "]");
    wrapped.push_back("{\"k\":" + tokens[i] + "}");
    wrapped.push_back(tokens[i] + "x");
  }
  for (size_t i = fixture_tokens + edge; i < tokens.size(); i += 7) wrapped.push_back("[" + tokens[i] + "]");
  compare_documents(tiers, wrapped);
  std::printf("number kernel parity: %zu fixture tokens, %zu edge tokens, %zu random tokens, %" PRIu64
              " token/terminator/padding cases, %" PRIu64 " whole decodes per tier; tiers %s\n",
              fixture_tokens, edge, tokens.size() - fixture_tokens - edge, compared_tokens, compared_documents, names.c_str());
}
} // namespace
int main() {
  try { run(); } catch (const std::exception& e) { std::fprintf(stderr, "FAIL %s\n", e.what()); return 1; }
}
