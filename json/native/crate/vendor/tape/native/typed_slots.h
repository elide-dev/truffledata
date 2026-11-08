#ifndef TRUFFLEDATA_TAPE_TYPED_SLOTS_H
#define TRUFFLEDATA_TAPE_TYPED_SLOTS_H
#include "tape/native/index_memory.h"
#include <cstdint>
#include <stdexcept>
#include <vector>

namespace truffledata::tape {
// The generated ABI types are template arguments so each producer publishes its actual C
// structs. Sharing layout never requires aliasing one generated struct as another struct type.
// A record field indexes records; a repeated field packs (first << 32) | count, indexing slots
// for scalar elements and records for record elements. The flat root needs no record allocation.
template<class Slot, class Record> class typed_slots {
 protected:
  index_memory memory_;
  std::pmr::vector<Slot> slots_;
  std::pmr::vector<Record> records_;
  Record root_{};

  size_t grow_slots(size_t count) {
    size_t first = slots_.size();
    if (count > UINT32_MAX - first) throw std::length_error("typed slot arena limit");
    slots_.resize(first + count);
    return first;
  }
  size_t grow_records(size_t count) {
    size_t first = records_.size();
    if (count > UINT32_MAX - first) throw std::length_error("typed record table limit");
    records_.resize(first + count);
    return first;
  }

 public:
  typed_slots(size_t limit, std::pmr::memory_resource* upstream)
      : memory_(limit, upstream), slots_(&memory_), records_(&memory_) {}
  typed_slots(const typed_slots&) = delete;
  typed_slots& operator=(const typed_slots&) = delete;
  const Slot* slots() const { return slots_.data(); }
  size_t size() const { return slots_.size(); }
  uint64_t present() const { return root_.present; }
  uint64_t nulls() const { return root_.nulls; }
  const Slot& slot(size_t index) const { return slots_.at(index); }
  const Record* records() const { return records_.empty() ? &root_ : records_.data(); }
  size_t record_count() const { return records_.empty() ? 1 : records_.size(); }
  const Record& record(size_t index) const {
    if (index >= record_count()) throw std::out_of_range("typed record entry");
    return records()[index];
  }
};
} // namespace truffledata::tape
#endif
