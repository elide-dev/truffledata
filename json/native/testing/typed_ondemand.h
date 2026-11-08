#ifndef TRUFFLEDATA_JSON_TESTING_TYPED_ONDEMAND_H
#define TRUFFLEDATA_JSON_TESTING_TYPED_ONDEMAND_H
#include "json/native/typed.h"
#include "json/native/generated/seam.h"
#include "tape/native/typed_plan.h"
#include "tape/native/typed_slots.h"
#include <algorithm>
#include <cstring>
#include <memory>
#include <simdutf.h>
#include <string>
#include <unordered_map>
#include <utility>

namespace truffledata::json::testing {
using tape::scalar_kind;
using tape::scalar_field;
using tape::record_plan;
using json::typed_plan;
using tape::max_plan_depth;
using tape::no_child;

using scalar_slot = JsonScalarSlot;
using record_entry = JsonRecordEntry;

// Fixed slots: no Myna builder, nodes, regions, or generic traversal tape.
// One slot arena and one record table, both charged to index_memory. Input and
// parser resources live with the result, independently of the plan.
// Root fields occupy slots [0, root size). A RECORD slot holds its child entry
// index in bits; a repeated slot holds (first << 32) | count, where first is a
// slot index for scalar elements or a record-table index for record elements.
class ondemand_typed_document final : private tape::typed_slots<scalar_slot, record_entry> {
  simdjson::padded_string input_;
  simdjson::ondemand::parser parser_;
  const typed_plan* plan_;

