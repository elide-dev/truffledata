// YAML parser probe (temporary): rapidyaml 0.16.0, libyaml 0.2.5, libfyaml 0.9.6.
//   cprobe events <file>                 yaml-test-suite events from rapidyaml's integer handler
//   cprobe bench <iters> <file>...       best-of-7 timing of scan-only and Myna-tape variants
#include <c4/yml/yml.hpp>
#include <c4/yml/std/string.hpp>
#include <c4/yml/parse_engine.def.hpp>
#include <c4/yml/extra/event_handler_ints.hpp>
#include <c4/yml/extra/ints_to_testsuite.hpp>
#include <yaml.h>
#ifndef PROBE_NO_FY
extern "C" {
typedef void (*fy_probe_cb)(void *user, int kind, const char *text, size_t len, int plain);
void *fy_probe_create(void);
void fy_probe_destroy(void *p);
unsigned long long fy_probe_run(void *p, const char *src, size_t n, fy_probe_cb cb, void *user);
}
#endif
#include "myna/values.hpp"

#include <charconv>
#include <cstdlib>
#include <chrono>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <memory_resource>
#include <sstream>
#include <string>
#include <vector>

using namespace c4::yml::extra;
namespace ievt = c4::yml::extra::ievt;

static std::string slurp(const char *path) {
  std::ifstream in(path, std::ios::binary);
  std::stringstream ss;
  ss << in.rdbuf();
  return ss.str();
}

static volatile uint64_t sink_;

// ---- core-schema (YAML 1.2.2 §10.3) plain scalar resolution into a Myna builder.
static bool all_digits(const char *p, const char *e) {
  if (p == e) return false;
  for (; p < e; ++p) if (*p < '0' || *p > '9') return false;
  return true;
}
template <class Text>
static void plain(myna::value_builder &b, std::string_view s, Text text) {
  size_t n = s.size();
  const char *p = s.data(), *e = p + n;
  if (n == 0 || (n == 1 && s[0] == '~') || s == "null" || s == "Null" || s == "NULL") return b.null();
  char c = s[0];
  if (c == 't' || c == 'T' || c == 'f' || c == 'F') {
    if (s == "true" || s == "True" || s == "TRUE") return b.boolean(true);
    if (s == "false" || s == "False" || s == "FALSE") return b.boolean(false);
    return text();
  }
  if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.') {
    const char *q = (c == '-' || c == '+') ? p + 1 : p;
    if (all_digits(q, e)) {
      int64_t v;
      auto r = std::from_chars(c == '+' ? p + 1 : p, e, v);
      if (r.ec == std::errc() && r.ptr == e) return b.integer(v);
      return text();  // big integer: a production decoder emits DECIMAL here
    }
    if (n > 2 && p[0] == '0' && (p[1] == 'x' || p[1] == 'o')) {
      uint64_t v;
      auto r = std::from_chars(p + 2, e, v, p[1] == 'x' ? 16 : 8);
      if (r.ec == std::errc() && r.ptr == e && v <= INT64_MAX) return b.integer(int64_t(v));
      return text();
    }
    // [-+]? ( \. [0-9]+ | [0-9]+ ( \. [0-9]* )? ) ( [eE] [-+]? [0-9]+ )?
    const char *i = q;
    bool digits = false;
    while (i < e && *i >= '0' && *i <= '9') ++i, digits = true;
    if (i < e && *i == '.') { ++i; while (i < e && *i >= '0' && *i <= '9') ++i, digits = true; }
    if (digits && i < e && (*i == 'e' || *i == 'E')) {
      ++i; if (i < e && (*i == '-' || *i == '+')) ++i;
      const char *d = i; while (i < e && *i >= '0' && *i <= '9') ++i;
      if (i == d) digits = false;
    }
    if (digits && i == e) {
      double v;
      auto r = std::from_chars(c == '+' ? p + 1 : p, e, v);
      if (r.ec == std::errc() && std::isfinite(v)) return b.floating(v);
      return text();
    }
    // .inf / .nan: Myna v3 F64 must be finite; probes store them as text.
  }
  return text();
}

// ---- rapidyaml integer events -> Myna tape (region refs into the in-place buffer and arena).
struct ryml_ints {
  ievt::evt_bits *evts = nullptr;
  std::vector<ievt::evt_bits> buf;
  std::string arena;
  EventHandlerInts handler;
  // RYML_RESOLVE_TAGS=1: let rapidyaml resolve tag handles and %TAG directives itself.
  static c4::yml::ParserOptions opts() {
    c4::yml::ParserOptions o;
    if (std::getenv("RYML_RESOLVE_TAGS")) o.resolve_tags(true).resolve_tags_all(true);
    return o;
  }
  c4::yml::ParseEngine<EventHandlerInts> parser{&handler, opts()};
  int run(std::string &src) {
    for (;;) {
      handler.reset(c4::to_substr(src), c4::to_substr(arena), buf.data(), (int)buf.size());
      parser.parse_in_place_ev("probe", c4::to_substr(src));
      size_t need = (size_t)handler.required_size_events();
      size_t aneed = handler.required_size_arena();
      if (need <= buf.size() && aneed <= arena.size()) return int(need);
      buf.resize(need * 2 + 64);
      arena.resize(aneed * 2 + 64);
      return -1;  // caller must restore src (parsed in place) and retry
    }
  }
};

