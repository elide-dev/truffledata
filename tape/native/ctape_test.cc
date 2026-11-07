// The compact tape (tape-gbps plan Task 1, §3): layout, directory-fused duplicate detection,
// saturation, YAML rewrites, validation, the Myna converter and the text arena over words.
//
// Until the JSON decoder writes compact tapes (Task 2), the 200-document corpus reaches ctape_writer
// through a transcoder from the shipped decoder's Myna tape plus its lexical spans and key meta:
// every string, number and key carries its exact token, so the transcoded tape is the one the
// decoder will write, and to_myna must give back the shipped decoder's tape node for node.
#include "tape/native/ctape.h"
#include "tape/native/testing.h"
#include "tape/native/text_arena.h"
#include "json/native/decoder.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <limits>
#include <memory_resource>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <vector>

using namespace truffledata::tape;
namespace c = truffledata::tape::ctape;

namespace {
void require_at(bool condition, const char* what, int line) {
  if (!condition) throw std::runtime_error("check failed at line " + std::to_string(line) + ": " + what);
}
#define require(condition) require_at((condition), #condition, __LINE__)

template<class F> bool throws(F f) {
  try { f(); } catch (const std::exception&) { return true; }
  return false;
}
template<class E, class F> bool throws_as(F f) {
  try { f(); } catch (const E&) { return true; } catch (const std::exception&) { return false; }
  return false;
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

uint32_t hash(std::string_view s) { return myna_value_hash(reinterpret_cast<const uint8_t*>(s.data()), s.size()); }
c::span span_of(std::string_view input, std::string_view token) {
  size_t at = input.find(token);
  if (at == std::string_view::npos) throw std::logic_error("token not in input");
  return {uint32_t(at), uint32_t(token.size())};
}
struct scratch {
  index_memory memory{SIZE_MAX, std::pmr::new_delete_resource()};
  block_arena arena{&memory};
};

// ---- the transcoder: shipped decoder (Myna v4, directory threshold 2) -> ctape_writer --------
struct reference {
  scratch s;
  simdjson::padded_string padded;
  truffledata::json::tape_decoder<tape_writer> decoder{&s.arena, {}, 2};
  myna_value_view_v4 view{};
  explicit reference(const std::string& source) : padded(source) {
    decoder.start(std::string_view(padded.data(), padded.size()),
                  truffledata::json::hint_for(padded.size(), {}, {}));
    simdjson::ondemand::parser parser;
    auto root = parser.iterate(padded).value();
    decoder.decode(root);
    view = decoder.view();
  }
  std::string_view input() const { return {padded.data(), padded.size()}; }
  std::string text(uint64_t i) const {
    const myna_value_node& n = view.base.nodes[i];
    if (n.tag & MYNA_VALUE_REGION_TEXT) {
      uint32_t region = myna_value_region_id(&n);
      const uint8_t* base = region == 0 ? view.base.bytes : view.base.regions[region - 1].data;
      return std::string(reinterpret_cast<const char*>(base) + myna_value_offset(&n), n.length);
    }
    std::string out;
    for (uint32_t b = 0; b < n.length; ++b) out += char(myna_value_inline_byte(&n, b));
    return out;
  }
};

uint64_t transcode(const reference& r, ctape_writer& w, uint64_t i, uint32_t depth, bool key) {
  const myna_value_node& n = r.view.base.nodes[i];
  uint64_t span = r.decoder.lexical_spans()[i];
  c::span token{uint32_t(span >> 32), uint32_t(span)};
  bool ascii = r.decoder.key_hashes()[i] & ascii_flag;
  switch (n.tag & MYNA_VALUE_KIND_MASK) {
    case MYNA_VALUE_NULL: w.null(); return i + 1;
    case MYNA_VALUE_BOOL: w.boolean(n.payload != 0); return i + 1;
    case MYNA_VALUE_I64: w.integer(int64_t(n.payload), token); return i + 1;
    case MYNA_VALUE_U64: w.unsigned_integer(n.payload, token); return i + 1;
    case MYNA_VALUE_F64: {
      double d;
      std::memcpy(&d, &n.payload, 8);
      w.floating(d, token);
      return i + 1;
    }
    case MYNA_VALUE_DECIMAL: w.decimal_ref(token); return i + 1;
    case MYNA_VALUE_STRING: {
      std::string decoded = r.text(i);
      // A body without escapes is exactly the decoded text (every escape shortens it).
      if (token.length == decoded.size() + 2) w.string_ref({token.offset + 1, uint32_t(decoded.size())}, ascii, key);
      else w.string(decoded, token, ascii, key);
      return myna_value_next(&n, i);
    }
    case MYNA_VALUE_ARRAY: {
      size_t at = w.begin(c::kind_array, depth);
      uint64_t k = i + 1;
      for (uint32_t e = 0; e < n.length; ++e) k = transcode(r, w, k, depth + 1, false);
      w.end(at, n.length);
      return n.next;
    }
    case MYNA_VALUE_OBJECT: {
      size_t at = w.begin(c::kind_object, depth);
      uint64_t k = i + 1;
      for (uint32_t m = 0; m < n.length; ++m) {
        w.member_key(at, hash(r.text(k)));
        k = transcode(r, w, k, depth + 1, true);
        k = transcode(r, w, k, depth + 1, false);
      }
      w.end(at, n.length);
      return n.next;
    }
  }
  throw std::logic_error("unexpected Myna node");
}

// A canonical event trace, from either representation.
void trace_myna(const reference& r, uint64_t& i, std::string& out, bool key) {
  const myna_value_node& n = r.view.base.nodes[i];
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
  std::vector<uint64_t> counts;
  void open(uint64_t, uint64_t w) {
    out += (c::kind(w) == c::kind_object ? "{" : "[") + std::to_string(c::field(w)) + ",";
  }
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

// ---- tests ----------------------------------------------------------------------------------

void words_follow_the_layout() {
  const std::string input = R"({"a":1,"bb":[true,null,"x\n"],"c":1.5})";
  scratch s;
  ctape_writer w(&s.arena, {});
  w.input(input);
  size_t root = w.begin(c::kind_object, 0, 0);
  w.member_key(root, hash("a"));
  w.string_ref(span_of(input, "a"), true, true);
  w.integer(1, span_of(input, "1"));
  w.member_key(root, hash("bb"));
  w.string_ref(span_of(input, "bb"), true, true);
  size_t array = w.begin(c::kind_array, 1, span_of(input, "[").offset);
  w.boolean(true);
  w.null();
  w.string("x\n", span_of(input, R"("x\n")"), true, false);
  w.end(array, 3);
  w.member_key(root, hash("c"));
  w.string_ref({uint32_t(input.find("\"c\"") + 1), 1}, true, true);
  w.floating(1.5, span_of(input, "1.5"));
  w.end(root, 3);
  td_tape_view v = w.view();
  require(v.version == TD_TAPE_VIEW_VERSION && v.flags == 0 && v.word_count == 15);
  require(v.input == reinterpret_cast<const uint8_t*>(input.data()) && v.input_bytes == input.size());
  const uint64_t* x = v.words;
  using c::make;
  // Root object: kind 9, WIDE, count 3, next 15; second word: directory 0 + 1, raw offset 0.
  require(x[0] == make(c::kind_object, c::wide_flag, 3, 15));
  require(x[1] == (uint64_t(1) << 32 | 0));
  require(x[2] == make(c::kind_string, c::key_flag | c::ascii_flag, 1, 2));
  require(x[3] == make(c::kind_i64, c::wide_flag, 1, 5) && x[4] == 1);
  require(x[5] == make(c::kind_string, c::key_flag | c::ascii_flag, 2, 8));
  require(x[6] == make(c::kind_array, c::wide_flag, 3, 12) && x[7] == 12);  // no directory; '[' at 12
  require(x[8] == make(c::kind_true, 0, 0, 0) && x[9] == make(c::kind_null, 0, 0, 0));
  // The escaped string is owned: decoded text in the pool, raw token (with quotes) in word 1.
  require(x[10] == make(c::kind_string, c::owned_flag | c::wide_flag | c::ascii_flag, 2, 0));
  require(x[11] == (uint64_t(span_of(input, R"("x\n")").offset) << 32 | 5));
  require(v.pool_bytes == 2 && std::memcmp(v.pool, "x\n", 2) == 0);
  require(x[12] == make(c::kind_string, c::key_flag | c::ascii_flag, 1, uint32_t(input.find("\"c\"") + 1)));
  double d;
  std::memcpy(&d, &x[14], 8);
  require(x[13] == make(c::kind_f64, c::wide_flag, 3, uint32_t(input.find("1.5"))) && d == 1.5);
  // Traversal: next(i) from word 0 only; second words are never nodes.
  require(c::after(0, x[0]) == 15 && c::after(2, x[2]) == 3 && c::after(3, x[3]) == 5 && c::after(6, x[6]) == 12);
  require(c::step(6, x[6]) == 8 && c::after(10, x[10]) == 12);
  // Directory: three (hash, key offset) entries sorted by unsigned hash then offset.
  require(v.directory_count == 3);
  uint64_t expected[3] = {c::entry(hash("a"), 2), c::entry(hash("bb"), 5), c::entry(hash("c"), 12)};
  std::sort(expected, expected + 3, [](uint64_t a, uint64_t b) {
    return c::entry_hash(a) < c::entry_hash(b) || (c::entry_hash(a) == c::entry_hash(b) && c::entry_offset(a) < c::entry_offset(b));
  });
  require(std::memcmp(v.directory, expected, sizeof expected) == 0);
  // The entry is Myna's directory entry, bit for bit: hash in the low half, key offset in the high.
  myna_value_directory_entry e;
  std::memcpy(&e, &v.directory[0], 8);
  require(e.hash == c::entry_hash(v.directory[0]) && e.key_offset == c::entry_offset(v.directory[0]));
  require(w.text_of(2) == "a" && w.text_of(5) == "bb" && w.text_of(10) == "x\n" && c::text(v, 12) == "c");
  require(w.value_count() == 10);
  const char* error = c::validate(v);
  require(error == nullptr);
  // Writer-side contract checks.
  require(throws([&] { w.floating(std::numeric_limits<double>::infinity(), {}); }));
  require(throws([&] { w.nonfinite(1.0, {}); }));
  require(throws_as<std::out_of_range>([&] { w.string_ref({uint32_t(input.size()), 1}, true, false); }));
  require(throws_as<std::length_error>([&] { w.decimal_ref({0, c::max_length + 1}); }));
  scratch t;
  ctape_writer limited(&t.arena, myna::value_limits{2, 100, 1});
  limited.input(input);
  size_t top = limited.begin(c::kind_array, 0);
  require(throws_as<std::length_error>([&] { limited.begin(c::kind_array, 1); }));  // depth
  limited.null();
  require(throws_as<std::length_error>([&] { limited.null(); }));  // values
  limited.end(top, 1);
}

// Builds {k0: 0, k1: 1, ...} with the given decoded key texts; key i is owned when owned[i].
void write_object(ctape_writer& w, const std::vector<std::string>& keys, std::string_view input,
                  const std::vector<bool>& owned = {}) {
  size_t object = w.begin(c::kind_object, 0);
  for (size_t i = 0; i < keys.size(); ++i) {
    w.member_key(object, hash(keys[i]));
    if (!owned.empty() && owned[i]) w.string(keys[i], {}, true, true);
    else w.string_ref(span_of(input, keys[i]), true, true);
    w.integer(int64_t(i), {});
  }
  w.end(object, keys.size());
}
bool duplicate(const std::vector<std::string>& keys, const std::vector<bool>& owned = {}) {
  std::string input;
  for (const auto& k : keys) input += "\"" + k + "\"";
  scratch s;
  ctape_writer w(&s.arena, {});
  w.input(input);
  bool found = throws_as<c::duplicate_key>([&] { write_object(w, keys, input, owned); });
  if (!found) require(c::validate(w.view()) == nullptr);
  // The rescanning close (YAML) detects exactly the same duplicates.
  scratch r;
  ctape_writer y(&r.arena, {});
  y.input(input);
  bool rescanned = throws_as<c::duplicate_key>([&] {
    size_t object = y.begin(c::kind_object, 0);
    for (size_t i = 0; i < keys.size(); ++i) {
      if (!owned.empty() && owned[i]) y.string(keys[i], {}, true, true);
      else y.string_ref(span_of(input, keys[i]), true, true);
      y.null();
    }
    y.end_rescan(object);
  });
  require(found == rescanned);
  return found;
}
std::vector<std::string> names(size_t count) {
  std::vector<std::string> keys;
  for (size_t i = 0; i < count; ++i) keys.push_back("key" + std::to_string(i) + "_");
  return keys;
}

void duplicates_detected_in_sorted_directory() {
  require(!duplicate({"a", "b"}));
  require(duplicate({"a", "a"}));                // adjacent
  require(duplicate({"a", "b", "c", "a"}));      // non-adjacent
  require(duplicate({"", ""}) && !duplicate({"", "a"}));
  for (size_t count : {2, 16, 17, 24, 25, 40, 1000}) {
    auto keys = names(count);
    require(!duplicate(keys));
    auto first = keys;
    first[count - 1] = first[0];  // the 17th (40th, ...) duplicating the 1st
    require(duplicate(first));
    auto middle = keys;
    middle[count / 2] = middle[count / 2 - 1];
    require(duplicate(middle));
  }
  // Escaped vs raw spellings of one key: an owned key equal to a referenced one.
  require(duplicate({"a", "b", "a"}, {false, false, true}));
  require(duplicate({"key0_", "key1_"}, {true, false}) == false);
  // Equal hashes with different bytes are not duplicates; a third key equal to the first one of a
  // colliding run is, even when its hash-equal neighbour differs.
  std::unordered_map<uint32_t, std::string> seen;
  std::string a, b;
  for (uint64_t i = 0;; ++i) {
    std::string candidate = "c" + std::to_string(i);
    auto [it, fresh] = seen.emplace(hash(candidate), candidate);
    if (!fresh) { a = it->second; b = candidate; break; }
  }
  require(a != b && hash(a) == hash(b));
  require(!duplicate({a, b}) && !duplicate({b, "x", a}));
  require(duplicate({a, b, a}) && duplicate({b, a, "y", b}));
  auto wide = names(40);
  wide[3] = a;
  wide[30] = b;
  require(!duplicate(wide));
  wide[39] = a;
  require(duplicate(wide));
  // Equal keys in different objects are not duplicates.
  {
    const std::string input = R"("a""b")";
    scratch s;
    ctape_writer w(&s.arena, {});
    w.input(input);
    size_t root = w.begin(c::kind_object, 0);
    w.member_key(root, hash("a"));
    w.string_ref({1, 1}, true, true);
    size_t inner = w.begin(c::kind_object, 1);
    w.member_key(inner, hash("a"));
    w.string_ref({1, 1}, true, true);
    w.null();
    w.member_key(inner, hash("b"));
    w.string_ref({4, 1}, true, true);
    w.null();
    w.end(inner, 2);
    w.member_key(root, hash("b"));
    w.string_ref({4, 1}, true, true);
    w.null();
    w.end(root, 2);
    require(c::validate(w.view()) == nullptr && w.view().directory_count == 4);
  }
}

void singleton_objects_have_no_directory() {
  const std::string input = R"({"a":{"b":1},"c":{}})";
  scratch s;
  ctape_writer w(&s.arena, {});
  w.input(input);
  size_t root = w.begin(c::kind_object, 0);
  w.member_key(root, hash("a"));
  w.string_ref(span_of(input, "a"), true, true);
  size_t one = w.begin(c::kind_object, 1, 5);
  w.member_key(one, hash("b"));
  w.string_ref(span_of(input, "b"), true, true);
  w.integer(1, span_of(input, "1"));
  w.end(one, 1);
  w.member_key(root, hash("c"));
  w.string_ref(span_of(input, "c"), true, true);
  size_t none = w.begin(c::kind_object, 1, uint32_t(input.find("{}")));
  w.end(none, 0);
  w.end(root, 2);
  td_tape_view v = w.view();
  require(c::validate(v) == nullptr);
  require(v.directory_count == 2);
  require(c::directory_of(v.words[one + 1]) == 0 && uint32_t(v.words[one + 1]) == 5);
  require(c::directory_of(v.words[none + 1]) == 0);
  require(c::directory_of(v.words[root + 1]) == 1);
  // The singleton's key did not stay pending: the root's directory names its own keys only.
  for (uint64_t i = 0; i < 2; ++i) {
    uint64_t key = root + c::entry_offset(v.directory[i]);
    require(c::kind(v.words[key]) == c::kind_string && (v.words[key] & c::key_flag));
    require(w.text_of(key) == "a" || w.text_of(key) == "c");
  }
}

void counts_saturate_and_validate() {
  scratch s;
  for (uint64_t n : {uint64_t(c::max_length), uint64_t(c::max_length) + 1}) {
    s.arena.reset();
    ctape_writer w(&s.arena, {});
    w.reserve(n + 2, 0, 0);
    size_t root = w.begin(c::kind_array, 0);
    for (uint64_t i = 0; i < n; ++i) w.null();
    w.end(root, n);
    td_tape_view v = w.view();
    require(c::field(v.words[0]) == c::max_length && c::after(0, v.words[0]) == n + 2);
    require(c::validate(v) == nullptr);
  }
  // A wrong count below saturation is rejected.
  ctape_writer w(&s.arena, {});
  size_t root = w.begin(c::kind_array, 0);
  w.null();
  w.null();
  w.end(root, 2);
  std::vector<uint64_t> words(w.view().words, w.view().words + w.view().word_count);
  words[0] = c::make(c::kind_array, c::wide_flag, 3, 4);
  td_tape_view v = w.view();
  v.words = words.data();
  require(c::validate(v) != nullptr);
}

void erase_append_truncate_renumber() {
  const std::string input = R"(abcdefghijklmnopqrstuvwxyz)";
  auto key = [&](ctape_writer& w, size_t, char k) { w.string_ref({uint32_t(k - 'a'), 1}, true, true); };
  // Last-wins: { a: {p, q}, b: [1, "...long..."], c: *b, d: {m, n}, a: 5 } -> the first `a` entry is
  // erased; the array, the REF to it and the closed object after it move down.
  {
    scratch s;
    ctape_writer w(&s.arena, {});
    w.input(input);
    size_t root = w.begin(c::kind_object, 0);
    key(w, root, 'a');
    size_t first = root + 2;  // key word index
    size_t pq = w.begin(c::kind_object, 1);
    key(w, pq, 'p'); w.null();
    key(w, pq, 'q'); w.null();
    w.end_rescan(pq);
    key(w, root, 'b');
    size_t array = w.begin(c::kind_array, 1);
    w.integer(1, {1, 1});
    w.string("a value longer than sixteen bytes", {}, true, false);
    w.end(array, 2);
    key(w, root, 'c');
    size_t ref = w.ref(array);
    key(w, root, 'd');
    size_t mn = w.begin(c::kind_object, 1);
    key(w, mn, 'm'); w.boolean(false);
    key(w, mn, 'n'); w.boolean(true);
    w.end_rescan(mn);
    uint32_t mn_directory = c::directory_of(w.words()[mn + 1]);
    require(mn_directory != 0);
    // A REF target cannot be erased; nothing changes when that is refused.
    size_t before = w.word_count();
    require(throws([&] { w.erase(array - 1, ref - array + 1); }));
    require(w.word_count() == before);
    // Erase key `a` and its value subtree.
    size_t erased = array - 1 - first;  // key a + {p, q}
    w.erase(first, erased);
    key(w, root, 'a');
    w.integer(5, {});
    w.end_rescan(root);
    td_tape_view v = w.view();
    require(c::validate(v) == nullptr);
    size_t array2 = array - erased, ref2 = ref - erased, mn2 = mn - erased;
    require(c::kind(v.words[array2]) == c::kind_array && c::after(array2, v.words[array2]) == ref2 - 1);
    require(c::kind(v.words[ref2]) == c::kind_ref && c::low(v.words[ref2]) == array2);
    require(c::after(mn2, v.words[mn2]) == mn2 + 2 + 4);
    // The closed object's directory moved with it: entries are object-relative.
    require(c::directory_of(v.words[mn2 + 1]) == mn_directory);
    require(c::after(0, v.words[0]) == v.word_count && c::field(v.words[0]) == 4);
    require(w.value_count() == 15);
  }
  // Merge: { base: {k: 1, j: [2]}, m: {<<: {x: 1, y: 2}, z: 3}, r: {<<: *base, k: 0} } — the
  // inline source's entries move into place by save/truncate/append blocks; the aliased source
  // contributes key copies plus REFs to the source's values.
  {
    scratch s;
    ctape_writer w(&s.arena, {});
    w.input(input);
    size_t root = w.begin(c::kind_object, 0);
    key(w, root, 'b');
    size_t base = w.begin(c::kind_object, 1);
    key(w, base, 'k');
    size_t one = w.word_count();
    w.integer(1, {});
    key(w, base, 'j');
    size_t j_value = w.begin(c::kind_array, 2);
    w.integer(2, {});
    w.end(j_value, 1);
    w.end_rescan(base);
    key(w, root, 'm');
    size_t m = w.begin(c::kind_object, 1);
    w.string("<<", {}, true, true);
    size_t source = w.begin(c::kind_object, 2);
    key(w, source, 'x'); w.integer(1, {});
    key(w, source, 'y'); w.integer(2, {});
    w.end_rescan(source);
    size_t z = w.word_count();
    key(w, m, 'z');
    w.integer(3, {});
    // Rewrite m's children: [x 1][y 2][z 3] from the saved words.
    size_t start = m + 2, end = w.word_count();
    std::vector<uint64_t> saved(w.words() + start, w.words() + end);
    w.truncate(start);
    size_t x_at = source + 2 - start;  // x entry inside saved
    w.append(saved.data() + x_at, 6, int64_t(w.word_count()) - int64_t(source + 2));
    w.append(saved.data() + (z - start), 3, int64_t(w.word_count()) - int64_t(z));
    w.end_rescan(m);
    // r: {<<: *base, k: 0} -> {k: 0, j: *base.j} (explicit k wins; j is a key copy plus a REF).
    key(w, root, 'r');
    size_t r = w.begin(c::kind_object, 1);
    key(w, r, 'k');
    w.integer(0, {});
    uint64_t j_key = w.words()[j_value - 1];
    w.append(&j_key, 1, int64_t(w.word_count()) - int64_t(j_value - 1));
    w.ref(j_value);
    w.end_rescan(r);
    w.end_rescan(root);
    td_tape_view v = w.view();
    require(c::validate(v) == nullptr);
    require(c::field(v.words[m]) == 3 && c::after(m, v.words[m]) == m + 2 + 9);
    require(w.text_of(m + 2) == "x" && w.text_of(m + 5) == "y" && w.text_of(m + 8) == "z");
    require(c::field(v.words[r]) == 2 && w.text_of(r + 5) == "j" && c::low(v.words[r + 6]) == j_value);
    require(c::kind(v.words[one]) == c::kind_i64 && v.words[one + 1] == 1);
  }
  // A closed block copied with an inner REF and an outer REF; truncation drops values and their count.
  {
    scratch s;
    ctape_writer w(&s.arena, {});
    w.input(input);
    size_t root = w.begin(c::kind_array, 0);
    w.integer(7, {});                     // 2: outer target
    size_t block = w.begin(c::kind_array, 1);  // 4
    w.integer(8, {});                     // 6: inner target
    w.ref(6);                             // 8: inner REF
    w.ref(2);                             // 9: outer REF
    w.end(block, 3);                      // next 10
    std::vector<uint64_t> saved(w.words() + block, w.words() + w.word_count());
    size_t values = w.value_count();
    w.truncate(block);
    require(w.value_count() == values - 4);
    w.boolean(true);                      // 4
    w.append(saved.data(), saved.size(), 1);  // block now at 5
    require(w.value_count() == values + 1);
    w.end(root, 3);
    td_tape_view v = w.view();
    require(c::validate(v) == nullptr);
    require(c::after(5, v.words[5]) == 11 && c::low(v.words[9]) == 7 && c::low(v.words[10]) == 2);
    require(throws([&] { w.append(saved.data(), 1, 0); }));  // half a container
  }
  // Rewrites refuse to run while announced keys are pending (JSON never rewrites).
  {
    scratch s;
    ctape_writer w(&s.arena, {});
    w.input(input);
    size_t root = w.begin(c::kind_object, 0);
    w.member_key(root, hash("a"));
    w.string_ref({0, 1}, true, true);
    w.null();
    require(throws_as<std::logic_error>([&] { w.truncate(2); }));
    require(throws_as<std::logic_error>([&] { w.erase(2, 2); }));
    w.end(root, 1);
  }
}

void to_myna_matches_tape_writer() {
  auto documents = testing::corpus();
  require(documents.size() == 200);
  size_t compared = 0;
  for (const auto& source : documents) {
    reference r(source);
    scratch s;
    ctape_writer w(&s.arena, {});
    w.input(r.input());
    require(transcode(r, w, 0, 0, false) == r.view.base.node_count);
    td_tape_view v = w.view();
    const char* error = c::validate(v);
    if (error) throw std::runtime_error(std::string("invalid transcoded tape: ") + error + " in " + source.substr(0, 60));
    require(w.value_count() == r.view.base.node_count);
    myna_value_view_v4 m = c::to_myna(v, &s.arena);
    const myna_value_view& a = m.base;
    const myna_value_view& b = r.view.base;
    require(a.version == MYNA_VALUE_ABI_V4 && a.version == b.version && a.flags == b.flags);
    require(a.node_count == b.node_count);
    require(std::memcmp(a.nodes, b.nodes, a.node_count * sizeof(myna_value_node)) == 0);
    require(a.byte_count == b.byte_count && (a.byte_count == 0 || std::memcmp(a.bytes, b.bytes, a.byte_count) == 0));
    require(a.region_count == 1 && b.region_count == 1);
    require(a.regions[0].data == b.regions[0].data && a.regions[0].length == b.regions[0].length);
    require(m.directory_count == r.view.directory_count);
    require(m.directory_count == 0
            || std::memcmp(m.directory, r.view.directory, m.directory_count * sizeof(myna_value_directory_entry)) == 0);
    require(m.metadata_count == 0);
    // The v3 prefix: the same nodes without directories.
    myna_value_view_v4 p = c::to_myna(v, &s.arena, MYNA_VALUE_ABI);
    require(p.base.version == MYNA_VALUE_ABI && p.directory_count == 0 && p.base.node_count == b.node_count);
    for (uint64_t i = 0; i < b.node_count; ++i) {
      const myna_value_node& n = b.nodes[i];
      require(p.base.nodes[i].payload == (n.tag == MYNA_VALUE_OBJECT ? 0 : n.payload));
    }
    ++compared;
  }
  require(compared == 200);
  // REF and metadata (YAML): a REF becomes a Myna REF to the target's node; metadata follows nodes.
  {
    const std::string input = "abc";
    scratch s;
    ctape_writer w(&s.arena, {});
    w.input(input);
    size_t root = w.begin(c::kind_array, 0);
    w.integer(-4, {0, 1});                    // node 1, words 2-3
    size_t target = w.begin(c::kind_array, 1);  // node 2, word 4
    w.nonfinite(std::numeric_limits<double>::infinity(), {});
    w.end(target, 1);
    w.ref(target);                            // node 4, word 8
    w.decimal("1e400", {0, 3});               // owned, inline in Myna
    w.end(root, 4);
    td_tape_view v = w.view();
    require(v.flags == TD_TAPE_NONFINITE);
    std::vector<uint32_t> meta(v.word_count, 0);
    meta[4] = 0x77;
    meta[8] = 0x11;
    v.meta = meta.data();
    v.meta_count = meta.size();
    v.flags |= TD_TAPE_HAS_META;
    require(c::validate(v) == nullptr);
    myna_value_view_v4 m = c::to_myna(v, &s.arena);
    require(m.base.flags == MYNA_VALUE_ALLOW_NON_FINITE && m.base.node_count == 6);
    require(m.base.nodes[4].tag == MYNA_VALUE_REF && m.base.nodes[4].payload == 2 && m.base.nodes[4].next == 5);
    require(m.base.nodes[2].next == 4 && m.base.nodes[0].next == 6 && m.base.nodes[0].length == 4);
    require(m.base.nodes[5].tag == MYNA_VALUE_DECIMAL && m.base.nodes[5].length == 5);
    require(m.metadata_count == 6 && m.metadata[2] == 0x77 && m.metadata[4] == 0x11 && m.metadata[1] == 0);
    require(throws([&] { c::to_myna(v, &s.arena, MYNA_VALUE_ABI); }));  // REF needs ABI 4
  }
}

void walk_matches_reference() {
  for (const auto& source : testing::corpus()) {
    reference r(source);
    scratch s;
    ctape_writer w(&s.arena, {});
    w.input(r.input());
    transcode(r, w, 0, 0, false);
    td_tape_view v = w.view();
    std::string expected;
    uint64_t i = 0;
    trace_myna(r, i, expected, false);
    trace_visitor t{v, {}, {}};
    c::walk(v, t);
    require(t.out == expected);
    // The text arena over words writes the same bytes and entries as the Myna overload.
    size_t capacity = text_capacity_bound(source.size());
    std::vector<uint8_t> out_a(capacity + 2), out_b(capacity + 2);
    std::vector<uint64_t> table_a(r.view.base.node_count), table_b(v.word_count);
    size_t used_a = write_text_arena(r.view.base, r.decoder.key_hashes().data(), table_a.data(), out_a.data(), capacity);
    size_t used_b = write_text_arena(v, table_b.data(), out_b.data(), capacity);
    require(used_a == used_b && used_a != text_too_small);
    require(std::memcmp(out_a.data(), out_b.data(), used_a) == 0);
    std::vector<uint64_t> entries_a, entries_b;
    for (uint64_t n = 0; n < r.view.base.node_count; ++n)
      if ((r.view.base.nodes[n].tag & MYNA_VALUE_KIND_MASK) == MYNA_VALUE_STRING) entries_a.push_back(table_a[n]);
    for (uint64_t n = 0; n < v.word_count; n = c::step(n, v.words[n])) {
      if (c::kind(v.words[n]) == c::kind_string) entries_b.push_back(table_b[n]);
      if (c::is_wide(v.words[n])) require(table_b[n + 1] == 0);
    }
    require(entries_a == entries_b);
  }
}

// One valid tape exercising every kind, corrupted one class at a time.
void validate_rejects_corruption() {
  const std::string input = R"({"a":1,"b":[true,"long string beyond sixteen"],"c":"\u00e9","d":1e400})";
  scratch s;
  ctape_writer w(&s.arena, {});
  w.input(input);
  size_t root = w.begin(c::kind_object, 0, 0);                         // 0
  w.member_key(root, hash("a"));
  w.string_ref(span_of(input, "a"), true, true);                       // 2
  w.integer(1, span_of(input, "1"));                                   // 3
  w.member_key(root, hash("b"));
  w.string_ref(span_of(input, "b"), true, true);                       // 5
  size_t array = w.begin(c::kind_array, 1, span_of(input, "[").offset);  // 6
  w.boolean(true);                                                     // 8
  w.string_ref(span_of(input, "long string beyond sixteen"), true, false);  // 9
  w.end(array, 2);
  w.member_key(root, hash("c"));
  w.string_ref({uint32_t(input.find("\"c\"") + 1), 1}, true, true);    // 10
  w.string("\xc3\xa9", span_of(input, R"("\u00e9")"), false, false);   // 11
  w.member_key(root, hash("d"));
  w.string_ref({uint32_t(input.find("\"d\"") + 1), 1}, true, true);    // 13
  w.decimal_ref(span_of(input, "1e400"));                              // 14
  w.end(root, 4);
  td_tape_view good = w.view();
  require(good.word_count == 15);
  require(c::validate(good) == nullptr);
  std::vector<uint64_t> directory(good.directory, good.directory + good.directory_count);
  int classes = 0;
  auto corrupt = [&](auto change) {
    std::vector<uint64_t> words(good.words, good.words + good.word_count);
    std::vector<uint64_t> dir = directory;
    td_tape_view v = good;
    change(words, dir, v);
    v.words = words.data();
    v.directory = dir.data();
    const char* error = c::validate(v);
    if (!error) throw std::runtime_error("corruption class " + std::to_string(classes) + " accepted");
    ++classes;
  };
  using W = std::vector<uint64_t>;
  corrupt([](W& x, W&, td_tape_view&) { x[3] &= ~c::wide_flag; });               // a flipped WIDE bit
  corrupt([](W& x, W&, td_tape_view&) { x[8] |= c::wide_flag; });                // WIDE on a one-word kind
  corrupt([](W& x, W&, td_tape_view&) { x[11] &= ~c::wide_flag; });              // OWNED without WIDE
  corrupt([](W& x, W&, td_tape_view&) { x[8] = uint64_t(0xF) << 60; });          // reserved kind
  corrupt([](W& x, W&, td_tape_view&) { x[8] = uint64_t(11) << 60; });           // reserved kind
  corrupt([](W& x, W&, td_tape_view&) { x[0] = c::make(c::kind_object, c::wide_flag, 4, 16); });  // next past the end
  corrupt([](W& x, W&, td_tape_view&) { x[6] = c::make(c::kind_array, c::wide_flag, 2, 9); });    // next inside a child
  corrupt([](W& x, W&, td_tape_view&) { x[6] = c::make(c::kind_array, c::wide_flag, 2, 13); });   // next past its parent's child
  corrupt([&](W& x, W&, td_tape_view&) { x[9] = c::make(c::kind_string, c::ascii_flag, 26, uint32_t(input.size()) - 3); });  // span past the input
  corrupt([&](W& x, W&, td_tape_view&) { x[3] = c::make(c::kind_i64, c::wide_flag, 2, uint32_t(input.size()) - 1); });     // number span past the input
  corrupt([](W& x, W&, td_tape_view&) { x[11] = c::make(c::kind_string, c::owned_flag | c::wide_flag, 3, 0); });  // pool span past the pool
  corrupt([&](W& x, W&, td_tape_view&) { x[12] = uint64_t(input.size()) << 32 | 4; });  // raw span past the input
  corrupt([](W&, W& d, td_tape_view&) { std::swap(d[0], d[1]); });                // an unsorted directory
  corrupt([](W&, W& d, td_tape_view&) { d[0] = c::entry(c::entry_hash(d[0]), 3); });  // entry naming a value
  corrupt([](W&, W& d, td_tape_view&) { d[0] = c::entry(c::entry_hash(d[0]) ^ 1, c::entry_offset(d[0])); });  // wrong hash
  corrupt([](W& x, W&, td_tape_view&) { x[1] = uint64_t(3) << 32; });            // directory run past the array
  corrupt([](W& x, W&, td_tape_view&) { x[1] = 0; });                            // object without a directory
  corrupt([](W& x, W&, td_tape_view&) { x[7] |= uint64_t(1) << 32; });           // directory on an array
  corrupt([](W& x, W&, td_tape_view&) { x[0] = c::make(c::kind_object, c::wide_flag, 3, 15); });  // count mismatch
  corrupt([](W& x, W&, td_tape_view&) { x[9] |= c::key_flag; });                 // KEY on a value
  corrupt([](W& x, W&, td_tape_view&) { x[2] &= ~c::key_flag; });                // a key without KEY
  corrupt([](W& x, W&, td_tape_view&) { x[2] = c::make(c::kind_null, 0, 0, 0); });  // a non-string key
  corrupt([](W& x, W&, td_tape_view&) { x[9] = c::make(c::kind_ref, 0, 0, 6); });   // REF to an open ancestor
  corrupt([](W& x, W&, td_tape_view&) { x[8] = c::make(c::kind_ref, 0, 0, 9); });   // REF forward
  corrupt([](W& x, W&, td_tape_view&) { x[8] = c::make(c::kind_ref, 0, 0, 4); });   // REF to a second word
  corrupt([](W& x, W&, td_tape_view&) { x[8] = c::make(c::kind_true, 0, 1, 0); });  // payload on a constant
  corrupt([](W& x, W&, td_tape_view&) { x[9] |= c::owned_flag | c::wide_flag; });  // OWNED text without its raw word
  corrupt([](W& x, W&, td_tape_view&) {                                          // ASCII claimed for non-ASCII text
    x[11] = c::make(c::kind_string, c::owned_flag | c::wide_flag | c::ascii_flag, 2, 0);
  });
  corrupt([](W& x, W&, td_tape_view&) {                                          // non-finite F64 without the flag
    double inf = std::numeric_limits<double>::infinity();
    x[3] = (x[3] & ~(uint64_t(0xF) << 60)) | uint64_t(c::kind_f64) << 60;
    std::memcpy(&x[4], &inf, 8);
  });
  corrupt([](W&, W&, td_tape_view& v) { v.word_count = 14; });                   // truncated tape
  corrupt([](W&, W&, td_tape_view& v) { v.version = 2; });
  corrupt([](W&, W&, td_tape_view& v) { v.flags = 4; });
  corrupt([](W&, W&, td_tape_view& v) { v.flags = TD_TAPE_HAS_META; });           // meta flag, no table
  // Two equal keys under equal hashes (a duplicate the writer would have refused).
  corrupt([&](W& x, W& d, td_tape_view&) {
    x[13] = x[2];
    for (auto& e : d)
      if (c::entry_offset(e) == 13) e = c::entry(hash("a"), 13);
    std::sort(d.begin(), d.end(), [](uint64_t a, uint64_t b) {
      return c::entry_hash(a) < c::entry_hash(b) || (c::entry_hash(a) == c::entry_hash(b) && c::entry_offset(a) < c::entry_offset(b));
    });
  });
  // ASCII text without the ASCII flag is fine (a conservative claim).
  {
    std::vector<uint64_t> words(good.words, good.words + good.word_count);
    words[9] &= ~c::ascii_flag;
    td_tape_view v = good;
    v.words = words.data();
    require(c::validate(v) == nullptr);
  }
  require(classes == 34);
  // Words after the root.
  {
    scratch t;
    ctape_writer two(&t.arena, {});
    two.null();
    two.null();
    require(c::validate(two.view()) != nullptr);
  }
}

void allocation_failure_at_every_growth() {
  std::string input;
  for (int i = 0; i < 300; ++i) input += "\"key-" + std::to_string(i) + "-padded-beyond-the-inline-limit\"";
  auto write = [&](std::pmr::memory_resource* memory) {
    block_arena arena(memory);
    ctape_writer w(&arena, {});
    w.input(input);
    size_t root = w.begin(c::kind_array, 0);
    for (int o = 0; o < 4; ++o) {
      size_t object = w.begin(c::kind_object, 1);
      for (int i = 0; i < 300; ++i) {
        std::string key = "key-" + std::to_string(i) + "-padded-beyond-the-inline-limit";
        w.member_key(object, hash(key));
        if (i % 3 == 0) w.string(key, span_of(input, "\"" + key + "\""), true, true);
        else w.string_ref(span_of(input, key), true, true);
        if (i % 2) w.floating(i + 0.5, {});
        else w.string("owned value text, long enough to grow the pool " + std::to_string(i), {}, true, false);
      }
      w.end(object, 300);
    }
    w.end(root, 4);
    td_tape_view v = w.view();
    require(c::validate(v) == nullptr && v.directory_count == 1200);
    myna_value_view_v4 m = c::to_myna(v, &arena);
    require(m.base.node_count == 1 + 4 * 601 && m.directory_count == 1200);
  };
  for (size_t fail = 0;; ++fail) {
    fault_memory upstream(fail);
    bool failed = false;
    try {
      index_memory memory(SIZE_MAX, &upstream);
      write(&memory);
    } catch (const std::bad_alloc&) {
      failed = true;
    }
    require(upstream.live == 0);
    if (!failed) {
      require(fail > 4);
      std::printf("allocation_failure_at_every_growth: %zu upstream allocations, each failed once\n", fail);
      return;
    }
  }
}
}  // namespace

int main() {
  struct test { const char* name; void (*run)(); };
  const test tests[] = {
      {"words_follow_the_layout", words_follow_the_layout},
      {"duplicates_detected_in_sorted_directory", duplicates_detected_in_sorted_directory},
      {"singleton_objects_have_no_directory", singleton_objects_have_no_directory},
      {"counts_saturate_and_validate", counts_saturate_and_validate},
      {"erase_append_truncate_renumber", erase_append_truncate_renumber},
      {"to_myna_matches_tape_writer", to_myna_matches_tape_writer},
      {"walk_matches_reference", walk_matches_reference},
      {"validate_rejects_corruption", validate_rejects_corruption},
      {"allocation_failure_at_every_growth", allocation_failure_at_every_growth},
  };
  int failures = 0;
  for (const auto& t : tests) {
    try {
      t.run();
      std::printf("ok   ctape.%s\n", t.name);
    } catch (const std::exception& e) {
      std::fprintf(stderr, "FAIL ctape.%s: %s\n", t.name, e.what());
      ++failures;
    }
  }
  return failures ? 1 : 0;
}
