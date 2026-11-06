#include "json/native/typed.h"
#include <iostream>
#include <memory>
#include <memory_resource>
#include <string>
#include <vector>
using namespace truffledata::json;
std::string_view slot_text(const scalar_slot& slot) {
  return {reinterpret_cast<const char*>(slot.text), slot.text_length};
}
std::string_view lexical(const scalar_slot& slot) {
  return {reinterpret_cast<const char*>(slot.lexical), slot.lexical_length};
}
void check(bool value) { if (!value) throw std::runtime_error("typed JSON check"); }
template<class F> void rejects(F operation) {
  bool rejected = false;
  try { operation(); } catch (const std::exception&) { rejected = true; }
  check(rejected);
}
scalar_field record_field(std::string name, uint32_t child, bool required = true,
                          bool nullable = false, bool repeated = false) {
  scalar_field field{std::move(name), scalar_kind::record, required, nullable};
  field.child = child;
  field.repeated = repeated;
  return field;
}
scalar_field repeated(scalar_field field, bool nullable = false) {
  field.repeated = true;
  field.nullable = nullable;
  return field;
}
uint64_t first_of(const scalar_slot& slot) { return slot.bits >> 32; }
uint64_t count_of(const scalar_slot& slot) { return slot.bits & UINT32_MAX; }
const scalar_slot& field_slot(const typed_document& d, size_t entry, size_t field) {
  return d.slot(d.record(entry).slots + field);
}

void nested_records() {
  // root {id, inner: record 1 (required, nullable), opt: record 1 (optional)}
  // record 1 {name, flag optional}
  scalar_field id{"id", scalar_kind::int64};
  typed_plan plan(std::vector<std::vector<scalar_field>>{
      {id, record_field("inner", 1, true, true), record_field("opt", 1, false)},
      {{"name", scalar_kind::string}, {"flag", scalar_kind::boolean, false}}});
  check(!plan.flat() && plan.record_count() == 2);
  typed_document d(plan, R"({"inner":{"flag":true,"name":"xA"},"id":5})");
  check(d.record_count() == 2);
  check(d.present() == 3 && d.nulls() == 0);
  check(d.record(0).present == 3 && d.record(0).slots == 0 && d.record(0).plan == 0);
  check(d.slot(0).bits == 5);
  check(d.slot(1).bits == 1 && d.slot(1).lexical == nullptr && d.slot(1).text == nullptr);
  check(d.slot(2).bits == 0); // missing optional record slot stays zero
  const auto& inner = d.record(1);
  check(inner.plan == 1 && inner.slots == 3 && inner.present == 3 && inner.nulls == 0);
  check(slot_text(field_slot(d, 1, 0)) == "xA");
  check(lexical(field_slot(d, 1, 0)) == R"("xA")");
  check(field_slot(d, 1, 1).bits == 1);
  rejects([&] { (void)d.record(2); });
  typed_document null_inner(plan, R"({"id":1,"inner":null})");
  check(null_inner.nulls() == 2 && null_inner.record_count() == 1);
  check(lexical(null_inner.slot(1)) == "null");
  typed_document both(plan, R"({"id":1,"inner":{"name":""},"opt":{"name":"o","flag":false}})");
  check(both.record_count() == 3 && both.slot(2).bits == 2);
  check(both.record(2).slots == 5 && slot_text(field_slot(both, 2, 0)) == "o");
  for (auto input : {
       R"({"id":1,"inner":{"name":"x","extra":1}})",        // unknown nested field
       R"({"id":1,"inner":{"name":"x","name":"y"}})",       // duplicate nested field
       R"({"id":1,"inner":{"flag":true}})",                 // missing nested required
       R"({"id":1,"inner":{"name":1}})",                    // nested type mismatch
       R"({"id":1,"inner":5})",                             // record type mismatch
       R"({"id":1,"inner":[]})",
       R"({"id":1,"inner":{"name":"x"},"inner":{"name":"y"}})",
       R"({"id":1,"inner":{"name":"x"},"opt":null})",       // opt is not nullable
       R"({"id":1})",                                       // missing required record
       R"({"id":1,"inner":{"name":"x"}} {})",               // trailing input
       R"({"id":1,"inner":{"name":"x",}})",
       R"({"id":1,"inner":{"name":"x"})"}) {
    rejects([&] { typed_document invalid(plan, input); });
  }
}