static uint64_t ints_to_tape(const std::string &src, const std::string &arena, const ievt::evt_bits *e, int n,
                             std::pmr::memory_resource *mem) {
  uint64_t nodes = 0;
  myna::value_builder b(mem);
  uint32_t rsrc = 0, rarena = 0;
  for (int i = 0; i < n; ++i) {
    int ev = e[i];
    int scope = ev & (ievt::BEG_ | ievt::END_ | ievt::SEQ_ | ievt::MAP_ | ievt::DOC_ | ievt::STRM);
    if (!(ev & ievt::WSTR)) {
      if (scope == ievt::BDOC) {
        b.clear();
        rsrc = b.region(std::string_view(src));
        rarena = b.region(std::string_view(arena));
      } else if (scope == ievt::EDOC) {
        nodes += b.view().node_count;
      } else if (scope == ievt::BSEQ) {
        b.begin_array();
      } else if (scope == ievt::BMAP) {
        b.begin_object();
      } else if (scope == ievt::ESEQ || scope == ievt::EMAP) {
        b.end();
      }
    } else if (ev & ievt::SCLR) {
      uint32_t off = uint32_t(e[i + 1]), len = uint32_t(e[i + 2]);
      bool aren = ev & ievt::AREN;
      const std::string &base = aren ? arena : src;
      std::string_view s(base.data() + off, len);
      auto text = [&] { if (len <= 16) b.string(s); else b.string_ref(aren ? rarena : rsrc, off, len); };
      if ((ev & ievt::KEY_) || !(ev & ievt::PLAI)) text();
      else plain(b, s, text);
      i += 2;
    } else if (ev & ievt::ALIA) {
      b.null();
      i += 2;
    } else if (ev & ievt::WSTR) {
      i += 2;  // anchors, tags: side-table material, skipped in the probe
    }
  }
  return nodes;
}

// ---- libyaml
static uint64_t libyaml_scan(const std::string &src, bool tape, std::pmr::memory_resource *mem) {
  yaml_parser_t p;
  yaml_parser_initialize(&p);
  yaml_parser_set_input_string(&p, (const unsigned char *)src.data(), src.size());
  yaml_event_t ev;
  uint64_t count = 0;
  myna::value_builder b(mem ? mem : std::pmr::get_default_resource());
  std::vector<int> stack;  // 1 = mapping expecting key
  for (;;) {
    if (!yaml_parser_parse(&p, &ev)) { std::fprintf(stderr, "libyaml error\n"); std::abort(); }
    yaml_event_type_t t = ev.type;
    ++count;
    if (tape) {
      switch (t) {
        case YAML_DOCUMENT_START_EVENT: b.clear(); stack.clear(); break;
        case YAML_DOCUMENT_END_EVENT: count += b.view().node_count; break;
        case YAML_SEQUENCE_START_EVENT: b.begin_array(); break;
        case YAML_MAPPING_START_EVENT: b.begin_object(); break;
        case YAML_SEQUENCE_END_EVENT: case YAML_MAPPING_END_EVENT: b.end(); break;
        case YAML_SCALAR_EVENT: {
          std::string_view s((const char *)ev.data.scalar.value, ev.data.scalar.length);
          auto text = [&] { b.string(s); };
          if (ev.data.scalar.style == YAML_PLAIN_SCALAR_STYLE && ev.data.scalar.plain_implicit) plain(b, s, text);
          else text();
          break;
        }
        case YAML_ALIAS_EVENT: b.null(); break;
        default: break;
      }
    } else if (t == YAML_SCALAR_EVENT) {
      count += ev.data.scalar.length;
    }
    yaml_event_delete(&ev);
    if (t == YAML_STREAM_END_EVENT) break;
  }
  yaml_parser_delete(&p);
  return count;
}

