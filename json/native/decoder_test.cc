// The JSON decoder on the compact tape (tape-gbps plan Task 2): parity with the 0.8.0 decoder over
// the 200-document corpus, directory-fused duplicate detection, number classification, exact
// spans, strict input, validate-only parity and allocation failure at every growth step.
#include "json/native/decoder.h"
#include "json/native/testing/myna_oracle.h"
#include "json/native/session.h"
#include "json/native/status.h"
#include "tape/native/testing.h"
#include "tape/native/text_arena.h"

#include <cstdio>
#include <cstring>
#include <iostream>
#include <memory_resource>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <vector>

using namespace truffledata;
using json::document;
using tape::testing::corpus;
using tape::testing::fixture;
using tape::testing::object_with_keys;
namespace c = tape::ctape;

namespace {
void require_at(bool condition, const char* what, int line) {
  if (!condition) throw std::runtime_error("check failed at line " + std::to_string(line) + ": " + what);
}
#define require(condition) require_at((condition), #condition, __LINE__)

template<class F> bool throws(F f) {
  try { f(); } catch (const std::exception&) { return true; }
  return false;
}
// The status the C ABI would report for whatever `f` throws (status_ok when nothing).
template<class F> int32_t status_of(F f) {
  try {
    f();
  } catch (...) {
    return json::current_exception_status();
  }
  return json::status_ok;
}

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

struct scratch {
  tape::index_memory memory{SIZE_MAX, std::pmr::new_delete_resource()};
  tape::block_arena arena{&memory};
};

// One padded input decoded by both the compact decoder and the 0.8.0 decoder at directory
// threshold 2 (user decision Q2), so to_myna of the compact tape must equal the reference tape
// node for node, with the same region pointer.
struct pair {
  simdjson::padded_string padded;
  scratch a, b;
  json::tape_decoder<tape::ctape_writer> compact{&a.arena, {}};
  json::legacy::tape_decoder<tape::tape_writer> reference{&b.arena, {}, 2};
  td_tape_view view{};
  myna_value_view_v4 myna{};
  explicit pair(const std::string& source) : padded(source) {
    std::string_view input(padded.data(), padded.size());
    simdjson::ondemand::parser parser;
    compact.start(input, json::hint_for(input.size(), {}, {}));
    auto root = parser.iterate(padded).value();
    compact.decode(root);
    view = compact.view();
    reference.start(input, json::legacy::hint_for(input.size(), {}, {}));
    auto again = parser.iterate(padded).value();
    reference.decode(again);
    myna = reference.view();
  }
  std::string text(uint64_t i) const {
    const myna_value_node& n = myna.base.nodes[i];
    if (n.tag & MYNA_VALUE_REGION_TEXT) {
      uint32_t region = myna_value_region_id(&n);
      const uint8_t* base = region == 0 ? myna.base.bytes : myna.base.regions[region - 1].data;
      return std::string(reinterpret_cast<const char*>(base) + myna_value_offset(&n), n.length);
    }
    std::string out;
    for (uint32_t b = 0; b < n.length; ++b) out += char(myna_value_inline_byte(&n, b));
    return out;
  }
};

// ---- words_match_reference_corpus ---------------------------------------------------------------
void words_match_reference_corpus() {
  auto documents = corpus();
  require(documents.size() == 200);
  for (const auto& source : documents) {
    pair p(source);
    const char* error = c::validate(p.view);
    if (error) throw std::runtime_error(std::string("invalid tape: ") + error + " in " + source.substr(0, 60));
    require(p.compact.writer().value_count() == p.myna.base.node_count);
    scratch s;
    myna_value_view_v4 m = c::to_myna(p.view, &s.arena);
    const myna_value_view& a = m.base;
    const myna_value_view& b = p.myna.base;
    require(a.version == MYNA_VALUE_ABI_V4 && a.version == b.version && a.flags == b.flags);
    require(a.node_count == b.node_count);
    require(std::memcmp(a.nodes, b.nodes, a.node_count * sizeof(myna_value_node)) == 0);
    require(a.byte_count == b.byte_count && (a.byte_count == 0 || std::memcmp(a.bytes, b.bytes, a.byte_count) == 0));
    require(a.region_count == 1 && b.region_count == 1);
    require(a.regions[0].data == b.regions[0].data && a.regions[0].length == b.regions[0].length);
    require(m.directory_count == p.myna.directory_count);
    require(m.directory_count == 0
            || std::memcmp(m.directory, p.myna.directory, m.directory_count * sizeof(myna_value_directory_entry)) == 0);
    // ASCII flags agree with the 0.8.0 key meta, KEY flags with key position.
    const uint32_t* meta = p.reference.key_hashes().data();
    uint64_t node = 0;
    for (uint64_t i = 0; i < p.view.word_count; i = c::step(i, p.view.words[i]), ++node) {
      uint64_t w = p.view.words[i];
      if (c::kind(w) == c::kind_string) require(bool(w & c::ascii_flag) == bool(meta[node] & tape::ascii_flag));
    }
    require(node == b.node_count);
  }
}

// ---- walk_matches_reference (moved from //tape/native:ctape_test) -------------------------------
void trace_myna(const pair& r, uint64_t& i, std::string& out, bool key) {
  const myna_value_node& n = r.myna.base.nodes[i];
  switch (n.tag & MYNA_VALUE_KIND_MASK) {
    case MYNA_VALUE_NULL: out += "n,"; ++i; return;
    case MYNA_VALUE_BOOL: out += n.payload ? "t," : "f,"; ++i; return;
    case MYNA_VALUE_I64: out += "i" + std::to_string(int64_t(n.payload)) + ","; ++i; return;
    case MYNA_VALUE_U64: out += "u" + std::to_string(n.payload) + ","; ++i; return;
    case MYNA_VALUE_F64: out += "f" + std::to_string(n.payload) + ","; ++i; return;
    case MYNA_VALUE_DECIMAL: out += "d" + r.text(i) + ","; ++i; return;
    case MYNA_VALUE_STRING: out += (key ? "k" : "s") + r.text(i) + ","; i = myna_value_next(&n, i); return;
    default: {
      bool object = (n.tag & MYNA_VALUE_KIND_MASK) == MYNA_VALUE_OBJECT;
      out += (object ? "{" : "[") + std::to_string(n.length) + ",";
      uint64_t children = object ? 2ull * n.length : n.length;
      ++i;
      for (uint64_t k = 0; k < children; ++k) trace_myna(r, i, out, object && k % 2 == 0);
      out += object ? "}," : "],";
    }
  }
}
struct trace_visitor {
  const td_tape_view& v;
  std::string out;
  void open(uint64_t, uint64_t w) { out += (c::kind(w) == c::kind_object ? "{" : "[") + std::to_string(c::field(w)) + ","; }
  void close(uint64_t i) { out += c::kind(v.words[i]) == c::kind_object ? "}," : "],"; }
  void scalar(uint64_t i, uint64_t w) {
    switch (c::kind(w)) {
      case c::kind_null: out += "n,"; break;
      case c::kind_false: out += "f,"; break;
      case c::kind_true: out += "t,"; break;
      case c::kind_i64: out += "i" + std::to_string(int64_t(v.words[i + 1])) + ","; break;
      case c::kind_u64: out += "u" + std::to_string(v.words[i + 1]) + ","; break;
      case c::kind_f64: out += "f" + std::to_string(v.words[i + 1]) + ","; break;
      case c::kind_decimal: out += "d" + std::string(c::text(v, i)) + ","; break;
      case c::kind_string: out += ((w & c::key_flag) ? "k" : "s") + std::string(c::text(v, i)) + ","; break;
      default: out += "?,"; break;
    }
  }
};
void walk_matches_reference() {
  for (const auto& source : corpus()) {
    pair p(source);
    std::string expected;
    uint64_t i = 0;
    trace_myna(p, i, expected, false);
    trace_visitor t{p.view, {}};
    c::walk(p.view, t);
    require(t.out == expected);
    // The text arena over words writes the same bytes and per-string entries as the Myna overload.
    size_t capacity = tape::text_capacity_bound(source.size());
    std::vector<uint8_t> out_a(capacity + 2), out_b(capacity + 2);
    std::vector<uint64_t> table_a(p.myna.base.node_count), table_b(p.view.word_count);
    size_t used_a = tape::write_text_arena(p.myna.base, p.reference.key_hashes().data(), table_a.data(), out_a.data(), capacity);
    size_t used_b = tape::write_text_arena(p.view, table_b.data(), out_b.data(), capacity);
    require(used_a == used_b && used_a != tape::text_too_small);
    require(std::memcmp(out_a.data(), out_b.data(), used_a) == 0);
    std::vector<uint64_t> entries_a, entries_b;
    for (uint64_t n = 0; n < p.myna.base.node_count; ++n)
      if ((p.myna.base.nodes[n].tag & MYNA_VALUE_KIND_MASK) == MYNA_VALUE_STRING) entries_a.push_back(table_a[n]);
    for (uint64_t n = 0; n < p.view.word_count; n = c::step(n, p.view.words[n])) {
      if (c::kind(p.view.words[n]) == c::kind_string) entries_b.push_back(table_b[n]);
      if (c::is_wide(p.view.words[n])) require(table_b[n + 1] == 0);
    }
    require(entries_a == entries_b);
  }
}

// ---- duplicate_rejected_after_unescaping ---------------------------------------------------------
// Every check runs through both the decoder and the validate-only walk.
json::session& validator() {
  static json::session s(myna::value_limits{}, tape::storage_limits{});
  return s;
}
bool rejected_as_duplicate(const std::string& source) {
  bool decoded = false, validated = false;
  try { document d(source); } catch (const c::duplicate_key&) { decoded = true; }
  try { validator().decode(source.data(), source.size(), json::session::encoding::utf8, true); }
  catch (const c::duplicate_key&) { validated = true; }
  require(decoded == validated);
  return decoded;
}
bool accepted(const std::string& source) {
  bool decoded = !throws([&] { document d(source); });
  bool validated = !throws([&] { validator().decode(source.data(), source.size(), json::session::encoding::utf8, true); });
  require(decoded == validated);
  return decoded;
}
void duplicate_rejected_after_unescaping() {
  for (size_t count : {2, 16, 17, 40, 1000}) {
    require(accepted(object_with_keys(count, SIZE_MAX, "")));
    // The last key duplicates the first ("kA"), raw and escaped.
    require(rejected_as_duplicate(object_with_keys(count, count - 1, "kA")));
    require(rejected_as_duplicate(object_with_keys(count, count - 1, "k\\u0041")));
    // Adjacent duplicates.
    if (count > 2) require(rejected_as_duplicate(object_with_keys(count, 2, "k1")));
  }
  // The 17th key duplicating the 1st.
  require(rejected_as_duplicate(object_with_keys(17, 16, "kA")));
  // An escaped long key equal to a raw one (owned vs referenced text).
  require(rejected_as_duplicate(R"({"abcdefghijklmnopqrst":1,"abcdefghij\u006blmnopqrst":2})"));
  require(rejected_as_duplicate(R"({"":1,"":2})"));
  require(rejected_as_duplicate(R"({"a":{"b":1,"c":2,"b":3}})"));
  require(rejected_as_duplicate(R"([{"x":1},{"y":1,"x":2,"y":3}])"));
  // Equal keys in different objects, or different keys with the same prefix, are not duplicates.
  require(accepted(R"({"":1,"a":{"":2}})"));
  require(accepted(R"({"a":{"a":{"a":1}},"b":{"a":2,"b":3}})"));
  require(accepted(R"({"a":1,"a ":2,"\u0061b":3,"ab ":4})"));
  // Distinct keys with one FNV-1a hash ("costarring"/"liquid" is the classic 32-bit FNV-1
  // collision; search FNV-1a pairs here so a colliding A,B,A run is pinned whatever they are).
  {
    std::vector<std::string> keys;
    std::unordered_map<uint32_t, std::string> seen;
    for (uint32_t i = 0; keys.empty(); ++i) {
      std::string k = "k" + std::to_string(i);
      uint32_t h = myna_value_hash(reinterpret_cast<const uint8_t*>(k.data()), k.size());
      auto [it, inserted] = seen.emplace(h, k);
      if (!inserted) keys = {it->second, k};
    }
    std::string a = keys[0], b = keys[1];
    require(accepted("{\"" + a + "\":1,\"" + b + "\":2}"));
    require(rejected_as_duplicate("{\"" + a + "\":1,\"" + b + "\":2,\"" + a + "\":3}"));
    require(rejected_as_duplicate("{\"" + b + "\":1,\"x\":0,\"" + a + "\":2,\"" + b + "\":3}"));
    document d("{\"" + a + "\":1,\"" + b + "\":2}");
    require(c::validate(d.view()) == nullptr && d.view().directory_count == 2);
    require(c::entry_hash(d.view().directory[0]) == c::entry_hash(d.view().directory[1]));
  }
  // Duplicates are syntax errors at the C ABI, and the session stays usable.
  require(status_of([] { document d(R"({"a":1,"a":2})"); }) == json::status_syntax);
}

// ---- number_classification_unchanged -------------------------------------------------------------
void number_classification_unchanged() {
  const std::string source =
      R"([1, -0, 18446744073709551615, 1.5, 1E400, 12345678901234567890123, -0.0, 9223372036854775807, -9223372036854775808, 9223372036854775808, 12345678901234567890])";
  document d(source);
  const td_tape_view& v = d.view();
  require(c::validate(v) == nullptr);
  std::vector<uint64_t> nodes;
  for (uint64_t i = 2; i < v.word_count; i = c::after(i, v.words[i])) nodes.push_back(i);
  require(nodes.size() == 11 && c::field(v.words[0]) == 11);
  auto kind = [&](size_t k) { return c::kind(v.words[nodes[k]]); };
  auto bits = [&](size_t k) { return v.words[nodes[k] + 1]; };
  auto token = [&](size_t k) { return std::string(c::text_at(v.input, c::low(v.words[nodes[k]]), c::field(v.words[nodes[k]]))); };
  require(kind(0) == c::kind_i64 && bits(0) == 1 && token(0) == "1");
  require(kind(1) == c::kind_f64 && bits(1) == 0x8000000000000000ull && token(1) == "-0");
  require(kind(2) == c::kind_u64 && bits(2) == UINT64_MAX);
  require(kind(3) == c::kind_f64 && token(3) == "1.5");
  require(kind(4) == c::kind_decimal && token(4) == "1E400" && !c::is_wide(v.words[nodes[4]]));
  require(kind(5) == c::kind_decimal && token(5) == "12345678901234567890123");
  require(kind(6) == c::kind_f64 && bits(6) == 0x8000000000000000ull && token(6) == "-0.0");
  require(kind(7) == c::kind_i64 && bits(7) == uint64_t(INT64_MAX));
  require(kind(8) == c::kind_i64 && bits(8) == uint64_t(INT64_MIN));
  require(kind(9) == c::kind_u64 && bits(9) == 9223372036854775808ull);
  require(kind(10) == c::kind_u64 && bits(10) == 12345678901234567890ull && token(10) == "12345678901234567890");
  for (auto bad : {"[01]", "[1.]", "[.5]", "[1e]", "[-]", "[+1]", "[1e+]", "[-01]", "[0x1]", "[1.e5]", "[Infinity]", "[1 2]", "NaN", "-"})
    require(throws([&] { document x(bad); }));
  // Scalar roots are one value; a number root keeps its token.
  for (auto ok : {"0", "-0", "1e999999999999999999999999", "\"hi\"", "null", "true", "false"}) {
    document x(ok);
    require(c::validate(x.view()) == nullptr && x.value_count() == 1);
  }
}

// ---- raw_control_byte_rejected / invalid_utf8_rejected_before_walk -------------------------------
void raw_control_byte_rejected() {
  for (std::string bad : {std::string("[\"a\x01" "bcdefghijklmnopqrstuvwxyz\"]"), std::string("[\"a\x01" "b\"]"),
                          std::string("{\"a\x01" "b\":1}"), std::string("[\"tab\there\"]"), std::string("[\"nl\nhere\"]")}) {
    require(throws([&] { document d(bad); }));
    require(throws([&] { validator().decode(bad.data(), bad.size(), json::session::encoding::utf8, true); }));
  }
}
void invalid_utf8_rejected_before_walk() {
  for (std::string bad : {std::string("[\"\xff\"]"), std::string("{\"\xc3\":1}"), std::string("[\"ok\",\"\xe4\xb8\"]"),
                          std::string("[1,2,3,\"\xed\xa0\x80\"]")}) {
    bool utf8 = false;
    try { document d(bad); } catch (const simdjson::simdjson_error& e) { utf8 = e.error() == simdjson::UTF8_ERROR; }
    require(utf8);
    require(status_of([&] { validator().decode(bad.data(), bad.size(), json::session::encoding::utf8, true); })
            == json::status_syntax);
  }
}

// ---- spans_are_exact_tokens ----------------------------------------------------------------------
// The tokens simdjson reports, in tape order: containers' opening offsets, keys' and scalars' raw
// tokens (quotes included).
template<class V> void tokens(V& value, const char* base, std::vector<std::string_view>& out) {
  using simdjson::ondemand::json_type;
  switch (value.type().value()) {
    case json_type::object: {
      std::string_view open = value.raw_json_token();
      out.push_back(open.substr(0, 1));
      for (auto field : value.get_object()) {
        out.push_back(json::trim(field.key_raw_json_token().value()));
        auto child = field.value().value();
        tokens(child, base, out);
      }
      break;
    }
    case json_type::array: {
      std::string_view open = value.raw_json_token();
      out.push_back(open.substr(0, 1));
      for (auto element : value.get_array()) {
        auto child = element.value();
        tokens(child, base, out);
      }
      break;
    }
    default: {
      out.push_back(json::trim(value.raw_json_token()));
    }
  }
}
void spans_are_exact_tokens() {
  std::vector<std::string> documents = corpus();
  documents.push_back(R"({"esc\"aped":"a\nb","raw":"plain","\u00e9":["x\u0041y",1E400,-0,  2.5e-3 ,{"n":null}]})");
  documents.push_back("  [ \"padded\" , 12 ,\t{ \"k\" :\"v\" } ]  ");
  for (const auto& source : documents) {
    simdjson::padded_string padded(source);
    simdjson::ondemand::parser parser;
    std::vector<std::string_view> expected;
    auto root = parser.iterate(padded).value();
    if (root.is_scalar().value()) {
      expected.push_back(json::trim(root.raw_json_token().value()));
    } else {
      tokens(root, padded.data(), expected);
    }
    scratch s;
    json::tape_decoder<tape::ctape_writer> decoder(&s.arena, {});
    decoder.start(std::string_view(padded.data(), padded.size()), json::hint_for(padded.size(), {}, {}));
    simdjson::ondemand::parser second;
    auto doc = second.iterate(padded).value();
    decoder.decode(doc);
    td_tape_view v = decoder.view();
    size_t k = 0;
    auto at = [&](std::string_view t) { return uint32_t(t.data() - padded.data()); };
    for (uint64_t i = 0; i < v.word_count; i = c::step(i, v.words[i]), ++k) {
      require(k < expected.size());
      std::string_view t = expected[k];
      uint64_t w = v.words[i];
      switch (c::kind(w)) {
        case c::kind_array:
        case c::kind_object:
          require(c::low(v.words[i + 1]) == at(t));
          break;
        case c::kind_i64: case c::kind_u64: case c::kind_f64: case c::kind_decimal:
          require(c::low(w) == at(t) && c::field(w) == t.size());
          break;
        case c::kind_string:
          if (w & c::owned_flag) {
            require(v.words[i + 1] == ((uint64_t(at(t)) << 32) | t.size()));
            require(t.find('\\') != std::string_view::npos);
          } else {
            require(c::low(w) == at(t) + 1 && c::field(w) == t.size() - 2);
          }
          break;
        default:
          require(t == "null" || t == "true" || t == "false");
      }
    }
    require(k == expected.size());
  }
}

