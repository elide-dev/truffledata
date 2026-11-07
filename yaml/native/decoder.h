#ifndef TRUFFLEDATA_YAML_DECODER_H
#define TRUFFLEDATA_YAML_DECODER_H
// yaml::handler: a rapidyaml ParseEngine event handler that writes Myna ABI v3 nodes directly into
// the session arena through the shared presized tape_writer (docs/superpowers/plans/2026-11-06-yaml.md,
// D1, D3). There is no rapidyaml tree, event array or DOM in between. Every node gets a `lexical`
// span (shared with JSON), a `key_hashes` entry (shared with JSON: ASCII bit, key hash bits and the
// key's offset from its object, so JSON's position-hinted reads work unchanged on YAML tapes) and a
// `yaml_meta` word:
//
//   bits 0-2   style: 0 plain, 1 single, 2 double, 3 literal, 4 folded, 5 block container,
//              6 flow container
//   bit 3      anchored
//   bit 4      alias
//   bits 5-7   key kind: 0 string, 1 null, 2 bool, 3 int, 4 float (keys are STRING nodes)
//   bits 8-31  tag index (0 none), or the alias ordinal when bit 4 is set
//
// Scalars are stored inline up to 16 bytes, else as region references into the input buffer that
// rapidyaml filtered in place (region 1), else (scalars that grew while filtering and live in the
// rapidyaml arena) copied into the tape's owned text. One handler decodes at most one document per
// parse; a second document is TD_YAML_MULTIPLE_DOCUMENTS.
#include "tape/native/arena.h"
#include "tape/native/key_table.h"
#include "tape/native/tape_writer.h"
#include "yaml/native/errors.h"
#include "yaml/native/input.h"
#include "yaml/native/scalars.h"

#include <c4/yml/event_handler_stack.hpp>
#include <c4/yml/parse_engine.hpp>
#include <c4/yml/tag.hpp>

#include <cstddef>
#include <cstdint>
#include <cstring>
#include <optional>
#include <string_view>

namespace truffledata::yaml {

using tape::arena_vec;
using tape::block_arena;
using tape::table_view;
using tape::tape_writer;

// yaml_meta layout (see the file comment).
inline constexpr uint32_t meta_style_mask = 0x7u;
inline constexpr uint32_t meta_anchored = 1u << 3;
inline constexpr uint32_t meta_alias = 1u << 4;
inline constexpr uint32_t meta_key_kind_shift = 5;
inline constexpr uint32_t meta_tag_shift = 8;
inline constexpr uint32_t meta_tag_limit = 1u << 24;
inline constexpr uint32_t style_block = 5;
inline constexpr uint32_t style_flow = 6;

// Document flags published with each result.
inline constexpr uint32_t doc_explicit_start = 1u << 0;  // `---`
inline constexpr uint32_t doc_explicit_end = 1u << 1;    // `...`
inline constexpr uint32_t doc_nonfinite = 1u << 2;       // non-finite F64 nodes (myna#52)
inline constexpr uint32_t doc_source_retained = 1u << 3; // `source` is a pristine copy of the input
inline constexpr uint32_t doc_present = 1u << 4;         // the input held a document (else root null)

// Decode policies (YamlOptions minus limits, which live in myna::value_limits and storage_limits).
struct policy {
  yaml::schema schema = yaml::schema::core;
  bool last_wins = false;       // DuplicateKeys.LAST_WINS
  bool merge_keys = false;      // mergeKeys
  bool reject_aliases = false;  // Aliases.REJECT
  bool reject_unknown_tags = false;  // UnknownTags.REJECT
  bool honor_version = false;   // VersionDirective.HONOR
  bool retain_source = false;   // per decode (TD_YAML_RETAIN_SOURCE)
  uint64_t max_aliases = 10000;
};

struct handler_state : public c4::yml::ParserState {
  c4::yml::type_bits evt_type;
};

class handler : public c4::yml::EventHandlerStack<handler, handler_state> {
 public:
  using state = handler_state;
  // Resolved tags are copied out when they arrive; they need not live in the source or arena.
  enum { requires_strings_on_buffers = false };  // NOLINT

  static constexpr size_t max_frames = 130;  // depth limit is at most 128

