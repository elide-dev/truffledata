#ifndef TRUFFLEDATA_TAPE_TAPE_WRITER_H
#define TRUFFLEDATA_TAPE_TAPE_WRITER_H
#include "tape/native/arena.h"
#include <myna/values.hpp>
#include <algorithm>
#include <cmath>
#include <cstring>
#include <stdexcept>
#include <string_view>

#if !defined(__BYTE_ORDER__) || __BYTE_ORDER__ != __ORDER_LITTLE_ENDIAN__
#error "tape_writer packs inline text with little-endian word stores"
#endif

namespace truffledata::tape {

// Presized Myna value tape writer (ABI v3, or v4 with object directories): writes myna_value_node
// records, owned text, the region table and the v4 directory directly into arena storage. It
// produces the same nodes, bytes, regions and directory as myna::value_builder for the same call
// sequence (pinned by //json/native:test, including enable_v4) and enforces the same node, text,
// depth and container limits. The caller keeps object keys and values paired; the JSON and YAML
// decoders do so by construction.
//
// Myna 0.11.0's value_builder gained reserve() (elide-dev/myna#46), but it still wraps every write
// in a try/catch that poisons the document, keeps a slot stack, re-hashes every key from its bytes
// when an object closes and grows std::pmr::vectors. This raw writer stays the production writer
// for both formats (JSON performance ledger, task V1): reserve() presizes node, text, directory and
// pending-key storage, and v4 directories reuse the key hash the decoder computes anyway for its
// duplicate check.
//
// ABI v4 (enable_v4; Myna 0.11.0, elide-dev/myna#45): objects with at least `threshold` members get
// a directory of (FNV-1a hash, key offset from the object) entries sorted by unsigned hash, then
// offset; the object's payload is the directory start plus one. The producer announces each member
// key with member_key(object, hash) right before writing the key node; `hash` must be
// myna_value_hash over the decoded key bytes. Producers that rewrite closed subtrees (erase,
// append: YAML) stay on v3.
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
  // ABI v4: finished directories, and the announced keys of the objects still open (innermost
  // last), each as (hash, key offset from its object).
  bool v4_ = false;
  uint32_t directory_threshold_ = 0;
  arena_vec<myna_value_directory_entry> directory_;
  arena_vec<myna_value_directory_entry> pending_;

