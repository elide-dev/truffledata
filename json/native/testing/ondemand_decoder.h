#ifndef TRUFFLEDATA_JSON_TESTING_ONDEMAND_DECODER_H
#define TRUFFLEDATA_JSON_TESTING_ONDEMAND_DECODER_H
// Retired generic producer: test-only differential and timing oracle.
#include "json/native/decoder_common.h"
namespace truffledata::json::testing {
// The lean On-Demand visitor. Writer is tape::ctape_writer (a retained or borrowed document) or
// null_writer (validate-only). One instance decodes one document.
template<class Writer> class ondemand_decoder final {
  Writer writer_;
  const char* base_ = nullptr;
  myna::value_limits limits_;

  [[gnu::always_inline]] tape::ctape::span of(std::string_view token) const {
    return {static_cast<uint32_t>(token.data() - base_), static_cast<uint32_t>(token.size())};
  }
  template<class Value> void visit(Value& value, uint32_t depth) {
    if (depth > limits_.depth) throw std::length_error("JSON depth limit");
    using simdjson::ondemand::json_type;
    switch (value.type().value()) {
      case json_type::unknown:
        throw std::invalid_argument("unknown JSON token");
      case json_type::object: {
        std::string_view open = value.raw_json_token();
        size_t index = writer_.begin(tape::ctape::kind_object, depth, static_cast<uint32_t>(open.data() - base_));
        uint64_t members = 0;
        for (auto entry : value.get_object()) {
          std::string_view raw = json::trim(entry.key_raw_json_token().value());
          std::string_view body = raw.substr(1, raw.size() - 2);
          body_scan s = scan(body);
          std::string_view key = s.clean ? body : entry.unescaped_key().value();
          writer_.key_text(index, reinterpret_cast<const uint8_t*>(key.data()), uint32_t(key.size()), s.clean);
          if (s.clean) writer_.string_ref(of(body), s.ascii, true);
          else writer_.string(key, of(raw), json::ascii(key), true);
          auto child = entry.value().value();
          visit(child, depth + 1);
          ++members;
        }
        writer_.end(index, members);
        break;
      }
      case json_type::array: {
        std::string_view open = value.raw_json_token();
        size_t index = writer_.begin(tape::ctape::kind_array, depth, static_cast<uint32_t>(open.data() - base_));
        uint64_t elements = 0;
        for (auto entry : value.get_array()) {
          auto child = entry.value();
          visit(child, depth + 1);
          ++elements;
        }
        writer_.end(index, elements);
        break;
      }
      case json_type::string: {
        std::string_view raw = json::trim(value.raw_json_token());
        std::string_view body = raw.substr(1, raw.size() - 2);
        body_scan s = scan(body);
        if (s.clean) {
          writer_.string_ref(of(body), s.ascii, false);
        } else {
          std::string_view decoded = value.get_string().value();
          writer_.string(decoded, of(raw), json::ascii(decoded), false);
        }
        break;
      }
      case json_type::number: {
        std::string_view raw = json::trim(value.raw_json_token());
        // get_number() succeeding is simdjson's full grammar validation; the RFC 8259 check runs
        // only for tokens it does not type (overflowing doubles, big integers) and for errors.
        auto parsed = value.get_number();
        if (!parsed.error()) {
          using simdjson::ondemand::number_type;
          auto n = parsed.value();
          switch (n.get_number_type()) {
            case number_type::signed_integer:
              // simdjson folds -0 into integer zero; JSON.parse-style consumers need negative zero.
              if (n.get_int64() == 0 && raw[0] == '-') writer_.floating(-0.0, of(raw));
              else writer_.integer(n.get_int64(), of(raw));
              return;
            case number_type::unsigned_integer:
              writer_.unsigned_integer(n.get_uint64(), of(raw));
              return;
            case number_type::floating_point_number:
              if (std::isfinite(n.get_double())) {
                writer_.floating(n.get_double(), of(raw));
                return;
              }
              break;
            default: break;
          }
        }
        if (!number(raw)) throw std::invalid_argument("invalid JSON number");
        writer_.decimal_ref(of(raw));
        break;
      }
      case json_type::boolean:
        writer_.boolean(value.get_bool().value());
        break;
      case json_type::null:
        if (!value.is_null().value()) throw std::invalid_argument("invalid JSON null");
        writer_.null();
        break;
    }
  }

 public:
  ondemand_decoder(std::pmr::memory_resource* memory, myna::value_limits limits)
      : writer_(memory, limits), limits_(limits) {}
  void shapes(tape::key_shape_cache* cache) { writer_.shapes(cache); }
  ondemand_decoder(const ondemand_decoder&) = delete;
  ondemand_decoder& operator=(const ondemand_decoder&) = delete;

  // Presizes storage and registers `input` (the retained, padded input). `walked`, when given, is
  // where the parser reads the same bytes (a stream's carry-over buffer, stream.h): offsets of
  // referenced text and spans are taken relative to it, so they index `input`.
  void start(std::string_view input, const decode_hint& hint, const char* walked = nullptr) {
    writer_.input(input);
    writer_.reserve(hint.words, hint.pool, hint.entries);
    base_ = walked ? walked : input.data();
  }
  // Walks one document; trailing input, a second root or any invalid value throws.
  void decode(simdjson::ondemand::document& root) {
    visit(root, 0);
    // raw_json consumes scalar roots; at_end rejects a second root/trailing tokens.
    if (root.is_scalar().value()) (void)root.raw_json().value();
    if (!root.at_end()) throw std::invalid_argument("trailing JSON input");
  }
  // Walks one document of an iterate_many stream. What follows it is the next document (or the
  // stream's truncated tail), which the stream judges, so there is no trailing-input check.
  void decode(simdjson::ondemand::document_reference& root) {
    visit(root, 0);
    if (root.is_scalar().value()) (void)root.raw_json().value();
  }
  // The published descriptor (compact writer only).
  td_tape_view view() const { return writer_.view(); }
  const Writer& writer() const { return writer_; }
  Writer& writer() { return writer_; }
};

}
#endif
