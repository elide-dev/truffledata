// Native YAML reference rows (plan Task 2 Step 4; Task 9 reuses them). Best of 7 rounds of a tight
// loop per variant and fixture, in one process:
//
//   ryml-ints(scan)             rapidyaml's integer event handler (src_extra; benchmark only)
//   ryml-tree                   rapidyaml's stock tree parser
//   libyaml(scan)               libyaml 0.2.5 events, every scalar touched (the probe's row; benchmark only)
//   td-session(decode+reset)    td_session_decode then td_session_reset
//   td-session(retain+release)  td_session_decode, td_session_retain, td_yaml_release
//   td-stream(documents)        multi-document input: td_yaml_stream_* over the whole buffer
//
// Every variant parses a fresh copy of the input (rapidyaml filters in place; the session copies
// into its own buffer). The acceptance hypothesis is checked against the scan row of the same run:
// session decode >= 0.8 x ryml-ints(scan) on records-block. With --passes=N the whole table runs N
// times (variants interleaved per fixture in each pass) and the gate is taken per pass, then from
// the best time of each variant over all passes.
//
// With --profile=S it instead samples (sampler.h) S seconds of td-session decode (td-stream for
// multi-document input) and S seconds of ryml-ints(scan) per fixture, writing
// profile-<variant>-<fixture>.txt into $TEST_UNDECLARED_OUTPUTS_DIR (or the working directory).
//
// Usage: bench [--quick] [--passes=N] [--profile=S] <fixture.yaml>...
#include "yaml/native/api.h"
#include "yaml/native/reference_tape.h"
#include "yaml/native/sampler.h"
#include "yaml/native/typed.h"

#include <c4/yml/event_handler_tree.hpp>
#include <c4/yml/parse.hpp>
#include <c4/yml/std/string.hpp>
#include <c4/yml/tree.hpp>
#include <yaml.h>

#include <algorithm>
#include <chrono>
#include <cstdio>
#include <cstdlib>
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

// The records fixture is a top-level sequence; a single enclosing field supplies the record-root
// contract used by both typed backends. The identical wrapped bytes are used by every row.
int typed_benchmark(const std::string& path, bool quick) {
  using namespace truffledata::yaml;
  auto record = [](const char* name, uint32_t child, bool repeated = false) {
    scalar_field field{name, scalar_kind::record}; field.child = child; field.repeated = repeated;
    return field;
  };
  auto array = [](const char* name, scalar_kind kind) {
    scalar_field field{name, kind}; field.repeated = true; return field;
  };
  typed_plan plan(std::vector<std::vector<scalar_field>>{
      {record("records", 1, true)},
      {{"id", scalar_kind::int64}, {"name", scalar_kind::string}, {"price", scalar_kind::float64},
       {"active", scalar_kind::boolean}, array("tags", scalar_kind::string), record("dims", 2),
       array("scores", scalar_kind::int64), {"note", scalar_kind::string},
       {"parent", scalar_kind::int64, true, true}},
      {{"w", scalar_kind::int64}, {"h", scalar_kind::float64}, {"unit", scalar_kind::string}}}, "YAML");
  std::istringstream lines(slurp(path));
  std::string line, input = "records:\n";
  while (std::getline(lines, line)) input += "  " + line + '\n';
  auto bytes = reinterpret_cast<const uint8_t*>(input.data());
  session_options options;
  options.storage.index_bytes = 256u << 20;
  truffledata::yaml::session generic(options);
  int iters = quick ? 10 : 100;
  double generic_ns = best_ns(iters, [&] {
    auto document = generic.decode(bytes, input.size(), 0);
    auto* retained = generic.retain();
    uint64_t nodes = document.view.node_count;
    retained->destroy();
    return nodes;
  });
  double typed_ns = best_ns(iters, [&] {
    typed_document document(plan, input, options);
    return document.size();
  });
  truffledata::yaml::session source_session(options);
  typed_source source(source_session.decode(bytes, input.size(), truffledata::yaml::session::retain_source),
                      options.rules.schema, false);
  double walk_ns = best_ns(iters, [&] {
    truffledata::tape::tape_typed<scalar_slot, record_entry, typed_source> slots(
        plan, source, options.storage.index_bytes - source_session.memory().current(), options.max_alias_expansion);
    return slots.size();
  });
  std::printf("typed records-block bytes=%zu generic-retain-ns=%.0f typed-ns=%.0f walk-ns=%.0f typed/generic=%.3f walk/typed=%.3f\n",
              input.size(), generic_ns, typed_ns, walk_ns, typed_ns / generic_ns, walk_ns / typed_ns);
  std::printf("typed<=1.2*generic=%s direct-handler-trigger(walk>15%%)=%s\n",
              typed_ns <= 1.2 * generic_ns ? "PASS" : "MISS", walk_ns > .15 * typed_ns ? "OPEN" : "closed");
  return 0; // An exploratory hypothesis report, not a relaxation of the release performance gate.
}

