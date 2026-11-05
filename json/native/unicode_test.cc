#include "json/native/typed.h"
#include <iostream>
using namespace truffledata::json;
extern "C" int64_t td_buffa_utf8_to_utf16(const uint8_t*, uint64_t, uint16_t*, uint64_t);
void check(bool value) { if (!value) throw std::runtime_error("Unicode contract check failed"); }
template<class F> void rejects(F operation) {
  bool rejected = false;
  try { operation(); } catch (const std::invalid_argument&) { rejected = true; }
  check(rejected);
}
int main() {
  for (size_t repetitions : {size_t(0), size_t(1), size_t(16), size_t(64), size_t(257), size_t(4096)}) {
    std::string utf8;
    std::string escaped;
    for (size_t i = 0; i < repetitions; ++i) {
      utf8 += "a中😀";
      escaped += R"(a\u4e2d\ud83d\ude00)";
    }
    std::vector<uint16_t> output(utf8.size() + 2, 0xdead);
    auto converted = td_buffa_utf8_to_utf16(reinterpret_cast<const uint8_t*>(utf8.data()), utf8.size(),
        output.data() + 1, utf8.size());
    check(converted == int64_t(4 * repetitions));
    check(output.front() == 0xdead && output.back() == 0xdead);
    for (uint32_t unit : {1u, 2u}) {
      scalar_field field{"s", scalar_kind::string};
      field.length_unit = unit;
      field.minimum_length = field.maximum_length = (unit == 1 ? 4 : 3) * repetitions;
      scalar_plan exact({field});
      for (const auto& encoded : {utf8, escaped}) {
        scalar_document accepted(exact, "{\"s\":\"" + encoded + "\"}");
        check(accepted.slot(0).text_length == utf8.size());
        check(accepted.allocations().index_allocations == 1);
        ++field.minimum_length;
        ++field.maximum_length;
        scalar_plan too_long({field});
        rejects([&] { scalar_document invalid(too_long, "{\"s\":\"" + encoded + "\"}"); });
        --field.minimum_length;
        --field.maximum_length;
      }
      if (repetitions) {
        --field.minimum_length; --field.maximum_length;
        scalar_plan too_short({field});
        rejects([&] { scalar_document invalid(too_short, "{\"s\":\"" + utf8 + "\"}"); });
      }
    }
  }
  scalar_field floating{"f", scalar_kind::float64};
  floating.float_bits = 32;
  scalar_plan float32({floating});
  scalar_document exact(float32, R"({"f":1.5})");
  rejects([&] { scalar_document rounded(float32, R"({"f":0.1})"); });
  rejects([&] { scalar_document overflow(float32, R"({"f":1e100})"); });
  floating.float_bits = 16;
  rejects([&] { scalar_plan bad({floating}); });
  std::cout << "Shared Rust/C++ simdutf and JSON Unicode contracts passed\n";
}
