#ifndef TRUFFLEDATA_JSON_TYPED_H
#define TRUFFLEDATA_JSON_TYPED_H
#include "json/native/decoder.h"
#include "json/native/generated/seam.h"
#include <cstring>
#include <simdutf.h>
#include <string>
#include <unordered_map>

namespace truffledata::json {
enum class scalar_kind { boolean, int64, uint64, float64, string };

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
};

// Immutable, reusable closed-record plan. Names are owned; lookup views cannot
// escape or be invalidated by a caller mutating its schema construction buffer.
class scalar_plan final {
  std::vector<scalar_field> fields_;
  std::unordered_map<std::string_view, size_t> ordinals_;
  uint64_t required_ = 0;
public:
  explicit scalar_plan(std::vector<scalar_field> fields) : fields_(std::move(fields)) {
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
        case scalar_kind::float64: case scalar_kind::string: break;
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
  scalar_plan(const scalar_plan&) = delete;
  scalar_plan& operator=(const scalar_plan&) = delete;
  size_t size() const { return fields_.size(); }
  uint64_t required() const { return required_; }
  const scalar_field& field(size_t index) const { return fields_.at(index); }
  size_t ordinal(std::string_view name) const {
    auto found = ordinals_.find(name);
    if (found == ordinals_.end()) throw std::invalid_argument("Unknown JSON field");
    return found->second;
  }
};

using scalar_slot = JsonScalarSlot;

// Fixed scalar slots: no Myna builder, nodes, regions, or generic traversal tape.
// Input/parser resources live with the result, independently of the plan.
class scalar_document final {
  simdjson::padded_string input_;
  simdjson::ondemand::parser parser_;
  index_memory memory_;
  std::pmr::vector<scalar_slot> slots_;
  uint64_t present_ = 0, nulls_ = 0;
  static std::string_view checked(std::string_view input, storage_limits storage) {
    if (input.size() > UINT32_MAX || input.size() > storage.input_bytes)
      throw std::length_error("JSON input limit");
    return input;
  }
public:
  scalar_document(const scalar_plan& plan, std::string_view input, storage_limits storage = {},
                  std::pmr::memory_resource* upstream = std::pmr::get_default_resource())
      : input_(checked(input, storage)), memory_(storage.index_bytes, upstream), slots_(&memory_) {
    if (!input_.data()) throw std::bad_alloc();
    slots_.resize(plan.size());
    auto root = parser_.iterate(input_).value();
    for (auto entry : root.get_object()) {
      size_t ordinal = plan.ordinal(entry.unescaped_key().value());
      uint64_t bit = uint64_t(1) << ordinal;
      if (present_ & bit) throw std::invalid_argument("Duplicate JSON field");
      present_ |= bit;
      const auto& field = plan.field(ordinal);
      auto value = entry.value().value();
      auto& slot = slots_[ordinal];
      auto lexical = trim(value.raw_json_token());
      slot.lexical = reinterpret_cast<uint8_t*>(const_cast<char*>(lexical.data()));
      slot.lexical_length = lexical.size();
      if (value.type().value() == simdjson::ondemand::json_type::null) {
        if (!value.is_null().value() || !field.nullable)
          throw std::invalid_argument("Unexpected JSON null");
        nulls_ |= bit;
        continue;
      }
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
      }
    }
    if (!root.at_end()) throw std::invalid_argument("Trailing JSON input");
    if ((present_ & plan.required()) != plan.required())
      throw std::invalid_argument("Missing required JSON field");
  }
  scalar_document(const scalar_document&) = delete;
  scalar_document& operator=(const scalar_document&) = delete;
  const scalar_slot* slots() const { return slots_.data(); }
  size_t size() const { return slots_.size(); }
  uint64_t present() const { return present_; }
  uint64_t nulls() const { return nulls_; }
  const scalar_slot& slot(size_t index) const { return slots_.at(index); }
  allocation_stats allocations() const {
    return {input_.size(), input_.size() + simdjson::SIMDJSON_PADDING,
            memory_.current(), memory_.peak(), memory_.allocations(), parser_.capacity()};
  }
};
} // namespace truffledata::json
#endif
