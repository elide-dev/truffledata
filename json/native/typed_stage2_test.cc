#include "json/native/testing/typed_ondemand.h"
#include <array>
#include <cstdio>
#include <random>
#include <sstream>
using namespace truffledata::json;
using testing::ondemand_typed_document;
static uint64_t cases;
std::string_view text(const uint8_t* p, uint32_t n) { return n ? std::string_view(reinterpret_cast<const char*>(p), n) : std::string_view(); }
void same(const typed_plan& plan, const std::string& input) {
  std::unique_ptr<typed_document> actual;
  std::unique_ptr<ondemand_typed_document> expected;
  try { actual = std::make_unique<typed_document>(plan, input); } catch (const std::exception&) {}
  try { expected = std::make_unique<ondemand_typed_document>(plan, input); } catch (const std::exception&) {}
  auto fail = [&] { std::fprintf(stderr, "typed mismatch case%llu: %s\n", static_cast<unsigned long long>(cases), input.c_str()); throw std::runtime_error("typed stage2 oracle mismatch"); };
  ++cases;
  if (bool(actual) != bool(expected)) fail();
  if (!actual) return;
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
int main() { numeric(); strings_and_shapes(); std::printf("typed structural oracle parity: %llu cases\n", static_cast<unsigned long long>(cases)); }