  explicit handler(c4::yml::Callbacks const& callbacks) : EventHandlerStack(callbacks) {
    m_tag_cache.m_entries.m_callbacks = callbacks;
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
    memory_ = memory;
    input_ = input;
    length_ = length;
    newlines_ = newlines;
    pristine_ = pristine;
    rules_ = rules;
    limits_ = limits;
    writer_.emplace(memory, limits);
    writer_->reserve(node_hint, byte_hint);
    lexical_.reserve(memory, node_hint);
    hashes_.reserve(memory, node_hint);
    meta_.reserve(memory, node_hint);
    region_ = writer_->region(std::string_view(input, length));
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
    if (writer_->node_count() == 0) {
      side(0, 0, 0);
      writer_->null();
    }
    if (writer_->has_nonfinite()) flags_ |= doc_nonfinite;
    if (pristine_) flags_ |= doc_source_retained;
    view_ = writer_->view();
  }
  // Drops the per-decode state before the arena is reset (key tables free into the arena).
  void clear() noexcept {
    while (depth_) frames_[--depth_].keys.reset();
    writer_.reset();
    lexical_.forget();
    hashes_.forget();
    meta_.forget();
    memory_ = nullptr;
    arena_ = nullptr;
    arena_capacity_ = arena_size_ = 0;
    docs_ = 0;
    flags_ = 0;
    version_ = 0;
    root_written_ = false;
    pending_tag_ = tag_kind::none;
    has_pending_anchor_ = false;
    last_ = {};
    view_ = {};
  }

  const myna_value_view& view() const { return view_; }
  table_view<uint64_t> lexical_spans() const { return {lexical_.data(), lexical_.size()}; }
  table_view<uint32_t> key_hashes() const { return {hashes_.data(), hashes_.size()}; }
  table_view<uint32_t> meta() const { return {meta_.data(), meta_.size()}; }
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

  // ---- containers -----------------------------------------------------------------------------
  void begin_map_key_flow() { complex_key(); }
  void begin_map_key_block() { complex_key(); }
  void begin_seq_key_flow() { complex_key(); }
  void begin_seq_key_block() { complex_key(); }
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
  void set_key_scalar_plain(c4::csubstr s) {
    key(s, scalar_style::plain, false);
    enable_<c4::yml::KEY | c4::yml::KEY_PLAIN>();
  }
  void set_val_scalar_plain(c4::csubstr s) {
    value(s, scalar_style::plain, false);
    enable_<c4::yml::VAL | c4::yml::VAL_PLAIN>();
  }
  void set_key_scalar_dquoted(c4::csubstr s) {
    key(s, scalar_style::double_quoted, false);
    enable_<c4::yml::KEY | c4::yml::KEY_DQUO>();
  }
  void set_val_scalar_dquoted(c4::csubstr s) {
    value(s, scalar_style::double_quoted, false);
    enable_<c4::yml::VAL | c4::yml::VAL_DQUO>();
  }
  void set_key_scalar_squoted(c4::csubstr s) {
    key(s, scalar_style::single_quoted, false);
    enable_<c4::yml::KEY | c4::yml::KEY_SQUO>();
  }
  void set_val_scalar_squoted(c4::csubstr s) {
    value(s, scalar_style::single_quoted, false);
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
    alias(ref, true);
  }
  void set_val_ref(c4::csubstr ref) {
    enable_<c4::yml::VAL | c4::yml::VALREF>();
    alias(ref, false);
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
  }

  // ---- retroactive mappings -------------------------------------------------------------------
  // `[a: b]`: the previous sequence element is the first key of a new single-pair flow mapping.
  void actually_val_is_first_key_of_new_map_flow() {
    retro_map();
    enable_<c4::yml::MAP | c4::yml::FLOW_SL>();
    _push();
  }
  // `[a]: b` at block level: a flow collection used as a key.
  void actually_val_is_first_key_of_new_map_block() { complex_key(); }

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
  struct frame {
    size_t node = 0;
    uint64_t count = 0;
    bool map = false;
    std::optional<tape::key_table> keys;
  };
  // The last value written into a flow sequence, kept so `[a: b]` can turn it into a key.
  struct last_value {
    bool valid = false;
    size_t node = 0;
    std::string_view text;
    scalar_style style = scalar_style::plain;
    bool empty = false;
    tag_kind tag = tag_kind::none;
    uint64_t lexical = 0;
  };

