// Native producer probe for the number kernels (WS8; manual, never a gate): stage 1 + stage 2
// into the structural tape writer, the builtin number parser against each kernel this CPU runs,
// in alternating order on one thread. Fixtures: the mixed comparison document as small (1),
// medium (20), big (1,000) and huge (20,000) element arrays (isa_probe/stage2_probe), and the
// canada-style numeric fixture (the 32 JsonFixture.numericDocument features as one array).
// These are native producer timings, not SVM end-to-end results.
#include "json/native/number_kernel.h"
#include "json/native/stage2.h"
#include "json/native/decoder.h"
#include "tape/native/testing.h"
#include <algorithm>
#include <chrono>
#include <cstdio>
#include <fstream>
#include <string>
#include <vector>
using namespace truffledata;
namespace kernel = truffledata::json::number_kernel;
volatile uint64_t observed;

std::string canada(double value) { char b[64]; std::snprintf(b, sizeof b, "%.17g", value); return b; }
std::string numeric_fixture() {
  std::string out = "[";
  for (int i = 0; i < 32; ++i) {
    int points = 160 + 4 * (i % 8);
    uint64_t state = 0x9E3779B97F4A7C15ull ^ uint64_t(i + 1);
    std::vector<int64_t> micros;
    for (int p = 0; p < points - 1; ++p) {
      state = state * 6364136223846793005ull + 1442695040888963407ull;
      micros.push_back(-141000000 + int64_t((state >> 11) % 89000000));
      state = state * 6364136223846793005ull + 1442695040888963407ull;
      micros.push_back(41000000 + int64_t((state >> 11) % 43000000));
    }
    micros.push_back(micros[0]); micros.push_back(micros[1]);
    std::string ring;
    for (size_t p = 0; p < micros.size(); p += 2)
      ring += (p ? ",[" : "[") + canada(double(micros[p]) / 1e6) + "," + canada(double(micros[p + 1]) / 1e6) + "]";
    out += (i ? "," : "") + std::string("{\"type\":\"Feature\",\"properties\":{\"name\":\"ring-") + std::to_string(i)
           + "\",\"id\":" + std::to_string(i) + "},\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[" + ring + "]]}}";
  }
  return out + "]";
}

double measure(const std::string& source, unsigned repetitions) {
  simdjson::padded_string input(source); json::stage1_index parser;
  tape::index_memory memory(SIZE_MAX, std::pmr::new_delete_resource()); tape::block_arena arena(&memory);
  tape::key_shape_cache cache; tape::structural_writer writer(&arena, {});
  auto once = [&] {
    writer.clear(); writer.shapes(&cache); writer.input({input.data(), input.size()});
    parser.run(reinterpret_cast<const uint8_t*>(input.data()), input.size());
    writer.reserve_structural(parser.size(), input.size());
    json::stage2_walk<tape::structural_writer> walk(reinterpret_cast<const uint8_t*>(input.data()), input.size(),
                                                     parser.indexes(), parser.size(), writer);
    walk.run(); observed = writer.value_count();
  };
  for (unsigned i = 0; i < 10; ++i) once();
  auto start = std::chrono::steady_clock::now();
  for (unsigned i = 0; i < repetitions; ++i) once();
  return std::chrono::duration<double, std::nano>(std::chrono::steady_clock::now() - start).count() / repetitions;
}

int main() {
  const char* names[] = {"builtin", "westmere", "haswell"};
  kernel::selected.store(kernel::unknown);
  kernel::tier active = kernel::active();
  std::printf("simdjson active %s; kernel tier %s\n", simdjson::get_active_implementation()->name().c_str(),
              names[uint8_t(active)]);
#if defined(__linux__)
  std::ifstream cpuinfo("/proc/cpuinfo");
  for (std::string line; std::getline(cpuinfo, line);)
    if (line.rfind("model name", 0) == 0 || line.rfind("flags", 0) == 0) { std::printf("cpu %s\n", line.c_str()); if (line.rfind("flags", 0) == 0) break; }
#endif
  if (active == kernel::tier::builtin) { std::puts("no SIMD kernel on this CPU; nothing to compare"); return 0; }
  std::vector<std::pair<const char*, std::string>> fixtures;
  for (auto [name, count] : {std::pair{"small", 1u}, {"medium", 20u}, {"big", 1000u}, {"huge", 20000u}}) {
    std::string source = "[";
    for (unsigned i = 0; i < count; ++i) { if (i) source += ','; source += tape::testing::fixture(int(i)); }
    fixtures.push_back({name, source + "]"});
  }
  fixtures.push_back({"numeric", numeric_fixture()});
  for (const auto& [name, source] : fixtures) {
    unsigned repetitions = std::max<unsigned>(5, unsigned(40000000 / source.size()));
    std::vector<double> ratios;
    for (unsigned round = 0; round < 7; ++round) {
      double times[2];
      for (unsigned n = 0; n < 2; ++n) {
        unsigned which = round % 2 ? 1 - n : n;
        kernel::selected.store(which ? uint8_t(active) : uint8_t(kernel::tier::builtin));
        times[which] = measure(source, repetitions);
      }
      ratios.push_back(times[0] / times[1]);
      std::printf("NUMBER %s bytes %zu round %u builtin %.0f ns %s %.0f ns builtin/kernel %.4f\n", name, source.size(),
                  round, times[0], names[uint8_t(active)], times[1], times[0] / times[1]);
    }
    std::sort(ratios.begin(), ratios.end());
    std::printf("NUMBER %s median builtin/kernel %.4f (range %.4f-%.4f)\n", name, ratios[ratios.size() / 2],
                ratios.front(), ratios.back());
  }
  kernel::selected.store(kernel::unknown);
}
