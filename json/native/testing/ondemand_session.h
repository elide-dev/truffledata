// Frozen pre-stage-2 stream for paired tests and timing only.
#ifndef TRUFFLEDATA_JSON_BASELINE_SESSION_H
#define TRUFFLEDATA_JSON_BASELINE_SESSION_H
#include "json/native/decoder.h"
#include "json/native/testing/ondemand_decoder.h"
#include "tape/native/text_arena.h"
#include <optional>

namespace truffledata::json_stream_baseline {
using namespace truffledata::json;

// Owner of a published JsonResult (td_json_decode documents, retained session results and
// td_json_result_myna conversions).
struct result_owner {
  virtual ~result_owner() = default;
  // Destroys and frees this owner through the allocator that created it.
  virtual void destroy() noexcept = 0;
};

// Thread-confined decode context: one stage-1 parser, padded input, and capacity-retained writer
// arrays reused across documents. A decoded document is borrowed: valid until the next call on the
// session. retain() moves the document's arena blocks and input into an independent owner.
class session final {
public:
  // Large inputs and parsers that grew past this are released before the next decode.
  static constexpr size_t retained_input_limit = size_t(1) << 20;

  struct document_view {
    // Null for a validate-only document.
    const td_tape_view* tape;
    size_t input_length;
    size_t index_allocations;  // Upstream allocations made by this decode.
  };

  // A retained document: arena storage, input buffer and the descriptor that points into them.
  class retained final : public result_owner {
    std::pmr::memory_resource* upstream_;
    tape::block_arena::storage storage_;
    char* input_;
    size_t input_capacity_;
    size_t bytes_;
  public:
    td_tape_view tape;
    retained(std::pmr::memory_resource* upstream, tape::block_arena::storage storage, size_t bytes,
             char* input, size_t input_capacity, td_tape_view tape)
        : upstream_(upstream), storage_(std::move(storage)), input_(input),
          input_capacity_(input_capacity), bytes_(bytes), tape(tape) {}
    // Index bytes (arena blocks and large allocations) now owned by this document.
    size_t bytes() const { return bytes_; }
    // The published compact descriptor.
    const void* descriptor() const {
      return &tape;
    }
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
  tape::storage_limits storage_;
  tape::index_memory memory_;
  tape::block_arena arena_;
  stage1_index parser_;
  tape::block_arena scratch_;
  std::optional<tape::structural_writer> writer_;
  std::optional<structural_null_writer> validator_;
  tape::arena_vec<uint64_t> text_table_;
  bool streamed_ = false;
  char* input_ = nullptr;
  size_t input_capacity_ = 0;
  size_t input_length_ = 0;
  std::optional<testing::ondemand_decoder<tape::ctape_writer>> decoder_;
  tape::key_shape_cache shapes_;
  td_tape_view view_{};
  bool live_ = false;

