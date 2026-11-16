// Differential test of the opt-in Rust parser backend against rapidyaml (design step 7): the
// rapidyaml and granit-parser builds of diff_dump.cc describe the same inputs under the same
// policies (yaml-test-suite inputs, probe fixtures, the session fuzz corpus and seeded generated
// documents), and every description must agree: the error status, or on success the events,
// resolved values, per-node kinds, styles, key kinds, tags, anchors, alias targets and lexical
// spans, side tables, document flags and version. Error messages and positions are not
// contractual (ruling 2026-11-14: status match is sufficient); they are counted, not compared.
//
// A remaining difference is fixed, or explained by a class in differential_exclusions.txt:
// `class<TAB>kind<TAB>regex<TAB>reason`, where kind is granit-rejects (rapidyaml accepts),
// granit-accepts (rapidyaml rejects), document (both accept, different documents) or truncation
// (both fail; only the stream's TRUNCATED vs SYNTAX classification differs) and the regex
// (ECMAScript) must match the input text. Only spec-conformant granit behaviour may be listed. The
// list is exact: a class that explains no difference fails.
//
// Usage: differential_test --ryml=<diff_dump> --rs=<diff_dump_rs> --exclusions=<file>
//                          [--generated=N --seed=S] <input>...
#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <map>
#include <regex>
#include <set>
#include <sstream>
#include <stdexcept>
#include <string>
#include <vector>

namespace {

std::string run(const std::string& command) {
  FILE* pipe = popen(command.c_str(), "r");
  if (!pipe) throw std::runtime_error("cannot run " + command);
  std::string out;
  char buffer[1 << 16];
  size_t n;
  while ((n = std::fread(buffer, 1, sizeof buffer, pipe)) > 0) out.append(buffer, n);
  int status = pclose(pipe);
  if (status != 0) throw std::runtime_error(command + " exited with status " + std::to_string(status));
  return out;
}

struct dump {
  std::map<std::string, std::string> blocks;     // "input mode" -> description
  std::map<std::string, std::string> generated;  // input -> generated text
};

dump parse(const std::string& text) {
  dump d;
  std::string* current = nullptr;
  size_t at = 0;
  while (at < text.size()) {
    size_t end = text.find('\n', at);
    if (end == std::string::npos) end = text.size();
    std::string line = text.substr(at, end - at);
    at = end + 1;
    if (line.rfind("@@ ", 0) == 0) {
      current = &d.blocks[line.substr(3)];
    } else if (line.rfind("## ", 0) == 0) {
      // Generated inputs follow the blocks; everything after the header is the input text.
      std::string name = line.substr(3);
      size_t next = text.find("\n## gen/", at - 1);
      size_t stop = next == std::string::npos ? text.size() : next + 1;
      std::string input = text.substr(at, stop - at);
      // The dump ends each generated input with one extra line break; the patterns see the input.
      if (!input.empty() && input.back() == '\n') input.pop_back();
      d.generated[name] = input;
      at = stop;
      current = nullptr;
    } else if (current) {
      *current += line + "\n";
    }
  }
  return d;
}

// The contractual part of a description: no informational lines, no error messages, and the
// error status without the parser/handler origin.
std::string strict(const std::string& block) {
  std::string out;
  std::istringstream lines(block);
  std::string line;
  while (std::getline(lines, line)) {
    if (!line.empty() && line[0] == '~') continue;
    if (line.rfind("message ", 0) == 0) continue;
    if (line.rfind("error ", 0) == 0 && line.size() > 7 && line.compare(line.size() - 7, 7, " parser") == 0)
      line.resize(line.size() - 7);
    out += line + "\n";
  }
  return out;
}

bool has_error(const std::string& block) { return block.rfind("error ", 0) == 0 || block.find("\nerror ") != std::string::npos; }

std::string read_input(const std::string& path) {
  std::ifstream in(path, std::ios::binary);
  std::ostringstream out;
  out << in.rdbuf();
  std::string text = out.str();
  if (path.find("/session/") != std::string::npos) text = text.size() > 2 ? text.substr(2) : std::string();
  return text;
}

std::vector<std::string> lines_of(const std::string& block) {
  std::vector<std::string> out;
  std::istringstream lines(block);
  std::string line;
  while (std::getline(lines, line))
    if (line.empty() || line[0] != '~') out.push_back(line);
  return out;
}

// True when the only differences are node lines where granit reports a lexical span and
// rapidyaml reports none. rapidyaml omits the raw spans of some quoted implicit keys (and of block
// scalars that start with blank lines); a span granit adds is the token's true source, so this
// class is explained, counted and reported, not failed. A span that differs or that granit lacks
// is never explained here.
bool span_additions_only(const std::string& left, const std::string& right) {
  std::vector<std::string> l = lines_of(left), r = lines_of(right);
  if (l.size() != r.size()) return false;
  bool any = false;
  for (size_t i = 0; i < l.size(); ++i) {
    if (l[i] == r[i]) continue;
    size_t span = r[i].rfind(" span=");
    if (l[i].rfind("node ", 0) != 0 || l[i].find(" span=") != std::string::npos || span == std::string::npos ||
        r[i].substr(0, span) != l[i])
      return false;
    any = true;
  }
  return any;
}

struct exclusion {
  std::string name, kind, pattern, reason;
  std::regex regex;
  size_t matched = 0;
};

std::vector<exclusion> read_exclusions(const std::string& path) {
  std::vector<exclusion> out;
  std::ifstream in(path);
  if (!in) throw std::runtime_error("cannot read " + path);
  std::string line;
  std::set<std::string> names;
  while (std::getline(in, line)) {
    if (line.empty() || line[0] == '#') continue;
    std::vector<std::string> f;
    std::istringstream cells(line);
    std::string cell;
    while (std::getline(cells, cell, '\t')) f.push_back(cell);
    if (f.size() != 4 || f[3].empty()) throw std::runtime_error("exclusions are class<TAB>kind<TAB>regex<TAB>reason: " + line);
    if (f[1] != "granit-rejects" && f[1] != "granit-accepts" && f[1] != "document" && f[1] != "truncation")
      throw std::runtime_error("unknown exclusion kind: " + line);
    if (!names.insert(f[0]).second) throw std::runtime_error("duplicate exclusion " + f[0]);
    out.push_back({f[0], f[1], f[2], f[3], std::regex(f[2], std::regex::ECMAScript)});
  }
  return out;
}

}  // namespace

