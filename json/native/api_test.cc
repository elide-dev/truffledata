#include "json/native/testing/myna_oracle.h"
#include "json/native/api.h"
#include "json/native/session.h"
#include <memory_resource>
#include <myna/values.h>
#include <cstddef>
#include <cstring>
#include <stdexcept>
#include <string>
#include <simdjson.h>
#include <simdutf.h>
#include <vector>

#define require(condition) do { if (!(condition)) throw std::runtime_error("ABI check failed at line " + std::to_string(__LINE__) + ": " #condition); } while (false)
int32_t decode(const std::string& source, JsonResult& result, uint64_t budget = 1024 * 1024) {
  return td_json_decode(reinterpret_cast<const uint8_t*>(source.data()), source.size(),
                        1024 * 1024, budget, 10000, 1024 * 1024, 128, &result);
}
namespace c = truffledata::tape::ctape;
const td_tape_view& tape_of(const JsonResult& r) { return *static_cast<const td_tape_view*>(r.view); }
std::string word_text(const JsonResult& r, uint64_t i) { return std::string(c::text(tape_of(r), i)); }
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
  auto* view = static_cast<const td_tape_view*>(result.view);
  // Input is the adopted memory itself: the string references the caller's buffer.
  require(view->input == padded.data());
  require((view->words[3] & c::owned_flag) == 0);
  td_json_release(&result);
  std::vector<uint8_t> unpadded(source.size() + simdjson::SIMDJSON_PADDING - 1, 0);
  std::memcpy(unpadded.data(), source.data(), source.size());
  require(decode_adopted(unpadded, unpadded.size()) == TD_JSON_OK);
  require(result.copied_input_bytes == source.size());
  view = static_cast<const td_tape_view*>(result.view);
  require(view->input != unpadded.data());
  td_json_release(&result);
  require(decode_adopted(padded, source.size() - 1) == TD_JSON_ARGUMENT);
  require(!result.owner);
  require(td_json_decode_adopted(nullptr, 1, 100, 1024, 1024, 10, 1024, 128, &result) == TD_JSON_ARGUMENT);
}

