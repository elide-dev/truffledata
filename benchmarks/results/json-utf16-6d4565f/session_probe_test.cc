// TEMPORARY performance probe (not committed as a target; retained under benchmarks/results):
// native session decode variants through the C ABI for the comparison fixture's documents.
//   S0  UTF-8 decode + reset (B1's borrowed path, re-measured)
//   T0  UTF-8 decode with a text arena (Task D2) + reset
//   U0  UTF-16LE decode (Task D3: simdutf validate + transcode into the padded buffer) + reset
//   U1  UTF-16LE decode with a text arena + reset
//   L0  Latin-1 decode (Task D3) + reset
//   C0  UTF-16 -> UTF-8 transcode alone (simdutf), to attribute U0 - S0
#include "json/native/api.h"
#include <simdjson.h>
#include <simdutf.h>
#include <algorithm>
#include <chrono>
#include <cstdio>
#include <string>
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
  for (int round = 0; round < 7; round++) {
    auto t0 = clk::now();
    for (int i = 0; i < reps; i++) f();
    best = std::min(best, std::chrono::duration<double, std::nano>(clk::now() - t0).count() / reps);
  }
  return best;
}

static void report(const char* name, const std::string& json, int reps) {
  std::u16string units(simdutf::utf16_length_from_utf8(json.data(), json.size()), u'\0');
  simdutf::convert_valid_utf8_to_utf16le(json.data(), json.size(), units.data());
  std::string latin1 = json;  // the fixture's raw text is ASCII (escapes spell the rest)
  std::vector<uint8_t> text(2 * 2 * units.size() + 16);
  JsonLimitsStruct limits{1ull << 30, 1ull << 30, 1u << 24, 1ull << 30, 128};
  void* session = td_json_session_create(&limits);
  if (!session) throw 1;
  JsonResult borrowed{};
  auto line = [&](const char* what, double ns) {
    std::printf("%-6s %8zu B | %-52s %10.0f ns %8.0f MB/s\n", name, json.size(), what, ns, json.size() / ns * 1000.0);
  };
  auto run = [&](const void* data, size_t bytes, uint32_t flags, uint8_t* out, size_t capacity) {
    if (td_json_session_decode(session, static_cast<const uint8_t*>(data), bytes, flags, out, capacity, &borrowed) != TD_JSON_OK)
      throw 2;
    sink += borrowed.lexical_count;
    td_json_session_reset(session);
  };
  line("S0 UTF-8 decode+reset", best_ns([&] { run(json.data(), json.size(), 0, nullptr, 0); }, reps));
  line("T0 UTF-8 decode+text arena+reset", best_ns([&] { run(json.data(), json.size(), TD_JSON_WANT_TEXT, text.data(), text.size()); }, reps));
  line("U0 UTF-16 decode+reset", best_ns([&] { run(units.data(), 2 * units.size(), TD_JSON_UTF16_INPUT, nullptr, 0); }, reps));
  line("U1 UTF-16 decode+text arena+reset", best_ns([&] {
    run(units.data(), 2 * units.size(), TD_JSON_UTF16_INPUT | TD_JSON_WANT_TEXT, text.data(), text.size()); }, reps));
  line("L0 Latin-1 decode+reset", best_ns([&] { run(latin1.data(), latin1.size(), TD_JSON_LATIN1_INPUT, nullptr, 0); }, reps));
  std::string scratch(3 * units.size() + 64, '\0');
  line("C0 UTF-16 validate+transcode only", best_ns([&] {
    if (!simdutf::validate_utf16le(units.data(), units.size())) throw 3;
    sink += simdutf::convert_valid_utf16le_to_utf8(units.data(), units.size(), scratch.data()); }, reps));
  if (td_json_session_decode(session, reinterpret_cast<const uint8_t*>(units.data()), 2 * units.size(),
                             TD_JSON_UTF16_INPUT | TD_JSON_WANT_TEXT, text.data(), text.size(), &borrowed) != TD_JSON_OK)
    throw 4;
  std::printf("%-6s %8zu B | steady state: index_allocations=%llu text_bytes=%llu\n", name, json.size(),
              (unsigned long long) borrowed.index_allocations, (unsigned long long) borrowed.text_bytes);
  td_json_session_release(session);
}

int main() {
  std::printf("simdjson %s active implementation: %s; simdutf active implementation: %s\n", SIMDJSON_VERSION,
              simdjson::get_active_implementation()->name().c_str(),
              simdutf::get_active_implementation()->name().c_str());
  std::string small = doc(1), medium = "[", big = "[";
  for (int i = 0; i < 20; i++) medium += (i ? "," : "") + doc(i);
  medium += "]";
  for (int i = 0; i < 1000; i++) big += (i ? "," : "") + doc(i);
  big += "]";
  report("small", small, 200000);
  report("medium", medium, 20000);
  report("big", big, 200);
  return 0;
}