  void free_input() noexcept {
    if (input_) upstream_->deallocate(input_, input_capacity_, alignof(std::max_align_t));
    input_ = nullptr;
    input_capacity_ = 0;
  }
  // Ensures the input buffer holds `length` bytes plus simdjson's padding.
  void reserve_input(size_t length) {
    size_t need = length + simdjson::SIMDJSON_PADDING;
    if (need > input_capacity_) {
      free_input();
      input_ = static_cast<char*>(upstream_->allocate(need, alignof(std::max_align_t)));
      input_capacity_ = need;
    }
  }
  // Decodes the document `open()` positions on the input buffer (`walked`: see tape_decoder::start)
  // and publishes it; validate-only walks it with nothing retained. On any exception the session
  // holds no document.
  template<class Open> document_view walk(size_t length, const char* walked, bool validate_only, Open&& open) {
    size_t before = memory_.allocations();
    std::string_view input(input_, length);
    try {
      if (validate_only) {
        decode_hint hint = hint_for(length, values_, storage_);
        arena_.hint(hint.block);
        testing::ondemand_decoder<null_writer> decoder(&arena_, values_);
        decoder.shapes(&shapes_);
        decoder.start(input, hint, walked);
        auto&& root = open();
        decoder.decode(root);
      } else {
        decode_hint hint = hint_for(length, values_, storage_);
        arena_.hint(hint.block);
        auto& decoder = decoder_.emplace(&arena_, values_);
        decoder.shapes(&shapes_);
        decoder.writer().directories(false);
        decoder.start(input, hint, walked);
        auto&& root = open();
        decoder.decode(root);
        view_ = decoder.view();

      }
    } catch (...) {
      recycle();
      throw;
    }
    size_t allocations = memory_.allocations() - before;
    if (validate_only) {
      recycle();
      return {nullptr, length, allocations};
    }
    live_ = true;
    return {&view_, length, allocations};
  }
  void drop_arrays() noexcept {
    decoder_.reset(); writer_.reset(); validator_.reset(); text_table_.forget();
    arena_.reset(); scratch_.reset(); streamed_ = false;
  }
  // Borrowed arrays survive ordinary reuse. A retained/oversized document gives up its storage.
  void recycle() noexcept {
    live_ = false;
    if (streamed_ || input_capacity_ > retained_input_limit) drop_arrays();
    else {
      if (writer_) writer_->clear();
      if (validator_) validator_->clear();
      text_table_.truncate(0);
    }
    if (input_capacity_ > retained_input_limit) free_input();
    parser_.release_above(retained_input_limit);
  }

