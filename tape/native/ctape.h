#ifndef TRUFFLEDATA_TAPE_CTAPE_H
#define TRUFFLEDATA_TAPE_CTAPE_H
// The compact tape (docs/superpowers/plans/2026-11-07-tape-gbps.md §3): the format-neutral
// hot-path representation of a decoded JSON or YAML document.
//
// Words. One contiguous array of native-endian 64-bit words; index 0 is the root value. Bits 60-63
// are the kind, bits 56-59 flags, bits 32-55 a 24-bit field, bits 0-31 a 32-bit field:
//
//   kind           words        bits 0-31                    bits 32-55        flags
//   NULL 0         1            optional raw token offset    token length
//   FALSE 1/TRUE 2 1            optional raw token offset    token length
//   I64 3/U64 4/   2            token offset (input)         token length      WIDE; word 1 = value bits
//   F64 5
//   DECIMAL 6      1 (2 OWNED)  token offset (input/pool)    token length      OWNED => WIDE, word 1 = raw span
//   STRING 7       1 (2 OWNED)  decoded offset (input/pool)  decoded length    ASCII, KEY, OWNED => WIDE,
//                                                                              word 1 = raw span
//   ARRAY 8/       2            next: the word after the     count (saturates  WIDE; word 1 = (directory
//   OBJECT 9                    subtree                      at 0xFFFFFF)      index + 1) << 32 | raw offset
//   REF 10         1            target word index            0                 (YAML aliases)
//   11-15          reserved; validate() rejects them
//
// Flags: WIDE (bit 59) a second word follows; OWNED (58) text lives in the pool; KEY (57) a STRING
// in key position; ASCII (56) every decoded byte < 0x80 (a claim: absent means "not known").
// In a TD_TAPE_YAML view an unowned STRING spans its plain source token directly; other
// views reference the body inside JSON quotes. Quoted/escaped YAML strings are OWNED.
// A raw span word is `offset << 32 | length` of the source token in the input (quotes included),
// 0 when the producer has none. Traversal: a node's successor is `wide ? i + 2 : i + 1`, except a
// container's, which is its `next`; a container's first child is `container + 2`. Objects
// alternate a KEY string and a value subtree. Second words are never nodes (Review Focus 2).
//
// Directories (§3.2). Each object with at least two members owns a run of `count` 64-bit entries
// `key_offset << 32 | hash` (bit-identical to myna_value_directory_entry) sorted by unsigned hash,
// then offset; `key_offset` is the key word's index minus the object's and `hash` is
// myna_value_hash (FNV-1a 32) over the decoded key bytes. The writer collects announced keys on a
// pending stack and sorts them when the object closes; while doing so an equal hash is
// byte-compared with every earlier key of its equal-hash run, and equal bytes throw duplicate_key.
// Objects of zero or one member have no directory (index 0). Directory runs are never moved or
// compacted: a rewrite that drops an object leaves its run unreferenced.
//
// Limits: a text token is at most 0xFFFFFF bytes; word indices, pool and input offsets are below
// 2^32; container counts above 0xFFFFFF read as 0xFFFFFF (the reader counts by walking).
#include "tape/native/arena.h"
#include <myna/values.hpp>
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <stdexcept>
#include <string_view>
#include <vector>
#if defined(__x86_64__)
#include <emmintrin.h>
#elif defined(__aarch64__)
#include <arm_neon.h>
#endif

#if !defined(__BYTE_ORDER__) || __BYTE_ORDER__ != __ORDER_LITTLE_ENDIAN__
#error "the compact tape's directory entries mirror Myna's little-endian layout"
#endif

extern "C" {
// The published descriptor (§3.3); json.seam/yaml.seam `struct TapeView` mirror it (Task 2).
// Borrowed: the producer keeps every array immutable and live while the view is in use.
typedef struct td_tape_view {
  uint32_t version;  // TD_TAPE_VIEW_VERSION
  uint32_t flags;    // TD_TAPE_NONFINITE, TD_TAPE_HAS_META, TD_TAPE_YAML
  const uint64_t* words;
  uint64_t word_count;
  const uint8_t* pool;  // owned text (escaped strings, YAML spellings)
  uint64_t pool_bytes;
  const uint8_t* input;  // the retained input, readable SIMDJSON_PADDING bytes past input_bytes
  uint64_t input_bytes;
  const uint64_t* directory;
  uint64_t directory_count;
  const uint32_t* meta;  // YAML: one uint32 per word (0 for second words)
  uint64_t meta_count;
} td_tape_view;
}
#define TD_TAPE_VIEW_VERSION 1u
#define TD_TAPE_NONFINITE 1u
#define TD_TAPE_HAS_META 2u
#define TD_TAPE_YAML 8u
static_assert(sizeof(td_tape_view) == 88, "td_tape_view layout");
static_assert(offsetof(td_tape_view, words) == 8 && offsetof(td_tape_view, pool) == 24, "td_tape_view layout");
static_assert(offsetof(td_tape_view, input) == 40 && offsetof(td_tape_view, directory) == 56, "td_tape_view layout");
static_assert(offsetof(td_tape_view, meta) == 72 && offsetof(td_tape_view, meta_count) == 80, "td_tape_view layout");

