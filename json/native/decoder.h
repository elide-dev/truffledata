#ifndef TRUFFLEDATA_JSON_DECODER_H
#define TRUFFLEDATA_JSON_DECODER_H
#include "json/native/index_memory.h"
#include "json/native/parser_pool.h"
#include <myna/values.hpp>
#include <simdjson.h>
#include <cmath>
#include <cstring>

namespace truffledata::json {

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

// Side-table entry per node: bits 0-30 hold the key hash (key nodes only), bit 31 marks string and
// key nodes whose decoded bytes are all ASCII. The hash is 32-bit FNV-1a over the decoded key
// bytes, masked to 31 bits; Java's JsonKey computes the same function.
inline constexpr uint32_t hash_mask = 0x7fffffffu;
inline constexpr uint32_t ascii_flag = 0x80000000u;
inline uint32_t key_hash(std::string_view s) {
  uint32_t h = 2166136261u;
  for (unsigned char c : s) { h ^= c; h *= 16777619u; }
  return h & hash_mask;
}
inline bool ascii(std::string_view s) {
  unsigned char any = 0;
  for (unsigned char c : s) any |= c;
  return any < 0x80;
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

// Per-object duplicate-key table: a linear (hash, length, bytes) compare for up to 16 keys on the
// stack, then open addressing in counted index memory. Keys are compared after escape decoding.
class key_table final {
  struct entry { uint32_t hash; uint32_t length; const char* data; };
  static constexpr size_t inline_keys = 16;
  entry small_[inline_keys];
  size_t count_ = 0;
  entry* table_ = nullptr;
  size_t capacity_ = 0, used_ = 0;
  std::pmr::memory_resource* memory_;
  static bool same(const entry& e, uint32_t hash, std::string_view key) {
    return e.hash == hash && e.length == key.size()
        && (key.empty() || std::memcmp(e.data, key.data(), key.size()) == 0);
  }
  // Empty slots have a null data pointer; empty keys carry a static non-null pointer.
  static void put(entry* table, size_t mask, const entry& e) {
    for (size_t i = e.hash & mask;; i = (i + 1) & mask)
      if (!table[i].data) { table[i] = e; return; }
  }
  void grow() {
    size_t capacity = capacity_ ? capacity_ * 2 : 64;
    auto* table = static_cast<entry*>(memory_->allocate(capacity * sizeof(entry), alignof(entry)));
    for (size_t i = 0; i < capacity; ++i) table[i] = {0, 0, nullptr};
    if (table_) {
      for (size_t i = 0; i < capacity_; ++i) if (table_[i].data) put(table, capacity - 1, table_[i]);
      memory_->deallocate(table_, capacity_ * sizeof(entry), alignof(entry));
    } else {
      for (size_t i = 0; i < count_; ++i) put(table, capacity - 1, small_[i]);
    }
    table_ = table;
    capacity_ = capacity;
  }
public:
  explicit key_table(std::pmr::memory_resource* memory) : memory_(memory) {}
  key_table(const key_table&) = delete;
  key_table& operator=(const key_table&) = delete;
  ~key_table() { if (table_) memory_->deallocate(table_, capacity_ * sizeof(entry), alignof(entry)); }
  // False when an equal decoded key is already present.
  bool insert(uint32_t hash, std::string_view key) {
    static const char empty = 0;
    entry e{hash, static_cast<uint32_t>(key.size()), key.empty() ? &empty : key.data()};
    if (!table_) {
      for (size_t i = 0; i < count_; ++i) if (same(small_[i], hash, key)) return false;
      if (count_ < inline_keys) { small_[count_++] = e; return true; }
      grow();
      used_ = count_;
    }
    size_t mask = capacity_ - 1;
    for (size_t i = hash & mask; table_[i].data; i = (i + 1) & mask)
      if (same(table_[i], hash, key)) return false;
    if ((used_ + 1) * 2 > capacity_) { grow(); mask = capacity_ - 1; }
    put(table_, mask, e);
    ++used_;
    return true;
  }
};

// The input is retained; the parser is pooled and released before construction
// completes. Unescaped text references the retained input; escaped text is
// copied into message-owned storage, so parser scratch never outlives the lease.
// One instance is immutable after construction and must not be reused to parse.
class document final {
  simdjson::padded_string owned_;
  // Either owned_ or caller memory adopted with sufficient padding; never copied twice.
  simdjson::padded_string_view input_;
  index_memory memory_;
  myna::value_builder builder_;
  uint32_t input_region_;
  uint64_t count_ = 0;
  myna::value_limits limits_;
  std::pmr::vector<uint64_t> lexical_;
  std::pmr::vector<uint32_t> hashes_;
  size_t parser_capacity_ = 0;

  // Packed span into the retained input region: offset << 32 | length; zero when absent.
  void lexical(std::string_view raw = {}, uint32_t meta = 0) {
    if (count_ >= limits_.nodes) throw std::length_error("JSON node limit");
    ++count_;
    if (raw.empty()) lexical_.push_back(0);
    else lexical_.push_back((uint64_t(raw.data() - input_.data()) << 32) | raw.size());
    hashes_.push_back(meta);
  }
  // `raw` is the trimmed token including quotes; `decoded` either is the body inside `raw`
  // (clean, referenced) or decoded scratch text (copied).
  void text(std::string_view decoded, std::string_view raw, bool referenced, uint32_t meta) {
    lexical(raw, meta);
    if (decoded.size() <= 16) builder_.string(decoded);
    else if (referenced)
      builder_.string_ref(input_region_,
          static_cast<uint32_t>(decoded.data() - input_.data()),
          static_cast<uint32_t>(decoded.size()));
    else builder_.string(decoded);
  }
  template<class Value> void visit(Value& value, uint32_t depth) {
    if (depth > limits_.depth) throw std::length_error("JSON depth limit");
    using simdjson::ondemand::json_type;
    switch (value.type().value()) {
      case json_type::unknown:
        throw std::invalid_argument("unknown JSON token");
      case json_type::object: {
        lexical();
        builder_.begin_object();
        key_table keys(&memory_);
        for (auto entry : value.get_object()) {
          std::string_view raw = json::trim(entry.key_raw_json_token().value());
          std::string_view body = raw.substr(1, raw.size() - 2);
          body_scan s = scan(body);
          std::string_view key = s.clean ? body : entry.unescaped_key().value();
          bool plain = s.clean ? s.ascii : ascii(key);
          uint32_t hash = key_hash(key);
          if (!keys.insert(hash, key)) throw std::invalid_argument("duplicate JSON key");
          text(key, raw, s.clean, hash | (plain ? ascii_flag : 0));
          auto child = entry.value().value();
          visit(child, depth + 1);
        }
        builder_.end();
        break;
      }
      case json_type::array:
        lexical();
        builder_.begin_array();
        for (auto entry : value.get_array()) {
          auto child = entry.value();
          visit(child, depth + 1);
        }
        builder_.end();
        break;
      case json_type::string: {
        std::string_view raw = json::trim(value.raw_json_token());
        std::string_view body = raw.substr(1, raw.size() - 2);
        body_scan s = scan(body);
        if (s.clean) {
          text(body, raw, true, s.ascii ? ascii_flag : 0);
        } else {
          std::string_view decoded = value.get_string().value();
          text(decoded, raw, false, ascii(decoded) ? ascii_flag : 0);
        }
        break;
      }
      case json_type::number: {
        std::string_view raw = json::trim(value.raw_json_token());
        // get_number() succeeding is simdjson's full grammar validation; the RFC 8259 check runs
        // only for tokens it does not type (overflowing doubles, big integers) and for errors.
        auto parsed = value.get_number();
        bool typed = false;
        if (!parsed.error()) {
          using simdjson::ondemand::number_type;
          auto n = parsed.value();
          switch (n.get_number_type()) {
            case number_type::signed_integer:
              lexical(raw);
              // simdjson folds -0 into integer zero; JSON.parse-style consumers need negative zero.
              if (n.get_int64() == 0 && raw[0] == '-') builder_.floating(-0.0);
              else builder_.integer(n.get_int64());
              typed = true; break;
            case number_type::unsigned_integer:
              lexical(raw);
              builder_.unsigned_integer(n.get_uint64()); typed = true; break;
            case number_type::floating_point_number:
              if (std::isfinite(n.get_double())) {
                lexical(raw);
                builder_.floating(n.get_double());
                typed = true;
              }
              break;
            default: break;
          }
        }
        if (!typed) {
          if (!number(raw)) throw std::invalid_argument("invalid JSON number");
          lexical(raw);
          builder_.validated_number_ref(input_region_,
              static_cast<uint32_t>(raw.data() - input_.data()),
              static_cast<uint32_t>(raw.size()));
        }
        break;
      }
      case json_type::boolean:
        lexical();
        builder_.boolean(value.get_bool().value());
        break;
      case json_type::null:
        lexical();
        if (!value.is_null().value()) throw std::invalid_argument("invalid JSON null");
        builder_.null();
        break;
    }
  }
  struct adopted_tag {};
  document(adopted_tag, simdjson::padded_string owned, simdjson::padded_string_view input,
           myna::value_limits limits, storage_limits storage, std::pmr::memory_resource* upstream)
      : owned_(std::move(owned)),
        input_(owned_.data() ? simdjson::padded_string_view(owned_.data(), owned_.size(), owned_.size() + simdjson::SIMDJSON_PADDING) : input),
        memory_(storage.index_bytes, upstream),
        builder_(&memory_, limits),
        input_region_(builder_.region(std::string_view(input_.data(), input_.size()))),
        limits_(limits), lexical_(&memory_), hashes_(&memory_) {
    parse();
  }
  void parse() {
    if (!input_.data()) throw std::bad_alloc();
    parser_lease lease = parser_pool::acquire();
    auto& parser = lease.get();
    auto root = parser.iterate(input_).value();
    visit(root, 0);
    // raw_json consumes scalar roots; at_end rejects a second root/trailing tokens.
    if (root.is_scalar().value()) (void)root.raw_json().value();
    if (!root.at_end()) throw std::invalid_argument("trailing JSON input");
    parser_capacity_ = parser.capacity();
    (void)builder_.view();
  }
public:
  explicit document(std::string_view input, myna::value_limits limits = {},
                    storage_limits storage = {},
                    std::pmr::memory_resource* upstream = std::pmr::get_default_resource())
      : document(adopted_tag{}, simdjson::padded_string(checked(input, storage)),
                 simdjson::padded_string_view(), limits, storage, upstream) {}
  // Adopt caller memory when it carries SIMDJSON_PADDING readable bytes past `length`;
  // otherwise copy once. The caller keeps adopted memory valid for the document's life.
  document(const char* data, size_t length, size_t capacity, myna::value_limits limits = {},
           storage_limits storage = {},
           std::pmr::memory_resource* upstream = std::pmr::get_default_resource())
      : document(adopted_tag{},
                 capacity >= length + simdjson::SIMDJSON_PADDING
                     ? simdjson::padded_string()
                     : simdjson::padded_string(checked(std::string_view(data, length), storage)),
                 capacity >= length + simdjson::SIMDJSON_PADDING
                     ? simdjson::padded_string_view(data, checked(std::string_view(data, length), storage).size(), capacity)
                     : simdjson::padded_string_view(),
                 limits, storage, upstream) {}
  bool adopted() const { return !owned_.data(); }
  document(const document&) = delete;
  document& operator=(const document&) = delete;
  myna_value_view view() const { return builder_.view(); }
  const std::pmr::vector<uint64_t>& lexical_spans() const { return lexical_; }
  // One entry per node; see key_hash and ascii_flag.
  const std::pmr::vector<uint32_t>& key_hashes() const { return hashes_; }
  allocation_stats allocations() const {
    return {adopted() ? 0 : input_.size(),
            adopted() ? input_.capacity() : input_.size() + simdjson::SIMDJSON_PADDING,
            memory_.current(), memory_.peak(), memory_.allocations(), parser_capacity_};
  }
private:
  static std::string_view checked(std::string_view input, storage_limits storage) {
    if (input.size() > UINT32_MAX || input.size() > storage.input_bytes) throw std::length_error("JSON input limit");
    return input;
  }
};
} // namespace truffledata::json
#endif
