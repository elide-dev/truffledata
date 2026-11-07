// TEMPORARY performance probe (not committed as a target; retained under benchmarks/results/json-sota-probe-*).
// What a state-of-the-art producer costs for every JSON use case with the strictness kept (duplicate keys
// rejected after unescaping, exact number tokens, strict UTF-8, no half-published results), measured in one
// process; every row of a document back to back.
//
// Reference rows (as in benchmarks/results/tape-gbps-probe-7f55043):
//   S0   simdjson stage 1 (structural index + UTF-8 validation) through dom_parser_implementation
//   S1   S0 + classify every structural index (the floor of any stage 2)
//   D0   simdjson DOM parse (its own stage 2: tape, every string copied, numbers typed, no duplicates, no spans)
//   W2c  the On-Demand lean walk into a checksum consumer (the On-Demand floor)
//   V4   the 0.8.0 Myna-tape decoder (json::legacy)
//   V5   the shipped compact-tape decoder (tape_decoder<ctape_writer>): directory-sorted duplicates
//   V5v  the shipped validate-only walk (tape_decoder<null_writer>)
// New rows:
//   V5s  the same On-Demand walk into a shape-cached writer: a per-depth key-sequence template (hidden-class
//        style) answers the duplicate check by masked 8-byte compares, caches the FNV-1a hashes and writes the
//        directory by the template's permutation; a miss sorts as V5 does and installs the object as the template
//   V5sn V5s without directories (what a sink consumer needs: JSON.parse, JavaSink, TruffleSink, streams)
//   V5sv V5s validate-only (no tape)
//   S3   our own stage 2 over simdjson's structural index writing the compact tape (ctape layout, bit-identical to
//        V5): SIMD quote/backslash scans, simdjson's number parser, SIMD-copy unescaping into the pool, exact token
//        spans, depth, trailing input, the shape-cached duplicate check and directories, arrays presized from the
//        structural count (no per-push checks, capacity retained across documents as a session would)
//   S3n  S3 without directories; S3v S3 validate-only (no tape); S3h S3 with per-object sorted directories and
//        no shape cache (isolates the shape cache); S3k S3 with checked pushes from the production hint sizes
//        over a reset block arena (isolates presizing and retained capacity)
//   N0   NDJSON (20,000 documents): iterate_many + the shipped V5 decoder per document (the stream path)
//   N1   NDJSON: stage 1 over the whole buffer + S3 per document (one structural index, many documents)
// Correctness: S3's words, pool and directory equal V5's on every fixture document, ctape::validate accepts
// them, and over the 200-document corpus and the 60 malformed documents S3 accepts and rejects exactly what V5
// does with identical tapes.
#include "json/native/decoder.h"
#include "json/native/legacy_decoder.h"
#include "tape/native/testing.h"
#include <simdjson.h>
#include <emmintrin.h>
#ifdef __AVX2__
#include <immintrin.h>
#endif
#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <stdexcept>
#include <string>
#include <vector>

namespace ct = truffledata::tape::ctape;
using truffledata::tape::arena_vec;
using truffledata::tape::block_arena;
using truffledata::tape::index_memory;
using clk = std::chrono::steady_clock;
static volatile uint64_t sink;

template <class F> static double best_ns(F&& f, int reps) {
  double best = 1e300;
  for (int round = 0; round < 9; round++) {
    auto t0 = clk::now();
    for (int i = 0; i < reps; i++) f();
    best = std::min(best, std::chrono::duration<double, std::nano>(clk::now() - t0).count() / reps);
  }
  return best;
}

[[noreturn]] static void fail(const char* m) { throw std::invalid_argument(m); }
static inline uint64_t load8(const uint8_t* p) { uint64_t w; std::memcpy(&w, p, 8); return w; }
static inline uint64_t mask8(size_t n) { return n >= 8 ? ~0ull : (uint64_t(1) << (8 * n)) - 1; }
static inline uint32_t fnv(const uint8_t* p, size_t n) { return myna_value_hash(p, n); }
static inline bool is_ws(uint8_t c) { return c == ' ' || c == '\n' || c == '\r' || c == '\t'; }
static inline bool ends_token(uint8_t c) { return is_ws(c) || c == ',' || c == '}' || c == ']' || c == ':' || c == 0; }

// ---- The shape cache: one key-sequence template per depth. A key of at most 32 bytes is stored inline as
// masked 8-byte words; the object matched when every key equals the template's in order and the counts agree,
// and then it holds no duplicate (the template was checked) and its directory is the template's permutation.
struct shape_key { uint8_t bytes[32]; uint32_t len, hash; };
struct shape_level { uint32_t count = 0; shape_key keys[32]; uint8_t rank[32]; };
static constexpr uint32_t shape_depths = 32, shape_max_keys = 32, shape_max_len = 32;
// One 16-byte compare per 16 bytes of key (`p` readable 32 bytes on: padded input or pool slack).
static inline bool key_eq(const shape_key& k, const uint8_t* p, uint32_t len) {
  if (k.len != len) return false;
  uint32_t m = uint32_t(_mm_movemask_epi8(_mm_cmpeq_epi8(_mm_loadu_si128(reinterpret_cast<const __m128i*>(p)),
                                                         _mm_loadu_si128(reinterpret_cast<const __m128i*>(k.bytes)))));
  uint32_t need = len >= 16 ? 0xFFFFu : (uint32_t(1) << len) - 1;
  if ((m & need) != need) return false;
  if (len <= 16) return true;
  uint32_t m2 = uint32_t(_mm_movemask_epi8(_mm_cmpeq_epi8(_mm_loadu_si128(reinterpret_cast<const __m128i*>(p + 16)),
                                                          _mm_loadu_si128(reinterpret_cast<const __m128i*>(k.bytes + 16)))));
  uint32_t need2 = len >= 32 ? 0xFFFFu : (uint32_t(1) << (len - 16)) - 1;
  return (m2 & need2) == need2;
}
static inline void key_set(shape_key& k, const uint8_t* p, uint32_t len, uint32_t hash) {
  k.len = len;
  k.hash = hash;
  std::memset(k.bytes, 0, sizeof k.bytes);
  std::memcpy(k.bytes, p, len);
}