void repeated_scalars() {
  scalar_field small{"v", scalar_kind::int64};
  small.minimum = 0; small.maximum = 10;
  scalar_field words{"w", scalar_kind::string, false};
  words.length_unit = 2; words.maximum_length = 2;
  typed_plan plan(std::vector<scalar_field>{repeated(small), repeated(words, true)});
  check(plan.flat());
  typed_document d(plan, R"({"v":[1, 2,10],"w":["ab","中"]})");
  check(d.record_count() == 1 && d.present() == 3);
  check(count_of(d.slot(0)) == 3 && first_of(d.slot(0)) == 2);
  check(d.slot(2).bits == 1 && d.slot(3).bits == 2 && d.slot(4).bits == 10);
  check(lexical(d.slot(3)) == "2");
  check(count_of(d.slot(1)) == 2 && first_of(d.slot(1)) == 5);
  check(slot_text(d.slot(5)) == "ab" && slot_text(d.slot(6)) == "中");
  check(d.size() == 7);
  typed_document empty(plan, R"({"v":[],"w":[]})");
  check(empty.slot(0).bits == 0 && empty.slot(1).bits == 0 && empty.size() == 2);
  check(empty.present() == 3 && empty.nulls() == 0);
  typed_document null_words(plan, R"({"v":[0],"w":null})");
  check(null_words.nulls() == 2 && null_words.slot(1).bits == 0);
  for (auto input : {
       R"({"v":[1,11]})", R"({"v":[-1]})", R"({"v":[1,null]})", R"({"v":[1,"2"]})",
       R"({"v":[1.5]})", R"({"v":5})", R"({"v":{}})", R"({"v":null})", R"({"v":[1,]})",
       R"({"v":[[1]]})", R"({"v":[1],"w":["abc"]})", R"({"v":[1],"w":[null]})",
       R"({"v":[1],"v":[2]})", R"({"v":[1})", R"({"v":[1]} [])"}) {
    rejects([&] { typed_document invalid(plan, input); });
  }
}

void repeated_records() {
  // root {items: repeated record 1, n optional}
  // record 1 {k, tags: repeated string optional, sub: record 2 optional}; record 2 {z}
  typed_plan plan(std::vector<std::vector<scalar_field>>{
      {record_field("items", 1, true, false, true), {"n", scalar_kind::int64, false}},
      {{"k", scalar_kind::int64}, repeated({"tags", scalar_kind::string, false}),
       record_field("sub", 2, false)},
      {{"z", scalar_kind::boolean}}});
  typed_document d(plan,
      R"({"items":[{"k":1,"sub":{"z":true}},{"k":2,"tags":["a","b"]},{"k":3}],"n":9})");
  check(d.record_count() == 5);
  const auto& items = d.slot(0);
  check(count_of(items) == 3 && first_of(items) == 1);
  for (uint64_t i = 0; i < 3; ++i) {
    check(d.record(first_of(items) + i).plan == 1);
    check(field_slot(d, first_of(items) + i, 0).bits == i + 1);
  }
  // Element 0's nested record lands after the contiguous element entries.
  check(field_slot(d, 1, 2).bits == 4 && d.record(4).plan == 2);
  check(field_slot(d, 4, 0).bits == 1);
  check(d.record(1).present == 5 && d.record(2).present == 3 && d.record(3).present == 1);
  const auto& tags = field_slot(d, 2, 1);
  check(count_of(tags) == 2);
  check(slot_text(d.slot(first_of(tags))) == "a" && slot_text(d.slot(first_of(tags) + 1)) == "b");
  check(d.slot(1).bits == 9);
  typed_document empty(plan, R"({"items":[]})");
  check(empty.slot(0).bits == 0 && empty.record_count() == 1 && empty.present() == 1);
  for (auto input : {
       R"({"items":null})", R"({"items":[null]})", R"({"items":[1]})", R"({"items":[{"k":1},]})",
       R"({"items":[{"k":1,"x":0}]})", R"({"items":[{"k":1,"k":2}]})", R"({"items":[{}]})",
       R"({"items":[{"k":1,"sub":{}}]})", R"({"items":[{"k":1,"tags":[1]}]})",
       R"({"items":{"k":1}})", R"({"items":[[]]})"}) {
    rejects([&] { typed_document invalid(plan, input); });
  }
}

std::vector<std::vector<scalar_field>> chain(size_t depth) {
  std::vector<std::vector<scalar_field>> records(depth);
  for (size_t i = 0; i + 1 < depth; ++i)
    records[i].push_back(record_field("c", static_cast<uint32_t>(i + 1)));
  records[depth - 1].push_back({"leaf", scalar_kind::int64});
  return records;
}

