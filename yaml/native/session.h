#ifndef TRUFFLEDATA_YAML_SESSION_H
#define TRUFFLEDATA_YAML_SESSION_H
// Thread-confined YAML decode context with the JSON session's semantics (JSON performance plan B1;
// YAML plan D6): one rapidyaml ParseEngine and handler, an input buffer and a block arena reused
// across documents. A decoded document is borrowed until the next call on the session; retain()
// moves the document's arena blocks and input into an independent owner without copying nodes.
//
// rapidyaml's own allocations (its parser stack, tag cache) go through c4::yml::Callbacks into the
// session's counted index_memory, so they are charged to maxIndexBytes and fail like every other
// allocation; its error callbacks throw yaml::error with the line and column.
#include "tape/native/arena.h"
#include "tape/native/index_memory.h"
#include "yaml/native/decoder.h"
#include "yaml/native/errors.h"
#include "yaml/native/input.h"

#include <c4/yml/common.hpp>
#include <c4/yml/parse_engine.def.hpp>

#include <cstddef>
#include <cstdint>
#include <cstring>
#include <memory_resource>
#include <new>

namespace truffledata::yaml {

using tape::index_memory;
using tape::storage_limits;

// Owner of a published YamlResult (retained session documents).
struct result_owner {
  virtual ~result_owner() = default;
  virtual void destroy() noexcept = 0;
};

struct session_options {
  policy rules;
  myna::value_limits values;  // nodes, text bytes, depth (<= 128)
  storage_limits storage;     // input bytes, index bytes
  uint64_t max_alias_expansion = 1000000;  // consumers' budget (export, typed decode)
};

// Storage presizing. The plan's estimate (Task 2): about one node per 6 input bytes for block YAML
// (the 242 KB records fixture holds 31,001 nodes, 7.8 bytes per node), so the hint over-reserves
// slightly and avoids growth. Hints are capped like JSON's and depend only on the configuration.
struct decode_hint {
  size_t nodes;
  size_t bytes;
  size_t block;
};
inline decode_hint hint_for(size_t input, const myna::value_limits& limits, const storage_limits& storage) {
  size_t cap = storage.index_bytes / 72 > 4096 ? storage.index_bytes / 72 : 4096;
  size_t nodes = input / 6 + 8;
  if (nodes > cap) nodes = cap;
  if (nodes > limits.nodes) nodes = static_cast<size_t>(limits.nodes);
  size_t bytes = input / 8 + 64;
  if (bytes > cap * 8) bytes = cap * 8;
  size_t block = 4 * sizeof(myna_value_region) + 256 + (input + 63) / 64 * 8;
  for (size_t part : {nodes * sizeof(myna_value_node), nodes * sizeof(uint64_t), nodes * sizeof(uint32_t),
                      nodes * sizeof(uint32_t), bytes})
    if (part <= tape::block_arena::large_threshold) block += part + alignof(std::max_align_t);
  return {nodes, bytes, block};
}

class session final {
 public:
  // Large inputs are released before the next decode, as in the JSON session.
  static constexpr size_t retained_input_limit = size_t(1) << 20;

  struct document_view {
    myna_value_view view;
    table_view<uint64_t> lexical;
    table_view<uint32_t> hashes;
    table_view<uint32_t> meta;
    const char* source;
    size_t source_length;
    size_t input_length;      // caller bytes
    size_t index_allocations; // upstream allocations made by this decode
    uint32_t flags;
    uint32_t version;
  };

  class retained final : public result_owner {
    std::pmr::memory_resource* upstream_;
    tape::block_arena::storage storage_;
    char* input_;
    size_t input_capacity_;
    size_t bytes_;

   public:
    myna_value_view view;
    retained(std::pmr::memory_resource* upstream, tape::block_arena::storage storage, size_t bytes, char* input,
             size_t input_capacity, myna_value_view view)
        : upstream_(upstream), storage_(std::move(storage)), input_(input), input_capacity_(input_capacity),
          bytes_(bytes), view(view) {}
    size_t bytes() const { return bytes_; }
    size_t input_capacity() const { return input_capacity_; }
    ~retained() override {
      if (input_) upstream_->deallocate(input_, input_capacity_, alignof(std::max_align_t));
    }
    void destroy() noexcept override {
      auto* upstream = upstream_;
      this->~retained();
      upstream->deallocate(this, sizeof(retained), alignof(retained));
    }
  };