// ---- The writer shared by the On-Demand visitor (V5s rows) and the stage 2 (S3 rows): the compact tape in the
// ctape layout (Tape), with or without directories (Dirs), or nothing but the checks (validate-only).
enum class mode { tape_dir, tape_nodir, validate, tape_nokeys };
template <mode M, bool Checked> struct sota_writer {
  static constexpr bool tape = M != mode::validate, dirs = M == mode::tape_dir, nokeys = M == mode::tape_nokeys;
  // Storage: presized std::vectors kept across documents (a session retaining capacity), or arena-backed with
  // checked growth as the production writer (Checked).
  std::vector<uint64_t> words_store, dir_store;
  std::vector<uint8_t> pool_store;
  std::pmr::memory_resource* mem = nullptr;
  uint64_t *w = nullptr, *wbase = nullptr, *wcap = nullptr;
  uint8_t *p = nullptr, *pbase = nullptr, *pcap = nullptr;
  uint64_t *d = nullptr, *dbase = nullptr, *dcap = nullptr;
  const uint8_t* input = nullptr;
  uint64_t input_bytes = 0;
  // Per-key stacks of the open objects: hash << 32 | ordinal, the key bytes, the key word offset.
  struct key_view { const uint8_t* ptr; uint32_t len; };
  std::vector<uint64_t> pending;
  std::vector<key_view> keys;
  std::vector<uint32_t> offsets;
  size_t pn = 0;
  struct obj { size_t index; uint32_t depth; uint32_t ti; uint32_t first; bool matched; };
  obj objs[130];
  uint32_t on = 0;
  shape_level levels[shape_depths];
  uint64_t values = 0, text_bytes = 0;
  uint64_t stats_template_hits = 0, stats_sorts = 0;

  [[gnu::noinline, gnu::cold]] void grow_words() {
    size_t size = w - wbase, cap = (wcap - wbase) * 2;
    auto* n = static_cast<uint64_t*>(mem->allocate(cap * 8, 8));
    std::memcpy(n, wbase, size * 8);
    wbase = n; w = n + size; wcap = n + cap;
  }
  [[gnu::noinline, gnu::cold]] void grow_pool(size_t need) {
    size_t size = p - pbase, cap = (pcap - pbase) * 2;
    while (cap < size + need + 32) cap *= 2;
    auto* n = static_cast<uint8_t*>(mem->allocate(cap, 8));
    std::memcpy(n, pbase, size);
    pbase = n; p = n + size; pcap = n + cap;
  }
  [[gnu::noinline, gnu::cold]] void grow_dir(size_t need) {
    size_t size = d - dbase, cap = (dcap - dbase) * 2;
    while (cap < size + need) cap *= 2;
    auto* n = static_cast<uint64_t*>(mem->allocate(cap * 8, 8));
    std::memcpy(n, dbase, size * 8);
    dbase = n; d = n + size; dcap = n + cap;
  }
  [[gnu::always_inline]] uint64_t* push(size_t n) {
    if constexpr (Checked) { if (wcap - w < ptrdiff_t(n)) grow_words(); }
    uint64_t* at = w;
    w += n;
    return at;
  }
  // Pool slack of at least 32 bytes beyond `need` (shape compares load 8-byte words).
  [[gnu::always_inline]] uint8_t* pool_reserve(size_t need) {
    if constexpr (Checked) { if (pcap - p < ptrdiff_t(need + 32)) grow_pool(need); }
    return p;
  }
  void pool_commit(size_t n) { p += n; }
  [[gnu::always_inline]] uint64_t* dir_push(size_t n) {
    if constexpr (Checked) { if (dcap - d < ptrdiff_t(n)) grow_dir(n); }
    uint64_t* at = d;
    d += n;
    return at;
  }
  // Presizes: exact bounds (Checked = false) or the production hint (Checked = true) over `memory`.
  void start(const uint8_t* in, size_t bytes, size_t words_cap, size_t pool_cap, size_t dir_cap,
             std::pmr::memory_resource* memory = nullptr) {
    input = in; input_bytes = bytes; mem = memory; values = 0; text_bytes = 0; pn = 0; on = 0;
    pool_cap += 32;
    if (memory) {
      wbase = static_cast<uint64_t*>(memory->allocate(words_cap * 8, 8)); wcap = wbase + words_cap;
      pbase = static_cast<uint8_t*>(memory->allocate(pool_cap, 8)); pcap = pbase + pool_cap;
      dbase = dirs ? static_cast<uint64_t*>(memory->allocate(dir_cap * 8, 8)) : nullptr; dcap = dbase + (dirs ? dir_cap : 0);
    } else {
      if (words_store.size() < words_cap) words_store.resize(words_cap);
      if (pool_store.size() < pool_cap) pool_store.resize(pool_cap);
      if (dirs && dir_store.size() < dir_cap) dir_store.resize(dir_cap);
      wbase = words_store.data(); wcap = wbase + words_store.size();
      pbase = pool_store.data(); pcap = pbase + pool_store.size();
      dbase = dirs ? dir_store.data() : nullptr; dcap = dbase + (dirs ? dir_store.size() : 0);
    }
    w = wbase; p = pbase; d = dbase;
    size_t kcap = Checked ? 256 : words_cap / 2 + 8;
    if (pending.size() < kcap) { pending.resize(kcap); keys.resize(kcap); if (dirs) offsets.resize(kcap); }
  }
  td_tape_view view() const {
    return {TD_TAPE_VIEW_VERSION, 0, wbase, uint64_t(w - wbase), pbase, uint64_t(p - pbase), input, input_bytes,
            dbase, uint64_t(d - dbase), nullptr, 0};
  }
  size_t word_count() const { return w - wbase; }
  size_t pool_bytes() const { return p - pbase; }
  size_t dir_count() const { return d - dbase; }

  // ---- containers
  size_t begin(uint32_t kind, uint32_t depth, uint32_t raw_offset) {
    ++values;
    size_t index = 0;
    if constexpr (tape) {
      index = w - wbase;
      uint64_t* x = push(2);
      x[0] = ct::make(kind, ct::wide_flag, 0, 0);
      x[1] = raw_offset;
    }
    if (kind == ct::kind_object) objs[on++] = {index, depth, 0, uint32_t(pn), true};
    return index;
  }
  // The key announced for the word written next. On a template hit nothing is pushed but the key offset
  // (directories); the first miss materializes the earlier keys' hashes and bytes from the template.
  [[gnu::noinline, gnu::cold]] void materialize(obj& o, shape_level& L) {
    for (uint32_t i = 0; i < o.ti; ++i) {
      pending[o.first + i] = uint64_t(L.keys[i].hash) << 32 | i;
      keys[o.first + i] = {L.keys[i].bytes, L.keys[i].len};
    }
  }
  [[gnu::always_inline]] void key_text(const uint8_t* ptr, uint32_t len) {
    if constexpr (nokeys) return;
    obj& o = objs[on - 1];
    if constexpr (Checked) {
      if (pn == pending.size()) { pending.resize(pn * 2); keys.resize(pn * 2); if (dirs) offsets.resize(pn * 2); }
    }
    uint32_t i = o.ti;
    if constexpr (dirs) offsets[pn] = uint32_t((w - wbase) - o.index);
    ++pn;
    if (o.matched) {
      if (o.depth < shape_depths) {
        shape_level& L = levels[o.depth];
        if (i < L.count && key_eq(L.keys[i], ptr, len)) { o.ti = i + 1; return; }
        materialize(o, L);
      }
      o.matched = false;
    }
    o.ti = i + 1;
    pending[o.first + i] = uint64_t(fnv(ptr, len)) << 32 | i;
    keys[o.first + i] = {ptr, len};
  }
  void end(size_t index, uint64_t count, uint32_t kind) {
    if constexpr (tape) {
      wbase[index] = ct::make(kind, ct::wide_flag, uint32_t(count < ct::max_length ? count : ct::max_length), uint32_t(w - wbase));
    }
    if (kind != ct::kind_object) return;
    obj& o = objs[--on];
    if constexpr (nokeys) { pn = o.first; return; }
    if (count >= 2) {
      shape_level* L = o.depth < shape_depths ? &levels[o.depth] : nullptr;
      uint64_t* run = pending.data() + o.first;
      if (o.matched && L && o.ti == L->count) {
        ++stats_template_hits;
        if constexpr (dirs) {
          uint64_t* out = dir_push(count);
          const uint32_t* off = offsets.data() + o.first;
          for (size_t i = 0; i < count; ++i) out[L->rank[i]] = ct::entry(L->keys[i].hash, off[i]);
          wbase[index + 1] |= uint64_t((out - dbase) + 1) << 32;
        }
      } else {
        ++stats_sorts;
        // Sort by (hash, ordinal): ordinals rise with key offsets, so this is the directory order.
        if (count <= 24) {
          for (size_t i = 1; i < count; ++i) {
            uint64_t x = run[i];
            size_t j = i;
            while (j > 0 && run[j - 1] > x) { run[j] = run[j - 1]; --j; }
            run[j] = x;
          }
        } else {
          std::sort(run, run + count);
        }
        const key_view* k = keys.data() + o.first;
        for (size_t i = 1; i < count; ++i) {
          if ((run[i] >> 32) != (run[i - 1] >> 32)) continue;
          key_view a = k[uint32_t(run[i])];
          for (size_t j = i; j > 0 && (run[j - 1] >> 32) == (run[i] >> 32); --j) {
            key_view b = k[uint32_t(run[j - 1])];
            if (a.len == b.len && std::memcmp(a.ptr, b.ptr, a.len) == 0) throw ct::duplicate_key();
          }
        }
        if constexpr (dirs) {
          uint64_t* out = dir_push(count);
          const uint32_t* off = offsets.data() + o.first;
          for (size_t i = 0; i < count; ++i) out[i] = ct::entry(uint32_t(run[i] >> 32), off[uint32_t(run[i])]);
          wbase[index + 1] |= uint64_t((out - dbase) + 1) << 32;
        }
        // Install as the template (keys of at most 32 bytes, at most 32 of them).
        if (L && count <= shape_max_keys) {
          bool ok = true;
          for (size_t i = 0; i < count && ok; ++i) ok = k[i].len <= shape_max_len;
          if (ok) {
            L->count = uint32_t(count);
            for (size_t j = 0; j < count; ++j) {
              uint32_t i = uint32_t(run[j]);
              L->rank[i] = uint8_t(j);
              key_set(L->keys[i], k[i].ptr, k[i].len, uint32_t(run[j] >> 32));
            }
          } else {
            L->count = 0;
          }
        }
      }
    }
    pn = o.first;
  }

  // ---- scalars (spans index the input)
  [[gnu::always_inline]] void number(uint32_t kind, uint64_t bits, ct::span tok) {
    ++values;
    if constexpr (tape) {
      uint64_t* x = push(2);
      x[0] = ct::make(kind, ct::wide_flag, tok.length, tok.offset);
      x[1] = bits;
    }
  }
  void integer(int64_t v, ct::span t) { number(ct::kind_i64, uint64_t(v), t); }
  void unsigned_integer(uint64_t v, ct::span t) { number(ct::kind_u64, v, t); }
  void floating(double v, ct::span t) { uint64_t b; std::memcpy(&b, &v, 8); number(ct::kind_f64, b, t); }
  void decimal_ref(ct::span t) {
    ++values; text_bytes += t.length;
    if constexpr (tape) *push(1) = ct::make(ct::kind_decimal, 0, t.length, t.offset);
  }
  void null() { ++values; if constexpr (tape) *push(1) = ct::make(ct::kind_null, 0, 0, 0); }
  void boolean(bool b) { ++values; if constexpr (tape) *push(1) = ct::make(b ? ct::kind_true : ct::kind_false, 0, 0, 0); }
  static uint64_t flags(bool ascii, bool key) { return (ascii ? ct::ascii_flag : 0) | (key ? ct::key_flag : 0); }
  // A clean body referenced in the input.
  [[gnu::always_inline]] void string_ref(ct::span decoded, bool ascii, bool key) {
    if (key) key_text(input + decoded.offset, decoded.length);
    ++values; text_bytes += decoded.length;
    if constexpr (tape) *push(1) = ct::make(ct::kind_string, flags(ascii, key), decoded.length, decoded.offset);
  }
  // Decoded text already in the pool at `pool_off` (the producer unescaped into pool_reserve()).
  [[gnu::always_inline]] void string_owned(uint32_t pool_off, uint32_t len, ct::span raw, bool ascii, bool key) {
    if (key) key_text(pbase + pool_off, len);
    ++values; text_bytes += len;
    if constexpr (tape) {
      uint64_t* x = push(2);
      x[0] = ct::make(ct::kind_string, flags(ascii, key) | ct::owned_flag | ct::wide_flag, len, pool_off);
      x[1] = ct::raw_word(raw);
    }
  }
  // Decoded text copied into the pool (the On-Demand producer).
  void string_copy(std::string_view decoded, ct::span raw, bool ascii, bool key) {
    uint8_t* at = pool_reserve(decoded.size());
    std::memcpy(at, decoded.data(), decoded.size());
    uint32_t off = uint32_t(at - pbase);
    pool_commit(decoded.size());
    string_owned(off, uint32_t(decoded.size()), raw, ascii, key);
  }
};

