// Multi-document streams (plan Task 3, stream_test): chunk-boundary independence, document
// boundaries, truncation, failure, backpressure and limits, steady-state allocation, the C ABI, and
// allocation failure injected into streams, anchors, aliases, tags and merges.
//
// Usage: stream_test <fixture.yaml>...   (yaml/native/testdata)
#include "yaml/native/api.h"
#include "yaml/native/session.h"
#include "yaml/native/stream.h"
#include "yaml/native/testing.h"

#include <cstdio>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>

using namespace truffledata::yaml;
using truffledata::yaml::testing::fault_memory;
using truffledata::yaml::testing::render;

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

struct run {
  std::vector<std::string> documents;
  int32_t status = status_ok;  // the terminal status: ok after exhausting a finished stream
};

// Feeds `text` in `chunk`-byte pieces (0: all at once) and renders every document.
run documents(session& s, const std::string& text, size_t chunk, size_t max_document = size_t(1) << 20,
              size_t buffer = size_t(1) << 20) {
  run r;
  stream st(s, max_document, buffer);
  size_t fed = 0;
  auto drain = [&]() {
    for (;;) {
      session::document_view d;
      int32_t status = st.next(&d);
      if (status == status_stream_empty) return true;
      if (status != status_ok) {
        r.status = status;
        return false;
      }
      r.documents.push_back(render(d.view));
    }
  };
  while (fed < text.size()) {
    size_t take = chunk ? std::min(chunk, text.size() - fed) : text.size() - fed;
    size_t accepted = st.feed(reinterpret_cast<const uint8_t*>(text.data()) + fed, take);
    fed += accepted;
    if (!drain()) return r;
  }
  st.finish();
  drain();
  return r;
}

void stream_split_matches_whole_parse(const std::vector<std::string>& fixtures) {
  session s{session_options{}};
  std::vector<std::string> inputs;
  for (const auto& path : fixtures)
    if (basename(path) == "records-stream.yaml") inputs.push_back(slurp(path));
  for (const auto& path : fixtures) {
    if (basename(path) != "config.yaml") continue;
    std::string config = slurp(path), many;
    for (int i = 0; i < 64; ++i) many += "---\n" + config;
    inputs.push_back(many);
  }
  inputs.push_back(
      "# leading comment\n%YAML 1.2\n%TAG !e! tag:e.com,2000:\n--- !e!x\na: |\n  ---\n  ...\n  text\nb: [1,\n  2]\n...\n"
      "# between\n--- >\n folded\n...\n%YAML 1.2\n---\n...\n--- \"quoted\n  ---  continued\"\n---\tx\n---\n- &a 1\n- *a\n"
      "...   # trailing comment\nbare: document\n");
  require(inputs.size() == 3);
  size_t total = 0;
  for (const auto& text : inputs) {
    run whole = documents(s, text, 0);
    require(whole.status == status_ok && !whole.documents.empty());
    for (size_t chunk : {size_t(1), size_t(7), size_t(64), size_t(4096), size_t(1000000)}) {
      run split = documents(s, text, chunk);
      require(split.status == status_ok && split.documents == whole.documents);
    }
    total += whole.documents.size();
  }
  run edge = documents(s, inputs[2], 0);
  require(edge.documents.size() == 7);
  require(edge.documents[0] == "{\"a\":\"---\\n...\\ntext\\n\",\"b\":[1,2]}");
  require(edge.documents[1] == "\"folded\\n\"");
  require(edge.documents[2] == "null");
  require(edge.documents[3] == "\"quoted ---  continued\"");
  require(edge.documents[4] == "\"x\"");
  require(edge.documents[5] == "[1,null]");
  require(edge.documents[6] == "{\"bare\":\"document\"}");
  require(documents(s, inputs[0], 0).documents.size() == 1000);
  require(documents(s, inputs[1], 0).documents.size() == 64);
  std::printf("stream_split_matches_whole_parse: %zu documents, chunks of 1, 7, 64, 4096 and 1000000 bytes\n", total);
}