// ---- Sessions (plan Task B1) ----------------------------------------------------------------
class fault_memory final : public std::pmr::memory_resource {
public:
  size_t fail_at, calls = 0, live = 0;
  explicit fault_memory(size_t fail) : fail_at(fail) {}
private:
  void* do_allocate(size_t bytes, size_t alignment) override {
    if (calls++ == fail_at) throw std::bad_alloc();
    live += bytes;
    return std::pmr::new_delete_resource()->allocate(bytes, alignment);
  }
  void do_deallocate(void* pointer, size_t bytes, size_t alignment) override {
    live -= bytes;
    std::pmr::new_delete_resource()->deallocate(pointer, bytes, alignment);
  }
  bool do_is_equal(const std::pmr::memory_resource& other) const noexcept override { return this == &other; }
};
std::string text_of(const myna_value_view* view, size_t index) {
  const auto& node = view->nodes[index];
  if ((node.tag & MYNA_VALUE_REGION_TEXT) == 0) {
    std::string out;
    for (uint32_t i = 0; i < node.length; ++i) out += char(myna_value_inline_byte(&node, i));
    return out;
  }
  auto region = node.payload >> 32;
  const uint8_t* data = region == 0 ? view->bytes : view->regions[region - 1].data;
  return std::string(reinterpret_cast<const char*>(data + uint32_t(node.payload)), node.length);
}
// Sessions publish compact tapes directly.
void sessions() {
  static_assert(sizeof(JsonLimitsStruct) == 40, "session limits");
  JsonLimitsStruct limits{1024 * 1024, 1024 * 1024, 100000, 1024 * 1024, 128};
  void* s = td_json_session_create(&limits);
  require(s != nullptr);
  auto run = [&](const std::string& source, JsonResult& out, uint32_t flags = 0) {
    return td_json_session_decode(s, reinterpret_cast<const uint8_t*>(source.data()), source.size(),
                                  flags, nullptr, 0, &out);
  };
  JsonResult borrowed{}, kept{};
  const std::string first = R"({"s":"first document text beyond inline","n":1})";
  require(run(first, borrowed) == TD_JSON_OK);
  // Borrowed results have no owner: td_json_release on them is a harmless no-op.
  require(!borrowed.owner && borrowed.view && tape_of(borrowed).word_count == 7);
  auto* view = static_cast<const td_tape_view*>(borrowed.view);
  const uint64_t* words = view->words;
  require(std::string(c::text(*view, 3)) == "first document text beyond inline");
  // Retain moves storage: the retained descriptor points at the very same nodes.
  require(td_json_session_retain(s, &kept) == TD_JSON_OK);
  require(kept.owner && tape_of(kept).words == words);
  require(td_json_session_retain(s, &kept) == TD_JSON_ARGUMENT);  // live result, and no document
  JsonResult other{};
  require(td_json_session_retain(s, &other) == TD_JSON_ARGUMENT);  // nothing borrowed any more
  // The next decode reuses the session; the retained document is untouched.
  require(run(R"({"s":"second document overwrites session storage","n":2})", borrowed) == TD_JSON_OK);
  auto* retained = static_cast<const td_tape_view*>(kept.view);
  require(std::string(c::text(*retained, 3)) == "first document text beyond inline");
  require(c::low(retained->words[5]) == 45 && c::field(retained->words[5]) == 1);
  // A failed decode publishes nothing and leaves the session reusable.
  for (auto bad : {"[1,]", "{\"a\":1,\"a\":2}", "[\"\\q\"]", "01"}) {
    require(run(bad, borrowed) == TD_JSON_SYNTAX);
    JsonResult empty{};
    require(std::memcmp(&borrowed, &empty, sizeof(empty)) == 0);
    require(td_json_session_retain(s, &other) == TD_JSON_ARGUMENT);
  }
  require(run("[true]", borrowed) == TD_JSON_OK);
  // Reset invalidates the borrow (O(1), leaf contract); there is nothing left to retain.
  td_json_session_reset(s);
  require(td_json_session_retain(s, &other) == TD_JSON_ARGUMENT);
  // Unknown flags and text buffers are rejected until their tasks define them.
  require(run("[]", borrowed, 1) == TD_JSON_ARGUMENT);
  uint8_t text[8];
  require(td_json_session_decode(s, reinterpret_cast<const uint8_t*>("[]"), 2, 0, text, 8, &borrowed) == TD_JSON_ARGUMENT);
  // The leaf decode is bounded by TD_JSON_CRITICAL_LIMIT.
  std::string critical = "[" + std::string(TD_JSON_CRITICAL_LIMIT - 2, ' ') + "]";
  require(critical.size() == TD_JSON_CRITICAL_LIMIT);
  require(td_json_session_decode_small(s, reinterpret_cast<const uint8_t*>(critical.data()), critical.size(), 0, nullptr, 0, &borrowed) == TD_JSON_OK);
  critical += " ";
  require(td_json_session_decode_small(s, reinterpret_cast<const uint8_t*>(critical.data()), critical.size(), 0, nullptr, 0, &borrowed) == TD_JSON_ARGUMENT);
  require(!borrowed.view);
  // Session limits apply per document.
  require(run(std::string(2 * 1024 * 1024, ' '), borrowed) == TD_JSON_LIMIT);
  td_json_session_release(s);
  // The retained result outlives its session.
  require(std::string(c::text(*retained, 3)) == "first document text beyond inline");
  td_json_release(&kept);
  td_json_release(&kept);
  require(!kept.owner && !kept.view);
  JsonLimitsStruct deep = limits;
  deep.max_depth = 129;
  require(td_json_session_create(&deep) == nullptr);
  require(td_json_session_create(nullptr) == nullptr);
}
void session_allocations() {
  using truffledata::json::session;
  const std::string doc = R"({"id":1007,"name":"item-1","note":"line \"1\" with \\ escapes\n and tail","tags":["t0","t1"],"dims":{"w":3,"h":1.5}})";
  std::string wide = "{";
  for (int i = 0; i < 40; ++i) wide += (i ? ",\"k" : "\"k") + std::to_string(i) + "\":[" + std::to_string(i) + "]";
  wide += "}";
  std::string big = "[";
  for (int i = 0; i < 3000; ++i) big += (i ? "," : "") + doc;
  big += "]";
  // Every upstream allocation of a decode/retain/decode sequence can fail; the session stays
  // consistent and everything is freed once the session and retained owner are destroyed.
  // Compact sessions also fail cleanly inside validate-only walks.
  {
  auto sequence = [&](std::pmr::memory_resource* memory, size_t* calls) {
    session::retained* owner = nullptr;
    {
      session s(myna::value_limits{}, truffledata::tape::storage_limits{}, memory);
      try {
        s.decode(doc.data(), doc.size());
        s.decode(wide.data(), wide.size(), session::encoding::utf8, true);
        s.decode(big.data(), big.size(), session::encoding::utf8, true);
        s.decode(wide.data(), wide.size());
        owner = s.retain();
        s.decode(big.data(), big.size());
        // Task D2: the text table is an arena allocation too (the big document's is large).
        std::vector<uint8_t> text(2 * big.size() + 16);
        const uint64_t* table = nullptr;
        require(s.write_text(text.data(), text.size(), table) != truffledata::tape::text_too_small);
        s.decode(doc.data(), doc.size());
        require(s.write_text(text.data(), text.size(), table) != truffledata::tape::text_too_small);
      } catch (...) {
        if (owner) owner->destroy();
        throw;
      }
    }
    if (calls) *calls = static_cast<fault_memory*>(memory)->calls;
    require(truffledata::tape::ctape::text(owner->tape, 2) == "k0");
    owner->destroy();
  };
  fault_memory counting(SIZE_MAX);
  size_t total = 0;
  sequence(&counting, &total);
  require(counting.live == 0 && total > 3);
  for (size_t i = 0; i < total; ++i) {
    fault_memory memory(i);
    bool rejected = false;
    try { sequence(&memory, nullptr); } catch (const std::bad_alloc&) { rejected = true; }
    require(rejected && memory.live == 0);
  }
  }
  // Steady state: after warm-up a session decodes without any upstream allocation, and keeps a
  // bounded number of blocks.
  session s(myna::value_limits{}, truffledata::tape::storage_limits{});
  for (const auto& source : {doc, wide}) s.decode(source.data(), source.size());
  size_t blocks = s.arena_blocks(), allocations = s.memory().allocations();
  for (int i = 0; i < 10000; ++i) {
    const auto& source = i % 2 ? doc : wide;
    auto d = s.decode(source.data(), source.size());
    require(d.index_allocations == 0);
  }
  require(s.memory().allocations() == allocations && s.arena_blocks() == blocks);
  // Validate-only has independent scratch capacity, also retained after its own warm-up.
  for (const auto& source : {doc, wide}) s.decode(source.data(), source.size(), session::encoding::utf8, true);
  allocations = s.memory().allocations();
  for (int i = 0; i < 10000; ++i) {
    const auto& source = i % 2 ? doc : wide;
    auto d = s.decode(source.data(), source.size(), session::encoding::utf8, true);
    require(d.index_allocations == 0 && !d.tape && !s.live());
  }
  require(s.memory().allocations() == allocations && s.arena_blocks() == blocks);
  // Big documents use large allocations that are released before the next decode; a parser and
  // input buffer that grew past 1 MiB are not kept.
  std::string huge = "[";
  for (int i = 0; i < 12000; ++i) huge += (i ? "," : "") + doc;
  huge += "]";
  s.decode(huge.data(), huge.size());
  require(s.input_capacity() > session::retained_input_limit);
  auto d = s.decode(doc.data(), doc.size());
  require(s.input_capacity() <= session::retained_input_limit);
  require(s.parser_capacity() <= session::retained_input_limit);
  require(s.memory().current() <= truffledata::tape::block_arena::retained_blocks * truffledata::tape::block_arena::max_block);
  (void)d;
}

