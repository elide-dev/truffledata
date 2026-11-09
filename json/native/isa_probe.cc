#include "json/native/testing/isa_variants.h"
#include "tape/native/testing.h"
#include <chrono>
#include <cstdio>
#include <string>
#include <vector>
using namespace truffledata;
volatile uint64_t isa_observed;
struct image { std::vector<uint64_t> words, directories; std::vector<uint8_t> pool; bool valid = false; };
template<template<class> class Walk> image parse(const std::string& source) {
  image result;
  try {
    simdjson::padded_string input(source); json::stage1_index parser;
    parser.run(reinterpret_cast<const uint8_t*>(input.data()), input.size());
    std::pmr::monotonic_buffer_resource memory; tape::structural_writer writer(&memory, {});
    writer.input({input.data(), input.size()}); writer.reserve_structural(parser.size(), input.size());
    Walk<tape::structural_writer> walk(reinterpret_cast<const uint8_t*>(input.data()), input.size(), parser.indexes(), parser.size(), writer);
    walk.run(); auto view = writer.view();
    result.words.assign(view.words, view.words + view.word_count);
    result.directories.assign(view.directory, view.directory + view.directory_count);
    result.pool.assign(view.pool, view.pool + view.pool_bytes); result.valid = true;
  } catch (const std::exception&) {}
  return result;
}
template<template<class> class Walk> void parity(const char* name) {
  uint64_t cases = 0;
  auto compare = [&](const std::string& source) {
    auto a = parse<json::stage2_walk>(source), b = parse<Walk>(source); ++cases;
    if (a.valid != b.valid || a.words != b.words || a.directories != b.directories || a.pool != b.pool)
      throw std::runtime_error(std::string("ISA parity ") + name + ":" + source);
  };
  for (auto& source : tape::testing::corpus()) compare(source);
  for (unsigned n = 0; n < 160; ++n) {
    std::string source = "{\"s\":\"" + std::string(n, 'x') + "\\u4e2d\\ud83d\\ude00" + std::string(160-n, 'y') + "\"}";
    compare(source);
    for (size_t i = 0; i < source.size(); ++i) compare(source.substr(0,i));
    compare("{\"s\":\"" + std::string(n, 'x') + "\\q\"}");
  }
  std::printf("ISA %s exact tape/malformed parity: %llu cases\n", name, static_cast<unsigned long long>(cases));
}
template<template<class> class Walk> double measure(const std::string& source, unsigned repetitions) {
  simdjson::padded_string input(source); json::stage1_index parser;
  tape::index_memory memory(SIZE_MAX, std::pmr::new_delete_resource()); tape::block_arena arena(&memory);
  tape::key_shape_cache cache; tape::structural_writer writer(&arena, {});
  auto once = [&] {
    writer.clear(); writer.shapes(&cache); writer.input({input.data(),input.size()});
    parser.run(reinterpret_cast<const uint8_t*>(input.data()),input.size());
    writer.reserve_structural(parser.size(),input.size());
    Walk<tape::structural_writer> walk(reinterpret_cast<const uint8_t*>(input.data()),input.size(),parser.indexes(),parser.size(),writer);
    walk.run(); isa_observed = writer.value_count();
  };
  for (unsigned i=0;i<10;++i) once();
  size_t allocations = memory.allocations(); auto start=std::chrono::steady_clock::now();
  for (unsigned i=0;i<repetitions;++i) once();
  double ns=std::chrono::duration<double,std::nano>(std::chrono::steady_clock::now()-start).count()/repetitions;
  if (memory.allocations()!=allocations) throw std::runtime_error("ISA measurement grew storage");
  return ns;
}
using measured = double (*)(const std::string&,unsigned);
// canada.json-style numeric document: arrays of coordinate pairs with 15-17 significant digits,
// so nearly every token takes the double path (plan WS8 numeric probe).
static std::string numeric_fixture(unsigned pairs) {
  std::string source = "[";
  uint64_t state = 0x9E3779B97F4A7C15ull;
  char buffer[64];
  for (unsigned i = 0; i < pairs; ++i) {
    if (i) source += ',';
    state = state * 6364136223846793005ull + 1442695040888963407ull;
    double x = -180.0 + double(state >> 11) / double(1ull << 53) * 360.0;
    state = state * 6364136223846793005ull + 1442695040888963407ull;
    double y = -90.0 + double(state >> 11) / double(1ull << 53) * 180.0;
    std::snprintf(buffer, sizeof buffer, "[%.15f,%.15f]", x, y);
    source += buffer;
  }
  return source + "]";
}
// Exact-tape parity on the numeric fixture and the boundary numbers the walk's exact rules
// cover (18-digit integers, negative zero, exponents, overflow to DECIMAL fallback).
template<template<class> class Walk> void numeric_parity(const char* name) {
  const char* cases[] = {"[0.1,-0.0,1e308,1e309,-1e-324,123456789012345678,1234567890123456789,"
    "18446744073709551615,18446744073709551616,0.30000000000000004,1E+2,-65.613616999999977,2.2250738585072014e-308]",
    "[1.7976931348623157e308,4.9e-324,100000000000000000000000,1e-400,-1.5e3,9007199254740993]"};
  for (const char* source : cases) {
    auto a = parse<json::stage2_walk>(source), b = parse<Walk>(source);
    if (a.valid != b.valid || a.words != b.words || a.directories != b.directories || a.pool != b.pool)
      throw std::runtime_error(std::string("numeric parity ") + name + ":" + source);
  }
  for (unsigned pairs : {1u, 37u, 1000u}) {
    std::string source = numeric_fixture(pairs);
    auto a = parse<json::stage2_walk>(source), b = parse<Walk>(source);
    if (!a.valid || a.valid != b.valid || a.words != b.words || a.pool != b.pool)
      throw std::runtime_error(std::string("numeric fixture parity ") + name);
  }
  std::printf("ISA %s numeric parity ok\n", name);
}
int main() {
  measured tiers[] = {measure<json::stage2_walk>, nullptr, nullptr, nullptr, nullptr};
  const char* names[] = {"baseline","avx2","avx512","sse42","avx2-scalar-numbers"};
  parity<json::stage2_walk>(names[0]);
#if defined(__x86_64__)
  if (__builtin_cpu_supports("avx2") && __builtin_cpu_supports("bmi") && __builtin_cpu_supports("bmi2") && __builtin_cpu_supports("lzcnt") && __builtin_cpu_supports("popcnt")) {
    tiers[1]=measure<json_avx2::stage2_walk>; parity<json_avx2::stage2_walk>(names[1]); numeric_parity<json_avx2::stage2_walk>(names[1]);
    tiers[4]=measure<json_avx2_scalar_numbers::stage2_walk>; parity<json_avx2_scalar_numbers::stage2_walk>(names[4]);
  }
  if (__builtin_cpu_supports("avx512bw") && __builtin_cpu_supports("avx512vl") && __builtin_cpu_supports("bmi2")) {
    tiers[2]=measure<json_avx512::stage2_walk>; parity<json_avx512::stage2_walk>(names[2]); numeric_parity<json_avx512::stage2_walk>(names[2]);
  }
  if (__builtin_cpu_supports("sse4.2") && __builtin_cpu_supports("pclmul") && __builtin_cpu_supports("popcnt")) {
    tiers[3]=measure<json_sse42::stage2_walk>; parity<json_sse42::stage2_walk>(names[3]); numeric_parity<json_sse42::stage2_walk>(names[3]);
  }
#endif
  // Numeric fixture first (the WS8 question), then the mixed big/huge fixtures as before.
  for (unsigned pairs : {2000u, 50000u}) {
    std::string source = numeric_fixture(pairs);
    unsigned repetitions = std::max<unsigned>(5, 20000000 / source.size());
    for (unsigned round = 0; round < 5; ++round) {
      double times[5]{};
      for (unsigned n = 0; n < 5; ++n) { unsigned tier = round % 2 ? 4 - n : n; if (tiers[tier]) times[tier] = tiers[tier](source, repetitions); }
      std::printf("ISA numeric bytes%zu round%u baseline %.0fns sse42-numbers %.0fns ratio %.3f avx2-numbers %.0fns ratio %.3f avx2-scalar-numbers %.0fns ratio %.3f avx512-numbers %.0fns ratio %.3f\n",
        source.size(), round, times[0], times[3], times[3] ? times[0] / times[3] : 0, times[1], times[1] ? times[0] / times[1] : 0,
        times[4], times[4] ? times[0] / times[4] : 0, times[2], times[2] ? times[0] / times[2] : 0);
    }
  }
  for (unsigned count : {1000u,20000u}) {
    std::string source="[";
    for (unsigned i=0;i<count;++i) {if(i)source+=',';source+=tape::testing::fixture(i);} source+=']';
    unsigned repetitions = std::max<unsigned>(5,20000000/source.size());
    for (unsigned round=0;round<5;++round) {
      double times[3]{};
      for(unsigned n=0;n<3;++n) {unsigned tier=round%2 ? 2-n : n;if(tiers[tier])times[tier]=tiers[tier](source,repetitions);}
      std::printf("ISA bytes%zu round%u baseline %.0fns avx2 %.0fns ratio %.3f avx512 %.0fns ratio %.3f\n",source.size(),round,times[0],times[1],times[1]?times[0]/times[1]:0,times[2],times[2]&&times[1]?times[1]/times[2]:0);
    }
  }
}