namespace truffledata::tape {
namespace ctape {

inline constexpr uint32_t kind_null = 0, kind_false = 1, kind_true = 2, kind_i64 = 3, kind_u64 = 4, kind_f64 = 5,
                          kind_decimal = 6, kind_string = 7, kind_array = 8, kind_object = 9, kind_ref = 10;
inline constexpr uint64_t wide_flag = uint64_t(1) << 59;
inline constexpr uint64_t owned_flag = uint64_t(1) << 58;
inline constexpr uint64_t key_flag = uint64_t(1) << 57;
inline constexpr uint64_t ascii_flag = uint64_t(1) << 56;
inline constexpr uint64_t flag_mask = uint64_t(0xF) << 56;
// The largest text token length and the saturated container count.
inline constexpr uint32_t max_length = 0xFFFFFF;
// Word indices (and so `next`) fit in 32 bits.
inline constexpr uint64_t max_words = UINT32_MAX;

inline constexpr uint64_t make(uint32_t kind, uint64_t flags, uint32_t field, uint32_t low) {
  return uint64_t(kind) << 60 | flags | uint64_t(field & max_length) << 32 | low;
}
inline constexpr uint32_t kind(uint64_t w) { return uint32_t(w >> 60); }
inline constexpr bool is_wide(uint64_t w) { return (w & wide_flag) != 0; }
// Bits 0-31: offset, `next` or REF target.
inline constexpr uint32_t low(uint64_t w) { return uint32_t(w); }
// Bits 32-55: length or count.
inline constexpr uint32_t field(uint64_t w) { return uint32_t(w >> 32) & max_length; }
inline constexpr bool is_container(uint32_t k) { return k == kind_array || k == kind_object; }
// The next word in tape order (a container's first child).
inline constexpr uint64_t step(uint64_t i, uint64_t w) { return i + (is_wide(w) ? 2 : 1); }
// The word after node i's subtree.
inline constexpr uint64_t after(uint64_t i, uint64_t w) { return is_container(kind(w)) ? low(w) : step(i, w); }
// A container's second word: directory index + 1 (0: none) and the raw offset of its opening token.
inline constexpr uint32_t directory_of(uint64_t second) { return uint32_t(second >> 32); }
inline constexpr uint64_t entry(uint32_t hash, uint32_t key_offset) { return uint64_t(key_offset) << 32 | hash; }
inline constexpr uint32_t entry_hash(uint64_t e) { return uint32_t(e); }
inline constexpr uint32_t entry_offset(uint64_t e) { return uint32_t(e >> 32); }

// A token or text extent in the input (or the pool).
struct span {
  uint32_t offset = 0;
  uint32_t length = 0;
};
inline constexpr uint64_t raw_word(span s) { return uint64_t(s.offset) << 32 | s.length; }

// Two keys of one object are equal after decoding. A std::invalid_argument, so status mappers that
// treat invalid input as a syntax error keep doing so.
class duplicate_key final : public std::invalid_argument {
 public:
  duplicate_key() : std::invalid_argument("duplicate key") {}
};

inline std::string_view text_at(const uint8_t* base, uint32_t offset, uint32_t length) {
  if (length == 0) return {};
  return {reinterpret_cast<const char*>(base) + offset, length};
}
// Text of the STRING or DECIMAL word at `index`.
inline std::string_view text(const td_tape_view& v, uint64_t index) {
  uint64_t w = v.words[index];
  return text_at((w & owned_flag) ? v.pool : v.input, low(w), field(w));
}

// Reference traversal of a well-formed tape (validate() first when the tape is untrusted):
// visitor.open(index, word) and visitor.close(index) bracket each container's children,
// visitor.scalar(index, word) visits every other node (keys are STRING words with key_flag).
template<class Visitor> void walk(const td_tape_view& v, Visitor& visitor) {
  std::vector<uint64_t> open;  // container indices, innermost last
  for (uint64_t i = 0; i < v.word_count;) {
    uint64_t w = v.words[i];
    if (is_container(kind(w))) {
      visitor.open(i, w);
      open.push_back(i);
    } else {
      visitor.scalar(i, w);
    }
    i = step(i, w);
    while (!open.empty() && low(v.words[open.back()]) == i) {
      visitor.close(open.back());
      open.pop_back();
    }
  }
}

namespace detail {
inline bool ascii(std::string_view s) {
  unsigned char any = 0;
  for (unsigned char c : s) any |= c;
  return any < 0x80;
}
inline bool within(uint64_t offset, uint64_t length, uint64_t size) { return offset <= size && length <= size - offset; }
}  // namespace detail

// A full structural check: null when well-formed, else the first problem found. Checks the view
// header; every word's kind, flags, WIDE consistency and payload fields; spans within the input,
// pool and raw spans within the input; ASCII claims; finite F64 unless NONFINITE; containers'
// `next` within their parent and equal to their children's end; counts (saturating); objects
// alternating KEY strings and non-KEY values; directories present exactly for objects of at least
// two members, in range, sorted, naming each of the object's key words once with the right hash,
// and holding no two equal keys; REF targets that are earlier, closed nodes; one root; and the
// meta table's shape. Allocates scratch from the heap (tests, debug checks).
inline const char* validate(const td_tape_view& v) {
  using detail::within;
  if (v.version != TD_TAPE_VIEW_VERSION) return "tape view version";
  if (v.flags & ~uint32_t(TD_TAPE_NONFINITE | TD_TAPE_HAS_META | TD_TAPE_YAML)) return "unknown tape view flags";
  if (v.word_count == 0 || !v.words) return "empty tape";
  if (v.word_count > max_words) return "tape word count";
  if ((v.pool_bytes && !v.pool) || (v.input_bytes && !v.input) || (v.directory_count && !v.directory))
    return "tape view array missing";
  if (v.flags & TD_TAPE_HAS_META) {
    if (!v.meta || v.meta_count != v.word_count) return "tape meta table size";
  } else if (v.meta_count) {
    return "tape meta table without HAS_META";
  }
  struct frame {
    uint64_t index, end, children;
    size_t keys;  // first of this object's key indices in `keys`
    bool object;
  };
  std::vector<frame> stack;
  std::vector<uint64_t> keys;
  std::vector<uint8_t> node(v.word_count, 0);  // 1 at every node start
  std::vector<uint64_t> offsets;
  uint64_t i = 0;
  for (;;) {
    while (!stack.empty() && i >= stack.back().end) {
      const frame f = stack.back();
      if (i != f.end) return "node crosses its container's end";
      if (f.object && f.children % 2) return "object key without a value";
      uint64_t count = f.object ? f.children / 2 : f.children;
      if (field(v.words[f.index]) != std::min<uint64_t>(count, max_length)) return "container count";
      if (f.object) {
        uint32_t dir = directory_of(v.words[f.index + 1]);
        if (count < 2) {
          if (dir) return "directory on an object below two members";
        } else {
          if (!dir) return "object of two or more members without a directory";
          if (!within(uint64_t(dir) - 1, count, v.directory_count)) return "directory run out of range";
          const uint64_t* e = v.directory + (dir - 1);
          const uint64_t* own = keys.data() + f.keys;
          offsets.clear();
          for (uint64_t k = 0; k < count; ++k) {
            if (k && (entry_hash(e[k - 1]) > entry_hash(e[k])
                      || (entry_hash(e[k - 1]) == entry_hash(e[k]) && entry_offset(e[k - 1]) >= entry_offset(e[k]))))
              return "directory not sorted by hash and offset";
            uint64_t key = f.index + entry_offset(e[k]);
            if (!std::binary_search(own, own + count, key)) return "directory entry names no key of its object";
            std::string_view t = text(v, key);
            if (myna_value_hash(reinterpret_cast<const uint8_t*>(t.data()), t.size()) != entry_hash(e[k]))
              return "directory hash differs from its key";
            for (uint64_t j = k; j > 0 && entry_hash(e[j - 1]) == entry_hash(e[k]); --j)
              if (text(v, f.index + entry_offset(e[j - 1])) == t) return "duplicate key";
          }
        }
        keys.resize(f.keys);
      }
      stack.pop_back();
    }
    if (stack.empty() && i > 0) break;
    uint64_t limit = stack.empty() ? v.word_count : stack.back().end;
    if (i >= limit) return "truncated tape";
    uint64_t w = v.words[i];
    uint32_t k = kind(w);
    uint64_t flags = w & flag_mask;
    bool wide = flags & wide_flag, owned = flags & owned_flag;
    if (k > kind_ref) return "reserved word kind";
    if (wide && i + 1 >= limit) return "second word past its container";
    if ((flags & (key_flag | ascii_flag)) && k != kind_string) return "KEY or ASCII flag on a non-string";
    if (owned && k != kind_string && k != kind_decimal) return "OWNED flag on a non-text kind";
    bool key_position = !stack.empty() && stack.back().object && stack.back().children % 2 == 0;
    if (key_position) {
      if (k != kind_string || !(flags & key_flag)) return "object key is not a KEY string";
      keys.push_back(i);
    } else if (flags & key_flag) {
      return "KEY flag outside key position";
    }
    if ((v.flags & TD_TAPE_HAS_META) && wide && v.meta[i + 1] != 0) return "meta on a second word";
    switch (k) {
      case kind_null:
      case kind_false:
      case kind_true:
        if (flags != 0) return "flags on a constant word";
        if (!within(low(w), field(w), v.input_bytes)) return "constant span past the input";
        break;
      case kind_i64:
      case kind_u64:
      case kind_f64:
        if (flags != wide_flag) return "number word flags";
        if (!within(low(w), field(w), v.input_bytes)) return "number span past the input";
        if (k == kind_f64 && !(v.flags & TD_TAPE_NONFINITE)) {
          double d;
          std::memcpy(&d, &v.words[i + 1], sizeof d);
          if (!std::isfinite(d)) return "non-finite F64 without NONFINITE";
        }
        break;
      case kind_decimal:
      case kind_string:
        if (wide != owned) return "WIDE differs from OWNED on text";
        if (!within(low(w), field(w), owned ? v.pool_bytes : v.input_bytes)) return "text span past its storage";
        if (owned) {
          uint64_t raw = v.words[i + 1];
          if (!within(raw >> 32, uint32_t(raw), v.input_bytes)) return "raw span past the input";
        }
        if ((flags & ascii_flag) && !detail::ascii(text(v, i))) return "ASCII flag on non-ASCII text";
        break;
      case kind_array:
      case kind_object: {
        if (flags != wide_flag) return "container word flags";
        uint64_t second = v.words[i + 1];
        if (k == kind_array && directory_of(second)) return "directory on an array";
        if (low(second) > v.input_bytes) return "container raw offset past the input";
        uint64_t next = low(w);
        if (next < i + 2 || next > limit) return "container next out of range";
        break;
      }
      case kind_ref: {
        if (w != make(kind_ref, 0, 0, low(w))) return "REF word flags";
        uint64_t target = low(w);
        if (target >= i || !node[target]) return "REF target is not an earlier node";
        if (after(target, v.words[target]) > i) return "REF target is open";
        break;
      }
    }
    node[i] = 1;
    if (!stack.empty()) ++stack.back().children;
    if (is_container(k)) {
      stack.push_back({i, low(w), 0, keys.size(), k == kind_object});
      i += 2;
    } else {
      i = step(i, w);
    }
  }
  if (i != v.word_count) return "words after the root";
  return nullptr;
}

// Converts a well-formed compact tape (see validate) into a Myna value tape in `memory` (normally
// the owning block arena; nothing is freed until it resets). ABI v4 (`version` MYNA_VALUE_ABI_V4)
// carries the directories, re-addressed to node offsets and emitted in object-close order, REF
// nodes and, under TD_TAPE_HAS_META, one metadata entry per node; ABI v3 (MYNA_VALUE_ABI) is the
// same nodes with object payloads 0 and no extension tables, and refuses REF. Text follows the
// shipped writer (tape_writer.h): STRING text of at most 16 bytes is inline, longer input text is
// region 1 (the input), longer owned text is copied into region 0; DECIMAL input tokens always
// reference region 1; owned DECIMAL text is inline or copied. NONFINITE maps to
// MYNA_VALUE_ALLOW_NON_FINITE.
inline myna_value_view_v4 to_myna(const td_tape_view& v, std::pmr::memory_resource* memory,
                                  uint32_t version = MYNA_VALUE_ABI_V4) {
  if (version != MYNA_VALUE_ABI && version != MYNA_VALUE_ABI_V4) throw std::invalid_argument("Myna ABI version");
  if (!memory) throw std::invalid_argument("null Myna allocator");
  if (v.word_count == 0) throw std::logic_error("empty compact tape");
  const bool v4 = version == MYNA_VALUE_ABI_V4;
  const bool metadata = v4 && (v.flags & TD_TAPE_HAS_META);
  uint64_t count = 0;
  for (uint64_t i = 0; i < v.word_count; i = step(i, v.words[i])) ++count;
  arena_vec<myna_value_node> nodes;
  arena_vec<uint8_t> bytes;
  arena_vec<myna_value_directory_entry> directory;
  arena_vec<uint32_t> meta;
  struct frame {
    uint64_t word, node, children;
  };
  arena_vec<frame> open;
  nodes.reserve(memory, count);
  if (metadata) meta.reserve(memory, count);
  // Node index of every node-start word (second words are left unset).
  auto* node_of = static_cast<uint32_t*>(memory->allocate(v.word_count * sizeof(uint32_t), alignof(uint32_t)));
  auto close = [&](const frame& f) {
    myna_value_node& n = nodes[f.node];
    bool object = n.tag == MYNA_VALUE_OBJECT;
    uint64_t members = object ? f.children / 2 : f.children;
    n.length = static_cast<uint32_t>(members);
    n.next = nodes.size();
    uint32_t dir = object ? directory_of(v.words[f.word + 1]) : 0;
    if (v4 && dir) {
      size_t start = directory.size();
      myna_value_directory_entry* out = directory.extend(memory, members);
      for (uint64_t k = 0; k < members; ++k) {
        uint64_t e = v.directory[dir - 1 + k];
        out[k] = {entry_hash(e), node_of[f.word + entry_offset(e)] - node_of[f.word]};
      }
      n.payload = start + 1;
    }
  };
  auto own = [&](std::string_view t) {
    if (bytes.size() > UINT32_MAX) throw std::length_error("Myna region offset limit");
    uint64_t at = bytes.size();
    std::memcpy(bytes.extend(memory, t.size()), t.data(), t.size());
    return at;
  };
  for (uint64_t i = 0; i < v.word_count;) {
    uint64_t w = v.words[i];
    uint32_t k = kind(w);
    size_t index = nodes.size();
    node_of[i] = static_cast<uint32_t>(index);
    if (open.size()) ++open.back().children;
    myna_value_node n{0, 0, 0, index + 1};
    switch (k) {
      case kind_null: n.tag = MYNA_VALUE_NULL; break;
      case kind_false: n.tag = MYNA_VALUE_BOOL; break;
      case kind_true: n.tag = MYNA_VALUE_BOOL; n.payload = 1; break;
      case kind_i64: n.tag = MYNA_VALUE_I64; n.payload = v.words[i + 1]; break;
      case kind_u64: n.tag = MYNA_VALUE_U64; n.payload = v.words[i + 1]; break;
      case kind_f64: n.tag = MYNA_VALUE_F64; n.payload = v.words[i + 1]; break;
      case kind_decimal:
      case kind_string: {
        uint32_t tag = k == kind_string ? MYNA_VALUE_STRING : MYNA_VALUE_DECIMAL;
        std::string_view t = text(v, i);
        bool owned = w & owned_flag;
        n.length = static_cast<uint32_t>(t.size());
        if ((owned || k == kind_string) && t.size() <= MYNA_VALUE_INLINE_BYTES) {
          uint64_t packed[2] = {0, 0};
          if (t.size()) std::memcpy(packed, t.data(), t.size());
          n.tag = tag;
          n.payload = packed[0];
          n.next = packed[1];
        } else if (owned) {
          n.tag = tag | MYNA_VALUE_REGION_TEXT;
          n.payload = own(t);
        } else {
          n.tag = tag | MYNA_VALUE_REGION_TEXT;
          n.payload = uint64_t(1) << 32 | low(w);
        }
        break;
      }
      case kind_array:
      case kind_object:
        n.tag = k == kind_array ? MYNA_VALUE_ARRAY : MYNA_VALUE_OBJECT;
        break;
      case kind_ref:
        if (!v4) throw std::invalid_argument("REF needs Myna ABI 4");
        n.tag = MYNA_VALUE_REF;
        n.payload = node_of[low(w)];
        break;
      default:
        throw std::invalid_argument("reserved compact tape kind");
    }
    *nodes.extend(memory, 1) = n;
    if (metadata) *meta.extend(memory, 1) = v.meta[i];
    if (is_container(k)) *open.extend(memory, 1) = {i, index, 0};
    i = step(i, w);
    while (open.size() && low(v.words[open.back().word]) == i) {
      close(open.back());
      open.truncate(open.size() - 1);
    }
  }
  memory->deallocate(node_of, v.word_count * sizeof(uint32_t), alignof(uint32_t));
  auto* region = static_cast<myna_value_region*>(memory->allocate(sizeof(myna_value_region), alignof(myna_value_region)));
  *region = {v.input, v.input_bytes};
  uint32_t flags = (v.flags & TD_TAPE_NONFINITE) ? MYNA_VALUE_ALLOW_NON_FINITE : 0;
  myna_value_view base{version, flags, nodes.data(), nodes.size(), bytes.data(), bytes.size(), region, 1};
  if (!v4) return {base, nullptr, 0, nullptr, 0};
  return {base, directory.data(), directory.size(), metadata ? meta.data() : nullptr, metadata ? meta.size() : 0};
}
}  // namespace ctape

// A checked key sequence at each container depth. Templates own their bytes and survive the
// input and arena from which they were learned. Active frames belong to one writer at a time.
struct key_shape_cache {
  static constexpr uint32_t depths = 32, max_keys = 32, max_bytes = 32;
  struct key {
    uint8_t bytes[max_bytes];
    uint32_t length, hash;
    bool equals(const uint8_t* text, uint32_t size, bool padded) const {
      if (length != size) return false;
      if (size == 0) return true;
      if (!padded) return std::memcmp(bytes, text, size) == 0;
      // JSON's clean keys live in input with at least 64 readable padding bytes. Other
      // producers use the bounded fallback above; no padding is inferred from a pointer.
      auto equal16 = [](const uint8_t* a, const uint8_t* b, uint32_t n) {
#if defined(__x86_64__)
        unsigned mask = unsigned(_mm_movemask_epi8(_mm_cmpeq_epi8(
            _mm_loadu_si128(reinterpret_cast<const __m128i*>(a)),
            _mm_loadu_si128(reinterpret_cast<const __m128i*>(b)))));
        unsigned needed = n >= 16 ? 0xffffu : (1u << n) - 1;
        return (mask & needed) == needed;
#elif defined(__aarch64__)
        static constexpr uint8_t positions[] = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
        uint8x16_t relevant = vcltq_u8(vld1q_u8(positions), vdupq_n_u8(uint8_t(n)));
        uint8x16_t equal = vceqq_u8(vld1q_u8(a), vld1q_u8(b));
        return vminvq_u8(vorrq_u8(equal, vmvnq_u8(relevant))) == 255;
#else
        return std::memcmp(a, b, n < 16 ? n : 16) == 0;
#endif
      };
      return equal16(bytes, text, size) && (size <= 16 || equal16(bytes + 16, text + 16, size - 16));
    }
    void set(std::string_view text, uint32_t value) {
      length = uint32_t(text.size()); hash = value;
      std::memset(bytes, 0, sizeof bytes);
      if (length) std::memcpy(bytes, text.data(), length);
    }
  };
  struct level { uint32_t count = 0; key keys[max_keys]; uint8_t rank[max_keys]; };
  struct frame { size_t object, first; uint32_t depth, count; bool matched; };
  level levels[depths];
  frame frames[depths];
  uint32_t active = 0;
  void start() { active = 0; }
  void begin(size_t object, size_t first, uint32_t depth) {
    if (depth < depths) frames[active++] = {object, first, depth, 0, true};
  }
  frame* current(size_t object) {
    return active && frames[active - 1].object == object ? &frames[active - 1] : nullptr;
  }
};

// Writes a compact tape into arena storage (§3.4). Producers announce each member key with
// member_key(object, hash) before writing it (JSON), or close an object with end_rescan(), which
// hashes its keys from their text (YAML, after merges and last-wins removals). Either way an object
// of at least two members gets its directory and its duplicate check when it closes.
//
// Limits are Myna's (value count, decoded text bytes, depth), so JSON's node limit keeps its
// meaning: one value is one Myna node. The rewrite operations (truncate, erase, append) keep the
// value count, `next`, REF targets and directory references consistent; they refuse to run while
// announced keys are pending, so a producer that rewrites closes objects with end_rescan().
class ctape_writer final {
  std::pmr::memory_resource* memory_;
  arena_vec<uint64_t> words_;
  arena_vec<uint8_t> pool_;
  arena_vec<uint64_t> directory_;
  // Announced keys of open objects, innermost last, as hash << 32 | key offset: the sort order.
  arena_vec<uint64_t> pending_;
  const uint8_t* input_ = nullptr;
  uint64_t input_bytes_ = 0;
  myna::value_limits limits_;
  uint64_t values_ = 0, text_bytes_ = 0;
  bool nonfinite_ = false;
  key_shape_cache* shapes_ = nullptr;

