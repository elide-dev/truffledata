#ifndef TRUFFLEDATA_JSON_DECODER_H
#define TRUFFLEDATA_JSON_DECODER_H
#include "json/native/memory.h"
#include <myna/values.hpp>
#include <simdjson.h>
#include <unordered_set>

namespace truffledata::json {

inline std::string_view trim(std::string_view s) {
  while (!s.empty() && (s.back() == ' ' || s.back() == '\n' ||
                        s.back() == '\r' || s.back() == '\t')) s.remove_suffix(1);
  return s;
}

// RFC 8259 grammar, deliberately independent of machine numeric range.
inline bool number(std::string_view s) {
  size_t i = 0;
  auto digit = [&] { return i < s.size() && s[i] >= '0' && s[i] <= '9'; };
  if (i < s.size() && s[i] == '-') ++i;
  if (!digit()) return false;
  if (s[i] == '0') ++i;
  else while (digit()) ++i;
  if (i < s.size() && s[i] == '.') {
    ++i;
    if (!digit()) return false;
    while (digit()) ++i;
  }
  if (i < s.size() && (s[i] == 'e' || s[i] == 'E')) {
    ++i;
    if (i < s.size() && (s[i] == '+' || s[i] == '-')) ++i;
    if (!digit()) return false;
    while (digit()) ++i;
  }
  return i == s.size();
}

// Compatibility implementation. The input and parser are retained together:
// decoded strings may point into parser scratch, never assumed input-backed.
// One instance is immutable after construction and must not be reused to parse.
class document final {
  simdjson::padded_string input_;
  simdjson::ondemand::parser parser_;
  index_memory memory_;
  myna::value_builder builder_;
  uint32_t input_region_;
  uint64_t count_ = 0;
  myna::value_limits limits_;
  std::pmr::vector<std::string_view> lexical_;

  void lexical(std::string_view raw = {}) {
    if (count_ >= limits_.nodes) throw std::length_error("JSON node limit");
    ++count_;
    lexical_.push_back(trim(raw));
  }
  void text(std::string_view decoded, std::string_view raw) {
    lexical(raw);
    if (decoded.size() <= 16) builder_.string(decoded);
    else {
      auto region = builder_.region(decoded);
      builder_.string_ref(region, 0, static_cast<uint32_t>(decoded.size()));
    }
  }
  template<class Value> void visit(Value& value, uint32_t depth) {
    if (depth > limits_.depth) throw std::length_error("JSON depth limit");
    using simdjson::ondemand::json_type;
    switch (value.type().value()) {
      case json_type::unknown:
        throw std::invalid_argument("unknown JSON token");
      case json_type::object: {
        lexical();
        builder_.begin_object();
        std::pmr::unordered_set<std::string_view> keys(&memory_);
        for (auto entry : value.get_object()) {
          auto raw = entry.key_raw_json_token().value();
          auto key = entry.unescaped_key().value();
          if (!keys.insert(key).second) throw std::invalid_argument("duplicate JSON key");
          text(key, raw);
          auto child = entry.value().value();
          visit(child, depth + 1);
        }
        builder_.end();
        break;
      }
      case json_type::array:
        lexical();
        builder_.begin_array();
        for (auto entry : value.get_array()) {
          auto child = entry.value();
          visit(child, depth + 1);
        }
        builder_.end();
        break;
      case json_type::string: {
        std::string_view raw = value.raw_json_token();
        text(value.get_string().value(), raw);
        break;
      }
      case json_type::number: {
        std::string_view raw = value.raw_json_token();
        raw = trim(raw);
        if (!number(raw)) throw std::invalid_argument("invalid JSON number");
        lexical(raw);
        builder_.validated_number_ref(input_region_,
            static_cast<uint32_t>(raw.data() - input_.data()),
            static_cast<uint32_t>(raw.size()));
        break;
      }
      case json_type::boolean:
        lexical();
        builder_.boolean(value.get_bool().value());
        break;
      case json_type::null:
        lexical();
        if (!value.is_null().value()) throw std::invalid_argument("invalid JSON null");
        builder_.null();
        break;
    }
  }
public:
  explicit document(std::string_view input, myna::value_limits limits = {},
                    storage_limits storage = {},
                    std::pmr::memory_resource* upstream = std::pmr::get_default_resource())
      : input_(checked(input, storage)), memory_(storage.index_bytes, upstream),
        builder_(&memory_, limits),
        input_region_(builder_.region(std::string_view(input_.data(), input_.size()))),
        limits_(limits), lexical_(&memory_) {
    if (!input_.data()) throw std::bad_alloc();
    auto root = parser_.iterate(input_).value();
    visit(root, 0);
    // Numeric tokens are checked lexically, not through a lossy numeric getter.
    // raw_json consumes scalar roots; at_end rejects a second root/trailing tokens.
    if (root.is_scalar().value()) (void)root.raw_json().value();
    if (!root.at_end()) throw std::invalid_argument("trailing JSON input");
    (void)builder_.view();
  }
  document(const document&) = delete;
  document& operator=(const document&) = delete;
  myna_value_view view() const { return builder_.view(); }
  const std::pmr::vector<std::string_view>& lexical_tokens() const { return lexical_; }
  allocation_stats allocations() const {
    return {input_.size(), input_.size() + simdjson::SIMDJSON_PADDING,
            memory_.current(), memory_.peak(), memory_.allocations(), parser_.capacity()};
  }
private:
  static std::string_view checked(std::string_view input, storage_limits storage) {
    if (input.size() > UINT32_MAX || input.size() > storage.input_bytes) throw std::length_error("JSON input limit");
    return input;
  }
};
} // namespace truffledata::json
#endif
