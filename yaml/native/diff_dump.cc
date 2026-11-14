// One side of the rapidyaml / Rust parser differential (//yaml/native:differential_test): built
// twice from this source, as :diff_dump (rapidyaml) and :diff_dump_rs (granit-parser through
// rust_source.h), because one binary holds one parser. Each prints, per input and policy, a
// canonical description of what the decoder publishes: per document the yaml-test-suite events
// (tape_events.h), the resolved values, and per node its kind, style, key kind, tag URI, anchor,
// alias target ordinal and lexical span, then the side tables, document flags and version; or the
// error status and, for handler (policy) errors, the message.
//
// Lines starting with `~` are informational (parser messages and error positions, which differ by
// design between the parsers); the comparator reports but does not fail on them.
//
// Usage: diff_dump --list=<file of paths> [--generated=N --seed=S]
//   Paths ending in `/session/<name>` are fuzz corpus files: two control bytes, then the document.
#include "yaml/native/session.h"
#include "yaml/native/stream.h"
#include "yaml/native/tape_events.h"
#include "yaml/native/testing.h"

#include <cstdio>
#include <fstream>
#include <sstream>
#include <string>
#include <unordered_map>
#include <vector>

using namespace truffledata::yaml;
using yaml_session = truffledata::yaml::session;

namespace {

std::string slurp(const std::string& path) {
  std::ifstream in(path, std::ios::binary);
  std::ostringstream out;
  out << in.rdbuf();
  return out.str();
}

struct mode {
  const char* name;
  session_options options;
  uint32_t flags;
};

// fast_path_test's seven policies, plus CORE with the retained source (lexical spans of quoted,
// block and multi-line scalars).
std::vector<mode> modes() {
  std::vector<mode> out;
  auto add = [&](const char* name, uint32_t flags, auto edit) {
    session_options o;
    edit(o);
    out.push_back({name, o, flags});
  };
  add("core", 0, [](session_options&) {});
  add("core+merge", 0, [](session_options& o) { o.rules.merge_keys = true; });
  add("core+last-wins", 0, [](session_options& o) { o.rules.last_wins = true; });
  add("core+pairs", 0, [](session_options& o) {
    o.rules.complex_keys_pairs = true;
    o.rules.duplicate_keys_pairs = true;
  });
  add("core+honor", 0, [](session_options& o) { o.rules.honor_version = true; });
  add("yaml11", 0, [](session_options& o) { o.rules.schema = schema::yaml11; });
  add("failsafe+pairs", 0, [](session_options& o) {
    o.rules.schema = schema::failsafe;
    o.rules.complex_keys_pairs = true;
    o.rules.duplicate_keys_pairs = true;
  });
  add("core+source", yaml_session::retain_source, [](session_options&) {});
  return out;
}

void describe(const yaml_session::document_view& d, std::string& out) {
  if (const char* problem = ct::validate(d.view)) {
    out += "invalid tape: ";
    out += problem;
    out += '\n';
    return;
  }
  out += "doc flags=" + std::to_string(d.flags) + " version=" + std::to_string(d.version) + "\n";
  testing::render_document(d, out);
  out += "values " + testing::render(d.view) + "\n";
  // Word offsets differ between the parsers where text is stored differently (filtered in place
  // vs owned), so nodes are numbered in tape order and references use those ordinals.
  std::unordered_map<uint64_t, size_t> ordinal;
  size_t n = 0;
  for (uint64_t i = 0; i < d.view.word_count; i = ct::step(i, d.view.words[i])) ordinal[i] = n++;
  auto node_of = [&](uint64_t word) {
    auto found = ordinal.find(word);
    return found == ordinal.end() ? std::string("?") : std::to_string(found->second);
  };
  for (uint64_t i = 0; i < d.view.word_count; i = ct::step(i, d.view.words[i])) {
    uint64_t w = d.view.words[i];
    uint32_t meta = d.meta[i];
    out += "node " + std::to_string(ordinal[i]) + " kind=" + std::to_string(ct::kind(w)) +
           " style=" + std::to_string(meta & meta_style_mask) +
           " keykind=" + std::to_string((meta & meta_key_kind_mask) >> meta_key_kind_shift);
    if (meta & meta_anchored) out += " anchored";
    if (meta & meta_alias) {
      uint32_t alias = meta >> meta_tag_shift;
      out += " alias=" + (alias < d.alias_targets.size() ? node_of(d.alias_targets[alias]) : std::string("?"));
    } else if (uint32_t tag = meta >> meta_tag_shift) {
      out += " tag=";
      if (tag <= d.tags.size()) out += d.strings.substr(d.tags[tag - 1].offset, d.tags[tag - 1].length);
    }
    if (ct::kind(w) == ct::kind_string) {
      out += " text=";
      // One line per node: line breaks in the text are escaped.
      for (char c : ct::text(d.view, i)) {
        if (c == '\n') out += "\\n";
        else if (c == '\\') out += "\\\\";
        else out += c;
      }
    }
    if (uint64_t span = lexical_span(d.view, i)) out += " span=" + std::to_string(span >> 32) + ":" + std::to_string(uint32_t(span));
    out += '\n';
  }
  for (size_t i = 0; i < d.anchors.size(); ++i) {
    const anchor_entry& a = d.anchors[i];
    out += "anchor " + node_of(a.node) + " " + std::string(d.strings.substr(a.offset, a.length)) + "\n";
  }
  for (size_t i = 0; i < d.directives.size(); ++i) {
    const directive_entry& t = d.directives[i];
    out += "directive " + std::string(d.strings.substr(t.handle_offset, t.handle_length)) + " " +
           std::string(d.strings.substr(t.prefix_offset, t.prefix_length)) + "\n";
  }
}

void describe_error(const error& e, std::string& out) {
  out += "error " + std::to_string(e.code) + (e.parser ? " parser" : "") + "\n";
  // Parser messages are each parser's own; handler (policy) messages must agree.
  out += e.parser ? "~message " : "message ";
  out += e.what();
  out += "\n~at " + std::to_string(e.line) + ":" + std::to_string(e.column) + "\n";
}

// documents(byte[]): the stream over the complete buffer, as the conformance target decodes it.
std::string decode_all(yaml_session& s, const std::string& yaml, uint32_t flags) {
  std::string out;
  stream st(s, size_t(64) << 20, size_t(64) << 20);
  st.feed(reinterpret_cast<const uint8_t*>(yaml.data()), yaml.size());
  st.finish();
  for (;;) {
    yaml_session::document_view d;
    int32_t status = st.next(&d, flags);
    if (status == status_stream_empty) break;
    if (status != status_ok) {
      if (const auto& e = st.last_error()) describe_error(*e, out);
      else out += "error " + std::to_string(status) + "\n";
      break;
    }
    describe(d, out);
  }
  return out;
}

// ---- generated documents ---------------------------------------------------------------------

class generator {
 public:
  explicit generator(uint64_t seed) : s_(seed | 1) {}

