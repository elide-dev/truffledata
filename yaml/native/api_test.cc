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
static_assert(sizeof(YamlResult) == 448 && offsetof(YamlResult, error_message) == 192, "YamlResult ABI");
static_assert(offsetof(YamlResult, yaml_meta) == 72, "compact YAML metadata");
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
  return !r.owner && !r.view && !r.yaml_meta && !r.source;
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
  require(!borrowed.owner && borrowed.view && borrowed.yaml_meta);
  require(borrowed.document_flags == TD_YAML_DOC_PRESENT && borrowed.error_message_length == 0);
  auto* view = static_cast<const td_tape_view*>(borrowed.view);
  const uint64_t* nodes = view->words;
  require(render(*view) == "{\"s\":\"first document text beyond inline\",\"n\":1}");
  // Retain moves storage: the retained descriptor points at the same nodes.
  require(td_yaml_session_retain(s, &kept) == TD_YAML_OK);
  require(kept.owner && static_cast<const td_tape_view*>(kept.view)->words == nodes);
  require(td_yaml_session_retain(s, &kept) == TD_YAML_ARGUMENT);
  YamlResult other{};
  require(td_yaml_session_retain(s, &other) == TD_YAML_ARGUMENT);
  // The next decode reuses the session; the retained document is untouched.
  require(run(s, "s: second document overwrites session storage\nn: 2\n", borrowed) == TD_YAML_OK);
  auto* retained = static_cast<const td_tape_view*>(kept.view);
  require(render(*retained) == "{\"s\":\"first document text beyond inline\",\"n\":1}");
  uint64_t span = lexical_span(*retained, testing::ordinal(*retained, 4));
  require(std::string(reinterpret_cast<const char*>(kept.source) + (span >> 32), uint32_t(span)) == "1");
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
  const auto& quoted = *static_cast<const td_tape_view*>(borrowed.view);
  span = lexical_span(quoted, testing::ordinal(quoted, 2));
  require(std::string(reinterpret_cast<const char*>(borrowed.source) + (span >> 32), uint32_t(span)) == "'it''s'");
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

void shared_text_capacity() {
  YamlOptionsStruct options = defaults();
  void* session = td_yaml_session_create(&options);
  require(session);
  YamlResult document{};
  YamlTextResult text{};
  require(td_yaml_session_write_text(session, nullptr, 0, &text) == TD_YAML_ARGUMENT);
  std::string input = "anchor: &key " + std::string(1024, 'x') + "\nitems: [";
  for (int i = 0; i < 20; ++i) input += std::string(i ? ", " : "") + "{*key : value}";
  input += "]\nunicode: [café, 雪, 😀, '', ascii]\n";
  require(run(session, input, document) == TD_YAML_OK);
  require(td_yaml_session_write_text(session, nullptr, 0, &text) == TD_YAML_OK);
  require(text.required_capacity > 2 * input.size() + 16 && !text.table && !text.bytes_written);
  std::vector<uint8_t> bytes(text.required_capacity, 0xA5);
  require(td_yaml_session_write_text(session, bytes.data(), 2, &text) == TD_YAML_TEXT_TOO_SMALL);
  require(!text.table && !text.bytes_written && bytes[0] == 0xA5 && bytes[2] == 0xA5);
  require(td_yaml_session_write_text(session, bytes.data(), bytes.size(), &text) == TD_YAML_OK);
  require(text.table && text.bytes_written <= bytes.size());
  const auto& view = *static_cast<const td_tape_view*>(document.view);
  const auto* table = static_cast<const uint64_t*>(text.table);
  for (uint64_t i = 0; i < view.word_count; i = ct::step(i, view.words[i])) {
    uint64_t entry = table[i];
    if (ct::kind(view.words[i]) != ct::kind_string) { require(entry == 0); continue; }
    std::string_view source = ct::text(view, i);
    size_t offset = entry >> 32, length = uint32_t(entry) >> 1;
    require(offset + length <= text.bytes_written);
    std::vector<char16_t> expected(source.size() + 1);
    size_t count = simdutf::convert_valid_utf8_to_utf16le(source.data(), source.size(), expected.data());
    if (entry & 1) {
      require(length == 2 * count && std::memcmp(bytes.data() + offset, expected.data(), length) == 0);
    } else {
      require(length == count);
      for (size_t j = 0; j < count; ++j) require(expected[j] == bytes[offset + j]);
    }
  }
  td_yaml_session_reset(session);
  require(td_yaml_session_write_text(session, nullptr, 0, &text) == TD_YAML_ARGUMENT && !text.table);
  require(run(session, "null", document) == TD_YAML_OK);
  require(td_yaml_session_write_text(session, nullptr, 0, &text) == TD_YAML_OK && text.required_capacity == 0);
  td_yaml_session_release(session);
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
        const uint64_t* table = nullptr;
        std::vector<uint8_t> text(s.text_capacity());
        require(s.write_text(text.data(), text.size(), table) <= text.size());
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
    shared_text_capacity();
    allocation_failures();
  } catch (const std::exception& e) {
    std::fprintf(stderr, "api_test failure: %s\n", e.what());
    return 1;
  }
  std::printf("YAML C ABI checks passed\n");
  return 0;
}
