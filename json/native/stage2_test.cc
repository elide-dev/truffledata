#include "json/native/stage2.h"
#include "json/native/decoder.h"
#include "json/native/status.h"
#include "tape/native/testing.h"
#include <cstdio>
#include <string>
#include <vector>
using namespace truffledata;
namespace {
void require(bool condition, const char* what) { if (!condition) throw std::runtime_error(what); }
struct image { std::vector<uint64_t> words, directories; std::vector<uint8_t> pool; };
image copy(const td_tape_view& view) {
  return {{view.words, view.words + view.word_count}, {view.directory, view.directory + view.directory_count},
          {view.pool, view.pool + view.pool_bytes}};
}
struct harness {
  json::stage1_index index;
  simdjson::ondemand::parser oracle;
  tape::key_shape_cache shapes;
  myna::value_limits limits{};
  template<class Writer> image run(const std::string& source, bool stage2) {
    simdjson::padded_string input(source);
    std::pmr::monotonic_buffer_resource memory;
    if (stage2) {
      index.run(reinterpret_cast<const uint8_t*>(input.data()), input.size());
      Writer writer(&memory, limits);
      writer.shapes(&shapes); writer.input({input.data(), input.size()});
      writer.reserve(size_t(index.size()) * 2 + 2, input.size(), index.size() / 2 + 1);
      json::stage2_walk<Writer> walk(reinterpret_cast<const uint8_t*>(input.data()), input.size(), index.indexes(), index.size(), writer);
      walk.run();
      if constexpr (std::is_same_v<Writer, tape::ctape_writer>) {
        auto view = writer.view();
        if (auto error = tape::ctape::validate(view)) throw std::runtime_error(error);
        return copy(view);
      }
    } else {
      json::tape_decoder<Writer> decoder(&memory, limits);
      decoder.start({input.data(), input.size()}, json::hint_for(input.size(), {}, {}));
      auto root = oracle.iterate(input).value(); decoder.decode(root);
      if constexpr (std::is_same_v<Writer, tape::ctape_writer>) return copy(decoder.view());
    }
    return {};
  }
  template<class Writer> int status(const std::string& source, bool stage2, image& output) {
    try { output = run<Writer>(source, stage2); return json::status_ok; }
    catch (...) { return json::current_exception_status(); }
  }
  void compare(const std::string& source) {
    image expected, actual, ignored;
    int before = status<tape::ctape_writer>(source, false, expected);
    int after = status<tape::ctape_writer>(source, true, actual);
    if (before != after) {
      std::fprintf(stderr, "status mismatch before=%d after=%d source=", before, after);
      for (unsigned char c : source) std::fprintf(stderr, "%02x", c);
      std::fprintf(stderr, "\n");
      throw std::runtime_error("stage2 acceptance mismatch");
    }
    if (before == json::status_ok)
      require(expected.words == actual.words && expected.directories == actual.directories && expected.pool == actual.pool,
              "stage2 tape differs from On-Demand");
    require(status<json::null_writer>(source, true, ignored) == before, "stage2 validate-only acceptance mismatch");
  }
};
std::vector<std::string> malformed() {
  using tape::testing::fixture;
  using tape::testing::object_with_keys;
  std::vector<std::string> out = {
      "", "01", "1.", "1e", "-", "NaN", "[1,]", "{\"a\":1,}", "[tru]", "[xyz]", "{\"x\":undefined}",
      "{\"a\":1,\"unused\":01}", "{\"a\":1,\"\\u0061\":2}", "{} false", "1 2", "\"x\" []", "\"\\uD800\"",
      "[\"\\q\"]", "[01]", "[1.]", "[.5]", "[1e]", "[-]", "[+1]", "[1e+]", "[-01]", "[0x1]", "[1.e5]",
      "[Infinity]", "[1 2]", "[\"a\x01" "b\"]", "{\"a\x01" "b\":1}", "[\"\xff\"]", "{\"\xc3\":1}", "{\"\":1,\"\":2}",
      object_with_keys(17, 16, "kA"), object_with_keys(40, 39, "k\\u0041"), object_with_keys(1000, 999, "kA"),
      "{\"a\":{\"b\":1,\"b\":2}}", "[{\"x\":1,\"y\":2,\"x\":3}]"};
  for (int i = 0; i < 10; ++i) { std::string f = fixture(i); out.push_back(f.substr(0, f.size() - 1 - size_t(i))); }
  for (int i = 0; i < 5; ++i) out.push_back(fixture(i) + " x");
  for (int i = 0; i < 5; ++i) { std::string f = fixture(i); out.push_back(f.substr(0, f.size() - 1) + ",\"id\":0}"); }
  return out;
}
void differential() {
  harness parser;
  for (const auto& text : tape::testing::corpus()) parser.compare(text);
  for (const auto& text : malformed()) parser.compare(text);
  for (const auto& text : {"truex", "false0", "nulltrue", "1x", "1x2", "[", "{", "{\"x\":", "[true,", "[true:",
      "[,1]", "{,}", "{\"x\" 1}", "{\"x\"::1}", "{\"x\":,1}", "[true false]", "\"\\u123\"", "\"\\uDC00\"",
      "[\"unterminated]", "\"\\", "\"\\u", "[[]"}) parser.compare(text);
  for (const std::string& token : {std::string("true\0",5), std::string("1\0",2), std::string("1\0" "2",3),
      std::string("null\0x",6), std::string("false\0true",10)}) parser.compare(token);
  parser.compare(std::string(129, '[') + "0" + std::string(129, ']'));
  for (const auto& token : {"0", "-0", "18446744073709551615", "18446744073709551616", "1E400", "1e-400", "0.12345678901234567890"})
    for (const auto& whitespace : {"", " ", "\r\n\t "}) {
      parser.compare(std::string(token) + whitespace);
      parser.compare(std::string("[") + token + whitespace + ",2]");
    }
  for (auto limits : {myna::value_limits{0, 0, 0}, myna::value_limits{1, 0, 128}, myna::value_limits{3, 1, 1}}) {
    parser.limits = limits;
    for (const auto& token : {"1x", "1.0x", "1x2", "0", "-0", "18446744073709551616", "[0,1]", "{\"a\":1}"})
      parser.compare(token);
  }
  std::puts("stage2 differential: corpus200, malformed60, grammar and exact numeric whitespace passed");
}
}
int main() { try { differential(); } catch (const std::exception& e) { std::fprintf(stderr,"FAIL %s\n",e.what()); return 1; } }
