#ifndef TRUFFLEDATA_JSON_TAPE_WRITER_H
#define TRUFFLEDATA_JSON_TAPE_WRITER_H
#include "json/native/arena.h"
#include <myna/values.hpp>
#include <cmath>
#include <cstring>
#include <stdexcept>
#include <string_view>

#if !defined(__BYTE_ORDER__) || __BYTE_ORDER__ != __ORDER_LITTLE_ENDIAN__
#error "tape_writer packs inline text with little-endian word stores"
#endif

namespace truffledata::json {

// Presized Myna ABI v3 tape writer: writes myna_value_node records, owned text and the region
// table directly into arena storage. It produces the same nodes, bytes and regions as
// myna::value_builder for the same call sequence (pinned by //json/native:test) and enforces the
// same node, text, depth and container limits, without the builder's per-node try/catch, slot
// bookkeeping or geometric growth from an empty vector. The caller keeps object keys and values
// paired; the JSON decoder does so by construction. Until Myna exposes a reserving builder
// (elide-dev/myna#46, request M2) this writer is the JSON-side substitute.
class tape_writer final {
  static constexpr size_t max_regions = 4;
  std::pmr::memory_resource* memory_;
  arena_vec<myna_value_node> nodes_;
  arena_vec<uint8_t> bytes_;
  myna_value_region* regions_ = nullptr;
  uint64_t region_count_ = 0;
  myna::value_limits limits_;
  uint64_t text_bytes_ = 0;

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
  void string(std::string_view utf8) { text(MYNA_VALUE_STRING, utf8); }
  void string_ref(uint32_t region, uint32_t offset, uint32_t length) {
    reference(MYNA_VALUE_STRING, region, offset, length);
  }
  void number_ref(uint32_t region, uint32_t offset, uint32_t length) {
    reference(MYNA_VALUE_DECIMAL, region, offset, length);
  }
  size_t node_count() const { return nodes_.size(); }
  myna_value_view view() const {
    if (nodes_.size() == 0) throw std::logic_error("incomplete Myna document");
    return {MYNA_VALUE_ABI, 0, nodes_.data(), nodes_.size(), bytes_.data(), bytes_.size(),
            regions_, region_count_};
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
} // namespace truffledata::json
#endif