// Decodes the arena entry of node `index` back to UTF-16 code units.
std::u16string arena_text(const JsonResult& result, const uint8_t* arena, size_t index) {
  uint64_t entry = static_cast<const uint64_t*>(result.text_table)[index];
  size_t offset = entry >> 32, length = (entry >> 1) & 0x7fffffffu;
  std::u16string out;
  if (entry & 1) {
    require(offset % 2 == 0 && length % 2 == 0);
    for (size_t i = 0; i < length; i += 2) out += char16_t(arena[offset + i] | arena[offset + i + 1] << 8);
  } else {
    for (size_t i = 0; i < length; ++i) out += char16_t(arena[offset + i]);
  }
  return out;
}
std::u16string utf16_of(const std::string& utf8) {
  std::u16string out(simdutf::utf16_length_from_utf8(utf8.data(), utf8.size()), u'\0');
  require(simdutf::convert_valid_utf8_to_utf16le(utf8.data(), utf8.size(), out.data()) == out.size());
  return out;
}
// Task D2: per-document text arenas.
void text_arenas() {
  JsonLimitsStruct limits{1024 * 1024, 1024 * 1024, 100000, 1024 * 1024, 128};
  void* s = td_json_session_create(&limits);
  require(s != nullptr);
  JsonResult result{};
  alignas(8) uint8_t arena[4096];
  auto run = [&](const std::string& source, uint64_t capacity, uint32_t flags = TD_JSON_WANT_TEXT) {
    return td_json_session_decode(s, reinterpret_cast<const uint8_t*>(source.data()), source.size(), flags,
                                  arena, capacity, &result);
  };
  // Keys and strings, ASCII, Latin-1 range, BMP, surrogate pairs, escapes, inline and region text.
  const std::string doc =
      R"({"id":"x","été":"café au lait, long enough for a region","k":"😀 中",)"
      R"("":"","n":[1,"plain ascii string beyond sixteen bytes"],"ÿ":"Ā"})";
  require(run(doc, 2 * doc.size() + 16) == TD_JSON_OK);
  require(result.text_table && result.text_bytes > 0 && result.text_bytes <= 2 * doc.size());
  auto* view = static_cast<const td_tape_view*>(result.view);
  const auto* table = static_cast<const uint64_t*>(result.text_table);
  size_t strings = 0;
  for (size_t i = 0; i < view->word_count; i = c::step(i, view->words[i])) {
    if (c::kind(view->words[i]) != c::kind_string) { require(table[i] == 0); continue; }
    ++strings;
    const std::string decoded(c::text(*view, i));
    require(arena_text(result, arena, i) == utf16_of(decoded));
    if (decoded == "id" || decoded == "été" || decoded == "café au lait, long enough for a region") require((table[i] & 1) == 0);
    if (decoded == "😀 中") require((table[i] & 1) == 1 && arena_text(result, arena, i).size() == 4);
    if (decoded == "Ā") require((table[i] & 1) == 1);
    if ((view->words[i] & c::wide_flag) != 0) require(table[i + 1] == 0);
  }
  require(strings == 12);
  // The bound holds by construction: "é" is 6 input bytes and 2 output bytes (or 1 as Latin-1).
  require(run(R"(["é"])", 2 * 10 + 16) == TD_JSON_OK && result.text_bytes == 1);
  require(run(R"(["Ā"])", 2 * 10 + 16) == TD_JSON_OK && result.text_bytes == 2);
  // A caller-shrunk capacity reports TD_JSON_TEXT_TOO_SMALL, publishes nothing, and the session
  // decodes the next document.
  for (uint64_t capacity : {uint64_t(0), uint64_t(1), uint64_t(10)}) {
    require(run(doc, capacity) == TD_JSON_TEXT_TOO_SMALL);
    JsonResult empty{};
    require(std::memcmp(&result, &empty, sizeof(empty)) == 0);
  }
  require(run(doc, sizeof(arena)) == TD_JSON_OK);
  // Argument checks: text needs the flag and an even buffer, the flag needs a buffer.
  require(td_json_session_decode(s, reinterpret_cast<const uint8_t*>("[]"), 2, 0, arena, 8, &result) == TD_JSON_ARGUMENT);
  require(td_json_session_decode(s, reinterpret_cast<const uint8_t*>("[]"), 2, TD_JSON_WANT_TEXT, nullptr, 0, &result) == TD_JSON_ARGUMENT);
  require(td_json_session_decode(s, reinterpret_cast<const uint8_t*>("[]"), 2, TD_JSON_WANT_TEXT, arena + 1, 8, &result) == TD_JSON_ARGUMENT);
  require(td_json_session_decode(s, reinterpret_cast<const uint8_t*>("[]"), 2, 64, nullptr, 0, &result) == TD_JSON_ARGUMENT);
  // Retained decodes never carry a text arena.
  require(run(doc, sizeof(arena), 0) == TD_JSON_ARGUMENT);
  require(td_json_session_decode(s, reinterpret_cast<const uint8_t*>(doc.data()), doc.size(), 0, nullptr, 0, &result) == TD_JSON_OK);
  require(!result.text_table && result.text_bytes == 0);

  td_json_session_release(s);
}