  [[noreturn, gnu::noinline, gnu::cold]] static void limit(const char* what) { throw std::length_error(what); }
  [[noreturn, gnu::noinline, gnu::cold]] static void invalid(const char* what) { throw std::invalid_argument(what); }
  [[noreturn, gnu::noinline, gnu::cold]] static void misuse(const char* what) { throw std::logic_error(what); }
  [[noreturn, gnu::noinline, gnu::cold]] static void range(const char* what) { throw std::out_of_range(what); }
  [[gnu::always_inline]] void value() {
    if (values_ >= limits_.nodes) limit("tape value limit");
    ++values_;
  }
  [[gnu::always_inline]] uint64_t* push(size_t n) {
    if (words_.size() > ctape::max_words - n) limit("tape word limit");
    return words_.extend(memory_, n);
  }
  [[gnu::always_inline]] void charge(size_t length) {
    if (length > ctape::max_length) limit("tape text token limit");
    if (length > limits_.text_bytes - text_bytes_) limit("tape text limit");
    text_bytes_ += length;
  }
  [[gnu::always_inline]] void in_input(ctape::span s) const {
    if (!ctape::detail::within(s.offset, s.length, input_bytes_)) range("tape input span");
  }
  void number(uint32_t kind, uint64_t bits, ctape::span token) {
    commit_number(token);
    parsed_number(kind, bits, token);
  }
  uint32_t own(std::string_view text) {
    if (text.size() > UINT32_MAX - pool_.size()) limit("tape pool limit");
    uint32_t at = static_cast<uint32_t>(pool_.size());
    if (text.size()) std::memcpy(pool_.extend(memory_, text.size()), text.data(), text.size());
    return at;
  }
  void owned_text(uint32_t kind, uint64_t flags, std::string_view text, ctape::span raw) {
    charge(text.size());
    in_input(raw);
    value();
    uint32_t at = own(text);
    uint64_t* w = push(2);
    w[0] = ctape::make(kind, flags | ctape::owned_flag | ctape::wide_flag, uint32_t(text.size()), at);
    w[1] = ctape::raw_word(raw);
  }
  static uint64_t flags(bool ascii, bool key) { return (ascii ? ctape::ascii_flag : 0) | (key ? ctape::key_flag : 0); }
  // Nodes in [from, to), which must start at a node boundary.
  uint64_t nodes_in(const uint64_t* w, size_t from, size_t to) const {
    uint64_t n = 0;
    for (size_t i = from; i < to; i = ctape::step(i, w[i])) ++n;
    return n;
  }
  // The object at `object` closes with `count` keys on top of pending_: at two or more they become
  // its directory run (sorted, then checked for duplicates within equal-hash runs).
  [[gnu::noinline, gnu::cold]] void materialize(key_shape_cache::frame& frame) {
    const auto& shape = shapes_->levels[frame.depth];
    for (uint32_t i = 0; i < frame.count; ++i)
      pending_[frame.first + i] |= uint64_t(shape.keys[i].hash) << 32;
    frame.matched = false;
  }
  void close_directory(size_t object, uint64_t count) {
    if (count > pending_.size()) misuse("object key not announced");
    size_t first = pending_.size() - count;
    auto* frame = shapes_ ? shapes_->current(object) : nullptr;
    auto* shape = frame ? &shapes_->levels[frame->depth] : nullptr;
    if (frame && (frame->count != count || frame->first != first)) misuse("object key count mismatch");
    bool hit = frame && frame->matched && count == shape->count;
    if (frame && frame->matched && !hit) materialize(*frame);
    if (count >= 2) {
      size_t start = directory_.size();
      if (start >= UINT32_MAX - count) limit("tape directory limit");
      uint64_t* out = directory_.extend(memory_, count);
      const uint64_t* in = pending_.data() + first;
      if (hit) {
        for (size_t i = 0; i < count; ++i)
          out[shape->rank[i]] = ctape::entry(shape->keys[i].hash, uint32_t(in[i]));
      } else {
      // Keys arrive in offset order; (hash, offset) keys are unique, so any sort is stable here.
      if (count <= 24) {
        for (size_t i = 0; i < count; ++i) {
          uint64_t e = in[i];
          size_t j = i;
          while (j > 0 && out[j - 1] > e) { out[j] = out[j - 1]; --j; }
          out[j] = e;
        }
      } else {
        std::memcpy(out, in, count * sizeof(uint64_t));
        std::sort(out, out + count);
      }
      // Equal decoded keys have equal hashes, so they sit in one equal-hash run; compare each key
      // with every earlier key of its run (runs of one are the common case: one compare of hashes).
      for (size_t i = 1; i < count; ++i) {
        if ((out[i] >> 32) != (out[i - 1] >> 32)) continue;
        std::string_view key = text_of(object + uint32_t(out[i]));
        for (size_t j = i; j > 0 && (out[j - 1] >> 32) == (out[i] >> 32); --j)
          if (text_of(object + uint32_t(out[j - 1])) == key) throw ctape::duplicate_key();
      }
      // Install only after every duplicate check has succeeded. Hash collisions preserve the
      // source-order tie break, so the rank permutation reproduces the sorted directory exactly.
      if (shape && count <= key_shape_cache::max_keys) {
        bool fits = true;
        for (size_t i = 0; i < count; ++i)
          fits &= text_of(object + uint32_t(in[i])).size() <= key_shape_cache::max_bytes;
        if (fits) {
          for (size_t i = 0; i < count; ++i) {
            shape->keys[i].set(text_of(object + uint32_t(in[i])), uint32_t(in[i] >> 32));
            for (size_t rank = 0; rank < count; ++rank)
              if (uint32_t(out[rank]) == uint32_t(in[i])) shape->rank[i] = uint8_t(rank);
          }
          shape->count = uint32_t(count);
        }
      }
      for (size_t i = 0; i < count; ++i) out[i] = ctape::entry(uint32_t(out[i] >> 32), uint32_t(out[i]));
      }
      uint64_t& second = words_[object + 1];
      second = uint64_t(start + 1) << 32 | uint32_t(second);
    }
    pending_.truncate(first);
    if (frame) --shapes_->active;
  }

