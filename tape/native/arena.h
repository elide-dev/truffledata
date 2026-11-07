#ifndef TRUFFLEDATA_TAPE_ARENA_H
#define TRUFFLEDATA_TAPE_ARENA_H
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <memory_resource>
#include <stdexcept>
#include <new>
#include <utility>

namespace truffledata::tape {

// Bump allocator over a reusable list of blocks. Small allocations bump within the current block;
// allocations above `large_threshold` go straight to the upstream resource (and are freed there
// when deallocated), so geometric growth of a big tape never wastes arena space. Every upstream
// request (block or large allocation) is charged by the upstream resource, normally index_memory,
// so budgets and allocation counts see blocks, not individual nodes.
//
// Deallocating the most recent small allocation rewinds the bump pointer; other small
// deallocations are no-ops until reset() or destruction. Not thread-safe.
class block_arena final : public std::pmr::memory_resource {
public:
  static constexpr size_t min_block = 4096;
  static constexpr size_t max_block = 64 * 1024;
  static constexpr size_t large_threshold = 32 * 1024;
  // Blocks kept across reset(); the rest are returned upstream.
  static constexpr size_t retained_blocks = 4;

private:
  struct block {
    block* next;
    size_t size;  // Total bytes including this header.
  };
  struct large {
    large* next;
    large* prev;
    void* base;
    size_t size;  // Total upstream bytes.
    size_t align;
  };
  static constexpr size_t header = (sizeof(block) + alignof(std::max_align_t) - 1) & ~(alignof(std::max_align_t) - 1);

  std::pmr::memory_resource* upstream_;
  block* head_ = nullptr;     // First block of the chain.
  block* current_ = nullptr;  // Block being bumped; blocks after it are spare.
  char* cursor_ = nullptr;
  char* end_ = nullptr;
  large* large_ = nullptr;
  size_t next_size_;

  static char* begin_of(block* b) { return reinterpret_cast<char*>(b) + header; }
  static char* end_of(block* b) { return reinterpret_cast<char*>(b) + b->size; }
  static char* align_up(char* p, size_t align) {
    auto value = reinterpret_cast<uintptr_t>(p);
    return reinterpret_cast<char*>((value + align - 1) & ~uintptr_t(align - 1));
  }

  void* allocate_large(size_t bytes, size_t align) {
    size_t a = align < alignof(std::max_align_t) ? alignof(std::max_align_t) : align;
    // The record sits immediately before the returned pointer.
    size_t offset = (sizeof(large) + a - 1) & ~(a - 1);
    if (bytes > SIZE_MAX - offset) throw std::bad_alloc();
    size_t total = offset + bytes;
    void* base = upstream_->allocate(total, a);
    auto* data = static_cast<char*>(base) + offset;
    auto* record = reinterpret_cast<large*>(data - sizeof(large));
    *record = {large_, nullptr, base, total, a};
    if (large_) large_->prev = record;
    large_ = record;
    return data;
  }
  void free_large(large* record) {
    if (record->prev) record->prev->next = record->next;
    else large_ = record->next;
    if (record->next) record->next->prev = record->prev;
    upstream_->deallocate(record->base, record->size, record->align);
  }
  // Moves to the next spare block that fits, or acquires a new one.
  void advance(size_t bytes, size_t align) {
    size_t need = header + bytes + align;
    for (block* b = current_ ? current_->next : head_; b; b = b->next) {
      if (b->size >= need) {
        // Unlink b and place it right after current_ so the chain stays [used..., spare...].
        if (b != (current_ ? current_->next : head_)) unlink_spare(b), link_after_current(b);
        enter(b);
        return;
      }
    }
    size_t size = next_size_ > need ? next_size_ : need;
    auto* b = static_cast<block*>(upstream_->allocate(size, alignof(std::max_align_t)));
    b->size = size;
    b->next = nullptr;
    link_after_current(b);
    enter(b);
    if (next_size_ < max_block) next_size_ = next_size_ * 2 > max_block ? max_block : next_size_ * 2;
  }
  void unlink_spare(block* target) {
    for (block** link = current_ ? &current_->next : &head_; *link; link = &(*link)->next)
      if (*link == target) { *link = target->next; return; }
  }
  void link_after_current(block* b) {
    if (current_) { b->next = current_->next; current_->next = b; }
    else { b->next = head_; head_ = b; }
  }
  void enter(block* b) {
    current_ = b;
    cursor_ = begin_of(b);
    end_ = end_of(b);
  }

