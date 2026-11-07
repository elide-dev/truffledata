// TEMPORARY performance probe (not committed as a target; retained under benchmarks/results):
// where the tape's cost goes between simdjson's ceiling and the shipped v4 decoder, and what
// leaner representations cost. One process; every row of a document is measured back to back.
//
//   I0   simdjson On-Demand iterate only (stage 1: structural index + UTF-8 validation)
//   D0   simdjson DOM parse (stage 1 + stage 2 tape, strings unescaped, numbers typed): the ceiling
//   D0d  D0 + strict duplicate-key check over the DOM (public dom::object iteration, key_table)
//   D0e  D0d + v4-style directory per object (hash, child offset) sorted into an arena
//   W1   On-Demand walk touching every value (unescaped_key, get_string, get_double), no output
//   W2   lean walk: raw tokens, scalar body scan, get_number first, no duplicate check, no output
//   W2s  W2 with an 8-byte SWAR body scan
//   W3   W2s + key_table duplicate check: the validate-only / direct-sink ceiling
//   W3v  W3 driving a virtual sink interface (one indirect call per event)
//   W3g  W3 with numbers validated by the RFC 8259 grammar scan only (lazy typing), no get_number
//   T8   W3 + compact 8-byte tape (simdjson-style: one word per value, numbers and containers two)
//   T16  W3 + fixed 16-byte nodes (kind | length | offset-or-next, value-or-raw-span)
//   T16d T16 + eager v4-style directories (pending stack, sort per closing object)
//   T24  W3 + Myna 24-byte nodes through tape_writer (ABI v3, no spans, no side table)
//   V3   shipped tape_decoder, ABI v3 (24-byte nodes + lexical spans + key meta)
//   V4   shipped tape_decoder, ABI v4 (+ directory per object): what ships
#include "json/native/decoder.h"
#include "tape/native/key_table.h"
#include <simdjson.h>
#include <algorithm>
#include <chrono>
#include <cstdio>
#include <cstring>
#include <string>
#include <type_traits>
#include <vector>

using clk = std::chrono::steady_clock;
static volatile uint64_t sink;

static std::string doc(int i) {
  std::string tags = "[", scores = "[";
  for (int k = 0; k < 1 + i % 4; k++) tags += (k ? ",\"t" : "\"t") + std::to_string(k) + "\"";
  for (int k = 0; k < 3 + (i & 1); k++) scores += (k ? "," : "") + std::to_string(i + k);
  std::string note = (i & 1) ? "\"line \\\"" + std::to_string(i) + "\\\" with \\\\ escapes\\n\\t\\u00e9 and \\ud83d\\ude00 tail\""
                             : "\"plain note for item " + std::to_string(i) + " padded beyond the inline text limit\"";
  return "{\"id\":" + std::to_string(1000 + 7 * i) + ",\"name\":\"item-" + std::to_string(i) + "\",\"price\":" +
         std::to_string(i) + ".25,\"active\":" + (i % 3 == 0 ? "true" : "false") + ",\"tags\":" + tags + "],\"dims\":{\"w\":" +
         std::to_string(3 * i) + ",\"h\":" + std::to_string(i) + ".5,\"unit\":\"cm\"},\"scores\":" + scores + "],\"note\":" + note +
         ",\"parent\":null}";
}

template <class F> static double best_ns(F&& f, int reps) {
  double best = 1e300;
  for (int round = 0; round < 9; round++) {
    auto t0 = clk::now();
    for (int i = 0; i < reps; i++) f();
    best = std::min(best, std::chrono::duration<double, std::nano>(clk::now() - t0).count() / reps);
  }
  return best;
}

// ---- W1: the plain On-Demand walk (as the V2 probe's D1).
template <class V> static uint64_t walk(V&& value) {
  using simdjson::ondemand::json_type;
  uint64_t n = 1;
  switch (value.type().value()) {
    case json_type::object:
      for (auto field : value.get_object()) {
        n += field.unescaped_key().value().size();
        auto child = field.value().value();
        n += walk(child);
      }
      break;
    case json_type::array:
      for (auto element : value.get_array()) {
        auto child = element.value();
        n += walk(child);
      }
      break;
    case json_type::string: n += value.get_string().value().size(); break;
    case json_type::number: n += uint64_t(value.get_double().value()); break;
    case json_type::boolean: n += value.get_bool().value(); break;
    default: n += value.is_null().value(); break;
  }
  return n;
}

// ---- SWAR body scan: clean (no backslash, no byte < 0x20) and ASCII, 8 bytes at a time. The
// input is simdjson-padded, so loads past the body stay inside the buffer; tail bytes are masked.
struct scan_result { bool clean; bool ascii; };
static inline scan_result swar_scan(const char* p, size_t n) {
  const uint64_t ones = 0x0101010101010101ull, highs = 0x8080808080808080ull;
  uint64_t bad = 0, any = 0;
  size_t i = 0;
  for (; i + 8 <= n; i += 8) {
    uint64_t v;
    std::memcpy(&v, p + i, 8);
    uint64_t bs = v ^ (ones * 0x5c);
    bad |= (bs - ones) & ~bs & highs;          // a zero byte in bs: a backslash
    bad |= (v - ones * 0x20) & ~v & highs;      // a byte below 0x20
    any |= v;
  }
  if (i < n) {
    uint64_t v;
    std::memcpy(&v, p + i, 8);
    v &= ~0ull >> (8 * (8 - (n - i)));          // keep the body's tail bytes only
    uint64_t keep = ~0ull >> (8 * (8 - (n - i)));
    uint64_t bs = v ^ (ones * 0x5c);
    bad |= (bs - ones) & ~bs & highs & keep;
    bad |= ((v - ones * 0x20) & ~v & highs) & keep;
    any |= v;
  }
  return {bad == 0, (any & highs) == 0};
}

// ---- Writers for the lean visitor. Each exposes the same calls; `null_writer` writes nothing.
struct null_writer {
  void start(const char*, size_t, size_t) {}
  size_t begin(bool) { return 0; }
  void end(size_t, uint64_t) {}
  void key(uint32_t, std::string_view, std::string_view, bool, bool) {}
  void string(std::string_view, std::string_view, bool, bool) {}
  void i64(int64_t, std::string_view) {}
  void u64(uint64_t, std::string_view) {}
  void f64(double, std::string_view) {}
  void decimal(std::string_view) {}
  void boolean(bool) {}
  void null() {}
  uint64_t count() const { return 0; }
};

