#ifndef TRUFFLEDATA_YAML_DECODER_H
#define TRUFFLEDATA_YAML_DECODER_H
// yaml::handler: rapidyaml events write the shared compact tape directly into the session arena.
// There is no intermediate tree or event array. Scalar spans, ASCII/KEY flags and object
// directories live in compact words; YAML metadata indexes word offsets (payload entries are 0):
//
//   bits 0-2   style: 0 plain, 1 single, 2 double, 3 literal, 4 folded, 5 block collection,
//              6 flow collection, 7 pair (see "Pairs" below)
//   bit 3      anchored
//   bit 4      alias
//   bits 5-7   key kind: 0 string, 1 null, 2 bool, 3 int, 4 float (keys are STRING nodes)
//   bits 8-31  tag index (1-based into `tags`; 0 none), or the alias ordinal when bit 4 is set
//
// Plain unfiltered strings reference the retained input. Quoted, folded, filtered and normalized
// spellings live in the owned pool with an explicit raw span. One handler decodes at most one
// document; stream.h splits streams before invoking it.
//
// Anchors and aliases: names bind to closed node offsets (redefinition affects later aliases).
// Value aliases are REF words. Key aliases keep canonical STRING text plus metadata; the small
// alias target table preserves this annotation and provides stable coordinates during rewrites.
// Recursive and undefined aliases fail before publication, so the representation stays linear.
//
// Merge keys (`<<`, when enabled) are resolved when their mapping closes (merge.h): merged entries
// are key copies plus alias-reference values placed at the position of `<<`; explicit keys win and
// earlier sources win.
//
// Pairs (internal; the deferred ComplexKeys.PAIRS shape, used by the yaml-test-suite target): with
// complex_keys_pairs, a mapping with a collection key, and with duplicate_keys_pairs a mapping with
// a duplicate key, is stored as an ARRAY (its own collection style) of 2-element ARRAY nodes
// [key, value] whose yaml_meta style is 7.
#include "tape/native/arena.h"
#include "tape/native/key_table.h"
#include "tape/native/ctape.h"
#include "yaml/native/errors.h"
#include "yaml/native/input.h"
#include "yaml/native/scalars.h"
#include "yaml/native/tags.h"

#if defined(__x86_64__)
#include <emmintrin.h>
#endif

#include <c4/yml/event_handler_stack.hpp>
#include <c4/yml/parse_engine.hpp>
#include <c4/yml/tag.hpp>

#include <cmath>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <optional>
#include <string_view>

namespace truffledata::yaml {

using tape::arena_vec;
using tape::block_arena;
using tape::table_view;
using tape::ctape_writer;
namespace ct = tape::ctape;

// Compact directories use the complete unsigned FNV hash. The legacy Myna side-table
// helper masks its high bit; sharing that masked hash would corrupt compact directories.
inline uint32_t full_key_hash(std::string_view text) {
  return myna_value_hash(reinterpret_cast<const uint8_t*>(text.data()), text.size());
}
inline uint32_t full_key_hash(std::string_view text, bool& plain) {
  uint32_t hash = 2166136261u;
  unsigned char any = 0;
  for (unsigned char c : text) { hash ^= c; hash *= 16777619u; any |= c; }
  plain = any < 0x80;
  return hash;
}

// yaml_meta layout (see the file comment).
inline constexpr uint32_t meta_style_mask = 0x7u;
inline constexpr uint32_t meta_anchored = 1u << 3;
inline constexpr uint32_t meta_alias = 1u << 4;
inline constexpr uint32_t meta_key_kind_shift = 5;
inline constexpr uint32_t meta_key_kind_mask = 0x7u << meta_key_kind_shift;
inline constexpr uint32_t meta_tag_shift = 8;
inline constexpr uint32_t meta_tag_limit = 1u << 24;
inline constexpr uint32_t style_block = 5;
inline constexpr uint32_t style_flow = 6;
inline constexpr uint32_t style_pair = 7;

// Document flags published with each result.
inline constexpr uint32_t doc_explicit_start = 1u << 0;   // `---`
inline constexpr uint32_t doc_explicit_end = 1u << 1;     // `...`
inline constexpr uint32_t doc_nonfinite = 1u << 2;        // non-finite F64 nodes (MYNA_VALUE_ALLOW_NON_FINITE set)
inline constexpr uint32_t doc_source_retained = 1u << 3;  // `source` is a pristine copy of the input
inline constexpr uint32_t doc_present = 1u << 4;          // the input held a document (else root null)
inline constexpr uint32_t doc_schema_1_1 = 1u << 5;       // resolved with the YAML 1.1 schema

// Raw scalar token span in the retained source (zero for synthetic values/containers).
inline uint64_t lexical_span(const td_tape_view& view, size_t index) {
  uint64_t w = view.words[index];
  uint32_t kind = ct::kind(w);
  if ((kind == ct::kind_string || kind == ct::kind_decimal) && (w & ct::owned_flag)) return view.words[index + 1];
  if (kind <= ct::kind_string) return uint64_t(ct::low(w)) << 32 | ct::field(w);
  return 0;
}

// Side tables of anchors, tags and directives (layouts match YamlAnchor, YamlSpan and
// YamlTagDirective in yaml.seam). Text offsets index the document's `strings` bytes.
struct anchor_entry {
  uint64_t node;
  uint32_t offset;
  uint32_t length;
};
struct span_entry {
  uint32_t offset;
  uint32_t length;
};
struct directive_entry {
  uint32_t handle_offset;
  uint32_t handle_length;
  uint32_t prefix_offset;
  uint32_t prefix_length;
};

// Decode policies (YamlOptions minus limits, which live in myna::value_limits and storage_limits).
struct policy {
  yaml::schema schema = yaml::schema::core;
  bool last_wins = false;             // DuplicateKeys.LAST_WINS
  bool duplicate_keys_pairs = false;  // internal: a mapping with a duplicate key is stored as pairs
  bool complex_keys_pairs = false;    // internal: a mapping with a collection key is stored as pairs
  bool merge_keys = false;            // mergeKeys (always on under YAML_1_1)
  bool reject_aliases = false;        // Aliases.REJECT
  bool reject_unknown_tags = false;   // UnknownTags.REJECT
  bool honor_version = false;         // VersionDirective.HONOR
  bool retain_source = false;         // per decode (TD_YAML_RETAIN_SOURCE)
  uint64_t max_aliases = 10000;
};

struct handler_state : public c4::yml::ParserState {
  c4::yml::type_bits evt_type;
};

// Arena-backed map from strings (stored in a byte table by offset) to uint32 values: tag interning
// and anchor name bindings. Open addressing, power-of-two capacity, rebuilt on growth.
class string_map {
  struct slot {
    uint32_t hash;
    uint32_t offset;
    uint32_t length;
    uint32_t value;  // UINT32_MAX: empty
  };
  slot* slots_ = nullptr;
  size_t capacity_ = 0, used_ = 0;

 public:
  void forget() { slots_ = nullptr, capacity_ = used_ = 0; }
  // Index of `key` (its bytes at strings + offset), or UINT32_MAX.
  uint32_t find(std::string_view key, uint32_t hash, const char* strings) const {
    if (!capacity_) return UINT32_MAX;
    for (size_t i = hash & (capacity_ - 1);; i = (i + 1) & (capacity_ - 1)) {
      const slot& s = slots_[i];
      if (s.value == UINT32_MAX) return UINT32_MAX;
      if (s.hash == hash && s.length == key.size() && std::memcmp(strings + s.offset, key.data(), key.size()) == 0)
        return s.value;
    }
  }
  // Inserts or rebinds `key` (already copied to strings + offset) to `value`.
  void put(std::pmr::memory_resource* memory, uint32_t hash, uint32_t offset, uint32_t length, uint32_t value,
           const char* strings) {
    if ((used_ + 1) * 2 > capacity_) grow(memory);
    for (size_t i = hash & (capacity_ - 1);; i = (i + 1) & (capacity_ - 1)) {
      slot& s = slots_[i];
      if (s.value == UINT32_MAX) {
        s = {hash, offset, length, value};
        ++used_;
        return;
      }
      if (s.hash == hash && s.length == length && std::memcmp(strings + s.offset, strings + offset, length) == 0) {
        s.value = value;
        s.offset = offset;
        return;
      }
    }
  }

 private:
  void grow(std::pmr::memory_resource* memory) {
    size_t capacity = capacity_ ? capacity_ * 2 : 16;
    auto* slots = static_cast<slot*>(memory->allocate(capacity * sizeof(slot), alignof(slot)));
    for (size_t i = 0; i < capacity; ++i) slots[i].value = UINT32_MAX;
    for (size_t k = 0; k < capacity_; ++k) {
      if (slots_[k].value == UINT32_MAX) continue;
      for (size_t i = slots_[k].hash & (capacity - 1);; i = (i + 1) & (capacity - 1))
        if (slots[i].value == UINT32_MAX) { slots[i] = slots_[k]; break; }
    }
    slots_ = slots;  // the old table stays in the arena until reset
    capacity_ = capacity;
  }
};

// Duplicate-key set of one open mapping, over tape::key_table's hashes: up to 16 keys inline behind
// a 64-bit filter of their hashes (a clear bit skips the linear compare, so a mapping of distinct
// keys rarely compares bytes), then a tape::key_table (open addressing in the session arena). Keys
// are compared after resolution; their bytes must stay valid while the set holds them.
class key_set {
  struct entry {
    uint32_t hash;
    uint32_t length;
    const char* data;
  };
  static constexpr uint32_t inline_keys = 16;
  entry small_[inline_keys];
  uint32_t count_ = 0;
  uint64_t seen_ = 0;
  std::optional<tape::key_table> table_;

  [[gnu::noinline]] bool spill(std::pmr::memory_resource* memory, uint32_t hash, std::string_view key) {
    if (!table_) {
      table_.emplace(memory);
      for (uint32_t i = 0; i < count_; ++i) table_->insert(small_[i].hash, {small_[i].data, small_[i].length});
    }
    return table_->insert(hash, key);
  }

 public:
  void clear() noexcept {
    count_ = 0;
    seen_ = 0;
    table_.reset();
  }
  // False when an equal key is already present.
  [[gnu::always_inline]] bool insert(std::pmr::memory_resource* memory, uint32_t hash, std::string_view key) {
    if (C4_LIKELY(!table_)) {
      const uint64_t bit = uint64_t(1) << (hash & 63);
      if (seen_ & bit) {
        for (uint32_t i = 0; i < count_; ++i) {
          const entry& e = small_[i];
          if (e.hash == hash && e.length == key.size() && (key.empty() || std::memcmp(e.data, key.data(), key.size()) == 0))
            return false;
        }
      }
      if (C4_LIKELY(count_ < inline_keys)) {
        seen_ |= bit;
        small_[count_++] = {hash, uint32_t(key.size()), key.data()};
        return true;
      }
    }
    return spill(memory, hash, key);
  }
};

class handler : public c4::yml::EventHandlerStack<handler, handler_state> {
 public:
  using state = handler_state;
  // Resolved tags are copied out when they arrive; they need not live in the source or arena.
  enum { requires_strings_on_buffers = false };  // NOLINT

