#ifndef TRUFFLEDATA_JSON_DECODER_H
#define TRUFFLEDATA_JSON_DECODER_H
// Structural producer for generic documents. The previous generic walk is test-only;
// typed plans use a structural slot writer, with On-Demand retained for failure diagnostics.
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
