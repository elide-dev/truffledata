#include "json/native/decoder.h"
#include "json/native/parser_pool.h"
#include <cstring>
#include <vector>
#include <iostream>
#include <stdexcept>
#include <string>
using truffledata::json::document;
void require_at(bool condition, int line) { if (!condition) throw std::runtime_error("check failed at line " + std::to_string(line)); }
#define require(condition) require_at((condition), __LINE__)
std::string text(const myna_value_view& view, size_t index) {
  const auto& node = view.nodes[index];
  if ((node.tag & MYNA_VALUE_REGION_TEXT) != 0) {
    auto region = node.payload >> 32;
    auto offset = uint32_t(node.payload);
    auto data = region == 0 ? view.bytes : view.regions[region - 1].data;
    return std::string(reinterpret_cast<const char*>(data + offset), node.length);
  }
  std::string result;
  for (uint32_t i = 0; i < node.length; ++i) result += char(myna_value_inline_byte(&node, i));
  return result;
}
std::string lexical_text(const document& parsed, size_t index) {
  uint64_t span = parsed.lexical_spans().at(index);
  const auto& region = parsed.view().regions[0];
  return std::string(reinterpret_cast<const char*>(region.data + (span >> 32)), uint32_t(span));
}
void number_checks() {
  document parsed(R"([1, -0, 18446744073709551615, 1.5, 1E400, 12345678901234567890, {"k\u0041":"v"}])");
  const auto* n = parsed.view().nodes;
  require(n[1].tag == MYNA_VALUE_I64 && n[1].payload == 1);
  require(n[2].tag == MYNA_VALUE_F64 && n[2].payload == 0x8000000000000000ull);
  require(n[3].tag == MYNA_VALUE_U64 && n[3].payload == UINT64_MAX);
  require(n[4].tag == MYNA_VALUE_F64);
  require((n[5].tag & 0xff) == MYNA_VALUE_DECIMAL);
  require(n[6].tag == MYNA_VALUE_U64 && n[6].payload == 12345678901234567890ull);
  require(lexical_text(parsed, 1) == "1");
  require(lexical_text(parsed, 2) == "-0");
  require(lexical_text(parsed, 5) == "1E400");
  require(lexical_text(parsed, 6) == "12345678901234567890");
  // Containers, booleans and nulls carry no span; keys and strings keep their quotes.
  require(parsed.lexical_spans().at(0) == 0 && parsed.lexical_spans().at(7) == 0);
  require(lexical_text(parsed, 8) == "\"k\\u0041\"");
  require(lexical_text(parsed, 9) == "\"v\"");
  require(parsed.lexical_spans().size() == parsed.view().node_count);
}
class fault_memory final : public std::pmr::memory_resource {
public:
  size_t fail_at, calls = 0, live = 0;
  explicit fault_memory(size_t fail) : fail_at(fail) {}
private:
  void* do_allocate(size_t bytes, size_t alignment) override {
    if (calls++ == fail_at) throw std::bad_alloc();
    auto pointer = std::pmr::new_delete_resource()->allocate(bytes, alignment);
    live += bytes;
    return pointer;
  }
  void do_deallocate(void* pointer, size_t bytes, size_t alignment) override {
    std::pmr::new_delete_resource()->deallocate(pointer, bytes, alignment);
    live -= bytes;
  }
  bool do_is_equal(const std::pmr::memory_resource& other) const noexcept override {
    return this == &other;
  }
};