 public:
  // Descriptor flag set when the tape holds a non-finite F64 (YAML).
  static constexpr uint32_t nonfinite_flag = TD_TAPE_NONFINITE;

  ctape_writer(std::pmr::memory_resource* memory, myna::value_limits limits) : memory_(memory), limits_(limits) {
    if (!memory) throw std::invalid_argument("null tape allocator");
  }
  void shapes(key_shape_cache* cache) { shapes_ = cache; if (cache) cache->start(); }
  ctape_writer(const ctape_writer&) = delete;
  ctape_writer& operator=(const ctape_writer&) = delete;

  // Registers the retained input that spans index (at most 4 GiB; the producer keeps it live and
  // padded as its readers require).
  void input(std::string_view input) {
    if (input.size() > UINT32_MAX) limit("tape input limit");
    input_ = reinterpret_cast<const uint8_t*>(input.data());
    input_bytes_ = input.size();
  }
  // Presizes words, pool bytes and directory entries (and up to 256 pending keys); later growth
  // moves to a larger arena span.
  void reserve(size_t words, size_t pool_bytes, size_t directory_entries) {
    words_.reserve(memory_, words);
    pool_.reserve(memory_, pool_bytes);
    directory_.reserve(memory_, directory_entries);
    pending_.reserve(memory_, directory_entries < 256 ? directory_entries : 256);
  }

