// Pins rapidyaml's build configuration (docs/superpowers/plans/2026-11-06-yaml.md, Task 1):
//
//   config_is_conformant_on_suite  every yaml-test-suite case through rapidyaml with
//                                  resolve_tags(true).resolve_tags_all(true): 402/402, strict
//                                  flow and document markers (the probe's conformance.py rule);
//   long_lines_are_linear          one-line flow documents scale linearly (assertions off);
//   tab_tokens_enabled             the overlay's defines reach this translation unit.
//
// Usage: ryml_config_test [--assert-lane] --expected-failures=<file> <case>/in.yaml...
// The assertion lane (@rapidyaml//:ryml_assert) skips the linearity case: with assertions on,
// rapidyaml rescans the rest of the line for each plain scalar in a flow mapping
// (biojppm/rapidyaml#694).
#include "yaml/native/ryml_events.h"

#include <c4/yml/std/string.hpp>

#include <algorithm>
#include <chrono>
#include <cstdio>
#include <cstring>
#include <exception>
#include <fstream>
#include <iostream>
#include <map>
#include <set>
#include <sstream>
#include <stdexcept>
#include <string>
#include <vector>

// ---- tab_tokens_enabled: compile-time checks on the configuration this TU was built with.
#if defined(RYML_WITH_TAB_TOKENS)
constexpr bool kTabTokens = true;
#else
constexpr bool kTabTokens = false;
#endif
#if defined(RYML_DEFAULT_CALLBACK_USES_EXCEPTIONS)
constexpr bool kExceptions = true;
#else
constexpr bool kExceptions = false;
#endif
static_assert(kTabTokens, "RYML_WITH_TAB_TOKENS must reach handler translation units through defines");
static_assert(kExceptions, "RYML_DEFAULT_CALLBACK_USES_EXCEPTIONS must reach handler translation units");
#if defined(TD_RYML_ASSERT_LANE)
constexpr bool kAssertLane = true;
static_assert(RYML_USE_ASSERT == 1 && C4_USE_ASSERT == 1, "the assertion lane builds with assertions on");
#else
constexpr bool kAssertLane = false;
static_assert(RYML_USE_ASSERT == 0 && C4_USE_ASSERT == 0,
              "rapidyaml assertions must be off whatever NDEBUG says (quadratic long lines)");
#endif

#if defined(__has_feature)
#if __has_feature(address_sanitizer) || __has_feature(undefined_behavior_sanitizer)
#define TD_SANITIZED 1
#endif
#endif
#if !defined(TD_SANITIZED) && (defined(__SANITIZE_ADDRESS__) || defined(__SANITIZE_UNDEFINED__))
#define TD_SANITIZED 1
#endif
#if defined(TD_SANITIZED)
constexpr bool kSanitized = true;
#else
constexpr bool kSanitized = false;
#endif