void allocation_checks() {
  using truffledata::json::storage_limits;
  const std::string source = R"({"long":"some long escaped content \u4e2d", "nested":[{"a":1,"b":true},null,4]})";
  document baseline(source);
  const auto stats = baseline.allocations();
  require(stats.copied_input_bytes == source.size());
  require(stats.padded_input_bytes == source.size() + simdjson::SIMDJSON_PADDING);
  // A small document fits one presized arena block: nothing transient overlaps it. Peak
  // accounting of transient overlap is pinned by tape_writer_checks (growth past the hint).
  require(stats.index_peak_bytes >= stats.index_retained_bytes && stats.index_retained_bytes > 0);
  require(stats.index_allocations == 1);
  require(stats.parser_input_capacity >= source.size());
  {
    document exact(source, {}, storage_limits{source.size(), stats.index_peak_bytes});
    require(exact.view().node_count == baseline.view().node_count);
  }
  for (auto limits : {storage_limits{source.size() - 1, stats.index_peak_bytes},
                      storage_limits{source.size(), stats.index_peak_bytes - 1},
                      storage_limits{source.size(), 0}}) {
    fault_memory memory(SIZE_MAX);
    bool rejected = false;
    try { document limited(source, {}, limits, &memory); }
    catch (const std::length_error&) { rejected = true; }
    require(rejected);
    require(memory.live == 0);
    if (limits.input_bytes < source.size()) require(memory.calls == 0);
  }
  // Fail each allocation, including transient duplicate-key tables and vector
  // growth. Constructor failure must release every earlier allocation.
  for (size_t i = 0; i <= stats.index_allocations; ++i) {
    fault_memory memory(i);
    bool rejected = false;
    try { document parsed(source, {}, {}, &memory); }
    catch (const std::bad_alloc&) { rejected = true; }
    require(rejected == (i < stats.index_allocations));
    require(memory.live == 0);
  }
  // Adopted input: every index allocation can still fail without leaking, and the
  // caller's buffer is never copied when it carries the parser padding.
  std::vector<char> padded(source.size() + simdjson::SIMDJSON_PADDING, '\0');
  std::memcpy(padded.data(), source.data(), source.size());
  for (size_t i = 0; i <= stats.index_allocations; ++i) {
    fault_memory memory(i);
    bool rejected = false;
    try {
      document parsed(padded.data(), source.size(), padded.size(), {}, {}, &memory);
      require(parsed.adopted() && parsed.allocations().copied_input_bytes == 0);
    } catch (const std::bad_alloc&) { rejected = true; }
    require(rejected == (i < stats.index_allocations));
    require(memory.live == 0);
  }
  fault_memory memory(SIZE_MAX);
  bool rejected = false;
  try { document invalid(R"({"x":[1,2,3],"x":4})", {}, {}, &memory); }
  catch (const std::invalid_argument&) { rejected = true; }
  require(rejected && memory.live == 0);
}

void retained_text_checks() {
  using truffledata::json::parser_lease;
  using truffledata::json::parser_pool;
  // 14 escaped CJK characters: 42 decoded bytes, forcing region (non-inline) storage.
  document a(R"({"s":"\u4e2d\u4e2d\u4e2d\u4e2d\u4e2d\u4e2d\u4e2d\u4e2d\u4e2d\u4e2d\u4e2d\u4e2d\u4e2d\u4e2d"})");
  const std::string first = text(a.view(), 2);
  require(first.size() == 42);
  document b(R"({"s":"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})");
  require(text(a.view(), 2) == first);
  // Unescaped long strings borrow the retained input region (region 1), skipping `["`.
  document plain(R"(["abcdefghijklmnopqrstuvwxyz0123456789"])");
  const auto& node = plain.view().nodes[1];
  require((node.tag & MYNA_VALUE_REGION_TEXT) != 0);
  require((node.payload >> 32) == 1 && uint32_t(node.payload) == 2);
  require(text(plain.view(), 1) == "abcdefghijklmnopqrstuvwxyz0123456789");
  // Documents do not retain parsers: the pool caps retained parsers at 8.
  { std::vector<parser_lease> leases; for (int i = 0; i < 10; ++i) leases.push_back(parser_pool::acquire()); }
  require(parser_pool::retained() <= 8);
  require(parser_pool::retained() >= 1);
  // A parser that grew past the retention limit is freed rather than pooled.
  {
    std::string big = "[" + std::string(parser_pool::retained_capacity_limit(), ' ') + "1]";
    document grown(big);
    (void)grown;
  }
  {
    std::vector<parser_lease> idle;
    for (size_t i = 0, n = parser_pool::retained(); i < n; ++i) idle.push_back(parser_pool::acquire());
    for (auto& lease : idle) require(lease.get().capacity() <= parser_pool::retained_capacity_limit());
  }
}

