#ifndef TRUFFLEDATA_JSON_TYPED_H
#define TRUFFLEDATA_JSON_TYPED_H
#include "json/native/decoder.h"
#include "json/native/generated/seam.h"
#include <algorithm>
#include <cstring>
#include <memory>
#include <simdutf.h>
#include <string>
#include <unordered_map>
#include <utility>

namespace truffledata::json {
enum class scalar_kind { boolean, int64, uint64, float64, string, record };

// Maximum record nesting of a typed plan: the root alone has depth 1.
inline constexpr size_t max_plan_depth = 32;
inline constexpr uint32_t no_child = UINT32_MAX;

struct scalar_field {
  std::string name;
  scalar_kind kind;
  bool required = true;
  bool nullable = false;
  int64_t minimum = INT64_MIN;
  int64_t maximum = INT64_MAX;
  uint64_t unsigned_maximum = UINT64_MAX;
  size_t max_utf8_bytes = UINT32_MAX;
  uint32_t float_bits = 0;
  uint32_t length_unit = 0; // 0 unknown, 1 UTF-16 units, 2 Unicode scalar values
  uint64_t minimum_length = 0;
  uint64_t maximum_length = UINT64_MAX;
  // A repeated field is a JSON array; scalar contracts apply to every element.
  bool repeated = false;
  // Record ordinal for scalar_kind::record, otherwise no_child.
  uint32_t child = no_child;
};

// Immutable closed-record plan. Names are owned; lookup views cannot escape or
// be invalidated by a caller mutating its schema construction buffer.
class record_plan final {
  std::vector<scalar_field> fields_;
  std::unordered_map<std::string_view, size_t> ordinals_;
  uint64_t required_ = 0;
public:
  explicit record_plan(std::vector<scalar_field> fields) : fields_(std::move(fields)) {
    if (fields_.size() > 64) throw std::invalid_argument("JSON scalar plan field limit");
    for (size_t i = 0; i < fields_.size(); ++i) {
      const auto& field = fields_[i];
      if (!simdutf::validate_utf8(field.name.data(), field.name.size()))
        throw std::invalid_argument("Invalid UTF-8 schema name");
      if (!ordinals_.emplace(field.name, i).second)
        throw std::invalid_argument("Duplicate JSON schema field");
      if (field.minimum > field.maximum) throw std::invalid_argument("Invalid integer range");
      switch (field.kind) {
        case scalar_kind::boolean: case scalar_kind::int64: case scalar_kind::uint64:
        case scalar_kind::float64: case scalar_kind::string:
          if (field.child != no_child) throw std::invalid_argument("Scalar JSON field child");
          break;
        case scalar_kind::record:
          if (field.child == no_child) throw std::invalid_argument("JSON record field child");
          break;
        default: throw std::invalid_argument("Unknown JSON scalar kind");
      }
      if ((field.float_bits != 0 && field.float_bits != 32 && field.float_bits != 64)
          || (field.float_bits && field.kind != scalar_kind::float64))
        throw std::invalid_argument("Invalid JSON floating width");
      if (field.length_unit > 2 || field.minimum_length > field.maximum_length
          || (field.length_unit && field.kind != scalar_kind::string))
        throw std::invalid_argument("Invalid JSON string length contract");
      if (field.required) required_ |= uint64_t(1) << i;
    }
  }
  record_plan(const record_plan&) = delete;
  record_plan& operator=(const record_plan&) = delete;
  size_t size() const { return fields_.size(); }
  uint64_t required() const { return required_; }
  const scalar_field& field(size_t index) const { return fields_.at(index); }
  size_t ordinal(std::string_view name) const {
    auto found = ordinals_.find(name);
    if (found == ordinals_.end()) throw std::invalid_argument("Unknown JSON field");
    return found->second;
  }
};

// Immutable, reusable typed plan: record 0 is the root; RECORD fields link to
// other records by ordinal. Recursive plans and plans deeper than
// max_plan_depth are rejected here, so decoding recursion is bounded.
class typed_plan final {
  std::vector<std::unique_ptr<record_plan>> records_;
  bool flat_ = true;

