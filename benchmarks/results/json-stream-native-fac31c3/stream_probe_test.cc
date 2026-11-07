// TEMPORARY performance probe (not committed as a target; retained under benchmarks/results):
// NDJSON through the Task F1 stream ABI against per-line session decodes, for the comparison
// fixture's documents (1,000 lines, the same generator as the json-perf-probe-861dff1 probe).
//   N0  per-line td_json_session_decode + reset over pre-split lines (full tape per document)
//   N1  stream: one feed of the whole buffer, finish, next() per document (batch 1 MiB)
//   N2  stream fed in 64 KiB chunks, draining after each (batch 1 MiB)
//   N3  N2 with a 64 KiB iterate_many batch
//   N4  N1 with a fresh stream per round on one session (create + release included)
//   I0  iterate_many(1 MiB) + source() per document, no decode (boundary cost alone)
#include "json/native/api.h"
#include <simdjson.h>
#include <algorithm>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <string>
#include <vector>

using clk = std::chrono::steady_clock;
static volatile uint64_t sink;
[[noreturn]] static void fail(int where, long detail = 0) {
  std::printf("probe failure %d (%ld)\n", where, detail);
  std::fflush(stdout);
  std::exit(1);
}

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

int main() {
  std::printf("simdjson %s active implementation: %s\n", SIMDJSON_VERSION,
              simdjson::get_active_implementation()->name().c_str());
  std::string ndjson;
  std::vector<std::pair<size_t, size_t>> lines;
  for (int i = 0; i < 1000; i++) {
    std::string d = doc(i);
    lines.push_back({ndjson.size(), d.size()});
    ndjson += d + "\n";
  }
  const size_t documents = lines.size();
  JsonLimitsStruct limits{1ull << 30, 1ull << 30, 1u << 24, 1ull << 30, 128};
  void* session = td_json_session_create(&limits);
  if (!session) fail(1);
  JsonResult borrowed{};
  auto line = [&](const char* what, double ns_per_round) {
    double per_doc = ns_per_round / documents;
    std::printf("ndjson %8zu B | %-58s %8.0f ns/doc %8.0f MB/s\n", ndjson.size(), what, per_doc,
                ndjson.size() / ns_per_round * 1000.0);
  };
  const int reps = 200;
  line("N0 per-line session_decode + reset", best_ns([&] {
    for (auto [at, length] : lines) {
      if (td_json_session_decode(session, reinterpret_cast<const uint8_t*>(ndjson.data() + at), length, 0, nullptr, 0,
                                 &borrowed) != TD_JSON_OK)
        fail(2);
      sink += borrowed.lexical_count;
      td_json_session_reset(session);
    }
  }, reps));
  auto drain = [&](void* stream) {
    size_t n = 0;
    int32_t status;
    while ((status = td_json_stream_next(stream, &borrowed)) == TD_JSON_OK) {
      sink += borrowed.lexical_count;
      ++n;
    }
    if (status != TD_JSON_STREAM_EMPTY) fail(3, status);
    return n;
  };
  auto run = [&](uint64_t batch, size_t chunk, bool fresh) {
    static void* kept = nullptr;
    void* stream = fresh || !kept ? td_json_stream_create(session, batch, batch) : kept;
    if (!stream) fail(4);
    size_t at = 0, n = 0;
    while (at < ndjson.size()) {
      size_t length = std::min(chunk, ndjson.size() - at);
      at += td_json_stream_feed(stream, reinterpret_cast<const uint8_t*>(ndjson.data() + at), length);
      n += drain(stream);
    }
    if (n != documents) fail(5, long(n));
    if (fresh) td_json_stream_release(stream);
    else kept = stream;  // streams are never finished here, so one stream carries on across rounds
  };
  line("N1 stream: one feed, next() per document (batch 1 MiB)", best_ns([&] { run(1 << 20, ndjson.size(), false); }, reps));
  line("N2 stream: 64 KiB feeds (batch 1 MiB)", best_ns([&] { run(1 << 20, 65536, true); }, reps));
  line("N3 stream: 64 KiB feeds (batch and max document 64 KiB)", best_ns([&] { run(65536, 65536, true); }, reps));
  line("N4 N1 + stream create/release per round", best_ns([&] { run(1 << 20, ndjson.size(), true); }, reps));
  simdjson::padded_string padded(ndjson);
  simdjson::ondemand::parser parser;
  line("I0 iterate_many(1 MiB) + source() per document, no decode", best_ns([&] {
    auto stream = parser.iterate_many(padded, 1 << 20).value();
    size_t n = 0;
    for (auto it = stream.begin(); it != stream.end(); ++it) {
      sink += it.source().size();
      ++n;
    }
    if (n != documents) fail(6, long(n));
  }, reps));
  // Steady state of the streamed path: the last document's index allocations.
  void* stream = td_json_stream_create(session, 1 << 20, 1 << 20);
  for (int round = 0; round < 3; round++) {
    td_json_stream_feed(stream, reinterpret_cast<const uint8_t*>(ndjson.data()), ndjson.size());
    drain(stream);
  }
  std::printf("ndjson %8zu B | steady state: index_allocations=%llu\n", ndjson.size(),
              (unsigned long long) borrowed.index_allocations);
  td_json_stream_release(stream);
  td_json_session_release(session);
  return 0;
}
