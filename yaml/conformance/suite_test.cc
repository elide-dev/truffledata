// yaml-test-suite (data-2022-01-17) through TruffleData's own decoder (docs/superpowers/plans/
// 2026-11-06-yaml.md, Task 3, Review Focus 5): every case is decoded by yaml::handler into tapes
// and side tables, split into documents by yaml::stream (documents(byte[]) over the complete
// buffer), and the tapes are rendered back to events (tape_events.h) and compared with test.event
// under strict flow and document markers (the probe's conformance.py rule, as in Task 1). Cases
// with an `error` file must fail. The same input fed in 1-byte and 7-byte chunks must give exactly
// the same events (or fail the same way), which pins the stream splitter on every case.
//
// The decoder runs with the event-faithful options: FAILSAFE schema (no implicit typing; explicit
// tags still apply), aliases allowed, unknown tags preserved, merge keys off, and the internal
// pairs representation for mappings with complex or duplicate keys.
//
// Usage: suite_test --expected-failures=<file> <case>/in.yaml...
#include "yaml/native/session.h"
#include "yaml/native/stream.h"
#include "yaml/native/tape_events.h"

#include <algorithm>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <iostream>
#include <map>
#include <set>
#include <sstream>
#include <stdexcept>
#include <string>
#include <vector>

using namespace truffledata::yaml;
using yaml_session = truffledata::yaml::session;

namespace {

void require_at(bool condition, const std::string& what, int line) {
  if (!condition) throw std::runtime_error("check failed at line " + std::to_string(line) + ": " + what);
}
#define require(condition, what) require_at((condition), (what), __LINE__)

bool read_file(const std::string& path, std::string* out) {
  std::ifstream in(path, std::ios::binary);
  if (!in) return false;
  std::ostringstream buffer;
  buffer << in.rdbuf();
  *out = buffer.str();
  return true;
}
bool file_exists(const std::string& path) { return std::ifstream(path).good(); }
std::string dirname(const std::string& path) {
  size_t slash = path.rfind('/');
  return slash == std::string::npos ? std::string(".") : path.substr(0, slash);
}
std::string basename(const std::string& path) {
  size_t slash = path.rfind('/');
  return slash == std::string::npos ? path : path.substr(slash + 1);
}
std::string case_id(const std::string& input) {
  std::string dir = dirname(input);
  std::string leaf = basename(dir);
  bool numbered = !leaf.empty() && std::all_of(leaf.begin(), leaf.end(), [](char c) { return c >= '0' && c <= '9'; });
  return numbered ? basename(dirname(dir)) + "/" + leaf : leaf;
}

session_options conformance_options() {
  session_options o;
  o.rules.schema = schema::failsafe;
  o.rules.complex_keys_pairs = true;
  o.rules.duplicate_keys_pairs = true;
  return o;
}

struct outcome {
  bool ok = false;
  std::string events;
  std::string error;
};

// documents(byte[]) over `yaml`, fed in `chunk`-byte pieces (0: all at once).
outcome decode_stream(yaml_session& s, const std::string& yaml, size_t chunk) {
  outcome r;
  stream st(s, size_t(64) << 20, size_t(64) << 20);
  r.events = "+STR\n";
  size_t fed = 0;
  auto drain = [&]() -> bool {
    for (;;) {
      yaml_session::document_view d;
      int32_t status = st.next(&d);
      if (status == status_stream_empty) return true;
      if (status != status_ok) {
        r.error = "status " + std::to_string(status);
        if (st.last_error()) r.error += std::string(": ") + st.last_error()->what();
        return false;
      }
      testing::render_document(d, r.events);
    }
  };
  while (fed < yaml.size()) {
    size_t take = chunk ? std::min(chunk, yaml.size() - fed) : yaml.size() - fed;
    size_t accepted = st.feed(reinterpret_cast<const uint8_t*>(yaml.data()) + fed, take);
    fed += accepted;
    if (!drain()) return r;
    if (accepted == 0 && !chunk) break;
  }
  st.finish();
  if (!drain()) return r;
  r.events += "-STR\n";
  r.ok = true;
  return r;
}

std::string rstrip(const std::string& s) {
  size_t end = s.size();
  while (end > 0 && std::strchr(" \t\r\n\v\f", s[end - 1]) != nullptr) --end;
  return s.substr(0, end);
}

// conformance.py's normalization with strict markers (as //yaml/native:ryml_config_test).
std::vector<std::string> normalize(const std::string& text) {
  std::vector<std::string> out;
  std::map<std::string, std::string> anchors;
  std::istringstream lines(text);
  std::string line;
  while (std::getline(lines, line)) {
    line = rstrip(line);
    if (line.find_first_not_of(" \t\r\n\v\f") == std::string::npos) continue;
    const bool event = line.rfind("=VAL", 0) == 0 || line.rfind("+MAP", 0) == 0 || line.rfind("+SEQ", 0) == 0 ||
                       line.rfind("=ALI", 0) == 0;
    if (event) {
      size_t at = 4;
      if (line.compare(at, 3, " {}") == 0 || line.compare(at, 3, " []") == 0) at += 3;
      if (at + 2 <= line.size() && line[at] == ' ' && (line[at + 1] == '&' || line[at + 1] == '*')) {
        size_t name_begin = at + 2;
        size_t name_end = line.find(' ', name_begin);
        if (name_end == std::string::npos) name_end = line.size();
        if (name_end > name_begin) {
          std::string name = line.substr(name_begin, name_end - name_begin);
          std::string number;
          if (line[at + 1] == '&') {
            number = std::to_string(anchors.size() + 1);
            anchors[name] = number;
          } else {
            auto found = anchors.emplace(name, "None").first;
            number = found->second;
          }
          line = line.substr(0, at + 2) + "A" + number + line.substr(name_end);
        }
      }
    }
    out.push_back(line);
  }
  return out;
}

// Format: `ID<TAB>reason<TAB>upstream link`; `#` comments and blank lines ignored. A case may be
// listed only for a rapidyaml defect with a biojppm/rapidyaml issue link and a ledger ruling.
std::map<std::string, std::string> read_expected_failures(const std::string& path) {
  std::string text;
  require(read_file(path, &text), "cannot read expected failures " + path);
  std::map<std::string, std::string> out;
  std::istringstream lines(text);
  std::string line;
  while (std::getline(lines, line)) {
    if (line.empty() || line[0] == '#') continue;
    std::vector<std::string> fields;
    std::istringstream cells(line);
    std::string cell;
    while (std::getline(cells, cell, '\t')) fields.push_back(cell);
    require(fields.size() == 3, "expected-failure lines are ID<TAB>reason<TAB>link: " + line);
    require(!fields[1].empty(), "expected failure without a reason: " + line);
    require(fields[2].rfind("https://github.com/biojppm/rapidyaml/issues/", 0) == 0,
            "expected failure without a rapidyaml issue link: " + line);
    require(out.emplace(fields[0], fields[1]).second, "duplicate entry " + fields[0]);
  }
  return out;
}

}  // namespace