  static constexpr size_t max_frames = 260;  // collections and pairs within the 128-deep limit

  explicit handler(c4::yml::Callbacks const& callbacks) : EventHandlerStack(callbacks) {
    m_tag_cache.m_entries.m_callbacks = callbacks;
    for (hash_slot& slot : hash_cache_) slot = {0, 0, UINT32_MAX, 0};
    _stack_reset_root();
  }
  handler(const handler&) = delete;
  handler& operator=(const handler&) = delete;
  ~handler() { clear(); }

  // ---- per-decode lifecycle (session.h) ------------------------------------------------------

  // Prepares a parse of `input` (validated UTF-8 that rapidyaml filters in place; `newlines` its LF
  // bitmap; `pristine` an unfiltered copy or null). Storage comes from `memory` (the session arena).
  void start(block_arena* memory, char* input, size_t length, const uint64_t* newlines, const char* pristine,
             const policy& rules, const myna::value_limits& limits, size_t node_hint, size_t byte_hint) {
    clear();
    // Templates point into the previous input: never carried across decodes.
    for (auto& level : templates_)
      for (key_template& t : level) t.count = 0;
    memory_ = memory;
    input_ = input;
    length_ = length;
    newlines_ = newlines;
    pristine_ = pristine;
    rules_ = rules;
    limits_ = limits;
    schema_ = rules.schema;
    writer_.emplace(memory, limits);
    writer_->input(std::string_view(pristine ? pristine : input, length));
    writer_->reserve(node_hint * 2, byte_hint, node_hint / 3);
    sparse_meta_ = !rules_.last_wins && !rules_.duplicate_keys_pairs && !rules_.complex_keys_pairs &&
                   !rules_.honor_version && !merges();
    meta_.reserve(memory, node_hint * 2);
    if (sparse_meta_) zero_meta(node_hint * 2);
    _stack_reset_root();
    m_curr->flags |= c4::yml::RUNK | c4::yml::RTOP;
    m_curr->evt_type = {};
    m_src = c4::substr(input, length);
    m_curr_doc = 0;
    m_tag_directives.clear();
    m_tag_cache.clear();
  }
  // Writes the root null when the input held no document; publishes the descriptor.
  void finish() {
    if (writer_->word_count() == 0) {
      writer_->null();
      side(0, 0);
    }
    if (writer_->has_nonfinite()) flags_ |= doc_nonfinite;
    if (pristine_) flags_ |= doc_source_retained;
    if (schema_ == schema::yaml11) flags_ |= doc_schema_1_1;
    zero_meta(writer_->word_count());
    meta_.truncate(writer_->word_count());
    view_ = writer_->view();
    view_.flags |= TD_TAPE_HAS_META | TD_TAPE_YAML;
    view_.meta = meta_.data();
    view_.meta_count = meta_.size();
  }
  // Drops the per-decode state before the arena is reset (key tables free into the arena).
  void clear() noexcept {
    while (depth_) frames_[--depth_].keys.clear();
    writer_.reset();
    meta_.forget();
    strings_.forget();
    tags_.forget();
    anchors_.forget();
    anchor_open_.forget();
    alias_targets_.forget();
    directives_.forget();
    tag_index_.forget();
    bindings_.forget();
    memory_ = nullptr;
    arena_ = nullptr;
    arena_capacity_ = arena_size_ = 0;
    docs_ = 0;
    flags_ = 0;
    version_ = 0;
    root_written_ = false;
    pending_tag_ = tag_kind::none;
    pending_tag_index_ = 0;
    has_pending_anchor_ = false;
    last_ = {};
    last_closed_ = SIZE_MAX;
    view_ = {};
  }

  // Test hook: false routes every scalar through the general resolver and writers, so tests can
  // prove the fast paths produce identical tapes and side tables. Production never clears it.
  void fast_paths(bool on) { fast_paths_ = on; }
  void fused_duplicates(bool on) { fused_duplicates_ = on; }

  const td_tape_view& view() const { return view_; }
  table_view<uint32_t> meta() const { return {meta_.data(), meta_.size()}; }
  table_view<span_entry> tags() const { return {tags_.data(), tags_.size()}; }
  table_view<anchor_entry> anchors() const { return {anchors_.data(), anchors_.size()}; }
  table_view<uint64_t> alias_targets() const { return {alias_targets_.data(), alias_targets_.size()}; }
  table_view<directive_entry> directives() const { return {directives_.data(), directives_.size()}; }
  std::string_view strings() const { return {strings_.data(), strings_.size()}; }
  uint32_t document_flags() const { return flags_; }
  uint32_t version() const { return version_; }
  uint64_t line() const { return m_curr ? m_curr->pos.line : 0; }
  uint64_t column() const { return m_curr ? m_curr->pos.col : 0; }

  // ---- members the parse engine reads directly ------------------------------------------------
  c4::yml::id_type m_curr_doc = 0;
  c4::yml::TagDirectives& tag_directives() { return m_tag_directives; }
  c4::yml::TagCache& tag_cache() { return m_tag_cache; }

  // ---- parse lifecycle ------------------------------------------------------------------------
  void start_parse(const char* filename, c4::substr src) { this->_stack_start_parse(filename, src); }
  void finish_parse() { this->_stack_finish_parse(); }
  void cancel_parse() {
    while (m_stack.size() > 1) _pop();
  }

  // ---- stream and documents -------------------------------------------------------------------
  void begin_stream() {}
  void end_stream() {}
  void begin_doc() {
    document(0);
    if (_stack_should_push_on_begin_doc()) {
      _push();
      enable_<c4::yml::DOC>();
    }
  }
  void begin_doc_expl() {
    document(doc_explicit_start);
    _push();
    enable_<c4::yml::DOC>();
  }
  void end_doc() {
    if (_stack_should_pop_on_end_doc()) _pop();
    ++m_curr_doc;
  }
  void end_doc_expl() {
    flags_ |= doc_explicit_end;
    if (_stack_should_pop_on_end_doc()) _pop();
    ++m_curr_doc;
  }

  // ---- collections ----------------------------------------------------------------------------
  void begin_map_key_flow() { open_key(MYNA_VALUE_OBJECT, style_flow, c4::yml::KEY | c4::yml::MAP | c4::yml::FLOW_SL); }
  void begin_map_key_block() { open_key(MYNA_VALUE_OBJECT, style_block, c4::yml::KEY | c4::yml::MAP | c4::yml::BLOCK); }
  void begin_seq_key_flow() { open_key(MYNA_VALUE_ARRAY, style_flow, c4::yml::KEY | c4::yml::SEQ | c4::yml::FLOW_SL); }
  void begin_seq_key_block() { open_key(MYNA_VALUE_ARRAY, style_block, c4::yml::KEY | c4::yml::SEQ | c4::yml::BLOCK); }
  void begin_map_val_flow() { open(MYNA_VALUE_OBJECT, style_flow, c4::yml::MAP | c4::yml::FLOW_SL); }
  void begin_map_val_block() { open(MYNA_VALUE_OBJECT, style_block, c4::yml::MAP | c4::yml::BLOCK); }
  void begin_seq_val_flow() { open(MYNA_VALUE_ARRAY, style_flow, c4::yml::SEQ | c4::yml::FLOW_SL); }
  void begin_seq_val_block() { open(MYNA_VALUE_ARRAY, style_block, c4::yml::SEQ | c4::yml::BLOCK); }
  void end_map_block() { close(); }
  void end_map_flow(bool /*multiline*/, c4::yml::type_bits /*style*/ = c4::yml::FLOW_ML1) { close(); }
  void end_seq_block() { close(); }
  void end_seq_flow(bool /*multiline*/, c4::yml::type_bits /*style*/ = c4::yml::FLOW_ML1) { close(); }
  void add_sibling() { m_curr->evt_type = {}; }

  // ---- scalars --------------------------------------------------------------------------------
  void set_key_scalar_plain_empty() {
    key(m_src.first(0), scalar_style::plain, true);
    enable_<c4::yml::KEY | c4::yml::KEY_PLAIN | c4::yml::KEYNIL>();
  }
  void set_val_scalar_plain_empty() {
    value(m_src.first(0), scalar_style::plain, true);
    enable_<c4::yml::VAL | c4::yml::VAL_PLAIN | c4::yml::VALNIL>();
  }
  [[gnu::always_inline]] void set_key_scalar_plain(c4::csubstr s) {
    short_text w;
    if (!(short_plain(s, w) && key_fast(s, w))) key(s, scalar_style::plain, false);
    enable_<c4::yml::KEY | c4::yml::KEY_PLAIN>();
  }
  [[gnu::always_inline]] void set_val_scalar_plain(c4::csubstr s) {
    short_text w;
    if (!(short_plain(s, w) && value_fast(s, w))) value(s, scalar_style::plain, false);
    enable_<c4::yml::VAL | c4::yml::VAL_PLAIN>();
  }
  void set_key_scalar_dquoted(c4::csubstr s) {
    s = unicode_dquoted(s);
    short_text w;
    if (!(short_quoted(s, w) && key_quoted_fast(s, w, scalar_style::double_quoted))) key(s, scalar_style::double_quoted, false);
    enable_<c4::yml::KEY | c4::yml::KEY_DQUO>();
  }
  void set_val_scalar_dquoted(c4::csubstr s) {
    s = unicode_dquoted(s);
    short_text w;
    if (!(short_quoted(s, w) && value_quoted_fast(s, w, scalar_style::double_quoted))) value(s, scalar_style::double_quoted, false);
    enable_<c4::yml::VAL | c4::yml::VAL_DQUO>();
  }
  void set_key_scalar_squoted(c4::csubstr s) {
    short_text w;
    if (!(short_quoted(s, w) && key_quoted_fast(s, w, scalar_style::single_quoted))) key(s, scalar_style::single_quoted, false);
    enable_<c4::yml::KEY | c4::yml::KEY_SQUO>();
  }
  void set_val_scalar_squoted(c4::csubstr s) {
    short_text w;
    if (!(short_quoted(s, w) && value_quoted_fast(s, w, scalar_style::single_quoted))) value(s, scalar_style::single_quoted, false);
    enable_<c4::yml::VAL | c4::yml::VAL_SQUO>();
  }
  void set_key_scalar_literal(c4::csubstr s) {
    key(s, scalar_style::literal, false);
    enable_<c4::yml::KEY | c4::yml::KEY_LITERAL>();
  }
  void set_val_scalar_literal(c4::csubstr s) {
    value(s, scalar_style::literal, false);
    enable_<c4::yml::VAL | c4::yml::VAL_LITERAL>();
  }
  void set_key_scalar_folded(c4::csubstr s) {
    key(s, scalar_style::folded, false);
    enable_<c4::yml::KEY | c4::yml::KEY_FOLDED>();
  }
  void set_val_scalar_folded(c4::csubstr s) {
    value(s, scalar_style::folded, false);
    enable_<c4::yml::VAL | c4::yml::VAL_FOLDED>();
  }
  // Only reported when scalar filtering is off; the decoder always filters.
  void mark_key_scalar_unfiltered() { throw error(status_internal, "YAML scalar left unfiltered"); }
  void mark_val_scalar_unfiltered() { throw error(status_internal, "YAML scalar left unfiltered"); }

