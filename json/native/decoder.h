#ifndef TRUFFLEDATA_JSON_DECODER_H
#define TRUFFLEDATA_JSON_DECODER_H
#include "json/native/arena.h"
#include "json/native/index_memory.h"
#include "json/native/parser_pool.h"
#include "json/native/tape_writer.h"
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


// Read-only view of a side table published with a document.
template<class T> class table_view {
  const T* data_;
  size_t size_;
public:
  table_view(const T* data, size_t size) : data_(data), size_(size) {}
  const T* data() const { return data_; }
  size_t size() const { return size_; }
  const T& operator[](size_t index) const { return data_[index]; }
  const T& at(size_t index) const {
    if (index >= size_) throw std::out_of_range("JSON side-table index");
    return data_[index];
  }
};

// Storage presizing from the input length. Typical JSON has one node per 5-8 input bytes; the
// worst case (`[1,1,...]`) has one per 2, which grows the arrays once or twice. Hints never exceed
// the Myna node limit or a fixed share of the index budget, and depend only on the configuration,
// so a document's allocation sequence (and its exact index peak) is reproducible.
struct decode_hint {
  size_t nodes;
  size_t bytes;
  size_t block;  // First arena block: the presized arrays that are not large allocations.
};
inline decode_hint hint_for(size_t input, const myna::value_limits& limits, const storage_limits& storage) {
  size_t cap = storage.index_bytes / 72 > 4096 ? storage.index_bytes / 72 : 4096;
  size_t nodes = input / 4 + 8;
  if (nodes > cap) nodes = cap;
  if (nodes > limits.nodes) nodes = static_cast<size_t>(limits.nodes);
  size_t bytes = input / 8 + 64;
  if (bytes > cap * 8) bytes = cap * 8;
  size_t block = 4 * sizeof(myna_value_region) + 256;
  for (size_t part : {nodes * sizeof(myna_value_node), nodes * sizeof(uint64_t), nodes * sizeof(uint32_t), bytes})
    if (part <= block_arena::large_threshold) block += part + alignof(std::max_align_t);
  return {nodes, bytes, block};
}

// Lean On-Demand visitor writing nodes, lexical spans and the key-hash side table into arena
// storage. Shared by documents and sessions; one instance decodes one document.
template<class Writer> class tape_decoder final {
  std::pmr::memory_resource* memory_;
  Writer writer_;
  arena_vec<uint64_t> lexical_;
  arena_vec<uint32_t> hashes_;
  const char* base_ = nullptr;
  uint32_t region_ = 0;
  myna::value_limits limits_;

  // Packed span into the retained input region: offset << 32 | length; zero when absent.
  void lexical(std::string_view raw = {}, uint32_t meta = 0) {
    if (lexical_.size() >= limits_.nodes) throw std::length_error("JSON node limit");
    lexical_.push(memory_, raw.empty() ? 0 : (uint64_t(raw.data() - base_) << 32) | raw.size());
    hashes_.push(memory_, meta);
  }
  // `raw` is the trimmed token including quotes; `decoded` either is the body inside `raw`
  // (clean, referenced) or decoded scratch text (copied).
  void text(std::string_view decoded, std::string_view raw, bool referenced, uint32_t meta) {
    lexical(raw, meta);
    if (decoded.size() <= MYNA_VALUE_INLINE_BYTES) writer_.string(decoded);
    else if (referenced)
      writer_.string_ref(region_, static_cast<uint32_t>(decoded.data() - base_),
                         static_cast<uint32_t>(decoded.size()));
    else writer_.string(decoded);
  }
  template<class Value> void visit(Value& value, uint32_t depth) {
    if (depth > limits_.depth) throw std::length_error("JSON depth limit");
    using simdjson::ondemand::json_type;
    switch (value.type().value()) {
      case json_type::unknown:
        throw std::invalid_argument("unknown JSON token");
      case json_type::object: {
        lexical();
        size_t index = writer_.begin(MYNA_VALUE_OBJECT, depth);
        uint64_t members = 0;
        key_table keys(memory_);
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
          ++members;
        }
        writer_.end(index, members);
        break;
      }
      case json_type::array: {
        lexical();
        size_t index = writer_.begin(MYNA_VALUE_ARRAY, depth);
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
        if (!parsed.error()) {
          using simdjson::ondemand::number_type;
          auto n = parsed.value();
          switch (n.get_number_type()) {
            case number_type::signed_integer:
              lexical(raw);
              // simdjson folds -0 into integer zero; JSON.parse-style consumers need negative zero.
              if (n.get_int64() == 0 && raw[0] == '-') writer_.floating(-0.0);
              else writer_.integer(n.get_int64());
              return;
            case number_type::unsigned_integer:
              lexical(raw);
              writer_.unsigned_integer(n.get_uint64());
              return;
            case number_type::floating_point_number:
              if (std::isfinite(n.get_double())) {
                lexical(raw);
                writer_.floating(n.get_double());
                return;
              }
              break;
            default: break;
          }
        }
        if (!number(raw)) throw std::invalid_argument("invalid JSON number");
        lexical(raw);
        writer_.number_ref(region_, static_cast<uint32_t>(raw.data() - base_),
                           static_cast<uint32_t>(raw.size()));
        break;
      }
      case json_type::boolean:
        lexical();
        writer_.boolean(value.get_bool().value());
        break;
      case json_type::null:
        lexical();
        if (!value.is_null().value()) throw std::invalid_argument("invalid JSON null");
        writer_.null();
        break;
    }
  }

