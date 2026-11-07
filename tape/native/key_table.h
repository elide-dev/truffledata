#ifndef TRUFFLEDATA_TAPE_KEY_TABLE_H
#define TRUFFLEDATA_TAPE_KEY_TABLE_H
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <memory_resource>
#include <string_view>

namespace truffledata::tape {

// Side-table entry per node. Bit 31 marks string and key nodes whose decoded bytes are all ASCII.
// Key nodes also carry bits 16-30: the low 15 bits of the key hash, and bits 0-15: the key's offset
// from its object node (1..65535; 0 when farther). Other nodes carry 0 there, so a nonzero low half
// identifies a direct member key of the object at exactly that offset: Java readers validate a
// position hint with one compare. key_hash is 32-bit FNV-1a over the decoded key bytes, masked to
// 31 bits (the duplicate table uses all of it); Java's JsonKey computes the same function.
inline constexpr uint32_t hash_mask = 0x7fffffffu;
inline constexpr uint32_t ascii_flag = 0x80000000u;
inline constexpr uint32_t key_offset_mask = 0xffffu;
inline uint32_t key_meta(uint32_t hash, uint64_t offset, bool plain) {
  return (plain ? ascii_flag : 0) | ((hash & 0x7fffu) << 16)
      | (offset <= key_offset_mask ? uint32_t(offset) : 0);
}
inline uint32_t key_hash(std::string_view s) {
  uint32_t h = 2166136261u;
  for (unsigned char c : s) { h ^= c; h *= 16777619u; }
  return h & hash_mask;
}
// key_hash and ascii in one pass.
inline uint32_t key_hash(std::string_view s, bool& plain) {
  uint32_t h = 2166136261u;
  unsigned char any = 0;
  for (unsigned char c : s) { h ^= c; h *= 16777619u; any |= c; }
  plain = any < 0x80;
  return h & hash_mask;
}
inline bool ascii(std::string_view s) {
  unsigned char any = 0;
  for (unsigned char c : s) any |= c;
  return any < 0x80;
}

// Per-object duplicate-key table: a linear (hash, length, bytes) compare for up to 16 keys on the
// stack, then open addressing in counted index memory. Keys are compared after escape decoding.
// The key bytes must stay valid and unchanged while the table lives.
//
// Compact tapes (ctape.h) detect duplicates in the directory sort instead (tape-gbps plan §3.2).
// This table stays for the Myna-tape JSON decoder until that plan's Task 2 and for YAML until its
// Task 8; key_hash/ascii/key_meta stay as long as those producers do.
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
} // namespace truffledata::tape

#endif