// Virtual sink: what a native direct-sink consumer (or a C callback per event) costs.
struct sink_iface {
  virtual ~sink_iface() = default;
  virtual void begin_object() = 0;
  virtual void begin_array() = 0;
  virtual void end() = 0;
  virtual void key(std::string_view) = 0;
  virtual void string(std::string_view) = 0;
  virtual void number_i64(int64_t) = 0;
  virtual void number_f64(double) = 0;
  virtual void decimal(std::string_view) = 0;
  virtual void boolean(bool) = 0;
  virtual void null() = 0;
};
struct counting_sink final : sink_iface {
  uint64_t n = 0;
  void begin_object() override { n += 1; }
  void begin_array() override { n += 2; }
  void end() override { n += 3; }
  void key(std::string_view k) override { n += k.size(); }
  void string(std::string_view s) override { n += s.size(); }
  void number_i64(int64_t v) override { n += uint64_t(v); }
  void number_f64(double v) override { n += uint64_t(v); }
  void decimal(std::string_view d) override { n += d.size(); }
  void boolean(bool b) override { n += b; }
  void null() override { n += 7; }
};
struct virtual_writer {
  sink_iface* s;
  void start(const char*, size_t, size_t) {}
  size_t begin(bool object) { if (object) s->begin_object(); else s->begin_array(); return 0; }
  void end(size_t, uint64_t) { s->end(); }
  void key(uint32_t, std::string_view k, std::string_view, bool, bool) { s->key(k); }
  void string(std::string_view v, std::string_view, bool, bool) { s->string(v); }
  void i64(int64_t v, std::string_view) { s->number_i64(v); }
  void u64(uint64_t v, std::string_view) { s->number_i64(int64_t(v)); }
  void f64(double v, std::string_view) { s->number_f64(v); }
  void decimal(std::string_view d) { s->decimal(d); }
  void boolean(bool b) { s->boolean(b); }
  void null() { s->null(); }
  uint64_t count() const { return static_cast<counting_sink*>(s)->n; }
};

// T8: simdjson-style compact tape. One 8-byte word per value: kind in the top byte, payload in
// the low 56 bits (strings: offset:32 | length:24 into the input or the owned byte pool; containers:
// count:24 | end-index:32, backpatched at close). Numbers take a second word for their 64 bits.
struct tape8 {
  std::pmr::memory_resource* memory;
  truffledata::tape::arena_vec<uint64_t> words;
  truffledata::tape::arena_vec<uint8_t> bytes;
  const char* base = nullptr;
  enum : uint64_t { K_NULL = 0, K_FALSE, K_TRUE, K_I64, K_U64, K_F64, K_STR, K_STR_OWNED, K_KEY, K_KEY_OWNED, K_DEC, K_OBJ, K_ARR };
  static constexpr uint64_t kind(uint64_t k) { return k << 56; }
  void start(const char* b, size_t input, size_t) {
    base = b;
    words.reserve(memory, input / 4 + 8);
    bytes.reserve(memory, input / 8 + 64);
  }
  [[gnu::always_inline]] void push(uint64_t w) { words.push(memory, w); }
  size_t begin(bool object) { size_t at = words.size(); push(kind(object ? K_OBJ : K_ARR)); return at; }
  void end(size_t at, uint64_t count) { words[at] |= (count & 0xffffff) << 32 | (words.size() & 0xffffffff); }
  void text(uint64_t k, std::string_view decoded, bool referenced) {
    if (referenced) push(kind(k) | (uint64_t(decoded.data() - base) << 24) | (decoded.size() & 0xffffff));
    else {
      size_t off = bytes.size();
      std::memcpy(bytes.extend(memory, decoded.size()), decoded.data(), decoded.size());
      push(kind(k + 1) | (uint64_t(off) << 24) | (decoded.size() & 0xffffff));
    }
  }
  void key(uint32_t, std::string_view decoded, std::string_view, bool referenced, bool) { text(K_KEY, decoded, referenced); }
  void string(std::string_view decoded, std::string_view, bool referenced, bool) { text(K_STR, decoded, referenced); }
  void i64(int64_t v, std::string_view) { push(kind(K_I64)); push(uint64_t(v)); }
  void u64(uint64_t v, std::string_view) { push(kind(K_U64)); push(v); }
  void f64(double v, std::string_view) { uint64_t b; std::memcpy(&b, &v, 8); push(kind(K_F64)); push(b); }
  void decimal(std::string_view raw) { push(kind(K_DEC) | (uint64_t(raw.data() - base) << 24) | (raw.size() & 0xffffff)); }
  void boolean(bool b) { push(kind(b ? K_TRUE : K_FALSE)); }
  void null() { push(kind(K_NULL)); }
  uint64_t count() const { return words.size(); }
};

