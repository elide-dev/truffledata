// Test-only (shared with plan Task 8): renders a decoded YAML document -- its tape plus the
// yaml_meta, lexical, tags, anchors and alias_targets side tables and the document flags -- back
// to yaml-test-suite event text (test.event format):
//
//   +DOC [---]  /  -DOC [...]
//   +MAP [{}] [&anchor] [<tag>]  ...  -MAP        (also a mapping stored as pairs)
//   +SEQ [[]] [&anchor] [<tag>]  ...  -SEQ
//   =VAL [&anchor] [<tag>] <style><text>          (style : ' " | >)
//   =ALI *anchor
//
// Scalar text is the node's string; a scalar that resolved to another kind (only explicitly
// tagged scalars do under FAILSAFE) is rendered from its lexical span. Escaping follows rapidyaml's
// escape_scalar, as the Task 1 renderer does.
#pragma once

#include "yaml/native/decoder.h"
#include "yaml/native/session.h"

#include <c4/yml/escape_scalar.hpp>

#include <algorithm>
#include <string>
#include <string_view>

namespace truffledata::yaml::testing {

class event_renderer {
 public:
  explicit event_renderer(const session::document_view& d) : d_(d) {}

  void document(std::string& out) {
    out += (d_.flags & doc_explicit_start) ? "+DOC ---\n" : "+DOC\n";
    node(0, out);
    out += (d_.flags & doc_explicit_end) ? "-DOC ...\n" : "-DOC\n";
  }

 private:
  const session::document_view& d_;

  std::string_view text(uint64_t index) const {
    return ct::text(d_.view, index);
  }

  // Anchor name of `index`, if anchored (anchors are sorted by node).
  std::string_view anchor_of(uint64_t index) const {
    const anchor_entry* begin = d_.anchors.data();
    const anchor_entry* end = begin + d_.anchors.size();
    for (const anchor_entry* a = begin; a != end; ++a)
      if (a->node == index) return d_.strings.substr(a->offset, a->length);
    return {};
  }

  void properties(uint64_t index, std::string& out) const {
    uint32_t meta = d_.meta[index];
    if (meta & meta_anchored) {
      out += " &";
      out += anchor_of(index);
    }
    uint32_t tag = (meta & meta_alias) ? 0 : meta >> meta_tag_shift;
    if (tag) {
      const span_entry& t = d_.tags[tag - 1];
      out += " <";
      out += d_.strings.substr(t.offset, t.length);
      out += '>';
    }
  }

  static char style_char(uint32_t style) {
    switch (style) {
      case 1: return '\'';
      case 2: return '"';
      case 3: return '|';
      case 4: return '>';
      default: return ':';
    }
  }

  // Renders the subtree at `index`; returns the index after it.
  uint64_t node(uint64_t index, std::string& out) const {
    uint64_t n = d_.view.words[index];
    uint32_t meta = d_.meta[index];
    uint32_t kind = ct::kind(n);
    if (meta & meta_alias) {
      uint64_t target = d_.alias_targets[meta >> meta_tag_shift];
      out += "=ALI *";
      out += anchor_of(target);
      out += '\n';
      return ct::step(index, n);
    }
    if (kind == ct::kind_object || kind == ct::kind_array) {
      uint32_t style = meta & meta_style_mask;
      bool pairs = kind == ct::kind_array && ct::field(n) && (d_.meta[index + 2] & meta_style_mask) == style_pair;
      bool mapping = kind == ct::kind_object || pairs;
      out += mapping ? "+MAP" : "+SEQ";
      if (style == style_flow) out += mapping ? " {}" : " []";
      properties(index, out);
      out += '\n';
      uint64_t at = index + 2;
      uint64_t children = kind == ct::kind_object ? 2 * uint64_t(ct::field(n)) : ct::field(n);
      for (uint64_t i = 0; i < children; ++i) {
        if (pairs) {
          at = node(at + 2, out);  // key and value of the pair
          at = node(at, out);
        } else {
          at = node(at, out);
        }
      }
      out += mapping ? "-MAP\n" : "-SEQ\n";
      return at;
    }
    out += "=VAL";
    properties(index, out);
    out += ' ';
    out += style_char(meta & meta_style_mask);
    std::string_view value;
    bool key_kind_string = ((meta & meta_key_kind_mask) >> meta_key_kind_shift) == 0;
    if (kind == ct::kind_string && key_kind_string) {
      value = text(index);
    } else {
      uint64_t span = lexical_span(d_.view, index);
      if (span) value = std::string_view(d_.source + (span >> 32), uint32_t(span));
    }
    c4::yml::escape_scalar_fn([&](c4::csubstr part) { out.append(part.str, part.len); },
                              c4::csubstr(value.data(), value.size()));
    out += '\n';
    return ct::step(index, n);
  }
};

// Renders documents as one yaml-test-suite stream.
inline void render_document(const session::document_view& d, std::string& out) { event_renderer(d).document(out); }

}  // namespace truffledata::yaml::testing