  // JSON stage-1 bounds; the pool includes readable scratch padding for scalar-root numbers.
  void reserve_structural(size_t count, size_t input_bytes) {
    words_.reserve(memory_, 2 * count + 2);
    pool_.reserve(memory_, input_bytes + 64);
    directory_.reserve(memory_, count / 2 + 1);
    pending_.reserve(memory_, count / 2 + 1);
  }

  // Reuse the arrays between documents; the arena remains live until retain or capacity release.
  void clear() {
    words_.truncate(0); pool_.truncate(0); directory_.truncate(0); pending_.truncate(0);
    values_ = text_bytes_ = 0; nonfinite_ = false;
    if (shapes_) shapes_->start();
  }
  uint8_t* pool_reserve(size_t bytes) {
    if (bytes > UINT32_MAX - pool_.size()) limit("tape pool limit");
    pool_.reserve(memory_, pool_.size() + bytes);
    return pool_.data() + pool_.size();
  }
  void pool_commit(size_t bytes) { pool_.extend(memory_, bytes); }
  size_t pool_size() const { return pool_.size(); }
  const uint8_t* pool_data() const { return pool_.data(); }
  [[gnu::always_inline]] void string_owned(uint32_t offset, uint32_t length, ctape::span raw, bool ascii, bool key) {
    charge(length); in_input(raw); value();
    uint64_t* w = push(2);
    w[0] = ctape::make(ctape::kind_string, flags(ascii, key) | ctape::owned_flag | ctape::wide_flag, length, offset);
    w[1] = ctape::raw_word(raw);
  }

