// TEMPORARY performance probe (not committed as a target; retained under benchmarks/results):
// JSON decode throughput against simdjson's own ceiling, ABI v3 vs v4 tapes, and the text arena
// against Myna's batch text export, for the comparison fixture's documents. One process, one
// worker: every row of a pair is measured back to back on the same documents.
//   D0  simdjson DOM parse (stage 1 + stage 2 tape, numbers and strings decoded): the ceiling
//   D1  simdjson On-Demand walk touching every value (type, number, string, key) without output
//   V3  tape_decoder writing an ABI v3 tape (no directories), one session-like arena, reset
//   V4  the same writing ABI v4 with a directory per object (what JSON ships)
//   B4  the same visitor over Myna 0.11.0's value_builder with reserve() and enable_v4(1)
//   S4  td_json_session_decode + reset through the C ABI (v4; the borrowed path sessions take)
//   T0  text arena (tape/native/text_arena.h) over a decoded v4 tape
//   T1  Myna myna_tape_text_v1 batch text export over the same tape (presized output and table)
#include "json/native/api.h"
#include "json/native/decoder.h"
#include "tape/native/text_arena.h"
#include <simdjson.h>
#include <simdutf.h>
#include <algorithm>
#include <chrono>
#include <cstdio>
#include <string>
#include <vector>

extern "C" int64_t myna_tape_text_v1(const myna_value_view*, uint8_t*, uint64_t, myna_text_entry*, uint64_t);

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

static void report(const char* name, const std::string& json, int reps) {
  auto line = [&](const char* what, double ns) {
    std::printf("%-6s %8zu B | %-48s %11.0f ns %9.0f MB/s %6.2f GB/s\n", name, json.size(), what, ns,
                json.size() / ns * 1000.0, json.size() / ns);
  };
  simdjson::padded_string padded(json);
  simdjson::dom::parser dom;
  line("D0 simdjson DOM parse (ceiling)", best_ns([&] { sink += dom.parse(padded).value().is_object(); }, reps));
  simdjson::ondemand::parser od;
  line("D1 simdjson On-Demand full walk", best_ns([&] {
    auto root = od.iterate(padded).value();
    if (root.is_scalar().value()) sink += 1;
    else sink += walk(root);
  }, reps));
  truffledata::tape::index_memory memory(SIZE_MAX, std::pmr::new_delete_resource());
  truffledata::tape::block_arena arena(&memory);
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
  line("V3 tape decoder, ABI v3 (no directories)", best_ns([&] { decode(truffledata::json::abi_v3); }, reps));
  line("V4 tape decoder, ABI v4 (directory per object)", best_ns([&] { decode(truffledata::json::directory_threshold); }, reps));
  line("B4 Myna value_builder, reserve + enable_v4", best_ns([&] {
    arena.reset();
    truffledata::json::decode_hint hint = truffledata::json::hint_for(padded.size(), {}, {});
    arena.hint(hint.block);
    truffledata::json::tape_decoder<truffledata::tape::builder_writer> decoder(&arena, {});
    decoder.start(std::string_view(padded.data(), padded.size()), hint);
    auto root = parser.iterate(padded).value();
    decoder.decode(root);
    sink += decoder.view().base.node_count + decoder.view().directory_count;
  }, reps));
  JsonLimitsStruct limits{1ull << 30, 1ull << 30, 1u << 24, 1ull << 30, 128};
  void* session = td_json_session_create(&limits);
  if (!session) throw 1;
  JsonResult borrowed{};
  line("S4 td_json_session_decode + reset (C ABI, v4)", best_ns([&] {
    if (td_json_session_decode(session, reinterpret_cast<const uint8_t*>(json.data()), json.size(), 0, nullptr, 0, &borrowed) != TD_JSON_OK)
      throw 2;
    sink += borrowed.lexical_count;
    td_json_session_reset(session);
  }, reps));
  if (td_json_session_decode(session, reinterpret_cast<const uint8_t*>(json.data()), json.size(), 0, nullptr, 0, &borrowed) != TD_JSON_OK)
    throw 3;
  const auto* view = static_cast<const myna_value_view*>(borrowed.view);
  const auto* meta = static_cast<const uint32_t*>(borrowed.key_hashes);
  std::vector<uint64_t> table(view->node_count);
  std::vector<uint8_t> out(truffledata::tape::text_capacity_bound(json.size()));
  line("T0 text arena (text_arena.h)", best_ns([&] {
    sink += truffledata::tape::write_text_arena(*view, meta, table.data(), out.data(), out.size()); }, reps));
  int64_t need = myna_tape_text_v1(view, nullptr, 0, nullptr, 0);
  if (need < 0) throw 4;
  std::vector<myna_text_entry> entries(view->node_count);
  std::vector<uint8_t> batch(size_t(need) + 16);
  line("T1 Myna myna_tape_text_v1 batch export", best_ns([&] {
    int64_t n = myna_tape_text_v1(view, batch.data(), batch.size(), entries.data(), entries.size());
    if (n < 0) throw 5;
    sink += uint64_t(n); }, reps));
  std::printf("%-6s %8zu B | nodes=%llu directory_entries=%llu text_arena_bytes=%zu myna_batch_bytes=%lld\n", name,
              json.size(), (unsigned long long) view->node_count,
              (unsigned long long) static_cast<const myna_value_view_v4*>(borrowed.view)->directory_count,
              truffledata::tape::write_text_arena(*view, meta, table.data(), out.data(), out.size()), (long long) need);
  td_json_session_reset(session);
  td_json_session_release(session);
  arena.release_all();
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
  std::string huge = "[";
  for (int i = 0; i < 20000; i++) huge += (i ? "," : "") + doc(i);
  huge += "]";
  report("small", small, 200000);
  report("medium", medium, 20000);
  report("big", big, 200);
  report("huge", huge, 10);
  return 0;
}
