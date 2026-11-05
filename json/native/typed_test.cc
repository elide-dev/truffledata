#include "json/native/typed.h"
#include <iostream>
#include <memory>
using namespace truffledata::json;
void check(bool value) { if (!value) throw std::runtime_error("typed JSON check"); }
template<class F> void rejects(F operation) {
  bool rejected = false;
  try { operation(); } catch (const std::exception&) { rejected = true; }
  check(rejected);
}
int main() {
  scalar_field integer{"n", scalar_kind::int64};
  integer.minimum = -128; integer.maximum = 127;
  scalar_field text{"s", scalar_kind::string, false, true};
  text.max_utf8_bytes = 10;
  scalar_plan plan({integer, text, {"b", scalar_kind::boolean, false}});
  scalar_document first(plan, R"({"s":"\u4e2d😀", "n":-128,"b":true})");
  check(first.present() == 7 && first.nulls() == 0);
  check(first.slot(0).bits == static_cast<uint64_t>(-128));
  check(first.slot(1).text == "中😀");
  check(first.slot(1).lexical == R"("\u4e2d😀")");
  check(first.slot(2).bits == 1);
  scalar_document missing(plan, R"({"n":127})");
  scalar_document null(plan, R"({"s":null,"n":0})");
  check(missing.present() == 1 && missing.nulls() == 0);
  check(null.present() == 3 && null.nulls() == 2);
  check(first.allocations().index_allocations == 1);
  check(first.allocations().index_retained_bytes == plan.size() * sizeof(scalar_slot));
  for (auto input : {R"({"n":128})", R"({"n":-129})", R"({"n":1.5})", R"({"n":null})",
       R"({"n":"1"})", R"({"n":0,"b":tru})", R"({"n":0,"s":"\q"})", R"({"n":0,"unused":1})",
       R"({"n":0,"\u006e":1})", R"({"n":0,})", R"({"n":0}[])", R"({"n":01})", "{}", "[]", "null",
       R"({"n":0,"s":"elevenchars"})"}) {
    rejects([&] { scalar_document invalid(plan, input); });
  }
  rejects([&] { scalar_document limited(plan, R"({"n":0})", {100, sizeof(scalar_slot) * 3 - 1}); });
  scalar_document exact(plan, R"({"n":0})", {100, sizeof(scalar_slot) * 3});
  check(exact.allocations().index_peak_bytes == sizeof(scalar_slot) * 3);
  std::unique_ptr<scalar_document> retained;
  {
    scalar_plan temporary({{"x", scalar_kind::string}});
    std::string input = R"({"x":"retained long string with \u4e2d"})";
    retained = std::make_unique<scalar_document>(temporary, input);
    input.assign(input.size(), 'x');
  }
  check(retained->slot(0).text == "retained long string with 中");
  scalar_plan numbers({{"u", scalar_kind::uint64}, {"f", scalar_kind::float64}});
  scalar_document number(numbers, R"({"u":18446744073709551615,"f":-0.0})");
  check(number.slot(0).bits == UINT64_MAX);
  check(number.slot(1).bits == (uint64_t(1) << 63));
  for (auto input : {R"({"u":18446744073709551616,"f":0})", R"({"u":-1,"f":0})", R"({"u":0,"f":1e9999})"})
    rejects([&] { scalar_document invalid(numbers, input); });
  std::vector<scalar_field> fields;
  std::string wide = "{";
  for (int i = 0; i < 64; ++i) {
    fields.push_back({std::to_string(i), scalar_kind::boolean});
    if (i) wide += ',';
    wide += '"' + std::to_string(i) + "\":true";
  }
  wide += '}';
  scalar_plan full(fields);
  scalar_document all(full, wide);
  check(all.present() == UINT64_MAX && all.slot(63).bits == 1);
  fields.push_back({"64", scalar_kind::boolean});
  rejects([&] { scalar_plan oversized(fields); });
  rejects([] { scalar_plan duplicate({{"a", scalar_kind::boolean}, {"a", scalar_kind::string}}); });
  scalar_plan signed64({{"v", scalar_kind::int64}});
  scalar_document minimum(signed64, R"({"v":-9223372036854775808})");
  scalar_document maximum(signed64, R"({"v":9223372036854775807})");
  check(minimum.slot(0).bits == (uint64_t(1) << 63));
  check(maximum.slot(0).bits == INT64_MAX);
  rejects([&] { scalar_document invalid(signed64, R"({"v":9223372036854775808})"); });
  rejects([&] { scalar_document invalid(signed64, R"({"v":-9223372036854775809})"); });
  scalar_field unsigned_field{"u", scalar_kind::uint64};
  unsigned_field.unsigned_maximum = 255;
  scalar_plan byte_plan({unsigned_field});
  scalar_document byte_max(byte_plan, R"({"u":255})");
  check(byte_max.slot(0).bits == 255);
  rejects([&] { scalar_document invalid(byte_plan, R"({"u":256})"); });
  rejects([&] { scalar_document failed(plan, R"({"n":0})", {}, std::pmr::null_memory_resource()); });
  rejects([] { scalar_plan invalid({{std::string(1, char(0xff)), scalar_kind::string}}); });
  scalar_plan empty({});
  scalar_document empty_record(empty, "{}");
  check(empty_record.present() == 0 && empty_record.allocations().index_allocations == 0);
  rejects([&] { (void)empty_record.slot(0); });
  std::cout << "Typed scalar JSON slots, contracts and retained ownership passed\n";
}
