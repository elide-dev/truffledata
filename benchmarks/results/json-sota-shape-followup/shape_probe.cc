#include "json/native/decoder.h"
#include "baseline_probe.h"
#include "tape/native/testing.h"
#include <chrono>
#include <cstdio>
#include <string>
using namespace truffledata;
volatile uint64_t observed;
template<class Decoder> double measure(const std::string& input, bool cached, size_t reps) {
  simdjson::padded_string padded(input);
  simdjson::ondemand::parser parser;
  tape::index_memory memory(SIZE_MAX, std::pmr::new_delete_resource());
  tape::block_arena arena(&memory);
  tape::key_shape_cache cache;
  auto hint = json::hint_for(input.size(), {}, {});
  auto once = [&] {
    arena.reset(); arena.hint(hint.block);
    Decoder decoder(&arena, {});
    if constexpr (std::is_same_v<Decoder, json::tape_decoder<tape::ctape_writer>> ||
                  std::is_same_v<Decoder, json::tape_decoder<json::null_writer>>) {
      if (cached) decoder.shapes(&cache);
      decoder.start({padded.data(), padded.size()}, hint);
    } else decoder.start({padded.data(), padded.size()}, json_baseline::hint_for(input.size(), {}, {}));
    auto root = parser.iterate(padded).value(); decoder.decode(root);
    observed = observed + decoder.writer().value_count();
  };
  for (unsigned i = 0; i < 10; ++i) once();
  double best = 1e30;
  for (unsigned round = 0; round < 5; ++round) {
    auto before = std::chrono::steady_clock::now();
    for (size_t i = 0; i < reps; ++i) once();
    auto after = std::chrono::steady_clock::now();
    double ns = std::chrono::duration<double, std::nano>(after - before).count() / reps;
    best = std::min(best, ns);
  }
  return best;
}
int main() {
  std::printf("shape cache bytes=%zu\n", sizeof(tape::key_shape_cache));
  for (unsigned size : {1, 20, 1000, 20000}) {
    std::string input;
    if (size == 1) input = tape::testing::fixture(1);
    else { input = "["; for (unsigned i = 0; i < size; ++i) { if (i) input += ','; input += tape::testing::fixture(i); } input += ']'; }
    size_t reps = std::max<size_t>(5, 20000000 / input.size());
    double baseline = measure<json_baseline::tape_decoder<tape::ctape_writer>>(input, false, reps);
    double cached = measure<json::tape_decoder<tape::ctape_writer>>(input, true, reps);
    double validate_base = measure<json_baseline::tape_decoder<json_baseline::null_writer>>(input, false, reps);
    double validate_cached = measure<json::tape_decoder<json::null_writer>>(input, true, reps);
    std::printf("%zu bytes baseline %.0fns cached %.0fns ratio %.3f validate-baseline %.0fns cached %.0fns ratio %.3f\n",
        input.size(), baseline, cached, baseline/cached, validate_base, validate_cached, validate_base/validate_cached);
  }
}