  // Opens an ARRAY or OBJECT; `depth` counts the containers already open around it (0 for the root)
  // and `raw_offset` is its opening token's input offset (0 when it has none). Returns its index.
  size_t begin(uint32_t kind, uint32_t depth, uint32_t raw_offset = 0) {
    if (!ctape::is_container(kind)) invalid("tape container kind");
    if (depth >= limits_.depth) limit("tape depth limit");
    value();
    size_t index = words_.size();
    uint64_t* w = push(2);
    w[0] = ctape::make(kind, ctape::wide_flag, 0, 0);  // next 0: open
    w[1] = raw_offset;
    if (shapes_ && kind == ctape::kind_object) shapes_->begin(index, pending_.size(), depth);
    return index;
  }
  // Closes the container at `index`, the innermost open one: `count` is elements or members. An
  // object takes its `count` announced keys (member_key) as its directory and duplicate check.
  void end(size_t index, uint64_t count) {
    uint64_t w = words_[index];
    if (ctape::kind(w) == ctape::kind_object) close_directory(index, count);
    words_[index] = ctape::make(ctape::kind(w), ctape::wide_flag,
                                uint32_t(count < ctape::max_length ? count : ctape::max_length),
                                uint32_t(words_.size()));
  }
  // Closes the innermost open object, hashing its keys from their text (producers that rewrite
  // their children: YAML). Builds the directory and checks duplicates as end() does.
  template<class Hash> void end_rescan(size_t index, Hash hash_of) {
    if (ctape::kind(words_[index]) != ctape::kind_object) misuse("end_rescan on a non-object");
    uint64_t members = 0;
    for (size_t k = index + 2; k < words_.size(); ++members) {
      uint64_t key = words_[k];
      if (ctape::kind(key) != ctape::kind_string || !(key & ctape::key_flag)) misuse("object key is not a KEY string");
      std::string_view t = text_of(k);
      uint32_t hash = hash_of(t);
      pending_.push(memory_, uint64_t(hash) << 32 | (k - index));
      size_t v = ctape::step(k, key);
      if (v >= words_.size()) misuse("object key without a value");
      k = ctape::after(v, words_[v]);
    }
    end(index, members);
  }
  void end_rescan(size_t index) {
    end_rescan(index, [](std::string_view text) {
      return myna_value_hash(reinterpret_cast<const uint8_t*>(text.data()), text.size());
    });
  }
  // Announces the word written next as a member key of the open object at `object`; `hash` is
  // myna_value_hash over the decoded key bytes.
  [[gnu::always_inline]] void member_key(size_t object, uint32_t hash) {
    pending_.push(memory_, uint64_t(hash) << 32 | (words_.size() - object));
  }