  // Decode flags.
  static constexpr uint32_t want_utf16_text = 1u << 0;  // reserved (text arena, plan Task 5)
  static constexpr uint32_t retain_source = 1u << 1;

  session(const session_options& options, std::pmr::memory_resource* upstream = std::pmr::get_default_resource())
      : upstream_(upstream),
        options_(options),
        memory_(options.storage.index_bytes, upstream),
        arena_(&memory_),
        handler_(callbacks(&memory_)),
        engine_(&handler_, parser_options()) {
    if (options.values.depth > 128) throw std::invalid_argument("YAML depth limit above 128");
  }
  session(const session&) = delete;
  session& operator=(const session&) = delete;
  ~session() {
    handler_.clear();
    arena_.release_all();
    free_input();
  }

  // Decodes zero or one document. Invalidates the previous borrowed document. On any exception the
  // session holds no document and stays reusable.
  document_view decode(const uint8_t* data, size_t length, uint32_t flags) {
    recycle();
    if (flags & ~retain_source) throw error(status_argument, "unsupported YAML decode flags");
    if (length > storage_limit()) throw limit_error("input bytes");
    size_t before = memory_.allocations();
    try {
      encoding e = detect(data, length);
      size_t capacity = utf8_capacity(e, length);
      if (capacity + 1 > input_capacity_) {
        free_input();
        input_ = static_cast<char*>(upstream_->allocate(capacity + 1, alignof(std::max_align_t)));
        input_capacity_ = capacity + 1;
      }
      size_t text = transcode(e, data, length, input_);
      if (text > UINT32_MAX) throw limit_error("input bytes");
      input_[text] = 0;
      input_length_ = length;
      tape::block_arena& arena = arena_;
      decode_hint hint = hint_for(text, options_.values, options_.storage);
      arena.hint(hint.block);
      size_t words = (text + 63) / 64;
      auto* newlines = static_cast<uint64_t*>(arena.allocate((words ? words : 1) * sizeof(uint64_t), alignof(uint64_t)));
      try {
        validate(std::string_view(input_, text), newlines);
      } catch (error& failure) {
        locate(failure, text);
        throw;
      }
      const char* pristine = nullptr;
      if (flags & retain_source) {
        char* copy = static_cast<char*>(arena.allocate(text ? text : 1, 1));
        if (text) std::memcpy(copy, input_, text);
        pristine = copy;
      }
      policy rules = options_.rules;
      rules.retain_source = pristine != nullptr;
      handler_.start(&arena_, input_, text, newlines, pristine, rules, options_.values, hint.nodes, hint.bytes);
      try {
        engine_.parse_in_place_ev("yaml", c4::substr(input_, text));
      } catch (error& failure) {
        if (!failure.line) failure.at(handler_.line(), handler_.column());
        throw;
      } catch (const std::length_error& limit) {
        // Myna node/text/depth/container limits and the index budget.
        throw limit_error(limit.what()).at(handler_.line(), handler_.column());
      }
      handler_.finish();
      source_ = pristine ? pristine : input_;
      source_length_ = text;
    } catch (...) {
      recycle();
      throw;
    }
    live_ = true;
    return {handler_.view(), handler_.lexical_spans(), handler_.key_hashes(), handler_.meta(), source_,
            source_length_, length, memory_.allocations() - before, handler_.document_flags(), handler_.version()};
  }

  bool live() const { return live_; }
  // Invalidates the borrowed document without freeing: O(1), no allocation, no exception.
  void reset() noexcept { live_ = false; }

  // Moves the live document's blocks and input into a new owner, O(blocks), without copying.
  retained* retain() {
    if (!live_) throw std::logic_error("no YAML session document");
    void* raw = upstream_->allocate(sizeof(retained), alignof(retained));
    tape::block_arena::storage storage;
    myna_value_view view = handler_.view();
    handler_.clear();
    size_t bytes = arena_.detach(storage, upstream_);
    memory_.forget(bytes);
    auto* owner = new (raw) retained(upstream_, std::move(storage), bytes, input_, input_capacity_, view);
    input_ = nullptr;
    input_capacity_ = 0;
    live_ = false;
    return owner;
  }

