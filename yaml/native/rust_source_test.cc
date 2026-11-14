// Smoke cases for the opt-in Rust parser backend (rust_source.h, TD_YAML_PARSER_RUST): documents
// decode through granit-parser into the same handler and tape as with rapidyaml; errors keep their
// statuses. The full gates are the conformance and differential targets.
#include "yaml/native/rust_source.h"
#include "yaml/native/session.h"
#include "yaml/native/testing.h"

#include <cstdio>
#include <string>

#ifndef TD_YAML_PARSER_RUST
#error "rust_source_test must be built against //yaml/native:decoder_rs"
#endif

using namespace truffledata::yaml;
using yaml_session = truffledata::yaml::session;
using truffledata::yaml::testing::ordinal;
using truffledata::yaml::testing::render;

namespace {

session_options with(policy rules = {}) {
  session_options o;
  o.rules = rules;
  return o;
}
yaml_session::document_view decode(yaml_session& s, const std::string& text, uint32_t flags = 0) {
  return s.decode(reinterpret_cast<const uint8_t*>(text.data()), text.size(), flags);
}
std::string tape(yaml_session& s, const std::string& text) { return render(decode(s, text).view); }
// Equal tapes, or a failure naming both.
void expect(yaml_session& s, const std::string& text, const std::string& expected) {
  std::string actual = tape(s, text);
  if (actual != expected) throw std::runtime_error("tape of " + text + "\n  got      " + actual + "\n  expected " + expected);
}
error failure(yaml_session& s, const std::string& text) {
  try {
    decode(s, text);
  } catch (const error& e) {
    return e;
  }
  throw std::runtime_error("decode succeeded: " + text);
}
std::string spelled(const yaml_session::document_view& d, size_t n) {
  uint64_t span = lexical_span(d.view, ordinal(d.view, n));
  return std::string(d.source + (span >> 32), uint32_t(span));
}

void documents() {
  yaml_session s(with());
  expect(s, "a: 1\nb: [x, 2.5, true, null]\nc: {d: e}\n", "{\"a\":1,\"b\":[\"x\",f2.5,true,null],\"c\":{\"d\":\"e\"}}");
  expect(s, "- \"esc\\taped\"\n- 'it''s'\n- |\n  lit\n  two\n- >-\n  fold\n  ed\n- plain\n  multi\n",
         "[\"esc\\taped\",\"it's\",\"lit\\ntwo\\n\",\"fold ed\",\"plain multi\"]");
  expect(s, "", "null");
  expect(s, "# only a comment\n", "null");
  expect(s, "a:\n", "{\"a\":null}");
  // Aliases are REF words; anchors, tags and directives reach the side tables.
  auto d = decode(s, "%TAG !e! tag:example.com,2000:\n--- !e!m\na: &x !local v\nb: *x\n");
  require(d.anchors.size() == 1 && d.alias_targets.size() == 1 && d.directives.size() == 1 && d.tags.size() == 2);
  require(d.strings.substr(d.directives[0].handle_offset, d.directives[0].handle_length) == "!e!");
  require(d.strings.substr(d.tags[0].offset, d.tags[0].length) == "tag:example.com,2000:m");
  require(decode(s, "%YAML 1.1\n--- yes\n").version == ((1u << 16) | 1));
  // Escaped keys are owned copies that stay valid across batches (more than 256 records).
  std::string wide = "{";
  for (int i = 0; i < 400; ++i) wide += "\"k\\u00e9" + std::to_string(i) + "\": " + std::to_string(i) + ", ";
  require(ct::field(decode(s, wide + "}").view.words[0]) == 400);
  require(failure(s, wide + "\"k\\u00e942\": x}").code == status_syntax);
}

void spans() {
  yaml_session s(with());
  auto d = decode(s, "a: 'q'\nb: \"e\\n\"\nc: |\n  lit\n\nd: plain\n  multi\ne: x y\n", yaml_session::retain_source);
  require(spelled(d, 2) == "'q'" && spelled(d, 4) == "\"e\\n\"" && spelled(d, 6) == "|\n  lit");
  require(spelled(d, 8) == "plain\n  multi" && spelled(d, 10) == "x y");
}

void errors() {
  yaml_session s(with());
  error e = failure(s, "a: 1\nb: [1, 2\n");
  require(e.code == status_syntax && e.parser && e.line == 2 && e.column == 4);
  require(failure(s, "a\n---\nb\n").code == status_multiple_documents);
  require(failure(s, std::string(129, '[') + std::string(129, ']')).code == status_limit);
  require(failure(s, "a: *x\n").code == status_syntax);
  require(std::string(failure(s, "a: &x [*x]\n").what()) == "recursive YAML alias");
  // Duplicate keys: reported at the repeated key (#40), through the relocating re-decode.
  e = failure(s, "x: 0\na: 1\na: 2\n");
  require(e.code == status_syntax && !e.relocate && std::string(e.what()) == "duplicate YAML key: a");
  require(e.line == 3 && e.column == 1);
  e = failure(s, "{1: a, 0x1: b}");
  require(e.line == 1 && e.column == 8 && std::string(e.what()) == "duplicate YAML key: 1");
  // The session stays usable.
  require(tape(s, "[true]") == "[true]");
}

}  // namespace

int main() {
  try {
    documents();
    spans();
    errors();
  } catch (const std::exception& e) {
    std::fprintf(stderr, "rust_source_test failure: %s\n", e.what());
    return 1;
  }
  std::puts("rust_source_test passed");
  return 0;
}
