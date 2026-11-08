#ifndef TRUFFLEDATA_TAPE_TYPED_PLAN_H
#define TRUFFLEDATA_TAPE_TYPED_PLAN_H
#include <algorithm>
#include <cstdint>
#include <memory>
#include <simdutf.h>
#include <stdexcept>
#include <string>
#include <string_view>
#include <unordered_map>
#include <utility>
#include <vector>

namespace truffledata::tape {
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
  // A repeated field is an array; scalar contracts apply to every element.
  bool repeated = false;
  // Record ordinal for scalar_kind::record, otherwise no_child.
  uint32_t child = no_child;
};

// Immutable closed-record plan. Names are owned; lookup views cannot escape or
// be invalidated by a caller mutating its schema construction buffer.
class record_plan final {
  std::string format_;
  std::vector<scalar_field> fields_;
  std::unordered_map<std::string_view, size_t> ordinals_;
  uint64_t required_ = 0;
public:
  explicit record_plan(std::vector<scalar_field> fields, std::string_view format)
      : format_(format), fields_(std::move(fields)) {
    if (fields_.size() > 64) throw std::invalid_argument(format_ + " scalar plan field limit");
    for (size_t i = 0; i < fields_.size(); ++i) {
      const auto& field = fields_[i];
      if (!simdutf::validate_utf8(field.name.data(), field.name.size()))
        throw std::invalid_argument("Invalid UTF-8 schema name");
      if (!ordinals_.emplace(field.name, i).second)
        throw std::invalid_argument("Duplicate " + format_ + " schema field");
      if (field.minimum > field.maximum) throw std::invalid_argument("Invalid integer range");
      switch (field.kind) {
        case scalar_kind::boolean: case scalar_kind::int64: case scalar_kind::uint64:
        case scalar_kind::float64: case scalar_kind::string:
          if (field.child != no_child) throw std::invalid_argument("Scalar " + format_ + " field child");
          break;
        case scalar_kind::record:
          if (field.child == no_child) throw std::invalid_argument(format_ + " record field child");
          break;
        default: throw std::invalid_argument("Unknown " + format_ + " scalar kind");
      }
      if ((field.float_bits != 0 && field.float_bits != 32 && field.float_bits != 64)
          || (field.float_bits && field.kind != scalar_kind::float64))
        throw std::invalid_argument("Invalid " + format_ + " floating width");
      if (field.length_unit > 2 || field.minimum_length > field.maximum_length
          || (field.length_unit && field.kind != scalar_kind::string))
        throw std::invalid_argument("Invalid " + format_ + " string length contract");
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
    if (found == ordinals_.end()) throw std::invalid_argument("Unknown " + format_ + " field");
    return found->second;
  }
};

// Immutable, reusable typed plan: record 0 is the root; RECORD fields link to
// other records by ordinal. Recursive plans and plans deeper than
// max_plan_depth are rejected here, so decoding recursion is bounded.
class typed_plan final {
  std::string format_;
  std::vector<std::unique_ptr<record_plan>> records_;
  bool flat_ = true;

  size_t measure(size_t record, size_t level, std::vector<uint8_t>& depths) const {
    constexpr uint8_t visiting = 0xff;
    if (level > max_plan_depth) throw std::invalid_argument(format_ + " plan depth");
    auto& depth = depths[record];
    if (depth == visiting) throw std::invalid_argument("Recursive " + format_ + " plan");
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
    if (level - 1 + depth > max_plan_depth) throw std::invalid_argument(format_ + " plan depth");
    return depth;
  }
public:
  explicit typed_plan(std::vector<std::vector<scalar_field>> records, std::string_view format = "JSON")
      : format_(format) {
    if (records.empty()) throw std::invalid_argument(format_ + " plan without root record");
    if (records.size() > UINT32_MAX) throw std::length_error(format_ + " plan record limit");
    records_.reserve(records.size());
    for (auto& fields : records)
      records_.push_back(std::make_unique<record_plan>(std::move(fields), format_));
    for (const auto& record : records_)
      for (size_t i = 0; i < record->size(); ++i) {
        const auto& field = record->field(i);
        if (field.kind == scalar_kind::record && field.child >= records_.size())
          throw std::invalid_argument(format_ + " record field child");
      }
    const auto& root = *records_[0];
    for (size_t i = 0; i < root.size(); ++i)
      if (root.field(i).kind == scalar_kind::record) flat_ = false;
    std::vector<uint8_t> depths(records_.size());
    for (size_t record = 0; record < records_.size(); ++record) (void)measure(record, 1, depths);
  }
  explicit typed_plan(std::vector<scalar_field> root, std::string_view format = "JSON")
      : typed_plan(std::vector<std::vector<scalar_field>>{std::move(root)}, format) {}
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

} // namespace truffledata::tape
#endif