template<class F> bool throws(F f) {
  try { f(); } catch (const std::exception&) { return true; }
  return false;
}
uint32_t fnv(const std::string& s) {
  uint32_t h = 2166136261u;
  for (unsigned char c : s) { h ^= c; h *= 16777619u; }
  return h & 0x7fffffffu;
}
std::string object_with_keys(size_t count, size_t duplicate_at, const std::string& spelling) {
  std::string source = "{";
  for (size_t i = 0; i < count; ++i) {
    if (i) source += ',';
    std::string key = i == duplicate_at ? spelling : (i == 0 ? "kA" : "k" + std::to_string(i));
    source += "\"" + key + "\":" + std::to_string(i);
  }
  return source + "}";
}
// Plan Task A1: number-first typing, deferred unescaping, hashed duplicate table, side table.
void lean_checks() {
  using truffledata::json::ascii_flag;
  // Review focus 2: a raw control byte or invalid UTF-8 never reaches the referenced path.
  require(throws([] { document d("[\"a\x01" "bcdefghijklmnopqrstuvwxyz\"]"); }));
  require(throws([] { document d("[\"a\x01" "b\"]"); }));
  require(throws([] { document d("{\"a\x01" "b\":1}"); }));
  bool utf8 = false;
  try { document d("[\"\xff\"]"); } catch (const simdjson::simdjson_error&) { utf8 = true; }
  require(utf8);
  utf8 = false;
  try { document d("{\"\xc3\":1}"); } catch (const simdjson::simdjson_error&) { utf8 = true; }
  require(utf8);
  // Review focus 3: number classification and rejection are unchanged.
  {
    document d(R"([1, -0, 18446744073709551615, 1.5, 1E400, 12345678901234567890123, -0.0, 9223372036854775807, -9223372036854775808, 9223372036854775808])");
    const auto* n = d.view().nodes;
    require(n[1].tag == MYNA_VALUE_I64 && n[1].payload == 1);
    require(n[2].tag == MYNA_VALUE_F64 && n[2].payload == 0x8000000000000000ull);
    require(n[3].tag == MYNA_VALUE_U64 && n[3].payload == UINT64_MAX);
    require(n[4].tag == MYNA_VALUE_F64);
    require((n[5].tag & 0xff) == MYNA_VALUE_DECIMAL);
    require((n[6].tag & 0xff) == MYNA_VALUE_DECIMAL);
    require(n[7].tag == MYNA_VALUE_F64 && n[7].payload == 0x8000000000000000ull);
    require(n[8].tag == MYNA_VALUE_I64 && n[8].payload == uint64_t(INT64_MAX));
    require(n[9].tag == MYNA_VALUE_I64 && n[9].payload == uint64_t(INT64_MIN));
    require(n[10].tag == MYNA_VALUE_U64 && n[10].payload == 9223372036854775808ull);
    require(lexical_text(d, 6) == "12345678901234567890123");
  }
  for (auto bad : {"[01]", "[1.]", "[.5]", "[1e]", "[-]", "[+1]", "[1e+]", "[-01]", "[0x1]", "[1.e5]", "[Infinity]", "[1 2]"})
    require(throws([&] { document d(bad); }));
  // A clean key longer than 16 bytes references the retained input instead of being copied.
  {
    document d(R"({"abcdefghijklmnopqrst":1})");
    const auto& key = d.view().nodes[1];
    require((key.tag & MYNA_VALUE_REGION_TEXT) != 0 && (key.payload >> 32) == 1);
    require(uint32_t(key.payload) == 2 && key.length == 20);
    require(d.allocations().index_retained_bytes > 0);
    require(d.view().byte_count == 0);
  }
  // An escaped long key is decoded into owned bytes (region 0).
  {
    document d("{\"abcdefghij\\" "u006blmnopqrst\":1}");
    const auto& key = d.view().nodes[1];
    require((key.tag & MYNA_VALUE_REGION_TEXT) != 0 && (key.payload >> 32) == 0);
    require(text(d.view(), 1) == "abcdefghijklmnopqrst");
  }
  // Duplicates after escape decoding are rejected at every object size, including the
  // open-addressing table beyond 16 keys; distinct keys are accepted.
  for (size_t count : {2, 16, 17, 40, 1000}) {
    require(!throws([&] { document d(object_with_keys(count, SIZE_MAX, "")); }));
    require(throws([&] { document d(object_with_keys(count, count - 1, "k\\u0041")); }));
    require(throws([&] { document d(object_with_keys(count, count - 1, "kA")); }));
  }
  // Every allocation of an object above 16 keys (the open-addressing table and its growth) can
  // fail without leaking.
  {
    const std::string wide = "[" + object_with_keys(200, SIZE_MAX, "") + "," + object_with_keys(40, SIZE_MAX, "") + "]";
    document baseline(wide);
    size_t total = baseline.allocations().index_allocations;
    for (size_t i = 0; i <= total; ++i) {
      fault_memory memory(i);
      bool rejected = false;
      try { document parsed(wide, {}, {}, &memory); } catch (const std::bad_alloc&) { rejected = true; }
      require(rejected == (i < total));
      require(memory.live == 0);
    }
  }
  require(throws([] { document d(R"({"":1,"":2})"); }));
  require(!throws([] { document d(R"({"":1,"a":{"":2}})"); }));
  // Key hashes and ASCII flags are published per node.
  {
    // Keys 8 and 10 are an escaped and a raw non-ASCII key; string 9 is escaped non-ASCII.
    const std::string u = "\\" "u00e9";
    document even("{\"id\":1,\"note\":\"plain\",\"x\":[true],\"" + u + "\":\"" + u + "t" + u
                  + "\",\"\xc3\xa9" "2\":\"ok\"}");
    const auto& h = even.key_hashes();
    require(h.size() == even.view().node_count && even.lexical_spans().size() == h.size());
    // Key entries: ASCII flag | low 15 hash bits << 16 | offset from the object node.
    auto key = [](const std::string& name, uint32_t offset, bool plain) {
      return (plain ? ascii_flag : 0u) | ((fnv(name) & 0x7fffu) << 16) | offset;
    };
    require(h[0] == 0);
    require(h[1] == key("id", 1, true));
    require(h[2] == 0);
    require(h[3] == key("note", 3, true));
    require(h[4] == ascii_flag);  // "plain" string: no hash, ASCII
    require(h[5] == key("x", 5, true));
    require(h[6] == 0 && h[7] == 0);  // array and boolean
    require(h[8] == key("\xc3\xa9", 8, false));  // escaped non-ASCII key: hash of decoded bytes
    require(h[9] == 0);  // escaped non-ASCII string
    require(h[10] == key("\xc3\xa9" "2", 10, false));  // raw non-ASCII key
    require(h[11] == ascii_flag);
    // Offsets are relative to the key's own object, so a nested key never looks like a member of
    // its ancestor at the same distance.
    document nested(R"({"a":{"b":1},"id":5,"o":{"c":{"d":2}}})");
    const auto& n = nested.key_hashes();
    require(n[1] == key("a", 1, true) && n[3] == key("b", 1, true) && n[5] == key("id", 5, true));
    require(n[7] == key("o", 7, true) && n[9] == key("c", 1, true) && n[11] == key("d", 1, true));
    // Offsets beyond 16 bits are published as 0 (no position hint possible).
    std::string wide = "{";
    for (int i = 0; i < 40000; ++i) wide += (i ? ",\"k" : "\"k") + std::to_string(i) + "\":0";
    wide += "}";
    document far(wide);
    require((far.key_hashes()[65535] & 0xffffu) == 65535 && (far.key_hashes()[65537] & 0xffffu) == 0);
    require((far.key_hashes()[65537] >> 16 & 0x7fffu) == (fnv("k32768") & 0x7fffu));
  }
}