  // Announce decoded bytes before the key word. A hit skips hashing and sorting, but every
  // position and the final count must match the independently checked template.
  [[gnu::always_inline]] void key_text(size_t object, const uint8_t* text, uint32_t length, bool padded = false) {
    uint64_t offset = words_.size() - object;
    auto* frame = shapes_ ? shapes_->current(object) : nullptr;
    if (frame) {
      auto& shape = shapes_->levels[frame->depth];
      uint32_t ordinal = frame->count;
      if (frame->matched) {
        if (ordinal < shape.count && shape.keys[ordinal].equals(text, length, padded)) {
          pending_.push(memory_, offset);
          ++frame->count;
          return;
        }
        materialize(*frame);
      }
      ++frame->count;
    }
    pending_.push(memory_, uint64_t(myna_value_hash(text, length)) << 32 | offset);
  }

  void null(ctape::span token = {}) {
    if (token.length > ctape::max_length) limit("tape text token limit");
    in_input(token); value();
    *push(1) = ctape::make(ctape::kind_null, 0, token.length, token.offset);
  }
  void boolean(bool b, ctape::span token = {}) {
    if (token.length > ctape::max_length) limit("tape text token limit");
    in_input(token); value();
    *push(1) = ctape::make(b ? ctape::kind_true : ctape::kind_false, 0, token.length, token.offset);
  }
  // The numeric parser can invoke its callback before reporting a grammar error. Provisional
  // words are not counted/published until commit_number; errors roll back before DECIMAL fallback.
  [[gnu::always_inline]] void parsed_number(uint32_t kind, uint64_t bits, ctape::span token) {
    uint64_t* w = push(2);
    w[0] = ctape::make(kind, ctape::wide_flag, token.length, token.offset);
    w[1] = bits;
  }
  [[gnu::always_inline]] void commit_number(ctape::span token) {
    if (token.length > ctape::max_length) limit("tape text token limit");
    in_input(token); value();
  }
  void rollback_number() { words_.truncate(words_.size() - 2); }
  void integer(int64_t v, ctape::span token) { number(ctape::kind_i64, uint64_t(v), token); }
  void unsigned_integer(uint64_t v, ctape::span token) { number(ctape::kind_u64, v, token); }
  void floating(double v, ctape::span token) {
    if (!std::isfinite(v)) invalid("non-finite number");
    uint64_t bits;
    std::memcpy(&bits, &v, sizeof bits);
    number(ctape::kind_f64, bits, token);
  }
  // An infinity or NaN (YAML); the view then carries TD_TAPE_NONFINITE.
  void nonfinite(double v, ctape::span token) {
    if (std::isfinite(v)) invalid("finite value written as non-finite");
    uint64_t bits;
    std::memcpy(&bits, &v, sizeof bits);
    number(ctape::kind_f64, bits, token);
    nonfinite_ = true;
  }
  bool has_nonfinite() const { return nonfinite_; }
  // A DECIMAL whose text is the input token.
  void decimal_ref(ctape::span token) {
    charge(token.length);
    in_input(token);
    value();
    *push(1) = ctape::make(ctape::kind_decimal, 0, token.length, token.offset);
  }
  // A DECIMAL with owned text (a normalized spelling); `raw` is its source token.
  void decimal(std::string_view text, ctape::span raw) { owned_text(ctape::kind_decimal, 0, text, raw); }
  // A STRING whose decoded text is the input span (a body without escapes).
  [[gnu::always_inline]] void string_ref(ctape::span decoded, bool ascii, bool key) {
    charge(decoded.length);
    in_input(decoded);
    value();
    *push(1) = ctape::make(ctape::kind_string, flags(ascii, key), decoded.length, decoded.offset);
  }
  // A STRING with owned decoded text (copied into the pool); `raw` is its source token.
  void string(std::string_view decoded, ctape::span raw, bool ascii, bool key) {
    owned_text(ctape::kind_string, flags(ascii, key), decoded, raw);
  }
  // A REF to the earlier, closed node at `target` (a YAML alias). Returns its index.
  size_t ref(size_t target) {
    if (target >= words_.size()) invalid("REF target must precede the reference");
    uint64_t w = words_[target];
    if (ctape::is_container(ctape::kind(w)) && ctape::low(w) == 0) invalid("REF target is open");
    value();
    size_t index = words_.size();
    *push(1) = ctape::make(ctape::kind_ref, 0, 0, uint32_t(target));
    return index;
  }