  static std::string_view checked(std::string_view input, tape::storage_limits storage) {
    if (input.size() > UINT32_MAX || input.size() > storage.input_bytes)
      throw std::length_error("JSON input limit");
    return input;
  }
  void lexical(size_t index, simdjson::ondemand::value& value) {
    auto token = trim(value.raw_json_token());
    auto& slot = slots_[index];
    slot.lexical = reinterpret_cast<uint8_t*>(const_cast<char*>(token.data()));
    slot.lexical_length = token.size();
  }
  // Writes one scalar under its contract. Never grows an arena.
  static void scalar(simdjson::ondemand::value& value, const scalar_field& field,
                     scalar_slot& slot) {
    switch (field.kind) {
      case scalar_kind::boolean:
        slot.bits = value.get_bool().value();
        break;
      case scalar_kind::int64: {
        int64_t integer = value.get_int64().value();
        if (integer < field.minimum || integer > field.maximum)
          throw std::invalid_argument("JSON integer range");
        slot.bits = static_cast<uint64_t>(integer);
        break;
      }
      case scalar_kind::uint64:
        slot.bits = value.get_uint64().value();
        if (slot.bits > field.unsigned_maximum)
          throw std::invalid_argument("JSON unsigned range");
        break;
      case scalar_kind::float64: {
        double number = value.get_double().value();
        if (!std::isfinite(number)) throw std::invalid_argument("Nonfinite JSON float");
        if (field.float_bits == 32 && static_cast<double>(static_cast<float>(number)) != number)
          throw std::invalid_argument("JSON floating width");
        std::memcpy(&slot.bits, &number, sizeof(number));
        break;
      }
      case scalar_kind::string: {
        auto text = value.get_string().value();
        if (text.size() > field.max_utf8_bytes)
          throw std::length_error("JSON string byte limit");
        // get_string has already validated/decoded UTF-8. Count with SIMD,
        // without a Java String, temporary UTF-16 buffer, or duplicate validation.
        if (field.length_unit) {
          uint64_t length = field.length_unit == 1
              ? simdutf::utf16_length_from_utf8(text.data(), text.size())
              : simdutf::count_utf8(text.data(), text.size());
          if (length < field.minimum_length || length > field.maximum_length)
            throw std::invalid_argument("JSON string length contract");
        }
        slot.text = reinterpret_cast<uint8_t*>(const_cast<char*>(text.data()));
        slot.text_length = text.size();
        break;
      }
      case scalar_kind::record:
        throw std::logic_error("JSON record decoded as scalar");
    }
  }
  // Appends one child entry and its slots; returns the entry index.
  uint64_t child(simdjson::ondemand::object object, uint32_t ordinal) {
    size_t entry = grow_records(1);
    const auto& plan = plan_->record(ordinal);
    size_t base = grow_slots(plan.size());
    auto masks = members(object, plan, base);
    records_[entry] = {masks.first, masks.second, static_cast<uint32_t>(base), ordinal};
    return entry;
  }
  // Decodes a JSON array; returns (first << 32) | count, or 0 when empty.
  uint64_t elements(simdjson::ondemand::array array, const scalar_field& field) {
    size_t count = array.count_elements().value();
    if (!count) return 0;
    size_t first, index = 0;
    if (field.kind == scalar_kind::record) {
      // Reserve the contiguous element entries before any element allocates
      // its own descendants.
      first = grow_records(count);
      const auto& plan = plan_->record(field.child);
      for (auto element : array) {
        auto object = element.get_object().value();
        if (index == count) throw std::invalid_argument("JSON array changed length");
        size_t base = grow_slots(plan.size());
        auto masks = members(object, plan, base);
        records_[first + index++] = {masks.first, masks.second, static_cast<uint32_t>(base),
                                     field.child};
      }
    } else {
      first = grow_slots(count);
      for (auto element : array) {
        auto value = element.value();
        if (index == count) throw std::invalid_argument("JSON array changed length");
        lexical(first + index, value);
        if (value.type().value() == simdjson::ondemand::json_type::null)
          throw std::invalid_argument("Unexpected JSON null element");
        scalar(value, field, slots_[first + index++]);
      }
    }
    if (index != count) throw std::invalid_argument("JSON array changed length");
    return (uint64_t(first) << 32) | count;
  }
  // Decodes one closed record whose slots start at base. Arenas may grow
  // while descendants decode, so slots are addressed by index, never held.
  std::pair<uint64_t, uint64_t> members(simdjson::ondemand::object object,
                                        const record_plan& plan, size_t base) {
    uint64_t present = 0, nulls = 0;
    for (auto member : object) {
      size_t ordinal = plan.ordinal(member.unescaped_key().value());
      uint64_t bit = uint64_t(1) << ordinal;
      if (present & bit) throw std::invalid_argument("Duplicate JSON field");
      present |= bit;
      const auto& field = plan.field(ordinal);
      auto value = member.value().value();
      size_t index = base + ordinal;
      bool container = field.repeated || field.kind == scalar_kind::record;
      if (value.type().value() == simdjson::ondemand::json_type::null) {
        lexical(index, value);
        if (!value.is_null().value() || !field.nullable)
          throw std::invalid_argument("Unexpected JSON null");
        nulls |= bit;
        continue;
      }
      if (!container) {
        lexical(index, value);
        scalar(value, field, slots_[index]);
        continue;
      }
      uint64_t bits = field.repeated ? elements(value.get_array().value(), field)
                                     : child(value.get_object().value(), field.child);
      slots_[index].bits = bits;
    }
    if ((present & plan.required()) != plan.required())
      throw std::invalid_argument("Missing required JSON field");
    return {present, nulls};
  }
public:
  ondemand_typed_document(const typed_plan& plan, std::string_view input, tape::storage_limits storage = {},
                 std::pmr::memory_resource* upstream = std::pmr::get_default_resource())
      : tape::typed_slots<scalar_slot, record_entry>(storage.index_bytes, upstream),
        input_(checked(input, storage)), plan_(&plan) {
    if (!input_.data()) throw std::bad_alloc();
    const auto& root = plan.root();
    slots_.resize(root.size());
    if (!plan.flat()) records_.resize(1);
    auto document = parser_.iterate(input_).value();
    auto masks = members(document.get_object().value(), root, 0);
    if (!document.at_end()) throw std::invalid_argument("Trailing JSON input");
    root_ = {masks.first, masks.second, 0, 0};
    if (!plan.flat()) records_[0] = root_;
    plan_ = nullptr; // completed documents retain no plan pointer
  }
  ondemand_typed_document(const ondemand_typed_document&) = delete;
  ondemand_typed_document& operator=(const ondemand_typed_document&) = delete;
  using tape::typed_slots<scalar_slot, record_entry>::slots;
  using tape::typed_slots<scalar_slot, record_entry>::size;
  using tape::typed_slots<scalar_slot, record_entry>::present;
  using tape::typed_slots<scalar_slot, record_entry>::nulls;
  using tape::typed_slots<scalar_slot, record_entry>::slot;
  using tape::typed_slots<scalar_slot, record_entry>::records;
  using tape::typed_slots<scalar_slot, record_entry>::record_count;
  using tape::typed_slots<scalar_slot, record_entry>::record;
  tape::allocation_stats allocations() const {
    return {input_.size(), input_.size() + simdjson::SIMDJSON_PADDING,
            memory_.current(), memory_.peak(), memory_.allocations(), parser_.capacity()};
  }
};
} // namespace truffledata::json
#endif
