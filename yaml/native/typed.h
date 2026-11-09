#ifndef TRUFFLEDATA_YAML_TYPED_H
#define TRUFFLEDATA_YAML_TYPED_H
#include "tape/native/tape_typed.h"
#include "yaml/native/session.h"

namespace truffledata::yaml {
using tape::scalar_field;
using tape::scalar_kind;
using tape::typed_plan;

// Identical fixed-slot contract to JSON. The public seam verifies offsets before publication.
struct scalar_slot {
  uint64_t bits = 0;
  uint8_t* text = nullptr;
  uint64_t text_length = 0;
  uint8_t* lexical = nullptr;
  uint64_t lexical_length = 0;
};
struct record_entry {
  uint64_t present = 0, nulls = 0;
  uint32_t slots = 0, plan = 0;
};

class typed_source final {
  session::document_view document_;
  schema schema_;

  std::string_view text(uint64_t index) const { return ct::text(document_.view, index); }
  uint32_t scalar_kind_at(uint64_t index) const {
    switch (ct::kind(document_.view.words[index])) {
      case ct::kind_null: return MYNA_VALUE_NULL;
      case ct::kind_false: case ct::kind_true: return MYNA_VALUE_BOOL;
      case ct::kind_i64: return MYNA_VALUE_I64;
      case ct::kind_u64: return MYNA_VALUE_U64;
      case ct::kind_f64: return MYNA_VALUE_F64;
      case ct::kind_decimal: return MYNA_VALUE_DECIMAL;
      case ct::kind_string: return MYNA_VALUE_STRING;
      default: throw std::invalid_argument("typed YAML scalar kind");
    }
  }
  std::string_view token(uint64_t index) const {
    uint64_t span = lexical_span(document_.view, index);
    return {document_.source + (span >> 32), uint32_t(span)};
  }

