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
int main() {
  measured tiers[] = {measure<json::stage2_walk>, nullptr, nullptr};
  const char* names[] = {"baseline","avx2","avx512"};
  parity<json::stage2_walk>(names[0]);
#if defined(__x86_64__)
  if (__builtin_cpu_supports("avx2") && __builtin_cpu_supports("bmi") && __builtin_cpu_supports("bmi2") && __builtin_cpu_supports("lzcnt") && __builtin_cpu_supports("popcnt")) {
    tiers[1]=measure<json_avx2::stage2_walk>; parity<json_avx2::stage2_walk>(names[1]);
  }
  if (__builtin_cpu_supports("avx512bw") && __builtin_cpu_supports("avx512vl") && __builtin_cpu_supports("bmi2")) {
    tiers[2]=measure<json_avx512::stage2_walk>; parity<json_avx512::stage2_walk>(names[2]);
  }
#endif
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