  size_t measure(size_t record, size_t level, std::vector<uint8_t>& depths) const {
    constexpr uint8_t visiting = 0xff;
    if (level > max_plan_depth) throw std::invalid_argument("JSON plan depth");
    auto& depth = depths[record];
    if (depth == visiting) throw std::invalid_argument("Recursive JSON plan");
    if (!depth) {
      depth = visiting;
      size_t measured = 1;
      const auto& plan = *records_[record];
      for (size_t i = 0; i < plan.size(); ++i) {
        const auto& field = plan.field(i);
        if (field.kind == scalar_kind::record)
          measured = std::max(measured, 1 + measure(field.child, level + 1, depths));
      }
      depth = static_cast<uint8_t>(measured);
    }
    if (level - 1 + depth > max_plan_depth) throw std::invalid_argument("JSON plan depth");
    return depth;
  }
public:
  explicit typed_plan(std::vector<std::vector<scalar_field>> records) {
    if (records.empty()) throw std::invalid_argument("JSON plan without root record");
    if (records.size() > UINT32_MAX) throw std::length_error("JSON plan record limit");
    records_.reserve(records.size());
    for (auto& fields : records)
      records_.push_back(std::make_unique<record_plan>(std::move(fields)));
    for (const auto& record : records_)
      for (size_t i = 0; i < record->size(); ++i) {
        const auto& field = record->field(i);
        if (field.kind == scalar_kind::record && field.child >= records_.size())
          throw std::invalid_argument("JSON record field child");
      }
    const auto& root = *records_[0];
    for (size_t i = 0; i < root.size(); ++i)
      if (root.field(i).kind == scalar_kind::record) flat_ = false;
    std::vector<uint8_t> depths(records_.size());
    for (size_t record = 0; record < records_.size(); ++record) (void)measure(record, 1, depths);
  }
  explicit typed_plan(std::vector<scalar_field> root)
      : typed_plan(std::vector<std::vector<scalar_field>>{std::move(root)}) {}
  typed_plan(const typed_plan&) = delete;
  typed_plan& operator=(const typed_plan&) = delete;
  size_t record_count() const { return records_.size(); }
  const record_plan& record(size_t index) const { return *records_.at(index); }
  const record_plan& root() const { return *records_[0]; }
  // A flat plan has no RECORD field reachable from the root, so its only
  // record entry is the root and needs no record-table allocation.
  bool flat() const { return flat_; }
  // Single-record convenience accessors over the root.
  size_t size() const { return root().size(); }
  uint64_t required() const { return root().required(); }
  const scalar_field& field(size_t index) const { return root().field(index); }
};

using scalar_slot = JsonScalarSlot;
using record_entry = JsonRecordEntry;

// Fixed slots: no Myna builder, nodes, regions, or generic traversal tape.
// One slot arena and one record table, both charged to index_memory. Input and
// parser resources live with the result, independently of the plan.
// Root fields occupy slots [0, root size). A RECORD slot holds its child entry
// index in bits; a repeated slot holds (first << 32) | count, where first is a
// slot index for scalar elements or a record-table index for record elements.
class typed_document final {
  simdjson::padded_string input_;
  simdjson::ondemand::parser parser_;
  index_memory memory_;
  std::pmr::vector<scalar_slot> slots_;
  std::pmr::vector<record_entry> records_;
  record_entry root_{};
  const typed_plan* plan_;

  static std::string_view checked(std::string_view input, storage_limits storage) {
    if (input.size() > UINT32_MAX || input.size() > storage.input_bytes)
      throw std::length_error("JSON input limit");
    return input;
  }
  size_t grow_slots(size_t count) {
    size_t first = slots_.size();
    if (count > UINT32_MAX - first) throw std::length_error("JSON slot arena limit");
    slots_.resize(first + count);
    return first;
  }
  size_t grow_records(size_t count) {
    size_t first = records_.size();
    if (count > UINT32_MAX - first) throw std::length_error("JSON record table limit");
    records_.resize(first + count);
    return first;
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
  typed_document(const typed_plan& plan, std::string_view input, storage_limits storage = {},
                 std::pmr::memory_resource* upstream = std::pmr::get_default_resource())
      : input_(checked(input, storage)), memory_(storage.index_bytes, upstream), slots_(&memory_),
        records_(&memory_), plan_(&plan) {
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
  typed_document(const typed_document&) = delete;
  typed_document& operator=(const typed_document&) = delete;
  const scalar_slot* slots() const { return slots_.data(); }
  size_t size() const { return slots_.size(); }
  uint64_t present() const { return root_.present; }
  uint64_t nulls() const { return root_.nulls; }
  const scalar_slot& slot(size_t index) const { return slots_.at(index); }
  // Flat plans publish the root entry from document control storage.
  const record_entry* records() const { return records_.empty() ? &root_ : records_.data(); }
  size_t record_count() const { return records_.empty() ? 1 : records_.size(); }
  const record_entry& record(size_t index) const {
    if (index >= record_count()) throw std::out_of_range("JSON record entry");
    return records()[index];
  }
  allocation_stats allocations() const {
    return {input_.size(), input_.size() + simdjson::SIMDJSON_PADDING,
            memory_.current(), memory_.peak(), memory_.allocations(), parser_.capacity()};
  }
};
} // namespace truffledata::json
#endif
