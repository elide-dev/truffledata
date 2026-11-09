#ifndef TRUFFLEDATA_JSON_BASELINE_PROBE_H
#define TRUFFLEDATA_JSON_BASELINE_PROBE_H
// The JSON decoder (tape-gbps plan §2.2, Task 2): one lean simdjson On-Demand walk writing the
// compact tape (tape/native/ctape.h) with embedded lexical spans, ASCII/KEY/OWNED flags and
// per-object directories whose sort detects duplicate keys, or (validate-only) the same walk and
// checks with nothing retained.
#include "json/native/parser_pool.h"
#include "tape/native/arena.h"
#include "tape/native/ctape.h"
#include "tape/native/index_memory.h"
#include <myna/values.hpp>
#include <simdjson.h>
#include <algorithm>
#include <cmath>
#include <cstring>
#include <type_traits>

namespace truffledata::json_baseline {
using json::parser_pool;
using json::parser_lease;

inline std::string_view trim(std::string_view s) {
  while (!s.empty() && (s.back() == ' ' || s.back() == '\n' ||
                        s.back() == '\r' || s.back() == '\t')) s.remove_suffix(1);
  return s;
}

// RFC 8259 grammar, deliberately independent of machine numeric range.
inline bool number(std::string_view s) {
  size_t i = 0;
  auto digit = [&] { return i < s.size() && s[i] >= '0' && s[i] <= '9'; };
  if (i < s.size() && s[i] == '-') ++i;
  if (!digit()) return false;
  if (s[i] == '0') ++i;
  else while (digit()) ++i;
  if (i < s.size() && s[i] == '.') {
    ++i;
    if (!digit()) return false;
    while (digit()) ++i;
  }
  if (i < s.size() && (s[i] == 'e' || s[i] == 'E')) {
    ++i;
    if (i < s.size() && (s[i] == '+' || s[i] == '-')) ++i;
    if (!digit()) return false;
    while (digit()) ++i;
  }
  return i == s.size();
}

// A string body is "clean" when it has no backslash and no raw byte below 0x20: it is then
// referenced without get_string(). Stage 1 validated UTF-8 for the whole buffer before any body is
// referenced, and stage 1 also rejects raw control bytes; bodies failing this scan still go through
// get_string()/unescaped_key() so simdjson validates their escapes.
struct body_scan { bool clean; bool ascii; };
inline body_scan scan(std::string_view body) {
  unsigned char any = 0;
  for (unsigned char c : body) {
    if (c == '\\' || c < 0x20) return {false, false};
    any |= c;
  }
  return {true, any < 0x80};
}
inline bool ascii(std::string_view text) {
  unsigned char any = 0;
  for (unsigned char c : text) any |= c;
  return any < 0x80;
}

// Storage presizing from the input length (§3.4): input / 3 + 8 words, input / 8 + 64 pool bytes
// and words / 2 + 1 directory entries. Typical JSON has one word per 3-6 input bytes; the worst
// case (`[1,1,...]`) has one per byte, which grows the words once or twice. Hints never exceed two
// words per allowed value or a fixed share of the index budget, and depend only on the
// configuration, so a document's allocation sequence (and its exact index peak) is reproducible.
struct decode_hint {
  size_t words;
  size_t pool;
  size_t entries;
  size_t block;  // First arena block: the presized arrays that are not large allocations.
};
inline decode_hint hint_for(size_t input, const myna::value_limits& limits, const tape::storage_limits& storage) {
  size_t cap = storage.index_bytes / 32 > 4096 ? storage.index_bytes / 32 : 4096;
  size_t words = input / 3 + 8;
  if (words > cap) words = cap;
  if (limits.nodes < words / 2) words = static_cast<size_t>(limits.nodes) * 2 + 2;
  size_t pool = input / 8 + 64;
  if (pool > cap * 8) pool = cap * 8;
  size_t entries = words / 2 + 1;
  size_t pending = entries < 256 ? entries : 256;
  size_t block = 256;
  for (size_t part : {words * sizeof(uint64_t), pool, entries * sizeof(uint64_t), pending * sizeof(uint64_t)})
    if (part <= tape::block_arena::large_threshold) block += part + alignof(std::max_align_t);
  return {words, pool, entries, block};
}

// The key hash of the validate-only walk, which builds no directory and so needs no FNV-1a: any
// function of the decoded bytes finds equal keys in one equal-hash run; this one mixes eight bytes
// per multiply instead of one. `readable` says the eight bytes at each position are readable (a
// clean key in the padded input), so the tail is one masked load.
inline uint32_t scratch_hash(const char* p, size_t n, bool readable) {
  uint64_t h = uint64_t(n) * 0x9E3779B97F4A7C15ull;
  for (; n >= 8; p += 8, n -= 8) {
    uint64_t w;
    std::memcpy(&w, p, 8);
    h = (h ^ w) * 0xBF58476D1CE4E5B9ull;
    h ^= h >> 29;
  }
  if (n) {
    uint64_t w = 0;
    if (readable) {
      std::memcpy(&w, p, 8);
      w &= ~uint64_t(0) >> (64 - 8 * n);
    } else {
      std::memcpy(&w, p, n);
    }
    h = (h ^ w) * 0xBF58476D1CE4E5B9ull;
  }
  return uint32_t(h >> 32) ^ uint32_t(h);
}

// The validate-only writer: no tape, the same limits as ctape_writer (values, decoded text bytes,
// depth, token lengths) and the same sorted-run duplicate check, over a scratch stack of the open
// objects' keys (hash << 32 | position, and the key text, which stays valid for the walk: clean
// keys are input spans, escaped ones live in the parser's string buffer). Its hash is
// scratch_hash, not the directory FNV-1a (see directory_hash).
class null_writer final {
  std::pmr::memory_resource* memory_;
  tape::arena_vec<uint64_t> pending_;
  tape::arena_vec<std::string_view> keys_;
  const char* input_ = nullptr;
  myna::value_limits limits_;
  uint64_t values_ = 0, text_bytes_ = 0;

