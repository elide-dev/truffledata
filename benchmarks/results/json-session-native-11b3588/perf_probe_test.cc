// TEMPORARY performance probe (not committed): decomposes native decode cost per shape.
// Variants are deliberately standalone copies so production headers stay untouched.
#include "json/native/api.h"
#include "json/native/decoder.h"
#include "json/native/probe_baseline_decoder.h"
#include <myna/values.hpp>
#include <simdjson.h>
#include <algorithm>
#include <chrono>
#include <cstdio>
#include <cstring>
#include <memory_resource>
#include <string>
#include <unordered_set>
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
  double best = 1e30;
  for (int round = 0; round < 7; round++) {
    auto t0 = clk::now();
    for (int r = 0; r < reps; r++) f();
    double ns = std::chrono::duration<double, std::nano>(clk::now() - t0).count() / reps;
    best = std::min(best, ns);
  }
  return best;
}

// ---- W0/W1: the existing bare walks -------------------------------------------------------
template <bool Dups> static uint64_t walk(simdjson::ondemand::value v) {
  using simdjson::ondemand::json_type;
  uint64_t n = 0;
  switch (v.type().value()) {
    case json_type::object: {
      std::pmr::unordered_set<std::string_view> keys;
      for (auto f : v.get_object()) {
        auto key = f.unescaped_key().value();
        if constexpr (Dups) keys.insert(key);
        n += key.size() + walk<Dups>(f.value().value());
      }
      return n;
    }
    case json_type::array:
      for (auto e : v.get_array()) n += walk<Dups>(e.value());
      return n;
    case json_type::string: return v.get_string().value().size();
    case json_type::number: return (uint64_t) v.get_double().value();
    case json_type::boolean: return v.get_bool().value();
    default: return v.is_null().value();
  }
}

// ---- Lean walk: raw tokens, deferred unescape, get_number first, small-object linear dups ----
static inline bool clean_body(std::string_view body) {
  for (unsigned char c : body) if (c == '\\' || c < 0x20) return false;
  return true;
}
static inline uint64_t fnv(std::string_view s) {
  uint64_t h = 1469598103934665603ull;
  for (unsigned char c : s) { h ^= c; h *= 1099511628211ull; }
  return h;
}
struct key_set {
  // Linear for small objects; open addressing (hash, ptr, len) beyond 16 keys.
  std::string_view small[16]; size_t count = 0;
  std::vector<std::pair<uint64_t, std::string_view>> table; size_t mask = 0, used = 0;
  bool insert(std::string_view k) {
    if (count < 16) {
      for (size_t i = 0; i < count; i++) if (small[i].size() == k.size() && small[i] == k) return false;
      small[count++] = k; return true;
    }
    if (table.empty()) {
      table.assign(64, {0, {}}); mask = 63;
      for (size_t i = 0; i < count; i++) put(fnv(small[i]) | 1, small[i]);
    }
    uint64_t h = fnv(k) | 1;
    for (size_t i = h & mask;; i = (i + 1) & mask) {
      if (!table[i].first) break;
      if (table[i].first == h && table[i].second == k) return false;
    }
    if ((used + 1) * 2 > table.size()) {
      auto old = std::move(table); table.assign(old.size() * 2, {0, {}}); mask = table.size() - 1; used = 0;
      for (auto& e : old) if (e.first) put(e.first, e.second);
    }
    put(h, k); return true;
  }
  void put(uint64_t h, std::string_view k) {
    for (size_t i = h & mask;; i = (i + 1) & mask) if (!table[i].first) { table[i] = {h, k}; used++; return; }
  }
};

