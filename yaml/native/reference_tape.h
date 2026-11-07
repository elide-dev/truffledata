// Test and benchmark only: the YAML probe's reference tape (benchmarks/results/yaml-probe-b242abd/
// sources/cprobe.cpp): rapidyaml's integer event handler (src_extra, never in production targets)
// walked into Myna's own value_builder with the probe's core-schema plain-scalar resolution. The
// decoder's tape must hold the same values for the probe fixtures (plan Task 2,
// tape_matches_reference_builder). The probe stored big integers and .inf/.nan as text; the
// fixtures hold neither.
#pragma once

#include <c4/yml/extra/event_handler_ints.hpp>
#include <c4/yml/parse_engine.hpp>
#include <c4/yml/std/string.hpp>
#include <c4/yml/std/vector.hpp>
#include <myna/values.hpp>

#include <charconv>
#include <cmath>
#include <cstdint>
#include <memory_resource>
#include <string>
#include <string_view>
#include <vector>

namespace truffledata::yaml::reference {

namespace ievt = c4::yml::extra::ievt;

inline bool all_digits(const char* p, const char* e) {
  if (p == e) return false;
  for (; p < e; ++p) if (*p < '0' || *p > '9') return false;
  return true;
}

template <class Text>
void plain(myna::value_builder& b, std::string_view s, Text text) {
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
    const char* q = (c == '-' || c == '+') ? p + 1 : p;
    if (all_digits(q, e)) {
      int64_t v;
      auto r = std::from_chars(c == '+' ? p + 1 : p, e, v);
      if (r.ec == std::errc() && r.ptr == e) return b.integer(v);
      return text();
    }
    if (n > 2 && p[0] == '0' && (p[1] == 'x' || p[1] == 'o')) {
      uint64_t v;
      auto r = std::from_chars(p + 2, e, v, p[1] == 'x' ? 16 : 8);
      if (r.ec == std::errc() && r.ptr == e && v <= INT64_MAX) return b.integer(int64_t(v));
      return text();
    }
    const char* i = q;
    bool digits = false;
    while (i < e && *i >= '0' && *i <= '9') ++i, digits = true;
    if (i < e && *i == '.') { ++i; while (i < e && *i >= '0' && *i <= '9') ++i, digits = true; }
    if (digits && i < e && (*i == 'e' || *i == 'E')) {
      ++i;
      if (i < e && (*i == '-' || *i == '+')) ++i;
      const char* d = i;
      while (i < e && *i >= '0' && *i <= '9') ++i;
      if (i == d) digits = false;
    }
    if (digits && i == e) {
      double v;
      auto r = std::from_chars(c == '+' ? p + 1 : p, e, v);
      if (r.ec == std::errc() && std::isfinite(v)) return b.floating(v);
      return text();
    }
  }
  return text();
}

// Parses `src` in place with the integer handler (selected configuration) and builds the probe's
// tape of the single document into `builder`. `src` must be a fresh copy of the input.
class ints_tape {
 public:
  ints_tape() : parser_(&handler_, options()) {}

  static c4::yml::ParserOptions options() {
    c4::yml::ParserOptions o;
    o.resolve_tags(true).resolve_tags_all(true);
    return o;
  }

  // Integer events only (the probe's `ryml-ints(scan)`); returns the event count.
  int scan(std::string& src) {
    for (;;) {
      std::string copy = src;
      if (events_.size() < 64 + copy.size() * 4) events_.resize(64 + copy.size() * 4);
      if (arena_.size() < 64 + copy.size()) arena_.resize(64 + copy.size());
      handler_.reset(c4::to_substr(copy), c4::to_substr(arena_), events_.data(), int(events_.size()));
      parser_.parse_in_place_ev("reference", c4::to_substr(copy));
      size_t need = size_t(handler_.required_size_events());
      size_t aneed = handler_.required_size_arena();
      if (need <= events_.size() && aneed <= arena_.size()) {
        src.swap(copy);
        return int(need);
      }
      events_.resize(need * 2 + 64);
      arena_.resize(aneed * 2 + 64);
    }
  }

  // Builds the tape of the (single) document of `src` (parsed in place) into `b`.
  void build(std::string& src, myna::value_builder& b) {
    int n = scan(src);
    const ievt::evt_bits* e = events_.data();
    uint32_t rsrc = 0, rarena = 0;
    for (int i = 0; i < n; ++i) {
      int ev = e[i];
      int scope = ev & (ievt::BEG_ | ievt::END_ | ievt::SEQ_ | ievt::MAP_ | ievt::DOC_ | ievt::STRM);
      if (!(ev & ievt::WSTR)) {
        if (scope == ievt::BDOC) {
          b.clear();
          rsrc = b.region(std::string_view(src));
          rarena = b.region(std::string_view(arena_.data(), arena_.size()));
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
        const char* base = aren ? arena_.data() : src.data();
        std::string_view s(base + off, len);
        auto text = [&] {
          if (len <= 16) b.string(s);
          else b.string_ref(aren ? rarena : rsrc, off, len);
        };
        if ((ev & ievt::KEY_) || !(ev & ievt::PLAI)) text();
        else plain(b, s, text);
        i += 2;
      } else {
        i += 2;  // aliases, anchors, tags: not in the fixtures
      }
    }
  }

 private:
  c4::yml::extra::EventHandlerInts handler_;
  c4::yml::ParseEngine<c4::yml::extra::EventHandlerInts> parser_;
  std::vector<ievt::evt_bits> events_;
  std::vector<char> arena_;
};

}  // namespace truffledata::yaml::reference