void plan_depth() {
  typed_plan deepest(chain(max_plan_depth));
  std::string input;
  for (size_t i = 0; i + 1 < max_plan_depth; ++i) input += R"({"c":)";
  input += R"({"leaf":7})";
  for (size_t i = 0; i + 1 < max_plan_depth; ++i) input += '}';
  typed_document d(deepest, input);
  check(d.record_count() == max_plan_depth);
  check(field_slot(d, max_plan_depth - 1, 0).bits == 7);
  rejects([] { typed_plan too_deep(chain(max_plan_depth + 1)); });
  // Depth is measured from every record and through shared children.
  auto shared = chain(max_plan_depth);
  shared[0].push_back(record_field("again", 1));
  typed_plan diamond(std::move(shared));
  auto deeper = chain(max_plan_depth);
  deeper.push_back({record_field("into", 0)});
  rejects([&] { typed_plan unreachable_too_deep(std::move(deeper)); });
  rejects([] { typed_plan self(std::vector<std::vector<scalar_field>>{{record_field("r", 0)}}); });
  rejects([] {
    typed_plan cycle(std::vector<std::vector<scalar_field>>{
        {record_field("a", 1)}, {record_field("b", 0)}});
  });
  rejects([] { typed_plan missing(std::vector<std::vector<scalar_field>>{{record_field("r", 1)}}); });
  rejects([] { typed_plan none(std::vector<scalar_field>{record_field("r", no_child)}); });
  rejects([] {
    scalar_field scalar{"s", scalar_kind::string};
    scalar.child = 0;
    typed_plan linked(std::vector<scalar_field>{scalar});
  });
  rejects([] { typed_plan empty(std::vector<std::vector<scalar_field>>{}); });
}

// Upstream that fails its Nth allocation and tracks live bytes.
class failing_resource final : public std::pmr::memory_resource {
  size_t fail_at_;
public:
  size_t allocations = 0, live = 0;
  explicit failing_resource(size_t fail_at) : fail_at_(fail_at) {}
  void* do_allocate(size_t bytes, size_t alignment) override {
    if (allocations++ == fail_at_) throw std::bad_alloc();
    live += bytes;
    return std::pmr::new_delete_resource()->allocate(bytes, alignment);
  }
  void do_deallocate(void* pointer, size_t bytes, size_t alignment) override {
    live -= bytes;
    std::pmr::new_delete_resource()->deallocate(pointer, bytes, alignment);
  }
  bool do_is_equal(const std::pmr::memory_resource& other) const noexcept override {
    return this == &other;
  }
};

void arena_failures() {
  typed_plan plan(std::vector<std::vector<scalar_field>>{
      {record_field("items", 1, true, false, true), repeated({"v", scalar_kind::int64})},
      {{"k", scalar_kind::int64}, repeated({"tags", scalar_kind::string, false}),
       record_field("sub", 2, false)},
      {{"z", scalar_kind::boolean}}});
  std::string input = R"({"v":[1,2,3,4,5,6,7,8],"items":[)";
  for (int i = 0; i < 12; ++i) {
    if (i) input += ',';
    input += R"({"k":)" + std::to_string(i) + R"(,"tags":["a","b","c"],"sub":{"z":false}})";
  }
  input += "]}";
  size_t expected = 0;
  {
    failing_resource counting(SIZE_MAX);
    typed_document complete(plan, input, {}, &counting);
    expected = complete.allocations().index_allocations;
    check(expected == counting.allocations && expected > 8);
    check(complete.record_count() == 1 + 12 + 12);
    check(complete.allocations().index_retained_bytes == counting.live);
  }
  // Every arena growth fails once; each failure unwinds with nothing retained.
  for (size_t fail = 0; fail < expected; ++fail) {
    failing_resource failing(fail);
    bool failed = false;
    try { typed_document partial(plan, input, {}, &failing); }
    catch (const std::bad_alloc&) { failed = true; }
    check(failed && failing.live == 0 && failing.allocations == fail + 1);
  }
  // The index budget bounds both arenas together.
  failing_resource counting(SIZE_MAX);
  typed_document measured(plan, input, {}, &counting);
  size_t peak = measured.allocations().index_peak_bytes;
  rejects([&] { typed_document limited(plan, input, {1 << 20, peak - 1}); });
  typed_document exact(plan, input, {1 << 20, peak});
  check(exact.allocations().index_peak_bytes == peak);
}

