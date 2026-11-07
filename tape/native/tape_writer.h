#ifndef TRUFFLEDATA_TAPE_TAPE_WRITER_H
#define TRUFFLEDATA_TAPE_TAPE_WRITER_H
#include "tape/native/arena.h"
#include <myna/values.hpp>
#include <cmath>
#include <cstring>
#include <stdexcept>
#include <string_view>

#if !defined(__BYTE_ORDER__) || __BYTE_ORDER__ != __ORDER_LITTLE_ENDIAN__
#error "tape_writer packs inline text with little-endian word stores"
#endif

namespace truffledata::tape {

// Presized Myna ABI v3 tape writer: writes myna_value_node records, owned text and the region
// table directly into arena storage. It produces the same nodes, bytes and regions as
// myna::value_builder for the same call sequence (pinned by //json/native:test) and enforces the
// same node, text, depth and container limits, without the builder's per-node try/catch, slot
// bookkeeping or geometric growth from an empty vector. The caller keeps object keys and values
// paired; the JSON and YAML decoders do so by construction. Until Myna exposes a reserving builder
// (elide-dev/myna#46, request M2) this writer is the substitute for both formats.
//
// Non-finite F64 nodes (YAML .inf/.nan) are written only through nonfinite(). Since Myna 0.10.0
// (elide-dev/myna#52) value ABI v3 allows them when descriptor flag bit 0,
// MYNA_VALUE_ALLOW_NON_FINITE, is set; view() sets it exactly when the tape holds one. floating()
// keeps refusing them, so JSON tapes stay finite-only with flags 0.
class tape_writer final {
  static constexpr size_t max_regions = 4;
  std::pmr::memory_resource* memory_;
  arena_vec<myna_value_node> nodes_;
  arena_vec<uint8_t> bytes_;
  myna_value_region* regions_ = nullptr;
  uint64_t region_count_ = 0;
  myna::value_limits limits_;
  uint64_t text_bytes_ = 0;
  bool nonfinite_ = false;

  myna_value_node* push(uint32_t tag, uint32_t length, uint64_t payload) {
    if (nodes_.size() >= limits_.nodes) throw std::length_error("Myna node limit");
    size_t index = nodes_.size();
    myna_value_node* node = nodes_.extend(memory_, 1);
    *node = {tag, length, payload, index + 1};
    return node;
  }
  void charge(size_t length) {
    if (length > UINT32_MAX || length > limits_.text_bytes - text_bytes_)
      throw std::length_error("Myna text limit");
    text_bytes_ += length;
  }
  void text(uint32_t kind, std::string_view value) {
    charge(value.size());
    if (value.size() <= MYNA_VALUE_INLINE_BYTES) {
      uint64_t words[2] = {0, 0};
      if (!value.empty()) std::memcpy(words, value.data(), value.size());
      myna_value_node* node = push(kind, static_cast<uint32_t>(value.size()), words[0]);
      node->next = words[1];
    } else {
      if (bytes_.size() > UINT32_MAX) throw std::length_error("Myna region offset limit");
      push(kind | MYNA_VALUE_REGION_TEXT, static_cast<uint32_t>(value.size()), bytes_.size());
      std::memcpy(bytes_.extend(memory_, value.size()), value.data(), value.size());
    }
  }
  void reference(uint32_t kind, uint32_t region, uint32_t offset, uint32_t length) {
    if (region == 0 || region > region_count_ || uint64_t(offset) + length > regions_[region - 1].length)
      throw std::out_of_range("Myna region extent");
    charge(length);
    push(kind | MYNA_VALUE_REGION_TEXT, length, (uint64_t(region) << 32) | offset);
  }

public:
  // Descriptor flag for tapes holding non-finite F64 nodes (Myna 0.10.0).
  static constexpr uint32_t nonfinite_flag = MYNA_VALUE_ALLOW_NON_FINITE;

