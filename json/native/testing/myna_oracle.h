// Test-only reference for compact-tape differential checks. The previous decoder writes
// Myna ABI v4 nodes, lexical spans and key metadata; production JSON uses compact words.
#ifndef TRUFFLEDATA_JSON_TESTING_MYNA_ORACLE_H
#define TRUFFLEDATA_JSON_TESTING_MYNA_ORACLE_H

#include "json/native/decoder.h"
#include "json/native/testing/ondemand_pool.h"
#include "tape/native/key_table.h"
#include "tape/native/tape_writer.h"

namespace truffledata::json::legacy {

// Storage presizing from the input length. Typical JSON has one node per 5-8 input bytes; the
// worst case (`[1,1,...]`) has one per 2, which grows the arrays once or twice. Hints never exceed
// the Myna node limit or a fixed share of the index budget, and depend only on the configuration,
// so a document's allocation sequence (and its exact index peak) is reproducible.
struct decode_hint {
  size_t nodes;
  size_t bytes;
  size_t block;  // First arena block: the presized arrays that are not large allocations.
};
inline decode_hint hint_for(size_t input, const myna::value_limits& limits, const tape::storage_limits& storage) {
  size_t cap = storage.index_bytes / 72 > 4096 ? storage.index_bytes / 72 : 4096;
  size_t nodes = input / 4 + 8;
  if (nodes > cap) nodes = cap;
  if (nodes > limits.nodes) nodes = static_cast<size_t>(limits.nodes);
  size_t bytes = input / 8 + 64;
  if (bytes > cap * 8) bytes = cap * 8;
  size_t block = 4 * sizeof(myna_value_region) + 256;
  // Nodes, lexical spans, key meta, owned text, and the v4 directory and pending keys.
  for (size_t part : {nodes * sizeof(myna_value_node), nodes * sizeof(uint64_t), nodes * sizeof(uint32_t), bytes,
                      tape::tape_writer::directory_capacity(nodes) * sizeof(myna_value_directory_entry),
                      tape::tape_writer::pending_capacity(nodes) * sizeof(myna_value_directory_entry)})
    if (part <= tape::block_arena::large_threshold) block += part + alignof(std::max_align_t);
  return {nodes, bytes, block};
}

// JSON tapes are Myna ABI v4 with a directory for every object with at least this many members
// (JSON performance ledger, task V1): compiled member reads keep a per-site directory rank, which
// is the same for every member order of one key set, and a directory entry proves its key belongs
// to the object.
inline constexpr int directory_threshold = 1;
// tape_decoder's `directories` argument for an ABI v3 tape (benchmark probes only).
inline constexpr int abi_v3 = -1;

// Lean On-Demand visitor writing nodes, lexical spans and the key-hash side table into arena
// storage. Shared by documents and sessions; one instance decodes one document.
template<class Writer> class tape_decoder final {
  std::pmr::memory_resource* memory_;
  Writer writer_;
  tape::arena_vec<uint64_t> lexical_;
  tape::arena_vec<uint32_t> hashes_;
  const char* base_ = nullptr;
  uint32_t region_ = 0;
  myna::value_limits limits_;
  int directories_;

  // Packed span into the retained input region: offset << 32 | length; zero when absent.
  void lexical(std::string_view raw = {}, uint32_t meta = 0) {
    if (lexical_.size() >= limits_.nodes) throw std::length_error("JSON node limit");
    lexical_.push(memory_, raw.empty() ? 0 : (uint64_t(raw.data() - base_) << 32) | raw.size());
    hashes_.push(memory_, meta);
  }
  // `raw` is the trimmed token including quotes; `decoded` either is the body inside `raw`
  // (clean, referenced) or decoded scratch text (copied).
  void text(std::string_view decoded, std::string_view raw, bool referenced, uint32_t meta) {
    lexical(raw, meta);
    if (decoded.size() <= MYNA_VALUE_INLINE_BYTES) writer_.string(decoded);
    else if (referenced)
      writer_.string_ref(region_, static_cast<uint32_t>(decoded.data() - base_),
                         static_cast<uint32_t>(decoded.size()));
    else writer_.string(decoded);
  }
  template<class Value> void visit(Value& value, uint32_t depth) {
    if (depth > limits_.depth) throw std::length_error("JSON depth limit");
    using simdjson::ondemand::json_type;
    switch (value.type().value()) {
      case json_type::unknown:
        throw std::invalid_argument("unknown JSON token");
      case json_type::object: {
        size_t object = lexical_.size();
        lexical();
        size_t index = writer_.begin(MYNA_VALUE_OBJECT, depth);
        uint64_t members = 0;
        tape::key_table keys(memory_);
        for (auto entry : value.get_object()) {
          std::string_view raw = json::trim(entry.key_raw_json_token().value());
          std::string_view body = raw.substr(1, raw.size() - 2);
          body_scan s = scan(body);
          std::string_view key = s.clean ? body : entry.unescaped_key().value();
          bool plain = s.clean ? s.ascii : tape::ascii(key);
          // Myna's directory hash (32-bit FNV-1a over the decoded bytes) serves the duplicate
          // table, the v4 directory and (its low 15 bits) the key meta.
          uint32_t hash = myna_value_hash(reinterpret_cast<const uint8_t*>(key.data()), key.size());
          if (!keys.insert(hash, key)) throw std::invalid_argument("duplicate JSON key");
          writer_.member_key(index, hash);
          text(key, raw, s.clean, tape::key_meta(hash, lexical_.size() - object, plain));
          auto child = entry.value().value();
          visit(child, depth + 1);
          ++members;
        }
        writer_.end(index, members);
        break;
      }
      case json_type::array: {
        lexical();
        size_t index = writer_.begin(MYNA_VALUE_ARRAY, depth);
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
          text(body, raw, true, s.ascii ? tape::ascii_flag : 0);
        } else {
          std::string_view decoded = value.get_string().value();
          text(decoded, raw, false, tape::ascii(decoded) ? tape::ascii_flag : 0);
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
              lexical(raw);
              // simdjson folds -0 into integer zero; JSON.parse-style consumers need negative zero.
              if (n.get_int64() == 0 && raw[0] == '-') writer_.floating(-0.0);
              else writer_.integer(n.get_int64());
              return;
            case number_type::unsigned_integer:
              lexical(raw);
              writer_.unsigned_integer(n.get_uint64());
              return;
            case number_type::floating_point_number:
              if (std::isfinite(n.get_double())) {
                lexical(raw);
                writer_.floating(n.get_double());
                return;
              }
              break;
            default: break;
          }
        }
        if (!number(raw)) throw std::invalid_argument("invalid JSON number");
        lexical(raw);
        writer_.number_ref(region_, static_cast<uint32_t>(raw.data() - base_),
                           static_cast<uint32_t>(raw.size()));
        break;
      }
      case json_type::boolean:
        lexical();
        writer_.boolean(value.get_bool().value());
        break;
      case json_type::null:
        lexical();
        if (!value.is_null().value()) throw std::invalid_argument("invalid JSON null");
        writer_.null();
        break;
    }
  }

