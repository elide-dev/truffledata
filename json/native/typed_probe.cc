#include "json/native/testing/typed_ondemand.h"
#include <array>
#include <chrono>
#include <cstdio>
using namespace truffledata::json;
volatile uint64_t typed_observed;
template<class Document> double measure(const typed_plan& plan, const std::array<std::string,32>& inputs, unsigned repetitions) {
  auto start = std::chrono::steady_clock::now();
  for (unsigned r = 0; r < repetitions; ++r) for (const auto& input : inputs) {
    Document result(plan, input);
    typed_observed = result.slot(0).bits ^ result.slot(1).bits ^ result.slot(2).bits;
  }
  return std::chrono::duration<double, std::nano>(std::chrono::steady_clock::now() - start).count() / (repetitions * inputs.size());
}
int main() {
  typed_plan plan({{"n",scalar_kind::int64},{"d",scalar_kind::float64},{"b",scalar_kind::boolean}});
  std::array<std::string,32> inputs;
  for (unsigned i=0; i<inputs.size(); ++i) {
    auto n="\"n\":"+std::to_string(1000+7*i), d="\"d\":"+std::to_string(i)+".5", b=std::string("\"b\":")+(i%2?"false":"true");
    inputs[i] = i%2 ? "{"+b+","+n+","+d+"}" : "{"+n+","+d+","+b+"}";
    typed_document actual(plan, inputs[i]); testing::ondemand_typed_document expected(plan, inputs[i]);
    if (actual.present()!=expected.present() || actual.nulls()!=expected.nulls()) throw std::runtime_error("typed masks");
    for (size_t k=0; k<3; ++k) if (actual.slot(k).bits!=expected.slot(k).bits) throw std::runtime_error("typed values");
  }
  (void)measure<typed_document>(plan, inputs, 2000);
  (void)measure<testing::ondemand_typed_document>(plan, inputs, 2000);
  for (unsigned round=0; round<5; ++round) {
    double old, fresh;
    if (round%2) { fresh=measure<typed_document>(plan, inputs, 10000); old=measure<testing::ondemand_typed_document>(plan, inputs, 10000); }
    else { old=measure<testing::ondemand_typed_document>(plan, inputs, 10000); fresh=measure<typed_document>(plan, inputs, 10000); }
    std::printf("typed round%u ondemand %.2fns structural %.2fns ratio %.3f\n",round,old,fresh,old/fresh);
  }
}