  document_view walk_structural(size_t length, bool validate_only, bool directories) {
    size_t before = memory_.allocations();
    try {
      parser_.run(reinterpret_cast<const uint8_t*>(input_), length);
      auto decode = [&](auto& writer) {
        writer.shapes(&shapes_);
        if constexpr (std::decay_t<decltype(writer)>::retains_values) writer.directories(directories);
        writer.input({input_, length});
        writer.reserve_structural(parser_.size(), length);
        stage2_walk<std::decay_t<decltype(writer)>> walk(reinterpret_cast<const uint8_t*>(input_), length,
                                                         parser_.indexes(), parser_.size(), writer);
        walk.run();
      };
      if (validate_only) {
        if (!validator_) validator_.emplace(&scratch_, values_);
        decode(*validator_);
      } else {
        if (!writer_) writer_.emplace(&arena_, values_);
        decode(*writer_);
        view_ = writer_->view();
      }
    } catch (...) { recycle(); throw; }
    size_t allocations = memory_.allocations() - before;
    live_ = !validate_only;
    return {validate_only ? nullptr : &view_, length, allocations};
  }

public:
  session(myna::value_limits values, tape::storage_limits storage,
          std::pmr::memory_resource* upstream = std::pmr::get_default_resource())
      : upstream_(upstream), values_(values), storage_(storage),
        memory_(storage.index_bytes, upstream), arena_(&memory_), scratch_(&memory_) {
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
  // exception the session holds no document and stays reusable. `validate_only` runs the same walk
  // and checks and keeps nothing (the view's descriptors are null; the session holds no document).
  //
  // Input encodings (Task D3): UTF-8 is copied; UTF-16LE (`bytes` even, `data` 2-byte aligned) is
  // validated by simdutf (an unpaired surrogate is std::invalid_argument, i.e. a syntax error) and
  // transcoded; Latin-1 is transcoded (every byte is one code point). Limits apply to the UTF-8
  // length, which is what spans and the tape describe.
  enum class encoding { utf8, utf16le, latin1 };
  document_view decode(const char* data, size_t bytes, encoding input = encoding::utf8, bool validate_only = false, bool directories = true) {
    recycle();
    size_t length = bytes;
    const char16_t* units = reinterpret_cast<const char16_t*>(data);
    if (input == encoding::utf16le) {
      if (bytes % 2) throw std::invalid_argument("odd UTF-16 input length");
      if (!simdutf::validate_utf16le(units, bytes / 2)) throw std::invalid_argument("unpaired UTF-16 surrogate");
      length = simdutf::utf8_length_from_utf16le(units, bytes / 2);
    } else if (input == encoding::latin1) {
      length = simdutf::utf8_length_from_latin1(data, bytes);
    }
    if (length > UINT32_MAX || length > storage_.input_bytes) throw std::length_error("JSON input limit");
    reserve_input(length);
    size_t written = length;
    if (input == encoding::utf16le) written = simdutf::convert_valid_utf16le_to_utf8(units, bytes / 2, input_);
    else if (input == encoding::latin1) written = simdutf::convert_latin1_to_utf8(data, bytes, input_);
    else std::memcpy(input_, data, length);
    if (written != length) throw std::logic_error("JSON input transcoding");
    std::memset(input_ + length, 0, simdjson::SIMDJSON_PADDING);
    input_length_ = length;
    return walk_structural(length, validate_only, directories);
  }

  // Decodes one document of a stream (stream.h): `root` is the iterate_many document positioned on
  // `data[0, length)` inside the stream's carry-over buffer. The bytes are copied into the session's
  // input buffer, which becomes the document's input, so the borrowed (and retained) document never
  // refers to the carry-over buffer; the walk itself reads the stream buffer in place, and spans are
  // taken relative to `data`, so they index the session's copy. Same borrowing, failure and retain
  // rules as decode().
  document_view decode_streamed(const char* data, size_t length, simdjson::ondemand::document_reference& root) {
    recycle();
    drop_arrays();
    streamed_ = true;
    if (length > UINT32_MAX || length > storage_.input_bytes) throw std::length_error("JSON input limit");
    reserve_input(length);
    std::memcpy(input_, data, length);
    std::memset(input_ + length, 0, simdjson::SIMDJSON_PADDING);
    input_length_ = length;
    return walk(length, data, false, [&]() -> simdjson::ondemand::document_reference& { return root; });
  }

  // Writes the live document's text arena (Task D2, text_arena.h) into `out`; the table (one entry
  // per word) lives in the session arena until the next decode.
  // Returns the bytes used, or text_too_small after dropping the document (the session stays
  // reusable). On an exception the document is dropped too.
  size_t write_text(uint8_t* out, size_t capacity, const uint64_t*& table) {
    if (!live_) throw std::logic_error("no JSON session document");
    try {
      size_t count = view_.word_count;
      if (!count) count = 1;
      text_table_.truncate(0);
      auto* entries = text_table_.extend(&arena_, count);
      size_t used = tape::write_text_arena(view_, entries, out, capacity);
      if (used == tape::text_too_small) recycle();
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
    if (view_.flags & TD_TAPE_NO_DIRECTORIES) {
      if (streamed_) { decoder_->writer().build_directories(); view_ = decoder_->view(); }
      else { writer_->build_directories(); view_ = writer_->view(); }
    }
    void* raw = upstream_->allocate(sizeof(retained), alignof(retained));
    tape::block_arena::storage storage;
    size_t bytes = arena_.detach(storage, upstream_);
    memory_.forget(bytes);
    auto* owner = new (raw) retained(upstream_, std::move(storage), bytes, input_, input_capacity_, view_);
    input_ = nullptr;
    input_capacity_ = 0;
    live_ = false;
    decoder_.reset(); writer_.reset(); text_table_.forget(); streamed_ = false;
    return owner;
  }

  // The live compact document's descriptor; valid only while live().
  const td_tape_view& view() const { return view_; }
  size_t input_length() const { return input_length_; }
  size_t parser_capacity() const { return parser_.capacity(); }
  size_t input_capacity() const { return input_capacity_; }
  const tape::index_memory& memory() const { return memory_; }
  // The allocator behind the session's input buffer and arena (streams buffer through it too).
  std::pmr::memory_resource* upstream() const { return upstream_; }
  size_t arena_blocks() const { return arena_.blocks(); }
};
} // namespace truffledata::json
#endif