// ---- validate_only_rejects_everything_decode_rejects -----------------------------------------------
// 60 malformed documents: grammar, numbers, escapes, control bytes, UTF-8, duplicates, trailing
// input and truncations of corpus documents.
std::vector<std::string> malformed() {
  std::vector<std::string> out = {
      "", "01", "1.", "1e", "-", "NaN", "[1,]", "{\"a\":1,}", "[tru]", "[xyz]", "{\"x\":undefined}",
      "{\"a\":1,\"unused\":01}", "{\"a\":1,\"\\u0061\":2}", "{} false", "1 2", "\"x\" []", "\"\\uD800\"",
      "[\"\\q\"]", "[01]", "[1.]", "[.5]", "[1e]", "[-]", "[+1]", "[1e+]", "[-01]", "[0x1]", "[1.e5]",
      "[Infinity]", "[1 2]",
      "[\"a\x01" "b\"]", "{\"a\x01" "b\":1}", "[\"\xff\"]", "{\"\xc3\":1}", "{\"\":1,\"\":2}",
      object_with_keys(17, 16, "kA"), object_with_keys(40, 39, "k\\u0041"), object_with_keys(1000, 999, "kA"),
      "{\"a\":{\"b\":1,\"b\":2}}", "[{\"x\":1,\"y\":2,\"x\":3}]"};
  for (int i = 0; i < 10; ++i) {
    std::string f = fixture(i);
    out.push_back(f.substr(0, f.size() - 1 - size_t(i)));
  }
  for (int i = 0; i < 5; ++i) out.push_back(fixture(i) + " x");
  for (int i = 0; i < 5; ++i) {
    std::string f = fixture(i);
    out.push_back(f.substr(0, f.size() - 1) + ",\"id\":0}");
  }
  return out;
}
void validate_only_rejects_everything_decode_rejects() {
  auto bad = malformed();
  require(bad.size() == 60);
  json::session s(myna::value_limits{}, tape::storage_limits{});
  for (const auto& source : bad) {
    int32_t decoded = status_of([&] { document d(source); });
    int32_t validated = status_of([&] { s.decode(source.data(), source.size(), json::session::encoding::utf8, true); });
    if (decoded == json::status_ok || decoded != validated)
      throw std::runtime_error("validate/decode disagree (" + std::to_string(decoded) + " vs " + std::to_string(validated) +
                               ") on: " + source.substr(0, 60));
    require(!s.live());
  }
  for (const auto& source : corpus()) {
    auto d = s.decode(source.data(), source.size(), json::session::encoding::utf8, true);
    require(!d.tape && !s.live() && d.input_length == source.size());
  }
  // Limits: the same values, text bytes, depth and input bounds.
  const std::string doc = "[\"xx\",[1],{\"k\":\"v\"}]";
  for (auto limits : {myna::value_limits{2, 100, 10}, myna::value_limits{100, 1, 10}, myna::value_limits{100, 100, 1},
                      myna::value_limits{6, 100, 10}, myna::value_limits{7, 100, 10}, myna::value_limits{100, 3, 10},
                      myna::value_limits{100, 4, 10}, myna::value_limits{100, 100, 2}}) {
    json::session limited(limits, tape::storage_limits{});
    int32_t decoded = status_of([&] { document d(doc, limits); });
    int32_t validated = status_of([&] { limited.decode(doc.data(), doc.size(), json::session::encoding::utf8, true); });
    require(decoded == validated);
  }
  require(status_of([&] { document d(doc, {6, 100, 10}); }) == json::status_limit);
  require(status_of([&] { document d(doc, {7, 4, 2}); }) == json::status_ok);
  require(status_of([&] { document d(doc, {7, 3, 10}); }) == json::status_limit);
  require(status_of([&] { document d(doc, {7, 4, 1}); }) == json::status_limit);
}