// T16: fixed 16-byte nodes. word0 = kind:8 | flags:8 | length-or-count:16 (low) ... packed as
// kind:8 | ascii:1 | len:23 | offset-or-next:32; word1 = number bits, raw span (offset:32 | len:32)
// of an escaped string, or the directory index of an object (T16d).
struct tape16 {
  struct node { uint64_t w0, w1; };
  std::pmr::memory_resource* memory;
  truffledata::tape::arena_vec<node> nodes;
  truffledata::tape::arena_vec<uint8_t> bytes;
  truffledata::tape::arena_vec<myna_value_directory_entry> directory, pending;
  const char* base = nullptr;
  bool directories = false;
  enum : uint64_t { K_NULL = 0, K_FALSE, K_TRUE, K_I64, K_U64, K_F64, K_STR, K_STR_OWNED, K_KEY, K_KEY_OWNED, K_DEC, K_OBJ, K_ARR };
  static constexpr uint64_t w0(uint64_t kind, bool ascii, uint64_t len, uint64_t off) {
    return kind << 56 | uint64_t(ascii) << 55 | (len & 0x7fffff) << 32 | (off & 0xffffffff);
  }
  void start(const char* b, size_t input, size_t) {
    base = b;
    size_t n = input / 4 + 8;
    nodes.reserve(memory, n);
    bytes.reserve(memory, input / 8 + 64);
    if (directories) { directory.reserve(memory, n / 2 + 1); pending.reserve(memory, 256); }
  }
  [[gnu::always_inline]] void push(uint64_t a, uint64_t b) { *nodes.extend(memory, 1) = {a, b}; }
  size_t begin(bool object) { size_t at = nodes.size(); push(w0(object ? K_OBJ : K_ARR, false, 0, 0), 0); return at; }
  void end(size_t at, uint64_t count) {
    nodes[at].w0 |= (count & 0x7fffff) << 32 | (nodes.size() & 0xffffffff);
    if (directories && (nodes[at].w0 >> 56) == K_OBJ) close_directory(at, count);
  }
  void close_directory(size_t at, uint64_t count) {
    size_t first = pending.size() - count;
    size_t start = directory.size();
    myna_value_directory_entry* out = directory.extend(memory, count);
    const myna_value_directory_entry* in = pending.data() + first;
    if (count <= 24) {
      for (size_t i = 0; i < count; ++i) {
        myna_value_directory_entry e = in[i];
        size_t j = i;
        while (j > 0 && out[j - 1].hash > e.hash) { out[j] = out[j - 1]; --j; }
        out[j] = e;
      }
    } else {
      std::memcpy(out, in, count * sizeof(myna_value_directory_entry));
      std::sort(out, out + count, [](const myna_value_directory_entry& a, const myna_value_directory_entry& b) {
        return a.hash < b.hash || (a.hash == b.hash && a.key_offset < b.key_offset);
      });
    }
    nodes[at].w1 = start + 1;
    pending.truncate(first);
  }
  void text(uint64_t k, std::string_view decoded, std::string_view raw, bool referenced, bool ascii) {
    if (referenced) push(w0(k, ascii, decoded.size(), decoded.data() - base), 0);
    else {
      size_t off = bytes.size();
      std::memcpy(bytes.extend(memory, decoded.size()), decoded.data(), decoded.size());
      push(w0(k + 1, ascii, decoded.size(), off), uint64_t(raw.data() - base) << 32 | raw.size());
    }
  }
  size_t object_ = 0;
  void key(uint32_t hash, std::string_view decoded, std::string_view raw, bool referenced, bool ascii) {
    if (directories) pending.push(memory, {hash, uint32_t(nodes.size() - object_)});
    text(K_KEY, decoded, raw, referenced, ascii);
  }
  void string(std::string_view decoded, std::string_view raw, bool referenced, bool ascii) { text(K_STR, decoded, raw, referenced, ascii); }
  void i64(int64_t v, std::string_view raw) { push(w0(K_I64, false, raw.size(), raw.data() - base), uint64_t(v)); }
  void u64(uint64_t v, std::string_view raw) { push(w0(K_U64, false, raw.size(), raw.data() - base), v); }
  void f64(double v, std::string_view raw) { uint64_t b; std::memcpy(&b, &v, 8); push(w0(K_F64, false, raw.size(), raw.data() - base), b); }
  void decimal(std::string_view raw) { push(w0(K_DEC, false, raw.size(), raw.data() - base), 0); }
  void boolean(bool b) { push(w0(b ? K_TRUE : K_FALSE, false, 0, 0), 0); }
  void null() { push(w0(K_NULL, false, 0, 0), 0); }
  uint64_t count() const { return nodes.size() + directory.size(); }
};

// T24: Myna 24-byte nodes through the production tape_writer, ABI v3, no side tables.
struct tape24 {
  std::pmr::memory_resource* memory;
  truffledata::tape::tape_writer writer;
  uint32_t region = 0;
  const char* base = nullptr;
  tape24(std::pmr::memory_resource* m) : memory(m), writer(m, {}) {}
  void start(const char* b, size_t input, size_t) {
    base = b;
    writer.reserve(input / 4 + 8, input / 8 + 64);
    region = writer.region(std::string_view(b, input));
  }
  size_t begin(bool object) { return writer.begin(object ? MYNA_VALUE_OBJECT : MYNA_VALUE_ARRAY, 0); }
  void end(size_t at, uint64_t count) { writer.end(at, count); }
  void text(std::string_view decoded, bool referenced) {
    if (decoded.size() <= MYNA_VALUE_INLINE_BYTES) writer.string(decoded);
    else if (referenced) writer.string_ref(region, uint32_t(decoded.data() - base), uint32_t(decoded.size()));
    else writer.string(decoded);
  }
  void key(uint32_t, std::string_view decoded, std::string_view, bool referenced, bool) { text(decoded, referenced); }
  void string(std::string_view decoded, std::string_view, bool referenced, bool) { text(decoded, referenced); }
  void i64(int64_t v, std::string_view) { writer.integer(v); }
  void u64(uint64_t v, std::string_view) { writer.unsigned_integer(v); }
  void f64(double v, std::string_view) { writer.floating(v); }
  void decimal(std::string_view raw) { writer.number_ref(region, uint32_t(raw.data() - base), uint32_t(raw.size())); }
  void boolean(bool b) { writer.boolean(b); }
  void null() { writer.null(); }
  uint64_t count() const { return writer.node_count(); }
};


// ---- Duplicate-check alternatives.
// K1: a 64-bit filter over the hash in front of the linear compares (YAML's key_set shape).
struct filter_keys {
  struct entry { uint32_t hash; uint32_t length; const char* data; };
  uint64_t filter = 0;
  entry small_[16];
  size_t count = 0;
  truffledata::tape::key_table* big = nullptr;
  std::pmr::memory_resource* memory;
  explicit filter_keys(std::pmr::memory_resource* m) : memory(m) {}
  ~filter_keys() { if (big) { big->~key_table(); memory->deallocate(big, sizeof(*big), alignof(truffledata::tape::key_table)); } }
  bool insert(uint32_t hash, std::string_view key) {
    if (big) return big->insert(hash, key);
    uint64_t bit = 1ull << (hash & 63);
    if (filter & bit) {
      for (size_t i = 0; i < count; ++i)
        if (small_[i].hash == hash && small_[i].length == key.size() && std::memcmp(small_[i].data, key.data(), key.size()) == 0) return false;
    }
    if (count == 16) {
      void* raw = memory->allocate(sizeof(truffledata::tape::key_table), alignof(truffledata::tape::key_table));
      big = new (raw) truffledata::tape::key_table(memory);
      for (size_t i = 0; i < count; ++i) big->insert(small_[i].hash, std::string_view(small_[i].data, small_[i].length));
      return big->insert(hash, key);
    }
    filter |= bit;
    small_[count++] = {hash, uint32_t(key.size()), key.data()};
    return true;
  }
};
// SWAR hash for keys of at most 8 bytes (one load, one multiply); FNV-1a beyond.
static inline uint32_t swar_hash(std::string_view key) {
  if (key.size() <= 8) {
    uint64_t w = 0;
    std::memcpy(&w, key.data(), 8);  // padded input: the load stays in bounds
    w &= key.size() == 8 ? ~0ull : (1ull << (8 * key.size())) - 1;
    w ^= uint64_t(key.size()) << 56;
    w *= 0x9E3779B97F4A7C15ull;
    return uint32_t(w >> 32);
  }
  return myna_value_hash(reinterpret_cast<const uint8_t*>(key.data()), key.size());
}
// K2: per-depth key templates (YAML decoder.h): an object whose keys repeat the previous object's
// at this depth, in order, needs no duplicate check (the template was checked); the first
// divergence falls back to a filter table seeded with the keys matched so far.
struct key_templates {
  struct entry { uint32_t hash; uint32_t length; const char* data; };
  struct level { entry keys[16]; uint32_t count = 0; };
  level levels[16];
};
struct template_keys {
  key_templates::level* tmpl;
  filter_keys fallback;
  uint32_t index = 0;
  bool matching = true;
  template_keys(std::pmr::memory_resource* m, key_templates::level* t) : tmpl(t), fallback(m) {}
  bool insert(uint32_t hash, std::string_view key) {
    if (matching) {
      if (index < tmpl->count && tmpl->keys[index].hash == hash && tmpl->keys[index].length == key.size()
          && std::memcmp(tmpl->keys[index].data, key.data(), key.size()) == 0) { ++index; return true; }
      matching = false;
      for (uint32_t i = 0; i < index; ++i) fallback.insert(tmpl->keys[i].hash, std::string_view(tmpl->keys[i].data, tmpl->keys[i].length));
    }
    ++index;
    return fallback.insert(hash, key);
  }
  // At close: an object that diverged (or a first object) becomes the template if it fits.
  void close() {
    if (matching) return;
    if (fallback.big || fallback.count > 16) { tmpl->count = 0; return; }
    for (size_t i = 0; i < fallback.count; ++i) tmpl->keys[i] = {fallback.small_[i].hash, fallback.small_[i].length, fallback.small_[i].data};
    tmpl->count = uint32_t(fallback.count);
  }
};