template <bool Dups> static uint64_t lean_walk(simdjson::ondemand::value v) {
  using simdjson::ondemand::json_type;
  uint64_t n = 0;
  switch (v.type().value()) {
    case json_type::object: {
      key_set keys;
      for (auto f : v.get_object()) {
        std::string_view key = f.escaped_key().value();
        if (!clean_body(key)) key = f.unescaped_key().value();
        if constexpr (Dups) if (!keys.insert(key)) throw std::invalid_argument("dup");
        n += key.size() + lean_walk<Dups>(f.value().value());
      }
      return n;
    }
    case json_type::array:
      for (auto e : v.get_array()) n += lean_walk<Dups>(e.value());
      return n;
    case json_type::string: {
      std::string_view raw = truffledata::json::trim(v.raw_json_token());
      std::string_view body = raw.substr(1, raw.size() - 2);
      if (clean_body(body)) return body.size();
      return v.get_string().value().size();
    }
    case json_type::number: {
      auto num = v.get_number();
      if (!num.error()) return (uint64_t) num.value().get_number_type();
      std::string_view raw = truffledata::json::trim(v.raw_json_token());
      if (!truffledata::json::number(raw)) throw std::invalid_argument("number");
      return raw.size();
    }
    case json_type::boolean: return v.get_bool().value();
    default: return v.is_null().value();
  }
}

// ---- Lean decode into a Myna builder in a monotonic arena ---------------------------------------
struct lean_decoder {
  myna::value_builder& b; uint32_t region; const char* base; std::vector<uint64_t>* spans;
  void span(std::string_view raw) { if (spans) spans->push_back(raw.empty() ? 0 : (uint64_t(raw.data() - base) << 32) | raw.size()); }
  void text(std::string_view decoded, std::string_view raw) {
    span(raw);
    if (decoded.size() <= 16) b.string(decoded);
    else if (decoded.data() >= base && decoded.data() < base + (1u << 30) && raw.size() == decoded.size() + 2)
      b.string_ref(region, uint32_t(decoded.data() - base), uint32_t(decoded.size()));
    else b.string(decoded);
  }
  void visit(simdjson::ondemand::value v) {
    using simdjson::ondemand::json_type;
    switch (v.type().value()) {
      case json_type::object: {
        span({}); b.begin_object(); key_set keys;
        for (auto f : v.get_object()) {
          std::string_view raw = truffledata::json::trim(f.key_raw_json_token().value());
          std::string_view key = f.escaped_key().value();
          if (!clean_body(key)) key = f.unescaped_key().value();
          if (!keys.insert(key)) throw std::invalid_argument("dup");
          text(key, raw);
          visit(f.value().value());
        }
        b.end(); break;
      }
      case json_type::array:
        span({}); b.begin_array();
        for (auto e : v.get_array()) visit(e.value());
        b.end(); break;
      case json_type::string: {
        std::string_view raw = truffledata::json::trim(v.raw_json_token());
        std::string_view body = raw.substr(1, raw.size() - 2);
        if (clean_body(body)) text(body, raw); else text(v.get_string().value(), raw);
        break;
      }
      case json_type::number: {
        std::string_view raw = truffledata::json::trim(v.raw_json_token());
        span(raw);
        auto num = v.get_number();
        if (!num.error()) {
          using simdjson::ondemand::number_type;
          auto n = num.value();
          switch (n.get_number_type()) {
            case number_type::signed_integer: if (n.get_int64() == 0 && raw[0] == '-') b.floating(-0.0); else b.integer(n.get_int64()); break;
            case number_type::unsigned_integer: b.unsigned_integer(n.get_uint64()); break;
            default: b.floating(n.get_double()); break;
          }
        } else {
          if (!truffledata::json::number(raw)) throw std::invalid_argument("number");
          b.validated_number_ref(region, uint32_t(raw.data() - base), uint32_t(raw.size()));
        }
        break;
      }
      case json_type::boolean: span({}); b.boolean(v.get_bool().value()); break;
      default: span({}); if (!v.is_null().value()) throw std::invalid_argument("null"); b.null(); break;
    }
  }
};