void boundaries() {
  session s{session_options{}};
  // `---` inside an indented block scalar is content; at column 0 it ends the document.
  require(documents(s, "a: |\n  ---\n  x\n---\nb\n", 0).documents ==
          std::vector<std::string>({"{\"a\":\"---\\nx\\n\"}", "\"b\""}));
  require(documents(s, "--- |\nline\n---\nnext\n", 0).documents == std::vector<std::string>({"\"line\\n\"", "\"next\""}));
  // Markers need a following space, tab, line break or the end of input.
  require(documents(s, "---word\n", 0).documents == std::vector<std::string>({"\"---word\""}));
  require(documents(s, "a\n---", 0).documents == std::vector<std::string>({"\"a\"", "null"}));
  // Comment-only documents are skipped; an empty stream has no documents.
  require(documents(s, "# nothing\n", 0).documents.empty());
  require(documents(s, "", 0).documents.empty());
  require(documents(s, "a\n...\n# only a comment\n", 0).documents == std::vector<std::string>({"\"a\""}));
  // A directive after an open document without `...` stays with it: rapidyaml rejects it.
  require(documents(s, "---\nkey: value\n%YAML 1.2\n---\n", 0).status == status_syntax);
  // CRLF line endings.
  require(documents(s, "a: 1\r\n---\r\nb: 2\r\n", 0).documents == std::vector<std::string>({"{\"a\":1}", "{\"b\":2}"}));
}

void truncation_and_failure() {
  session s{session_options{}};
  // The finished input ends inside the final document.
  run r = documents(s, "a: 1\n---\nb: [1, 2", 0);
  require(r.documents.size() == 1 && r.status == status_stream_truncated);
  require(documents(s, "a: 1\n---\nk: \"unterminated", 3).status == status_stream_truncated);
  // A final document cut inside a UTF-8 sequence.
  require(documents(s, "a: 1\n---\nb: \xC3", 0).status == status_stream_truncated);
  // A syntax error mid-stream fails that document, and the stream stays failed.
  stream st(s, 1 << 20, 1 << 20);
  std::string text = "a\n---\nb: : c\n---\nd\n";
  st.feed(reinterpret_cast<const uint8_t*>(text.data()), text.size());
  st.finish();
  session::document_view d;
  require(st.next(&d) == status_ok && render(d.view) == "\"a\"");
  require(st.next(&d) == status_syntax && st.last_error() && st.last_error()->line > 0);
  require(st.next(&d) == status_syntax && st.failed());
  require(st.feed(reinterpret_cast<const uint8_t*>("x"), 1) == 0);
  // Semantic failures report their own status, not truncation.
  require(documents(s, "a\n---\n{k: 1, k: 2}", 0).status == status_syntax);
  require(documents(s, "a\n---\n[" + std::string(129, '[') + "]", 0).status != status_ok);
}

void backpressure_and_limits() {
  session s{session_options{}};
  std::string text;
  for (int i = 0; i < 50; ++i) text += "---\nitem: " + std::to_string(i) + "\n";
  // A 64-byte buffer accepts a little at a time; documents still come out in order.
  stream st(s, 1 << 20, 64);
  std::vector<std::string> out;
  size_t fed = 0;
  bool limited = false;
  while (fed < text.size()) {
    size_t accepted = st.feed(reinterpret_cast<const uint8_t*>(text.data()) + fed, text.size() - fed);
    limited |= accepted < text.size() - fed;
    fed += accepted;
    session::document_view d;
    while (st.next(&d) == status_ok) out.push_back(render(d.view));
  }
  st.finish();
  session::document_view d;
  while (st.next(&d) == status_ok) out.push_back(render(d.view));
  require(limited && out.size() == 50 && out[49] == "{\"item\":49}");
  // A document larger than max_document_bytes is TD_YAML_LIMIT.
  require(documents(s, "---\nsmall\n---\n" + std::string(200, 'x') + "\n---\nafter\n", 0, 100).status == status_limit);
  require(documents(s, std::string(200, 'x'), 7, 100).status == status_limit);
}