 public:
  typed_source(session::document_view document, schema mode, bool /*honor_version*/)
      : document_(document), schema_((document.flags & doc_schema_1_1) ? schema::yaml11 : mode == schema::yaml11 ? schema::core : mode) {}
  uint64_t resolve(uint64_t index) const {
    // Native tapes contain only backward references to completed subtrees. Keep the bounds here
    // so the walker is safe even if a future producer accidentally publishes a malformed ref.
    while (ct::kind(document_.view.words[index]) == ct::kind_ref) {
      uint64_t target = ct::low(document_.view.words[index]);
      if (target >= index) throw std::invalid_argument("invalid typed YAML alias");
      index = target;
    }
    return index;
  }
  tape::typed_kind kind(uint64_t index) const {
    switch (ct::kind(document_.view.words[index])) {
      case ct::kind_null: return tape::typed_kind::null;
      case ct::kind_array: return tape::typed_kind::array;
      case ct::kind_object: return tape::typed_kind::object;
      default: return tape::typed_kind::scalar;
    }
  }
  size_t count(uint64_t index) const {
    size_t count = ct::field(document_.view.words[index]);
    if (count != ct::max_length) return count;
    bool object = kind(index) == tape::typed_kind::object;
    count = 0;
    for (uint64_t at = first(index), end = next(index); at < end; ++count)
      at = next(object ? next(at) : at);
    return count;
  }
  uint64_t first(uint64_t index) const { return index + 2; }
  uint64_t next(uint64_t index) const { return ct::after(index, document_.view.words[index]); }
  std::string_view key(uint64_t index) const { return text(index); }
  void lexical(uint64_t index, scalar_slot& slot) const {
    auto bytes = token(index);
    slot.lexical = reinterpret_cast<uint8_t*>(const_cast<char*>(bytes.data()));
    slot.lexical_length = bytes.size();
  }
  void scalar(uint64_t index, const scalar_field& field, scalar_slot& slot,
              std::pmr::memory_resource* scratch) const {
    lexical(index, slot);
    auto style = scalar_style(document_.meta[index] & meta_style_mask);
    uint32_t kind = scalar_kind_at(index);
    if (field.kind == scalar_kind::string) {
      // Strings keep the decoded scalar text. Plain numeric scalars have no text node, so use
      // their original token; this preserves 1.0, hexadecimal notation and separators exactly.
      auto value = kind == MYNA_VALUE_STRING ? text(index) : token(index);
      if (value.size() > field.max_utf8_bytes) throw std::length_error("YAML string byte limit");
      if (field.length_unit) {
        uint64_t length = field.length_unit == 1
            ? simdutf::utf16_length_from_utf8(value.data(), value.size())
            : simdutf::count_utf8(value.data(), value.size());
        if (length < field.minimum_length || length > field.maximum_length)
          throw std::invalid_argument("YAML string length contract");
      }
      slot.text = reinterpret_cast<uint8_t*>(const_cast<char*>(value.data()));
      slot.text_length = value.size();
      return;
    }
    if (style != scalar_style::plain)
      throw std::invalid_argument("quoted YAML scalar requires a string field");
    tag_kind tag;
    switch (field.kind) {
      case scalar_kind::boolean: tag = tag_kind::bool_; break;
      case scalar_kind::int64: case scalar_kind::uint64: tag = tag_kind::int_; break;
      case scalar_kind::float64: tag = tag_kind::float_; break;
      default: throw std::invalid_argument("YAML scalar field kind");
    }
    yaml::scalar value;
    if ((field.kind == scalar_kind::boolean && kind == MYNA_VALUE_BOOL) ||
        ((field.kind == scalar_kind::int64 || field.kind == scalar_kind::uint64) &&
         (kind == MYNA_VALUE_I64 || kind == MYNA_VALUE_U64)) ||
        (field.kind == scalar_kind::float64 && kind == MYNA_VALUE_F64)) {
      // Generic resolution already produced this exact scalar kind. Apply the schema's range
      // and width below without parsing the same spelling twice.
      value.kind = kind;
      value.bits = kind == MYNA_VALUE_BOOL ? ct::kind(document_.view.words[index]) == ct::kind_true
                                           : document_.view.words[index + 1];
    } else {
      value = resolve_tagged(token(index), tag, schema_, scratch);
    }
    if (field.kind == scalar_kind::boolean) { slot.bits = value.bits; return; }
    if ((field.kind == scalar_kind::int64 || field.kind == scalar_kind::uint64) &&
        value.kind == MYNA_VALUE_F64 && value.bits == (uint64_t(1) << 63)) {
      value.kind = MYNA_VALUE_I64;
      value.bits = 0; // An integer contract has no signed zero.
    }
    if (field.kind == scalar_kind::int64) {
      if (value.kind != MYNA_VALUE_I64 || int64_t(value.bits) < field.minimum ||
          int64_t(value.bits) > field.maximum)
        throw std::invalid_argument("YAML integer range");
      slot.bits = value.bits;
      return;
    }
    if (field.kind == scalar_kind::uint64) {
      if ((value.kind != MYNA_VALUE_U64 && value.kind != MYNA_VALUE_I64) ||
          (value.kind == MYNA_VALUE_I64 && int64_t(value.bits) < 0) ||
          value.bits > field.unsigned_maximum)
        throw std::invalid_argument("YAML unsigned range");
      slot.bits = value.bits;
      return;
    }
    if (value.kind != MYNA_VALUE_F64) throw std::invalid_argument("YAML floating range");
    double number;
    std::memcpy(&number, &value.bits, sizeof number);
    if (field.float_bits == 32 && !std::isnan(number) &&
        static_cast<double>(static_cast<float>(number)) != number)
      throw std::invalid_argument("YAML floating width");
    slot.bits = value.bits;
  }
};

class typed_document final {
  session session_;
  typed_source source_;
  tape::tape_typed<scalar_slot, record_entry, typed_source> slots_;

 public:
  typed_document(const typed_plan& plan, std::string_view input, session_options options = {},
                 std::pmr::memory_resource* upstream = std::pmr::get_default_resource())
      : session_(options, upstream),
        source_(session_.decode(reinterpret_cast<const uint8_t*>(input.data()), input.size(),
                                session::retain_source), options.rules.schema, options.rules.honor_version),
        slots_(plan, source_, options.storage.index_bytes - session_.memory().current(),
               options.max_alias_expansion, upstream) {}
  const scalar_slot* slots() const { return slots_.slots(); }
  size_t size() const { return slots_.size(); }
  const scalar_slot& slot(size_t index) const { return slots_.slot(index); }
  const record_entry* records() const { return slots_.records(); }
  size_t record_count() const { return slots_.record_count(); }
  const record_entry& record(size_t index) const { return slots_.record(index); }
  uint64_t present() const { return slots_.present(); }
  uint64_t nulls() const { return slots_.nulls(); }
  tape::allocation_stats allocations() const {
    size_t retained = session_.memory().current();
    const auto& slots = slots_.memory();
    return {session_.input_length(), session_.input_capacity(), retained + slots.current(),
            std::max(session_.memory().peak(), retained + slots.peak()),
            session_.memory().allocations() + slots.allocations(), 0};
  }
};
} // namespace truffledata::yaml
#endif