// ---- The On-Demand visitor (decoder.h's visit) over sota_writer: the V5s rows.
template <class W> struct od_visitor {
  W& w;
  const char* base;
  ct::span of(std::string_view t) const { return {uint32_t(t.data() - base), uint32_t(t.size())}; }
  template <class Value> void visit(Value& value, uint32_t depth) {
    using simdjson::ondemand::json_type;
    if (depth > 128) fail("depth");
    switch (value.type().value()) {
      case json_type::unknown: fail("unknown token");
      case json_type::object: {
        std::string_view open = value.raw_json_token();
        size_t index = w.begin(ct::kind_object, depth, uint32_t(open.data() - base));
        uint64_t members = 0;
        for (auto entry : value.get_object()) {
          std::string_view raw = truffledata::json::trim(entry.key_raw_json_token().value());
          std::string_view body = raw.substr(1, raw.size() - 2);
          truffledata::json::body_scan s = truffledata::json::scan(body);
          if (s.clean) w.string_ref(of(body), s.ascii, true);
          else { std::string_view key = entry.unescaped_key().value(); w.string_copy(key, of(raw), truffledata::json::ascii(key), true); }
          auto child = entry.value().value();
          visit(child, depth + 1);
          ++members;
        }
        w.end(index, members, ct::kind_object);
        break;
      }
      case json_type::array: {
        std::string_view open = value.raw_json_token();
        size_t index = w.begin(ct::kind_array, depth, uint32_t(open.data() - base));
        uint64_t elements = 0;
        for (auto entry : value.get_array()) { auto child = entry.value(); visit(child, depth + 1); ++elements; }
        w.end(index, elements, ct::kind_array);
        break;
      }
      case json_type::string: {
        std::string_view raw = truffledata::json::trim(value.raw_json_token());
        std::string_view body = raw.substr(1, raw.size() - 2);
        truffledata::json::body_scan s = truffledata::json::scan(body);
        if (s.clean) w.string_ref(of(body), s.ascii, false);
        else { std::string_view decoded = value.get_string().value(); w.string_copy(decoded, of(raw), truffledata::json::ascii(decoded), false); }
        break;
      }
      case json_type::number: {
        std::string_view raw = truffledata::json::trim(value.raw_json_token());
        auto parsed = value.get_number();
        if (!parsed.error()) {
          using simdjson::ondemand::number_type;
          auto n = parsed.value();
          switch (n.get_number_type()) {
            case number_type::signed_integer:
              if (n.get_int64() == 0 && raw[0] == '-') w.floating(-0.0, of(raw)); else w.integer(n.get_int64(), of(raw));
              return;
            case number_type::unsigned_integer: w.unsigned_integer(n.get_uint64(), of(raw)); return;
            case number_type::floating_point_number:
              if (std::isfinite(n.get_double())) { w.floating(n.get_double(), of(raw)); return; }
              break;
            default: break;
          }
        }
        if (!truffledata::json::number(raw)) fail("invalid number");
        w.decimal_ref(of(raw));
        break;
      }
      case json_type::boolean: w.boolean(value.get_bool().value()); break;
      case json_type::null:
        if (!value.is_null().value()) fail("invalid null");
        w.null();
        break;
    }
  }
  void run(simdjson::ondemand::document& root) {
    visit(root, 0);
    if (root.is_scalar().value()) (void)root.raw_json().value();
    if (!root.at_end()) fail("trailing input");
  }
};