  // ---- anchors, aliases and tags (they precede the node they apply to) ------------------------
  void set_key_anchor(c4::csubstr anchor) {
    enable_<c4::yml::KEYANCH>();
    pending_anchor(anchor);
  }
  void set_val_anchor(c4::csubstr anchor) {
    enable_<c4::yml::VALANCH>();
    pending_anchor(anchor);
  }
  void set_key_ref(c4::csubstr ref) {
    enable_<c4::yml::KEY | c4::yml::KEYREF>();
    alias_key(ref);
  }
  void set_val_ref(c4::csubstr ref) {
    enable_<c4::yml::VAL | c4::yml::VALREF>();
    alias_value(ref);
  }
  void set_key_tag(c4::csubstr tag) {
    enable_<c4::yml::KEYTAG>();
    pending(tag);
  }
  void set_val_tag(c4::csubstr tag) {
    enable_<c4::yml::VALTAG>();
    pending(tag);
  }
  void add_directive_yaml(c4::csubstr version) { directive_version(version); }
  void add_directive_tag(c4::csubstr handle, c4::csubstr prefix) {
    if (!m_tag_directives.add(handle, prefix, m_curr_doc))
      throw syntax_error("too many YAML %TAG directives").at(line(), column());
    span_entry h = store(std::string_view(handle.str, handle.len));
    span_entry p = store(std::string_view(prefix.str, prefix.len));
    *directives_.extend(memory_, 1) = {h.offset, h.length, p.offset, p.length};
  }

  // ---- retroactive mappings -------------------------------------------------------------------
  // `[a: b]`: the previous sequence element is the first key of a new single-pair flow mapping.
  void actually_val_is_first_key_of_new_map_flow() {
    retro_map(style_flow);
    enable_<c4::yml::MAP | c4::yml::FLOW_SL>();
    _push();
  }
  // `[a]: b` at block level: a flow collection used as a key.
  void actually_val_is_first_key_of_new_map_block() {
    retro_map(style_block);
    _push();
  }

  // ---- rapidyaml arena: scalars that grow while filtering, resolved tags ----------------------
  c4::substr arena() { return c4::substr(arena_, arena_size_); }
  c4::substr arena_rem() { return c4::substr(arena_ + arena_size_, arena_capacity_ - arena_size_); }
  c4::substr alloc_arena(size_t len) {
    if (len > arena_capacity_ - arena_size_) grow_arena(arena_size_ + len);
    c4::substr s(arena_ + arena_size_, len);
    arena_size_ += len;
    return s;
  }
  void reserve_arena(size_t len) {
    if (len > arena_capacity_) grow_arena(len);
  }

  // ---- stack helpers used by EventHandlerStack and the engine ---------------------------------
  void _push() {
    _stack_push();
    m_curr->evt_type = {};
  }
  void _pop() { _stack_pop(); }
  template <c4::yml::type_bits bits>
  void enable_() noexcept { m_curr->evt_type |= bits; }
  template <c4::yml::type_bits bits>
  void disable_() noexcept { m_curr->evt_type &= ~bits; }
  template <c4::yml::type_bits bits>
  bool has_any_() const noexcept { return (m_curr->evt_type & bits) != c4::yml::type_bits(0); }

 private:
  enum class frame_kind : uint8_t { seq, map, pair };
  static constexpr uint32_t no_anchor = UINT32_MAX;
  static constexpr uint8_t no_template = 0xFF;
  static constexpr size_t none = SIZE_MAX;

  struct frame {
    size_t node = 0;
    uint64_t count = 0;  // seq: elements; map: entries (counted at the key); pair: children
    frame_kind kind = frame_kind::seq;
    bool pairs = false;       // a mapping stored as pairs
    uint32_t anchor = no_anchor;
    tag_kind tag = tag_kind::none;
    size_t merge = none;      // node of the `<<` key
    key_set keys;
    uint8_t follow = no_template;  // key template this mapping's keys have matched so far
    uint8_t record = no_template;  // key template slot recording this mapping's keys
  };
  // The last scalar or alias written into a sequence, kept so `[a: b]` can turn it into a key.
  struct last_value {
    bool valid = false;
    size_t node = 0;
    std::string_view text;
    scalar_style style = scalar_style::plain;
    bool empty = false;
    tag_kind tag = tag_kind::none;
    uint32_t tag_index = 0;
    uint64_t lexical = 0;
    uint32_t anchor = no_anchor;
    bool alias = false;
    uint32_t ordinal = 0;
  };

  [[noreturn]] void fail(std::string_view message) { throw syntax_error(message).at(line(), column()); }

  bool in_input(const char* p, size_t n) const { return p && p >= input_ && p + n <= input_ + length_; }

  static ct::span unpack(uint64_t span) { return {uint32_t(span >> 32), uint32_t(span)}; }
  // Metadata indexes compact word offsets; payload words always carry zero metadata. Raw spans
  // live beside owned strings and in scalar headers, so no lexical/hash side tables are needed.
  // Strict mappings never rewrite child ranges. Zero their reserved metadata once, then only
  // non-plain/tagged/anchored nodes need a store. Growing a short reservation zeros the new tail.
  void zero_meta(size_t end) {
    if (end > meta_.size()) {
      size_t count = end - meta_.size();
      std::memset(meta_.extend(memory_, count), 0, count * sizeof(uint32_t));
    }
  }
  [[gnu::always_inline]] void side(size_t node, uint32_t meta) {
    if (sparse_meta_) {
      if (meta != 0) { zero_meta(writer_->word_count()); meta_[node] = meta; }
      return;
    }
    size_t count = writer_->word_count() - meta_.size();
    if (count == 0 || count > 2) throw error(status_internal, "YAML metadata alignment");
    uint32_t* entries = meta_.extend(memory_, count);
    entries[0] = meta;
    if (count == 2) entries[1] = 0;
  }
  static uint32_t myna_kind(uint64_t word) {
    switch (ct::kind(word)) {
      case ct::kind_null: case ct::kind_ref: return MYNA_VALUE_NULL;
      case ct::kind_false: case ct::kind_true: return MYNA_VALUE_BOOL;
      case ct::kind_i64: return MYNA_VALUE_I64;
      case ct::kind_u64: return MYNA_VALUE_U64;
      case ct::kind_f64: return MYNA_VALUE_F64;
      case ct::kind_decimal: return MYNA_VALUE_DECIMAL;
      case ct::kind_string: return MYNA_VALUE_STRING;
      case ct::kind_array: return MYNA_VALUE_ARRAY;
      default: return MYNA_VALUE_OBJECT;
    }
  }
  static uint64_t raw_span(const uint64_t* words, size_t index) {
    uint64_t w = words[index];
    uint32_t kind = ct::kind(w);
    if ((kind == ct::kind_string || kind == ct::kind_decimal) && (w & ct::owned_flag)) return words[index + 1];
    if (kind <= ct::kind_string) return pack(ct::low(w), ct::field(w));
    return 0;
  }
  size_t step(size_t index) const { return ct::step(index, writer_->words()[index]); }

  // Copies text into the document's string table.
  span_entry store(std::string_view text) {
    if (strings_.size() + text.size() > UINT32_MAX) throw limit_error("text bytes");
    span_entry e{uint32_t(strings_.size()), uint32_t(text.size())};
    if (!text.empty()) std::memcpy(strings_.extend(memory_, text.size()), text.data(), text.size());
    return e;
  }
  std::string_view string_at(uint32_t offset, uint32_t length) const { return {strings_.data() + offset, length}; }

  void document(uint32_t flags) {
    if (++docs_ > 1) {
      throw error(status_multiple_documents, "YAML input holds more than one document; decode it as a stream")
          .at(line(), column());
    }
    flags_ |= flags | doc_present;
  }

  void directive_version(c4::csubstr version) {
    std::string_view v(version.str, version.len);
    size_t dot = v.find('.');
    uint32_t major = 0, minor = 0;
    auto r1 = std::from_chars(v.data(), v.data() + (dot == std::string_view::npos ? v.size() : dot), major);
    if (dot == std::string_view::npos || r1.ec != std::errc()) fail("invalid YAML %YAML directive");
    auto r2 = std::from_chars(v.data() + dot + 1, v.data() + v.size(), minor);
    if (r2.ec != std::errc() || r2.ptr != v.data() + v.size()) fail("invalid YAML %YAML directive");
    if (major != 1) fail("unsupported YAML version (only 1.x)");
    version_ = (major << 16) | (minor > 0xffff ? 0xffff : minor);
    // VersionDirective.HONOR: a 1.1 document resolves with the 1.1 schema (and merge keys).
    if (rules_.honor_version && minor == 1) schema_ = schema::yaml11;
  }

  bool merges() const { return rules_.merge_keys || schema_ == schema::yaml11; }
  // Ordinary REJECT mappings already sort and compare decoded keys in their compact directory.
  // Rewrite policies still need the incremental table to locate earlier entries before closing.
  bool fused_duplicates() const {
    return fused_duplicates_ && !rules_.last_wins && !rules_.duplicate_keys_pairs && !rules_.complex_keys_pairs && !merges();
  }

  // ---- tags -----------------------------------------------------------------------------------
  uint32_t intern(std::string_view uri) {
    uint32_t hash = full_key_hash(uri);
    uint32_t found = tag_index_.find(uri, hash, strings_.data());
    if (found != UINT32_MAX) return found;
    if (tags_.size() + 1 >= meta_tag_limit) throw limit_error("tags");
    span_entry e = store(uri);
    *tags_.extend(memory_, 1) = e;
    uint32_t index = uint32_t(tags_.size());  // 1-based
    tag_index_.put(memory_, hash, e.offset, e.length, index, strings_.data());
    return index;
  }

  void pending(c4::csubstr tag) {
    // rapidyaml's TagCache::add is noexcept (RYML_NOEXCEPT with assertions off) and grows through
    // the allocation callback, so a failing allocation there would terminate. The engine has just
    // added this tag; keeping the cache within its 16-entry inline storage means it never allocates.
    if (m_tag_cache.m_entries.size() >= c4::yml::TagCache::Entries::sso_size - 1) m_tag_cache.clear();
    std::string_view uri = tag_uri(std::string_view(tag.str, tag.len));
    tag_kind kind = classify_tag(uri);
    if (kind == tag_kind::other && rules_.reject_unknown_tags) fail("YAML unknown tag");
    pending_tag_ = kind;
    pending_tag_index_ = intern(uri);
  }

  struct taken_tag {
    tag_kind kind;
    uint32_t index;
  };
  taken_tag take_tag() {
    taken_tag t{pending_tag_, pending_tag_index_};
    pending_tag_ = tag_kind::none;
    pending_tag_index_ = 0;
    return t;
  }

