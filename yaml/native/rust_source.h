#ifndef TRUFFLEDATA_YAML_RUST_SOURCE_H
#define TRUFFLEDATA_YAML_RUST_SOURCE_H
// The opt-in pure-Rust parser backend's driver (TD_YAML_PARSER_RUST; //yaml/native:truffledata_yaml_rs).
// granit-parser (yaml/native/rs) produces flat event records in batches; this loop delivers each
// to the same yaml::handler entry points rapidyaml's ParseEngine calls, so schemas, keys, aliases,
// merges, tags, limits and the tape are identical code. The Rust side is pull-based: no C++
// exception unwinds through Rust and every Rust panic is caught there (STATUS_INTERNAL).
//
// Differences from the rapidyaml driver: the input is never filtered in place (granit borrows it
// immutably); escaped, folded and block scalars arrive as owned copies, which are copied into the
// handler's arena so key tables can keep pointers for the whole decode. The handler's positions
// come from the records (handler::external_position): error lines and columns are those of the
// event being delivered, and parser (syntax) messages are granit's.
#include "yaml/native/decoder.h"
#include "yaml/native/errors.h"

#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <new>

extern "C" {
// yaml/native/rs/events.rs (RsEvent) and ffi.rs (RsError); layouts must match.
typedef struct td_yaml_rs_event {
  uint8_t kind;
  uint8_t style;
  uint8_t flags;
  uint8_t reserved;
  uint32_t text_offset, text_length;
  uint32_t raw_offset, raw_length;
  uint32_t anchor_offset, anchor_length;
  uint32_t tag_offset, tag_length;
  uint32_t line, column;
  uint32_t version;
} td_yaml_rs_event;

typedef struct td_yaml_rs_error {
  int32_t status;
  uint32_t line, column, offset;
  uint32_t message_length;
  char message[256];
} td_yaml_rs_error;

void* td_yaml_rs_parser_create(void);
int32_t td_yaml_rs_parser_begin(void* parser, const uint8_t* input, uint64_t length, td_yaml_rs_error* error);
int32_t td_yaml_rs_parser_next(void* parser, td_yaml_rs_event* out, uint32_t capacity, const uint8_t** scratch,
                               td_yaml_rs_error* error);
void td_yaml_rs_parser_end(void* parser);
void td_yaml_rs_parser_destroy(void* parser);
}

static_assert(sizeof(td_yaml_rs_event) == 48, "td_yaml_rs_event layout");

namespace truffledata::yaml {

class rust_source {
 public:
  // Record kinds, styles and flags (events.rs).
  enum : uint8_t { doc_start = 1, doc_end, seq_start, seq_end, map_start, map_end, scalar, alias, tag_directive };
  enum : uint8_t { style_block_collection = 5, style_flow_collection = 6 };
  enum : uint8_t {
    flag_explicit = 1u << 0,
    flag_empty = 1u << 1,
    flag_text_in_input = 1u << 2,
    flag_anchor = 1u << 3,
    flag_tag = 1u << 4,
    flag_anchor_in_scratch = 1u << 5,
  };
  static constexpr uint32_t batch = 256;
  // Open collections tracked by the driver. The handler's own frame limit (260) and the writer's
  // depth limit (<= 128) fire first; this bound only keeps the driver's stack fixed-size.
  static constexpr size_t max_open = 512;

  rust_source() : parser_(td_yaml_rs_parser_create()) {
    if (!parser_) throw std::bad_alloc();
  }
  rust_source(const rust_source&) = delete;
  rust_source& operator=(const rust_source&) = delete;
  ~rust_source() { td_yaml_rs_parser_destroy(parser_); }

  // Parses input[0, length) (validated UTF-8, unchanged during the parse) into the started handler.
  // Throws yaml::error (or the handler's exceptions); re-runnable after any failure.
  void parse(handler& h, char* input, size_t length) {
    struct ender {
      void* parser;
      ~ender() { td_yaml_rs_parser_end(parser); }
    } end{parser_};
    td_yaml_rs_error failure;
    if (td_yaml_rs_parser_begin(parser_, reinterpret_cast<const uint8_t*>(input), length, &failure) != 0) raise(failure);
    input_ = input;
    length_ = length;
    depth_ = 0;
    h.start_parse("yaml", c4::substr(input, length));
    h.begin_stream();
    for (;;) {
      const uint8_t* scratch = nullptr;
      int32_t count = td_yaml_rs_parser_next(parser_, records_, batch, &scratch, &failure);
      if (count < 0) raise(failure);
      if (count == 0) break;
      scratch_ = reinterpret_cast<const char*>(scratch);
      for (int32_t i = 0; i < count; ++i) deliver(h, records_[i]);
    }
    if (depth_) throw error(status_internal, "YAML parser ended inside a collection");
    h.end_stream();
    h.finish_parse();
  }

 private:
  struct open {
    bool map;
    bool flow;
    bool key;  // a mapping expecting its next key
  };

  [[noreturn]] static void raise(const td_yaml_rs_error& f) {
    std::string_view message(f.message, f.message_length < sizeof f.message ? f.message_length : 0);
    if (f.status == -1) throw syntax_error(message).at(f.line, f.column).from_parser();
    // A syntax error rapidyaml's backend raises in the decoder (unsupported %YAML version,
    // unpaired surrogates): the decoder's message, and never a stream truncation.
    if (f.status == -4) throw syntax_error(message).at(f.line, f.column);
    throw error(status_internal, message.empty() ? std::string_view("YAML parser failure") : message);
  }

  // The next node fills a key slot of the enclosing mapping (and flips it to a value slot).
  bool next_is_key() {
    if (!depth_) return false;
    open& o = stack_[depth_ - 1];
    if (!o.map) return false;
    bool key = o.key;
    o.key = !key;
    return key;
  }