// D0t: strict duplicate check walking simdjson's DOM tape words directly (tape.md layout).
static uint64_t dom_tape_dup(const uint64_t* tape, const uint8_t* strings, std::pmr::memory_resource* memory) {
  uint64_t checked = 0;
  uint64_t n = tape[0] & 0xffffffffffffull;
  for (uint64_t i = 1; i < n; ++i) {
    uint64_t w = tape[i];
    char t = char(w >> 56);
    if (t == '{') {
      uint64_t end = (w & 0xffffffffull) - 1;  // the payload is one past the matching brace
      filter_keys keys(memory);
      uint64_t j = i + 1;
      while (j < end) {
        uint64_t kw = tape[j];  // the key string
        uint32_t len;
        std::memcpy(&len, strings + (kw & 0xffffffffffffffull), 4);
        std::string_view key(reinterpret_cast<const char*>(strings + (kw & 0xffffffffffffffull) + 4), len);
        if (!keys.insert(swar_hash(key), key)) throw std::invalid_argument("duplicate JSON key");
        ++checked;
        uint64_t vw = tape[j + 1];
        char vt = char(vw >> 56);
        if (vt == '{' || vt == '[') j = vw & 0xffffffffull;
        else if (vt == 'l' || vt == 'u' || vt == 'd') j += 3;
        else j += 2;
      }
    } else if (t == 'l' || t == 'u' || t == 'd') ++i;
  }
  return checked;
}

// T8x: tape8 without per-push capacity checks (presized to the worst-case bound).
struct tape8x : tape8 {
  uint64_t* out = nullptr;
  void start(const char* b, size_t input, size_t) {
    base = b;
    words.reserve(memory, input + 8);  // two words per 2-byte number is the worst case
    bytes.reserve(memory, input / 8 + 64);
    out = words.data();
  }
  [[gnu::always_inline]] void push(uint64_t w) { *out++ = w; }
  size_t begin(bool object) { size_t at = out - words.data(); push(kind(object ? K_OBJ : K_ARR)); return at; }
  void end(size_t at, uint64_t count) { words.data()[at] |= (count & 0xffffff) << 32 | ((out - words.data()) & 0xffffffff); }
  void text(uint64_t k, std::string_view decoded, bool referenced) {
    if (referenced) push(kind(k) | (uint64_t(decoded.data() - base) << 24) | (decoded.size() & 0xffffff));
    else {
      size_t off = bytes.size();
      std::memcpy(bytes.extend(memory, decoded.size()), decoded.data(), decoded.size());
      push(kind(k + 1) | (uint64_t(off) << 24) | (decoded.size() & 0xffffff));
    }
  }
  void key(uint32_t, std::string_view decoded, std::string_view, bool referenced, bool) { text(K_KEY, decoded, referenced); }
  void string(std::string_view decoded, std::string_view, bool referenced, bool) { text(K_STR, decoded, referenced); }
  void i64(int64_t v, std::string_view) { push(kind(K_I64)); push(uint64_t(v)); }
  void u64(uint64_t v, std::string_view) { push(kind(K_U64)); push(v); }
  void f64(double v, std::string_view) { uint64_t b; std::memcpy(&b, &v, 8); push(kind(K_F64)); push(b); }
  void decimal(std::string_view raw) { push(kind(K_DEC) | (uint64_t(raw.data() - base) << 24) | (raw.size() & 0xffffff)); }
  void boolean(bool b) { push(kind(b ? K_TRUE : K_FALSE)); }
  void null() { push(kind(K_NULL)); }
  uint64_t count() const { return out - words.data(); }
};

