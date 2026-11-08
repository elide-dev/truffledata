#ifndef TRUFFLEDATA_JSON_TYPED_H
#define TRUFFLEDATA_JSON_TYPED_H
#include "json/native/decoder.h"
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

namespace truffledata::json {
using tape::scalar_kind;
using tape::scalar_field;
using tape::record_plan;
// The schema itself supplies immutable template positions. No result or caller buffer is retained.
class typed_plan final {
  tape::typed_plan plan_;
  std::vector<tape::key_shape_cache::key> keys_;
  bool scalar_ = true;
  uint8_t first_byte_[256];
  void prepare() {
    std::memset(first_byte_, 0xff, sizeof first_byte_);
    for (size_t i = 0; i < plan_.size(); ++i) {
      const auto& f = plan_.field(i);
      if (f.repeated || f.kind == scalar_kind::record) scalar_ = false;
    }
    if (!scalar_) return;
    keys_.resize(plan_.size());
    for (size_t i = 0; i < keys_.size(); ++i) {
      const auto& name = plan_.field(i).name;
      if (name.size() <= 32) keys_[i].set(name, 0);
      else keys_[i].clean = 0;
      if (keys_[i].clean && !name.empty()) {
        auto& bucket = first_byte_[static_cast<uint8_t>(name[0])];
        bucket = bucket == 0xff ? static_cast<uint8_t>(i) : 0xfe;
      }
    }
  }
 public:
  explicit typed_plan(std::vector<std::vector<scalar_field>> records) : plan_(std::move(records)) { prepare(); }
  explicit typed_plan(std::vector<scalar_field> fields) : plan_(std::move(fields)) { prepare(); }
  typed_plan(const typed_plan&) = delete;
  typed_plan& operator=(const typed_plan&) = delete;
  size_t record_count() const { return plan_.record_count(); }
  const record_plan& record(size_t i) const { return plan_.record(i); }
  const record_plan& root() const { return plan_.root(); }
  bool flat() const { return plan_.flat(); }
  size_t size() const { return plan_.size(); }
  uint64_t required() const { return plan_.required(); }
  const scalar_field& field(size_t i) const { return plan_.field(i); }
  bool scalar() const { return scalar_; }
  const tape::key_shape_cache::key& key(size_t i) const { return keys_[i]; }
  uint8_t first_byte(uint8_t byte) const { return first_byte_[byte]; }
};
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
class typed_document final : private tape::typed_slots<scalar_slot, record_entry> {
  simdjson::padded_string input_;
  simdjson::ondemand::parser parser_;
  const typed_plan* plan_;
  // Escaped strings replace the old owned parser string buffer (separate from the slot-index budget).
  // Reserve once before publication so every text pointer remains stable after the parser lease ends.
  std::unique_ptr<uint8_t[]> pool_;
  size_t pool_size_ = 0;
  size_t parser_capacity_ = 0;