  c4::csubstr input_text(uint32_t offset, uint32_t length) const {
    if (size_t(offset) + length > length_) throw error(status_internal, "YAML parser record outside the input");
    return {input_ + offset, length};
  }
  // Owned scalar text lives in scratch until the next batch; the handler may keep pointers to key
  // text for the whole decode, so it is copied into the handler's arena.
  c4::csubstr text_of(handler& h, const td_yaml_rs_event& e) {
    if (e.flags & flag_text_in_input) return input_text(e.text_offset, e.text_length);
    if (e.text_length == 0) {
      // Empty quoted text: point just past the opening quote, as rapidyaml does.
      size_t at = size_t(e.raw_offset) + (e.raw_length ? 1 : 0);
      return {input_ + (at <= length_ ? at : length_), size_t(0)};
    }
    c4::substr copy = h.alloc_arena(e.text_length);
    std::memcpy(copy.str, scratch_ + e.text_offset, e.text_length);
    return copy;
  }
  c4::csubstr scratch_text(uint32_t offset, uint32_t length) const { return {scratch_ + offset, length}; }

  void properties(handler& h, const td_yaml_rs_event& e, bool key) {
    if (e.flags & flag_tag) {
      c4::csubstr tag = scratch_text(e.tag_offset, e.tag_length);
      key ? h.set_key_tag(tag) : h.set_val_tag(tag);
    }
    if (e.flags & flag_anchor) {
      c4::csubstr name = e.flags & flag_anchor_in_scratch ? scratch_text(e.anchor_offset, e.anchor_length)
                                                          : input_text(e.anchor_offset, e.anchor_length);
      key ? h.set_key_anchor(name) : h.set_val_anchor(name);
    }
  }

  void deliver(handler& h, const td_yaml_rs_event& e) {
    h.external_position({e.line, e.column, e.raw_offset, size_t(e.raw_offset) + e.raw_length});
    switch (e.kind) {
      case tag_directive: {
        // rapidyaml's TagDirectives keep the pointers; the handler copies both into its tables.
        c4::substr handle = h.alloc_arena(e.anchor_length), prefix = h.alloc_arena(e.tag_length);
        if (e.anchor_length) std::memcpy(handle.str, scratch_ + e.anchor_offset, e.anchor_length);
        if (e.tag_length) std::memcpy(prefix.str, scratch_ + e.tag_offset, e.tag_length);
        h.add_directive_tag(handle, prefix);
        return;
      }
      case doc_start:
        if (e.version) {
          char version[24];
          int n = std::snprintf(version, sizeof version, "%u.%u", e.version >> 16, e.version & 0xffff);
          h.add_directive_yaml(c4::csubstr(version, size_t(n)));
        }
        e.flags & flag_explicit ? h.begin_doc_expl() : h.begin_doc();
        return;
      case doc_end:
        e.flags & flag_explicit ? h.end_doc_expl() : h.end_doc();
        return;
      case seq_start:
      case map_start: {
        if (depth_ == max_open) throw limit_error("depth");
        const bool key = next_is_key(), map = e.kind == map_start, flow = e.style == style_flow_collection;
        properties(h, e, key);
        if (key) {
          if (map) flow ? h.begin_map_key_flow() : h.begin_map_key_block();
          else flow ? h.begin_seq_key_flow() : h.begin_seq_key_block();
        } else {
          if (map) flow ? h.begin_map_val_flow() : h.begin_map_val_block();
          else flow ? h.begin_seq_val_flow() : h.begin_seq_val_block();
        }
        stack_[depth_++] = {map, flow, true};
        return;
      }
      case seq_end:
      case map_end: {
        if (!depth_ || stack_[depth_ - 1].map != (e.kind == map_end))
          throw error(status_internal, "YAML parser closed the wrong collection");
        const open o = stack_[--depth_];
        if (o.map) o.flow ? h.end_map_flow(false) : h.end_map_block();
        else o.flow ? h.end_seq_flow(false) : h.end_seq_block();
        return;
      }
      case scalar: {
        const bool key = next_is_key();
        properties(h, e, key);
        if (e.flags & flag_empty) {
          key ? h.set_key_scalar_plain_empty() : h.set_val_scalar_plain_empty();
          return;
        }
        c4::csubstr text = text_of(h, e);
        switch (scalar_style(e.style)) {
          case scalar_style::plain: key ? h.set_key_scalar_plain(text) : h.set_val_scalar_plain(text); return;
          case scalar_style::single_quoted: key ? h.set_key_scalar_squoted(text) : h.set_val_scalar_squoted(text); return;
          case scalar_style::double_quoted: key ? h.set_key_scalar_dquoted(text) : h.set_val_scalar_dquoted(text); return;
          case scalar_style::literal: key ? h.set_key_scalar_literal(text) : h.set_val_scalar_literal(text); return;
          case scalar_style::folded: key ? h.set_key_scalar_folded(text) : h.set_val_scalar_folded(text); return;
        }
        throw error(status_internal, "unknown YAML scalar style from the parser");
      }
      case alias: {
        const bool key = next_is_key();
        c4::csubstr name = input_text(e.anchor_offset, e.anchor_length);
        key ? h.set_key_ref(name) : h.set_val_ref(name);
        return;
      }
      default:
        throw error(status_internal, "unknown YAML parser record");
    }
  }

  void* parser_;
  char* input_ = nullptr;
  size_t length_ = 0;
  const char* scratch_ = nullptr;
  size_t depth_ = 0;
  open stack_[max_open];
  td_yaml_rs_event records_[batch];
};

}  // namespace truffledata::yaml
#endif