  // ---- rewrites (YAML) ------------------------------------------------------------------------
  // Drops words [words, word_count()); `words` is a node boundary. Their owned text stays in the pool.
  void truncate(size_t words) {
    if (pending_.size()) misuse("tape rewrite with announced keys pending");
    if (words >= words_.size()) return;
    values_ -= nodes_in(words_.data(), words, words_.size());
    words_.truncate(words);
  }
  // Removes the closed nodes [first, first + count): later words move down, their `next` and REF
  // targets beyond the range are renumbered, and a REF into the range is refused before anything
  // changes. Containers still open must start before `first`. Directory runs are not moved: those
  // of removed objects become unreferenced; moved objects keep theirs (entries are object-relative).
  void erase(size_t first, size_t count) {
    if (pending_.size()) misuse("tape rewrite with announced keys pending");
    size_t size = words_.size();
    if (count == 0) return;
    if (first > size || count > size - first) range("tape erase range");
    uint64_t* w = words_.data();
    size_t end = first + count;
    for (size_t i = end; i < size; i = ctape::step(i, w[i])) {
      uint32_t k = ctape::kind(w[i]);
      if (k == ctape::kind_ref && ctape::low(w[i]) >= first && ctape::low(w[i]) < end) invalid("erase removes a REF target");
      if (ctape::is_container(k) && ctape::low(w[i]) == 0) misuse("erase before an open container");
    }
    values_ -= nodes_in(w, first, end);
    std::memmove(w + first, w + end, (size - end) * sizeof(uint64_t));
    words_.truncate(size - count);
    for (size_t i = first; i < size - count; i = ctape::step(i, w[i])) {
      uint32_t k = ctape::kind(w[i]);
      if (ctape::is_container(k) || (k == ctape::kind_ref && ctape::low(w[i]) >= end))
        w[i] -= count;  // `next` / target sit in the low 32 bits and stay positive
    }
  }
  // Appends `count` words of whole, closed nodes copied from `source` (not this tape), which stood
  // at index word_count() - shift: every container `next` and every REF target inside the copied
  // range moves by `shift`; REF targets before it are unchanged (the caller relocates REFs to other
  // moved blocks through words()).
  void append(const uint64_t* source, size_t count, int64_t shift) {
    if (pending_.size()) misuse("tape rewrite with announced keys pending");
    uint64_t nodes = 0;
    for (size_t i = 0; i < count; i = ctape::step(i, source[i])) {
      uint64_t w = source[i];
      if (ctape::kind(w) > ctape::kind_ref) invalid("reserved compact tape kind");
      if (ctape::is_wide(w) && i + 1 >= count) invalid("appended words end inside a node");
      if (ctape::is_container(ctape::kind(w)) && ctape::low(w) == 0) invalid("appended container is open");
      ++nodes;
    }
    if (nodes > limits_.nodes - values_) limit("tape value limit");
    size_t at = words_.size();
    uint64_t origin = uint64_t(int64_t(at) - shift);
    uint64_t* out = push(count);
    std::memcpy(out, source, count * sizeof(uint64_t));
    values_ += nodes;
    for (size_t i = 0; i < count; i = ctape::step(i, out[i])) {
      uint32_t k = ctape::kind(out[i]);
      if (ctape::is_container(k) || (k == ctape::kind_ref && ctape::low(out[i]) >= origin && ctape::low(out[i]) - origin < count)) {
        uint64_t moved = uint64_t(int64_t(ctape::low(out[i])) + shift);
        if (moved > ctape::max_words) range("appended index out of range");
        out[i] = (out[i] & ~uint64_t(UINT32_MAX)) | moved;
      }
    }
  }

  // ---- access ---------------------------------------------------------------------------------
  // Direct word access for rewriting producers; invalidated by any later write.
  uint64_t* words() { return words_.data(); }
  const uint64_t* words() const { return words_.data(); }
  size_t word_count() const { return words_.size(); }
  // Values written (Myna nodes), the quantity limits.nodes bounds.
  uint64_t value_count() const { return values_; }
  // Text of the STRING or DECIMAL word at `index`.
  std::string_view text_of(size_t index) const {
    uint64_t w = words_[index];
    return ctape::text_at((w & ctape::owned_flag) ? pool_.data() : input_, ctape::low(w), ctape::field(w));
  }
  // The descriptor (no meta: a producer with a meta table sets meta, meta_count and HAS_META).
  td_tape_view view() const {
    if (words_.size() == 0) misuse("empty compact tape");
    return {TD_TAPE_VIEW_VERSION, nonfinite_ ? TD_TAPE_NONFINITE : 0u, words_.data(), words_.size(),
            pool_.data(), pool_.size(), input_, input_bytes_, directory_.data(), directory_.size(), nullptr, 0};
  }
};
}  // namespace truffledata::tape
#endif