  tape_writer(std::pmr::memory_resource* memory, myna::value_limits limits)
      : memory_(memory), limits_(limits) {
    if (!memory) throw std::invalid_argument("null Myna allocator");
  }
  tape_writer(const tape_writer&) = delete;
  tape_writer& operator=(const tape_writer&) = delete;

  // Presizes node and owned-text storage; later growth moves to a larger arena span.
  void reserve(size_t nodes, size_t bytes) {
    if (nodes > limits_.nodes) nodes = static_cast<size_t>(limits_.nodes);
    nodes_.reserve(memory_, nodes);
    bytes_.reserve(memory_, bytes);
  }
  uint32_t region(std::string_view value) {
    if (!regions_)
      regions_ = static_cast<myna_value_region*>(
          memory_->allocate(max_regions * sizeof(myna_value_region), alignof(myna_value_region)));
    if (region_count_ == max_regions) throw std::length_error("Myna region count");
    regions_[region_count_++] = {reinterpret_cast<const uint8_t*>(value.data()), value.size()};
    return static_cast<uint32_t>(region_count_);
  }
  // `depth` counts the containers already open around this one (0 for the root).
  size_t begin(uint32_t kind, uint32_t depth) {
    if (depth >= limits_.depth) throw std::length_error("Myna depth limit");
    size_t index = nodes_.size();
    push(kind, 0, 0);
    return index;
  }
  // `count` is elements for arrays and members for objects.
  void end(size_t index, uint64_t count) {
    auto& node = nodes_[index];
    if (count > (node.tag == MYNA_VALUE_OBJECT ? UINT32_MAX / 2 : UINT32_MAX))
      throw std::length_error("Myna container limit");
    node.length = static_cast<uint32_t>(count);
    node.next = nodes_.size();
  }
  void null() { push(MYNA_VALUE_NULL, 0, 0); }
  void boolean(bool value) { push(MYNA_VALUE_BOOL, 0, value); }
  void integer(int64_t value) { push(MYNA_VALUE_I64, 0, static_cast<uint64_t>(value)); }
  void unsigned_integer(uint64_t value) { push(MYNA_VALUE_U64, 0, value); }
  void floating(double value) {
    if (!std::isfinite(value)) throw std::invalid_argument("non-finite JSON number");
    uint64_t bits;
    std::memcpy(&bits, &value, sizeof(bits));
    push(MYNA_VALUE_F64, 0, bits);
  }
  // Raw IEEE bits of an infinity or NaN (see the class comment).
  void nonfinite(double value) {
    if (std::isfinite(value)) throw std::invalid_argument("finite value written as non-finite");
    uint64_t bits;
    std::memcpy(&bits, &value, sizeof(bits));
    push(MYNA_VALUE_F64, 0, bits);
    nonfinite_ = true;
  }
  bool has_nonfinite() const { return nonfinite_; }
  void string(std::string_view utf8) { text(MYNA_VALUE_STRING, utf8); }
  void string_ref(uint32_t region, uint32_t offset, uint32_t length) {
    reference(MYNA_VALUE_STRING, region, offset, length);
  }
  void number_ref(uint32_t region, uint32_t offset, uint32_t length) {
    reference(MYNA_VALUE_DECIMAL, region, offset, length);
  }
  // DECIMAL text owned by the tape (inline up to 16 bytes), for normalized number spellings.
  void decimal(std::string_view digits) { text(MYNA_VALUE_DECIMAL, digits); }
  size_t node_count() const { return nodes_.size(); }

