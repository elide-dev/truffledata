#ifndef TRUFFLEDATA_YAML_TAGS_H
#define TRUFFLEDATA_YAML_TAGS_H
// Tag classification and YAML 1.1 type validation (docs/superpowers/plans/2026-11-06-yaml.md, D2):
//
//   - rapidyaml resolves `!!x`, `%TAG` handles and verbatim tags itself (resolve_tags_all); the
//     decoder receives `<tag:yaml.org,2002:str>`, `<tag:example.com,2000:app/x>`, `!local` or `!`;
//   - core tags (str, int, float, bool, null, seq, map) are enforced by scalars.h and decoder.h;
//   - 1.1 type tags keep their natural kinds and are validated here: !!binary (base64 through
//     simdutf, whitespace allowed), !!timestamp (the YAML 1.1 timestamp grammar), !!set, !!omap and
//     !!pairs (shape checked by the decoder when the collection closes);
//   - any other tag is preserved (its URI goes in the tags table) or rejected (UnknownTags.REJECT);
//   - `!` is non-specific: a scalar is a string.
#include "yaml/native/scalars.h"

#include <simdutf.h>

#include <cstddef>
#include <string_view>

namespace truffledata::yaml {

inline constexpr std::string_view core_prefix = "tag:yaml.org,2002:";

// The tag's URI without rapidyaml's brackets: `tag:yaml.org,2002:str`, `!local`, `!`.
inline std::string_view tag_uri(std::string_view resolved) {
  if (resolved.size() >= 2 && resolved.front() == '<' && resolved.back() == '>')
    return resolved.substr(1, resolved.size() - 2);
  return resolved;
}

inline tag_kind classify_tag(std::string_view uri) {
  if (uri == "!") return tag_kind::nonspecific;
  if (uri.substr(0, core_prefix.size()) != core_prefix) return tag_kind::other;
  std::string_view name = uri.substr(core_prefix.size());
  switch (name.size()) {
    case 3:
      if (name == "str") return tag_kind::str;
      if (name == "int") return tag_kind::int_;
      if (name == "seq") return tag_kind::seq;
      if (name == "map") return tag_kind::map;
      if (name == "set") return tag_kind::set;
      break;
    case 4:
      if (name == "bool") return tag_kind::bool_;
      if (name == "null") return tag_kind::null_;
      if (name == "omap") return tag_kind::omap;
      break;
    case 5:
      if (name == "float") return tag_kind::float_;
      if (name == "pairs") return tag_kind::pairs;
      if (name == "merge") return tag_kind::merge;
      if (name == "value") return tag_kind::value;
      break;
    case 6:
      if (name == "binary") return tag_kind::binary;
      break;
    case 9:
      if (name == "timestamp") return tag_kind::timestamp;
      break;
    default:
      break;
  }
  return tag_kind::other;
}

// !!binary: base64 (RFC 4648) with ASCII whitespace allowed anywhere, decoded through simdutf into
// a small stack buffer chunk by chunk, so validation allocates nothing.
inline bool valid_base64(std::string_view text) {
  char out[1024];
  const char* p = text.data();
  size_t left = text.size();
  for (;;) {
    size_t outlen = sizeof out;
    simdutf::result r = simdutf::base64_to_binary_safe(p, left, out, outlen);
    if (r.error == simdutf::error_code::SUCCESS) return true;
    if (r.error != simdutf::error_code::OUTPUT_BUFFER_TOO_SMALL || r.count == 0) return false;
    p += r.count;
    left -= r.count;
  }
}

// !!timestamp: the YAML 1.1 grammar (scalars.h, shared with implicit 1.1 resolution).
using detail::valid_timestamp;

}  // namespace truffledata::yaml
#endif