  [[noreturn, gnu::noinline, gnu::cold]] static void limit(const char* what) { throw std::length_error(what); }
  [[gnu::always_inline]] void value() {
    if (values_ >= limits_.nodes) limit("tape value limit");
    ++values_;
  }
  [[gnu::always_inline]] void charge(size_t length) {
    if (length > tape::ctape::max_length) limit("tape text token limit");
    if (length > limits_.text_bytes - text_bytes_) limit("tape text limit");
    text_bytes_ += length;
  }
  [[gnu::always_inline]] void number(tape::ctape::span token) {
    if (token.length > tape::ctape::max_length) limit("tape text token limit");
    value();
  }
  void close(uint64_t count) {
    size_t first = pending_.size() - count;
    if (count >= 2) {
      uint64_t* e = pending_.data() + first;
      // Entry i names keys_[first + i]: hash << 32 | i sorts by (hash, position).
      for (size_t i = 0; i < count; ++i) e[i] |= i;
      if (count <= 24) {
        for (size_t i = 1; i < count; ++i) {
          uint64_t x = e[i];
          size_t j = i;
          while (j > 0 && e[j - 1] > x) { e[j] = e[j - 1]; --j; }
          e[j] = x;
        }
      } else {
        std::sort(e, e + count);
      }
      const std::string_view* k = keys_.data() + first;
      for (size_t i = 1; i < count; ++i) {
        if ((e[i] >> 32) != (e[i - 1] >> 32)) continue;
        std::string_view key = k[uint32_t(e[i])];
        for (size_t j = i; j > 0 && (e[j - 1] >> 32) == (e[i] >> 32); --j)
          if (k[uint32_t(e[j - 1])] == key) throw tape::ctape::duplicate_key();
      }
    }
    pending_.truncate(first);
    keys_.truncate(first);
  }
  [[gnu::always_inline]] void key_text(bool key, std::string_view text) {
    if (key) *keys_.extend(memory_, 1) = text;
  }