  // ---- anchors and aliases --------------------------------------------------------------------
  void pending_anchor(c4::csubstr anchor) {
    pending_anchor_ = store(std::string_view(anchor.str, anchor.len));
    has_pending_anchor_ = true;
  }

  // Defines the pending anchor on `node` (closed for scalars, open for collections until close).
  uint32_t define_anchor(size_t node, bool open) {
    if (C4_LIKELY(!has_pending_anchor_)) return no_anchor;
    return bind_anchor(node, open);
  }
  [[gnu::noinline]] uint32_t bind_anchor(size_t node, bool open) {
    has_pending_anchor_ = false;
    uint32_t id = uint32_t(anchors_.size());
    *anchors_.extend(memory_, 1) = {node, pending_anchor_.offset, pending_anchor_.length};
    *anchor_open_.extend(memory_, 1) = open ? 1 : 0;
    std::string_view name = string_at(pending_anchor_.offset, pending_anchor_.length);
    bindings_.put(memory_, full_key_hash(name), pending_anchor_.offset, pending_anchor_.length, id, strings_.data());
    return id;
  }

  // Resolves `*name` to its anchor id: undefined, recursive (still open) or rejected aliases fail.
  uint32_t resolve_alias(c4::csubstr ref) {
    std::string_view name(ref.str, ref.len);
    if (!name.empty() && name[0] == '*') name.remove_prefix(1);
    if (rules_.reject_aliases) fail("YAML aliases are rejected");
    uint32_t id = bindings_.find(name, full_key_hash(name), strings_.data());
    if (id == UINT32_MAX) fail("undefined YAML alias");
    if (anchor_open_[id]) fail("recursive YAML alias");
    if (alias_targets_.size() >= rules_.max_aliases) throw limit_error("aliases");
    if (alias_targets_.size() + 1 >= meta_tag_limit) throw limit_error("aliases");
    return id;
  }
  uint32_t add_alias_target(uint64_t node) {
    uint32_t ordinal = uint32_t(alias_targets_.size());
    *alias_targets_.extend(memory_, 1) = node;
    return ordinal;
  }

  void alias_value(c4::csubstr ref) {
    uint32_t id = resolve_alias(ref);
    frame* f = top();
    if (!f && root_written_) throw error(status_internal, "YAML second root value");
    if (f && f->kind == frame_kind::map && f->pairs) throw error(status_internal, "YAML value without a key");
    take_tag();
    has_pending_anchor_ = false;
    size_t node = writer_->word_count();
    uint32_t ordinal = add_alias_target(anchors_[id].node);
    writer_->ref(anchors_[id].node);
    side(node, meta_alias | (ordinal << meta_tag_shift));
    last_ = {};
    if (f && f->kind == frame_kind::seq) {
      last_.valid = true;
      last_.node = node;
      last_.alias = true;
      last_.ordinal = ordinal;
    }
    child_done(f);
  }

  // An alias used as a key: a STRING key node with the target scalar's key text, the alias bit and
  // the ordinal. An alias to a collection is a complex key.
  void alias_key(c4::csubstr ref) {
    uint32_t id = resolve_alias(ref);
    take_tag();
    has_pending_anchor_ = false;
    uint32_t ordinal = add_alias_target(anchors_[id].node);
    write_alias_key(ordinal);
  }

  void write_alias_key(uint32_t ordinal) {
    frame* f = top();
    if (!f || f->kind != frame_kind::map) throw error(status_internal, "YAML key outside a mapping");
    uint64_t target = alias_targets_[ordinal];
    uint32_t tag = myna_kind(writer_->words()[target]);
    if (tag == MYNA_VALUE_ARRAY || tag == MYNA_VALUE_OBJECT) {
      complex_key_start(*f);
      size_t node = writer_->word_count();
      writer_->ref(target);
      side(node, meta_alias | (ordinal << meta_tag_shift));
      child_done(top());  // the pair's key
      return;
    }
    char buffer[40];
    key_kind kind;
    std::string_view text = node_key_text(target, buffer, kind);
    if (text.data() == buffer) text = stable_copy(text);
    place_key(*f, text, kind, 0, meta_alias | (ordinal << meta_tag_shift) | (uint32_t(kind) << meta_key_kind_shift), false);
  }

  // Key text and kind of an existing scalar node (an alias target).
  std::string_view node_key_text(uint64_t index, char (&buffer)[40], key_kind& kind) const {
    const uint64_t w = writer_->words()[index];
    uint32_t meta = index < meta_.size() ? meta_[index] : 0;
    scalar r;
    r.kind = myna_kind(w);
    r.bits = ct::kind(w) == ct::kind_true ? 1 : (ct::is_wide(w) ? writer_->words()[index + 1] : 0);
    switch (r.kind) {
      case MYNA_VALUE_STRING:
        kind = key_kind((meta & meta_key_kind_mask) >> meta_key_kind_shift);
        return writer_->text_of(index);
      case MYNA_VALUE_DECIMAL: {
        std::string_view t = writer_->text_of(index);
        kind = t.find_first_of(".eE") == std::string_view::npos ? key_kind::integer : key_kind::floating;
        return t;
      }
      case MYNA_VALUE_NULL: kind = key_kind::null; break;
      case MYNA_VALUE_BOOL: kind = key_kind::boolean; break;
      case MYNA_VALUE_F64: {
        kind = key_kind::floating;
        double d;
        std::memcpy(&d, &r.bits, sizeof d);
        r.nonfinite = !std::isfinite(d);
        break;
      }
      default: kind = key_kind::integer; break;
    }
    return key_text(r, buffer);
  }

  // ---- fast paths: short untagged plain scalars -------------------------------------------------
  // The common scalar is an untagged, unanchored plain scalar of 1-16 bytes in the input, decoded
  // under CORE inside a mapping stored as an object or a sequence. Its bytes are loaded once as two
  // words (the session pads the input, so the 16-byte read is safe; bytes past the scalar are
  // masked) and serve the span (no space or LF: single-line, the source token), the
  // classification, key hash cache, ASCII detection and compact string reference. Each fast path
  // produces exactly the general path's compact words and metadata; anything else (and every
  // duplicate key) returns false and takes the general path.
  struct short_text {
    uint64_t lo, hi;
  };
  struct hash_slot {
    uint64_t lo, hi;
    uint32_t length;  // UINT32_MAX: empty
    uint32_t hash;
  };
  static constexpr size_t hash_cache_slots = 256;
  static constexpr size_t template_levels = 8;
  static constexpr uint32_t template_keys = 16;
  struct template_key {
    uint64_t lo, hi;
    uint32_t length, hash;
    const char* data;
  };
  struct key_template {
    uint32_t count = 0;
    template_key keys[template_keys];
  };

  static constexpr uint64_t word(const char* s, size_t n) {
    uint64_t w = 0;
    for (size_t i = 0; i < n; ++i) w |= uint64_t(uint8_t(s[i])) << (8 * i);
    return w;
  }

  [[gnu::always_inline]] bool short_plain(c4::csubstr s, short_text& w) const {
    if (!fast_paths_ || pending_tag_ != tag_kind::none || has_pending_anchor_ || schema_ != schema::core) return false;
    if (s.len == 0 || s.len > 16 || !in_input(s.str, s.len)) return false;
#if defined(__x86_64__)
    const __m128i positions = _mm_setr_epi8(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
    const __m128i relevant = _mm_cmpgt_epi8(_mm_set1_epi8(char(s.len)), positions);
    __m128i bytes = _mm_loadu_si128(reinterpret_cast<const __m128i*>(s.str));
    // Unsigned saturated subtraction maps exactly the bytes <= space to zero.
    const __m128i bad = _mm_cmpeq_epi8(_mm_subs_epu8(bytes, _mm_set1_epi8(0x20)), _mm_setzero_si128());
    if (_mm_movemask_epi8(_mm_and_si128(bad, relevant))) return false;
    bytes = _mm_and_si128(bytes, relevant);
    w = {uint64_t(_mm_cvtsi128_si64(bytes)), uint64_t(_mm_cvtsi128_si64(_mm_srli_si128(bytes, 8)))};
#else
    uint64_t lo, hi;
    std::memcpy(&lo, s.str, 8);
    std::memcpy(&hi, s.str + 8, 8);
    const uint64_t lo_mask = low_bytes(s.len), hi_mask = low_bytes(s.len > 8 ? s.len - 8 : 0);
    // Any byte <= 0x20 (space, LF, also TAB and CR, which only send a scalar to the general path)
    // within the scalar: exact for "any" (a borrow only follows a true hit in a lower byte).
    constexpr uint64_t ones = 0x0101010101010101ull, highs = 0x8080808080808080ull;
    if ((((lo - ones * 0x21) & ~lo & highs) & lo_mask) | (((hi - ones * 0x21) & ~hi & highs) & hi_mask)) return false;
    w = {lo & lo_mask, hi & hi_mask};
#endif
    return true;
  }

  // An unsigned decimal of 1-8 digits (bytes [0, n) of `lo`, the rest zero), converted with SWAR
  // multiplies: digits are checked a word at a time, padded with leading '0's and folded in pairs.
  [[gnu::always_inline]] static bool decimal8(uint64_t lo, size_t n, uint64_t& value) {
    const uint64_t shift = 8 * (8 - n);
    // Leading zeros fill the low bytes (the first characters); the digits move to the high bytes.
    uint64_t x = n == 8 ? lo : (lo << shift) | (0x3030303030303030ull & ((uint64_t(1) << shift) - 1));
    if (((x + 0x4646464646464646ull) | (x - 0x3030303030303030ull)) & 0x8080808080808080ull) return false;
    x -= 0x3030303030303030ull;
    x = (x * 10) + (x >> 8);
    x = (((x & 0x000000FF000000FFull) * 0x000F424000000064ull) +
         (((x >> 16) & 0x000000FF000000FFull) * 0x0000271000000001ull)) >> 32;
    value = x;
    return true;
  }

  // `[0-9]*\.[0-9]*` of n <= 8 bytes with at least one digit: the digits without the point as an
  // integer (high bits) and the fraction digit count (low byte). False for anything else.
  [[gnu::always_inline]] static bool decimal_point8(uint64_t lo, size_t n, uint64_t& packed) {
    if (n < 2) return false;
    const uint64_t dots = detail::equal(lo, '.') & low_bytes(n);
    if (!dots || (dots & (dots - 1))) return false;  // exactly one '.'
    const size_t at = size_t(__builtin_ctzll(dots)) / 8;
    // Remove the '.' byte: bytes above it move down one place.
    const uint64_t below = lo & low_bytes(at);
    const uint64_t above = at + 1 < 8 ? (lo >> (8 * (at + 1))) << (8 * at) : 0;
    uint64_t value;
    if (!decimal8(below | above, n - 1, value)) return false;
    packed = (value << 8) | uint64_t(n - 1 - at);
    return true;
  }

  // Quoted scalars are strings under every schema; without a retained source their span is 0. A
  // short one still in the input (rapidyaml filtered it in place, so it did not grow) is loaded the
  // same way as a short plain scalar.
  [[gnu::always_inline]] bool short_quoted(c4::csubstr s, short_text& w) const {
    if (!fast_paths_ || pending_tag_ != tag_kind::none || has_pending_anchor_ || pristine_) return false;
    if (s.len == 0 || s.len > 16 || !in_input(s.str, s.len)) return false;
    uint64_t lo, hi;
    std::memcpy(&lo, s.str, 8);
    std::memcpy(&hi, s.str + 8, 8);
    w = {lo & low_bytes(s.len), hi & low_bytes(s.len > 8 ? s.len - 8 : 0)};
    return true;
  }

  // True when a plain scalar is certainly a string under CORE: it cannot start a number or `~`,
  // and it is not one of the null or bool spellings (resolve_plain's forms).
  [[gnu::always_inline]] static bool core_string(uint64_t lo, size_t n) {
    const unsigned c = unsigned(lo & 0xff);
    if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == '~') return false;
    if (n == 4)
      return lo != word("null", 4) && lo != word("Null", 4) && lo != word("NULL", 4) && lo != word("true", 4) &&
             lo != word("True", 4) && lo != word("TRUE", 4);
    if (n == 5) return lo != word("false", 5) && lo != word("False", 5) && lo != word("FALSE", 5);
    return true;
  }

