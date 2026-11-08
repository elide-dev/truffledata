#include "json/native/session.h"
#include "tape/native/testing.h"
#include <array>
#include <chrono>
#include <cstdio>
using namespace truffledata;
volatile uint64_t text_observed;
double measure(bool fused, unsigned repetitions, unsigned records) {
  json::session session({}, {});
  std::array<std::string, 32> inputs;
  size_t max_length = 0;
  for (unsigned i = 0; i < inputs.size(); ++i) {
    if (records == 1) inputs[i] = tape::testing::fixture(i);
    else {
      inputs[i] = "[";
      for (unsigned j = 0; j < records; ++j) {
        if (j) inputs[i] += ',';
        inputs[i] += tape::testing::fixture(i + j);
      }
      inputs[i] += ']';
    }
    max_length = std::max(max_length, inputs[i].size());
  }
  std::vector<uint8_t> storage(max_length * 2 + 16);
  auto* out = storage.data();
  const size_t capacity = storage.size();
  auto consume = [&] {
    for (const auto& input : inputs) {
      auto d = session.decode(input.data(), input.size(), json::session::encoding::utf8, false, false,
                              fused ? out : nullptr, capacity);
      const uint64_t* table;
      auto used = session.write_text(out, capacity, table);
      if (used == tape::text_too_small) throw std::runtime_error("text probe capacity");
      text_observed = used ^ table[d.tape->word_count - 1];
    }
  };
  for (unsigned warm = 0; warm < std::max(2u, 2000 / records); ++warm) consume();
  auto start = std::chrono::steady_clock::now();
  for (unsigned i = 0; i < repetitions; ++i) consume();
  return std::chrono::duration<double, std::nano>(std::chrono::steady_clock::now() - start).count()
      / (repetitions * inputs.size());
}
int main() {
  for (unsigned records : {1u, 20u, 1000u}) for (unsigned round = 0; round < 7; ++round) {
    double before, after;
    if (round % 2) { after = measure(true, 10000 / records, records); before = measure(false, 10000 / records, records); }
    else { before = measure(false, 10000 / records, records); after = measure(true, 10000 / records, records); }
    std::printf("text records%u round%u postpass %.2fns fused %.2fns ratio %.3f\n", records, round, before, after, before / after);
  }
}