#ifndef PROBE_NO_FY
// ---- libfyaml (event loop in fyprobe.c)
struct fy_tape { myna::value_builder b; uint64_t nodes = 0; explicit fy_tape(std::pmr::memory_resource *m) : b(m) {} };
static void fy_cb(void *user, int kind, const char *text, size_t len, int pl) {
  auto &t = *static_cast<fy_tape *>(user);
  switch (kind) {
    case 0: t.b.clear(); break;
    case 1: t.nodes += t.b.view().node_count; break;
    case 2: t.b.begin_array(); break;
    case 3: t.b.begin_object(); break;
    case 4: t.b.end(); break;
    case 5: { std::string_view s(text ? text : "", text ? len : 0); auto str = [&] { t.b.string(s); };
              if (pl) plain(t.b, s, str); else str(); break; }
    case 6: t.b.null(); break;
  }
}
static uint64_t fy_scan(void *fyp, const std::string &src, bool tape, std::pmr::memory_resource *mem) {
  if (!tape) return fy_probe_run(fyp, src.data(), src.size(), nullptr, nullptr);
  fy_tape t(mem);
  fy_probe_run(fyp, src.data(), src.size(), fy_cb, &t);
  return t.nodes;
}

#endif
template <class F>
static void bench(const char *name, const char *file, size_t bytes, int iters, F f) {
  double best = 1e300;
  for (int round = 0; round < 7; ++round) {
    auto t0 = std::chrono::steady_clock::now();
    for (int i = 0; i < iters; ++i) sink_ += f();
    auto t1 = std::chrono::steady_clock::now();
    double ns = std::chrono::duration<double, std::nano>(t1 - t0).count() / iters;
    if (ns < best) best = ns;
  }
  std::printf("%-22s %-20s %9zu B %12.0f ns %8.1f MB/s\n", name, file, bytes, best, bytes / best * 1e3);
  std::fflush(stdout);
}

int main(int argc, char **argv) {
  c4::yml::set_callbacks(c4::yml::get_callbacks());
  if (argc >= 3 && !std::strcmp(argv[1], "events")) {
    std::string src = slurp(argv[2]);
    std::string copy = src;
    try {
      ryml_ints r;
      r.buf.resize(64 + src.size() * 4);
      r.arena.resize(64 + src.size());
      int n = r.run(copy);
      if (n < 0) { copy = src; n = r.run(copy); }
      std::string out = events_ints_to_testsuite<std::string>(c4::to_csubstr(copy), c4::to_csubstr(r.arena),
                                                              r.buf.data(), n);
      std::fwrite(out.data(), 1, out.size(), stdout);
      return 0;
    } catch (std::exception const &e) {
      std::fprintf(stderr, "error: %s\n", e.what());
      return 1;
    }
  }
  if (argc < 4 || std::strcmp(argv[1], "bench")) { std::fprintf(stderr, "usage\n"); return 2; }
  int iters = std::atoi(argv[2]);
  std::vector<char> arena_mem(256 << 20);
#ifndef PROBE_NO_FY
  void *fyp = fy_probe_create();
#endif
  for (int a = 3; a < argc; ++a) {
    const char *path = argv[a];
    const char *file = std::strrchr(path, '/') ? std::strrchr(path, '/') + 1 : path;
    std::string src = slurp(path);
    std::string work = src;
    size_t n = src.size();
    int it = std::max(1, int(iters * 200000.0 / (n + 1000)));
    bench("memcpy", file, n, it, [&] { std::memcpy(work.data(), src.data(), n); return uint64_t(work[n / 2]); });
    {
      ryml_ints r;
      r.buf.resize(64 + n * 4);
      r.arena.resize(64 + n);
      bench("ryml-ints(scan)", file, n, it, [&] {
        std::memcpy(work.data(), src.data(), n);
        return uint64_t(r.run(work));
      });
      bench("ryml-ints->tape", file, n, it, [&] {
        std::memcpy(work.data(), src.data(), n);
        int k = r.run(work);
        std::pmr::monotonic_buffer_resource mem(arena_mem.data(), arena_mem.size());
        return ints_to_tape(work, r.arena, r.buf.data(), k, &mem);
      });
    }
    {
      c4::yml::Tree tree;
      c4::yml::Parser::handler_type h;
      c4::yml::Parser parser(&h);
      bench("ryml-tree", file, n, it, [&] {
        std::memcpy(work.data(), src.data(), n);
        tree.clear();
        tree.clear_arena();
        c4::yml::parse_in_place(&parser, c4::to_substr(work), &tree);
        return uint64_t(tree.size());
      });
    }
    bench("libyaml(scan)", file, n, it, [&] { return libyaml_scan(src, false, nullptr); });
    bench("libyaml->tape", file, n, it, [&] {
      std::pmr::monotonic_buffer_resource mem(arena_mem.data(), arena_mem.size());
      return libyaml_scan(src, true, &mem);
    });
#ifndef PROBE_NO_FY
    bench("libfyaml(scan)", file, n, it, [&] { return fy_scan(fyp, src, false, nullptr); });
    bench("libfyaml->tape", file, n, it, [&] {
      std::pmr::monotonic_buffer_resource mem(arena_mem.data(), arena_mem.size());
      return fy_scan(fyp, src, true, &mem);
    });
#endif
  }
#ifndef PROBE_NO_FY
  fy_probe_destroy(fyp);
#endif
  return 0;
}