// Task D3: UTF-16 and Latin-1 input.
void utf16_input() {
  JsonLimitsStruct limits{1024 * 1024, 1024 * 1024, 100000, 1024 * 1024, 128};
  void* s = td_json_session_create(&limits);
  require(s != nullptr);
  JsonResult result{};
  alignas(8) uint8_t arena[4096];
  // Task D3: UTF-16LE input parses to the same tape as its UTF-8 spelling.
  const std::string utf8 = R"({"clé":"中文 😀","n":[1.5,-0,1E400],"é":"é"})";
  std::u16string units = utf16_of(utf8);
  auto same_tape = [&](const JsonResult& a, const JsonResult& b) {
    const auto& x = tape_of(a); const auto& y = tape_of(b);
    require(x.word_count == y.word_count && x.pool_bytes == y.pool_bytes && x.directory_count == y.directory_count);
    require(std::memcmp(x.words, y.words, x.word_count * 8) == 0);
    require(!x.pool_bytes || std::memcmp(x.pool, y.pool, x.pool_bytes) == 0);
    require(!x.directory_count || std::memcmp(x.directory, y.directory, x.directory_count * 8) == 0);
  };
  void* reference = td_json_session_create(&limits);
  JsonResult expected{};
  require(td_json_session_decode(reference, reinterpret_cast<const uint8_t*>(utf8.data()), utf8.size(), 0, nullptr, 0, &expected) == TD_JSON_OK);
  require(td_json_session_decode(s, reinterpret_cast<const uint8_t*>(units.data()), 2 * units.size(), TD_JSON_UTF16_INPUT, nullptr, 0, &result) == TD_JSON_OK);
  same_tape(result, expected);
  // Lexical spans and copied_input_bytes describe the transcoded UTF-8 input.
  require(result.copied_input_bytes == utf8.size());
  require(tape_of(result).input_bytes == tape_of(expected).input_bytes);
  // UTF-16 input with a text arena.
  require(td_json_session_decode(s, reinterpret_cast<const uint8_t*>(units.data()), 2 * units.size(),
                                 TD_JSON_UTF16_INPUT | TD_JSON_WANT_TEXT, arena, 2 * 2 * units.size() + 16, &result) == TD_JSON_OK);
  require(arena_text(result, arena, 2) == u"clé" && arena_text(result, arena, 3) == u"中文 😀");
  // Unpaired surrogates, odd lengths and misaligned input are rejected; the session survives.
  for (std::u16string bad : {std::u16string(u"[\"") + char16_t(0xd800) + u"\"]", std::u16string(u"[\"") + char16_t(0xdc00) + u"x\"]"}) {
    require(td_json_session_decode(s, reinterpret_cast<const uint8_t*>(bad.data()), 2 * bad.size(), TD_JSON_UTF16_INPUT, nullptr, 0, &result) == TD_JSON_SYNTAX);
    require(!result.view);
  }
  require(td_json_session_decode(s, reinterpret_cast<const uint8_t*>(units.data()), 2 * units.size() - 1, TD_JSON_UTF16_INPUT, nullptr, 0, &result) == TD_JSON_ARGUMENT);
  alignas(2) uint8_t shifted[64] = {};
  std::memcpy(shifted + 1, u"[1]", 6);
  require(td_json_session_decode(s, shifted + 1, 6, TD_JSON_UTF16_INPUT, nullptr, 0, &result) == TD_JSON_ARGUMENT);
  // Latin-1 input: every byte is one code point.
  const std::string latin1 = "{\"caf\xe9\":\"\xff\"}";
  require(td_json_session_decode(s, reinterpret_cast<const uint8_t*>(latin1.data()), latin1.size(), TD_JSON_LATIN1_INPUT, nullptr, 0, &result) == TD_JSON_OK);
  require(word_text(result, 2) == "café"
          && word_text(result, 3) == "ÿ");
  // Limits apply to the transcoded UTF-8 length.
  JsonLimitsStruct tight = limits;
  tight.max_input_bytes = 8;
  void* small = td_json_session_create(&tight);
  std::u16string wide = u"[\"中中\"]";  // 12 UTF-16 bytes, 10 UTF-8 bytes
  require(td_json_session_decode(small, reinterpret_cast<const uint8_t*>(wide.data()), 2 * wide.size(), TD_JSON_UTF16_INPUT, nullptr, 0, &result) == TD_JSON_LIMIT);
  std::u16string fits = u"[\"中\"]";  // 7 UTF-8 bytes
  require(td_json_session_decode(small, reinterpret_cast<const uint8_t*>(fits.data()), 2 * fits.size(), TD_JSON_UTF16_INPUT, nullptr, 0, &result) == TD_JSON_OK);
  td_json_session_release(small);
  td_json_session_release(reference);
  // Flag combinations: UTF-16 and Latin-1 input are exclusive.
  require(td_json_session_decode(s, reinterpret_cast<const uint8_t*>(u"[]"), 4, TD_JSON_UTF16_INPUT | TD_JSON_LATIN1_INPUT, nullptr, 0, &result) == TD_JSON_ARGUMENT);
  td_json_session_release(s);
}

