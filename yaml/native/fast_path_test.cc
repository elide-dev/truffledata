// The decoder's scalar fast paths (short untagged plain scalars, the core fast resolver, the key
// hash cache and the inline key set) against its general paths: every input decodes with the fast
// paths on and off (session::fast_paths, a test hook) to byte-identical results — nodes, owned
// text, every side table, document flags — or to the same error (status, message, line, column).
//
// Inputs: the probe fixtures, all 402 yaml-test-suite inputs and a generated corpus of scalars,
// keys and duplicate keys, each under several policies (CORE, merge keys, LAST_WINS, pairs for
// complex and duplicate keys, retained source, YAML_1_1 and FAILSAFE, where the fast paths stay off).
//
// Usage: fast_path_test <file.yaml>...
#include "yaml/native/session.h"
#include "yaml/native/stream.h"
#include "yaml/native/testing.h"

#include <cstdio>
#include <cstring>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>

using namespace truffledata::yaml;

namespace {
std::string slurp(const std::string& path) {
  std::ifstream in(path, std::ios::binary);
  std::ostringstream out;
  out << in.rdbuf();
  return out.str();
}

template <class T>
void append(std::string& out, const T* data, size_t count) {
  if (count) out.append(reinterpret_cast<const char*>(data), count * sizeof(T));
}

// Everything a decode publishes, as bytes (region payloads hold the region index and offset, which
// both runs share; region base pointers are the session buffer in both and are not compared).
std::string snapshot(const session::document_view& d) {
  std::string out;
  append(out, d.view.nodes, d.view.node_count);
  out += "|bytes|";
  append(out, d.view.bytes, d.view.byte_count);
  out += "|regions|" + std::to_string(d.view.region_count) + "|flags|" + std::to_string(d.view.flags);
  for (uint64_t r = 0; r < d.view.region_count; ++r) out += "|" + std::to_string(d.view.regions[r].length);
  out += "|lexical|";
  append(out, d.lexical.data(), d.lexical.size());
  out += "|hashes|";
  append(out, d.hashes.data(), d.hashes.size());
  out += "|meta|";
  append(out, d.meta.data(), d.meta.size());
  out += "|tags|";
  append(out, d.tags.data(), d.tags.size());
  out += "|anchors|";
  append(out, d.anchors.data(), d.anchors.size());
  out += "|aliases|";
  append(out, d.alias_targets.data(), d.alias_targets.size());
  out += "|directives|";
  append(out, d.directives.data(), d.directives.size());
  out += "|strings|";
  out.append(d.strings.data(), d.strings.size());
  out += "|doc|" + std::to_string(d.flags) + "|" + std::to_string(d.version);
  return out;
}

// All documents of `yaml` (documents(byte[]): the stream over the complete buffer).
std::string decode_all(session& s, const std::string& yaml, bool fast) {
  s.fast_paths(fast);
  stream st(s, size_t(64) << 20, size_t(64) << 20);
  std::string out;
  st.feed(reinterpret_cast<const uint8_t*>(yaml.data()), yaml.size());
  st.finish();
  for (;;) {
    session::document_view d;
    int32_t status = st.next(&d);
    if (status == status_stream_empty) break;
    if (status != status_ok) {
      out += "#error " + std::to_string(status);
      if (const auto& e = st.last_error())
        out += std::string(": ") + e->what() + " @" + std::to_string(e->line) + ":" + std::to_string(e->column);
      break;
    }
    out += "#doc " + snapshot(d);
  }
  return out;
}

// One session decode (zero or one document) with `flags`, e.g. retain_source.
std::string decode_one(session& s, const std::string& yaml, uint32_t flags, bool fast) {
  s.fast_paths(fast);
  try {
    return "#doc " + snapshot(s.decode(reinterpret_cast<const uint8_t*>(yaml.data()), yaml.size(), flags));
  } catch (const error& e) {
    return "#error " + std::to_string(e.code) + ": " + e.what() + " @" + std::to_string(e.line) + ":" +
           std::to_string(e.column);
  }
}

struct mode {
  const char* name;
  session_options options;
  uint32_t flags;
};

std::vector<mode> modes() {
  std::vector<mode> out;
  auto add = [&](const char* name, auto edit) {
    session_options o;
    edit(o);
    out.push_back({name, o, 0});
  };
  add("core", [](session_options&) {});
  add("core+merge", [](session_options& o) { o.rules.merge_keys = true; });
  add("core+last-wins", [](session_options& o) { o.rules.last_wins = true; });
  add("core+pairs", [](session_options& o) {
    o.rules.complex_keys_pairs = true;
    o.rules.duplicate_keys_pairs = true;
  });
  add("core+honor", [](session_options& o) { o.rules.honor_version = true; });
  add("yaml11", [](session_options& o) { o.rules.schema = schema::yaml11; });
  add("failsafe+pairs", [](session_options& o) {
    o.rules.schema = schema::failsafe;
    o.rules.complex_keys_pairs = true;
    o.rules.duplicate_keys_pairs = true;
  });
  return out;
}

// Scalars around every fast-path boundary: lengths 1-17, the null/bool spellings and near misses,
// number forms (signs, dots, 15-19 digits, exponents, radix prefixes, specials), non-ASCII text,
// spaces, `~` and `<<`.
std::vector<std::string> scalar_corpus() {
  std::vector<std::string> v = {
      "a", "ab", "abcdefgh", "abcdefghi", "abcdefghijklmnop", "abcdefghijklmnopq", "null", "Null", "NULL", "nULL",
      "nul", "nulls", "true", "True", "TRUE", "tRUE", "false", "False", "FALSE", "fAlse", "falsey", "t", "f", "n", "y",
      "yes", "no", "on", "off", "~", "~x", "<<", "<", "=", "0", "-0", "+0", "00", "007", "-", "+", ".", "-.", "+.",
      "1.", "-1.", ".5", "-.5", "+.5", "0.25", "-0.0", "1.5", "3.14159", "123456789012345", "1234567890123456",
      "12345678901234567", "123456789012345678", "1234567890123456789", "12345678901234567890",
      "9223372036854775807", "9223372036854775808", "-9223372036854775808", "-9223372036854775809",
      "18446744073709551615", "18446744073709551616", "0.1234567890123", "0.12345678901234", "0.123456789012345",
      "0.1234567890123456", "12345678.1234567", "1234567.12345678", "1e3", "1E3", "1.5e-3", "-1e400", "1e-400",
      "0x1F", "0o17", "0b101", "0x", "0o", ".inf", "-.inf", "+.Inf", ".nan", ".NaN", "1_000", "1:20", "2001-12-14",
      "12abc", "-abc", "+abc", ".abc", "1.2.3", "\xC3\xA9t\xC3\xA9", "caf\xC3\xA9", "\xE2\x82\xAC", "a-b_c",
      "with space", "x y z", "0xG", "--", "-1", "+1", "-.0", "10.", "-10.0001"};
  return v;
}

std::string generated_documents() {
  std::string out;
  std::vector<std::string> corpus = scalar_corpus();
  // A block mapping per scalar (as key and as value), a block and a flow sequence of all of them,
  // and mappings with duplicate keys after resolution.
  for (size_t i = 0; i < corpus.size(); ++i) {
    out += "--- \n" + corpus[i] + ": " + corpus[(i + 7) % corpus.size()] + "\nk" + std::to_string(i) + ": " + corpus[i] +
           "\n";
  }
  out += "---\n";
  for (const auto& s : corpus) out += "- " + s + "\n";
  out += "---\n[";
  for (size_t i = 0; i < corpus.size(); ++i) out += (i ? ", " : "") + corpus[i];
  out += "]\n";
  out += "--- {a: 1, b: 2, a: 3}\n--- {1: a, 0x1: b}\n--- {null: a, ~: b}\n--- {a: 1, \"a\": 2}\n";
  out += "--- {x: 1, <<: {y: 2}, z: 3}\n--- {<<: {a: 1}, <<: {b: 2}}\n";
  out += "---\n";
  for (int i = 0; i < 40; ++i) out += "key" + std::to_string(i % 23) + ": " + std::to_string(i) + "\n";  // > 16 keys
  out += "---\n";
  for (int i = 0; i < 40; ++i) out += "key" + std::to_string(i) + ": v\n";
  out += "--- &a x\n--- !!str 12\n--- [a: b, c, d: e]\n--- top\n";
  return out;
}
}  // namespace