// libyaml events over `src` (every document), touching every scalar: the probe's libyaml(scan).
uint64_t libyaml_scan(const std::string& src) {
  yaml_parser_t p;
  yaml_parser_initialize(&p);
  yaml_parser_set_input_string(&p, reinterpret_cast<const unsigned char*>(src.data()), src.size());
  yaml_event_t ev;
  uint64_t count = 0;
  for (;;) {
    if (!yaml_parser_parse(&p, &ev)) {
      std::fprintf(stderr, "libyaml error: %s\n", p.problem ? p.problem : "?");
      std::abort();
    }
    yaml_event_type_t t = ev.type;
    ++count;
    if (t == YAML_SCALAR_EVENT) count += ev.data.scalar.length;
    yaml_event_delete(&ev);
    if (t == YAML_STREAM_END_EVENT) break;
  }
  yaml_parser_delete(&p);
  return count;
}

// Every document of `src` through the stream API (documents(byte[])); returns the node count.
uint64_t stream_documents(void* session, const std::string& src) {
  void* st = td_yaml_stream_create(session, uint64_t(64) << 20, uint64_t(64) << 20);
  if (!st) std::abort();
  if (td_yaml_stream_feed(st, reinterpret_cast<const uint8_t*>(src.data()), src.size()) != src.size()) std::abort();
  td_yaml_stream_finish(st);
  uint64_t nodes = 0;
  for (;;) {
    YamlResult r{};
    int32_t status = td_yaml_stream_next(st, &r);
    if (status == TD_YAML_STREAM_EMPTY) break;
    if (status != TD_YAML_OK) std::abort();
    nodes += static_cast<const myna_value_view*>(r.view)->node_count;
  }
  td_yaml_stream_release(st);
  return nodes;
}
// Runs `f` for `seconds` of wall time under the sampler; writes the samples to `name`.
int profile(const std::string& name, double seconds, const std::function<uint64_t()>& f) {
  int top = 0;
  if (!truffledata::yaml::sampler::start(97, &top)) {
    std::printf("profile %s: sampler unavailable on this platform\n", name.c_str());
    return 0;
  }
  auto t0 = std::chrono::steady_clock::now();
  uint64_t runs = 0;
  while (std::chrono::duration<double>(std::chrono::steady_clock::now() - t0).count() < seconds) {
    sink = sink + f();
    ++runs;
  }
  truffledata::yaml::sampler::stop();
  const char* dir = std::getenv("TEST_UNDECLARED_OUTPUTS_DIR");
  std::string path = (dir ? std::string(dir) + "/" : std::string()) + name;
  std::FILE* out = std::fopen(path.c_str(), "w");
  if (!out) return 4;
  truffledata::yaml::sampler::write(out);
  std::fclose(out);
  std::printf("profile %s: %llu runs, %zu samples\n", name.c_str(), (unsigned long long)runs,
              size_t(truffledata::yaml::sampler::count));
  return 0;
}
}  // namespace