  std::string document() {
    out_.clear();
    anchors_ = 0;
    tag_handle_ = false;
    bool marker = false;
    if (chance(8)) out_ += pick({"%YAML 1.1\n", "%YAML 1.2\n"}), marker = true;
    if (chance(8)) out_ += "%TAG !e! tag:example.com,2000:\n", tag_handle_ = true, marker = true;
    if (marker || chance(20)) out_ += "---";
    switch (below(10)) {
      case 0: case 1: case 2: case 3: case 4:
        if (out_.size() && out_.back() == '-') out_ += '\n';
        block_map(0, 0);
        break;
      case 5: case 6:
        if (out_.size() && out_.back() == '-') out_ += '\n';
        block_seq(0, 0);
        break;
      case 7:
        out_ += out_.size() ? " " : "";
        out_ += flow(0) + "\n";
        break;
      case 8:
        out_ += out_.size() ? " " : "";
        out_ += props('s') + scalar(false) + "\n";
        break;
      default:
        out_ += out_.size() ? " " : "";
        out_ += block_scalar(0);
        break;
    }
    if (chance(10)) out_ += "...\n";
    if (chance(5)) out_ += "--- second\n";
    if (chance(12)) mutate();
    return out_;
  }

 private:
  uint64_t next() {
    s_ ^= s_ << 13;
    s_ ^= s_ >> 7;
    s_ ^= s_ << 17;
    return s_;
  }
  size_t below(size_t n) { return size_t(next() % n); }
  bool chance(int percent) { return below(100) < size_t(percent); }
  std::string pick(std::initializer_list<const char*> options) { return *(options.begin() + below(options.size())); }