// ---- W2c: the On-Demand walk into a checksum (the On-Demand floor; from the earlier probe).
struct checksum_walk {
  uint64_t sum = 0;
  template <class Value> void visit(Value& value) {
    using simdjson::ondemand::json_type;
    switch (value.type().value()) {
      case json_type::object:
        sum += 1;
        for (auto entry : value.get_object()) {
          std::string_view raw = truffledata::json::trim(entry.key_raw_json_token().value());
          std::string_view body = raw.substr(1, raw.size() - 2);
          auto s = truffledata::json::scan(body);
          std::string_view key = s.clean ? body : entry.unescaped_key().value();
          sum += key.size() * 31 + (key.empty() ? 0 : uint8_t(key[0])) + s.ascii;
          auto child = entry.value().value();
          visit(child);
        }
        break;
      case json_type::array:
        sum += 2;
        for (auto entry : value.get_array()) { auto child = entry.value(); visit(child); }
        break;
      case json_type::string: {
        std::string_view raw = truffledata::json::trim(value.raw_json_token());
        std::string_view body = raw.substr(1, raw.size() - 2);
        auto s = truffledata::json::scan(body);
        std::string_view v = s.clean ? body : value.get_string().value();
        sum += v.size() * 31 + (v.empty() ? 0 : uint8_t(v[0])) + s.ascii;
        break;
      }
      case json_type::number: {
        std::string_view raw = truffledata::json::trim(value.raw_json_token());
        auto parsed = value.get_number();
        if (!parsed.error()) {
          using simdjson::ondemand::number_type;
          auto n = parsed.value();
          switch (n.get_number_type()) {
            case number_type::signed_integer: sum += uint64_t(n.get_int64()); return;
            case number_type::unsigned_integer: sum += n.get_uint64(); return;
            case number_type::floating_point_number: { double x = n.get_double(); uint64_t b; std::memcpy(&b, &x, 8); sum += b; return; }
            default: break;
          }
        }
        if (!truffledata::json::number(raw)) fail("invalid number");
        sum += raw.size();
        break;
      }
      case json_type::boolean: sum += value.get_bool().value(); break;
      default: sum += value.is_null().value() ? 7 : 0; break;
    }
  }
};

// ---- S3: our own stage 2 over simdjson's structural index.
struct scan_hit { uint32_t len; bool quote; uint32_t hi; };
// The first '"' or '\\' at or after p (hi: the high bits of the bytes before it).
static inline scan_hit find_quote_or_backslash(const uint8_t* p, const uint8_t* limit) {
  uint32_t hi = 0;
  size_t i = 0;
#ifdef __AVX2__
  const __m256i q = _mm256_set1_epi8('"'), b = _mm256_set1_epi8('\\');
  for (;; i += 32) {
    __m256i v = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(p + i));
    uint32_t m = uint32_t(_mm256_movemask_epi8(_mm256_or_si256(_mm256_cmpeq_epi8(v, q), _mm256_cmpeq_epi8(v, b))));
    uint32_t h = uint32_t(_mm256_movemask_epi8(v));
    if (m) {
      uint32_t pos = uint32_t(__builtin_ctz(m));
      hi |= h & ((uint32_t(1) << pos) - 1);
      return {uint32_t(i + pos), p[i + pos] == '"', hi};
    }
    hi |= h;
    if (p + i + 32 > limit) fail("unterminated string");
  }