int main(int argc, char** argv) {
  std::string expected_path;
  std::vector<std::string> inputs;
  for (int i = 1; i < argc; ++i) {
    std::string arg = argv[i];
    if (arg.rfind("--expected-failures=", 0) == 0) expected_path = arg.substr(std::strlen("--expected-failures="));
    else inputs.push_back(arg);
  }
  try {
    require(!expected_path.empty(), "--expected-failures=<file> is required");
    auto expected = read_expected_failures(expected_path);
    yaml_session s(conformance_options());
    std::set<std::string> seen;
    size_t passed = 0, valid = 0, valid_passed = 0, errors = 0, errors_detected = 0, documents = 0;
    std::vector<std::string> problems;
    for (const auto& input : inputs) {
      const std::string id = case_id(input);
      require(seen.insert(id).second, "duplicate case " + id);
      const std::string dir = dirname(input);
      std::string yaml;
      require(read_file(input, &yaml), "cannot read " + input);
      const bool must_fail = file_exists(dir + "/error");
      outcome whole = decode_stream(s, yaml, 0);
      bool pass;
      std::string why;
      if (must_fail) {
        ++errors;
        pass = !whole.ok;
        if (pass) ++errors_detected;
        else why = "expected an error, decode succeeded:\n" + whole.events;
      } else {
        ++valid;
        std::string expected_events;
        require(read_file(dir + "/test.event", &expected_events), "cannot read " + dir + "/test.event");
        if (!whole.ok) {
          pass = false;
          why = "error: " + whole.error;
        } else {
          pass = normalize(whole.events) == normalize(expected_events);
          if (!pass) why = "events differ:\n--- expected\n" + expected_events + "--- actual\n" + whole.events;
        }
        if (pass) {
          ++valid_passed;
          documents += size_t(std::count(whole.events.begin(), whole.events.end(), '\n'));
        }
      }
      // Chunked feeding must not change anything.
      for (size_t chunk : {size_t(1), size_t(7)}) {
        outcome split = decode_stream(s, yaml, chunk);
        if (split.ok != whole.ok || (whole.ok && split.events != whole.events)) {
          pass = false;
          why += "\nchunk " + std::to_string(chunk) + " differs from the whole buffer:\n" +
                 (split.ok ? split.events : "error: " + split.error) + "\n--- whole\n" +
                 (whole.ok ? whole.events : "error: " + whole.error);
        }
      }
      passed += pass;
      auto listed = expected.find(id);
      if (listed != expected.end()) {
        if (pass) problems.push_back(id + ": listed in expected failures but passes; remove the entry");
      } else if (!pass) {
        problems.push_back(id + ": " + why);
      }
    }
    for (const auto& entry : expected)
      if (!seen.count(entry.first)) problems.push_back(entry.first + ": listed in expected failures but not a suite case");
    std::cout << "yaml_test_suite (TruffleData handler): " << passed << "/" << inputs.size() << " passed (valid "
              << valid_passed << "/" << valid << ", errors detected " << errors_detected << "/" << errors
              << "), expected failures listed: " << expected.size() << "\n";
    for (const auto& problem : problems) std::cerr << "  " << problem << "\n";
    require(inputs.size() == 402 && valid == 308 && errors == 94, "unexpected yaml-test-suite case counts");
    require(problems.empty(), std::to_string(problems.size()) + " conformance problems");
  } catch (const std::exception& e) {
    std::cerr << "yaml_test_suite failure: " << e.what() << '\n';
    return 1;
  }
  std::cout << "yaml-test-suite conformance through the TruffleData handler passed\n";
  return 0;
}