  [[noreturn]] void fail(std::string_view message) { throw syntax_error(message).at(line(), column()); }
  void complex_key() { fail("YAML complex key"); }

  bool in_input(const char* p, size_t n) const { return p >= input_ && p + n <= input_ + length_ && p; }

  // The three side tables grow in step (their capacities only differ after a failed growth).
  void side(uint64_t lexical, uint32_t hash, uint32_t meta) {
    *lexical_.extend(memory_, 1) = lexical;
    *hashes_.extend(memory_, 1) = hash;
    *meta_.extend(memory_, 1) = meta;
  }

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
    if (dot == std::string_view::npos || r1.ec != std::errc())
      fail("invalid YAML %YAML directive");
    auto r2 = std::from_chars(v.data() + dot + 1, v.data() + v.size(), minor);
    if (r2.ec != std::errc() || r2.ptr != v.data() + v.size()) fail("invalid YAML %YAML directive");
    if (major != 1) fail("unsupported YAML version (only 1.x)");
    version_ = (major << 16) | (minor > 0xffff ? 0xffff : minor);
  }

  void pending_anchor(c4::csubstr) { fail("YAML anchors and aliases are not supported"); }
  void alias(c4::csubstr, bool) { fail("YAML anchors and aliases are not supported"); }

  // Core tags only; tag interning and the 1.1 types arrive with the side tables.
  void pending(c4::csubstr tag) {
    std::string_view t(tag.str, tag.len);
    if (t.size() >= 2 && t.front() == '<' && t.back() == '>') t = t.substr(1, t.size() - 2);
    constexpr std::string_view core = "tag:yaml.org,2002:";
    tag_kind kind = tag_kind::other;
    if (t == "!") kind = tag_kind::nonspecific;
    else if (t.substr(0, core.size()) == core) {
      std::string_view name = t.substr(core.size());
      if (name == "str") kind = tag_kind::str;
      else if (name == "int") kind = tag_kind::int_;
      else if (name == "float") kind = tag_kind::float_;
      else if (name == "bool") kind = tag_kind::bool_;
      else if (name == "null") kind = tag_kind::null_;
      else if (name == "seq") kind = tag_kind::seq;
      else if (name == "map") kind = tag_kind::map;
    }
    if (kind == tag_kind::other) fail("YAML tag not supported");
    pending_tag_ = kind;
  }

  // ---- lexical spans ----------------------------------------------------------------------
  static bool space(char c) { return c == ' ' || c == '\t' || c == '\r' || c == '\n'; }
  static uint64_t pack(size_t offset, size_t length) { return (uint64_t(offset) << 32) | uint64_t(length); }