  // Direct node access for producers that rewrite closed subtrees (YAML merge keys, retroactive
  // flow mappings, last-wins duplicate keys). Pointers are invalidated by any later write.
  myna_value_node* nodes() { return nodes_.data(); }
  const myna_value_node* nodes() const { return nodes_.data(); }
  // Text of a STRING or DECIMAL node (inline, owned or region).
  std::string_view text_of(size_t index) const { return text_of(nodes_[index]); }
  // The same for a copy of one of this tape's nodes (the text it names stays valid).
  std::string_view text_of(const myna_value_node& node) const {
    if (node.tag & MYNA_VALUE_REGION_TEXT) {
      uint32_t region = uint32_t(node.payload >> 32);
      const uint8_t* base = region == 0 ? bytes_.data() : regions_[region - 1].data;
      return {reinterpret_cast<const char*>(base + uint32_t(node.payload)), node.length};
    }
    return {reinterpret_cast<const char*>(&node.payload), node.length};
  }
  // Drops nodes [count, node_count()). Their owned text stays in the byte table.
  void truncate(size_t count) { nodes_.truncate(count); }
  // Removes the closed nodes [first, first + count): later nodes move down and every `next` beyond
  // `first` is renumbered. Containers still open (their `next` is set by end()) must start before
  // `first`. O(nodes after first).
  void erase(size_t first, size_t count) {
    size_t size = nodes_.size();
    if (count == 0) return;
    if (first > size || count > size - first) throw std::out_of_range("Myna erase range");
    myna_value_node* n = nodes_.data();
    std::memmove(n + first, n + first + count, (size - first - count) * sizeof(myna_value_node));
    nodes_.truncate(size - count);
    for (size_t i = first; i < size - count; ++i)
      if (!myna_value_inline(&n[i]) && n[i].next > first) n[i].next -= count;
  }
  // Appends `count` closed nodes copied from `source` (which must not point into this tape),
  // adding `shift` to every non-inline `next`. The caller keeps the copied structure well-formed.
  void append(const myna_value_node* source, size_t count, int64_t shift) {
    if (count > limits_.nodes - nodes_.size()) throw std::length_error("Myna node limit");
    myna_value_node* out = nodes_.extend(memory_, count);
    std::memcpy(out, source, count * sizeof(myna_value_node));
    for (size_t i = 0; i < count; ++i)
      if (!myna_value_inline(&out[i])) out[i].next = uint64_t(int64_t(out[i].next) + shift);
  }
  myna_value_view view() const {
    if (nodes_.size() == 0) throw std::logic_error("incomplete Myna document");
    return {MYNA_VALUE_ABI, nonfinite_ ? nonfinite_flag : 0, nodes_.data(), nodes_.size(),
            bytes_.data(), bytes_.size(), regions_, region_count_};
  }
};

// Adapter running the decoder against myna::value_builder; used to prove tape_writer parity.
class builder_writer final {
  myna::value_builder builder_;
public:
  builder_writer(std::pmr::memory_resource* memory, myna::value_limits limits) : builder_(memory, limits) {}
  void reserve(size_t, size_t) {}
  uint32_t region(std::string_view value) { return builder_.region(value); }
  size_t begin(uint32_t kind, uint32_t) {
    if (kind == MYNA_VALUE_OBJECT) builder_.begin_object();
    else builder_.begin_array();
    return 0;
  }
  void end(size_t, uint64_t) { builder_.end(); }
  void null() { builder_.null(); }
  void boolean(bool value) { builder_.boolean(value); }
  void integer(int64_t value) { builder_.integer(value); }
  void unsigned_integer(uint64_t value) { builder_.unsigned_integer(value); }
  void floating(double value) { builder_.floating(value); }
  void string(std::string_view utf8) { builder_.string(utf8); }
  void string_ref(uint32_t region, uint32_t offset, uint32_t length) { builder_.string_ref(region, offset, length); }
  void number_ref(uint32_t region, uint32_t offset, uint32_t length) {
    builder_.validated_number_ref(region, offset, length);
  }
  myna_value_view view() const { return builder_.view(); }
};
} // namespace truffledata::tape

// JSON's names for the shared types until the Java/C++ rename (YAML plan Task 4) settles them.
namespace truffledata::json {
using tape::builder_writer;
using tape::tape_writer;
} // namespace truffledata::json
#endif
