#include "json/native/api.h"
#include <myna/values.h>
#include <cstddef>
#include <cstring>
#include <stdexcept>
#include <string>
#include <simdjson.h>
#include <vector>

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
  fields[0].child = fields[1].child = UINT32_MAX;
  JsonRecordPlan root{0, 2}, wide{0, 65}, none{0, 0};
  JsonPlan plan{};
  require(td_json_plan_create(&root, 1, fields, 2, 2, &plan) == TD_JSON_OK);
  require(td_json_plan_create(&root, 1, fields, 2, 2, &plan) == TD_JSON_ARGUMENT);
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
  // Flat plans publish the root entry without a record-table allocation.
  require(result.record_count == 1 && result.records);
  auto* entries = static_cast<const JsonRecordEntry*>(result.records);
  require(entries[0].present == 3 && entries[0].nulls == 0);
  require(entries[0].slots == 0 && entries[0].plan == 0);
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
  require(td_json_plan_create(&wide, 1, fields, 65, 2, &plan) == TD_JSON_LIMIT);
  require(td_json_plan_create(&root, 1, fields, 2, 1, &plan) == TD_JSON_LIMIT);
  fields[0].flags = 8;
  require(td_json_plan_create(&root, 1, fields, 2, 2, &plan) == TD_JSON_ARGUMENT);
  require(!plan.owner);
  require(td_json_plan_create(&none, 1, nullptr, 0, 0, &plan) == TD_JSON_OK);
  require(parse("{}") == TD_JSON_OK);
  require(result.count == 0 && result.present == 0 && result.index_allocations == 0);
  td_json_plan_release(&plan);
  td_json_typed_release(&result);
}
JsonScalarField abi_field(uint8_t* name, uint32_t kind, uint32_t flags,
                          uint32_t child = UINT32_MAX) {
  JsonScalarField field{name, 1, kind, flags, INT64_MIN, INT64_MAX, UINT64_MAX, 100};
  field.child = child;
  return field;
}
void nested() {
  static_assert(sizeof(JsonScalarField) == 88 && offsetof(JsonScalarField, child) == 80, "");
  static_assert(sizeof(JsonRecordPlan) == 8 && alignof(JsonRecordPlan) == 4, "");
  static_assert(sizeof(JsonRecordEntry) == 24 && offsetof(JsonRecordEntry, plan) == 20, "");
  static_assert(sizeof(JsonTypedResult) == 112 && offsetof(JsonTypedResult, records) == 96, "");
  uint8_t a[] = {'a'}, b[] = {'b'}, c[] = {'c'}, d[] = {'d'};
  // root {a: int64, b: repeated record 1}; record 1 {c: repeated int64 0..9, d: record 2 optional nullable}
  // record 2 {a: string}
  JsonScalarField fields[] = {
      abi_field(a, 1, 1), abi_field(b, 5, 1 | 4, 1),
      abi_field(c, 1, 1 | 4), abi_field(d, 5, 2, 2),
      abi_field(a, 4, 1)};
  fields[2].minimum = 0; fields[2].maximum = 9;
  JsonRecordPlan records[] = {{0, 2}, {2, 2}, {4, 1}};
  JsonPlan plan{};
  require(td_json_plan_create(records, 3, fields, 5, 5, &plan) == TD_JSON_OK);
  JsonTypedResult result{};
  auto parse = [&](const std::string& source, uint64_t budget = 100000) {
    auto status = td_json_typed_decode(&plan, reinterpret_cast<const uint8_t*>(source.data()),
        source.size(), 10000, budget, &result);
    if (status != TD_JSON_OK) {
      JsonTypedResult empty{};
      require(std::memcmp(&result, &empty, sizeof(result)) == 0);
    }
    return status;
  };
  require(parse(R"({"b":[{"c":[1,2]},{"c":[],"d":{"a":"deep text"}},{"c":[9],"d":null}],"a":7})")
          == TD_JSON_OK);
  require(result.present == 3 && result.record_count == 5);
  auto* slots = static_cast<const JsonScalarSlot*>(result.slots);
  auto* entries = static_cast<const JsonRecordEntry*>(result.records);
  require(slots[0].bits == 7);
  require(slots[1].bits == ((uint64_t(1) << 32) | 3));
  require(entries[1].plan == 1 && entries[2].plan == 1 && entries[3].plan == 1);
  const auto& c0 = slots[entries[1].slots];
  require((c0.bits & UINT32_MAX) == 2);
  require(slots[(c0.bits >> 32) + 1].bits == 2);
  require(slots[entries[2].slots].bits == 0);
  const auto& d1 = slots[entries[2].slots + 1];
  require(d1.bits == 4 && entries[4].plan == 2 && entries[4].present == 1);
  const auto& text = slots[entries[4].slots];
  require(std::string(reinterpret_cast<const char*>(text.text), text.text_length) == "deep text");
  require(entries[3].nulls == 2 && entries[3].present == 3);
  td_json_plan_release(&plan);
  require(std::string(reinterpret_cast<const char*>(text.text), text.text_length) == "deep text");
  td_json_typed_release(&result);
  require(!result.records && result.record_count == 0);
  require(td_json_plan_create(records, 3, fields, 5, 5, &plan) == TD_JSON_OK);
  require(parse(R"({"a":1,"b":[{"c":[10]}]})") == TD_JSON_CONTRACT);
  require(parse(R"({"a":1,"b":[{"c":[1,null]}]})") == TD_JSON_CONTRACT);
  require(parse(R"({"a":1,"b":[{"c":[],"x":1}]})") == TD_JSON_CONTRACT);
  require(parse(R"({"a":1,"b":[{"c":[],"c":[]}]})") == TD_JSON_CONTRACT);
  require(parse(R"({"a":1,"b":[{}]})") == TD_JSON_CONTRACT);
  require(parse(R"({"a":1,"b":null})") == TD_JSON_CONTRACT);
  require(parse(R"({"a":1,"b":[{"c":[1,]}]})") == TD_JSON_SYNTAX);
  require(parse(R"({"a":1,"b":[]} 1)") == TD_JSON_SYNTAX);
  require(parse(R"({"a":1,"b":[{"c":[1,2,3,4,5,6,7,8]}]})", 400) == TD_JSON_LIMIT);
  require(parse(R"({"a":1,"b":[]})") == TD_JSON_OK);
  require(result.record_count == 1 && result.count == 2);
  td_json_typed_release(&result);
  td_json_plan_release(&plan);
  // Malformed plan tables.
  require(td_json_plan_create(nullptr, 1, fields, 5, 5, &plan) == TD_JSON_ARGUMENT);
  require(td_json_plan_create(records, 0, fields, 5, 5, &plan) == TD_JSON_ARGUMENT);
  require(td_json_plan_create(records, 3, fields, 4, 5, &plan) == TD_JSON_ARGUMENT);
  require(td_json_plan_create(records, 2, fields, 5, 5, &plan) == TD_JSON_ARGUMENT);
  records[1].fields_offset = 1;
  require(td_json_plan_create(records, 3, fields, 5, 5, &plan) == TD_JSON_ARGUMENT);
  records[1].fields_offset = 2;
  require(td_json_plan_create(records, TD_JSON_MAX_PLAN_RECORDS + 1, fields, 5, 5, &plan)
          == TD_JSON_LIMIT);
  fields[3].child = 3;
  require(td_json_plan_create(records, 3, fields, 5, 5, &plan) == TD_JSON_ARGUMENT);
  fields[3].child = 0; // record 1 -> root -> record 1 cycle
  require(td_json_plan_create(records, 3, fields, 5, 5, &plan) == TD_JSON_ARGUMENT);
  fields[3].child = 2;
  fields[0].child = 0;
  require(td_json_plan_create(records, 3, fields, 5, 5, &plan) == TD_JSON_ARGUMENT);
  fields[0].child = UINT32_MAX;
  fields[4].kind = 6;
  require(td_json_plan_create(records, 3, fields, 5, 5, &plan) == TD_JSON_ARGUMENT);
  fields[4].kind = 4;
  fields[4].flags = 8;
  require(td_json_plan_create(records, 3, fields, 5, 5, &plan) == TD_JSON_ARGUMENT);
  fields[4].flags = 1;
  require(!plan.owner);
  // Depth: a chain of TD_JSON_MAX_PLAN_DEPTH records is accepted; one more is not.
  std::vector<JsonScalarField> chain;
  std::vector<JsonRecordPlan> links;
  for (uint32_t i = 0; i <= TD_JSON_MAX_PLAN_DEPTH; ++i) {
    chain.push_back(abi_field(c, 5, 1, i + 1));
    links.push_back({i, 1});
  }
  chain.back() = abi_field(c, 1, 1);
  require(td_json_plan_create(links.data(), links.size(), chain.data(), chain.size(),
                              chain.size(), &plan) == TD_JSON_ARGUMENT);
  chain[TD_JSON_MAX_PLAN_DEPTH - 1] = abi_field(c, 1, 1);
  require(td_json_plan_create(links.data(), TD_JSON_MAX_PLAN_DEPTH, chain.data(),
                              TD_JSON_MAX_PLAN_DEPTH, chain.size(), &plan) == TD_JSON_OK);
  td_json_plan_release(&plan);
}
void scalar_contracts() {
  uint8_t name[] = {'v'};
  JsonScalarField field{name, 1, 4, 1, INT64_MIN, INT64_MAX, UINT64_MAX, 100};
  field.child = UINT32_MAX;
  JsonRecordPlan single{0, 1};
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
  require(td_json_plan_create(&single, 1, &field, 1, 1, &plan) == TD_JSON_OK);
  require(parse(R"({"v":"\ud83d\ude00"})") == TD_JSON_OK);
  require(parse(R"({"v":"a"})") == TD_JSON_CONTRACT);
  td_json_plan_release(&plan);
  field.length_unit = 2;
  field.minimum_length = field.maximum_length = 1;
  require(td_json_plan_create(&single, 1, &field, 1, 1, &plan) == TD_JSON_OK);
  require(parse(R"({"v":"\ud83d\ude00"})") == TD_JSON_OK);
  require(parse(R"({"v":"ab"})") == TD_JSON_CONTRACT);
  td_json_plan_release(&plan);
  field.kind = 3;
  field.length_unit = 0;
  field.float_bits = 32;
  require(td_json_plan_create(&single, 1, &field, 1, 1, &plan) == TD_JSON_OK);
  require(parse(R"({"v":1.5})") == TD_JSON_OK);
  require(parse(R"({"v":0.1})") == TD_JSON_CONTRACT);
  td_json_plan_release(&plan);
}
void adopted() {
  const std::string source = R"({"s":"abcdefghijklmnopqrstuvwxyz0123456789"})";
  JsonResult result{};
  auto decode_adopted = [&](std::vector<uint8_t>& buffer, uint64_t capacity) {
    return td_json_decode_adopted(buffer.data(), source.size(), capacity,
        1024 * 1024, 1024 * 1024, 10000, 1024 * 1024, 128, &result);
  };
  std::vector<uint8_t> padded(source.size() + simdjson::SIMDJSON_PADDING, 0);
  std::memcpy(padded.data(), source.data(), source.size());
  require(decode_adopted(padded, padded.size()) == TD_JSON_OK);
  require(result.copied_input_bytes == 0 && result.padded_input_bytes == padded.size());
  auto* view = static_cast<const myna_value_view*>(result.view);
  // Region 1 is the adopted memory itself: the string references the caller's buffer.
  require(view->regions[0].data == padded.data());
  require(view->nodes[2].payload >> 32 == 1);
  td_json_release(&result);
  std::vector<uint8_t> unpadded(source.size() + simdjson::SIMDJSON_PADDING - 1, 0);
  std::memcpy(unpadded.data(), source.data(), source.size());
  require(decode_adopted(unpadded, unpadded.size()) == TD_JSON_OK);
  require(result.copied_input_bytes == source.size());
  view = static_cast<const myna_value_view*>(result.view);
  require(view->regions[0].data != unpadded.data());
  td_json_release(&result);
  require(decode_adopted(padded, source.size() - 1) == TD_JSON_ARGUMENT);
  require(!result.owner);
  require(td_json_decode_adopted(nullptr, 1, 100, 1024, 1024, 10, 1024, 128, &result) == TD_JSON_ARGUMENT);
}
int main() {
  adopted();
  scalar_contracts();
  typed();
  nested();
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
  static_assert(sizeof(JsonResult) == 88, "ABI 4 publishes lexical spans");
  require(first.lexical_count == 5 && first.lexical);
  require(static_cast<const uint64_t*>(first.lexical)[2] == ((uint64_t(5) << 32) | 3));
  require(first.control_bytes > 0 && first.index_peak_bytes >= first.index_retained_bytes);
  auto number = view->nodes[2];
  require(number.tag == MYNA_VALUE_I64 && number.payload == 123);
  const auto span = static_cast<const uint64_t*>(first.lexical)[2];
  require(std::memcmp(view->regions[0].data + (span >> 32), "123", uint32_t(span)) == 0);
  auto saved = first;
  require(decode("[]", first) == TD_JSON_ARGUMENT);
  require(std::memcmp(&first, &saved, sizeof(first)) == 0);
  JsonResult sibling{};
  require(decode("[false]", sibling) == TD_JSON_OK);
  td_json_release(&sibling);
  require(view->node_count == 5);
  td_json_release(&first);
  td_json_release(&first);
  require(!first.lexical && first.lexical_count == 0);
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