  // Full FNV hash of short text, memoized by its exact bytes and length (keys repeat).
  [[gnu::always_inline]] uint32_t cached_hash(short_text w, const char* text, size_t n) {
    uint64_t mix = (w.lo ^ (w.hi * 0x9E3779B97F4A7C15ull) ^ n) * 0xff51afd7ed558ccdull;
    hash_slot& slot = hash_cache_[mix >> 56];
    if (C4_LIKELY(slot.lo == w.lo && slot.hi == w.hi && slot.length == n)) return slot.hash;
    uint32_t hash = full_key_hash(std::string_view(text, n));
    slot = {w.lo, w.hi, uint32_t(n), hash};
    return hash;
  }

  uint32_t directory_hash(std::string_view text) {
    const bool padded = in_input(text.data(), text.size()) ||
        (pristine_ && text.data() >= pristine_ && text.data() + text.size() <= pristine_ + length_);
    if (text.size() <= 16 && padded) {
      short_text w;
      std::memcpy(&w.lo, text.data(), 8);
      std::memcpy(&w.hi, text.data() + 8, 8);
      w.lo &= low_bytes(text.size());
      w.hi &= low_bytes(text.size() > 8 ? text.size() - 8 : 0);
      return cached_hash(w, text.data(), text.size());
    }
    return full_key_hash(text);
  }

  [[gnu::always_inline]] bool key_fast(c4::csubstr s, short_text w) {
    if (C4_UNLIKELY(!depth_)) return false;
    frame& f = frames_[depth_ - 1];
    if (f.kind != frame_kind::map || f.pairs || !core_string(w.lo, s.len)) return false;
    if (rules_.merge_keys && s.len == 2 && w.lo == word("<<", 2)) return false;
    return fast_key(f, s, w, pack(size_t(s.str - input_), s.len), 0);
  }

  [[gnu::always_inline]] bool key_quoted_fast(c4::csubstr s, short_text w, scalar_style style) {
    if (C4_UNLIKELY(!depth_)) return false;
    frame& f = frames_[depth_ - 1];
    if (f.kind != frame_kind::map || f.pairs) return false;
    return fast_key(f, s, w, 0, uint32_t(style));
  }

  // A string key of mapping `f` (stored as an object) whose text is the loaded words: follows or
  // records the level's key template, else hashes (memo) and checks duplicates. Returns false,
  // having written nothing, on a duplicate, which the general path then reports or resolves.
  [[gnu::always_inline]] bool fast_key(frame& f, c4::csubstr s, short_text w, uint64_t span, uint32_t meta) {
    const size_t level = depth_index(f);
    if (f.follow != no_template) {
      // Following a template: key i equal to the template's key i keeps the keys distinct.
      const key_template& t = templates_[level][f.follow];
      const size_t i = size_t(f.count);
      if (C4_LIKELY(i < t.count && t.keys[i].lo == w.lo && t.keys[i].hi == w.hi && t.keys[i].length == s.len)) {
        place_fast_key(f, s, w, t.keys[i].hash, span, meta);
        return true;
      }
      leave_template(f);
    } else if (f.count == 0 && level < template_levels && !rules_.last_wins) {
      for (uint8_t slot = 0; slot < 2; ++slot) {
        const key_template& t = templates_[level][slot];
        if (t.count && t.keys[0].lo == w.lo && t.keys[0].hi == w.hi && t.keys[0].length == s.len) {
          f.follow = slot;
          place_fast_key(f, s, w, t.keys[0].hash, span, meta);
          return true;
        }
      }
      f.record = template_next_[level];
      templates_[level][f.record].count = 0;
    }
    const uint32_t hash = cached_hash(w, s.str, s.len);
    if (!fused_duplicates() && C4_UNLIKELY(!f.keys.insert(memory_, hash, std::string_view(s.str, s.len)))) return false;
    if (f.record != no_template) {
      key_template& t = templates_[level][f.record];
      if (t.count == f.count && t.count < template_keys) t.keys[t.count++] = {w.lo, w.hi, uint32_t(s.len), hash, s.str};
      else drop_record(f);
    }
    place_fast_key(f, s, w, hash, span, meta);
    return true;
  }

  [[gnu::always_inline]] void place_fast_key(frame& f, c4::csubstr s, short_text w, uint32_t hash, uint64_t span,
                                             uint32_t meta) {
    const bool plain = ((w.lo | w.hi) & 0x8080808080808080ull) == 0;
    const size_t node = writer_->word_count();
    if (fused_duplicates()) writer_->member_key(f.node, hash);
    // short_plain/short_quoted already proved this padded input span and loaded its bytes.
    if ((meta & meta_style_mask) == 0) writer_->string_ref(unpack(span), plain, true);
    else writer_->string(std::string_view(s.str, s.len), {}, plain, true);
    side(node, meta);
    ++f.count;
  }

  // ---- key templates ----------------------------------------------------------------------------
  // Mappings at one nesting level usually repeat the same keys in one or two orders (records). Each
  // of the first template_levels levels keeps the keys of the last two mappings whose keys all took
  // the fast path (at most 16, distinct by the duplicate check). A new mapping whose first key equals
  // a template's first key follows it: while key i equals the template's key i (same bytes and
  // length) the keys so far are a copy of a distinct prefix, so neither the duplicate check nor the
  // hash is needed; the hash comes from the template. On the first difference (or any key taking
  // the general path) the matched prefix is inserted into the mapping's key set from the template
  // (same bytes) and decoding continues as usual. Templates never outlive a decode.
  void leave_template(frame& f) {
    if (f.follow != no_template) {
      const key_template& t = templates_[depth_index(f)][f.follow];
      if (!fused_duplicates()) for (size_t k = 0; k < size_t(f.count); ++k)
        f.keys.insert(memory_, t.keys[k].hash, std::string_view(t.keys[k].data, t.keys[k].length));
      f.follow = no_template;
    }
    drop_record(f);
  }
  void drop_record(frame& f) {
    if (f.record != no_template) {
      templates_[depth_index(f)][f.record].count = 0;
      f.record = no_template;
    }
  }
  // A recorded mapping closes: its template becomes the newest at its level.
  void finish_template(frame& f) {
    const size_t level = depth_index(f);
    if (templates_[level][f.record].count == f.count && f.count) template_next_[level] = uint8_t(f.record ^ 1);
    else templates_[level][f.record].count = 0;
    f.record = no_template;
  }
  size_t depth_index(const frame& f) const { return size_t(&f - frames_); }

  [[gnu::always_inline]] bool value_quoted_fast(c4::csubstr s, short_text w, scalar_style style) {
    if (C4_UNLIKELY(!depth_)) return false;
    frame& f = frames_[depth_ - 1];
    if (f.kind == frame_kind::pair || (f.kind == frame_kind::map && f.pairs)) return false;
    const size_t node = writer_->word_count();
    const bool plain = ((w.lo | w.hi) & 0x8080808080808080ull) == 0;
    writer_->string(std::string_view(s.str, s.len), {}, plain, false);
    side(node, uint32_t(style));
    if (f.kind == frame_kind::seq) {
      last_ = {true, node, std::string_view(s.str, s.len), style, false, tag_kind::none, 0, 0, no_anchor, false, 0};
      ++f.count;
    } else {
      last_.valid = false;
    }
    return true;
  }

  [[gnu::always_inline]] bool value_fast(c4::csubstr s, short_text w) {
    if (C4_UNLIKELY(!depth_)) return false;
    frame& f = frames_[depth_ - 1];
    if (f.kind == frame_kind::pair || (f.kind == frame_kind::map && f.pairs)) return false;
    const std::string_view text(s.str, s.len);
    const size_t node = writer_->word_count();
    const bool plain = ((w.lo | w.hi) & 0x8080808080808080ull) == 0;
    const uint64_t span = pack(size_t(s.str - input_), s.len);
    const ct::span token = unpack(span);
    uint64_t digits;
    if (C4_LIKELY(core_string(w.lo, s.len))) {
      writer_->string_ref(token, plain, false);
    } else if (s.len <= 8 && decimal8(w.lo, s.len, digits)) {
      writer_->integer(int64_t(digits), token);  // core_int: `[0-9]{1,8}` is that I64
    } else if (s.len <= 8 && decimal_point8(w.lo, s.len, digits)) {
      // `[0-9]*\.[0-9]*` with 1-7 digits: resolve_core_fast's exactly rounded quotient.
      double v = double(digits >> 8) / detail::exact_powers[digits & 0xff];
      writer_->floating(v, token);
    } else if (s.len == 4 && (w.lo == word("null", 4) || w.lo == word("Null", 4) || w.lo == word("NULL", 4))) {
      writer_->null(token);
    } else if ((s.len == 4 && (w.lo == word("true", 4) || w.lo == word("True", 4) || w.lo == word("TRUE", 4))) ||
               (s.len == 5 && (w.lo == word("false", 5) || w.lo == word("False", 5) || w.lo == word("FALSE", 5)))) {
      writer_->boolean(s.len == 4, token);
    } else {
      scalar r;
      if (!resolve_core_fast(text, r)) return false;
      write_value(r, pack(size_t(s.str - input_), s.len), scalar_style::plain);
    }
    side(node, 0);
    if (f.kind == frame_kind::seq) {
      last_ = {true, node, text, scalar_style::plain, false, tag_kind::none, 0, span, no_anchor, false, 0};
      ++f.count;
    } else {
      last_.valid = false;
    }
    return true;
  }

