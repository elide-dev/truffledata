// Native YAML reference rows (plan Task 2 Step 4; Task 9 reuses them). Best of 7 rounds of a tight
// loop per variant and fixture, in one process:
//
//   ryml-ints(scan)             rapidyaml's integer event handler (src_extra; benchmark only)
//   ryml-tree                   rapidyaml's stock tree parser
//   td-session(decode+reset)    td_yaml_session_decode then td_yaml_session_reset
//   td-session(retain+release)  td_yaml_session_decode, td_yaml_session_retain, td_yaml_release
//
// Every variant parses a fresh copy of the input (rapidyaml filters in place; the session copies
// into its own buffer). The acceptance hypothesis is checked against the scan row of the same run:
// session decode >= 0.8 x ryml-ints(scan) on records-block.
//
// Usage: bench [--quick] <fixture.yaml>...
#include "yaml/native/api.h"
#include "yaml/native/reference_tape.h"

#include <c4/yml/event_handler_tree.hpp>
#include <c4/yml/parse.hpp>
#include <c4/yml/std/string.hpp>
#include <c4/yml/tree.hpp>

#include <algorithm>
#include <chrono>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <functional>
#include <map>
#include <sstream>
#include <string>
#include <vector>

namespace {
volatile uint64_t sink;

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

double best_ns(int iters, const std::function<uint64_t()>& f) {
  double best = 1e300;
  for (int round = 0; round < 7; ++round) {
    auto t0 = std::chrono::steady_clock::now();
    for (int i = 0; i < iters; ++i) sink = sink + f();
    auto t1 = std::chrono::steady_clock::now();
    double ns = std::chrono::duration<double, std::nano>(t1 - t0).count() / iters;
    best = std::min(best, ns);
  }
  return best;
}

YamlOptionsStruct options() {
  YamlOptionsStruct o{};
  o.max_input_bytes = 64u << 20;
  o.max_index_bytes = 256u << 20;
  o.max_nodes = 10000000;
  o.max_text_bytes = 64u << 20;
  o.max_aliases = 10000;
  o.max_alias_expansion = 1000000;
  o.max_depth = 128;
  return o;
}
}  // namespace

int main(int argc, char** argv) {
  bool quick = false;
  std::vector<std::string> files;
  for (int i = 1; i < argc; ++i) {
    if (std::strcmp(argv[i], "--quick") == 0) quick = true;
    else files.push_back(argv[i]);
  }
  std::map<std::string, std::map<std::string, double>> mbps;
  YamlOptionsStruct o = options();
  void* session = td_yaml_session_create(&o);
  if (!session) return 2;
  std::printf("%-28s %-20s %10s %14s %10s\n", "variant", "fixture", "bytes", "best ns", "MB/s");
  for (const auto& path : files) {
    const std::string file = basename(path);
    const std::string src = slurp(path);
    const size_t n = src.size();
    int iters = std::max(1, int((quick ? 2e6 : 4e7) / double(n + 1000)));
    std::string work = src;
    auto row = [&](const char* name, double ns) {
      double rate = double(n) / ns * 1e3;
      mbps[file][name] = rate;
      std::printf("%-28s %-20s %10zu %14.0f %10.1f\n", name, file.c_str(), n, ns, rate);
      std::fflush(stdout);
    };
    row("memcpy", best_ns(iters, [&] {
      std::memcpy(&work[0], src.data(), n);
      return uint64_t(work[n / 2]);
    }));
    {
      truffledata::yaml::reference::ints_tape ints;
      row("ryml-ints(scan)", best_ns(iters, [&] {
        work = src;
        return uint64_t(ints.scan(work));
      }));
    }
    {
      c4::yml::Tree tree;
      c4::yml::Parser::handler_type handler;
      c4::yml::Parser parser(&handler);
      row("ryml-tree", best_ns(iters, [&] {
        std::memcpy(&work[0], src.data(), n);
        tree.clear();
        tree.clear_arena();
        c4::yml::parse_in_place(&parser, c4::to_substr(work), &tree);
        return uint64_t(tree.size());
      }));
    }
    YamlResult result{};
    int32_t probe = td_yaml_session_decode(session, reinterpret_cast<const uint8_t*>(src.data()), n, 0, nullptr, 0, &result);
    if (probe == TD_YAML_OK) {
      row("td-session(decode+reset)", best_ns(iters, [&] {
        YamlResult r{};
        td_yaml_session_decode(session, reinterpret_cast<const uint8_t*>(src.data()), n, 0, nullptr, 0, &r);
        uint64_t nodes = static_cast<const myna_value_view*>(r.view)->node_count;
        td_yaml_session_reset(session);
        return nodes;
      }));
      row("td-session(retain+release)", best_ns(iters, [&] {
        YamlResult r{}, kept{};
        td_yaml_session_decode(session, reinterpret_cast<const uint8_t*>(src.data()), n, 0, nullptr, 0, &r);
        td_yaml_session_retain(session, &kept);
        uint64_t nodes = static_cast<const myna_value_view*>(kept.view)->node_count;
        td_yaml_release(&kept);
        return nodes;
      }));
    } else {
      std::printf("%-28s %-20s %10zu %14s %10s  (status %d: multi-document input)\n", "td-session(decode+reset)",
                  file.c_str(), n, "-", "-", probe);
    }
  }
  td_yaml_session_release(session);
  auto block = mbps.find("records-block.yaml");
  if (block != mbps.end() && block->second.count("td-session(decode+reset)")) {
    double ratio = block->second["td-session(decode+reset)"] / block->second["ryml-ints(scan)"];
    std::printf("acceptance: records-block td-session(decode+reset) / ryml-ints(scan) = %.3f (hypothesis >= 0.8): %s\n",
                ratio, ratio >= 0.8 ? "met" : "MISSED");
  }
  return 0;
}