public:
  tape_decoder(std::pmr::memory_resource* memory, myna::value_limits limits)
      : memory_(memory), writer_(memory, limits), limits_(limits) {}
  tape_decoder(const tape_decoder&) = delete;
  tape_decoder& operator=(const tape_decoder&) = delete;

  // Presizes storage and registers `input` (the retained, padded input) as region 1.
  void start(std::string_view input, const decode_hint& hint) {
    writer_.reserve(hint.nodes, hint.bytes);
    lexical_.reserve(memory_, hint.nodes);
    hashes_.reserve(memory_, hint.nodes);
    base_ = input.data();
    region_ = writer_.region(input);
  }
  // Walks one document; trailing input, a second root or any invalid value throws.
  void decode(simdjson::ondemand::document& root) {
    visit(root, 0);
    // raw_json consumes scalar roots; at_end rejects a second root/trailing tokens.
    if (root.is_scalar().value()) (void)root.raw_json().value();
    if (!root.at_end()) throw std::invalid_argument("trailing JSON input");
  }
  myna_value_view view() const { return writer_.view(); }
  table_view<uint64_t> lexical_spans() const { return {lexical_.data(), lexical_.size()}; }
  table_view<uint32_t> key_hashes() const { return {hashes_.data(), hashes_.size()}; }
};

// The input is retained; the parser is pooled and released before construction completes.
// Unescaped text references the retained input; escaped text is copied into message-owned
// storage, so parser scratch never outlives the lease. Nodes, owned text, lexical spans and key
// hashes live in one block arena charged to index_memory. One instance is immutable after
// construction and must not be reused to parse.
template<class Writer> class basic_document final {
  simdjson::padded_string owned_;
  // Either owned_ or caller memory adopted with sufficient padding; never copied twice.
  simdjson::padded_string_view input_;
  index_memory memory_;
  block_arena arena_;
  tape_decoder<Writer> decoder_;
  myna_value_view view_{};
  size_t parser_capacity_ = 0;

  struct adopted_tag {};
  basic_document(adopted_tag, simdjson::padded_string owned, simdjson::padded_string_view input,
                 myna::value_limits limits, storage_limits storage, std::pmr::memory_resource* upstream)
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
public:
  explicit basic_document(std::string_view input, myna::value_limits limits = {},
                          storage_limits storage = {},
                          std::pmr::memory_resource* upstream = std::pmr::get_default_resource())
      : basic_document(adopted_tag{}, simdjson::padded_string(checked(input, storage)),
                       simdjson::padded_string_view(), limits, storage, upstream) {}
  // Adopt caller memory when it carries SIMDJSON_PADDING readable bytes past `length`;
  // otherwise copy once. The caller keeps adopted memory valid for the document's life.
  basic_document(const char* data, size_t length, size_t capacity, myna::value_limits limits = {},
                 storage_limits storage = {},
                 std::pmr::memory_resource* upstream = std::pmr::get_default_resource())
      : basic_document(adopted_tag{},
                       capacity >= length + simdjson::SIMDJSON_PADDING
                           ? simdjson::padded_string()
                           : simdjson::padded_string(checked(std::string_view(data, length), storage)),
                       capacity >= length + simdjson::SIMDJSON_PADDING
                           ? simdjson::padded_string_view(data, checked(std::string_view(data, length), storage).size(), capacity)
                           : simdjson::padded_string_view(),
                       limits, storage, upstream) {}
  bool adopted() const { return !owned_.data(); }
  basic_document(const basic_document&) = delete;
  basic_document& operator=(const basic_document&) = delete;
  myna_value_view view() const { return view_; }
  table_view<uint64_t> lexical_spans() const { return decoder_.lexical_spans(); }
  // One entry per node; see key_hash and ascii_flag.
  table_view<uint32_t> key_hashes() const { return decoder_.key_hashes(); }
  // Index accounting counts arena blocks and large allocations, not individual nodes.
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
// Production documents write through the presized tape writer; builder_document runs the same
// visitor through myna::value_builder and exists to prove byte-for-byte parity in tests.
using document = basic_document<tape_writer>;
using builder_document = basic_document<builder_writer>;
} // namespace truffledata::json
#endif