namespace {

using truffledata::yaml::testing::ryml_events;
using truffledata::yaml::testing::selected_options;

void require_at(bool condition, const std::string &what, int line) {
  if (!condition) throw std::runtime_error("check failed at line " + std::to_string(line) + ": " + what);
}
#define require(condition, what) require_at((condition), (what), __LINE__)

bool read_file(const std::string &path, std::string *out) {
  std::ifstream in(path, std::ios::binary);
  if (!in) return false;
  std::ostringstream buffer;
  buffer << in.rdbuf();
  *out = buffer.str();
  return true;
}

bool file_exists(const std::string &path) { return std::ifstream(path).good(); }

std::string dirname(const std::string &path) {
  size_t slash = path.rfind('/');
  return slash == std::string::npos ? std::string(".") : path.substr(0, slash);
}

std::string basename(const std::string &path) {
  size_t slash = path.rfind('/');
  return slash == std::string::npos ? path : path.substr(slash + 1);
}

// "…/229Q/in.yaml" -> "229Q"; "…/Y79Y/004/in.yaml" -> "Y79Y/004".
std::string case_id(const std::string &input) {
  std::string dir = dirname(input);
  std::string leaf = basename(dir);
  bool numbered = !leaf.empty() && std::all_of(leaf.begin(), leaf.end(), [](char c) { return c >= '0' && c <= '9'; });
  return numbered ? basename(dirname(dir)) + "/" + leaf : leaf;
}

struct parse_result {
  bool ok = false;
  std::string events;
  std::string error;
};

parse_result parse_events(const std::string &yaml, bool render = true) {
  parse_result result;
  std::string buffer = yaml;  // rapidyaml filters in place
  ryml_events handler(render);
  c4::yml::ParseEngine<ryml_events> parser(&handler, selected_options());
  try {
    handler.reset(c4::to_substr(buffer));
    parser.parse_in_place_ev("yaml-test-suite", c4::to_substr(buffer));
    result.ok = true;
    result.events = handler.events();
  } catch (const std::exception &e) {
    result.error = e.what();
  } catch (...) {
    result.error = "non-standard exception";
  }
  return result;
}

// ---- conformance.py's normalization with strict markers: trailing whitespace and blank lines
// dropped, anchor and alias names renumbered by first appearance; flow ({}/[]) and document
// (---/...) markers kept.
std::string rstrip(const std::string &s) {
  size_t end = s.size();
  while (end > 0 && std::strchr(" \t\r\n\v\f", s[end - 1]) != nullptr) --end;
  return s.substr(0, end);
}

std::vector<std::string> normalize(const std::string &text) {
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
            number = std::to_string(anchors.size() + 1);  // as conformance.py: len(anchors) + 1
            anchors[name] = number;
          } else {
            // conformance.py records an unknown alias as None (it counts towards later numbers).
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

struct expected_failure {
  std::string reason;
  std::string link;
};

// Format: one case per line, `ID<TAB>reason<TAB>upstream link`; `#` comments and blank lines are
// ignored. A case may be listed only for a genuine rapidyaml defect with an upstream issue link.
std::map<std::string, expected_failure> read_expected_failures(const std::string &path) {
  std::string text;
  require(read_file(path, &text), "cannot read expected failures " + path);
  std::map<std::string, expected_failure> out;
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
    require(out.emplace(fields[0], expected_failure{fields[1], fields[2]}).second, "duplicate entry " + fields[0]);
  }
  return out;
}

void config_is_conformant_on_suite(const std::vector<std::string> &inputs, const std::string &expected_path) {
  auto expected = read_expected_failures(expected_path);
  std::set<std::string> seen;
  size_t passed = 0, valid = 0, valid_passed = 0, errors = 0, errors_detected = 0;
  std::vector<std::string> problems;
  for (const auto &input : inputs) {
    const std::string id = case_id(input);
    require(seen.insert(id).second, "duplicate case " + id);
    const std::string dir = dirname(input);
    std::string yaml;
    require(read_file(input, &yaml), "cannot read " + input);
    const bool must_fail = file_exists(dir + "/error");
    parse_result result = parse_events(yaml);
    bool pass;
    std::string why;
    if (must_fail) {
      ++errors;
      pass = !result.ok;
      if (pass) ++errors_detected;
      else why = "expected an error, parse succeeded";
    } else {
      ++valid;
      std::string expected_events;
      require(read_file(dir + "/test.event", &expected_events), "cannot read " + dir + "/test.event");
      if (!result.ok) {
        pass = false;
        why = "error: " + result.error;
      } else {
        pass = normalize(result.events) == normalize(expected_events);
        if (!pass) why = "events differ:\n--- expected\n" + expected_events + "--- actual\n" + result.events;
      }
      if (pass) ++valid_passed;
    }
    passed += pass;
    auto listed = expected.find(id);
    if (listed != expected.end()) {
      if (pass) problems.push_back(id + ": listed in expected failures but passes; remove the entry");
    } else if (!pass) {
      problems.push_back(id + ": " + why);
    }
  }
  for (const auto &entry : expected) {
    if (!seen.count(entry.first)) problems.push_back(entry.first + ": listed in expected failures but not a suite case");
  }
  std::cout << "config_is_conformant_on_suite: " << passed << "/" << inputs.size() << " passed (valid "
            << valid_passed << "/" << valid << ", errors detected " << errors_detected << "/" << errors
            << "), expected failures listed: " << expected.size() << "\n";
  for (const auto &problem : problems) std::cerr << "  " << problem << "\n";
  // data-2022-01-17: 402 cases, 308 valid and 94 that must fail.
  require(inputs.size() == 402 && valid == 308 && errors == 94, "unexpected yaml-test-suite case counts");
  require(problems.empty(), std::to_string(problems.size()) + " conformance problems");
}

// ---- long_lines_are_linear

// A one-line JSON-shaped flow document of exactly `bytes` bytes: an array of records whose
// numbers and booleans are plain scalars inside flow mappings (the assertion-bound path).
std::string flow_document(size_t bytes) {
  std::string doc = "[";
  for (size_t i = 0;; ++i) {
    char record[256];
    int n = std::snprintf(record, sizeof record,
                          "%s{\"id\":%zu,\"name\":\"item-%zu\",\"price\":%zu.25,\"active\":%s,"
                          "\"tags\":[\"a\",\"b\",%zu],\"nested\":{\"x\":%zu,\"y\":null},\"note\":\"plain text\"}",
                          i == 0 ? "" : ",", i, i, i % 1000, (i % 2) ? "true" : "false", i, i * 7);
    if (doc.size() + size_t(n) + 1 > bytes) break;
    doc.append(record, size_t(n));
  }
  doc.append(bytes - doc.size() - 1, ' ');
  doc.push_back(']');
  return doc;
}

double best_seconds(const std::string &doc, size_t reps, uint64_t *events) {
  std::string buffer;
  double best = 1e30;
  for (size_t r = 0; r < reps; ++r) {
    buffer = doc;
    ryml_events handler(/*render=*/false);
    c4::yml::ParseEngine<ryml_events> parser(&handler, selected_options());
    handler.reset(c4::to_substr(buffer));
    auto start = std::chrono::steady_clock::now();
    parser.parse_in_place_ev("long-line", c4::to_substr(buffer));
    auto stop = std::chrono::steady_clock::now();
    best = std::min(best, std::chrono::duration<double>(stop - start).count());
    *events = handler.event_count();
  }
  return best;
}

void long_lines_are_linear() {
  const size_t sizes[] = {16u << 10, 256u << 10, 4u << 20};
  double seconds[3];
  for (int i = 0; i < 3; ++i) {
    std::string doc = flow_document(sizes[i]);
    require(doc.size() == sizes[i] && doc.find('\n') == std::string::npos, "flow fixture shape");
    // Sanity: the renderer sees the same document (spot-check the smallest one fully).
    if (i == 0) require(parse_events(doc).ok, "flow fixture parses");
    size_t reps = std::max<size_t>(5, (32u << 20) / sizes[i]);
    uint64_t events = 0;
    seconds[i] = best_seconds(doc, reps, &events);
    require(events > sizes[i] / 16, "flow fixture produced events");
    std::printf("long_lines_are_linear: %8zu bytes  best of %4zu: %10.1f us  %8.1f MB/s  (%llu events)\n", sizes[i],
                reps, seconds[i] * 1e6, double(sizes[i]) / seconds[i] / 1e6, (unsigned long long)events);
  }
  const double ratio_small = seconds[1] / seconds[0];
  const double ratio = seconds[2] / seconds[1];
  const double mbps = double(sizes[2]) / seconds[2] / 1e6;
  std::printf("long_lines_are_linear: time(256 KiB)/time(16 KiB) = %.2f, time(4 MiB)/time(256 KiB) = %.2f "
              "(16x the bytes; quadratic would be ~256)\n",
              ratio_small, ratio);
  require(ratio <= 20.0, "time(4 MiB) / time(256 KiB) must be <= 20 (linear)");
  if (kSanitized) {
    std::printf("long_lines_are_linear: sanitizer build, throughput floor (100 MB/s) not enforced\n");
  } else {
    require(mbps >= 100.0, "4 MiB one-line flow document must parse at >= 100 MB/s");
  }
}

}  // namespace

int main(int argc, char **argv) {
  bool assert_lane = false;
  std::string expected_path;
  std::vector<std::string> inputs;
  for (int i = 1; i < argc; ++i) {
    std::string arg = argv[i];
    if (arg == "--assert-lane") {
      assert_lane = true;
    } else if (arg.rfind("--expected-failures=", 0) == 0) {
      expected_path = arg.substr(std::strlen("--expected-failures="));
    } else {
      inputs.push_back(arg);
    }
  }
  try {
    require(assert_lane == kAssertLane, "--assert-lane must be passed exactly when linking @rapidyaml//:ryml_assert");
    require(!expected_path.empty(), "--expected-failures=<file> is required");
    config_is_conformant_on_suite(inputs, expected_path);
    if (assert_lane) {
      std::printf("long_lines_are_linear: skipped in the assertion lane (biojppm/rapidyaml#694)\n");
    } else {
      long_lines_are_linear();
    }
  } catch (const std::exception &e) {
    std::cerr << "ryml_config_test failure: " << e.what() << '\n';
    return 1;
  }
  std::cout << "rapidyaml configuration checks passed\n";
  return 0;
}