  void* do_allocate(size_t bytes, size_t align) override {
    if (bytes > large_threshold) return allocate_large(bytes, align);
    char* p = align_up(cursor_, align);
    if (!cursor_ || p > end_ || size_t(end_ - p) < bytes) {
      advance(bytes, align);
      p = align_up(cursor_, align);
    }
    cursor_ = p + bytes;
    return p;
  }
  void do_deallocate(void* pointer, size_t bytes, size_t) override {
    if (bytes > large_threshold) {
      free_large(reinterpret_cast<large*>(static_cast<char*>(pointer) - sizeof(large)));
      return;
    }
    // Rewind only the most recent allocation of the current block.
    if (static_cast<char*>(pointer) + bytes == cursor_ && static_cast<char*>(pointer) >= begin_of(current_))
      cursor_ = static_cast<char*>(pointer);
  }
  bool do_is_equal(const std::pmr::memory_resource& other) const noexcept override {
    return this == &other;
  }

public:
  // `first_block` sizes the first upstream block (clamped to [min_block, max_block]).
  explicit block_arena(std::pmr::memory_resource* upstream, size_t first_block = min_block)
      : upstream_(upstream), next_size_(clamp(first_block)) {
    if (!upstream) throw std::invalid_argument("null tape arena upstream");
  }
  block_arena(const block_arena&) = delete;
  block_arena& operator=(const block_arena&) = delete;
  ~block_arena() override { release_all(); }

  static size_t clamp(size_t size) {
    size += header;
    if (size < min_block) return min_block;
    if (size > max_block) return max_block;
    return (size + 4095) & ~size_t(4095);
  }
  // Sizes the next block acquired from upstream (no effect on blocks already held).
  void hint(size_t bytes) { next_size_ = clamp(bytes); }

  // Frees every large allocation, keeps at most retained_blocks blocks and rewinds to the first.
  void reset() noexcept {
    while (large_) free_large(large_);
    size_t kept = 0;
    block** link = &head_;
    while (*link) {
      if (kept < retained_blocks) { ++kept; link = &(*link)->next; continue; }
      block* doomed = *link;
      *link = doomed->next;
      upstream_->deallocate(doomed, doomed->size, alignof(std::max_align_t));
    }
    current_ = nullptr;
    cursor_ = end_ = nullptr;
  }
  void release_all() noexcept {
    while (large_) free_large(large_);
    while (head_) {
      block* doomed = head_;
      head_ = doomed->next;
      upstream_->deallocate(doomed, doomed->size, alignof(std::max_align_t));
    }
    current_ = nullptr;
    cursor_ = end_ = nullptr;
  }
  // Blocks and large allocations detached from an arena; frees them through `upstream` on
  // destruction. Detached storage no longer counts against the arena's budget.
  class storage final {
    block* blocks_ = nullptr;
    large* large_ = nullptr;
    std::pmr::memory_resource* upstream_ = nullptr;
    friend class block_arena;
  public:
    storage() = default;
    storage(const storage&) = delete;
    storage& operator=(const storage&) = delete;
    storage(storage&& other) noexcept { *this = std::move(other); }
    storage& operator=(storage&& other) noexcept {
      std::swap(blocks_, other.blocks_);
      std::swap(large_, other.large_);
      std::swap(upstream_, other.upstream_);
      return *this;
    }
    ~storage() {
      while (large_) {
        large* doomed = large_;
        large_ = doomed->next;
        upstream_->deallocate(doomed->base, doomed->size, doomed->align);
      }
      while (blocks_) {
        block* doomed = blocks_;
        blocks_ = doomed->next;
        upstream_->deallocate(doomed, doomed->size, alignof(std::max_align_t));
      }
    }
  };
  // Moves the blocks used since the last reset() and every live large allocation into `out`, to be
  // freed through `release_to` (the resource beneath this arena's accounting). Spare blocks stay.
  // Returns the upstream bytes moved, which the caller removes from its accounting. O(blocks).
  size_t detach(storage& out, std::pmr::memory_resource* release_to) noexcept {
    size_t bytes = 0;
    block* used = nullptr;
    if (current_) {
      used = head_;
      head_ = current_->next;
      current_->next = nullptr;
      for (block* b = used; b; b = b->next) bytes += b->size;
    }
    for (large* l = large_; l; l = l->next) bytes += l->size;
    out.blocks_ = used;
    out.large_ = large_;
    out.upstream_ = release_to;
    large_ = nullptr;
    current_ = nullptr;
    cursor_ = end_ = nullptr;
    return bytes;
  }

