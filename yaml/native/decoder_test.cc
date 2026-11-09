// The decoder core (plan Task 2, decoder_test): tapes against the probe's reference builder,
// duplicate and complex keys, depth, string storage, lexical spans and the single-document rule.
//
// Usage: decoder_test <fixture.yaml>...   (yaml/native/testdata, from the probe's fixtures.py)
#include "yaml/native/decoder.h"

#include "yaml/native/reference_tape.h"
#include "yaml/native/session.h"
#include "yaml/native/testing.h"

#include <cstdio>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>

using namespace truffledata::yaml;
using yaml_session = truffledata::yaml::session;
using truffledata::yaml::testing::node_text;
using truffledata::yaml::testing::render;
using truffledata::yaml::testing::ordinal;
using truffledata::yaml::testing::node_count;

namespace {
std::string slurp(const std::string& path) {
  std::ifstream in(path, std::ios::binary);
  std::ostringstream out;
  out << in.rdbuf();
  return out.str();
}
std::string basename(const std::string& path) {
  size_t slash = path.rfind('/');
  return slash == std::string::npos ? path : path.substr(slash + 1);
}

session_options with(policy rules = {}, myna::value_limits values = {}) {
  session_options o;
  o.rules = rules;
  o.values = values;
  return o;
}

yaml_session::document_view decode(yaml_session& s, const std::string& text, uint32_t flags = 0) {
  try {
    return s.decode(reinterpret_cast<const uint8_t*>(text.data()), text.size(), flags);
  } catch (const error&) {
    throw;
  } catch (const std::exception& e) {
    throw std::runtime_error(std::string(e.what()) + " decoding: " + text.substr(0, 200));
  }
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

void tape_matches_reference_builder(const std::vector<std::string>& fixtures) {
  yaml_session s(with());
  size_t compared = 0;
  for (const auto& path : fixtures) {
    std::string name = basename(path);
    if (name == "records-stream.yaml") continue;  // multi-document: the stream API (Task 3)
    std::string text = slurp(path);
    std::string ours = tape(s, text);
    std::string copy = text;
    reference::ints_tape reference;
    std::pmr::monotonic_buffer_resource memory;
    myna::value_builder builder(&memory);
    reference.build(copy, builder);
    std::string theirs = render(builder.view());
    if (ours != theirs) {
      size_t at = 0;
      while (at < ours.size() && at < theirs.size() && ours[at] == theirs[at]) ++at;
      std::fprintf(stderr, "%s differs at %zu:\n ours:   %.120s\n theirs: %.120s\n", name.c_str(), at,
                   ours.c_str() + (at > 40 ? at - 40 : 0), theirs.c_str() + (at > 40 ? at - 40 : 0));
    }
    require(ours == theirs);
    std::printf("tape_matches_reference_builder: %-20s %8zu bytes  %7llu nodes  equal\n", name.c_str(), text.size(),
                (unsigned long long)node_count(decode(s, text).view));
    ++compared;
  }
  require(compared == 4);
}

void values() {
  yaml_session s(with());
  require(tape(s, "") == "null");
  require(tape(s, "# only a comment\n") == "null");
  require(tape(s, "---\n") == "null");
  require(tape(s, "--- ~\n...\n") == "null");
  require(tape(s, "a: 1\nb: [true, ~, 1.5, -0, 0x1F, .inf]\nc: {d: e}\n") ==
          "{\"a\":1,\"b\":[true,null,f1.5,f-0,31,finf],\"c\":{\"d\":\"e\"}}");
  require(tape(s, "- 18446744073709551615\n- 18446744073709551616\n- 1e400\n- +1e400\n") ==
          "[u18446744073709551615,d18446744073709551616,d1e400,d1e400]");
  require(tape(s, "k: 'single ''q'''\nd: \"tab\\there\"\nl: |\n  line\n  two\nf: >-\n  folded\n  text\n") ==
          "{\"k\":\"single 'q'\",\"d\":\"tab\\there\",\"l\":\"line\\ntwo\\n\",\"f\":\"folded text\"}");
  require(tape(s, "plain: multi\n  line\n") == "{\"plain\":\"multi line\"}");
  require(tape(s, "!!str 12") == "\"12\"");
  require(tape(s, "- !!int \"12\"\n- !!float 1\n- ! 12\n- !!null ''\n") == "[12,f1,\"12\",null]");
  require(failure(s, "!!int abc").code == status_syntax);
  require(failure(s, "!!map [1]").code == status_syntax);
  require(failure(s, "!!str [1]").code == status_syntax);
  require(tape(s, "!!seq [1]") == "[1]");
  // `[a: b]`: the sequence element becomes the key of a single-pair flow mapping.
  require(tape(s, "[a: b, c, \"d\": e, 1: f]") == "[{\"a\":\"b\"},\"c\",{\"d\":\"e\"},{\"1\":\"f\"}]");
  require(tape(s, "[: x]") == "[{\"\":\"x\"}]");
  // Keys keep the resolved kind in yaml_meta bits 5-7.
  auto d = decode(s, "{~: a, true: b, 0x1F: c, 1.50: d, s: e, \"1\": f}");
  require(render(d.view) == "{\"\":\"a\",\"true\":\"b\",\"31\":\"c\",\"1.5\":\"d\",\"s\":\"e\",\"1\":\"f\"}");
  uint32_t kinds[] = {1, 2, 3, 4, 0, 0};
  for (int i = 0; i < 6; ++i) require(((d.meta[ordinal(d.view, 1 + 2 * i)] >> meta_key_kind_shift) & 7) == kinds[i]);
  require((d.meta[ordinal(d.view, 11)] & meta_style_mask) == uint32_t(scalar_style::double_quoted));
  require((d.meta[ordinal(d.view, 0)] & meta_style_mask) == style_flow);
  // %YAML 1.1 is recorded; 2.0 is rejected.
  require(decode(s, "%YAML 1.1\n--- yes\n").version == ((1u << 16) | 1));
  require(tape(s, "%YAML 1.1\n--- yes\n") == "\"yes\"");
  require(failure(s, "%YAML 2.0\n--- x\n").code == status_syntax);
  // Syntax errors carry rapidyaml's line and column.
  error e = failure(s, "a: 1\nb: [1, 2\n");
  require(e.code == status_syntax && e.line > 0);
  // An alias is a NULL node with the alias bit (semantics_test covers the side tables).
  auto aliased = decode(s, "a: &x 1\nb: *x\n");
  require(render(aliased.view) == "{\"a\":1,\"b\":null}" && (aliased.meta[ordinal(d.view, 4)] & meta_alias) && aliased.alias_targets[0] == ordinal(aliased.view, 2));
  // The JSON and failsafe schemas.
  yaml_session json(with(policy{schema::json}));
  require(tape(json, "{\"a\": [1, -0, 1.5, true, null, \"x\"]}") == "{\"a\":[1,f-0,f1.5,true,null,\"x\"]}");
  require(failure(json, "a: 1").code == status_syntax);
  require(failure(json, "[.5]").code == status_syntax);
  yaml_session failsafe(with(policy{schema::failsafe}));
  require(tape(failsafe, "a: [1, ~, true, ]") == "{\"a\":[\"1\",\"~\",\"true\"]}");
}

void duplicate_keys_after_resolution() {
  yaml_session s(with());
  for (const char* doc : {"{1: a, 0x1: b}", "{1: a, \"1\": b}", "{null: a, ~: b}", "{a: 1, \"a\": 2}", "{0o17: a, 15: b}",
                          "{1.5: a, 1.50: b}", "a: 1\nb: 2\na: 3\n", "{\"\": a, ~: b}"}) {
    error e = failure(s, doc);
    require(e.code == status_syntax && std::string(e.what()) == "duplicate YAML key");
  }
  // Many keys move the table from the inline array to open addressing.
  std::string wide = "{";
  for (int i = 0; i < 100; ++i) wide += "k" + std::to_string(i) + ": " + std::to_string(i) + ", ";
  require(ct::field(decode(s, wide + "}").view.words[0]) == 100);
  require(failure(s, wide + "k42: x}").code == status_syntax);
  // Nested mappings have their own key sets.
  require(tape(s, "{a: {a: 1}, b: {a: 2}}") == "{\"a\":{\"a\":1},\"b\":{\"a\":2}}");

  policy last;
  last.last_wins = true;
  yaml_session lw(with(last));
  auto d = decode(lw, "{a: 1, a: 2}");
  require(render(d.view) == "{\"a\":2}" && ct::field(d.view.words[0]) == 1 && node_count(d.view) == 3);
  d = decode(lw, "a: [1, {x: y}]\nb: long string value beyond inline\na: 3\nc: {a: 4}\nb: 5\n");
  require(render(d.view) == "{\"a\":3,\"c\":{\"a\":4},\"b\":5}");
  // Directory offsets and metadata follow variable-width compaction.
  require(ct::validate(d.view) == nullptr);
  require(d.meta.size() == d.view.word_count);
  require(render(decode(lw, "{1: a, 0x1: b, \"1\": c}").view) == "{\"1\":\"c\"}");
  require(render(decode(lw, "[{k: 1, k: 2}, {k: 3}]").view) == "[{\"k\":2},{\"k\":3}]");
}

void complex_key_rejected() {
  yaml_session s(with());
  for (const char* doc : {"? [a, b]\n: c\n", "{[a]: x}", "{{a: 1}: x}", "[a]: b\n", "{a: 1}: x\n", "? {a: 1}\n: x\n",
                          "[[a]: b]", "[{a: 1}: b]"}) {
    error e = failure(s, doc);
    require(e.code == status_syntax && std::string(e.what()) == "YAML complex key");
  }
  // Explicit scalar keys are fine.
  require(tape(s, "? a\n: b\n") == "{\"a\":\"b\"}");
  require(tape(s, "? a\n") == "{\"a\":null}");
}

void depth_limit() {
  yaml_session s(with());
  for (int depth : {128, 129}) {
    std::string flow = std::string(size_t(depth), '[') + std::string(size_t(depth), ']');
    std::string block;
    for (int i = 0; i < depth; ++i) block += std::string(size_t(i) * 2, ' ') + "k:\n";
    block += std::string(size_t(depth) * 2, ' ') + "v\n";
    if (depth == 128) {
      require(node_count(decode(s, flow).view) == 128);
      require(node_count(decode(s, block).view) == 2 * 128 + 1);
    } else {
      require(failure(s, flow).code == status_limit);
      require(failure(s, block).code == status_limit);
    }
  }
  yaml_session shallow(with({}, myna::value_limits{UINT32_MAX, UINT32_MAX, 2}));
  require(tape(shallow, "[[1]]") == "[[1]]");
  require(failure(shallow, "[[[1]]]").code == status_limit);
}

void input_and_owned_strings() {
  yaml_session s(with());
  const std::string doc = "- 0123456789abcdef\n- 0123456789abcdefg\n- \"\\u00e9t\\u00e9 quoted, longer\"\n- |\n  literal block text\n"
                          "- \"\\L grows\"\n";
  auto d = decode(s, doc);
  const auto* n = d.view.words;
  require(render(d.view) == "[\"0123456789abcdef\",\"0123456789abcdefg\",\"\xC3\xA9t\xC3\xA9 quoted, longer\",\"literal block text\\n\",\"\xE2\x80\xA8 grows\"]");
  require(ct::kind(n[ordinal(d.view, 1)]) == ct::kind_string && ct::field(n[ordinal(d.view, 1)]) == 16);
  require(!(n[ordinal(d.view, 1)] & ct::owned_flag));
  require(!(n[ordinal(d.view, 2)] & ct::owned_flag));
  // Shrinking quoted/block text stays in filtered input; text that grows into rapidyaml's
  // separate arena still needs owned pool storage. Both carry an independent raw-span word.
  for (size_t i : {size_t(3), size_t(4)}) {
    size_t at = ordinal(d.view, i);
    require((n[at] & ct::wide_flag) && !(n[at] & ct::owned_flag));
    require(lexical_span(d.view, at) == 0);
  }
  require((n[ordinal(d.view, 5)] & (ct::wide_flag | ct::owned_flag)) == (ct::wide_flag | ct::owned_flag));
  require(lexical_span(d.view, ordinal(d.view, 5)) == 0);
  require(node_text(d.view, ordinal(d.view, 5)) == "\xE2\x80\xA8 grows");
  require((n[ordinal(d.view, 1)] & ct::ascii_flag) && !(n[ordinal(d.view, 3)] & ct::ascii_flag));
  const std::string expected = render(d.view);
  truffledata::tape::index_memory allocations(SIZE_MAX, std::pmr::new_delete_resource());
  truffledata::tape::block_arena copies(&allocations);
  require(ct::validate(d.view) == nullptr);
  require(render(ct::to_myna(d.view, &copies).base) == expected);
  auto pristine = decode(s, doc, yaml_session::retain_source);
  require(render(pristine.view) == expected && ct::validate(pristine.view) == nullptr);
  require(render(ct::to_myna(pristine.view, &copies).base) == expected);
  for (size_t i : {size_t(3), size_t(4), size_t(5)})
    require(pristine.view.words[ordinal(pristine.view, i)] & ct::owned_flag);
  uint64_t quoted = lexical_span(pristine.view, ordinal(pristine.view, 3));
  uint64_t block = lexical_span(pristine.view, ordinal(pristine.view, 4));
  require(std::string(pristine.source + (quoted >> 32), uint32_t(quoted)) == "\"\\u00e9t\\u00e9 quoted, longer\"");
  require(std::string(pristine.source + (block >> 32), uint32_t(block)) == "|\n  literal block text");
}

std::string span(const yaml_session::document_view& d, size_t node) {
  uint64_t s = lexical_span(d.view, ordinal(d.view, node));
  if (!s) return "";
  return std::string(d.source + (s >> 32), uint32_t(s));
}

void lexical_spans_plain_always_quoted_with_retain_source() {
  yaml_session s(with());
  const std::string doc = "plain: 0x1F\n'single': 'it''s'\n\"double\": \"a\\tb\"\nmulti: one\n  two\nblock: |\n  text\n"
                          "flow: [1, two , +3]\nempty:\n";
  auto d = decode(s, doc);
  require(render(d.view) ==
          "{\"plain\":31,\"single\":\"it's\",\"double\":\"a\\tb\",\"multi\":\"one two\",\"block\":\"text\\n\",\"flow\":[1,\"two\",3],"
          "\"empty\":null}");
  require(span(d, 1) == "plain" && span(d, 2) == "0x1F");
  require(span(d, 3) == "" && span(d, 4) == "");      // quoted: filtered in place
  require(span(d, 5) == "" && span(d, 6) == "");
  require(span(d, 7) == "multi" && span(d, 8) == "");  // multi-line plain
  require(span(d, 9) == "block" && span(d, 10) == "");
  require(span(d, 11) == "flow" && span(d, 13) == "1" && span(d, 14) == "two" && span(d, 15) == "+3");
  require(span(d, 16) == "empty" && lexical_span(d.view, ordinal(d.view, 17)) == 0);
  require(lexical_span(d.view, ordinal(d.view, 0)) == 0 && lexical_span(d.view, ordinal(d.view, 12)) == 0);  // collections carry no span
  require(!(d.flags & doc_source_retained) && d.source != nullptr);

  auto r = decode(s, doc, yaml_session::retain_source);
  require(r.flags & doc_source_retained);
  require(span(r, 2) == "0x1F");
  require(span(r, 3) == "'single'" && span(r, 4) == "'it''s'");
  require(span(r, 5) == "\"double\"" && span(r, 6) == "\"a\\tb\"");
  require(span(r, 8) == "one\n  two");
  require(span(r, 10) == "|\n  text");
  // Comments after scalars do not widen spans.
  auto c = decode(s, "- x # note\n- y\n# trailing\n- z\n");
  require(span(c, 1) == "x" && span(c, 2) == "y" && span(c, 3) == "z");
  auto f = decode(s, "- a\n- folded: >\n    one\n    two\n", yaml_session::retain_source);
  require(span(f, 4) == ">\n    one\n    two");
}

void multiple_documents_rejected_by_decode(const std::vector<std::string>& fixtures) {
  yaml_session s(with());
  for (const char* doc : {"a\n---\nb\n", "--- a\n--- b\n", "a\n...\n---\nb\n", "---\n---\n"}) {
    error e = failure(s, doc);
    require(e.code == status_multiple_documents);
  }
  require(tape(s, "--- a\n...\n") == "\"a\"");
  for (const auto& path : fixtures)
    if (basename(path) == "records-stream.yaml") require(failure(s, slurp(path)).code == status_multiple_documents);
}
}  // namespace

int main(int argc, char** argv) {
  std::vector<std::string> fixtures(argv + 1, argv + argc);
  try {
    tape_matches_reference_builder(fixtures);
    values();
    duplicate_keys_after_resolution();
    complex_key_rejected();
    depth_limit();
    input_and_owned_strings();
    lexical_spans_plain_always_quoted_with_retain_source();
    multiple_documents_rejected_by_decode(fixtures);
  } catch (const std::exception& e) {
    std::fprintf(stderr, "decoder_test failure: %s\n", e.what());
    return 1;
  }
  std::printf("decoder checks passed\n");
  return 0;
}