void steady_state() {
  session s{session_options{}};
  stream st(s, 1 << 20, 1 << 16);
  const std::string doc = "---\nid: 1007\nname: &n item\ntags: [*n, !!str 2, !x y]\nnote: \"text \\u00e9\"\n";
  size_t allocations = 0;
  for (int i = 0; i < 10000; ++i) {
    size_t accepted = st.feed(reinterpret_cast<const uint8_t*>(doc.data()), doc.size());
    require(accepted == doc.size());
    session::document_view d;
    int32_t status = st.next(&d);
    if (i == 0) {
      require(status == status_stream_empty);  // the first document completes with the second `---`
      continue;
    }
    require(status == status_ok && d.view.node_count == 12);
    if (i == 3) allocations = s.memory().allocations();
    if (i > 3) require(d.index_allocations == 0);
  }
  require(s.memory().allocations() == allocations && st.documents() == 9999);
}

void c_abi() {
  YamlOptionsStruct o{};
  o.max_input_bytes = 1 << 20;
  o.max_index_bytes = 1 << 20;
  o.max_nodes = 100000;
  o.max_text_bytes = 1 << 20;
  o.max_aliases = 100;
  o.max_alias_expansion = 1000000;
  o.max_depth = 128;
  void* s = td_yaml_session_create(&o);
  require(s);
  void* st = td_yaml_stream_create(s, 1 << 20, 1 << 20);
  require(st && !td_yaml_stream_create(s, 0, 1));
  const std::string text = "%TAG !e! tag:e.com,2000:\n--- !e!m\nk: &a [1]\nv: *a\n...\n--- second\n";
  require(td_yaml_stream_feed(st, reinterpret_cast<const uint8_t*>(text.data()), text.size()) == text.size());
  YamlResult r{};
  require(td_yaml_stream_next(st, &r) == TD_YAML_OK);
  require(!r.owner && r.view && r.tag_count == 1 && r.anchor_count == 1 && r.alias_count == 1 && r.directive_count == 1);
  auto* tags = static_cast<const YamlSpan*>(r.tags);
  require(std::string(reinterpret_cast<const char*>(r.strings) + tags[0].offset, tags[0].length) == "tag:e.com,2000:m");
  auto* anchors = static_cast<const YamlAnchor*>(r.anchors);
  require(anchors[0].node == 2 && std::string(reinterpret_cast<const char*>(r.strings) + anchors[0].offset, anchors[0].length) == "a");
  require(static_cast<const uint64_t*>(r.alias_targets)[0] == 2);
  require(r.document_flags == (TD_YAML_DOC_PRESENT | TD_YAML_DOC_EXPLICIT_START | TD_YAML_DOC_EXPLICIT_END));
  // The stream's current document can be retained like any session document.
  YamlResult kept{};
  require(td_yaml_session_retain(s, &kept) == TD_YAML_OK && kept.anchor_count == 1 && kept.alias_count == 1);
  require(td_yaml_stream_next(st, &r) == TD_YAML_STREAM_EMPTY);
  td_yaml_stream_finish(st);
  require(td_yaml_stream_next(st, &r) == TD_YAML_OK && render(*static_cast<const myna_value_view*>(r.view)) == "\"second\"");
  require(td_yaml_stream_next(st, &r) == TD_YAML_STREAM_EMPTY);
  require(render(*static_cast<const myna_value_view*>(kept.view)) == "{\"k\":[1],\"v\":null}");
  td_yaml_stream_release(st);
  // A failing document publishes its error.
  st = td_yaml_stream_create(s, 1 << 20, 1 << 20);
  const std::string bad = "a\n---\n[1, 2\n";
  td_yaml_stream_feed(st, reinterpret_cast<const uint8_t*>(bad.data()), bad.size());
  td_yaml_stream_finish(st);
  require(td_yaml_stream_next(st, &r) == TD_YAML_OK);
  require(td_yaml_stream_next(st, &r) == TD_YAML_STREAM_TRUNCATED && r.error_line > 0 && r.error_message_length > 0 && !r.view);
  td_yaml_stream_release(st);
  td_yaml_session_release(s);
  td_yaml_release(&kept);
}

