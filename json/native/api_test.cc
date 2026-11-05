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
int main() {
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