  // Diagnostics: blocks held (used and spare) and live large allocations.
  size_t blocks() const {
    size_t n = 0;
    for (block* b = head_; b; b = b->next) ++n;
    return n;
  }
  size_t large_allocations() const {
    size_t n = 0;
    for (large* l = large_; l; l = l->next) ++n;
    return n;
  }
};

// Growable array whose storage comes from a memory resource (normally a block_arena). Plain
// pointers so a finished tape is published as-is; the owning arena frees the storage.
template<class T> class arena_vec {
  T* data_ = nullptr;
  size_t size_ = 0, capacity_ = 0;
  void grow(std::pmr::memory_resource* memory, size_t minimum) {
    size_t capacity = capacity_ ? capacity_ * 2 : 16;
    if (capacity < minimum) capacity = minimum;
    if (capacity > SIZE_MAX / sizeof(T)) throw std::bad_alloc();
    auto* data = static_cast<T*>(memory->allocate(capacity * sizeof(T), alignof(T)));
    if (size_) std::memcpy(data, data_, size_ * sizeof(T));
    if (data_) memory->deallocate(data_, capacity_ * sizeof(T), alignof(T));
    data_ = data;
    capacity_ = capacity;
  }
public:
  void reserve(std::pmr::memory_resource* memory, size_t capacity) {
    if (capacity > capacity_) grow(memory, capacity);
  }
  void push(std::pmr::memory_resource* memory, const T& value) {
    if (size_ == capacity_) grow(memory, size_ + 1);
    data_[size_++] = value;
  }
  // Appends `count` elements without initializing them; returns the first.
  T* extend(std::pmr::memory_resource* memory, size_t count) {
    if (count > capacity_ - size_) grow(memory, size_ + count);
    T* first = data_ + size_;
    size_ += count;
    return first;
  }
  // Forgets the storage (its arena owns it) without freeing.
  void forget() { data_ = nullptr; size_ = capacity_ = 0; }
  T* data() { return data_; }
  const T* data() const { return data_; }
  size_t size() const { return size_; }
  T& operator[](size_t i) { return data_[i]; }
  const T& operator[](size_t i) const { return data_[i]; }
  T& back() { return data_[size_ - 1]; }
  // Drops trailing elements (storage is kept).
  void truncate(size_t size) { if (size < size_) size_ = size; }
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
    if (index >= size_) throw std::out_of_range("tape side-table index");
    return data_[index];
  }
};
} // namespace truffledata::tape

// JSON's names for the shared types until the Java/C++ rename (YAML plan Task 4) settles them.
namespace truffledata::json {
using tape::arena_vec;
using tape::block_arena;
using tape::table_view;
} // namespace truffledata::json
#endif