// ---- ABI 8: compact sessions, validate-only and explicit Myna interchange ----------------------
void compact_sessions() {
  static_assert(sizeof(TapeView) == 88 && offsetof(TapeView, meta_count) == 80, "ABI 8 TapeView");
  // Compact descriptors contain text spans; JsonResult needs no separate lexical/hash tables.
  static_assert(sizeof(JsonResult) == 88 && offsetof(JsonResult, text_table) == 72, "ABI 8 JsonResult");
  static_assert(sizeof(JsonLimitsStruct) == 40 && offsetof(JsonLimitsStruct, flags) == 36, "ABI 8 limits");
  JsonLimitsStruct limits{1024 * 1024, 1024 * 1024, 100000, 1024 * 1024, 128, 0};
  JsonLimitsStruct unknown = limits;
  unknown.flags = 2;
  require(td_json_session_create(&unknown) == nullptr);
  void* s = td_json_session_create(&limits);
  require(s != nullptr);
  auto run = [&](const std::string& source, JsonResult& out, uint32_t flags = 0) {
    return td_json_session_decode(s, reinterpret_cast<const uint8_t*>(source.data()), source.size(), flags, nullptr, 0, &out);
  };
  JsonResult borrowed{}, kept{};
  const std::string first = R"({"s":"first document text beyond inline","n":1,"e":"\u00e9t\u00e9"})";
  require(run(first, borrowed) == TD_JSON_OK);
  require(!borrowed.owner && borrowed.view);
  const td_tape_view& view = tape_of(borrowed);
  require(view.version == TD_TAPE_VIEW_VERSION && c::validate(view) == nullptr && view.directory_count == 3);
  require(view.input_bytes == first.size() && borrowed.copied_input_bytes == first.size());
  require(word_text(borrowed, 3) == "first document text beyond inline");
  require(word_text(borrowed, 8) == "\xc3\xa9t\xc3\xa9" && (view.words[8] & c::owned_flag));
  // The number word's span is its token in the retained input.
  require(c::kind(view.words[5]) == c::kind_i64
          && std::string(c::text_at(view.input, c::low(view.words[5]), c::field(view.words[5]))) == "1");
  td_json_release(&borrowed);  // a borrowed result: harmless no-op
  require(run(first, borrowed) == TD_JSON_OK);
  // Retain moves storage: the retained descriptor points at the very same words.
  require(td_json_session_retain(s, &kept) == TD_JSON_OK);
  require(kept.owner && tape_of(kept).words == tape_of(borrowed).words);
  require(td_json_session_retain(s, &kept) == TD_JSON_ARGUMENT);
  require(run(R"({"s":"second document overwrites session storage","n":2})", borrowed) == TD_JSON_OK);
  require(word_text(kept, 3) == "first document text beyond inline" && c::validate(tape_of(kept)) == nullptr);
  // Failed decodes publish nothing; the session stays reusable.
  JsonResult other{}, empty{};
  for (auto bad : {"[1,]", "{\"a\":1,\"a\":2}", "{\"a\":1,\"\\u0061\":2}", "[\"\\q\"]", "01"}) {
    require(run(bad, borrowed) == TD_JSON_SYNTAX);
    require(std::memcmp(&borrowed, &empty, sizeof(empty)) == 0);
    require(td_json_session_retain(s, &other) == TD_JSON_ARGUMENT);
  }
  // Validate-only: status only, nothing to retain, same errors; exclusive with a text arena.
  require(run(first, borrowed, TD_JSON_VALIDATE_ONLY) == TD_JSON_OK);
  require(!borrowed.view && !borrowed.owner && borrowed.copied_input_bytes == first.size());
  require(td_json_session_retain(s, &other) == TD_JSON_ARGUMENT);
  for (auto bad : {"[1,]", "{\"a\":1,\"a\":2}", "{\"k\\u0041\":1,\"kA\":2}", "[\"\\q\"]", "01", "[1] 2"}) {
    require(run(bad, borrowed, TD_JSON_VALIDATE_ONLY) == TD_JSON_SYNTAX);
    require(std::memcmp(&borrowed, &empty, sizeof(empty)) == 0);
  }
  alignas(8) uint8_t text[512];
  require(td_json_session_decode(s, reinterpret_cast<const uint8_t*>(first.data()), first.size(),
                                 TD_JSON_VALIDATE_ONLY | TD_JSON_WANT_TEXT, text, sizeof(text), &borrowed) == TD_JSON_ARGUMENT);
  require(run(std::string(2 * 1024 * 1024, ' '), borrowed, TD_JSON_VALIDATE_ONLY) == TD_JSON_LIMIT);
  // Text arenas index words: one entry per word, 0 for non-strings and second words.
  const std::string doc =
      R"({"id":"x","été":"café au lait, long enough for a region","k":"😀 中","esc":"a\"b\u00ff",)"
      R"("":"","n":[1,"plain ascii string beyond sixteen bytes"],"ÿ":"Ā"})";
  require(td_json_session_decode(s, reinterpret_cast<const uint8_t*>(doc.data()), doc.size(), TD_JSON_WANT_TEXT,
                                 text, 2 * doc.size() + 16, &borrowed) == TD_JSON_OK);
  const td_tape_view& t = tape_of(borrowed);
  const auto* table = static_cast<const uint64_t*>(borrowed.text_table);
  size_t strings = 0;
  for (uint64_t i = 0; i < t.word_count;) {
    uint64_t w = t.words[i];
    uint64_t next = c::step(i, w);
    if (next == i + 2) require(table[i + 1] == 0);
    if (c::kind(w) != c::kind_string) {
      require(table[i] == 0);
    } else {
      ++strings;
      require(arena_text(borrowed, text, i) == utf16_of(word_text(borrowed, i)));
    }
    i = next;
  }
  require(strings == 14);
  // UTF-16 input decodes to the same words (spans index the transcoded UTF-8 input).
  {
    std::u16string units = utf16_of(doc);
    JsonResult expected{};
    void* reference = td_json_session_create(&limits);
    require(td_json_session_decode(reference, reinterpret_cast<const uint8_t*>(doc.data()), doc.size(), 0, nullptr, 0, &expected) == TD_JSON_OK);
    require(td_json_session_decode(s, reinterpret_cast<const uint8_t*>(units.data()), 2 * units.size(), TD_JSON_UTF16_INPUT,
                                   nullptr, 0, &borrowed) == TD_JSON_OK);
    const td_tape_view& a = tape_of(borrowed);
    const td_tape_view& b = tape_of(expected);
    require(a.word_count == b.word_count && std::memcmp(a.words, b.words, 8 * a.word_count) == 0);
    require(a.input_bytes == b.input_bytes && std::memcmp(a.input, b.input, a.input_bytes) == 0);
    require(a.directory_count == b.directory_count && std::memcmp(a.directory, b.directory, 8 * a.directory_count) == 0);
    td_json_session_release(reference);
  }
  // td_json_result_myna: an owned Myna v4 copy (threshold-2 directories, its own input), equal to
  // the 0.8.0 decoder's tape at threshold 2, valid after the session moves on and is released.
  {
    require(run(doc, borrowed) == TD_JSON_OK);
    JsonResult myna{};
    require(td_json_result_myna(&borrowed, &myna) == TD_JSON_OK);
    require(myna.owner && myna.view && myna.copied_input_bytes == doc.size());
    require(td_json_result_myna(&borrowed, &myna) == TD_JSON_ARGUMENT);  // live output
    auto* m = static_cast<const myna_value_view_v4*>(myna.view);
    require(m->base.version == MYNA_VALUE_ABI_V4 && m->base.region_count == 1);
    require(m->base.regions[0].data != tape_of(borrowed).input);
    simdjson::padded_string padded(doc);
    truffledata::tape::index_memory memory(SIZE_MAX, std::pmr::new_delete_resource());
    truffledata::tape::block_arena arena(&memory);
    {
      truffledata::json::legacy::tape_decoder<truffledata::tape::tape_writer> reference(&arena, {}, 2);
      reference.start(std::string_view(padded.data(), padded.size()), truffledata::json::legacy::hint_for(padded.size(), {}, {}));
      simdjson::ondemand::parser parser;
      auto root = parser.iterate(padded).value();
      reference.decode(root);
      auto r = reference.view();
      require(m->base.node_count == r.base.node_count);
      for (uint64_t i = 0; i < r.base.node_count; ++i) {
        const auto& x = m->base.nodes[i];
        const auto& y = r.base.nodes[i];
        // Region-1 text references the same offsets of each copy of the input.
        require(x.tag == y.tag && x.length == y.length && x.next == y.next && x.payload == y.payload);
      }
      require(m->base.byte_count == r.base.byte_count
              && (r.base.byte_count == 0 || std::memcmp(m->base.bytes, r.base.bytes, r.base.byte_count) == 0));
      require(m->directory_count == r.directory_count
              && std::memcmp(m->directory, r.directory, r.directory_count * sizeof(myna_value_directory_entry)) == 0);
    }
    arena.release_all();
    require(run("[1]", borrowed, TD_JSON_VALIDATE_ONLY) == TD_JSON_OK);
    JsonResult none{};
    require(td_json_result_myna(&borrowed, &none) == TD_JSON_ARGUMENT);  // validate-only: no tape
    require(td_json_result_myna(&myna, &none) == TD_JSON_ARGUMENT);      // a Myna descriptor
    require(text_of(&m->base, 4) == "café au lait, long enough for a region");
    // The conversion owns everything it references.
    td_json_session_release(s);
    s = nullptr;
    require(text_of(&m->base, 4) == "café au lait, long enough for a region");
    td_json_release(&myna);
    require(!myna.owner && !myna.view);
  }
  // A retained compact result converts too, and the conversion outlives it.
  JsonResult converted{};
  require(td_json_result_myna(&kept, &converted) == TD_JSON_OK);
  td_json_release(&kept);
  require(text_of(&static_cast<const myna_value_view_v4*>(converted.view)->base, 2) == "first document text beyond inline");
  td_json_release(&converted);
}