  // Spans index the input: plain single-line scalars always; quoted, multi-line plain and block
  // scalars only when a pristine copy of the source is retained (rapidyaml rewrote them in place).
  uint64_t lexical(const char* text, size_t length, scalar_style style, bool empty) const {
    if (empty || !text) return 0;
    const bool located = in_input(text, length);
    size_t position = m_curr->pos.offset;
    if (position > length_) position = length_;
    switch (style) {
      case scalar_style::plain: {
        if (!located) return 0;
        size_t begin = size_t(text - input_), end = begin + length;
        // Folding turns every line break into a space or LF: a scalar holding neither (numbers,
        // identifiers) was single-line and its bytes are the source token.
        bool folded = false;
        for (size_t i = 0; i < length; ++i) folded |= text[i] == ' ' || text[i] == '\n';
        if (!folded) return pack(begin, length);
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

  // ---- node writing -----------------------------------------------------------------------
  scalar resolve_now(std::string_view text, scalar_style style, bool empty, tag_kind tag) {
    (void)empty;
    return resolve(text, style, rules_.schema, tag, memory_);
  }

  void write_text(std::string_view text) {
    if (text.size() > MYNA_VALUE_INLINE_BYTES && in_input(text.data(), text.size()))
      writer_->string_ref(region_, uint32_t(text.data() - input_), uint32_t(text.size()));
    else
      writer_->string(text);
  }

  void write_value(const scalar& r) {
    switch (r.kind) {
      case MYNA_VALUE_NULL: writer_->null(); break;
      case MYNA_VALUE_BOOL: writer_->boolean(r.bits != 0); break;
      case MYNA_VALUE_I64: writer_->integer(int64_t(r.bits)); break;
      case MYNA_VALUE_U64: writer_->unsigned_integer(r.bits); break;
      case MYNA_VALUE_F64: {
        double d;
        std::memcpy(&d, &r.bits, sizeof d);
        // The one place non-finite floats enter a tape (myna#52: see tape_writer::nonfinite).
        if (r.nonfinite) writer_->nonfinite(d);
        else writer_->floating(d);
        break;
      }
      case MYNA_VALUE_DECIMAL:
        if (r.token && in_input(r.text.data(), r.text.size()))
          writer_->number_ref(region_, uint32_t(r.text.data() - input_), uint32_t(r.text.size()));
        else
          writer_->decimal(r.text);
        break;
      default: write_text(r.text); break;
    }
  }

  static uint32_t meta_word(uint32_t style, key_kind kind) {
    return style | (uint32_t(kind) << meta_key_kind_shift);
  }

  tag_kind take_tag() {
    tag_kind t = pending_tag_;
    pending_tag_ = tag_kind::none;
    return t;
  }

  frame* top() { return depth_ ? &frames_[depth_ - 1] : nullptr; }

  void value(c4::csubstr s, scalar_style style, bool empty) {
    frame* f = top();
    if (!f && root_written_) throw error(status_internal, "YAML second root value");
    std::string_view text(s.str ? s.str : "", s.len);
    tag_kind tag = take_tag();
    uint64_t span = lexical(s.str, s.len, style, empty);
    scalar r = resolve_now(text, style, empty, tag);
    size_t node = writer_->node_count();
    write_value(r);
    side(span, r.kind == MYNA_VALUE_STRING && tape::ascii(r.text) ? tape::ascii_flag : 0,
         meta_word(uint32_t(style), key_kind::string));
    if (!f) root_written_ = true;
    else if (!f->map) ++f->count;
    // A flow sequence element may turn out to be the first key of `[a: b]`.
    last_ = {};
    if (f && !f->map) {
      last_.valid = true;
      last_.node = node;
      last_.text = text;  // input bytes or rapidyaml arena bytes: both stay put (grow_arena)
      last_.style = style;
      last_.empty = empty;
      last_.tag = tag;
      last_.lexical = span;
    }
  }

  void key(c4::csubstr s, scalar_style style, bool empty) {
    std::string_view text(s.str ? s.str : "", s.len);
    tag_kind tag = take_tag();
    uint64_t span = lexical(s.str, s.len, style, empty);
    write_key(text, style, empty, tag, span);
  }

  void write_key(std::string_view text, scalar_style style, bool empty, tag_kind tag, uint64_t span) {
    frame* f = top();
    if (!f || !f->map) throw error(status_internal, "YAML key outside a mapping");
    scalar r = resolve_now(text, style, empty, tag);
    char buffer[32];
    std::string_view canonical = key_text(r, buffer);
    // Keys are compared after resolution and stringification. The table keeps pointers: input
    // bytes, arena bytes and literals stay valid for the decode; formatted numbers are copied.
    if (canonical.data() == buffer) canonical = stable_copy(canonical);
    bool plain;
    uint32_t hash = tape::key_hash(canonical, plain);
    if (!f->keys->insert(hash, canonical)) {
      if (!rules_.last_wins) fail("duplicate YAML key");
      remove_entry(*f, canonical);
    }
    size_t node = writer_->node_count();
    write_text(canonical);
    side(span, tape::key_meta(hash, node - f->node, plain), meta_word(uint32_t(style), r.key));
    ++f->count;
  }

  std::string_view stable_copy(std::string_view text) {
    if (text.empty()) return text;
    char* copy = static_cast<char*>(memory_->allocate(text.size(), 1));
    std::memcpy(copy, text.data(), text.size());
    return {copy, text.size()};
  }

  // DuplicateKeys.LAST_WINS: removes the earlier entry with key `canonical` from the open mapping.
  void remove_entry(frame& f, std::string_view canonical) {
    myna_value_node* n = writer_->nodes();
    size_t end = writer_->node_count();
    size_t at = f.node + 1;
    while (at < end) {
      size_t value_node = at + 1;
      size_t after = myna_value_next(&n[value_node], value_node);
      if (writer_->text_of(at) == canonical) {
        size_t count = after - at;
        writer_->erase(at, count);
        erase_side(at, count);
        --f.count;
        // Later keys of this mapping moved closer to it.
        size_t size = writer_->node_count();
        n = writer_->nodes();
        for (size_t k = at; k < size;) {
          uint32_t& h = hashes_[k];
          h = (h & ~tape::key_offset_mask) | (k - f.node <= tape::key_offset_mask ? uint32_t(k - f.node) : 0);
          size_t v = k + 1;
          k = myna_value_next(&n[v], v);
        }
        return;
      }
      at = after;
    }
    throw error(status_internal, "YAML duplicate key not found");
  }

  void erase_side(size_t first, size_t count) {
    size_t size = lexical_.size();
    std::memmove(lexical_.data() + first, lexical_.data() + first + count, (size - first - count) * sizeof(uint64_t));
    std::memmove(hashes_.data() + first, hashes_.data() + first + count, (size - first - count) * sizeof(uint32_t));
    std::memmove(meta_.data() + first, meta_.data() + first + count, (size - first - count) * sizeof(uint32_t));
    lexical_.truncate(size - count);
    hashes_.truncate(size - count);
    meta_.truncate(size - count);
  }

  void open(uint32_t kind, uint32_t style, c4::yml::type_bits bits) {
    frame* parent = top();
    if (!parent && root_written_) throw error(status_internal, "YAML second root value");
    tag_kind tag = take_tag();
    if (tag != tag_kind::none && tag != tag_kind::nonspecific) {
      bool fits = (tag == tag_kind::map && kind == MYNA_VALUE_OBJECT) || (tag == tag_kind::seq && kind == MYNA_VALUE_ARRAY);
      if (!fits) fail("YAML tag does not match the collection kind");
    }
    if (depth_ + 1 >= max_frames) throw limit_error("depth");
    size_t node = writer_->begin(kind, uint32_t(depth_));
    side(0, 0, style);
    if (!parent) root_written_ = true;
    else if (!parent->map) ++parent->count;
    frame& f = frames_[depth_++];
    f.node = node;
    f.count = 0;
    f.map = kind == MYNA_VALUE_OBJECT;
    if (f.map) f.keys.emplace(memory_);
    last_ = {};
    if (m_parent) m_parent->has_children = true;
    m_curr->evt_type |= bits;
    _push();
  }

  void close() {
    _pop();
    frame& f = frames_[--depth_];
    writer_->end(f.node, f.count);
    f.keys.reset();
    last_ = {};  // a closed collection is never a key
  }

  void retro_map() {
    frame* f = top();
    if (!f || f->map || !last_.valid || last_.node + 1 != writer_->node_count()) complex_key();
    last_value saved = last_;
    writer_->truncate(saved.node);
    lexical_.truncate(saved.node);
    hashes_.truncate(saved.node);
    meta_.truncate(saved.node);
    --f->count;
    if (depth_ + 1 >= max_frames) throw limit_error("depth");
    size_t node = writer_->begin(MYNA_VALUE_OBJECT, uint32_t(depth_));
    side(0, 0, style_flow);
    ++f->count;
    frame& m = frames_[depth_++];
    m.node = node;
    m.count = 0;
    m.map = true;
    m.keys.emplace(memory_);
    last_ = {};
    write_key(saved.text, saved.style, saved.empty, saved.tag, saved.lexical);
  }

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
  myna::value_limits limits_;
  std::optional<tape_writer> writer_;
  arena_vec<uint64_t> lexical_;
  arena_vec<uint32_t> hashes_;
  arena_vec<uint32_t> meta_;
  uint32_t region_ = 0;
  frame frames_[max_frames];
  size_t depth_ = 0;
  tag_kind pending_tag_ = tag_kind::none;
  bool has_pending_anchor_ = false;
  last_value last_;
  size_t docs_ = 0;
  uint32_t flags_ = 0;
  uint32_t version_ = 0;
  bool root_written_ = false;
  myna_value_view view_{};
  char* arena_ = nullptr;
  size_t arena_capacity_ = 0, arena_size_ = 0;
  c4::yml::TagDirectives m_tag_directives;
  c4::yml::TagCache m_tag_cache;
};

}  // namespace truffledata::yaml
#endif
