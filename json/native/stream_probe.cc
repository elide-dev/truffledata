// Paired production stream probe. The test-only baseline freezes the previous session/stream path.
#include "json/native/stream.h"
#include "json/native/testing/ondemand_stream.h"
#include "tape/native/testing.h"
#include <chrono>
#include <cstdio>
#include <string>

using namespace truffledata;
volatile uint64_t stream_observed;
template<class Session, class Stream> uint64_t consume(const std::string& input, bool exact) {
  Session session({}, {});
  Stream stream(session, 1 << 20, 1 << 20);
  typename Session::document_view view{};
  size_t at = 0, documents = 0;
  uint64_t digest = 1469598103934665603ull;
  auto mix = [&](uint64_t value) { digest = (digest ^ value) * 1099511628211ull; };
  for (;;) {
    at += stream.feed(reinterpret_cast<const uint8_t*>(input.data() + at), input.size() - at);
    if (at == input.size()) stream.finish();
    int status;
    while ((status = stream.next(view)) == json::status_ok) {
      ++documents;
      const auto& tape = *view.tape;
      mix(tape.word_count); mix(tape.pool_bytes); mix(tape.input_bytes);
      if (exact) {
        if (tape::ctape::validate(tape)) throw std::runtime_error("invalid stream tape");
        for (uint64_t i = 0; i < tape.word_count; ++i) mix(tape.words[i]);
        for (uint64_t i = 0; i < tape.pool_bytes; ++i) mix(tape.pool[i]);
        for (uint64_t i = 0; i < tape.input_bytes; ++i) mix(tape.input[i]);
      }
    }
    if (status != json::status_stream_empty) throw std::runtime_error("stream failed");
    if (stream.finished()) break;
  }
  mix(documents);
  return digest;
}
template<class Session, class Stream> double measure(const std::string& input, unsigned reps) {
  auto start = std::chrono::steady_clock::now();
  for (unsigned i = 0; i < reps; ++i) stream_observed = consume<Session, Stream>(input, false);
  return std::chrono::duration<double, std::nano>(std::chrono::steady_clock::now() - start).count() / reps;
}
int main() {
  std::string input;
  for (unsigned i = 0; i < 20000; ++i) { input += tape::testing::fixture(i); input += '\n'; }
  auto baseline = consume<json_stream_baseline::session, json_stream_baseline::stream>(input, true);
  auto structural = consume<json::session, json::stream>(input, true);
  if (baseline != structural) throw std::runtime_error("stream tapes differ from On-Demand oracle");
  for (unsigned round = 0; round < 5; ++round) {
    double old_ns, new_ns;
    if (round % 2) {
      new_ns = measure<json::session, json::stream>(input, 10);
      old_ns = measure<json_stream_baseline::session, json_stream_baseline::stream>(input, 10);
    } else {
      old_ns = measure<json_stream_baseline::session, json_stream_baseline::stream>(input, 10);
      new_ns = measure<json::session, json::stream>(input, 10);
    }
    std::printf("round%u bytes%zu baseline %.0fns structural %.0fns ratio %.3f GB/s %.3f\n",
                round, input.size(), old_ns, new_ns, old_ns / new_ns, input.size() / new_ns);
  }
}