// The lean visitor: decoder.h's visit() with the writer abstracted, the duplicate check, the body
// scan and number typing selectable.
enum class numbers { typed, grammar_only };
enum dup { none = 0, table = 1, filter = 2, templates = 3, templates_swar = 4 };
template <class Writer, int Duplicates, bool Swar, numbers Numbers>
struct lean {
  std::pmr::memory_resource* memory;
  Writer& w;
  const char* base;
  key_templates tmpls{};
  static inline scan_result scan_body(std::string_view body) {
    if constexpr (Swar) return swar_scan(body.data(), body.size());
    else { auto s = truffledata::json::scan(body); return {s.clean, s.ascii}; }
  }
  template <class Value> void visit(Value& value, uint32_t depth) {
    using simdjson::ondemand::json_type;
    switch (value.type().value()) {
      case json_type::unknown: throw std::invalid_argument("unknown JSON token");
      case json_type::object: {
        size_t index = w.begin(true);
        if constexpr (std::is_same_v<Writer, tape16>) w.object_ = index;
        uint64_t members = 0;
        auto make_keys = [&]() {
          if constexpr (Duplicates == dup::filter) return filter_keys(memory);
          else if constexpr (Duplicates >= dup::templates) return template_keys(memory, &tmpls.levels[depth < 16 ? depth : 15]);
          else return truffledata::tape::key_table(memory);
        };
        auto keys = make_keys();
        for (auto entry : value.get_object()) {
          std::string_view raw = truffledata::json::trim(entry.key_raw_json_token().value());
          std::string_view body = raw.substr(1, raw.size() - 2);
          scan_result s = scan_body(body);
          std::string_view key = s.clean ? body : entry.unescaped_key().value();
          bool ascii = s.clean ? s.ascii : truffledata::tape::ascii(key);
          uint32_t hash = 0;
          if constexpr (Duplicates || std::is_same_v<Writer, tape16>) {
            if constexpr (Duplicates == dup::templates_swar) hash = swar_hash(key);
            else hash = myna_value_hash(reinterpret_cast<const uint8_t*>(key.data()), key.size());
          }
          if constexpr (Duplicates != dup::none) {
            if (!keys.insert(hash, key)) throw std::invalid_argument("duplicate JSON key");
          }
          w.key(hash, key, raw, s.clean, ascii);
          auto child = entry.value().value();
          visit(child, depth + 1);
          if constexpr (std::is_same_v<Writer, tape16>) w.object_ = index;
          ++members;
        }
        w.end(index, members);
        if constexpr (Duplicates >= dup::templates) keys.close();
        break;
      }
      case json_type::array: {
        size_t index = w.begin(false);
        uint64_t elements = 0;
        for (auto entry : value.get_array()) {
          auto child = entry.value();
          visit(child, depth + 1);
          ++elements;
        }
        w.end(index, elements);
        break;
      }
      case json_type::string: {
        std::string_view raw = truffledata::json::trim(value.raw_json_token());
        std::string_view body = raw.substr(1, raw.size() - 2);
        scan_result s = scan_body(body);
        if (s.clean) w.string(body, raw, true, s.ascii);
        else {
          std::string_view decoded = value.get_string().value();
          w.string(decoded, raw, false, truffledata::tape::ascii(decoded));
        }
        break;
      }
      case json_type::number: {
        std::string_view raw = truffledata::json::trim(value.raw_json_token());
        if constexpr (Numbers == numbers::grammar_only) {
          if (!truffledata::json::number(raw)) throw std::invalid_argument("invalid JSON number");
          // raw_json_token peeks; the iterator skips the unconsumed scalar when it advances.
          w.decimal(raw);
          break;
        } else {
          auto parsed = value.get_number();
          if (!parsed.error()) {
            using simdjson::ondemand::number_type;
            auto n = parsed.value();
            switch (n.get_number_type()) {
              case number_type::signed_integer:
                if (n.get_int64() == 0 && raw[0] == '-') w.f64(-0.0, raw); else w.i64(n.get_int64(), raw);
                return;
              case number_type::unsigned_integer: w.u64(n.get_uint64(), raw); return;
              case number_type::floating_point_number:
                if (std::isfinite(n.get_double())) { w.f64(n.get_double(), raw); return; }
                break;
              default: break;
            }
          }
          if (!truffledata::json::number(raw)) throw std::invalid_argument("invalid JSON number");
          w.decimal(raw);
          break;
        }
      }
      case json_type::boolean: w.boolean(value.get_bool().value()); break;
      case json_type::null:
        if (!value.is_null().value()) throw std::invalid_argument("invalid JSON null");
        w.null();
        break;
    }
  }
  void run(simdjson::ondemand::document& root) {
    visit(root, 0);
    if (root.is_scalar().value()) (void)root.raw_json().value();
    if (!root.at_end()) throw std::invalid_argument("trailing JSON input");
  }
};

// ---- DOM duplicate check and directory build over the public dom API.
static uint64_t dom_dup(simdjson::dom::element e, std::pmr::memory_resource* memory) {
  uint64_t n = 1;
  if (e.is_object()) {
    truffledata::tape::key_table keys(memory);
    for (auto field : e.get_object().value()) {
      std::string_view key = field.key;
      uint32_t hash = myna_value_hash(reinterpret_cast<const uint8_t*>(key.data()), key.size());
      if (!keys.insert(hash, key)) throw std::invalid_argument("duplicate JSON key");
      n += dom_dup(field.value, memory);
    }
  } else if (e.is_array()) {
    for (auto el : e.get_array().value()) n += dom_dup(el, memory);
  }
  return n;
}
static uint64_t dom_dir(simdjson::dom::element e, std::pmr::memory_resource* memory,
                        truffledata::tape::arena_vec<myna_value_directory_entry>& directory,
                        truffledata::tape::arena_vec<myna_value_directory_entry>& pending) {
  uint64_t n = 1;
  if (e.is_object()) {
    truffledata::tape::key_table keys(memory);
    size_t first = pending.size();
    uint32_t i = 0;
    for (auto field : e.get_object().value()) {
      std::string_view key = field.key;
      uint32_t hash = myna_value_hash(reinterpret_cast<const uint8_t*>(key.data()), key.size());
      if (!keys.insert(hash, key)) throw std::invalid_argument("duplicate JSON key");
      pending.push(memory, {hash, i++});
      n += dom_dir(field.value, memory, directory, pending);
    }
    size_t count = pending.size() - first;
    myna_value_directory_entry* out = directory.extend(memory, count);
    std::memcpy(out, pending.data() + first, count * sizeof(myna_value_directory_entry));
    std::sort(out, out + count, [](const myna_value_directory_entry& a, const myna_value_directory_entry& b) {
      return a.hash < b.hash || (a.hash == b.hash && a.key_offset < b.key_offset);
    });
    pending.truncate(first);
  } else if (e.is_array()) {
    for (auto el : e.get_array().value()) n += dom_dir(el, memory, directory, pending);
  }
  return n;
}


// ---- Round 3.
// A consumer that keeps every value alive without a call: folds lengths, first bytes and number
// bits into a checksum. Makes the no-output rows honest (a null writer lets the compiler drop the
// key and string scans entirely).
struct checksum_writer {
  uint64_t sum = 0;
  void start(const char*, size_t, size_t) {}
  size_t begin(bool object) { sum += object ? 1 : 2; return 0; }
  void end(size_t, uint64_t count) { sum += count; }
  void fold(std::string_view v, bool ascii) { sum += v.size() * 31 + (v.empty() ? 0 : uint8_t(v[0])) + ascii; }
  void key(uint32_t h, std::string_view k, std::string_view, bool, bool ascii) { sum += h; fold(k, ascii); }
  void string(std::string_view v, std::string_view, bool, bool ascii) { fold(v, ascii); }
  void i64(int64_t v, std::string_view) { sum += uint64_t(v); }
  void u64(uint64_t v, std::string_view) { sum += v; }
  void f64(double v, std::string_view) { uint64_t b; std::memcpy(&b, &v, 8); sum += b; }
  void decimal(std::string_view raw) { sum += raw.size(); }
  void boolean(bool b) { sum += b; }
  void null() { sum += 7; }
  uint64_t count() const { return sum; }
};

