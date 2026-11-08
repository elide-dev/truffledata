#include "json/native/testing/typed_ondemand.h"
#include <array>
#include <cstdio>
#include <random>
#include <sstream>
#include "json/native/status.h"
using namespace truffledata::json;
using testing::ondemand_typed_document;
static uint64_t cases;
std::string_view text(const uint8_t* p, uint32_t n) { return n ? std::string_view(reinterpret_cast<const char*>(p), n) : std::string_view(); }
int typed_status() {
  try { throw; }
  catch (const std::bad_alloc&) { return status_allocation; }
  catch (const std::length_error&) { return status_limit; }
  catch (const simdjson::simdjson_error& error) {
    if (error.error() == simdjson::INCORRECT_TYPE || error.error() == simdjson::NUMBER_OUT_OF_RANGE)
      return status_contract;
    return status_of(error.error());
  }
  catch (const stage2_detail::syntax_error&) { return status_syntax; }
  catch (const std::invalid_argument&) { return status_contract; }
  catch (...) { return status_internal; }
}
void same(const typed_plan& plan, const std::string& input, truffledata::tape::storage_limits limits = {}) {
  std::unique_ptr<typed_document> actual;
  std::unique_ptr<ondemand_typed_document> expected;
  int actual_status = status_ok, expected_status = status_ok;
  try { actual = std::make_unique<typed_document>(plan, input, limits); } catch (...) { actual_status = typed_status(); }
  try { expected = std::make_unique<ondemand_typed_document>(plan, input, limits); } catch (...) { expected_status = typed_status(); }
  auto fail = [&] { std::fprintf(stderr, "typed mismatch case%llu: %s\n", static_cast<unsigned long long>(cases), input.c_str()); throw std::runtime_error("typed stage2 oracle mismatch"); };
  ++cases;
  if (bool(actual) != bool(expected)) fail();
  if (!plan.scalar() && actual_status != expected_status) {
    std::fprintf(stderr, "actual status %d expected %d\n", actual_status, expected_status); fail();
  }
  if (!actual) return;
  if (!plan.scalar()) {
    const auto a = actual->allocations(), b = expected->allocations();
    if (a.index_retained_bytes != b.index_retained_bytes || a.index_peak_bytes != b.index_peak_bytes
        || a.index_allocations != b.index_allocations) fail();
  }
  if (actual->present() != expected->present() || actual->nulls() != expected->nulls() || actual->size() != expected->size() || actual->record_count() != expected->record_count()) fail();
  for (size_t i = 0; i < actual->size(); ++i) {
    auto& a = actual->slot(i); auto& b = expected->slot(i);
    if (a.bits != b.bits || text(a.text, a.text_length) != text(b.text, b.text_length)
        || text(a.lexical, a.lexical_length) != text(b.lexical, b.lexical_length)) fail();
  }
  for (size_t i = 0; i < actual->record_count(); ++i) {
    auto& a = actual->record(i); auto& b = expected->record(i);
    if (a.present != b.present || a.nulls != b.nulls || a.slots != b.slots || a.plan != b.plan) fail();
  }
}
void numeric() {
  const std::array kinds{scalar_kind::int64, scalar_kind::uint64, scalar_kind::float64};
  std::array<std::unique_ptr<typed_plan>, 3> plans;
  for (size_t i = 0; i < 3; ++i) plans[i] = std::make_unique<typed_plan>(std::vector<scalar_field>{{"v", kinds[i]}});
  for (const auto* token : {"0", "-0", "0.0", "-0.0", "-0e0", "1e0", "1.0", "9223372036854775807", "9223372036854775808", "-9223372036854775808", "-9223372036854775809", "18446744073709551615", "18446744073709551616", "1e308", "1e309", "1e-324", "5e-324", "2.2250738585072014e-308", "9007199254740993", "1e-99999", "1e99999", "01", "-01", "+1", "1.", ".1", "1e", "1e+", "NaN", "Infinity", "null", "true", "\"1\"", "[]", "{}"})
    for (const auto& plan : plans) same(*plan, std::string("{\"v\":") + token + "}");
  std::mt19937_64 random(0x54a9d621);
  for (unsigned i = 0; i < 15000; ++i) {
    uint64_t bits = random();
    auto integer = std::to_string(bits);
    auto signed_integer = std::to_string(static_cast<int64_t>(bits));
    auto decimal = (i & 1 ? "-" : "") + integer + "." + std::to_string(random()) + "e" + std::to_string(int(random() % 800) - 400);
    for (const auto& plan : plans) {
      same(*plan, "{\"v\":" + integer + "}");
      same(*plan, "{\"v\":" + signed_integer + "}");
      same(*plan, "{\"v\":" + decimal + "}");
    }
  }
}
void strings_and_shapes() {
  scalar_field a{"a", scalar_kind::string, false, true};
  scalar_field aa{"aa", scalar_kind::string, false, true};
  scalar_field slash{"a\\b", scalar_kind::boolean, false};
  scalar_field long_key{std::string(80, 'k'), scalar_kind::string, false};
  scalar_field empty{"", scalar_kind::boolean, false};
  typed_plan plan({a, aa, slash, long_key, empty, {"n", scalar_kind::int64, false}});
  const std::array<std::string, 12> tokens{"\"\"", "\"a\"", "\"a\\nb\"", "\"\\u4e2d\\ud83d\\ude00\"", "\"中😀\"", "\"\\ud800\"", "\"\\udfff\"", "\"\\q\"", "\"\\u0000\"", "null", "false", "1"};
  for (const auto& x : tokens) for (const auto& y : tokens) {
    auto input = "{\"aa\":" + x + ",\"n\":-0,\"a\":" + y + ",\"a\\\\b\":true,\"\":false}";
    same(plan, input);
    for (size_t n = 0; n < input.size(); ++n) same(plan, input.substr(0, n));
    same(plan, input + " {}");
  }
  same(plan, "{\"" + long_key.name + "\":\"escaped \\u4e2d\"}");
  same(plan, R"({"a":"x","\u0061":"duplicate"})");
  same(plan, R"({"\u0061":"x","a":"duplicate"})");
  same(plan, R"({"unknown":null})");
  same(plan, std::string("{\"a\":\"") + char(0xff) + "\"}");
  scalar_field bounded{"s", scalar_kind::string};
  bounded.length_unit = 2; bounded.minimum_length = 1; bounded.maximum_length = 2; bounded.max_utf8_bytes = 6;
  typed_plan length_plan({bounded});
  for (auto& t : tokens) same(length_plan, "{\"s\":" + t + "}");
  scalar_field f32{"v", scalar_kind::float64}; f32.float_bits = 32;
  typed_plan narrow({f32});
  for (auto token : {"-0", "1.5", "1.1", "16777216", "16777217", "1e-300", "1e40"}) same(narrow, std::string("{\"v\":") + token + "}");
  typed_plan empty_plan(std::vector<scalar_field>{});
  for (auto input : {"{}", "[]", "null", "{\"a\":1}", "{}{}"}) same(empty_plan, input);
}
scalar_field record_field(std::string name, uint32_t child, bool repeated = false) {
  scalar_field field{std::move(name), scalar_kind::record}; field.child = child;
  field.repeated = repeated; return field;
}
scalar_field repeated_field(std::string name, scalar_kind kind) {
  scalar_field field{std::move(name), kind}; field.repeated = true; return field;
}
void nested_and_repeated() {
  auto optional = record_field("optional", 1); optional.required = false; optional.nullable = true;
  auto words = repeated_field("words", scalar_kind::string); words.nullable = true;
  auto bounded = repeated_field("numbers", scalar_kind::int64); bounded.minimum = -5; bounded.maximum = 5;
  typed_plan plan(std::vector<std::vector<scalar_field>>{
    {record_field("child", 1), record_field("items", 1, true), words, bounded, optional},
    {{"n", scalar_kind::int64}, {"s", scalar_kind::string}, record_field("leaf", 2)},
    {{"b", scalar_kind::boolean}, repeated_field("u", scalar_kind::uint64)}});
  const std::string point = R"({"n":-0,"s":"中\\x","leaf":{"b":true,"u":[0,18446744073709551615]}})";
  const std::string reordered = R"({"leaf":{"u":[],"b":false},"s":"\u4e2d\ud83d\ude00","\u006e":9})";
  const std::string empty = R"({"words":null,"numbers":[],"items":[],"child":)" + point + "}";
  const std::string full = R"({"words":["","abc","\u0000"],"numbers":[-5,0,5],"items":[)"
      + point + "," + reordered + "],\"child\":" + point + ",\"optional\":" + reordered + "}";
  for (const auto& input : {empty, full}) {
    same(plan, input);
    same(plan, input + "{}");
    for (size_t cut = 0; cut < input.size(); ++cut) same(plan, input.substr(0, cut));
    for (size_t at = 0; at < input.size(); ++at) {
      same(plan, input.substr(0, at) + input.substr(at + 1));
      for (char c : std::string("{}[],:\"0ntf ")) {
        std::string corrupt = input; corrupt[at] = c; same(plan, corrupt);
      }
    }
    for (size_t limit : {size_t(0), size_t(1), size_t(48), size_t(256), size_t(1024), size_t(4096)})
      same(plan, input, {input.size(), limit});
    same(plan, input, {input.size() - 1, size_t(-1)});
  }
  for (const auto* token : {"null", "true", "0", "0.0", "\"x\"", "[]", "{}", "[null]", "[[]]", "[{}]",
                           "[1,]", "[,1]", "[1,,2]", "[1 2]", "[1,2}", "[1e999]", "[9223372036854775808]"}) {
    same(plan, std::string("{\"child\":") + token + R"(,"items":[],"words":[],"numbers":[]})");
    same(plan, "{\"child\":" + point + ",\"items\":" + token + R"(,"words":[],"numbers":[]})");
    same(plan, "{\"child\":" + point + R"(,"items":[],"words":)" + token + R"(,"numbers":[]})");
    same(plan, "{\"child\":" + point + R"(,"items":[],"words":[],"numbers":)" + token + "}");
    for (size_t limit : {size_t(256), size_t(1024), size_t(4096)})
      same(plan, "{\"child\":" + point + ",\"items\":" + token + R"(,"words":[],"numbers":[]})",
           {4096, limit});
  }
  auto nullable_items = record_field("items", 1, true); nullable_items.nullable = true;
  auto nullable_child = record_field("child", 1); nullable_child.nullable = true;
  typed_plan empty_children(std::vector<std::vector<scalar_field>>{{nullable_child, nullable_items}, {}});
  for (const auto* input : {R"({"child":{},"items":[{},{},{}]})", R"({"child":null,"items":null})",
                           R"({"child":{},"items":[]})", R"({"child":{},"items":[null]})",
                           R"({"child":{"extra":0},"items":[]})", R"({"child":{},"items":[{"x":1}]})"})
    same(empty_children, input);
  // Empty child records allocate table entries but no slots. A late non-record element must
  // fail before a scalar callback could mistake a record-table index for a slot index.
  std::string empties = R"({"child":{},"items":[)";
  for (size_t i = 0; i < 80; ++i) empties += "{},";
  for (const auto* value : {"{}", "null", "true", "1", "\"x\"", "[]"})
    same(empty_children, empties + value + "]}");
  // Repeated scalars share exact lexical/numeric semantics with flat scalars.
  std::mt19937_64 random(0x41f4e2);
  for (auto kind : {scalar_kind::int64, scalar_kind::uint64, scalar_kind::float64}) {
    typed_plan scalars({repeated_field("v", kind)});
    for (size_t i = 0; i < 2000; ++i) {
      std::string n = std::to_string(random());
      std::string d = "-" + n + ".5e" + std::to_string(int(random() % 800) - 400);
      same(scalars, "{\"v\":[0," + n + "," + d + "]}");
    }
  }
  // Every valid record depth, including one repeated edge at each hop.
  for (size_t depth = 1; depth <= max_plan_depth; ++depth) {
    std::vector<std::vector<scalar_field>> records(depth);
    records.back().push_back(repeated_field("v", scalar_kind::int64));
    std::string input = R"({"v":[7,8]})";
    for (size_t i = depth - 1; i > 0; --i) {
      records[i - 1].push_back(record_field("c", i, true));
      input = "{\"c\":[" + input + "]}";
    }
    typed_plan deep(std::move(records)); same(deep, input);
  }
}
int main() { numeric(); strings_and_shapes(); nested_and_repeated(); std::printf("typed structural oracle parity: %llu cases\n", static_cast<unsigned long long>(cases)); }
