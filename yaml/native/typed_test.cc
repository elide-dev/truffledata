#include "yaml/native/typed.h"
#include "yaml/native/testing.h"
#include <iostream>

using namespace truffledata::yaml;
using truffledata::yaml::testing::fault_memory;

template<class F> void rejects(F operation) {
  bool rejected = false;
  try { operation(); } catch (const std::exception&) { rejected = true; }
  require(rejected);
}
std::string_view text(const scalar_slot& slot) {
  return {reinterpret_cast<const char*>(slot.text), slot.text_length};
}
scalar_field child(std::string name, uint32_t ordinal, bool repeated = false) {
  scalar_field field{std::move(name), scalar_kind::record};
  field.child = ordinal;
  field.repeated = repeated;
  return field;
}

int main() {
  typed_plan flat(std::vector<scalar_field>{{"s", scalar_kind::string},
      {"i", scalar_kind::int64}, {"f", scalar_kind::float64}, {"b", scalar_kind::boolean}}, "YAML");
  typed_document value(flat, "s: 1.0\ni: 0x1f\nf: .inf\nb: true\n");
  require(text(value.slot(0)) == "1.0" && value.slot(1).bits == 31);
  double number; uint64_t bits = value.slot(2).bits; std::memcpy(&number, &bits, sizeof number);
  require(std::isinf(number) && number > 0 && value.slot(3).bits == 1);
  require(value.record_count() == 1 && value.present() == 15 && value.nulls() == 0);
  typed_document zero(flat, "{s: 'x', i: -0, f: .nan, b: false}");
  require(text(zero.slot(0)) == "x" && zero.slot(1).bits == 0 && zero.slot(3).bits == 0);
  bits = zero.slot(2).bits; std::memcpy(&number, &bits, sizeof number); require(std::isnan(number));
  for (auto input : {"{s: x, i: '12', f: 1.0, b: true}", "{s: x, i: 1, f: 1.0, b: 'true'}",
       "{s: x, i: 9223372036854775808, f: 1.0, b: true}", "{s: x, i: 1, f: 1e400, b: true}",
       "{s: x, i: 1, f: 1.0}", "{s: x, i: 1, f: 1.0, b: true, extra: 1}",
       "{s: x, i: 1, i: 2, f: 1.0, b: true}", "[1, 2]", ""})
    rejects([&] { typed_document invalid(flat, input); });

  scalar_field words{"words", scalar_kind::string}; words.repeated = true;
  typed_plan nested(std::vector<std::vector<scalar_field>>{
      {child("a", 1), child("b", 1), child("items", 1, true), words},
      {{"n", scalar_kind::int64}, {"optional", scalar_kind::string, false, true}}}, "YAML");
  std::string input = "a: &a {n: 7, optional: null}\nb: *a\nitems: [*a, {n: 9}]\nwords: [0x1f, 'quoted']\n";
  typed_document aliases(nested, input);
  require(aliases.record_count() == 5 && aliases.record(1).nulls == 2 && aliases.record(2).nulls == 2);
  require(aliases.slot(aliases.record(3).slots).bits == 7 && aliases.slot(aliases.record(4).slots).bits == 9);
  uint64_t first = aliases.slot(3).bits >> 32;
  require((aliases.slot(3).bits & UINT32_MAX) == 2 && text(aliases.slot(first)) == "0x1f");
  require(text(aliases.slot(first + 1)) == "quoted");
  session_options small; small.max_alias_expansion = 5;
  rejects([&] { typed_document invalid(nested, input, small); });
  session_options merge; merge.rules.merge_keys = true;
  typed_document merged(nested, "a: &a {n: 7}\nb: {<<: *a}\nitems: []\nwords: []", merge);
  require(merged.slot(merged.record(2).slots).bits == 7);
  session_options old; old.rules.schema = schema::yaml11;
  typed_document legacy(flat, "{s: 01, i: 0b11, f: 1:20.0, b: yes}", old);
  require(text(legacy.slot(0)) == "01" && legacy.slot(1).bits == 3 && legacy.slot(3).bits == 1);
  bits = legacy.slot(2).bits; std::memcpy(&number, &bits, sizeof number); require(number == 80);

  scalar_field narrow{"i", scalar_kind::int64}; narrow.minimum = -2; narrow.maximum = 2;
  typed_plan bounded(std::vector<scalar_field>{narrow}, "YAML");
  rejects([&] { typed_document invalid(bounded, "i: 3"); });
  words.repeated = false; words.length_unit = 2; words.maximum_length = 1;
  typed_plan unicode(std::vector<scalar_field>{words}, "YAML");
  typed_document one(unicode, "words: '😀'"); require(text(one.slot(0)) == "😀");
  rejects([&] { typed_document invalid(unicode, "words: '😀a'"); });

  fault_memory memory;
  { typed_document counted(nested, input, {}, &memory); require(memory.live > 0); }
  require(memory.live == 0);
  size_t calls = memory.calls;
  for (size_t i = 0; i < calls; ++i) {
    fault_memory failing(i);
    rejects([&] { typed_document failed(nested, input, {}, &failing); });
    require(failing.live == 0);
  }
  std::cout << "Typed YAML slots, schema coercion, aliases, merges, limits and " << calls
            << " allocation failures passed\n";
}