int main(int argc, char** argv) {
  try {
    std::string ryml, rs, exclusions_path, generated = "0", seed = "1";
    std::vector<std::string> inputs;
    for (int i = 1; i < argc; ++i) {
      std::string arg = argv[i];
      if (arg.rfind("--ryml=", 0) == 0) ryml = arg.substr(7);
      else if (arg.rfind("--rs=", 0) == 0) rs = arg.substr(5);
      else if (arg.rfind("--exclusions=", 0) == 0) exclusions_path = arg.substr(13);
      else if (arg.rfind("--generated=", 0) == 0) generated = arg.substr(12);
      else if (arg.rfind("--seed=", 0) == 0) seed = arg.substr(7);
      else inputs.push_back(arg);
    }
    const char* tmp = std::getenv("TEST_TMPDIR");
    std::string list = std::string(tmp ? tmp : ".") + "/differential_inputs.txt";
    {
      std::ofstream out(list);
      for (const auto& input : inputs) out << input << "\n";
    }
    const std::string args = " --list=" + list + " --generated=" + generated + " --seed=" + seed;
    dump a = parse(run(ryml + args)), b = parse(run(rs + args));
    auto exclusions = read_exclusions(exclusions_path);
    std::map<std::string, std::string> texts;  // input -> text, for the exclusion patterns
    for (const auto& input : inputs) texts[input] = read_input(input);
    for (const auto& [name, text] : a.generated) texts[name] = text;

    size_t compared = 0, equal = 0, informational = 0, excluded = 0, errors_both = 0, spans_added = 0;
    std::map<std::string, size_t> classes, examples;
    std::vector<std::string> shown;  // a few unexplained differences of each class
    std::vector<std::string> problems;
    if (a.blocks.size() != b.blocks.size()) throw std::runtime_error("the two dumps describe different input sets");
    for (const auto& [key, left] : a.blocks) {
      auto other = b.blocks.find(key);
      if (other == b.blocks.end()) throw std::runtime_error("missing in the Rust dump: " + key);
      const std::string& right = other->second;
      ++compared;
      const bool left_error = has_error(left), right_error = has_error(right);
      if (left_error && right_error) ++errors_both;
      if (strict(left) == strict(right)) {
        ++equal;
        if (left != right) ++informational;
        continue;
      }
      const std::string input = key.substr(0, key.rfind(' '));
      if (span_additions_only(left, right)) {
        ++spans_added;
        continue;
      }
      std::string cls, kind;
      if (left_error != right_error) {
        cls = left_error ? "rapidyaml error, granit ok" : "granit error, rapidyaml ok";
        kind = left_error ? "granit-accepts" : "granit-rejects";
      } else {
        cls = left_error ? "different error status" : "different document";
        kind = left_error ? "status" : "document";
        // Both fail with a syntax error and only the stream's truncated classification differs.
        auto untruncated = [](std::string text) {
          size_t at = text.find("truncated\n");
          if (at != std::string::npos) text.erase(at, 10);
          return text;
        };
        if (left_error && untruncated(strict(left)) == untruncated(strict(right))) {
          cls = "truncated vs syntax";
          kind = "truncation";
        }
      }
      ++classes[cls];
      const std::string& text = texts[input];
      bool explained = false;
      for (exclusion& x : exclusions) {
        if (x.kind == kind && std::regex_search(text, x.regex)) {
          ++x.matched;
          explained = true;
          break;
        }
      }
      if (explained) {
        ++excluded;
        continue;
      }
      std::string report = key + ": " + cls + "\n--- rapidyaml\n" + left.substr(0, 1500) + "--- granit\n" + right.substr(0, 1500);
      auto gen = a.generated.find(input);
      if (gen != a.generated.end()) report += "--- input\n" + gen->second;
      problems.push_back(report);
      if (examples[cls]++ < 6) shown.push_back(report);
    }
    for (const exclusion& x : exclusions)
      if (!x.matched) problems.push_back("exclusion matches no difference: " + x.name);

    std::printf("differential_test: %zu decodes compared (%zu inputs x %zu policies); %zu equal (%zu both failing), "
                "%zu where granit adds lexical spans rapidyaml omits, %zu excluded, %zu unexplained; error messages or "
                "positions differ in %zu equal decodes\n",
                compared, inputs.size() + std::stoul(generated), compared / (inputs.size() + std::stoul(generated)),
                equal, errors_both, spans_added, excluded, problems.size(), informational);
    for (const auto& [cls, count] : classes) std::printf("  %s: %zu\n", cls.c_str(), count);
    for (const exclusion& x : exclusions) std::printf("  excluded by %s (%s): %zu\n", x.name.c_str(), x.kind.c_str(), x.matched);
    for (const auto& report : shown) std::fprintf(stderr, "\n%s\n", report.c_str());
    // Every unexplained difference, for review (bazel-testlogs/.../test.outputs/differences.txt).
    if (const char* outputs = std::getenv("TEST_UNDECLARED_OUTPUTS_DIR")) {
      std::ofstream all(std::string(outputs) + "/differences.txt");
      for (const auto& report : problems) all << report << "\n\n";
    }
    for (size_t i = 0; i < problems.size(); ++i)
      if (problems[i].rfind("exclusion", 0) == 0) std::fprintf(stderr, "%s\n", problems[i].c_str());
    if (!problems.empty()) {
      std::fprintf(stderr, "differential_test: %zu unexplained differences\n", problems.size());
      return 1;
    }
  } catch (const std::exception& e) {
    std::fprintf(stderr, "differential_test failure: %s\n", e.what());
    return 1;
  }
  return 0;
}
