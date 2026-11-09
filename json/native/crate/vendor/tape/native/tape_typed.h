#ifndef TRUFFLEDATA_TAPE_TYPED_WALK_H
#define TRUFFLEDATA_TAPE_TYPED_WALK_H
#include "tape/native/arena.h"
#include "tape/native/typed_plan.h"
#include "tape/native/typed_slots.h"

namespace truffledata::tape {
enum class typed_kind { null, scalar, array, object };

// A source supplies navigation and scalar conversion over its native tape. The walk owns only
// fixed ABI slots, record entries and conversion scratch; it never materializes a value tree.
// Node indices stay format-specific (Myna node ordinals or compact word offsets).
template<class Slot, class Record, class Source>
class tape_typed final : public typed_slots<Slot, Record> {
  using base = typed_slots<Slot, Record>;
  using base::slots_;
  using base::records_;
  using base::root_;
  using base::memory_;
  using base::grow_slots;
  using base::grow_records;
  block_arena scratch_;
  const typed_plan* plan_;
  Source* source_;
  uint64_t remaining_;

  uint64_t visit(uint64_t node) {
    if (!remaining_) throw std::length_error("typed alias expansion limit");
    --remaining_;
    return source_->resolve(node);
  }
  uint64_t child(uint64_t node, uint32_t ordinal) {
    size_t entry = grow_records(1);
    const auto& plan = plan_->record(ordinal);
    size_t first = grow_slots(plan.size());
    auto masks = members(node, plan, first);
    records_[entry] = {masks.first, masks.second, static_cast<uint32_t>(first), ordinal};
    return entry;
  }
  uint64_t elements(uint64_t node, const scalar_field& field) {
    if (source_->kind(node) != typed_kind::array)
      throw std::invalid_argument("typed field requires an array");
    size_t count = source_->count(node);
    if (!count) return 0;
    size_t first = field.kind == scalar_kind::record ? grow_records(count) : grow_slots(count);
    uint64_t current = source_->first(node);
    for (size_t i = 0; i < count; ++i) {
      uint64_t value = visit(current);
      if (field.kind == scalar_kind::record) {
        const auto& plan = plan_->record(field.child);
        size_t slots = grow_slots(plan.size());
        auto masks = members(value, plan, slots);
        records_[first + i] = {masks.first, masks.second, static_cast<uint32_t>(slots), field.child};
      } else {
        if (source_->kind(value) != typed_kind::scalar)
          throw std::invalid_argument("typed array requires non-null scalar elements");
        source_->scalar(value, field, slots_[first + i], &scratch_);
      }
      current = source_->next(current);
    }
    return (uint64_t(first) << 32) | count;
  }
  std::pair<uint64_t, uint64_t> members(uint64_t node, const record_plan& plan, size_t first) {
    if (source_->kind(node) != typed_kind::object)
      throw std::invalid_argument("typed field requires a mapping");
    uint64_t present = 0, nulls = 0;
    uint64_t key = source_->first(node);
    for (size_t i = 0, count = source_->count(node); i < count; ++i) {
      size_t ordinal = plan.ordinal(source_->key(key));
      uint64_t bit = uint64_t(1) << ordinal;
      if (present & bit) throw std::invalid_argument("duplicate typed field");
      present |= bit;
      const auto& field = plan.field(ordinal);
      uint64_t original = source_->next(key);
      uint64_t value = visit(original);
      size_t index = first + ordinal;
      if (source_->kind(value) == typed_kind::null) {
        if (!field.nullable) throw std::invalid_argument("unexpected typed null");
        source_->lexical(value, slots_[index]);
        nulls |= bit;
      } else if (field.repeated) {
        uint64_t bits = elements(value, field); // descendants may grow slots_
        slots_[index].bits = bits;
      } else if (field.kind == scalar_kind::record) {
        uint64_t bits = child(value, field.child);
        slots_[index].bits = bits;
      } else {
        if (source_->kind(value) != typed_kind::scalar)
          throw std::invalid_argument("typed field requires a scalar");
        source_->scalar(value, field, slots_[index], &scratch_);
      }
      key = source_->next(original);
    }
    if ((present & plan.required()) != plan.required())
      throw std::invalid_argument("missing required typed field");
    return {present, nulls};
  }

 public:
  tape_typed(const typed_plan& plan, Source& source, size_t index_limit, uint64_t expansion_limit,
             std::pmr::memory_resource* upstream = std::pmr::get_default_resource())
      : base(index_limit, upstream), scratch_(&memory_), plan_(&plan), source_(&source),
        remaining_(expansion_limit) {
    grow_slots(plan.root().size());
    if (!plan.flat()) grow_records(1);
    auto masks = members(visit(0), plan.root(), 0);
    root_ = {masks.first, masks.second, 0, 0};
    if (!plan.flat()) records_[0] = root_;
    plan_ = nullptr;
    source_ = nullptr; // Completed results retain no plan/source pointer.
  }
  const index_memory& memory() const { return memory_; }
};
} // namespace truffledata::tape
#endif
