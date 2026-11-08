// Test-only price-of-duplicate-check comparator: grammar, numbers, text, limits and compact
// storage are identical. Only key announcements/templates/duplicate rejection are omitted.
#include "json/native/decoder.h"
#include "tape/native/testing.h"
#include <chrono>
#include <cstdio>
#include <string>
#include <vector>
using namespace truffledata;
namespace {
class no_duplicates {
  tape::structural_writer writer_;
 public:
  static constexpr bool retains_values = true;
  no_duplicates(std::pmr::memory_resource* memory, myna::value_limits limits) : writer_(memory, limits) {}
  void clear() { writer_.clear(); }
  void shapes(tape::key_shape_cache*) { writer_.shapes(nullptr); }
  void input(std::string_view in) { writer_.input(in); }
  void directories(bool enabled) { if (enabled) throw std::logic_error("S3x must be directoryless"); writer_.directories(false); }
  void reserve_structural(size_t n, size_t bytes) { writer_.reserve_structural(n, bytes); }
  size_t begin(uint32_t kind, uint32_t depth, uint32_t offset) { return writer_.begin(kind, depth, offset); }
  void end(size_t index, uint64_t count) {
    if (tape::ctape::kind(writer_.words()[index]) == tape::ctape::kind_object) {
      // Deliberately asserts distinctness without checking, only in this diagnostic executable.
      writer_.end_distinct(index, count);
    } else writer_.end(index, count);
  }
  void key_text(size_t, const uint8_t*, uint32_t, bool = false) {}
  uint32_t cached_key_ref(size_t, uint32_t, uint32_t) { return UINT32_MAX; }
  void string_ref(tape::ctape::span s, bool ascii, bool key) { writer_.string_ref(s, ascii, key); }
  void string_owned(uint32_t offset, uint32_t length, tape::ctape::span raw, bool ascii, bool key) {
    writer_.string_owned(offset, length, raw, ascii, key);
  }
  uint8_t* pool_reserve(size_t bytes) { return writer_.pool_reserve(bytes); }
  void pool_commit(size_t bytes) { writer_.pool_commit(bytes); }
  size_t pool_size() const { return writer_.pool_size(); }
  const uint8_t* pool_data() const { return writer_.pool_data(); }
  void null() { writer_.null(); }
  void boolean(bool b) { writer_.boolean(b); }
  void parsed_number(uint32_t k, uint64_t bits, tape::ctape::span token) { writer_.parsed_number(k, bits, token); }
  void commit_number(tape::ctape::span token) { writer_.commit_number(token); }
  void rollback_number() { writer_.rollback_number(); }
  void decimal_ref(tape::ctape::span token) { writer_.decimal_ref(token); }
  uint64_t value_count() const { return writer_.value_count(); }
  td_tape_view view() const { return writer_.view(); }
};
volatile uint64_t duplicate_observed;
struct image { std::vector<uint64_t> words; std::vector<uint8_t> pool; };
template<class Writer> struct harness {
  tape::index_memory memory{SIZE_MAX, std::pmr::new_delete_resource()};
  tape::block_arena arena{&memory};
  tape::key_shape_cache cache;
  json::stage1_index index;
  Writer writer{&arena, {}};
  void decode(const simdjson::padded_string& input) {
    writer.clear(); writer.shapes(&cache); writer.directories(false); writer.input({input.data(), input.size()});
    index.run(reinterpret_cast<const uint8_t*>(input.data()), input.size());
    writer.reserve_structural(index.size(), input.size());
    json::stage2_walk<Writer> walk(reinterpret_cast<const uint8_t*>(input.data()), input.size(), index.indexes(), index.size(), writer);
    walk.run(); duplicate_observed = writer.value_count();
  }
  image copy(const simdjson::padded_string& input) {
    decode(input); auto v = writer.view();
    if (v.directory_count != 0) throw std::runtime_error("unexpected directory");
    image out{{v.words, v.words + v.word_count}, {}};
    if (v.pool_bytes) out.pool.assign(v.pool, v.pool + v.pool_bytes);
    return out;
  }
  double measure(const simdjson::padded_string& input, unsigned repetitions) {
    for (size_t i = 0, warm = std::max<size_t>(10, 100000000 / input.size()); i < warm; ++i) decode(input);
    size_t allocations = memory.allocations();
    auto start = std::chrono::steady_clock::now();
    for (unsigned i = 0; i < repetitions; ++i) decode(input);
    double ns = std::chrono::duration<double, std::nano>(std::chrono::steady_clock::now() - start).count() / repetitions;
    if (memory.allocations() != allocations) throw std::runtime_error("steady duplicate probe allocated");
    return ns;
  }
};
}
int main(int argc, char** argv) {
  harness<tape::structural_writer> checked;
  harness<no_duplicates> unchecked;
  for (const auto& source : tape::testing::corpus()) {
    simdjson::padded_string input{std::string_view(source)};
    auto a = checked.copy(input), b = unchecked.copy(input);
    if (a.words != b.words || a.pool != b.pool) throw std::runtime_error("duplicate-only comparator changed values");
  }
  for (auto source : {R"({"a":1,"a":2})", R"({"a":1,"\u0061":2})"}) {
    simdjson::padded_string input{std::string_view(source)};
    bool rejected = false;
    try { checked.decode(input); } catch (const tape::ctape::duplicate_key&) { rejected = true; }
    if (!rejected) throw std::runtime_error("checked writer accepted duplicate");
    unchecked.decode(input);
  }
  std::puts("S3n/S3x exact valid corpus: 200; duplicate discriminator: 2");
  if (argc == 2 && std::string_view(argv[1]) == "--check") return 0;
  for (unsigned records : {1u, 20u, 1000u, 20000u}) {
    std::string source;
    if (records == 1) source = tape::testing::fixture(1);
    else {
      source = "[";
      for (unsigned i = 0; i < records; ++i) { if (i) source += ','; source += tape::testing::fixture(i); }
      source += ']';
    }
    simdjson::padded_string input{std::string_view(source)};
    unsigned reps = std::max<size_t>(5, 20000000 / input.size());
    for (unsigned round = 0; round < 5; ++round) {
      double n, x;
      if (round % 2) { x = unchecked.measure(input, reps); n = checked.measure(input, reps); }
      else { n = checked.measure(input, reps); x = unchecked.measure(input, reps); }
      std::printf("bytes%zu round%u checked %.2fns no-duplicates %.2fns S3n/S3x %.3f\n", input.size(), round, n, x, x / n);
    }
  }
}
