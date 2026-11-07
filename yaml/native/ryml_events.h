// Test-only rapidyaml event handler that renders yaml-test-suite event text (test.event format).
//
// The rendering rules are ported from rapidyaml 0.16.0's
// src_extra/c4/yml/extra/ints_to_testsuite.cpp and the state handling from
// src_extra/c4/yml/extra/event_handler_ints.hpp (both MIT, Copyright (c) 2018 Joao Paulo
// Magalhaes), but the handler writes the text directly instead of going through integer events.
// src_extra is never built by this repository.
//
// The handler is also the reference for the decoder's own handler (Task 2): it shows the full
// event interface ParseEngine<Handler> requires. With `render` off it only counts events, which the
// scaling test uses to time the parse engine alone.
#pragma once

#include <c4/yml/escape_scalar.hpp>
#include <c4/yml/event_handler_stack.hpp>
#include <c4/yml/parse_engine.def.hpp>
#include <c4/yml/parse_engine.hpp>
#include <c4/yml/tag.hpp>

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace truffledata::yaml::testing {

struct ryml_events_state : public c4::yml::ParserState {
  c4::yml::type_bits evt_type;
};

class ryml_events : public c4::yml::EventHandlerStack<ryml_events, ryml_events_state> {
 public:
  using state = ryml_events_state;
  // Resolved tags must live in the source or the arena (as for the integer handler).
  enum { requires_strings_on_buffers = true };  // NOLINT

  explicit ryml_events(bool render = true) : EventHandlerStack(c4::yml::get_callbacks()), m_render(render) {
    reset(c4::substr{});
  }

  /// Prepares a parse of `src` (which the engine filters in place).
  void reset(c4::substr src) {
    _stack_reset_root();
    m_curr->flags |= c4::yml::RUNK | c4::yml::RTOP;
    m_curr->evt_type = {};
    m_src = src;
    m_arena_pos = 0;
    m_curr_doc = 0;
    m_tag_directives.clear();
    m_tag_cache.clear();
    m_out.clear();
    m_starts.clear();
    m_anchor.clear();
    m_tag.clear();
    m_has_anchor = false;
    m_has_tag = false;
    m_last_node = 0;
    m_doc_body = 0;
    m_events = 0;
  }

  /// The rendered events of the last parse.
  const std::string &events() const { return m_out; }
  /// Number of events of the last parse (counted in both modes).
  uint64_t event_count() const { return m_events; }

  // ---- members the parse engine reads directly
  c4::yml::id_type m_curr_doc = 0;

  c4::yml::TagDirectives &tag_directives() { return m_tag_directives; }
  c4::yml::TagCache &tag_cache() { return m_tag_cache; }

  // ---- parse lifecycle

  void start_parse(const char *filename, c4::substr src) { this->_stack_start_parse(filename, src); }
  void finish_parse() { this->_stack_finish_parse(); }
  void cancel_parse() {
    while (m_stack.size() > 1) _pop();
  }

  // ---- stream and documents

  void begin_stream() { line_("+STR"); }
  void end_stream() { line_("-STR"); }

  void begin_doc() {
    doc_line_("+DOC");
    if (_stack_should_push_on_begin_doc()) {
      _push();
      enable_<c4::yml::DOC>();
    }
  }
  void end_doc() {
    line_("-DOC");
    if (_stack_should_pop_on_end_doc()) _pop();
    ++m_curr_doc;
  }
  void begin_doc_expl() {
    doc_line_("+DOC ---");
    _push();
    enable_<c4::yml::DOC>();
  }
  void end_doc_expl() {
    line_("-DOC ...");
    if (_stack_should_pop_on_end_doc()) _pop();
    ++m_curr_doc;
  }

  // ---- mappings

  void begin_map_key_flow() { open_("+MAP", "{}", c4::yml::KEY | c4::yml::MAP | c4::yml::FLOW_SL); }
  void begin_map_key_block() { open_("+MAP", "", c4::yml::KEY | c4::yml::MAP | c4::yml::BLOCK); }
  void begin_map_val_flow() { open_("+MAP", "{}", c4::yml::MAP | c4::yml::FLOW_SL); }
  void begin_map_val_block() { open_("+MAP", "", c4::yml::MAP | c4::yml::BLOCK); }
  void end_map_block() { close_("-MAP"); }
  void end_map_flow(bool /*multiline*/, c4::yml::type_bits /*style*/ = c4::yml::FLOW_ML1) { close_("-MAP"); }

  // ---- sequences

  void begin_seq_key_flow() { open_("+SEQ", "[]", c4::yml::KEY | c4::yml::SEQ | c4::yml::FLOW_SL); }
  void begin_seq_key_block() { open_("+SEQ", "", c4::yml::KEY | c4::yml::SEQ | c4::yml::BLOCK); }
  void begin_seq_val_flow() { open_("+SEQ", "[]", c4::yml::SEQ | c4::yml::FLOW_SL); }
  void begin_seq_val_block() { open_("+SEQ", "", c4::yml::SEQ | c4::yml::BLOCK); }
  void end_seq_block() { close_("-SEQ"); }
  void end_seq_flow(bool /*multiline*/, c4::yml::type_bits /*style*/ = c4::yml::FLOW_ML1) { close_("-SEQ"); }

  void add_sibling() { m_curr->evt_type = {}; }

  // ---- scalars

  void set_key_scalar_plain_empty() {
    scalar_(':', m_src.first(0));
    enable_<c4::yml::KEY | c4::yml::KEY_PLAIN | c4::yml::KEYNIL>();
  }
  void set_val_scalar_plain_empty() {
    scalar_(':', m_src.first(0));
    enable_<c4::yml::VAL | c4::yml::VAL_PLAIN | c4::yml::VALNIL>();
  }
  void set_key_scalar_plain(c4::csubstr s) {
    scalar_(':', s);
    enable_<c4::yml::KEY | c4::yml::KEY_PLAIN>();
  }
  void set_val_scalar_plain(c4::csubstr s) {
    scalar_(':', s);
    enable_<c4::yml::VAL | c4::yml::VAL_PLAIN>();
  }
  void set_key_scalar_dquoted(c4::csubstr s) {
    scalar_('"', s);
    enable_<c4::yml::KEY | c4::yml::KEY_DQUO>();
  }
  void set_val_scalar_dquoted(c4::csubstr s) {
    scalar_('"', s);
    enable_<c4::yml::VAL | c4::yml::VAL_DQUO>();
  }
  void set_key_scalar_squoted(c4::csubstr s) {
    scalar_('\'', s);
    enable_<c4::yml::KEY | c4::yml::KEY_SQUO>();
  }
  void set_val_scalar_squoted(c4::csubstr s) {
    scalar_('\'', s);
    enable_<c4::yml::VAL | c4::yml::VAL_SQUO>();
  }
  void set_key_scalar_literal(c4::csubstr s) {
    scalar_('|', s);
    enable_<c4::yml::KEY | c4::yml::KEY_LITERAL>();
  }
  void set_val_scalar_literal(c4::csubstr s) {
    scalar_('|', s);
    enable_<c4::yml::VAL | c4::yml::VAL_LITERAL>();
  }
  void set_key_scalar_folded(c4::csubstr s) {
    scalar_('>', s);
    enable_<c4::yml::KEY | c4::yml::KEY_FOLDED>();
  }
  void set_val_scalar_folded(c4::csubstr s) {
    scalar_('>', s);
    enable_<c4::yml::VAL | c4::yml::VAL_FOLDED>();
  }
  // Only reported when the engine is configured not to filter scalars; never here.
  void mark_key_scalar_unfiltered() {}
  void mark_val_scalar_unfiltered() {}

  // ---- anchors and aliases (anchor/tag events precede the node they apply to)

  void set_key_anchor(c4::csubstr anchor) {
    enable_<c4::yml::KEYANCH>();
    pending_(m_anchor, m_has_anchor, anchor);
  }
  void set_val_anchor(c4::csubstr anchor) {
    enable_<c4::yml::VALANCH>();
    pending_(m_anchor, m_has_anchor, anchor);
  }
  void set_key_ref(c4::csubstr ref) {
    enable_<c4::yml::KEY | c4::yml::KEYREF>();
    alias_(ref);
  }
  void set_val_ref(c4::csubstr ref) {
    enable_<c4::yml::VAL | c4::yml::VALREF>();
    alias_(ref);
  }

  // ---- tags and directives

  void set_key_tag(c4::csubstr tag) {
    enable_<c4::yml::KEYTAG>();
    pending_(m_tag, m_has_tag, tag);
  }
  void set_val_tag(c4::csubstr tag) {
    enable_<c4::yml::VALTAG>();
    pending_(m_tag, m_has_tag, tag);
  }
  void add_directive_yaml(c4::csubstr /*version*/) { ++m_events; }
  void add_directive_tag(c4::csubstr handle, c4::csubstr prefix) {
    if (!m_tag_directives.add(handle, prefix, m_curr_doc)) {
      RYML_ERR_PARSE_CB_(m_stack.m_callbacks, m_curr->pos, "too many %TAG directives");
    }
    ++m_events;
  }

  // ---- retroactive mappings

  /// `[a: b]`, `{[a]: b}`: the previous value (with its anchor and tag) is the first key of a new
  /// flow mapping that starts where that value started.
  void actually_val_is_first_key_of_new_map_flow() {
    insert_(m_last_node, "+MAP {}\n");
    enable_<c4::yml::MAP | c4::yml::FLOW_SL>();
    _push();
  }
  /// `[a]: b` at document level: the mapping starts right after the document start.
  void actually_val_is_first_key_of_new_map_block() {
    insert_(m_doc_body, "+MAP\n");
    _push();
  }

  // ---- arena: scalars that grow while filtering, and resolved tags

  c4::substr arena() { return c4::substr(m_arena.data(), m_arena_pos); }
  c4::substr arena_rem() { return c4::substr(m_arena.data() + m_arena_pos, m_arena.size() - m_arena_pos); }
  c4::substr alloc_arena(size_t len) {
    if (len > m_arena.size() - m_arena_pos) {
      size_t cap = m_arena.size() * 2;
      if (cap < m_arena_pos + len) cap = m_arena_pos + len;
      if (cap < 256) cap = 256;
      m_arena.resize(cap);  // the engine relocates its pending strings when the arena moves
    }
    c4::substr s(m_arena.data() + m_arena_pos, len);
    m_arena_pos += len;
    return s;
  }
  void reserve_arena(size_t len) {
    if (m_arena.size() < len) m_arena.resize(len);
  }

  // ---- stack helpers used by EventHandlerStack and the engine

  void _push() {
    _stack_push();
    m_curr->evt_type = {};
  }
  void _pop() { _stack_pop(); }

  template <c4::yml::type_bits bits>
  void enable_() noexcept {
    m_curr->evt_type |= bits;
  }
  template <c4::yml::type_bits bits>
  void disable_() noexcept {
    m_curr->evt_type &= ~bits;
  }
  template <c4::yml::type_bits bits>
  bool has_any_() const noexcept {
    return (m_curr->evt_type & bits) != c4::yml::type_bits(0);
  }

 private:
  void append_(c4::csubstr s) { m_out.append(s.str, s.len); }

  void line_(c4::csubstr text) {
    ++m_events;
    if (!m_render) return;
    append_(text);
    m_out.push_back('\n');
  }

  void doc_line_(c4::csubstr text) {
    line_(text);
    m_doc_body = m_out.size();
  }

  // Anchor then tag, as ints_to_testsuite renders them; both are consumed by the next node.
  void properties_() {
    if (m_has_anchor) {
      m_out.append(" &");
      m_out.append(m_anchor);
    }
    if (m_has_tag) {
      if (!m_tag.empty() && m_tag[0] == '<') {
        m_out.push_back(' ');
        m_out.append(m_tag);
      } else {
        m_out.append(" <");
        m_out.append(m_tag);
        m_out.push_back('>');
      }
    }
    m_has_anchor = false;
    m_has_tag = false;
  }

  void pending_(std::string &slot, bool &flag, c4::csubstr text) {
    ++m_events;
    flag = true;
    if (m_render) slot.assign(text.str, text.len);
  }

  void scalar_(char style, c4::csubstr s) {
    ++m_events;
    if (!m_render) {
      m_has_anchor = m_has_tag = false;
      return;
    }
    m_last_node = m_out.size();
    m_out.append("=VAL");
    properties_();
    m_out.push_back(' ');
    m_out.push_back(style);
    c4::yml::escape_scalar_fn([this](c4::csubstr part) { append_(part); }, s);
    m_out.push_back('\n');
  }

  void alias_(c4::csubstr ref) {
    ++m_events;
    if (!m_render) return;
    m_last_node = m_out.size();
    m_out.append("=ALI *");
    append_(ref.sub(1));  // skip the leading '*'
    m_out.push_back('\n');
  }

  void open_(c4::csubstr event, c4::csubstr style, c4::yml::type_bits bits) {
    ++m_events;
    if (m_render) {
      m_starts.push_back(m_out.size());
      append_(event);
      if (style.len) {
        m_out.push_back(' ');
        append_(style);
      }
      properties_();
      m_out.push_back('\n');
    } else {
      m_has_anchor = m_has_tag = false;
    }
    if (m_parent) m_parent->has_children = true;
    m_curr->evt_type |= bits;
    _push();
  }

  void close_(c4::csubstr event) {
    _pop();
    ++m_events;
    if (!m_render) return;
    append_(event);
    m_out.push_back('\n');
    // A closed container may become the key of a retroactive flow mapping.
    if (!m_starts.empty()) {
      m_last_node = m_starts.back();
      m_starts.pop_back();
    }
  }

  void insert_(size_t at, const char *text) {
    ++m_events;
    if (!m_render) return;
    m_out.insert(at, text);
    // The new mapping is an open container that starts at `at`.
    m_starts.push_back(at);
  }

  bool m_render;
  std::string m_out;
  std::vector<size_t> m_starts;  // output offset of each open container's start line
  std::string m_anchor;
  std::string m_tag;
  bool m_has_anchor = false;
  bool m_has_tag = false;
  size_t m_last_node = 0;  // start of the last complete node (scalar, alias or closed container)
  size_t m_doc_body = 0;   // output offset right after the last +DOC line
  uint64_t m_events = 0;
  std::vector<char> m_arena;
  size_t m_arena_pos = 0;
  c4::yml::TagDirectives m_tag_directives;
  c4::yml::TagCache m_tag_cache;
};

/// The selected parser configuration (D1): rapidyaml resolves tag handles, %TAG directives and
/// `!!x` shorthands itself.
inline c4::yml::ParserOptions selected_options() {
  c4::yml::ParserOptions options;
  options.resolve_tags(true).resolve_tags_all(true);
  return options;
}

}  // namespace truffledata::yaml::testing