int main(int argc, char** argv) {
  try {
    std::vector<std::pair<std::string, std::string>> inputs;
    for (int i = 1; i < argc; ++i) inputs.emplace_back(argv[i], slurp(argv[i]));
    inputs.emplace_back("<generated>", generated_documents());
    size_t compared = 0, errors = 0;
    for (const mode& m : modes()) {
      session s(m.options);
      for (int run = 0; run < 3; ++run) {
        for (const auto& input : inputs) {
          // run 0: documents(byte[]); runs 1 and 2: one session decode, with retained source in run 2.
          uint32_t flags = run == 2 ? uint32_t(session::retain_source) : 0;
          std::string fast = run ? decode_one(s, input.second, flags, true) : decode_all(s, input.second, true);
          std::string general = run ? decode_one(s, input.second, flags, false) : decode_all(s, input.second, false);
          if (fast != general) {
            size_t at = 0;
            while (at < fast.size() && at < general.size() && fast[at] == general[at]) ++at;
            std::fprintf(stderr, "%s [%s]: fast and general paths differ at byte %zu of %zu/%zu\n", input.first.c_str(),
                         m.name, at, fast.size(), general.size());
          }
          require(fast == general);
          ++compared;
          errors += fast.find("#error") != std::string::npos;
        }
      }
    }
    std::printf("fast_path_test: %zu decodes equal with fast paths on and off (%zu ending in the same error)\n",
                compared, errors);
  } catch (const std::exception& e) {
    std::fprintf(stderr, "fast_path_test failure: %s\n", e.what());
    return 1;
  }
  return 0;
}
