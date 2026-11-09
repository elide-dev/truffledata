#ifndef TRUFFLEDATA_TAPE_TEXT_ARENA_H
#define TRUFFLEDATA_TAPE_TEXT_ARENA_H
#include "tape/native/ctape.h"
#include "tape/native/key_table.h"
#include <myna/values.h>
#include <simdutf.h>
#include <cstdint>
#include <cstring>

namespace truffledata::tape {

// Per-document text arena (JSON performance plan Task D2, moved here for YAML in YAML plan Task 4):
// every key and string node's decoded text,
// written once in tape order into a caller buffer in the representation a UTF-16 guest string
// uses, so a Truffle sink can build each string over the shared buffer without transcoding:
//   - Latin-1 (one byte per code point) when every code point is at most U+00FF. ASCII strings are
//     known from the side table and copied; other strings are converted by simdutf, which fails
//     (and is retried as UTF-16) at the first code point above U+00FF.
//   - UTF-16LE otherwise, at an even offset.
// Each node gets one table entry: offset << 32 | byte length << 1 | utf16. Non-string nodes and
// empty strings are 0. `meta` is the key-hash side table (key_table.h), whose ascii_flag marks
// ASCII strings. Strings must come from a validated tape (for JSON, stage 1 validated the input and
// simdjson decoded the escapes), so the non-validating UTF-16 conversion is safe.
//
// Capacity bound (JSON): a string of n decoded UTF-8 bytes was at least n + 2 input bytes (its
// quotes; escapes only lengthen the input) and needs at most 2n + 1 output bytes (UTF-16 plus one
// byte of alignment), so 2 × input length bytes always suffice — 2 × UTF-16 input bytes too, since
// each UTF-16 input unit yields at most one output unit. Java presizes to 2 × length + 16. Another
// producer must prove its own bound before sharing that presizing (YAML: plan Task 5).
inline constexpr uint64_t text_utf16_bit = 1;
inline constexpr size_t text_too_small = SIZE_MAX;

inline size_t text_capacity_bound(size_t input_bytes) { return 2 * input_bytes + 16; }

// One string's entry: appends `text` (n > 0 decoded UTF-8 bytes) at `used` and stores its table
// entry in `entry`; false when `capacity` is insufficient.
inline bool write_text_entry(const char* text, size_t length, bool plain, uint8_t* out, size_t capacity,
                             size_t& used, uint64_t& entry) {
  if (plain) {
    if (length > capacity - used) return false;
    std::memcpy(out + used, text, length);
    entry = uint64_t(used) << 32 | uint64_t(length) << 1;
    used += length;
    return true;
  }
  // Non-ASCII: Latin-1 output is shorter than the UTF-8 input, so the input length bounds it.
  if (length <= capacity - used) {
    size_t latin1 = simdutf::convert_utf8_to_latin1(text, length, reinterpret_cast<char*>(out + used));
    if (latin1 != 0) {
      entry = uint64_t(used) << 32 | uint64_t(latin1) << 1;
      used += latin1;
      return true;
    }
  }
  size_t at = used + (used & 1);
  size_t units = simdutf::utf16_length_from_utf8(text, length);
  if (at > capacity || 2 * units > capacity - at) return false;
  size_t written = simdutf::convert_valid_utf8_to_utf16le(text, length, reinterpret_cast<char16_t*>(out + at));
  entry = uint64_t(at) << 32 | uint64_t(2 * written) << 1 | text_utf16_bit;
  used = at + 2 * written;
  return true;
}

// Writes the arena; returns the bytes used, or text_too_small (leaving `table` and `out` partly
// written) when `capacity` is insufficient. `out` must be 2-byte aligned.
inline size_t write_text_arena(const myna_value_view& view, const uint32_t* meta, uint64_t* table,
                               uint8_t* out, size_t capacity) {
  size_t used = 0;
  for (uint64_t i = 0; i < view.node_count; ++i) {
    const myna_value_node& node = view.nodes[i];
    table[i] = 0;
    if ((node.tag & MYNA_VALUE_KIND_MASK) != MYNA_VALUE_STRING || node.length == 0) continue;
    uint64_t words[2];
    const char* text;
    if (node.tag & MYNA_VALUE_REGION_TEXT) {
      uint32_t region = myna_value_region_id(&node);
      const uint8_t* base = region == 0 ? view.bytes : view.regions[region - 1].data;
      text = reinterpret_cast<const char*>(base) + myna_value_offset(&node);
    } else {
      words[0] = node.payload;
      words[1] = node.next;
      text = reinterpret_cast<const char*>(words);
    }
    if (!write_text_entry(text, node.length, meta[i] & ascii_flag, out, capacity, used, table[i])) return text_too_small;
  }
  return used;
}

// The same over a compact tape (tape-gbps plan §3.5): one entry per word, 0 for non-string words,
// empty strings and second words; the ASCII flag comes from the word and the text from the input
// or the pool. The bound and the validation precondition are as above.
inline size_t write_text_arena(const td_tape_view& view, uint64_t* table, uint8_t* out, size_t capacity) {
  size_t used = 0;
  for (uint64_t i = 0; i < view.word_count;) {
    uint64_t w = view.words[i];
    table[i] = 0;
    uint64_t next = ctape::step(i, w);
    if (next == i + 2) table[i + 1] = 0;
    if (ctape::kind(w) == ctape::kind_string && ctape::field(w) != 0) {
      std::string_view text = ctape::text(view, i);
      if (!write_text_entry(text.data(), text.size(), w & ctape::ascii_flag, out, capacity, used, table[i]))
        return text_too_small;
    }
    i = next;
  }
  return used;
}
} // namespace truffledata::tape
#endif