// ---- documents, hints and allocation --------------------------------------------------------------
void documents_and_hints() {
  // The fixture: one presized block, nine members, a directory of nine entries plus the dims object's.
  document small(fixture(1));
  const td_tape_view& v = small.view();
  require(c::validate(v) == nullptr && small.value_count() == 31);
  require(v.directory_count == 9 + 3 && small.allocations().index_allocations == 1);
  require(v.input_bytes == fixture(1).size());
  // The escaped note is owned; plain strings reference the input.
  {
    document d(R"({"s":"plain text","e":"esc\"aped","k\u0041":1})");
    const td_tape_view& t = d.view();
    require(c::validate(t) == nullptr && t.pool_bytes == std::string("esc\"aped").size() + 2);
    require(std::string(c::text(t, 3)) == "plain text" && !(t.words[3] & c::owned_flag));
    require(std::string(c::text(t, 5)) == "esc\"aped" && (t.words[5] & c::owned_flag));
    require(std::string(c::text(t, 7)) == "kA" && (t.words[7] & c::owned_flag) && (t.words[7] & c::key_flag));
  }
  // Singleton objects carry no directory; objects of two or more do.
  {
    document d(R"({"a":{"b":1},"c":{}})");
    const td_tape_view& t = d.view();
    require(c::validate(t) == nullptr && t.directory_count == 2);
    require(c::directory_of(t.words[1]) == 1 && c::directory_of(t.words[4]) == 0 && c::directory_of(t.words[10]) == 0);
  }
  // The 1,000-record array: a handful of large allocations; the compact tape is far smaller than
  // the 0.8.0 tape and its side tables.
  {
    std::string big = "[";
    for (int i = 0; i < 1000; ++i) big += (i ? "," : "") + fixture(i);
    big += "]";
    document large(big);
    require(large.value_count() == 31001 && large.allocations().index_allocations <= 8);
    json::legacy::document reference(big);
    require(large.allocations().index_retained_bytes < reference.allocations().index_retained_bytes);
  }
  // Growth past the hint (one word per input byte) moves to larger spans; the overlap is charged
  // to the peak, the exact peak budget decodes and one byte less is a limit.
  {
    std::string dense = "[";
    for (int i = 0; i < 40000; ++i) dense += i ? ",1" : "1";
    dense += "]";
    document grown(dense);
    auto stats = grown.allocations();
    require(grown.value_count() == 40001 && grown.view().word_count == 80002);
    require(stats.index_peak_bytes > stats.index_retained_bytes);
    { document exact(dense, {}, tape::storage_limits{dense.size(), stats.index_peak_bytes}); }
    require(throws([&] { document d(dense, {}, tape::storage_limits{dense.size(), stats.index_peak_bytes - 1}); }));
    require(throws([&] { document d(dense, {}, tape::storage_limits{dense.size() - 1, SIZE_MAX}); }));
  }
  // Adopted input is never copied.
  {
    const std::string source = R"({"s":"abcdefghijklmnopqrstuvwxyz0123456789"})";
    std::vector<char> padded(source.size() + simdjson::SIMDJSON_PADDING, '\0');
    std::memcpy(padded.data(), source.data(), source.size());
    document d(padded.data(), source.size(), padded.size());
    require(d.adopted() && d.view().input == reinterpret_cast<const uint8_t*>(padded.data()));
    require(d.allocations().copied_input_bytes == 0);
  }
}