int main() {
  scalar_field integer{"n", scalar_kind::int64};
  integer.minimum = -128; integer.maximum = 127;
  scalar_field text{"s", scalar_kind::string, false, true};
  text.max_utf8_bytes = 10;
  typed_plan plan({integer, text, {"b", scalar_kind::boolean, false}});
  typed_document first(plan, R"({"s":"\u4e2d😀", "n":-128,"b":true})");
  check(first.present() == 7 && first.nulls() == 0);
  check(first.slot(0).bits == static_cast<uint64_t>(-128));
  check(slot_text(first.slot(1)) == "中😀");
  check(lexical(first.slot(1)) == R"("\u4e2d😀")");
  check(first.slot(2).bits == 1);
  typed_document missing(plan, R"({"n":127})");
  typed_document null(plan, R"({"s":null,"n":0})");
  check(missing.present() == 1 && missing.nulls() == 0);
  check(null.present() == 3 && null.nulls() == 2);
  check(first.allocations().index_allocations == 1);
  check(first.record_count() == 1 && first.record(0).present == 7 && first.record(0).slots == 0);
  check(first.allocations().index_retained_bytes == plan.size() * sizeof(scalar_slot));
  for (auto input : {R"({"n":128})", R"({"n":-129})", R"({"n":1.5})", R"({"n":null})",
       R"({"n":"1"})", R"({"n":0,"b":tru})", R"({"n":0,"s":"\q"})", R"({"n":0,"unused":1})",
       R"({"n":0,"\u006e":1})", R"({"n":0,})", R"({"n":0}[])", R"({"n":01})", "{}", "[]", "null",
       R"({"n":0,"s":"elevenchars"})"}) {
    rejects([&] { typed_document invalid(plan, input); });
  }
  rejects([&] { typed_document limited(plan, R"({"n":0})", {100, sizeof(scalar_slot) * 3 - 1}); });
  typed_document exact(plan, R"({"n":0})", {100, sizeof(scalar_slot) * 3});
  check(exact.allocations().index_peak_bytes == sizeof(scalar_slot) * 3);
  std::unique_ptr<typed_document> retained;
  {
    typed_plan temporary({{"x", scalar_kind::string}});
    std::string input = R"({"x":"retained long string with \u4e2d"})";
    retained = std::make_unique<typed_document>(temporary, input);
    input.assign(input.size(), 'x');
  }
  check(slot_text(retained->slot(0)) == "retained long string with 中");
  typed_plan numbers({{"u", scalar_kind::uint64}, {"f", scalar_kind::float64}});
  typed_document number(numbers, R"({"u":18446744073709551615,"f":-0.0})");
  check(number.slot(0).bits == UINT64_MAX);
  check(number.slot(1).bits == (uint64_t(1) << 63));
  for (auto input : {R"({"u":18446744073709551616,"f":0})", R"({"u":-1,"f":0})", R"({"u":0,"f":1e9999})"})
    rejects([&] { typed_document invalid(numbers, input); });
  std::vector<scalar_field> fields;
  std::string wide = "{";
  for (int i = 0; i < 64; ++i) {
    fields.push_back({std::to_string(i), scalar_kind::boolean});
    if (i) wide += ',';
    wide += '"' + std::to_string(i) + "\":true";
  }
  wide += '}';
  typed_plan full(fields);
  typed_document all(full, wide);
  check(all.present() == UINT64_MAX && all.slot(63).bits == 1);
  fields.push_back({"64", scalar_kind::boolean});
  rejects([&] { typed_plan oversized(fields); });
  rejects([] { typed_plan duplicate({{"a", scalar_kind::boolean}, {"a", scalar_kind::string}}); });
  typed_plan signed64({{"v", scalar_kind::int64}});
  typed_document minimum(signed64, R"({"v":-9223372036854775808})");
  typed_document maximum(signed64, R"({"v":9223372036854775807})");
  check(minimum.slot(0).bits == (uint64_t(1) << 63));
  check(maximum.slot(0).bits == INT64_MAX);
  rejects([&] { typed_document invalid(signed64, R"({"v":9223372036854775808})"); });
  rejects([&] { typed_document invalid(signed64, R"({"v":-9223372036854775809})"); });
  scalar_field unsigned_field{"u", scalar_kind::uint64};
  unsigned_field.unsigned_maximum = 255;
  typed_plan byte_plan({unsigned_field});
  typed_document byte_max(byte_plan, R"({"u":255})");
  check(byte_max.slot(0).bits == 255);
  rejects([&] { typed_document invalid(byte_plan, R"({"u":256})"); });
  rejects([&] { typed_document failed(plan, R"({"n":0})", {}, std::pmr::null_memory_resource()); });
  rejects([] { typed_plan invalid({{std::string(1, char(0xff)), scalar_kind::string}}); });
  typed_plan empty(std::vector<scalar_field>{});
  typed_document empty_record(empty, "{}");
  check(empty_record.present() == 0 && empty_record.allocations().index_allocations == 0);
  rejects([&] { (void)empty_record.slot(0); });
  nested_records();
  repeated_scalars();
  repeated_records();
  plan_depth();
  arena_failures();
  std::cout << "Typed nested and repeated JSON slots, contracts and retained ownership passed\n";
}