  // Mostly tags that fit the node (`s` scalar, `m` mapping, `q` sequence, `a` either); a few
  // that do not, so tag validation errors stay covered.
  std::string tag(char kind) {
    if (tag_handle_ && chance(30)) return "!e!thing";
    if (chance(10)) return pick({"!!int", "!!float", "!!bool", "!!null", "!!binary", "!!timestamp", "!!map", "!!seq", "!!set", "!!omap"});
    switch (kind) {
      case 's': return pick({"!!str", "!local", "!", "!<tag:x.org,2000:v>", "!<!verbatim>", "!e%21x"});
      case 'm': return pick({"!!map", "!local", "!<tag:x.org,2000:m>"});
      case 'q': return pick({"!!seq", "!local", "!<tag:x.org,2000:q>"});
      default: return pick({"!local", "!<tag:x.org,2000:v>"});
    }
  }
  std::string props(char kind = 'a') {
    std::string p;
    if (chance(8)) p += tag(kind) + " ";
    if (chance(10)) p += "&a" + std::to_string(++anchors_) + " ";
    return p;
  }
  std::string alias() {
    if (!anchors_ || chance(1)) return "*missing";
    return "*a" + std::to_string(1 + below(size_t(anchors_)));
  }
  std::string plain() {
    return pick({"a", "b", "key", "value", "x y", "hello world", "1", "-2", "3.5", "0x1F", "0o17", "1e3", ".inf",
                 "-.inf", ".nan", "~", "null", "Null", "true", "False", "yes", "no", "on", "off", "<<", "=",
                 "2001-12-14", "12:30", "\xC3\xA9", "\xE6\x97\xA5\xE6\x9C\xAC", "a-b", "a_b", "1_000", "+12", "0.1",
                 "007", "18446744073709551616", "9223372036854775807", "-0", "1.", "a#b", "a: b", "x:y", "c, d",
                 "aGVsbG8=", "2001-12-14t21:59:43.10-05:00"});
  }
  std::string quoted() {
    return pick({"'it''s'", "'a b'", "''", "'\xC3\xA9'", "\"a\\tb\"", "\"\\u00e9\"", "\"\\x41\"", "\"\\ud83d\\ude00\"",
                 "\"\\ud800\"", "\"\"", "\"q\\\"x\"", "\"a\\\\b\"", "\"\\N\\_\\L\\P\"", "\"\\U0001F600\"", "\"1\"",
                 "'true'", "\"null\"", "\"a\\\n  b\"", "'x\n  y'", "\"tab\there\""});
  }
  std::string scalar(bool flow_context) {
    std::string s = chance(30) ? quoted() : plain();
    // Flow context: plain scalars must not hold flow indicators or ": ".
    if (flow_context && s.find_first_of(",[]{}") != std::string::npos) s = "a";
    if (flow_context && s.find(": ") != std::string::npos) s = "b";
    return s;
  }
  std::string key() {
    switch (below(20)) {
      case 0: return quoted();
      case 1: return "~";
      case 2: return std::to_string(below(4));
      case 3: return alias();
      case 4: return "<<";
      case 5: return "[k1, k2]";
      case 6: return "{k: v}";
      default: {
        std::string k = pick({"a", "b", "c", "id", "name", "x", "y", "1", "0x1", "true", "null", "\xC3\xA9"});
        return chance(30) ? k + std::to_string(below(3)) : k;
      }
    }
  }
  std::string block_scalar(int indent) {
    std::string header = pick({"|", "|-", "|+", ">", ">-", ">+", "|2", ">1"});
    std::string body;
    int lines = 1 + int(below(3));
    for (int i = 0; i < lines; ++i) {
      body += std::string(size_t(indent + 2), ' ') + pick({"line", "more text", "  indented", "x: y", "# not comment"}) + "\n";
      if (chance(20)) body += "\n";
    }
    return header + "\n" + body;
  }
  std::string flow(int depth) {
    if (depth > 4 || chance(25)) return chance(15) ? alias() : props('s') + scalar(true);
    const bool sequence = chance(50);
    std::string out = props(sequence ? 'q' : 'm');
    int n = int(below(4));
    if (sequence) {
      out += "[";
      for (int i = 0; i < n; ++i) {
        if (i) out += ", ";
        out += chance(10) ? scalar(true) + ": " + flow(depth + 1) : flow(depth + 1);
      }
      if (chance(5)) out += ",";
      return out + "]";
    }
    out += "{";
    for (int i = 0; i < n; ++i) {
      if (i) out += ", ";
      std::string k = chance(10) ? alias() : scalar(true);
      out += chance(8) ? k : k + ": " + flow(depth + 1);
    }
    return out + "}";
  }
  void comment() {
    if (chance(5)) out_ += "  # note";
  }
  // The value after `key:` or `- ` at `indent`.
  void block_value(int indent, int depth, bool in_seq) {
    switch (depth > 5 ? 0 : below(12)) {
      case 0: case 1: case 2: case 3:
        out_ += " " + props('s') + scalar(false);
        comment();
        out_ += "\n";
        return;
      case 4:
        out_ += " " + alias() + "\n";
        return;
      case 5:
        out_ += "\n";
        return;
      case 6:
        out_ += " " + flow(1) + "\n";
        return;
      case 7:
        out_ += " " + props('s') + block_scalar(indent);
        return;
      case 8: case 9: {
        std::string p = props('m');
        out_ += p.empty() ? "\n" : " " + p.substr(0, p.size() - 1) + "\n";
        block_map(indent + 2, depth + 1);
        return;
      }
      default: {
        std::string p = props('q');
        out_ += p.empty() ? "\n" : " " + p.substr(0, p.size() - 1) + "\n";
        // Indentless sequences under a mapping key, sometimes.
        block_seq(in_seq || chance(70) ? indent + 2 : indent, depth + 1);
        return;
      }
    }
  }
  void block_map(int indent, int depth) {
    int n = 1 + int(below(4));
    for (int i = 0; i < n; ++i) {
      out_ += std::string(size_t(indent), ' ');
      if (chance(4)) {
        out_ += "? " + scalar(false) + "\n" + std::string(size_t(indent), ' ') + ":";
      } else {
        std::string k = key();
        out_ += k == "<<" && chance(70) ? "<<: " + alias() + "\n" : k + ":";
        if (out_.back() == '\n') continue;
      }
      block_value(indent, depth, false);
    }
  }
  void block_seq(int indent, int depth) {
    int n = 1 + int(below(4));
    for (int i = 0; i < n; ++i) {
      out_ += std::string(size_t(indent), ' ') + "-";
      if (chance(15)) {
        out_ += " " + key() + ": " + scalar(false) + "\n";
        continue;
      }
      block_value(indent, depth, true);
    }
  }
  void mutate() {
    if (out_.empty()) return;
    size_t at = below(out_.size());
    switch (below(3)) {
      case 0: out_.erase(at, 1); break;
      case 1: out_.insert(at, pick({"- ", ": ", "[", "]", "{", "}", ",", "&", "*", "!", "|", "'", "\"", "\t", "\n", "#", "%", "? "})); break;
      default: out_.insert(at, std::string(1 + below(3), ' ')); break;
    }
  }