 public:
  null_writer(std::pmr::memory_resource* memory, myna::value_limits limits) : memory_(memory), limits_(limits) {}
  null_writer(const null_writer&) = delete;
  null_writer& operator=(const null_writer&) = delete;

  void input(std::string_view input) { input_ = input.data(); }
  void reserve(size_t, size_t, size_t entries) {
    size_t pending = entries < 256 ? entries : 256;
    pending_.reserve(memory_, pending);
    keys_.reserve(memory_, pending);
  }
  // The index is only the container kind: end() needs to know whether keys close with it.
  size_t begin(uint32_t kind, uint32_t depth, uint32_t = 0) {
    if (depth >= limits_.depth) limit("tape depth limit");
    value();
    return kind == tape::ctape::kind_object;
  }
  void end(size_t object, uint64_t count) {
    if (object) close(count);
  }
  // Keys of open objects stack in order; the innermost object's are on top when it closes.
  [[gnu::always_inline]] void member_key(size_t, uint32_t hash) { pending_.push(memory_, uint64_t(hash) << 32); }
  void null() { value(); }
  void boolean(bool) { value(); }
  void integer(int64_t, tape::ctape::span token) { number(token); }
  void unsigned_integer(uint64_t, tape::ctape::span token) { number(token); }
  void floating(double, tape::ctape::span token) { number(token); }
  void decimal_ref(tape::ctape::span token) {
    charge(token.length);
    value();
  }
  [[gnu::always_inline]] void string_ref(tape::ctape::span decoded, bool, bool key) {
    charge(decoded.length);
    value();
    key_text(key, {input_ + decoded.offset, decoded.length});
  }
  void string(std::string_view decoded, tape::ctape::span, bool, bool key) {
    charge(decoded.size());
    value();
    key_text(key, decoded);
  }
  uint64_t value_count() const { return values_; }
};

// Whether a writer's member_key needs myna_value_hash (it builds directories), or any hash.
template<class Writer> inline constexpr bool directory_hash = !std::is_same_v<Writer, null_writer>;

// The lean On-Demand visitor. Writer is tape::ctape_writer (a retained or borrowed document) or
// null_writer (validate-only). One instance decodes one document.
template<class Writer> class tape_decoder final {
  Writer writer_;
  const char* base_ = nullptr;
  myna::value_limits limits_;

  [[gnu::always_inline]] tape::ctape::span of(std::string_view token) const {
    return {static_cast<uint32_t>(token.data() - base_), static_cast<uint32_t>(token.size())};
  }
  template<class Value> void visit(Value& value, uint32_t depth) {
    if (depth > limits_.depth) throw std::length_error("JSON depth limit");
    using simdjson::ondemand::json_type;
    switch (value.type().value()) {
      case json_type::unknown:
        throw std::invalid_argument("unknown JSON token");
      case json_type::object: {
        std::string_view open = value.raw_json_token();
        size_t index = writer_.begin(tape::ctape::kind_object, depth, static_cast<uint32_t>(open.data() - base_));
        uint64_t members = 0;
        for (auto entry : value.get_object()) {
          std::string_view raw = json::trim(entry.key_raw_json_token().value());
          std::string_view body = raw.substr(1, raw.size() - 2);
          body_scan s = scan(body);
          std::string_view key = s.clean ? body : entry.unescaped_key().value();
          // Myna's directory hash (32-bit FNV-1a over the decoded bytes): the directory entry and
          // the duplicate check's equal-hash runs (validate-only: scratch_hash, no directory).
          if constexpr (directory_hash<Writer>)
            writer_.member_key(index, myna_value_hash(reinterpret_cast<const uint8_t*>(key.data()), key.size()));
          else
            writer_.member_key(index, scratch_hash(key.data(), key.size(), s.clean));
          if (s.clean) writer_.string_ref(of(body), s.ascii, true);
          else writer_.string(key, of(raw), json::ascii(key), true);
          auto child = entry.value().value();
          visit(child, depth + 1);
          ++members;
        }
        writer_.end(index, members);
        break;
      }
      case json_type::array: {
        std::string_view open = value.raw_json_token();
        size_t index = writer_.begin(tape::ctape::kind_array, depth, static_cast<uint32_t>(open.data() - base_));
        uint64_t elements = 0;
        for (auto entry : value.get_array()) {
          auto child = entry.value();
          visit(child, depth + 1);
          ++elements;
        }
        writer_.end(index, elements);
        break;
      }
      case json_type::string: {
        std::string_view raw = json::trim(value.raw_json_token());
        std::string_view body = raw.substr(1, raw.size() - 2);
        body_scan s = scan(body);
        if (s.clean) {
          writer_.string_ref(of(body), s.ascii, false);
        } else {
          std::string_view decoded = value.get_string().value();
          writer_.string(decoded, of(raw), json::ascii(decoded), false);
        }
        break;
      }
      case json_type::number: {
        std::string_view raw = json::trim(value.raw_json_token());
        // get_number() succeeding is simdjson's full grammar validation; the RFC 8259 check runs
        // only for tokens it does not type (overflowing doubles, big integers) and for errors.
        auto parsed = value.get_number();
        if (!parsed.error()) {
          using simdjson::ondemand::number_type;
          auto n = parsed.value();
          switch (n.get_number_type()) {
            case number_type::signed_integer:
              // simdjson folds -0 into integer zero; JSON.parse-style consumers need negative zero.
              if (n.get_int64() == 0 && raw[0] == '-') writer_.floating(-0.0, of(raw));
              else writer_.integer(n.get_int64(), of(raw));
              return;
            case number_type::unsigned_integer:
              writer_.unsigned_integer(n.get_uint64(), of(raw));
              return;
            case number_type::floating_point_number:
              if (std::isfinite(n.get_double())) {
                writer_.floating(n.get_double(), of(raw));
                return;
              }
              break;
            default: break;
          }
        }
        if (!number(raw)) throw std::invalid_argument("invalid JSON number");
        writer_.decimal_ref(of(raw));
        break;
      }
      case json_type::boolean:
        writer_.boolean(value.get_bool().value());
        break;
      case json_type::null:
        if (!value.is_null().value()) throw std::invalid_argument("invalid JSON null");
        writer_.null();
        break;
    }
  }

 public:
  tape_decoder(std::pmr::memory_resource* memory, myna::value_limits limits)
      : writer_(memory, limits), limits_(limits) {}
  tape_decoder(const tape_decoder&) = delete;
  tape_decoder& operator=(const tape_decoder&) = delete;

  // Presizes storage and registers `input` (the retained, padded input). `walked`, when given, is
  // where the parser reads the same bytes (a stream's carry-over buffer, stream.h): offsets of
  // referenced text and spans are taken relative to it, so they index `input`.
  void start(std::string_view input, const decode_hint& hint, const char* walked = nullptr) {
    writer_.input(input);
    writer_.reserve(hint.words, hint.pool, hint.entries);
    base_ = walked ? walked : input.data();
  }
  // Walks one document; trailing input, a second root or any invalid value throws.
  void decode(simdjson::ondemand::document& root) {
    visit(root, 0);
    // raw_json consumes scalar roots; at_end rejects a second root/trailing tokens.
    if (root.is_scalar().value()) (void)root.raw_json().value();
    if (!root.at_end()) throw std::invalid_argument("trailing JSON input");
  }
  // Walks one document of an iterate_many stream. What follows it is the next document (or the
  // stream's truncated tail), which the stream judges, so there is no trailing-input check.
  void decode(simdjson::ondemand::document_reference& root) {
    visit(root, 0);
    if (root.is_scalar().value()) (void)root.raw_json().value();
  }
  // The published descriptor (compact writer only).
  td_tape_view view() const { return writer_.view(); }
  const Writer& writer() const { return writer_; }
};

// A one-shot compact document (tests and benchmarks; td_json_decode moves to it in Task 3). The
// input is retained; the parser is pooled and released before construction completes. Escaped
// text is copied into the pool, so parser scratch never outlives the lease. Words, pool and
// directories live in one block arena charged to index_memory. Immutable after construction.
class document final {
  simdjson::padded_string owned_;
  // Either owned_ or caller memory adopted with sufficient padding; never copied twice.
  simdjson::padded_string_view input_;
  tape::index_memory memory_;
  tape::block_arena arena_;
  tape_decoder<tape::ctape_writer> decoder_;
  td_tape_view view_{};
  size_t parser_capacity_ = 0;

