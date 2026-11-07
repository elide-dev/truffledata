#ifndef TRUFFLEDATA_TAPE_INDEX_MEMORY_H
#define TRUFFLEDATA_TAPE_INDEX_MEMORY_H
#include <cstddef>
#include <memory_resource>
#include <stdexcept>

namespace truffledata::tape {
struct storage_limits {
  size_t input_bytes = 64 * 1024 * 1024;
  size_t index_bytes = 64 * 1024 * 1024;
};

// Tracks requested allocation extents, not allocator metadata or a parser's private allocations
// (simdjson's; rapidyaml's go through it by callbacks). Growth is charged before the old allocation is released.
class index_memory final : public std::pmr::memory_resource {
  std::pmr::memory_resource* upstream_;
  size_t limit_;
  size_t current_ = 0, peak_ = 0, allocations_ = 0;
  void* do_allocate(size_t bytes, size_t alignment) override {
    if (bytes > limit_ - current_) throw std::length_error("index allocation limit");
    void* pointer = upstream_->allocate(bytes, alignment);
    current_ += bytes;
    if (current_ > peak_) peak_ = current_;
    ++allocations_;
    return pointer;
  }
  void do_deallocate(void* pointer, size_t bytes, size_t alignment) override {
    upstream_->deallocate(pointer, bytes, alignment);
    current_ -= bytes;
  }
  bool do_is_equal(const std::pmr::memory_resource& other) const noexcept override {
    return this == &other;
  }
public:
  explicit index_memory(size_t limit, std::pmr::memory_resource* upstream)
      : upstream_(upstream), limit_(limit) {
    if (!upstream) throw std::invalid_argument("null index allocator");
  }
  // Stops accounting for bytes whose ownership moved elsewhere (a retained session document);
  // their new owner frees them through upstream().
  void forget(size_t bytes) { current_ -= bytes < current_ ? bytes : current_; }
  std::pmr::memory_resource* upstream() const { return upstream_; }
  size_t current() const { return current_; }
  size_t peak() const { return peak_; }
  size_t allocations() const { return allocations_; }
};

struct allocation_stats {
  size_t copied_input_bytes;
  size_t padded_input_bytes;
  size_t index_retained_bytes;
  size_t index_peak_bytes;
  size_t index_allocations;
  // Public parser capacity measures input capacity, NOT actual scratch bytes.
  size_t parser_input_capacity;
};
} // namespace truffledata::tape

#endif