  // ---- lexical spans --------------------------------------------------------------------------
  static bool space(char c) { return c == ' ' || c == '\t' || c == '\r' || c == '\n'; }
  // blank() of n <= 16 bytes that lie in the padded input: two word loads, bytes past n masked.
  [[gnu::always_inline]] static bool blank16(const char* text, size_t n) {
    uint64_t lo, hi;
    std::memcpy(&lo, text, 8);
    std::memcpy(&hi, text + 8, 8);
    uint64_t lo_hits = (detail::equal(lo, ' ') | detail::equal(lo, '\n')) & low_bytes(n);
    uint64_t hi_hits = (detail::equal(hi, ' ') | detail::equal(hi, '\n')) & low_bytes(n > 8 ? n - 8 : 0);
    return (lo_hits | hi_hits) != 0;
  }
  // Mask of the first min(n, 8) bytes of a little-endian word.
  [[gnu::always_inline]] static uint64_t low_bytes(size_t n) {
    return n >= 8 ? ~uint64_t(0) : (uint64_t(1) << (8 * n)) - 1;
  }
  // True when text[0, n) holds a space or an LF (exact SWAR masks, eight bytes per step).
  static bool blank(const char* text, size_t n) {
    uint64_t hits = 0;
    size_t i = 0;
    for (; i + 8 <= n; i += 8) {
      uint64_t x;
      std::memcpy(&x, text + i, 8);
      hits |= detail::equal(x, ' ') | detail::equal(x, '\n');
    }
    if (i < n) {
      if (n >= 8) {
        uint64_t x;
        std::memcpy(&x, text + n - 8, 8);
        hits |= detail::equal(x, ' ') | detail::equal(x, '\n');
      } else {
        for (; i < n; ++i) hits |= text[i] == ' ' || text[i] == '\n';
      }
    }
    return hits != 0;
  }
  static uint64_t pack(size_t offset, size_t length) { return (uint64_t(offset) << 32) | uint64_t(length); }

  // Spans index the input: plain single-line scalars always; quoted, multi-line plain and block
  // scalars only when a pristine copy of the source is retained (rapidyaml rewrote them in place).
  [[gnu::always_inline]] uint64_t lexical(const char* text, size_t length, scalar_style style, bool empty) const {
    // Plain scalars in the input holding no space or LF (numbers, identifiers) were single-line:
    // their bytes are the source token. The padded input makes a 16-byte read past them safe.
    if (C4_LIKELY(style == scalar_style::plain && !empty && in_input(text, length) && length <= 16 &&
                  !blank16(text, length)))
      return pack(size_t(text - input_), length);
    return lexical_slow(text, length, style, empty);
  }
  [[gnu::noinline]] uint64_t lexical_slow(const char* text, size_t length, scalar_style style, bool empty) const {
    if (empty || !text) return 0;
    const bool located = in_input(text, length);
    size_t position = m_curr->pos.offset;
    if (position > length_) position = length_;
    switch (style) {
      case scalar_style::plain: {
        if (!located) return 0;
        size_t begin = size_t(text - input_), end = begin + length;
        // Folding turns every line break into a space or LF: a scalar holding neither (numbers,
        // identifiers) was single-line and its bytes are the source token. Eight bytes per step.
        if (C4_LIKELY(!blank(text, length))) return pack(begin, length);
        // The source token ends at the last non-space byte before the parser position; bytes
        // between the filtered end and there are the folded scalar's leftover tail.
        size_t source_end = position;
        while (source_end > end && space(input_[source_end - 1])) --source_end;
        if (source_end < end) source_end = end;
        if (!has_newline(newlines_, begin, source_end)) return pack(begin, length);
        return pristine_ ? pack(begin, source_end - begin) : 0;
      }
      case scalar_style::single_quoted:
      case scalar_style::double_quoted: {
        if (!pristine_ || !located || text == input_) return 0;
        size_t begin = size_t(text - input_) - 1;
        char quote = style == scalar_style::single_quoted ? '\'' : '"';
        if (pristine_[begin] != quote || position <= begin + 1 || pristine_[position - 1] != quote) return 0;
        return pack(begin, position - begin);
      }
      case scalar_style::literal:
      case scalar_style::folded: {
        if (!pristine_ || !located) return 0;
        // The header line (`|`, `>-`, `!tag |2 # comment`) is the line before the content.
        size_t content = size_t(text - input_);
        size_t at = content;
        while (at > 0 && pristine_[at - 1] != '\n') --at;  // start of the content's line
        if (at == 0) return 0;
        size_t header_end = at - 1, header = header_end;
        while (header > 0 && pristine_[header - 1] != '\n') --header;
        size_t indicator = SIZE_MAX;
        for (size_t i = header; i < header_end; ++i) {
          char c = pristine_[i];
          if (c == '#' && i > header && (pristine_[i - 1] == ' ' || pristine_[i - 1] == '\t')) break;
          if (c == '|' || c == '>') indicator = i;
        }
        if (indicator == SIZE_MAX) return 0;
        size_t end = position;
        while (end > indicator + 1 && space(pristine_[end - 1])) --end;
        return pack(indicator, end - indicator);
      }
    }
    return 0;
  }

  // rapidyaml filters JSON-style surrogate escapes as separate three-byte UTF-8 sequences.
  // Accept a paired UTF-16 escape as one Unicode scalar (JSON compatibility), and reject all
  // unpaired surrogates/out-of-range escapes before publishing validated text to the tape. The
  // parser owns this mutable decoded buffer; repairing it only shrinks the scalar in place.
  c4::csubstr unicode_dquoted(c4::csubstr s) {
    if (s.empty() || simdutf::validate_utf8(s.str, s.len)) return s;
    char* out = const_cast<char*>(s.str);
    size_t read = 0, written = 0;
    while (read < s.len) {
      const auto* p = reinterpret_cast<const uint8_t*>(s.str + read);
      if (s.len - read >= 3 && p[0] == 0xED && p[1] >= 0xA0 && p[1] <= 0xBF && (p[2] & 0xC0) == 0x80) {
        if (p[1] >= 0xB0 || s.len - read < 6 || p[3] != 0xED || p[4] < 0xB0 || p[4] > 0xBF || (p[5] & 0xC0) != 0x80)
          fail("unpaired surrogate in a YAML quoted scalar");
        uint32_t high = 0xD000u | uint32_t(p[1] & 0x3F) << 6 | uint32_t(p[2] & 0x3F);
        uint32_t low = 0xD000u | uint32_t(p[4] & 0x3F) << 6 | uint32_t(p[5] & 0x3F);
        written += encode_utf8(0x10000u + ((high - 0xD800u) << 10) + (low - 0xDC00u), out + written);
        read += 6;
      } else out[written++] = s.str[read++];
    }
    if (!simdutf::validate_utf8(out, written)) fail("invalid Unicode escape in a YAML quoted scalar");
    return {out, written};
  }

  // ---- node writing ---------------------------------------------------------------------------
  // Resolves a scalar with its tag: 1.1 type tags and unknown tags are validated here and stored as
  // strings; collection tags on scalars are errors.
  [[gnu::noinline]] scalar resolve_now(std::string_view text, scalar_style style, tag_kind tag) {
    switch (tag) {
      case tag_kind::binary:
        if (!valid_base64(text)) fail("invalid base64 in a YAML !!binary scalar");
        return resolve(text, style, schema_, tag_kind::str, memory_);
      case tag_kind::timestamp:
        if (!valid_timestamp(text)) fail("invalid YAML !!timestamp scalar");
        return resolve(text, style, schema_, tag_kind::str, memory_);
      case tag_kind::merge:
        if (text != "<<") fail("YAML !!merge tag on a scalar other than <<");
        return resolve(text, style, schema_, tag_kind::str, memory_);
      case tag_kind::value:
        if (text != "=") fail("YAML !!value tag on a scalar other than =");
        return resolve(text, style, schema_, tag_kind::str, memory_);
      case tag_kind::set:
      case tag_kind::omap:
      case tag_kind::pairs:
      case tag_kind::seq:
      case tag_kind::map:
        fail("YAML collection tag on a scalar");
      default:
        return resolve(text, style, schema_, tag, memory_);
    }
  }

  [[gnu::always_inline]] void write_text(std::string_view text, bool key = false, uint64_t span = 0, bool plain = false) {
    ct::span raw = unpack(span);
    if (plain && in_input(text.data(), text.size()) && raw.offset == size_t(text.data() - input_) && raw.length == text.size())
      writer_->string_ref(raw, ascii_text(text), key);
    else writer_->string(text, raw, ascii_text(text), key);
  }

  [[gnu::always_inline]] void write_value(const scalar& r, uint64_t span, scalar_style style) {
    const ct::span token = unpack(span);
    switch (r.kind) {
      case MYNA_VALUE_NULL: writer_->null(token); break;
      case MYNA_VALUE_BOOL: writer_->boolean(r.bits != 0, token); break;
      case MYNA_VALUE_I64: writer_->integer(int64_t(r.bits), token); break;
      case MYNA_VALUE_U64: writer_->unsigned_integer(r.bits, token); break;
      case MYNA_VALUE_F64: {
        double d;
        std::memcpy(&d, &r.bits, sizeof d);
        if (r.nonfinite) writer_->nonfinite(d, token);
        else writer_->floating(d, token);
        break;
      }
      case MYNA_VALUE_DECIMAL: writer_->decimal(r.text, token); break;
      default: write_text(r.text, false, span, style == scalar_style::plain); break;
    }
  }

  static uint32_t meta_word(uint32_t style, key_kind kind, uint32_t tag_index) {
    return style | (uint32_t(kind) << meta_key_kind_shift) | (tag_index << meta_tag_shift);
  }

  frame* top() { return depth_ ? &frames_[depth_ - 1] : nullptr; }

  frame& push_frame(size_t node, frame_kind kind) {
    if (depth_ >= max_frames) throw limit_error("depth");
    frame& f = frames_[depth_++];
    f.node = node;
    f.count = 0;
    f.kind = kind;
    f.pairs = false;
    f.anchor = no_anchor;
    f.tag = tag_kind::none;
    f.merge = none;
    f.keys.clear();
    f.follow = no_template;
    f.record = no_template;
    return f;
  }

  // Container depth for the tape writer: pairs count like collections.
  uint32_t nesting() const { return uint32_t(depth_); }

  // A child of `f` (a scalar, alias or closed collection) is complete.
  void child_done(frame* f) {
    if (!f) {
      root_written_ = true;
      return;
    }
    if (f->kind == frame_kind::seq) {
      ++f->count;
    } else if (f->kind == frame_kind::pair) {
      if (++f->count == 2) close_pair();
    }
    // map: counted at the key
  }

  void close_pair() {
    frame& p = frames_[--depth_];
    writer_->end(p.node, 2);
    child_counted_in_pairs_map();
  }
  // The pairs mapping around a just-closed pair counts it.
  void child_counted_in_pairs_map() {
    frame* m = top();
    if (m && m->kind == frame_kind::map && m->pairs) ++m->count;
  }