#else
  const __m128i q = _mm_set1_epi8('"'), b = _mm_set1_epi8('\\');
  for (;; i += 16) {
    __m128i v = _mm_loadu_si128(reinterpret_cast<const __m128i*>(p + i));
    uint32_t m = uint32_t(_mm_movemask_epi8(_mm_or_si128(_mm_cmpeq_epi8(v, q), _mm_cmpeq_epi8(v, b))));
    uint32_t h = uint32_t(_mm_movemask_epi8(v));
    if (m) {
      uint32_t pos = uint32_t(__builtin_ctz(m));
      hi |= h & ((uint32_t(1) << pos) - 1);
      return {uint32_t(i + pos), p[i + pos] == '"', hi};
    }
    hi |= h;
    if (p + i + 16 > limit) fail("unterminated string");
  }
#endif
}
static inline int hexval(uint8_t c) {
  if (c >= '0' && c <= '9') return c - '0';
  if (c >= 'a' && c <= 'f') return c - 'a' + 10;
  if (c >= 'A' && c <= 'F') return c - 'A' + 10;
  return -1;
}
static inline bool ascii_bytes(const uint8_t* p, size_t n) {
  uint64_t any = 0;
  size_t i = 0;
  for (; i + 8 <= n; i += 8) any |= load8(p + i);
  for (; i < n; ++i) any |= p[i];
  return (any & 0x8080808080808080ull) == 0;
}

template <class W> struct stage2 {
  const uint8_t* buf;
  const uint8_t* limit;  // buf + length + padding
  const uint32_t* idx;
  uint32_t n;
  uint32_t at = 0;
  W& w;
  uint32_t max_depth = 128;
  struct frame { size_t index; uint64_t count; bool object; };
  frame frames[130];
  uint32_t depth = 0;

  struct num_writer {
    W& w; ct::span tok; bool negative;
    void append_s64(int64_t v) { if (v == 0 && negative) w.floating(-0.0, tok); else w.integer(v, tok); }
    void append_u64(uint64_t v) { w.unsigned_integer(v, tok); }
    void append_double(double v) { if (!std::isfinite(v)) fail("non-finite"); w.floating(v, tok); }
  };
  // Unescapes the body at s (after the opening quote) into the pool: SIMD copy of clean runs, scalar escapes.
  // Returns the decoded length; `raw_end` receives the closing quote.
  uint32_t unescape(const uint8_t* s, const uint8_t*& raw_end) {
    uint8_t* out = w.pool_reserve(size_t(limit - s));
    uint8_t* o = out;
    for (;;) {
      scan_hit h = find_quote_or_backslash(s, limit);
      std::memcpy(o, s, h.len);  // clean run
      o += h.len; s += h.len;
      if (h.quote) { raw_end = s; break; }
      uint8_t e = s[1];
      s += 2;
      switch (e) {
        case '"': *o++ = '"'; break; case '\\': *o++ = '\\'; break; case '/': *o++ = '/'; break;
        case 'b': *o++ = '\b'; break; case 'f': *o++ = '\f'; break; case 'n': *o++ = '\n'; break;
        case 'r': *o++ = '\r'; break; case 't': *o++ = '\t'; break;
        case 'u': {
          auto read4 = [&](const uint8_t* q) -> int {
            int v = 0;
            for (int k = 0; k < 4; ++k) { int x = hexval(q[k]); if (x < 0) return -1; v = v * 16 + x; }
            return v;
          };
          int cp = read4(s);
          if (cp < 0) fail("bad \\u escape");
          s += 4;
          if (cp >= 0xD800 && cp <= 0xDBFF) {
            if (s[0] != '\\' || s[1] != 'u') fail("unpaired surrogate");
            int lo = read4(s + 2);
            if (lo < 0xDC00 || lo > 0xDFFF) fail("unpaired surrogate");
            s += 6;
            cp = 0x10000 + ((cp - 0xD800) << 10) + (lo - 0xDC00);
          } else if (cp >= 0xDC00 && cp <= 0xDFFF) fail("unpaired surrogate");
          if (cp < 0x80) *o++ = uint8_t(cp);
          else if (cp < 0x800) { *o++ = uint8_t(0xC0 | (cp >> 6)); *o++ = uint8_t(0x80 | (cp & 0x3F)); }
          else if (cp < 0x10000) { *o++ = uint8_t(0xE0 | (cp >> 12)); *o++ = uint8_t(0x80 | ((cp >> 6) & 0x3F)); *o++ = uint8_t(0x80 | (cp & 0x3F)); }
          else { *o++ = uint8_t(0xF0 | (cp >> 18)); *o++ = uint8_t(0x80 | ((cp >> 12) & 0x3F)); *o++ = uint8_t(0x80 | ((cp >> 6) & 0x3F)); *o++ = uint8_t(0x80 | (cp & 0x3F)); }
          break;
        }
        default: fail("bad escape");
      }
    }
    w.pool_commit(size_t(o - out));
    return uint32_t(o - out);
  }
  // The string whose opening quote is at `start`.
  [[gnu::always_inline]] void string(uint32_t start, bool key) {
    const uint8_t* p = buf + start + 1;
    scan_hit h = find_quote_or_backslash(p, limit);
    if (h.quote) {
      w.string_ref({start + 1, h.len}, h.hi == 0, key);
      return;
    }
    uint32_t pool_off = uint32_t(w.p - w.pbase);
    const uint8_t* raw_end;
    uint32_t len = unescape(p, raw_end);
    bool ascii = ascii_bytes(w.pbase + pool_off, len);
    w.string_owned(pool_off, len, {start, uint32_t(raw_end + 1 - buf - start)}, ascii, key);
  }
  [[gnu::always_inline]] void number(uint32_t start) {
    uint32_t end = idx[at];  // the next structural index (the sentinel past the last one)
    while (end > start && is_ws(buf[end - 1])) --end;
    num_writer nw{w, {start, end - start}, buf[start] == '-'};
    if (simdjson::builtin::numberparsing::parse_number(buf + start, nw) == simdjson::SUCCESS) return;
    std::string_view raw(reinterpret_cast<const char*>(buf + start), end - start);
    if (!truffledata::json::number(raw)) fail("invalid number");
    w.decimal_ref({start, end - start});
  }
  [[gnu::always_inline]] void literal(uint32_t start, const char* word, uint32_t len) {
    if (std::memcmp(buf + start, word, len) != 0 || !ends_token(buf[start + len])) fail("bad literal");
  }
  // One document starting at idx[at]; returns after its root closes (what follows is the caller's).
  void document() {
    if (at >= n) fail("empty");
    goto value;
  value: {
      uint32_t start = idx[at++];
      uint8_t c = buf[start];
      switch (c) {
        case '{': {
          if (depth >= max_depth) fail("depth");
          size_t ix = w.begin(ct::kind_object, depth, start);
          frames[depth++] = {ix, 0, true};
          if (at < n && buf[idx[at]] == '}') { ++at; w.end(ix, 0, ct::kind_object); --depth; goto after; }
          goto key;
        }
        case '[': {
          if (depth >= max_depth) fail("depth");
          size_t ix = w.begin(ct::kind_array, depth, start);
          frames[depth++] = {ix, 0, false};
          if (at < n && buf[idx[at]] == ']') { ++at; w.end(ix, 0, ct::kind_array); --depth; goto after; }
          goto value;
        }
        case '"': string(start, false); goto after;
        case 't': literal(start, "true", 4); w.boolean(true); goto after;
        case 'f': literal(start, "false", 5); w.boolean(false); goto after;
        case 'n': literal(start, "null", 4); w.null(); goto after;
        default:
          if (c == '-' || (c >= '0' && c <= '9')) { number(start); goto after; }
          fail("unexpected token");
      }
    }
  after: {
      for (;;) {
        if (depth == 0) return;
        frame& f = frames[depth - 1];
        ++f.count;
        if (at >= n) fail("unterminated container");
        uint8_t d = buf[idx[at++]];
        if (d == ',') { if (f.object) goto key; goto value; }
        if (d == (f.object ? '}' : ']')) { w.end(f.index, f.count, f.object ? ct::kind_object : ct::kind_array); --depth; continue; }
        fail("expected , or a closing bracket");
      }
    }
  key: {
      if (at >= n || buf[idx[at]] != '"') fail("expected a key");
      string(idx[at++], true);
      if (at >= n || buf[idx[at++]] != ':') fail("expected a colon");
      goto value;
    }
  }
  void run() {
    document();
    if (at != n) fail("trailing input");
  }
};