public:
  // `directories`: the v4 directory threshold (directory_threshold), or abi_v3.
  tape_decoder(std::pmr::memory_resource* memory, myna::value_limits limits, int directories = directory_threshold)
      : memory_(memory), writer_(memory, limits), limits_(limits), directories_(directories) {}
  tape_decoder(const tape_decoder&) = delete;
  tape_decoder& operator=(const tape_decoder&) = delete;

  // Presizes storage and registers `input` (the retained, padded input) as region 1. `walked`, when
  // given, is where the parser reads the same bytes (a stream's carry-over buffer, stream.h):
  // offsets of referenced text and lexical spans are taken relative to it, so they index `input`.
  void start(std::string_view input, const decode_hint& hint, const char* walked = nullptr) {
    if (directories_ >= 0) writer_.enable_v4(static_cast<uint32_t>(directories_));
    writer_.reserve(hint.nodes, hint.bytes);
    lexical_.reserve(memory_, hint.nodes);
    hashes_.reserve(memory_, hint.nodes);
    base_ = walked ? walked : input.data();
    region_ = writer_.region(input);
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
  // The complete descriptor: v4 (with the directory), or a v3 prefix with no extension tables.
  myna_value_view_v4 view() const {
    if (writer_.v4()) return writer_.view_v4();
    return {writer_.view(), nullptr, 0, nullptr, 0};
  }
  tape::table_view<uint64_t> lexical_spans() const { return {lexical_.data(), lexical_.size()}; }
  tape::table_view<uint32_t> key_hashes() const { return {hashes_.data(), hashes_.size()}; }
};

