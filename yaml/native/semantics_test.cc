// YAML semantics (plan Task 3, semantics_test): the 1.1/1.2 schema boundary, anchors and aliases
// as reference nodes (linear memory, recursion and undefined names rejected, rebinding), merge
// keys, tags (core, 1.1 types, %TAG handles, verbatim, PRESERVE/REJECT), key kinds, non-finite
// floats, and the pairs representation.
#include "yaml/native/session.h"
#include "yaml/native/tags.h"
#include "yaml/native/testing.h"

#include <cmath>
#include <cstdio>
#include <cstring>
#include <string>

using namespace truffledata::yaml;
using yaml_session = truffledata::yaml::session;
using truffledata::yaml::testing::node_text;
using truffledata::yaml::testing::render;
using truffledata::yaml::testing::ordinal;
using truffledata::yaml::testing::node_count;

namespace {
session_options with(policy rules = {}) {
  session_options o;
  o.rules = rules;
  return o;
}
policy schema11() {
  policy p;
  p.schema = schema::yaml11;
  return p;
}
yaml_session::document_view decode(yaml_session& s, const std::string& text) {
  return s.decode(reinterpret_cast<const uint8_t*>(text.data()), text.size(), 0);
}
std::string tape(yaml_session& s, const std::string& text) { return render(decode(s, text).view); }
error failure(yaml_session& s, const std::string& text) {
  try {
    decode(s, text);
  } catch (const error& e) {
    return e;
  }
  throw std::runtime_error("decode succeeded: " + text);
}
std::string message(yaml_session& s, const std::string& text) { return failure(s, text).what(); }
std::string tag_of(const yaml_session::document_view& d, size_t node) {
  node = ordinal(d.view, node);
  uint32_t index = d.meta[node] >> meta_tag_shift;
  if (!index || (d.meta[node] & meta_alias)) return "";
  return std::string(d.strings.substr(d.tags[index - 1].offset, d.tags[index - 1].length));
}
std::string anchor_name(const yaml_session::document_view& d, size_t id) {
  return std::string(d.strings.substr(d.anchors[id].offset, d.anchors[id].length));
}

void schema_boundaries() {
  const std::string doc = "a: on\nb: yes\nc: y\nd: 0755\ne: 1:20\nf: 2001-12-14\ng: !!str 0x1F\nh: 0b101\ni: 1_000\n"
                          "j: off\nk: 0x1_0000_0000_0000_0000\n<<: {m: 1}\n";
  yaml_session core(with());
  auto d = decode(core, doc);
  require(render(d.view) ==
          "{\"a\":\"on\",\"b\":\"yes\",\"c\":\"y\",\"d\":755,\"e\":\"1:20\",\"f\":\"2001-12-14\",\"g\":\"0x1F\",\"h\":\"0b101\","
          "\"i\":\"1_000\",\"j\":\"off\",\"k\":\"0x1_0000_0000_0000_0000\",\"<<\":{\"m\":1}}");
  require(tag_of(d, 12) == "");  // no implicit timestamp under core
  yaml_session v11(with(schema11()));
  d = decode(v11, doc);
  require(render(d.view) ==
          "{\"a\":true,\"b\":true,\"c\":true,\"d\":493,\"e\":80,\"f\":\"2001-12-14\",\"g\":\"0x1F\",\"h\":5,\"i\":1000,"
          "\"j\":false,\"k\":d18446744073709551616,\"m\":1}");
  require(tag_of(d, 12) == "tag:yaml.org,2002:timestamp" && (d.flags & doc_schema_1_1));
  require(tag_of(d, 14) == "tag:yaml.org,2002:str");
  // More 1.1 forms.
  require(tape(v11, "[0o17, 017, -0x1F, +0b11, 190:20:30, 1_000.5, .5, 1.5e+3, 1e3, 1., 190:20:30.15, ~, Null, NO, Off]") ==
          "[\"0o17\",15,-31,3,685230,f1000.5,f0.5,f1500,\"1e3\",f1,f685230.15,null,null,false,false]");
  require(tape(v11, "- 2001-12-14t21:59:43.10-05:00\n- 2001-12-14 21:59:43.10 -5\n- 2002-1-2\n") ==
          "[\"2001-12-14t21:59:43.10-05:00\",\"2001-12-14 21:59:43.10 -5\",\"2002-1-2\"]");
  require(tag_of(decode(v11, "- 2002-1-2"), 1) == "");  // the date-only form needs two-digit fields
  // !!str 0x1F is the string under both schemas; !!int 0755 follows each schema.
  require(tape(core, "[!!str 0x1F, !!int 0755]") == "[\"0x1F\",755]");
  require(tape(v11, "[!!str 0x1F, !!int 0755, !!float 1, !!bool yes]") == "[\"0x1F\",493,f1,true]");
  // %YAML 1.1 is recorded; resolution switches only with VersionDirective.HONOR.
  d = decode(core, "%YAML 1.1\n--- [yes, 0755]\n");
  require(render(d.view) == "[\"yes\",755]" && d.version == ((1u << 16) | 1) && !(d.flags & doc_schema_1_1));
  policy honor;
  honor.honor_version = true;
  yaml_session h(with(honor));
  d = decode(h, "%YAML 1.1\n--- [yes, 0755, {<<: {a: 1}}]\n");
  require(render(d.view) == "[true,493,{\"a\":1}]" && (d.flags & doc_schema_1_1));
  require(tape(h, "%YAML 1.2\n--- [yes, 0755]\n") == "[\"yes\",755]");
  require(tape(h, "[yes]") == "[\"yes\"]");
}

void alias_graph_is_linear() {
  // Billion laughs: 10 levels of 10 aliases each would expand to 10^10 nodes.
  std::string doc = "a: &a [lol, lol, lol, lol, lol, lol, lol, lol, lol, lol]\n";
  const char* names = "abcdefghij";
  for (int level = 1; level < 10; ++level) {
    doc += std::string(1, names[level]) + ": &" + names[level] + " [";
    for (int i = 0; i < 10; ++i) doc += std::string(i ? ", " : "") + "*" + names[level - 1];
    doc += "]\n";
  }
  yaml_session s(with());
  auto d = decode(s, doc);
  require(node_count(d.view) < 200);
  require(d.anchors.size() == 10 && d.alias_targets.size() == 90);
  require(s.memory().peak() < 64 * 1024);
  std::printf("alias_graph_is_linear: %zu bytes, %llu nodes, %zu anchors, %zu aliases, index peak %zu bytes\n",
              doc.size(), (unsigned long long)node_count(d.view), d.anchors.size(), d.alias_targets.size(),
              s.memory().peak());
  // Every alias resolves in one step to its anchored collection.
  for (size_t i = 0; i < d.alias_targets.size(); ++i) {
    uint64_t target = d.alias_targets[i];
    require(ct::kind(d.view.words[target]) == ct::kind_array && (d.meta[target] & meta_anchored));
    require(!(d.meta[target] & meta_alias));
  }
  // The alias nodes are NULL with the ordinal in yaml_meta.
  size_t aliases = 0;
  for (size_t i = 0; i < d.view.word_count; i = ct::step(i, d.view.words[i])) {
    if (!(d.meta[i] & meta_alias)) continue;
    require(ct::kind(d.view.words[i]) == ct::kind_ref && (d.meta[i] >> meta_tag_shift) == aliases);
    ++aliases;
  }
  require(aliases == 90);
  // maxAliases and Aliases.REJECT.
  policy few;
  few.max_aliases = 89;
  yaml_session limited(with(few));
  require(failure(limited, doc).code == status_limit);
  policy reject;
  reject.reject_aliases = true;
  yaml_session rejecting(with(reject));
  require(message(rejecting, "a: &x 1\nb: *x\n") == "YAML aliases are rejected");
  require(tape(rejecting, "a: &x 1\n") == "{\"a\":1}");
}

void recursive_alias_rejected() {
  yaml_session s(with());
  for (const char* doc : {"&a [*a]", "&a {k: *a}", "&a [1, [2, *a]]", "a: &x\n  b: *x\n", "- &x\n  - *x\n"}) {
    error e = failure(s, doc);
    require(e.code == status_syntax && std::string(e.what()) == "recursive YAML alias");
  }
}

void undefined_alias_rejected() {
  yaml_session s(with());
  for (const char* doc : {"*x", "a: *x\n", "[*x]", "a: &y 1\nb: *x\n", "{*x : 1}"}) {
    error e = failure(s, doc);
    require(e.code == status_syntax && std::string(e.what()) == "undefined YAML alias");
  }
}

void anchor_redefinition_rebinds() {
  yaml_session s(with());
  auto d = decode(s, "a: &x 1\nb: *x\nc: &x 2\nd: *x\ne: &y [*x]\n");
  require(render(d.view) == "{\"a\":1,\"b\":null,\"c\":2,\"d\":null,\"e\":[null]}");
  require(d.anchors.size() == 3 && anchor_name(d, 0) == "x" && anchor_name(d, 1) == "x" && anchor_name(d, 2) == "y");
  require(d.alias_targets[0] == ordinal(d.view, 2) && d.alias_targets[1] == ordinal(d.view, 6) && d.alias_targets[2] == ordinal(d.view, 6));
  require(d.anchors[0].node == ordinal(d.view, 2) && d.anchors[1].node == ordinal(d.view, 6) && d.anchors[2].node == ordinal(d.view, 10));
  // Aliases as keys hold the target's key text and the alias bit.
  d = decode(s, "{a: &k b, &n 0x1F: c, map: {*k : 1, *n : 2}}");
  require(render(d.view) == "{\"a\":\"b\",\"31\":\"c\",\"map\":{\"b\":1,\"31\":2}}");
  require((d.meta[ordinal(d.view, 7)] & meta_alias) && d.alias_targets[d.meta[ordinal(d.view, 7)] >> meta_tag_shift] == ordinal(d.view, 2));
  require(((d.meta[ordinal(d.view, 9)] >> meta_key_kind_shift) & 7) == uint32_t(key_kind::integer));
  // An alias to a collection is a complex key.
  require(message(s, "a: &c [1]\nb: {*c : x}\n") == "YAML complex key");
  // `[&x a: b, *x]`: the anchor follows the scalar into the retroactive mapping's key.
  d = decode(s, "[&x a: b, *x]");
  require(render(d.view) == "[{\"a\":\"b\"},null]" && d.anchors[0].node == ordinal(d.view, 2) && d.alias_targets[0] == ordinal(d.view, 2));
  require(d.meta[ordinal(d.view, 2)] & meta_anchored);
}

void merge_semantics() {
  policy merging;
  merging.merge_keys = true;
  yaml_session s(with(merging));
  const std::string base = "base: &b {x: 1, y: 2}\nover: &o {y: 3, z: 4}\n";
  auto d = decode(s, base + "m: {<<: [*o, *b], y: 9, w: 0}\n");
  require(render(d.view) ==
          "{\"base\":{\"x\":1,\"y\":2},\"over\":{\"y\":3,\"z\":4},\"m\":{\"z\":null,\"x\":null,\"y\":9,\"w\":0}}");
  // Merged values are alias references to the source values (no deep copy).
  size_t m = ordinal(d.view, 14);  // the merged mapping
  require(ct::kind(d.view.words[m]) == ct::kind_object && ct::field(d.view.words[m]) == 4);
  size_t z = ct::step(m + 2, d.view.words[m + 2]);
  size_t x = ct::step(z + 1, d.view.words[z + 1]);
  require(ct::kind(d.view.words[z]) == ct::kind_ref && ct::low(d.view.words[z]) == ordinal(d.view, 12));
  require(ct::kind(d.view.words[x]) == ct::kind_ref && ct::low(d.view.words[x]) == ordinal(d.view, 4));
  require(ct::validate(d.view) == nullptr);
  // Earlier sources win, explicit keys win wherever they are, merged entries sit at `<<`.
  require(tape(s, base + "m: {w: 0, <<: [*b, *o]}\n").find("\"m\":{\"w\":0,\"x\":null,\"y\":null,\"z\":null}") !=
          std::string::npos);
  require(tape(s, base + "m: {<<: *b, x: 7}\n").find("\"m\":{\"y\":null,\"x\":7}") != std::string::npos);
  // Inline sources move into place.
  require(tape(s, "{<<: {x: 1, y: [2]}, y: 3}") == "{\"x\":1,\"y\":3}");
  require(tape(s, "{<<: [{x: 1}, {x: 2, z: [3]}], a: 0}") == "{\"x\":1,\"z\":[3],\"a\":0}");
  // An alias to a merged value targets the original node, not another alias.
  d = decode(s, "a: &a {k: &v [1]}\nb: &b {<<: *a}\nc: {<<: *b}\n");
  require(render(d.view) == "{\"a\":{\"k\":[1]},\"b\":{\"k\":null},\"c\":{\"k\":null}}");
  require((d.meta[ordinal(d.view, 13)] & meta_alias) && d.alias_targets[d.meta[ordinal(d.view, 13)] >> meta_tag_shift] == ordinal(d.view, 4));
  // Anchors inside a mapping that merges move with their entries.
  d = decode(s, base + "m: {q: &q 5, <<: *b}\nn: *q\n");
  require(render(d.view).find("\"m\":{\"q\":5,\"x\":null,\"y\":null},\"n\":null") != std::string::npos);
  require(d.alias_targets[d.alias_targets.size() - 1] == ordinal(d.view, 16));
  // Merging a non-mapping is an error.
  for (const char* bad : {"{<<: 1}", "{<<: [1]}", "a: &s [1]\nb: {<<: *s}\n", "{<<: [{a: 1}, 2]}"})
    require(message(s, bad) == "YAML merge of a non-mapping");
  require(message(s, "{<<: {a: 1}, <<: {b: 2}}") == "duplicate YAML key");
  // An anchor that merging would remove is an error.
  require(message(s, "{<<: &m {a: 1}}") == "YAML merge removes an anchored node");
  // LAST_WINS interplay: explicit duplicates resolve first, then the merge fills gaps.
  policy last = merging;
  last.last_wins = true;
  yaml_session lw(with(last));
  require(tape(lw, "{a: 1, <<: {a: 2, b: 2}, a: 3}") == "{\"b\":2,\"a\":3}");
  require(tape(lw, "{<<: {a: 1}, <<: {b: 2}}") == "{\"b\":2}");
  // Merge keys are off by default, and a quoted "<<" is never a merge.
  yaml_session plain(with());
  require(tape(plain, "{<<: {a: 1}}") == "{\"<<\":{\"a\":1}}");
  require(tape(s, "{\"<<\": {a: 1}}") == "{\"<<\":{\"a\":1}}");
  require(tape(s, "{!!merge <<: {a: 1}}") == "{\"a\":1}");
}

void tags() {
  yaml_session s(with());
  auto d = decode(s, "- !!str 12\n- !!int \"12\"\n- !local 12\n- !<tag:x.org,2000:y> v\n- ! 12\n- !!binary aGVsbG8gd29y\n  bGQ=\n");
  require(render(d.view) == "[\"12\",12,\"12\",\"v\",\"12\",\"aGVsbG8gd29y bGQ=\"]");
  require(tag_of(d, 1) == "tag:yaml.org,2002:str" && tag_of(d, 2) == "tag:yaml.org,2002:int");
  require(tag_of(d, 3) == "!local" && tag_of(d, 4) == "tag:x.org,2000:y" && tag_of(d, 5) == "!");
  require(tag_of(d, 6) == "tag:yaml.org,2002:binary");
  require(message(s, "!!int abc") == "YAML !!int tag on a non-integer scalar");
  require(message(s, "!!binary 'not base64!'") == "invalid base64 in a YAML !!binary scalar");
  // A long !!binary goes through simdutf in chunks without allocating.
  std::string blob(6000, 'A');
  require(tape(s, "!!binary " + blob).size() == blob.size() + 2);
  require(message(s, "!!timestamp yesterday") == "invalid YAML !!timestamp scalar");
  require(tape(s, "!!timestamp 2001-12-14t21:59:43.10-05:00") == "\"2001-12-14t21:59:43.10-05:00\"");
  // !!set, !!omap, !!pairs.
  d = decode(s, "--- !!set\n? a\n? b\n");
  require(render(d.view) == "{\"a\":null,\"b\":null}" && tag_of(d, 0) == "tag:yaml.org,2002:set");
  require(message(s, "!!set {a: 1}") == "YAML !!set with a non-null value");
  d = decode(s, "!!omap [a: 1, b: 2]");
  require(render(d.view) == "[{\"a\":1},{\"b\":2}]" && tag_of(d, 0) == "tag:yaml.org,2002:omap");
  require(message(s, "!!omap [a]") == "YAML !!omap entry is not a single-pair mapping");
  require(message(s, "!!pairs [{a: 1, b: 2}]") == "YAML !!pairs entry is not a single-pair mapping");
  require(message(s, "!!set [a]") == "YAML mapping tag on a sequence");
  require(message(s, "!!omap {a: 1}") == "YAML sequence tag on a mapping");
  require(message(s, "!!int [1]") == "YAML scalar tag on a collection");
  require(message(s, "!!seq a") == "YAML collection tag on a scalar");
  // %TAG handles resolve through rapidyaml; the directive is recorded.
  d = decode(s, "%TAG !e! tag:example.com,2000:app/\n--- !e!foo bar\n");
  require(render(d.view) == "\"bar\"" && tag_of(d, 0) == "tag:example.com,2000:app/foo");
  require(d.directives.size() == 1);
  require(d.strings.substr(d.directives[0].handle_offset, d.directives[0].handle_length) == "!e!");
  require(d.strings.substr(d.directives[0].prefix_offset, d.directives[0].prefix_length) == "tag:example.com,2000:app/");
  // Tags are interned once per document.
  d = decode(s, "[!!str a, !!str b, !x c, !x d]");
  require(d.tags.size() == 2 && (d.meta[ordinal(d.view, 1)] >> meta_tag_shift) == (d.meta[ordinal(d.view, 2)] >> meta_tag_shift));
  // UnknownTags.REJECT.
  policy strict;
  strict.reject_unknown_tags = true;
  yaml_session r(with(strict));
  require(message(r, "!local 12") == "YAML unknown tag");
  require(tape(r, "[!!str 12, ! 13]") == "[\"12\",\"13\"]");
  // Many distinct tags exercise rapidyaml's tag cache and the interning table past their inline sizes.
  std::string many = "[";
  for (int i = 0; i < 100; ++i) many += "!t" + std::to_string(i) + " v, ";
  many += "]";
  d = decode(s, many);
  require(d.tags.size() == 100 && ct::field(d.view.words[0]) == 100);
}

void non_string_keys_keep_kind() {
  yaml_session s(with());
  auto d = decode(s, "{~: a, true: b, 012: c, 1.50: d, .inf: e, 18446744073709551616: f, \"s\": g, !!str 3: h}");
  require(render(d.view) ==
          "{\"\":\"a\",\"true\":\"b\",\"12\":\"c\",\"1.5\":\"d\",\".inf\":\"e\",\"18446744073709551616\":\"f\",\"s\":\"g\",\"3\":\"h\"}");
  key_kind kinds[] = {key_kind::null, key_kind::boolean, key_kind::integer, key_kind::floating, key_kind::floating,
                      key_kind::integer, key_kind::string, key_kind::string};
  for (int i = 0; i < 8; ++i) require(((d.meta[ordinal(d.view, 1 + 2 * i)] >> meta_key_kind_shift) & 7) == uint32_t(kinds[i]));
  // The source spelling stays in the lexical span.
  uint64_t span = lexical_span(d.view, ordinal(d.view, 5));
  require(std::string(d.source + (span >> 32), uint32_t(span)) == "012");
}

void nonfinite_floats() {
  yaml_session s(with());
  auto d = decode(s, "[.inf, -.Inf, .NAN, !!float .inf, 1.5]");
  require(d.flags & doc_nonfinite);
  double v[3];
  for (int i = 0; i < 3; ++i) {
    require(ct::kind(d.view.words[ordinal(d.view, 1 + i)]) == ct::kind_f64);
    std::memcpy(&v[i], d.view.words + ordinal(d.view, 1 + i) + 1, sizeof(double));
  }
  require(std::isinf(v[0]) && v[0] > 0 && std::isinf(v[1]) && v[1] < 0 && std::isnan(v[2]));
  require(d.view.flags == (TD_TAPE_NONFINITE | TD_TAPE_HAS_META | TD_TAPE_YAML));
  auto finite = decode(s, "[1.5]");
  require(!(finite.flags & doc_nonfinite) && finite.view.flags == (TD_TAPE_HAS_META | TD_TAPE_YAML));
}

void pairs_representation() {
  policy pairs;
  pairs.complex_keys_pairs = true;
  pairs.duplicate_keys_pairs = true;
  yaml_session s(with(pairs));
  auto d = decode(s, "a: 1\n[b, c]: 2\n? {d: 3}\n: 4\n");
  require(render(d.view) == "[[\"a\",1],[[\"b\",\"c\"],2],[{\"d\":3},4]]");
  require((d.meta[ordinal(d.view, 0)] & meta_style_mask) == style_block && (d.meta[ordinal(d.view, 1)] & meta_style_mask) == style_pair);
  require(tape(s, "{a: 1, a: 2}") == "[[\"a\",1],[\"a\",2]]");
  require(tape(s, "[[a]: b]") == "[[[[\"a\"],\"b\"]]]");
  d = decode(s, "x: &k [1]\ny: {*k : 2}\n");
  require(render(d.view) == "{\"x\":[1],\"y\":[[null,2]]}");
  require((d.meta[ordinal(d.view, 7)] & meta_alias) && d.alias_targets[d.meta[ordinal(d.view, 7)] >> meta_tag_shift] == ordinal(d.view, 2));
  // Anchors inside a mapping converted to pairs follow their nodes.
  d = decode(s, "{a: &v 1, [x]: 2, b: *v}");
  require(render(d.view) == "[[\"a\",1],[[\"x\"],2],[\"b\",null]]");
  require(d.anchors[0].node == ordinal(d.view, 3) && d.alias_targets[0] == ordinal(d.view, 3));
  // New word offsets overlap later old blocks: each anchor must relocate exactly once.
  d = decode(s, "{a: &a one, b: &b two, c: &c three, a: *a, d: *b, e: *c}");
  require(ct::validate(d.view) == nullptr);
  require(d.anchors.size() == 3 && d.alias_targets.size() == 3);
  for (size_t i = 0; i < 3; ++i) require(d.alias_targets[i] == d.anchors[i].node);
  require(node_text(d.view, d.anchors[0].node) == "one");
  require(node_text(d.view, d.anchors[1].node) == "two");
  require(node_text(d.view, d.anchors[2].node) == "three");
}
}  // namespace

int main() {
  try {
    schema_boundaries();
    alias_graph_is_linear();
    recursive_alias_rejected();
    undefined_alias_rejected();
    anchor_redefinition_rebinds();
    merge_semantics();
    tags();
    non_string_keys_keep_kind();
    nonfinite_floats();
    pairs_representation();
  } catch (const std::exception& e) {
    std::fprintf(stderr, "semantics_test failure: %s\n", e.what());
    return 1;
  }
  std::printf("YAML semantics checks passed\n");
  return 0;
}