// Every upstream allocation of a decode can fail without leaking: with the real hint (the document
// class, over a block arena) and with a one-element hint over a resource that sends every request
// upstream, so each growth of the compact writer's words, pool, directory and pending keys (and the
// validate-only key stack) is its own upstream allocation.
class owning_memory final : public std::pmr::memory_resource {
  std::pmr::memory_resource* upstream_;
  struct block { void* p; size_t bytes, alignment; };
  std::vector<block> live_;
  void* do_allocate(size_t bytes, size_t alignment) override {
    void* p = upstream_->allocate(bytes, alignment);
    live_.push_back({p, bytes, alignment});
    return p;
  }
  void do_deallocate(void* p, size_t bytes, size_t alignment) override {
    for (auto& b : live_)
      if (b.p == p) { b = live_.back(); live_.pop_back(); break; }
    upstream_->deallocate(p, bytes, alignment);
  }
  bool do_is_equal(const std::pmr::memory_resource& other) const noexcept override { return this == &other; }
 public:
  explicit owning_memory(std::pmr::memory_resource* upstream) : upstream_(upstream) {}
  ~owning_memory() override {
    for (auto& b : live_) upstream_->deallocate(b.p, b.bytes, b.alignment);
  }
};
template<class Writer> void decode_with(std::pmr::memory_resource* upstream, const std::string& source,
                                        const json::decode_hint& hint, bool arena) {
  tape::index_memory memory(SIZE_MAX, upstream);
  tape::block_arena blocks(&memory);
  owning_memory each(&memory);
  simdjson::padded_string padded(source);
  json::tape_decoder<Writer> decoder(arena ? static_cast<std::pmr::memory_resource*>(&blocks) : &each, {});
  decoder.start(std::string_view(padded.data(), padded.size()), hint);
  simdjson::ondemand::parser parser;
  auto root = parser.iterate(padded).value();
  decoder.decode(root);
}
template<class Run> size_t fail_every_allocation(Run run) {
  fault_memory counting(SIZE_MAX);
  run(&counting);
  require(counting.live == 0);
  for (size_t i = 0; i < counting.calls; ++i) {
    fault_memory memory(i);
    bool failed = false;
    try { run(&memory); } catch (const std::bad_alloc&) { failed = true; }
    require(failed && memory.live == 0);
  }
  return counting.calls;
}
void allocation_failure_at_every_growth() {
  std::string source = "[";
  for (int o = 0; o < 3; ++o) {
    source += o ? ",{" : "{";
    for (int i = 0; i < 300; ++i)
      source += (i ? ",\"key-" : "\"key-") + std::to_string(i) + (i % 3 ? "\":" : "\\u0041\":") +
                (i % 2 ? "\"esc\\naped value " + std::to_string(i) + "\"" : std::to_string(i));
    source += "}";
  }
  source += ",[1,2,3,4,5,6,7,8,9,10]]";
  const json::decode_hint tiny{1, 1, 1, 0};
  size_t calls = fail_every_allocation([&](std::pmr::memory_resource* m) { decode_with<tape::ctape_writer>(m, source, tiny, false); });
  std::printf("allocation_failure_at_every_growth: %zu upstream allocations with a one-element hint\n", calls);
  require(calls > 20);  // words, pool, directory and pending keys each grew several times
  fail_every_allocation([&](std::pmr::memory_resource* m) {
    decode_with<tape::ctape_writer>(m, source, json::hint_for(source.size(), {}, {}), true);
  });
  require(fail_every_allocation([&](std::pmr::memory_resource* m) { decode_with<json::null_writer>(m, source, tiny, false); }) > 10);
  fail_every_allocation([&](std::pmr::memory_resource* m) { document d(source, {}, {}, m); });
  // A failed duplicate check frees everything too.
  fault_memory memory(SIZE_MAX);
  require(throws([&] { document d(object_with_keys(300, 299, "kA"), {}, {}, &memory); }) && memory.live == 0);
}

