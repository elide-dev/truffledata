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
#include <type_traits>

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
  struct record_keys {
    std::vector<tape::key_shape_cache::key> keys;
    uint8_t first_byte[256];
  };
  std::vector<record_keys> children_;
  static void prepare_keys(const record_plan& record,
                           std::vector<tape::key_shape_cache::key>& keys, uint8_t* first_byte) {
    std::memset(first_byte, 0xff, 256);
    keys.resize(record.size());
    for (size_t i = 0; i < keys.size(); ++i) {
      const auto& name = record.field(i).name;
      if (name.size() <= 32) keys[i].set(name, 0);
      else keys[i].clean = 0;
      if (keys[i].clean && !name.empty()) {
        auto& bucket = first_byte[static_cast<uint8_t>(name[0])];
        bucket = bucket == 0xff ? static_cast<uint8_t>(i) : 0xfe;
      }
    }
  }
  void prepare() {
    std::memset(first_byte_, 0xff, sizeof first_byte_);
    for (size_t i = 0; i < plan_.size(); ++i) {
      const auto& f = plan_.field(i);
      if (f.repeated || f.kind == scalar_kind::record) scalar_ = false;
    }
    prepare_keys(plan_.root(), keys_, first_byte_);
    children_.resize(plan_.record_count() - 1);
    for (size_t i = 1; i < plan_.record_count(); ++i)
      prepare_keys(plan_.record(i), children_[i - 1].keys, children_[i - 1].first_byte);
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
  const tape::key_shape_cache::key* keys(size_t record) const {
    return record ? children_[record - 1].keys.data() : keys_.data();
  }
  const uint8_t* first_bytes(size_t record) const {
    return record ? children_[record - 1].first_byte : first_byte_;
  }
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
  // Only allocated on a failed nested decode to preserve established error diagnostics.
  simdjson::ondemand::parser parser_;
  const typed_plan* plan_;
  // Escaped strings replace the old owned parser string buffer (separate from the slot-index budget).
  // Reserve once before publication so every text pointer remains stable after the parser lease ends.
  std::unique_ptr<uint8_t[]> pool_;
  size_t pool_size_ = 0;
  size_t parser_capacity_ = 0;

  struct flat_state {
    size_t field = 0, expected = 0;
    bool opened = false;
  };
  struct nested_state {
    struct frame {
      bool array;
      uint32_t plan, base, entry, field, expected;
      uint64_t present, nulls;
      const record_plan* schema;
      const scalar_field* selected;
      const tape::key_shape_cache::key* keys;
      const uint8_t* first_bytes;
      size_t slot, parent_slot, first, count, used;
    };
    // A valid plan has <=32 record levels, with at most one array per record hop.
    frame frames[2 * max_plan_depth];
    size_t depth = 0;
  };
  template<bool Nested> class typed_writer {
    typed_document& document_;
    const typed_plan& plan_;
    std::conditional_t<Nested, nested_state, flat_state> state_;
    const uint32_t* indexes_;
    size_t index_count_;
    uint32_t number_kind_ = 0;
    uint64_t number_bits_ = 0;
    using span = tape::ctape::span;
    const uint8_t* input() const { return reinterpret_cast<const uint8_t*>(document_.input_.data()); }
    const scalar_field& field() const {
      if constexpr (Nested) {
        if (!state_.depth) throw std::invalid_argument("Expected JSON object");
        const auto& frame = state_.frames[state_.depth - 1];
        if (!frame.selected) throw std::invalid_argument("Expected JSON field");
        return *frame.selected;
      } else {
        if (!state_.opened) throw std::invalid_argument("Expected JSON object");
        return plan_.field(state_.field);
      }
    }
    const scalar_field& scalar_field_value() const {
      const auto& f = field();
      if constexpr (Nested) {
        if (f.repeated && !state_.frames[state_.depth - 1].array)
          throw std::invalid_argument("Expected JSON array");
      }
      return f;
    }
    size_t slot_index() const {
      if constexpr (Nested) {
        const auto& frame = state_.frames[state_.depth - 1];
        if (frame.array) {
          if (frame.used >= frame.count) throw std::invalid_argument("JSON array changed length");
        }
        return frame.slot;
      } else return state_.field;
    }
    scalar_slot& slot() { return document_.slots_[slot_index()]; }
    void complete_scalar() {
      if constexpr (Nested) {
        auto& frame = state_.frames[state_.depth - 1];
        if (frame.array) { ++frame.used; ++frame.slot; }
      }
    }
    void lexical(span token) {
      slot().lexical = const_cast<uint8_t*>(input()) + token.offset;
      slot().lexical_length = token.length;
    }
    void select(size_t ordinal) {
      uint64_t bit = uint64_t(1) << ordinal;
      if constexpr (Nested) {
        auto& frame = state_.frames[state_.depth - 1];
        if (frame.present & bit) throw std::invalid_argument("Duplicate JSON field");
        frame.present |= bit; frame.field = ordinal;
        frame.selected = &frame.schema->field(ordinal);
        frame.slot = frame.base + ordinal;
        frame.expected = ordinal + 1 == frame.schema->size() ? 0 : ordinal + 1;
      } else {
        if (document_.root_.present & bit) throw std::invalid_argument("Duplicate JSON field");
        document_.root_.present |= bit;
        state_.field = ordinal;
        state_.expected = (ordinal + 1) % plan_.size();
      }
    }
    // The ABI promises contiguous array entries even when elements allocate descendants.
    // Count direct elements from stage1 indexes before reserving exactly the old arena extent.
    // The normal stage2 walk still validates every byte/token before publication.
    size_t array_count(uint32_t start) const {
      auto at = std::lower_bound(indexes_, indexes_ + index_count_, start);
      if (at == indexes_ + index_count_ || *at != start)
        throw std::logic_error("JSON array structural index");
      ++at;
      if (at == indexes_ + index_count_) stage2_detail::syntax("Unterminated JSON array");
      if (input()[*at] == ']') return 0;
      size_t count = 1, depth = 1;
      for (; at != indexes_ + index_count_; ++at) {
        uint8_t c = input()[*at];
        if (c == '[' || c == '{') ++depth;
        else if (c == ']' || c == '}') {
          if (!--depth) return count;
        } else if (c == ',' && depth == 1) ++count;
      }
      stage2_detail::syntax("Unterminated JSON array");
    }
    void text(const uint8_t* bytes, uint32_t length, span raw) {
      const auto& f = scalar_field_value();
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
      lexical(raw); complete_scalar();
    }
    void floating(double value, span token) {
      const auto& f = field();
      if (!std::isfinite(value)) throw std::invalid_argument("Nonfinite JSON float");
      if (f.float_bits == 32 && double(float(value)) != value)
        throw std::invalid_argument("JSON floating width");
      std::memcpy(&slot().bits, &value, sizeof value); lexical(token); complete_scalar();
    }
   public:
    static constexpr bool retains_values = true;
    typed_writer(typed_document& document, const typed_plan& plan,
                 const uint32_t* indexes, size_t count)
        : document_(document), plan_(plan), indexes_(indexes), index_count_(count) {}
    size_t begin(uint32_t kind, uint32_t depth, uint32_t start) {
      if constexpr (!Nested) {
        if (depth || kind != tape::ctape::kind_object)
          throw std::invalid_argument("Expected JSON scalar record");
        state_.opened = true; return 0;
      } else {
        if (depth >= 2 * max_plan_depth) throw std::invalid_argument("JSON typed container depth");
        // Initialize only the active frame variant; zeroing/copying the whole stack record
        // for every tiny child would cost more than the scalar values it contains.
        auto& fresh = state_.frames[depth];
        fresh.array = kind == tape::ctape::kind_array;
        if (!depth) {
          if (fresh.array) throw std::invalid_argument("Expected JSON object");
          fresh.plan = fresh.base = fresh.entry = 0;
        } else {
          const auto& f = field();
          auto& parent = state_.frames[depth - 1];
          if (fresh.array) {
            if (parent.array || !f.repeated) throw std::invalid_argument("Unexpected JSON array");
            fresh.selected = &f; fresh.parent_slot = slot_index();
            fresh.count = array_count(start); fresh.first = fresh.used = 0;
            if (fresh.count) fresh.first = f.kind == scalar_kind::record
                ? document_.grow_records(fresh.count) : document_.grow_slots(fresh.count);
            fresh.slot = fresh.first;
          } else {
            if (f.kind != scalar_kind::record || (f.repeated && !parent.array))
              throw std::invalid_argument("Unexpected JSON object");
            fresh.plan = f.child;
            if (parent.array) {
              if (parent.used == parent.count) throw std::invalid_argument("JSON array changed length");
              fresh.entry = parent.first + parent.used++;
            } else {
              size_t parent_slot = slot_index();
              fresh.entry = document_.grow_records(1);
              document_.slots_[parent_slot].bits = fresh.entry;
            }
            fresh.base = document_.grow_slots(plan_.record(f.child).size());
          }
        }
        if (!fresh.array) {
          fresh.schema = &plan_.record(fresh.plan);
          fresh.selected = nullptr;
          fresh.keys = plan_.keys(fresh.plan); fresh.first_bytes = plan_.first_bytes(fresh.plan);
          fresh.present = fresh.nulls = 0; fresh.field = fresh.expected = 0;
          fresh.slot = fresh.base;
        }
        state_.depth = depth + 1; return depth;
      }
    }
    void end(size_t index, uint64_t count) {
      if constexpr (!Nested) {
        if ((document_.root_.present & plan_.required()) != plan_.required())
          throw std::invalid_argument("Missing required JSON field");
      } else {
        auto& frame = state_.frames[index];
        if (frame.array) {
          if (frame.used != frame.count || count != frame.count)
            throw std::invalid_argument("JSON array changed length");
          document_.slots_[frame.parent_slot].bits = frame.count
              ? (uint64_t(frame.first) << 32) | frame.count : 0;
        } else {
          uint64_t required = frame.schema->required();
          if ((frame.present & required) != required)
            throw std::invalid_argument("Missing required JSON field");
          record_entry record{frame.present, frame.nulls, frame.base, frame.plan};
          if (!index) document_.root_ = record;
          if (!plan_.flat()) document_.records_[frame.entry] = record;
        }
        state_.depth = index;
      }
    }
    void key_text(size_t, const uint8_t* bytes, uint32_t length, bool = false) {
      const auto& record = [&]() -> const record_plan& {
        if constexpr (Nested) return *state_.frames[state_.depth - 1].schema;
        else return plan_.root();
      }();
      select(record.ordinal({reinterpret_cast<const char*>(bytes), length}));
    }
    uint32_t cached_key_ref(size_t, uint32_t offset, uint32_t available) {
      size_t next;
      if constexpr (Nested) {
        const auto& frame = state_.frames[state_.depth - 1];
        next = frame.expected;
      } else next = state_.expected;
      auto key_at = [&](size_t ordinal) -> const tape::key_shape_cache::key& {
        if constexpr (Nested) return state_.frames[state_.depth - 1].keys[ordinal];
        else return plan_.key(ordinal);
      };
      auto matches = [&](size_t ordinal) {
        const auto& key = key_at(ordinal);
        const uint8_t* at = input() + offset;
        if (!key.clean || available <= key.length || at[key.length] != '"'
            || !key.equals(at, key.length, true)) return false;
        select(ordinal); return true;
      };
      if constexpr (Nested) {
        if (!state_.frames[state_.depth - 1].schema->size()) return UINT32_MAX;
      } else if (!plan_.size()) return UINT32_MAX;
      if (matches(next)) return key_at(next).length;
      uint8_t bucket;
      if constexpr (Nested) bucket = state_.frames[state_.depth - 1].first_bytes[input()[offset]];
      else bucket = plan_.first_byte(input()[offset]);
      if (bucket < 64 && bucket != next && matches(bucket)) return key_at(bucket).length;
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
    void literal_span(span token) {
      (void)field();
      if constexpr (Nested) {
        const auto& frame = state_.frames[state_.depth - 1];
        if (frame.array && frame.selected->kind == scalar_kind::record)
          throw std::invalid_argument("Expected JSON record element");
      }
      lexical(token);
    }
    void boolean(bool value) {
      if (scalar_field_value().kind != scalar_kind::boolean) throw std::invalid_argument("JSON boolean type");
      slot().bits = value; complete_scalar();
    }
    void null() {
      if (!field().nullable) throw std::invalid_argument("Unexpected JSON null");
      if constexpr (Nested) {
        auto& frame = state_.frames[state_.depth - 1];
        if (frame.array) throw std::invalid_argument("Unexpected JSON null element");
        frame.nulls |= uint64_t(1) << frame.field;
      } else document_.root_.nulls |= uint64_t(1) << state_.field;
    }
    void parsed_number(uint32_t kind, uint64_t bits, span) { number_kind_ = kind; number_bits_ = bits; }
    void rollback_number() {}
    void decimal_ref(span token) {
      if (scalar_field_value().kind != scalar_kind::float64) throw std::invalid_argument("JSON numeric range");
      // Unlike the generic tape, a typed F64 field accepts finite integers beyond U64 and underflow.
      floating(simdjson::builtin::numberparsing::parse_double(input() + token.offset).value(), token);
    }
    void commit_number(span token) {
      const auto& f = scalar_field_value(); uint32_t kind = number_kind_; uint64_t bits = number_bits_;
      switch (f.kind) {
        case scalar_kind::int64:
          if (kind == tape::ctape::kind_f64 && bits == (uint64_t(1) << 63) && token.length == 2) {
            kind = tape::ctape::kind_i64; bits = 0;
          }
          if (kind != tape::ctape::kind_i64 || int64_t(bits) < f.minimum || int64_t(bits) > f.maximum)
            throw std::invalid_argument("JSON integer range");
          slot().bits = bits; lexical(token); complete_scalar(); return;
        case scalar_kind::uint64:
          if ((kind != tape::ctape::kind_i64 && kind != tape::ctape::kind_u64)
              || input()[token.offset] == '-' || bits > f.unsigned_maximum)
            throw std::invalid_argument("JSON unsigned range");
          slot().bits = bits; lexical(token); complete_scalar(); return;
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
    if (!plan.flat()) records_.resize(1);
    try {
      auto lease = basic_parser_pool<stage1_index>::acquire();
      auto& parser = lease.get();
      parser.run(reinterpret_cast<const uint8_t*>(input_.data()), input_.size());
      if (plan.scalar()) {
        typed_writer<false> writer(*this, plan, parser.indexes(), parser.size());
        stage2_walk<typed_writer<false>> walk(reinterpret_cast<const uint8_t*>(input_.data()), input_.size(),
                                           parser.indexes(), parser.size(), writer);
        walk.run();
      } else {
        typed_writer<true> writer(*this, plan, parser.indexes(), parser.size());
        stage2_walk<typed_writer<true>> walk(reinterpret_cast<const uint8_t*>(input_.data()), input_.size(),
                                          parser.indexes(), parser.size(), writer);
        walk.run();
      }
      parser_capacity_ = parser.capacity();
    } catch (const std::bad_alloc&) {
      throw;
    } catch (...) {
      if (plan.scalar()) throw;
      // The structural pass can encounter a schema/type error or a speculative array bound
      // before On-Demand encounters a malformed descendant. Preserve the established C ABI
      // diagnostic on failures without adding a second pass to successful decodes. Release
      // partial arenas first so the replay observes the original per-document index budget.
      { std::pmr::vector<scalar_slot> empty(&memory_); slots_.swap(empty); }
      { std::pmr::vector<record_entry> empty(&memory_); records_.swap(empty); }
      pool_.reset(); pool_size_ = 0; root_ = {};
      slots_.resize(root.size());
      if (!plan.flat()) records_.resize(1);
      auto document = parser_.iterate(input_).value();
      (void)members(document.get_object().value(), root, 0);
      if (!document.at_end()) throw std::invalid_argument("Trailing JSON input");
      // Never publish a document that failed full structural validation, even if an oracle
      // implementation were to accept it. Differential tests keep such cases visible.
      throw;
    }
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
