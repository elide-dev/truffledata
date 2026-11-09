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
    typed_observed = result.slot(0).bits ^ result.size() ^ result.record_count();
  }
  return std::chrono::duration<double, std::nano>(std::chrono::steady_clock::now() - start).count() / (repetitions * inputs.size());
}
int flat() {
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
  return 0;
}

scalar_field rec(std::string name, uint32_t child, bool repeated = false) {
  scalar_field f{std::move(name), scalar_kind::record}; f.child = child; f.repeated = repeated; return f;
}
std::string point(unsigned n, bool escaped, bool reverse) {
  auto x = std::string(escaped ? "\"\\u0078\":" : "\"x\":") + std::to_string(n);
  auto d = "\"d\":" + std::to_string(n) + ".5";
  auto tag = std::string("\"tag\":") + (escaped ? "\"\\u4e2d\\n\"" : "\"ab\"");
  auto meta = std::string("\"meta\":{\"active\":") + (n % 2 ? "false}" : "true}");
  return reverse ? "{" + meta + "," + tag + "," + d + "," + x + "}"
      : "{" + x + "," + d + "," + tag + "," + meta + "}";
}
void nested(unsigned mode, const char* name) {
  scalar_field values{"values", scalar_kind::int64}; values.repeated = true;
  typed_plan plan(std::vector<std::vector<scalar_field>>{
      {{"id", scalar_kind::int64}, rec("head", 1), rec("items", 1, true), values},
      {{"x", scalar_kind::int64}, {"d", scalar_kind::float64}, {"tag", scalar_kind::string}, rec("meta", 2)},
      {{"active", scalar_kind::boolean}}});
  std::array<std::string,32> inputs;
  for (unsigned i = 0; i < inputs.size(); ++i) {
    unsigned count = mode == 0 ? 0 : mode == 1 ? 16 : mode == 2 ? 4 : i % 9;
    bool escaped = mode >= 2 && i % 2;
    std::string items = "[", values = "[";
    for (unsigned j = 0; j < count; ++j) {
      if (j) { items += ','; values += ','; }
      items += point(1000 + i * 17 + j, escaped, mode >= 2 && j % 2);
      values += std::to_string(int(j) - int(i));
    }
    items += ']'; values += ']';
    auto head = "\"head\":" + point(100 + i, escaped, mode >= 2);
    inputs[i] = mode >= 2 && i % 2
        ? "{\"values\":" + values + ",\"items\":" + items + "," + head + ",\"id\":" + std::to_string(i) + "}"
        : "{\"id\":" + std::to_string(i) + "," + head + ",\"items\":" + items + ",\"values\":" + values + "}";
    typed_document actual(plan, inputs[i]); testing::ondemand_typed_document expected(plan, inputs[i]);
    if (actual.size() != expected.size() || actual.record_count() != expected.record_count())
      throw std::runtime_error("nested shape mismatch");
    auto text = [](const uint8_t* p, size_t n) { return n ? std::string_view(reinterpret_cast<const char*>(p), n) : std::string_view(); };
    for (size_t j = 0; j < actual.size(); ++j) {
      auto& a = actual.slot(j); auto& b = expected.slot(j);
      if (a.bits != b.bits || text(a.text, a.text_length) != text(b.text, b.text_length)
          || text(a.lexical, a.lexical_length) != text(b.lexical, b.lexical_length))
        throw std::runtime_error("nested slot mismatch");
    }
    for (size_t j = 0; j < actual.record_count(); ++j) {
      auto& a = actual.record(j); auto& b = expected.record(j);
      if (a.present != b.present || a.nulls != b.nulls || a.plan != b.plan || a.slots != b.slots)
        throw std::runtime_error("nested record mismatch");
    }
    if (i == 0 || i == 1) {
      auto a = actual.allocations(), b = expected.allocations();
      std::printf("%s input%u bytes%zu slots%zu records%zu old_index%zu new_index%zu old_peak%zu new_peak%zu old_allocs%zu new_allocs%zu\n",
          name, i, inputs[i].size(), actual.size(), actual.record_count(), b.index_retained_bytes, a.index_retained_bytes,
          b.index_peak_bytes, a.index_peak_bytes, b.index_allocations, a.index_allocations);
    }
  }
  (void)measure<typed_document>(plan, inputs, 500);
  (void)measure<testing::ondemand_typed_document>(plan, inputs, 500);
  for (unsigned round = 0; round < 5; ++round) {
    double old, fresh;
    if (round % 2) { fresh = measure<typed_document>(plan, inputs, 2000); old = measure<testing::ondemand_typed_document>(plan, inputs, 2000); }
    else { old = measure<testing::ondemand_typed_document>(plan, inputs, 2000); fresh = measure<typed_document>(plan, inputs, 2000); }
    std::printf("%s round%u ondemand %.2fns structural %.2fns ratio %.3f\n", name, round, old, fresh, old / fresh);
  }
}
int main() {
  flat();
  nested(0, "nested"); nested(1, "repeated"); nested(2, "mixed"); nested(3, "churn");
}