// S1/S2: our own stage 2 over simdjson's structural index (what simdjson's DOM stage 2 does for
// its own tape). Stage 1 (public dom_parser_implementation::stage1) validates UTF-8 and indexes
// every structural character and scalar start. S1 only classifies tokens; S2 is a recursive-descent
// stage 2 writing the compact tape: grammar (commas, colons, literals, numbers), clean-string
// SWAR scans to the closing quote (escaped strings decoded scalar-wise into the owned pool),
// integers typed, doubles left as lazy DECIMAL spans (a production stage 2 would use fast_float),
// depth, duplicate keys through key_table, trailing input rejected.
struct stage2 {
  const uint8_t* buf;
  const uint32_t* idx;
  uint32_t n;
  uint32_t at = 0;
  std::pmr::memory_resource* memory;
  tape8& w;
  [[noreturn]] static void fail(const char* m) { throw std::invalid_argument(m); }
  static bool ws(uint8_t c) { return c == ' ' || c == '\n' || c == '\r' || c == '\t'; }
  // Finds the closing quote of the string body at p; returns (body length, clean, ascii).
  struct body { size_t len; bool clean; bool ascii; };
  static inline body find_end(const uint8_t* p) {
    const uint64_t ones = 0x0101010101010101ull, highs = 0x8080808080808080ull;
    size_t i = 0;
    uint64_t any = 0;
    for (;; i += 8) {
      uint64_t v;
      std::memcpy(&v, p + i, 8);
      uint64_t q = v ^ (ones * '"');
      uint64_t quote = (q - ones) & ~q & highs;
      uint64_t bs = v ^ (ones * '\\');
      uint64_t bad = ((bs - ones) & ~bs & highs) | ((v - ones * 0x20) & ~v & highs);
      if (quote) {
        int qi = __builtin_ctzll(quote) / 8;
        uint64_t keep = qi == 0 ? 0 : (~0ull >> (8 * (8 - qi)));
        if (!(bad & keep)) {
          any |= v & keep;
          return {i + size_t(qi), true, (any & highs) == 0};
        }
        // An escape before the quote: the quote may be escaped, so finish with the scalar scan.
        bad = 1;
      }
      if (bad) {
        // An escape or control byte before the quote: a scalar scan finishes the body.
        size_t j = i;
        while (p[j] != '"') { if (p[j] == '\\') j += 2; else ++j; }
        return {j, false, false};
      }
      any |= v;
    }
  }
  static int hex(uint8_t c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
  }
  // Scalar unescape into the owned pool; validates escapes and surrogates.
  std::string_view unescape(const uint8_t* p, size_t len) {
    // The pool is presized to the input length (unescaping never lengthens), so views stay valid.
    uint8_t* out = w.bytes.extend(memory, len);
    uint8_t* o = out;
    for (size_t i = 0; i < len;) {
      uint8_t c = p[i];
      if (c < 0x20) fail("control byte in string");
      if (c != '\\') { *o++ = c; ++i; continue; }
      if (i + 1 >= len) fail("bad escape");
      uint8_t e = p[i + 1];
      i += 2;
      switch (e) {
        case '"': *o++ = '"'; break; case '\\': *o++ = '\\'; break; case '/': *o++ = '/'; break;
        case 'b': *o++ = '\b'; break; case 'f': *o++ = '\f'; break; case 'n': *o++ = '\n'; break;
        case 'r': *o++ = '\r'; break; case 't': *o++ = '\t'; break;
        case 'u': {
          auto read4 = [&](size_t k) -> int {
            if (k + 4 > len) return -1;
            int v = 0;
            for (int d = 0; d < 4; ++d) { int h = hex(p[k + d]); if (h < 0) return -1; v = v * 16 + h; }
            return v;
          };
          int cp = read4(i);
          if (cp < 0) fail("bad \\u escape");
          i += 4;
          if (cp >= 0xD800 && cp <= 0xDBFF) {
            if (i + 6 > len || p[i] != '\\' || p[i + 1] != 'u') fail("unpaired surrogate");
            int lo = read4(i + 2);
            if (lo < 0xDC00 || lo > 0xDFFF) fail("unpaired surrogate");
            i += 6;
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
    w.bytes.truncate(size_t(o - w.bytes.data()));
    return {reinterpret_cast<const char*>(out), size_t(o - out)};
  }
  // The string whose opening quote is at buf + start; returns the decoded text and whether it is
  // referenced (clean) and ASCII. Advances `at` past the string.
  struct text { std::string_view decoded; bool referenced; bool ascii; };
  text string_at(uint32_t start) {
    const uint8_t* p = buf + start + 1;
    body b = find_end(p);
    ++at;
    if (b.clean) return {std::string_view(reinterpret_cast<const char*>(p), b.len), true, b.ascii};
    std::string_view d = unescape(p, b.len);
    return {d, false, truffledata::tape::ascii(d)};
  }
  // Writes the word for `t` (a pool-owned text references the pool without a second copy).
  void emit(uint64_t kind, const text& t) {
    if (t.referenced) w.push(tape8::kind(kind) | (uint64_t(t.decoded.data() - reinterpret_cast<const char*>(buf)) << 24) | (t.decoded.size() & 0xffffff));
    else w.push(tape8::kind(kind + 1) | (uint64_t(reinterpret_cast<const uint8_t*>(t.decoded.data()) - w.bytes.data()) << 24) | (t.decoded.size() & 0xffffff));
  }
  // Scalar token at buf + start, ending before whitespace or a structural character.
  std::string_view token(uint32_t start) {
    const uint8_t* p = buf + start;
    size_t len = 0;
    for (;;) {
      uint8_t c = p[len];
      if (ws(c) || c == ',' || c == '}' || c == ']' || c == ':' || c == 0) break;
      ++len;
    }
    return {reinterpret_cast<const char*>(p), len};
  }
  void number(std::string_view raw) {
    if (!truffledata::json::number(raw)) fail("invalid JSON number");
    // Integers up to 18 digits are typed here; everything else stays a lazy DECIMAL span.
    size_t i = raw[0] == '-';
    if (raw.size() - i <= 18) {
      bool integer = true;
      for (size_t k = i; k < raw.size(); ++k) if (raw[k] < '0' || raw[k] > '9') { integer = false; break; }
      if (integer) {
        uint64_t v = 0;
        for (size_t k = i; k < raw.size(); ++k) v = v * 10 + uint64_t(raw[k] - '0');
        if (i && v == 0) w.f64(-0.0, raw); else w.i64(i ? -int64_t(v) : int64_t(v), raw);
        return;
      }
    }
    w.decimal(raw);
  }
  void value(uint32_t depth) {
    if (at >= n) fail("unexpected end");
    if (depth > 128) fail("depth");
    uint32_t start = idx[at];
    uint8_t c = buf[start];
    switch (c) {
      case '{': {
        ++at;
        size_t index = w.begin(true);
        uint64_t members = 0;
        truffledata::tape::key_table keys(memory);
        if (at < n && buf[idx[at]] == '}') { ++at; w.end(index, 0); return; }
        for (;;) {
          if (at >= n || buf[idx[at]] != '"') fail("expected key");
          text k = string_at(idx[at]);
          uint32_t hash = myna_value_hash(reinterpret_cast<const uint8_t*>(k.decoded.data()), k.decoded.size());
          if (!keys.insert(hash, k.decoded)) fail("duplicate JSON key");
          emit(tape8::K_KEY, k);
          if (at >= n || buf[idx[at]] != ':') fail("expected colon");
          ++at;
          value(depth + 1);
          ++members;
          if (at >= n) fail("unterminated object");
          uint8_t d = buf[idx[at]];
          ++at;
          if (d == ',') continue;
          if (d == '}') break;
          fail("expected , or }");
        }
        w.end(index, members);
        return;
      }
      case '[': {
        ++at;
        size_t index = w.begin(false);
        uint64_t elements = 0;
        if (at < n && buf[idx[at]] == ']') { ++at; w.end(index, 0); return; }
        for (;;) {
          value(depth + 1);
          ++elements;
          if (at >= n) fail("unterminated array");
          uint8_t d = buf[idx[at]];
          ++at;
          if (d == ',') continue;
          if (d == ']') break;
          fail("expected , or ]");
        }
        w.end(index, elements);
        return;
      }
      case '"': { text t = string_at(start); emit(tape8::K_STR, t); return; }
      case 't': if (std::memcmp(buf + start, "true", 4) || token(start).size() != 4) fail("bad literal"); ++at; w.boolean(true); return;
      case 'f': if (std::memcmp(buf + start, "false", 5) || token(start).size() != 5) fail("bad literal"); ++at; w.boolean(false); return;
      case 'n': if (std::memcmp(buf + start, "null", 4) || token(start).size() != 4) fail("bad literal"); ++at; w.null(); return;
      default:
        if (c == '-' || (c >= '0' && c <= '9')) { ++at; number(token(start)); return; }
        fail("unexpected token");
    }
  }
  void run() {
    value(0);
    if (at != n) fail("trailing JSON input");
  }
};
// S1: classify every indexed token (no validation, no output): the structural-walk floor.
static uint64_t structural_classify(const uint8_t* buf, const uint32_t* idx, uint32_t n) {
  uint64_t sum = 0;
  for (uint32_t i = 0; i < n; ++i) sum += buf[idx[i]];
  return sum;
}

template <class L> struct tag_t { using type = L; };
static void report(const char* name, const std::string& json, int reps) {
  auto line = [&](const char* what, double ns) {
    std::printf("%-6s %8zu B | %-52s %11.0f ns %9.0f MB/s %6.2f GB/s\n", name, json.size(), what, ns,
                json.size() / ns * 1000.0, json.size() / ns);
  };
  simdjson::padded_string padded(json);
  simdjson::ondemand::parser od;
  line("I0 On-Demand iterate only (stage 1)", best_ns([&] { auto d = od.iterate(padded).value(); sink += d.is_scalar().value(); }, reps));
  simdjson::dom::parser dom;
  line("D0 simdjson DOM parse (ceiling)", best_ns([&] { sink += dom.parse(padded).value().is_object(); }, reps));
  truffledata::tape::index_memory memory(SIZE_MAX, std::pmr::new_delete_resource());
  truffledata::tape::block_arena arena(&memory);
  line("D0d DOM parse + strict duplicate check (dom walk)", best_ns([&] {
    arena.reset();
    sink += dom_dup(dom.parse(padded).value(), &arena); }, reps));
  line("D0t DOM parse + duplicate check over the raw DOM tape words", best_ns([&] {
    arena.reset();
    auto e = dom.parse(padded).value();
    (void)e;
    sink += dom_tape_dup(dom.doc.tape.get(), dom.doc.string_buf.get(), &arena); }, reps));
  line("D0e D0d + directory per object (hash, sort)", best_ns([&] {
    arena.reset();
    truffledata::tape::arena_vec<myna_value_directory_entry> directory, pending;
    directory.reserve(&arena, padded.size() / 8 + 8);
    pending.reserve(&arena, 256);
    sink += dom_dir(dom.parse(padded).value(), &arena, directory, pending); }, reps));
  line("W1 On-Demand full walk (get_string, get_double)", best_ns([&] {
    auto root = od.iterate(padded).value();
    if (root.is_scalar().value()) sink += 1; else sink += walk(root); }, reps));
  auto run = [&](auto&& writer, auto tag) {
    using L = typename decltype(tag)::type;
    arena.reset();
    writer.start(padded.data(), padded.size(), 0);
    L l{&arena, writer, padded.data()};
    auto root = od.iterate(padded).value();
    l.run(root);
    sink += writer.count();
  };
  line("W2 lean walk, scalar scan, no duplicate check, no output", best_ns([&] {
    null_writer w; run(w, tag_t<lean<null_writer, dup::none, false, numbers::typed>>{}); }, reps));
  line("W2s lean walk, SWAR scan, no duplicate check, no output", best_ns([&] {
    null_writer w; run(w, tag_t<lean<null_writer, dup::none, true, numbers::typed>>{}); }, reps));
  line("W3 W2s + duplicate check: validate-only ceiling", best_ns([&] {
    null_writer w; run(w, tag_t<lean<null_writer, dup::table, true, numbers::typed>>{}); }, reps));
  line("W2c lean walk, checksum consumer, no duplicate check", best_ns([&] {
    checksum_writer w; run(w, tag_t<lean<checksum_writer, dup::none, true, numbers::typed>>{}); }, reps));
  line("W3c W2c + key_table duplicate check (honest validate ceiling)", best_ns([&] {
    checksum_writer w; run(w, tag_t<lean<checksum_writer, dup::table, true, numbers::typed>>{}); }, reps));
  simdjson::dom::parser s1;
  if (s1.allocate(padded.size()) != simdjson::SUCCESS) throw 9;
  auto& impl = *s1.implementation;
  auto stage1 = [&] {
    if (impl.stage1(reinterpret_cast<const uint8_t*>(padded.data()), padded.size(), simdjson::stage1_mode::regular) != simdjson::SUCCESS) throw 10;
  };
  line("S0 stage 1 through dom_parser_implementation", best_ns([&] { stage1(); sink += impl.n_structural_indexes; }, reps));
  line("S1 S0 + classify every structural index (walk floor)", best_ns([&] {
    stage1();
    sink += structural_classify(reinterpret_cast<const uint8_t*>(padded.data()), impl.structural_indexes.get(), impl.n_structural_indexes); }, reps));
  auto s2 = [&](tape8& w) {
    stage1();
    arena.reset();
    w.start(padded.data(), padded.size(), 0);
    w.bytes.reserve(&arena, padded.size() + 16);
    stage2 st{reinterpret_cast<const uint8_t*>(padded.data()), impl.structural_indexes.get(), impl.n_structural_indexes, 0, &arena, w};
    try { st.run(); } catch (const std::exception& e) {
      std::printf("%-6s %8zu B | S2 FAILED: %s at structural %u of %u\n", name, json.size(), e.what(), st.at, st.n);
      throw;
    }
  };
  bool s2_ok = true;
  try { tape8 w{&arena}; s2(w); } catch (...) { s2_ok = false; }
  if (s2_ok) line("S2 own stage 2 over the index: validate + duplicates + compact tape", best_ns([&] {
    tape8 w{&arena}; s2(w); sink += w.count(); }, reps));
  if (s2_ok) {
    tape8 w{&arena};
    s2(w);
    tape8 w2{&arena};
    run(w2, tag_t<lean<tape8, dup::table, true, numbers::typed>>{});
    std::printf("%-6s %8zu B | S2 words=%llu vs On-Demand tape8 words=%zu (doubles lazy in S2)\n", name, json.size(),
                (unsigned long long) w.count(), w2.words.size());
  }
  line("K1 W2s + duplicate check through a 64-bit filter", best_ns([&] {
    null_writer w; run(w, tag_t<lean<null_writer, dup::filter, true, numbers::typed>>{}); }, reps));
  line("K2 W2s + per-depth key templates, FNV hash", best_ns([&] {
    null_writer w; run(w, tag_t<lean<null_writer, dup::templates, true, numbers::typed>>{}); }, reps));
  line("K3 W2s + key templates + SWAR hash (<= 8 B keys)", best_ns([&] {
    null_writer w; run(w, tag_t<lean<null_writer, dup::templates_swar, true, numbers::typed>>{}); }, reps));
  line("T8k K3 + compact 8-byte tape", best_ns([&] {
    tape8 w{&arena}; run(w, tag_t<lean<tape8, dup::templates_swar, true, numbers::typed>>{}); }, reps));
  line("T8x K3 + compact 8-byte tape, unchecked presized pushes", best_ns([&] {
    tape8x w{{&arena}}; run(w, tag_t<lean<tape8x, dup::templates_swar, true, numbers::typed>>{}); }, reps));
  line("W3g W3 with grammar-only numbers (lazy typing)", best_ns([&] {
    null_writer w; run(w, tag_t<lean<null_writer, dup::table, true, numbers::grammar_only>>{}); }, reps));
  counting_sink cs;
  line("W3v W3 driving a virtual sink (direct-sink ceiling)", best_ns([&] {
    virtual_writer w{&cs}; run(w, tag_t<lean<virtual_writer, dup::table, true, numbers::typed>>{}); }, reps));
  line("T8 W3 + compact 8-byte tape (simdjson-style)", best_ns([&] {
    tape8 w{&arena}; run(w, tag_t<lean<tape8, dup::table, true, numbers::typed>>{}); }, reps));
  line("T16 W3 + fixed 16-byte nodes with embedded spans", best_ns([&] {
    tape16 w{&arena}; run(w, tag_t<lean<tape16, dup::table, true, numbers::typed>>{}); }, reps));
  line("T16d T16 + eager directory per object", best_ns([&] {
    tape16 w{&arena}; w.directories = true; run(w, tag_t<lean<tape16, dup::table, true, numbers::typed>>{}); }, reps));
  line("T24 W3 + Myna 24-byte nodes (tape_writer v3, no side tables)", best_ns([&] {
    tape24 w{&arena}; run(w, tag_t<lean<tape24, dup::table, true, numbers::typed>>{}); }, reps));
  simdjson::ondemand::parser parser;
  auto decode = [&](int directories) {
    arena.reset();
    truffledata::json::decode_hint hint = truffledata::json::hint_for(padded.size(), {}, {});
    arena.hint(hint.block);
    truffledata::json::tape_decoder<truffledata::tape::tape_writer> decoder(&arena, {}, directories);
    decoder.start(std::string_view(padded.data(), padded.size()), hint);
    auto root = parser.iterate(padded).value();
    decoder.decode(root);
    sink += decoder.view().base.node_count + decoder.view().directory_count;
  };
  line("V3 shipped tape_decoder, ABI v3 (spans + meta)", best_ns([&] { decode(truffledata::json::abi_v3); }, reps));
  line("V4 shipped tape_decoder, ABI v4 (+ directories)", best_ns([&] { decode(truffledata::json::directory_threshold); }, reps));
  {
    tape16 w{&arena};
    w.directories = true;
    run(w, tag_t<lean<tape16, dup::table, true, numbers::typed>>{});
    std::printf("%-6s %8zu B | nodes16=%zu directory=%zu owned_bytes=%zu tape16_bytes=%zu (%.2f x input)\n", name, json.size(),
                w.nodes.size(), w.directory.size(), w.bytes.size(), w.nodes.size() * 16 + w.directory.size() * 8,
                double(w.nodes.size() * 16 + w.directory.size() * 8) / json.size());
    tape8 w8{&arena};
    run(w8, tag_t<lean<tape8, dup::table, true, numbers::typed>>{});
    std::printf("%-6s %8zu B | words8=%zu tape8_bytes=%zu (%.2f x input); shipped v4: %.2f x input\n", name, json.size(),
                w8.words.size(), w8.words.size() * 8, double(w8.words.size() * 8) / json.size(),
                double(w.nodes.size() * (24 + 8 + 4) + w.directory.size() * 8) / json.size());
  }
  arena.release_all();
}

int main() {
  std::printf("simdjson %s active implementation: %s\n", SIMDJSON_VERSION,
              simdjson::get_active_implementation()->name().c_str());
  std::string small = doc(1), medium = "[", big = "[";
  for (int i = 0; i < 20; i++) medium += (i ? "," : "") + doc(i);
  medium += "]";
  for (int i = 0; i < 1000; i++) big += (i ? "," : "") + doc(i);
  big += "]";
  std::string huge = "[";
  for (int i = 0; i < 20000; i++) huge += (i ? "," : "") + doc(i);
  huge += "]";
  report("small", small, 200000);
  report("medium", medium, 20000);
  report("big", big, 200);
  report("huge", huge, 10);
  return 0;
}
