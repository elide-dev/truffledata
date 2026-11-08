#ifndef TRUFFLEDATA_JSON_DECODER_H
#define TRUFFLEDATA_JSON_DECODER_H
// Structural producer for one-shot documents, with the On-Demand walk retained for stream
// migration and the differential oracle. Both emit the same checked compact tape.
#include "json/native/parser_pool.h"
#include "json/native/decoder_common.h"
#include "json/native/stage2.h"
#include "tape/native/arena.h"
#include "tape/native/ctape.h"
#include "tape/native/index_memory.h"
#include <myna/values.hpp>
#include <simdjson.h>
#include <algorithm>
#include <cmath>
#include <cstring>
#include <type_traits>

namespace truffledata::json {

// The lean On-Demand visitor. Writer is tape::ctape_writer (a retained or borrowed document) or
// null_writer (validate-only). One instance decodes one document.
template<class Writer> class tape_decoder final {
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
  tape_decoder(std::pmr::memory_resource* memory, myna::value_limits limits)
      : writer_(memory, limits), limits_(limits) {}
  void shapes(tape::key_shape_cache* cache) { writer_.shapes(cache); }
  tape_decoder(const tape_decoder&) = delete;
  tape_decoder& operator=(const tape_decoder&) = delete;

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

// A one-shot compact document, used by td_json_decode, tests, and benchmarks. The
// input is retained; the parser is pooled and released before construction completes. Escaped
// text is copied into the pool, so parser scratch never outlives the lease. Words, pool and
// directories live in one block arena charged to index_memory. Immutable after construction.
class document final {
  simdjson::padded_string owned_;
  // Either owned_ or caller memory adopted with sufficient padding; never copied twice.
  simdjson::padded_string_view input_;
  tape::index_memory memory_;
  tape::block_arena arena_;
  tape::structural_writer writer_;
  td_tape_view view_{};
  size_t parser_capacity_ = 0;

  struct adopted_tag {};
  document(adopted_tag, simdjson::padded_string owned, simdjson::padded_string_view input,
           myna::value_limits limits, tape::storage_limits storage, std::pmr::memory_resource* upstream)
      : owned_(std::move(owned)),
        input_(owned_.data() ? simdjson::padded_string_view(owned_.data(), owned_.size(), owned_.size() + simdjson::SIMDJSON_PADDING) : input),
        memory_(storage.index_bytes, upstream),
        arena_(&memory_),
        writer_(&arena_, limits) {
    if (!input_.data()) throw std::bad_alloc();
    decode_hint hint = hint_for(input_.size(), limits, storage);
    arena_.hint(hint.block);
    auto lease = basic_parser_pool<stage1_index>::acquire();
    auto& parser = lease.get();
    parser.run(reinterpret_cast<const uint8_t*>(input_.data()), input_.size());
    writer_.input({input_.data(), input_.size()});
    writer_.reserve_structural(parser.size(), input_.size());
    tape::key_shape_cache shapes;
    writer_.shapes(&shapes);
    stage2_walk<tape::structural_writer> walk(reinterpret_cast<const uint8_t*>(input_.data()), input_.size(),
                                       parser.indexes(), parser.size(), writer_);
    walk.run();
    writer_.shapes(nullptr);
    parser_capacity_ = parser.capacity();
    view_ = writer_.view();
  }
  static std::string_view checked(std::string_view input, tape::storage_limits storage) {
    if (input.size() > UINT32_MAX || input.size() > storage.input_bytes) throw std::length_error("JSON input limit");
    return input;
  }

 public:
  explicit document(std::string_view input, myna::value_limits limits = {}, tape::storage_limits storage = {},
                    std::pmr::memory_resource* upstream = std::pmr::get_default_resource())
      : document(adopted_tag{}, simdjson::padded_string(checked(input, storage)), simdjson::padded_string_view(),
                 limits, storage, upstream) {}
  // Adopt caller memory when it carries SIMDJSON_PADDING readable bytes past `length`; otherwise
  // copy once. The caller keeps adopted memory valid for the document's life.
  document(const char* data, size_t length, size_t capacity, myna::value_limits limits = {},
           tape::storage_limits storage = {}, std::pmr::memory_resource* upstream = std::pmr::get_default_resource())
      : document(adopted_tag{},
                 capacity >= length + simdjson::SIMDJSON_PADDING
                     ? simdjson::padded_string()
                     : simdjson::padded_string(checked(std::string_view(data, length), storage)),
                 capacity >= length + simdjson::SIMDJSON_PADDING
                     ? simdjson::padded_string_view(data, checked(std::string_view(data, length), storage).size(), capacity)
                     : simdjson::padded_string_view(),
                 limits, storage, upstream) {}
  document(const document&) = delete;
  document& operator=(const document&) = delete;
  bool adopted() const { return !owned_.data(); }
  const td_tape_view& view() const { return view_; }
  uint64_t value_count() const { return writer_.value_count(); }
  // Index accounting counts arena blocks and large allocations, not individual words.
  tape::allocation_stats allocations() const {
    return {adopted() ? 0 : input_.size(),
            adopted() ? input_.capacity() : input_.size() + simdjson::SIMDJSON_PADDING,
            memory_.current(), memory_.peak(), memory_.allocations(), parser_capacity_};
  }
};

} // namespace truffledata::json
#endif