  uint64_t s_;
  std::string out_;
  int anchors_ = 0;
  bool tag_handle_ = false;
};

}  // namespace

int main(int argc, char** argv) {
  std::string list;
  size_t generated = 0;
  uint64_t seed = 1;
  for (int i = 1; i < argc; ++i) {
    std::string arg = argv[i];
    if (arg.rfind("--list=", 0) == 0) list = arg.substr(7);
    else if (arg.rfind("--generated=", 0) == 0) generated = std::stoul(arg.substr(12));
    else if (arg.rfind("--seed=", 0) == 0) seed = std::stoull(arg.substr(7));
  }
  std::vector<std::pair<std::string, std::string>> inputs;
  {
    std::istringstream paths(slurp(list));
    std::string path;
    while (paths >> path) {
      std::string text = slurp(path);
      if (path.find("/session/") != std::string::npos) text = text.size() > 2 ? text.substr(2) : std::string();
      inputs.emplace_back(path, text);
    }
  }
  generator g(seed);
  for (size_t i = 0; i < generated; ++i) inputs.emplace_back("gen/" + std::to_string(i), g.document());
  for (const mode& m : modes()) {
    yaml_session s(m.options);
    for (const auto& input : inputs) {
      std::string out = "@@ " + input.first + " " + m.name + "\n";
      try {
        out += decode_all(s, input.second, m.flags);
      } catch (const std::exception& e) {
        out += "exception ";
        out += e.what();
        out += '\n';
      }
      std::fwrite(out.data(), 1, out.size(), stdout);
    }
  }
  // The generated inputs themselves, for reproducing a reported difference.
  for (size_t i = 0; i < inputs.size(); ++i) {
    if (inputs[i].first.rfind("gen/", 0) != 0) continue;
    std::string out = "## " + inputs[i].first + "\n" + inputs[i].second + "\n";
    std::fwrite(out.data(), 1, out.size(), stdout);
  }
  return 0;
}