  [[gnu::noinline]] void value(c4::csubstr s, scalar_style style, bool empty) {
    frame* f = top();
    if (!f && root_written_) throw error(status_internal, "YAML second root value");
    if (f && f->kind == frame_kind::map && f->pairs) throw error(status_internal, "YAML value without a key");
    std::string_view text(s.str ? s.str : "", s.len);
    uint64_t span = lexical(s.str, s.len, style, empty);
    scalar r;
    taken_tag tag{tag_kind::none, 0};
    uint32_t tag_index = 0;
    // Untagged plain scalars under CORE (nearly every value) skip the general resolver.
    if (!(fast_paths_ && pending_tag_ == tag_kind::none && style == scalar_style::plain && schema_ == schema::core &&
          resolve_core_fast(text, r))) {
      tag = take_tag();
      r = resolve_now(text, style, tag.kind);
      tag_index = tag.index;
      if (r.implicit == tag_kind::timestamp && !tag_index) tag_index = intern("tag:yaml.org,2002:timestamp");
    }
    size_t node = writer_->word_count();
    write_value(r, span, style);
    uint32_t anchor = define_anchor(node, false);
    side(node, meta_word(uint32_t(style), key_kind::string, tag_index) | (anchor != no_anchor ? meta_anchored : 0));
    if (f && f->kind == frame_kind::seq) {
      // input bytes or rapidyaml arena bytes: both stay put (grow_arena)
      last_ = {true, node, text, style, empty, tag.kind, tag_index, span, anchor, false, 0};
    } else {
      last_.valid = false;  // retro_map reads the other fields only when valid
    }
    child_done(f);
  }

  // tape::ascii, with two word loads for short text in the padded input.
  [[gnu::always_inline]] bool ascii_text(std::string_view s) const {
    if (C4_LIKELY(s.size() <= 16 && in_input(s.data(), s.size()))) {
      uint64_t lo, hi;
      std::memcpy(&lo, s.data(), 8);
      std::memcpy(&hi, s.data() + 8, 8);
      uint64_t any = (lo & low_bytes(s.size())) | (hi & low_bytes(s.size() > 8 ? s.size() - 8 : 0));
      return (any & 0x8080808080808080ull) == 0;
    }
    return ascii(s);
  }
  // tape::ascii eight bytes at a time.
  static bool ascii(std::string_view s) {
    const char* p = s.data();
    size_t n = s.size();
    uint64_t any = 0;
    size_t i = 0;
    for (; i + 8 <= n; i += 8) {
      uint64_t w;
      std::memcpy(&w, p + i, 8);
      any |= w;
    }
    if (i < n) {
      if (n >= 8) {
        uint64_t w;
        std::memcpy(&w, p + n - 8, 8);
        any |= w;
      } else {
        for (; i < n; ++i) any |= uint8_t(p[i]);
      }
    }
    return (any & 0x8080808080808080ull) == 0;
  }

  [[gnu::noinline]] void key(c4::csubstr s, scalar_style style, bool empty) {
    std::string_view text(s.str ? s.str : "", s.len);
    taken_tag tag = take_tag();
    uint64_t span = lexical(s.str, s.len, style, empty);
    write_key(text, style, empty, tag, span);
  }

  void write_key(std::string_view text, scalar_style style, bool empty, taken_tag tag, uint64_t span) {
    frame* f = top();
    if (!f || f->kind != frame_kind::map) throw error(status_internal, "YAML key outside a mapping");
    (void)empty;
    scalar r;
    if (!(fast_paths_ && tag.kind == tag_kind::none && style == scalar_style::plain && schema_ == schema::core &&
          resolve_core_fast(text, r)))
      r = resolve_now(text, style, tag.kind);
    uint32_t tag_index = tag.index;
    if (r.implicit == tag_kind::timestamp && !tag_index) tag_index = intern("tag:yaml.org,2002:timestamp");
    // `<<` (plain and untagged, or tagged !!merge) is a merge key when merges are on.
    bool merge = merges() && !f->pairs &&
                 ((tag.kind == tag_kind::none && style == scalar_style::plain && text == "<<") || tag.kind == tag_kind::merge);
    char buffer[40];
    std::string_view canonical = key_text(r, buffer);
    // Keys are compared after resolution and stringification. The table keeps pointers: input
    // bytes, arena bytes and literals stay valid for the decode; formatted numbers are copied.
    if (canonical.data() == buffer) canonical = stable_copy(canonical);
    place_key(*f, canonical, r.key, span, meta_word(uint32_t(style), r.key, tag_index), merge);
  }

  // Writes a scalar key node into the mapping `f` (checking duplicates) or, for a mapping stored
  // as pairs, into a new pair. `extra_meta` carries style, kind, tag or alias bits.
  void place_key(frame& f, std::string_view canonical, key_kind kind, uint64_t span, uint32_t extra_meta, bool merge) {
    (void)kind;
    leave_template(f);  // the general path keeps every key in f.keys
    uint32_t hash = full_key_hash(canonical);
    frame* target = &f;
    if (f.pairs) {
      open_pair(f);
      target = top();
    } else if (merge) {
      if (f.merge != none) {
        if (!rules_.last_wins) duplicate(f);
        remove_merge_entry(f);
      }
    } else if (!fused_duplicates() && !f.keys.insert(memory_, hash, canonical)) {
      if (rules_.duplicate_keys_pairs) {
        convert_to_pairs(f);
        open_pair(f);
        target = top();
      } else if (!rules_.last_wins) {
        duplicate(f);
      } else {
        remove_entry(f, canonical);
      }
    }
    size_t node = writer_->word_count();
    if (fused_duplicates()) writer_->member_key(target->node, hash);
    write_text(canonical, target->kind == frame_kind::map, span, (extra_meta & meta_style_mask) == 0);
    uint32_t anchor = define_anchor(node, false);
    side(node, extra_meta | (anchor != no_anchor ? meta_anchored : 0));
    if (target->kind == frame_kind::map) {
      if (merge) target->merge = node;
      ++target->count;
    } else {
      child_done(target);
    }
  }

  [[noreturn]] void duplicate(frame&) { fail("duplicate YAML key"); }

  std::string_view stable_copy(std::string_view text) {
    if (text.empty()) return text;
    char* copy = static_cast<char*>(memory_->allocate(text.size(), 1));
    std::memcpy(copy, text.data(), text.size());
    return {copy, text.size()};
  }

  // ---- collections ----------------------------------------------------------------------------
  void open(uint32_t kind, uint32_t style, c4::yml::type_bits bits) {
    frame* parent = top();
    if (!parent && root_written_) throw error(status_internal, "YAML second root value");
    if (parent && parent->kind == frame_kind::map && parent->pairs)
      throw error(status_internal, "YAML value without a key");
    begin_collection(kind, style);
    if (m_parent) m_parent->has_children = true;
    m_curr->evt_type |= bits;
    _push();
  }

  // A collection used as a key: a complex key (rejected, or a new pair of a pairs mapping).
  void open_key(uint32_t kind, uint32_t style, c4::yml::type_bits bits) {
    frame* f = top();
    if (!f || f->kind != frame_kind::map) throw error(status_internal, "YAML key outside a mapping");
    complex_key_start(*f);
    begin_collection(kind, style);
    if (m_parent) m_parent->has_children = true;
    m_curr->evt_type |= bits;
    _push();
  }

  // A complex key arrives in mapping `f`: reject, or convert to pairs and open the key's pair.
  void complex_key_start(frame& f) {
    if (!rules_.complex_keys_pairs) fail("YAML complex key");
    if (!f.pairs) convert_to_pairs(f);
    open_pair(f);
  }

  void begin_collection(uint32_t kind, uint32_t style) {
    taken_tag tag = take_tag();
    switch (tag.kind) {
      case tag_kind::none: case tag_kind::nonspecific: case tag_kind::other: break;
      case tag_kind::map: case tag_kind::set:
        if (kind != MYNA_VALUE_OBJECT) fail("YAML mapping tag on a sequence");
        break;
      case tag_kind::seq: case tag_kind::omap: case tag_kind::pairs:
        if (kind != MYNA_VALUE_ARRAY) fail("YAML sequence tag on a mapping");
        break;
      default:
        fail("YAML scalar tag on a collection");
    }
    size_t node = writer_->begin(kind == MYNA_VALUE_OBJECT ? ct::kind_object : ct::kind_array, nesting());
    uint32_t anchor = define_anchor(node, true);
    side(node, style | (tag.index << meta_tag_shift) | (anchor != no_anchor ? meta_anchored : 0));
    frame& f = push_frame(node, kind == MYNA_VALUE_OBJECT ? frame_kind::map : frame_kind::seq);
    f.anchor = anchor;
    f.tag = tag.kind;
    last_ = {};
  }

  void open_pair(frame& map) {
    (void)map;
    size_t node = writer_->begin(ct::kind_array, nesting());
    side(node, style_pair);
    push_frame(node, frame_kind::pair);
  }

  void close() {
    _pop();
    frame* f = top();
    if (!f || f->kind == frame_kind::pair) throw error(status_internal, "YAML close without a collection");
    if (f->kind == frame_kind::map && f->merge != none) apply_merge(*f);
    if (f->kind == frame_kind::map && !f->pairs) {
      const key_template* cached = f->follow != no_template ? &templates_[depth_index(*f)][f->follow]
          : f->record != no_template ? &templates_[depth_index(*f)][f->record] : nullptr;
      if (cached && cached->count < f->count) cached = nullptr;
      size_t index = 0;
      try {
        if (fused_duplicates()) writer_->end(f->node, f->count);
        else writer_->end_rescan(f->node, [&](std::string_view text) {
          return cached ? cached->keys[index++].hash : directory_hash(text);
        });
      }
      catch (const ct::duplicate_key&) { duplicate(*f); }
    } else writer_->end(f->node, f->count);
    // A template only becomes reusable after its directory proves all keys distinct.
    if (f->record != no_template) finish_template(*f);
    if (f->tag != tag_kind::none) zero_meta(writer_->word_count());
    validate_shape(*f);
    if (f->anchor != no_anchor) anchor_open_[f->anchor] = 0;
    size_t node = f->node;
    f->keys.clear();
    --depth_;
    last_ = {};
    last_closed_ = node;
    child_done(top());
  }

  // !!set: a mapping of nulls (or empty plain scalars, which FAILSAFE reads as ""); !!omap and
  // !!pairs: a sequence of single-pair mappings.
  void validate_shape(const frame& f) {
    const uint64_t* n = writer_->words();
    if (f.tag == tag_kind::set) {
      if (f.pairs) fail("YAML !!set with complex keys");
      for (size_t k = f.node + 2; k < writer_->word_count();) {
        size_t v = step(k);
        bool empty = ct::kind(n[v]) == ct::kind_string && ct::field(n[v]) == 0 && meta_[v] == uint32_t(scalar_style::plain);
        if ((ct::kind(n[v]) != ct::kind_null && !empty) || (meta_[v] & meta_alias)) fail("YAML !!set with a non-null value");
        k = after(v);
      }
    } else if (f.tag == tag_kind::omap || f.tag == tag_kind::pairs) {
      for (size_t e = f.node + 2; e < writer_->word_count(); e = after(e)) {
        if (ct::kind(n[e]) != ct::kind_object || ct::field(n[e]) != 1 || (meta_[e] & meta_style_mask) == style_pair)
          fail(f.tag == tag_kind::omap ? "YAML !!omap entry is not a single-pair mapping"
                                       : "YAML !!pairs entry is not a single-pair mapping");
      }
    }
  }