// ---- Harness
struct stage1_box {
  simdjson::dom::parser parser;
  simdjson::internal::dom_parser_implementation* impl = nullptr;
  void allocate(size_t bytes) {
    if (parser.allocate(bytes) != simdjson::SUCCESS) throw std::bad_alloc();
    impl = parser.implementation.get();
  }
  void run(const simdjson::padded_string& s) {
    auto err = impl->stage1(reinterpret_cast<const uint8_t*>(s.data()), s.size(), simdjson::stage1_mode::regular);
    if (err != simdjson::SUCCESS) throw std::invalid_argument(simdjson::error_message(err));
  }
  const uint32_t* idx() const { return impl->structural_indexes.get(); }
  uint32_t n() const { return impl->n_structural_indexes; }
};

template <class W> static void s3_run(stage1_box& s1, const simdjson::padded_string& padded, W& w, std::pmr::memory_resource* mem, bool hint) {
  s1.run(padded);
  size_t n = s1.n();
  if (hint) {
    truffledata::json::decode_hint h = truffledata::json::hint_for(padded.size(), {}, {});
    w.start(reinterpret_cast<const uint8_t*>(padded.data()), padded.size(), h.words, h.pool, h.entries, mem);
  } else {
    w.start(reinterpret_cast<const uint8_t*>(padded.data()), padded.size(), 2 * n + 2, padded.size(), n / 2 + 1, nullptr);
  }
  stage2<W> st{reinterpret_cast<const uint8_t*>(padded.data()), reinterpret_cast<const uint8_t*>(padded.data()) + padded.size() + simdjson::SIMDJSON_PADDING,
               s1.idx(), uint32_t(n), 0, w};
  st.run();
}

struct tape_image { std::vector<uint64_t> words, dir; std::vector<uint8_t> pool; };
static tape_image image(const td_tape_view& v) {
  return {{v.words, v.words + v.word_count}, {v.directory, v.directory + v.directory_count}, {v.pool, v.pool + v.pool_bytes}};
}
static bool same(const tape_image& a, const tape_image& b) { return a.words == b.words && a.dir == b.dir && a.pool == b.pool; }

// V5 through the shipped decoder; returns the image or throws.
static tape_image v5_image(block_arena& arena, simdjson::ondemand::parser& parser, const simdjson::padded_string& padded) {
  arena.reset();
  truffledata::json::decode_hint hint = truffledata::json::hint_for(padded.size(), {}, {});
  arena.hint(hint.block);
  truffledata::json::tape_decoder<truffledata::tape::ctape_writer> decoder(&arena, {});
  decoder.start(std::string_view(padded.data(), padded.size()), hint);
  auto root = parser.iterate(padded).value();
  decoder.decode(root);
  return image(decoder.view());
}
static tape_image s3_image(stage1_box& s1, const simdjson::padded_string& padded, sota_writer<mode::tape_dir, false>& w) {
  s1.allocate(padded.size());
  s3_run(s1, padded, w, nullptr, false);
  td_tape_view v = w.view();
  const char* err = ct::validate(v);
  if (err) throw std::logic_error(std::string("S3 tape invalid: ") + err);
  return image(v);
}

static std::vector<std::string> malformed() {
  using truffledata::tape::testing::fixture;
  using truffledata::tape::testing::object_with_keys;
  std::vector<std::string> out = {
      "", "01", "1.", "1e", "-", "NaN", "[1,]", "{\"a\":1,}", "[tru]", "[xyz]", "{\"x\":undefined}",
      "{\"a\":1,\"unused\":01}", "{\"a\":1,\"\\u0061\":2}", "{} false", "1 2", "\"x\" []", "\"\\uD800\"",
      "[\"\\q\"]", "[01]", "[1.]", "[.5]", "[1e]", "[-]", "[+1]", "[1e+]", "[-01]", "[0x1]", "[1.e5]",
      "[Infinity]", "[1 2]",
      "[\"a\x01" "b\"]", "{\"a\x01" "b\":1}", "[\"\xff\"]", "{\"\xc3\":1}", "{\"\":1,\"\":2}",
      object_with_keys(17, 16, "kA"), object_with_keys(40, 39, "k\\u0041"), object_with_keys(1000, 999, "kA"),
      "{\"a\":{\"b\":1,\"b\":2}}", "[{\"x\":1,\"y\":2,\"x\":3}]"};
  for (int i = 0; i < 10; ++i) { std::string f = fixture(i); out.push_back(f.substr(0, f.size() - 1 - size_t(i))); }
  for (int i = 0; i < 5; ++i) out.push_back(fixture(i) + " x");
  for (int i = 0; i < 5; ++i) { std::string f = fixture(i); out.push_back(f.substr(0, f.size() - 1) + ",\"id\":0}"); }
  return out;
}