// The fixture document of the comparison harness and the perf probe (nine members, 210 B for i=1).
std::string fixture(int i) {
  std::string tags = "[", scores = "[";
  for (int k = 0; k < 1 + i % 4; k++) tags += (k ? ",\"t" : "\"t") + std::to_string(k) + "\"";
  for (int k = 0; k < 3 + (i & 1); k++) scores += (k ? "," : "") + std::to_string(i + k);
  std::string note = (i & 1) ? "\"line \\\"" + std::to_string(i) + "\\\" with \\\\ escapes\\n\\t\\u00e9 and \\ud83d\\ude00 tail\""
                             : "\"plain note for item " + std::to_string(i) + " padded beyond the inline text limit\"";
  return "{\"id\":" + std::to_string(1000 + 7 * i) + ",\"name\":\"item-" + std::to_string(i) + "\",\"price\":" +
         std::to_string(i) + ".25,\"active\":" + (i % 3 == 0 ? "true" : "false") + ",\"tags\":" + tags + "],\"dims\":{\"w\":" +
         std::to_string(3 * i) + ",\"h\":" + std::to_string(i) + ".5,\"unit\":\"cm\"},\"scores\":" + scores + "],\"note\":" + note +
         ",\"parent\":null}";
}
// 200 documents: fixture shapes plus escapes, exact numbers, deep nesting, wide objects, scalars.
std::vector<std::string> corpus() {
  std::vector<std::string> documents;
  for (int i = 0; i < 160; ++i) documents.push_back(fixture(i));
  std::string big = "[";
  for (int i = 0; i < 300; ++i) big += (i ? "," : "") + fixture(i);
  documents.push_back(big + "]");
  std::string dense = "[";
  for (int i = 0; i < 2000; ++i) dense += i ? ",1" : "1";
  documents.push_back(dense + "]");
  for (int i = 0; i < 38; ++i) {
    switch (i % 6) {
      case 0: documents.push_back("[" + std::to_string(i) + ",-0,1E400,18446744073709551615,12345678901234567890123,-1.5e-300]"); break;
      case 1: documents.push_back(object_with_keys(20 + i * 10, SIZE_MAX, "")); break;
      case 2: documents.push_back(std::string(size_t(40 + i), '[') + std::to_string(i) + std::string(size_t(40 + i), ']')); break;
      case 3: documents.push_back("\"" + std::string(size_t(i * 7), 'x') + "\\n\""); break;
      case 4: documents.push_back("{\"\\u00e9\":\"" + std::string(size_t(i), 'y') + "\",\"k\":{\"\":[]}}"); break;
      default: documents.push_back(i % 2 ? "null" : "{}"); break;
    }
  }
  return documents;
}
// Plan Task A2: the presized tape writer over a block arena.
void tape_writer_checks() {
  using truffledata::json::builder_document;
  auto documents = corpus();
  require(documents.size() == 200);
  for (const auto& source : documents) {
    document lean(source);
    builder_document reference(source);
    auto a = lean.view(), b = reference.view();
    require(a.version == b.version && a.flags == b.flags && a.node_count == b.node_count);
    require(std::memcmp(a.nodes, b.nodes, a.node_count * sizeof(myna_value_node)) == 0);
    require(a.byte_count == b.byte_count && (a.byte_count == 0 || std::memcmp(a.bytes, b.bytes, a.byte_count) == 0));
    require(a.region_count == 1 && b.region_count == 1);
    require(a.regions[0].length == b.regions[0].length && a.regions[0].length == source.size());
    require(std::memcmp(a.regions[0].data, b.regions[0].data, source.size()) == 0);
    require(lean.lexical_spans().size() == a.node_count && reference.lexical_spans().size() == a.node_count);
    require(std::memcmp(lean.lexical_spans().data(), reference.lexical_spans().data(), a.node_count * 8) == 0);
    require(std::memcmp(lean.key_hashes().data(), reference.key_hashes().data(), a.node_count * 4) == 0);
  }
  // The small fixture fits one arena block; the 1,000-record array needs a handful of large
  // allocations instead of one per container and growth step.
  {
    document small(fixture(1));
    require(small.view().node_count == 31);
    require(small.allocations().index_allocations <= 3);
    std::string big = "[";
    for (int i = 0; i < 1000; ++i) big += (i ? "," : "") + fixture(i);
    document large(big + "]");
    require(large.view().node_count == 31001 && large.allocations().index_allocations <= 8);
  }
  // Growth past the presized hint (one node per two input bytes) moves to larger spans; the
  // overlap of old and new large spans is charged to the peak, and every step can fail cleanly.
  {
    std::string dense = "[";
    for (int i = 0; i < 40000; ++i) dense += i ? ",1" : "1";
    dense += "]";
    document grown(dense);
    auto stats = grown.allocations();
    require(grown.view().node_count == 40001);
    require(stats.index_peak_bytes > stats.index_retained_bytes);
    {
      document exact(dense, {}, truffledata::json::storage_limits{dense.size(), stats.index_peak_bytes});
      require(exact.view().node_count == 40001);
    }
    require(throws([&] { document d(dense, {}, truffledata::json::storage_limits{dense.size(), stats.index_peak_bytes - 1}); }));
    for (size_t i = 0; i <= stats.index_allocations; ++i) {
      fault_memory memory(i);
      bool rejected = false;
      try { document parsed(dense, {}, {}, &memory); } catch (const std::bad_alloc&) { rejected = true; }
      require(rejected == (i < stats.index_allocations));
      require(memory.live == 0);
    }
  }
  // Myna limits are enforced identically by both writers, with nothing retained afterwards.
  for (auto limits : {myna::value_limits{2, 100, 10}, myna::value_limits{100, 1, 10}, myna::value_limits{100, 100, 1}}) {
    for (int writer = 0; writer < 2; ++writer) {
      fault_memory memory(SIZE_MAX);
      bool rejected = false;
      try {
        if (writer) builder_document d("[\"xx\",[1]]", limits, {}, &memory);
        else document d("[\"xx\",[1]]", limits, {}, &memory);
      } catch (const std::length_error&) { rejected = true; }
      require(rejected && memory.live == 0);
    }
  }
  // Long text past the inline limit lands in owned bytes or references the input exactly as before.
  {
    document d("[\"" + std::string(5000, 'z') + "\",\"\\u00e9" + std::string(5000, 'w') + "\"]");
    require(d.view().nodes[1].payload >> 32 == 1 && d.view().nodes[2].payload >> 32 == 0);
    require(d.view().byte_count == 5002);
  }
}