  // A value slot of `f` is taken back (a retroactive mapping replaces it).
  void uncount(frame* f) {
    if (!f) root_written_ = false;
    else if (f->kind != frame_kind::map) --f->count;
  }

  // ---- retroactive mappings ---------------------------------------------------------------------
  // `[a: b]` (flow) and `[a]: b` (block): the last complete value becomes the first key of a new
  // mapping that starts where it started.
  void retro_map(uint32_t style) {
    frame* f = top();
    if (last_.valid && step(last_.node) == writer_->word_count()) {
      // A scalar or alias element.
      last_value saved = last_;
      writer_->pop_scalar(saved.node);
      meta_.truncate(saved.node);
      uncount(f);
      size_t node = writer_->begin(ct::kind_object, nesting());
      side(node, style);
      push_frame(node, frame_kind::map);
      last_ = {};
      if (saved.anchor != no_anchor) {
        has_pending_anchor_ = false;
        anchors_[saved.anchor].node = node + 2;
      }
      if (saved.alias) {
        write_alias_key(saved.ordinal);
      } else {
        frame& m = *top();
        scalar r = resolve_now(saved.text, saved.style, saved.tag);
        char buffer[40];
        std::string_view canonical = key_text(r, buffer);
        if (canonical.data() == buffer) canonical = stable_copy(canonical);
        uint32_t meta = meta_word(uint32_t(saved.style), r.key, saved.tag_index);
        size_t key_node = writer_->word_count();
        place_key(m, canonical, r.key, saved.lexical, meta, false);
        if (saved.anchor != no_anchor) { zero_meta(key_node + 1); meta_[key_node] |= meta_anchored; }
      }
      return;
    }
    // A collection: the last closed collection, which must end the tape.
    if (last_closed_ == none || !rules_.complex_keys_pairs) fail("YAML complex key");
    size_t start = last_closed_;
    size_t end = writer_->word_count();
    if (ct::low(writer_->words()[start]) != end) fail("YAML complex key");
    size_t count = end - start;
    auto* scratch = save(start, count);
    restore_truncate(start);
    uncount(f);
    size_t node = writer_->begin(ct::kind_array, nesting());
    side(node, style);
    frame& m = push_frame(node, frame_kind::map);
    m.pairs = true;
    m.keys.clear();
    open_pair(m);
    size_t at = writer_->word_count();
    replay(scratch, 0, count, int64_t(at) - int64_t(start));
    remap(start, end, int64_t(at) - int64_t(start));
    last_closed_ = none;
    last_ = {};
    child_done(top());  // the key of the pair is complete
  }

  // ---- tape rewriting (pairs, merges, last-wins) ----------------------------------------------
  struct saved_nodes {
    uint64_t* nodes;
    uint32_t* meta;
  };
  // Copies compact words and their metadata to arena scratch before a rewrite.
  saved_nodes* save(size_t start, size_t count) {
    auto* s = static_cast<saved_nodes*>(memory_->allocate(sizeof(saved_nodes), alignof(saved_nodes)));
    size_t n = count ? count : 1;
    s->nodes = static_cast<uint64_t*>(memory_->allocate(n * sizeof(uint64_t), alignof(uint64_t)));
    s->meta = static_cast<uint32_t*>(memory_->allocate(n * sizeof(uint32_t), alignof(uint32_t)));
    if (count) {
      std::memcpy(s->nodes, writer_->words() + start, count * sizeof(uint64_t));
      std::memcpy(s->meta, meta_.data() + start, count * sizeof(uint32_t));
    }
    return s;
  }
  void restore_truncate(size_t size) {
    writer_->truncate(size);
    meta_.truncate(size);
  }
  void replay(const saved_nodes* s, size_t from, size_t count, int64_t shift) {
    writer_->append(s->nodes + from, count, shift);
    std::memcpy(meta_.extend(memory_, count), s->meta + from, count * sizeof(uint32_t));
  }
  // The alias binding table also covers aliases used as canonical scalar keys. Rewrites update
  // that table first, then synchronize REF words, including references across moved blocks.
  void sync_refs() {
    for (size_t i = 0; i < writer_->word_count(); i = step(i)) {
      uint64_t& w = writer_->words()[i];
      if (ct::kind(w) == ct::kind_ref) w = ct::make(ct::kind_ref, 0, 0, uint32_t(alias_targets_[meta_[i] >> meta_tag_shift]));
    }
  }
  // Anchors and alias targets inside the moved block [start, end) follow it by `shift`.
  void remap(size_t start, size_t end, int64_t shift) {
    for (size_t i = 0; i < anchors_.size(); ++i)
      if (anchors_[i].node >= start && anchors_[i].node < end) anchors_[i].node = uint64_t(int64_t(anchors_[i].node) + shift);
    for (size_t i = 0; i < alias_targets_.size(); ++i)
      if (alias_targets_[i] >= start && alias_targets_[i] < end) alias_targets_[i] = uint64_t(int64_t(alias_targets_[i]) + shift);
  }
  // Fails when an anchor or alias target lies in [start, end), which a rewrite is removing.
  void forbid_removed(size_t start, size_t end, std::string_view why) {
    for (size_t i = 0; i < anchors_.size(); ++i)
      if (anchors_[i].node >= start && anchors_[i].node < end) fail(why);
    for (size_t i = 0; i < alias_targets_.size(); ++i)
      if (alias_targets_[i] >= start && alias_targets_[i] < end) fail(why);
  }

  size_t after(size_t index) const { return ct::after(index, writer_->words()[index]); }

  // Rewrites the open mapping `f` ([key, value]...) as pairs ([[key, value]]...).
  void convert_to_pairs(frame& f) {
    if (f.merge != none) fail("YAML merge key in a mapping with complex or duplicate keys");
    leave_template(f);
    size_t start = f.node + 2, end = writer_->word_count();
    size_t count = end - start;
    auto* s = save(start, count);
    restore_truncate(start);
    writer_->words()[f.node] = ct::make(ct::kind_array, ct::wide_flag, 0, 0);
    auto* relocated = static_cast<uint64_t*>(memory_->allocate((count ? count : 1) * sizeof(uint64_t), alignof(uint64_t)));
    for (size_t k = 0; k < count;) {
      size_t value = ct::step(k, s->nodes[k]);
      size_t next = ct::after(value + start, s->nodes[value]) - start;
      size_t pair = writer_->begin(ct::kind_array, nesting());
      side(pair, style_pair);
      size_t at = writer_->word_count();
      replay(s, k, next - k, int64_t(at) - int64_t(k + start));
      writer_->words()[at] &= ~ct::key_flag;  // now a pair element
      for (size_t i = k; i < next; ++i) relocated[i] = at + i - k;
      writer_->end(pair, 2);
      k = next;
    }
    // Apply each relocation once from the original coordinate system. New offsets can overlap
    // a later old block, so incrementally remapping anchors would move them twice.
    auto relocate = [&](uint64_t& node) { if (node >= start && node < end) node = relocated[node - start]; };
    for (size_t i = 0; i < anchors_.size(); ++i) relocate(anchors_[i].node);
    for (size_t i = 0; i < alias_targets_.size(); ++i) relocate(alias_targets_[i]);
    f.pairs = true;
    f.keys.clear();
    sync_refs();
  }

  // DuplicateKeys.LAST_WINS: removes the earlier entry with key `canonical` from the open mapping.
  void remove_entry(frame& f, std::string_view canonical) {
    size_t end = writer_->word_count();
    for (size_t at = f.node + 2; at < end;) {
      size_t value_node = step(at);
      size_t next = after(value_node);
      if (at != f.merge && writer_->text_of(at) == canonical) {
        erase_entry(f, at, next);
        return;
      }
      at = next;
    }
    throw error(status_internal, "YAML duplicate key not found");
  }
  void remove_merge_entry(frame& f) {
    size_t at = f.merge;
    f.merge = none;
    erase_entry(f, at, after(step(at)));
  }
  void erase_entry(frame& f, size_t at, size_t next) {
    forbid_removed(at, next, "YAML duplicate key removes an anchored node");
    size_t count = next - at;
    writer_->erase(at, count);
    erase_side(at, count);
    remap(next, SIZE_MAX, -int64_t(count));
    if (f.merge != none && f.merge > at) f.merge -= count;
    --f.count;
    sync_refs();
  }
  void erase_side(size_t first, size_t count) {
    size_t size = meta_.size();
    std::memmove(meta_.data() + first, meta_.data() + first + count, (size - first - count) * sizeof(uint32_t));
    meta_.truncate(size - count);
  }

  // Merge keys at mapping end (merge.h).
  void apply_merge(frame& f);

  void grow_arena(size_t need) {
    size_t capacity = arena_capacity_ ? arena_capacity_ * 2 : 256;
    if (capacity < need) capacity = need;
    char* grown = static_cast<char*>(memory_->allocate(capacity, 1));
    if (arena_size_) std::memcpy(grown, arena_, arena_size_);
    // The old span is never freed or reused before the session arena resets, so text the handler
    // already holds (scalars, keys) stays valid; the engine relocates its own pointers.
    arena_ = grown;
    arena_capacity_ = capacity;
  }

  // Per-decode state.
  block_arena* memory_ = nullptr;
  char* input_ = nullptr;
  size_t length_ = 0;
  const uint64_t* newlines_ = nullptr;
  const char* pristine_ = nullptr;
  policy rules_;
  yaml::schema schema_ = yaml::schema::core;
  myna::value_limits limits_;
  std::optional<ctape_writer> writer_;
  arena_vec<uint32_t> meta_;
  bool sparse_meta_ = false;
  arena_vec<char> strings_;
  arena_vec<span_entry> tags_;
  arena_vec<anchor_entry> anchors_;
  arena_vec<uint8_t> anchor_open_;
  arena_vec<uint64_t> alias_targets_;
  arena_vec<directive_entry> directives_;
  string_map tag_index_;
  string_map bindings_;
  frame frames_[max_frames];
  size_t depth_ = 0;
  tag_kind pending_tag_ = tag_kind::none;
  uint32_t pending_tag_index_ = 0;
  bool has_pending_anchor_ = false;
  span_entry pending_anchor_{};
  last_value last_;
  size_t last_closed_ = SIZE_MAX;
  size_t docs_ = 0;
  uint32_t flags_ = 0;
  uint32_t version_ = 0;
  bool root_written_ = false;
  td_tape_view view_{};
  char* arena_ = nullptr;
  size_t arena_capacity_ = 0, arena_size_ = 0;
  c4::yml::TagDirectives m_tag_directives;
  c4::yml::TagCache m_tag_cache;
  hash_slot hash_cache_[hash_cache_slots];
  key_template templates_[template_levels][2];
  uint8_t template_next_[template_levels] = {};
  bool fast_paths_ = true;
  bool fused_duplicates_ = true;
};

}  // namespace truffledata::yaml

#include "yaml/native/merge.h"
#endif