static void differential() {
  index_memory memory(SIZE_MAX, std::pmr::new_delete_resource());
  block_arena arena(&memory);
  simdjson::ondemand::parser parser;
  stage1_box s1;
  sota_writer<mode::tape_dir, false> w;
  size_t agree = 0, disagree = 0, s3_rejects = 0, v5_rejects = 0;
  auto check = [&](const std::string& doc, bool report_tapes) {
    simdjson::padded_string padded(doc);
    bool v5_ok = true, s3_ok = true;
    tape_image a, b;
    std::string v5_err, s3_err;
    try { a = v5_image(arena, parser, padded); } catch (const std::exception& e) { v5_ok = false; v5_err = e.what(); }
    try { b = s3_image(s1, padded, w); } catch (const std::exception& e) { s3_ok = false; s3_err = e.what(); }
    v5_rejects += !v5_ok; s3_rejects += !s3_ok;
    bool ok = v5_ok == s3_ok && (!v5_ok || same(a, b));
    if (ok) ++agree; else {
      ++disagree;
      std::printf("  DIFF %s: V5 %s (%s) S3 %s (%s)%s\n", doc.substr(0, 50).c_str(), v5_ok ? "ok" : "rejects", v5_err.c_str(),
                  s3_ok ? "ok" : "rejects", s3_err.c_str(), (v5_ok && s3_ok && report_tapes) ? " tapes differ" : "");
    }
  };
  for (const auto& doc : truffledata::tape::testing::corpus()) check(doc, true);
  std::printf("differential corpus (200): agree=%zu disagree=%zu (V5 rejects %zu, S3 rejects %zu)\n", agree, disagree, v5_rejects, s3_rejects);
  agree = disagree = s3_rejects = v5_rejects = 0;
  for (const auto& doc : malformed()) check(doc, true);
  std::printf("differential malformed (60): agree=%zu disagree=%zu (V5 rejects %zu, S3 rejects %zu)\n", agree, disagree, v5_rejects, s3_rejects);
  arena.release_all();
}

template <class L> struct tag_t { using type = L; };
static void report(const char* name, const std::string& json, int reps) {
  auto line = [&](const char* what, double ns) {
    std::printf("%-6s %8zu B | %-66s %11.0f ns %9.0f MB/s %6.2f GB/s\n", name, json.size(), what, ns,
                json.size() / ns * 1000.0, json.size() / ns);
  };
  simdjson::padded_string padded(json);
  index_memory memory(SIZE_MAX, std::pmr::new_delete_resource());
  block_arena arena(&memory);
  simdjson::ondemand::parser od;
  stage1_box s1;
  s1.allocate(padded.size());
  line("S0 stage 1 (structural index + UTF-8)", best_ns([&] { s1.run(padded); sink += s1.n(); }, reps));
  line("S1 S0 + classify every structural index (stage-2 floor)", best_ns([&] {
    s1.run(padded);
    uint64_t sum = 0; const uint32_t* idx = s1.idx(); uint32_t n = s1.n();
    for (uint32_t i = 0; i < n; ++i) sum += uint8_t(padded.data()[idx[i]]);
    sink += sum; }, reps));
  simdjson::dom::parser dom;
  line("D0 simdjson DOM parse (its stage 2: no duplicates, no spans)", best_ns([&] { sink += dom.parse(padded).value().is_object(); }, reps));
  line("W2c On-Demand lean walk, checksum consumer (On-Demand floor)", best_ns([&] {
    checksum_walk c; auto root = od.iterate(padded).value(); c.visit(root); sink += c.sum; }, reps));
  simdjson::ondemand::parser parser;
  line("V4 0.8.0 Myna-tape decoder", best_ns([&] {
    arena.reset();
    truffledata::json::legacy::decode_hint hint = truffledata::json::legacy::hint_for(padded.size(), {}, {});
    arena.hint(hint.block);
    truffledata::json::legacy::tape_decoder<truffledata::tape::tape_writer> decoder(&arena, {}, truffledata::json::legacy::directory_threshold);
    decoder.start(std::string_view(padded.data(), padded.size()), hint);
    auto root = parser.iterate(padded).value();
    decoder.decode(root);
    sink += decoder.view().base.node_count; }, reps));
  auto compact = [&](auto tag) {
    using Writer = typename decltype(tag)::type;
    arena.reset();
    truffledata::json::decode_hint hint = truffledata::json::hint_for(padded.size(), {}, {});
    arena.hint(hint.block);
    truffledata::json::tape_decoder<Writer> decoder(&arena, {});
    decoder.start(std::string_view(padded.data(), padded.size()), hint);
    auto root = parser.iterate(padded).value();
    decoder.decode(root);
    sink += decoder.writer().value_count();
  };
  line("V5 shipped compact-tape decoder (sorted directories, fused duplicates)", best_ns([&] { compact(tag_t<truffledata::tape::ctape_writer>{}); }, reps));
  line("V5v shipped validate-only walk (null_writer)", best_ns([&] { compact(tag_t<truffledata::json::null_writer>{}); }, reps));
  // V5s rows: On-Demand walk into the shape-cached writer, arena-backed with checked growth as V5.
  auto ods = [&](auto& w) {
    arena.reset();
    truffledata::json::decode_hint hint = truffledata::json::hint_for(padded.size(), {}, {});
    arena.hint(hint.block);
    w.start(reinterpret_cast<const uint8_t*>(padded.data()), padded.size(), hint.words, hint.pool, hint.entries, &arena);
    od_visitor<std::remove_reference_t<decltype(w)>> v{w, padded.data()};
    auto root = parser.iterate(padded).value();
    v.run(root);
    sink += w.values;
  };
  sota_writer<mode::tape_dir, true> w5s;
  sota_writer<mode::tape_nodir, true> w5sn;
  sota_writer<mode::validate, true> w5sv;
  line("V5s On-Demand walk + shape-cached duplicates and directories", best_ns([&] { ods(w5s); }, reps));
  line("V5sn V5s without directories (sink consumers)", best_ns([&] { ods(w5sn); }, reps));
  line("V5sv V5s validate-only (no tape)", best_ns([&] { ods(w5sv); }, reps));
  // S3 rows.
  sota_writer<mode::tape_dir, false> w3;
  sota_writer<mode::tape_nodir, false> w3n;
  sota_writer<mode::validate, false> w3v;
  sota_writer<mode::tape_dir, true> w3k;
  line("S3 own stage 2 over the structural index -> compact tape + directories", best_ns([&] { s3_run(s1, padded, w3, nullptr, false); sink += w3.values; }, reps));
  line("S3n S3 without directories (sink consumers)", best_ns([&] { s3_run(s1, padded, w3n, nullptr, false); sink += w3n.values; }, reps));
  line("S3v S3 validate-only (no tape)", best_ns([&] { s3_run(s1, padded, w3v, nullptr, false); sink += w3v.values; }, reps));
  sota_writer<mode::tape_nokeys, false> w3x;
  line("S3x S3n with no duplicate check at all (strictness dropped: the price of the check)", best_ns([&] { s3_run(s1, padded, w3x, nullptr, false); sink += w3x.values; }, reps));
  line("S3k S3 with checked pushes from the production hint over a reset arena", best_ns([&] {
    arena.reset();
    truffledata::json::decode_hint hint = truffledata::json::hint_for(padded.size(), {}, {});
    arena.hint(hint.block);
    s3_run(s1, padded, w3k, &arena, true); sink += w3k.values; }, reps));
  {
    // S3h: templates cleared before every document, so the first object at each depth sorts and installs
    // the template and the rest match it: what a session's first document of a new shape pays.
    sota_writer<mode::tape_dir, false> w3h;
    line("S3h S3, templates cleared before every document", best_ns([&] {
      for (auto& L : w3h.levels) L.count = 0;
      s3_run(s1, padded, w3h, nullptr, false); sink += w3h.values; }, reps));
  }
  {
    // Shape statistics and tape equality.
    sota_writer<mode::tape_dir, false> w;
    s3_run(s1, padded, w, nullptr, false);
    uint64_t hits0 = w.stats_template_hits, sorts0 = w.stats_sorts;
    w.stats_template_hits = w.stats_sorts = 0;
    s3_run(s1, padded, w, nullptr, false);
    td_tape_view v = w.view();
    const char* err = ct::validate(v);
    tape_image a = v5_image(arena, parser, padded), b = image(v);
    std::printf("%-6s %8zu B | S3 validate: %s; S3 tape == V5 tape: %s; words=%llu directory=%llu pool=%llu (%.2f x input); "
                "template hits/sorts first document %llu/%llu, steady %llu/%llu\n",
                name, json.size(), err ? err : "ok", same(a, b) ? "yes" : "NO",
                (unsigned long long) v.word_count, (unsigned long long) v.directory_count, (unsigned long long) v.pool_bytes,
                double(8 * v.word_count + 8 * v.directory_count + v.pool_bytes) / json.size(),
                (unsigned long long) hits0, (unsigned long long) sorts0,
                (unsigned long long) w.stats_template_hits, (unsigned long long) w.stats_sorts);
  }
  arena.release_all();
}