int checks();
int main() {
  try { return checks(); }
  catch (const std::exception& e) { std::cerr << "decoder test failure: " << e.what() << '\n'; return 1; }
}
int checks() {
  number_checks();
  lean_checks();
  tape_writer_checks();
  retained_text_checks();
  allocation_checks();
  for (auto source : {"null", "true", "false", "0", "-0", "1e999999999999999999999999", "[]", "{}", "\"hi\"", "[1,2,3]", "{\"a\":null,\"b\":[true,\"x\"]}"}) {
    try { document parsed(source); require(parsed.view().node_count > 0); }
    catch (const std::exception& e) { std::cerr << source << ": " << e.what() << '\n'; return 1; }
  }
  for (auto source : {"", "01", "1.", "1e", "-", "NaN", "[1,]", "{\"a\":1,}", "[tru]", "[xyz]", "{\"x\":undefined}", "{\"a\":1,\"unused\":01}", "{\"a\":1,\"\\u0061\":2}", "{} false", "1 2", "\"x\" []", "\"\\uD800\"", "[\"\\q\"]"}) {
    bool rejected = false;
    try { document parsed(source); } catch (const std::exception&) { rejected = true; }
    if (!rejected) { std::cerr << "accepted: " << source << '\n'; return 1; }
  }
  document first("{\"long\":\"escaped string with \\u4e2d content\",\"n\":1e9999999999999999999}");
  { document second("[false]"); }
  require(lexical_text(first, 2) == "\"escaped string with \\u4e2d content\"");
  require(first.view().node_count == 5);
  require(text(first.view(), 2) == "escaped string with 中 content");
  require(text(first.view(), 4) == "1e9999999999999999999");
  require(first.view().nodes[0].next == 5);
  for (auto limits : {myna::value_limits{2, 100, 10}, myna::value_limits{100, 1, 10}, myna::value_limits{100, 100, 1}}) {
    bool rejected = false;
    try { document limited("[\"xx\",[1]]", limits); } catch (const std::length_error&) { rejected = true; }
    require(rejected);
  }
  std::cout << "JSON parser-to-index compatibility checks passed\n";
  return 0;
}