void templates_survive_documents_and_reject_escaped_duplicates() {
  auto object = [](const std::vector<std::string>& keys) {
    std::string text = "{";
    for (size_t i = 0; i < keys.size(); ++i) {
      if (i) text += ',';
      text += '"' + keys[i] + "\":" + std::to_string(i);
    }
    return text + '}';
  };
  for (bool validate : {false, true}) {
    json::session session({}, {});
    auto decode = [&](const std::string& text) {
      return status_of([&] { session.decode(text.data(), text.size(), json::session::encoding::utf8, validate); });
    };
    for (unsigned count = 2; count <= 32; ++count) {
      std::vector<std::string> keys;
      for (unsigned i = 0; i < count; ++i) keys.push_back("key" + std::to_string(i));
      require(decode(object(keys)) == json::status_ok);
      // Different encoded spelling of the exact same decoded template sequence is a hit.
      keys[0] = "\\u006bey0";
      require(decode(object(keys)) == json::status_ok);
      for (unsigned position = 0; position <= count; ++position) {
        auto duplicate = keys;
        duplicate.insert(duplicate.begin() + position, "key0");
        require(decode(object(duplicate)) == json::status_syntax);
      }
      keys.pop_back();
      require(decode(object(keys)) == json::status_ok);
      keys.push_back(std::string(40, 'x'));
      require(decode(object(keys)) == json::status_ok);
      keys.push_back(std::string(40, 'x'));
      require(decode(object(keys)) == json::status_syntax);
    }
  }
}
}  // namespace