// ---- DOM walk into the same builder (numbers/strings already parsed by the DOM stage) ----------
struct dom_decoder {
  myna::value_builder& b;
  void visit(simdjson::dom::element e) {
    using simdjson::dom::element_type;
    switch (e.type()) {
      case element_type::OBJECT: {
        b.begin_object(); key_set keys;
        for (auto [k, v] : e.get_object().value()) { if (!keys.insert(k)) throw std::invalid_argument("dup"); b.string(k); visit(v); }
        b.end(); break;
      }
      case element_type::ARRAY: b.begin_array(); for (auto v : e.get_array().value()) visit(v); b.end(); break;
      case element_type::STRING: b.string(e.get_string().value()); break;
      case element_type::INT64: b.integer(e.get_int64().value()); break;
      case element_type::UINT64: b.unsigned_integer(e.get_uint64().value()); break;
      case element_type::DOUBLE: b.floating(e.get_double().value()); break;
      case element_type::BOOL: b.boolean(e.get_bool().value()); break;
      default: b.null(); break;
    }
  }
};

static std::vector<char> arena_buffer(16u << 20);

static void report(const char* name, const std::string& json, int reps) {
  simdjson::padded_string padded(json);
  simdjson::ondemand::parser parser;
  simdjson::dom::parser dom;
  auto mbs = [&](double ns) { return json.size() / ns * 1000.0; };
  auto line = [&](const char* what, double ns) {
    std::printf("%-6s %8zu B | %-44s %10.0f ns %8.0f MB/s\n", name, json.size(), what, ns, mbs(ns));
  };
  line("iterate only (stage 1)", best_ns([&] { auto d = parser.iterate(padded); sink += d.type().value() == simdjson::ondemand::json_type::object; }, reps));
  line("W0 bare walk", best_ns([&] { auto d = parser.iterate(padded); sink += walk<false>(d.get_value().value()); }, reps));
  line("W1 bare walk + pmr set", best_ns([&] { auto d = parser.iterate(padded); sink += walk<true>(d.get_value().value()); }, reps));
  line("W2 lean walk, no dups", best_ns([&] { auto d = parser.iterate(padded); sink += lean_walk<false>(d.get_value().value()); }, reps));
  line("W3 lean walk + linear/OA dups", best_ns([&] { auto d = parser.iterate(padded); sink += lean_walk<true>(d.get_value().value()); }, reps));
  {
    truffledata::json::document d(json);
    auto s = d.allocations();
    std::printf("%-6s %8zu B | current document: nodes=%llu index_allocations=%zu index_peak=%zu retained=%zu\n", name, json.size(),
                (unsigned long long) d.view().node_count, s.index_allocations, s.index_peak_bytes, s.index_retained_bytes);
  }
  {
    truffledata::json::baseline::document d(json);
    auto s = d.allocations();
    std::printf("%-6s %8zu B | baseline document (861dff1): nodes=%llu index_allocations=%zu index_peak=%zu retained=%zu\n", name, json.size(),
                (unsigned long long) d.view().node_count, s.index_allocations, s.index_peak_bytes, s.index_retained_bytes);
  }
  line("B0 baseline document 861dff1 (default resource)", best_ns([&] { truffledata::json::baseline::document d(json); sink += d.view().node_count; }, reps));
  line("D0 current document (default resource)", best_ns([&] { truffledata::json::document d(json); sink += d.view().node_count; }, reps));
  line("D1 current document (monotonic arena)", best_ns([&] {
    std::pmr::monotonic_buffer_resource arena(arena_buffer.data(), arena_buffer.size(), std::pmr::null_memory_resource());
    truffledata::json::document d(json, {}, {}, &arena); sink += d.view().node_count; }, reps));
  line("D2 lean decode -> builder, arena, spans", best_ns([&] {
    std::pmr::monotonic_buffer_resource arena(arena_buffer.data(), arena_buffer.size(), std::pmr::null_memory_resource());
    myna::value_builder b(&arena); std::vector<uint64_t> spans; spans.reserve(json.size() / 4 + 8);
    uint32_t region = b.region(std::string_view(padded.data(), padded.size()));
    auto d = parser.iterate(padded);
    lean_decoder ld{b, region, padded.data(), &spans}; ld.visit(d.get_value().value());
    if (!d.at_end()) throw std::invalid_argument("trailing");
    sink += b.view().node_count; }, reps));
  line("D3 lean decode -> builder, arena, no spans", best_ns([&] {
    std::pmr::monotonic_buffer_resource arena(arena_buffer.data(), arena_buffer.size(), std::pmr::null_memory_resource());
    myna::value_builder b(&arena);
    uint32_t region = b.region(std::string_view(padded.data(), padded.size()));
    auto d = parser.iterate(padded);
    lean_decoder ld{b, region, padded.data(), nullptr}; ld.visit(d.get_value().value());
    sink += b.view().node_count; }, reps));
  line("D4 DOM parse only", best_ns([&] { auto e = dom.parse(padded).value(); sink += (uint64_t) e.type(); }, reps));
  line("D5 DOM parse + walk -> builder, arena", best_ns([&] {
    std::pmr::monotonic_buffer_resource arena(arena_buffer.data(), arena_buffer.size(), std::pmr::null_memory_resource());
    myna::value_builder b(&arena);
    auto e = dom.parse(padded).value(); dom_decoder dd{b}; dd.visit(e); sink += b.view().node_count; }, reps));
  line("A0 C ABI decode+release", best_ns([&] {
    JsonResult r{};
    if (td_json_decode((const uint8_t*) json.data(), json.size(), 1ull << 30, 1ull << 30, 1u << 24, 1ull << 30, 128, &r) != TD_JSON_OK) throw 1;
    td_json_release(&r); }, reps));
  {
    JsonLimitsStruct limits{1ull << 30, 1ull << 30, 1u << 24, 1ull << 30, 128};
    void* session = td_json_session_create(&limits);
    if (!session) throw 1;
    JsonResult borrowed{};
    line("S0 session decode+reset (borrowed)", best_ns([&] {
      if (td_json_session_decode(session, (const uint8_t*) json.data(), json.size(), 0, nullptr, 0, &borrowed) != TD_JSON_OK) throw 1;
      sink += borrowed.lexical_count;
      td_json_session_reset(session); }, reps));
    line("S1 session decode+retain+release", best_ns([&] {
      if (td_json_session_decode(session, (const uint8_t*) json.data(), json.size(), 0, nullptr, 0, &borrowed) != TD_JSON_OK) throw 1;
      JsonResult kept{};
      if (td_json_session_retain(session, &kept) != TD_JSON_OK) throw 1;
      td_json_release(&kept); }, reps));
    if (td_json_session_decode(session, (const uint8_t*) json.data(), json.size(), 0, nullptr, 0, &borrowed) != TD_JSON_OK) throw 1;
    std::printf("%-6s %8zu B | session steady state: index_allocations=%llu\n", name, json.size(),
                (unsigned long long) borrowed.index_allocations);
    td_json_session_release(session);
  }
  line("A1 C ABI decode only (release excluded)", best_ns([&] {
    JsonResult r{};
    auto t0 = clk::now();
    if (td_json_decode((const uint8_t*) json.data(), json.size(), 1ull << 30, 1ull << 30, 1u << 24, 1ull << 30, 128, &r) != TD_JSON_OK) throw 1;
    sink += std::chrono::duration<double, std::nano>(clk::now() - t0).count() > 0;
    td_json_release(&r); }, reps) );
}