// Every upstream allocation of a sequence touching streams, anchors, aliases, tags, %TAG directives
// and merges can fail; everything unwinds and frees.
void allocation_failures() {
  std::string text;
  for (int i = 0; i < 40; ++i) {
    text += "%TAG !e" + std::to_string(i) + "! tag:e.com,2000:\n--- !e" + std::to_string(i) + "!doc\n";
    text += "base: &b {x: 1, y: [1, 2]}\nm: {<<: *b, z: !!binary aGVsbG8=}\n";
    for (int k = 0; k < 20; ++k) text += "t" + std::to_string(k) + ": !tag" + std::to_string(k) + " &a" + std::to_string(k) + " v\n";
    for (int k = 0; k < 20; ++k) text += "r" + std::to_string(k) + ": *a" + std::to_string(k) + "\n";
    text += "...\n";  // directives of the next document need an explicit end
  }
  // One large document whose anchor, alias, tag and string tables outgrow arena blocks.
  text += "---\n";
  for (int k = 0; k < 3000; ++k) text += "a" + std::to_string(k) + ": !t" + std::to_string(k) + " &n" + std::to_string(k) + " x\n";
  for (int k = 0; k < 3000; ++k) text += "b" + std::to_string(k) + ": *n" + std::to_string(k) + "\n";
  policy p;
  p.merge_keys = true;
  session_options o;
  o.rules = p;
  auto sequence = [&](fault_memory& memory) {
    session s(o, &memory);
    stream st(s, 1 << 20, 4096, &memory);
    size_t fed = 0, docs = 0;
    while (fed < text.size()) {
      fed += st.feed(reinterpret_cast<const uint8_t*>(text.data()) + fed, std::min<size_t>(1000, text.size() - fed));
      session::document_view d;
      int32_t status;
      while ((status = st.next(&d)) == status_ok) ++docs;
      if (status == status_allocation) throw std::bad_alloc();
      require(status == status_stream_empty);
    }
    st.finish();
    session::document_view d;
    int32_t status;
    while ((status = st.next(&d)) == status_ok) ++docs;
    if (status == status_allocation) throw std::bad_alloc();
    require(docs == 41);
  };
  fault_memory counting;
  sequence(counting);
  size_t total = counting.calls;
  std::printf("allocation_failures: counting run %zu allocations, %zu live bytes after\n", total, counting.live);
  require(counting.live == 0 && total > 10);
  for (size_t i = 0; i < total; ++i) {
    fault_memory memory(i);
    bool rejected = false;
    try {
      sequence(memory);
    } catch (const std::bad_alloc&) {
      rejected = true;
    }
    require(rejected && memory.live == 0);
  }
  std::printf("allocation_failures: %zu upstream allocations, each failed once\n", total);
}
}  // namespace

int main(int argc, char** argv) {
  std::vector<std::string> fixtures(argv + 1, argv + argc);
  try {
    stream_split_matches_whole_parse(fixtures);
    boundaries();
    truncation_and_failure();
    backpressure_and_limits();
    steady_state();
    c_abi();
    allocation_failures();
  } catch (const std::exception& e) {
    std::fprintf(stderr, "stream_test failure: %s\n", e.what());
    return 1;
  }
  std::printf("YAML stream checks passed\n");
  return 0;
}