  // The live document; valid only while live().
  const myna_value_view& view() const { return handler_.view(); }
  table_view<uint64_t> lexical_spans() const { return handler_.lexical_spans(); }
  table_view<uint32_t> key_hashes() const { return handler_.key_hashes(); }
  table_view<uint32_t> meta() const { return handler_.meta(); }
  const char* source() const { return source_; }
  size_t source_length() const { return source_length_; }
  uint32_t document_flags() const { return handler_.document_flags(); }
  uint32_t version() const { return handler_.version(); }
  size_t input_length() const { return input_length_; }
  size_t input_capacity() const { return input_capacity_; }
  const index_memory& memory() const { return memory_; }
  size_t arena_blocks() const { return arena_.blocks(); }
  const session_options& options() const { return options_; }

 private:
  static c4::yml::Callbacks callbacks(index_memory* memory) {
    c4::yml::Callbacks cb;
    cb.set_user_data(memory);
    cb.set_allocate([](size_t len, void*, void* user) -> void* {
      return static_cast<index_memory*>(user)->allocate(len ? len : 1, alignof(std::max_align_t));
    });
    cb.set_free([](void* mem, size_t len, void* user) {
      static_cast<index_memory*>(user)->deallocate(mem, len ? len : 1, alignof(std::max_align_t));
    });
    cb.set_error_basic([](c4::csubstr msg, c4::yml::ErrorDataBasic const&, void*) {
      throw syntax_error(std::string_view(msg.str, msg.len));
    });
    cb.set_error_parse([](c4::csubstr msg, c4::yml::ErrorDataParse const& data, void*) {
      auto line = data.ymlloc.line == c4::yml::npos ? 0 : data.ymlloc.line;
      auto column = data.ymlloc.col == c4::yml::npos ? 0 : data.ymlloc.col;
      throw syntax_error(std::string_view(msg.str, msg.len)).at(line, column);
    });
    cb.set_error_visit([](c4::csubstr msg, c4::yml::ErrorDataVisit const&, void*) {
      throw error(status_internal, std::string_view(msg.str, msg.len));
    });
    return cb;
  }
  // D1: rapidyaml resolves tag handles, %TAG directives and `!!x` shorthands itself.
  static c4::yml::ParserOptions parser_options() {
    c4::yml::ParserOptions options;
    options.resolve_tags(true).resolve_tags_all(true);
    return options;
  }
  size_t storage_limit() const {
    size_t limit = options_.storage.input_bytes;
    return limit < UINT32_MAX ? limit : UINT32_MAX;
  }
  // Line and column of a validation failure (the buffer is still unfiltered).
  void locate(error& failure, size_t length) const {
    if (failure.offset == error::npos || failure.offset > length) return;
    uint64_t line = 1, column = 1;
    for (size_t i = 0; i < failure.offset; ++i) {
      if (input_[i] == '\n') ++line, column = 1;
      else if ((static_cast<unsigned char>(input_[i]) & 0xC0) != 0x80) ++column;
    }
    failure.at(line, column);
  }
  void free_input() noexcept {
    if (input_) upstream_->deallocate(input_, input_capacity_, alignof(std::max_align_t));
    input_ = nullptr;
    input_capacity_ = 0;
  }
  // Drops the previous document and bounds what the session keeps between documents.
  void recycle() noexcept {
    live_ = false;
    handler_.clear();
    arena_.reset();
    source_ = nullptr;
    source_length_ = 0;
    if (input_capacity_ > retained_input_limit) free_input();
  }

  std::pmr::memory_resource* upstream_;
  session_options options_;
  index_memory memory_;
  tape::block_arena arena_;
  handler handler_;
  c4::yml::ParseEngine<handler> engine_;
  char* input_ = nullptr;
  size_t input_capacity_ = 0;
  size_t input_length_ = 0;
  const char* source_ = nullptr;
  size_t source_length_ = 0;
  bool live_ = false;
};

}  // namespace truffledata::yaml
#endif