int main() {
  struct test { const char* name; void (*run)(); };
  const test tests[] = {
      {"templates_survive_documents_and_reject_escaped_duplicates", templates_survive_documents_and_reject_escaped_duplicates},
      {"words_match_reference_corpus", words_match_reference_corpus},
      {"walk_matches_reference", walk_matches_reference},
      {"duplicate_rejected_after_unescaping", duplicate_rejected_after_unescaping},
      {"number_classification_unchanged", number_classification_unchanged},
      {"raw_control_byte_rejected", raw_control_byte_rejected},
      {"invalid_utf8_rejected_before_walk", invalid_utf8_rejected_before_walk},
      {"spans_are_exact_tokens", spans_are_exact_tokens},
      {"validate_only_rejects_everything_decode_rejects", validate_only_rejects_everything_decode_rejects},
      {"documents_and_hints", documents_and_hints},
      {"allocation_failure_at_every_growth", allocation_failure_at_every_growth},
  };
  int failures = 0;
  for (const auto& t : tests) {
    try {
      t.run();
      std::printf("ok   decoder.%s\n", t.name);
    } catch (const std::exception& e) {
      std::fprintf(stderr, "FAIL decoder.%s: %s\n", t.name, e.what());
      ++failures;
    }
  }
  return failures ? 1 : 0;
}
