// The C ABI (plan Task 2, api_test): sessions, retained results, failures that publish nothing but
// the error, allocation failure at every upstream allocation (arena blocks, large allocations, the
// input buffer and rapidyaml's own callbacks) and steady-state decoding without allocation.
#include "yaml/native/api.h"

#include "yaml/native/session.h"
#include "yaml/native/testing.h"

#include <myna/values.h>

#include <cstddef>
#include <cstdio>
#include <cstring>
#include <string>

using namespace truffledata::yaml;
using yaml_session = truffledata::yaml::session;
using truffledata::yaml::testing::fault_memory;
using truffledata::yaml::testing::render;

namespace {
static_assert(sizeof(YamlResult) == 488 && offsetof(YamlResult, error_message) == 232, "YamlResult ABI");
static_assert(offsetof(YamlResult, key_hashes) == 88 && offsetof(YamlResult, yaml_meta) == 96, "shared JSON prefix");
static_assert(sizeof(YamlOptionsStruct) == 80, "YamlOptionsStruct ABI");

YamlOptionsStruct defaults() {
  YamlOptionsStruct o{};
  o.max_input_bytes = 64u << 20;
  o.max_index_bytes = 64u << 20;
  o.max_nodes = 1000000;
  o.max_text_bytes = 64u << 20;
  o.max_aliases = 10000;
  o.max_alias_expansion = 1000000;
  o.max_depth = 128;
  return o;
}

int32_t run(void* s, const std::string& text, YamlResult& out, uint32_t flags = 0) {
  return td_yaml_session_decode(s, reinterpret_cast<const uint8_t*>(text.data()), text.size(), flags, nullptr, 0, &out);
}
std::string error_text(const YamlResult& r) {
  return std::string(reinterpret_cast<const char*>(&r) + offsetof(YamlResult, error_message), r.error_message_length);
}
bool nothing_published(const YamlResult& r) {
  return !r.owner && !r.view && !r.lexical && !r.key_hashes && !r.yaml_meta && !r.source && r.lexical_count == 0;
}

void typed_plans() {
  auto options = defaults();
  YamlRecordPlan record{0, 1};
  YamlScalarField field{};
  field.name = reinterpret_cast<uint8_t*>(const_cast<char*>("n"));
  field.name_length = 1; field.kind = 1; field.flags = 1; field.child = UINT32_MAX;
  field.minimum = INT64_MIN; field.maximum = INT64_MAX;
  YamlPlan plan{};
  require(td_yaml_plan_create(&record, 1, &field, 1, 1024, &options, &plan) == TD_YAML_OK);
  require(td_yaml_plan_create(&record, 1, &field, 1, 1024, &options, &plan) == TD_YAML_ARGUMENT);
  auto decode = [&](const char* text, YamlTypedResult& result) {
    return td_yaml_typed_decode(&plan, reinterpret_cast<const uint8_t*>(text), std::strlen(text), &result);
  };
  YamlTypedResult good{}, bad{};
  require(decode("n: 0x1f", good) == TD_YAML_OK && good.owner && good.count == 1 && good.present == 1);
  require(decode("n: 1", good) == TD_YAML_ARGUMENT);
  require(decode("n: '31'", bad) == TD_YAML_CONTRACT && !bad.owner && !bad.slots && bad.error_message_length > 0);
  require(decode("n: [", bad) == TD_YAML_SYNTAX && bad.error_line > 0 && !bad.owner);
  require(decode("other: 1", bad) == TD_YAML_CONTRACT && !bad.owner);
  td_yaml_plan_release(&plan); td_yaml_plan_release(&plan);
  YamlScalarSlot copied;
  std::memcpy(&copied, good.slots, sizeof copied);
  require(copied.bits == 31); // Result survives plan close.
  td_yaml_typed_release(&good); td_yaml_typed_release(&good); td_yaml_typed_release(&bad);
  require(!good.owner && !good.slots);
  require(decode("n: 1", bad) == TD_YAML_ARGUMENT);
  field.child = 0;
  require(td_yaml_plan_create(&record, 1, &field, 1, 1024, &options, &plan) == TD_YAML_ARGUMENT);
  field.child = UINT32_MAX;
  require(td_yaml_plan_create(&record, 1, &field, 1, 0, &options, &plan) == TD_YAML_LIMIT);
}

void sessions() {
  YamlOptionsStruct options = defaults();
  void* s = td_yaml_session_create(&options);
  require(s != nullptr);
  YamlResult borrowed{}, kept{};
  const std::string first = "s: first document text beyond inline\nn: 1\n";
  require(run(s, first, borrowed) == TD_YAML_OK);
  require(!borrowed.owner && borrowed.view && borrowed.lexical_count == 5 && borrowed.key_hashes && borrowed.yaml_meta);
  require(borrowed.document_flags == TD_YAML_DOC_PRESENT && borrowed.error_message_length == 0);
  auto* view = static_cast<const myna_value_view*>(borrowed.view);
  const myna_value_node* nodes = view->nodes;
  require(render(*view) == "{\"s\":\"first document text beyond inline\",\"n\":1}");
  // Retain moves storage: the retained descriptor points at the same nodes.
  require(td_yaml_session_retain(s, &kept) == TD_YAML_OK);
  require(kept.owner && static_cast<const myna_value_view*>(kept.view)->nodes == nodes);
  require(td_yaml_session_retain(s, &kept) == TD_YAML_ARGUMENT);
  YamlResult other{};
  require(td_yaml_session_retain(s, &other) == TD_YAML_ARGUMENT);
  // The next decode reuses the session; the retained document is untouched.
  require(run(s, "s: second document overwrites session storage\nn: 2\n", borrowed) == TD_YAML_OK);
  auto* retained = static_cast<const myna_value_view*>(kept.view);
  require(render(*retained) == "{\"s\":\"first document text beyond inline\",\"n\":1}");
  const uint64_t* spans = static_cast<const uint64_t*>(kept.lexical);
  require(std::string(reinterpret_cast<const char*>(kept.source) + (spans[4] >> 32), uint32_t(spans[4])) == "1");
  // A failed decode publishes nothing but the error and leaves the session reusable.
  for (auto bad : {"a: [1, 2\n", "{a: 1, a: 2}", "\"unterminated\n", "a: b: c\n"}) {
    require(run(s, bad, borrowed) == TD_YAML_SYNTAX);
    require(nothing_published(borrowed) && borrowed.error_message_length > 0 && borrowed.error_line > 0);
    require(td_yaml_session_retain(s, &other) == TD_YAML_ARGUMENT);
  }
  // rapidyaml's exceptions become status 4 with line and column.
  require(run(s, "a: 1\nb: [1, 2\n", borrowed) == TD_YAML_SYNTAX);
  require(borrowed.error_line >= 2 && borrowed.error_column > 0 && !error_text(borrowed).empty());
  require(run(s, "ok: [x\x01]\n", borrowed) == TD_YAML_SYNTAX);
  require(borrowed.error_line == 1 && borrowed.error_column == 7);
  require(error_text(borrowed).find("control character") != std::string::npos);
  require(run(s, "a\n---\nb\n", borrowed) == TD_YAML_MULTIPLE_DOCUMENTS && nothing_published(borrowed));
  require(run(s, std::string(129, '[') + std::string(129, ']'), borrowed) == TD_YAML_LIMIT);
  require(error_text(borrowed).rfind("YAML resource limit", 0) == 0);
  require(run(s, "[true]", borrowed) == TD_YAML_OK);
  // Reset invalidates the borrow (O(1), leaf contract).
  td_yaml_session_reset(s);
  require(td_yaml_session_retain(s, &other) == TD_YAML_ARGUMENT);
  // Reserved flags and text buffers are rejected.
  require(run(s, "[]", borrowed, TD_YAML_WANT_UTF16_TEXT) == TD_YAML_ARGUMENT);
  uint8_t text[8];
  require(td_yaml_session_decode(s, reinterpret_cast<const uint8_t*>("[]"), 2, 0, text, 8, &borrowed) == TD_YAML_ARGUMENT);
  require(td_yaml_session_decode(s, nullptr, 0, 0, nullptr, 0, &borrowed) == TD_YAML_ARGUMENT);
  // Source retention publishes a pristine copy for quoted spans.
  require(run(s, "k: 'it''s'\n", borrowed, TD_YAML_RETAIN_SOURCE) == TD_YAML_OK);
  require(borrowed.document_flags & TD_YAML_DOC_SOURCE_RETAINED);
  spans = static_cast<const uint64_t*>(borrowed.lexical);
  require(std::string(reinterpret_cast<const char*>(borrowed.source) + (spans[2] >> 32), uint32_t(spans[2])) == "'it''s'");
  // Non-finite floats are flagged.
  require(run(s, "[.inf, -.inf, .nan]", borrowed) == TD_YAML_OK && (borrowed.document_flags & TD_YAML_DOC_NONFINITE));
  // Input limits apply per document.
  YamlOptionsStruct tight = defaults();
  tight.max_input_bytes = 16;
  void* small = td_yaml_session_create(&tight);
  require(run(small, std::string(17, ' '), borrowed) == TD_YAML_LIMIT);
  require(run(small, "[1, 2]", borrowed) == TD_YAML_OK);
  td_yaml_session_release(small);
  td_yaml_session_release(s);
  // The retained result outlives its session.
  require(render(*retained) == "{\"s\":\"first document text beyond inline\",\"n\":1}");
  td_yaml_release(&kept);
  td_yaml_release(&kept);
  require(!kept.owner && !kept.view);
  // Invalid options.
  YamlOptionsStruct bad = defaults();
  bad.max_depth = 129;
  require(td_yaml_session_create(&bad) == nullptr);
  bad = defaults();
  bad.schema = 4;
  require(td_yaml_session_create(&bad) == nullptr);
  bad = defaults();
  bad.complex_keys = 2;
  require(td_yaml_session_create(&bad) == nullptr);
  require(td_yaml_session_create(nullptr) == nullptr);
  require(std::strcmp(reinterpret_cast<const char*>(td_yaml_fingerprint()), TRUFFLEDATA_YAML_SEAM_ABI) == 0);
}

std::string deep(int depth) {
  std::string doc;
  for (int i = 0; i < depth; ++i) doc += std::string(size_t(i) * 2, ' ') + "k" + std::to_string(i) + ":\n";
  return doc + std::string(size_t(depth) * 2, ' ') + "leaf\n";
}

void allocation_failures() {
  const std::string doc =
      "id: 1007\nname: item-1\nnote: \"line \\\"1\\\" with \\\\ escapes\\n and tail \\L\"\ntags: [t0, t1]\ndims: {w: 3, h: 1.5}\n";
  std::string wide = "{";
  for (int i = 0; i < 40; ++i) wide += "k" + std::to_string(i) + ": [" + std::to_string(i) + "], ";
  wide += "}";
  std::string big = "- {first: 1}\n";
  for (int i = 0; i < 2000; ++i) big += "- {id: " + std::to_string(i) + ", name: item, note: a longer note text}\n";
  const std::string nested = deep(40);  // past rapidyaml's 16-entry inline stack: callback allocations
  // Every upstream allocation of a decode/retain/decode sequence can fail; the session stays
  // consistent and everything is freed once the session and the retained owner are destroyed.
  auto sequence = [&](fault_memory& memory) {
    yaml_session::retained* owner = nullptr;
    {
      yaml_session s(session_options{}, &memory);
      try {
        s.decode(reinterpret_cast<const uint8_t*>(doc.data()), doc.size(), 0);
        s.decode(reinterpret_cast<const uint8_t*>(wide.data()), wide.size(), yaml_session::retain_source);
        owner = s.retain();
        s.decode(reinterpret_cast<const uint8_t*>(nested.data()), nested.size(), 0);
        s.decode(reinterpret_cast<const uint8_t*>(big.data()), big.size(), 0);
        s.decode(reinterpret_cast<const uint8_t*>(doc.data()), doc.size(), 0);
      } catch (...) {
        if (owner) owner->destroy();
        throw;
      }
    }
    require(render(owner->view).rfind("{\"k0\":[0]", 0) == 0);
    owner->destroy();
  };
  fault_memory counting;
  sequence(counting);
  size_t total = counting.calls;
  require(counting.live == 0 && total > 8);
  size_t rapidyaml = 0;
  {
    fault_memory probe;
    yaml_session s(session_options{}, &probe);
    s.decode(reinterpret_cast<const uint8_t*>(doc.data()), doc.size(), 0);
    size_t before = s.memory().allocations();
    s.decode(reinterpret_cast<const uint8_t*>(nested.data()), nested.size(), 0);
    rapidyaml = s.memory().allocations() - before;
  }
  require(rapidyaml > 0);
  for (size_t i = 0; i < total; ++i) {
    fault_memory memory(i);
    bool rejected = false;
    try {
      sequence(memory);
    } catch (const std::bad_alloc&) {
      rejected = true;
    }
    require(rejected && memory.live == 0);
  }
  std::printf("allocation_failures: %zu upstream allocations, each failed once\n", total);

  // Steady state: after warm-up a session decodes without any upstream allocation and keeps a
  // bounded number of blocks.
  yaml_session s(session_options{});
  for (int i = 0; i < 3; ++i) s.decode(reinterpret_cast<const uint8_t*>(doc.data()), doc.size(), 0);
  size_t blocks = s.arena_blocks(), allocations = s.memory().allocations();
  for (int i = 0; i < 10000; ++i) {
    auto d = s.decode(reinterpret_cast<const uint8_t*>(doc.data()), doc.size(), 0);
    require(d.index_allocations == 0);
  }
  require(s.memory().allocations() == allocations && s.arena_blocks() == blocks);
  // Large inputs are not kept: the input buffer above 1 MiB is released before the next decode.
  std::string huge = "- " + std::string(size_t(1) << 21, 'x') + "\n";
  s.decode(reinterpret_cast<const uint8_t*>(huge.data()), huge.size(), 0);
  require(s.input_capacity() > yaml_session::retained_input_limit);
  s.decode(reinterpret_cast<const uint8_t*>(doc.data()), doc.size(), 0);
  require(s.input_capacity() <= yaml_session::retained_input_limit);
  require(s.memory().current() <= truffledata::tape::block_arena::retained_blocks * truffledata::tape::block_arena::max_block + 4096);
}
}  // namespace

int main() {
  try {
    sessions();
    typed_plans();
    allocation_failures();
  } catch (const std::exception& e) {
    std::fprintf(stderr, "api_test failure: %s\n", e.what());
    return 1;
  }
  std::printf("YAML C ABI checks passed\n");
  return 0;
}