int main() {
  compact_sessions();
  sessions();
  text_arenas();
  utf16_input();
  session_allocations();
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
  const auto* view = static_cast<const td_tape_view*>(first.view);
  require(view->version == TD_TAPE_VIEW_VERSION && view->word_count == 7 && c::validate(*view) == nullptr);
  static_assert(sizeof(JsonResult) == 88 && offsetof(JsonResult, text_table) == 72 && offsetof(JsonResult, text_bytes) == 80, "ABI 8");
  require(view->directory_count == 2 && view->directory && view->meta_count == 0 && !view->meta);
  require(c::directory_of(view->words[1]) == 1);
  require(c::kind(view->words[3]) == c::kind_i64 && view->words[4] == 123);
  require(c::text_at(view->input, c::low(view->words[3]), c::field(view->words[3])) == "123");
  require((view->words[2] & (c::ascii_flag | c::key_flag)) == (c::ascii_flag | c::key_flag));
  require((view->words[6] & c::ascii_flag) != 0);
  require(first.control_bytes > 0 && first.index_peak_bytes >= first.index_retained_bytes);
  auto saved = first;
  require(decode("[]", first) == TD_JSON_ARGUMENT);
  require(std::memcmp(&first, &saved, sizeof(first)) == 0);
  JsonResult sibling{};
  require(decode("[false]", sibling) == TD_JSON_OK);
  td_json_release(&sibling);
  require(view->word_count == 7);
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
