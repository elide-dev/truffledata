#include "json/native/decoder.h"
#include "json/native/testing/stage2_baseline_probe.h"
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
template<class Writer> double structural(const std::string& input, size_t reps) {
  simdjson::padded_string padded(input);
  json::stage1_index parser;
  tape::index_memory memory(SIZE_MAX, std::pmr::new_delete_resource());
  tape::block_arena arena(&memory);
  tape::key_shape_cache cache;
  Writer writer(&arena, {});
  auto once = [&] {
    writer.clear(); writer.shapes(&cache); writer.input({padded.data(), padded.size()});
    parser.run(reinterpret_cast<const uint8_t*>(padded.data()), padded.size());
    writer.reserve_structural(parser.size(), padded.size());
    json::stage2_walk<Writer> walk(reinterpret_cast<const uint8_t*>(padded.data()), padded.size(), parser.indexes(), parser.size(), writer);
    walk.run(); observed = observed + writer.value_count();
  };
  for (unsigned i = 0; i < 10; ++i) once();
  size_t before_allocations = memory.allocations();
  double best = 1e30;
  for (unsigned round = 0; round < 5; ++round) {
    auto before = std::chrono::steady_clock::now();
    for (size_t i = 0; i < reps; ++i) once();
    auto after = std::chrono::steady_clock::now();
    best = std::min(best, std::chrono::duration<double, std::nano>(after - before).count() / reps);
  }
  if (memory.allocations() != before_allocations) throw std::runtime_error("steady state allocated");
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
    double cached = structural<tape::structural_writer>(input, reps);
    double validate_base = measure<json_baseline::tape_decoder<json_baseline::null_writer>>(input, false, reps);
    double validate_cached = structural<json::structural_null_writer>(input, reps);
    std::printf("%zu bytes baseline %.0fns cached %.0fns ratio %.3f validate-baseline %.0fns cached %.0fns ratio %.3f\n",
        input.size(), baseline, cached, baseline/cached, validate_base, validate_cached, validate_base/validate_cached);
  }
}
