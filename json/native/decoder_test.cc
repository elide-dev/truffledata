#include "json/native/decoder.h"
#include "json/native/parser_pool.h"
#include <vector>
#include <iostream>
#include <stdexcept>
#include <string>
using truffledata::json::document;
void require_at(bool condition, int line) { if (!condition) throw std::runtime_error("check failed at line " + std::to_string(line)); }
#define require(condition) require_at((condition), __LINE__)
std::string text(const myna_value_view& view, size_t index) {
  const auto& node = view.nodes[index];
  if ((node.tag & MYNA_VALUE_REGION_TEXT) != 0) {
    auto region = node.payload >> 32;
    auto offset = uint32_t(node.payload);
    auto data = region == 0 ? view.bytes : view.regions[region - 1].data;
    return std::string(reinterpret_cast<const char*>(data + offset), node.length);
  }
  std::string result;
  for (uint32_t i = 0; i < node.length; ++i) result += char(myna_value_inline_byte(&node, i));
  return result;
}
std::string lexical_text(const document& parsed, size_t index) {
  uint64_t span = parsed.lexical_spans().at(index);
  const auto& region = parsed.view().regions[0];
  return std::string(reinterpret_cast<const char*>(region.data + (span >> 32)), uint32_t(span));
}
void number_checks() {
  document parsed(R"([1, -0, 18446744073709551615, 1.5, 1E400, 12345678901234567890, {"k\u0041":"v"}])");
  const auto* n = parsed.view().nodes;
  require(n[1].tag == MYNA_VALUE_I64 && n[1].payload == 1);
  require(n[2].tag == MYNA_VALUE_F64 && n[2].payload == 0x8000000000000000ull);
  require(n[3].tag == MYNA_VALUE_U64 && n[3].payload == UINT64_MAX);
  require(n[4].tag == MYNA_VALUE_F64);
  require((n[5].tag & 0xff) == MYNA_VALUE_DECIMAL);
  require(n[6].tag == MYNA_VALUE_U64 && n[6].payload == 12345678901234567890ull);
  require(lexical_text(parsed, 1) == "1");
  require(lexical_text(parsed, 2) == "-0");
  require(lexical_text(parsed, 5) == "1E400");
  require(lexical_text(parsed, 6) == "12345678901234567890");
  // Containers, booleans and nulls carry no span; keys and strings keep their quotes.
  require(parsed.lexical_spans().at(0) == 0 && parsed.lexical_spans().at(7) == 0);
  require(lexical_text(parsed, 8) == "\"k\\u0041\"");
  require(lexical_text(parsed, 9) == "\"v\"");
  require(parsed.lexical_spans().size() == parsed.view().node_count);
}
class fault_memory final : public std::pmr::memory_resource {
public:
  size_t fail_at, calls = 0, live = 0;
  explicit fault_memory(size_t fail) : fail_at(fail) {}
private:
  void* do_allocate(size_t bytes, size_t alignment) override {
    if (calls++ == fail_at) throw std::bad_alloc();
    auto pointer = std::pmr::new_delete_resource()->allocate(bytes, alignment);
    live += bytes;
    return pointer;
  }
  void do_deallocate(void* pointer, size_t bytes, size_t alignment) override {
    std::pmr::new_delete_resource()->deallocate(pointer, bytes, alignment);
    live -= bytes;
  }
  bool do_is_equal(const std::pmr::memory_resource& other) const noexcept override {
    return this == &other;
  }
};

void allocation_checks() {
  using truffledata::json::storage_limits;
  const std::string source = R"({"long":"some long escaped content \u4e2d", "nested":[{"a":1,"b":true},null,4]})";
  document baseline(source);
  const auto stats = baseline.allocations();
  require(stats.copied_input_bytes == source.size());
  require(stats.padded_input_bytes == source.size() + simdjson::SIMDJSON_PADDING);
  require(stats.index_peak_bytes > stats.index_retained_bytes);
  require(stats.parser_input_capacity >= source.size());
  {
    document exact(source, {}, storage_limits{source.size(), stats.index_peak_bytes});
    require(exact.view().node_count == baseline.view().node_count);
  }
  for (auto limits : {storage_limits{source.size() - 1, stats.index_peak_bytes},
                      storage_limits{source.size(), stats.index_peak_bytes - 1},
                      storage_limits{source.size(), 0}}) {
    fault_memory memory(SIZE_MAX);
    bool rejected = false;
    try { document limited(source, {}, limits, &memory); }
    catch (const std::length_error&) { rejected = true; }
    require(rejected);
    require(memory.live == 0);
    if (limits.input_bytes < source.size()) require(memory.calls == 0);
  }
  // Fail each allocation, including transient duplicate-key tables and vector
  // growth. Constructor failure must release every earlier allocation.
  for (size_t i = 0; i <= stats.index_allocations; ++i) {
    fault_memory memory(i);
    bool rejected = false;
    try { document parsed(source, {}, {}, &memory); }
    catch (const std::bad_alloc&) { rejected = true; }
    require(rejected == (i < stats.index_allocations));
    require(memory.live == 0);
  }
  fault_memory memory(SIZE_MAX);
  bool rejected = false;
  try { document invalid(R"({"x":[1,2,3],"x":4})", {}, {}, &memory); }
  catch (const std::invalid_argument&) { rejected = true; }
  require(rejected && memory.live == 0);
}