static void ndjson(const std::string& nd, int reps) {
  auto line = [&](const char* what, double ns) {
    std::printf("%-6s %8zu B | %-66s %11.0f ns %9.0f MB/s %6.2f GB/s\n", "ndjson", nd.size(), what, ns,
                nd.size() / ns * 1000.0, nd.size() / ns);
  };
  simdjson::padded_string padded(nd);
  index_memory memory(SIZE_MAX, std::pmr::new_delete_resource());
  block_arena arena(&memory);
  simdjson::ondemand::parser parser;
  line("N0 iterate_many (1 MiB batches) + V5 per document (the stream path)", best_ns([&] {
    auto docs = parser.iterate_many(padded, size_t(1) << 20).value();
    uint64_t count = 0;
    for (auto it = docs.begin(); it != docs.end(); ++it) {
      arena.reset();
      truffledata::json::decode_hint hint = truffledata::json::hint_for(300, {}, {});
      arena.hint(hint.block);
      truffledata::json::tape_decoder<truffledata::tape::ctape_writer> decoder(&arena, {});
      decoder.start(std::string_view(padded.data(), padded.size()), hint);
      simdjson::ondemand::document_reference doc = *it;
      decoder.decode(doc);
      count += decoder.writer().value_count();
    }
    sink += count; }, reps));
  stage1_box s1;
  s1.allocate(padded.size());
  sota_writer<mode::tape_dir, false> w;
  sota_writer<mode::tape_nodir, false> wn;
  auto n1 = [&](auto& w) {
    s1.run(padded);
    uint32_t n = s1.n();
    w.start(reinterpret_cast<const uint8_t*>(padded.data()), padded.size(), 4096, 4096, 2048, nullptr);
    stage2<std::remove_reference_t<decltype(w)>> st{reinterpret_cast<const uint8_t*>(padded.data()),
        reinterpret_cast<const uint8_t*>(padded.data()) + padded.size() + simdjson::SIMDJSON_PADDING, s1.idx(), n, 0, w};
    uint64_t count = 0;
    while (st.at < n) {
      w.w = w.wbase; w.p = w.pbase; w.d = w.dbase;  // a fresh document in the retained capacity
      st.document();
      count += w.word_count();
    }
    sink += count;
  };
  line("N1 stage 1 over the buffer + S3 per document (directories)", best_ns([&] { n1(w); }, reps));
  line("N1n stage 1 over the buffer + S3 per document, no directories", best_ns([&] { n1(wn); }, reps));
  arena.release_all();
}

int main() {
  std::printf("simdjson %s active implementation: %s; builtin: %s; probe ISA: %s\n", SIMDJSON_VERSION,
              simdjson::get_active_implementation()->name().c_str(), simdjson::builtin_implementation()->name().c_str(),
#ifdef __AVX2__
              "avx2"
#else
              "sse2"
#endif
  );
  differential();
  using truffledata::tape::testing::fixture;
  std::string small = fixture(1), medium = "[", big = "[", huge = "[", nd;
  for (int i = 0; i < 20; i++) medium += (i ? "," : "") + fixture(i);
  medium += "]";
  for (int i = 0; i < 1000; i++) big += (i ? "," : "") + fixture(i);
  big += "]";
  for (int i = 0; i < 20000; i++) { huge += (i ? "," : "") + fixture(i); nd += fixture(i) + "\n"; }
  huge += "]";
  report("small", small, 200000);
  report("medium", medium, 20000);
  report("big", big, 200);
  report("huge", huge, 10);
  ndjson(nd, 10);
  return 0;
}