int main() {
  std::printf("simdjson %s active implementation: %s (%s)\n", SIMDJSON_VERSION,
              simdjson::get_active_implementation()->name().c_str(),
              simdjson::get_active_implementation()->description().c_str());
#ifdef SIMDJSON_THREADS_ENABLED
  std::printf("SIMDJSON_THREADS_ENABLED=%d\n", (int) SIMDJSON_THREADS_ENABLED);
#else
  std::printf("SIMDJSON_THREADS_ENABLED not defined (document_stream is single-threaded)\n");
#endif
  std::string small = doc(1), medium = "[", big = "[", ndjson;
  for (int i = 0; i < 20; i++) medium += (i ? "," : "") + doc(i);
  medium += "]";
  for (int i = 0; i < 1000; i++) { big += (i ? "," : "") + doc(i); ndjson += doc(i) + "\n"; }
  big += "]";
  report("small", small, 200000);
  report("medium", medium, 20000);
  report("big", big, 200);

  // ---- NDJSON: per-document iterate versus iterate_many, both with the lean walk ----
  {
    simdjson::padded_string padded(ndjson);
    simdjson::ondemand::parser parser;
    std::vector<std::pair<size_t, size_t>> lines;
    size_t start = 0;
    for (size_t i = 0; i < ndjson.size(); i++) if (ndjson[i] == '\n') { lines.push_back({start, i - start}); start = i + 1; }
    auto per_doc = best_ns([&] {
      uint64_t n = 0;
      for (auto [off, len] : lines) {
        simdjson::padded_string_view view(padded.data() + off, len, padded.size() + simdjson::SIMDJSON_PADDING - off);
        auto d = parser.iterate(view); n += lean_walk<true>(d.get_value().value());
      }
      sink += n; }, 200);
    auto many = [&](size_t batch) {
      return best_ns([&] {
        uint64_t n = 0;
        auto stream = parser.iterate_many(padded, batch).value();
        for (auto d : stream) n += lean_walk<true>(d.get_value().value());
        if (stream.truncated_bytes()) throw 1;
        sink += n; }, 200);
    };
    std::printf("ndjson %8zu B | %-44s %10.0f ns/doc %8.0f MB/s\n", ndjson.size(), "per-document iterate + lean walk", per_doc / 1000, ndjson.size() / per_doc * 1000);
    for (size_t batch : {size_t(64) << 10, size_t(1) << 20, size_t(4) << 20})
      std::printf("ndjson %8zu B | iterate_many(batch=%7zu) + lean walk          %10.0f ns/doc %8.0f MB/s\n", ndjson.size(), batch, many(batch) / 1000, ndjson.size() / many(batch) * 1000);
  }

  // ---- Big: lazy pointer query and a top-level frame index built by skipping ----
  {
    simdjson::padded_string padded(big);
    simdjson::ondemand::parser parser;
    auto ptr = best_ns([&] { auto d = parser.iterate(padded); sink += d.at_pointer("/999/id").get_int64().value(); }, 200);
    auto ptr0 = best_ns([&] { auto d = parser.iterate(padded); sink += d.at_pointer("/0/id").get_int64().value(); }, 200);
    std::vector<uint64_t> frames; frames.reserve(1000);
    auto skel = best_ns([&] {
      frames.clear();
      auto d = parser.iterate(padded);
      for (auto e : d.get_array()) { auto raw = e.raw_json().value(); frames.push_back((uint64_t(raw.data() - padded.data()) << 32) | raw.size()); }
      sink += frames.size(); }, 200);
    auto one = best_ns([&] {
      auto [off, len] = std::pair<size_t, size_t>{frames[999] >> 32, frames[999] & 0xffffffffu};
      simdjson::padded_string_view view(padded.data() + off, len, padded.size() + simdjson::SIMDJSON_PADDING - off);
      std::pmr::monotonic_buffer_resource arena(arena_buffer.data(), arena_buffer.size(), std::pmr::null_memory_resource());
      myna::value_builder b(&arena); uint32_t region = b.region(std::string_view(padded.data(), padded.size()));
      auto d = parser.iterate(view); lean_decoder ld{b, region, padded.data(), nullptr}; ld.visit(d.get_value().value());
      sink += b.view().node_count; }, 20000);
    std::printf("big    %8zu B | at_pointer(/999/id) fresh iterate              %10.0f ns\n", big.size(), ptr);
    std::printf("big    %8zu B | at_pointer(/0/id) fresh iterate                %10.0f ns\n", big.size(), ptr0);
    std::printf("big    %8zu B | top-level frame index by raw_json() skipping   %10.0f ns %8.0f MB/s\n", big.size(), skel, big.size() / skel * 1000);
    std::printf("big    %8zu B | materialize one element from its frame        %10.0f ns\n", big.size(), one);
  }
  return 0;
}
