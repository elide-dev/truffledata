#include "json/native/api.h"
#include <myna/values.h>
#include <cstring>
#include <stdexcept>
#include <string>

void require(bool condition) { if (!condition) throw std::runtime_error("ABI check failed"); }
int32_t decode(const std::string& source, JsonResult& result, uint64_t budget = 1024 * 1024) {
  return td_json_decode(reinterpret_cast<const uint8_t*>(source.data()), source.size(),
                        1024 * 1024, budget, 10000, 1024 * 1024, 128, &result);
}
void typed() {
  uint8_t n[] = {'n'}, s[] = {'s'};
  JsonScalarField fields[] = {
      {n, 1, 1, 1, -128, 127, UINT64_MAX, 100},
      {s, 1, 4, 2, INT64_MIN, INT64_MAX, UINT64_MAX, 100}};
  JsonPlan plan{};
  require(td_json_plan_create(fields, 2, 2, &plan) == TD_JSON_OK);
  require(td_json_plan_create(fields, 2, 2, &plan) == TD_JSON_ARGUMENT);
  n[0] = 'x'; s[0] = 'y';
  struct Guard { uint64_t before = 11; JsonTypedResult result{}; uint64_t after = 22; } guard;
  auto& result = guard.result;
  auto parse = [&](const std::string& source, uint64_t budget = 10000) {
    return td_json_typed_decode(&plan, reinterpret_cast<const uint8_t*>(source.data()),
        source.size(), 10000, budget, &result);
  };
  require(parse(R"({"s":"long retained text \u4e2d","n":-128})") == TD_JSON_OK);
  require(result.count == 2 && result.present == 3 && result.nulls == 0);
  require(result.index_allocations == 1 && result.index_retained_bytes == 2 * sizeof(JsonScalarSlot));
  auto* slots = static_cast<const JsonScalarSlot*>(result.slots);
  require(slots[0].bits == static_cast<uint64_t>(-128));
  require(parse("{}") == TD_JSON_ARGUMENT);
  td_json_typed_release(&result);
  require(parse(R"({"n":128})") == TD_JSON_CONTRACT);
  require(parse(R"({"n":0,"s":"\q"})") == TD_JSON_SYNTAX);
  require(parse(R"({"n":0})", 1) == TD_JSON_LIMIT);
  require(!result.owner && !result.slots);
  require(parse(R"({"s":null,"n":127})") == TD_JSON_OK);
  require(result.present == 3 && result.nulls == 2);
  td_json_typed_release(&result);
  require(parse(R"({"n":0,"s":"retained after plan close"})") == TD_JSON_OK);
  td_json_plan_release(&plan);
  td_json_plan_release(&plan);
  slots = static_cast<const JsonScalarSlot*>(result.slots);
  require(std::string(reinterpret_cast<const char*>(slots[1].text), slots[1].text_length) == "retained after plan close");
  td_json_typed_release(&result);
  td_json_typed_release(&result);
  require(parse("{}") == TD_JSON_ARGUMENT);
  require(guard.before == 11 && guard.after == 22);
  require(td_json_plan_create(fields, 65, 2, &plan) == TD_JSON_LIMIT);
  require(td_json_plan_create(fields, 2, 1, &plan) == TD_JSON_LIMIT);
  fields[0].flags = 4;
  require(td_json_plan_create(fields, 2, 2, &plan) == TD_JSON_ARGUMENT);
  require(!plan.owner);
  require(td_json_plan_create(nullptr, 0, 0, &plan) == TD_JSON_OK);
  require(parse("{}") == TD_JSON_OK);
  require(result.count == 0 && result.present == 0 && result.index_allocations == 0);
  td_json_plan_release(&plan);
  td_json_typed_release(&result);
}
void scalar_contracts() {
  uint8_t name[] = {'v'};
  JsonScalarField field{name, 1, 4, 1, INT64_MIN, INT64_MAX, UINT64_MAX, 100};
  field.length_unit = 1;
  field.minimum_length = field.maximum_length = 2;
  JsonPlan plan{};
  JsonTypedResult result{};
  auto parse = [&](const std::string& source) {
    auto status = td_json_typed_decode(&plan, reinterpret_cast<const uint8_t*>(source.data()),
        source.size(), 10000, 10000, &result);
    if (status != TD_JSON_OK) require(!result.owner && !result.slots);
    td_json_typed_release(&result);
    return status;
  };
  require(td_json_plan_create(&field, 1, 1, &plan) == TD_JSON_OK);
  require(parse(R"({"v":"\ud83d\ude00"})") == TD_JSON_OK);
  require(parse(R"({"v":"a"})") == TD_JSON_CONTRACT);
  td_json_plan_release(&plan);
  field.length_unit = 2;
  field.minimum_length = field.maximum_length = 1;
  require(td_json_plan_create(&field, 1, 1, &plan) == TD_JSON_OK);
  require(parse(R"({"v":"\ud83d\ude00"})") == TD_JSON_OK);
  require(parse(R"({"v":"ab"})") == TD_JSON_CONTRACT);
  td_json_plan_release(&plan);
  field.kind = 3;
  field.length_unit = 0;
  field.float_bits = 32;
  require(td_json_plan_create(&field, 1, 1, &plan) == TD_JSON_OK);
  require(parse(R"({"v":1.5})") == TD_JSON_OK);
  require(parse(R"({"v":0.1})") == TD_JSON_CONTRACT);
  td_json_plan_release(&plan);
}
int main() {
  scalar_contracts();
  typed();
  require(std::strcmp(reinterpret_cast<const char*>(td_json_fingerprint()), TRUFFLEDATA_JSON_SEAM_ABI) == 0);
  struct guarded { uint64_t before = 91; JsonResult result{}; uint64_t after = 92; } buffer;
  auto& first = buffer.result;
  {
    std::string source = R"({"a":123,"b":"long text retained beyond input lifetime"})";
    require(decode(source, first) == TD_JSON_OK);
    source.assign(source.size(), 'x');
  }
  auto* view = static_cast<const myna_value_view*>(first.view);
  require(view->version == MYNA_VALUE_ABI && view->node_count == 5);
  require(first.control_bytes > 0 && first.index_peak_bytes >= first.index_retained_bytes);
  auto number = view->nodes[2];
  const auto& region = view->regions[(number.payload >> 32) - 1];
  require(std::memcmp(region.data + uint32_t(number.payload), "123", 3) == 0);
  auto saved = first;
  require(decode("[]", first) == TD_JSON_ARGUMENT);
  require(std::memcmp(&first, &saved, sizeof(first)) == 0);
  JsonResult sibling{};
  require(decode("[false]", sibling) == TD_JSON_OK);
  td_json_release(&sibling);
  require(view->node_count == 5);
  td_json_release(&first);
  td_json_release(&first);
  JsonResult empty{};
  require(std::memcmp(&first, &empty, sizeof(first)) == 0);
  for (auto source : {"[1,]", "{\"a\":1,\"a\":2}", "{} true", "[undefined]"}) {
    require(decode(source, first) == TD_JSON_SYNTAX);
    require(std::memcmp(&first, &empty, sizeof(first)) == 0);
  }
  require(decode("{}", first, 0) == TD_JSON_LIMIT);
  require(td_json_decode(nullptr, 1, 1, 1, 1, 1, 1, &first) == TD_JSON_ARGUMENT);
  require(td_json_decode(nullptr, 1, 1, 1, 1, 1, 1, nullptr) == TD_JSON_ARGUMENT);
  const uint8_t scalar[] = {'1'};
  require(td_json_decode(scalar, UINT64_MAX, UINT64_MAX, 1, 1, 1, 1, &first) == TD_JSON_LIMIT);
  require(td_json_decode(scalar, 1, 1, 1000, 1, 1, 129, &first) == TD_JSON_ARGUMENT);
  require(td_json_decode(scalar, 1, 1, 1000, 0, 1, 1, &first) == TD_JSON_LIMIT);
  require(std::memcmp(&first, &empty, sizeof(first)) == 0);
  require(buffer.before == 91 && buffer.after == 92);
  require(decode("true", first) == TD_JSON_OK);
  td_json_release(&first);
}
