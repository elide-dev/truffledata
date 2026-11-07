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
  require(!borrowed.owner && borrowed.view && borrowed.lexical_count == 5 && borrowed.key_hashes);
  auto* view = static_cast<const myna_value_view*>(borrowed.view);
  const myna_value_node* nodes = view->nodes;
  require(text_of(view, 2) == "first document text beyond inline");
  // Retain moves storage: the retained descriptor points at the very same nodes.
  require(td_json_session_retain(s, &kept) == TD_JSON_OK);
  require(kept.owner && static_cast<const myna_value_view*>(kept.view)->nodes == nodes);
  require(td_json_session_retain(s, &kept) == TD_JSON_ARGUMENT);  // live result, and no document
  JsonResult other{};
  require(td_json_session_retain(s, &other) == TD_JSON_ARGUMENT);  // nothing borrowed any more
  // The next decode reuses the session; the retained document is untouched.
  require(run(R"({"s":"second document overwrites session storage","n":2})", borrowed) == TD_JSON_OK);
  auto* retained = static_cast<const myna_value_view*>(kept.view);
  require(text_of(retained, 2) == "first document text beyond inline");
  require(static_cast<const uint64_t*>(kept.lexical)[4] == ((uint64_t(45) << 32) | 1));
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
  require(text_of(retained, 2) == "first document text beyond inline");
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
  auto sequence = [&](std::pmr::memory_resource* memory, size_t* calls) {
    session::retained* owner = nullptr;
    {
      session s(myna::value_limits{}, truffledata::json::storage_limits{}, memory);
      try {
        s.decode(doc.data(), doc.size());
        s.decode(wide.data(), wide.size());
        owner = s.retain();
        s.decode(big.data(), big.size());
        // Task D2: the text table is an arena allocation too (the big document's is large).
        std::vector<uint8_t> text(2 * big.size() + 16);
        const uint64_t* table = nullptr;
        require(s.write_text(text.data(), text.size(), table) != truffledata::json::text_too_small);
        s.decode(doc.data(), doc.size());
        require(s.write_text(text.data(), text.size(), table) != truffledata::json::text_too_small);
      } catch (...) {
        if (owner) owner->destroy();
        throw;
      }
    }
    if (calls) *calls = static_cast<fault_memory*>(memory)->calls;
    require(text_of(&owner->view, 1) == "k0");
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
  // Steady state: after warm-up a session decodes without any upstream allocation, and keeps a
  // bounded number of blocks.
  session s(myna::value_limits{}, truffledata::json::storage_limits{});
  for (int i = 0; i < 3; ++i) s.decode(doc.data(), doc.size());
  size_t blocks = s.arena_blocks(), allocations = s.memory().allocations();
  for (int i = 0; i < 10000; ++i) {
    auto d = s.decode(doc.data(), doc.size());
    require(d.index_allocations == 0);
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
  require(s.memory().current() <= truffledata::json::block_arena::retained_blocks * truffledata::json::block_arena::max_block);
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
  auto* view = static_cast<const myna_value_view*>(result.view);
  const auto* table = static_cast<const uint64_t*>(result.text_table);
  size_t strings = 0;
  for (size_t i = 0; i < view->node_count; ++i) {
    if ((view->nodes[i].tag & MYNA_VALUE_KIND_MASK) != MYNA_VALUE_STRING) {
      require(table[i] == 0);
      continue;
    }
    ++strings;
    // Every key and string round-trips to the code units of its decoded UTF-8.
    require(arena_text(result, arena, i) == utf16_of(text_of(view, i)));
  }
  require(strings == 12);
  // Representation: Latin-1 whenever every code point is ≤ U+00FF, UTF-16 otherwise.
  require((table[1] & 1) == 0 && (table[3] & 1) == 0 && (table[4] & 1) == 0);  // "id", "été", "café…"
  require((table[6] & 1) == 1 && arena_text(result, arena, 6).size() == 4);     // 😀 is two units
  require((table[view->node_count - 1] & 1) == 1 && (table[view->node_count - 2] & 1) == 0);
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

int main() {
  sessions();
  text_arenas();
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
  auto* view = static_cast<const myna_value_view*>(first.view);
  require(view->version == MYNA_VALUE_ABI && view->node_count == 5);
  static_assert(sizeof(JsonResult) == 112 && offsetof(JsonResult, key_hashes) == 88
                    && offsetof(JsonResult, text_table) == 96 && offsetof(JsonResult, text_bytes) == 104,
                "ABI 5 publishes lexical spans, key hashes and session text arenas");
  require(first.lexical_count == 5 && first.lexical);
  require(static_cast<const uint64_t*>(first.lexical)[2] == ((uint64_t(5) << 32) | 3));
  {
    // One side-table entry per node: ASCII flag on keys and strings; keys add the low 15 bits of
    // their FNV-1a hash (<< 16) and their offset from the object node.
    auto* hashes = static_cast<const uint32_t*>(first.key_hashes);
    require(hashes != nullptr);
    auto fnv = [](const char* s) {
      uint32_t h = 2166136261u;
      for (; *s; ++s) { h ^= uint8_t(*s); h *= 16777619u; }
      return h & 0x7fffffffu;
    };
    require(hashes[0] == 0 && hashes[2] == 0);
    require(hashes[1] == (0x80000000u | (fnv("a") & 0x7fffu) << 16 | 1));
    require(hashes[3] == (0x80000000u | (fnv("b") & 0x7fffu) << 16 | 3));
    require(hashes[4] == 0x80000000u);
  }
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
  require(!first.lexical && first.lexical_count == 0 && !first.key_hashes);
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
