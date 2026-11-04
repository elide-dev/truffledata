#include "json/native/decoder.h"
#include <iostream>
#include <stdexcept>
using truffledata::json::document;
void require(bool condition) { if (!condition) throw std::runtime_error("check failed"); }
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
int main() {
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
  require(first.lexical_tokens().at(2) == "\"escaped string with \\u4e2d content\"");
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
}