int main(int argc, char** argv) {
  bool quick = false, typed = false;
  int passes = 1;
  double profile_seconds = 0;
  std::vector<std::string> files;
  for (int i = 1; i < argc; ++i) {
    if (std::strcmp(argv[i], "--typed") == 0) typed = true;
    else if (std::strcmp(argv[i], "--quick") == 0) quick = true;
    else if (std::strncmp(argv[i], "--passes=", 9) == 0) passes = std::max(1, std::atoi(argv[i] + 9));
    else if (std::strncmp(argv[i], "--profile=", 10) == 0) profile_seconds = std::atof(argv[i] + 10);
    else files.push_back(argv[i]);
  }
  if (typed) {
    if (files.size() != 1) return 2;
    return typed_benchmark(files[0], quick);
  }
  if (profile_seconds > 0) {
    YamlOptionsStruct po = options();
    void* ps = td_yaml_session_create(&po);
    for (const auto& path : files) {
      const std::string file = basename(path);
      const std::string src = slurp(path);
      YamlResult probe{};
      bool single =
          td_yaml_session_decode(ps, reinterpret_cast<const uint8_t*>(src.data()), src.size(), 0, nullptr, 0, &probe) ==
          TD_YAML_OK;
      int rc = profile("profile-td-" + file + ".txt", profile_seconds, [&]() -> uint64_t {
        if (!single) return stream_documents(ps, src);
        YamlResult r{};
        td_yaml_session_decode(ps, reinterpret_cast<const uint8_t*>(src.data()), src.size(), 0, nullptr, 0, &r);
        td_yaml_session_reset(ps);
        return r.view ? 1 : 0;
      });
      if (rc) return rc;
      truffledata::yaml::reference::ints_tape ints;
      std::string work;
      rc = profile("profile-ints-" + file + ".txt", profile_seconds, [&]() -> uint64_t {
        work = src;
        return uint64_t(ints.scan(work));
      });
      if (rc) return rc;
    }
    td_yaml_session_release(ps);
    return 0;
  }
  // best[file][variant]: minimum time over all passes; per-pass gate ratios.
  std::map<std::string, std::map<std::string, double>> best;
  std::map<std::string, size_t> sizes;
  std::vector<double> gates;
  YamlOptionsStruct o = options();
  void* session = td_yaml_session_create(&o);
  if (!session) return 2;
  for (int pass = 1; pass <= passes; ++pass) {
    std::map<std::string, std::map<std::string, double>> ns_of;
    std::printf("pass %d of %d\n", pass, passes);
    std::printf("%-28s %-20s %10s %14s %10s\n", "variant", "fixture", "bytes", "best ns", "MB/s");
    for (const auto& path : files) {
      const std::string file = basename(path);
      const std::string src = slurp(path);
      const size_t n = src.size();
      sizes[file] = n;
      int iters = std::max(1, int((quick ? 2e6 : 4e7) / double(n + 1000)));
      std::string work = src;
      auto row = [&](const char* name, double ns) {
        ns_of[file][name] = ns;
        auto& b = best[file][name];
        if (b == 0 || ns < b) b = ns;
        std::printf("%-28s %-20s %10zu %14.0f %10.1f\n", name, file.c_str(), n, ns, double(n) / ns * 1e3);
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
      row("libyaml(scan)", best_ns(std::max(1, iters / 4), [&] { return libyaml_scan(src); }));
      YamlResult result{};
      int32_t probe = td_yaml_session_decode(session, reinterpret_cast<const uint8_t*>(src.data()), n, 0, nullptr, 0, &result);
      td_yaml_session_reset(session);
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
      } else if (probe == TD_YAML_MULTIPLE_DOCUMENTS) {
        row("td-stream(documents)", best_ns(iters, [&] { return stream_documents(session, src); }));
      } else {
        std::printf("%-28s %-20s %10zu  status %d\n", "td-session(decode+reset)", file.c_str(), n, probe);
        return 3;
      }
    }
    auto block = ns_of.find("records-block.yaml");
    if (block != ns_of.end() && block->second.count("td-session(decode+reset)")) {
      double ratio = block->second["ryml-ints(scan)"] / block->second["td-session(decode+reset)"];
      gates.push_back(ratio);
      std::printf("pass %d gate: records-block td-session(decode+reset) / ryml-ints(scan) = %.3f\n\n", pass, ratio);
    }
  }
  td_yaml_session_release(session);
  if (passes > 1) {
    std::printf("best over %d passes\n", passes);
    std::printf("%-28s %-20s %10s %14s %10s %8s %8s %8s\n", "variant", "fixture", "bytes", "best ns", "MB/s", "/ints",
                "/tree", "/libyaml");
    for (const auto& path : files) {
      const std::string file = basename(path);
      auto& v = best[file];
      for (const char* name : {"ryml-ints(scan)", "ryml-tree", "libyaml(scan)", "td-session(decode+reset)",
                               "td-session(retain+release)", "td-stream(documents)"}) {
        if (!v.count(name)) continue;
        double ns = v[name];
        std::printf("%-28s %-20s %10zu %14.0f %10.1f %8.3f %8.3f %8.3f\n", name, file.c_str(), sizes[file], ns,
                    double(sizes[file]) / ns * 1e3, v["ryml-ints(scan)"] / ns, v["ryml-tree"] / ns,
                    v["libyaml(scan)"] / ns);
      }
    }
  }
  auto block = best.find("records-block.yaml");
  if (block != best.end() && block->second.count("td-session(decode+reset)")) {
    double ratio = block->second["ryml-ints(scan)"] / block->second["td-session(decode+reset)"];
    std::sort(gates.begin(), gates.end());
    std::printf("acceptance: records-block td-session(decode+reset) / ryml-ints(scan) = %.3f (best times over %d "
                "passes; per pass min %.3f, median %.3f, max %.3f) (hypothesis >= 0.8): %s\n",
                ratio, passes, gates.front(), gates[gates.size() / 2], gates.back(), ratio >= 0.8 ? "met" : "MISSED");
  }
  return 0;
}