  class flat_writer {
    typed_document& document_;
    const typed_plan& plan_;
    size_t field_ = 0, expected_ = 0;
    bool opened_ = false;
    uint32_t number_kind_ = 0;
    uint64_t number_bits_ = 0;
    using span = tape::ctape::span;
    const uint8_t* input() const { return reinterpret_cast<const uint8_t*>(document_.input_.data()); }
    const scalar_field& field() const {
      if (!opened_) throw std::invalid_argument("Expected JSON object");
      return plan_.field(field_);
    }
    scalar_slot& slot() { return document_.slots_[field_]; }
    void lexical(span token) {
      slot().lexical = const_cast<uint8_t*>(input()) + token.offset;
      slot().lexical_length = token.length;
    }
    void select(size_t ordinal) {
      uint64_t bit = uint64_t(1) << ordinal;
      if (document_.root_.present & bit) throw std::invalid_argument("Duplicate JSON field");
      document_.root_.present |= bit;
      field_ = ordinal;
      expected_ = (ordinal + 1) % plan_.size();
    }
    void text(const uint8_t* bytes, uint32_t length, span raw) {
      const auto& f = field();
      if (f.kind != scalar_kind::string) throw std::invalid_argument("JSON string type");
      if (length > f.max_utf8_bytes) throw std::length_error("JSON string byte limit");
      if (f.length_unit) {
        uint64_t n = f.length_unit == 1
            ? simdutf::utf16_length_from_utf8(reinterpret_cast<const char*>(bytes), length)
            : simdutf::count_utf8(reinterpret_cast<const char*>(bytes), length);
        if (n < f.minimum_length || n > f.maximum_length)
          throw std::invalid_argument("JSON string length contract");
      }
      slot().text = const_cast<uint8_t*>(bytes); slot().text_length = length;
      lexical(raw);
    }
    void floating(double value, span token) {
      const auto& f = field();
      if (!std::isfinite(value)) throw std::invalid_argument("Nonfinite JSON float");
      if (f.float_bits == 32 && double(float(value)) != value)
        throw std::invalid_argument("JSON floating width");
      std::memcpy(&slot().bits, &value, sizeof value); lexical(token);
    }
   public:
    static constexpr bool retains_values = true;
    flat_writer(typed_document& document, const typed_plan& plan) : document_(document), plan_(plan) {}
    size_t begin(uint32_t kind, uint32_t depth, uint32_t) {
      if (depth || kind != tape::ctape::kind_object) throw std::invalid_argument("Expected JSON scalar record");
      opened_ = true; return 0;
    }
    void end(size_t, uint64_t) {
      if ((document_.root_.present & plan_.required()) != plan_.required())
        throw std::invalid_argument("Missing required JSON field");
    }
    void key_text(size_t, const uint8_t* bytes, uint32_t length, bool = false) {
      select(plan_.root().ordinal({reinterpret_cast<const char*>(bytes), length}));
    }
    uint32_t cached_key_ref(size_t, uint32_t offset, uint32_t available) {
      auto matches = [&](size_t ordinal) {
        const auto& key = plan_.key(ordinal);
        const uint8_t* at = input() + offset;
        if (!key.clean || available <= key.length || at[key.length] != '"'
            || !key.equals(at, key.length, true)) return false;
        select(ordinal); return true;
      };
      if (!plan_.size()) return UINT32_MAX;
      size_t next = expected_;
      if (matches(next)) return plan_.key(next).length;
      // Unique first-byte schema buckets handle reordered fields without an unordered-map lookup.
      // Collisions and long/escaped names retain the ordinary exact decoded-name lookup.
      uint8_t bucket = plan_.first_byte(input()[offset]);
      if (bucket < 64 && bucket != next && matches(bucket)) return plan_.key(bucket).length;
      return UINT32_MAX;
    }
    void string_ref(span raw, bool, bool key) {
      if (!key) text(input() + raw.offset, raw.length, {raw.offset - 1, raw.length + 2});
    }
    void string_owned(uint32_t offset, uint32_t length, span raw, bool, bool key) {
      if (!key) text(pool_data() + offset, length, raw);
    }
    uint8_t* pool_reserve(size_t length) {
      size_t capacity = document_.input_.size() + simdjson::SIMDJSON_PADDING;
      if (!document_.pool_) document_.pool_.reset(new uint8_t[capacity]);
      if (length > capacity - document_.pool_size_) throw std::length_error("JSON decoded text bound");
      return document_.pool_.get() + document_.pool_size_;
    }
    // Grow without value-initializing over the bytes already produced by stage2.
    void pool_commit(size_t length) { document_.pool_size_ += length; }
    size_t pool_size() const { return document_.pool_size_; }
    const uint8_t* pool_data() const { return document_.pool_.get(); }
    void literal_span(span token) { (void)field(); lexical(token); }
    void boolean(bool value) {
      if (field().kind != scalar_kind::boolean) throw std::invalid_argument("JSON boolean type");
      slot().bits = value;
    }
    void null() {
      if (!field().nullable) throw std::invalid_argument("Unexpected JSON null");
      document_.root_.nulls |= uint64_t(1) << field_;
    }
    void parsed_number(uint32_t kind, uint64_t bits, span) { number_kind_ = kind; number_bits_ = bits; }
    void rollback_number() {}
    void decimal_ref(span token) {
      if (field().kind != scalar_kind::float64) throw std::invalid_argument("JSON numeric range");
      // Unlike the generic tape, a typed F64 field accepts finite integers beyond U64 and underflow.
      floating(simdjson::builtin::numberparsing::parse_double(input() + token.offset).value(), token);
    }
    void commit_number(span token) {
      const auto& f = field(); uint32_t kind = number_kind_; uint64_t bits = number_bits_;
      switch (f.kind) {
        case scalar_kind::int64:
          if (kind == tape::ctape::kind_f64 && bits == (uint64_t(1) << 63) && token.length == 2) {
            kind = tape::ctape::kind_i64; bits = 0;
          }
          if (kind != tape::ctape::kind_i64 || int64_t(bits) < f.minimum || int64_t(bits) > f.maximum)
            throw std::invalid_argument("JSON integer range");
          slot().bits = bits; lexical(token); return;
        case scalar_kind::uint64:
          if ((kind != tape::ctape::kind_i64 && kind != tape::ctape::kind_u64)
              || input()[token.offset] == '-' || bits > f.unsigned_maximum)
            throw std::invalid_argument("JSON unsigned range");
          slot().bits = bits; lexical(token); return;
        case scalar_kind::float64: {
          double value;
          if (kind == tape::ctape::kind_i64) value = double(int64_t(bits));
          else if (kind == tape::ctape::kind_u64) value = double(bits);
          else std::memcpy(&value, &bits, sizeof value);
          floating(value, token); return;
        }
        default: throw std::invalid_argument("JSON number type");
      }
    }
  };

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
  typed_document(const typed_plan& plan, std::string_view input, tape::storage_limits storage = {},
                 std::pmr::memory_resource* upstream = std::pmr::get_default_resource())
      : tape::typed_slots<scalar_slot, record_entry>(storage.index_bytes, upstream),
        input_(checked(input, storage)), plan_(&plan) {
    if (!input_.data()) throw std::bad_alloc();
    const auto& root = plan.root();
    slots_.resize(root.size());
    if (plan.scalar()) {
      auto lease = basic_parser_pool<stage1_index>::acquire();
      auto& parser = lease.get();
      parser.run(reinterpret_cast<const uint8_t*>(input_.data()), input_.size());
      flat_writer writer(*this, plan);
      stage2_walk<flat_writer> walk(reinterpret_cast<const uint8_t*>(input_.data()), input_.size(),
                                   parser.indexes(), parser.size(), writer);
      walk.run(); parser_capacity_ = parser.capacity(); plan_ = nullptr;
      return;
    }
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
            memory_.current(), memory_.peak(), memory_.allocations(), parser_capacity_ ? parser_capacity_ : parser_.capacity()};
  }
};
} // namespace truffledata::json
#endif