void retained_text_checks() {
  using truffledata::json::parser_lease;
  using truffledata::json::parser_pool;
  // 14 escaped CJK characters: 42 decoded bytes, forcing region (non-inline) storage.
  document a(R"({"s":"\u4e2d\u4e2d\u4e2d\u4e2d\u4e2d\u4e2d\u4e2d\u4e2d\u4e2d\u4e2d\u4e2d\u4e2d\u4e2d\u4e2d"})");
  const std::string first = text(a.view(), 2);
  require(first.size() == 42);
  document b(R"({"s":"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})");
  require(text(a.view(), 2) == first);
  // Unescaped long strings borrow the retained input region (region 1), skipping `["`.
  document plain(R"(["abcdefghijklmnopqrstuvwxyz0123456789"])");
  const auto& node = plain.view().nodes[1];
  require((node.tag & MYNA_VALUE_REGION_TEXT) != 0);
  require((node.payload >> 32) == 1 && uint32_t(node.payload) == 2);
  require(text(plain.view(), 1) == "abcdefghijklmnopqrstuvwxyz0123456789");
  // Documents do not retain parsers: the pool caps retained parsers at 8.
  { std::vector<parser_lease> leases; for (int i = 0; i < 10; ++i) leases.push_back(parser_pool::acquire()); }
  require(parser_pool::retained() <= 8);
  require(parser_pool::retained() >= 1);
}

int checks();
int main() {
  try { return checks(); }
  catch (const std::exception& e) { std::cerr << "decoder test failure: " << e.what() << '\n'; return 1; }
}
int checks() {
  number_checks();
  retained_text_checks();
  allocation_checks();
  for (auto source : {"null", "true", "false", "0", "-0", "1e999999999999999999999999", "[]", "{}", "\"hi\"", "[1,2,3]", "{\"a\":null,\"b\":[true,\"x\"]}"}) {
    try { document parsed(source); require(parsed.view().node_count > 0); }
    catch (const std::exception& e) { std::cerr << source << ": " << e.what() << '\n'; return 1; }
  }
  for (auto source : {"", "01", "1.", "1e", "-", "NaN", "[1,]", "{\"a\":1,}", "[tru]", "[xyz]", "{\"x\":undefined}", "{\"a\":1,\"unused\":01}", "{\"a\":1,\"\\u0061\":2}", "{} false", "1 2", "\"x\" []", "\"\\uD800\"", "[\"\\q\"]"}) {
    bool rejected = false;
    try { document parsed(source); } catch (const std::exception&) { rejected = true; }
    if (!rejected) { std::cerr << "accepted: " << source << '\n'; return 1; }
  }
  document first("{\"long\":\"escaped string with \\u4e2d content\",\"n\":1e9999999999999999999}");
  { document second("[false]"); }
  require(lexical_text(first, 2) == "\"escaped string with \\u4e2d content\"");
  require(first.view().node_count == 5);
  require(text(first.view(), 2) == "escaped string with 中 content");
  require(text(first.view(), 4) == "1e9999999999999999999");
  require(first.view().nodes[0].next == 5);
  for (auto limits : {myna::value_limits{2, 100, 10}, myna::value_limits{100, 1, 10}, myna::value_limits{100, 100, 1}}) {
    bool rejected = false;
    try { document limited("[\"xx\",[1]]", limits); } catch (const std::length_error&) { rejected = true; }
    require(rejected);
  }
  std::cout << "JSON parser-to-index compatibility checks passed\n";
  return 0;
}