  struct adopted_tag {};
  document(adopted_tag, simdjson::padded_string owned, simdjson::padded_string_view input,
           myna::value_limits limits, tape::storage_limits storage, std::pmr::memory_resource* upstream)
      : owned_(std::move(owned)),
        input_(owned_.data() ? simdjson::padded_string_view(owned_.data(), owned_.size(), owned_.size() + simdjson::SIMDJSON_PADDING) : input),
        memory_(storage.index_bytes, upstream),
        arena_(&memory_),
        decoder_(&arena_, limits) {
    if (!input_.data()) throw std::bad_alloc();
    decode_hint hint = hint_for(input_.size(), limits, storage);
    arena_.hint(hint.block);
    decoder_.start(std::string_view(input_.data(), input_.size()), hint);
    parser_lease lease = parser_pool::acquire();
    auto& parser = lease.get();
    auto root = parser.iterate(input_).value();
    decoder_.decode(root);
    parser_capacity_ = parser.capacity();
    view_ = decoder_.view();
  }
  static std::string_view checked(std::string_view input, tape::storage_limits storage) {
    if (input.size() > UINT32_MAX || input.size() > storage.input_bytes) throw std::length_error("JSON input limit");
    return input;
  }

 public:
  explicit document(std::string_view input, myna::value_limits limits = {}, tape::storage_limits storage = {},
                    std::pmr::memory_resource* upstream = std::pmr::get_default_resource())
      : document(adopted_tag{}, simdjson::padded_string(checked(input, storage)), simdjson::padded_string_view(),
                 limits, storage, upstream) {}
  // Adopt caller memory when it carries SIMDJSON_PADDING readable bytes past `length`; otherwise
  // copy once. The caller keeps adopted memory valid for the document's life.
  document(const char* data, size_t length, size_t capacity, myna::value_limits limits = {},
           tape::storage_limits storage = {}, std::pmr::memory_resource* upstream = std::pmr::get_default_resource())
      : document(adopted_tag{},
                 capacity >= length + simdjson::SIMDJSON_PADDING
                     ? simdjson::padded_string()
                     : simdjson::padded_string(checked(std::string_view(data, length), storage)),
                 capacity >= length + simdjson::SIMDJSON_PADDING
                     ? simdjson::padded_string_view(data, checked(std::string_view(data, length), storage).size(), capacity)
                     : simdjson::padded_string_view(),
                 limits, storage, upstream) {}
  document(const document&) = delete;
  document& operator=(const document&) = delete;
  bool adopted() const { return !owned_.data(); }
  const td_tape_view& view() const { return view_; }
  uint64_t value_count() const { return decoder_.writer().value_count(); }
  // Index accounting counts arena blocks and large allocations, not individual words.
  tape::allocation_stats allocations() const {
    return {adopted() ? 0 : input_.size(),
            adopted() ? input_.capacity() : input_.size() + simdjson::SIMDJSON_PADDING,
            memory_.current(), memory_.peak(), memory_.allocations(), parser_capacity_};
  }
};

} // namespace truffledata::json
#endif
