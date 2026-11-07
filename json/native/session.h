#ifndef TRUFFLEDATA_JSON_SESSION_H
#define TRUFFLEDATA_JSON_SESSION_H
#include "json/native/decoder.h"
#include "json/native/text_arena.h"
#include <optional>

namespace truffledata::json {

// Owner of a published JsonResult (td_json_decode documents and retained session results).
struct result_owner {
  virtual ~result_owner() = default;
  // Destroys and frees this owner through the allocator that created it.
  virtual void destroy() noexcept = 0;
};

// Thread-confined decode context: one On-Demand parser, a padded input buffer and a block arena,
// reused across documents. A decoded document is borrowed: valid until the next call on the
// session. retain() moves the document's arena blocks and input into an independent owner.
class session final {
public:
  // Large inputs and parsers that grew past this are released before the next decode.
  static constexpr size_t retained_input_limit = size_t(1) << 20;

  struct document_view {
    myna_value_view view;
    table_view<uint64_t> lexical;
    table_view<uint32_t> hashes;
    size_t input_length;
    size_t index_allocations;  // Upstream allocations made by this decode.
  };

  // A retained document: arena storage, input buffer and the descriptor that points into them.
  class retained final : public result_owner {
    std::pmr::memory_resource* upstream_;
    block_arena::storage storage_;
    char* input_;
    size_t input_capacity_;
    size_t bytes_;
  public:
    myna_value_view view;
    retained(std::pmr::memory_resource* upstream, block_arena::storage storage, size_t bytes,
             char* input, size_t input_capacity, myna_value_view view)
        : upstream_(upstream), storage_(std::move(storage)), input_(input),
          input_capacity_(input_capacity), bytes_(bytes), view(view) {}
    // Index bytes (arena blocks and large allocations) now owned by this document.
    size_t bytes() const { return bytes_; }
    ~retained() override {
      if (input_) upstream_->deallocate(input_, input_capacity_, alignof(std::max_align_t));
    }
    void destroy() noexcept override {
      auto* upstream = upstream_;
      this->~retained();
      upstream->deallocate(this, sizeof(retained), alignof(retained));
    }
  };

private:
  std::pmr::memory_resource* upstream_;
  myna::value_limits values_;
  storage_limits storage_;
  index_memory memory_;
  block_arena arena_;
  simdjson::ondemand::parser parser_;
  char* input_ = nullptr;
  size_t input_capacity_ = 0;
  size_t input_length_ = 0;
  std::optional<tape_decoder<tape_writer>> decoder_;
  myna_value_view view_{};
  bool live_ = false;

  void free_input() noexcept {
    if (input_) upstream_->deallocate(input_, input_capacity_, alignof(std::max_align_t));
    input_ = nullptr;
    input_capacity_ = 0;
  }
  // Drops the previous document and bounds what the session keeps between documents.
  void recycle() noexcept {
    live_ = false;
    decoder_.reset();
    arena_.reset();
    if (input_capacity_ > retained_input_limit) free_input();
    if (parser_.capacity() > retained_input_limit) parser_ = simdjson::ondemand::parser();
  }

public:
  session(myna::value_limits values, storage_limits storage,
          std::pmr::memory_resource* upstream = std::pmr::get_default_resource())
      : upstream_(upstream), values_(values), storage_(storage),
        memory_(storage.index_bytes, upstream), arena_(&memory_) {
    if (values.depth > 128) throw std::invalid_argument("JSON depth limit above 128");
  }
  session(const session&) = delete;
  session& operator=(const session&) = delete;
  ~session() {
    decoder_.reset();
    arena_.release_all();
    free_input();
  }

  // Decodes one document into the session. Invalidates the previous borrowed document. On any
  // exception the session holds no document and stays reusable.
  document_view decode(const char* data, size_t length) {
    recycle();
    if (length > UINT32_MAX || length > storage_.input_bytes) throw std::length_error("JSON input limit");
    size_t need = length + simdjson::SIMDJSON_PADDING;
    if (need > input_capacity_) {
      free_input();
      input_ = static_cast<char*>(upstream_->allocate(need, alignof(std::max_align_t)));
      input_capacity_ = need;
    }
    std::memcpy(input_, data, length);
    std::memset(input_ + length, 0, simdjson::SIMDJSON_PADDING);
    input_length_ = length;
    size_t before = memory_.allocations();
    try {
      decode_hint hint = hint_for(length, values_, storage_);
      arena_.hint(hint.block);
      auto& decoder = decoder_.emplace(&arena_, values_);
      decoder.start(std::string_view(input_, length), hint);
      auto root = parser_.iterate(simdjson::padded_string_view(input_, length, input_capacity_)).value();
      decoder.decode(root);
      view_ = decoder.view();
    } catch (...) {
      recycle();
      throw;
    }
    live_ = true;
    return {view_, decoder_->lexical_spans(), decoder_->key_hashes(), length,
            memory_.allocations() - before};
  }

  // Writes the live document's text arena (Task D2, text_arena.h) into `out`; the per-node table
  // lives in the session arena until the next decode. Returns the bytes used, or text_too_small
  // after dropping the document (the session stays reusable). On an exception the document is
  // dropped too.
  size_t write_text(uint8_t* out, size_t capacity, const uint64_t*& table) {
    if (!live_) throw std::logic_error("no JSON session document");
    try {
      size_t count = view_.node_count ? view_.node_count : 1;
      auto* entries = static_cast<uint64_t*>(arena_.allocate(count * sizeof(uint64_t), alignof(uint64_t)));
      size_t used = write_text_arena(view_, decoder_->key_hashes().data(), entries, out, capacity);
      if (used == text_too_small) recycle();
      table = entries;
      return used;
    } catch (...) {
      recycle();
      throw;
    }
  }

  bool live() const { return live_; }
  // Invalidates the borrowed document without freeing: O(1), no allocation, no exception.
  void reset() noexcept { live_ = false; }

  // Moves the live document's blocks and input into a new owner, O(blocks), without copying.
  retained* retain() {
    if (!live_) throw std::logic_error("no JSON session document");
    void* raw = upstream_->allocate(sizeof(retained), alignof(retained));
    block_arena::storage storage;
    size_t bytes = arena_.detach(storage, upstream_);
    memory_.forget(bytes);
    auto* owner = new (raw) retained(upstream_, std::move(storage), bytes, input_, input_capacity_, view_);
    input_ = nullptr;
    input_capacity_ = 0;
    live_ = false;
    decoder_.reset();
    return owner;
  }

  // The live document; valid only while live().
  const myna_value_view& view() const { return view_; }
  table_view<uint64_t> lexical_spans() const { return decoder_->lexical_spans(); }
  table_view<uint32_t> key_hashes() const { return decoder_->key_hashes(); }
  size_t input_length() const { return input_length_; }
  size_t parser_capacity() const { return parser_.capacity(); }
  size_t input_capacity() const { return input_capacity_; }
  const index_memory& memory() const { return memory_; }
  size_t arena_blocks() const { return arena_.blocks(); }
};
} // namespace truffledata::json
#endif