  // Limit failures are thrown out of line so push() and charge() stay small enough to inline.
  [[noreturn, gnu::noinline, gnu::cold]] static void limit(const char* what) { throw std::length_error(what); }
  [[noreturn, gnu::noinline, gnu::cold]] static void invalid(const char* what) { throw std::invalid_argument(what); }
  [[gnu::always_inline]] myna_value_node* push(uint32_t tag, uint32_t length, uint64_t payload) {
    if (nodes_.size() >= limits_.nodes) limit("Myna node limit");
    size_t index = nodes_.size();
    myna_value_node* node = nodes_.extend(memory_, 1);
    *node = {tag, length, payload, index + 1};
    return node;
  }
  [[gnu::always_inline]] void charge(size_t length) {
    if (length > UINT32_MAX || length > limits_.text_bytes - text_bytes_) limit("Myna text limit");
    text_bytes_ += length;
  }
  // Inline text words (zero-padded little-endian bytes) of n <= 16 bytes, with overlapping fixed-size
  // loads inside [p, p + n) instead of a variable-length memcpy call.
  static void pack_inline(const char* p, size_t n, uint64_t (&words)[2]) {
    uint64_t lo = 0, hi = 0;
    if (n >= 8) {
      std::memcpy(&lo, p, 8);
      if (n > 8) {
        uint64_t tail;
        std::memcpy(&tail, p + n - 8, 8);
        hi = tail >> (8 * (16 - n));
      }
    } else if (n >= 4) {
      uint32_t a, b;
      std::memcpy(&a, p, 4);
      std::memcpy(&b, p + n - 4, 4);
      lo = uint64_t(a) | (uint64_t(b) << (8 * (n - 4)));
    } else if (n) {
      lo = uint64_t(uint8_t(p[0])) | (uint64_t(uint8_t(p[n / 2])) << (8 * (n / 2)))
          | (uint64_t(uint8_t(p[n - 1])) << (8 * (n - 1)));
    }
    words[0] = lo;
    words[1] = hi;
  }
  void text(uint32_t kind, std::string_view value) {
    charge(value.size());
    if (value.size() <= MYNA_VALUE_INLINE_BYTES) {
      uint64_t words[2];
      pack_inline(value.data(), value.size(), words);
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

  // The closing object's keys are the last `count` pending entries (nested objects already took
  // theirs). At or above the threshold they are sorted by (unsigned hash, offset) into the
  // directory, as myna::value_builder::end does; below it they are dropped (linear lookup).
  void close_directory(myna_value_node& node, uint64_t count) {
    if (count > pending_.size()) throw std::logic_error("Myna object key not announced");
    size_t first = pending_.size() - count;
    if (directory_threshold_ && count >= directory_threshold_) {
      size_t start = directory_.size();
      myna_value_directory_entry* out = directory_.extend(memory_, count);
      const myna_value_directory_entry* in = pending_.data() + first;
      // Offsets arrive increasing, so a stable sort by hash alone yields (hash, offset) order.
      if (count <= 24) {
        for (size_t i = 0; i < count; ++i) {
          myna_value_directory_entry e = in[i];
          size_t j = i;
          while (j > 0 && out[j - 1].hash > e.hash) { out[j] = out[j - 1]; --j; }
          out[j] = e;
        }
      } else {
        std::memcpy(out, in, count * sizeof(myna_value_directory_entry));
        std::sort(out, out + count, [](const myna_value_directory_entry& a, const myna_value_directory_entry& b) {
          return a.hash < b.hash || (a.hash == b.hash && a.key_offset < b.key_offset);
        });
      }
      node.payload = start + 1;
    }
    pending_.truncate(first);
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

  // Presizes node and owned-text storage and, on a v4 tape, the directory and the pending keys of
  // open objects; later growth moves to a larger arena span.
  void reserve(size_t nodes, size_t bytes) {
    if (nodes > limits_.nodes) nodes = static_cast<size_t>(limits_.nodes);
    nodes_.reserve(memory_, nodes);
    bytes_.reserve(memory_, bytes);
    if (v4_) {
      directory_.reserve(memory_, directory_capacity(nodes));
      pending_.reserve(memory_, pending_capacity(nodes));
    }
  }
  // Directory entries a v4 tape of `nodes` nodes can need (one key per two nodes at most), and the
  // pending-key capacity reserve() takes; producers size their first arena block with these.
  static size_t directory_capacity(size_t nodes) { return nodes / 2 + 1; }
  static size_t pending_capacity(size_t nodes) { return nodes / 2 + 1 < 256 ? nodes / 2 + 1 : 256; }
  // Switches this empty tape to ABI v4 with directories for objects of at least `threshold` members
  // (0: none, as for Myna's builder). Call before reserve() so that it presizes the directory.
  void enable_v4(uint32_t threshold) {
    if (nodes_.size()) throw std::logic_error("enable ABI 4 before writing");
    v4_ = true;
    directory_threshold_ = threshold;
  }
  bool v4() const { return v4_; }
  // Announces the node written next as a member key of the open object at index `object`, with
  // `hash` = myna_value_hash of its decoded bytes. Required for every member key of a v4 tape.
  [[gnu::always_inline]] void member_key(size_t object, uint32_t hash) {
    size_t offset = nodes_.size() - object;
    if (offset > UINT32_MAX) throw std::length_error("Myna directory offset");
    pending_.push(memory_, {hash, static_cast<uint32_t>(offset)});
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
    bool object = node.tag == MYNA_VALUE_OBJECT;
    if (count > (object ? UINT32_MAX / 2 : UINT32_MAX))
      throw std::length_error("Myna container limit");
    node.length = static_cast<uint32_t>(count);
    node.next = nodes_.size();
    if (v4_ && object) close_directory(node, count);
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
  // An inline STRING of `length` <= 16 bytes already packed as Myna's two little-endian payload
  // words (bytes past `length` zero), for producers that loaded the words themselves.
  [[gnu::always_inline]] void string_words(uint64_t lo, uint64_t hi, uint32_t length) {
    if (length > MYNA_VALUE_INLINE_BYTES) invalid("inline Myna text above 16 bytes");
    charge(length);
    push(MYNA_VALUE_STRING, length, lo)->next = hi;
  }
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
    if (v4_) throw std::logic_error("Myna ABI 4 tapes are not rewritten");
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
    if (v4_) throw std::logic_error("Myna ABI 4 tapes are not rewritten");
    if (count > limits_.nodes - nodes_.size()) throw std::length_error("Myna node limit");
    myna_value_node* out = nodes_.extend(memory_, count);
    std::memcpy(out, source, count * sizeof(myna_value_node));
    for (size_t i = 0; i < count; ++i)
      if (!myna_value_inline(&out[i])) out[i].next = uint64_t(int64_t(out[i].next) + shift);
  }
  // The ABI v3 descriptor. A v4 tape exports view_v4() instead, never its truncated prefix.
  myna_value_view view() const {
    if (v4_) throw std::logic_error("use view_v4 for ABI 4");
    return prefix(MYNA_VALUE_ABI);
  }
  myna_value_view_v4 view_v4() const {
    if (!v4_) throw std::logic_error("incomplete ABI 4 document");
    return {prefix(MYNA_VALUE_ABI_V4), directory_.data(), directory_.size(), nullptr, 0};
  }

private:
  myna_value_view prefix(uint32_t version) const {
    if (nodes_.size() == 0) throw std::logic_error("incomplete Myna document");
    return {version, nonfinite_ ? nonfinite_flag : 0, nodes_.data(), nodes_.size(),
            bytes_.data(), bytes_.size(), regions_, region_count_};
  }
};

// Adapter running the decoder against myna::value_builder; used to prove tape_writer parity.
class builder_writer final {
  myna::value_builder builder_;
  myna::value_limits limits_;
public:
  builder_writer(std::pmr::memory_resource* memory, myna::value_limits limits)
      : builder_(memory, limits), limits_(limits) {}
  // Myna 0.11.0's reserve (elide-dev/myna#46): total nodes and bytes, one region and the full
  // depth, clamped to the limits (the builder rejects reservations above them).
  void reserve(size_t nodes, size_t bytes) {
    builder_.reserve(std::min<uint64_t>(nodes, limits_.nodes), std::min<uint64_t>(bytes, limits_.text_bytes), 1,
                     limits_.depth);
  }
  void enable_v4(uint32_t threshold) {
    builder_.enable_v4(threshold);
    v4_ = true;
  }
  bool v4() const { return v4_; }
  // The builder hashes each object's keys itself when the object closes.
  void member_key(size_t, uint32_t) {}
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
  myna_value_view_v4 view_v4() const { return builder_.view_v4(); }
private:
  bool v4_ = false;
};
} // namespace truffledata::tape

#endif