// The input is retained; the parser is pooled and released before construction completes.
// Unescaped text references the retained input; escaped text is copied into message-owned
// storage, so parser scratch never outlives the lease. Nodes, owned text, lexical spans and key
// hashes live in one block arena charged to index_memory. One instance is immutable after
// construction and must not be reused to parse.
template<class Writer> class basic_document final {
  simdjson::padded_string owned_;
  // Either owned_ or caller memory adopted with sufficient padding; never copied twice.
  simdjson::padded_string_view input_;
  tape::index_memory memory_;
  tape::block_arena arena_;
  tape_decoder<Writer> decoder_;
  myna_value_view_v4 view_{};
  size_t parser_capacity_ = 0;

  struct adopted_tag {};
  basic_document(adopted_tag, simdjson::padded_string owned, simdjson::padded_string_view input,
                 myna::value_limits limits, tape::storage_limits storage, std::pmr::memory_resource* upstream)
      : owned_(std::move(owned)),
        input_(owned_.data() ? simdjson::padded_string_view(owned_.data(), owned_.size(), owned_.size() + simdjson::SIMDJSON_PADDING) : input),
        memory_(storage.index_bytes, upstream),
        arena_(&memory_),
        decoder_(&arena_, limits) {
    if (!input_.data()) throw std::bad_alloc();
    decode_hint hint = hint_for(input_.size(), limits, storage);
    arena_.hint(hint.block);
    decoder_.start(std::string_view(input_.data(), input_.size()), hint);
    parser_lease lease = parser_pool::acquire();
    auto& parser = lease.get();
    auto root = parser.iterate(input_).value();
    decoder_.decode(root);
    parser_capacity_ = parser.capacity();
    view_ = decoder_.view();
  }
public:
  explicit basic_document(std::string_view input, myna::value_limits limits = {},
                          tape::storage_limits storage = {},
                          std::pmr::memory_resource* upstream = std::pmr::get_default_resource())
      : basic_document(adopted_tag{}, simdjson::padded_string(checked(input, storage)),
                       simdjson::padded_string_view(), limits, storage, upstream) {}
  // Adopt caller memory when it carries SIMDJSON_PADDING readable bytes past `length`;
  // otherwise copy once. The caller keeps adopted memory valid for the document's life.
  basic_document(const char* data, size_t length, size_t capacity, myna::value_limits limits = {},
                 tape::storage_limits storage = {},
                 std::pmr::memory_resource* upstream = std::pmr::get_default_resource())
      : basic_document(adopted_tag{},
                       capacity >= length + simdjson::SIMDJSON_PADDING
                           ? simdjson::padded_string()
                           : simdjson::padded_string(checked(std::string_view(data, length), storage)),
                       capacity >= length + simdjson::SIMDJSON_PADDING
                           ? simdjson::padded_string_view(data, checked(std::string_view(data, length), storage).size(), capacity)
                           : simdjson::padded_string_view(),
                       limits, storage, upstream) {}
  bool adopted() const { return !owned_.data(); }
  basic_document(const basic_document&) = delete;
  basic_document& operator=(const basic_document&) = delete;
  // The descriptor prefix (node, text and region fields; version 4); view_v4() is the published one.
  myna_value_view view() const { return view_.base; }
  const myna_value_view_v4& view_v4() const { return view_; }
  tape::table_view<uint64_t> lexical_spans() const { return decoder_.lexical_spans(); }
  // One entry per node; see key_hash and ascii_flag.
  tape::table_view<uint32_t> key_hashes() const { return decoder_.key_hashes(); }
  // Index accounting counts arena blocks and large allocations, not individual nodes.
  tape::allocation_stats allocations() const {
    return {adopted() ? 0 : input_.size(),
            adopted() ? input_.capacity() : input_.size() + simdjson::SIMDJSON_PADDING,
            memory_.current(), memory_.peak(), memory_.allocations(), parser_capacity_};
  }
private:
  static std::string_view checked(std::string_view input, tape::storage_limits storage) {
    if (input.size() > UINT32_MAX || input.size() > storage.input_bytes) throw std::length_error("JSON input limit");
    return input;
  }
};
// Production documents write through the presized tape writer; builder_document runs the same
// visitor through myna::value_builder and exists to prove byte-for-byte parity in tests.
using document = basic_document<tape::tape_writer>;
using builder_document = basic_document<tape::builder_writer>;
} // namespace truffledata::json::legacy
#endif
